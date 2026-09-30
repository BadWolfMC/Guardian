package com.badwolfmc.guardian.core.operations;

import com.badwolfmc.guardian.core.artifact.ArtifactCatalogException;
import com.badwolfmc.guardian.core.artifact.ArtifactCatalogStore;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyException;
import com.badwolfmc.guardian.core.policy.AdmissionPolicyLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Phase6FilesystemHardeningTest {
    @TempDir
    Path tempDir;

    @Test
    void safeRegularFileEnforcesExactBoundAndStrictUtf8() throws Exception {
        Path exact = tempDir.resolve("exact.txt");
        Files.writeString(exact, "12345678", StandardCharsets.UTF_8);
        assertEquals("12345678", SafeRegularFile.readUtf8(exact, 8));
        assertThrows(IOException.class, () -> SafeRegularFile.readUtf8(exact, 7));

        Path malformed = tempDir.resolve("malformed.txt");
        Files.write(malformed, new byte[]{(byte) 0xC3, (byte) 0x28});
        IOException invalidUtf8 = assertThrows(IOException.class,
            () -> SafeRegularFile.readUtf8(malformed, 8));
        assertTrue(invalidUtf8.getMessage().contains("UTF-8"));
    }

    @Test
    void safeRegularFileRejectsDirectoriesAndSymbolicLinks() throws Exception {
        Path directory = tempDir.resolve("directory");
        Files.createDirectory(directory);
        assertThrows(IOException.class, () -> SafeRegularFile.read(directory, 32));

        Path target = tempDir.resolve("target.txt");
        Files.writeString(target, "data", StandardCharsets.UTF_8);
        Path link = symlinkOrSkip(tempDir.resolve("link.txt"), target.getFileName());
        assertThrows(IOException.class, () -> SafeRegularFile.read(link, 32));
    }


    @Test
    void safeDirectoryCreatesOnlyRealChildrenAndRejectsSymlinkedComponents() throws Exception {
        Path realChild = tempDir.resolve("real/child");
        SafeDirectory.ensureChildDirectories(tempDir, realChild);
        assertTrue(Files.isDirectory(realChild, java.nio.file.LinkOption.NOFOLLOW_LINKS));
        SafeDirectory.requireRealDirectory(realChild);

        Path outside = tempDir.resolveSibling(tempDir.getFileName() + "-outside");
        Files.createDirectories(outside);
        Path link = symlinkOrSkip(tempDir.resolve("linked"), outside);
        assertThrows(IOException.class,
            () -> SafeDirectory.ensureChildDirectories(tempDir, link.resolve("nested")));
        assertThrows(IOException.class, () -> SafeDirectory.requireRealDirectory(link));
    }

    @Test
    void keyResolverRejectsSymlinkedKeyAndFullSecretComparisonDoesNotUseDisplayFingerprint() throws Exception {
        byte[] first = new byte[32];
        byte[] second = new byte[32];
        second[31] = 1;
        ProxyAssertionSecret a = new ProxyAssertionSecret(first, "a");
        ProxyAssertionSecret b = new ProxyAssertionSecret(first, "b");
        ProxyAssertionSecret c = new ProxyAssertionSecret(second, "c");
        assertTrue(a.sameKey(b));
        assertFalse(a.sameKey(c));

        Path real = tempDir.resolve("real.key");
        Files.writeString(real, java.util.Base64.getEncoder().encodeToString(first), StandardCharsets.UTF_8);
        symlinkOrSkip(tempDir.resolve(ProxyAssertionSecretResolver.DEFAULT_KEY_FILE), real.getFileName());
        assertThrows(IllegalArgumentException.class, () -> ProxyAssertionSecretResolver.resolveFile(
            tempDir, ProxyAssertionSecretResolver.DEFAULT_KEY_FILE));
    }

    @Test
    void conventionalPrivateInfrastructureKeysAreIgnoredByGit() throws Exception {
        String gitignore = Files.readString(Path.of("../.gitignore"), StandardCharsets.UTF_8);
        assertTrue(gitignore.lines().anyMatch("proxy-assertion.key"::equals));
        assertTrue(gitignore.lines().anyMatch("guardian-server-auth.key"::equals));
    }

    @Test
    void policyAndArtifactCatalogRejectSymlinkedAdministratorFiles() throws Exception {
        Path policyTarget = tempDir.resolve("policy-target.yml");
        Files.writeString(policyTarget, "schema-version: 1\n", StandardCharsets.UTF_8);
        Path policyLink = symlinkOrSkip(tempDir.resolve("policy.yml"), policyTarget.getFileName());
        AdmissionPolicyException policy = assertThrows(AdmissionPolicyException.class,
            () -> new AdmissionPolicyLoader().load(policyLink, tempDir.resolve("missing-artifacts.yml")));
        assertTrue(policy.getMessage().contains("regular non-symlink"));

        Path catalogTarget = tempDir.resolve("catalog-target.yml");
        Files.writeString(catalogTarget, "schema-version: 1\nartifacts:\n", StandardCharsets.UTF_8);
        Path catalogLink = symlinkOrSkip(tempDir.resolve("artifacts.yml"), catalogTarget.getFileName());
        ArtifactCatalogException catalog = assertThrows(ArtifactCatalogException.class,
            () -> new ArtifactCatalogStore(catalogLink).load());
        assertTrue(catalog.getMessage().contains("regular non-symlink"));
    }

    private static Path symlinkOrSkip(Path link, Path target) throws Exception {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            Assumptions.assumeTrue(false, "symbolic links unavailable in this test environment: " + ex);
            throw ex;
        }
    }
}
