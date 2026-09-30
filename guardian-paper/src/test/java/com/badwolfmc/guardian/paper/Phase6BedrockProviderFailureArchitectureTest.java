package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6BedrockProviderFailureArchitectureTest {
    @Test
    void paperTracksBedrockProvidersAcrossEnableDisableLifecycle() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        assertTrue(adapter.contains("bedrockDetector.pluginDisabled(pluginName)"));
        assertTrue(adapter.contains("bedrockDetector.pluginEnabled(pluginName)"));
        assertTrue(adapter.contains("standalone origin checks now fail closed until it is restored"));
    }

    @Test
    void paperDetectorDoesNotFreezeProviderAvailabilityAtConstruction() throws Exception {
        String detector = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperBedrockDetector.java"));
        assertTrue(detector.contains("BedrockProviderState"));
        assertTrue(detector.contains("pluginEnabled(String pluginName)"));
        assertTrue(detector.contains("pluginDisabled(String pluginName)"));
        assertTrue(detector.contains("stabilizeSignal"));
        assertFalse(detector.contains("private final boolean geyserAvailable"));
        assertFalse(detector.contains("private final boolean floodgateAvailable"));
    }

    @Test
    void backendFloodgateFailureRemainsDiagnosticRatherThanAdmissionAuthority() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        int start = adapter.indexOf("private BackendInspectionSnapshot.FloodgateSanity sanityCheckBackendFloodgate");
        int end = adapter.indexOf("    private ", start + 10);
        assertTrue(start >= 0 && end > start);
        String method = adapter.substring(start, end);
        assertTrue(method.contains("FloodgateSanity.ERROR"));
        assertTrue(method.contains("Trusted proxy admission remains authoritative"));
        assertFalse(method.contains("session.decide("));
    }

    @Test
    void backendInspectionDistinguishesCleanAbsenceFromProviderFailure() throws Exception {
        String snapshot = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/BackendInspectionSnapshot.java"));
        assertTrue(snapshot.contains("ERROR"));
        assertTrue(snapshot.contains("NOT_AVAILABLE"));
    }
}
