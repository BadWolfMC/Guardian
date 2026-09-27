package com.badwolfmc.guardian.core.policy;

import java.util.Objects;

/** Structured mod-policy reason retained independently from player-facing text. */
public record PolicyViolation(PolicyViolationCode code, String ruleId, String modId, String detail) {
    public PolicyViolation {
        Objects.requireNonNull(code, "code");
        ruleId = ruleId == null ? "" : ruleId;
        modId = modId == null ? "" : modId;
        detail = detail == null ? "" : detail;
    }
}
