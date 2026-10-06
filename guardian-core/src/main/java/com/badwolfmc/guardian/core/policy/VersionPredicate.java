package com.badwolfmc.guardian.core.policy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded administrator version predicate.
 *
 * <p>Supported forms are: {@code *}; exact strings; one trailing {@code *} prefix wildcard;
 * and whitespace-separated numeric dotted comparisons such as {@code >=1.2 <2.0}. Comparator
 * operands deliberately accept only dotted numeric versions. Observed versions may append
 * {@code +} build metadata (for example {@code 1.8.7+fabric.26.2}); that metadata is ignored
 * for numeric comparison. Other non-numeric/mod-specific versions remain fully supported through
 * exact and prefix matching rather than guessed ordering.</p>
 */
public final class VersionPredicate {
    private static final Pattern COMPARATOR = Pattern.compile("(>=|<=|>|<|==|=)([0-9]+(?:\\.[0-9]+)*)");
    private static final Pattern NUMERIC_CANDIDATE = Pattern.compile(
        "([0-9]+(?:\\.[0-9]+)*)(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"
    );
    private static final int MAX_EXPRESSION_CHARS = 192;

    private enum Kind { ANY, EXACT, PREFIX, RANGE }
    private record Term(String operator, List<BigInteger> version) {}

    private final String expression;
    private final Kind kind;
    private final String literal;
    private final List<Term> terms;

    private VersionPredicate(String expression, Kind kind, String literal, List<Term> terms) {
        this.expression = expression;
        this.kind = kind;
        this.literal = literal;
        this.terms = List.copyOf(terms);
    }

    public static VersionPredicate parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String value = raw.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("version predicate must not be blank");
        if (value.length() > MAX_EXPRESSION_CHARS) {
            throw new IllegalArgumentException("version predicate exceeds " + MAX_EXPRESSION_CHARS + " characters");
        }
        if (value.equals("*")) return new VersionPredicate(value, Kind.ANY, "", List.of());

        if (value.indexOf('*') >= 0) {
            if (!value.endsWith("*") || value.indexOf('*') != value.length() - 1) {
                throw new IllegalArgumentException("version wildcard is supported only as one trailing '*'");
            }
            String prefix = value.substring(0, value.length() - 1);
            if (prefix.isEmpty()) return new VersionPredicate(value, Kind.ANY, "", List.of());
            return new VersionPredicate(value, Kind.PREFIX, prefix, List.of());
        }

        if (value.startsWith(">") || value.startsWith("<") || value.startsWith("=")) {
            String[] pieces = value.split("\\s+");
            ArrayList<Term> terms = new ArrayList<>();
            for (String piece : pieces) {
                Matcher matcher = COMPARATOR.matcher(piece);
                if (!matcher.matches()) {
                    throw new IllegalArgumentException(
                        "invalid numeric comparison term '" + piece + "'; expected forms like >=1.2 or <2.0");
                }
                terms.add(new Term(matcher.group(1), numericParts(matcher.group(2))));
            }
            if (terms.isEmpty()) throw new IllegalArgumentException("version range has no comparison terms");
            validateSatisfiableRange(terms);
            return new VersionPredicate(value, Kind.RANGE, "", terms);
        }

        if (value.contains(" ") || value.contains("\t") || value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException("exact version predicates must not contain whitespace");
        }
        return new VersionPredicate(value, Kind.EXACT, value, List.of());
    }

    public boolean matches(String version) {
        if (version == null) return false;
        return switch (kind) {
            case ANY -> true;
            case EXACT -> version.equals(literal);
            case PREFIX -> version.startsWith(literal);
            case RANGE -> matchesRange(version);
        };
    }

    private boolean matchesRange(String version) {
        Matcher candidateMatcher = NUMERIC_CANDIDATE.matcher(version);
        if (!candidateMatcher.matches()) return false;
        List<BigInteger> candidate = numericParts(candidateMatcher.group(1));
        for (Term term : terms) {
            int compared = compareNumeric(candidate, term.version());
            boolean match = switch (term.operator()) {
                case ">" -> compared > 0;
                case ">=" -> compared >= 0;
                case "<" -> compared < 0;
                case "<=" -> compared <= 0;
                case "=", "==" -> compared == 0;
                default -> false;
            };
            if (!match) return false;
        }
        return true;
    }

    private static void validateSatisfiableRange(List<Term> terms) {
        List<BigInteger> equality = null;
        List<BigInteger> lower = null;
        boolean lowerInclusive = true;
        List<BigInteger> upper = null;
        boolean upperInclusive = true;

        for (Term term : terms) {
            switch (term.operator()) {
                case "=", "==" -> {
                    if (equality != null && compareNumeric(equality, term.version()) != 0) {
                        throw new IllegalArgumentException("numeric version range contains conflicting equality terms");
                    }
                    equality = term.version();
                }
                case ">", ">=" -> {
                    boolean inclusive = term.operator().equals(">=");
                    if (lower == null) {
                        lower = term.version();
                        lowerInclusive = inclusive;
                    } else {
                        int compared = compareNumeric(term.version(), lower);
                        if (compared > 0) {
                            lower = term.version();
                            lowerInclusive = inclusive;
                        } else if (compared == 0) {
                            lowerInclusive = lowerInclusive && inclusive;
                        }
                    }
                }
                case "<", "<=" -> {
                    boolean inclusive = term.operator().equals("<=");
                    if (upper == null) {
                        upper = term.version();
                        upperInclusive = inclusive;
                    } else {
                        int compared = compareNumeric(term.version(), upper);
                        if (compared < 0) {
                            upper = term.version();
                            upperInclusive = inclusive;
                        } else if (compared == 0) {
                            upperInclusive = upperInclusive && inclusive;
                        }
                    }
                }
                default -> throw new IllegalArgumentException("unsupported numeric comparison operator " + term.operator());
            }
        }

        if (equality != null) {
            if (!satisfiesLower(equality, lower, lowerInclusive) || !satisfiesUpper(equality, upper, upperInclusive)) {
                throw new IllegalArgumentException("numeric version range is contradictory");
            }
            return;
        }
        if (lower != null && upper != null) {
            int compared = compareNumeric(lower, upper);
            if (compared > 0 || (compared == 0 && (!lowerInclusive || !upperInclusive))) {
                throw new IllegalArgumentException("numeric version range is contradictory");
            }
        }
    }

    private static boolean satisfiesLower(
        List<BigInteger> candidate, List<BigInteger> lower, boolean inclusive
    ) {
        if (lower == null) return true;
        int compared = compareNumeric(candidate, lower);
        return compared > 0 || (inclusive && compared == 0);
    }

    private static boolean satisfiesUpper(
        List<BigInteger> candidate, List<BigInteger> upper, boolean inclusive
    ) {
        if (upper == null) return true;
        int compared = compareNumeric(candidate, upper);
        return compared < 0 || (inclusive && compared == 0);
    }

    private static List<BigInteger> numericParts(String value) {
        String[] split = value.split("\\.");
        ArrayList<BigInteger> parts = new ArrayList<>(split.length);
        for (String part : split) parts.add(new BigInteger(part));
        return List.copyOf(parts);
    }

    private static int compareNumeric(List<BigInteger> left, List<BigInteger> right) {
        int length = Math.max(left.size(), right.size());
        for (int i = 0; i < length; i++) {
            BigInteger a = i < left.size() ? left.get(i) : BigInteger.ZERO;
            BigInteger b = i < right.size() ? right.get(i) : BigInteger.ZERO;
            int compared = a.compareTo(b);
            if (compared != 0) return compared;
        }
        return 0;
    }

    public String expression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression;
    }
}
