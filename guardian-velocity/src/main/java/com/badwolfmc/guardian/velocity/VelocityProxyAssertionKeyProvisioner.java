package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.operations.ProxyAssertionSecretResolver;
import com.badwolfmc.guardian.protocol.GuardianProtocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/** Generates the shared proxy assertion key on Guardian-Velocity only. */
final class VelocityProxyAssertionKeyProvisioner {
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE
    );

    private final SecureRandom random;

    VelocityProxyAssertionKeyProvisioner() {
        this(new SecureRandom());
    }

    VelocityProxyAssertionKeyProvisioner(SecureRandom random) {
        this.random = random;
    }

    boolean ensureGenerated(Path dataDirectory) throws IOException {
        Path data = dataDirectory.toAbsolutePath().normalize();
        Files.createDirectories(data);
        Path key = data.resolve(ProxyAssertionSecretResolver.DEFAULT_KEY_FILE).normalize();
        if (!key.startsWith(data)) {
            throw new IOException("Guardian proxy assertion key path escaped the plugin data directory");
        }
        if (Files.exists(key, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(key, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Guardian proxy assertion key path is not a regular file: " + key);
            }
            return false;
        }

        byte[] secret = new byte[GuardianProtocol.PROXY_SECRET_BYTES];
        random.nextBytes(secret);
        String encoded = Base64.getEncoder().encodeToString(secret) + System.lineSeparator();
        try {
            Files.writeString(key, encoded, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ex) {
            if (!Files.isRegularFile(key, LinkOption.NOFOLLOW_LINKS)) throw ex;
            return false;
        } finally {
            java.util.Arrays.fill(secret, (byte) 0);
        }

        try {
            Files.setPosixFilePermissions(key, OWNER_ONLY);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and other non-POSIX filesystems rely on their inherited ACLs.
        }
        return true;
    }
}
