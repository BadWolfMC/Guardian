package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VelocityMessagesTest {
    @Test
    void bundledFallbackLocaleCoversVelocityAdmissionDenials() {
        VelocityMessages messages = VelocityMessages.load();
        for (DecisionReason reason : DecisionReason.values()) {
            assertNotNull(messages.render(reason, ClientClassification.JAVA_FABRIC));
        }
    }
    @Test
    void manifestDenialUsesSpecificPolicyProblemKey() {
        GuardianDecision denied = GuardianDecision.deny(
            DecisionReason.MANIFEST_DENIED,
            "internal detail",
            Map.of("policy_violation", "ARTIFACT_NOT_ACCEPTED", "mod_id", "examplemod")
        );
        assertEquals("admission.manifest-denied.artifact-not-accepted", VelocityMessages.keyFor(denied));
    }

}
