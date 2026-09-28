package com.badwolfmc.guardian.paper;

import org.geysermc.geyser.api.GeyserApi;

import java.util.UUID;

final class GeyserBedrockLookup {
    private GeyserBedrockLookup() {
    }

    static boolean isBedrockPlayer(UUID playerId) {
        GeyserApi api = GeyserApi.api();
        if (api == null) {
            throw new IllegalStateException("Geyser API is not available yet");
        }
        return api.isBedrockPlayer(playerId);
    }
}
