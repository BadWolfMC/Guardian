package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.core.operations.OperationalLogLevel;
import com.badwolfmc.guardian.core.operations.ProxyAssertionSecret;
import com.badwolfmc.guardian.core.operations.GuardianServerChallengeSigner;
import com.badwolfmc.guardian.paper.PaperAuthorityMode;
import com.badwolfmc.guardian.protection.ProtectionPolicy;

import java.util.Objects;

public record GuardianPaperSettings(
    int schemaVersion,
    boolean admissionEnabled,
    boolean protectionEnabled,
    String serverName,
    String locale,
    String helpUrl,
    PaperAuthorityMode authorityMode,
    int handshakeTimeoutSeconds,
    int challengeChannelWaitTicks,
    OperationalLogLevel loggingLevel,
    ProxyAssertionSecret proxyAssertionSecret,
    GuardianServerChallengeSigner serverChallengeSigner,
    ProtectionPolicy protectionPolicy
) {
    public GuardianPaperSettings {
        serverName = serverName == null ? "" : serverName.trim();
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(helpUrl, "helpUrl");
        Objects.requireNonNull(authorityMode, "authorityMode");
        Objects.requireNonNull(loggingLevel, "loggingLevel");
        Objects.requireNonNull(protectionPolicy, "protectionPolicy");
        if (admissionEnabled && authorityMode == PaperAuthorityMode.VELOCITY) {
            Objects.requireNonNull(proxyAssertionSecret, "proxyAssertionSecret");
        }
    }

    public long handshakeTimeoutTicks() {
        return handshakeTimeoutSeconds * 20L;
    }
    public GuardianPaperSettings(
        int schemaVersion,
        boolean admissionEnabled,
        boolean protectionEnabled,
        String serverName,
        String locale,
        String helpUrl,
        PaperAuthorityMode authorityMode,
        int handshakeTimeoutSeconds,
        int challengeChannelWaitTicks,
        OperationalLogLevel loggingLevel,
        ProxyAssertionSecret proxyAssertionSecret,
        ProtectionPolicy protectionPolicy
    ) {
        this(schemaVersion, admissionEnabled, protectionEnabled, serverName, locale, helpUrl, authorityMode,
            handshakeTimeoutSeconds, challengeChannelWaitTicks, loggingLevel, proxyAssertionSecret, null,
            protectionPolicy);
    }

}
