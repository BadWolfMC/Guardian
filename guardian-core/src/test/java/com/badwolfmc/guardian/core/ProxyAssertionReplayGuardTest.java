package com.badwolfmc.guardian.core;

import com.badwolfmc.guardian.protocol.GuardianProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProxyAssertionReplayGuardTest {
    @Test
    void rejectsExactReplayUntilAuthenticatedExpiry() {
        ProxyAssertionReplayGuard guard = new ProxyAssertionReplayGuard(4);
        byte[] payload = payload(1);

        assertEquals(ProxyAssertionReplayGuard.Result.ACCEPTED, guard.record(payload, 2_000L, 1_000L));
        assertEquals(ProxyAssertionReplayGuard.Result.REPLAYED, guard.record(payload, 2_000L, 1_001L));
    }

    @Test
    void differentAuthenticatedSignaturesRemainIndependent() {
        ProxyAssertionReplayGuard guard = new ProxyAssertionReplayGuard(4);
        assertEquals(ProxyAssertionReplayGuard.Result.ACCEPTED, guard.record(payload(1), 2_000L, 1_000L));
        assertEquals(ProxyAssertionReplayGuard.Result.ACCEPTED, guard.record(payload(2), 2_000L, 1_000L));
    }

    @Test
    void expiredEntriesArePrunedBeforeCapacityCheck() {
        ProxyAssertionReplayGuard guard = new ProxyAssertionReplayGuard(1);
        assertEquals(ProxyAssertionReplayGuard.Result.ACCEPTED, guard.record(payload(1), 1_100L, 1_000L));
        assertEquals(0, guard.sizeForTest(1_100L));
        assertEquals(ProxyAssertionReplayGuard.Result.ACCEPTED, guard.record(payload(2), 1_300L, 1_100L));
    }

    @Test
    void capacityFailsClosedInsteadOfGrowingWithoutBound() {
        ProxyAssertionReplayGuard guard = new ProxyAssertionReplayGuard(1);
        assertEquals(ProxyAssertionReplayGuard.Result.ACCEPTED, guard.record(payload(1), 2_000L, 1_000L));
        assertEquals(ProxyAssertionReplayGuard.Result.CAPACITY_EXCEEDED,
            guard.record(payload(2), 2_000L, 1_000L));
    }

    @Test
    void malformedOrExpiredInputsAreRejected() {
        ProxyAssertionReplayGuard guard = new ProxyAssertionReplayGuard(1);
        assertThrows(IllegalArgumentException.class,
            () -> guard.record(new byte[GuardianProtocol.PROXY_HMAC_BYTES], 2_000L, 1_000L));
        assertThrows(IllegalArgumentException.class,
            () -> guard.record(payload(1), 1_000L, 1_000L));
    }

    private static byte[] payload(int signatureSeed) {
        byte[] payload = new byte[64];
        payload[payload.length - 1] = (byte) signatureSeed;
        return payload;
    }
}
