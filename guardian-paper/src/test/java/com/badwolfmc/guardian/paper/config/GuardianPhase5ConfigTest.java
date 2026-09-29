package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.core.operations.OperationalLogLevel;
import com.badwolfmc.guardian.core.operations.ProxyAssertionSecretResolver;
import com.badwolfmc.guardian.paper.PaperAuthorityMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class GuardianPhase5ConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void standaloneDoesNotRequireProxyKey() throws Exception {
        Path config = writeConfig(defaultConfig());
        GuardianPaperSettings settings = new GuardianConfigLoader().load(config);
        assertEquals(PaperAuthorityMode.STANDALONE, settings.authorityMode());
        assertEquals(OperationalLogLevel.NORMAL, settings.loggingLevel());
        assertNull(settings.proxyAssertionSecret());
    }

    @Test
    void velocityAuthorityRequiresCopiedVelocityKey() throws Exception {
        Path config = writeConfig(defaultConfig().replace("authority: standalone", "authority: velocity"));
        GuardianConfigurationException ex = assertThrows(
            GuardianConfigurationException.class, () -> new GuardianConfigLoader().load(config));

        assertEquals(GuardianConfigurationException.Kind.EXTERNAL_DEPENDENCY, ex.kind());
        assertFalse(ex.recoverableAtStartup());
        assertTrue(ex.getMessage().contains("proxy assertion key file is missing"));
        assertTrue(ex.getMessage().contains("Copy proxy-assertion.key from the Guardian-Velocity data directory"));

        byte[] key = new byte[32];
        key[31] = 7;
        writeKey(key);
        GuardianPaperSettings settings = new GuardianConfigLoader().load(config);
        assertNotNull(settings.proxyAssertionSecret());
        assertEquals("file:proxy-assertion.key", settings.proxyAssertionSecret().sourceDescription());
    }

    @Test
    void fileKeyAndDebugLoggingAreReloadablePaperOwnedSettings() throws Exception {
        writeKey(new byte[32]);
        String configText = defaultConfig()
            .replace("authority: standalone", "authority: velocity")
            .replace("level: NORMAL", "level: DEBUG");

        GuardianPaperSettings settings = new GuardianConfigLoader().load(writeConfig(configText));
        assertEquals(OperationalLogLevel.DEBUG, settings.loggingLevel());
        assertEquals("file:proxy-assertion.key", settings.proxyAssertionSecret().sourceDescription());
    }

    @Test
    void malformedOptionalOperationalValuesAreRejectedInsteadOfSilentlyDefaulted() throws Exception {
        GuardianConfigurationException loggingType = assertThrows(GuardianConfigurationException.class, () ->
            new GuardianConfigLoader().load(writeConfig(defaultConfig().replace("level: NORMAL", "level: 7"))));
        assertTrue(loggingType.getMessage().contains("logging.level"));

        GuardianConfigurationException serverType = assertThrows(GuardianConfigurationException.class, () ->
            new GuardianConfigLoader().load(writeConfig(defaultConfig().replace("server-name: \"\"", "server-name: 7"))));
        assertTrue(serverType.getMessage().contains("server-name"));
    }

    @Test
    void invalidLoggingLevelIsRejected() throws Exception {
        Path config = writeConfig(defaultConfig().replace("level: NORMAL", "level: TRACE"));
        GuardianConfigurationException ex = assertThrows(
            GuardianConfigurationException.class, () -> new GuardianConfigLoader().load(config));
        assertTrue(ex.getMessage().contains("NORMAL or DEBUG"));
    }

    private void writeKey(byte[] bytes) throws Exception {
        Files.writeString(
            tempDir.resolve(ProxyAssertionSecretResolver.DEFAULT_KEY_FILE),
            Base64.getEncoder().encodeToString(bytes),
            StandardCharsets.UTF_8
        );
    }

    private Path writeConfig(String value) throws Exception {
        Path config = tempDir.resolve("config.yml");
        Files.writeString(config, value, StandardCharsets.UTF_8);
        return config;
    }

    private static String defaultConfig() throws Exception {
        try (InputStream input = GuardianPhase5ConfigTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
