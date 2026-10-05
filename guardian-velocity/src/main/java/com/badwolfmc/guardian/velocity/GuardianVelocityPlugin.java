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
import com.badwolfmc.guardian.core.artifact.ArtifactCatalogException;
import com.badwolfmc.guardian.core.artifact.ArtifactImportService;
import com.badwolfmc.guardian.core.operations.ConfigurationSchemaMigrator;
import com.badwolfmc.guardian.core.operations.ActiveInspectionSnapshot;
import com.badwolfmc.guardian.core.operations.DiagnosticText;
import com.badwolfmc.guardian.core.operations.SafeDirectory;
import com.badwolfmc.guardian.core.policy.AdmissionPermissionSnapshot;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyEvaluator;
import com.badwolfmc.guardian.core.policy.AdmissionPolicySnapshot;
import com.badwolfmc.guardian.core.policy.AdmissionProfileProvider;
import com.badwolfmc.guardian.core.policy.AdmissionProfileProviderGate;
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
import com.badwolfmc.guardian.velocity.config.VelocityConfigurationException;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Production Velocity host for Guardian's network-authoritative Admission domain. */
public final class GuardianVelocityPlugin {
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
    private final ConnectionIdentityRegistry<VelocityAdmissionSession> sessions = new ConnectionIdentityRegistry<>();
    private final ConnectionIdentityRegistry<VelocityAdmissionGrant> admitted = new ConnectionIdentityRegistry<>();
    private final VelocityInspectionService inspections = new VelocityInspectionService();
    private final BedrockDetector bedrockDetector;
    private final AdmissionPolicyEvaluator policyEvaluator = new AdmissionPolicyEvaluator();
    private AdmissionProfileProvider profileProvider = AdmissionProfileProvider.none();
    private VelocityRuntimeManager runtimeManager;
    private ArtifactImportService artifactImportService;

    @Inject
    public GuardianVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.bedrockDetector = new BedrockDetector(server, logger);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        ensureAdministratorFile("config.yml");
        ensureAdministratorFile("policy.yml");
        ensureAdministratorFile("locales/" + VelocityMessages.FALLBACK_LOCALE + ".properties");
        migratePublicReleaseSchemas();

        try {
            if (new VelocityProxyAssertionKeyProvisioner().ensureGenerated(dataDirectory)) {
                logger.warn("Guardian-Velocity generated proxy-assertion.key. Copy this file unchanged to "
                    + "plugins/Guardian/proxy-assertion.key on every Paper backend configured with "
                    + "admission.authority: velocity. Keep this key private.");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Guardian-Velocity could not provision proxy-assertion.key", ex);
        }

        runtimeManager = new VelocityRuntimeManager(dataDirectory);
        final VelocityRuntimeSnapshot runtime;
        try {
            runtime = runtimeManager.loadInitial();
        } catch (VelocityConfigurationException ex) {
            throw new IllegalStateException("Guardian-Velocity runtime activation failed at "
                + ex.path() + ": " + ex.getMessage(), ex);
        }

        artifactImportService = new ArtifactImportService(dataDirectory);
        try {
            artifactImportService.ensureInputDirectory();
        } catch (ArtifactCatalogException ex) {
            throw new IllegalStateException("Guardian-Velocity artifact-import initialization failed: "
                + ex.getMessage(), ex);
        }

        if (server.getPluginManager().isLoaded("luckperms")) {
            profileProvider = new VelocityLuckPermsProfileProvider(server);
            logger.info("Guardian Admission profile provider: LuckPerms (proxy-static query options). "
                + "Runtime provider failures fail closed.");
        } else {
            logger.info("Guardian Admission profile provider: LuckPerms not present; default/identity profiles only.");
        }

        // Security-sensitive Guardian client/proxy channels are consumed at the proxy and never forwarded.
        server.getChannelRegistrar().register(PRESENCE, CHALLENGE, RESPONSE, PROXY_ADMISSION);

        GuardianVelocityCommand command = new GuardianVelocityCommand(
            this, server, logger, runtimeManager, inspections, artifactImportService);
        CommandMeta meta = server.getCommandManager().metaBuilder("guardianv").plugin(this).build();
        server.getCommandManager().register(meta, command);

        logger.info("Guardian-Velocity activated: authority=VELOCITY, profiles={}, logging={}, timeout={}s, "
                + "proxyAssertionSource={}, proxyAssertionFingerprint={}.",
            runtime.admissionPolicy().profiles().size(), runtime.settings().loggingLevel(),
            runtime.settings().handshakeTimeoutSeconds(),
            runtime.settings().proxyAssertionSecret().sourceDescription(),
            runtime.settings().proxyAssertionSecret().fingerprint());
    }

    private void migratePublicReleaseSchemas() {
        migrateAdministratorSchema(
            dataDirectory.resolve("config.yml"),
            com.badwolfmc.guardian.velocity.config.VelocityConfigLoader.MAX_CONFIG_BYTES,
            ConfigurationSchemaMigrator.Surface.VELOCITY_CONFIG);
        migrateAdministratorSchema(
            dataDirectory.resolve("policy.yml"),
            com.badwolfmc.guardian.core.policy.AdmissionPolicyLoader.MAX_POLICY_BYTES,
            ConfigurationSchemaMigrator.Surface.ADMISSION_POLICY);
    }

    private void migrateAdministratorSchema(Path path, int maxBytes, ConfigurationSchemaMigrator.Surface surface) {
        try {
            var prepared = ConfigurationSchemaMigrator.prepare(path, maxBytes, surface);
            if (prepared.isEmpty()) return;
            var published = ConfigurationSchemaMigrator.publish(prepared.get());
            logger.warn("Guardian-Velocity upgraded {} from schema {} to {}. Original preserved at {}. Review the migrated file before future edits.",
                path.getFileName(), published.fromSchema(), published.toSchema(), published.backupPath());
        } catch (IOException ex) {
            throw new IllegalStateException("Guardian-Velocity could not safely migrate administrator file '"
                + path + "': " + ex.getMessage(), ex);
        }
    }

    @Subscribe
    public EventTask onPlayerConfiguration(PlayerConfigurationEvent event) {
        Player player = event.player();
        VelocityAdmissionGrant existingGrant = admitted.get(player);
        if (existingGrant != null) {
            debugCurrent("Guardian reconfiguration for {}: reusing authoritative Admission for this proxy connection.",
                player.getUsername());
            updateInspectionBackend(player, event.server());
            sendProxyAdmission(player, event.server(), existingGrant);
            return null;
        }

        VelocityAdmissionSession session = sessions.computeIfAbsent(
            player, ignored -> new VelocityAdmissionSession(newProxySessionId(), runtimeManager.current()));

        GuardianDecision existing = session.decision();
        if (existing != null) {
            completeDecision(player, event.server(), session, existing);
            return null;
        }

        BedrockEvidence bedrock = bedrockDetector.detect(player.getUniqueId());
        session.setBedrockEvidence(bedrock);
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
            completeDecision(player, event.server(), session, failure);
            return null;
        }

        String brand = player.getClientBrand();
        session.setObservedBrand(brand);
        ClientClassification classification = ClientOriginClassifier.classify(
            bedrock.geyser() == BedrockSignal.BEDROCK,
            bedrock.floodgate() == BedrockSignal.BEDROCK,
            brand);
        session.setClassification(classification);
        debug(session, "Guardian configuration for {}: brand={}, geyser={}, floodgate={}, classification={}, backend={}",
            player.getUsername(), String.valueOf(brand), bedrock.geyser(), bedrock.floodgate(), classification,
            backendName(event.server()));

        AdmissionPolicySnapshot policySnapshot = session.runtimeSnapshot().admissionPolicy();
        int timeoutSeconds = session.runtimeSnapshot().settings().handshakeTimeoutSeconds();
        CompletableFuture<AdmissionPermissionSnapshot> permissions = AdmissionProfileProviderGate.resolve(
            profileProvider, player.getUniqueId(), policySnapshot, timeoutSeconds);

        CompletableFuture<GuardianDecision> admission = permissions.handle((permissionSnapshot, throwable) -> {
            if (throwable != null) {
                if (policySnapshot.identityOverrides().containsKey(player.getUniqueId())) {
                    logger.warn("Guardian Admission profile provider failed for identity-overridden player {}; "
                            + "continuing with the configured identity profile and no provider bypasses: {}",
                        player.getUsername(), rootMessage(throwable));
                    return AdmissionPermissionSnapshot.none();
                }

                GuardianDecision failure = GuardianDecision.deny(
                    DecisionReason.PROFILE_RESOLUTION_FAILED,
                    "pre-login profile provider failed or timed out; refusing default-profile fallback");
                session.decide(failure);
                logger.warn("Guardian Admission profile provider failed for {}; admission fails closed instead of "
                        + "falling back to the default profile: {}",
                    player.getUsername(), rootMessage(throwable));
                return null;
            }
            return permissionSnapshot;
        }).thenCompose(permissionSnapshot -> {
            if (permissionSnapshot == null) {
                return session.decisionFuture();
            }

            var resolved = AdmissionProfileResolver.resolve(policySnapshot, player.getUniqueId(), permissionSnapshot);
            session.setResolvedProfile(resolved);
            ClientPolicyResult clientResult = policyEvaluator.evaluateClient(resolved, classification, brand);
            debug(session, "Guardian shared policy for {}: profile={}, source={}, action={}",
                player.getUsername(), resolved.profile().id(), resolved.source(), clientResult.action());

            if (clientResult.terminalDecision() != null) {
                session.decide(clientResult.terminalDecision());
                return session.decisionFuture();
            }

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

        CompletableFuture<Void> hold = admission.thenAccept(decision ->
            completeDecision(player, event.server(), session, decision));

        // Velocity explicitly awaits this continuation while the connection remains in CONFIGURATION.
        return EventTask.resumeWhenComplete(hold.exceptionally(throwable -> {
            logger.error("Guardian Admission future failed for {}: {}", player.getUsername(), rootMessage(throwable));
            GuardianDecision failure = GuardianDecision.deny(
                DecisionReason.CONFIGURATION_ERROR, "shared admission policy evaluation failed");
            session.decide(failure);
            GuardianDecision terminal = session.decision() == null ? failure : session.decision();
            completeDecision(player, event.server(), session, terminal);
            return null;
        }));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        ChannelIdentifier identifier = event.getIdentifier();
        if (!isGuardianChannel(identifier)) return;

        // Mark handled before examining source/data: clients and backends cannot spoof/forward these channels.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof Player player)) {
            logger.warn("Consumed unexpected backend-origin Guardian channel {}. Security-sensitive Guardian "
                + "channels must terminate at Velocity.", identifier.getId());
            return;
        }

        if (identifier.equals(PROXY_ADMISSION)) {
            logger.warn("Consumed client-origin proxy-admission assertion attempt from {}.", player.getUsername());
            return;
        }
        if (identifier.equals(CHALLENGE)) {
            logger.warn("Consumed unexpected client-origin Guardian challenge from {}.", player.getUsername());
            return;
        }
        if (admitted.contains(player)) {
            debugCurrent("Guardian consumed post-admission client channel {} from {}; no re-attestation is required.",
                identifier.getId(), player.getUsername());
            return;
        }

        VelocityAdmissionSession session = sessions.computeIfAbsent(
            player, ignored -> new VelocityAdmissionSession(newProxySessionId(), runtimeManager.current()));

        if (identifier.equals(PRESENCE)) {
            handlePresence(player, session, event.getData());
        } else if (identifier.equals(RESPONSE)) {
            handleResponse(player, session, event.getData());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        sessions.remove(player);
        admitted.remove(player);
        inspections.remove(player);
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

        debug(session, "Guardian Cerberus presence from {}: protocol={}, capabilities=0x{}",
            player.getUsername(), presence.minProtocolVersion() + ".." + presence.maxProtocolVersion(),
            Long.toHexString(presence.capabilities()));

        GuardianDecision presenceDecision = validateCerberusPresence(session, presence);
        if (presenceDecision != null) recordConfigurationAttestationFailure(session, presenceDecision);
    }

    private static long requiredCerberusCapabilities(VelocityAdmissionSession session) {
        long required = session.runtimeSnapshot().admissionPolicy().cerberusReleaseTrust().requiredCapabilities();
        if (session.runtimeSnapshot().settings().serverChallengeSigner() != null) {
            required |= GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE;
        }
        return required;
    }

    private static GuardianDecision validateCerberusPresence(
        VelocityAdmissionSession session, Presence presence
    ) {
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
            && session.runtimeSnapshot().settings().serverChallengeSigner() == null) {
            return GuardianDecision.deny(
                DecisionReason.CERBERUS_SERVER_AUTH_REQUIRED,
                "Cerberus requires an authenticated Guardian challenge, but Guardian-Velocity server authentication is disabled");
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

    private void recordConfigurationAttestationFailure(
        VelocityAdmissionSession session, GuardianDecision failure
    ) {
        session.recordConfigurationAttestationFailure(failure);
        if (session.cerberusRequired() && session.decision() == null) session.decide(failure);
    }

    private void startChallenge(Player player, VelocityAdmissionSession session) {
        if (session.decision() != null
            || session.classification() != ClientClassification.JAVA_FABRIC
            || !session.tryMarkChallengeSent()) return;

        byte[] nonce = new byte[GuardianProtocol.NONCE_BYTES];
        random.nextBytes(nonce);
        session.setNonce(nonce);

        final boolean sent;
        try {
            long requiredCapabilities = requiredCerberusCapabilities(session);
            var signer = session.runtimeSnapshot().settings().serverChallengeSigner();
            Challenge challenge = signer == null
                ? new Challenge(GuardianProtocol.VERSION, requiredCapabilities, nonce)
                : signer.authenticatedChallenge(
                    GuardianProtocol.VERSION, requiredCapabilities, nonce, player.getUniqueId());
            sent = player.sendPluginMessage(
                CHALLENGE,
                ProtocolCodec.encodeChallenge(challenge));
        } catch (RuntimeException ex) {
            logger.warn("Could not send Guardian CONFIGURATION challenge to {}: {}", player.getUsername(), rootMessage(ex));
            session.decide(GuardianDecision.deny(
                DecisionReason.CONFIGURATION_ERROR, "Velocity CONFIGURATION challenge send failed"));
            return;
        }

        if (!sent) {
            session.decide(GuardianDecision.deny(
                DecisionReason.CONFIGURATION_ERROR, "Velocity declined Guardian CONFIGURATION challenge send"));
            return;
        }
        debug(session, "Guardian CONFIGURATION challenge sent to {}.", player.getUsername());
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
        GuardianDecision integrityDecision = ProtocolV1ResponseValidator.validate(
            session.nonce(), response, session.runtimeSnapshot().admissionPolicy().cerberusReleaseTrust());
        GuardianDecision decision = integrityDecision;
        if (integrityDecision.outcome() == DecisionOutcome.ALLOW) {
            session.setManifest(response.manifest());
            if (session.resolvedProfile() == null) {
                decision = GuardianDecision.deny(DecisionReason.CONFIGURATION_ERROR,
                    "Cerberus response arrived before shared admission profile resolution");
            } else {
                decision = policyEvaluator.evaluateManifest(session.resolvedProfile(), response.manifest()).decision();
            }
        }
        session.decide(decision);
        debug(session, "Guardian CONFIGURATION response from {}: {} / {} ({})",
            player.getUsername(), decision.outcome(), decision.reason(), decision.detail());
    }

    private void armTimeout(Player player, VelocityAdmissionSession session) {
        int timeoutSeconds = session.runtimeSnapshot().settings().handshakeTimeoutSeconds();
        CompletableFuture.delayedExecutor(timeoutSeconds, TimeUnit.SECONDS).execute(() -> {
            if (session.decision() != null) return;

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
                debug(session, "Guardian handshake timeout for {}: {}", player.getUsername(), timeoutDecision.reason());
            }
        });
    }

    private void completeDecision(
        Player player, ServerConnection backend, VelocityAdmissionSession session, GuardianDecision decision
    ) {
        if (sessions.get(player) != session || !player.isActive()) {
            sessions.remove(player, session);
            debug(session, "Guardian ignored stale Admission completion for {} after connection end/replacement.",
                player.getUsername());
            return;
        }
        if (decision.outcome() == DecisionOutcome.ALLOW) {
            captureInspection(player, backend, session, decision);
            ConnectionOrigin origin = session.classification() == ClientClassification.BEDROCK
                ? ConnectionOrigin.BEDROCK : ConnectionOrigin.JAVA;
            VelocityAdmissionGrant grant = new VelocityAdmissionGrant(session.proxySessionId(), origin);
            admitted.put(player, grant);
            sendProxyAdmission(player, backend, grant);
        }
        applyDecision(player, session, decision);
        // Keep denied terminal sessions connection-bound until DisconnectEvent so a late client
        // message cannot recreate a fresh Admission session after the final deny decision. Allowed
        // connections move to the immutable grant registry and no longer need mutable session state.
        if (decision.outcome() == DecisionOutcome.ALLOW) {
            sessions.remove(player, session);
        }
    }

    private void applyDecision(Player player, VelocityAdmissionSession session, GuardianDecision decision) {
        logSummary(player, session, decision);
        if (decision.outcome() == DecisionOutcome.DENY) {
            player.disconnect(session.runtimeSnapshot().messages().render(decision, session.classification()));
        }
    }

    private boolean sendProxyAdmission(Player player, ServerConnection backend, VelocityAdmissionGrant grant) {
        if (backend == null) {
            logger.warn("Guardian could not assert admission for {} because no backend configuration connection "
                + "is available. Proxy-side admission remains authoritative.", player.getUsername());
            return false;
        }

        // Operational key rotation affects existing admitted proxy sessions on their next backend assertion;
        // the original Admission decision/policy snapshot remains unchanged for the connection lifetime.
        byte[] proxySecret = runtimeManager.current().settings().proxyAssertionSecret().copyBytes();
        long issuedAt = System.currentTimeMillis();
        ConnectionOrigin connectionOrigin = grant.connectionOrigin();
        ProxyAdmissionAssertion assertion = new ProxyAdmissionAssertion(
            GuardianProtocol.PROXY_ASSERTION_VERSION,
            player.getUniqueId(),
            grant.proxySessionId(),
            connectionOrigin,
            issuedAt,
            issuedAt + GuardianProtocol.PROXY_ASSERTION_TTL_MILLIS);

        final boolean sent;
        try {
            byte[] encoded = ProxyAdmissionCodec.encode(assertion, proxySecret);
            sent = backend.sendPluginMessage(PROXY_ADMISSION, encoded);
        } catch (RuntimeException ex) {
            logger.warn("Guardian proxy assertion send failed for {} -> {}: {}",
                player.getUsername(), DiagnosticText.oneLine(backend.getServerInfo().getName()), rootMessage(ex));
            return false;
        } finally {
            Arrays.fill(proxySecret, (byte) 0);
        }
        if (!sent) {
            logger.error("Guardian backend {} declined proxy assertion for {}.",
                backend.getServerInfo().getName(), player.getUsername());
            return false;
        }

        debugCurrent("Guardian trusted admission asserted for {} -> {}: session={}, origin={}",
            player.getUsername(), backend.getServerInfo().getName(),
            HexFormat.of().formatHex(grant.proxySessionId()), connectionOrigin);
        return true;
    }

    private void captureInspection(
        Player player, ServerConnection backend, VelocityAdmissionSession session, GuardianDecision decision
    ) {
        if (session.classification() == null || session.resolvedProfile() == null || session.bedrockEvidence() == null) {
            logger.warn("Guardian could not retain an inspection snapshot for {} because admission metadata was incomplete.",
                player.getUsername());
            return;
        }
        ActiveInspectionSnapshot snapshot = new ActiveInspectionSnapshot(
            player.getUniqueId(), player.getUsername(), backendName(backend), session.classification(),
            session.observedBrand(), session.resolvedProfile().profile().id(), session.resolvedProfile().source(),
            session.cerberusPresence(), decision, session.manifest(), session.bedrockEvidence(),
            session.runtimeSnapshot().generation());
        if (!inspections.put(player, snapshot)) {
            logger.warn("Guardian active inspection store is full; snapshot omitted for {}.", player.getUsername());
        }
    }

    private void updateInspectionBackend(Player player, ServerConnection backend) {
        inspections.updateBackend(player, backendName(backend));
    }

    private void logSummary(Player player, VelocityAdmissionSession session, GuardianDecision decision) {
        if (!session.tryMarkSummaryLogged()) return;
        String profile = session.resolvedProfile() == null ? "<unknown>" : session.resolvedProfile().profile().id();
        int mods = session.manifest() == null ? 0 : session.manifest().entries().size();
        StringBuilder summary = new StringBuilder()
            .append(player.getUsername()).append(' ').append(decision.outcome()).append(": ")
            .append(session.classification() == null ? "UNKNOWN" : session.classification())
            .append(", brand=").append(session.observedBrand() == null ? "<unknown>" : session.observedBrand())
            .append(", profile=").append(profile)
            .append(", ").append(decision.reason());
        if (mods > 0) summary.append(", mods=").append(mods);
        logger.info(DiagnosticText.oneLine(summary.toString()));
    }

    private void debug(VelocityAdmissionSession session, String format, Object... args) {
        if (session.runtimeSnapshot().settings().loggingLevel().debugEnabled())
            logger.info(format, safeDiagnosticArgs(args));
    }

    private void debugCurrent(String format, Object... args) {
        if (runtimeManager.current().settings().loggingLevel().debugEnabled())
            logger.info(format, safeDiagnosticArgs(args));
    }

    private static Object[] safeDiagnosticArgs(Object... args) {
        Object[] safe = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            safe[i] = DiagnosticText.oneLine(String.valueOf(args[i]));
        }
        return safe;
    }

    private void ensureAdministratorFile(String resourcePath) {
        Path data = dataDirectory.toAbsolutePath().normalize();
        Path destination = data.resolve(resourcePath).normalize();
        if (!destination.startsWith(data)) {
            throw new IllegalStateException("Guardian-Velocity administrator resource escapes plugin data directory: "
                + resourcePath);
        }
        try {
            Path parent = destination.getParent();
            if (parent != null && !parent.equals(data)) {
                SafeDirectory.ensureChildDirectories(data, parent);
            } else if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) return;
            try (InputStream input = GuardianVelocityPlugin.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (input == null) throw new IOException("packaged resource is missing: " + resourcePath);
                Files.copy(input, destination);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("could not install Guardian-Velocity administrator file "
                + destination + ": " + ex.getMessage(), ex);
        }
    }

    private static String backendName(ServerConnection backend) {
        return backend == null ? "<none>" : backend.getServerInfo().getName();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return DiagnosticText.oneLine(
            current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage());
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
