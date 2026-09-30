package com.badwolfmc.guardian.velocity;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Concurrent registry keyed by the exact connection object rather than {@link Object#equals(Object)}.
 *
 * <p>Velocity Admission state is connection-scoped. UUID/equality keyed maps allow a delayed event
 * from an older same-player connection to collide with state owned by a newer connection. This
 * registry deliberately uses reference identity so both lifecycles remain isolated.</p>
 */
final class ConnectionIdentityRegistry<T> {
    private final ConcurrentHashMap<IdentityKey, T> values = new ConcurrentHashMap<>();

    T get(Object connection) {
        return values.get(IdentityKey.of(connection));
    }

    boolean contains(Object connection) {
        return values.containsKey(IdentityKey.of(connection));
    }

    T computeIfAbsent(Object connection, Function<Object, T> factory) {
        Objects.requireNonNull(factory, "factory");
        Object owner = Objects.requireNonNull(connection, "connection");
        return values.computeIfAbsent(IdentityKey.of(owner), ignored ->
            Objects.requireNonNull(factory.apply(owner), "factory result"));
    }

    void put(Object connection, T value) {
        values.put(IdentityKey.of(connection), Objects.requireNonNull(value, "value"));
    }

    boolean remove(Object connection) {
        return values.remove(IdentityKey.of(connection)) != null;
    }

    boolean remove(Object connection, T value) {
        return values.remove(IdentityKey.of(connection), Objects.requireNonNull(value, "value"));
    }

    int size() {
        return values.size();
    }

    void clear() {
        values.clear();
    }

    private static final class IdentityKey {
        private final Object reference;
        private final int hash;

        private IdentityKey(Object reference) {
            this.reference = Objects.requireNonNull(reference, "reference");
            this.hash = System.identityHashCode(reference);
        }

        static IdentityKey of(Object reference) {
            return new IdentityKey(reference);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IdentityKey key && reference == key.reference;
        }
    }
}
