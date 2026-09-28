package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.BedrockEvidence;
import com.badwolfmc.guardian.core.BedrockResolution;
import com.badwolfmc.guardian.core.BedrockSignal;
import com.badwolfmc.guardian.core.ClientOriginClassifier;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionOutcome;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.core.ProtocolV1ResponseValidator;
import com.badwolfmc.guardian.core.operations.ActiveInspectionSnapshot;
import com.badwolfmc.guardian.core.policy.AdmissionPermissionSnapshot;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyEvaluator;
import com.badwolfmc.guardian.core.policy.AdmissionProfileProvider;
import com.badwolfmc.guardian.core.policy.AdmissionProfileResolver;
import com.badwolfmc.guardian.core.policy.ClientPolicyResult;
import com.badwolfmc.guardian.core.policy.ResolvedAdmissionProfile;
import com.badwolfmc.guardian.core.ProxyAdmissionValidator;
import com.badwolfmc.guardian.protocol.Challenge;
import com.badwolfmc.guardian.protocol.ConnectionOrigin;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Presence;
import com.badwolfmc.guardian.protocol.ProtocolCodec;
import com.badwolfmc.guardian.protocol.ProtocolException;
import com.badwolfmc.guardian.protocol.ProxyAdmissionAssertion;
import com.badwolfmc.guardian.protocol.ProxyAdmissionCodec;
import com.badwolfmc.guardian.protocol.Response;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import com.badwolfmc.guardian.paper.config.GuardianRuntimeManager;
import com.badwolfmc.guardian.paper.locale.GuardianMessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Guardian-Paper Admission adapter preserving the Phase 0 proven transport boundaries.
 *
 * <p>In standalone authority mode it preserves the proven hybrid transport: CONFIGURATION handles brand
 * and Cerberus presence/protocol, while compatible Fabric clients complete the nonce exchange in
 * immediate quarantined PLAY. In Velocity authority mode Paper does not re-attest the client; it
 * accepts only a short-lived infrastructure-authenticated admission assertion from
 * Guardian-Velocity.</p>
 */
final class PaperAdmissionAdapter implements Listener, PluginMessageListener {
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<UUID, AdmissionSession> sessions = new ConcurrentHashMap<>();
    private final GuardianPaperPlugin plugin;
    private final GuardianRuntimeManager runtimeManager;
    private final GuardianMessageRenderer messageRenderer;
    private final AdmissionPolicyEvaluator policyEvaluator = new AdmissionPolicyEvaluator();
    private final PaperBedrockDetector bedrockDetector;
    private final PaperInspectionService inspectionService;
    private AdmissionProfileProvider profileProvider = AdmissionProfileProvider.none();

    PaperAdmissionAdapter(
        GuardianPaperPlugin plugin,
        GuardianRuntimeManager runtimeManager,
        GuardianMessageRenderer messageRenderer,
        PaperInspectionService inspectionService
    ) {
        this.plugin = plugin;
        this.runtimeManager = runtimeManager;
        this.messageRenderer = messageRenderer;
        this.inspectionService = inspectionService;
        this.bedrockDetector = new PaperBedrockDetector(plugin);
    }

    void enable() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(
            plugin, GuardianProtocol.CHALLENGE_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
            plugin, GuardianProtocol.PRESENCE_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
            plugin, GuardianProtocol.RESPONSE_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
            plugin, GuardianProtocol.PROXY_ADMISSION_CHANNEL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        try {
            profileProvider = PaperLuckPermsProfileProvider.create(plugin);
        } catch (RuntimeException | LinkageError ex) {
            profileProvider = AdmissionProfileProvider.none();
            plugin.getLogger().warning("Guardian could not initialize LuckPerms profile integration; "
                + "default/identity profiles remain available: " + ex.getMessage());
        }

        var settings = runtimeManager.current().settings();
        plugin.getLogger().info("Guardian Admission enabled: authority=" + settings.authorityMode()
            + ", handshakeTimeout=" + settings.handshakeTimeoutSeconds() + "s"
            + ", challengeChannelWait=" + settings.challengeChannelWaitTicks() + " ticks.");
    }

    void disable() {
        sessions.values().forEach(session ->
            session.response().completeExceptionally(new IllegalStateException("Guardian Admission disabled")));
        sessions.clear();
        inspectionService.clear();
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInitialConfigure(PlayerConnectionInitialConfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        UUID playerId = requirePlayerId(connection);
        if (playerId == null) {
            plugin.getLogger().warning("Initial configuration had no authenticated UUID; Admission session not created.");
            return;
        }

        inspectionService.remove(playerId);
        AdmissionSession session = new AdmissionSession(playerId, runtimeManager.current());
        sessions.put(playerId, session);
        debug(session, "Guardian Admission initial configuration: " + displayName(connection)
            + " brand=" + String.valueOf(connection.getClientBrandName())
            + " listeningChannels=" + connection.getListeningPluginChannels());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        UUID playerId = requirePlayerId(connection);
        if (playerId == null) {
            return;
        }

        AdmissionSession session = sessions.get(playerId);
        if (session == null) {
            return; // A fresh login session is created only for initial configuration.
        }

        if (session.snapshot().settings().authorityMode() == PaperAuthorityMode.VELOCITY) {
            debug(session, "Guardian backend configuration for " + displayName(connection)
                + ": authority=VELOCITY, brand=" + String.valueOf(connection.getClientBrandName())
                + ", proxyAssertion=" + (session.proxyAdmission() != null ? "present" : "pending"));
            // Do not wait here. Velocity's PlayerConfigurationEvent is fired after the backend has
            // finished its configuration work; blocking this async Paper event would risk a
            // circular wait. The final PlayerConnectionValidateLoginEvent is the admission gate.
            return;
        }

        resolveProfileAsyncGate(session);
        evaluateStandaloneConfiguration(connection, session, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onValidateLogin(PlayerConnectionValidateLoginEvent event) {
        if (!(event.getConnection() instanceof PlayerConfigurationConnection connection)) {
            return;
        }

        UUID playerId = requirePlayerId(connection);
        if (playerId == null) {
            return;
        }

        AdmissionSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }

        if (session.snapshot().settings().authorityMode() == PaperAuthorityMode.VELOCITY) {
            GuardianDecision decision = session.decision();
            if (decision == null) {
                decision = GuardianDecision.deny(
                    DecisionReason.PROXY_ASSERTION_REQUIRED,
                    "no valid Guardian-Velocity admission assertion arrived before final validation");
                session.decide(decision);
            }

            GuardianDecision finalDecision = session.decision();
            debug(session, "Guardian backend pre-world decision for " + displayName(connection) + ": "
                + finalDecision.outcome() + " / " + finalDecision.reason() + " ("
                + finalDecision.detail() + ")");
            if (finalDecision.outcome() == DecisionOutcome.DENY) {
                event.kickMessage(messageRenderer.renderDecision(
                    session.snapshot(), finalDecision, session.classification()));
            }
            sessions.remove(playerId, session);
            return;
        }

        // Velocity currently mirrors the client brand to a backend late in CONFIGURATION. If the
        // async configuration event observed null, retry once here before deciding. This preserves
        // direct standalone behavior while giving transparent Velocity mode a supported late gate.
        if (session.decision() == null && !session.playHandshakeRequired()) {
            evaluateStandaloneConfiguration(connection, session, true);
        }

        if (session.playHandshakeRequired() && session.decision() == null) {
            debug(session, "Standalone CONFIGURATION gate passed for " + displayName(connection)
                + ": compatible Cerberus presence detected; nonce handshake deferred to quarantined PLAY.");
            return; // Keep the session for PlayerJoinEvent / PLAY plugin messaging.
        }

        GuardianDecision decision = session.decision();
        if (decision == null) {
            decision = GuardianDecision.deny(DecisionReason.CLIENT_DENIED,
                "client brand remained unavailable through final standalone validation");
            session.decide(decision);
        }

        GuardianDecision finalDecision = session.decision();
        debug(session, "Standalone pre-world decision for " + displayName(connection) + ": "
            + finalDecision.outcome() + " / " + finalDecision.reason() + " ("
            + finalDecision.detail() + ")");

        logStandaloneSummary(displayName(connection), session, finalDecision);
        if (finalDecision.outcome() == DecisionOutcome.DENY) {
            event.kickMessage(messageRenderer.renderDecision(
                    session.snapshot(), finalDecision, session.classification()));
        } else {
            captureStandaloneInspection(displayName(connection), session, finalDecision);
        }
        sessions.remove(playerId, session);
    }

    private void resolveProfileAsyncGate(AdmissionSession session) {
        if (session.resolvedProfile() != null) return;
        AdmissionPermissionSnapshot permissions;
        try {
            permissions = profileProvider.resolve(session.playerId(), session.snapshot().requireAdmissionPolicy())
                .toCompletableFuture()
                .orTimeout(session.snapshot().settings().handshakeTimeoutSeconds(), TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Guardian Admission profile provider failed for "
                        + session.playerId() + "; falling back to default/identity profile without bypasses: "
                        + rootMessage(throwable));
                    return AdmissionPermissionSnapshot.none();
                })
                .join();
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Guardian Admission profile provider failed for " + session.playerId()
                + "; falling back to default/identity profile without bypasses: " + rootMessage(ex));
            permissions = AdmissionPermissionSnapshot.none();
        }
        session.setResolvedProfile(AdmissionProfileResolver.resolve(
            session.snapshot().requireAdmissionPolicy(), session.playerId(), permissions));
    }

    private ResolvedAdmissionProfile resolvedProfile(AdmissionSession session) {
        ResolvedAdmissionProfile resolved = session.resolvedProfile();
        if (resolved != null) return resolved;
        // The async CONFIGURATION event is the normal provider-resolution point. If a platform lifecycle
        // edge reaches final validation without it, fail safely to deterministic identity/default resolution
        // rather than blocking the final login gate on external storage.
        resolved = AdmissionProfileResolver.resolve(
            session.snapshot().requireAdmissionPolicy(), session.playerId(), AdmissionPermissionSnapshot.none());
        session.setResolvedProfile(resolved);
        return session.resolvedProfile();
    }

    private void evaluateStandaloneConfiguration(
        PlayerConfigurationConnection connection, AdmissionSession session, boolean finalAttempt
    ) {
        if (session.decision() != null || session.playHandshakeRequired()) {
            return;
        }

        BedrockEvidence bedrock = bedrockDetector.detect(session.playerId());
        session.setBedrockEvidence(bedrock);
        if (bedrock.disagrees()) {
            plugin.getLogger().warning("Guardian Geyser/Floodgate disagreement for " + displayName(connection)
                + ": geyser=" + bedrock.geyser() + ", floodgate=" + bedrock.floodgate()
                + "; positive supported API evidence classifies this connection as BEDROCK.");
        }
        if (bedrock.resolution() == BedrockResolution.INDETERMINATE) {
            session.decide(GuardianDecision.deny(
                DecisionReason.CONFIGURATION_ERROR,
                "Bedrock origin integration failed; refusing to reinterpret an indeterminate connection as Java"));
            plugin.getLogger().warning("Guardian could not determine standalone connection origin for "
                + displayName(connection) + " because an available Bedrock integration failed: geyser="
                + bedrock.geyser() + ", floodgate=" + bedrock.floodgate());
            return;
        }

        String brand = connection.getClientBrandName();
        session.setObservedBrand(brand);
        if (bedrock.resolution() == BedrockResolution.JAVA
            && (brand == null || brand.isBlank()) && !finalAttempt) {
            debug(session, "Guardian standalone configuration for " + displayName(connection)
                + ": brand not yet available; deferring Java classification to final login validation"
                + ", geyser=" + bedrock.geyser() + ", floodgate=" + bedrock.floodgate()
                + ", cerberusPresent=" + session.cerberusPresent());
            return;
        }

        ClientClassification classification = ClientOriginClassifier.classify(
            bedrock.geyser() == BedrockSignal.BEDROCK,
            bedrock.floodgate() == BedrockSignal.BEDROCK,
            brand);
        session.setClassification(classification);
        ResolvedAdmissionProfile resolved = resolvedProfile(session);
        ClientPolicyResult clientResult = policyEvaluator.evaluateClient(resolved, classification, brand);
        debug(session, "Guardian standalone policy evaluation for " + displayName(connection)
            + ": brand=" + String.valueOf(brand)
            + ", classification=" + classification
            + ", profile=" + resolved.profile().id()
            + ", profileSource=" + resolved.source()
            + ", action=" + clientResult.action()
            + ", cerberusPresent=" + session.cerberusPresent());

        if (clientResult.terminalDecision() != null) {
            session.decide(clientResult.terminalDecision());
            return;
        }

        // REQUIRE_CERBERUS is valid only for JAVA_FABRIC. Shared policy validation enforces that
        // invariant so raw unknown-brand rules cannot create an attestation path.
        GuardianDecision presenceFailure = session.configurationPresenceFailure();
        if (presenceFailure != null) {
            session.decide(presenceFailure);
            return;
        }
        if (!session.cerberusPresent()) {
            session.decide(GuardianDecision.deny(DecisionReason.CERBERUS_REQUIRED,
                "Fabric client did not send a valid Cerberus CONFIGURATION presence payload"));
            return;
        }

        // Paper's supported CONFIGURATION send path has the channel-registration limitation proven
        // during feasibility testing. Only the nonce challenge/response moves into bounded quarantined PLAY.
        session.requirePlayHandshake();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        AdmissionSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.playHandshakeRequired()) {
            return;
        }

        session.setQuarantined(true);
        debug(session, "Guardian PLAY quarantine active for " + player.getName()
            + "; listeningChannels=" + player.getListeningPluginChannels());

        plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> handleHandshakeTimeout(player.getUniqueId()),
            session.snapshot().settings().handshakeTimeoutTicks());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inspectionService.remove(event.getPlayer().getUniqueId());
        AdmissionSession session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.response().completeExceptionally(new IllegalStateException("player quit"));
        }
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        inspectionService.remove(event.getPlayerUniqueId());
        AdmissionSession session = sessions.remove(event.getPlayerUniqueId());
        if (session != null) {
            session.response().completeExceptionally(new IllegalStateException("connection closed"));
        }
    }

    // --- Bounded PLAY quarantine retained from the proven standalone transport ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isQuarantined(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isQuarantined(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isQuarantined(player)) event.setCancelled(true);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        AdmissionSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.playHandshakeRequired()) {
            return;
        }

        if (GuardianProtocol.PRESENCE_CHANNEL.equals(channel)) {
            handlePlayPresence(player, session, message);
            return;
        }
        if (GuardianProtocol.RESPONSE_CHANNEL.equals(channel)) {
            handlePlayResponse(player, session, message);
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull PlayerConnection connection, byte @NotNull [] message) {
        if (!(connection instanceof PlayerConfigurationConnection configurationConnection)) {
            return;
        }

        UUID playerId = requirePlayerId(configurationConnection);
        if (playerId == null) {
            return;
        }
        AdmissionSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }

        if (GuardianProtocol.PROXY_ADMISSION_CHANNEL.equals(channel)) {
            if (session.snapshot().settings().authorityMode() == PaperAuthorityMode.VELOCITY) {
                handleProxyAdmission(configurationConnection, session, message);
            } else {
                plugin.getLogger().warning("Ignoring proxy admission assertion while Paper is in standalone authority mode for "
                    + displayName(configurationConnection));
            }
            return;
        }

        if (session.snapshot().settings().authorityMode() == PaperAuthorityMode.VELOCITY) {
            // Guardian-Velocity is required to consume the client-facing Cerberus channels. If one
            // reaches the backend, the network trust boundary is not behaving as designed.
            if (GuardianProtocol.PRESENCE_CHANNEL.equals(channel)
                || GuardianProtocol.RESPONSE_CHANNEL.equals(channel)) {
                session.decide(GuardianDecision.deny(DecisionReason.CONFIGURATION_ERROR,
                    "client-facing Guardian channel leaked through Velocity to the backend"));
                plugin.getLogger().warning("Guardian channel-isolation violation for "
                    + displayName(configurationConnection) + ": " + channel);
            }
            return;
        }

        if (GuardianProtocol.PRESENCE_CHANNEL.equals(channel)) {
            handleConfigurationPresence(configurationConnection, session, message);
            return;
        }
        if (GuardianProtocol.RESPONSE_CHANNEL.equals(channel)) {
            // A CONFIGURATION response is not expected in standalone fallback mode. Record the
            // protocol failure, but let shared client policy decide first whether Cerberus applies.
            session.recordConfigurationPresenceFailure(GuardianDecision.deny(DecisionReason.MANIFEST_INVALID,
                "unexpected Cerberus response during CONFIGURATION fallback mode"));
        }
    }

    private void handleProxyAdmission(
        PlayerConfigurationConnection connection, AdmissionSession session, byte[] message
    ) {
        byte[] proxySecret = runtimeManager.current().settings().proxyAssertionSecret().copyBytes();
        final ProxyAdmissionAssertion assertion;
        try {
            assertion = ProxyAdmissionCodec.decodeAndVerify(message, proxySecret);
        } catch (ProtocolException | IllegalArgumentException ex) {
            session.decide(GuardianDecision.deny(DecisionReason.PROXY_ASSERTION_INVALID,
                "invalid trusted proxy assertion: " + ex.getMessage()));
            plugin.getLogger().warning("Rejected Guardian proxy assertion for " + displayName(connection)
                + ": " + ex.getMessage());
            return;
        }

        long now = System.currentTimeMillis();
        UUID expectedPlayerId = requirePlayerId(connection);
        if (expectedPlayerId == null) {
            session.decide(GuardianDecision.deny(DecisionReason.PROXY_ASSERTION_INVALID,
                "authenticated player UUID unavailable while verifying proxy assertion"));
            return;
        }
        GuardianDecision metadataDecision = ProxyAdmissionValidator.validate(assertion, expectedPlayerId, now);
        if (metadataDecision.outcome() == DecisionOutcome.DENY) {
            session.decide(metadataDecision);
            plugin.getLogger().warning("Rejected Guardian proxy assertion metadata for "
                + displayName(connection) + ": " + metadataDecision.detail());
            return;
        }

        if (!session.recordProxyAdmission(assertion)) {
            session.decide(GuardianDecision.deny(DecisionReason.PROXY_ASSERTION_INVALID,
                "conflicting duplicate proxy admission assertion"));
            return;
        }

        BackendInspectionSnapshot.FloodgateSanity sanity = sanityCheckBackendFloodgate(connection, assertion, session);
        session.decide(metadataDecision);
        inspectionService.putBackend(new BackendInspectionSnapshot(
            assertion.playerId(), displayName(connection), serverName(), assertion.connectionOrigin(),
            HexFormat.of().formatHex(assertion.proxySessionId()), metadataDecision, sanity));
        debug(session, "Trusted Guardian proxy admission received for " + displayName(connection)
            + ": session=" + HexFormat.of().formatHex(assertion.proxySessionId())
            + ", origin=" + assertion.connectionOrigin()
            + ", expiresInMs=" + Math.max(0L, assertion.expiresAtEpochMillis() - now));
    }

    private BackendInspectionSnapshot.FloodgateSanity sanityCheckBackendFloodgate(
        PlayerConfigurationConnection connection, ProxyAdmissionAssertion assertion, AdmissionSession session
    ) {
        boolean proxyBedrock = assertion.connectionOrigin() == ConnectionOrigin.BEDROCK;
        org.bukkit.plugin.Plugin floodgate = plugin.getServer().getPluginManager().getPlugin("floodgate");
        if (floodgate == null || !floodgate.isEnabled()) {
            if (proxyBedrock) {
                debug(session, "Guardian backend Floodgate sanity check skipped for "
                    + displayName(connection) + ": proxy origin=BEDROCK, backend Floodgate unavailable.");
            }
            return BackendInspectionSnapshot.FloodgateSanity.NOT_AVAILABLE;
        }

        final boolean backendBedrock;
        try {
            backendBedrock = FloodgateBedrockLookup.isFloodgatePlayer(assertion.playerId());
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().warning("Guardian backend Floodgate sanity check failed for "
                + displayName(connection) + ": " + ex.getMessage());
            return BackendInspectionSnapshot.FloodgateSanity.NOT_AVAILABLE;
        }

        if (proxyBedrock == backendBedrock) {
            debug(session, "Guardian backend Floodgate sanity check agrees for "
                + displayName(connection) + ": proxyOrigin=" + assertion.connectionOrigin()
                + ", backendFloodgate=" + backendBedrock);
            return BackendInspectionSnapshot.FloodgateSanity.AGREES;
        }

        // Guardian-Velocity remains the sole admission authority in Velocity mode. A backend
        // mismatch is security-relevant defense-in-depth evidence and is logged prominently, but
        // Guardian-Paper must not turn this sanity check into an independent second policy decision.
        plugin.getLogger().warning("Guardian BACKEND FLOODGATE DISAGREEMENT for " + displayName(connection)
            + ": proxyOrigin=" + assertion.connectionOrigin()
            + ", backendFloodgate=" + backendBedrock
            + ". Trusted proxy admission remains authoritative for this deployment.");
        return BackendInspectionSnapshot.FloodgateSanity.DISAGREES;
    }

    private void handleConfigurationPresence(
        PlayerConfigurationConnection connection, AdmissionSession session, byte[] message
    ) {
        if (message.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            session.recordConfigurationPresenceFailure(GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "CONFIGURATION presence exceeds Guardian protocol limit"));
            return;
        }

        final Presence presence;
        try {
            presence = ProtocolCodec.decodePresence(message);
        } catch (ProtocolException ex) {
            session.recordConfigurationPresenceFailure(GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "invalid Cerberus CONFIGURATION presence: " + ex.getMessage()));
            return;
        }

        if (!session.recordPresence(presence)) {
            session.recordConfigurationPresenceFailure(GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "conflicting duplicate Cerberus presence"));
            return;
        }

        debug(session, "Guardian Cerberus CONFIGURATION presence from " + displayName(connection)
            + ": protocol=" + presence.minProtocolVersion() + ".." + presence.maxProtocolVersion()
            + ", brand=" + String.valueOf(connection.getClientBrandName()));

        if (!presence.supports(GuardianProtocol.VERSION) || (presence.capabilities() & GuardianProtocol.REQUIRED_CAPABILITIES) != GuardianProtocol.REQUIRED_CAPABILITIES) {
            session.recordConfigurationPresenceFailure(GuardianDecision.deny(
                DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
                "Cerberus announced protocol range " + presence.minProtocolVersion() + ".." + presence.maxProtocolVersion()
                    + " with capabilities 0x" + Long.toHexString(presence.capabilities())
                    + "; Guardian requires protocol " + GuardianProtocol.VERSION + " capabilities 0x" + Long.toHexString(GuardianProtocol.REQUIRED_CAPABILITIES)));
        }
    }

    private void handlePlayPresence(Player player, AdmissionSession session, byte[] message) {
        Presence presence = decodePresence(session, message, "PLAY");
        if (presence == null) {
            GuardianDecision decision = session.decision();
            if (decision != null) {
                finishPlayDecision(player, session, decision);
            }
            return;
        }
        if (session.decision() != null) {
            finishPlayDecision(player, session, session.decision());
            return;
        }

        debug(session, "Guardian Cerberus PLAY presence from " + player.getName()
            + ": protocol=" + presence.minProtocolVersion() + ".." + presence.maxProtocolVersion()
            + ", listeningChannels=" + player.getListeningPluginChannels());

        if (!presence.supports(GuardianProtocol.VERSION) || (presence.capabilities() & GuardianProtocol.REQUIRED_CAPABILITIES) != GuardianProtocol.REQUIRED_CAPABILITIES) {
            finishPlayDecision(player, session, GuardianDecision.deny(
                DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
                "Cerberus announced protocol range " + presence.minProtocolVersion() + ".." + presence.maxProtocolVersion()
                    + " with capabilities 0x" + Long.toHexString(presence.capabilities())
                    + "; Guardian requires protocol " + GuardianProtocol.VERSION + " capabilities 0x" + Long.toHexString(GuardianProtocol.REQUIRED_CAPABILITIES)));
            return;
        }

        sendPlayChallenge(player, session, 0);
    }

    private Presence decodePresence(AdmissionSession session, byte[] message, String phase) {
        if (message.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            session.decide(GuardianDecision.deny(DecisionReason.MANIFEST_INVALID,
                phase + " presence exceeds Guardian protocol limit"));
            return null;
        }

        final Presence presence;
        try {
            presence = ProtocolCodec.decodePresence(message);
        } catch (ProtocolException ex) {
            session.decide(GuardianDecision.deny(DecisionReason.MANIFEST_INVALID,
                "invalid Cerberus " + phase + " presence: " + ex.getMessage()));
            return null;
        }

        if (!session.recordPresence(presence)) {
            session.decide(GuardianDecision.deny(DecisionReason.MANIFEST_INVALID,
                "conflicting duplicate Cerberus presence"));
            return null;
        }
        return presence;
    }

    private void sendPlayChallenge(Player player, AdmissionSession session, int waitedTicks) {
        if (session.challengeSent() || session.decision() != null || !player.isOnline()) {
            return;
        }

        Set<String> listeningChannels = player.getListeningPluginChannels();
        if (!listeningChannels.contains(GuardianProtocol.CHALLENGE_CHANNEL)) {
            if (waitedTicks < session.snapshot().settings().challengeChannelWaitTicks()) {
                if (waitedTicks == 0) {
                    debug(session, "Cerberus PLAY presence arrived before Paper saw the challenge channel for "
                        + player.getName() + "; waiting up to " + session.snapshot().settings().challengeChannelWaitTicks()
                        + " ticks for registration. listeningChannels=" + listeningChannels);
                }
                plugin.getServer().getScheduler().runTaskLater(
                    plugin,
                    () -> sendPlayChallenge(player, session, waitedTicks + 1),
                    1L
                );
            } else {
                finishPlayDecision(player, session, GuardianDecision.deny(
                    DecisionReason.CERBERUS_TIMEOUT,
                    "Cerberus PLAY challenge channel was not registered within "
                        + session.snapshot().settings().challengeChannelWaitTicks() + " ticks"));
            }
            return;
        }

        if (waitedTicks > 0) {
            debug(session, "Guardian challenge channel became available for " + player.getName()
                + " after " + waitedTicks + " tick(s).");
        }

        if (!session.tryMarkChallengeSent()) {
            return;
        }

        byte[] nonce = new byte[GuardianProtocol.NONCE_BYTES];
        random.nextBytes(nonce);
        session.setNonce(nonce);

        try {
            player.sendPluginMessage(
                plugin,
                GuardianProtocol.CHALLENGE_CHANNEL,
                ProtocolCodec.encodeChallenge(new Challenge(GuardianProtocol.VERSION, GuardianProtocol.REQUIRED_CAPABILITIES, nonce))
            );
            debug(session, "Guardian PLAY challenge sent to " + player.getName()
                + "; listeningChannels=" + player.getListeningPluginChannels());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not send Guardian PLAY challenge to " + player.getName() + ": " + ex);
            finishPlayDecision(player, session, GuardianDecision.deny(
                DecisionReason.CONFIGURATION_ERROR, "PLAY challenge send failed"));
        }
    }

    private void handlePlayResponse(Player player, AdmissionSession session, byte[] message) {
        if (!session.challengeSent()) {
            finishPlayDecision(player, session, GuardianDecision.deny(DecisionReason.MANIFEST_INVALID, "Cerberus response arrived before Guardian challenge"));
            return;
        }
        if (!session.tryMarkResponseReceived()) {
            finishPlayDecision(player, session, GuardianDecision.deny(DecisionReason.MANIFEST_INVALID, "duplicate Cerberus response"));
            return;
        }
        if (session.decision() != null) return;
        if (message.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            finishPlayDecision(player, session, GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "PLAY response exceeds Guardian protocol limit"));
            return;
        }

        final Response response;
        try {
            response = ProtocolCodec.decodeResponse(message);
        } catch (ProtocolException ex) {
            finishPlayDecision(player, session, GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "invalid PLAY response: " + ex.getMessage()));
            return;
        }

        session.response().complete(response);
        session.setManifest(response.manifest());
        GuardianDecision integrityDecision = ProtocolV1ResponseValidator.validate(session.nonce(), response);
        if (integrityDecision.outcome() == DecisionOutcome.DENY) {
            finishPlayDecision(player, session, integrityDecision);
            return;
        }
        GuardianDecision policyDecision = policyEvaluator
            .evaluateManifest(resolvedProfile(session), response.manifest())
            .decision();
        finishPlayDecision(player, session, policyDecision);
    }

    private void handleHandshakeTimeout(UUID playerId) {
        AdmissionSession session = sessions.get(playerId);
        if (session == null || !session.playHandshakeRequired() || session.decision() != null) {
            return;
        }

        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            sessions.remove(playerId, session);
            return;
        }

        GuardianDecision decision = session.challengeSent()
            ? GuardianDecision.deny(DecisionReason.CERBERUS_TIMEOUT,
                "Cerberus entered PLAY and received/was sent a challenge, but no valid response arrived in time")
            : GuardianDecision.deny(DecisionReason.CERBERUS_TIMEOUT,
                "Cerberus was detected during CONFIGURATION but did not complete PLAY handshake startup in time");
        finishPlayDecision(player, session, decision);
    }

    private void finishPlayDecision(Player player, AdmissionSession session, GuardianDecision decision) {
        session.decide(decision);
        GuardianDecision finalDecision = session.decision();
        if (finalDecision == null) {
            return;
        }

        debug(session, "Guardian PLAY decision for " + player.getName() + ": "
            + finalDecision.outcome() + " / " + finalDecision.reason() + " (" + finalDecision.detail() + ")");
        logStandaloneSummary(player.getName(), session, finalDecision);

        if (finalDecision.outcome() == DecisionOutcome.ALLOW) {
            session.setQuarantined(false);
            captureStandaloneInspection(player.getName(), session, finalDecision);
            sessions.remove(player.getUniqueId(), session);
            debug(session, "Guardian PLAY quarantine released for " + player.getName());
        } else {
            player.kick(messageRenderer.renderDecision(
                session.snapshot(), finalDecision, session.classification()));
            sessions.remove(player.getUniqueId(), session);
        }
    }


    private void captureStandaloneInspection(
        String playerName, AdmissionSession session, GuardianDecision decision
    ) {
        if (session.classification() == null || session.resolvedProfile() == null || session.bedrockEvidence() == null) {
            return;
        }
        ActiveInspectionSnapshot snapshot = new ActiveInspectionSnapshot(
            session.playerId(), playerName, serverName(), session.classification(), session.observedBrand(),
            session.resolvedProfile().profile().id(), session.resolvedProfile().source(), session.cerberusPresence(),
            decision, session.manifest(), session.bedrockEvidence());
        if (!inspectionService.putAuthoritative(snapshot)) {
            plugin.getLogger().warning("Guardian active inspection store is full; snapshot omitted for " + playerName);
        }
    }

    private void logStandaloneSummary(String playerName, AdmissionSession session, GuardianDecision decision) {
        if (!session.tryMarkSummaryLogged()) return;
        String profile = session.resolvedProfile() == null ? "<unknown>" : session.resolvedProfile().profile().id();
        int mods = session.manifest() == null ? 0 : session.manifest().entries().size();
        plugin.getLogger().info("Guardian " + playerName + " " + decision.outcome() + ": "
            + (session.classification() == null ? "UNKNOWN" : session.classification())
            + ", profile=" + profile + ", " + decision.reason()
            + (mods == 0 ? "" : ", mods=" + mods));
    }

    private void debug(AdmissionSession session, String message) {
        if (session.snapshot().settings().loggingLevel().debugEnabled()) plugin.getLogger().info(message);
    }

    private String serverName() {
        String configured = runtimeManager.current().settings().serverName();
        return configured.isBlank() ? plugin.getServer().getName() : configured;
    }

    private boolean isQuarantined(Player player) {
        AdmissionSession session = sessions.get(player.getUniqueId());
        return session != null && session.quarantined();
    }

    private static UUID requirePlayerId(PlayerConfigurationConnection connection) {
        return connection.getProfile().getId();
    }

    private static String displayName(PlayerConfigurationConnection connection) {
        String name = connection.getProfile().getName();
        UUID id = connection.getProfile().getId();
        return name != null ? name : String.valueOf(id);
    }
}
