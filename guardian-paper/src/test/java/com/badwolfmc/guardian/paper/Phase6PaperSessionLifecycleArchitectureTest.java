package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6PaperSessionLifecycleArchitectureTest {
    @Test
    void configurationCallbacksResolveByExactConnectionIdentity() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        String registry = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionSessions.java"));

        assertTrue(adapter.contains(
            "new AdmissionSession(playerId, runtimeManager.current(), connection)"));
        assertTrue(count(adapter, "sessions.forConfiguration(") >= 3,
            "configure, final validation, and CONFIGURATION plugin messages must use exact connection lookup");
        assertTrue(registry.contains("session.matchesConfigurationConnection(connectionIdentity)"));
        assertTrue(count(adapter, "if (session.readyForPlay())") >= 2,
            "late CONFIGURATION work must be frozen after the session is handed to PLAY");
    }

    @Test
    void playCallbacksAndTimeoutsResolveByExactPlayerAndSession() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));

        assertTrue(adapter.contains("AdmissionSession session = resolveJoinSession(player)"));
        assertTrue(adapter.contains("if (!session.bindPlayConnection(player))"));
        assertTrue(adapter.contains("sessions.forPlay(player.getUniqueId(), player)"));
        assertTrue(adapter.contains("() -> handleHandshakeTimeout(player, session)"));
        assertTrue(adapter.contains("if (!isCurrentPlaySession(player, session)"));
        assertFalse(adapter.contains("handleHandshakeTimeout(player.getUniqueId())"));
    }

    @Test
    void uuidOnlyCloseEventCannotBlindlyRemoveANewerSession() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));

        int close = adapter.indexOf("public void onConnectionClose(PlayerConnectionCloseEvent event)");
        int quarantine = adapter.indexOf("// --- Bounded PLAY quarantine", close);
        String body = adapter.substring(close, quarantine);
        assertTrue(body.contains("for (AdmissionSession session : sessions.forPlayer(playerId))"));
        assertTrue(body.contains("configurationConnectionIdentity()"));
        assertTrue(body.contains("!boundConnection.isConnected()"));
        assertTrue(body.contains("sessions.remove(session)"));
        assertFalse(body.contains("sessions.remove(event.getPlayerUniqueId())"));
        assertFalse(body.contains("inspectionService.remove"),
            "UUID-only close events must not erase a newer active inspection snapshot");
    }

    @Test
    void ambiguousSameUuidPlayHandoffFailsClosedInsteadOfGuessing() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));

        assertTrue(adapter.contains("if (candidates.size() == 1 && !anotherActivePlayConnection)"));
        assertTrue(adapter.contains("multiple concurrent Guardian admission sessions were ready for the same UUID"));
        assertTrue(adapter.contains("player.kick(messageRenderer.renderDecision("));
        assertFalse(adapter.contains("sessions.get(player.getUniqueId())"));
    }

    @Test
    void activeInspectionIsCreatedOnlyAfterTheExactConnectionEntersPlay() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));

        int validate = adapter.indexOf("public void onValidateLogin(PlayerConnectionValidateLoginEvent event)");
        int join = adapter.indexOf("public void onJoin(PlayerJoinEvent event)", validate);
        String validationBody = adapter.substring(validate, join);
        assertFalse(validationBody.contains("captureStandaloneInspection("));
        assertFalse(validationBody.contains("inspectionService.putBackend("));
        assertFalse(adapter.substring(0, validate).contains("inspectionService.remove(playerId)"),
            "a competing initial connection must not erase the currently active player's snapshot");

        int quit = adapter.indexOf("public void onQuit(PlayerQuitEvent event)", join);
        String joinBody = adapter.substring(join, quit);
        assertTrue(joinBody.contains("captureStandaloneInspection(player, session, decision)"));
        assertTrue(joinBody.contains("captureBackendInspection(player, session, decision)"));
    }

    private static int count(String source, String needle) {
        int count = 0;
        int at = 0;
        while ((at = source.indexOf(needle, at)) >= 0) {
            count++;
            at += needle.length();
        }
        return count;
    }
}
