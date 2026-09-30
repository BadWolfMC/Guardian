package com.badwolfmc.guardian.protocol;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Server-authentication proof attached to a Guardian challenge. */
public record GuardianChallengeAuthentication(
    UUID playerId,
    long issuedAtEpochMillis,
    long expiresAtEpochMillis,
    byte[] ed25519Signature
) {
    public GuardianChallengeAuthentication {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(ed25519Signature, "ed25519Signature");
        if (expiresAtEpochMillis <= issuedAtEpochMillis) {
            throw new IllegalArgumentException("challenge authentication expiry must follow issue time");
        }
        if (ed25519Signature.length != GuardianProtocol.GUARDIAN_CHALLENGE_SIGNATURE_BYTES) {
            throw new IllegalArgumentException(
                "Guardian challenge signature must be exactly "
                    + GuardianProtocol.GUARDIAN_CHALLENGE_SIGNATURE_BYTES + " bytes");
        }
        ed25519Signature = Arrays.copyOf(ed25519Signature, ed25519Signature.length);
    }

    @Override
    public byte[] ed25519Signature() {
        return Arrays.copyOf(ed25519Signature, ed25519Signature.length);
    }
}
