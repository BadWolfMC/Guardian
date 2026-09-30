package com.badwolfmc.guardian.velocity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConnectionIdentityRegistryTest {
    @Test
    void equalButDistinctConnectionObjectsNeverShareState() {
        ConnectionIdentityRegistry<String> registry = new ConnectionIdentityRegistry<>();
        String first = new String("same-player");
        String second = new String("same-player");

        registry.put(first, "old");
        registry.put(second, "new");

        assertEquals(first, second, "fixture must be equality-equal");
        assertFalse(first == second, "fixture must be reference-distinct");
        assertEquals("old", registry.get(first));
        assertEquals("new", registry.get(second));
        assertEquals(2, registry.size());
    }

    @Test
    void delayedOldDisconnectCannotRemoveNewConnectionState() {
        ConnectionIdentityRegistry<Object> registry = new ConnectionIdentityRegistry<>();
        Object oldConnection = new Object();
        Object newConnection = new Object();
        Object oldState = new Object();
        Object newState = new Object();

        registry.put(oldConnection, oldState);
        registry.put(newConnection, newState);

        assertTrue(registry.remove(oldConnection));
        assertNull(registry.get(oldConnection));
        assertSame(newState, registry.get(newConnection));
        assertEquals(1, registry.size());
    }

    @Test
    void staleCompletionCannotCompareAndRemoveAnotherValue() {
        ConnectionIdentityRegistry<Object> registry = new ConnectionIdentityRegistry<>();
        Object connection = new Object();
        Object current = new Object();
        Object stale = new Object();

        registry.put(connection, current);

        assertFalse(registry.remove(connection, stale));
        assertSame(current, registry.get(connection));
        assertTrue(registry.remove(connection, current));
        assertNull(registry.get(connection));
    }

    @Test
    void computeIfAbsentIsConnectionIdentityScoped() {
        ConnectionIdentityRegistry<Object> registry = new ConnectionIdentityRegistry<>();
        Object first = new Object();
        Object second = new Object();
        Object firstState = registry.computeIfAbsent(first, ignored -> new Object());

        assertSame(firstState, registry.computeIfAbsent(first, ignored -> new Object()));
        Object secondState = registry.computeIfAbsent(second, ignored -> new Object());
        assertFalse(firstState == secondState);
    }
}
