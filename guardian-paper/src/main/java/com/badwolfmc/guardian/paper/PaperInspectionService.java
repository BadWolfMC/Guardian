package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.operations.ActiveInspectionSnapshot;
import com.badwolfmc.guardian.core.operations.ActiveInspectionStore;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Authority-aware in-memory inspection state; deliberately has no historical persistence. */
final class PaperInspectionService {
    private static final int MAX_BACKEND_SNAPSHOTS = ActiveInspectionStore.DEFAULT_MAX_SNAPSHOTS;

    private final ActiveInspectionStore authoritative = new ActiveInspectionStore();
    private final ConcurrentHashMap<UUID, BackendInspectionSnapshot> backend = new ConcurrentHashMap<>();
    private final Map<UUID, Player> owners = new HashMap<>();

    synchronized boolean putAuthoritative(Player owner, ActiveInspectionSnapshot snapshot) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(snapshot, "snapshot");
        UUID playerId = owner.getUniqueId();
        if (!playerId.equals(snapshot.playerId())) {
            throw new IllegalArgumentException("inspection owner UUID does not match authoritative snapshot UUID");
        }
        if (!authoritative.put(snapshot)) return false;
        backend.remove(playerId);
        owners.put(playerId, owner);
        return true;
    }

    synchronized boolean putBackend(Player owner, BackendInspectionSnapshot snapshot) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(snapshot, "snapshot");
        UUID playerId = owner.getUniqueId();
        if (!playerId.equals(snapshot.playerId())) {
            throw new IllegalArgumentException("inspection owner UUID does not match backend snapshot UUID");
        }
        if (!backend.containsKey(playerId) && backend.size() >= MAX_BACKEND_SNAPSHOTS) return false;
        backend.put(playerId, snapshot);
        authoritative.remove(playerId);
        owners.put(playerId, owner);
        return true;
    }

    synchronized Optional<ActiveInspectionSnapshot> authoritative(Player owner) {
        Objects.requireNonNull(owner, "owner");
        UUID playerId = owner.getUniqueId();
        if (owners.get(playerId) != owner) return Optional.empty();
        return authoritative.get(playerId);
    }

    synchronized Optional<BackendInspectionSnapshot> backend(Player owner) {
        Objects.requireNonNull(owner, "owner");
        UUID playerId = owner.getUniqueId();
        if (owners.get(playerId) != owner) return Optional.empty();
        return Optional.ofNullable(backend.get(playerId));
    }

    int authoritativeCount() {
        return authoritative.size();
    }

    int backendCount() {
        return backend.size();
    }

    synchronized void remove(Player owner) {
        Objects.requireNonNull(owner, "owner");
        UUID playerId = owner.getUniqueId();
        if (!owners.remove(playerId, owner)) return;
        authoritative.remove(playerId);
        backend.remove(playerId);
    }

    synchronized void clear() {
        owners.clear();
        authoritative.clear();
        backend.clear();
    }
}
