package com.badwolfmc.guardian.core.policy;

import com.badwolfmc.guardian.core.AdmissionPermissions;
import com.badwolfmc.guardian.core.ClientAction;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.protocol.Manifest;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.OriginKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Platform-neutral deterministic Guardian Admission policy evaluator. */
public final class AdmissionPolicyEvaluator {
    public ClientPolicyResult evaluateClient(
        ResolvedAdmissionProfile resolved,
        ClientClassification classification,
        String rawBrand
    ) {
        Objects.requireNonNull(resolved, "resolved");
        Objects.requireNonNull(classification, "classification");

        ClientAction action = resolved.profile().clientPolicy().actionFor(classification, rawBrand);
        if (action == ClientAction.REQUIRE_CERBERUS) {
            return new ClientPolicyResult(action, null);
        }

        AdmissionPermissionSnapshot permissions = resolved.permissions();
        boolean bypass = permissions.has(AdmissionPermissions.CLIENT_BYPASS)
            || permissions.has(AdmissionPermissions.clientBypass(classification.policyKey()));

        if (action == ClientAction.DENY && bypass) {
            return new ClientPolicyResult(
                ClientAction.ALLOW,
                GuardianDecision.allow(
                    DecisionReason.CLIENT_POLICY_BYPASSED,
                    "profile=" + resolved.profile().id() + ", client=" + classification.policyKey()
                )
            );
        }

        if (action == ClientAction.DENY) {
            return new ClientPolicyResult(
                action,
                GuardianDecision.deny(
                    DecisionReason.CLIENT_DENIED,
                    "profile=" + resolved.profile().id() + ", client=" + classification.policyKey()
                )
            );
        }

        DecisionReason reason = switch (classification) {
            case BEDROCK -> DecisionReason.BEDROCK_POLICY;
            case JAVA_VANILLA -> DecisionReason.VANILLA_POLICY;
            case JAVA_OPTIFINE -> DecisionReason.OPTIFINE_POLICY;
            case JAVA_UNKNOWN -> DecisionReason.UNKNOWN_BRAND_POLICY;
            case JAVA_FABRIC -> DecisionReason.CLIENT_POLICY_ALLOWED;
        };
        return new ClientPolicyResult(
            action,
            GuardianDecision.allow(reason, "profile=" + resolved.profile().id())
        );
    }

    /**
     * Evaluates only structurally valid protocol manifests. Protocol/session/integrity validation must happen first.
     * Bypasses here therefore cannot exempt unsupported protocol, nonce/session, malformed/canonicalization, replay,
     * payload-limit, or proxy-assertion failures.
     */
    public PolicyEvaluation evaluateManifest(ResolvedAdmissionProfile resolved, Manifest manifest) {
        Objects.requireNonNull(resolved, "resolved");
        Objects.requireNonNull(manifest, "manifest");

        ModPolicy policy = resolved.profile().modPolicy();
        Map<String, ManifestEntry> entries = new LinkedHashMap<>();
        for (ManifestEntry entry : manifest.entries()) {
            entries.put(entry.modId(), entry);
        }

        ArrayList<PolicyViolation> raw = new ArrayList<>();
        evaluateRequired(policy, entries, raw);
        for (ManifestEntry entry : manifest.entries()) {
            evaluateEntry(policy, entry, raw);
        }

        List<PolicyViolation> unique = deduplicate(raw);
        ArrayList<PolicyViolation> active = new ArrayList<>();
        ArrayList<PolicyViolation> bypassed = new ArrayList<>();
        AdmissionPermissionSnapshot permissions = resolved.permissions();
        for (PolicyViolation violation : unique) {
            boolean mayBypass = permissions.has(AdmissionPermissions.MOD_BYPASS)
                || (!violation.modId().isEmpty()
                    && permissions.has(AdmissionPermissions.modBypass(violation.modId())));
            (mayBypass ? bypassed : active).add(violation);
        }

        if (!active.isEmpty()) {
            PolicyViolation first = active.getFirst();
            LinkedHashMap<String, String> context = new LinkedHashMap<>();
            context.put("policy_violation", first.code().name());
            context.put("mod_id", first.modId());
            ManifestEntry problemEntry = entries.get(first.modId());
            if (problemEntry != null) context.put("version", problemEntry.version());
            return new PolicyEvaluation(
                GuardianDecision.deny(
                    DecisionReason.MANIFEST_DENIED,
                    "profile=" + resolved.profile().id() + ", " + summarize(first)
                        + (active.size() > 1 ? ", additionalViolations=" + (active.size() - 1) : ""),
                    context
                ),
                active,
                bypassed
            );
        }

        DecisionReason reason = bypassed.isEmpty()
            ? DecisionReason.CERBERUS_VERIFIED
            : DecisionReason.MOD_POLICY_BYPASSED;
        String detail = "profile=" + resolved.profile().id() + ", mods=" + manifest.entries().size();
        if (!bypassed.isEmpty()) detail += ", bypassedViolations=" + bypassed.size();
        return new PolicyEvaluation(
            GuardianDecision.allow(reason, detail),
            List.of(),
            bypassed
        );
    }

    private static void evaluateRequired(
        ModPolicy policy,
        Map<String, ManifestEntry> entries,
        List<PolicyViolation> violations
    ) {
        for (RequiredModRule required : policy.requiredByModId().values()) {
            ManifestEntry entry = entries.get(required.modId());
            if (entry == null) {
                violations.add(new PolicyViolation(
                    PolicyViolationCode.REQUIRED_MOD_MISSING,
                    required.id(),
                    required.modId(),
                    "required mod is not present"
                ));
                continue;
            }
            matchAcceptances(required.id(), entry, required.acceptances(), violations);
        }
    }

    private static void evaluateEntry(ModPolicy policy, ManifestEntry entry, List<PolicyViolation> violations) {
        ModRule explicit = policy.rulesByModId().get(entry.modId());
        if (explicit != null) {
            if (explicit.action() == ModRuleAction.DENY) {
                violations.add(new PolicyViolation(
                    PolicyViolationCode.EXPLICIT_MOD_DENY,
                    explicit.id(), entry.modId(), "mod is explicitly denied"
                ));
                // Explicit DENY is already the strongest membership result for this mod. Do not also
                // report redundant UNLISTED/origin membership violations for the same top-level entry.
                return;
            }
            matchAcceptances(explicit.id(), entry, explicit.acceptances(), violations);
        }

        if (entry.parentModId() != null || entry.originKind() == OriginKind.NESTED) {
            // Nested entries remain fully visible and can be explicitly constrained/denied/required, but they are
            // not independently subject to unlisted membership. If installed top-level, the same ID has no parent
            // and becomes independently policy-addressable.
            return;
        }
        if (entry.originKind() == OriginKind.BUILTIN) {
            return;
        }

        if (entry.originKind() == OriginKind.DIRECTORY
            && policy.directoryAction() == OriginPolicyAction.DENY) {
            violations.add(new PolicyViolation(
                PolicyViolationCode.DIRECTORY_ORIGIN_DENIED,
                "", entry.modId(), "top-level DIRECTORY development origin is denied"
            ));
        }
        if (entry.originKind() == OriginKind.MIXED_OR_UNKNOWN
            && policy.mixedOrUnknownAction() == OriginPolicyAction.DENY) {
            violations.add(new PolicyViolation(
                PolicyViolationCode.MIXED_OR_UNKNOWN_ORIGIN_DENIED,
                "", entry.modId(), "top-level MIXED_OR_UNKNOWN origin is denied"
            ));
        }

        // Required mods count as permitted for allowlist membership. Baseline/bootstrap/runtime entries are
        // explicit configuration, not filename guesses, and likewise do not need duplicate ALLOW rules.
        if (policy.requiredByModId().containsKey(entry.modId()) || policy.baselineModIds().contains(entry.modId())) {
            return;
        }

        if (policy.mode() == ModPolicyMode.ALLOWLIST) {
            if (explicit == null || explicit.action() != ModRuleAction.ALLOW) {
                violations.add(new PolicyViolation(
                    PolicyViolationCode.UNLISTED_MOD,
                    "", entry.modId(), "top-level mod is unlisted in ALLOWLIST mode"
                ));
            }
        }
    }

    private static void matchAcceptances(
        String ruleId,
        ManifestEntry entry,
        List<ArtifactAcceptance> acceptances,
        List<PolicyViolation> violations
    ) {
        if (acceptances.isEmpty()) return;
        boolean versionMatched = false;
        for (ArtifactAcceptance acceptance : acceptances) {
            ArtifactAcceptance.Match match = acceptance.match(entry);
            if (match == ArtifactAcceptance.Match.MATCH) return;
            if (match == ArtifactAcceptance.Match.ARTIFACT_MISMATCH) versionMatched = true;
        }
        if (versionMatched) {
            violations.add(new PolicyViolation(
                PolicyViolationCode.ARTIFACT_NOT_ACCEPTED,
                ruleId, entry.modId(),
                "version matched but observed origin/SHA-256 is not accepted"
            ));
        } else {
            violations.add(new PolicyViolation(
                PolicyViolationCode.VERSION_NOT_ACCEPTED,
                ruleId, entry.modId(),
                "version '" + entry.version() + "' does not satisfy any accepted predicate"
            ));
        }
    }

    private static List<PolicyViolation> deduplicate(List<PolicyViolation> input) {
        Set<String> keys = new LinkedHashSet<>();
        ArrayList<PolicyViolation> result = new ArrayList<>();
        for (PolicyViolation violation : input) {
            String key = violation.code() + "\u0000" + violation.ruleId() + "\u0000" + violation.modId();
            if (keys.add(key)) result.add(violation);
        }
        return List.copyOf(result);
    }

    private static String summarize(PolicyViolation violation) {
        StringBuilder result = new StringBuilder(violation.code().name());
        if (!violation.modId().isEmpty()) result.append(" mod=").append(violation.modId());
        if (!violation.ruleId().isEmpty()) result.append(" rule=").append(violation.ruleId());
        if (!violation.detail().isEmpty()) result.append(" (").append(violation.detail()).append(')');
        return result.toString();
    }
}
