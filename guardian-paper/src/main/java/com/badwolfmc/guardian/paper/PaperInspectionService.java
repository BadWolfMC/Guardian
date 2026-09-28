package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.operations.ActiveInspectionSnapshot;
import com.badwolfmc.guardian.core.operations.ActiveInspectionStore;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Authority-aware in-memory inspection state; deliberately has no historical persistence. */
final class PaperInspectionService {
    private final ActiveInspectionStore authoritative = new ActiveInspectionStore();
    private final ConcurrentHashMap<UUID, BackendInspectionSnapshot> backend = new ConcurrentHashMap<>();

    boolean putAuthoritative(ActiveInspectionSnapshot snapshot) {
        backend.remove(snapshot.playerId());
        return authoritative.put(snapshot);
    }

    void putBackend(BackendInspectionSnapshot snapshot) {
        authoritative.remove(snapshot.playerId());
        backend.put(snapshot.playerId(), snapshot);
    }

    Optional<ActiveInspectionSnapshot> authoritative(UUID playerId) {
        return authoritative.get(playerId);
    }

    Optional<BackendInspectionSnapshot> backend(UUID playerId) {
        return Optional.ofNullable(backend.get(playerId));
    }

    int authoritativeCount() {
        return authoritative.size();
    }

    int backendCount() {
        return backend.size();
    }

    void remove(UUID playerId) {
        authoritative.remove(playerId);
        backend.remove(playerId);
    }

    void clear() {
        authoritative.clear();
        backend.clear();
    }
}
