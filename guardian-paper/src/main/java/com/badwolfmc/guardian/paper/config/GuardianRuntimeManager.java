package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.core.policy.AdmissionPolicyException;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyLoader;
import com.badwolfmc.guardian.core.policy.AdmissionPolicySnapshot;
import com.badwolfmc.guardian.paper.PaperAuthorityMode;
import com.badwolfmc.guardian.paper.locale.GuardianLocaleLoader;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Parse -> validate -> immutable candidate -> atomic activation. */
public final class GuardianRuntimeManager {
    private final Path configPath;
    private final Path localesDirectory;
    private final Path admissionPolicyPath;
    private final Path artifactCatalogPath;
    private final GuardianConfigLoader configLoader;
    private final GuardianLocaleLoader localeLoader;
    private final AdmissionPolicyLoader admissionPolicyLoader;
    private final AtomicReference<GuardianRuntimeSnapshot> active = new AtomicReference<>();

    public GuardianRuntimeManager(Path configPath, Path localesDirectory) {
        this(configPath, localesDirectory, new GuardianConfigLoader(), new GuardianLocaleLoader(),
            new AdmissionPolicyLoader());
    }

    GuardianRuntimeManager(
        Path configPath,
        Path localesDirectory,
        GuardianConfigLoader configLoader,
        GuardianLocaleLoader localeLoader,
        AdmissionPolicyLoader admissionPolicyLoader
    ) {
        this.configPath = configPath;
        this.localesDirectory = localesDirectory;
        Path dataDirectory = configPath.toAbsolutePath().normalize().getParent();
        if (dataDirectory == null) throw new IllegalArgumentException("configPath must have a parent directory");
        this.admissionPolicyPath = dataDirectory.resolve("policy.yml");
        this.artifactCatalogPath = dataDirectory.resolve("artifacts.yml");
        this.configLoader = configLoader;
        this.localeLoader = localeLoader;
        this.admissionPolicyLoader = admissionPolicyLoader;
    }

    public GuardianRuntimeSnapshot loadInitial() throws GuardianConfigurationException {
        if (active.get() != null) {
            throw new IllegalStateException("Guardian runtime snapshot is already active");
        }
        GuardianRuntimeSnapshot candidate = loadCandidate();
        active.set(candidate);
        return candidate;
    }

    public GuardianRuntimeSnapshot reload() throws GuardianConfigurationException {
        if (active.get() == null) {
            throw new IllegalStateException("Guardian runtime snapshot has not been initialized");
        }
        GuardianRuntimeSnapshot candidate = loadCandidate();
        active.set(candidate);
        return candidate;
    }

    /** Files-only validation seam; candidate is never activated. */
    public GuardianRuntimeSnapshot validateFiles() throws GuardianConfigurationException {
        return loadCandidate();
    }

    public GuardianRuntimeSnapshot current() {
        GuardianRuntimeSnapshot snapshot = active.get();
        if (snapshot == null) {
            throw new IllegalStateException("Guardian runtime snapshot has not been initialized");
        }
        return snapshot;
    }

    private GuardianRuntimeSnapshot loadCandidate() throws GuardianConfigurationException {
        GuardianPaperSettings settings = configLoader.load(configPath);
        AdmissionPolicySnapshot admissionPolicy = null;
        if (settings.admissionEnabled() && settings.authorityMode() == PaperAuthorityMode.STANDALONE) {
            try {
                admissionPolicy = admissionPolicyLoader.load(admissionPolicyPath, artifactCatalogPath);
            } catch (AdmissionPolicyException ex) {
                throw new GuardianConfigurationException(
                    ex.path(), GuardianConfigurationException.Kind.ADMISSION_POLICY,
                    ex.getMessage(), ex);
            }
        }
        return new GuardianRuntimeSnapshot(
            settings,
            localeLoader.load(localesDirectory, settings.locale()),
            admissionPolicy
        );
    }
}
