package com.badwolfmc.guardian.core.operations;

import com.badwolfmc.guardian.core.BedrockEvidence;
import com.badwolfmc.guardian.core.BedrockSignal;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.core.policy.ProfileResolutionSource;
import com.badwolfmc.guardian.protocol.ArtifactSha256;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Manifest;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.OriginKind;
import com.badwolfmc.guardian.protocol.Presence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OperationsSupportTest {
    @TempDir
    Path tempDir;

    @Test
    void operationalLogLevelIsDeliberatelySmallAndCaseInsensitive() {
        assertEquals(OperationalLogLevel.NORMAL, OperationalLogLevel.parse("normal"));
        assertEquals(OperationalLogLevel.DEBUG, OperationalLogLevel.parse(" DEBUG "));
        assertFalse(OperationalLogLevel.NORMAL.debugEnabled());
        assertTrue(OperationalLogLevel.DEBUG.debugEnabled());
        assertThrows(IllegalArgumentException.class, () -> OperationalLogLevel.parse("TRACE"));
    }

    @Test
    void environmentSecretIsValidatedAndNeverExposedByReference() {
        byte[] key = new byte[32];
        key[0] = 42;
        String encoded = Base64.getEncoder().encodeToString(key);
        ProxyAssertionSecret secret = ProxyAssertionSecretResolver.resolve(
            tempDir, ProxyAssertionSecretSource.ENVIRONMENT, "GUARDIAN_TEST_SECRET", "ignored",
            Map.of("GUARDIAN_TEST_SECRET", encoded));

        assertEquals("environment:GUARDIAN_TEST_SECRET", secret.sourceDescription());
        assertEquals(16, secret.fingerprint().length());
        byte[] first = secret.copyBytes();
        first[0] = 0;
        assertEquals(42, secret.copyBytes()[0]);
    }

    @Test
    void proxyAssertionSecretEnforcesExactKeyLengthAtItsOwnBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new ProxyAssertionSecret(new byte[31], "test"));
        assertDoesNotThrow(() -> new ProxyAssertionSecret(new byte[GuardianProtocol.PROXY_SECRET_BYTES], "test"));
    }

    @Test
    void missingOrWrongLengthEnvironmentSecretFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> ProxyAssertionSecretResolver.resolve(
            tempDir, ProxyAssertionSecretSource.ENVIRONMENT, "MISSING", "ignored", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ProxyAssertionSecretResolver.resolve(
            tempDir, ProxyAssertionSecretSource.ENVIRONMENT, "BAD", "ignored",
            Map.of("BAD", Base64.getEncoder().encodeToString(new byte[31]))));
    }

    @Test
    void fileSecretMustRemainInsideDataDirectoryAndIsBounded() throws Exception {
        String encoded = Base64.getEncoder().encodeToString(new byte[32]);
        Files.writeString(tempDir.resolve("proxy-assertion.secret"), encoded, StandardCharsets.UTF_8);
        ProxyAssertionSecret secret = ProxyAssertionSecretResolver.resolve(
            tempDir, ProxyAssertionSecretSource.FILE, "ignored", "proxy-assertion.secret", Map.of());
        assertEquals("file:proxy-assertion.secret", secret.sourceDescription());

        assertThrows(IllegalArgumentException.class, () -> ProxyAssertionSecretResolver.resolve(
            tempDir, ProxyAssertionSecretSource.FILE, "ignored", "../outside.secret", Map.of()));

        Files.writeString(tempDir.resolve("too-large.secret"), "x".repeat(513), StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> ProxyAssertionSecretResolver.resolve(
            tempDir, ProxyAssertionSecretSource.FILE, "ignored", "too-large.secret", Map.of()));
    }

    @Test
    void activeInspectionSnapshotExposesOnlyPolicyAddressableTopLevelModsByDefault() {
        ArtifactSha256 hash = new ArtifactSha256("00".repeat(32));
        Manifest manifest = new Manifest("26.2", "0.18.6", "test", GuardianProtocol.REQUIRED_CAPABILITIES, List.of(
            new ManifestEntry("java", "25", null, OriginKind.BUILTIN),
            new ManifestEntry("sodium", "1.0", null, OriginKind.ARCHIVE, hash),
            new ManifestEntry("fabric-api", "1.0", null, OriginKind.ARCHIVE, hash),
            new ManifestEntry("fabric-api-base", "1.0", "fabric-api", OriginKind.NESTED)
        ));
        ActiveInspectionSnapshot snapshot = snapshot(UUID.randomUUID(), manifest);

        assertEquals(4, snapshot.loaderKnownCount());
        assertEquals(List.of("fabric-api", "sodium"), snapshot.policyAddressableMods().stream()
            .map(ManifestEntry::modId).sorted().toList());
    }

    @Test
    void activeInspectionStoreIsBoundedButAllowsReplacement() {
        ActiveInspectionStore store = new ActiveInspectionStore(1);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(store.put(snapshot(first, null)));
        assertTrue(store.put(snapshot(first, null).withBackend("beta")));
        assertFalse(store.put(snapshot(second, null)));
        assertEquals("beta", store.get(first).orElseThrow().backend());
        store.remove(first);
        assertTrue(store.put(snapshot(second, null)));
        assertEquals(1, store.size());
    }

    @Test
    void inspectionSnapshotBackendUpdateRetainsAdmissionEvidence() {
        ActiveInspectionSnapshot original = snapshot(UUID.randomUUID(), null);
        ActiveInspectionSnapshot moved = original.withBackend("gamma");
        assertEquals("gamma", moved.backend());
        assertEquals(original.playerId(), moved.playerId());
        assertEquals(original.decision(), moved.decision());
        assertEquals(original.profileId(), moved.profileId());
    }

    private static ActiveInspectionSnapshot snapshot(UUID playerId, Manifest manifest) {
        return new ActiveInspectionSnapshot(
            playerId,
            "Alice",
            "alpha",
            ClientClassification.JAVA_FABRIC,
            "fabric",
            "default",
            ProfileResolutionSource.DEFAULT,
            new Presence(GuardianProtocol.VERSION, GuardianProtocol.VERSION,
                GuardianProtocol.REQUIRED_CAPABILITIES, "test"),
            GuardianDecision.allow(DecisionReason.CERBERUS_VERIFIED, "ok"),
            manifest,
            new BedrockEvidence(BedrockSignal.NOT_BEDROCK, BedrockSignal.NOT_BEDROCK)
        );
    }
}
