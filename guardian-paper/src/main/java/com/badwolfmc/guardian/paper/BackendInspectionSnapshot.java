package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.core.operations.DiagnosticText;
import com.badwolfmc.guardian.protocol.ConnectionOrigin;

import java.util.Objects;
import java.util.UUID;

/** Paper-local evidence retained for an active connection admitted by Guardian-Velocity. */
record BackendInspectionSnapshot(
    UUID playerId,
    String playerName,
    String serverName,
    ConnectionOrigin assertedOrigin,
    String proxySessionId,
    GuardianDecision decision,
    FloodgateSanity floodgateSanity
) {
    BackendInspectionSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        playerName = normalize(playerName, "<unknown>");
        serverName = normalize(serverName, "<unknown>");
        Objects.requireNonNull(assertedOrigin, "assertedOrigin");
        proxySessionId = normalize(proxySessionId, "<unknown>");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(floodgateSanity, "floodgateSanity");
    }

    enum FloodgateSanity {
        AGREES,
        DISAGREES,
        ERROR,
        NOT_AVAILABLE,
        NOT_APPLICABLE
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : DiagnosticText.oneLine(value);
    }
}
