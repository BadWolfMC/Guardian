package com.badwolfmc.guardian.core.policy;

import com.badwolfmc.guardian.core.AdmissionPermissions;
import com.badwolfmc.guardian.core.ClientAction;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.protocol.ArtifactSha256;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.OriginKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AdmissionPolicyInheritanceTest {
    @TempDir Path dir;

    private static String example() {
        return """
            schema-version: 2
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
                  baseline: [fabricloader, cerberus]
                  required:
                    require-helper:
                      mod: helpermod
                      accept:
                        - version: '1.0'
                          verification: VERSION_ONLY
                  rules:
                    allow-sodium:
                      mod: sodium
                      action: ALLOW
                      accept:
                        - version: '0.9.1'
                          verification: HASH_REQUIRED
                          sha256:
                            - 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
              staff:
                inherit: default
                priority: 100
                mods:
                  rules:
                    allow-staffmod:
                      mod: staffmod
                      action: ALLOW
              admin:
                inherit: staff
                priority: 200
                mods:
                  rules:
                    allow-adminmod:
                      mod: adminmod
                      action: ALLOW
              senior:
                inherit: admin
                priority: 300
            """;
    }

    private AdmissionPolicySnapshot load(String yaml) throws Exception {
        Path policy = dir.resolve("policy.yml");
        Files.writeString(policy, yaml);
        return new AdmissionPolicyLoader().load(policy, dir.resolve("artifacts.yml"));
    }

    // A bad replacement must fail the test itself rather than silently test the unchanged fixture.
    private static String replaceOnce(String yaml, String from, String to) {
        int first = yaml.indexOf(from);
        assertTrue(first >= 0, "missing fixture fragment: " + from);
        assertEquals(-1, yaml.indexOf(from, first + from.length()), "ambiguous fixture fragment: " + from);
        return yaml.substring(0, first) + to + yaml.substring(first + from.length());
    }

    private AdmissionPolicyException rejects(String yaml, String messageFragment) {
        AdmissionPolicyException error = assertThrows(AdmissionPolicyException.class, () -> load(yaml));
        assertTrue(error.getMessage().contains(messageFragment),
            () -> "expected '" + messageFragment + "' but got: " + error.getMessage());
        return error;
    }

    private static String independentOnly() {
        String yaml = example();
        int begin = yaml.indexOf("\n  staff:\n");
        assertTrue(begin > 0, "staff section not found in full-schema fixture");
        return yaml.substring(0, begin) + "\n";
    }

    @Test
    void inheritsPublicRulesAndRestrictionsTransitivelyAcrossFourLevels() throws Exception {
        AdmissionPolicySnapshot snapshot = load(example());
        assertEquals(4, snapshot.profiles().size());
        ModPolicy base = snapshot.profile("default").modPolicy();
        ModPolicy staff = snapshot.profile("staff").modPolicy();
        ModPolicy admin = snapshot.profile("admin").modPolicy();
        ModPolicy senior = snapshot.profile("senior").modPolicy();
        assertEquals(1, base.rulesByModId().size());
        assertEquals(2, staff.rulesByModId().size());
        assertEquals(3, admin.rulesByModId().size());
        assertEquals(3, senior.rulesByModId().size());
        assertTrue(staff.rulesByModId().containsKey("sodium"));
        assertTrue(staff.rulesByModId().containsKey("staffmod"));
        assertTrue(admin.rulesByModId().containsKey("adminmod"));
        assertTrue(senior.rulesByModId().containsKey("sodium"));
        assertEquals(ModPolicyMode.ALLOWLIST, senior.mode());
        assertEquals(OriginPolicyAction.DENY, senior.directoryAction());
        assertEquals(base.baselineModIds(), senior.baselineModIds());
        assertTrue(senior.requiredByModId().containsKey("helpermod"));
        assertEquals("require-helper", senior.requiredByModId().get("helpermod").id());
        assertEquals(0, snapshot.profile("default").priority());
        assertEquals(100, snapshot.profile("staff").priority());
        assertEquals(200, snapshot.profile("admin").priority());
        assertEquals(300, snapshot.profile("senior").priority());
        assertEquals(ClientAction.REQUIRE_CERBERUS,
            snapshot.profile("senior").clientPolicy().configuredAction(ClientClassification.JAVA_FABRIC));
    }

    @Test
    void inheritedArtifactVersionsAndSha256StillMatchExactly() throws Exception {
        var profiles = load(example()).profiles();
        var baselineRule = profiles.get("default").modPolicy().rulesByModId().get("sodium");
        var inheritedRule = profiles.get("admin").modPolicy().rulesByModId().get("sodium");
        ArtifactAcceptance acceptance = inheritedRule.acceptances().getFirst();
        assertEquals(baselineRule.acceptances().getFirst().version().expression(), acceptance.version().expression());
        assertEquals(ArtifactVerification.HASH_REQUIRED, acceptance.verification());
        assertEquals(ArtifactAcceptance.Match.MATCH, acceptance.match(
            new ManifestEntry("sodium", "0.9.1", null, OriginKind.ARCHIVE, new ArtifactSha256("a".repeat(64)))));
        assertEquals(ArtifactAcceptance.Match.VERSION_MISMATCH, acceptance.match(
            new ManifestEntry("sodium", "0.9.2", null, OriginKind.ARCHIVE, new ArtifactSha256("a".repeat(64)))));
        assertEquals(ArtifactAcceptance.Match.ARTIFACT_MISMATCH, acceptance.match(
            new ManifestEntry("sodium", "0.9.1", null, OriginKind.ARCHIVE, new ArtifactSha256("b".repeat(64)))));
        assertEquals(ArtifactAcceptance.Match.ARTIFACT_MISMATCH, acceptance.match(
            new ManifestEntry("sodium", "0.9.1", null, OriginKind.ARCHIVE)));
        assertEquals(ArtifactVerification.VERSION_ONLY,
            profiles.get("staff").modPolicy().requiredByModId().get("helpermod").acceptances().getFirst().verification());
    }

    @Test
    void selectsExactlyOneStaffProfileEvenWithBothPermissions() throws Exception {
        AdmissionPolicySnapshot snapshot = load(example());
        AdmissionPermissionSnapshot permissions = new AdmissionPermissionSnapshot("test", Map.of(
            AdmissionPermissions.profile("default"), true,
            AdmissionPermissions.profile("staff"), true));
        ResolvedAdmissionProfile resolved = AdmissionProfileResolver.resolve(snapshot,
            UUID.fromString("11111111-1111-1111-1111-111111111111"), permissions);
        assertEquals("staff", resolved.profile().id());
        assertEquals(ProfileResolutionSource.PROVIDER, resolved.source());
        assertTrue(resolved.profile().modPolicy().rulesByModId().containsKey("sodium"));
        assertTrue(resolved.profile().modPolicy().rulesByModId().containsKey("staffmod"));
        assertFalse(resolved.profile().modPolicy().rulesByModId().containsKey("adminmod"));
        assertEquals("default", AdmissionProfileResolver.resolve(snapshot, UUID.randomUUID(),
            AdmissionPermissionSnapshot.none()).profile().id());
    }

    @Test
    void allowsExplicitSameRuleIdAndModIdOverrideWithoutMutatingParent() throws Exception {
        String yaml = replaceOnce(example(),
            "          mod: adminmod\n          action: ALLOW",
            "          mod: adminmod\n          action: ALLOW\n"
                + "        allow-sodium:\n          override: true\n          mod: sodium\n          action: DENY");
        var snapshot = load(yaml);
        assertEquals(ModRuleAction.DENY, snapshot.profile("admin").modPolicy().rulesByModId().get("sodium").action());
        assertEquals(ModRuleAction.DENY, snapshot.profile("senior").modPolicy().rulesByModId().get("sodium").action());
        assertEquals(ModRuleAction.ALLOW, snapshot.profile("staff").modPolicy().rulesByModId().get("sodium").action());
        assertEquals(ModRuleAction.ALLOW, snapshot.profile("default").modPolicy().rulesByModId().get("sodium").action());
        assertTrue(snapshot.profile("admin").modPolicy().requiredByModId().containsKey("helpermod"));
    }

    @Test
    void permitsPartialChildClientOriginAndBrandOverrides() throws Exception {
        String yaml = replaceOnce(example(), "  staff:\n    inherit: default\n    priority: 100",
            "  staff:\n    inherit: default\n    priority: 100\n"
                + "    clients:\n      optifine: DENY\n"
                + "    unknown-brands:\n      brands: [staffclient]\n"
                + "    mods:\n      origins:\n        directory: ALLOW");
        // The child already declares mods.rules: move that rule collection beneath the same mods map.
        yaml = replaceOnce(yaml, "    mods:\n      rules:\n        allow-staffmod:",
            "      rules:\n        allow-staffmod:");
        AdmissionPolicySnapshot snapshot = load(yaml);
        var staff = snapshot.profile("staff");
        assertEquals(ClientAction.DENY, staff.clientPolicy().configuredAction(ClientClassification.JAVA_OPTIFINE));
        assertEquals(ClientAction.ALLOW, snapshot.profile("default").clientPolicy()
            .configuredAction(ClientClassification.JAVA_OPTIFINE));
        assertEquals(ClientAction.REQUIRE_CERBERUS,
            staff.clientPolicy().configuredAction(ClientClassification.JAVA_FABRIC));
        assertEquals(ClientAction.ALLOW,
            staff.clientPolicy().actionFor(ClientClassification.JAVA_UNKNOWN, "staffclient"));
        assertEquals(ClientAction.DENY,
            snapshot.profile("default").clientPolicy().actionFor(ClientClassification.JAVA_UNKNOWN, "staffclient"));
        assertEquals(OriginPolicyAction.ALLOW, staff.modPolicy().directoryAction());
        assertEquals(OriginPolicyAction.DENY, staff.modPolicy().mixedOrUnknownAction());
    }

    @Test
    void explicitRequiredOverrideRetainsMandatoryStatus() throws Exception {
        String yaml = replaceOnce(example(),
            "  staff:\n    inherit: default\n    priority: 100\n    mods:\n      rules:",
            "  staff:\n    inherit: default\n    priority: 100\n    mods:\n"
                + "      required:\n        require-helper:\n          override: true\n"
                + "          mod: helpermod\n          accept:\n"
                + "            - version: '2.0'\n              verification: VERSION_ONLY\n"
                + "      rules:");
        AdmissionPolicySnapshot snapshot = load(yaml);
        RequiredModRule inherited = snapshot.profile("staff").modPolicy().requiredByModId().get("helpermod");
        assertNotNull(inherited);
        assertEquals("2.0", inherited.acceptances().getFirst().version().expression());
        assertEquals("1.0", snapshot.profile("default").modPolicy().requiredByModId()
            .get("helpermod").acceptances().getFirst().version().expression());
        assertTrue(snapshot.profile("senior").modPolicy().requiredByModId().containsKey("helpermod"));
    }

    @Test
    void rejectsUnknownParent() {
        rejects(replaceOnce(example(), "inherit: default", "inherit: missing"), "unknown profile");
    }

    @Test
    void rejectsSelfInheritance() {
        rejects(replaceOnce(example(), "inherit: default", "inherit: staff"), "cannot inherit itself");
    }

    @Test
    void rejectsIndirectCycle() {
        rejects(replaceOnce(example(), "  default:\n    priority: 0",
            "  default:\n    inherit: senior\n    priority: 0"), "inheritance cycle");
    }

    @Test
    void rejectsNullListAndNumericParentValues() {
        rejects(replaceOnce(example(), "inherit: default", "inherit: null"), "non-blank string");
        rejects(replaceOnce(example(), "inherit: default", "inherit: [default]"), "non-blank string");
        rejects(replaceOnce(example(), "inherit: default", "inherit: 123"), "non-blank string");
    }

    @Test
    void rejectsMissingLocalPriority() {
        rejects(replaceOnce(example(), "    inherit: default\n    priority: 100", "    inherit: default"),
            "missing required key 'priority'");
    }

    private static String withExtraChain(int additions, boolean descendantsDeclaredFirst) {
        StringBuilder extraProfiles = new StringBuilder();
        String parent = "senior";
        for (int i = 0; i < additions; i++) {
            extraProfiles.append("  extra").append(i).append(":\n    inherit: ")
                .append(parent).append("\n    priority: ").append(400 + i).append('\n');
            parent = "extra" + i;
        }
        return descendantsDeclaredFirst
            ? replaceOnce(example(), "profiles:\n", "profiles:\n" + extraProfiles)
            : example() + extraProfiles;
    }

    @Test
    void acceptsExactlySixteenInheritanceLevelsRegardlessOfDeclarationOrder() throws Exception {
        assertEquals(16, load(withExtraChain(12, false)).profiles().size());
        assertEquals(16, load(withExtraChain(12, true)).profiles().size());
    }

    @Test
    void rejectsDeepChainBeyondSixteenProfiles() {
        // Cover cached-ancestor resolution and reverse declaration order.
        rejects(withExtraChain(13, false), "exceeds 16 levels");
        rejects(withExtraChain(13, true), "exceeds 16 levels");
    }

    @Test
    void rejectsSilentInheritedRuleReplacement() {
        String yaml = replaceOnce(example(), "allow-staffmod:", "allow-sodium:");
        yaml = replaceOnce(yaml, "mod: staffmod", "mod: sodium");
        rejects(yaml, "use override: true");
    }

    @Test
    void rejectsSameModDifferentRuleId() {
        rejects(replaceOnce(example(), "mod: staffmod", "mod: sodium"), "duplicates inherited mod");
    }

    @Test
    void rejectsOverrideWithoutInheritedTarget() {
        rejects(replaceOnce(example(), "mod: staffmod",
            "override: true\n          mod: staffmod"), "override=true without an inherited rule");
    }

    @Test
    void rejectsOverrideChangingTargetMod() {
        String yaml = replaceOnce(example(), "allow-staffmod:", "allow-sodium:\n          override: true");
        rejects(yaml, "override changes inherited mod");
    }

    @Test
    void rejectsSameRuleIdAcrossRequiredAndExplicitCollections() {
        String yaml = replaceOnce(example(), "allow-staffmod:", "require-helper:");
        rejects(yaml, "duplicates inherited rule id");
    }

    @Test
    void retainsIndependentRootProfileBehavior() throws Exception {
        AdmissionPolicySnapshot snapshot = load(independentOnly());
        assertEquals(1, snapshot.profiles().size());
        assertEquals(1, snapshot.defaultProfile().modPolicy().rulesByModId().size());
        assertTrue(snapshot.defaultProfile().modPolicy().requiredByModId().containsKey("helpermod"));
    }

    @Test
    void rejectsRootWithoutRequiredSections() {
        String root = independentOnly();
        int start = root.indexOf("    clients:\n");
        int end = root.indexOf("    unknown-brands:\n", start);
        assertTrue(start >= 0 && end > start);
        rejects(root.substring(0, start) + root.substring(end), "missing required key 'clients'");
    }
}
