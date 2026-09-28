package com.badwolfmc.guardian.core.operations;

import com.badwolfmc.guardian.protocol.GuardianProtocol;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable-in-practice wrapper that never exposes its backing key bytes directly. */
public final class ProxyAssertionSecret {
    private final byte[] bytes;
    private final String sourceDescription;
    private final String fingerprint;

    public ProxyAssertionSecret(byte[] bytes, String sourceDescription) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != GuardianProtocol.PROXY_SECRET_BYTES) {
            throw new IllegalArgumentException("proxy assertion secret must be exactly "
                + GuardianProtocol.PROXY_SECRET_BYTES + " bytes");
        }
        this.sourceDescription = Objects.requireNonNull(sourceDescription, "sourceDescription");
        this.bytes = bytes.clone();
        this.fingerprint = fingerprint(this.bytes);
    }

    public byte[] copyBytes() {
        return bytes.clone();
    }

    public String sourceDescription() {
        return sourceDescription;
    }

    /** Non-secret identifier useful for confirming that proxy/backends loaded the same key. */
    public String fingerprint() {
        return fingerprint;
    }

    private static String fingerprint(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
