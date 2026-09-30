package com.badwolfmc.guardian.core.operations;

/** Makes untrusted diagnostic text single-line, bounded, and visibly escaped for logs/staff output. */
public final class DiagnosticText {
    public static final int MAX_OUTPUT_CHARS = 512;
    private static final String TRUNCATED = "...[truncated]";

    private DiagnosticText() {}

    public static String oneLine(String value) {
        if (value == null) return "<null>";
        StringBuilder result = new StringBuilder(Math.min(value.length(), MAX_OUTPUT_CHARS));
        boolean truncated = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String replacement = escaped(codePoint);
            int required = replacement == null ? Character.charCount(codePoint) : replacement.length();
            if (result.length() + required > MAX_OUTPUT_CHARS - TRUNCATED.length()) {
                truncated = true;
                break;
            }
            if (replacement == null) result.appendCodePoint(codePoint);
            else result.append(replacement);
            offset += Character.charCount(codePoint);
        }
        if (truncated) result.append(TRUNCATED);
        return result.toString();
    }

    private static String escaped(int codePoint) {
        return switch (codePoint) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> {
                int type = Character.getType(codePoint);
                if (Character.isISOControl(codePoint)
                    || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                    yield codePoint <= 0xFFFF
                        ? String.format("\\u%04X", codePoint)
                        : String.format("\\U%08X", codePoint);
                }
                yield null;
            }
        };
    }
}
