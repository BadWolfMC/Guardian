package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
        assertNotNull(loader.getResource("admission/policy.yml"),
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
}
