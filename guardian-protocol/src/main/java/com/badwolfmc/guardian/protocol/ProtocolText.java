package com.badwolfmc.guardian.protocol;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Shared validation for human-readable protocol metadata carried across the trust boundary. */
final class ProtocolText {
    private ProtocolText() {}

    static void validate(String value, int maxUtf8Bytes, String fieldName) {
        encode(value, maxUtf8Bytes, fieldName);
    }

    static byte[] encode(String value, int maxUtf8Bytes, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is blank");
        }
        rejectDiagnosticControls(value, fieldName);
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value));
            int bytes = encoded.remaining();
            if (bytes > maxUtf8Bytes) {
                throw new IllegalArgumentException(fieldName + " exceeds " + maxUtf8Bytes + " UTF-8 bytes");
            }
            byte[] result = new byte[bytes];
            encoded.get(result);
            return result;
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException(fieldName + " contains invalid Unicode", ex);
        }
    }

    private static void rejectDiagnosticControls(String value, String fieldName) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR) {
                throw new IllegalArgumentException(
                    fieldName + " contains forbidden control/format character U+"
                        + String.format("%04X", codePoint));
            }
            offset += Character.charCount(codePoint);
        }
    }
}
