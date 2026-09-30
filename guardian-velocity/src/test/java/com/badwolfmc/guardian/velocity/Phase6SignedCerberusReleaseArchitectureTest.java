package com.badwolfmc.guardian.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6SignedCerberusReleaseArchitectureTest {
    @Test
    void velocityUsesSharedReleaseTrustForPresenceChallengeAndResponseValidation() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        assertTrue(source.contains("cerberusReleaseTrust().requiredCapabilities()"));
        assertTrue(source.contains("requiredCerberusCapabilities(session)"));
        assertTrue(source.contains("cerberusReleaseTrust())"));
        assertTrue(source.contains("DecisionReason.CERBERUS_RELEASE_REQUIRED"));
    }
}
