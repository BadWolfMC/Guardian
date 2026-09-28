package com.badwolfmc.guardian.velocity.config;

import com.badwolfmc.guardian.core.operations.OperationalLogLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VelocityConfigLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void productionEnvironmentSecretLoadsWithExplicitVelocityDeployment() throws Exception {
        Path config = writeConfig(defaultConfig());
        byte[] key = new byte[32];
        key[0] = 12;
        VelocityOperationalSettings settings = new VelocityConfigLoader(Map.of(
            "GUARDIAN_PROXY_ASSERTION_SECRET", Base64.getEncoder().encodeToString(key))).load(config);

        assertEquals(1, settings.schemaVersion());
        assertEquals("en_us", settings.locale());
        assertEquals(10, settings.handshakeTimeoutSeconds());
        assertEquals(OperationalLogLevel.NORMAL, settings.loggingLevel());
        assertEquals("environment:GUARDIAN_PROXY_ASSERTION_SECRET",
            settings.proxyAssertionSecret().sourceDescription());
    }

    @Test
    void missingSecretFailsStartupValidation() throws Exception {
        Path config = writeConfig(defaultConfig());
        VelocityConfigurationException ex = assertThrows(
            VelocityConfigurationException.class, () -> new VelocityConfigLoader(Map.of()).load(config));
        assertTrue(ex.getMessage().contains("GUARDIAN_PROXY_ASSERTION_SECRET"));
    }

    @Test
    void fileSecretAndDebugLoggingAreSupported() throws Exception {
        Files.writeString(tempDir.resolve("proxy-assertion.secret"),
            Base64.getEncoder().encodeToString(new byte[32]), StandardCharsets.UTF_8);
        Path config = writeConfig(defaultConfig()
            .replace("secret-source: ENVIRONMENT", "secret-source: FILE")
            .replace("level: NORMAL", "level: DEBUG")
            .replace("handshake-timeout-seconds: 10", "handshake-timeout-seconds: 4"));

        VelocityOperationalSettings settings = new VelocityConfigLoader(Map.of()).load(config);
        assertEquals(OperationalLogLevel.DEBUG, settings.loggingLevel());
        assertEquals(4, settings.handshakeTimeoutSeconds());
        assertEquals("file:proxy-assertion.secret", settings.proxyAssertionSecret().sourceDescription());
    }

    @Test
    void wrongDeploymentAndUnknownKeysFailClosed() throws Exception {
        VelocityConfigLoader loader = new VelocityConfigLoader(Map.of(
            "GUARDIAN_PROXY_ASSERTION_SECRET", Base64.getEncoder().encodeToString(new byte[32])));
        VelocityConfigurationException wrongAuthority = assertThrows(VelocityConfigurationException.class,
            () -> loader.load(writeConfig(defaultConfig().replace("authority: velocity", "authority: paper"))));
        assertTrue(wrongAuthority.getMessage().contains("must be 'velocity'"));

        VelocityConfigurationException unknown = assertThrows(VelocityConfigurationException.class,
            () -> loader.load(writeConfig(defaultConfig() + "\nunknown-root: true\n")));
        assertTrue(unknown.getMessage().contains("unknown key"));
    }

    @Test
    void invalidTimingAndLoggingFailClosed() throws Exception {
        VelocityConfigLoader loader = new VelocityConfigLoader(Map.of(
            "GUARDIAN_PROXY_ASSERTION_SECRET", Base64.getEncoder().encodeToString(new byte[32])));
        VelocityConfigurationException timing = assertThrows(VelocityConfigurationException.class,
            () -> loader.load(writeConfig(defaultConfig().replace(
                "handshake-timeout-seconds: 10", "handshake-timeout-seconds: 9999"))));
        assertTrue(timing.getMessage().contains("handshake-timeout-seconds"));

        VelocityConfigurationException logging = assertThrows(VelocityConfigurationException.class,
            () -> loader.load(writeConfig(defaultConfig().replace("level: NORMAL", "level: TRACE"))));
        assertTrue(logging.getMessage().contains("NORMAL or DEBUG"));
    }

    private Path writeConfig(String value) throws Exception {
        Path config = tempDir.resolve("config.yml");
        Files.writeString(config, value, StandardCharsets.UTF_8);
        return config;
    }

    private static String defaultConfig() throws Exception {
        try (InputStream input = VelocityConfigLoaderTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
