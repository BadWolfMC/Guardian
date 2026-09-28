package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.BedrockEvidence;
import com.badwolfmc.guardian.core.BedrockResolution;
import com.badwolfmc.guardian.core.BedrockSignal;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.ClientOriginClassifier;
import com.badwolfmc.guardian.core.DecisionOutcome;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.core.ProtocolV1ResponseValidator;
import com.badwolfmc.guardian.core.policy.AdmissionPermissionSnapshot;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyEvaluator;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyException;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyRuntimeManager;
import com.badwolfmc.guardian.core.policy.AdmissionPolicySnapshot;
import com.badwolfmc.guardian.core.policy.AdmissionProfileProvider;
import com.badwolfmc.guardian.core.policy.AdmissionProfileResolver;
import com.badwolfmc.guardian.core.policy.ClientPolicyResult;
import com.badwolfmc.guardian.protocol.Challenge;
import com.badwolfmc.guardian.protocol.ConnectionOrigin;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Presence;
import com.badwolfmc.guardian.protocol.ProtocolCodec;
import com.badwolfmc.guardian.protocol.ProtocolException;
import com.badwolfmc.guardian.protocol.ProxyAdmissionAssertion;
import com.badwolfmc.guardian.protocol.ProxyAdmissionCodec;
import com.badwolfmc.guardian.protocol.Response;
import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Phase 0B.3 Velocity feasibility spike.
 *
 * <p>This checkpoint adds supported Geyser/Floodgate Bedrock classification to the already-proven
 * proxy-side CONFIGURATION admission path and carries trusted connection origin to Paper for a
 * backend Floodgate sanity check.</p>
 */
@Plugin(
    id = "guardian",
    name = "Guardian",
    version = "0.1.0-phase4",
    description = "Guardian Admission for Velocity",
    authors = {"BadWolfMC"},
    dependencies = {
        @Dependency(id = "geyser", optional = true),
        @Dependency(id = "floodgate", optional = true),
        @Dependency(id = "luckperms", optional = true)
    }
)
public final class GuardianVelocityPlugin {
    private static final int HANDSHAKE_TIMEOUT_SECONDS = 10;

    private static final ChannelIdentifier PRESENCE =
        MinecraftChannelIdentifier.from(GuardianProtocol.PRESENCE_CHANNEL);
    private static final ChannelIdentifier CHALLENGE =
        MinecraftChannelIdentifier.from(GuardianProtocol.CHALLENGE_CHANNEL);
    private static final ChannelIdentifier RESPONSE =
        MinecraftChannelIdentifier.from(GuardianProtocol.RESPONSE_CHANNEL);
    private static final ChannelIdentifier PROXY_ADMISSION =
        MinecraftChannelIdentifier.from(GuardianProtocol.PROXY_ADMISSION_CHANNEL);

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<UUID, VelocityAdmissionSession> sessions = new ConcurrentHashMap<>();
    private final BedrockDetector bedrockDetector;
    private final VelocityMessages messages;
    private final AdmissionPolicyEvaluator policyEvaluator = new AdmissionPolicyEvaluator();
    private AdmissionPolicyRuntimeManager policyRuntime;
    private AdmissionProfileProvider profileProvider = AdmissionProfileProvider.none();
    private byte[] proxySecret;

    @Inject
    public GuardianVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.bedrockDetector = new BedrockDetector(server, logger);
        this.messages = VelocityMessages.load();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        ensureSharedPolicyFile();
        policyRuntime = new AdmissionPolicyRuntimeManager(
            dataDirectory.resolve("admission/policy.yml"), dataDirectory.resolve("artifacts.yml"));
        try {
            policyRuntime.loadInitial();
        } catch (AdmissionPolicyException ex) {
            throw new IllegalStateException("Guardian-Velocity admission policy activation failed at "
                + ex.path() + ": " + ex.getMessage(), ex);
        }
        if (server.getPluginManager().isLoaded("luckperms")) {
            try {
                profileProvider = new VelocityLuckPermsProfileProvider();
                logger.info("Guardian Admission profile provider: LuckPerms (proxy-static query options).");
            } catch (RuntimeException | LinkageError ex) {
                profileProvider = AdmissionProfileProvider.none();
                logger.warn("Guardian could not initialize Velocity LuckPerms profile integration; "
                    + "default/identity profiles remain available: {}", ex.getMessage());
            }
        } else {
            logger.info("Guardian Admission profile provider: LuckPerms not present; default/identity profiles only.");
        }

        // Register all security-sensitive Guardian channels so PluginMessageEvent is fired for
        // them. The event handler below marks them handled before examining the source, which is
        // the Velocity-documented pattern for preventing client/backend spoofing or leakage.
        server.getChannelRegistrar().register(PRESENCE, CHALLENGE, RESPONSE, PROXY_ADMISSION);
        try {
            proxySecret = ProxyAdmissionCodec.decodeBase64Secret(
                System.getenv("GUARDIAN_PHASE0B_PROXY_SECRET"));
            logger.info("Guardian Phase 0B.3 trusted proxy assertions enabled; timeout={}s.",
                HANDSHAKE_TIMEOUT_SECONDS);
        } catch (IllegalArgumentException ex) {
            proxySecret = null;
            logger.warn("Guardian Phase 0B.3 proxy assertions are unavailable because "
                + "GUARDIAN_PHASE0B_PROXY_SECRET is not a valid Base64-encoded 32-byte shared secret: {}. "
                + "Proxy-side admission remains available, but Guardian-Paper in VELOCITY authority mode "
                + "will fail closed without an assertion.",
                ex.getMessage());
        }
    }

    @Subscribe
    public EventTask onPlayerConfiguration(PlayerConfigurationEvent event) {
        Player player = event.player();
        VelocityAdmissionSession session = sessions.computeIfAbsent(
            player.getUniqueId(), ignored -> new VelocityAdmissionSession(newProxySessionId()));

        if (session.admitted()) {
            logger.info("Guardian Phase 0B.3 reconfiguration for {}: reusing Phase 3 admission for this proxy connection.",
                player.getUsername());
            sendProxyAdmission(player, event.server(), session);
            return null;
        }

        GuardianDecision existing = session.decision();
        if (existing != null) {
            applyDecision(player, existing);
            return null;
        }

        BedrockEvidence bedrock = bedrockDetector.detect(player.getUniqueId());
        if (bedrock.disagrees()) {
            logger.warn("Guardian Geyser/Floodgate disagreement for {}: geyser={}, floodgate={}; "
                    + "positive supported API evidence classifies this connection as BEDROCK.",
                player.getUsername(), bedrock.geyser(), bedrock.floodgate());
        }
        if (bedrock.resolution() == BedrockResolution.INDETERMINATE) {
            GuardianDecision failure = GuardianDecision.deny(
                DecisionReason.CONFIGURATION_ERROR,
                "Bedrock origin integration failed; refusing to reinterpret an indeterminate connection as Java");
            session.decide(failure);
            logger.warn("Guardian could not determine connection origin for {} because an available Bedrock "
                    + "integration failed: geyser={}, floodgate={}",
                player.getUsername(), bedrock.geyser(), bedrock.floodgate());
            applyDecision(player, failure);
            return null;
        }

        ClientClassification classification = ClientOriginClassifier.classify(
            bedrock.geyser() == BedrockSignal.BEDROCK,
            bedrock.floodgate() == BedrockSignal.BEDROCK,
            player.getClientBrand());
        session.setClassification(classification);
        logger.info("Guardian configuration for {}: brand={}, geyser={}, floodgate={}, classification={}, backend={}",
            player.getUsername(), String.valueOf(player.getClientBrand()), bedrock.geyser(), bedrock.floodgate(),
            classification, event.server() == null ? "<none>" : event.server().getServerInfo().getName());

        AdmissionPolicySnapshot policySnapshot = policyRuntime.current();
        CompletableFuture<AdmissionPermissionSnapshot> permissions = profileProvider
            .resolve(player.getUniqueId(), policySnapshot)
            .toCompletableFuture()
            .orTimeout(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .exceptionally(throwable -> {
                logger.warn("Guardian Admission profile provider failed for {}; falling back to default/identity "
                    + "profile without bypasses: {}", player.getUsername(), rootMessage(throwable));
                return AdmissionPermissionSnapshot.none();
            });

        CompletableFuture<GuardianDecision> admission = permissions.thenCompose(permissionSnapshot -> {
            var resolved = AdmissionProfileResolver.resolve(
                policySnapshot, player.getUniqueId(), permissionSnapshot);
            session.setResolvedProfile(resolved);
            ClientPolicyResult clientResult = policyEvaluator.evaluateClient(
                resolved, classification, player.getClientBrand());
            logger.info("Guardian Phase 3 shared policy for {}: profile={}, source={}, action={}",
                player.getUsername(), resolved.profile().id(), resolved.source(), clientResult.action());

            if (clientResult.terminalDecision() != null) {
                session.decide(clientResult.terminalDecision());
                return session.decisionFuture();
            }

            // REQUIRE_CERBERUS. Mark the session before inspecting any early Cerberus traffic so
            // presence/response handlers cannot race profile resolution into denying an ALLOW client.
            session.requireCerberus();
            GuardianDecision presenceFailure = session.configurationAttestationFailure();
            if (presenceFailure != null) {
                session.decide(presenceFailure);
            } else if (session.decision() == null) {
                armTimeout(player, session);
                startChallenge(player, session);
            }
            return session.decisionFuture();
        });

        CompletableFuture<Void> hold = admission.thenAccept(decision -> {
            if (decision.outcome() == DecisionOutcome.ALLOW) {
                sendProxyAdmission(player, event.server(), session);
            }
            applyDecision(player, decision);
        });

        // PlayerConfigurationEvent is explicitly awaited by Velocity. Returning a continuation task
        // holds progression in CONFIGURATION while the same Phase 3 policy used by Paper resolves.
        return EventTask.resumeWhenComplete(hold.exceptionally(throwable -> {
            logger.error("Guardian Phase 3 admission future failed for {}", player.getUsername(), throwable);
            GuardianDecision failure = GuardianDecision.deny(
                DecisionReason.CONFIGURATION_ERROR, "shared admission policy evaluation failed");
            session.decide(failure);
            player.disconnect(messages.render(DecisionReason.CONFIGURATION_ERROR, session.classification()));
            return null;
        }));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        ChannelIdentifier identifier = event.getIdentifier();
        if (!isGuardianChannel(identifier)) {
            return;
        }

        // Security invariant: never allow Guardian's client/proxy channels to pass through the
        // proxy in either direction, even if the packet is malformed or from the wrong source.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof Player player)) {
            logger.debug("Consumed backend-origin Guardian channel {} during Phase 0B.3.", identifier.getId());
            return;
        }

        VelocityAdmissionSession session = sessions.computeIfAbsent(
            player.getUniqueId(), ignored -> new VelocityAdmissionSession(newProxySessionId()));

        if (identifier.equals(PRESENCE)) {
            handlePresence(player, session, event.getData());
        } else if (identifier.equals(RESPONSE)) {
            handleResponse(player, session, event.getData());
        } else if (identifier.equals(PROXY_ADMISSION)) {
            // This channel is infrastructure-only. A normal client may know its name and format,
            // but Velocity consumes the packet and never forwards it to Guardian-Paper.
            logger.warn("Consumed client-origin proxy-admission assertion attempt from {}.",
                player.getUsername());
        } else {
            // guardian:challenge is proxy -> client only. A client-origin challenge is consumed.
            logger.warn("Consumed unexpected client-origin Guardian challenge from {}.", player.getUsername());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void handlePresence(Player player, VelocityAdmissionSession session, byte[] data) {
        if (data.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            recordConfigurationAttestationFailure(session, GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "CONFIGURATION presence exceeds Guardian protocol limit"));
            return;
        }

        final Presence presence;
        try {
            presence = ProtocolCodec.decodePresence(data);
        } catch (ProtocolException ex) {
            recordConfigurationAttestationFailure(session, GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID,
                "invalid Cerberus CONFIGURATION presence: " + ex.getMessage()));
            return;
        }

        if (!session.recordPresence(presence)) {
            recordConfigurationAttestationFailure(session, GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "conflicting duplicate Cerberus presence"));
            return;
        }

        logger.info("Guardian Phase 0B.3 Cerberus presence from {}: protocol={}",
            player.getUsername(), presence.minProtocolVersion() + ".." + presence.maxProtocolVersion());

        if (!presence.supports(GuardianProtocol.VERSION) || (presence.capabilities() & GuardianProtocol.REQUIRED_CAPABILITIES) != GuardianProtocol.REQUIRED_CAPABILITIES) {
            recordConfigurationAttestationFailure(session, GuardianDecision.deny(
                DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
                "Cerberus announced protocol range " + presence.minProtocolVersion() + ".." + presence.maxProtocolVersion()
                    + " with capabilities 0x" + Long.toHexString(presence.capabilities())
                    + "; Guardian requires protocol " + GuardianProtocol.VERSION + " capabilities 0x" + Long.toHexString(GuardianProtocol.REQUIRED_CAPABILITIES)));
        }
    }

    /**
     * Cerberus traffic can arrive before profile resolution. Record failures first and only make
     * them connection-fatal once shared client policy has actually selected REQUIRE_CERBERUS.
     */
    private void recordConfigurationAttestationFailure(
        VelocityAdmissionSession session, GuardianDecision failure
    ) {
        session.recordConfigurationAttestationFailure(failure);
        if (session.cerberusRequired() && session.decision() == null) {
            session.decide(failure);
        }
    }

    private void startChallenge(Player player, VelocityAdmissionSession session) {
        if (session.decision() != null
            || session.classification() != ClientClassification.JAVA_FABRIC
            || !session.tryMarkChallengeSent()) {
            return;
        }

        byte[] nonce = new byte[GuardianProtocol.NONCE_BYTES];
        random.nextBytes(nonce);
        session.setNonce(nonce);

        boolean sent;
        try {
            sent = player.sendPluginMessage(
                CHALLENGE,
                ProtocolCodec.encodeChallenge(new Challenge(GuardianProtocol.VERSION, GuardianProtocol.REQUIRED_CAPABILITIES, nonce))
            );
        } catch (RuntimeException ex) {
            logger.warn("Could not send Guardian Phase 0B.3 CONFIGURATION challenge to {}.",
                player.getUsername(), ex);
            session.decide(GuardianDecision.deny(
                DecisionReason.CONFIGURATION_ERROR, "Velocity CONFIGURATION challenge send failed"));
            return;
        }

        if (!sent) {
            session.decide(GuardianDecision.deny(
                DecisionReason.CONFIGURATION_ERROR,
                "Velocity declined Guardian CONFIGURATION challenge send"));
            return;
        }

        logger.info("Guardian Phase 0B.3 CONFIGURATION challenge sent to {}.", player.getUsername());
    }

    private void handleResponse(Player player, VelocityAdmissionSession session, byte[] data) {
        if (!session.cerberusRequired()) {
            if (session.decision() == null) {
                recordConfigurationAttestationFailure(session, GuardianDecision.deny(
                    DecisionReason.MANIFEST_INVALID,
                    "Cerberus response arrived before shared client policy required attestation"));
            }
            return;
        }
        if (!session.tryMarkResponseReceived()) {
            session.decide(GuardianDecision.deny(DecisionReason.MANIFEST_INVALID, "duplicate Cerberus response"));
            return;
        }
        if (session.decision() != null) return;
        if (!session.challengeSent() || session.nonce() == null) {
            session.decide(GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "Cerberus response arrived before Guardian challenge"));
            return;
        }
        if (data.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            session.decide(GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "CONFIGURATION response exceeds Guardian protocol limit"));
            return;
        }

        final Response response;
        try {
            response = ProtocolCodec.decodeResponse(data);
        } catch (ProtocolException ex) {
            session.decide(GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID,
                "invalid Cerberus CONFIGURATION response: " + ex.getMessage()));
            return;
        }

        GuardianDecision integrityDecision = ProtocolV1ResponseValidator.validate(session.nonce(), response);
        GuardianDecision decision = integrityDecision;
        if (integrityDecision.outcome() == DecisionOutcome.ALLOW) {
            if (session.resolvedProfile() == null) {
                decision = GuardianDecision.deny(DecisionReason.CONFIGURATION_ERROR,
                    "Cerberus response arrived before shared admission profile resolution");
            } else {
                decision = policyEvaluator.evaluateManifest(session.resolvedProfile(), response.manifest()).decision();
            }
        }
        session.decide(decision);
        logger.info("Guardian Phase 0B.3 CONFIGURATION response from {}: {} / {} ({})",
            player.getUsername(), decision.outcome(), decision.reason(), decision.detail());
    }

    private void armTimeout(Player player, VelocityAdmissionSession session) {
        CompletableFuture.delayedExecutor(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (session.decision() != null) {
                return;
            }

            GuardianDecision timeoutDecision;
            if (!session.cerberusPresent()) {
                timeoutDecision = GuardianDecision.deny(
                    DecisionReason.CERBERUS_REQUIRED,
                    "Fabric client did not identify Cerberus after the Velocity CONFIGURATION challenge");
            } else if (session.challengeSent()) {
                timeoutDecision = GuardianDecision.deny(
                    DecisionReason.CERBERUS_TIMEOUT,
                    "Cerberus presence was received and challenge sent, but no valid response arrived in time");
            } else {
                timeoutDecision = GuardianDecision.deny(
                    DecisionReason.CONFIGURATION_ERROR,
                    "Cerberus presence was received but Guardian could not begin the challenge");
            }

            if (session.decide(timeoutDecision)) {
                logger.info("Guardian Phase 0B.3 timeout for {}: {}", player.getUsername(), timeoutDecision.reason());
            }
        });
    }

    private void applyDecision(Player player, GuardianDecision decision) {
        logger.info("Guardian Phase 0B.3 decision for {}: {} / {} ({})",
            player.getUsername(), decision.outcome(), decision.reason(), decision.detail());
        if (decision.outcome() == DecisionOutcome.DENY) {
            VelocityAdmissionSession session = sessions.get(player.getUniqueId());
            ClientClassification classification = session == null ? null : session.classification();
            player.disconnect(messages.render(decision, classification));
        }
    }

    private boolean sendProxyAdmission(
        Player player, ServerConnection backend, VelocityAdmissionSession session
    ) {
        if (backend == null) {
            logger.warn("Guardian Phase 0B.3 could not assert admission for {} because no backend "
                + "configuration connection is available. Proxy-side admission remains authoritative.",
                player.getUsername());
            return false;
        }
        if (proxySecret == null) {
            logger.warn("Guardian Phase 0B.3 did not assert admission for {} -> {} because the shared "
                + "proxy secret is unavailable. A Guardian-Paper backend in VELOCITY authority mode "
                + "will fail closed.",
                player.getUsername(), backend.getServerInfo().getName());
            return false;
        }

        long issuedAt = System.currentTimeMillis();
        ConnectionOrigin connectionOrigin = session.classification() == ClientClassification.BEDROCK
            ? ConnectionOrigin.BEDROCK
            : ConnectionOrigin.JAVA;
        ProxyAdmissionAssertion assertion = new ProxyAdmissionAssertion(
            GuardianProtocol.PROXY_ASSERTION_VERSION,
            player.getUniqueId(),
            session.proxySessionId(),
            connectionOrigin,
            issuedAt,
            issuedAt + GuardianProtocol.PROXY_ASSERTION_TTL_MILLIS
        );

        final boolean sent;
        try {
            sent = backend.sendPluginMessage(
                PROXY_ADMISSION,
                ProxyAdmissionCodec.encode(assertion, proxySecret)
            );
        } catch (RuntimeException ex) {
            logger.warn("Guardian Phase 0B.3 proxy assertion send failed for {} -> {}.",
                player.getUsername(), backend.getServerInfo().getName(), ex);
            return false;
        }

        if (!sent) {
            logger.error("Guardian Phase 0B.3 backend {} declined proxy assertion for {}.",
                backend.getServerInfo().getName(), player.getUsername());
            return false;
        }

        logger.info("Guardian Phase 0B.3 trusted admission asserted for {} -> {}: session={}, origin={}",
            player.getUsername(), backend.getServerInfo().getName(),
            HexFormat.of().formatHex(session.proxySessionId()), connectionOrigin);
        return true;
    }

    /** Minimal Phase 3 reload seam; Phase 5 owns final Velocity admin UX. */
    void reloadAdmissionPolicy() throws AdmissionPolicyException {
        policyRuntime.reload();
    }

    /** Minimal files-only validation seam; does not activate the candidate. */
    void validateAdmissionPolicyFiles() throws AdmissionPolicyException {
        policyRuntime.validateFiles();
    }

    private void ensureSharedPolicyFile() {
        Path destination = dataDirectory.resolve("admission/policy.yml");
        if (Files.exists(destination)) return;
        try {
            Files.createDirectories(destination.getParent());
            try (InputStream in = GuardianVelocityPlugin.class.getClassLoader()
                .getResourceAsStream("admission/policy.yml")) {
                if (in == null) throw new IOException("packaged admission/policy.yml is missing");
                Files.copy(in, destination);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("could not install Guardian shared admission policy at "
                + destination, ex);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private byte[] newProxySessionId() {
        byte[] value = new byte[GuardianProtocol.PROXY_SESSION_ID_BYTES];
        random.nextBytes(value);
        return value;
    }

    private static boolean isGuardianChannel(ChannelIdentifier identifier) {
        return identifier.equals(PRESENCE)
            || identifier.equals(CHALLENGE)
            || identifier.equals(RESPONSE)
            || identifier.equals(PROXY_ADMISSION);
    }

}
