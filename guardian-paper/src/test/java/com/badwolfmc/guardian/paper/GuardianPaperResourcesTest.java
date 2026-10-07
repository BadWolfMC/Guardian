package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardianPaperResourcesTest {
    @Test
    void requiredPluginResourcesArePackaged() {
        ClassLoader loader = GuardianPaperResourcesTest.class.getClassLoader();
        assertNotNull(loader.getResource("plugin.yml"), "plugin.yml must be present");
        assertNotNull(loader.getResource("config.yml"), "config.yml must be present");
        assertNotNull(loader.getResource("locales/en_us.properties"),
            "required fallback locale must be packaged");
        assertNotNull(loader.getResource("policy.yml"),
            "shared admission policy must be packaged");
    }

    @Test
    void modernCommandRegistrationUsesFinalPaperPermissionNamespace() throws Exception {
        ClassLoader loader = GuardianPaperResourcesTest.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream("plugin.yml")) {
            assertNotNull(input, "plugin.yml must be present");
            String pluginYml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(pluginYml.contains("commands:"),
                "Paper lifecycle command registration should not retain the legacy plugin.yml command surface");
            assertTrue(pluginYml.contains("guardian.command.status:"));
            assertTrue(pluginYml.contains("guardian.command.validate:"));
            assertTrue(pluginYml.contains("guardian.command.reload:"));
            assertTrue(pluginYml.contains("guardian.command.inspect:"));
            assertTrue(pluginYml.contains("guardian.command.artifacts.scan:"));
            assertFalse(pluginYml.contains("guardian.artifacts.scan:"));
        }
    }

    @Test
    void publicReleaseDefaultsUseSchemaTwoWhileLocaleKeepsIndependentSchema() throws Exception {
        ClassLoader loader = GuardianPaperResourcesTest.class.getClassLoader();
        assertResourceContains(loader, "config.yml", "schema-version: 2");
        assertResourceContains(loader, "policy.yml", "schema-version: 2");
        assertResourceContains(loader, "locales/en_us.properties", "schema-version=1");
    }

    @Test
    void phase8PaperMetadataTargetsApi263() throws Exception {
        ClassLoader loader = GuardianPaperResourcesTest.class.getClassLoader();
        assertResourceContains(loader, "plugin.yml", "api-version: '26.3'");
    }

    @Test
    void paperVersionComesFromGradleExpandedPluginMetadata() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        String command = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/GuardianPaperCommand.java"));
        String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"));

        assertTrue(build.contains("filesMatching('plugin.yml')"));
        assertTrue(build.contains("expand version: guardianVersion"));
        assertTrue(pluginYml.contains("version: '${version}'"));
        assertTrue(command.contains("plugin.getPluginMeta().getVersion()"),
            "Paper status should report the version from loaded plugin metadata");
    }

    private static void assertResourceContains(ClassLoader loader, String resource, String expected) throws Exception {
        try (InputStream input = loader.getResourceAsStream(resource)) {
            assertNotNull(input, resource + " must be present");
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains(expected), resource + " should contain " + expected);
        }
    }
}
