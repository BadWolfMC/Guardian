package com.badwolfmc.guardian.core;

import com.badwolfmc.guardian.protocol.ConnectionOrigin;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.ProxyAdmissionAssertion;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyAdmissionValidatorTest {
    private static final UUID PLAYER = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

    @Test
    void expiryBoundaryIsStrict() {
        long now = 50_000L;
        assertEquals(DecisionReason.PROXY_ADMISSION_VERIFIED,
            ProxyAdmissionValidator.validate(assertion(now - 1_000L, now + 1L), PLAYER, now).reason());
        assertEquals(DecisionReason.PROXY_ASSERTION_INVALID,
            ProxyAdmissionValidator.validate(assertion(now - 1_000L, now), PLAYER, now).reason());
    }

    @Test
    void maximumLifetimeBoundaryIsExact() {
        long issued = 50_000L;
        long now = issued + 1L;
        assertEquals(DecisionReason.PROXY_ADMISSION_VERIFIED,
            ProxyAdmissionValidator.validate(
                assertion(issued, issued + GuardianProtocol.PROXY_ASSERTION_TTL_MILLIS), PLAYER, now).reason());
        assertEquals(DecisionReason.PROXY_ASSERTION_INVALID,
            ProxyAdmissionValidator.validate(
                assertion(issued, issued + GuardianProtocol.PROXY_ASSERTION_TTL_MILLIS + 1L), PLAYER, now).reason());
    }

    @Test
    void futureIssuanceHonorsOnlyConfiguredClockSkew() {
        long now = 50_000L;
        long exactBoundary = now + GuardianProtocol.PROXY_ASSERTION_CLOCK_SKEW_MILLIS;
        assertEquals(DecisionReason.PROXY_ADMISSION_VERIFIED,
            ProxyAdmissionValidator.validate(assertion(exactBoundary, exactBoundary + 1_000L), PLAYER, now).reason());
        assertEquals(DecisionReason.PROXY_ASSERTION_INVALID,
            ProxyAdmissionValidator.validate(assertion(exactBoundary + 1L, exactBoundary + 1_001L), PLAYER, now).reason());
    }

    @Test
    void uuidBindingRemainsMandatory() {
        long now = 50_000L;
        assertEquals(DecisionReason.PROXY_ASSERTION_INVALID,
            ProxyAdmissionValidator.validate(assertion(now - 1_000L, now + 1_000L), UUID.randomUUID(), now).reason());
    }


    @Test
    void clockSkewBoundaryOverflowFailsClosed() {
        long now = Long.MAX_VALUE - GuardianProtocol.PROXY_ASSERTION_CLOCK_SKEW_MILLIS + 1L;
        long issued = now - 10L;
        long expires = Long.MAX_VALUE;
        assertEquals(DecisionReason.PROXY_ASSERTION_INVALID,
            ProxyAdmissionValidator.validate(assertion(issued, expires), PLAYER, now).reason());
    }

    @Test
    void extremeTimestampArithmeticCannotWrapIntoAValidLifetime() {
        ProxyAdmissionAssertion overflow = assertion(Long.MIN_VALUE + 10L, Long.MAX_VALUE - 10L);
        assertEquals(DecisionReason.PROXY_ASSERTION_INVALID,
            ProxyAdmissionValidator.validate(overflow, PLAYER, 50_000L).reason());
    }

    private static ProxyAdmissionAssertion assertion(long issuedAt, long expiresAt) {
        return new ProxyAdmissionAssertion(
            GuardianProtocol.PROXY_ASSERTION_VERSION,
            PLAYER,
            new byte[GuardianProtocol.PROXY_SESSION_ID_BYTES],
            ConnectionOrigin.JAVA,
            issuedAt,
            expiresAt
        );
    }
}
