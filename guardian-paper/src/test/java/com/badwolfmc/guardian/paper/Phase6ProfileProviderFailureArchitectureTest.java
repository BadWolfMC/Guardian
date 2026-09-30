package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Phase6ProfileProviderFailureArchitectureTest {
    @Test
    void paperFailsClosedWhenSelectedProviderCannotResolveOrdinaryPlayer() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        assertTrue(adapter.contains("AdmissionProfileProviderGate.resolve"));
        assertTrue(adapter.contains("DecisionReason.PROFILE_RESOLUTION_FAILED"));
        assertTrue(adapter.contains("refusing default-profile fallback"));
        assertFalse(adapter.contains("falling back to default/identity profile without bypasses"));
        assertTrue(adapter.contains("identityOverrides().containsKey(session.playerId())"),
            "an explicit UUID override may continue safely without provider-derived bypasses");
    }

    @Test
    void paperTracksLuckPermsDisappearanceWithoutQueryingBukkitFromAsyncResolution() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        String provider = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperLuckPermsProfileProvider.java"));
        int resolve = provider.indexOf("CompletionStage<AdmissionPermissionSnapshot> resolve");
        String resolveBody = provider.substring(resolve);

        assertTrue(adapter.contains("onPluginDisable(PluginDisableEvent event)"));
        assertTrue(adapter.contains("onServiceUnregister(ServiceUnregisterEvent event)"));
        assertTrue(adapter.contains("AdmissionProfileProvider.unavailable"));
        assertFalse(resolveBody.contains("getPluginManager()"),
            "async pre-login resolution must not query Bukkit plugin state");
        assertFalse(resolveBody.contains("getServicesManager()"),
            "async pre-login resolution must not query Bukkit service state");
    }

    @Test
    void finalValidationCannotSilentlyInventDefaultProfile() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        int method = adapter.indexOf("private ResolvedAdmissionProfile resolvedProfile");
        String body = adapter.substring(method, adapter.indexOf("private void evaluateStandaloneConfiguration", method));
        assertTrue(body.contains("PROFILE_RESOLUTION_FAILED"));
        assertFalse(body.contains("default profile"));
    }
}
