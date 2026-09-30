package com.badwolfmc.guardian.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6BedrockProviderFailureArchitectureTest {
    @Test
    void velocityRefreshesOptionalProviderPresenceAroundEachOriginDecision() throws Exception {
        String detector = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/BedrockDetector.java"));
        assertTrue(detector.contains("refreshAvailability();"));
        assertTrue(detector.contains("BedrockProviderState"));
        assertTrue(detector.contains("stabilizeSignal"));
        assertTrue(detector.contains("server.getPluginManager().isLoaded(\"geyser\")"));
        assertTrue(detector.contains("server.getPluginManager().isLoaded(\"floodgate\")"));
        assertFalse(detector.contains("private final boolean geyserAvailable"));
        assertFalse(detector.contains("private final boolean floodgateAvailable"));
    }

    @Test
    void javaClassificationStillDependsOnlyOnTrustedProviderEvidenceAndBrand() throws Exception {
        String plugin = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        int classifier = plugin.indexOf("ClientOriginClassifier.classify(");
        assertTrue(classifier >= 0);
        String call = plugin.substring(classifier, plugin.indexOf(");", classifier) + 2);
        assertTrue(call.contains("bedrock.geyser() == BedrockSignal.BEDROCK"));
        assertTrue(call.contains("bedrock.floodgate() == BedrockSignal.BEDROCK"));
        assertTrue(call.contains("brand"));
        assertFalse(call.toLowerCase().contains("username"));
    }
}
