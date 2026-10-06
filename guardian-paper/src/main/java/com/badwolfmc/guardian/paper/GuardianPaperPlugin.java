package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.operations.SafeDirectory;
import com.badwolfmc.guardian.core.operations.ConfigurationSchemaMigrator;
import com.badwolfmc.guardian.core.operations.DiagnosticText;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalogException;
import com.badwolfmc.guardian.core.artifact.ArtifactImportService;
import com.badwolfmc.guardian.paper.config.GuardianConfigurationException;
import com.badwolfmc.guardian.paper.config.GuardianRuntimeManager;
import com.badwolfmc.guardian.paper.config.GuardianRuntimeSnapshot;
import com.badwolfmc.guardian.paper.config.GuardianStartupRecovery;
import com.badwolfmc.guardian.paper.locale.GuardianLocaleLoader;
import com.badwolfmc.guardian.paper.locale.GuardianMessageRenderer;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Paper runtime host for Guardian's independent Admission and Protection domains. */
public final class GuardianPaperPlugin extends JavaPlugin {
    private static final int MAX_STARTUP_RECOVERIES = 4;

    private GuardianRuntimeManager runtimeManager;
    private PaperAdmissionAdapter admissionAdapter;
    private PaperProtectionRuntime protectionRuntime;
    private ArtifactImportService artifactImportService;
    private final PaperInspectionService inspectionService = new PaperInspectionService();

    @Override
    public void onEnable() {
        ensureAdministratorFile("config.yml");
        ensureAdministratorFile("policy.yml");
        ensureAdministratorFile("locales/" + GuardianLocaleLoader.FALLBACK_LOCALE + ".properties");

        Path data = getDataFolder().toPath();
        migratePublicReleaseSchemas(data);
        runtimeManager = new GuardianRuntimeManager(data.resolve("config.yml"), data.resolve("locales"));
        GuardianRuntimeSnapshot snapshot = loadInitialWithRecovery(data);

        GuardianMessageRenderer messageRenderer = new GuardianMessageRenderer();
        artifactImportService = new ArtifactImportService(data);
        initializeArtifactCatalogSurface();
        registerAdministratorCommand(messageRenderer);
        if (snapshot.settings().admissionEnabled()) {
            admissionAdapter = new PaperAdmissionAdapter(this, runtimeManager, messageRenderer, inspectionService);
            admissionAdapter.enable();
        } else {
            getLogger().info("Guardian Admission disabled by configuration.");
        }

        if (snapshot.settings().protectionEnabled()) {
            protectionRuntime = new PaperProtectionRuntime(this, runtimeManager, messageRenderer);
            protectionRuntime.enable();
        } else {
            getLogger().info("Guardian Protection disabled by configuration.");
        }

        getLogger().info("Guardian runtime activated: schema=" + snapshot.settings().schemaVersion()
            + ", admission=" + snapshot.settings().admissionEnabled()
            + ", protection=" + snapshot.settings().protectionEnabled()
            + ", locale=" + snapshot.settings().locale() + ".");
    }

    private void migratePublicReleaseSchemas(Path data) {
        migrateAdministratorSchema(
            data.resolve("config.yml"),
            com.badwolfmc.guardian.paper.config.GuardianConfigLoader.MAX_CONFIG_BYTES,
            ConfigurationSchemaMigrator.Surface.PAPER_CONFIG);
        migrateAdministratorSchema(
            data.resolve("policy.yml"),
            com.badwolfmc.guardian.core.policy.AdmissionPolicyLoader.MAX_POLICY_BYTES,
            ConfigurationSchemaMigrator.Surface.ADMISSION_POLICY);
    }

    private void migrateAdministratorSchema(Path path, int maxBytes, ConfigurationSchemaMigrator.Surface surface) {
        try {
            var prepared = ConfigurationSchemaMigrator.prepare(path, maxBytes, surface);
            if (prepared.isEmpty()) return;
            var published = ConfigurationSchemaMigrator.publish(prepared.get());
            getLogger().warning("Guardian upgraded " + path.getFileName() + " from schema "
                + published.fromSchema() + " to " + published.toSchema() + ". Original preserved at "
                + published.backupPath() + ". Review the migrated file before future edits.");
        } catch (IOException ex) {
            throw new IllegalStateException("Guardian could not safely migrate administrator file '"
                + path + "': " + ex.getMessage(), ex);
        }
    }

    @Override
    public void onDisable() {
        if (admissionAdapter != null) {
            admissionAdapter.disable();
            admissionAdapter = null;
        }
        if (protectionRuntime != null) {
            protectionRuntime.disable();
            protectionRuntime = null;
        }
    }

    /**
     * Atomically activates a validated runtime candidate and reconciles domain adapters.
     * A future Guardian administrative command may call this without acquiring raw-reload semantics.
     */
    synchronized GuardianRuntimeSnapshot reloadRuntime() throws GuardianConfigurationException {
        GuardianRuntimeSnapshot previous = runtimeManager.current();
        GuardianRuntimeSnapshot current = runtimeManager.reload();
        reconcileAdmission(previous, current);
        reconcileProtection(previous, current);
        return current;
    }

    GuardianRuntimeManager runtimeManager() {
        return runtimeManager;
    }

    private void reconcileAdmission(GuardianRuntimeSnapshot previous, GuardianRuntimeSnapshot current) {
        boolean wasEnabled = previous.settings().admissionEnabled();
        boolean nowEnabled = current.settings().admissionEnabled();
        boolean authorityChanged = previous.settings().authorityMode() != current.settings().authorityMode();

        if (wasEnabled && (!nowEnabled || authorityChanged) && admissionAdapter != null) {
            admissionAdapter.disable();
            admissionAdapter = null;
        }

        if (nowEnabled && (!wasEnabled || authorityChanged)) {
            admissionAdapter = new PaperAdmissionAdapter(this, runtimeManager, new GuardianMessageRenderer(), inspectionService);
            admissionAdapter.enable();
        }
    }

    private void reconcileProtection(GuardianRuntimeSnapshot previous, GuardianRuntimeSnapshot current) {
        boolean wasEnabled = previous.settings().protectionEnabled();
        boolean nowEnabled = current.settings().protectionEnabled();
        if (!wasEnabled && nowEnabled) {
            protectionRuntime = new PaperProtectionRuntime(
                this, runtimeManager, new GuardianMessageRenderer());
            protectionRuntime.enable();
            return;
        }
        if (wasEnabled && !nowEnabled) {
            if (protectionRuntime != null) {
                protectionRuntime.disable();
                protectionRuntime = null;
            }
            return;
        }
        if (nowEnabled && protectionRuntime != null) {
            protectionRuntime.reconfigure(previous, current);
        }
    }

    private GuardianRuntimeSnapshot loadInitialWithRecovery(Path dataDirectory) {
        GuardianStartupRecovery recovery = new GuardianStartupRecovery();
        Set<Path> recovered = new HashSet<>();

        for (int attempts = 0; attempts <= MAX_STARTUP_RECOVERIES; attempts++) {
            try {
                return runtimeManager.loadInitial();
            } catch (GuardianConfigurationException ex) {
                Path problemPath = ex.path().toAbsolutePath().normalize();
                if (!ex.recoverableAtStartup()
                    || attempts == MAX_STARTUP_RECOVERIES
                    || !recovered.add(problemPath)) {
                    throw activationFailure(ex);
                }

                getLogger().severe("Guardian rejected invalid startup file '" + problemPath + "': "
                    + DiagnosticText.oneLine(ex.getMessage()));
                try {
                    recoverStartupFile(dataDirectory, problemPath, recovery);
                } catch (IOException recoveryError) {
                    throw new IllegalStateException(
                        "Guardian could not preserve/recover invalid startup file '" + problemPath + "'. "
                            + "Guardian will remain disabled.", recoveryError);
                }
            }
        }

        throw new IllegalStateException("Guardian configuration activation exhausted startup recovery attempts.");
    }

    private void recoverStartupFile(
        Path dataDirectory,
        Path problemPath,
        GuardianStartupRecovery recovery
    ) throws IOException {
        Path data = dataDirectory.toAbsolutePath().normalize();
        Path config = data.resolve("config.yml").normalize();
        Path locales = data.resolve("locales").normalize();
        Path fallbackLocale = locales.resolve(GuardianLocaleLoader.FALLBACK_LOCALE + ".properties").normalize();

        if (problemPath.equals(config)) {
            recoverFromPackagedDefault(problemPath, "config.yml", recovery,
                "Review the backup and reapply any intended custom settings.");
            return;
        }

        if (problemPath.equals(fallbackLocale)) {
            recoverFromPackagedDefault(
                problemPath,
                "locales/" + GuardianLocaleLoader.FALLBACK_LOCALE + ".properties",
                recovery,
                "Review the backup before restoring any customized messages."
            );
            return;
        }

        if (problemPath.startsWith(locales)
            && problemPath.getFileName().toString().endsWith(".properties")) {
            GuardianStartupRecovery.RecoveryResult result = recovery.backupAndRemove(problemPath);
            getLogger().warning("Guardian preserved invalid optional locale as '" + result.backup()
                + "'. No packaged default exists for that locale; Guardian will use the required '"
                + GuardianLocaleLoader.FALLBACK_LOCALE + "' fallback catalog.");
            return;
        }

        throw new IOException("refusing startup recovery outside Guardian config/locale paths: " + problemPath);
    }

    private void recoverFromPackagedDefault(
        Path problemPath,
        String resourcePath,
        GuardianStartupRecovery recovery,
        String followUp
    ) throws IOException {
        try (InputStream defaultResource = getResource(resourcePath)) {
            if (defaultResource == null) {
                throw new IOException("packaged Guardian resource is missing: " + resourcePath);
            }
            GuardianStartupRecovery.RecoveryResult result =
                recovery.backupAndRestoreDefault(problemPath, defaultResource);
            getLogger().warning("Guardian preserved the invalid file as '" + result.backup()
                + "' and restored packaged defaults at '" + result.original() + "'. " + followUp);
        }
    }

    private void initializeArtifactCatalogSurface() {
        try {
            artifactImportService.ensureInputDirectory();
            if (Files.exists(getDataFolder().toPath().resolve("artifacts.yml"))) {
                int entries = artifactImportService.validateCatalog().size();
                getLogger().info("Guardian artifact catalog validated with " + entries + " exact artifact entries.");
            }
        } catch (ArtifactCatalogException ex) {
            // Standalone Admission policy validation has already rejected an invalid catalog
            // when policy authority needs it. Keep this later surface warning non-fatal for deployments
            // where Admission is disabled or policy authority lives at Velocity.
            getLogger().warning("Guardian artifact catalog is not ready for import: " + DiagnosticText.oneLine(ex.getMessage()));
        }
    }

    private void registerAdministratorCommand(GuardianMessageRenderer renderer) {
        GuardianPaperCommand command = new GuardianPaperCommand(
            this, renderer, artifactImportService, inspectionService);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
            event -> event.registrar().register("guardian", command));
    }

    private static IllegalStateException activationFailure(GuardianConfigurationException ex) {
        return new IllegalStateException("Guardian configuration activation failed: " + ex.getMessage(), ex);
    }

    private void ensureAdministratorFile(String resourcePath) {
        Path data = getDataFolder().toPath().toAbsolutePath().normalize();
        Path destination = data.resolve(resourcePath).normalize();
        if (!destination.startsWith(data)) {
            throw new IllegalStateException("Guardian administrator resource escapes plugin data directory: " + resourcePath);
        }
        try {
            Path parent = destination.getParent();
            if (parent != null && !parent.equals(data)) {
                SafeDirectory.ensureChildDirectories(data, parent);
            }
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) return;
            saveResource(resourcePath, false);
        } catch (IOException ex) {
            throw new IllegalStateException(
                "could not prepare Guardian administrator resource " + destination + ": " + ex.getMessage(), ex);
        }
    }
}
