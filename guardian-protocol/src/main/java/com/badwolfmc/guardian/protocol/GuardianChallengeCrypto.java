package com.badwolfmc.guardian.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Domain-separated public-key verification for authenticated Guardian challenges. */
public final class GuardianChallengeCrypto {
    public static final String ALGORITHM = "Ed25519";
    private static final byte[] DOMAIN = "BadWolfMC Guardian server challenge v1"
        .getBytes(StandardCharsets.US_ASCII);

    private GuardianChallengeCrypto() {}

    public static byte[] signingMessage(
        int protocolVersion,
        long requiredCapabilities,
        byte[] nonce,
        UUID playerId,
        long issuedAtEpochMillis,
        long expiresAtEpochMillis
    ) {
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(playerId, "playerId");
        if (nonce.length != GuardianProtocol.NONCE_BYTES) {
            throw new IllegalArgumentException("nonce must be exactly " + GuardianProtocol.NONCE_BYTES + " bytes");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(DOMAIN.length + 80);
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeShort(DOMAIN.length);
                out.write(DOMAIN);
                out.writeShort(protocolVersion);
                out.writeLong(requiredCapabilities);
                out.write(nonce);
                out.writeLong(playerId.getMostSignificantBits());
                out.writeLong(playerId.getLeastSignificantBits());
                out.writeLong(issuedAtEpochMillis);
                out.writeLong(expiresAtEpochMillis);
            }
            return buffer.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory Guardian challenge signing message failed", impossible);
        }
    }

    public static boolean verify(
        List<PublicKey> trustedKeys,
        Challenge challenge,
        UUID expectedPlayerId,
        Clock clock
    ) {
        Objects.requireNonNull(trustedKeys, "trustedKeys");
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(expectedPlayerId, "expectedPlayerId");
        Objects.requireNonNull(clock, "clock");
        GuardianChallengeAuthentication auth = challenge.authentication();
        if (auth == null || trustedKeys.isEmpty() || !expectedPlayerId.equals(auth.playerId())) return false;

        long issued = auth.issuedAtEpochMillis();
        long expires = auth.expiresAtEpochMillis();
        long lifetime;
        try {
            lifetime = Math.subtractExact(expires, issued);
        } catch (ArithmeticException ex) {
            return false;
        }
        if (lifetime <= 0 || lifetime > GuardianProtocol.GUARDIAN_CHALLENGE_AUTH_TTL_MILLIS) return false;

        long now = clock.millis();
        long latestAcceptableIssue;
        long earliestAcceptableExpiry;
        try {
            latestAcceptableIssue = Math.addExact(now, GuardianProtocol.GUARDIAN_CHALLENGE_AUTH_CLOCK_SKEW_MILLIS);
            earliestAcceptableExpiry = Math.subtractExact(now, GuardianProtocol.GUARDIAN_CHALLENGE_AUTH_CLOCK_SKEW_MILLIS);
        } catch (ArithmeticException ex) {
            return false;
        }
        if (issued > latestAcceptableIssue || expires <= earliestAcceptableExpiry) return false;

        byte[] message = signingMessage(
            challenge.protocolVersion(), challenge.requiredCapabilities(), challenge.nonce(),
            auth.playerId(), issued, expires);
        byte[] signature = auth.ed25519Signature();
        for (PublicKey key : trustedKeys) {
            if (verifyOne(key, message, signature)) return true;
        }
        return false;
    }

    private static boolean verifyOne(PublicKey key, byte[] message, byte[] signature) {
        if (key == null) return false;
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(key);
            verifier.update(message);
            return verifier.verify(signature);
        } catch (GeneralSecurityException ex) {
            return false;
        }
    }

    /** Decodes a Base64 X.509 SubjectPublicKeyInfo Ed25519 public key. */
    public static PublicKey decodePublicKey(String base64) throws GeneralSecurityException {
        Objects.requireNonNull(base64, "base64");
        byte[] encoded;
        try {
            encoded = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new GeneralSecurityException("public key is not valid Base64", ex);
        }
        if (encoded.length < 32 || encoded.length > 128) {
            throw new GeneralSecurityException("unexpected Ed25519 X.509 public-key length " + encoded.length);
        }
        PublicKey key = KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(encoded));
        if (!isEd25519KeyAlgorithm(key.getAlgorithm())) {
            throw new GeneralSecurityException("decoded public key is not Ed25519");
        }
        return key;
    }

    public static boolean isEd25519KeyAlgorithm(String algorithm) {
        return "Ed25519".equalsIgnoreCase(algorithm) || "EdDSA".equalsIgnoreCase(algorithm);
    }
}
