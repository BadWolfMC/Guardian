package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Presence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityAdmissionSessionTest {
    private static byte[] sessionId() {
        byte[] id = new byte[GuardianProtocol.PROXY_SESSION_ID_BYTES];
        for (int i = 0; i < id.length; i++) {
            id[i] = (byte) i;
        }
        return id;
    }

    @Test
    void duplicatePresenceMustAgreeOnProtocol() {
        VelocityAdmissionSession session = new VelocityAdmissionSession(sessionId(), null);

        assertTrue(session.recordPresence(new Presence(1, 1, GuardianProtocol.KNOWN_CAPABILITIES, "test")));
        assertTrue(session.recordPresence(new Presence(1, 1, GuardianProtocol.KNOWN_CAPABILITIES, "test")));
        assertFalse(session.recordPresence(new Presence(99, 99, GuardianProtocol.KNOWN_CAPABILITIES, "test")));
    }

    @Test
    void onlyOneResponseCanBeClaimedPerChallenge() {
        VelocityAdmissionSession session = new VelocityAdmissionSession(sessionId(), null);

        assertTrue(session.tryMarkResponseReceived());
        assertFalse(session.tryMarkResponseReceived());
    }

    @Test
    void firstDecisionWinsAndCompletesAwaitedFuture() {
        VelocityAdmissionSession session = new VelocityAdmissionSession(sessionId(), null);
        GuardianDecision allowed = GuardianDecision.allow(DecisionReason.CERBERUS_VERIFIED, "ok");
        GuardianDecision denied = GuardianDecision.deny(DecisionReason.MANIFEST_INVALID, "late");

        assertTrue(session.decide(allowed));
        assertFalse(session.decide(denied));
        assertSame(allowed, session.decision());
        assertSame(allowed, session.decisionFuture().join());
        assertTrue(session.admitted());
    }


    @Test
    void timeoutDecisionCannotBeReplacedByLateSuccessfulCompletion() {
        VelocityAdmissionSession session = new VelocityAdmissionSession(sessionId(), null);
        GuardianDecision timeout = GuardianDecision.deny(DecisionReason.CERBERUS_TIMEOUT, "timeout");
        GuardianDecision lateAllow = GuardianDecision.allow(DecisionReason.CERBERUS_VERIFIED, "late allow");

        assertTrue(session.decide(timeout));
        assertFalse(session.decide(lateAllow));
        assertSame(timeout, session.decision());
        assertSame(timeout, session.decisionFuture().join());
        assertFalse(session.admitted());
    }

    @Test
    void proxySessionIdIsDefensivelyCopied() {
        byte[] original = sessionId();
        VelocityAdmissionSession session = new VelocityAdmissionSession(original, null);
        original[0] = 99;

        byte[] returned = session.proxySessionId();
        assertArrayEquals(sessionId(), returned);
        returned[1] = 88;
        assertArrayEquals(sessionId(), session.proxySessionId());
    }
}
