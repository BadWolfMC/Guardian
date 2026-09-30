package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.core.operations.SafeRegularFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Preserves invalid administrator files before startup recovery.
 *
 * <p>This helper is intentionally not used by reload. Reload remains strictly
 * parse/validate/activate and keeps the prior runtime snapshot on failure.</p>
 */
public final class GuardianStartupRecovery {
    public static final int MAX_RECOVERY_BACKUP_BYTES = 1024 * 1024;

    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter
        .ofPattern("uuuuMMdd'T'HHmmssSSS'Z'")
        .withZone(ZoneOffset.UTC);

    private final Clock clock;

    public GuardianStartupRecovery() {
        this(Clock.systemUTC());
    }

    GuardianStartupRecovery(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RecoveryResult backupAndRestoreDefault(Path path, InputStream packagedDefault) throws IOException {
        Objects.requireNonNull(packagedDefault, "packagedDefault");
        Path backup = backup(path);

        Path absolute = path.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IOException("cannot determine parent directory for " + path);
        }
        Files.createDirectories(parent);

        Path temporary = Files.createTempFile(parent, ".guardian-recovery-", ".tmp");
        boolean moved = false;
        try {
            Files.copy(packagedDefault, temporary, StandardCopyOption.REPLACE_EXISTING);
            moveReplacing(temporary, absolute);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
        return new RecoveryResult(absolute, backup, true);
    }

    /** Preserve an invalid optional locale and remove it so normal locale fallback can take over. */
    public RecoveryResult backupAndRemove(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path backup = backup(absolute);
        Files.delete(absolute);
        return new RecoveryResult(absolute, backup, false);
    }

    private Path backup(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("cannot back up missing path " + absolute);
        }
        final byte[] original = SafeRegularFile.readAllowEmpty(absolute, MAX_RECOVERY_BACKUP_BYTES);

        String timestamp = BACKUP_TIMESTAMP.format(Instant.now(clock));
        String baseName = absolute.getFileName() + ".invalid-" + timestamp;
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IOException("cannot determine parent directory for " + absolute);
        }

        Path backup = parent.resolve(baseName + ".bak");
        int suffix = 2;
        while (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            backup = parent.resolve(baseName + "-" + suffix++ + ".bak");
        }
        Files.write(backup, original, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return backup;
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record RecoveryResult(Path original, Path backup, boolean defaultRestored) {
    }
}
