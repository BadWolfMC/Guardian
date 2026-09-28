package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.BedrockEvidence;
import com.badwolfmc.guardian.core.BedrockSignal;

import java.util.UUID;

/** Supported standalone-Paper origin discovery through optional Geyser/Floodgate APIs. */
final class PaperBedrockDetector {
    private final GuardianPaperPlugin plugin;
    private final boolean geyserAvailable;
    private final boolean floodgateAvailable;

    PaperBedrockDetector(GuardianPaperPlugin plugin) {
        this.plugin = plugin;
        this.geyserAvailable = isEnabled(plugin, "Geyser-Spigot");
        this.floodgateAvailable = isEnabled(plugin, "floodgate");
    }

    BedrockEvidence detect(UUID playerId) {
        return new BedrockEvidence(queryGeyser(playerId), queryFloodgate(playerId));
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
            plugin.getLogger().warning("Guardian could not query the optional Geyser API for "
                + playerId + ": " + ex.getMessage());
            return BedrockSignal.ERROR;
        }
    }

    private BedrockSignal queryFloodgate(UUID playerId) {
        if (!floodgateAvailable) {
            return BedrockSignal.UNAVAILABLE;
        }
        try {
            return FloodgateBedrockLookup.isFloodgatePlayer(playerId)
                ? BedrockSignal.BEDROCK
                : BedrockSignal.NOT_BEDROCK;
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().warning("Guardian could not query the optional Floodgate API for "
                + playerId + ": " + ex.getMessage());
            return BedrockSignal.ERROR;
        }
    }

    private static boolean isEnabled(GuardianPaperPlugin plugin, String pluginName) {
        var integration = plugin.getServer().getPluginManager().getPlugin(pluginName);
        return integration != null && integration.isEnabled();
    }
}
