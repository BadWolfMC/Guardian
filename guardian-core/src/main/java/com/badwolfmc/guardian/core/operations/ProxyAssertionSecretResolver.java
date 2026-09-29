package com.badwolfmc.guardian.core.operations;

import com.badwolfmc.guardian.protocol.ProxyAdmissionCodec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Resolves and validates the shared Guardian-Velocity -> Guardian-Paper assertion key file. */
public final class ProxyAssertionSecretResolver {
    public static final String DEFAULT_KEY_FILE = "proxy-assertion.key";
    public static final int MAX_SECRET_FILE_BYTES = 512;

    private ProxyAssertionSecretResolver() {
    }

    public static ProxyAssertionSecret resolveFile(Path dataDirectory, String relativeFile) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        String normalized = requireToken(relativeFile, "proxy assertion key file");
        Path configured = Path.of(normalized);
        if (configured.isAbsolute()) {
            throw new IllegalArgumentException("proxy assertion key file must be relative to the Guardian data directory");
        }
        Path base = dataDirectory.toAbsolutePath().normalize();
        Path path = base.resolve(configured).normalize();
        if (!path.startsWith(base)) {
            throw new IllegalArgumentException("proxy assertion key file escapes the Guardian data directory");
        }
        rejectSymlinkComponents(base, configured.normalize());
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("proxy assertion key file is missing: " + configured);
        }
        try {
            long size = Files.size(path);
            if (size <= 0 || size > MAX_SECRET_FILE_BYTES) {
                throw new IllegalArgumentException(
                    "proxy assertion key file must contain at most " + MAX_SECRET_FILE_BYTES + " bytes");
            }
            final byte[] bytes;
            try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(MAX_SECRET_FILE_BYTES + 1);
            }
            if (bytes.length == 0 || bytes.length > MAX_SECRET_FILE_BYTES) {
                throw new IllegalArgumentException(
                    "proxy assertion key file must contain at most " + MAX_SECRET_FILE_BYTES + " bytes");
            }
            String raw = new String(bytes, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                throw new IllegalArgumentException("proxy assertion key file is empty");
            }
            return decode(raw, "file:" + configured.toString().replace('\\', '/'));
        } catch (IOException ex) {
            throw new IllegalArgumentException("could not read proxy assertion key file: " + configured, ex);
        }
    }

    private static void rejectSymlinkComponents(Path base, Path relative) {
        Path current = base;
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(
                    "proxy assertion key path must not contain symbolic links: " + relative);
            }
        }
    }

    private static ProxyAssertionSecret decode(String raw, String sourceDescription) {
        try {
            return new ProxyAssertionSecret(ProxyAdmissionCodec.decodeBase64Secret(raw), sourceDescription);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                "proxy assertion key from " + sourceDescription
                    + " must be Base64 for exactly 32 bytes: " + ex.getMessage(), ex);
        }
    }

    private static String requireToken(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be configured");
        }
        return value.trim();
    }
}
