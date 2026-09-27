package com.badwolfmc.guardian.core.policy;

import com.badwolfmc.guardian.core.GuardianDecision;

import java.util.List;
import java.util.Objects;

public record PolicyEvaluation(
    GuardianDecision decision,
    List<PolicyViolation> violations,
    List<PolicyViolation> bypassedViolations
) {
    public PolicyEvaluation {
        Objects.requireNonNull(decision, "decision");
        violations = List.copyOf(violations);
        bypassedViolations = List.copyOf(bypassedViolations);
    }
}
