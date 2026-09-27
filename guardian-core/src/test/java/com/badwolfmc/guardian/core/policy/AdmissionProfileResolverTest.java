package com.badwolfmc.guardian.core.policy;

import com.badwolfmc.guardian.core.AdmissionPermissions;
import com.badwolfmc.guardian.core.AdmissionPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdmissionProfileResolverTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void identityOverrideBeatsProviderAndProviderUsesHighestPriority() {
        AdmissionProfile defaults = profile("default", 0);
        AdmissionProfile member = profile("member", 10);
        AdmissionProfile staff = profile("staff", 20);
        AdmissionPolicySnapshot snapshot = new AdmissionPolicySnapshot(1, "default",
            Map.of("default", defaults, "member", member, "staff", staff),
            Map.of(PLAYER, "member"));
        AdmissionPermissionSnapshot permissions = new AdmissionPermissionSnapshot("test", Map.of(
            AdmissionPermissions.profile("member"), true,
            AdmissionPermissions.profile("staff"), true));

        ResolvedAdmissionProfile overridden = AdmissionProfileResolver.resolve(snapshot, PLAYER, permissions);
        assertEquals("member", overridden.profile().id());
        assertEquals(ProfileResolutionSource.IDENTITY_OVERRIDE, overridden.source());

        ResolvedAdmissionProfile provider = AdmissionProfileResolver.resolve(snapshot, UUID.randomUUID(), permissions);
        assertEquals("staff", provider.profile().id());
        assertEquals(ProfileResolutionSource.PROVIDER, provider.source());
    }

    @Test
    void fallsBackToDefaultWhenProviderIsAbsentOrHasNoMatch() {
        AdmissionProfile defaults = profile("default", 0);
        AdmissionPolicySnapshot snapshot = new AdmissionPolicySnapshot(1, "default",
            Map.of("default", defaults), Map.of());
        ResolvedAdmissionProfile result = AdmissionProfileResolver.resolve(snapshot, PLAYER, AdmissionPermissionSnapshot.none());
        assertEquals("default", result.profile().id());
        assertEquals(ProfileResolutionSource.DEFAULT, result.source());
    }

    private static AdmissionProfile profile(String id, int priority) {
        return new AdmissionProfile(id, priority, AdmissionPolicy.defaults(), new ModPolicy(
            ModPolicyMode.DENYLIST, OriginPolicyAction.DENY, OriginPolicyAction.DENY,
            Set.of("fabricloader", "cerberus"), Map.of(), Map.of()));
    }
}
