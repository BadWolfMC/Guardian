package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.operations.OperationalLogLevel;
import com.badwolfmc.guardian.velocity.config.VelocityConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    }

    @Test
    void failedReloadPreservesPreviousActiveRuntime() throws Exception {
        writeDefaults();
        VelocityRuntimeManager manager = new VelocityRuntimeManager(tempDir);
        VelocityRuntimeSnapshot original = manager.loadInitial();
        Path policy = tempDir.resolve("admission/policy.yml");
        Files.writeString(policy, resource("admission/policy.yml")
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
        Files.writeString(config, resource("config.yml").replace(
            "secret-source: ENVIRONMENT", "secret-source: FILE").replace("level: NORMAL", "level: DEBUG"),
            StandardCharsets.UTF_8);

        VelocityRuntimeSnapshot candidate = manager.validateFiles();
        assertEquals(OperationalLogLevel.DEBUG, candidate.settings().loggingLevel());
        assertSame(original, manager.current());
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
        Files.createDirectories(tempDir.resolve("admission"));
        Files.createDirectories(tempDir.resolve("locales"));
        Files.writeString(tempDir.resolve("config.yml"), resource("config.yml")
            .replace("secret-source: ENVIRONMENT", "secret-source: FILE"), StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("proxy-assertion.secret"),
            Base64.getEncoder().encodeToString(new byte[32]), StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("admission/policy.yml"), resource("admission/policy.yml"), StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("locales/en_us.properties"), resource("locales/en_us.properties"), StandardCharsets.UTF_8);
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = VelocityRuntimeManagerTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, "missing resource " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
