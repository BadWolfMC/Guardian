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
        assertTrue(source.contains("VelocityRuntimeSnapshot policySnapshot") ||
            source.contains("AdmissionPolicySnapshot policySnapshot = session.runtimeSnapshot().admissionPolicy()"),
            "one immutable shared policy/runtime snapshot must be captured for the proxy admission session");
        assertTrue(source.contains("AdmissionProfileProviderGate.resolve("));
        assertTrue(source.contains("profileProvider, player.getUniqueId(), policySnapshot"),
            "profile-provider resolution must receive the same immutable policy context through the fail-closed provider gate");
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
            "intentional LuckPerms absence must retain deterministic default/identity fallback");
        assertTrue(source.contains("DecisionReason.PROFILE_RESOLUTION_FAILED"),
            "runtime provider failure must fail closed instead of silently selecting the default profile");
    }
}
