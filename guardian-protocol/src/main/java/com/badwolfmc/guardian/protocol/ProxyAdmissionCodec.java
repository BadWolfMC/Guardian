package com.badwolfmc.guardian.protocol;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

/**
 * Canonical proxy-admission assertion encoding and HMAC verification.
 *
 * <p>The shared secret belongs only to Guardian-Velocity and Guardian-Paper. It is deliberately
 * unrelated to Cerberus and is useful because both holders are server-side infrastructure.</p>
 */
public final class ProxyAdmissionCodec {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private ProxyAdmissionCodec() {
    }

    public static byte[] encode(ProxyAdmissionAssertion assertion, byte[] secret) {
        requireSecret(secret);
        byte[] unsigned = encodeUnsigned(assertion);
        byte[] signature = hmac(unsigned, secret);

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(unsigned.length + signature.length);
            bytes.write(unsigned);
            bytes.write(signature);
            byte[] payload = bytes.toByteArray();
            if (payload.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("proxy assertion exceeds payload limit");
            }
            return payload;
        } catch (IOException ex) {
            throw new IllegalStateException("in-memory proxy assertion encoding failed", ex);
        }
    }

    public static ProxyAdmissionAssertion decodeAndVerify(byte[] payload, byte[] secret) throws ProtocolException {
        requireSecret(secret);
        if (payload == null || payload.length == 0) {
            throw new ProtocolException("empty proxy assertion");
        }
        if (payload.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            throw new ProtocolException("proxy assertion exceeds payload limit");
        }
        if (payload.length <= GuardianProtocol.PROXY_HMAC_BYTES) {
            throw new ProtocolException("truncated proxy assertion");
        }

        int unsignedLength = payload.length - GuardianProtocol.PROXY_HMAC_BYTES;
        byte[] unsigned = java.util.Arrays.copyOf(payload, unsignedLength);
        byte[] suppliedSignature = java.util.Arrays.copyOfRange(payload, unsignedLength, payload.length);
        byte[] expectedSignature = hmac(unsigned, secret);
        if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
            throw new ProtocolException("proxy assertion HMAC mismatch");
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(unsigned))) {
            if (in.readInt() != GuardianProtocol.MAGIC) {
                throw new ProtocolException("invalid protocol magic");
            }
            int type = in.readUnsignedByte();
            if (type != GuardianProtocol.TYPE_PROXY_ADMISSION) {
                throw new ProtocolException("unexpected message type " + type);
            }

            int assertionVersion = Short.toUnsignedInt(in.readShort());
            UUID playerId = new UUID(in.readLong(), in.readLong());
            byte[] proxySessionId = in.readNBytes(GuardianProtocol.PROXY_SESSION_ID_BYTES);
            if (proxySessionId.length != GuardianProtocol.PROXY_SESSION_ID_BYTES) {
                throw new ProtocolException("truncated proxy session id");
            }
            ConnectionOrigin connectionOrigin = switch (in.readUnsignedByte()) {
                case 0 -> ConnectionOrigin.JAVA;
                case 1 -> ConnectionOrigin.BEDROCK;
                default -> throw new ProtocolException("invalid proxy connection origin");
            };
            long issuedAt = in.readLong();
            long expiresAt = in.readLong();
            if (in.available() != 0) {
                throw new ProtocolException("unexpected trailing proxy assertion bytes");
            }
            return new ProxyAdmissionAssertion(
                assertionVersion, playerId, proxySessionId, connectionOrigin, issuedAt, expiresAt);
        } catch (EOFException ex) {
            throw new ProtocolException("truncated proxy assertion", ex);
        } catch (IOException ex) {
            throw new ProtocolException("unable to decode proxy assertion", ex);
        }
    }

    public static byte[] decodeBase64Secret(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("shared secret is missing");
        }
        final byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("shared secret is not valid Base64", ex);
        }
        requireSecret(secret);
        return secret;
    }

    private static byte[] encodeUnsigned(ProxyAdmissionAssertion assertion) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(GuardianProtocol.MAGIC);
                out.writeByte(GuardianProtocol.TYPE_PROXY_ADMISSION);
                out.writeShort(assertion.assertionVersion());
                out.writeLong(assertion.playerId().getMostSignificantBits());
                out.writeLong(assertion.playerId().getLeastSignificantBits());
                out.write(assertion.proxySessionId());
                out.writeByte(switch (assertion.connectionOrigin()) {
                    case JAVA -> 0;
                    case BEDROCK -> 1;
                });
                out.writeLong(assertion.issuedAtEpochMillis());
                out.writeLong(assertion.expiresAtEpochMillis());
            }
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("in-memory proxy assertion encoding failed", ex);
        }
    }

    private static byte[] hmac(byte[] payload, byte[] secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("JVM does not provide " + HMAC_ALGORITHM, ex);
        }
    }

    private static void requireSecret(byte[] secret) {
        if (secret == null || secret.length != GuardianProtocol.PROXY_SECRET_BYTES) {
            throw new IllegalArgumentException(
                "shared secret must be exactly " + GuardianProtocol.PROXY_SECRET_BYTES + " bytes");
        }
    }
}
