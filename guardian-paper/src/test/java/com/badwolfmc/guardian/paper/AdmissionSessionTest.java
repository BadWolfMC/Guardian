package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.protocol.ConnectionOrigin;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.ProxyAdmissionAssertion;
import com.badwolfmc.guardian.protocol.Presence;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdmissionSessionTest {
    @Test
    void onlyOneResponseCanBeClaimedPerChallenge() {
        AdmissionSession session = new AdmissionSession(UUID.randomUUID(), null);

        assertTrue(session.tryMarkResponseReceived());
        assertFalse(session.tryMarkResponseReceived());
    }

    @Test
    void duplicatePresenceMustBeIdentical() {
        AdmissionSession session = new AdmissionSession(UUID.randomUUID(), null);
        Presence presence = new Presence(1, 1, GuardianProtocol.KNOWN_CAPABILITIES, "test");

        assertTrue(session.recordPresence(presence));
        assertTrue(session.recordPresence(presence));
        assertFalse(session.recordPresence(new Presence(99, 99,
            GuardianProtocol.KNOWN_CAPABILITIES, "test")));
    }

    @Test
    void configurationCallbacksMustMatchExactConnectionIdentity() {
        Object connection = new Object();
        AdmissionSession session = new AdmissionSession(UUID.randomUUID(), null, connection);

        assertTrue(session.matchesConfigurationConnection(connection));
        assertFalse(session.matchesConfigurationConnection(new Object()));
    }

    @Test
    void playConnectionCanBeBoundOnlyToOneExactIdentity() {
        AdmissionSession session = new AdmissionSession(UUID.randomUUID(), null, new Object());
        Object firstPlayer = new Object();
        Object secondPlayer = new Object();

        assertTrue(session.bindPlayConnection(firstPlayer));
        assertTrue(session.bindPlayConnection(firstPlayer));
        assertFalse(session.bindPlayConnection(secondPlayer));
        assertTrue(session.matchesPlayConnection(firstPlayer));
        assertFalse(session.matchesPlayConnection(secondPlayer));
    }

    @Test
    void playConnectionIsNotReadyUntilConfigurationExplicitlyHandsOff() {
        AdmissionSession session = new AdmissionSession(UUID.randomUUID(), null, new Object());

        assertFalse(session.readyForPlay());
        session.markReadyForPlay();
        assertTrue(session.readyForPlay());
    }

    @Test
    void timeoutDecisionCannotBeReplacedByLateSuccessfulCompletion() {
        AdmissionSession session = new AdmissionSession(UUID.randomUUID(), null);
        GuardianDecision timeout = GuardianDecision.deny(DecisionReason.CERBERUS_TIMEOUT, "timeout");
        GuardianDecision lateAllow = GuardianDecision.allow(DecisionReason.CERBERUS_VERIFIED, "late allow");

        session.decide(timeout);
        session.decide(lateAllow);

        assertSame(timeout, session.decision());
    }

    @Test
    void proxyAssertionIsAcceptedOnlyOncePerPaperConfigurationSession() {
        AdmissionSession session = new AdmissionSession(UUID.randomUUID(), null);
        ProxyAdmissionAssertion assertion = new ProxyAdmissionAssertion(
            GuardianProtocol.PROXY_ASSERTION_VERSION,
            session.playerId(),
            new byte[GuardianProtocol.PROXY_SESSION_ID_BYTES],
            ConnectionOrigin.JAVA,
            1_000L,
            2_000L
        );

        assertTrue(session.recordProxyAdmission(assertion));
        assertFalse(session.recordProxyAdmission(assertion));
    }

}
