package com.badwolfmc.guardian.core.policy;

import java.nio.file.Path;

/** Strict shared admission-policy parse/validation failure. */
public final class AdmissionPolicyException extends Exception {
    private final Path path;

    public AdmissionPolicyException(Path path, String message) {
        super(message);
        this.path = path;
    }

    public AdmissionPolicyException(Path path, String message, Throwable cause) {
        super(message, cause);
        this.path = path;
    }

    public Path path() {
        return path;
    }
}
