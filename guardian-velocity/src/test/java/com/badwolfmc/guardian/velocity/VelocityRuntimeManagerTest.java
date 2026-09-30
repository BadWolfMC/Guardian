package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.operations.OperationalLogLevel;
import com.badwolfmc.guardian.protocol.GuardianChallengeCrypto;
import com.badwolfmc.guardian.velocity.config.VelocityConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class VelocityRuntimeManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsCompleteImmutableAuthorityCandidate() throws Exception {
        writeDefaults();
        VelocityRuntimeManager manager = new VelocityRuntimeManager(tempDir);
        VelocityRuntimeSnapshot snapshot = manager.loadInitial();
        assertSame(snapshot, manager.current());
        assertEquals("default", snapshot.admissionPolicy().defaultProfileId());
        assertEquals(OperationalLogLevel.NORMAL, snapshot.settings().loggingLevel());
        assertNotNull(snapshot.messages());
        assertEquals(0, snapshot.artifactCatalog().size());
        assertEquals(1L, snapshot.generation());
    }

    @Test
    void successfulReloadAdvancesRuntimeGenerationButValidationDoesNot() throws Exception {
        writeDefaults();
        VelocityRuntimeManager manager = new VelocityRuntimeManager(tempDir);
        VelocityRuntimeSnapshot original = manager.loadInitial();

        VelocityRuntimeSnapshot candidate = manager.validateFiles();
        assertEquals(original.generation(), candidate.generation());
        assertSame(original, manager.current());

        VelocityRuntimeSnapshot reloaded = manager.reload();
        assertEquals(original.generation() + 1L, reloaded.generation());
        assertSame(reloaded, manager.current());
    }

    @Test
    void failedReloadPreservesPreviousActiveRuntime() throws Exception {
        writeDefaults();
        VelocityRuntimeManager manager = new VelocityRuntimeManager(tempDir);
        VelocityRuntimeSnapshot original = manager.loadInitial();
        Path policy = tempDir.resolve("policy.yml");
        Files.writeString(policy, resource("policy.yml")
            .replace("default-profile: default", "default-profile: missing"), StandardCharsets.UTF_8);

        assertThrows(VelocityConfigurationException.class, manager::reload);
        assertSame(original, manager.current());
    }

    @Test
    void filesOnlyValidationNeverActivatesCandidate() throws Exception {
        writeDefaults();
        VelocityRuntimeManager manager = new VelocityRuntimeManager(tempDir);
        VelocityRuntimeSnapshot original = manager.loadInitial();
        Path config = tempDir.resolve("config.yml");
        Files.writeString(config, resource("config.yml").replace("level: NORMAL", "level: DEBUG"),
            StandardCharsets.UTF_8);

        VelocityRuntimeSnapshot candidate = manager.validateFiles();
        assertEquals(OperationalLogLevel.DEBUG, candidate.settings().loggingLevel());
        assertSame(original, manager.current());
    }


    @Test
    void serverAuthenticationKeyRotationIsCandidateScopedAndActivatesOnlyOnReload() throws Exception {
        writeDefaults();
        var first = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        var second = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        Path config = tempDir.resolve("config.yml");
        Files.writeString(config, resource("config.yml")
            .replace("server-authentication:\n    enabled: false",
                "server-authentication:\n    enabled: true"), StandardCharsets.UTF_8);
        Path key = tempDir.resolve("guardian-server-auth.key");
        Files.write(key, first.getPrivate().getEncoded());

        VelocityRuntimeManager manager = new VelocityRuntimeManager(tempDir);
        VelocityRuntimeSnapshot original = manager.loadInitial();
        assertNotNull(original.settings().serverChallengeSigner());

        Files.write(key, second.getPrivate().getEncoded());
        VelocityRuntimeSnapshot candidate = manager.validateFiles();
        assertFalse(original.settings().serverChallengeSigner()
            .sameKey(candidate.settings().serverChallengeSigner()));
        assertSame(original, manager.current(), "validation must not activate the rotated key");

        VelocityRuntimeSnapshot reloaded = manager.reload();
        assertSame(reloaded, manager.current());
        assertFalse(original.settings().serverChallengeSigner()
            .sameKey(reloaded.settings().serverChallengeSigner()));
        assertTrue(candidate.settings().serverChallengeSigner()
            .sameKey(reloaded.settings().serverChallengeSigner()));
    }

    @Test
    void repeatedFailedReloadsNeverAdvanceGenerationOrReplaceActiveSnapshot() throws Exception {
        writeDefaults();
        VelocityRuntimeManager manager = new VelocityRuntimeManager(tempDir);
        VelocityRuntimeSnapshot original = manager.loadInitial();
        Path policy = tempDir.resolve("policy.yml");
        Files.writeString(policy, resource("policy.yml")
            .replace("default-profile: default", "default-profile: missing"), StandardCharsets.UTF_8);

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThrows(VelocityConfigurationException.class, manager::reload);
            assertSame(original, manager.current());
            assertEquals(1L, manager.current().generation());
        }
    }

    @Test
    void invalidLocaleFailsReloadWithoutPartialActivation() throws Exception {
        writeDefaults();
        VelocityRuntimeManager manager = new VelocityRuntimeManager(tempDir);
        VelocityRuntimeSnapshot original = manager.loadInitial();
        Files.writeString(tempDir.resolve("locales/en_us.properties"),
            "schema-version=1\nadmission.denied=<red>only one key</red>\n", StandardCharsets.UTF_8);

        assertThrows(VelocityConfigurationException.class, manager::reload);
        assertSame(original, manager.current());
    }

    private void writeDefaults() throws Exception {
        Files.createDirectories(tempDir.resolve("locales"));
        Files.writeString(tempDir.resolve("config.yml"), resource("config.yml"), StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("proxy-assertion.key"),
            Base64.getEncoder().encodeToString(new byte[32]), StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("policy.yml"), resource("policy.yml"), StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("locales/en_us.properties"), resource("locales/en_us.properties"), StandardCharsets.UTF_8);
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = VelocityRuntimeManagerTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, "missing resource " + name);
            return normalizeNewlines(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
