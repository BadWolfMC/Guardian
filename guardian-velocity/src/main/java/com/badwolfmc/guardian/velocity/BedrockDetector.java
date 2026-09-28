package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.BedrockEvidence;
import com.badwolfmc.guardian.core.BedrockSignal;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.UUID;

/** Queries optional supported origin providers without treating usernames as identity evidence. */
final class BedrockDetector {
    private final Logger logger;
    private final boolean geyserAvailable;
    private final boolean floodgateAvailable;

    BedrockDetector(ProxyServer server, Logger logger) {
        this.logger = logger;
        this.geyserAvailable = server.getPluginManager().getPlugin("geyser").isPresent();
        this.floodgateAvailable = server.getPluginManager().getPlugin("floodgate").isPresent();
    }

    BedrockEvidence detect(UUID playerId) {
        BedrockSignal geyser = queryGeyser(playerId);
        BedrockSignal floodgate = queryFloodgate(playerId);
        return new BedrockEvidence(geyser, floodgate);
    }

    private BedrockSignal queryGeyser(UUID playerId) {
        if (!geyserAvailable) {
            return BedrockSignal.UNAVAILABLE;
        }
        try {
            return GeyserBedrockLookup.isBedrockPlayer(playerId)
                ? BedrockSignal.BEDROCK
                : BedrockSignal.NOT_BEDROCK;
        } catch (RuntimeException | LinkageError ex) {
            logger.warn("Guardian could not query the optional Geyser API for {}.", playerId, ex);
            return BedrockSignal.ERROR;
        }
    }

    private BedrockSignal queryFloodgate(UUID playerId) {
        if (!floodgateAvailable) {
            return BedrockSignal.UNAVAILABLE;
        }
        try {
            return FloodgateBedrockLookup.isBedrockPlayer(playerId)
                ? BedrockSignal.BEDROCK
                : BedrockSignal.NOT_BEDROCK;
        } catch (RuntimeException | LinkageError ex) {
            logger.warn("Guardian could not query the optional Floodgate API for {}.", playerId, ex);
            return BedrockSignal.ERROR;
        }
    }
}
