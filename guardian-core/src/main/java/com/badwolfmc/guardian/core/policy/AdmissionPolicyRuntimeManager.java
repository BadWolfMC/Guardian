package com.badwolfmc.guardian.core.policy;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Shared atomic parse -> validate -> immutable-snapshot activation seam for admission policy. */
public final class AdmissionPolicyRuntimeManager {
    private final Path policyPath;
    private final Path artifactCatalogPath;
    private final AdmissionPolicyLoader loader;
    private final AtomicReference<AdmissionPolicySnapshot> active = new AtomicReference<>();

    public AdmissionPolicyRuntimeManager(Path policyPath, Path artifactCatalogPath) {
        this(policyPath, artifactCatalogPath, new AdmissionPolicyLoader());
    }

    AdmissionPolicyRuntimeManager(Path policyPath, Path artifactCatalogPath, AdmissionPolicyLoader loader) {
        this.policyPath = Objects.requireNonNull(policyPath, "policyPath");
        this.artifactCatalogPath = Objects.requireNonNull(artifactCatalogPath, "artifactCatalogPath");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public AdmissionPolicySnapshot loadInitial() throws AdmissionPolicyException {
        if (active.get() != null) throw new IllegalStateException("admission policy is already active");
        AdmissionPolicySnapshot candidate = validateFiles();
        active.set(candidate);
        return candidate;
    }

    public AdmissionPolicySnapshot reload() throws AdmissionPolicyException {
        if (active.get() == null) throw new IllegalStateException("admission policy is not initialized");
        AdmissionPolicySnapshot candidate = validateFiles();
        active.set(candidate);
        return candidate;
    }

    /** Files-only parse/normalize/validate; never activates the candidate. */
    public AdmissionPolicySnapshot validateFiles() throws AdmissionPolicyException {
        return loader.load(policyPath, artifactCatalogPath);
    }

    public AdmissionPolicySnapshot current() {
        AdmissionPolicySnapshot value = active.get();
        if (value == null) throw new IllegalStateException("admission policy is not initialized");
        return value;
    }
}
