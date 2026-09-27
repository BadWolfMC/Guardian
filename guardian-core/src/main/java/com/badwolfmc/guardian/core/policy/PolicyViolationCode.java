package com.badwolfmc.guardian.core.policy;

public enum PolicyViolationCode {
    REQUIRED_MOD_MISSING,
    EXPLICIT_MOD_DENY,
    UNLISTED_MOD,
    VERSION_NOT_ACCEPTED,
    ARTIFACT_NOT_ACCEPTED,
    DIRECTORY_ORIGIN_DENIED,
    MIXED_OR_UNKNOWN_ORIGIN_DENIED
}
