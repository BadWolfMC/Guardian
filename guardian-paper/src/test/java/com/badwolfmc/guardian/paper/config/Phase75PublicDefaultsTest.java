package com.badwolfmc.guardian.paper.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase75PublicDefaultsTest {
    @Test
    void packagedProductionDefaultsEnableBothIndependentDomains() throws Exception {
        String config = resource("config.yml");
        assertTrue(config.contains("admission:\n    enabled: true"));
        assertTrue(config.contains("protection:\n    enabled: true"));
    }

    @Test
    void packagedHelpDestinationIsProjectFacingRatherThanBadWolfMcWebsite() throws Exception {
        String config = resource("config.yml");
        String locale = resource("locales/en_us.properties");
        assertTrue(config.contains("https://github.com/BadWolfMC/Guardian"));
        assertTrue(locale.contains("https://github.com/BadWolfMC/Guardian"));
        assertFalse(config.contains("https://www.badwolfmc.com/"));
        assertFalse(locale.contains("https://www.badwolfmc.com/"));
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = Phase75PublicDefaultsTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, name + " must be present");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
