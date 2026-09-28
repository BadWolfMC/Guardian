package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.core.operations.OperationalLogLevel;
import com.badwolfmc.guardian.paper.PaperAuthorityMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GuardianPhase5ConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void standaloneDoesNotRequireProxySecret() throws Exception {
        Path config = writeConfig(defaultConfig());
        GuardianPaperSettings settings = new GuardianConfigLoader(Map.of()).load(config);
        assertEquals(PaperAuthorityMode.STANDALONE, settings.authorityMode());
        assertEquals(OperationalLogLevel.NORMAL, settings.loggingLevel());
        assertNull(settings.proxyAssertionSecret());
    }

    @Test
    void velocityAuthorityRequiresValidatedProductionSecret() throws Exception {
        Path config = writeConfig(defaultConfig().replace("authority: standalone", "authority: velocity"));
        GuardianConfigLoader missing = new GuardianConfigLoader(Map.of());
        GuardianConfigurationException ex = assertThrows(GuardianConfigurationException.class, () -> missing.load(config));
        assertTrue(ex.getMessage().contains("GUARDIAN_PROXY_ASSERTION_SECRET"));

        byte[] key = new byte[32];
        key[31] = 7;
        GuardianPaperSettings settings = new GuardianConfigLoader(Map.of(
            "GUARDIAN_PROXY_ASSERTION_SECRET", Base64.getEncoder().encodeToString(key))).load(config);
        assertNotNull(settings.proxyAssertionSecret());
        assertEquals("environment:GUARDIAN_PROXY_ASSERTION_SECRET",
            settings.proxyAssertionSecret().sourceDescription());
    }

    @Test
    void fileSecretAndDebugLoggingAreReloadablePaperOwnedSettings() throws Exception {
        String configText = defaultConfig()
            .replace("authority: standalone", "authority: velocity")
            .replace("level: NORMAL", "level: DEBUG")
            .replace("secret-source: ENVIRONMENT", "secret-source: FILE");
        Files.writeString(tempDir.resolve("proxy-assertion.secret"),
            Base64.getEncoder().encodeToString(new byte[32]), StandardCharsets.UTF_8);
        GuardianPaperSettings settings = new GuardianConfigLoader(Map.of()).load(writeConfig(configText));
        assertEquals(OperationalLogLevel.DEBUG, settings.loggingLevel());
        assertEquals("file:proxy-assertion.secret", settings.proxyAssertionSecret().sourceDescription());
    }

    @Test
    void malformedOptionalOperationalValuesAreRejectedInsteadOfSilentlyDefaulted() throws Exception {
        GuardianConfigurationException loggingType = assertThrows(GuardianConfigurationException.class, () ->
            new GuardianConfigLoader(Map.of()).load(writeConfig(defaultConfig().replace("level: NORMAL", "level: 7"))));
        assertTrue(loggingType.getMessage().contains("logging.level"));

        GuardianConfigurationException serverType = assertThrows(GuardianConfigurationException.class, () ->
            new GuardianConfigLoader(Map.of()).load(writeConfig(defaultConfig().replace("server-name: \"\"", "server-name: 7"))));
        assertTrue(serverType.getMessage().contains("server-name"));
    }

    @Test
    void invalidLoggingLevelIsRejected() throws Exception {
        Path config = writeConfig(defaultConfig().replace("level: NORMAL", "level: TRACE"));
        GuardianConfigurationException ex = assertThrows(
            GuardianConfigurationException.class, () -> new GuardianConfigLoader(Map.of()).load(config));
        assertTrue(ex.getMessage().contains("NORMAL or DEBUG"));
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
