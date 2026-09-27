package com.badwolfmc.guardian.core.policy;

import com.badwolfmc.guardian.core.AdmissionPolicy;

import java.util.Objects;

public record AdmissionProfile(String id, int priority, AdmissionPolicy clientPolicy, ModPolicy modPolicy) {
    public AdmissionProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientPolicy, "clientPolicy");
        Objects.requireNonNull(modPolicy, "modPolicy");
    }
}
