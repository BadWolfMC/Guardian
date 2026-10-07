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
        assertTrue(manager.contains("cerberus-release-signing.pub"));
        assertTrue(manager.contains("Published Cerberus release public key is not byte-identical"));
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
    void releaseVersionValidationIsCentralizedAndSupportsSemVerBuildMetadata() throws Exception {
        String rootBuild = Files.readString(Path.of("../build.gradle"));
        String velocityBuild = Files.readString(Path.of("../guardian-velocity/build.gradle"));
        String manager = Files.readString(Path.of("../tools/release-manager.ps1"));
        String release = Files.readString(Path.of("../.github/workflows/release-candidate.yml"));

        assertTrue(rootBuild.contains("def guardianVersion = rootProject.providers.gradleProperty('guardianVersion').orElse('1.0.2').get()"));
        assertTrue(rootBuild.contains("def semVerPattern"));
        assertTrue(rootBuild.contains("optional prerelease/build metadata"));
        assertTrue(rootBuild.contains("\\+([0-9A-Za-z-]+"), "root Gradle validation must accept SemVer build metadata");
        assertFalse(velocityBuild.contains("Guardian version must match"), "Velocity must use the root version validator");
        assertTrue(manager.contains("$SemVerPattern"));
        assertTrue(manager.contains("(?:\\+([0-9A-Za-z-]+"), "PowerShell validation must accept SemVer build metadata");
        assertTrue(release.contains("./gradlew --no-daemon help \"-PguardianVersion=$RELEASE_VERSION\""));
        assertFalse(release.contains("SemVer-like"), "the workflow must not maintain a third independent version regex");
    }

    @Test
    void windowsCiExercisesActualPowerShellFinalizationWithDisposableKeys() throws Exception {
        String ci = Files.readString(Path.of("../.github/workflows/ci.yml"));
        String release = Files.readString(Path.of("../.github/workflows/release-candidate.yml"));
        String smoke = Files.readString(Path.of("../tools/test-release-workflow.ps1"));

        assertTrue(ci.contains("runs-on: windows-latest"));
        assertTrue(ci.contains("if-no-files-found: warn"));
        assertTrue(release.contains("guardian-${{ inputs.version }}-test-reports"));
        assertTrue(release.contains("if-no-files-found: warn"));
        assertTrue(ci.contains(".\\tools\\test-release-workflow.ps1"));
        assertTrue(smoke.contains("1.0.2-ci.smoke+windows"));
        assertTrue(smoke.contains("-Action generate-release-key"));
        assertTrue(smoke.contains("-Action generate-server-identity"));
        assertTrue(smoke.contains("-Action finalize-release"));
        assertTrue(smoke.contains("-Action verify-release"));
        assertTrue(smoke.contains("PowerShell release-workflow smoke passed with disposable keys."));
        assertFalse(smoke.contains("GuardianSecrets"));
        assertFalse(smoke.contains("C:\\Users\\"), "smoke tooling must not embed an operator-specific Windows path");
        assertFalse(smoke.contains("D:\\"), "smoke tooling must not embed an operator-specific drive path");
    }

    @Test
    void publicCerberusSourceContainsNoDevelopmentRuntimeSwitches() throws Exception {
        String client = Files.readString(Path.of("src/main/java/com/badwolfmc/cerberus/CerberusClient.java"));
        assertFalse(client.contains("guardian.cerberus.dev."));
        assertFalse(client.contains("Boolean.getBoolean("));
        assertFalse(client.contains("Integer.getInteger("));
        assertTrue(client.contains("GuardianProtocol.VERSION"));
    }

    @Test
    void cerberusPublishesModIcon() throws Exception {
        String metadata = Files.readString(Path.of("src/main/resources/fabric.mod.json"));
        assertTrue(metadata.contains("\"icon\": \"assets/cerberus/icon.png\""));
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/cerberus/icon.png")));
    }
}
