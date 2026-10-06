package com.badwolfmc.cerberus;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase75ReleaseWorkflowArchitectureTest {
    @Test
    void releaseCandidateProducesChecksummedOfflineSigningInput() throws Exception {
        String workflow = Files.readString(Path.of("../.github/workflows/release-candidate.yml"));
        assertTrue(workflow.contains("cerberus-fabric-$RELEASE_VERSION-unsigned.jar"));
        assertTrue(workflow.contains("cerberus-release-tools-$RELEASE_VERSION.jar"));
        assertTrue(workflow.contains("RELEASE_INPUT.json"));
        assertTrue(workflow.contains("SHA256SUMS-CI.txt"));
        assertTrue(workflow.contains("$GITHUB_SHA"));
        assertTrue(workflow.contains("$GITHUB_RUN_ID"));
        assertTrue(workflow.contains("$GITHUB_RUN_ATTEMPT"));
        assertTrue(workflow.contains("$GITHUB_REPOSITORY"));
        assertTrue(workflow.contains("permissions:\n  contents: read"));
        assertFalse(workflow.contains("cerberus-release-signing.key"));
    }

    @Test
    void localReleaseManagerFinalizesWithoutCommittingOrRebuildingUnsignedCerberus() throws Exception {
        String manager = Files.readString(Path.of("../tools/release-manager.ps1"));
        String build = Files.readString(Path.of("build.gradle"));
        assertTrue(manager.contains("'finalize-release'"));
        assertTrue(manager.contains("'verify-release'"));
        assertTrue(manager.contains("UnsignedCerberusJar"));
        assertTrue(manager.contains("ReleasePublicKey"));
        assertTrue(manager.contains("RELEASE_PROVENANCE.txt"));
        assertTrue(manager.contains("ReleaseToolJar"));
        assertTrue(manager.contains("CerberusReleaseSigner"));
        assertTrue(manager.contains("CerberusReleaseVerifier"));
        assertTrue(manager.contains("release_tool_sha256"));
        assertTrue(manager.contains("release_input_checksums_sha256"));
        assertTrue(manager.contains("release_public_key_sha256"));
        assertTrue(manager.contains("server_auth_trust_sha256"));
        assertTrue(manager.contains("READY TO UPLOAD"));
        assertTrue(manager.contains("Machine-local build path marker"));
        assertTrue(build.contains("-PcerberusUnsignedJar") || build.contains("cerberusUnsignedJar"));
        assertTrue(build.contains("releaseToolJar"));
        assertTrue(build.contains("LICENSE-GPL-3.0.txt"));
        assertTrue(build.contains("THIRD-PARTY-NOTICES.md"));
        assertTrue(manager.contains("Final Paper artifact is not byte-identical to the checked CI input."));
        assertTrue(manager.contains("Final Velocity artifact is not byte-identical to the checked CI input."));
        assertFalse(build.contains("dependsOn tasks.named('jar'), tasks.named('releaseToolClasses')"));
    }

    @Test
    void releaseManagerAvoidsAmbiguousPowerShellVariableColonInterpolation() throws Exception {
        String manager = Files.readString(Path.of("../tools/release-manager.ps1"));
        Pattern ambiguous = Pattern.compile("\"[^\"\r\n]*\\$[A-Za-z_][A-Za-z0-9_]*:[^\"\r\n]*\"");
        assertFalse(ambiguous.matcher(manager).find(),
            "double-quoted PowerShell strings must use ${name}: when a variable is followed by a colon");
    }

    @Test
    void githubActionsArePinnedToImmutableCommits() throws Exception {
        String ci = Files.readString(Path.of("../.github/workflows/ci.yml"));
        String release = Files.readString(Path.of("../.github/workflows/release-candidate.yml"));
        for (String workflow : new String[] {ci, release}) {
            assertActionPinned(workflow, "actions/checkout");
            assertActionPinned(workflow, "actions/setup-java");
            assertActionPinned(workflow, "gradle/actions/wrapper-validation");
            assertActionPinned(workflow, "actions/upload-artifact");
            assertTrue(workflow.contains("persist-credentials: false"));
        }
    }

    private static void assertActionPinned(String workflow, String action) {
        Pattern immutableUse = Pattern.compile(
            "(?m)^\\s*(?:-\\s*)?uses:\\s+" + Pattern.quote(action) + "@[0-9a-f]{40}(?:\\s+#.*)?$"
        );
        assertTrue(immutableUse.matcher(workflow).find(), action + " must be pinned to a full commit SHA");
        assertFalse(workflow.contains(action + "@v"), action + " must not use a mutable version tag");
    }

    @Test
    void productionStartupKeepsDiagnosticsGatedWithoutAdvertisingThem() throws Exception {
        String client = Files.readString(Path.of("src/main/java/com/badwolfmc/cerberus/CerberusClient.java"));
        assertTrue(client.contains("guardian.cerberus.dev.suppressResponse"));
        assertTrue(client.contains("guardian.cerberus.dev.malformedResponse"));
        assertTrue(client.contains("guardian.cerberus.dev.logManifest"));
        assertFalse(client.contains("Diagnostic switches use guardian.cerberus.dev.*"));
    }

    @Test
    void cerberusPublishesModIcon() throws Exception {
        String metadata = Files.readString(Path.of("src/main/resources/fabric.mod.json"));
        assertTrue(metadata.contains("\"icon\": \"assets/cerberus/icon.png\""));
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/cerberus/icon.png")));
    }
}
