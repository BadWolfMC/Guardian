package com.badwolfmc.cerberus;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6SignedReleaseArchitectureTest {
    @Test
    void clientAdvertisesSignedCapabilityOnlyFromSelfCheckedReleaseMetadata() throws Exception {
        String client = Files.readString(Path.of("src/main/java/com/badwolfmc/cerberus/CerberusClient.java"));
        String provider = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/cerberus/release/CerberusReleaseIdentityProvider.java"));
        String runtimeCrypto = Files.readString(Path.of(
            "../guardian-protocol/src/main/java/com/badwolfmc/guardian/protocol/CerberusReleaseCrypto.java"));
        assertTrue(client.contains("CAP_SIGNED_CERBERUS_RELEASE"));
        assertTrue(client.contains("CerberusReleaseIdentityProvider.current()"));
        assertTrue(provider.contains("CerberusReleaseArtifact.canonicalDigest(jar).equals(identity.canonicalDigest())"));
        assertFalse(provider.contains("PrivateKey"));
        assertFalse(runtimeCrypto.contains("PrivateKey"));
        assertFalse(runtimeCrypto.contains("PKCS8EncodedKeySpec"));
    }

    @Test
    void releasePrivateKeyIsConsumedOnlyByOfflineReleaseTool() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        String signer = Files.readString(Path.of(
            "src/releaseTool/java/com/badwolfmc/cerberus/release/CerberusReleaseSigner.java"));
        assertTrue(build.contains("cerberusReleasePrivateKey"));
        assertTrue(build.contains("signCerberusRelease"));
        assertTrue(build.contains("dependsOn tasks.named('releaseToolClasses')"));
        assertTrue(build.contains("cerberusUnsignedJar"),
            "offline signing must consume an explicit CI-built unsigned artifact");
        assertFalse(build.contains("dependsOn tasks.named('jar'), tasks.named('releaseToolClasses')"),
            "offline signing must not silently rebuild the unsigned Cerberus JAR");
        assertFalse(build.contains("def unsignedJar = tasks.named('jar').get().archiveFile.get().asFile"));
        assertTrue(build.contains("metadata.version != project.version.toString()"),
            "the signer task must reject a CI artifact whose Fabric version does not match the requested release");
        assertTrue(build.contains("verifyCerberusRelease"));
        assertFalse(build.contains("tasks.named('remapJar')"),
            "Minecraft 26.1+ non-obfuscated Loom does not provide remapJar");
        assertTrue(signer.contains("PKCS8EncodedKeySpec"));
        assertTrue(signer.contains("Offline release-build tool"));
        assertTrue(signer.contains("LinkOption.NOFOLLOW_LINKS"));
        assertTrue(signer.contains("Files.createTempFile"));
        assertTrue(signer.contains("copyStableUnsignedJar(input, temporary)"),
            "the signer must snapshot the exact checked unsigned bytes before signing them");
        assertTrue(signer.contains("StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS"));
        assertTrue(signer.contains("unsigned Cerberus input changed while it was being copied for signing"));
        assertTrue(signer.contains("CerberusReleaseArtifact.readIdentity(temporary)"),
            "preflight release metadata checks must apply to the stable snapshot that will be signed");
        assertTrue(signer.contains("containsEntry(temporary, GuardianChallengeTrustAnchors.ENTRY_NAME)"));
        assertTrue(signer.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(signer.contains("refusing to overwrite existing signed output"));
        assertTrue(signer.contains("missing END PRIVATE KEY"),
            "malformed PEM signing keys must fail explicitly instead of falling through to opaque key parsing");
        assertTrue(signer.contains("PEM body is not valid Base64"));
    }
}
