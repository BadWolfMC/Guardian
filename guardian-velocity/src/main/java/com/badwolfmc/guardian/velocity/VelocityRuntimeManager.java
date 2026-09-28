package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.artifact.ArtifactCatalog;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalogException;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalogStore;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyException;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyLoader;
import com.badwolfmc.guardian.core.policy.AdmissionPolicySnapshot;
import com.badwolfmc.guardian.velocity.config.VelocityConfigLoader;
import com.badwolfmc.guardian.velocity.config.VelocityConfigurationException;
import com.badwolfmc.guardian.velocity.config.VelocityOperationalSettings;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Parse/normalize/validate complete proxy candidate before one atomic runtime activation. */
final class VelocityRuntimeManager {
    private final Path configPath;
    private final Path localesDirectory;
    private final Path policyPath;
    private final Path artifactCatalogPath;
    private final VelocityConfigLoader configLoader;
    private final AdmissionPolicyLoader policyLoader;
    private final AtomicReference<VelocityRuntimeSnapshot> active = new AtomicReference<>();

    VelocityRuntimeManager(Path dataDirectory) {
        this(dataDirectory, new VelocityConfigLoader(), new AdmissionPolicyLoader());
    }

    VelocityRuntimeManager(
        Path dataDirectory,
        VelocityConfigLoader configLoader,
        AdmissionPolicyLoader policyLoader
    ) {
        Path data = dataDirectory.toAbsolutePath().normalize();
        this.configPath = data.resolve("config.yml");
        this.localesDirectory = data.resolve("locales");
        this.policyPath = data.resolve("admission/policy.yml");
        this.artifactCatalogPath = data.resolve("artifacts.yml");
        this.configLoader = configLoader;
        this.policyLoader = policyLoader;
    }

    VelocityRuntimeSnapshot loadInitial() throws VelocityConfigurationException {
        if (active.get() != null) throw new IllegalStateException("Guardian-Velocity runtime is already active");
        VelocityRuntimeSnapshot candidate = loadCandidate();
        active.set(candidate);
        return candidate;
    }

    VelocityRuntimeSnapshot reload() throws VelocityConfigurationException {
        if (active.get() == null) throw new IllegalStateException("Guardian-Velocity runtime is not initialized");
        VelocityRuntimeSnapshot candidate = loadCandidate();
        active.set(candidate);
        return candidate;
    }

    /** Files-only validation; never activates the candidate. */
    VelocityRuntimeSnapshot validateFiles() throws VelocityConfigurationException {
        return loadCandidate();
    }

    VelocityRuntimeSnapshot current() {
        VelocityRuntimeSnapshot snapshot = active.get();
        if (snapshot == null) throw new IllegalStateException("Guardian-Velocity runtime is not initialized");
        return snapshot;
    }

    private VelocityRuntimeSnapshot loadCandidate() throws VelocityConfigurationException {
        VelocityOperationalSettings settings = configLoader.load(configPath);
        ArtifactCatalog catalogBefore = loadArtifactCatalog();
        final AdmissionPolicySnapshot policy;
        try {
            policy = policyLoader.load(policyPath, artifactCatalogPath);
        } catch (AdmissionPolicyException ex) {
            throw new VelocityConfigurationException(ex.path(), ex.getMessage(), ex);
        }
        VelocityMessages messages = VelocityMessages.load(localesDirectory, settings.locale());
        ArtifactCatalog artifactCatalog = loadArtifactCatalog();
        if (!catalogBefore.equals(artifactCatalog)) {
            throw new VelocityConfigurationException(artifactCatalogPath,
                "artifact catalog changed while the runtime candidate was being validated; retry the operation");
        }
        return new VelocityRuntimeSnapshot(settings, policy, messages, artifactCatalog);
    }

    private ArtifactCatalog loadArtifactCatalog() throws VelocityConfigurationException {
        try {
            return new ArtifactCatalogStore(artifactCatalogPath).load();
        } catch (ArtifactCatalogException ex) {
            throw new VelocityConfigurationException(artifactCatalogPath, ex.getMessage(), ex);
        }
    }
}
