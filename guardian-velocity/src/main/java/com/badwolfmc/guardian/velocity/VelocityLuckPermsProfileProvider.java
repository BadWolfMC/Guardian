package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.AdmissionPermissions;
import com.badwolfmc.guardian.core.policy.AdmissionPermissionSnapshot;
import com.badwolfmc.guardian.core.policy.AdmissionProfileProvider;
import com.badwolfmc.guardian.core.policy.AdmissionPolicySnapshot;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Optional Velocity/LuckPerms pre-login provider using proxy-static query options. */
final class VelocityLuckPermsProfileProvider implements AdmissionProfileProvider {
    private final LuckPerms luckPerms;

    VelocityLuckPermsProfileProvider() {
        this.luckPerms = LuckPermsProvider.get();
    }

    @Override
    public CompletionStage<AdmissionPermissionSnapshot> resolve(
        UUID playerId, AdmissionPolicySnapshot policySnapshot
    ) {
        return luckPerms.getUserManager().loadUser(playerId).thenApply(user -> {
            var queryOptions = luckPerms.getContextManager().getStaticQueryOptions();
            Map<String, Boolean> effective = user.getCachedData().getPermissionData(queryOptions).getPermissionMap();
            LinkedHashMap<String, Boolean> admission = new LinkedHashMap<>();
            effective.forEach((permission, value) -> {
                if (permission.equals(AdmissionPermissions.ROOT)
                    || permission.startsWith(AdmissionPermissions.ROOT + ".")) {
                    admission.put(permission, value);
                }
            });
            return new AdmissionPermissionSnapshot("luckperms-velocity", admission);
        });
    }
}
