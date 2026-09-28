package com.badwolfmc.guardian.core;

import java.util.Objects;

/**
 * Platform-neutral combination of supported Geyser/Floodgate origin evidence.
 *
 * <p>Positive supported API evidence wins so a known Bedrock connection is never sent down the
 * Cerberus path merely because another optional provider is absent, negative, or temporarily
 * unhealthy. If no provider supplies positive evidence, an actual provider query failure makes the
 * origin indeterminate rather than silently reinterpreting the connection as Java.</p>
 */
public record BedrockEvidence(BedrockSignal geyser, BedrockSignal floodgate) {
    public BedrockEvidence {
        Objects.requireNonNull(geyser, "geyser");
        Objects.requireNonNull(floodgate, "floodgate");
    }

    public BedrockResolution resolution() {
        if (geyser == BedrockSignal.BEDROCK || floodgate == BedrockSignal.BEDROCK) {
            return BedrockResolution.BEDROCK;
        }
        if (geyser == BedrockSignal.ERROR || floodgate == BedrockSignal.ERROR) {
            return BedrockResolution.INDETERMINATE;
        }
        return BedrockResolution.JAVA;
    }

    public boolean disagrees() {
        return isExplicit(geyser) && isExplicit(floodgate) && geyser != floodgate;
    }

    private static boolean isExplicit(BedrockSignal signal) {
        return signal == BedrockSignal.BEDROCK || signal == BedrockSignal.NOT_BEDROCK;
    }
}
