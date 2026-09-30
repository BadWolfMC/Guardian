package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.operations.ActiveInspectionSnapshot;
import com.badwolfmc.guardian.core.operations.ActiveInspectionStore;
import com.velocitypowered.api.proxy.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Exact-connection owner for Velocity's current authoritative inspection snapshots.
 *
 * <p>The core store remains UUID-addressed, but every platform operation is serialized with the
 * exact Velocity {@link Player} object that owns that UUID's current snapshot. This prevents a
 * delayed same-UUID disconnect or staff lookup from touching evidence owned by a newer connection.</p>
 */
final class VelocityInspectionService {
    private final ActiveInspectionStore snapshots;
    private final Map<UUID, Player> owners = new HashMap<>();

    VelocityInspectionService() {
        this(new ActiveInspectionStore());
    }

    VelocityInspectionService(ActiveInspectionStore snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    synchronized boolean put(Player owner, ActiveInspectionSnapshot snapshot) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(snapshot, "snapshot");
        UUID playerId = owner.getUniqueId();
        if (!playerId.equals(snapshot.playerId())) {
            throw new IllegalArgumentException("inspection owner UUID does not match snapshot UUID");
        }
        if (!snapshots.put(snapshot)) return false;
        owners.put(playerId, owner);
        return true;
    }

    synchronized Optional<ActiveInspectionSnapshot> get(Player owner) {
        Objects.requireNonNull(owner, "owner");
        UUID playerId = owner.getUniqueId();
        if (owners.get(playerId) != owner) return Optional.empty();
        return snapshots.get(playerId);
    }

    synchronized boolean updateBackend(Player owner, String backend) {
        Optional<ActiveInspectionSnapshot> existing = get(owner);
        if (existing.isEmpty()) return false;
        return snapshots.put(existing.orElseThrow().withBackend(backend));
    }

    synchronized void remove(Player owner) {
        Objects.requireNonNull(owner, "owner");
        UUID playerId = owner.getUniqueId();
        if (!owners.remove(playerId, owner)) return;
        snapshots.remove(playerId);
    }

    synchronized int size() {
        return snapshots.size();
    }

    synchronized void clear() {
        owners.clear();
        snapshots.clear();
    }
}
