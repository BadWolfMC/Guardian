package com.badwolfmc.guardian.core.operations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** No-follow helpers for Guardian-owned child directories below an already selected data root. */
public final class SafeDirectory {
    private SafeDirectory() {}

    /** Requires one existing directory object itself to be a real directory rather than a symlink. */
    public static void requireRealDirectory(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        if (Files.isSymbolicLink(directory)
            || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("expected a real directory, not a symlink or other file type: " + directory);
        }
    }

    /**
     * Creates missing child directories one component at a time without following an existing
     * symlink beneath {@code trustedBase}. The base itself is a deployment-owned boundary and is
     * intentionally not rejected merely because an administrator mounted/symlinked that root.
     */
    public static void ensureChildDirectories(Path trustedBase, Path directory) throws IOException {
        Objects.requireNonNull(trustedBase, "trustedBase");
        Objects.requireNonNull(directory, "directory");
        Path base = trustedBase.toAbsolutePath().normalize();
        Path target = directory.toAbsolutePath().normalize();
        if (!target.startsWith(base)) {
            throw new IOException("child directory escapes Guardian data root: " + target);
        }
        if (target.equals(base)) return;
        if (!Files.isDirectory(base)) {
            throw new IOException("Guardian data root does not exist or is not a directory: " + base);
        }

        Path current = base;
        for (Path part : base.relativize(target)) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                requireRealDirectory(current);
            } else {
                Files.createDirectory(current);
                requireRealDirectory(current);
            }
        }
    }
}
