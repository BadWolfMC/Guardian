package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.artifact.ApprovedArtifact;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalogException;
import com.badwolfmc.guardian.core.artifact.ArtifactImportResult;
import com.badwolfmc.guardian.core.artifact.ArtifactImportService;
import com.badwolfmc.guardian.core.operations.ActiveInspectionSnapshot;
import com.badwolfmc.guardian.core.operations.ActiveInspectionStore;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.velocity.config.VelocityConfigurationException;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Network-authoritative Guardian-Velocity administrator surface. */
final class GuardianVelocityCommand implements SimpleCommand {
    static final String STATUS_PERMISSION = "guardian.velocity.command.status";
    static final String VALIDATE_PERMISSION = "guardian.velocity.command.validate";
    static final String RELOAD_PERMISSION = "guardian.velocity.command.reload";
    static final String INSPECT_PERMISSION = "guardian.velocity.command.inspect";
    static final String ARTIFACTS_PERMISSION = "guardian.velocity.command.artifacts.scan";

    private final Object plugin;
    private final ProxyServer server;
    private final Logger logger;
    private final VelocityRuntimeManager runtimeManager;
    private final ActiveInspectionStore inspections;
    private final ArtifactImportService artifactImportService;
    private final AtomicBoolean scanRunning = new AtomicBoolean();

    GuardianVelocityCommand(
        Object plugin,
        ProxyServer server,
        Logger logger,
        VelocityRuntimeManager runtimeManager,
        ActiveInspectionStore inspections,
        ArtifactImportService artifactImportService
    ) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
        this.runtimeManager = runtimeManager;
        this.inspections = inspections;
        this.artifactImportService = artifactImportService;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            send(invocation.source(), "command.usage.velocity", TagResolver.empty());
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(invocation.source(), args);
            case "validate" -> validate(invocation.source(), args);
            case "reload" -> reload(invocation.source(), args);
            case "inspect" -> inspect(invocation.source(), args);
            case "artifacts" -> artifacts(invocation.source(), args);
            default -> send(invocation.source(), "command.usage.velocity", TagResolver.empty());
        }
    }

    /** Always own /guardianv at the proxy; subcommand permissions are enforced explicitly here. */
    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        CommandSource source = invocation.source();
        if (args.length <= 1) {
            List<String> roots = new ArrayList<>();
            if (source.hasPermission(STATUS_PERMISSION)) roots.add("status");
            if (source.hasPermission(VALIDATE_PERMISSION)) roots.add("validate");
            if (source.hasPermission(RELOAD_PERMISSION)) roots.add("reload");
            if (source.hasPermission(INSPECT_PERMISSION)) roots.add("inspect");
            if (source.hasPermission(ARTIFACTS_PERMISSION)) roots.add("artifacts");
            return prefix(roots, args.length == 0 ? "" : args[0]);
        }
        if ("inspect".equalsIgnoreCase(args[0]) && source.hasPermission(INSPECT_PERMISSION) && args.length == 2) {
            return prefix(server.getAllPlayers().stream().map(Player::getUsername).sorted().toList(), args[1]);
        }
        if ("artifacts".equalsIgnoreCase(args[0]) && source.hasPermission(ARTIFACTS_PERMISSION) && args.length == 2) {
            return prefix(List.of("scan"), args[1]);
        }
        return List.of();
    }

    private void status(CommandSource source, String[] args) {
        if (args.length != 1) { send(source, "command.usage.velocity", TagResolver.empty()); return; }
        if (!require(source, STATUS_PERMISSION)) return;
        VelocityRuntimeSnapshot runtime = runtimeManager.current();
        send(source, "command.status.velocity.header", tags(
            "version", GuardianVelocityPlugin.VERSION,
            "protocol", Integer.toString(GuardianProtocol.VERSION)));
        send(source, "command.status.velocity.runtime", tags(
            "locale", runtime.settings().locale(),
            "logging", runtime.settings().loggingLevel().name(),
            "timeout", Integer.toString(runtime.settings().handshakeTimeoutSeconds())));
        send(source, "command.status.velocity.policy", tags(
            "profiles", Integer.toString(runtime.admissionPolicy().profiles().size()),
            "default_profile", runtime.admissionPolicy().defaultProfileId(),
            "artifacts", Integer.toString(runtime.artifactCatalog().size())));
        send(source, "command.status.velocity.integrations", TagResolver.builder()
            .resolver(Placeholder.component("luckperms", availability("luckperms")))
            .resolver(Placeholder.component("geyser", availability("geyser")))
            .resolver(Placeholder.component("floodgate", availability("floodgate")))
            .build());
        send(source, "command.status.velocity.assertion", tags(
            "source", runtime.settings().proxyAssertionSecret().sourceDescription(),
            "fingerprint", runtime.settings().proxyAssertionSecret().fingerprint()));
        send(source, "command.status.velocity.snapshots", tags("snapshots", Integer.toString(inspections.size())));
    }

    private void validate(CommandSource source, String[] args) {
        if (args.length != 1) { send(source, "command.usage.velocity", TagResolver.empty()); return; }
        if (!require(source, VALIDATE_PERMISSION)) return;
        try {
            runtimeManager.validateFiles();
            send(source, "command.validate.success", TagResolver.empty());
        } catch (VelocityConfigurationException ex) {
            logger.warn("Guardian-Velocity validation rejected: {}", ex.getMessage());
            send(source, "command.validate.failed", tags("error", ex.getMessage()));
        }
    }

    private void reload(CommandSource source, String[] args) {
        if (args.length != 1) { send(source, "command.usage.velocity", TagResolver.empty()); return; }
        if (!require(source, RELOAD_PERMISSION)) return;
        try {
            runtimeManager.reload();
            send(source, "command.reload.success", TagResolver.builder()
                .resolver(Placeholder.component("scope", localized("command.value.scope.velocity-admission")))
                .build());
        } catch (VelocityConfigurationException ex) {
            logger.warn("Guardian-Velocity reload rejected; previous runtime remains active: {}", ex.getMessage());
            send(source, "command.reload.failed", tags("error", ex.getMessage()));
        }
    }

    private void inspect(CommandSource source, String[] args) {
        if (args.length != 2) { send(source, "command.usage.velocity", TagResolver.empty()); return; }
        if (!require(source, INSPECT_PERMISSION)) return;
        Player target = server.getPlayer(args[1]).orElse(null);
        if (target == null) {
            send(source, "command.inspect.no-active-data", tags("player", args[1]));
            return;
        }
        ActiveInspectionSnapshot snapshot = inspections.get(target.getUniqueId()).orElse(null);
        if (snapshot == null) {
            send(source, "command.inspect.no-active-data", tags("player", target.getUsername()));
            return;
        }
        String backend = target.getCurrentServer()
            .map(connection -> connection.getServerInfo().getName())
            .orElse(snapshot.backend());
        if (!backend.equals(snapshot.backend())) {
            snapshot = snapshot.withBackend(backend);
            inspections.put(snapshot);
        }

        VelocityRuntimeSnapshot runtime = runtimeManager.current();
        send(source, "command.inspect.velocity.header", tags("player", snapshot.playerName()));
        send(source, "command.inspect.velocity.identity", tags(
            "backend", snapshot.backend(), "uuid", snapshot.playerId().toString()));
        send(source, "command.inspect.velocity.client", tags(
            "classification", snapshot.classification().name(), "brand", snapshot.observedBrand()));
        send(source, "command.inspect.velocity.profile", tags(
            "profile", snapshot.profileId(), "profile_source", snapshot.profileSource().name()));
        String protocol = snapshot.cerberusPresence() == null ? "-" : snapshot.cerberusPresence().minProtocolVersion()
            + ".." + snapshot.cerberusPresence().maxProtocolVersion();
        TagResolver.Builder cerberusTags = TagResolver.builder()
            .resolver(Placeholder.unparsed("cerberus_protocol", protocol));
        if (snapshot.cerberusPresence() == null) {
            cerberusTags.resolver(Placeholder.component("cerberus", localized("command.value.not-applicable")));
        } else {
            cerberusTags.resolver(Placeholder.unparsed("cerberus", snapshot.cerberusPresence().cerberusVersion()));
        }
        send(source, "command.inspect.velocity.cerberus", cerberusTags.build());
        send(source, "command.inspect.velocity.decision", tags(
            "outcome", snapshot.decision().outcome().name(), "reason", snapshot.decision().reason().name()));
        send(source, "command.inspect.velocity.mods-summary", tags(
            "policy_mods", Integer.toString(snapshot.policyAddressableMods().size()),
            "loader_mods", Integer.toString(snapshot.loaderKnownCount())));
        for (ManifestEntry entry : snapshot.policyAddressableMods()) {
            String statusKey;
            if (entry.artifactSha256() == null) {
                statusKey = "command.value.artifact.no-hash";
            } else if (runtime.artifactCatalog().contains(
                new ApprovedArtifact(entry.modId(), entry.version(), entry.artifactSha256()))) {
                statusKey = "command.value.artifact.catalogued";
            } else {
                statusKey = "command.value.artifact.not-catalogued";
            }
            send(source, "command.inspect.velocity.mod", TagResolver.builder()
                .resolver(Placeholder.unparsed("mod_id", entry.modId()))
                .resolver(Placeholder.unparsed("version", entry.version()))
                .resolver(Placeholder.component("artifact_status", localized(statusKey)))
                .build());
        }
        send(source, "command.inspect.velocity.bedrock", tags(
            "geyser", snapshot.bedrockEvidence().geyser().name(),
            "floodgate", snapshot.bedrockEvidence().floodgate().name()));
    }

    private void artifacts(CommandSource source, String[] args) {
        if (args.length != 2 || !"scan".equalsIgnoreCase(args[1])) {
            send(source, "artifacts.command.usage.velocity", TagResolver.empty());
            return;
        }
        if (!require(source, ARTIFACTS_PERMISSION)) return;
        if (!scanRunning.compareAndSet(false, true)) {
            send(source, "artifacts.scan.already-running", TagResolver.empty());
            return;
        }
        send(source, "artifacts.scan.started", TagResolver.empty());
        server.getScheduler().buildTask(plugin, () -> {
            try {
                ArtifactImportResult result = artifactImportService.scanAndMerge();
                send(source, result.catalogChanged() ? "artifacts.scan.success" : "artifacts.scan.unchanged", tags(
                    "scanned", Integer.toString(result.scannedJars()),
                    "discovered", Integer.toString(result.discoveredArtifacts()),
                    "added", Integer.toString(result.addedCatalogEntries()),
                    "total", Integer.toString(result.totalCatalogEntries())));
            } catch (ArtifactCatalogException ex) {
                logger.warn("Guardian-Velocity artifact scan rejected: {}", ex.getMessage());
                send(source, "artifacts.scan.failed", tags("error", ex.getMessage()));
            } finally {
                scanRunning.set(false);
            }
        }).schedule();
    }

    private boolean require(CommandSource source, String permission) {
        if (source.hasPermission(permission)) return true;
        send(source, "command.no-permission", TagResolver.empty());
        return false;
    }

    private Component availability(String pluginId) {
        return localized(server.getPluginManager().isLoaded(pluginId)
            ? "command.value.available" : "command.value.unavailable");
    }

    private Component localized(String key) {
        return runtimeManager.current().messages().render(key, TagResolver.empty());
    }

    private void send(CommandSource source, String key, TagResolver resolver) {
        source.sendMessage(runtimeManager.current().messages().render(key, resolver));
    }

    private static TagResolver tags(String... pairs) {
        TagResolver.Builder builder = TagResolver.builder();
        for (int i = 0; i < pairs.length; i += 2) {
            builder.resolver(Placeholder.unparsed(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]));
        }
        return builder.build();
    }

    private static List<String> prefix(List<String> values, String rawPrefix) {
        String prefix = rawPrefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
