package com.badwolfmc.guardian.core;

/**
 * One optional integration's Bedrock-origin observation for the current connection.
 *
 * <p>{@link #UNAVAILABLE} means the integration is not installed/enabled on this host. {@link #ERROR}
 * means Guardian expected to query an available integration but could not obtain a trustworthy answer.</p>
 */
public enum BedrockSignal {
    UNAVAILABLE,
    NOT_BEDROCK,
    BEDROCK,
    ERROR
}
