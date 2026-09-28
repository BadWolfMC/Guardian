package com.badwolfmc.guardian.core.operations;

import java.util.Locale;

/** Deliberately small production logging policy shared by Guardian platform hosts. */
public enum OperationalLogLevel {
    NORMAL,
    DEBUG;

    public static OperationalLogLevel parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("logging level must be NORMAL or DEBUG");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("logging level must be NORMAL or DEBUG", ex);
        }
    }

    public boolean debugEnabled() {
        return this == DEBUG;
    }
}
