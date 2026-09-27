package com.badwolfmc.guardian.core;

import java.util.Map;
import java.util.Objects;

public record GuardianDecision(
    DecisionOutcome outcome,
    DecisionReason reason,
    String detail,
    Map<String, String> context
) {
    public GuardianDecision {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reason, "reason");
        detail = detail == null ? "" : detail;
        context = context == null ? Map.of() : Map.copyOf(context);
    }

    public GuardianDecision(DecisionOutcome outcome, DecisionReason reason, String detail) {
        this(outcome, reason, detail, Map.of());
    }

    public static GuardianDecision allow(DecisionReason reason, String detail) {
        return new GuardianDecision(DecisionOutcome.ALLOW, reason, detail);
    }

    public static GuardianDecision deny(DecisionReason reason, String detail) {
        return new GuardianDecision(DecisionOutcome.DENY, reason, detail);
    }

    public static GuardianDecision deny(
        DecisionReason reason,
        String detail,
        Map<String, String> context
    ) {
        return new GuardianDecision(DecisionOutcome.DENY, reason, detail, context);
    }
}
