package com.badwolfmc.guardian.core.operations;

import com.badwolfmc.guardian.core.BedrockEvidence;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.core.policy.ProfileResolutionSource;
import com.badwolfmc.guardian.protocol.Manifest;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.OriginKind;
import com.badwolfmc.guardian.protocol.Presence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable, bounded-by-protocol view retained only while the corresponding player is connected. */
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
    Manifest manifest,
    BedrockEvidence bedrockEvidence
) {
    public ActiveInspectionSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        playerName = normalize(playerName, "<unknown>");
        backend = normalize(backend, "<none>");
        Objects.requireNonNull(classification, "classification");
        observedBrand = normalize(observedBrand, "<unknown>");
        profileId = normalize(profileId, "<unknown>");
        Objects.requireNonNull(profileSource, "profileSource");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(bedrockEvidence, "bedrockEvidence");
    }

    public int loaderKnownCount() {
        return manifest == null ? 0 : manifest.entries().size();
    }

    /** Entries the Phase 3 policy treats as independently addressable ordinary installed mods. */
    public List<ManifestEntry> policyAddressableMods() {
        if (manifest == null) return List.of();
        return manifest.entries().stream()
            .filter(entry -> entry.parentModId() == null)
            .filter(entry -> entry.originKind() != OriginKind.NESTED)
            .filter(entry -> entry.originKind() != OriginKind.BUILTIN)
            .toList();
    }

    public ActiveInspectionSnapshot withBackend(String newBackend) {
        return new ActiveInspectionSnapshot(
            playerId, playerName, newBackend, classification, observedBrand, profileId, profileSource,
            cerberusPresence, decision, manifest, bedrockEvidence);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
