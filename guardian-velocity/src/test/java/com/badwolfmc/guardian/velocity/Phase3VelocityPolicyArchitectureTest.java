package com.badwolfmc.guardian.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Phase3VelocityPolicyArchitectureTest {
    @Test
    void velocityConsumesSharedEvaluatorInsteadOfFeasibilityClientSwitch() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        assertTrue(source.contains("AdmissionPolicyEvaluator"));
        assertTrue(source.contains("policyEvaluator.evaluateClient"));
        assertTrue(source.contains("AdmissionPolicySnapshot policySnapshot = policyRuntime.current()"),
            "one immutable shared policy snapshot must be captured for the proxy admission session");
        assertTrue(source.contains("resolve(player.getUniqueId(), policySnapshot)"),
            "profile-provider resolution must receive the same immutable policy context");
        assertTrue(source.contains("policyEvaluator.evaluateManifest"));
        assertFalse(source.contains("GuardianDecision.allow(\n                DecisionReason.BEDROCK_POLICY"));
        assertFalse(source.contains("if (classification == ClientClassification."),
            "Velocity must not retain positive client-class policy branches");
        assertFalse(source.contains("if (classification != ClientClassification."),
            "Velocity must not retain negative client-class policy branches");
        assertTrue(source.contains("session.requireCerberus();"));
        assertTrue(source.contains("configurationAttestationFailure()"),
            "early Cerberus protocol failures must not outrun shared client-policy resolution");
        assertTrue(source.contains("AdmissionProfileProvider.none()"),
            "LuckPerms absence/failure must retain deterministic default/identity fallback");
    }
}
