package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.artifact.ArtifactCatalogException;
import com.badwolfmc.guardian.core.artifact.ArtifactImportResult;
import com.badwolfmc.guardian.core.artifact.ArtifactImportService;
import com.badwolfmc.guardian.core.operations.ActiveInspectionSnapshot;
import com.badwolfmc.guardian.paper.config.GuardianConfigurationException;
import com.badwolfmc.guardian.paper.config.GuardianRuntimeSnapshot;
import com.badwolfmc.guardian.paper.locale.GuardianMessageRenderer;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Paper-local Guardian administrator surface. It never proxies command requests to Velocity. */
final class GuardianPaperCommand implements BasicCommand {
    static final String STATUS_PERMISSION = "guardian.command.status";
    static final String VALIDATE_PERMISSION = "guardian.command.validate";
    static final String RELOAD_PERMISSION = "guardian.command.reload";
    static final String INSPECT_PERMISSION = "guardian.command.inspect";
    static final String ARTIFACTS_PERMISSION = "guardian.command.artifacts.scan";

    private final GuardianPaperPlugin plugin;
    private final GuardianMessageRenderer renderer;
    private final ArtifactImportService artifactImportService;
    private final PaperInspectionService inspectionService;
    private final AtomicBoolean scanRunning = new AtomicBoolean();

    GuardianPaperCommand(
        GuardianPaperPlugin plugin,
        GuardianMessageRenderer renderer,
        ArtifactImportService artifactImportService,
        PaperInspectionService inspectionService
    ) {
        this.plugin = plugin;
        this.renderer = renderer;
        this.artifactImportService = artifactImportService;
        this.inspectionService = inspectionService;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            send(sender, "command.usage.paper", TagResolver.empty());
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender, args);
            case "validate" -> validate(sender, args);
            case "reload" -> reload(sender, args);
            case "inspect" -> inspect(sender, args);
            case "artifacts" -> artifacts(sender, args);
            default -> send(sender, "command.usage.paper", TagResolver.empty());
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length <= 1) {
            List<String> roots = new ArrayList<>();
            if (sender.hasPermission(STATUS_PERMISSION)) roots.add("status");
            if (sender.hasPermission(VALIDATE_PERMISSION)) roots.add("validate");
            if (sender.hasPermission(RELOAD_PERMISSION)) roots.add("reload");
            if (sender.hasPermission(INSPECT_PERMISSION)) roots.add("inspect");
            if (sender.hasPermission(ARTIFACTS_PERMISSION)) roots.add("artifacts");
            return prefix(roots, args.length == 0 ? "" : args[0]);
        }
        if ("inspect".equalsIgnoreCase(args[0]) && sender.hasPermission(INSPECT_PERMISSION) && args.length == 2) {
            return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList(), args[1]);
        }
        if ("artifacts".equalsIgnoreCase(args[0]) && sender.hasPermission(ARTIFACTS_PERMISSION) && args.length == 2) {
            return prefix(List.of("scan"), args[1]);
        }
        return List.of();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(STATUS_PERMISSION)
            || sender.hasPermission(VALIDATE_PERMISSION)
            || sender.hasPermission(RELOAD_PERMISSION)
            || sender.hasPermission(INSPECT_PERMISSION)
            || sender.hasPermission(ARTIFACTS_PERMISSION);
    }

    private void status(CommandSender sender, String[] args) {
        if (args.length != 1) { send(sender, "command.usage.paper", TagResolver.empty()); return; }
        if (!require(sender, STATUS_PERMISSION)) return;
        GuardianRuntimeSnapshot snapshot = plugin.runtimeManager().current();
        String serverName = snapshot.settings().serverName().isBlank()
            ? plugin.getServer().getName() : snapshot.settings().serverName();
        send(sender, "command.status.paper.header", tags(
            "version", plugin.getPluginMeta().getVersion(),
            "protocol", Integer.toString(GuardianProtocol.VERSION),
            "server", serverName));
        send(sender, "command.status.paper.runtime", TagResolver.builder()
            .resolver(Placeholder.unparsed("authority", snapshot.settings().authorityMode().name()))
            .resolver(Placeholder.component("admission", localized(snapshot.settings().admissionEnabled()
                ? "command.value.enabled" : "command.value.disabled")))
            .resolver(Placeholder.component("protection", localized(snapshot.settings().protectionEnabled()
                ? "command.value.enabled" : "command.value.disabled")))
            .resolver(Placeholder.unparsed("locale", snapshot.settings().locale()))
            .resolver(Placeholder.unparsed("logging", snapshot.settings().loggingLevel().name()))
            .build());
        send(sender, "command.status.paper.integrations", TagResolver.builder()
            .resolver(Placeholder.component("luckperms", availability("LuckPerms")))
            .resolver(Placeholder.component("geyser", availability("Geyser-Spigot")))
            .resolver(Placeholder.component("floodgate", availability("floodgate")))
            .build());
        String source = snapshot.settings().proxyAssertionSecret() == null ? "-" : snapshot.settings().proxyAssertionSecret().sourceDescription();
        String fingerprint = snapshot.settings().proxyAssertionSecret() == null ? "-" : snapshot.settings().proxyAssertionSecret().fingerprint();
        send(sender, "command.status.paper.assertion", TagResolver.builder()
            .resolver(Placeholder.component("assertion", localized(snapshot.settings().authorityMode() == PaperAuthorityMode.VELOCITY
                ? "command.value.configured" : "command.value.not-applicable")))
            .resolver(Placeholder.unparsed("source", source))
            .resolver(Placeholder.unparsed("fingerprint", fingerprint))
            .build());
        send(sender, "command.status.paper.snapshots", tags(
            "authoritative", Integer.toString(inspectionService.authoritativeCount()),
            "backend", Integer.toString(inspectionService.backendCount())));
    }

    private void validate(CommandSender sender, String[] args) {
        if (args.length != 1) { send(sender, "command.usage.paper", TagResolver.empty()); return; }
        if (!require(sender, VALIDATE_PERMISSION)) return;
        try {
            plugin.runtimeManager().validateFiles();
            send(sender, "command.validate.success", TagResolver.empty());
        } catch (GuardianConfigurationException ex) {
            plugin.getLogger().warning("Guardian Paper validation rejected: " + ex.getMessage());
            send(sender, "command.validate.failed", tags("error", ex.getMessage()));
        }
    }

    private void reload(CommandSender sender, String[] args) {
        if (args.length != 1) { send(sender, "command.usage.paper", TagResolver.empty()); return; }
        if (!require(sender, RELOAD_PERMISSION)) return;
        try {
            plugin.reloadRuntime();
            send(sender, "command.reload.success", TagResolver.builder()
                .resolver(Placeholder.component("scope", localized("command.value.scope.paper-local")))
                .build());
        } catch (GuardianConfigurationException ex) {
            plugin.getLogger().warning("Guardian Paper reload rejected; previous runtime remains active: "
                + ex.getMessage());
            send(sender, "command.reload.failed", tags("error", ex.getMessage()));
        }
    }

    private void inspect(CommandSender sender, String[] args) {
        if (args.length != 2) { send(sender, "command.usage.paper", TagResolver.empty()); return; }
        if (!require(sender, INSPECT_PERMISSION)) return;
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "command.inspect.no-active-data", tags("player", args[1]));
            return;
        }
        GuardianRuntimeSnapshot runtime = plugin.runtimeManager().current();
        if (runtime.settings().authorityMode() == PaperAuthorityMode.VELOCITY) {
            BackendInspectionSnapshot snapshot = inspectionService.backend(target.getUniqueId()).orElse(null);
            if (snapshot == null) {
                send(sender, "command.inspect.no-active-data", tags("player", target.getName()));
                return;
            }
            send(sender, "command.inspect.paper.header", tags("player", snapshot.playerName()));
            send(sender, "command.inspect.paper.backend", tags(
                "server", snapshot.serverName(), "origin", snapshot.assertedOrigin().name()));
            send(sender, "command.inspect.paper.backend.sanity", tags(
                "sanity", snapshot.floodgateSanity().name(), "player", snapshot.playerName()));
            return;
        }

        ActiveInspectionSnapshot snapshot = inspectionService.authoritative(target.getUniqueId()).orElse(null);
        if (snapshot == null) {
            send(sender, "command.inspect.no-active-data", tags("player", target.getName()));
            return;
        }
        send(sender, "command.inspect.paper.header", tags("player", snapshot.playerName()));
        send(sender, "command.inspect.paper.standalone.client", tags(
            "classification", snapshot.classification().name(), "brand", snapshot.observedBrand()));
        send(sender, "command.inspect.paper.standalone.profile", tags(
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
        send(sender, "command.inspect.paper.standalone.cerberus", cerberusTags.build());
        send(sender, "command.inspect.paper.standalone.decision", tags(
            "outcome", snapshot.decision().outcome().name(), "reason", snapshot.decision().reason().name()));
        send(sender, "command.inspect.paper.standalone.mods", tags(
            "policy_mods", Integer.toString(snapshot.policyAddressableMods().size()),
            "loader_mods", Integer.toString(snapshot.loaderKnownCount())));
        for (ManifestEntry entry : snapshot.policyAddressableMods()) {
            send(sender, "command.inspect.paper.standalone.mod", tags(
                "mod_id", entry.modId(), "version", entry.version()));
        }
    }

    private void artifacts(CommandSender sender, String[] args) {
        if (args.length != 2 || !"scan".equalsIgnoreCase(args[1])) {
            send(sender, "command.usage.paper", TagResolver.empty());
            return;
        }
        if (!require(sender, ARTIFACTS_PERMISSION)) return;
        if (plugin.runtimeManager().current().settings().authorityMode() == PaperAuthorityMode.VELOCITY) {
            send(sender, "command.artifacts.velocity-owned", TagResolver.empty());
            return;
        }
        if (!scanRunning.compareAndSet(false, true)) {
            send(sender, "artifacts.scan.already-running", TagResolver.empty());
            return;
        }
        send(sender, "artifacts.scan.started", TagResolver.empty());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ArtifactImportResult result = artifactImportService.scanAndMerge();
                Bukkit.getScheduler().runTask(plugin, () -> sendArtifactSuccess(sender, result));
            } catch (ArtifactCatalogException ex) {
                plugin.getLogger().warning("Guardian artifact scan rejected: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin,
                    () -> send(sender, "artifacts.scan.failed", tags("error", ex.getMessage())));
            } finally {
                scanRunning.set(false);
            }
        });
    }

    private void sendArtifactSuccess(CommandSender sender, ArtifactImportResult result) {
        send(sender, result.catalogChanged() ? "artifacts.scan.success" : "artifacts.scan.unchanged", tags(
            "scanned", Integer.toString(result.scannedJars()),
            "discovered", Integer.toString(result.discoveredArtifacts()),
            "added", Integer.toString(result.addedCatalogEntries()),
            "total", Integer.toString(result.totalCatalogEntries())));
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        send(sender, "command.no-permission", TagResolver.empty());
        return false;
    }

    private Component availability(String pluginName) {
        return localized(plugin.getServer().getPluginManager().isPluginEnabled(pluginName)
            ? "command.value.available" : "command.value.unavailable");
    }

    private Component localized(String key) {
        return renderer.render(plugin.runtimeManager().current(), key, TagResolver.empty());
    }

    private void send(CommandSender sender, String key, TagResolver resolver) {
        sender.sendMessage(renderer.render(plugin.runtimeManager().current(), key, resolver));
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
