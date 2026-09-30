package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6InspectionHardeningArchitectureTest {
    @Test
    void standaloneInspectionShowsAdmissionGenerationAndBoundedProjection() throws Exception {
        String command = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/GuardianPaperCommand.java"));
        assertTrue(command.contains("sendRuntimeContext(sender, snapshot, runtime.generation())"));
        assertTrue(command.contains("snapshot.policyAddressableCount()"));
        assertTrue(command.contains("snapshot.omittedPolicyAddressableCount()"));
        assertTrue(command.contains("for (InspectionMod entry : snapshot.policyAddressableMods())"));
        assertTrue(command.contains("entry.originKind().name()"));
        String locale = Files.readString(Path.of("../shared-resources/locales/en_us.properties"));
        assertTrue(locale.contains("command.inspect.paper.standalone.mod="));
        assertTrue(locale.contains("<origin>"));
    }

    @Test
    void standaloneSnapshotCapturesAdmissionRuntimeGeneration() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        assertTrue(adapter.contains("session.manifest(), session.bedrockEvidence(), session.snapshot().generation()"));
    }

    @Test
    void backendInspectionStoreIsBoundedLikeAuthoritativeStore() throws Exception {
        String service = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperInspectionService.java"));
        assertTrue(service.contains("MAX_BACKEND_SNAPSHOTS = ActiveInspectionStore.DEFAULT_MAX_SNAPSHOTS"));
        assertTrue(service.contains("backend.size() >= MAX_BACKEND_SNAPSHOTS"));
        assertTrue(service.contains("boolean putBackend"));
    }
}
