package com.badwolfmc.guardian.core.operations;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory-only active inspection storage. Historical persistence is intentionally out of scope. */
public final class ActiveInspectionStore {
    public static final int DEFAULT_MAX_SNAPSHOTS = 2_048;

    private final int maxSnapshots;
    private final ConcurrentHashMap<UUID, ActiveInspectionSnapshot> snapshots = new ConcurrentHashMap<>();

    public ActiveInspectionStore() {
        this(DEFAULT_MAX_SNAPSHOTS);
    }

    public ActiveInspectionStore(int maxSnapshots) {
        if (maxSnapshots < 1) throw new IllegalArgumentException("maxSnapshots must be positive");
        this.maxSnapshots = maxSnapshots;
    }

    public synchronized boolean put(ActiveInspectionSnapshot snapshot) {
        UUID playerId = snapshot.playerId();
        if (!snapshots.containsKey(playerId) && snapshots.size() >= maxSnapshots) return false;
        snapshots.put(playerId, snapshot);
        return true;
    }

    public Optional<ActiveInspectionSnapshot> get(UUID playerId) {
        return Optional.ofNullable(snapshots.get(playerId));
    }

    public synchronized void remove(UUID playerId) {
        snapshots.remove(playerId);
    }

    public int size() {
        return snapshots.size();
    }

    public synchronized void clear() {
        snapshots.clear();
    }
}
