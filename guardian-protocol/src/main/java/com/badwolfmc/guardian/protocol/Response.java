package com.badwolfmc.guardian.protocol;

import java.util.Arrays;
import java.util.Objects;

public record Response(
    int protocolVersion,
    long capabilities,
    byte[] nonce,
    Manifest manifest,
    CerberusReleaseIdentity cerberusReleaseIdentity
) {
    public Response {
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(manifest, "manifest");
        if (protocolVersion < 1 || protocolVersion > GuardianProtocol.MAX_PROTOCOL_VERSION) {
            throw new IllegalArgumentException("protocolVersion out of range");
        }
        if (nonce.length != GuardianProtocol.NONCE_BYTES) {
            throw new IllegalArgumentException(
                "nonce must be exactly " + GuardianProtocol.NONCE_BYTES + " bytes");
        }
        boolean signedCapability = (capabilities & GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE) != 0L;
        if (signedCapability != (cerberusReleaseIdentity != null)) {
            throw new IllegalArgumentException(
                "signed Cerberus release capability and identity payload must agree");
        }
        nonce = Arrays.copyOf(nonce, nonce.length);
    }

    public Response(int protocolVersion, long capabilities, byte[] nonce, Manifest manifest) {
        this(protocolVersion, capabilities, nonce, manifest, null);
    }

    @Override
    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }
}
