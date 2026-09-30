package com.badwolfmc.guardian.core.policy;

import com.badwolfmc.guardian.protocol.CerberusReleaseCrypto;
import com.badwolfmc.guardian.protocol.CerberusReleaseIdentity;
import com.badwolfmc.guardian.protocol.GuardianProtocol;

import java.security.PublicKey;
import java.util.List;
import java.util.Objects;

/** Shared Admission trust policy for optional official signed Cerberus releases. */
public final class CerberusReleaseTrust {
    public enum Verification {
        VERIFIED,
        NOT_REQUIRED,
        MISSING_IDENTITY,
        VERSION_MISMATCH,
        UNTRUSTED_SIGNATURE
    }

    private final boolean required;
    private final List<PublicKey> trustedEd25519Keys;

    public CerberusReleaseTrust(boolean required, List<PublicKey> trustedEd25519Keys) {
        this.required = required;
        this.trustedEd25519Keys = List.copyOf(Objects.requireNonNull(trustedEd25519Keys, "trustedEd25519Keys"));
        if (required && this.trustedEd25519Keys.isEmpty()) {
            throw new IllegalArgumentException("signed Cerberus release is required but no trusted Ed25519 key is configured");
        }
    }

    public static CerberusReleaseTrust disabled() {
        return new CerberusReleaseTrust(false, List.of());
    }

    public boolean required() {
        return required;
    }

    public List<PublicKey> trustedEd25519Keys() {
        return trustedEd25519Keys;
    }

    public long requiredCapabilities() {
        return GuardianProtocol.REQUIRED_CAPABILITIES
            | (required ? GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE : 0L);
    }

    public Verification verify(CerberusReleaseIdentity identity, String manifestCerberusVersion) {
        Objects.requireNonNull(manifestCerberusVersion, "manifestCerberusVersion");
        if (!required) return Verification.NOT_REQUIRED;
        if (identity == null) return Verification.MISSING_IDENTITY;
        if (!manifestCerberusVersion.equals(identity.releaseVersion())) return Verification.VERSION_MISMATCH;
        for (PublicKey key : trustedEd25519Keys) {
            if (CerberusReleaseCrypto.verify(key, identity)) return Verification.VERIFIED;
        }
        return Verification.UNTRUSTED_SIGNATURE;
    }
}
