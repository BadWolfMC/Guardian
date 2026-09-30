package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.core.policy.AdmissionPolicyException;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyLoader;
import com.badwolfmc.guardian.core.policy.AdmissionPolicySnapshot;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalogStore;
import com.badwolfmc.guardian.core.operations.ProxyAssertionSecretResolver;
import com.badwolfmc.guardian.core.operations.GuardianServerChallengeKeyResolver;
import com.badwolfmc.guardian.core.operations.StableFileFingerprint;
import com.badwolfmc.guardian.paper.PaperAuthorityMode;
import com.badwolfmc.guardian.paper.locale.GuardianLocaleLoader;

import java.io.IOException;
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

    public synchronized GuardianRuntimeSnapshot loadInitial() throws GuardianConfigurationException {
        if (active.get() != null) {
            throw new IllegalStateException("Guardian runtime snapshot is already active");
        }
        GuardianRuntimeSnapshot candidate = loadCandidate(1L);
        active.set(candidate);
        return candidate;
    }

    public synchronized GuardianRuntimeSnapshot reload() throws GuardianConfigurationException {
        if (active.get() == null) {
            throw new IllegalStateException("Guardian runtime snapshot has not been initialized");
        }
        GuardianRuntimeSnapshot previous = active.get();
        long nextGeneration = nextGeneration(previous.generation());
        GuardianRuntimeSnapshot candidate = loadCandidate(nextGeneration);
        active.set(candidate);
        return candidate;
    }

    /** Files-only validation seam; candidate is never activated. */
    public GuardianRuntimeSnapshot validateFiles() throws GuardianConfigurationException {
        GuardianRuntimeSnapshot current = active.get();
        return loadCandidate(current == null ? 1L : current.generation());
    }

    public GuardianRuntimeSnapshot current() {
        GuardianRuntimeSnapshot snapshot = active.get();
        if (snapshot == null) {
            throw new IllegalStateException("Guardian runtime snapshot has not been initialized");
        }
        return snapshot;
    }

    private GuardianRuntimeSnapshot loadCandidate(long generation) throws GuardianConfigurationException {
        String configBefore = fingerprintRequired(configPath, GuardianConfigLoader.MAX_CONFIG_BYTES);
        GuardianPaperSettings settings = configLoader.load(configPath);

        String policyBefore = null;
        String catalogBefore = null;
        if (settings.admissionEnabled() && settings.authorityMode() == PaperAuthorityMode.STANDALONE) {
            policyBefore = fingerprintRequired(admissionPolicyPath, AdmissionPolicyLoader.MAX_POLICY_BYTES);
            catalogBefore = fingerprintOptional(artifactCatalogPath, ArtifactCatalogStore.MAX_CATALOG_BYTES);
        }

        Path fallbackLocale = localesDirectory.resolve(GuardianLocaleLoader.FALLBACK_LOCALE + ".properties");
        Path selectedLocale = localesDirectory.resolve(settings.locale() + ".properties");
        String fallbackBefore = fingerprintRequired(fallbackLocale, GuardianLocaleLoader.MAX_LOCALE_BYTES);
        String selectedBefore = GuardianLocaleLoader.FALLBACK_LOCALE.equals(settings.locale())
            ? fallbackBefore
            : fingerprintOptional(selectedLocale, GuardianLocaleLoader.MAX_LOCALE_BYTES);

        Path dataDirectory = configPath.toAbsolutePath().normalize().getParent();
        if (dataDirectory == null) throw new IllegalArgumentException("config path has no parent directory");

        String serverAuthKeyBefore = null;
        Path serverAuthKeyPath = dataDirectory.resolve(GuardianServerChallengeKeyResolver.DEFAULT_KEY_FILE);
        if (settings.serverChallengeSigner() != null) {
            serverAuthKeyBefore = fingerprintRequired(
                serverAuthKeyPath, GuardianServerChallengeKeyResolver.MAX_PRIVATE_KEY_FILE_BYTES);
            requireLoadedServerAuthKeyMatchesCurrent(dataDirectory, serverAuthKeyPath, settings);
        }

        String proxyKeyBefore = null;
        Path proxyKeyPath = dataDirectory.resolve(ProxyAssertionSecretResolver.DEFAULT_KEY_FILE);
        if (settings.admissionEnabled() && settings.authorityMode() == PaperAuthorityMode.VELOCITY) {
            proxyKeyBefore = fingerprintRequired(proxyKeyPath, ProxyAssertionSecretResolver.MAX_SECRET_FILE_BYTES);
            requireLoadedSecretMatchesCurrent(proxyKeyPath, settings);
        }

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
        var localeCatalog = localeLoader.load(localesDirectory, settings.locale());

        requireUnchanged(configPath, configBefore, fingerprintRequired(configPath, GuardianConfigLoader.MAX_CONFIG_BYTES));
        requireUnchanged(fallbackLocale, fallbackBefore, fingerprintRequired(fallbackLocale, GuardianLocaleLoader.MAX_LOCALE_BYTES));
        if (!GuardianLocaleLoader.FALLBACK_LOCALE.equals(settings.locale())) {
            requireUnchanged(selectedLocale, selectedBefore, fingerprintOptional(selectedLocale, GuardianLocaleLoader.MAX_LOCALE_BYTES));
        }
        if (policyBefore != null) {
            requireUnchanged(admissionPolicyPath, policyBefore, fingerprintRequired(admissionPolicyPath, AdmissionPolicyLoader.MAX_POLICY_BYTES));
            requireUnchanged(artifactCatalogPath, catalogBefore, fingerprintOptional(artifactCatalogPath, ArtifactCatalogStore.MAX_CATALOG_BYTES));
        }
        if (proxyKeyBefore != null) {
            requireUnchanged(proxyKeyPath, proxyKeyBefore, fingerprintRequired(proxyKeyPath, ProxyAssertionSecretResolver.MAX_SECRET_FILE_BYTES));
        }
        if (serverAuthKeyBefore != null) {
            requireUnchanged(serverAuthKeyPath, serverAuthKeyBefore, fingerprintRequired(
                serverAuthKeyPath, GuardianServerChallengeKeyResolver.MAX_PRIVATE_KEY_FILE_BYTES));
        }

        return new GuardianRuntimeSnapshot(settings, localeCatalog, admissionPolicy, generation);
    }

    private static void requireLoadedSecretMatchesCurrent(Path proxyKeyPath, GuardianPaperSettings settings)
        throws GuardianConfigurationException {
        try {
            Path dataDirectory = proxyKeyPath.toAbsolutePath().normalize().getParent();
            if (dataDirectory == null) throw new IllegalArgumentException("proxy assertion key has no parent directory");
            var current = ProxyAssertionSecretResolver.resolveFile(
                dataDirectory, ProxyAssertionSecretResolver.DEFAULT_KEY_FILE);
            if (!settings.proxyAssertionSecret().sameKey(current)) {
                throw new GuardianConfigurationException(
                    proxyKeyPath, GuardianConfigurationException.Kind.INVALID,
                    "proxy assertion key changed while the runtime candidate was being validated; retry the operation");
            }
        } catch (GuardianConfigurationException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new GuardianConfigurationException(
                proxyKeyPath, GuardianConfigurationException.Kind.INVALID,
                "proxy assertion key changed or became unreadable while the runtime candidate was being validated: "
                    + ex.getMessage(), ex);
        }
    }

    private static void requireLoadedServerAuthKeyMatchesCurrent(
        Path dataDirectory, Path keyPath, GuardianPaperSettings settings
    ) throws GuardianConfigurationException {
        try {
            var current = GuardianServerChallengeKeyResolver.resolveFile(dataDirectory);
            if (!settings.serverChallengeSigner().sameKey(current)) {
                throw new GuardianConfigurationException(
                    keyPath, GuardianConfigurationException.Kind.INVALID,
                    "Guardian server authentication key changed while the runtime candidate was being validated; retry the operation");
            }
        } catch (GuardianConfigurationException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new GuardianConfigurationException(
                keyPath, GuardianConfigurationException.Kind.INVALID,
                "Guardian server authentication key changed or became unreadable while the runtime candidate was being validated: "
                    + ex.getMessage(), ex);
        }
    }

    private static long nextGeneration(long current) {
        if (current == Long.MAX_VALUE) throw new IllegalStateException("Guardian runtime generation exhausted");
        return current + 1L;
    }

    private static String fingerprintRequired(Path path, int maxBytes) throws GuardianConfigurationException {
        try {
            return StableFileFingerprint.required(path, maxBytes);
        } catch (IOException ex) {
            throw new GuardianConfigurationException(path, GuardianConfigurationException.Kind.INVALID,
                "administrator file was not a stable regular non-symlink file: " + ex.getMessage(), ex);
        }
    }

    private static String fingerprintOptional(Path path, int maxBytes) throws GuardianConfigurationException {
        try {
            return StableFileFingerprint.optional(path, maxBytes);
        } catch (IOException ex) {
            throw new GuardianConfigurationException(path, GuardianConfigurationException.Kind.INVALID,
                "administrator file was not a stable regular non-symlink file: " + ex.getMessage(), ex);
        }
    }

    private static void requireUnchanged(Path path, String before, String after) throws GuardianConfigurationException {
        if (!before.equals(after)) {
            throw new GuardianConfigurationException(path, GuardianConfigurationException.Kind.INVALID,
                "administrator file changed while the runtime candidate was being validated; retry the operation");
        }
    }
}
