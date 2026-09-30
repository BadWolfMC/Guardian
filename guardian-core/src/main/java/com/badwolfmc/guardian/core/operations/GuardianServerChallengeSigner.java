package com.badwolfmc.guardian.core.operations;

import com.badwolfmc.guardian.protocol.Challenge;
import com.badwolfmc.guardian.protocol.GuardianChallengeAuthentication;
import com.badwolfmc.guardian.protocol.GuardianChallengeCrypto;
import com.badwolfmc.guardian.protocol.GuardianProtocol;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Immutable server-held signer for Guardian -> Cerberus challenge authentication. */
public final class GuardianServerChallengeSigner {
    private final PrivateKey privateKey;
    private final byte[] privateKeyFingerprint;
    private final Clock clock;

    GuardianServerChallengeSigner(PrivateKey privateKey, Clock clock) {
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
        this.clock = Objects.requireNonNull(clock, "clock");
        byte[] encoded = privateKey.getEncoded();
        if (encoded == null || encoded.length == 0) {
            throw new IllegalArgumentException("Guardian server authentication key is not encodable");
        }
        try {
            this.privateKeyFingerprint = MessageDigest.getInstance("SHA-256").digest(encoded);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    public Challenge authenticatedChallenge(
        int protocolVersion,
        long requiredCapabilities,
        byte[] nonce,
        UUID playerId
    ) {
        Objects.requireNonNull(playerId, "playerId");
        long capabilities = requiredCapabilities | GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE;
        long issuedAt = clock.millis();
        long expiresAt;
        try {
            expiresAt = Math.addExact(issuedAt, GuardianProtocol.GUARDIAN_CHALLENGE_AUTH_TTL_MILLIS);
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("Guardian challenge authentication clock overflow", ex);
        }
        byte[] message = GuardianChallengeCrypto.signingMessage(
            protocolVersion, capabilities, nonce, playerId, issuedAt, expiresAt);
        final byte[] signature;
        try {
            Signature signer = Signature.getInstance(GuardianChallengeCrypto.ALGORITHM);
            signer.initSign(privateKey);
            signer.update(message);
            signature = signer.sign();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("could not sign Guardian server challenge", ex);
        }
        if (signature.length != GuardianProtocol.GUARDIAN_CHALLENGE_SIGNATURE_BYTES) {
            throw new IllegalStateException("unexpected Ed25519 Guardian challenge signature length " + signature.length);
        }
        return new Challenge(
            protocolVersion,
            capabilities,
            nonce,
            new GuardianChallengeAuthentication(playerId, issuedAt, expiresAt, signature)
        );
    }

    public boolean sameKey(GuardianServerChallengeSigner other) {
        return other != null && MessageDigest.isEqual(privateKeyFingerprint, other.privateKeyFingerprint);
    }
}
