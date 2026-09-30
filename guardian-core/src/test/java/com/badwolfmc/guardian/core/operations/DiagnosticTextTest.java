package com.badwolfmc.guardian.core.operations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticTextTest {
    @Test
    void escapesLineControlAndBidiFormattingCharacters() {
        String value = "ok\r\n[INFO] forged\t\u001b[31m\u202Eevil\u2028next";
        String safe = DiagnosticText.oneLine(value);

        assertFalse(safe.contains("\r"));
        assertFalse(safe.contains("\n"));
        assertFalse(safe.contains("\t"));
        assertFalse(safe.contains("\u001b"));
        assertFalse(safe.contains("\u202E"));
        assertFalse(safe.contains("\u2028"));
        assertEquals("ok\\r\\n[INFO] forged\\t\\u001B[31m\\u202Eevil\\u2028next", safe);
    }

    @Test
    void preservesMiniMessageLikeTextAsLiteralDiagnosticContent() {
        assertEquals("<red><click:run_command:'/op me'>x</click></red>",
            DiagnosticText.oneLine("<red><click:run_command:'/op me'>x</click></red>"));
    }

    @Test
    void boundsVeryLargeUntrustedDiagnostics() {
        String safe = DiagnosticText.oneLine("x".repeat(10_000));
        assertTrue(safe.length() <= DiagnosticText.MAX_OUTPUT_CHARS);
        assertTrue(safe.endsWith("...[truncated]"));
    }
}
