package com.badwolfmc.guardian.protocol;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProxyAdmissionCodecTest {
    private static final UUID PLAYER = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

    @Test
    void signedAssertionRoundTripsExactly() throws Exception {
        byte[] secret = secret(7);
        ProxyAdmissionAssertion expected = assertion(1_000L, 2_000L);

        ProxyAdmissionAssertion decoded = ProxyAdmissionCodec.decodeAndVerify(
            ProxyAdmissionCodec.encode(expected, secret), secret);

        assertEquals(expected.assertionVersion(), decoded.assertionVersion());
        assertEquals(expected.playerId(), decoded.playerId());
        assertArrayEquals(expected.proxySessionId(), decoded.proxySessionId());
        assertEquals(expected.connectionOrigin(), decoded.connectionOrigin());
        assertEquals(expected.issuedAtEpochMillis(), decoded.issuedAtEpochMillis());
        assertEquals(expected.expiresAtEpochMillis(), decoded.expiresAtEpochMillis());
    }

    @Test
    void tamperingOrWrongKeyFailsHmacVerification() {
        byte[] encoded = ProxyAdmissionCodec.encode(assertion(1_000L, 2_000L), secret(7));
        byte[] tampered = encoded.clone();
        tampered[8] ^= 1;

        assertThrows(ProtocolException.class,
            () -> ProxyAdmissionCodec.decodeAndVerify(tampered, secret(7)));
        assertThrows(ProtocolException.class,
            () -> ProxyAdmissionCodec.decodeAndVerify(encoded, secret(8)));
    }

    @Test
    void truncationAndTrailingUnsignedDataCannotBeAccepted() {
        byte[] encoded = ProxyAdmissionCodec.encode(assertion(1_000L, 2_000L), secret(7));
        assertThrows(ProtocolException.class,
            () -> ProxyAdmissionCodec.decodeAndVerify(
                Arrays.copyOf(encoded, GuardianProtocol.PROXY_HMAC_BYTES), secret(7)));

        byte[] expanded = Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(ProtocolException.class,
            () -> ProxyAdmissionCodec.decodeAndVerify(expanded, secret(7)));
    }

    private static ProxyAdmissionAssertion assertion(long issuedAt, long expiresAt) {
        byte[] proxySession = new byte[GuardianProtocol.PROXY_SESSION_ID_BYTES];
        Arrays.fill(proxySession, (byte) 3);
        return new ProxyAdmissionAssertion(
            GuardianProtocol.PROXY_ASSERTION_VERSION,
            PLAYER,
            proxySession,
            ConnectionOrigin.JAVA,
            issuedAt,
            expiresAt
        );
    }

    private static byte[] secret(int seed) {
        byte[] secret = new byte[GuardianProtocol.PROXY_SECRET_BYTES];
        Arrays.fill(secret, (byte) seed);
        return secret;
    }
}
