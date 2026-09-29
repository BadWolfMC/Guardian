package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Phase5PaperOperationsArchitectureTest {
    @Test
    void paperOwnsGuardianRootWithFinalPermissionNamespace() throws Exception {
        String plugin = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/GuardianPaperPlugin.java"));
        String command = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/GuardianPaperCommand.java"));
        assertTrue(plugin.contains("register(\"guardian\", command)"));
        assertFalse(plugin.contains("register(\"guardianv\", command)"));
        assertTrue(command.contains("guardian.command.status"));
        assertTrue(command.contains("guardian.command.validate"));
        assertTrue(command.contains("guardian.command.reload"));
        assertTrue(command.contains("guardian.command.inspect"));
        assertTrue(command.contains("guardian.command.artifacts.scan"));
        assertFalse(command.contains("guardian.artifacts.scan"));
    }


    @Test
    void paperUsesTopLevelSharedPolicyLayout() throws Exception {
        String plugin = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/GuardianPaperPlugin.java"));
        String runtime = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/config/GuardianRuntimeManager.java"));
        assertTrue(plugin.contains("ensureAdministratorFile(\"policy.yml\")"));
        assertFalse(plugin.contains("ensureAdministratorFile(\"admission/policy.yml\")"));
        assertTrue(runtime.contains("dataDirectory.resolve(\"policy.yml\")"));
        assertFalse(runtime.contains("dataDirectory.resolve(\"admission/policy.yml\")"));
    }

    @Test
    void velocityAuthorityPaperInspectionUsesBackendEvidenceOnly() throws Exception {
        String command = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/GuardianPaperCommand.java"));
        int inspectMethod = command.indexOf("private void inspect(");
        int velocityBranch = command.indexOf("authorityMode() == PaperAuthorityMode.VELOCITY", inspectMethod);
        int standaloneLookup = command.indexOf("inspectionService.authoritative", velocityBranch);
        assertTrue(inspectMethod >= 0 && velocityBranch > inspectMethod && standaloneLookup > velocityBranch);
        String backendBranch = command.substring(velocityBranch, standaloneLookup);
        assertTrue(backendBranch.contains("inspectionService.backend"));
        assertFalse(backendBranch.contains("Manifest"));
        assertFalse(backendBranch.contains("policyAddressableMods"));
    }

    @Test
    void backendArtifactScanRefusesToPretendItOwnsNetworkCatalog() throws Exception {
        String command = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/GuardianPaperCommand.java"));
        assertTrue(command.contains("PaperAuthorityMode.VELOCITY"));
        assertTrue(command.contains("command.artifacts.velocity-owned"));
        assertTrue(command.indexOf("command.artifacts.velocity-owned") < command.indexOf("scanAndMerge()"));
    }

    @Test
    void successfulProxyAssertionsDoNotEmitNormalDuplicateAdmissionSummary() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        assertTrue(adapter.contains("debug(session, \"Trusted Guardian proxy admission received"));
        assertFalse(adapter.contains("getLogger().info(() -> \"Trusted Guardian proxy admission received"));
    }
}
