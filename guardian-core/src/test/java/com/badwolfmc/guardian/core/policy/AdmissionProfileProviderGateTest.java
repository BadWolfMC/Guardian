package com.badwolfmc.guardian.core.policy;

import com.badwolfmc.guardian.core.AdmissionPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionProfileProviderGateTest {
    private static final UUID PLAYER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final AdmissionPolicySnapshot POLICY = policy();

    @Test
    void successfulProviderSnapshotPassesThrough() {
        AdmissionPermissionSnapshot expected = new AdmissionPermissionSnapshot("test", Map.of("guardian.test", true));
        var actual = AdmissionProfileProviderGate.resolve(
            (player, policy) -> CompletableFuture.completedFuture(expected), PLAYER, POLICY, 1).join();
        assertSame(expected, actual);
    }

    @Test
    void noneProviderRemainsAValidIntentionalFallback() {
        var actual = AdmissionProfileProviderGate.resolve(AdmissionProfileProvider.none(), PLAYER, POLICY, 1).join();
        assertEquals("none", actual.provider());
        assertTrue(actual.permissions().isEmpty());
    }

    @Test
    void explicitlyUnavailableProviderRemainsExceptional() {
        var future = AdmissionProfileProviderGate.resolve(
            AdmissionProfileProvider.unavailable("provider missing"), PLAYER, POLICY, 1);
        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void synchronousProviderFailureRemainsExceptional() {
        var future = AdmissionProfileProviderGate.resolve((player, policy) -> {
            throw new IllegalStateException("provider down");
        }, PLAYER, POLICY, 1);
        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void asynchronousProviderFailureRemainsExceptional() {
        var future = AdmissionProfileProviderGate.resolve(
            (player, policy) -> CompletableFuture.failedFuture(new IllegalStateException("storage down")),
            PLAYER, POLICY, 1);
        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void nullCompletionStageIsRejected() {
        var future = AdmissionProfileProviderGate.resolve((player, policy) -> null, PLAYER, POLICY, 1);
        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(NullPointerException.class, ex.getCause());
    }

    @Test
    void nullPermissionSnapshotIsRejected() {
        var future = AdmissionProfileProviderGate.resolve(
            (player, policy) -> CompletableFuture.completedFuture(null), PLAYER, POLICY, 1);
        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(NullPointerException.class, ex.getCause());
    }

    @Test
    void timeoutRemainsExceptionalInsteadOfProducingDefaultPermissions() {
        var never = new CompletableFuture<AdmissionPermissionSnapshot>();
        var future = AdmissionProfileProviderGate.resolve((player, policy) -> never, PLAYER, POLICY, 1);
        assertThrows(CompletionException.class, () -> future.orTimeout(2, TimeUnit.SECONDS).join());
        assertTrue(future.isCompletedExceptionally());
    }

    private static AdmissionPolicySnapshot policy() {
        AdmissionProfile defaults = new AdmissionProfile("default", 0, AdmissionPolicy.defaults(), new ModPolicy(
            ModPolicyMode.DENYLIST, OriginPolicyAction.DENY, OriginPolicyAction.DENY,
            Set.of("fabricloader", "cerberus"), Map.of(), Map.of()));
        return new AdmissionPolicySnapshot(1, "default", Map.of("default", defaults), Map.of());
    }
}
