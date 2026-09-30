package com.badwolfmc.cerberus.release;

import com.badwolfmc.guardian.protocol.GuardianChallengeCrypto;
import com.badwolfmc.guardian.protocol.GuardianChallengeTrustAnchors;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

/** Offline helper for generating the long-lived Guardian server-authentication Ed25519 identity. */
public final class GuardianServerIdentityGenerator {
    private GuardianServerIdentityGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "usage: GuardianServerIdentityGenerator <private-key-out> <public-keys-out>");
        }
        Path privateOut = Path.of(args[0]).toAbsolutePath().normalize();
        Path publicOut = Path.of(args[1]).toAbsolutePath().normalize();
        if (privateOut.equals(publicOut)) throw new IllegalArgumentException("private/public outputs must differ");
        if (Files.exists(privateOut, LinkOption.NOFOLLOW_LINKS) || Files.exists(publicOut, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("refusing to overwrite an existing Guardian server identity file");
        }
        if (privateOut.getParent() == null || publicOut.getParent() == null) {
            throw new IllegalArgumentException("identity outputs must have parent directories");
        }
        Files.createDirectories(privateOut.getParent());
        Files.createDirectories(publicOut.getParent());

        var generator = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM);
        var pair = generator.generateKeyPair();
        String privatePem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(pair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
        String publicText = GuardianChallengeTrustAnchors.canonicalText(java.util.List.of(pair.getPublic()));

        boolean privateCreated = false;
        try {
            Files.writeString(privateOut, privatePem, StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            privateCreated = true;
            restrictPrivatePermissions(privateOut);
            Files.writeString(publicOut, publicText, StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception ex) {
            if (privateCreated) {
                try { Files.deleteIfExists(privateOut); } catch (Exception ignored) { }
            }
            throw ex;
        }

        System.out.println("Guardian server-authentication identity generated.");
        System.out.println("Private key: " + privateOut);
        System.out.println("Public trust anchor: " + publicOut);
        System.out.println("Keep the private key on Guardian Admission authorities only; the public file may be embedded in Cerberus releases.");
    }

    private static void restrictPrivatePermissions(Path path) throws Exception {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Set<PosixFilePermission> ownerOnly = EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, ownerOnly);
        }
    }
}
