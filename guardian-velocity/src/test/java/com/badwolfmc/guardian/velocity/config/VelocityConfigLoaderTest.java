package com.badwolfmc.guardian.velocity.config;

import com.badwolfmc.guardian.core.operations.ConfigurationSchemaMigrator;
import com.badwolfmc.guardian.core.operations.OperationalLogLevel;
import com.badwolfmc.guardian.core.operations.ProxyAssertionSecretResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class VelocityConfigLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void generatedFileKeyLoadsWithExplicitVelocityDeployment() throws Exception {
        writeKey(new byte[32]);
        Path config = writeConfig(defaultConfig());
        VelocityOperationalSettings settings = new VelocityConfigLoader().load(config);

        assertEquals(2, settings.schemaVersion());
        assertEquals("en_us", settings.locale());
        assertEquals(10, settings.handshakeTimeoutSeconds());
        assertEquals(OperationalLogLevel.NORMAL, settings.loggingLevel());
        assertEquals("file:proxy-assertion.key", settings.proxyAssertionSecret().sourceDescription());
    }

    @Test
    void representativePhase6ConfigMigratesAndPreservesProxyOperationalValues() throws Exception {
        writeKey(new byte[32]);
        Path config = writeConfig(defaultConfig()
            .replaceFirst("schema-version: 2", "schema-version: 1")
            .replace("level: NORMAL", "level: DEBUG")
            .replace("handshake-timeout-seconds: 10", "handshake-timeout-seconds: 7"));

        var prepared = ConfigurationSchemaMigrator.prepare(
            config, VelocityConfigLoader.MAX_CONFIG_BYTES, ConfigurationSchemaMigrator.Surface.VELOCITY_CONFIG
        ).orElseThrow();
        var published = ConfigurationSchemaMigrator.publish(prepared);

        VelocityOperationalSettings settings = new VelocityConfigLoader().load(config);
        assertEquals(2, settings.schemaVersion());
        assertEquals(OperationalLogLevel.DEBUG, settings.loggingLevel());
        assertEquals(7, settings.handshakeTimeoutSeconds());
        assertTrue(Files.exists(published.backupPath()));
        assertTrue(Files.readString(published.backupPath(), StandardCharsets.UTF_8).contains("schema-version: 1"));
    }

    @Test
    void missingKeyFailsStartupValidationWithoutMutatingConfiguration() throws Exception {
        Path config = writeConfig(defaultConfig());
        String before = Files.readString(config, StandardCharsets.UTF_8);

        VelocityConfigurationException ex = assertThrows(
            VelocityConfigurationException.class, () -> new VelocityConfigLoader().load(config));

        assertTrue(ex.getMessage().contains("proxy assertion key file is missing"));
        assertEquals(before, Files.readString(config, StandardCharsets.UTF_8));
    }

    @Test
    void malformedKeyFailsClosed() throws Exception {
        writeKey(new byte[31]);
        VelocityConfigurationException ex = assertThrows(
            VelocityConfigurationException.class, () -> new VelocityConfigLoader().load(writeConfig(defaultConfig())));
        assertTrue(ex.getMessage().contains("exactly 32 bytes"));
    }

    @Test
    void debugLoggingAndTimingAreSupported() throws Exception {
        writeKey(new byte[32]);
        Path config = writeConfig(defaultConfig()
            .replace("level: NORMAL", "level: DEBUG")
            .replace("handshake-timeout-seconds: 10", "handshake-timeout-seconds: 4"));

        VelocityOperationalSettings settings = new VelocityConfigLoader().load(config);
        assertEquals(OperationalLogLevel.DEBUG, settings.loggingLevel());
        assertEquals(4, settings.handshakeTimeoutSeconds());
    }

    @Test
    void wrongDeploymentAndUnknownKeysFailClosed() throws Exception {
        writeKey(new byte[32]);
        VelocityConfigLoader loader = new VelocityConfigLoader();
        VelocityConfigurationException wrongAuthority = assertThrows(VelocityConfigurationException.class,
            () -> loader.load(writeConfig(defaultConfig().replace("authority: velocity", "authority: paper"))));
        assertTrue(wrongAuthority.getMessage().contains("must be 'velocity'"));

        VelocityConfigurationException unknown = assertThrows(VelocityConfigurationException.class,
            () -> loader.load(writeConfig(defaultConfig() + "\nunknown-root: true\n")));
        assertTrue(unknown.getMessage().contains("unknown key"));
    }

    @Test
    void invalidTimingAndLoggingFailClosed() throws Exception {
        writeKey(new byte[32]);
        VelocityConfigLoader loader = new VelocityConfigLoader();
        VelocityConfigurationException timing = assertThrows(VelocityConfigurationException.class,
            () -> loader.load(writeConfig(defaultConfig().replace(
                "handshake-timeout-seconds: 10", "handshake-timeout-seconds: 9999"))));
        assertTrue(timing.getMessage().contains("handshake-timeout-seconds"));

        VelocityConfigurationException logging = assertThrows(VelocityConfigurationException.class,
            () -> loader.load(writeConfig(defaultConfig().replace("level: NORMAL", "level: TRACE"))));
        assertTrue(logging.getMessage().contains("NORMAL or DEBUG"));
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
        try (InputStream input = VelocityConfigLoaderTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
