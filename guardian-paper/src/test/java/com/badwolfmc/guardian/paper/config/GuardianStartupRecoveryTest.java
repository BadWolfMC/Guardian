package com.badwolfmc.guardian.paper.config;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class GuardianStartupRecoveryTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-09-24T11:23:56.789Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void backupAndRestoreDefaultPreservesInvalidBytesAndReplacesActiveFile() throws Exception {
        Path config = tempDir.resolve("config.yml");
        byte[] invalid = "broken: [yaml\n".getBytes(StandardCharsets.UTF_8);
        byte[] defaults = "schema-version: 1\n".getBytes(StandardCharsets.UTF_8);
        Files.write(config, invalid);

        GuardianStartupRecovery.RecoveryResult result = new GuardianStartupRecovery(FIXED_CLOCK)
            .backupAndRestoreDefault(config, new ByteArrayInputStream(defaults));

        assertArrayEquals(defaults, Files.readAllBytes(config));
        assertArrayEquals(invalid, Files.readAllBytes(result.backup()));
        assertEquals("config.yml.invalid-20260924T112356789Z.bak", result.backup().getFileName().toString());
        assertTrue(result.defaultRestored());
    }

    @Test
    void emptyInvalidFileCanStillBePreservedAndRecovered() throws Exception {
        Path config = tempDir.resolve("config.yml");
        Files.write(config, new byte[0]);
        byte[] defaults = "schema-version: 1\n".getBytes(StandardCharsets.UTF_8);

        GuardianStartupRecovery.RecoveryResult result = new GuardianStartupRecovery(FIXED_CLOCK)
            .backupAndRestoreDefault(config, new ByteArrayInputStream(defaults));

        assertEquals(0, Files.size(result.backup()));
        assertArrayEquals(defaults, Files.readAllBytes(config));
    }

    @Test
    void collidingTimestampUsesSuffixWithoutOverwritingEarlierBackup() throws Exception {
        Path config = tempDir.resolve("config.yml");
        Files.writeString(config, "first", StandardCharsets.UTF_8);
        GuardianStartupRecovery recovery = new GuardianStartupRecovery(FIXED_CLOCK);

        GuardianStartupRecovery.RecoveryResult first = recovery.backupAndRestoreDefault(
            config, new ByteArrayInputStream("default-one".getBytes(StandardCharsets.UTF_8)));
        Files.writeString(config, "second", StandardCharsets.UTF_8);
        GuardianStartupRecovery.RecoveryResult second = recovery.backupAndRestoreDefault(
            config, new ByteArrayInputStream("default-two".getBytes(StandardCharsets.UTF_8)));

        assertEquals("config.yml.invalid-20260924T112356789Z.bak", first.backup().getFileName().toString());
        assertEquals("config.yml.invalid-20260924T112356789Z-2.bak", second.backup().getFileName().toString());
        assertEquals("first", Files.readString(first.backup(), StandardCharsets.UTF_8));
        assertEquals("second", Files.readString(second.backup(), StandardCharsets.UTF_8));
    }

    @Test
    void recoveryRefusesSymlinkInsteadOfFollowingExternalTarget() throws Exception {
        Path target = tempDir.resolve("external.txt");
        Files.writeString(target, "do-not-copy-or-replace", StandardCharsets.UTF_8);
        Path link = tempDir.resolve("config.yml");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException ex) {
            Assumptions.assumeTrue(false, "symbolic links unavailable in this test environment: " + ex);
        }

        GuardianStartupRecovery recovery = new GuardianStartupRecovery(FIXED_CLOCK);
        assertThrows(java.io.IOException.class, () -> recovery.backupAndRestoreDefault(
            link, new ByteArrayInputStream("schema-version: 1\n".getBytes(StandardCharsets.UTF_8))));

        assertTrue(Files.isSymbolicLink(link));
        assertEquals("do-not-copy-or-replace", Files.readString(target, StandardCharsets.UTF_8));
        try (var stream = Files.list(tempDir)) {
            assertTrue(stream.noneMatch(path -> path.getFileName().toString().contains(".invalid-")));
        }
    }

    @Test
    void backupAndRemoveAllowsOptionalLocaleFallbackWithoutLosingOriginal() throws Exception {
        Path locale = tempDir.resolve("custom.properties");
        Files.writeString(locale, "invalid", StandardCharsets.UTF_8);

        GuardianStartupRecovery.RecoveryResult result =
            new GuardianStartupRecovery(FIXED_CLOCK).backupAndRemove(locale);

        assertFalse(Files.exists(locale));
        assertEquals("invalid", Files.readString(result.backup(), StandardCharsets.UTF_8));
        assertFalse(result.defaultRestored());
    }
}
