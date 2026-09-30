package com.badwolfmc.guardian.core.policy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Platform-neutral asynchronous pre-login permission/profile provider seam.
 *
 * <p>The immutable policy snapshot is supplied as context so adapters can resolve only the
 * administrator-defined profile/bypass surface when their permission system supports targeted
 * checks. Providers may return a broader bounded Guardian permission snapshot, but policy
 * interpretation remains in guardian-core.</p>
 */
@FunctionalInterface
public interface AdmissionProfileProvider {
    CompletionStage<AdmissionPermissionSnapshot> resolve(UUID playerId, AdmissionPolicySnapshot policySnapshot);

    static AdmissionProfileProvider none() {
        return (ignoredPlayer, ignoredPolicy) -> CompletableFuture.completedFuture(AdmissionPermissionSnapshot.none());
    }

    static AdmissionProfileProvider unavailable(String reason) {
        String message = reason == null || reason.isBlank() ? "admission profile provider unavailable" : reason;
        return (ignoredPlayer, ignoredPolicy) ->
            CompletableFuture.failedFuture(new IllegalStateException(message));
    }
}
