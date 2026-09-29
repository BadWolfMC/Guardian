package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.core.BrandRuleMode;
import com.badwolfmc.guardian.core.ClientAction;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.protection.ProtectionRuleMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GuardianRuntimeManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsVersionedImmutableProductionSnapshot() throws Exception {
        GuardianRuntimeManager manager = managerWithDefaults();
        GuardianRuntimeSnapshot snapshot = manager.loadInitial();

        assertEquals(1, snapshot.settings().schemaVersion());
        assertTrue(snapshot.settings().admissionEnabled());
        assertFalse(snapshot.settings().protectionEnabled());
        assertEquals(ClientAction.REQUIRE_CERBERUS,
            snapshot.requireAdmissionPolicy().defaultProfile().clientPolicy()
                .configuredAction(ClientClassification.JAVA_FABRIC));
        assertEquals(BrandRuleMode.ALLOWLIST,
            snapshot.requireAdmissionPolicy().defaultProfile().clientPolicy().unknownBrandPolicy().mode());
        assertTrue(snapshot.settings().protectionPolicy().execution().roots().contains("plugins"));
        assertEquals(ProtectionRuleMode.DENYLIST, snapshot.settings().protectionPolicy().visibility().mode());
        assertEquals(ProtectionRuleMode.DENYLIST, snapshot.settings().protectionPolicy().namespaces().mode());
        assertTrue(snapshot.settings().protectionPolicy().perCommandVisibilityBypass());
        assertTrue(snapshot.settings().protectionPolicy().notificationsEnabled());
        assertSame(snapshot, manager.current());
    }

    @Test
    void malformedInitialConfigFailsWithoutActivatingOrMutatingFile() throws Exception {
        Files.createDirectories(tempDir.resolve("locales"));
        String malformed = "schema-version: [this is not valid YAML\n";
        Path config = tempDir.resolve("config.yml");
        Files.writeString(config, malformed, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("locales/en_us.properties"),
            defaultResource("locales/en_us.properties"), StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("policy.yml"),
            defaultResource("policy.yml"), StandardCharsets.UTF_8);

        GuardianRuntimeManager manager = new GuardianRuntimeManager(config, tempDir.resolve("locales"));

        GuardianConfigurationException ex =
            assertThrows(GuardianConfigurationException.class, manager::loadInitial);
        assertEquals(config, ex.path());
        assertEquals(GuardianConfigurationException.Kind.MALFORMED, ex.kind());
        assertTrue(ex.recoverableAtStartup());
        assertThrows(IllegalStateException.class, manager::current);
        assertEquals(malformed, Files.readString(config, StandardCharsets.UTF_8));
    }

    @Test
    void invalidInitialFallbackLocaleFailsWithoutActivatingOrMutatingFile() throws Exception {
        Files.createDirectories(tempDir.resolve("locales"));
        Files.writeString(tempDir.resolve("config.yml"), defaultResource("config.yml"), StandardCharsets.UTF_8);
        Path locale = tempDir.resolve("locales/en_us.properties");
        String invalid = "schema-version=1\nadmission.denied=<red>Only one key</red>\n";
        Files.writeString(locale, invalid, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("policy.yml"),
            defaultResource("policy.yml"), StandardCharsets.UTF_8);

        GuardianRuntimeManager manager = new GuardianRuntimeManager(
            tempDir.resolve("config.yml"), tempDir.resolve("locales"));

        GuardianConfigurationException ex =
            assertThrows(GuardianConfigurationException.class, manager::loadInitial);
        assertEquals(locale, ex.path());
        assertEquals(GuardianConfigurationException.Kind.INVALID, ex.kind());
        assertTrue(ex.recoverableAtStartup());
        assertThrows(IllegalStateException.class, manager::current);
        assertEquals(invalid, Files.readString(locale, StandardCharsets.UTF_8));
    }


    @Test
    void unsupportedSchemaIsNotAutomaticallyRecoverableAtStartup() throws Exception {
        String unsupported = defaultResource("config.yml").replace("schema-version: 1", "schema-version: 2");
        writeDefaults(unsupported);

        GuardianConfigurationException ex = assertThrows(
            GuardianConfigurationException.class,
            () -> new GuardianRuntimeManager(tempDir.resolve("config.yml"), tempDir.resolve("locales")).loadInitial()
        );

        assertEquals(GuardianConfigurationException.Kind.UNSUPPORTED_SCHEMA, ex.kind());
        assertFalse(ex.recoverableAtStartup());
        assertEquals(unsupported, Files.readString(tempDir.resolve("config.yml"), StandardCharsets.UTF_8));
    }

    @Test
    void malformedReloadLeavesPriorSnapshotActiveAndFileUntouched() throws Exception {
        GuardianRuntimeManager manager = managerWithDefaults();
        GuardianRuntimeSnapshot original = manager.loadInitial();
        Path config = tempDir.resolve("config.yml");
        String malformed = "schema-version: [this is not valid YAML\n";
        Files.writeString(config, malformed, StandardCharsets.UTF_8);

        assertThrows(GuardianConfigurationException.class, manager::reload);
        assertSame(original, manager.current());
        assertEquals(malformed, Files.readString(config, StandardCharsets.UTF_8));
    }

    @Test
    void structurallyInvalidReloadLeavesPriorSnapshotActiveAndFileUntouched() throws Exception {
        GuardianRuntimeManager manager = managerWithDefaults();
        GuardianRuntimeSnapshot original = manager.loadInitial();
        Path config = tempDir.resolve("config.yml");
        String invalid = defaultResource("config.yml")
            .replace("challenge-channel-wait-ticks: 40", "challenge-channel-wait-ticks: 99999");
        Files.writeString(config, invalid, StandardCharsets.UTF_8);

        GuardianConfigurationException ex = assertThrows(GuardianConfigurationException.class, manager::reload);
        assertTrue(ex.getMessage().contains("challenge-channel-wait-ticks"));
        assertSame(original, manager.current());
        assertEquals(invalid, Files.readString(config, StandardCharsets.UTF_8));
    }


    @Test
    void invalidProtectionReloadLeavesPriorSnapshotActiveAndFileUntouched() throws Exception {
        GuardianRuntimeManager manager = managerWithDefaults();
        GuardianRuntimeSnapshot original = manager.loadInitial();
        Path config = tempDir.resolve("config.yml");
        String invalid = defaultResource("config.yml")
            .replace("      - plugins\n      - ver", "      - plugins\n      - /PLUGINS\n      - ver");
        Files.writeString(config, invalid, StandardCharsets.UTF_8);

        GuardianConfigurationException ex = assertThrows(GuardianConfigurationException.class, manager::reload);
        assertTrue(ex.getMessage().contains("duplicate normalized command root 'plugins'"));
        assertSame(original, manager.current());
        assertEquals(invalid, Files.readString(config, StandardCharsets.UTF_8));
    }

    @Test
    void namespacedProtectionRulesRejectUnnamespacedRoots() throws Exception {
        String invalid = defaultResource("config.yml")
            .replace("      - bukkit:version\n\n  notifications:",
                "      - bukkit:version\n      - plugins\n\n  notifications:");
        writeDefaults(invalid);

        GuardianConfigurationException ex = assertThrows(
            GuardianConfigurationException.class,
            () -> new GuardianRuntimeManager(tempDir.resolve("config.yml"), tempDir.resolve("locales")).loadInitial()
        );
        assertTrue(ex.getMessage().contains("namespaced-command rules must contain full namespace:command roots"));
    }

    @Test
    void invalidLocaleReloadIsAtomicAndPreservesLocaleFile() throws Exception {
        GuardianRuntimeManager manager = managerWithDefaults();
        GuardianRuntimeSnapshot original = manager.loadInitial();
        Path locale = tempDir.resolve("locales/en_us.properties");
        String invalid = "schema-version=1\nadmission.denied=<red>Only one key</red>\n";
        Files.writeString(locale, invalid, StandardCharsets.UTF_8);

        GuardianConfigurationException ex = assertThrows(GuardianConfigurationException.class, manager::reload);
        assertTrue(ex.getMessage().contains("missing required locale key"));
        assertSame(original, manager.current());
        assertEquals(invalid, Files.readString(locale, StandardCharsets.UTF_8));
    }

    @Test
    void allFeatureCombinationsAreValid() throws Exception {
        for (boolean admission : new boolean[]{false, true}) {
            for (boolean protection : new boolean[]{false, true}) {
                writeDefaults(
                    defaultResource("config.yml")
                        .replace("admission:\n    enabled: true", "admission:\n    enabled: " + admission)
                        .replace("protection:\n    enabled: false", "protection:\n    enabled: " + protection)
                );
                GuardianRuntimeSnapshot snapshot = new GuardianRuntimeManager(
                    tempDir.resolve("config.yml"), tempDir.resolve("locales")).loadInitial();
                assertEquals(admission, snapshot.settings().admissionEnabled());
                assertEquals(protection, snapshot.settings().protectionEnabled());
            }
        }
    }

    @Test
    void contradictoryUnknownBrandModeFailsValidationInSharedPolicy() throws Exception {
        writeDefaults(defaultResource("config.yml"));
        Path policy = tempDir.resolve("policy.yml");
        String invalid = defaultResource("policy.yml")
            .replace("mode: ALLOWLIST", "mode: DENYLIST");
        Files.writeString(policy, invalid, StandardCharsets.UTF_8);
        GuardianConfigurationException ex = assertThrows(
            GuardianConfigurationException.class,
            () -> new GuardianRuntimeManager(tempDir.resolve("config.yml"), tempDir.resolve("locales")).loadInitial()
        );
        assertEquals(GuardianConfigurationException.Kind.ADMISSION_POLICY, ex.kind());
        assertFalse(ex.recoverableAtStartup());
        assertTrue(ex.getMessage().contains("unknown client action must be ALLOW"));
    }

    @Test
    void filesOnlyValidationDoesNotReplaceActiveSnapshot() throws Exception {
        GuardianRuntimeManager manager = managerWithDefaults();
        GuardianRuntimeSnapshot original = manager.loadInitial();
        Path policy = tempDir.resolve("policy.yml");
        Files.writeString(policy, defaultResource("policy.yml")
            .replace("default-profile: default", "default-profile: missing"), StandardCharsets.UTF_8);
        assertThrows(GuardianConfigurationException.class, manager::validateFiles);
        assertSame(original, manager.current());
    }

    private GuardianRuntimeManager managerWithDefaults() throws IOException {
        writeDefaults(defaultResource("config.yml"));
        return new GuardianRuntimeManager(tempDir.resolve("config.yml"), tempDir.resolve("locales"));
    }

    private void writeDefaults(String config) throws IOException {
        Files.createDirectories(tempDir.resolve("locales"));
        Files.writeString(tempDir.resolve("config.yml"), config, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("locales/en_us.properties"),
            defaultResource("locales/en_us.properties"), StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("policy.yml"),
            defaultResource("policy.yml"), StandardCharsets.UTF_8);
    }

    private static String defaultResource(String name) throws IOException {
        try (InputStream in = GuardianRuntimeManagerTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, "missing test resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
