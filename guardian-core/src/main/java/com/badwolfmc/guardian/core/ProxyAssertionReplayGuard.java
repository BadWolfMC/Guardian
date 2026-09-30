package com.badwolfmc.guardian.core;

import com.badwolfmc.guardian.protocol.GuardianProtocol;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded in-memory replay guard for already HMAC-verified proxy assertion payloads.
 *
 * <p>The final HMAC bytes are a compact authenticated fingerprint of the complete assertion. The
 * guard deliberately records only short-lived verifier state; it does not persist player history or
 * assertion contents.</p>
 */
public final class ProxyAssertionReplayGuard {
    public enum Result {
        ACCEPTED,
        REPLAYED,
        CAPACITY_EXCEEDED
    }

    private final int maxEntries;
    private final Map<SignatureKey, Long> seenUntil = new HashMap<>();

    public ProxyAssertionReplayGuard(int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    /**
     * Records one verified assertion payload until its authenticated expiry.
     *
     * <p>Callers must invoke this only after HMAC and metadata validation succeeds.</p>
     */
    public synchronized Result record(byte[] verifiedPayload, long expiresAtEpochMillis, long nowEpochMillis) {
        Objects.requireNonNull(verifiedPayload, "verifiedPayload");
        if (verifiedPayload.length <= GuardianProtocol.PROXY_HMAC_BYTES) {
            throw new IllegalArgumentException("verified proxy assertion payload is truncated");
        }
        if (expiresAtEpochMillis <= nowEpochMillis) {
            throw new IllegalArgumentException("verified proxy assertion is already expired");
        }

        pruneExpired(nowEpochMillis);
        SignatureKey key = SignatureKey.fromPayload(verifiedPayload);
        Long existingExpiry = seenUntil.get(key);
        if (existingExpiry != null && existingExpiry > nowEpochMillis) {
            return Result.REPLAYED;
        }
        if (seenUntil.size() >= maxEntries) {
            return Result.CAPACITY_EXCEEDED;
        }
        seenUntil.put(key, expiresAtEpochMillis);
        return Result.ACCEPTED;
    }

    public synchronized void clear() {
        seenUntil.clear();
    }

    synchronized int sizeForTest(long nowEpochMillis) {
        pruneExpired(nowEpochMillis);
        return seenUntil.size();
    }

    private void pruneExpired(long nowEpochMillis) {
        Iterator<Map.Entry<SignatureKey, Long>> iterator = seenUntil.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= nowEpochMillis) iterator.remove();
        }
    }

    private static final class SignatureKey {
        private final byte[] bytes;
        private final int hashCode;

        private SignatureKey(byte[] bytes) {
            this.bytes = bytes;
            this.hashCode = Arrays.hashCode(bytes);
        }

        static SignatureKey fromPayload(byte[] payload) {
            int start = payload.length - GuardianProtocol.PROXY_HMAC_BYTES;
            return new SignatureKey(Arrays.copyOfRange(payload, start, payload.length));
        }

        @Override
        public boolean equals(Object other) {
            return this == other || (other instanceof SignatureKey key && Arrays.equals(bytes, key.bytes));
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
