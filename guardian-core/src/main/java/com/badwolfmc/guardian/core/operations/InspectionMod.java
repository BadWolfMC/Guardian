package com.badwolfmc.guardian.core.operations;

import com.badwolfmc.guardian.protocol.ArtifactSha256;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.OriginKind;

import java.util.Objects;

/** Bounded immutable projection of one policy-addressable mod retained for active staff inspection. */
public record InspectionMod(
    String modId,
    String version,
    OriginKind originKind,
    ArtifactSha256 artifactSha256
) {
    public InspectionMod {
        modId = DiagnosticText.oneLine(Objects.requireNonNull(modId, "modId"));
        version = DiagnosticText.oneLine(Objects.requireNonNull(version, "version"));
        Objects.requireNonNull(originKind, "originKind");
    }

    static InspectionMod from(ManifestEntry entry) {
        return new InspectionMod(entry.modId(), entry.version(), entry.originKind(), entry.artifactSha256());
    }
}
