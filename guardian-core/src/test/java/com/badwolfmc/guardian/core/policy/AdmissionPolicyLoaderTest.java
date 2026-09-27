package com.badwolfmc.guardian.core.policy;

import com.badwolfmc.guardian.core.ClientAction;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.artifact.ApprovedArtifact;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalog;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalogStore;
import com.badwolfmc.guardian.protocol.ArtifactSha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionPolicyLoaderTest {
    @TempDir Path tempDir;

    @Test
    void parsesPortablePolicyWithoutPlatformConfigurationTypes() throws Exception {
        Path policy = writePolicy(basePolicy(""));
        AdmissionPolicySnapshot snapshot = new AdmissionPolicyLoader().load(policy, tempDir.resolve("artifacts.yml"));
        assertEquals("default", snapshot.defaultProfileId());
        assertEquals(ClientAction.REQUIRE_CERBERUS,
            snapshot.defaultProfile().clientPolicy().configuredAction(ClientClassification.JAVA_FABRIC));
        assertEquals(ModPolicyMode.ALLOWLIST, snapshot.defaultProfile().modPolicy().mode());
        assertTrue(snapshot.defaultProfile().modPolicy().baselineModIds().contains("fabricloader"));
    }

    @Test
    void resolvesCatalogReferencesButCatalogPresenceIsNotAnImplicitRule() throws Exception {
        ArtifactSha256 hash = new ArtifactSha256("a".repeat(64));
        new ArtifactCatalogStore(tempDir.resolve("artifacts.yml")).store(ArtifactCatalog.of(List.of(
            new ApprovedArtifact("sodium", "0.9.1+mc26.2", hash),
            new ApprovedArtifact("unused-catalogued", "1.0", new ArtifactSha256("b".repeat(64)))
        )));
        String rules = """
              allow-sodium:
                mod: sodium
                action: ALLOW
                accept:
                  - version: "0.9.*"
                    verification: HASH_REQUIRED
                    catalog: true
            """;
        AdmissionPolicySnapshot snapshot = new AdmissionPolicyLoader().load(
            writePolicy(basePolicy(rules)), tempDir.resolve("artifacts.yml"));
        ModPolicy mods = snapshot.defaultProfile().modPolicy();
        assertTrue(mods.rulesByModId().containsKey("sodium"));
        assertFalse(mods.rulesByModId().containsKey("unused-catalogued"),
            "catalog identity data must never create implicit permission rules");
        ArtifactAcceptance acceptance = mods.rulesByModId().get("sodium").acceptances().getFirst();
        assertEquals(hash, acceptance.catalogHashesByVersion().get("0.9.1+mc26.2").iterator().next());
    }

    @Test
    void rejectsMissingCatalogReference() throws Exception {
        String rules = """
              allow-sodium:
                mod: sodium
                action: ALLOW
                accept:
                  - version: "0.9.*"
                    verification: HASH_REQUIRED
                    catalog: true
            """;
        AdmissionPolicyException ex = assertThrows(AdmissionPolicyException.class,
            () -> new AdmissionPolicyLoader().load(writePolicy(basePolicy(rules)), tempDir.resolve("artifacts.yml")));
        assertTrue(ex.getMessage().contains("no matching catalog entry"));
    }

    @Test
    void rejectsContradictoryRequiredAndUnconditionalDeny() throws Exception {
        String yaml = basePolicy("")
            .replace("      required: {}", "      required:\n"
                + "        require-example:\n"
                + "          mod: example")
            .replace("      rules: {}", "      rules:\n"
                + "        deny-example:\n"
                + "          mod: example\n"
                + "          action: DENY");
        AdmissionPolicyException ex = assertThrows(AdmissionPolicyException.class,
            () -> new AdmissionPolicyLoader().load(writePolicy(yaml), tempDir.resolve("artifacts.yml")));
        assertTrue(ex.getMessage().contains("requires mod 'example' but also unconditionally denies it"));
    }

    @Test
    void rejectsProfilePriorityTiesAndMissingOverrideTargets() throws Exception {
        String tie = basePolicy("").replace("profiles:\n  default:", """
            profiles:
              staff:
                priority: 0
                clients:
                  bedrock: ALLOW
                  vanilla: ALLOW
                  optifine: ALLOW
                  fabric: REQUIRE_CERBERUS
                  unknown: DENY
                unknown-brands:
                  mode: ALLOWLIST
                  brands: []
                mods:
                  mode: DENYLIST
                  origins:
                    directory: DENY
                    mixed-or-unknown: DENY
                  baseline: []
                  required: {}
                  rules: {}
              default:
            """);
        AdmissionPolicyException priority = assertThrows(AdmissionPolicyException.class,
            () -> new AdmissionPolicyLoader().load(writePolicy(tie), tempDir.resolve("artifacts.yml")));
        assertTrue(priority.getMessage().contains("priorities must be unique"));

        String missing = basePolicy("").replace("identity-overrides: {}", """
            identity-overrides:
              "11111111-1111-1111-1111-111111111111": missing
            """);
        AdmissionPolicyException override = assertThrows(AdmissionPolicyException.class,
            () -> new AdmissionPolicyLoader().load(writePolicy(missing), tempDir.resolve("artifacts.yml")));
        assertTrue(override.getMessage().contains("references missing profile"));
    }

    @Test
    void rejectsMalformedIdentifiersVersionsArtifactRulesAndDuplicateKeys() throws Exception {
        String badVersion = basePolicy("""
              allow-example:
                mod: example
                action: ALLOW
                accept:
                  - version: ">=one"
                    verification: VERSION_ONLY
            """);
        assertTrue(assertThrows(AdmissionPolicyException.class,
            () -> new AdmissionPolicyLoader().load(writePolicy(badVersion), tempDir.resolve("artifacts.yml")))
            .getMessage().contains("version invalid"));

        String badArtifact = basePolicy("""
              allow-example:
                mod: example
                action: ALLOW
                accept:
                  - version: "1.0"
                    verification: VERSION_ONLY
                    sha256:
                      - "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            """);
        assertTrue(assertThrows(AdmissionPolicyException.class,
            () -> new AdmissionPolicyLoader().load(writePolicy(badArtifact), tempDir.resolve("artifacts.yml")))
            .getMessage().contains("VERSION_ONLY cannot declare"));

        String duplicate = basePolicy("").replace("default-profile: default",
            "default-profile: default\ndefault-profile: default");
        assertTrue(assertThrows(AdmissionPolicyException.class,
            () -> new AdmissionPolicyLoader().load(writePolicy(duplicate), tempDir.resolve("artifacts.yml")))
            .getMessage().contains("malformed admission policy YAML"));
    }

    @Test
    void atomicManagerRetainsPriorSnapshotWhenCandidateBecomesInvalidAndFilesOnlyValidationDoesNotActivate() throws Exception {
        Path policy = writePolicy(basePolicy(""));
        AdmissionPolicyRuntimeManager manager = new AdmissionPolicyRuntimeManager(policy, tempDir.resolve("artifacts.yml"));
        AdmissionPolicySnapshot original = manager.loadInitial();

        Files.writeString(policy, basePolicy("").replace("default-profile: default", "default-profile: missing"));
        assertThrows(AdmissionPolicyException.class, manager::reload);
        assertSame(original, manager.current());
        assertThrows(AdmissionPolicyException.class, manager::validateFiles);
        assertSame(original, manager.current());
    }

    private Path writePolicy(String yaml) throws Exception {
        Path path = tempDir.resolve("policy-" + System.nanoTime() + ".yml");
        Files.writeString(path, yaml);
        return path;
    }

    private static String basePolicy(String rules) {
        String rulesBlock = rules.isBlank() ? "{}" : "\n" + rules.strip().indent(8).stripTrailing();
        return """
            schema-version: 1
            default-profile: default
            identity-overrides: {}
            profiles:
              default:
                priority: 0
                clients:
                  bedrock: ALLOW
                  vanilla: ALLOW
                  optifine: ALLOW
                  fabric: REQUIRE_CERBERUS
                  unknown: DENY
                unknown-brands:
                  mode: ALLOWLIST
                  brands: []
                mods:
                  mode: ALLOWLIST
                  origins:
                    directory: DENY
                    mixed-or-unknown: DENY
                  baseline:
                    - fabricloader
                    - cerberus
                  required: {}
                  rules: %s
            """.formatted(rulesBlock);
    }
}
