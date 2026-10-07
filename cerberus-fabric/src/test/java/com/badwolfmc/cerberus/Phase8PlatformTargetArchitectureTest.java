package com.badwolfmc.cerberus;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase8PlatformTargetArchitectureTest {
    @Test
    void phase8TargetsTheReviewedMinecraft263PlatformSet() throws Exception {
        Properties properties = loadProperties(Path.of("../gradle.properties"));

        assertEquals("26.3", properties.getProperty("minecraft_version"));
        assertEquals("26.3.build.+", properties.getProperty("paper_api_version"));
        assertEquals("0.19.5", properties.getProperty("fabric_loader_version"));
        assertEquals("0.162.0+26.3", properties.getProperty("fabric_api_version"));
        assertEquals("1.18.2", properties.getProperty("fabric_loom_version"));
        assertEquals("4.2.1-SNAPSHOT", properties.getProperty("velocity_api_version"));
        assertEquals("2.11.2-SNAPSHOT", properties.getProperty("geyser_api_version"));
        assertEquals("2.2.5-SNAPSHOT", properties.getProperty("floodgate_api_version"));
        assertEquals("5.5", properties.getProperty("luckperms_api_version"));
    }

    @Test
    void platformMetadataAdvertisesMinecraft263WithoutCompatibilityShims() throws Exception {
        String paperMetadata = Files.readString(Path.of("../guardian-paper/src/main/resources/plugin.yml"));
        String fabricMetadata = Files.readString(Path.of("src/main/resources/fabric.mod.json"));

        assertTrue(paperMetadata.contains("api-version: '26.3'"));
        assertTrue(fabricMetadata.contains("\"minecraft\": \"~26.3\""));
        assertTrue(fabricMetadata.contains("\"fabricloader\": \">=0.19.5\""));
        assertTrue(fabricMetadata.contains("\"java\": \">=25\""));
    }

    @Test
    void phase8UsesPatchReleaseVersionForThePlatformPort() throws Exception {
        String rootBuild = Files.readString(Path.of("../build.gradle"));
        assertTrue(rootBuild.contains("orElse('1.0.2').get()"));
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(Files.readString(path)));
        return properties;
    }
}
