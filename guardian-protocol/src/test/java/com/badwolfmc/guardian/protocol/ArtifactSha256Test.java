package com.badwolfmc.guardian.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactSha256Test {
    @TempDir Path temp;

    @Test
    void knownFixtureBytesProduceStableSha256() throws Exception {
        Path file = temp.resolve("fixture.jar");
        Files.writeString(file, "hello");
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            ArtifactSha256.hashRegularFile(file, 1024).hex()
        );
    }

    @Test
    void hashingIsBounded() throws Exception {
        Path file = temp.resolve("large.jar");
        Files.write(file, new byte[33]);
        assertThrows(java.io.IOException.class, () -> ArtifactSha256.hashRegularFile(file, 32));
    }
    @Test
    void symbolicLinkIsNeverFollowedForArtifactHashing() throws Exception {
        Path target = temp.resolve("target.jar");
        Files.writeString(target, "secret-ish bytes");
        Path link = temp.resolve("linked.jar");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException ex) {
            return; // Host does not permit symlink creation.
        }

        java.io.IOException thrown = assertThrows(
            java.io.IOException.class, () -> ArtifactSha256.hashRegularFile(link, 1024));
        assertTrue(thrown.getMessage().contains("regular non-symlink"));
    }

}
