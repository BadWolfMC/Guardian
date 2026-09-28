package com.badwolfmc.guardian.velocity.config;

import java.nio.file.Path;
import java.util.Objects;

public final class VelocityConfigurationException extends Exception {
    private final Path path;

    public VelocityConfigurationException(Path path, String message) {
        super(message);
        this.path = Objects.requireNonNull(path, "path");
    }

    public VelocityConfigurationException(Path path, String message, Throwable cause) {
        super(message, cause);
        this.path = Objects.requireNonNull(path, "path");
    }

    public Path path() {
        return path;
    }
}
