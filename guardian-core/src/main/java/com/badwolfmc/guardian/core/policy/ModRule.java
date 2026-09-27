package com.badwolfmc.guardian.core.policy;

import java.util.List;
import java.util.Objects;

public record ModRule(String id, String modId, ModRuleAction action, List<ArtifactAcceptance> acceptances) {
    public ModRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(acceptances, "acceptances");
        acceptances = List.copyOf(acceptances);
        if (action == ModRuleAction.DENY && !acceptances.isEmpty()) {
            throw new IllegalArgumentException("DENY mod rules are unconditional and cannot declare accept clauses");
        }
    }
}
