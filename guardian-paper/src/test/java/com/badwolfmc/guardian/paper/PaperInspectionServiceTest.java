package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.protocol.ConnectionOrigin;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperInspectionServiceTest {
    @Test
    void exactPlayerIdentityPreventsSameUuidReconnectFromSeeingOrDeletingOlderSnapshot() {
        UUID playerId = UUID.randomUUID();
        Player oldConnection = player(playerId);
        Player newConnection = player(playerId);
        PaperInspectionService service = new PaperInspectionService();

        BackendInspectionSnapshot oldSnapshot = snapshot(playerId, "old-session");
        BackendInspectionSnapshot newSnapshot = snapshot(playerId, "new-session");

        assertTrue(service.putBackend(oldConnection, oldSnapshot));
        assertEquals(oldSnapshot, service.backend(oldConnection).orElseThrow());
        assertTrue(service.backend(newConnection).isEmpty(),
            "a newer same-UUID Player must not inherit inspection evidence owned by the older connection");

        service.remove(newConnection);
        assertEquals(oldSnapshot, service.backend(oldConnection).orElseThrow(),
            "a disconnect callback for a non-owner must not delete the owner's snapshot");

        assertTrue(service.putBackend(newConnection, newSnapshot));
        assertTrue(service.backend(oldConnection).isEmpty(),
            "once the newer connection captures evidence the older Player must no longer see it");
        assertEquals(newSnapshot, service.backend(newConnection).orElseThrow());

        service.remove(oldConnection);
        assertEquals(newSnapshot, service.backend(newConnection).orElseThrow(),
            "a delayed old disconnect must not remove the newer connection's snapshot");

        service.remove(newConnection);
        assertTrue(service.backend(newConnection).isEmpty());
    }

    private static BackendInspectionSnapshot snapshot(UUID playerId, String sessionId) {
        return new BackendInspectionSnapshot(
            playerId,
            "Alice",
            "alpha",
            ConnectionOrigin.JAVA,
            sessionId,
            GuardianDecision.allow(DecisionReason.PROXY_ADMISSION_VERIFIED, "ok"),
            BackendInspectionSnapshot.FloodgateSanity.NOT_AVAILABLE
        );
    }

    private static Player player(UUID playerId) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> playerId;
                case "toString" -> "TestPlayer[" + playerId + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        throw new AssertionError("unsupported primitive return type " + type);
    }
}
