package com.badwolfmc.cerberus;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6GuardianServerAuthenticationArchitectureTest {
    @Test
    void pinnedCerberusVerifiesChallengeBeforeManifestDisclosure() throws Exception {
        String client = Files.readString(Path.of("src/main/java/com/badwolfmc/cerberus/CerberusClient.java"));
        int trustCheck = client.indexOf("GuardianServerTrustStore.verify(challenge, localPlayerId)");
        int collectManifest = client.indexOf("FabricManifestCollector.collect(capabilities)");
        assertTrue(trustCheck >= 0, "Cerberus must verify the Guardian challenge");
        assertTrue(collectManifest > trustCheck, "manifest collection/disclosure must happen only after server authentication");
        assertTrue(client.contains("context.client().getUser().getProfileId()"),
            "challenge verification must bind to the local authenticated session UUID");
        assertTrue(client.contains("CAP_AUTHENTICATED_GUARDIAN_CHALLENGE"));
    }

    @Test
    void serverAuthenticationUsesPinnedPublicKeysAndNoClientSecret() throws Exception {
        String trustStore = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/cerberus/trust/GuardianServerTrustStore.java"));
        String build = Files.readString(Path.of("build.gradle"));
        String runtimeTree = Files.readString(Path.of(
            "../guardian-protocol/src/main/java/com/badwolfmc/guardian/protocol/GuardianChallengeCrypto.java"));

        assertTrue(trustStore.contains("GuardianChallengeTrustAnchors.ENTRY_NAME"));
        assertTrue(trustStore.contains("getModContainer(\"cerberus\")"),
            "trust anchors must be resolved from Cerberus's own Fabric ModContainer");
        assertTrue(trustStore.contains("cerberus.findPath(ENTRY_NAME)"));
        assertFalse(trustStore.contains("getResourceAsStream"),
            "a shared classloader resource lookup could resolve a colliding resource from another mod");
        assertTrue(build.contains("guardianServerAuthPublicKeys"));
        assertTrue(build.contains("generateGuardianServerIdentity"));
        assertFalse(trustStore.contains("PrivateKey"));
        assertFalse(runtimeTree.contains("PrivateKey"));
        assertFalse(runtimeTree.contains("PKCS8EncodedKeySpec"));
    }

    @Test
    void embeddedTrustAnchorReadIsProviderAwareNoFollowAndStable() throws Exception {
        String trustStore = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/cerberus/trust/GuardianServerTrustStore.java"));

        assertTrue(trustStore.contains("openReadChannel(resource)"));
        assertTrue(trustStore.contains("provider().getScheme()"),
            "embedded archive resources need provider-aware channel options");
        assertTrue(trustStore.contains("StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS"),
            "ordinary filesystem resources must retain no-follow descriptor semantics");
        assertTrue(trustStore.contains("sameSnapshot(attributes, after)"),
            "trust-anchor identity/size/mtime must be rechecked after the read");
        assertFalse(trustStore.contains("Files.newInputStream(resource)"));
    }

    @Test
    void releaseSignerEmbedsTrustAnchorsBeforeSigningCanonicalRelease() throws Exception {
        String signer = normalizeNewlines(Files.readString(Path.of(
            "src/releaseTool/java/com/badwolfmc/cerberus/release/CerberusReleaseSigner.java")));
        int trustInjection = signer.indexOf("injectEntry(\n                    temporary,\n                    GuardianChallengeTrustAnchors.ENTRY_NAME");
        int canonicalDigest = signer.indexOf("CerberusReleaseArtifact.canonicalDigest");
        assertTrue(trustInjection >= 0);
        assertTrue(canonicalDigest > trustInjection,
            "server-auth trust anchors must be part of the canonical contents covered by release signing");
        assertTrue(signer.contains("GuardianChallengeTrustAnchors.canonicalText"));
    }

    private static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
