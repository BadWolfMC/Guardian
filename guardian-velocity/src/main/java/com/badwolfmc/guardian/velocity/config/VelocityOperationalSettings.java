package com.badwolfmc.guardian.velocity.config;

import com.badwolfmc.guardian.core.operations.OperationalLogLevel;
import com.badwolfmc.guardian.core.operations.ProxyAssertionSecret;
import com.badwolfmc.guardian.core.operations.GuardianServerChallengeSigner;

import java.util.Objects;

/** Immutable proxy-local operational configuration. Admission policy remains platform-neutral. */
public record VelocityOperationalSettings(
    int schemaVersion,
    String locale,
    int handshakeTimeoutSeconds,
    OperationalLogLevel loggingLevel,
    ProxyAssertionSecret proxyAssertionSecret,
    GuardianServerChallengeSigner serverChallengeSigner
) {
    public VelocityOperationalSettings {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(loggingLevel, "loggingLevel");
        Objects.requireNonNull(proxyAssertionSecret, "proxyAssertionSecret");
    }
}
