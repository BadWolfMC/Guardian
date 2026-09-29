package com.badwolfmc.guardian.paper.config;

import java.nio.file.Path;
import java.util.Objects;

/** Validation failure tied to a specific administrator-owned Guardian file. */
public final class GuardianConfigurationException extends Exception {
    public enum Kind {
        MISSING,
        MALFORMED,
        INVALID,
        EXTERNAL_DEPENDENCY,
        ADMISSION_POLICY,
        UNSUPPORTED_SCHEMA
    }

    private final Path path;
    private final Kind kind;

    public GuardianConfigurationException(Path path, Kind kind, String message) {
        super(format(path, message));
        this.path = Objects.requireNonNull(path, "path");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public GuardianConfigurationException(Path path, Kind kind, String message, Throwable cause) {
        super(format(path, message), cause);
        this.path = Objects.requireNonNull(path, "path");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public Path path() {
        return path;
    }

    public Kind kind() {
        return kind;
    }

    /** Startup may recover only administrator-edit failures, never schema/version mismatches. */
    public boolean recoverableAtStartup() {
        return kind == Kind.MALFORMED || kind == Kind.INVALID;
    }

    private static String format(Path path, String message) {
        return Objects.requireNonNull(path, "path") + ": " + Objects.requireNonNull(message, "message");
    }
}
