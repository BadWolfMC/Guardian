package com.badwolfmc.guardian.core;

import com.badwolfmc.guardian.core.policy.CerberusReleaseTrust;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.ManifestCanonicalizer;
import com.badwolfmc.guardian.protocol.Response;

import java.util.Arrays;
import java.util.Objects;

public final class ProtocolV1ResponseValidator {
    private ProtocolV1ResponseValidator() {}

    public static GuardianDecision validate(byte[] expectedNonce, Response response) {
        return validate(expectedNonce, response, CerberusReleaseTrust.disabled());
    }

    public static GuardianDecision validate(
        byte[] expectedNonce,
        Response response,
        CerberusReleaseTrust releaseTrust
    ) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(releaseTrust, "releaseTrust");
        if (expectedNonce == null || expectedNonce.length != GuardianProtocol.NONCE_BYTES) {
            return GuardianDecision.deny(
                DecisionReason.CONFIGURATION_ERROR, "server challenge nonce has invalid length");
        }
        if (response.protocolVersion() != GuardianProtocol.VERSION) {
            return GuardianDecision.deny(
                DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
                "client protocol=" + response.protocolVersion() + ", server protocol=" + GuardianProtocol.VERSION);
        }
        if (GuardianProtocol.hasUnknownCapabilities(response.capabilities())) {
            return GuardianDecision.deny(
                DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
                "Cerberus advertised unknown protocol-v1 capability bits 0x"
                    + Long.toHexString(response.capabilities() & ~GuardianProtocol.KNOWN_CAPABILITIES));
        }
        if (!GuardianProtocol.supportsProtocolV1Capabilities(response.capabilities())) {
            return GuardianDecision.deny(
                DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
                "Cerberus lacks required protocol-v1 capabilities");
        }
        if (releaseTrust.required()
            && (response.capabilities() & GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE) == 0L) {
            return GuardianDecision.deny(
                DecisionReason.CERBERUS_RELEASE_REQUIRED,
                "Admission policy requires an official signed Cerberus release identity");
        }
        if (response.capabilities() != response.manifest().capabilities()) {
            return GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "response/manifest capability mismatch");
        }
        if (!Arrays.equals(expectedNonce, response.nonce())) {
            return GuardianDecision.deny(DecisionReason.MANIFEST_INVALID, "challenge nonce mismatch");
        }
        try {
            ManifestCanonicalizer.validateCanonical(response.manifest());
        } catch (IllegalArgumentException ex) {
            return GuardianDecision.deny(DecisionReason.MANIFEST_INVALID, ex.getMessage());
        }

        CerberusReleaseTrust.Verification release = releaseTrust.verify(
            response.cerberusReleaseIdentity(), response.manifest().cerberusVersion());
        if (release == CerberusReleaseTrust.Verification.MISSING_IDENTITY) {
            return GuardianDecision.deny(
                DecisionReason.CERBERUS_RELEASE_REQUIRED,
                "Admission policy requires signed Cerberus release identity metadata");
        }
        if (release == CerberusReleaseTrust.Verification.VERSION_MISMATCH) {
            return GuardianDecision.deny(
                DecisionReason.CERBERUS_RELEASE_UNTRUSTED,
                "signed Cerberus release version does not match the reported manifest version");
        }
        if (release == CerberusReleaseTrust.Verification.UNTRUSTED_SIGNATURE) {
            return GuardianDecision.deny(
                DecisionReason.CERBERUS_RELEASE_UNTRUSTED,
                "Cerberus release identity did not verify against any trusted Ed25519 release key");
        }

        return GuardianDecision.allow(
            DecisionReason.CERBERUS_VERIFIED,
            "Cerberus protocol-v1 response and canonical manifest validated ("
                + response.manifest().entries().size() + " mods)"
                + (release == CerberusReleaseTrust.Verification.VERIFIED ? ", signed release verified" : ""));
    }
}
