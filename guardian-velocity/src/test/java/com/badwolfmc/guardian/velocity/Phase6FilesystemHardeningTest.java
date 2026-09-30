package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.operations.ProxyAssertionSecretResolver;
import com.badwolfmc.guardian.velocity.config.VelocityConfigLoader;
import com.badwolfmc.guardian.velocity.config.VelocityConfigurationException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class Phase6FilesystemHardeningTest {
    @TempDir
    Path tempDir;

    @Test
    void velocityConfigRejectsOversizedAndSymlinkFilesBeforeYamlParsing() throws Exception {
        writeKey();
        Path oversized = tempDir.resolve("oversized.yml");
        Files.writeString(oversized, "x".repeat(VelocityConfigLoader.MAX_CONFIG_BYTES + 1), StandardCharsets.UTF_8);
        VelocityConfigurationException tooLarge = assertThrows(VelocityConfigurationException.class,
            () -> new VelocityConfigLoader().load(oversized));
        assertTrue(tooLarge.getMessage().contains("stable regular non-symlink"));

        Path target = tempDir.resolve("target.yml");
        Files.writeString(target, defaultConfig(), StandardCharsets.UTF_8);
        Path link = symlinkOrSkip(tempDir.resolve("config.yml"), target.getFileName());
        assertThrows(VelocityConfigurationException.class, () -> new VelocityConfigLoader().load(link));
    }

    @Test
    void velocityLocaleRejectsSymlinkedFallbackAndOversizedLocale() throws Exception {
        Path locales = tempDir.resolve("locales");
        Files.createDirectories(locales);
        Path target = locales.resolve("real.properties");
        Files.writeString(target, "schema-version=1\n", StandardCharsets.UTF_8);
        symlinkOrSkip(locales.resolve("en_us.properties"), target.getFileName());
        VelocityConfigurationException symlink = assertThrows(VelocityConfigurationException.class,
            () -> VelocityMessages.load(locales, "en_us"));
        assertTrue(symlink.getMessage().contains("stable regular non-symlink"));

        Files.delete(locales.resolve("en_us.properties"));
        Files.writeString(locales.resolve("en_us.properties"),
            "x".repeat(VelocityMessages.MAX_LOCALE_BYTES + 1), StandardCharsets.UTF_8);
        VelocityConfigurationException tooLarge = assertThrows(VelocityConfigurationException.class,
            () -> VelocityMessages.load(locales, "en_us"));
        assertTrue(tooLarge.getMessage().contains("stable regular non-symlink"));
    }


    @Test
    void velocityLocaleDirectoryItselfCannotBeASymlink() throws Exception {
        Path realLocales = tempDir.resolve("real-locales");
        Files.createDirectories(realLocales);
        Path locales = symlinkOrSkip(tempDir.resolve("locales-link"), realLocales);
        VelocityConfigurationException ex = assertThrows(VelocityConfigurationException.class,
            () -> VelocityMessages.load(locales, "en_us"));
        assertTrue(ex.getMessage().contains("real directory"));
    }

    @Test
    void velocityStartupProvisioningGuardsNestedAdministratorDirectories() throws Exception {
        String plugin = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        assertTrue(plugin.contains("SafeDirectory.ensureChildDirectories(data, parent)"));
        assertTrue(plugin.contains("Files.exists(destination, LinkOption.NOFOLLOW_LINKS)"));
    }

    private void writeKey() throws Exception {
        Files.writeString(tempDir.resolve(ProxyAssertionSecretResolver.DEFAULT_KEY_FILE),
            Base64.getEncoder().encodeToString(new byte[32]), StandardCharsets.UTF_8);
    }

    private static String defaultConfig() throws Exception {
        try (InputStream input = Phase6FilesystemHardeningTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path symlinkOrSkip(Path link, Path target) throws Exception {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            Assumptions.assumeTrue(false, "symbolic links unavailable in this test environment: " + ex);
            throw ex;
        }
    }
}
