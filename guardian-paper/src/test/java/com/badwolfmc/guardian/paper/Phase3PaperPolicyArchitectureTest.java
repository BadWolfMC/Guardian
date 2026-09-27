package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Phase3PaperPolicyArchitectureTest {
    @Test
    void paperConsumesSharedPolicyEvaluatorAndKeepsVelocityModeAssertionOnly() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        assertTrue(source.contains("AdmissionPolicyEvaluator"));
        assertTrue(source.contains("profileProvider.resolve(session.playerId(), session.snapshot().requireAdmissionPolicy())"),
            "Paper profile resolution must receive the immutable session policy snapshot");
        assertTrue(source.contains("evaluateManifest(resolvedProfile(session), response.manifest())"));
        assertFalse(source.contains("settings().admissionPolicy()"),
            "Paper operational config must not own the shared admission policy");
        assertTrue(source.contains("authorityMode() == PaperAuthorityMode.VELOCITY"));
        assertTrue(source.contains("Do not wait here"),
            "Velocity-authoritative Paper must remain assertion-only rather than re-evaluating policy");
        assertTrue(source.contains("recordConfigurationPresenceFailure(GuardianDecision.deny"),
            "early CONFIGURATION Cerberus failures must be deferred until shared client policy requires attestation");
    }

    @Test
    void paperRestartsAdmissionAdapterWhenAuthorityChangesOnReload() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/badwolfmc/guardian/paper/GuardianPaperPlugin.java"));
        assertTrue(source.contains("boolean authorityChanged = previous.settings().authorityMode() != current.settings().authorityMode()"));
        assertTrue(source.contains("wasEnabled && (!nowEnabled || authorityChanged)"));
        assertTrue(source.contains("nowEnabled && (!wasEnabled || authorityChanged)"));
    }
}
