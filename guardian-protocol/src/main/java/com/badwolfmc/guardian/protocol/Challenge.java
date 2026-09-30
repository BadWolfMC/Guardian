package com.badwolfmc.guardian.protocol;

import java.util.Arrays;
import java.util.Objects;

public record Challenge(
    int protocolVersion,
    long requiredCapabilities,
    byte[] nonce,
    GuardianChallengeAuthentication authentication
) {
    public Challenge {
        Objects.requireNonNull(nonce, "nonce");
        if (protocolVersion < 1 || protocolVersion > GuardianProtocol.MAX_PROTOCOL_VERSION) {
            throw new IllegalArgumentException("protocolVersion out of range");
        }
        if (nonce.length != GuardianProtocol.NONCE_BYTES) {
            throw new IllegalArgumentException("nonce must be exactly " + GuardianProtocol.NONCE_BYTES + " bytes");
        }
        boolean authenticated = (requiredCapabilities & GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE) != 0L;
        if (authenticated != (authentication != null)) {
            throw new IllegalArgumentException(
                "authenticated Guardian challenge capability and authentication proof must agree");
        }
        nonce = Arrays.copyOf(nonce, nonce.length);
    }

    public Challenge(int protocolVersion, long requiredCapabilities, byte[] nonce) {
        this(protocolVersion, requiredCapabilities, nonce, null);
    }

    @Override public byte[] nonce() { return Arrays.copyOf(nonce, nonce.length); }
}
