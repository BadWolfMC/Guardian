package com.badwolfmc.guardian.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6VelocitySessionLifecycleArchitectureTest {
    @Test
    void mutableAdmissionAndGrantsAreBoundToExactPlayerConnectionIdentity() throws Exception {
        String plugin = normalizeNewlines(Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java")));

        assertTrue(plugin.contains(
            "ConnectionIdentityRegistry<VelocityAdmissionSession> sessions"));
        assertTrue(plugin.contains(
            "ConnectionIdentityRegistry<VelocityAdmissionGrant> admitted"));
        assertTrue(plugin.contains("VelocityAdmissionGrant existingGrant = admitted.get(player)"));
        assertTrue(plugin.contains("sessions.computeIfAbsent(\n            player,"));
        assertTrue(plugin.contains("if (sessions.get(player) != session || !player.isActive())"));
        assertTrue(plugin.contains("sessions.remove(player, session)"),
            "stale/inactive completion must discard only its exact connection state");
        assertFalse(plugin.contains("ConcurrentHashMap<UUID, VelocityAdmissionSession>"));
        assertFalse(plugin.contains("ConcurrentHashMap<UUID, VelocityAdmissionGrant>"));
    }

    @Test
    void delayedDisconnectCanRemoveOnlyItsOwnConnectionState() throws Exception {
        String plugin = normalizeNewlines(Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java")));

        int disconnect = plugin.indexOf("public void onDisconnect(DisconnectEvent event)");
        int presence = plugin.indexOf("private void handlePresence", disconnect);
        String body = plugin.substring(disconnect, presence);

        assertTrue(body.contains("Player player = event.getPlayer()"));
        assertTrue(body.contains("sessions.remove(player)"));
        assertTrue(body.contains("admitted.remove(player)"));
        assertFalse(body.contains("getUniqueId()"));
    }

    @Test
    void terminalDenialRemainsBoundUntilDisconnectSoLatePacketsCannotStartFreshSession() throws Exception {
        String plugin = normalizeNewlines(Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java")));

        int complete = plugin.indexOf("private void completeDecision(");
        int apply = plugin.indexOf("private void applyDecision(", complete);
        String body = plugin.substring(complete, apply);

        assertTrue(body.contains("if (decision.outcome() == DecisionOutcome.ALLOW)"));
        assertTrue(body.contains("sessions.remove(player, session)"));
        assertFalse(body.contains("sessions.remove(player, session);\n    }"),
            "session removal must not be unconditional after a deny");
    }

    private static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
