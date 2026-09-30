package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6ProxyAssertionArchitectureTest {
    @Test
    void paperUsesConnectionSnapshotKeyAndBoundedReplayGuard() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));

        assertTrue(adapter.contains("session.snapshot().settings().proxyAssertionSecret().copyBytes()"),
            "assertion verification must stay bound to the immutable runtime snapshot for this connection");
        assertTrue(adapter.contains("new ProxyAssertionReplayGuard(MAX_PROXY_ASSERTION_REPLAY_ENTRIES)"));
        assertTrue(adapter.contains("proxyAssertionReplayGuard.record("));
        assertTrue(adapter.contains("replayed proxy admission assertion"));
        assertTrue(adapter.contains("proxy assertion replay guard capacity exhausted"));
        assertTrue(adapter.contains("Arrays.fill(proxySecret, (byte) 0)"));
        assertFalse(adapter.contains(
            "runtimeManager.current().settings().proxyAssertionSecret().copyBytes()"));
    }

    @Test
    void paperRejectsDuplicateAssertionsWithinOneConfigurationSession() throws Exception {
        String session = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/AdmissionSession.java"));
        assertTrue(session.contains("proxyAdmission.compareAndSet(null"));
        assertFalse(session.contains("existing.connectionOrigin() == assertion.connectionOrigin()"));
    }
    @Test
    void velocityAuthorityFailsClosedWhenDirectBackendConnectionHasNoAssertion() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));

        assertTrue(adapter.contains("settings().authorityMode() == PaperAuthorityMode.VELOCITY"));
        assertTrue(adapter.contains("DecisionReason.PROXY_ASSERTION_REQUIRED"));
        assertTrue(adapter.contains(
            "no valid Guardian-Velocity admission assertion arrived before final validation"));
        assertTrue(adapter.contains("event.kickMessage(messageRenderer.renderDecision("));
    }

}
