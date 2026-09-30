package com.badwolfmc.guardian.protocol;

public final class GuardianProtocol {
    public static final int VERSION = 1;
    public static final int MIN_VERSION = 1;
    public static final int NONCE_BYTES = 16;
    public static final int MAX_PROTOCOL_VERSION = 0xFFFF;
    public static final int MAX_PAYLOAD_BYTES = 65_536;
    public static final int MAX_MANIFEST_ENTRIES = 512;
    public static final int MAX_MOD_ID_BYTES = 64;
    public static final int MAX_VERSION_BYTES = 256;
    public static final int MAX_RELEASE_METADATA_BYTES = 256;
    public static final int MAX_RELATIONSHIP_DEPTH = 16;
    public static final long MAX_ARTIFACT_BYTES = 512L * 1024L * 1024L;
    public static final int MAX_RESPONSES_PER_CHALLENGE = 1;
    public static final long MAX_HANDSHAKE_MILLIS = 10_000L;

    public static final long CAP_CANONICAL_MANIFEST_V1 = 1L;
    public static final long CAP_CONTAINMENT_RELATIONSHIPS = 1L << 1;
    public static final long CAP_ORIGIN_KIND = 1L << 2;
    public static final long CAP_ARTIFACT_SHA256 = 1L << 3;
    public static final long CAP_SIGNED_CERBERUS_RELEASE = 1L << 4;
    public static final long CAP_AUTHENTICATED_GUARDIAN_CHALLENGE = 1L << 5;
    public static final long REQUIRED_CAPABILITIES = CAP_CANONICAL_MANIFEST_V1 | CAP_CONTAINMENT_RELATIONSHIPS | CAP_ORIGIN_KIND | CAP_ARTIFACT_SHA256;
    public static final long KNOWN_CAPABILITIES = REQUIRED_CAPABILITIES | CAP_SIGNED_CERBERUS_RELEASE
        | CAP_AUTHENTICATED_GUARDIAN_CHALLENGE;

    public static boolean hasUnknownCapabilities(long capabilities) {
        return (capabilities & ~KNOWN_CAPABILITIES) != 0L;
    }

    public static boolean supportsProtocolV1Capabilities(long capabilities) {
        return !hasUnknownCapabilities(capabilities)
            && (capabilities & REQUIRED_CAPABILITIES) == REQUIRED_CAPABILITIES;
    }

    public static final int GUARDIAN_CHALLENGE_SIGNATURE_BYTES = 64;
    public static final long GUARDIAN_CHALLENGE_AUTH_TTL_MILLIS = 15_000L;
    public static final long GUARDIAN_CHALLENGE_AUTH_CLOCK_SKEW_MILLIS = 2_000L;

    public static final int PROXY_ASSERTION_VERSION = 2;
    public static final int PROXY_SESSION_ID_BYTES = 16;
    public static final int PROXY_HMAC_BYTES = 32;
    public static final int PROXY_SECRET_BYTES = 32;
    public static final long PROXY_ASSERTION_TTL_MILLIS = 15_000L;
    public static final long PROXY_ASSERTION_CLOCK_SKEW_MILLIS = 2_000L;

    public static final String PRESENCE_CHANNEL = "guardian:presence";
    public static final String CHALLENGE_CHANNEL = "guardian:challenge";
    public static final String RESPONSE_CHANNEL = "guardian:response";
    public static final String PROXY_ADMISSION_CHANNEL = "guardian:proxy-admission";

    static final int MAGIC = 0x47554130; // GUA0; retained because proxy assertions share this envelope.
    static final int TYPE_CHALLENGE = 1, TYPE_RESPONSE = 2, TYPE_PRESENCE = 3, TYPE_PROXY_ADMISSION = 4;
    private GuardianProtocol() {}
}
