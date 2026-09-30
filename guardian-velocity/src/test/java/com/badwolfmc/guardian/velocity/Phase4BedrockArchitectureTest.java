package com.badwolfmc.guardian.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase4BedrockArchitectureTest {
    @Test
    void velocityFailsClosedOnProviderFailureButPositiveEvidenceStillWins() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        assertTrue(source.contains("BedrockResolution.INDETERMINATE"));
        assertTrue(source.contains("refusing to reinterpret an indeterminate connection as Java"));
        assertTrue(source.contains("positive supported API evidence classifies this connection as BEDROCK"));
        assertFalse(source.contains("feasibility-era integration"));
    }

    @Test
    void optionalProviderQueryFailuresAreDistinctFromProviderAbsence() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/BedrockDetector.java"));
        assertTrue(source.contains("unavailableSignal"));
        assertTrue(source.contains("BedrockSignal.ERROR"));
        assertFalse(source.contains("Guardian Phase 0B.3 could not query"));
    }
}
