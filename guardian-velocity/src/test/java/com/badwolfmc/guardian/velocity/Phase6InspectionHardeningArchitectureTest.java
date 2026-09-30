package com.badwolfmc.guardian.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6InspectionHardeningArchitectureTest {
    @Test
    void authoritativeInspectionShowsAdmissionGenerationAndBoundedProjection() throws Exception {
        String command = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityCommand.java"));
        assertTrue(command.contains("sendRuntimeContext(source, snapshot, runtime.generation())"));
        assertTrue(command.contains("snapshot.policyAddressableCount()"));
        assertTrue(command.contains("snapshot.omittedPolicyAddressableCount()"));
        assertTrue(command.contains("for (InspectionMod entry : snapshot.policyAddressableMods())"));
        assertTrue(command.contains("Placeholder.unparsed(\"origin\", entry.originKind().name())"));
        String locale = Files.readString(Path.of("../shared-resources/locales/en_us.properties"));
        assertTrue(locale.contains("command.inspect.velocity.mod="));
        assertTrue(locale.contains("<origin>"));
    }

    @Test
    void snapshotCapturesAdmissionRuntimeGeneration() throws Exception {
        String plugin = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        assertTrue(plugin.contains("session.runtimeSnapshot().generation()"));
    }

    @Test
    void staleSameUuidDisconnectAndLookupCannotCrossConnectionOwnership() throws Exception {
        String plugin = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        String service = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/VelocityInspectionService.java"));
        String command = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityCommand.java"));

        assertTrue(plugin.contains("VelocityInspectionService inspections"));
        assertTrue(plugin.contains("inspections.remove(player)"));
        assertTrue(service.contains("owners.get(playerId) != owner"));
        assertTrue(service.contains("owners.remove(playerId, owner)"));
        assertTrue(command.contains("inspections.get(target)"));
        assertFalse(command.contains("inspections.put(snapshot)"),
            "staff inspection must not race lifecycle ownership by rewriting stored snapshots");
    }
}
