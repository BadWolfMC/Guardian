package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.core.artifact.ArtifactCatalog;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalogStore;
import com.badwolfmc.guardian.core.operations.ConfigurationSchemaMigrator;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GuardianPhase7UpgradeTest {
    @TempDir
    Path tempDir;

    @Test
    void representativePhase6InstallMigratesAndLoadsWithoutLosingAdministratorValues() throws Exception {
        Path config = tempDir.resolve("config.yml");
        Path policy = tempDir.resolve("policy.yml");
        Path artifacts = tempDir.resolve("artifacts.yml");

        String phase6Config = resource("config.yml")
            .replaceFirst("schema-version: 2", "schema-version: 1")
            .replace("server-name: \"\"", "server-name: \"alpha-custom\"")
            .replace("level: NORMAL", "level: DEBUG")
            .replace("https://www.badwolfmc.com/", "https://example.invalid/guardian-help");
        String phase6Policy = resource("policy.yml")
            .replaceFirst("schema-version: 2", "schema-version: 1")
            .replace("default-profile: default", "default-profile: veteran")
            .replace("  default:\n", "  veteran:\n")
            .replace("    priority: 0", "    priority: 73")
            .replaceFirst(
                "(?s)\\n# Optional official Cerberus release provenance\\..*?\\ncerberus-release-trust:\\n  required: false\\n  ed25519-public-keys: \\[\\]\\n",
                "\n"
            );

        Files.writeString(config, phase6Config, StandardCharsets.UTF_8);
        Files.writeString(policy, phase6Policy, StandardCharsets.UTF_8);
        new ArtifactCatalogStore(artifacts).store(ArtifactCatalog.empty());

        var configPrepared = ConfigurationSchemaMigrator.prepare(
            config,
            GuardianConfigLoader.MAX_CONFIG_BYTES,
            ConfigurationSchemaMigrator.Surface.PAPER_CONFIG
        ).orElseThrow();
        var policyPrepared = ConfigurationSchemaMigrator.prepare(
            policy,
            AdmissionPolicyLoader.MAX_POLICY_BYTES,
            ConfigurationSchemaMigrator.Surface.ADMISSION_POLICY
        ).orElseThrow();
        ConfigurationSchemaMigrator.publish(configPrepared);
        ConfigurationSchemaMigrator.publish(policyPrepared);

        GuardianPaperSettings settings = new GuardianConfigLoader().load(config);
        var admission = new AdmissionPolicyLoader().load(policy, artifacts);

        assertEquals(2, settings.schemaVersion());
        assertEquals("alpha-custom", settings.serverName());
        assertEquals("https://example.invalid/guardian-help", settings.helpUrl());
        assertEquals("DEBUG", settings.loggingLevel().name());
        assertEquals(2, admission.schemaVersion());
        assertEquals("veteran", admission.defaultProfileId());
        assertEquals(73, admission.profiles().get("veteran").priority());
        assertFalse(admission.cerberusReleaseTrust().required());
        assertTrue(admission.cerberusReleaseTrust().trustedEd25519Keys().isEmpty());

        String migratedPolicy = Files.readString(policy, StandardCharsets.UTF_8);
        assertTrue(migratedPolicy.contains("cerberus-release-trust:"));
        assertTrue(migratedPolicy.contains("required: false"));
        try (var files = Files.list(tempDir)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().contains(".pre-schema2-")));
        }
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = GuardianPhase7UpgradeTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, name + " must be present");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
