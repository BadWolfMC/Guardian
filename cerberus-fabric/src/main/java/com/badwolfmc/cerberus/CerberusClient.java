package com.badwolfmc.cerberus;

import com.badwolfmc.cerberus.network.ChallengePayload;
import com.badwolfmc.cerberus.network.PresencePayload;
import com.badwolfmc.cerberus.network.ResponsePayload;
import com.badwolfmc.guardian.protocol.Challenge;
import com.badwolfmc.guardian.protocol.CerberusReleaseIdentity;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Manifest;
import com.badwolfmc.guardian.protocol.Presence;
import com.badwolfmc.guardian.protocol.ProtocolCodec;
import com.badwolfmc.guardian.protocol.ProtocolException;
import com.badwolfmc.guardian.protocol.Response;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.badwolfmc.cerberus.manifest.FabricManifestCollector;
import com.badwolfmc.cerberus.release.CerberusReleaseIdentityProvider;
import com.badwolfmc.cerberus.trust.GuardianServerTrustStore;

import java.util.UUID;

public final class CerberusClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Cerberus");

    @Override
    public void onInitializeClient() {
        registerPayloadTypes();
        registerConfigurationTransport();
        registerPlayTransport();
        LOGGER.info("Cerberus initialized with Guardian protocol v1 manifest reporting. Diagnostic switches use guardian.cerberus.dev.*.");
    }

    private static void registerPayloadTypes() {
        // CONFIGURATION: presence is useful and proven to cross Fabric -> Paper.
        PayloadTypeRegistry.clientboundConfiguration().register(ChallengePayload.TYPE, ChallengePayload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(PresencePayload.TYPE, PresencePayload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(ResponsePayload.TYPE, ResponsePayload.CODEC);

        // PLAY fallback: same protocol messages, phase-specific Fabric registries.
        PayloadTypeRegistry.clientboundPlay().register(ChallengePayload.TYPE, ChallengePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PresencePayload.TYPE, PresencePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ResponsePayload.TYPE, ResponsePayload.CODEC);
    }

    private static void registerConfigurationTransport() {
        ClientConfigurationNetworking.registerGlobalReceiver(ChallengePayload.TYPE, (payload, context) -> {
            LOGGER.info("Guardian CONFIGURATION challenge received; responding.");
            respondToChallenge(payload, context.responseSender(), "CONFIGURATION",
                context.client().getUser().getProfileId());
        });

        ClientConfigurationConnectionEvents.START.register((listener, client) -> {
            try {
                boolean canSendPresence = ClientConfigurationNetworking.canSend(PresencePayload.TYPE);
                boolean canSendResponse = ClientConfigurationNetworking.canSend(ResponsePayload.TYPE);
                LOGGER.info("Guardian CONFIGURATION START: presenceSendable={}, responseSendable={}, challengeReceivable={}",
                    canSendPresence,
                    canSendResponse,
                    ClientConfigurationNetworking.getReceived().contains(ChallengePayload.TYPE.id()));

                if (!canSendPresence) {
                    LOGGER.warn("Connected server did not advertise the Guardian CONFIGURATION presence channel; "
                        + "attempting presence anyway (known Paper/Fabric interoperability behavior).");
                }

                int protocol = selectedProtocol();
                ClientConfigurationNetworking.send(
                    new PresencePayload(ProtocolCodec.encodePresence(new Presence(protocol, protocol, capabilities(), cerberusVersion())))
                );
                LOGGER.info("Attempted Guardian CONFIGURATION presence with protocol {} (serverAdvertised={}).",
                    protocol, canSendPresence);
            } catch (RuntimeException ex) {
                LOGGER.warn("Could not send Guardian CONFIGURATION presence", ex);
            }
        });
    }

    private static void registerPlayTransport() {
        ClientPlayNetworking.registerGlobalReceiver(ChallengePayload.TYPE, (payload, context) ->
            respondToChallenge(payload, context.responseSender(), "PLAY",
                context.client().getUser().getProfileId()));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            try {
                boolean canSendPresence = ClientPlayNetworking.canSend(PresencePayload.TYPE);
                boolean canSendResponse = ClientPlayNetworking.canSend(ResponsePayload.TYPE);
                LOGGER.info("Guardian PLAY JOIN: presenceSendable={}, responseSendable={}, challengeReceivable={}",
                    canSendPresence,
                    canSendResponse,
                    ClientPlayNetworking.getReceived().contains(ChallengePayload.TYPE.id()));

                if (!canSendPresence) {
                    LOGGER.warn("Connected server did not advertise the Guardian PLAY presence channel; "
                        + "attempting presence anyway for fallback interoperability testing.");
                }

                int protocol = selectedProtocol();
                sender.sendPacket(new PresencePayload(ProtocolCodec.encodePresence(new Presence(protocol, protocol, capabilities(), cerberusVersion()))));
                LOGGER.info("Attempted Guardian PLAY presence with protocol {} (serverAdvertised={}).",
                    protocol, canSendPresence);
            } catch (RuntimeException ex) {
                LOGGER.warn("Could not send Guardian PLAY presence", ex);
            }
        });
    }

    private static void respondToChallenge(
        ChallengePayload payload, PacketSender sender, String phase, UUID localPlayerId
    ) {
        try {
            Challenge challenge = ProtocolCodec.decodeChallenge(payload.bytes());

            if (GuardianServerTrustStore.requiresAuthenticatedChallenge()
                && !GuardianServerTrustStore.verify(challenge, localPlayerId)) {
                LOGGER.warn("Ignoring unauthenticated or untrusted Guardian {} challenge; Cerberus manifest was not disclosed.", phase);
                return;
            }

            // Re-announce presence when Guardian actively challenges us. On Velocity, the initial
            // CONFIGURATION START presence can arrive before a backend connection is in flight and
            // therefore before Velocity exposes plugin messages to plugins. Re-announcing here makes
            // the distinct CERBERUS_REQUIRED vs CERBERUS_TIMEOUT states robust without changing the
            // standalone Paper PLAY fallback.
            int responseProtocol = selectedProtocol();
            sender.sendPacket(new PresencePayload(ProtocolCodec.encodePresence(new Presence(responseProtocol, responseProtocol, capabilities(), cerberusVersion()))));

            if (Boolean.getBoolean("guardian.cerberus.dev.suppressResponse")) {
                LOGGER.info(
                    "Received Guardian {} challenge; re-announced presence then deliberately suppressed "
                        + "the response for timeout testing.",
                    phase
                );
                return;
            }

            if (Boolean.getBoolean("guardian.cerberus.dev.malformedResponse")) {
                sender.sendPacket(new ResponsePayload(new byte[] {0x00}));
                LOGGER.info("Sent deliberately malformed Guardian {} response.", phase);
                return;
            }

            long capabilities = capabilities();
            if (challenge.protocolVersion() != GuardianProtocol.VERSION || (capabilities & challenge.requiredCapabilities()) != challenge.requiredCapabilities()) {
                LOGGER.warn("Guardian {} challenge requested unsupported protocol/capabilities; no response will be sent.", phase);
                return;
            }
            Manifest manifest = FabricManifestCollector.collect(capabilities);
            if (Boolean.getBoolean("guardian.cerberus.dev.logManifest")) {
                LOGGER.info("Cerberus sanitized canonical manifest: minecraft={}, loader={}, cerberus={}, entries={}",
                    manifest.minecraftVersion(), manifest.fabricLoaderVersion(), manifest.cerberusVersion(), manifest.entries());
            }
            Response response = new Response(responseProtocol, capabilities, challenge.nonce(), manifest, releaseIdentity());
            sender.sendPacket(new ResponsePayload(ProtocolCodec.encodeResponse(response)));
            LOGGER.info("Responded to Guardian {} challenge with protocol {} and {} canonical manifest entries.",
                phase, responseProtocol, manifest.entries().size());
        } catch (ProtocolException | RuntimeException ex) {
            LOGGER.warn("Ignoring invalid Guardian {} challenge", phase, ex);
        }
    }

    private static long capabilities() {
        return GuardianProtocol.REQUIRED_CAPABILITIES
            | (releaseIdentity() == null ? 0L : GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE)
            | (GuardianServerTrustStore.requiresAuthenticatedChallenge()
                ? GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE : 0L);
    }

    private static CerberusReleaseIdentity releaseIdentity() {
        return CerberusReleaseIdentityProvider.current();
    }

    private static int selectedProtocol() {
        return Integer.getInteger("guardian.cerberus.dev.protocol", GuardianProtocol.VERSION);
    }

    private static String cerberusVersion() {
        return FabricLoader.getInstance().getModContainer("cerberus")
            .map(ModContainer::getMetadata).map(m -> m.getVersion().getFriendlyString()).orElse("unknown");
    }
}
