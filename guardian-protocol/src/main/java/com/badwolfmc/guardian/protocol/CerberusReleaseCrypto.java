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
import java.util.Base64;
import java.util.Objects;

/** Domain-separated Ed25519 signing/verification for Cerberus release identity metadata. */
public final class CerberusReleaseCrypto {
    public static final String ALGORITHM = "Ed25519";
    private static final byte[] DOMAIN = "BadWolfMC Guardian Cerberus release identity v1"
        .getBytes(StandardCharsets.US_ASCII);

    private CerberusReleaseCrypto() {}

    public static byte[] signingMessage(String releaseVersion, ArtifactSha256 canonicalDigest) {
        Objects.requireNonNull(releaseVersion, "releaseVersion");
        Objects.requireNonNull(canonicalDigest, "canonicalDigest");
        byte[] versionBytes = ProtocolText.encode(
            releaseVersion, GuardianProtocol.MAX_RELEASE_METADATA_BYTES, "Cerberus signed release version");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(
                DOMAIN.length + 2 + versionBytes.length + ArtifactSha256.BYTES + 8);
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeShort(DOMAIN.length);
                out.write(DOMAIN);
                out.writeShort(versionBytes.length);
                out.write(versionBytes);
                out.write(canonicalDigest.bytes());
            }
            return buffer.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory release signing message failed", impossible);
        }
    }

    public static boolean verify(PublicKey publicKey, CerberusReleaseIdentity identity) {
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(identity, "identity");
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(signingMessage(identity.releaseVersion(), identity.canonicalDigest()));
            return verifier.verify(identity.ed25519Signature());
        } catch (GeneralSecurityException ex) {
            return false;
        }
    }

    /** Decodes a base64 X.509 SubjectPublicKeyInfo Ed25519 public key. */
    public static PublicKey decodePublicKey(String base64) throws GeneralSecurityException {
        Objects.requireNonNull(base64, "base64");
        byte[] encoded;
        try {
            encoded = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new GeneralSecurityException("public key is not valid base64", ex);
        }
        if (encoded.length < 32 || encoded.length > 128) {
            throw new GeneralSecurityException("unexpected Ed25519 X.509 public-key length " + encoded.length);
        }
        PublicKey key = KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(encoded));
        if (!GuardianChallengeCrypto.isEd25519KeyAlgorithm(key.getAlgorithm())) {
            throw new GeneralSecurityException("decoded public key is not Ed25519");
        }
        return key;
    }
}
