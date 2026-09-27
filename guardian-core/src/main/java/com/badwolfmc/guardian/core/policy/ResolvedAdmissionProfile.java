package com.badwolfmc.guardian.core.policy;

import java.util.Objects;

public record ResolvedAdmissionProfile(
    AdmissionProfile profile,
    AdmissionPermissionSnapshot permissions,
    ProfileResolutionSource source
) {
    public ResolvedAdmissionProfile {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(source, "source");
    }
}
