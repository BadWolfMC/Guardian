package com.badwolfmc.guardian.protocol;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ManifestCanonicalizer {
    private static final Comparator<ManifestEntry> ORDER = Comparator.comparing(ManifestEntry::modId);
    private static final Pattern FABRIC_MOD_ID = Pattern.compile("[a-z][a-z0-9_-]{1,63}");

    private ManifestCanonicalizer() {}

    public static Manifest canonicalize(Manifest manifest) {
        List<ManifestEntry> sorted = manifest.entries().stream().sorted(ORDER).toList();
        Manifest result = new Manifest(
            manifest.minecraftVersion(),
            manifest.fabricLoaderVersion(),
            manifest.cerberusVersion(),
            manifest.capabilities(),
            sorted
        );
        validate(result, false);
        return result;
    }

    public static void validateCanonical(Manifest manifest) {
        validate(manifest, true);
    }

    private static void validate(Manifest manifest, boolean requireOrder) {
        bounded(manifest.minecraftVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES, "minecraft version");
        bounded(manifest.fabricLoaderVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES, "Fabric Loader version");
        bounded(manifest.cerberusVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES, "Cerberus version");
        if (manifest.entries().size() > GuardianProtocol.MAX_MANIFEST_ENTRIES) {
            throw new IllegalArgumentException("manifest entry count exceeds limit");
        }

        Map<String, ManifestEntry> byId = new HashMap<>();
        String previous = null;
        for (ManifestEntry entry : manifest.entries()) {
            bounded(entry.modId(), GuardianProtocol.MAX_MOD_ID_BYTES, "mod id");
            if (!FABRIC_MOD_ID.matcher(entry.modId()).matches()) {
                throw new IllegalArgumentException("invalid Fabric mod id: " + entry.modId());
            }
            bounded(entry.version(), GuardianProtocol.MAX_VERSION_BYTES, "mod version");
            if (entry.parentModId() != null) {
                bounded(entry.parentModId(), GuardianProtocol.MAX_MOD_ID_BYTES, "parent mod id");
            }

            boolean nestedOrigin = entry.originKind() == OriginKind.NESTED;
            boolean hasParent = entry.parentModId() != null;
            if (nestedOrigin != hasParent) {
                throw new IllegalArgumentException(
                    "manifest containment/origin mismatch for " + entry.modId()
                        + ": NESTED origin and parentModId must either both be present or both be absent"
                );
            }

            boolean requiresArtifactHash = entry.originKind() == OriginKind.ARCHIVE;
            if (requiresArtifactHash && entry.artifactSha256() == null) {
                throw new IllegalArgumentException(
                    "top-level archive is missing SHA-256 artifact identity: " + entry.modId()
                );
            }
            if (!requiresArtifactHash && entry.artifactSha256() != null) {
                throw new IllegalArgumentException(
                    "artifact SHA-256 is only valid for top-level archive entries: " + entry.modId()
                );
            }
            if (byId.put(entry.modId(), entry) != null) {
                throw new IllegalArgumentException("duplicate mod id: " + entry.modId());
            }
            if (entry.modId().equals(entry.parentModId())) {
                throw new IllegalArgumentException("mod cannot contain itself: " + entry.modId());
            }
            if (requireOrder && previous != null && previous.compareTo(entry.modId()) >= 0) {
                throw new IllegalArgumentException("manifest entries are not in canonical order");
            }
            previous = entry.modId();
        }

        for (ManifestEntry entry : manifest.entries()) {
            if (entry.parentModId() != null && !byId.containsKey(entry.parentModId())) {
                throw new IllegalArgumentException("missing containing mod: " + entry.parentModId());
            }
        }

        for (ManifestEntry entry : manifest.entries()) {
            Set<String> seen = new HashSet<>();
            ManifestEntry cursor = entry;
            int depth = 0;
            while (cursor.parentModId() != null) {
                if (!seen.add(cursor.modId())) {
                    throw new IllegalArgumentException("containment cycle at " + cursor.modId());
                }
                if (++depth > GuardianProtocol.MAX_RELATIONSHIP_DEPTH) {
                    throw new IllegalArgumentException("containment depth exceeds limit");
                }
                cursor = byId.get(cursor.parentModId());
            }
        }
    }

    private static void bounded(String value, int max, String name) {
        ProtocolText.validate(value, max, name);
    }
}
