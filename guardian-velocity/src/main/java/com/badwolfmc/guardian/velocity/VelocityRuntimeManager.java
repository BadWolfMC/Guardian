package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.artifact.ArtifactCatalog;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalogException;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalogStore;
import com.badwolfmc.guardian.core.operations.ProxyAssertionSecretResolver;
import com.badwolfmc.guardian.core.operations.GuardianServerChallengeKeyResolver;
import com.badwolfmc.guardian.core.operations.StableFileFingerprint;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyException;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyLoader;
import com.badwolfmc.guardian.core.policy.AdmissionPolicySnapshot;
import com.badwolfmc.guardian.velocity.config.VelocityConfigLoader;
import com.badwolfmc.guardian.velocity.config.VelocityConfigurationException;
import com.badwolfmc.guardian.velocity.config.VelocityOperationalSettings;

import java.io.IOException;
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
        this.policyPath = data.resolve("policy.yml");
        this.artifactCatalogPath = data.resolve("artifacts.yml");
        this.configLoader = configLoader;
        this.policyLoader = policyLoader;
    }

    synchronized VelocityRuntimeSnapshot loadInitial() throws VelocityConfigurationException {
        if (active.get() != null) throw new IllegalStateException("Guardian-Velocity runtime is already active");
        VelocityRuntimeSnapshot candidate = loadCandidate(1L);
        active.set(candidate);
        return candidate;
    }

    synchronized VelocityRuntimeSnapshot reload() throws VelocityConfigurationException {
        if (active.get() == null) throw new IllegalStateException("Guardian-Velocity runtime is not initialized");
        VelocityRuntimeSnapshot previous = active.get();
        long nextGeneration = nextGeneration(previous.generation());
        VelocityRuntimeSnapshot candidate = loadCandidate(nextGeneration);
        active.set(candidate);
        return candidate;
    }

    /** Files-only validation; never activates the candidate. */
    VelocityRuntimeSnapshot validateFiles() throws VelocityConfigurationException {
        VelocityRuntimeSnapshot current = active.get();
        return loadCandidate(current == null ? 1L : current.generation());
    }

    VelocityRuntimeSnapshot current() {
        VelocityRuntimeSnapshot snapshot = active.get();
        if (snapshot == null) throw new IllegalStateException("Guardian-Velocity runtime is not initialized");
        return snapshot;
    }

    private VelocityRuntimeSnapshot loadCandidate(long generation) throws VelocityConfigurationException {
        String configBefore = fingerprintRequired(configPath, VelocityConfigLoader.MAX_CONFIG_BYTES);
        Path proxyKeyPath = configPath.toAbsolutePath().normalize().getParent()
            .resolve(ProxyAssertionSecretResolver.DEFAULT_KEY_FILE);
        String proxyKeyBefore = fingerprintRequired(proxyKeyPath, ProxyAssertionSecretResolver.MAX_SECRET_FILE_BYTES);

        VelocityOperationalSettings settings = configLoader.load(configPath);
        String serverAuthKeyBefore = null;
        Path serverAuthKeyPath = configPath.toAbsolutePath().normalize().getParent()
            .resolve(GuardianServerChallengeKeyResolver.DEFAULT_KEY_FILE);
        if (settings.serverChallengeSigner() != null) {
            serverAuthKeyBefore = fingerprintRequired(
                serverAuthKeyPath, GuardianServerChallengeKeyResolver.MAX_PRIVATE_KEY_FILE_BYTES);
            requireLoadedServerAuthKeyMatchesCurrent(
                configPath.toAbsolutePath().normalize().getParent(), serverAuthKeyPath, settings);
        }
        String policyBefore = fingerprintRequired(policyPath, AdmissionPolicyLoader.MAX_POLICY_BYTES);
        String catalogBefore = fingerprintOptional(artifactCatalogPath, ArtifactCatalogStore.MAX_CATALOG_BYTES);
        Path fallbackLocale = localesDirectory.resolve(VelocityMessages.FALLBACK_LOCALE + ".properties");
        Path selectedLocale = localesDirectory.resolve(settings.locale() + ".properties");
        String fallbackBefore = fingerprintRequired(fallbackLocale, VelocityMessages.MAX_LOCALE_BYTES);
        String selectedBefore = VelocityMessages.FALLBACK_LOCALE.equals(settings.locale())
            ? fallbackBefore
            : fingerprintOptional(selectedLocale, VelocityMessages.MAX_LOCALE_BYTES);

        final AdmissionPolicySnapshot policy;
        try {
            policy = policyLoader.load(policyPath, artifactCatalogPath);
        } catch (AdmissionPolicyException ex) {
            throw new VelocityConfigurationException(ex.path(), ex.getMessage(), ex);
        }
        VelocityMessages messages = VelocityMessages.load(localesDirectory, settings.locale());
        ArtifactCatalog artifactCatalog = loadArtifactCatalog();

        requireUnchanged(configPath, configBefore, fingerprintRequired(configPath, VelocityConfigLoader.MAX_CONFIG_BYTES));
        requireUnchanged(proxyKeyPath, proxyKeyBefore, fingerprintRequired(proxyKeyPath, ProxyAssertionSecretResolver.MAX_SECRET_FILE_BYTES));
        requireUnchanged(policyPath, policyBefore, fingerprintRequired(policyPath, AdmissionPolicyLoader.MAX_POLICY_BYTES));
        requireUnchanged(artifactCatalogPath, catalogBefore, fingerprintOptional(artifactCatalogPath, ArtifactCatalogStore.MAX_CATALOG_BYTES));
        requireUnchanged(fallbackLocale, fallbackBefore, fingerprintRequired(fallbackLocale, VelocityMessages.MAX_LOCALE_BYTES));
        if (!VelocityMessages.FALLBACK_LOCALE.equals(settings.locale())) {
            requireUnchanged(selectedLocale, selectedBefore, fingerprintOptional(selectedLocale, VelocityMessages.MAX_LOCALE_BYTES));
        }
        if (serverAuthKeyBefore != null) {
            requireUnchanged(serverAuthKeyPath, serverAuthKeyBefore, fingerprintRequired(
                serverAuthKeyPath, GuardianServerChallengeKeyResolver.MAX_PRIVATE_KEY_FILE_BYTES));
        }

        return new VelocityRuntimeSnapshot(settings, policy, messages, artifactCatalog, generation);
    }

    private static void requireLoadedServerAuthKeyMatchesCurrent(
        Path dataDirectory, Path keyPath, VelocityOperationalSettings settings
    ) throws VelocityConfigurationException {
        try {
            var current = GuardianServerChallengeKeyResolver.resolveFile(dataDirectory);
            if (!settings.serverChallengeSigner().sameKey(current)) {
                throw new VelocityConfigurationException(keyPath,
                    "Guardian server authentication key changed while the runtime candidate was being validated; retry the operation");
            }
        } catch (VelocityConfigurationException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new VelocityConfigurationException(keyPath,
                "Guardian server authentication key changed or became unreadable while the runtime candidate was being validated: "
                    + ex.getMessage(), ex);
        }
    }

    private static long nextGeneration(long current) {
        if (current == Long.MAX_VALUE) throw new IllegalStateException("Guardian-Velocity runtime generation exhausted");
        return current + 1L;
    }

    private static String fingerprintRequired(Path path, int maxBytes) throws VelocityConfigurationException {
        try {
            return StableFileFingerprint.required(path, maxBytes);
        } catch (IOException ex) {
            throw new VelocityConfigurationException(path,
                "administrator file was not a stable regular non-symlink file: " + ex.getMessage(), ex);
        }
    }

    private static String fingerprintOptional(Path path, int maxBytes) throws VelocityConfigurationException {
        try {
            return StableFileFingerprint.optional(path, maxBytes);
        } catch (IOException ex) {
            throw new VelocityConfigurationException(path,
                "administrator file was not a stable regular non-symlink file: " + ex.getMessage(), ex);
        }
    }

    private static void requireUnchanged(Path path, String before, String after)
        throws VelocityConfigurationException {
        if (!before.equals(after)) {
            throw new VelocityConfigurationException(path,
                "administrator file changed while the runtime candidate was being validated; retry the operation");
        }
    }

    private ArtifactCatalog loadArtifactCatalog() throws VelocityConfigurationException {
        try {
            return new ArtifactCatalogStore(artifactCatalogPath).load();
        } catch (ArtifactCatalogException ex) {
            throw new VelocityConfigurationException(artifactCatalogPath, ex.getMessage(), ex);
        }
    }
}
