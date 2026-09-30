package com.badwolfmc.guardian.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Phase6ProfileProviderFailureArchitectureTest {
    @Test
    void velocityFailsClosedWhenSelectedProviderCannotResolveOrdinaryPlayer() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        assertTrue(source.contains("AdmissionProfileProviderGate.resolve"));
        assertTrue(source.contains("DecisionReason.PROFILE_RESOLUTION_FAILED"));
        assertTrue(source.contains("refusing default-profile fallback"));
        assertFalse(source.contains("falling back to default/identity profile without bypasses"));
        assertTrue(source.contains("identityOverrides().containsKey(player.getUniqueId())"),
            "an explicit UUID override may continue safely without provider-derived bypasses");
    }

    @Test
    void velocityLuckPermsProviderRechecksRuntimeAvailabilityPerResolution() throws Exception {
        String provider = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/VelocityLuckPermsProfileProvider.java"));
        int resolve = provider.indexOf("CompletionStage<AdmissionPermissionSnapshot> resolve");
        String resolveBody = provider.substring(resolve);
        assertTrue(resolveBody.contains("isLoaded(\"luckperms\")"));
        assertTrue(resolveBody.contains("LuckPermsProvider.get()"));
        assertTrue(resolveBody.contains("failedFuture"));
    }
}
