package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.paper.locale.GuardianLocaleLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Phase6FilesystemHardeningTest {
    @TempDir
    Path tempDir;

    @Test
    void paperConfigRejectsOversizedAndSymlinkFilesBeforeYamlParsing() throws Exception {
        Path oversized = tempDir.resolve("oversized.yml");
        Files.writeString(oversized, "x".repeat(GuardianConfigLoader.MAX_CONFIG_BYTES + 1), StandardCharsets.UTF_8);
        GuardianConfigurationException tooLarge = assertThrows(GuardianConfigurationException.class,
            () -> new GuardianConfigLoader().load(oversized));
        assertEquals(GuardianConfigurationException.Kind.INVALID, tooLarge.kind());
        assertTrue(tooLarge.getMessage().contains("stable regular non-symlink"));

        Path target = tempDir.resolve("target.yml");
        Files.writeString(target, defaultConfig(), StandardCharsets.UTF_8);
        Path link = symlinkOrSkip(tempDir.resolve("config.yml"), target.getFileName());
        GuardianConfigurationException symlink = assertThrows(GuardianConfigurationException.class,
            () -> new GuardianConfigLoader().load(link));
        assertEquals(GuardianConfigurationException.Kind.INVALID, symlink.kind());
    }

    @Test
    void paperLocaleRejectsSymlinkedFallbackAndOversizedLocale() throws Exception {
        Path locales = tempDir.resolve("locales");
        Files.createDirectories(locales);
        Path target = locales.resolve("real.properties");
        Files.writeString(target, "schema-version=1\n", StandardCharsets.UTF_8);
        symlinkOrSkip(locales.resolve("en_us.properties"), target.getFileName());
        GuardianConfigurationException symlink = assertThrows(GuardianConfigurationException.class,
            () -> new GuardianLocaleLoader().load(locales, "en_us"));
        assertEquals(GuardianConfigurationException.Kind.INVALID, symlink.kind());

        Files.delete(locales.resolve("en_us.properties"));
        Files.writeString(locales.resolve("en_us.properties"),
            "x".repeat(GuardianLocaleLoader.MAX_LOCALE_BYTES + 1), StandardCharsets.UTF_8);
        GuardianConfigurationException tooLarge = assertThrows(GuardianConfigurationException.class,
            () -> new GuardianLocaleLoader().load(locales, "en_us"));
        assertEquals(GuardianConfigurationException.Kind.INVALID, tooLarge.kind());
    }


    @Test
    void paperLocaleDirectoryItselfCannotBeASymlink() throws Exception {
        Path realLocales = tempDir.resolve("real-locales");
        Files.createDirectories(realLocales);
        Path locales = symlinkOrSkip(tempDir.resolve("locales-link"), realLocales);
        GuardianConfigurationException ex = assertThrows(GuardianConfigurationException.class,
            () -> new GuardianLocaleLoader().load(locales, "en_us"));
        assertEquals(GuardianConfigurationException.Kind.INVALID, ex.kind());
        assertTrue(ex.getMessage().contains("real directory"));
    }

    @Test
    void paperStartupProvisioningGuardsNestedAdministratorDirectories() throws Exception {
        String plugin = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/GuardianPaperPlugin.java"));
        assertTrue(plugin.contains("SafeDirectory.ensureChildDirectories(data, parent)"));
        assertTrue(plugin.contains("Files.exists(destination, LinkOption.NOFOLLOW_LINKS)"));
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
