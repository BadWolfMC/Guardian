package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.artifact.ArtifactCatalog;
import com.badwolfmc.guardian.core.policy.AdmissionPolicySnapshot;
import com.badwolfmc.guardian.velocity.config.VelocityOperationalSettings;

import java.util.Objects;

/** Fully validated immutable runtime candidate activated atomically by Guardian-Velocity. */
record VelocityRuntimeSnapshot(
    VelocityOperationalSettings settings,
    AdmissionPolicySnapshot admissionPolicy,
    VelocityMessages messages,
    ArtifactCatalog artifactCatalog
) {
    VelocityRuntimeSnapshot {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(admissionPolicy, "admissionPolicy");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(artifactCatalog, "artifactCatalog");
    }
}
