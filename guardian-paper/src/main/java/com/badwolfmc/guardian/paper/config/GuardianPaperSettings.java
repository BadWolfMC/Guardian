package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.paper.PaperAuthorityMode;
import com.badwolfmc.guardian.protection.ProtectionPolicy;

import java.util.Objects;

public record GuardianPaperSettings(
    int schemaVersion,
    boolean admissionEnabled,
    boolean protectionEnabled,
    String locale,
    String helpUrl,
    PaperAuthorityMode authorityMode,
    int handshakeTimeoutSeconds,
    int challengeChannelWaitTicks,
    ProtectionPolicy protectionPolicy
) {
    public GuardianPaperSettings {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(helpUrl, "helpUrl");
        Objects.requireNonNull(authorityMode, "authorityMode");
        Objects.requireNonNull(protectionPolicy, "protectionPolicy");
    }

    public long handshakeTimeoutTicks() {
        return handshakeTimeoutSeconds * 20L;
    }
}
