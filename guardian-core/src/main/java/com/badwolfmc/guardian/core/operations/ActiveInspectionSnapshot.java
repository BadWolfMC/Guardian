package com.badwolfmc.guardian.core.operations;

import com.badwolfmc.guardian.core.BedrockEvidence;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.core.policy.ProfileResolutionSource;
import com.badwolfmc.guardian.protocol.Manifest;
import com.badwolfmc.guardian.protocol.OriginKind;
import com.badwolfmc.guardian.protocol.Presence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable active-player inspection state.
 *
 * <p>The complete client manifest is intentionally not retained after Admission. Only counts plus a
 * bounded projection of policy-addressable top-level mods survive into the operational snapshot.</p>
 */
public record ActiveInspectionSnapshot(
    UUID playerId,
    String playerName,
    String backend,
    ClientClassification classification,
    String observedBrand,
    String profileId,
    ProfileResolutionSource profileSource,
    Presence cerberusPresence,
    GuardianDecision decision,
    int loaderKnownCount,
    int policyAddressableCount,
    List<InspectionMod> policyAddressableMods,
    BedrockEvidence bedrockEvidence,
    long admissionRuntimeGeneration
) {
    /** Ordinary inspect output is deliberately bounded even when protocol-v1 accepts larger manifests. */
    public static final int MAX_RETAINED_POLICY_MODS = 64;

    public ActiveInspectionSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        playerName = normalize(playerName, "<unknown>");
        backend = normalize(backend, "<none>");
        Objects.requireNonNull(classification, "classification");
        observedBrand = normalize(observedBrand, "<unknown>");
        profileId = normalize(profileId, "<unknown>");
        Objects.requireNonNull(profileSource, "profileSource");
        Objects.requireNonNull(decision, "decision");
        if (loaderKnownCount < 0) throw new IllegalArgumentException("loaderKnownCount must not be negative");
        if (policyAddressableCount < 0) throw new IllegalArgumentException("policyAddressableCount must not be negative");
        if (policyAddressableCount > loaderKnownCount) {
            throw new IllegalArgumentException("policyAddressableCount must not exceed loaderKnownCount");
        }
        policyAddressableMods = List.copyOf(Objects.requireNonNull(policyAddressableMods, "policyAddressableMods"));
        if (policyAddressableMods.size() > MAX_RETAINED_POLICY_MODS) {
            throw new IllegalArgumentException("policyAddressableMods exceeds retained inspection limit");
        }
        if (policyAddressableMods.size() > policyAddressableCount) {
            throw new IllegalArgumentException("retained policy mods exceed total policy-addressable count");
        }
        Objects.requireNonNull(bedrockEvidence, "bedrockEvidence");
        if (admissionRuntimeGeneration < 1) {
            throw new IllegalArgumentException("admissionRuntimeGeneration must be positive");
        }
    }

    public ActiveInspectionSnapshot(
        UUID playerId,
        String playerName,
        String backend,
        ClientClassification classification,
        String observedBrand,
        String profileId,
        ProfileResolutionSource profileSource,
        Presence cerberusPresence,
        GuardianDecision decision,
        Manifest manifest,
        BedrockEvidence bedrockEvidence,
        long admissionRuntimeGeneration
    ) {
        this(
            playerId,
            playerName,
            backend,
            classification,
            observedBrand,
            profileId,
            profileSource,
            cerberusPresence,
            decision,
            loaderKnownCount(manifest),
            policyAddressableCount(manifest),
            retainedPolicyMods(manifest),
            bedrockEvidence,
            admissionRuntimeGeneration
        );
    }

    /** Compatibility constructor for focused tests that do not model reload generations. */
    public ActiveInspectionSnapshot(
        UUID playerId,
        String playerName,
        String backend,
        ClientClassification classification,
        String observedBrand,
        String profileId,
        ProfileResolutionSource profileSource,
        Presence cerberusPresence,
        GuardianDecision decision,
        Manifest manifest,
        BedrockEvidence bedrockEvidence
    ) {
        this(playerId, playerName, backend, classification, observedBrand, profileId, profileSource,
            cerberusPresence, decision, manifest, bedrockEvidence, 1L);
    }

    public int omittedPolicyAddressableCount() {
        return policyAddressableCount - policyAddressableMods.size();
    }

    public boolean predatesRuntime(long currentRuntimeGeneration) {
        return admissionRuntimeGeneration != currentRuntimeGeneration;
    }

    public ActiveInspectionSnapshot withBackend(String newBackend) {
        return new ActiveInspectionSnapshot(
            playerId, playerName, newBackend, classification, observedBrand, profileId, profileSource,
            cerberusPresence, decision, loaderKnownCount, policyAddressableCount, policyAddressableMods,
            bedrockEvidence, admissionRuntimeGeneration);
    }

    private static int loaderKnownCount(Manifest manifest) {
        return manifest == null ? 0 : manifest.entries().size();
    }

    private static int policyAddressableCount(Manifest manifest) {
        if (manifest == null) return 0;
        int count = 0;
        for (var entry : manifest.entries()) {
            if (policyAddressable(entry.parentModId(), entry.originKind())) count++;
        }
        return count;
    }

    private static List<InspectionMod> retainedPolicyMods(Manifest manifest) {
        if (manifest == null) return List.of();
        ArrayList<InspectionMod> retained = new ArrayList<>(
            Math.min(MAX_RETAINED_POLICY_MODS, manifest.entries().size()));
        for (var entry : manifest.entries()) {
            if (!policyAddressable(entry.parentModId(), entry.originKind())) continue;
            if (retained.size() == MAX_RETAINED_POLICY_MODS) break;
            retained.add(InspectionMod.from(entry));
        }
        return List.copyOf(retained);
    }

    private static boolean policyAddressable(String parentModId, OriginKind originKind) {
        return parentModId == null && originKind != OriginKind.NESTED && originKind != OriginKind.BUILTIN;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : DiagnosticText.oneLine(value);
    }
}
