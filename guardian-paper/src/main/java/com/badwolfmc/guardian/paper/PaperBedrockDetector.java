package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.BedrockEvidence;
import com.badwolfmc.guardian.core.BedrockProviderState;
import com.badwolfmc.guardian.core.BedrockSignal;
import com.badwolfmc.guardian.core.operations.DiagnosticText;

import java.util.Locale;
import java.util.UUID;

/** Supported standalone-Paper origin discovery through optional Geyser/Floodgate APIs. */
final class PaperBedrockDetector {
    private static final String GEYSER_PLUGIN = "Geyser-Spigot";
    private static final String FLOODGATE_PLUGIN = "floodgate";

    private final GuardianPaperPlugin plugin;
    private final BedrockProviderState geyserState;
    private final BedrockProviderState floodgateState;

    PaperBedrockDetector(GuardianPaperPlugin plugin) {
        this.plugin = plugin;
        this.geyserState = new BedrockProviderState(isEnabled(plugin, GEYSER_PLUGIN));
        this.floodgateState = new BedrockProviderState(isEnabled(plugin, FLOODGATE_PLUGIN));
    }

    BedrockEvidence detect(UUID playerId) {
        BedrockProviderState.Snapshot geyser = geyserState.snapshot();
        BedrockProviderState.Snapshot floodgate = floodgateState.snapshot();
        BedrockSignal geyserSignal = queryGeyser(playerId, geyser);
        BedrockSignal floodgateSignal = queryFloodgate(playerId, floodgate);
        // Re-check both generations after both queries so a lifecycle change anywhere inside the
        // combined origin observation cannot leave stale provider evidence authoritative.
        geyserSignal = geyserState.stabilizeSignal(geyser, geyserSignal);
        floodgateSignal = floodgateState.stabilizeSignal(floodgate, floodgateSignal);
        return new BedrockEvidence(geyserSignal, floodgateSignal);
    }

    BedrockSignal floodgateSignal(UUID playerId) {
        BedrockProviderState.Snapshot provider = floodgateState.snapshot();
        BedrockSignal signal = queryFloodgate(playerId, provider);
        return floodgateState.stabilizeSignal(provider, signal);
    }

    boolean pluginEnabled(String pluginName) {
        if (matches(pluginName, GEYSER_PLUGIN)) {
            geyserState.markAvailable();
            return true;
        }
        if (matches(pluginName, FLOODGATE_PLUGIN)) {
            floodgateState.markAvailable();
            return true;
        }
        return false;
    }

    boolean pluginDisabled(String pluginName) {
        if (matches(pluginName, GEYSER_PLUGIN)) {
            geyserState.markUnavailable();
            return true;
        }
        if (matches(pluginName, FLOODGATE_PLUGIN)) {
            floodgateState.markUnavailable();
            return true;
        }
        return false;
    }

    private BedrockSignal queryGeyser(UUID playerId, BedrockProviderState.Snapshot provider) {
        if (!provider.available()) {
            return geyserState.unavailableSignal(provider);
        }
        try {
            boolean bedrock = GeyserBedrockLookup.isBedrockPlayer(playerId);
            return geyserState.queriedSignal(provider, bedrock);
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().warning("Guardian could not query the optional Geyser API for "
                + playerId + ": " + safeException(ex));
            return BedrockSignal.ERROR;
        }
    }

    private BedrockSignal queryFloodgate(UUID playerId, BedrockProviderState.Snapshot provider) {
        if (!provider.available()) {
            return floodgateState.unavailableSignal(provider);
        }
        try {
            boolean bedrock = FloodgateBedrockLookup.isFloodgatePlayer(playerId);
            return floodgateState.queriedSignal(provider, bedrock);
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().warning("Guardian could not query the optional Floodgate API for "
                + playerId + ": " + safeException(ex));
            return BedrockSignal.ERROR;
        }
    }

    private static boolean isEnabled(GuardianPaperPlugin plugin, String pluginName) {
        var integration = plugin.getServer().getPluginManager().getPlugin(pluginName);
        return integration != null && integration.isEnabled();
    }

    private static boolean matches(String actual, String expected) {
        return actual != null && actual.toLowerCase(Locale.ROOT).equals(expected.toLowerCase(Locale.ROOT));
    }

    private static String safeException(Throwable throwable) {
        String message = throwable.getMessage();
        return DiagnosticText.oneLine(message == null ? throwable.getClass().getSimpleName() : message);
    }
}
