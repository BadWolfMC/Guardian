package com.badwolfmc.guardian.core.policy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Fully normalized, validated immutable admission-policy snapshot. */
public final class AdmissionPolicySnapshot {
    private final int schemaVersion;
    private final String defaultProfileId;
    private final Map<String, AdmissionProfile> profiles;
    private final Map<UUID, String> identityOverrides;

    public AdmissionPolicySnapshot(
        int schemaVersion,
        String defaultProfileId,
        Map<String, AdmissionProfile> profiles,
        Map<UUID, String> identityOverrides
    ) {
        this.schemaVersion = schemaVersion;
        this.defaultProfileId = Objects.requireNonNull(defaultProfileId, "defaultProfileId");
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        this.identityOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(identityOverrides));
        if (!this.profiles.containsKey(defaultProfileId)) {
            throw new IllegalArgumentException("default profile does not exist: " + defaultProfileId);
        }
    }

    public int schemaVersion() { return schemaVersion; }
    public String defaultProfileId() { return defaultProfileId; }
    public Map<String, AdmissionProfile> profiles() { return profiles; }
    public Map<UUID, String> identityOverrides() { return identityOverrides; }
    public AdmissionProfile defaultProfile() { return profiles.get(defaultProfileId); }
    public AdmissionProfile profile(String id) { return profiles.get(id); }
}
