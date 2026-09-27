package com.badwolfmc.guardian.core.policy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable pre-login effective permission data supplied by an optional provider. */
public final class AdmissionPermissionSnapshot {
    private final String provider;
    private final Map<String, Boolean> permissions;

    public AdmissionPermissionSnapshot(String provider, Map<String, Boolean> permissions) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.permissions = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(permissions, "permissions")));
    }

    public static AdmissionPermissionSnapshot none() {
        return new AdmissionPermissionSnapshot("none", Map.of());
    }

    public String provider() { return provider; }
    public Map<String, Boolean> permissions() { return permissions; }

    /** Exact effective node lookup. Guardian correctness never relies on provider wildcard expansion. */
    public boolean has(String permission) {
        return Boolean.TRUE.equals(permissions.get(permission));
    }
}
