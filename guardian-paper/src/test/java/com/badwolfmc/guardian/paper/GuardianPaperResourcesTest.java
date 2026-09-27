package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
    void artifactScanCommandAndPermissionAreDeclared() throws Exception {
        ClassLoader loader = GuardianPaperResourcesTest.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream("plugin.yml")) {
            assertNotNull(input, "plugin.yml must be present");
            String pluginYml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(pluginYml.contains("commands:"));
            assertTrue(pluginYml.contains("guardian:"));
            assertTrue(pluginYml.contains("guardian.artifacts.scan:"));
        }
    }
}
