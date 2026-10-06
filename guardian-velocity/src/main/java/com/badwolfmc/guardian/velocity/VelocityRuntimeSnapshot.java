package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.artifact.ArtifactCatalog;
import com.badwolfmc.guardian.core.policy.AdmissionPolicySnapshot;
import com.badwolfmc.guardian.velocity.config.VelocityOperationalSettings;

import java.util.Objects;

/** Fully validated immutable runtime snapshot activated atomically by Guardian-Velocity. */
record VelocityRuntimeSnapshot(
    VelocityOperationalSettings settings,
    AdmissionPolicySnapshot admissionPolicy,
    VelocityMessages messages,
    ArtifactCatalog artifactCatalog,
    long generation
) {
    VelocityRuntimeSnapshot {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(admissionPolicy, "admissionPolicy");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(artifactCatalog, "artifactCatalog");
        if (generation < 1) throw new IllegalArgumentException("generation must be positive");
    }

    VelocityRuntimeSnapshot(
        VelocityOperationalSettings settings,
        AdmissionPolicySnapshot admissionPolicy,
        VelocityMessages messages,
        ArtifactCatalog artifactCatalog
    ) {
        this(settings, admissionPolicy, messages, artifactCatalog, 1L);
    }
}
