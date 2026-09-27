package com.badwolfmc.guardian.core.policy;

import com.badwolfmc.guardian.protocol.ArtifactSha256;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.OriginKind;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** One OR-clause for acceptable version/artifact identity. */
public final class ArtifactAcceptance {
    public enum Match {
        MATCH,
        VERSION_MISMATCH,
        ARTIFACT_MISMATCH
    }

    private final VersionPredicate version;
    private final ArtifactVerification verification;
    private final NavigableSet<ArtifactSha256> directHashes;
    private final NavigableMap<String, NavigableSet<ArtifactSha256>> catalogHashesByVersion;

    public ArtifactAcceptance(
        VersionPredicate version,
        ArtifactVerification verification,
        Set<ArtifactSha256> directHashes,
        Map<String, ? extends Set<ArtifactSha256>> catalogHashesByVersion
    ) {
        this.version = Objects.requireNonNull(version, "version");
        this.verification = Objects.requireNonNull(verification, "verification");
        TreeSet<ArtifactSha256> direct = new TreeSet<>(Objects.requireNonNull(directHashes, "directHashes"));
        TreeMap<String, NavigableSet<ArtifactSha256>> catalog = new TreeMap<>();
        Objects.requireNonNull(catalogHashesByVersion, "catalogHashesByVersion").forEach((key, value) ->
            catalog.put(key, Collections.unmodifiableNavigableSet(new TreeSet<>(value))));
        this.directHashes = Collections.unmodifiableNavigableSet(direct);
        this.catalogHashesByVersion = Collections.unmodifiableNavigableMap(catalog);
        if (verification == ArtifactVerification.VERSION_ONLY
            && (!this.directHashes.isEmpty() || !this.catalogHashesByVersion.isEmpty())) {
            throw new IllegalArgumentException("VERSION_ONLY acceptance cannot declare SHA-256 identities");
        }
        if (verification == ArtifactVerification.HASH_REQUIRED
            && this.directHashes.isEmpty() && this.catalogHashesByVersion.isEmpty()) {
            throw new IllegalArgumentException("HASH_REQUIRED acceptance requires direct hashes and/or catalog matches");
        }
    }

    public VersionPredicate version() {
        return version;
    }

    public ArtifactVerification verification() {
        return verification;
    }

    public Set<ArtifactSha256> directHashes() {
        return directHashes;
    }

    public Map<String, ? extends Set<ArtifactSha256>> catalogHashesByVersion() {
        return catalogHashesByVersion;
    }

    public Match match(ManifestEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (!version.matches(entry.version())) return Match.VERSION_MISMATCH;
        if (verification == ArtifactVerification.VERSION_ONLY) return Match.MATCH;
        if (entry.originKind() != OriginKind.ARCHIVE || entry.artifactSha256() == null) {
            return Match.ARTIFACT_MISMATCH;
        }
        ArtifactSha256 observed = entry.artifactSha256();
        if (directHashes.contains(observed)) return Match.MATCH;
        Set<ArtifactSha256> versionCatalog = catalogHashesByVersion.get(entry.version());
        return versionCatalog != null && versionCatalog.contains(observed)
            ? Match.MATCH : Match.ARTIFACT_MISMATCH;
    }
}
