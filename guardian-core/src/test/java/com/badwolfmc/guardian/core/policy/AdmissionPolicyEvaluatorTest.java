package com.badwolfmc.guardian.core.policy;

import com.badwolfmc.guardian.core.*;
import com.badwolfmc.guardian.protocol.ArtifactSha256;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Manifest;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.OriginKind;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionPolicyEvaluatorTest {
    private static final ArtifactSha256 HASH_A = new ArtifactSha256("a".repeat(64));
    private static final ArtifactSha256 HASH_B = new ArtifactSha256("b".repeat(64));
    private final AdmissionPolicyEvaluator evaluator = new AdmissionPolicyEvaluator();

    @Test
    void clientClassesUseSharedActionsAndUnknownBrandRulesOnlyForUnknown() {
        ResolvedAdmissionProfile defaults = resolved(clientPolicy(
            ClientAction.ALLOW, ClientAction.ALLOW, ClientAction.ALLOW,
            ClientAction.REQUIRE_CERBERUS, ClientAction.DENY,
            BrandRuleMode.ALLOWLIST, Set.of("special-client")), denylist(), Map.of());

        assertEquals(DecisionReason.BEDROCK_POLICY,
            evaluator.evaluateClient(defaults, ClientClassification.BEDROCK, "fabric").terminalDecision().reason());
        ResolvedAdmissionProfile bedrockDenied = resolved(clientPolicy(
            ClientAction.DENY, ClientAction.ALLOW, ClientAction.ALLOW,
            ClientAction.REQUIRE_CERBERUS, ClientAction.DENY,
            BrandRuleMode.ALLOWLIST, Set.of()), denylist(), Map.of());
        assertEquals(DecisionOutcome.DENY,
            evaluator.evaluateClient(bedrockDenied, ClientClassification.BEDROCK, "Geyser").terminalDecision().outcome(),
            "Bedrock remains subject to configurable shared client policy rather than a bypass");
        assertEquals(DecisionReason.VANILLA_POLICY,
            evaluator.evaluateClient(defaults, ClientClassification.JAVA_VANILLA, "vanilla").terminalDecision().reason());
        assertEquals(DecisionReason.OPTIFINE_POLICY,
            evaluator.evaluateClient(defaults, ClientClassification.JAVA_OPTIFINE, "optifine").terminalDecision().reason());
        assertEquals(ClientAction.REQUIRE_CERBERUS,
            evaluator.evaluateClient(defaults, ClientClassification.JAVA_FABRIC, "special-client").action(),
            "unknown-brand allow rules must never turn positively identified Fabric into ALLOW");
        assertEquals(DecisionOutcome.ALLOW,
            evaluator.evaluateClient(defaults, ClientClassification.JAVA_UNKNOWN, "SPECIAL-CLIENT").terminalDecision().outcome());
        assertEquals(DecisionOutcome.DENY,
            evaluator.evaluateClient(defaults, ClientClassification.JAVA_UNKNOWN, "other").terminalDecision().outcome());
    }

    @Test
    void fabricClientClassSupportsConfiguredAllowDenyAndRequireCerberusActions() {
        ResolvedAdmissionProfile allowed = resolved(clientPolicy(
            ClientAction.ALLOW, ClientAction.ALLOW, ClientAction.ALLOW,
            ClientAction.ALLOW, ClientAction.DENY,
            BrandRuleMode.ALLOWLIST, Set.of()), denylist(), Map.of());
        assertEquals(DecisionOutcome.ALLOW,
            evaluator.evaluateClient(allowed, ClientClassification.JAVA_FABRIC, "fabric").terminalDecision().outcome());

        ResolvedAdmissionProfile denied = resolved(clientPolicy(
            ClientAction.ALLOW, ClientAction.ALLOW, ClientAction.ALLOW,
            ClientAction.DENY, ClientAction.DENY,
            BrandRuleMode.ALLOWLIST, Set.of()), denylist(), Map.of());
        assertEquals(DecisionOutcome.DENY,
            evaluator.evaluateClient(denied, ClientClassification.JAVA_FABRIC, "fabric").terminalDecision().outcome());

        ResolvedAdmissionProfile required = resolved(clientPolicy(
            ClientAction.ALLOW, ClientAction.ALLOW, ClientAction.ALLOW,
            ClientAction.REQUIRE_CERBERUS, ClientAction.DENY,
            BrandRuleMode.ALLOWLIST, Set.of()), denylist(), Map.of());
        assertEquals(ClientAction.REQUIRE_CERBERUS,
            evaluator.evaluateClient(required, ClientClassification.JAVA_FABRIC, "fabric").action());
    }

    @Test
    void unknownBrandDenylistCanDenyOneUnknownWhileAllowingOtherUnknown() {
        ResolvedAdmissionProfile profile = resolved(clientPolicy(
            ClientAction.ALLOW, ClientAction.ALLOW, ClientAction.ALLOW,
            ClientAction.REQUIRE_CERBERUS, ClientAction.ALLOW,
            BrandRuleMode.DENYLIST, Set.of("bad-brand")), denylist(), Map.of());
        assertEquals(DecisionOutcome.DENY,
            evaluator.evaluateClient(profile, ClientClassification.JAVA_UNKNOWN, "BAD-BRAND").terminalDecision().outcome());
        assertEquals(DecisionOutcome.ALLOW,
            evaluator.evaluateClient(profile, ClientClassification.JAVA_UNKNOWN, "odd-but-okay").terminalDecision().outcome());
    }

    @Test
    void clientBypassIsPolicyScopedAndCannotTurnRequireCerberusIntoAllow() {
        ResolvedAdmissionProfile profile = resolved(clientPolicy(
            ClientAction.ALLOW, ClientAction.ALLOW, ClientAction.ALLOW,
            ClientAction.REQUIRE_CERBERUS, ClientAction.DENY,
            BrandRuleMode.ALLOWLIST, Set.of()), denylist(), Map.of(
                AdmissionPermissions.CLIENT_BYPASS, true));
        assertEquals(DecisionReason.CLIENT_POLICY_BYPASSED,
            evaluator.evaluateClient(profile, ClientClassification.JAVA_UNKNOWN, "mystery").terminalDecision().reason());
        assertEquals(ClientAction.REQUIRE_CERBERUS,
            evaluator.evaluateClient(profile, ClientClassification.JAVA_FABRIC, "fabric").action());
    }

    @Test
    void allowlistTreatsRequiredAndBaselineAsPermittedAndNestedAsContained() {
        RequiredModRule required = new RequiredModRule("require-fabric-api", "fabric-api", List.of());
        ModRule sodium = new ModRule("allow-sodium", "sodium", ModRuleAction.ALLOW, List.of());
        ModPolicy policy = new ModPolicy(ModPolicyMode.ALLOWLIST, OriginPolicyAction.DENY,
            OriginPolicyAction.DENY, Set.of("fabricloader", "cerberus"),
            Map.of("fabric-api", required), Map.of("sodium", sodium));
        Manifest manifest = manifest(
            archive("cerberus", "0.1.0", HASH_A),
            archive("fabric-api", "0.160.0+26.2", HASH_A),
            nested("fabric-api-base", "1", "fabric-api"),
            nested("deep-library", "1", "fabric-api-base"),
            archive("fabricloader", "0.19.5", HASH_A),
            builtin("java", "25"), builtin("minecraft", "26.2"),
            archive("sodium", "0.9.1+mc26.2", HASH_A));

        PolicyEvaluation result = evaluator.evaluateManifest(resolved(AdmissionPolicy.defaults(), policy, Map.of()), manifest);
        assertEquals(DecisionOutcome.ALLOW, result.decision().outcome());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void sameIdBecomesUnlistedWhenInstalledTopLevelInsteadOfNested() {
        ModPolicy policy = new ModPolicy(ModPolicyMode.ALLOWLIST, OriginPolicyAction.DENY,
            OriginPolicyAction.DENY, Set.of("fabricloader", "cerberus"), Map.of(), Map.of());
        PolicyEvaluation nested = evaluator.evaluateManifest(resolved(AdmissionPolicy.defaults(), policy, Map.of()),
            manifest(archive("cerberus", "1", HASH_A), archive("fabricloader", "1", HASH_A),
                archive("parent", "1", HASH_A), nested("library", "1", "parent")));
        assertTrue(nested.violations().stream().noneMatch(v -> v.modId().equals("library")),
            "nested library is visible but not independently subject to unlisted membership");

        PolicyEvaluation topLevel = evaluator.evaluateManifest(resolved(AdmissionPolicy.defaults(), policy, Map.of()),
            manifest(archive("cerberus", "1", HASH_A), archive("fabricloader", "1", HASH_A),
                archive("library", "1", HASH_A)));
        assertTrue(topLevel.violations().stream().anyMatch(v ->
            v.code() == PolicyViolationCode.UNLISTED_MOD && v.modId().equals("library")));
        assertEquals("UNLISTED_MOD", topLevel.decision().context().get("policy_violation"));
        assertEquals("library", topLevel.decision().context().get("mod_id"));
    }

    @Test
    void denylistAllowsUnlistedButExplicitDenyAndRequiredRulesRemainOrthogonal() {
        RequiredModRule required = new RequiredModRule("require-api", "fabric-api", List.of());
        ModRule denyRule = new ModRule("deny-baritone", "baritone", ModRuleAction.DENY, List.of());
        ModPolicy policy = new ModPolicy(ModPolicyMode.DENYLIST, OriginPolicyAction.DENY,
            OriginPolicyAction.DENY, Set.of(), Map.of("fabric-api", required), Map.of("baritone", denyRule));
        PolicyEvaluation missing = evaluator.evaluateManifest(resolved(AdmissionPolicy.defaults(), policy, Map.of()),
            manifest(archive("random-mod", "1", HASH_A)));
        assertTrue(missing.violations().stream().anyMatch(v -> v.code() == PolicyViolationCode.REQUIRED_MOD_MISSING));
        assertTrue(missing.violations().stream().noneMatch(v -> v.modId().equals("random-mod")));

        PolicyEvaluation denied = evaluator.evaluateManifest(resolved(AdmissionPolicy.defaults(), policy, Map.of()),
            manifest(archive("fabric-api", "1", HASH_A), archive("baritone", "1", HASH_A)));
        assertEquals(1, denied.violations().size(),
            "explicit DENY must not also produce a redundant unlisted violation for the same mod");
        assertEquals(PolicyViolationCode.EXPLICIT_MOD_DENY, denied.violations().getFirst().code());
    }

    @Test
    void requiredModVersionPredicateIsEnforcedWhileRemainingAllowlistPermitted() {
        RequiredModRule required = new RequiredModRule(
            "require-api",
            "fabric-api",
            List.of(new ArtifactAcceptance(
                VersionPredicate.parse("0.160.*"),
                ArtifactVerification.VERSION_ONLY,
                Set.of(),
                Map.of()
            ))
        );
        ModPolicy policy = new ModPolicy(
            ModPolicyMode.ALLOWLIST,
            OriginPolicyAction.DENY,
            OriginPolicyAction.DENY,
            Set.of(),
            Map.of("fabric-api", required),
            Map.of()
        );
        ResolvedAdmissionProfile profile = resolved(AdmissionPolicy.defaults(), policy, Map.of());

        assertEquals(DecisionOutcome.ALLOW, evaluator.evaluateManifest(profile,
            manifest(archive("fabric-api", "0.160.0+26.2", HASH_A))).decision().outcome());
        PolicyEvaluation wrong = evaluator.evaluateManifest(profile,
            manifest(archive("fabric-api", "0.161.0+26.2", HASH_A)));
        assertTrue(wrong.violations().stream().anyMatch(v ->
            v.code() == PolicyViolationCode.VERSION_NOT_ACCEPTED && v.modId().equals("fabric-api")));
        assertEquals("0.161.0+26.2", wrong.decision().context().get("version"));
    }

    @Test
    void minimumVersionRuleAcceptsFabricBuildMetadata() {
        ModRule rule = new ModRule(
            "allow-bettergrass",
            "bettergrass",
            ModRuleAction.ALLOW,
            List.of(new ArtifactAcceptance(
                VersionPredicate.parse(">=1.8.7"),
                ArtifactVerification.VERSION_ONLY,
                Set.of(),
                Map.of()
            ))
        );
        ModPolicy policy = new ModPolicy(
            ModPolicyMode.ALLOWLIST,
            OriginPolicyAction.DENY,
            OriginPolicyAction.DENY,
            Set.of(),
            Map.of(),
            Map.of("bettergrass", rule)
        );
        ResolvedAdmissionProfile profile = resolved(AdmissionPolicy.defaults(), policy, Map.of());

        assertEquals(DecisionOutcome.ALLOW, evaluator.evaluateManifest(profile,
            manifest(archive("bettergrass", "1.8.7+fabric.26.2", HASH_A))).decision().outcome());
        assertEquals(DecisionOutcome.ALLOW, evaluator.evaluateManifest(profile,
            manifest(archive("bettergrass", "2.0.0+fabric.26.2", HASH_A))).decision().outcome());

        PolicyEvaluation tooOld = evaluator.evaluateManifest(profile,
            manifest(archive("bettergrass", "1.8.6+fabric.26.2", HASH_A)));
        assertTrue(tooOld.violations().stream().anyMatch(v ->
            v.code() == PolicyViolationCode.VERSION_NOT_ACCEPTED && v.modId().equals("bettergrass")));
    }

    @Test
    void versionOnlyAndExactHashRulesSupportSeveralVersionsAndHashes() {
        ArtifactAcceptance one = new ArtifactAcceptance(VersionPredicate.parse("1.0"),
            ArtifactVerification.HASH_REQUIRED, Set.of(HASH_A, HASH_B), Map.of());
        ArtifactAcceptance two = new ArtifactAcceptance(VersionPredicate.parse("2.*"),
            ArtifactVerification.VERSION_ONLY, Set.of(), Map.of());
        ModRule rule = new ModRule("allow-example", "example", ModRuleAction.ALLOW, List.of(one, two));
        ModPolicy policy = new ModPolicy(ModPolicyMode.ALLOWLIST, OriginPolicyAction.DENY,
            OriginPolicyAction.DENY, Set.of(), Map.of(), Map.of("example", rule));
        ResolvedAdmissionProfile profile = resolved(AdmissionPolicy.defaults(), policy, Map.of());

        assertEquals(DecisionOutcome.ALLOW, evaluator.evaluateManifest(profile,
            manifest(archive("example", "1.0", HASH_B))).decision().outcome());
        assertEquals(DecisionOutcome.ALLOW, evaluator.evaluateManifest(profile,
            manifest(archive("example", "2.7-custom", HASH_A))).decision().outcome());
        PolicyEvaluation badHash = evaluator.evaluateManifest(profile,
            manifest(archive("example", "1.0", new ArtifactSha256("c".repeat(64)))));
        assertTrue(badHash.violations().stream().anyMatch(v -> v.code() == PolicyViolationCode.ARTIFACT_NOT_ACCEPTED));
        assertEquals("1.0", badHash.decision().context().get("version"));
        PolicyEvaluation badVersion = evaluator.evaluateManifest(profile,
            manifest(archive("example", "3.0", HASH_A)));
        assertTrue(badVersion.violations().stream().anyMatch(v -> v.code() == PolicyViolationCode.VERSION_NOT_ACCEPTED));
    }

    @Test
    void exactHashCannotBeSatisfiedByDirectoryOrMixedOriginAndOriginPolicyIsExplicit() {
        ArtifactAcceptance exact = new ArtifactAcceptance(VersionPredicate.parse("1.0"),
            ArtifactVerification.HASH_REQUIRED, Set.of(HASH_A), Map.of());
        ModRule rule = new ModRule("allow-dev", "example", ModRuleAction.ALLOW, List.of(exact));
        ModPolicy policy = new ModPolicy(ModPolicyMode.ALLOWLIST, OriginPolicyAction.DENY,
            OriginPolicyAction.DENY, Set.of(), Map.of(), Map.of("example", rule));
        PolicyEvaluation directory = evaluator.evaluateManifest(resolved(AdmissionPolicy.defaults(), policy, Map.of()),
            manifest(new ManifestEntry("example", "1.0", null, OriginKind.DIRECTORY)));
        assertTrue(directory.violations().stream().anyMatch(v -> v.code() == PolicyViolationCode.DIRECTORY_ORIGIN_DENIED));
        assertTrue(directory.violations().stream().anyMatch(v -> v.code() == PolicyViolationCode.ARTIFACT_NOT_ACCEPTED));

        PolicyEvaluation mixed = evaluator.evaluateManifest(resolved(AdmissionPolicy.defaults(), policy, Map.of()),
            manifest(new ManifestEntry("example", "1.0", null, OriginKind.MIXED_OR_UNKNOWN)));
        assertTrue(mixed.violations().stream().anyMatch(v -> v.code() == PolicyViolationCode.MIXED_OR_UNKNOWN_ORIGIN_DENIED));
    }

    @Test
    void explicitlyAllowedDevelopmentOriginsRemainPolicyAddressableWithoutPretendingToHaveArchiveIdentity() {
        ModRule allowDev = new ModRule("allow-dev", "example", ModRuleAction.ALLOW, List.of());
        ModPolicy policy = new ModPolicy(
            ModPolicyMode.ALLOWLIST,
            OriginPolicyAction.ALLOW,
            OriginPolicyAction.ALLOW,
            Set.of(),
            Map.of(),
            Map.of("example", allowDev)
        );
        ResolvedAdmissionProfile profile = resolved(AdmissionPolicy.defaults(), policy, Map.of());

        for (OriginKind origin : List.of(OriginKind.DIRECTORY, OriginKind.MIXED_OR_UNKNOWN)) {
            PolicyEvaluation result = evaluator.evaluateManifest(profile,
                manifest(new ManifestEntry("example", "1.0", null, origin)));
            assertEquals(DecisionOutcome.ALLOW, result.decision().outcome());
            assertTrue(result.violations().isEmpty());
        }
    }

    @Test
    void modBypassesApplyOnlyAfterPolicyViolationExists() {
        ModPolicy policy = new ModPolicy(ModPolicyMode.ALLOWLIST, OriginPolicyAction.DENY,
            OriginPolicyAction.DENY, Set.of(), Map.of(), Map.of());
        ResolvedAdmissionProfile profile = resolved(AdmissionPolicy.defaults(), policy,
            Map.of(AdmissionPermissions.modBypass("baritone"), true));
        PolicyEvaluation result = evaluator.evaluateManifest(profile, manifest(archive("baritone", "1", HASH_A)));
        assertEquals(DecisionOutcome.ALLOW, result.decision().outcome());
        assertEquals(DecisionReason.MOD_POLICY_BYPASSED, result.decision().reason());
        assertEquals(1, result.bypassedViolations().size());
    }


    @Test
    void identicalNormalizedInputsProduceIdenticalDecisionForEitherAdapter() {
        ArtifactAcceptance exact = new ArtifactAcceptance(VersionPredicate.parse("1.*"),
            ArtifactVerification.HASH_REQUIRED, Set.of(HASH_A), Map.of());
        ModRule rule = new ModRule("allow-example", "example", ModRuleAction.ALLOW, List.of(exact));
        ModPolicy policy = new ModPolicy(ModPolicyMode.ALLOWLIST, OriginPolicyAction.DENY,
            OriginPolicyAction.DENY, Set.of("fabricloader", "cerberus"), Map.of(), Map.of("example", rule));
        ResolvedAdmissionProfile resolved = resolved(AdmissionPolicy.defaults(), policy, Map.of());
        Manifest sameManifest = manifest(
            archive("cerberus", "0.1.0-phase3", HASH_A),
            archive("example", "1.4", HASH_A),
            archive("fabricloader", "0.19.5", HASH_A));

        GuardianDecision paperSuppliedInputs = evaluator.evaluateManifest(resolved, sameManifest).decision();
        GuardianDecision velocitySuppliedInputs = evaluator.evaluateManifest(resolved, sameManifest).decision();

        assertEquals(paperSuppliedInputs, velocitySuppliedInputs,
            "adapter identity must not participate in shared policy semantics");
    }

    @Test
    void policyBypassCannotDefeatProtocolIntegrityFailure() {
        byte[] expected = new byte[GuardianProtocol.NONCE_BYTES];
        byte[] wrong = new byte[GuardianProtocol.NONCE_BYTES];
        wrong[0] = 1;
        var response = new com.badwolfmc.guardian.protocol.Response(
            GuardianProtocol.VERSION, GuardianProtocol.REQUIRED_CAPABILITIES, wrong,
            manifest(archive("baritone", "1", HASH_A)));
        GuardianDecision integrity = ProtocolV1ResponseValidator.validate(expected, response);
        assertEquals(DecisionOutcome.DENY, integrity.outcome());
        assertEquals(DecisionReason.MANIFEST_INVALID, integrity.reason());

        ModPolicy policy = new ModPolicy(ModPolicyMode.ALLOWLIST, OriginPolicyAction.DENY,
            OriginPolicyAction.DENY, Set.of(), Map.of(), Map.of());
        ResolvedAdmissionProfile bypass = resolved(AdmissionPolicy.defaults(), policy,
            Map.of(AdmissionPermissions.MOD_BYPASS, true));
        assertEquals(DecisionOutcome.ALLOW,
            evaluator.evaluateManifest(bypass, response.manifest()).decision().outcome(),
            "the policy layer could bypass its own violation, proving integrity must remain an earlier gate");
        assertEquals(DecisionOutcome.DENY, integrity.outcome(),
            "protocol/session integrity remains non-bypassable because adapters never call policy after this denial");
    }

    private static AdmissionPolicy clientPolicy(
        ClientAction bedrock, ClientAction vanilla, ClientAction optifine,
        ClientAction fabric, ClientAction unknown, BrandRuleMode brandMode, Set<String> brands
    ) {
        EnumMap<ClientClassification, ClientAction> actions = new EnumMap<>(ClientClassification.class);
        actions.put(ClientClassification.BEDROCK, bedrock);
        actions.put(ClientClassification.JAVA_VANILLA, vanilla);
        actions.put(ClientClassification.JAVA_OPTIFINE, optifine);
        actions.put(ClientClassification.JAVA_FABRIC, fabric);
        actions.put(ClientClassification.JAVA_UNKNOWN, unknown);
        return new AdmissionPolicy(actions, new UnknownBrandPolicy(brandMode, brands));
    }

    private static ModPolicy denylist() {
        return new ModPolicy(ModPolicyMode.DENYLIST, OriginPolicyAction.DENY,
            OriginPolicyAction.DENY, Set.of(), Map.of(), Map.of());
    }

    private static ResolvedAdmissionProfile resolved(AdmissionPolicy clients, ModPolicy mods, Map<String, Boolean> permissions) {
        return new ResolvedAdmissionProfile(new AdmissionProfile("default", 0, clients, mods),
            new AdmissionPermissionSnapshot("test", permissions), ProfileResolutionSource.DEFAULT);
    }

    private static Manifest manifest(ManifestEntry... entries) {
        return new Manifest("26.2", "0.19.5", "0.1.0-phase3", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(entries));
    }

    private static ManifestEntry archive(String id, String version, ArtifactSha256 hash) {
        return new ManifestEntry(id, version, null, OriginKind.ARCHIVE, hash);
    }

    private static ManifestEntry nested(String id, String version, String parent) {
        return new ManifestEntry(id, version, parent, OriginKind.NESTED);
    }

    private static ManifestEntry builtin(String id, String version) {
        return new ManifestEntry(id, version, null, OriginKind.BUILTIN);
    }
}
