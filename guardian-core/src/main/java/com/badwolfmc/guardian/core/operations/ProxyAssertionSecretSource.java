package com.badwolfmc.guardian.core.operations;

import java.util.Locale;

/** Supported production sources for the shared Guardian-Velocity -> Guardian-Paper assertion secret. */
public enum ProxyAssertionSecretSource {
    ENVIRONMENT,
    FILE;

    public static ProxyAssertionSecretSource parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("proxy assertion secret source must be ENVIRONMENT or FILE");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("proxy assertion secret source must be ENVIRONMENT or FILE", ex);
        }
    }
}
