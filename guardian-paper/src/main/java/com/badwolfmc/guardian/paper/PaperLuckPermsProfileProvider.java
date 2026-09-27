package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.AdmissionPermissions;
import com.badwolfmc.guardian.core.policy.AdmissionPermissionSnapshot;
import com.badwolfmc.guardian.core.policy.AdmissionProfileProvider;
import com.badwolfmc.guardian.core.policy.AdmissionPolicySnapshot;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Optional Paper/LuckPerms pre-login provider. Loaded only when LuckPerms is available. */
final class PaperLuckPermsProfileProvider implements AdmissionProfileProvider {
    private final LuckPerms luckPerms;

    private PaperLuckPermsProfileProvider(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    static AdmissionProfileProvider create(GuardianPaperPlugin plugin) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            plugin.getLogger().info("Guardian Admission profile provider: LuckPerms not present; default/identity profiles only.");
            return AdmissionProfileProvider.none();
        }
        RegisteredServiceProvider<LuckPerms> registration =
            plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (registration == null || registration.getProvider() == null) {
            plugin.getLogger().warning("LuckPerms is enabled but its API service is unavailable; Guardian will use default/identity profiles.");
            return AdmissionProfileProvider.none();
        }
        plugin.getLogger().info("Guardian Admission profile provider: LuckPerms (static pre-login query options).");
        return new PaperLuckPermsProfileProvider(registration.getProvider());
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
            return new AdmissionPermissionSnapshot("luckperms-paper", admission);
        });
    }
}
