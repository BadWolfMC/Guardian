package com.badwolfmc.guardian.core.policy;

import com.badwolfmc.guardian.core.AdmissionPermissions;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/** Implements identity override -> highest-priority provider match -> default precedence. */
public final class AdmissionProfileResolver {
    private AdmissionProfileResolver() {}

    public static ResolvedAdmissionProfile resolve(
        AdmissionPolicySnapshot snapshot,
        UUID playerId,
        AdmissionPermissionSnapshot permissions
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(permissions, "permissions");

        String override = snapshot.identityOverrides().get(playerId);
        if (override != null) {
            return new ResolvedAdmissionProfile(
                Objects.requireNonNull(snapshot.profile(override), "validated override profile"),
                permissions,
                ProfileResolutionSource.IDENTITY_OVERRIDE
            );
        }

        AdmissionProfile selected = snapshot.profiles().values().stream()
            .filter(profile -> permissions.has(AdmissionPermissions.profile(profile.id())))
            .max(Comparator.comparingInt(AdmissionProfile::priority))
            .orElse(null);
        if (selected != null) {
            return new ResolvedAdmissionProfile(selected, permissions, ProfileResolutionSource.PROVIDER);
        }
        return new ResolvedAdmissionProfile(snapshot.defaultProfile(), permissions, ProfileResolutionSource.DEFAULT);
    }
}
