package com.badwolfmc.guardian.core.operations;

import com.badwolfmc.guardian.protocol.GuardianChallengeCrypto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Safe loader for the long-lived Guardian server-authentication Ed25519 private key. */
public final class GuardianServerChallengeKeyResolver {
    public static final String DEFAULT_KEY_FILE = "guardian-server-auth.key";
    public static final int MAX_PRIVATE_KEY_FILE_BYTES = 16 * 1024;

    private GuardianServerChallengeKeyResolver() {}

    public static GuardianServerChallengeSigner resolveFile(Path dataDirectory) {
        return resolveFile(dataDirectory, DEFAULT_KEY_FILE, Clock.systemUTC());
    }

    static GuardianServerChallengeSigner resolveFile(Path dataDirectory, String relativeFile, Clock clock) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(clock, "clock");
        String token = requireToken(relativeFile, "Guardian server authentication key file");
        Path configured = Path.of(token);
        if (configured.isAbsolute()) {
            throw new IllegalArgumentException("Guardian server authentication key file must be relative to the Guardian data directory");
        }
        Path base = dataDirectory.toAbsolutePath().normalize();
        Path path = base.resolve(configured).normalize();
        if (!path.startsWith(base)) {
            throw new IllegalArgumentException("Guardian server authentication key file escapes the Guardian data directory");
        }
        rejectSymlinkComponents(base, configured.normalize());
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Guardian server authentication key file is missing: " + configured);
        }

        byte[] fileBytes;
        try {
            fileBytes = SafeRegularFile.read(path, MAX_PRIVATE_KEY_FILE_BYTES);
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                "Guardian server authentication key must be a stable regular non-symlink file: "
                    + configured + ": " + ex.getMessage(), ex);
        }
        if (fileBytes.length == 0) {
            throw new IllegalArgumentException("Guardian server authentication key file is empty");
        }

        byte[] der = decodePemIfNeeded(fileBytes);
        try {
            PrivateKey privateKey = KeyFactory.getInstance(GuardianChallengeCrypto.ALGORITHM)
                .generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!GuardianChallengeCrypto.isEd25519KeyAlgorithm(privateKey.getAlgorithm())) {
                throw new GeneralSecurityException("decoded private key is not Ed25519");
            }
            return new GuardianServerChallengeSigner(privateKey, clock);
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException(
                "Guardian server authentication key must be Ed25519 PKCS#8 DER or PEM PRIVATE KEY: "
                    + ex.getMessage(), ex);
        } finally {
            Arrays.fill(fileBytes, (byte) 0);
            if (der != fileBytes) Arrays.fill(der, (byte) 0);
        }
    }

    private static byte[] decodePemIfNeeded(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.US_ASCII).trim();
        if (!text.startsWith("-----BEGIN PRIVATE KEY-----")) return bytes;
        if (!text.endsWith("-----END PRIVATE KEY-----")) {
            throw new IllegalArgumentException("Guardian server authentication PEM key is missing END PRIVATE KEY");
        }
        String base64 = text
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Guardian server authentication PEM body is not valid Base64", ex);
        }
    }

    private static void rejectSymlinkComponents(Path base, Path relative) {
        Path current = base;
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(
                    "Guardian server authentication key path must not contain symbolic links: " + relative);
            }
        }
    }

    private static String requireToken(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must be configured");
        return value.trim();
    }
}
