package com.badwolfmc.guardian.core.policy;

import java.util.List;
import java.util.Objects;

public record RequiredModRule(String id, String modId, List<ArtifactAcceptance> acceptances) {
    public RequiredModRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(acceptances, "acceptances");
        acceptances = List.copyOf(acceptances);
    }
}
