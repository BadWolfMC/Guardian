package com.badwolfmc.guardian.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Phase5OperationsArchitectureTest {
    @Test
    void velocityOwnsDistinctGuardianVCommandAndFinalPermissionNamespace() throws Exception {
        String plugin = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        String command = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityCommand.java"));
        assertTrue(plugin.contains("metaBuilder(\"guardianv\")"));
        assertFalse(plugin.contains("metaBuilder(\"guardian\")"));
        assertTrue(command.contains("guardian.velocity.command.status"));
        assertTrue(command.contains("guardian.velocity.command.validate"));
        assertTrue(command.contains("guardian.velocity.command.reload"));
        assertTrue(command.contains("guardian.velocity.command.inspect"));
        assertTrue(command.contains("guardian.velocity.command.artifacts.scan"));
        assertTrue(command.contains("public boolean hasPermission(Invocation invocation)"));
        assertTrue(command.contains("return true;"),
            "proxy root must stay owned at Velocity so permission failure cannot forward /guardianv to Paper");
    }

    @Test
    void productionSourceRemovesPhase0BNamesAndLegacySecretContract() throws Exception {
        String plugin = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        String paper = Files.readString(Path.of(
            "../guardian-paper/src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        assertFalse(plugin.contains("Phase 0B"));
        assertFalse(plugin.contains("PHASE0B"));
        assertFalse(plugin.contains("GUARDIAN_PHASE0B_PROXY_SECRET"));
        assertFalse(paper.contains("GUARDIAN_PHASE0B_PROXY_SECRET"));
        assertTrue(plugin.contains("GUARDIAN") || Files.exists(Path.of("src/main/resources/config.yml")));
    }

    @Test
    void authoritativeInspectionStaysAtProxyAndIsRemovedOnDisconnect() throws Exception {
        String plugin = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        assertTrue(plugin.contains("ActiveInspectionStore"));
        assertTrue(plugin.contains("captureInspection"));
        assertTrue(plugin.contains("inspections.remove(playerId)"));
        assertTrue(plugin.contains("sessions.get(player.getUniqueId()) != session"),
            "asynchronous Admission completion must not resurrect state after disconnect/session replacement");
        int start = plugin.indexOf("private boolean sendProxyAdmission");
        int end = plugin.indexOf("private void captureInspection", start);
        assertTrue(start >= 0 && end > start);
        String assertionMethod = plugin.substring(start, end);
        assertFalse(assertionMethod.contains("session.manifest"),
            "trusted backend assertion must not carry the Fabric manifest");
    }

    @Test
    void velocityRuntimeUsesCompleteAtomicCandidateAndFilesOnlyValidation() throws Exception {
        String runtime = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/VelocityRuntimeManager.java"));
        assertTrue(runtime.contains("VelocityOperationalSettings settings = configLoader.load(configPath)"));
        assertTrue(runtime.contains("policyLoader.load(policyPath, artifactCatalogPath)"));
        assertTrue(runtime.contains("VelocityMessages.load(localesDirectory, settings.locale())"));
        assertTrue(runtime.contains("ArtifactCatalog catalogBefore = loadArtifactCatalog()"));
        assertTrue(runtime.contains("if (!catalogBefore.equals(artifactCatalog))"),
            "candidate must reject a catalog that changes concurrently with policy validation");
        assertTrue(runtime.contains("VelocityRuntimeSnapshot candidate = loadCandidate();"));
        assertTrue(runtime.contains("active.set(candidate);"));
        assertTrue(runtime.contains("validateFiles()"));
    }
}
