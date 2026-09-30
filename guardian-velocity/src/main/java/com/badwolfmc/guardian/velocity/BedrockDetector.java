package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.BedrockEvidence;
import com.badwolfmc.guardian.core.BedrockProviderState;
import com.badwolfmc.guardian.core.BedrockSignal;
import com.badwolfmc.guardian.core.operations.DiagnosticText;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.UUID;

/** Queries optional supported origin providers without treating usernames as identity evidence. */
final class BedrockDetector {
    private final ProxyServer server;
    private final Logger logger;
    private final BedrockProviderState geyserState;
    private final BedrockProviderState floodgateState;

    BedrockDetector(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.geyserState = new BedrockProviderState(server.getPluginManager().isLoaded("geyser"));
        this.floodgateState = new BedrockProviderState(server.getPluginManager().isLoaded("floodgate"));
    }

    BedrockEvidence detect(UUID playerId) {
        refreshAvailability();
        BedrockProviderState.Snapshot geyser = geyserState.snapshot();
        BedrockProviderState.Snapshot floodgate = floodgateState.snapshot();
        BedrockSignal geyserSignal = queryGeyser(playerId, geyser);
        BedrockSignal floodgateSignal = queryFloodgate(playerId, floodgate);
        // Refresh once more before finalizing provider evidence. Velocity has no supported
        // hot-reload lifecycle contract for plugins, but this keeps even unusual manager-state
        // changes from becoming stale origin authority.
        refreshAvailability();
        geyserSignal = geyserState.stabilizeSignal(geyser, geyserSignal);
        floodgateSignal = floodgateState.stabilizeSignal(floodgate, floodgateSignal);
        return new BedrockEvidence(geyserSignal, floodgateSignal);
    }

    /** Refreshes proxy plugin-manager capability state before/after each new origin decision. */
    void refreshAvailability() {
        updateState(geyserState, server.getPluginManager().isLoaded("geyser"));
        updateState(floodgateState, server.getPluginManager().isLoaded("floodgate"));
    }

    private BedrockSignal queryGeyser(UUID playerId, BedrockProviderState.Snapshot provider) {
        if (!provider.available()) {
            return geyserState.unavailableSignal(provider);
        }
        try {
            boolean bedrock = GeyserBedrockLookup.isBedrockPlayer(playerId);
            return geyserState.queriedSignal(provider, bedrock);
        } catch (RuntimeException | LinkageError ex) {
            logger.warn("Guardian could not query the optional Geyser API for {}: {}", playerId, safeException(ex));
            return BedrockSignal.ERROR;
        }
    }

    private BedrockSignal queryFloodgate(UUID playerId, BedrockProviderState.Snapshot provider) {
        if (!provider.available()) {
            return floodgateState.unavailableSignal(provider);
        }
        try {
            boolean bedrock = FloodgateBedrockLookup.isBedrockPlayer(playerId);
            return floodgateState.queriedSignal(provider, bedrock);
        } catch (RuntimeException | LinkageError ex) {
            logger.warn("Guardian could not query the optional Floodgate API for {}: {}", playerId, safeException(ex));
            return BedrockSignal.ERROR;
        }
    }

    private static void updateState(BedrockProviderState state, boolean available) {
        if (available) {
            state.markAvailable();
        } else {
            state.markUnavailable();
        }
    }

    private static String safeException(Throwable throwable) {
        String message = throwable.getMessage();
        return DiagnosticText.oneLine(message == null ? throwable.getClass().getSimpleName() : message);
    }
}
