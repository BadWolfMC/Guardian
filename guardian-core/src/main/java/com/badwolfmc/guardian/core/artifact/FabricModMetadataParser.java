package com.badwolfmc.guardian.core.artifact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small bounded JSON parser used only for inert fabric.mod.json metadata inspection. */
final class FabricModMetadataParser {
    private static final int MAX_JSON_DEPTH = 32;

    record Metadata(String modId, String version) {}
    private record JsonNumber(String value) {}

    private final String input;
    private int index;

    private FabricModMetadataParser(String input) {
        this.input = input;
    }

    static Metadata parse(String input) throws ArtifactCatalogException {
        Object root = new FabricModMetadataParser(input).parseDocument();
        if (!(root instanceof Map<?, ?> map)) {
            throw new ArtifactCatalogException("fabric.mod.json root must be a JSON object");
        }
        Object schema = map.get("schemaVersion");
        if (!(schema instanceof JsonNumber number) || !"1".equals(number.value())) {
            throw new ArtifactCatalogException("fabric.mod.json schemaVersion must be numeric 1");
        }
        Object id = map.get("id");
        Object version = map.get("version");
        if (!(id instanceof String modId) || modId.isBlank()) {
            throw new ArtifactCatalogException("fabric.mod.json id must be a non-blank string");
        }
        if (!(version instanceof String versionString) || versionString.isBlank()) {
            throw new ArtifactCatalogException("fabric.mod.json version must be a non-blank string");
        }
        try {
            ApprovedArtifact probe = new ApprovedArtifact(
                modId,
                versionString,
                com.badwolfmc.guardian.protocol.ArtifactSha256.fromBytes(new byte[32])
            );
            return new Metadata(probe.modId(), probe.version());
        } catch (IllegalArgumentException ex) {
            throw new ArtifactCatalogException("invalid Fabric identity metadata: " + ex.getMessage(), ex);
        }
    }

    private Object parseDocument() throws ArtifactCatalogException {
        skipWhitespace();
        Object value = parseValue(0);
        skipWhitespace();
        if (index != input.length()) {
            throw error("unexpected trailing JSON content");
        }
        return value;
    }

    private Object parseValue(int depth) throws ArtifactCatalogException {
        if (depth > MAX_JSON_DEPTH) {
            throw error("JSON nesting exceeds " + MAX_JSON_DEPTH);
        }
        skipWhitespace();
        if (index >= input.length()) throw error("unexpected end of JSON");
        return switch (input.charAt(index)) {
            case '{' -> parseObject(depth + 1);
            case '[' -> parseArray(depth + 1);
            case '"' -> parseString();
            case 't' -> parseLiteral("true", Boolean.TRUE);
            case 'f' -> parseLiteral("false", Boolean.FALSE);
            case 'n' -> parseLiteral("null", null);
            default -> {
                char c = input.charAt(index);
                if (c == '-' || (c >= '0' && c <= '9')) yield parseNumber();
                throw error("unexpected JSON token");
            }
        };
    }

    private Map<String, Object> parseObject(int depth) throws ArtifactCatalogException {
        expect('{');
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (consume('}')) return result;
        while (true) {
            skipWhitespace();
            if (index >= input.length() || input.charAt(index) != '"') {
                throw error("JSON object key must be a string");
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            // Match Fabric Loader's metadata behavior: later duplicate keys replace earlier values.
            result.put(key, parseValue(depth));
            skipWhitespace();
            if (consume('}')) return result;
            expect(',');
        }
    }

    private List<Object> parseArray(int depth) throws ArtifactCatalogException {
        expect('[');
        ArrayList<Object> result = new ArrayList<>();
        skipWhitespace();
        if (consume(']')) return result;
        while (true) {
            result.add(parseValue(depth));
            skipWhitespace();
            if (consume(']')) return result;
            expect(',');
        }
    }

    private String parseString() throws ArtifactCatalogException {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (index < input.length()) {
            char c = input.charAt(index++);
            if (c == '"') return out.toString();
            if (c == '\\') {
                if (index >= input.length()) throw error("unterminated JSON escape");
                char escape = input.charAt(index++);
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> out.append(parseUnicodeEscape());
                    default -> throw error("invalid JSON escape");
                }
            } else {
                // Fabric Loader's JsonReader accepts literal control characters (notably raw line breaks)
                // inside quoted metadata strings. We only retain bounded metadata and use id/version, so mirror
                // that compatibility rather than rejecting otherwise loadable Fabric artifacts.
                out.append(c);
            }
        }
        throw error("unterminated JSON string");
    }

    private char parseUnicodeEscape() throws ArtifactCatalogException {
        if (index + 4 > input.length()) throw error("truncated JSON unicode escape");
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(input.charAt(index++), 16);
            if (digit < 0) throw error("invalid JSON unicode escape");
            value = (value << 4) | digit;
        }
        return (char) value;
    }

    private Object parseLiteral(String literal, Object value) throws ArtifactCatalogException {
        if (!input.startsWith(literal, index)) throw error("invalid JSON literal");
        index += literal.length();
        return value;
    }

    private JsonNumber parseNumber() throws ArtifactCatalogException {
        int start = index;
        if (consume('-') && index >= input.length()) throw error("truncated JSON number");
        if (consume('0')) {
            // Leading zero is complete unless followed by fraction/exponent.
        } else {
            if (!digit1to9(peek())) throw error("invalid JSON number");
            while (isDigit(peek())) index++;
        }
        if (consume('.')) {
            if (!isDigit(peek())) throw error("invalid JSON fraction");
            while (isDigit(peek())) index++;
        }
        char next = peek();
        if (next == 'e' || next == 'E') {
            index++;
            next = peek();
            if (next == '+' || next == '-') index++;
            if (!isDigit(peek())) throw error("invalid JSON exponent");
            while (isDigit(peek())) index++;
        }
        return new JsonNumber(input.substring(start, index));
    }

    private void skipWhitespace() {
        while (index < input.length()) {
            char c = input.charAt(index);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') index++;
            else break;
        }
    }

    private void expect(char expected) throws ArtifactCatalogException {
        if (index >= input.length() || input.charAt(index) != expected) {
            throw error("expected '" + expected + "'");
        }
        index++;
    }

    private boolean consume(char expected) {
        if (index < input.length() && input.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private char peek() {
        return index < input.length() ? input.charAt(index) : '\0';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean digit1to9(char c) {
        return c >= '1' && c <= '9';
    }

    private ArtifactCatalogException error(String message) {
        return new ArtifactCatalogException(message + " near character " + index);
    }
}
