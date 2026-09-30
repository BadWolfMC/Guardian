package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperAdmissionSessionsTest {
    @Test
    void sameUuidConnectionsRemainIndependentlyAddressable() {
        UUID playerId = UUID.randomUUID();
        Object firstConnection = new Object();
        Object secondConnection = new Object();
        AdmissionSession first = new AdmissionSession(playerId, null, firstConnection);
        AdmissionSession second = new AdmissionSession(playerId, null, secondConnection);
        PaperAdmissionSessions sessions = new PaperAdmissionSessions();

        sessions.add(first);
        sessions.add(second);

        assertSame(first, sessions.forConfiguration(playerId, firstConnection));
        assertSame(second, sessions.forConfiguration(playerId, secondConnection));
        assertEquals(2, sessions.forPlayer(playerId).size());
    }

    @Test
    void staleConnectionCannotResolveNewerSessionByUuidAlone() {
        UUID playerId = UUID.randomUUID();
        Object currentConnection = new Object();
        AdmissionSession current = new AdmissionSession(playerId, null, currentConnection);
        PaperAdmissionSessions sessions = new PaperAdmissionSessions();
        sessions.add(current);

        assertNull(sessions.forConfiguration(playerId, new Object()));
        assertSame(current, sessions.forConfiguration(playerId, currentConnection));
    }

    @Test
    void exactPlayIdentitySurvivesAnotherSameUuidSession() {
        UUID playerId = UUID.randomUUID();
        AdmissionSession old = new AdmissionSession(playerId, null, new Object());
        AdmissionSession newer = new AdmissionSession(playerId, null, new Object());
        Object oldPlayer = new Object();
        assertTrue(old.bindPlayConnection(oldPlayer));

        PaperAdmissionSessions sessions = new PaperAdmissionSessions();
        sessions.add(old);
        sessions.add(newer);

        assertSame(old, sessions.forPlay(playerId, oldPlayer));
        assertNull(sessions.forPlay(playerId, new Object()));
        assertTrue(sessions.remove(newer));
        assertTrue(sessions.contains(old));
        assertFalse(sessions.contains(newer));
    }
}
