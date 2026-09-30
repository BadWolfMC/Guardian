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
import com.badwolfmc.guardian.core.ProxyAssertionReplayGuard;
import com.badwolfmc.guardian.core.operations.ActiveInspectionSnapshot;
import com.badwolfmc.guardian.core.operations.DiagnosticText;
import com.badwolfmc.guardian.core.policy.AdmissionPermissionSnapshot;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyEvaluator;
import com.badwolfmc.guardian.core.policy.AdmissionProfileProvider;
import com.badwolfmc.guardian.core.policy.AdmissionProfileProviderGate;
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
import com.badwolfmc.guardian.paper.config.GuardianRuntimeManager;
import com.badwolfmc.guardian.paper.locale.GuardianMessageRenderer;
import net.luckperms.api.LuckPerms;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private static final int MAX_PROXY_ASSERTION_REPLAY_ENTRIES = 16_384;

    private final SecureRandom random = new SecureRandom();
    private final PaperAdmissionSessions sessions = new PaperAdmissionSessions();
    private final ProxyAssertionReplayGuard proxyAssertionReplayGuard =
        new ProxyAssertionReplayGuard(MAX_PROXY_ASSERTION_REPLAY_ENTRIES);
    private final GuardianPaperPlugin plugin;
    private final GuardianRuntimeManager runtimeManager;
    private final GuardianMessageRenderer messageRenderer;
    private final AdmissionPolicyEvaluator policyEvaluator = new AdmissionPolicyEvaluator();
    private final PaperBedrockDetector bedrockDetector;
    private final PaperInspectionService inspectionService;
    private final PaperQuarantineGuard quarantineGuard;
    private volatile AdmissionProfileProvider profileProvider = AdmissionProfileProvider.none();

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
        this.quarantineGuard = new PaperQuarantineGuard(sessions);
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
        plugin.getServer().getPluginManager().registerEvents(quarantineGuard, plugin);
        refreshPaperProfileProvider("startup");

        var settings = runtimeManager.current().settings();
        plugin.getLogger().info("Guardian Admission enabled: authority=" + settings.authorityMode()
            + ", handshakeTimeout=" + settings.handshakeTimeoutSeconds() + "s"
            + ", challengeChannelWait=" + settings.challengeChannelWaitTicks() + " ticks.");
    }

    void disable() {
        sessions.all().forEach(session ->
            session.response().completeExceptionally(new IllegalStateException("Guardian Admission disabled")));
        sessions.clear();
        proxyAssertionReplayGuard.clear();
        inspectionService.clear();
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        String pluginName = event.getPlugin().getName();
        if (bedrockDetector.pluginDisabled(pluginName)) {
            plugin.getLogger().warning("Guardian Bedrock-origin provider " + DiagnosticText.oneLine(pluginName)
                + " was disabled after startup; standalone origin checks now fail closed until it is restored, "
                + "and Velocity-mode backend Floodgate checks remain diagnostic only.");
        }
        if (!pluginName.equalsIgnoreCase("LuckPerms")) return;
        profileProvider = AdmissionProfileProvider.unavailable(
            "LuckPerms was disabled after Guardian selected it as the profile provider");
        plugin.getLogger().warning("LuckPerms was disabled; Guardian Admission profile resolution now fails closed "
            + "for players without explicit identity overrides.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        String pluginName = event.getPlugin().getName();
        if (bedrockDetector.pluginEnabled(pluginName)) {
            plugin.getLogger().info("Guardian Bedrock-origin provider " + DiagnosticText.oneLine(pluginName)
                + " enabled/restored; subsequent origin checks will query its supported API.");
        }
        if (!pluginName.equalsIgnoreCase("LuckPerms")) return;
        refreshPaperProfileProvider("LuckPerms enable");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getService() != LuckPerms.class) return;
        refreshPaperProfileProvider("LuckPerms service registration");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (event.getProvider().getService() != LuckPerms.class) return;
        profileProvider = AdmissionProfileProvider.unavailable(
            "LuckPerms API service was unregistered after Guardian selected it as the profile provider");
        plugin.getLogger().warning("LuckPerms API service was unregistered; Guardian Admission profile resolution "
            + "now fails closed for players without explicit identity overrides.");
    }

    private void refreshPaperProfileProvider(String trigger) {
        try {
            profileProvider = PaperLuckPermsProfileProvider.create(plugin);
        } catch (RuntimeException | LinkageError ex) {
            profileProvider = AdmissionProfileProvider.unavailable(
                "LuckPerms profile integration could not initialize: " + rootMessage(ex));
            plugin.getLogger().warning("Guardian could not initialize the detected LuckPerms profile integration "
                + "during " + trigger + "; affected admissions fail closed: " + rootMessage(ex));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInitialConfigure(PlayerConnectionInitialConfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        UUID playerId = requirePlayerId(connection);
        if (playerId == null) {
            plugin.getLogger().warning("Initial configuration had no authenticated UUID; Admission session not created.");
            return;
        }

        AdmissionSession session = new AdmissionSession(playerId, runtimeManager.current(), connection);
        sessions.add(session);
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

        AdmissionSession session = sessions.forConfiguration(playerId, connection);
        if (session == null) {
            return; // A fresh login session is created only for initial configuration.
        }
        if (session.readyForPlay()) {
            return; // Final validation already handed this exact session to PLAY.
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

        AdmissionSession session = sessions.forConfiguration(playerId, connection);
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
                sessions.remove(session);
            } else {
                session.markReadyForPlay();
            }
            return;
        }

        // Velocity currently mirrors the client brand to a backend late in CONFIGURATION. If the
        // async configuration event observed null, retry once here before deciding. This preserves
        // direct standalone behavior while giving transparent Velocity mode a supported late gate.
        if (session.decision() == null && !session.playHandshakeRequired()) {
            evaluateStandaloneConfiguration(connection, session, true);
        }

        if (session.playHandshakeRequired() && session.decision() == null) {
            session.markReadyForPlay();
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
            sessions.remove(session);
        } else {
            session.markReadyForPlay();
        }
    }

    private void resolveProfileAsyncGate(AdmissionSession session) {
        if (session.resolvedProfile() != null || session.decision() != null) return;

        var policySnapshot = session.snapshot().requireAdmissionPolicy();
        try {
            AdmissionPermissionSnapshot permissions = AdmissionProfileProviderGate.resolve(
                    profileProvider, session.playerId(), policySnapshot,
                    session.snapshot().settings().handshakeTimeoutSeconds())
                .join();
            session.setResolvedProfile(AdmissionProfileResolver.resolve(
                policySnapshot, session.playerId(), permissions));
        } catch (RuntimeException ex) {
            if (policySnapshot.identityOverrides().containsKey(session.playerId())) {
                // Identity overrides are complete pre-login configuration authority. Provider failure can
                // safely remove provider-derived bypasses without changing which profile applies.
                session.setResolvedProfile(AdmissionProfileResolver.resolve(
                    policySnapshot, session.playerId(), AdmissionPermissionSnapshot.none()));
                plugin.getLogger().warning("Guardian Admission profile provider failed for identity-overridden player "
                    + session.playerId() + "; continuing with the configured identity profile and no provider "
                    + "bypasses: " + rootMessage(ex));
                return;
            }

            session.decide(GuardianDecision.deny(
                DecisionReason.PROFILE_RESOLUTION_FAILED,
                "pre-login profile provider failed or timed out; refusing default-profile fallback"));
            plugin.getLogger().warning("Guardian Admission profile provider failed for " + session.playerId()
                + "; admission fails closed instead of falling back to the default profile: " + rootMessage(ex));
        }
    }

    private ResolvedAdmissionProfile resolvedProfile(AdmissionSession session) {
        ResolvedAdmissionProfile resolved = session.resolvedProfile();
        if (resolved != null) return resolved;

        var policySnapshot = session.snapshot().requireAdmissionPolicy();
        if (policySnapshot.identityOverrides().containsKey(session.playerId())) {
            resolved = AdmissionProfileResolver.resolve(
                policySnapshot, session.playerId(), AdmissionPermissionSnapshot.none());
            session.setResolvedProfile(resolved);
            return resolved;
        }

        session.decide(GuardianDecision.deny(
            DecisionReason.PROFILE_RESOLUTION_FAILED,
            "pre-login profile resolution did not complete before final validation"));
        plugin.getLogger().warning("Guardian reached final standalone validation for " + session.playerId()
            + " without a resolved admission profile; admission fails closed.");
        return null;
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
        if (resolved == null) {
            return;
        }
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
        return DiagnosticText.oneLine(
            current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        AdmissionSession session = resolveJoinSession(player);
        if (session == null) {
            return;
        }
        if (!session.bindPlayConnection(player)) {
            return;
        }

        GuardianDecision decision = session.decision();
        if (!session.playHandshakeRequired()) {
            if (decision == null || decision.outcome() != DecisionOutcome.ALLOW) {
                return;
            }
            if (session.snapshot().settings().authorityMode() == PaperAuthorityMode.VELOCITY) {
                captureBackendInspection(player, session, decision);
            } else {
                captureStandaloneInspection(player, session, decision);
            }
            sessions.remove(session);
            return;
        }

        session.setQuarantined(true);
        debug(session, "Guardian PLAY quarantine active for " + player.getName()
            + "; listeningChannels=" + player.getListeningPluginChannels());

        plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> handleHandshakeTimeout(player, session),
            session.snapshot().settings().handshakeTimeoutTicks());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        inspectionService.remove(player);

        AdmissionSession session = sessions.forPlay(playerId, player);
        if (session != null && sessions.remove(session)) {
            session.response().completeExceptionally(new IllegalStateException("player quit"));
        }
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        UUID playerId = event.getPlayerUniqueId();
        // PlayerConnectionCloseEvent exposes only UUID/name/address, not the exact connection that
        // closed. Inspect every same-UUID session independently and dispose only sessions whose own
        // bound CONFIGURATION connection is observably disconnected. A newer connected session is
        // therefore preserved even when this callback belongs to an older connection.
        for (AdmissionSession session : sessions.forPlayer(playerId)) {
            Object identity = session.configurationConnectionIdentity();
            if (identity instanceof PlayerConnection boundConnection && !boundConnection.isConnected()
                && sessions.remove(session)) {
                session.response().completeExceptionally(new IllegalStateException("connection closed"));
            }
        }
    }

    // --- Bounded PLAY quarantine enforcement lives in PaperQuarantineGuard ---

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        AdmissionSession session = sessions.forPlay(player.getUniqueId(), player);
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
        AdmissionSession session = sessions.forConfiguration(playerId, configurationConnection);
        if (session == null) {
            return;
        }
        if (session.readyForPlay()) {
            return; // Freeze CONFIGURATION state once the exact session passes the final gate.
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
        // Bind verification to the immutable runtime snapshot captured for this exact Paper
        // configuration connection. A concurrent key reload must not change the verifier halfway
        // through this connection's admission gate.
        byte[] proxySecret = session.snapshot().settings().proxyAssertionSecret().copyBytes();
        final ProxyAdmissionAssertion assertion;
        try {
            assertion = ProxyAdmissionCodec.decodeAndVerify(message, proxySecret);
        } catch (ProtocolException | IllegalArgumentException ex) {
            String detail = DiagnosticText.oneLine(ex.getMessage());
            session.decide(GuardianDecision.deny(DecisionReason.PROXY_ASSERTION_INVALID,
                "invalid trusted proxy assertion: " + detail));
            plugin.getLogger().warning("Rejected Guardian proxy assertion for " + displayName(connection)
                + ": " + detail);
            return;
        } finally {
            Arrays.fill(proxySecret, (byte) 0);
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
                + displayName(connection) + ": " + DiagnosticText.oneLine(metadataDecision.detail()));
            return;
        }

        ProxyAssertionReplayGuard.Result replay = proxyAssertionReplayGuard.record(
            message, assertion.expiresAtEpochMillis(), now);
        if (replay != ProxyAssertionReplayGuard.Result.ACCEPTED) {
            String detail = replay == ProxyAssertionReplayGuard.Result.REPLAYED
                ? "replayed proxy admission assertion"
                : "proxy assertion replay guard capacity exhausted";
            session.decide(GuardianDecision.deny(DecisionReason.PROXY_ASSERTION_INVALID, detail));
            plugin.getLogger().warning("Rejected Guardian proxy assertion for " + displayName(connection)
                + ": " + detail);
            return;
        }

        if (!session.recordProxyAdmission(assertion)) {
            session.decide(GuardianDecision.deny(DecisionReason.PROXY_ASSERTION_INVALID,
                "duplicate proxy admission assertion for one Paper configuration session"));
            return;
        }

        BackendInspectionSnapshot.FloodgateSanity sanity = sanityCheckBackendFloodgate(connection, assertion, session);
        session.setBackendFloodgateSanity(sanity);
        session.decide(metadataDecision);
        debug(session, "Trusted Guardian proxy admission received for " + displayName(connection)
            + ": session=" + HexFormat.of().formatHex(assertion.proxySessionId())
            + ", origin=" + assertion.connectionOrigin()
            + ", expiresInMs=" + Math.max(0L, assertion.expiresAtEpochMillis() - now));
    }

    private BackendInspectionSnapshot.FloodgateSanity sanityCheckBackendFloodgate(
        PlayerConfigurationConnection connection, ProxyAdmissionAssertion assertion, AdmissionSession session
    ) {
        boolean proxyBedrock = assertion.connectionOrigin() == ConnectionOrigin.BEDROCK;
        BedrockSignal backendSignal = bedrockDetector.floodgateSignal(assertion.playerId());
        if (backendSignal == BedrockSignal.UNAVAILABLE) {
            if (proxyBedrock) {
                debug(session, "Guardian backend Floodgate sanity check skipped for "
                    + displayName(connection) + ": proxy origin=BEDROCK, backend Floodgate unavailable.");
            }
            return BackendInspectionSnapshot.FloodgateSanity.NOT_AVAILABLE;
        }
        if (backendSignal == BedrockSignal.ERROR) {
            plugin.getLogger().warning("Guardian backend Floodgate sanity check FAILED for "
                + displayName(connection)
                + ". Trusted proxy admission remains authoritative; backend Floodgate evidence is unavailable.");
            return BackendInspectionSnapshot.FloodgateSanity.ERROR;
        }

        boolean backendBedrock = backendSignal == BedrockSignal.BEDROCK;
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

        GuardianDecision presenceDecision = validateCerberusPresence(session, presence);
        if (presenceDecision != null) session.recordConfigurationPresenceFailure(presenceDecision);
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

        GuardianDecision presenceDecision = validateCerberusPresence(session, presence);
        if (presenceDecision != null) {
            finishPlayDecision(player, session, presenceDecision);
            return;
        }

        sendPlayChallenge(player, session, 0);
    }

    private static long requiredCerberusCapabilities(AdmissionSession session) {
        long required = session.snapshot().requireAdmissionPolicy().cerberusReleaseTrust().requiredCapabilities();
        if (session.snapshot().settings().serverChallengeSigner() != null) {
            required |= GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE;
        }
        return required;
    }

    private static GuardianDecision validateCerberusPresence(AdmissionSession session, Presence presence) {
        if (!presence.supports(GuardianProtocol.VERSION)
            || GuardianProtocol.hasUnknownCapabilities(presence.capabilities())
            || !GuardianProtocol.supportsProtocolV1Capabilities(presence.capabilities())) {
            return GuardianDecision.deny(
                DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
                "Cerberus announced protocol range " + presence.minProtocolVersion() + ".." + presence.maxProtocolVersion()
                    + " with capabilities 0x" + Long.toHexString(presence.capabilities())
                    + "; Guardian requires protocol " + GuardianProtocol.VERSION + " base capabilities 0x"
                    + Long.toHexString(GuardianProtocol.REQUIRED_CAPABILITIES));
        }
        if ((presence.capabilities() & GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE) != 0L
            && session.snapshot().settings().serverChallengeSigner() == null) {
            return GuardianDecision.deny(
                DecisionReason.CERBERUS_SERVER_AUTH_REQUIRED,
                "Cerberus requires an authenticated Guardian challenge, but standalone Paper server authentication is disabled");
        }
        long required = requiredCerberusCapabilities(session);
        if ((presence.capabilities() & required) != required) {
            if ((required & GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE) != 0L
                && (presence.capabilities() & GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE) == 0L) {
                return GuardianDecision.deny(
                    DecisionReason.CERBERUS_RELEASE_REQUIRED,
                    "Admission policy requires an official signed Cerberus release identity");
            }
            return GuardianDecision.deny(
                DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
                "Cerberus does not provide all capabilities required by this Admission policy");
        }
        return null;
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
        if (!isCurrentPlaySession(player, session)
            || session.challengeSent() || session.decision() != null || !player.isOnline()) {
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
            long requiredCapabilities = requiredCerberusCapabilities(session);
            var signer = session.snapshot().settings().serverChallengeSigner();
            Challenge challenge = signer == null
                ? new Challenge(GuardianProtocol.VERSION, requiredCapabilities, nonce)
                : signer.authenticatedChallenge(
                    GuardianProtocol.VERSION, requiredCapabilities, nonce, player.getUniqueId());
            player.sendPluginMessage(
                plugin,
                GuardianProtocol.CHALLENGE_CHANNEL,
                ProtocolCodec.encodeChallenge(challenge)
            );
            debug(session, "Guardian PLAY challenge sent to " + player.getName()
                + "; listeningChannels=" + player.getListeningPluginChannels());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not send Guardian PLAY challenge to " + player.getName() + ": " + rootMessage(ex));
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
        GuardianDecision integrityDecision = ProtocolV1ResponseValidator.validate(
            session.nonce(), response, session.snapshot().requireAdmissionPolicy().cerberusReleaseTrust());
        if (integrityDecision.outcome() == DecisionOutcome.DENY) {
            finishPlayDecision(player, session, integrityDecision);
            return;
        }
        session.setManifest(response.manifest());
        GuardianDecision policyDecision = policyEvaluator
            .evaluateManifest(resolvedProfile(session), response.manifest())
            .decision();
        finishPlayDecision(player, session, policyDecision);
    }

    private void handleHandshakeTimeout(Player player, AdmissionSession session) {
        if (!isCurrentPlaySession(player, session)
            || !session.playHandshakeRequired() || session.decision() != null) {
            return;
        }

        if (!player.isOnline()) {
            sessions.remove(session);
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
        if (!isCurrentPlaySession(player, session)) {
            return;
        }
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
            captureStandaloneInspection(player, session, finalDecision);
            sessions.remove(session);
            debug(session, "Guardian PLAY quarantine released for " + player.getName());
        } else {
            player.kick(messageRenderer.renderDecision(
                session.snapshot(), finalDecision, session.classification()));
            sessions.remove(session);
        }
    }


    private void captureStandaloneInspection(
        Player player, AdmissionSession session, GuardianDecision decision
    ) {
        String playerName = player.getName();
        if (session.classification() == null || session.resolvedProfile() == null || session.bedrockEvidence() == null) {
            return;
        }
        ActiveInspectionSnapshot snapshot = new ActiveInspectionSnapshot(
            session.playerId(), playerName, serverName(), session.classification(), session.observedBrand(),
            session.resolvedProfile().profile().id(), session.resolvedProfile().source(), session.cerberusPresence(),
            decision, session.manifest(), session.bedrockEvidence(), session.snapshot().generation());
        if (!inspectionService.putAuthoritative(player, snapshot)) {
            plugin.getLogger().warning("Guardian active inspection store is full; snapshot omitted for " + playerName);
        }
    }

    private void captureBackendInspection(
        Player player, AdmissionSession session, GuardianDecision decision
    ) {
        String playerName = player.getName();
        ProxyAdmissionAssertion assertion = session.proxyAdmission();
        BackendInspectionSnapshot.FloodgateSanity sanity = session.backendFloodgateSanity();
        if (assertion == null || sanity == null) {
            return;
        }
        if (!inspectionService.putBackend(player, new BackendInspectionSnapshot(
            assertion.playerId(), playerName, serverName(), assertion.connectionOrigin(),
            HexFormat.of().formatHex(assertion.proxySessionId()), decision, sanity))) {
            plugin.getLogger().warning("Guardian backend inspection store is full; snapshot omitted for " + playerName);
        }
    }

    private void logStandaloneSummary(String playerName, AdmissionSession session, GuardianDecision decision) {
        if (!session.tryMarkSummaryLogged()) return;
        String profile = session.resolvedProfile() == null ? "<unknown>" : session.resolvedProfile().profile().id();
        int mods = session.manifest() == null ? 0 : session.manifest().entries().size();
        plugin.getLogger().info(DiagnosticText.oneLine("Guardian " + playerName + " " + decision.outcome() + ": "
            + (session.classification() == null ? "UNKNOWN" : session.classification())
            + ", profile=" + profile + ", " + decision.reason()
            + (mods == 0 ? "" : ", mods=" + mods)));
    }

    private void debug(AdmissionSession session, String message) {
        if (session.snapshot().settings().loggingLevel().debugEnabled())
            plugin.getLogger().info(DiagnosticText.oneLine(message));
    }

    private String serverName() {
        String configured = runtimeManager.current().settings().serverName();
        return configured.isBlank() ? plugin.getServer().getName() : configured;
    }

    private AdmissionSession resolveJoinSession(Player player) {
        UUID playerId = player.getUniqueId();
        List<AdmissionSession> candidates = new ArrayList<>();
        boolean anotherActivePlayConnection = false;
        for (AdmissionSession session : sessions.forPlayer(playerId)) {
            if (session.playConnectionBound()) {
                Object playIdentity = session.playConnectionIdentity();
                if (playIdentity instanceof Player boundPlayer
                    && boundPlayer != player && boundPlayer.isOnline()) {
                    anotherActivePlayConnection = true;
                }
                continue;
            }
            if (!session.readyForPlay()) {
                continue;
            }
            Object identity = session.configurationConnectionIdentity();
            if (identity instanceof PlayerConnection boundConnection && !boundConnection.isConnected()) {
                if (sessions.remove(session)) {
                    session.response().completeExceptionally(
                        new IllegalStateException("configuration connection closed before PLAY handoff"));
                }
                continue;
            }
            candidates.add(session);
        }

        if (candidates.size() == 1 && !anotherActivePlayConnection) {
            return candidates.getFirst();
        }
        if (candidates.isEmpty()) {
            return null;
        }

        // Paper does not expose a public API token that directly links the CONFIGURATION object to
        // the later Player object. Multiple simultaneously-ready same-UUID sessions are therefore
        // intentionally treated as ambiguous rather than guessing which admission belongs here.
        GuardianDecision ambiguity = GuardianDecision.deny(
            DecisionReason.CONFIGURATION_ERROR,
            anotherActivePlayConnection
                ? "another active PLAY connection already owns this Guardian admission UUID"
                : "multiple concurrent Guardian admission sessions were ready for the same UUID");
        AdmissionSession renderingSession = candidates.getFirst();
        for (AdmissionSession candidate : candidates) {
            candidate.decide(ambiguity);
            if (sessions.remove(candidate)) {
                candidate.response().completeExceptionally(
                    new IllegalStateException("ambiguous same-UUID PLAY handoff"));
            }
        }
        plugin.getLogger().warning("Guardian rejected ambiguous same-UUID PLAY handoff for "
            + player.getName() + " (" + playerId + "): " + candidates.size()
            + " unbound session(s) were ready; anotherActivePlayConnection=" + anotherActivePlayConnection + ".");
        player.kick(messageRenderer.renderDecision(
            renderingSession.snapshot(), ambiguity, renderingSession.classification()));
        return null;
    }

    private boolean isCurrentPlaySession(Player player, AdmissionSession session) {
        return sessions.contains(session) && session.matchesPlayConnection(player);
    }

    private static UUID requirePlayerId(PlayerConfigurationConnection connection) {
        return connection.getProfile().getId();
    }

    private static String displayName(PlayerConfigurationConnection connection) {
        String name = connection.getProfile().getName();
        UUID id = connection.getProfile().getId();
        return DiagnosticText.oneLine(name != null ? name : String.valueOf(id));
    }
}
