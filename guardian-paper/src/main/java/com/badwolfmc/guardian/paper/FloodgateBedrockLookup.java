package com.badwolfmc.guardian.paper;

import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

final class FloodgateBedrockLookup {
    private FloodgateBedrockLookup() {
    }

    static boolean isFloodgatePlayer(UUID playerId) {
        FloodgateApi api = FloodgateApi.getInstance();
        if (api == null) {
            throw new IllegalStateException("Floodgate API is not available yet");
        }
        return api.isFloodgatePlayer(playerId);
    }
}
