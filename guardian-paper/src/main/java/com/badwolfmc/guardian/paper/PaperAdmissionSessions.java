package com.badwolfmc.guardian.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Exact-connection registry for Paper Admission sessions.
 *
 * <p>A UUID is not a connection identity. During a disconnect/reconnect race, callbacks from an
 * older connection may overlap the newer connection for the same authenticated UUID. Keep the
 * short-lived sessions independently addressable so stale work can never mutate or release the
 * newer session merely because the UUID matches.</p>
 */
final class PaperAdmissionSessions {
    private final Map<UUID, Set<AdmissionSession>> byPlayer = new HashMap<>();

    synchronized void add(AdmissionSession session) {
        byPlayer.computeIfAbsent(session.playerId(), ignored -> new HashSet<>()).add(session);
    }

    synchronized AdmissionSession forConfiguration(UUID playerId, Object connectionIdentity) {
        for (AdmissionSession session : sessionsFor(playerId)) {
            if (session.matchesConfigurationConnection(connectionIdentity)) return session;
        }
        return null;
    }

    synchronized AdmissionSession forPlay(UUID playerId, Object connectionIdentity) {
        for (AdmissionSession session : sessionsFor(playerId)) {
            if (session.matchesPlayConnection(connectionIdentity)) return session;
        }
        return null;
    }

    synchronized List<AdmissionSession> forPlayer(UUID playerId) {
        return List.copyOf(sessionsFor(playerId));
    }

    synchronized List<AdmissionSession> all() {
        List<AdmissionSession> result = new ArrayList<>();
        byPlayer.values().forEach(result::addAll);
        return List.copyOf(result);
    }

    synchronized boolean contains(AdmissionSession session) {
        return sessionsFor(session.playerId()).contains(session);
    }

    synchronized boolean remove(AdmissionSession session) {
        Set<AdmissionSession> sessions = byPlayer.get(session.playerId());
        if (sessions == null || !sessions.remove(session)) return false;
        if (sessions.isEmpty()) byPlayer.remove(session.playerId());
        return true;
    }

    synchronized void clear() {
        byPlayer.clear();
    }

    private Set<AdmissionSession> sessionsFor(UUID playerId) {
        Set<AdmissionSession> sessions = byPlayer.get(playerId);
        return sessions == null ? Set.of() : sessions;
    }
}
