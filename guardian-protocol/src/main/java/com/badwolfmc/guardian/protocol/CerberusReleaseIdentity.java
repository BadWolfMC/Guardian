package com.badwolfmc.guardian.protocol;

import java.util.Arrays;
import java.util.Objects;

/**
 * Client-reported signed identity for an official Cerberus release artifact.
 *
 * <p>The signature authenticates the canonical release digest and release version against a
 * server-trusted Ed25519 public key. It does not prove that a hostile client is executing the
 * signed bytes.</p>
 */
public record CerberusReleaseIdentity(
    String releaseVersion,
    ArtifactSha256 canonicalDigest,
    byte[] ed25519Signature
) {
    public static final int ED25519_SIGNATURE_BYTES = 64;

    public CerberusReleaseIdentity {
        Objects.requireNonNull(releaseVersion, "releaseVersion");
        Objects.requireNonNull(canonicalDigest, "canonicalDigest");
        Objects.requireNonNull(ed25519Signature, "ed25519Signature");
        ProtocolText.validate(releaseVersion, GuardianProtocol.MAX_RELEASE_METADATA_BYTES,
            "Cerberus signed release version");
        if (ed25519Signature.length != ED25519_SIGNATURE_BYTES) {
            throw new IllegalArgumentException(
                "Ed25519 signature must be exactly " + ED25519_SIGNATURE_BYTES + " bytes");
        }
        ed25519Signature = Arrays.copyOf(ed25519Signature, ed25519Signature.length);
    }

    @Override
    public byte[] ed25519Signature() {
        return Arrays.copyOf(ed25519Signature, ed25519Signature.length);
    }
}
