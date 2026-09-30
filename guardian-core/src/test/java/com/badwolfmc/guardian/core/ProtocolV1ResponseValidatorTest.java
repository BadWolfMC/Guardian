package com.badwolfmc.guardian.core;

import com.badwolfmc.guardian.core.policy.CerberusReleaseTrust;
import com.badwolfmc.guardian.protocol.ArtifactSha256;
import com.badwolfmc.guardian.protocol.CerberusReleaseCrypto;
import com.badwolfmc.guardian.protocol.CerberusReleaseIdentity;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Manifest;
import com.badwolfmc.guardian.protocol.ManifestCanonicalizer;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.OriginKind;
import com.badwolfmc.guardian.protocol.Response;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolV1ResponseValidatorTest {
    @Test
    void acceptsCanonicalManifest() {
        assertEquals(
            DecisionReason.CERBERUS_VERIFIED,
            ProtocolV1ResponseValidator.validate(nonce(), response(nonce(), GuardianProtocol.REQUIRED_CAPABILITIES)).reason()
        );
    }

    @Test
    void rejectsNonceMismatch() {
        byte[] wrong = nonce();
        wrong[0] = 9;
        assertEquals(
            DecisionReason.MANIFEST_INVALID,
            ProtocolV1ResponseValidator.validate(nonce(), response(wrong, GuardianProtocol.REQUIRED_CAPABILITIES)).reason()
        );
    }

    @Test
    void rejectsMissingArtifactHashCapability() {
        long oldCapabilities = GuardianProtocol.CAP_CANONICAL_MANIFEST_V1
            | GuardianProtocol.CAP_CONTAINMENT_RELATIONSHIPS
            | GuardianProtocol.CAP_ORIGIN_KIND;
        assertEquals(
            DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
            ProtocolV1ResponseValidator.validate(nonce(), response(nonce(), oldCapabilities)).reason()
        );
    }


    @Test
    void rejectsUnknownProtocolV1CapabilityBits() {
        long unknown = GuardianProtocol.REQUIRED_CAPABILITIES | (1L << 40);
        assertEquals(
            DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
            ProtocolV1ResponseValidator.validate(nonce(), response(nonce(), unknown)).reason()
        );
    }

    @Test
    void signedReleaseRequirementAcceptsTrustedIdentityAndRejectsUnsignedOrWrongKey() throws Exception {
        KeyPair trusted = KeyPairGenerator.getInstance(CerberusReleaseCrypto.ALGORITHM).generateKeyPair();
        KeyPair wrong = KeyPairGenerator.getInstance(CerberusReleaseCrypto.ALGORITHM).generateKeyPair();
        CerberusReleaseTrust trust = new CerberusReleaseTrust(true, List.of(trusted.getPublic()));
        long capabilities = GuardianProtocol.REQUIRED_CAPABILITIES | GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE;
        ArtifactSha256 releaseDigest = ArtifactSha256.fromBytes(new byte[ArtifactSha256.BYTES]);
        CerberusReleaseIdentity identity = new CerberusReleaseIdentity(
            "x", releaseDigest, sign(trusted.getPrivate(), "x", releaseDigest));

        assertEquals(DecisionReason.CERBERUS_VERIFIED,
            ProtocolV1ResponseValidator.validate(nonce(), signedResponse(capabilities, identity), trust).reason());
        assertEquals(DecisionReason.CERBERUS_RELEASE_REQUIRED,
            ProtocolV1ResponseValidator.validate(nonce(), response(nonce(), GuardianProtocol.REQUIRED_CAPABILITIES), trust).reason());

        CerberusReleaseIdentity untrusted = new CerberusReleaseIdentity(
            "x", releaseDigest, sign(wrong.getPrivate(), "x", releaseDigest));
        assertEquals(DecisionReason.CERBERUS_RELEASE_UNTRUSTED,
            ProtocolV1ResponseValidator.validate(nonce(), signedResponse(capabilities, untrusted), trust).reason());
    }

    @Test
    void signedReleaseIdentityIsReusableMetadataNotRemoteAttestation() throws Exception {
        KeyPair trusted = KeyPairGenerator.getInstance(CerberusReleaseCrypto.ALGORITHM).generateKeyPair();
        CerberusReleaseTrust trust = new CerberusReleaseTrust(true, List.of(trusted.getPublic()));
        ArtifactSha256 releaseDigest = ArtifactSha256.fromBytes(new byte[ArtifactSha256.BYTES]);
        CerberusReleaseIdentity identity = new CerberusReleaseIdentity(
            "x", releaseDigest, sign(trusted.getPrivate(), "x", releaseDigest));
        long capabilities = GuardianProtocol.REQUIRED_CAPABILITIES | GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE;
        byte[] firstNonce = nonce();
        byte[] secondNonce = nonce();
        secondNonce[0] = 42;

        assertEquals(DecisionReason.CERBERUS_VERIFIED,
            ProtocolV1ResponseValidator.validate(firstNonce,
                signedResponse(capabilities, identity, firstNonce), trust).reason());
        assertEquals(DecisionReason.CERBERUS_VERIFIED,
            ProtocolV1ResponseValidator.validate(secondNonce,
                signedResponse(capabilities, identity, secondNonce), trust).reason());
    }

    @Test
    void signedReleaseRequirementBindsSignedVersionToManifestCerberusVersion() throws Exception {
        KeyPair trusted = KeyPairGenerator.getInstance(CerberusReleaseCrypto.ALGORITHM).generateKeyPair();
        CerberusReleaseTrust trust = new CerberusReleaseTrust(true, List.of(trusted.getPublic()));
        ArtifactSha256 releaseDigest = ArtifactSha256.fromBytes(new byte[ArtifactSha256.BYTES]);
        CerberusReleaseIdentity identity = new CerberusReleaseIdentity(
            "other", releaseDigest, sign(trusted.getPrivate(), "other", releaseDigest));
        long capabilities = GuardianProtocol.REQUIRED_CAPABILITIES | GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE;
        assertEquals(DecisionReason.CERBERUS_RELEASE_UNTRUSTED,
            ProtocolV1ResponseValidator.validate(nonce(), signedResponse(capabilities, identity), trust).reason());
    }

    @Test
    void rejectsNonCanonicalOrder() {
        Manifest manifest = new Manifest(
            "26.2",
            "0.19.5",
            "x",
            GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(archive("z", 1), archive("a", 2))
        );
        Response response = new Response(1, GuardianProtocol.REQUIRED_CAPABILITIES, nonce(), manifest);
        assertEquals(
            DecisionReason.MANIFEST_INVALID,
            ProtocolV1ResponseValidator.validate(nonce(), response).reason()
        );
    }

    @Test
    void rejectsUnsupportedResponseProtocol() {
        Response response = response(nonce(), GuardianProtocol.REQUIRED_CAPABILITIES);
        Response wrong = new Response(
            GuardianProtocol.VERSION + 1,
            response.capabilities(),
            response.nonce(),
            response.manifest()
        );
        assertEquals(
            DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
            ProtocolV1ResponseValidator.validate(nonce(), wrong).reason()
        );
    }

    private static Response response(byte[] nonce, long capabilities) {
        Manifest manifest = ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2",
            "0.19.5",
            "x",
            capabilities,
            List.of(archive("fabricloader", 1))
        ));
        return new Response(1, capabilities, nonce, manifest);
    }

    private static Response signedResponse(long capabilities, CerberusReleaseIdentity identity) {
        return signedResponse(capabilities, identity, nonce());
    }

    private static Response signedResponse(long capabilities, CerberusReleaseIdentity identity, byte[] nonce) {
        Manifest manifest = ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "0.19.5", "x", capabilities, List.of(archive("fabricloader", 1))));
        return new Response(1, capabilities, nonce, manifest, identity);
    }

    private static ManifestEntry archive(String id, int seed) {
        byte[] bytes = new byte[ArtifactSha256.BYTES];
        java.util.Arrays.fill(bytes, (byte) seed);
        return new ManifestEntry(id, "1", null, OriginKind.ARCHIVE, ArtifactSha256.fromBytes(bytes));
    }

    private static byte[] nonce() {
        return new byte[GuardianProtocol.NONCE_BYTES];
    }
    private static byte[] sign(PrivateKey privateKey, String version, ArtifactSha256 digest) throws Exception {
        Signature signer = Signature.getInstance(CerberusReleaseCrypto.ALGORITHM);
        signer.initSign(privateKey);
        signer.update(CerberusReleaseCrypto.signingMessage(version, digest));
        return signer.sign();
    }

}
