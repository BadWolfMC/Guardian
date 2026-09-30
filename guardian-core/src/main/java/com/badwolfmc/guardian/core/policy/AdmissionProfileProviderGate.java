package com.badwolfmc.guardian.core.policy;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Security boundary around asynchronous pre-login profile providers.
 *
 * <p>Provider failure is represented as an exceptional completion. Callers must decide explicitly
 * whether an independently configured identity override is sufficient to continue; ordinary users
 * must never be silently reinterpreted as the default profile after a provider outage.</p>
 */
public final class AdmissionProfileProviderGate {
    private AdmissionProfileProviderGate() {}

    public static CompletableFuture<AdmissionPermissionSnapshot> resolve(
        AdmissionProfileProvider provider,
        UUID playerId,
        AdmissionPolicySnapshot policySnapshot,
        int timeoutSeconds
    ) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(policySnapshot, "policySnapshot");
        if (timeoutSeconds <= 0) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("profile provider timeout must be positive"));
        }

        final CompletionStage<AdmissionPermissionSnapshot> stage;
        try {
            stage = Objects.requireNonNull(
                provider.resolve(playerId, policySnapshot),
                "profile provider returned a null completion stage");
        } catch (RuntimeException | LinkageError ex) {
            return CompletableFuture.failedFuture(ex);
        }

        final CompletableFuture<AdmissionPermissionSnapshot> future;
        try {
            future = Objects.requireNonNull(
                stage.toCompletableFuture(), "profile provider returned a null CompletableFuture");
        } catch (RuntimeException | LinkageError ex) {
            return CompletableFuture.failedFuture(ex);
        }

        return future
            .thenApply(snapshot -> Objects.requireNonNull(
                snapshot, "profile provider returned a null permission snapshot"))
            .orTimeout(timeoutSeconds, TimeUnit.SECONDS);
    }
}
