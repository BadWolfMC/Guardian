package com.badwolfmc.cerberus.release;

import com.badwolfmc.guardian.protocol.CerberusReleaseArtifact;
import com.badwolfmc.guardian.protocol.CerberusReleaseCrypto;
import com.badwolfmc.guardian.protocol.CerberusReleaseIdentity;
import com.badwolfmc.guardian.protocol.CerberusReleaseMetadataCodec;
import com.badwolfmc.guardian.protocol.GuardianChallengeTrustAnchors;
import com.badwolfmc.guardian.protocol.GuardianProtocol;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Offline release-build tool. The private signing key is read only by this process and never packaged. */
public final class CerberusReleaseSigner {
    private static final long MAX_PRIVATE_KEY_BYTES = 16 * 1024L;

    private CerberusReleaseSigner() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4 && args.length != 5) {
            throw new IllegalArgumentException(
                "usage: CerberusReleaseSigner <unsigned-jar> <signed-jar> <private-key-pkcs8> <release-version> [guardian-server-public-keys]");
        }
        Path input = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        Path privateKeyPath = Path.of(args[2]).toAbsolutePath().normalize();
        String releaseVersion = args[3];
        Path serverTrustPath = args.length == 5 ? Path.of(args[4]).toAbsolutePath().normalize() : null;
        if (input.equals(output)) throw new IllegalArgumentException("signed output must differ from unsigned input");

        String canonicalServerTrust = serverTrustPath == null ? null : readCanonicalServerTrust(serverTrustPath);
        PrivateKey privateKey = readPrivateKey(privateKeyPath);

        Path outputDirectory = output.getParent();
        Files.createDirectories(outputDirectory);
        Path temporary = Files.createTempFile(outputDirectory, output.getFileName().toString() + ".", ".tmp");
        boolean published = false;
        try {
            copyStableUnsignedJar(input, temporary);
            if (CerberusReleaseArtifact.readIdentity(temporary) != null) {
                throw new IllegalArgumentException("unsigned input already contains Cerberus release metadata");
            }
            if (containsEntry(temporary, GuardianChallengeTrustAnchors.ENTRY_NAME)) {
                throw new IllegalArgumentException("unsigned input already contains Guardian server trust anchors");
            }
            if (canonicalServerTrust != null) {
                injectEntry(
                    temporary,
                    GuardianChallengeTrustAnchors.ENTRY_NAME,
                    canonicalServerTrust.getBytes(StandardCharsets.UTF_8));
            }

            var digest = CerberusReleaseArtifact.canonicalDigest(temporary);
            byte[] signature = sign(privateKey, releaseVersion, digest);
            CerberusReleaseIdentity identity = new CerberusReleaseIdentity(releaseVersion, digest, signature);
            injectMetadata(temporary, CerberusReleaseMetadataCodec.encode(identity));

            if (!digest.equals(CerberusReleaseArtifact.canonicalDigest(temporary))) {
                throw new IllegalStateException("signed JAR canonical digest changed after metadata injection");
            }
            CerberusReleaseIdentity embedded = CerberusReleaseArtifact.readIdentity(temporary);
            if (embedded == null || !embedded.canonicalDigest().equals(digest)
                || !embedded.releaseVersion().equals(releaseVersion)) {
                throw new IllegalStateException("signed JAR release metadata verification failed");
            }

            publishAtomically(temporary, output);
            published = true;
        } finally {
            if (!published) Files.deleteIfExists(temporary);
        }

        System.out.println("Cerberus signed release created: " + output.getFileName());
        System.out.println("Canonical SHA-256: " + CerberusReleaseArtifact.canonicalDigest(output).hex());
        if (canonicalServerTrust != null) {
            System.out.println("Guardian server-authentication trust anchors embedded: "
                + GuardianChallengeTrustAnchors.parse(canonicalServerTrust).size());
        }
    }


    private static void copyStableUnsignedJar(Path input, Path temporary) throws IOException {
        BasicFileAttributes before = Files.readAttributes(input, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || Files.isSymbolicLink(input)) {
            throw new IOException("unsigned Cerberus input must be a regular non-symlink file");
        }
        if (before.size() < 1 || before.size() > GuardianProtocol.MAX_ARTIFACT_BYTES) {
            throw new IOException("unsigned Cerberus input has an invalid size");
        }

        long copied = 0L;
        ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
        try (SeekableByteChannel source = Files.newByteChannel(
                input, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             SeekableByteChannel target = Files.newByteChannel(
                temporary, Set.of(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))) {
            while (true) {
                buffer.clear();
                int read = source.read(buffer);
                if (read == -1) break;
                copied = Math.addExact(copied, read);
                if (copied > GuardianProtocol.MAX_ARTIFACT_BYTES) {
                    throw new IOException("unsigned Cerberus input exceeds artifact safety limit");
                }
                buffer.flip();
                while (buffer.hasRemaining()) target.write(buffer);
            }
        } catch (ArithmeticException ex) {
            throw new IOException("unsigned Cerberus input length overflow", ex);
        }

        BasicFileAttributes after = Files.readAttributes(input, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile() || Files.isSymbolicLink(input)
            || before.size() != after.size()
            || !before.lastModifiedTime().equals(after.lastModifiedTime())
            || !Objects.equals(before.fileKey(), after.fileKey())
            || copied != after.size()) {
            throw new IOException("unsigned Cerberus input changed while it was being copied for signing");
        }
    }

    private static byte[] sign(PrivateKey privateKey, String releaseVersion,
                               com.badwolfmc.guardian.protocol.ArtifactSha256 digest)
        throws GeneralSecurityException {
        Signature signer = Signature.getInstance(CerberusReleaseCrypto.ALGORITHM);
        signer.initSign(privateKey);
        signer.update(CerberusReleaseCrypto.signingMessage(releaseVersion, digest));
        byte[] signature = signer.sign();
        if (signature.length != CerberusReleaseIdentity.ED25519_SIGNATURE_BYTES) {
            throw new GeneralSecurityException("unexpected Ed25519 signature length " + signature.length);
        }
        return signature;
    }

    private static PrivateKey readPrivateKey(Path path) throws IOException, GeneralSecurityException {
        byte[] fileBytes = readStablePrivateKey(path);
        byte[] der = decodePemIfNeeded(fileBytes);
        try {
            return KeyFactory.getInstance(CerberusReleaseCrypto.ALGORITHM)
                .generatePrivate(new PKCS8EncodedKeySpec(der));
        } finally {
            java.util.Arrays.fill(fileBytes, (byte) 0);
            if (der != fileBytes) java.util.Arrays.fill(der, (byte) 0);
        }
    }

    private static byte[] readStablePrivateKey(Path path) throws IOException {
        BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || Files.isSymbolicLink(path)) {
            throw new IOException("release private key must be a regular non-symlink file");
        }
        if (before.size() < 1 || before.size() > MAX_PRIVATE_KEY_BYTES) {
            throw new IOException("release private key has an invalid size");
        }

        ByteBuffer buffer = ByteBuffer.allocate((int) MAX_PRIVATE_KEY_BYTES + 1);
        try (SeekableByteChannel channel = Files.newByteChannel(
            path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // Continue until EOF or the bounded buffer is full.
            }
        }
        if (buffer.position() == 0 || buffer.position() > MAX_PRIVATE_KEY_BYTES) {
            throw new IOException("release private key has an invalid size");
        }

        BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile() || before.size() != after.size()
            || !before.lastModifiedTime().equals(after.lastModifiedTime())
            || !Objects.equals(before.fileKey(), after.fileKey())
            || after.size() != buffer.position()) {
            throw new IOException("release private key changed while it was being read");
        }
        byte[] bytes = new byte[buffer.position()];
        buffer.flip();
        buffer.get(bytes);
        return bytes;
    }

    private static byte[] decodePemIfNeeded(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.US_ASCII).trim();
        if (!text.startsWith("-----BEGIN PRIVATE KEY-----")) return bytes;
        if (!text.endsWith("-----END PRIVATE KEY-----")) {
            throw new IllegalArgumentException("Cerberus release-signing PEM key is missing END PRIVATE KEY");
        }
        String base64 = text
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Cerberus release-signing PEM body is not valid Base64", ex);
        }
    }


    private static String readCanonicalServerTrust(Path path) throws IOException, GeneralSecurityException {
        BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || Files.isSymbolicLink(path)) {
            throw new IOException("Guardian server public-key file must be a regular non-symlink file");
        }
        if (before.size() < 1 || before.size() > GuardianChallengeTrustAnchors.MAX_TEXT_BYTES) {
            throw new IOException("Guardian server public-key file has an invalid size");
        }
        ByteBuffer buffer = ByteBuffer.allocate(GuardianChallengeTrustAnchors.MAX_TEXT_BYTES + 1);
        try (SeekableByteChannel channel = Files.newByteChannel(
            path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // bounded no-follow read
            }
        }
        if (buffer.position() == 0 || buffer.position() > GuardianChallengeTrustAnchors.MAX_TEXT_BYTES) {
            throw new IOException("Guardian server public-key file exceeds size limit");
        }
        byte[] bytes = new byte[buffer.position()];
        buffer.flip();
        buffer.get(bytes);
        BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile() || before.size() != after.size()
            || !before.lastModifiedTime().equals(after.lastModifiedTime())
            || !Objects.equals(before.fileKey(), after.fileKey())
            || after.size() != bytes.length) {
            throw new IOException("Guardian server public-key file changed while it was being read");
        }
        String text = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString();
        return GuardianChallengeTrustAnchors.canonicalText(GuardianChallengeTrustAnchors.parse(text));
    }

    private static boolean containsEntry(Path jar, String entryName) throws IOException {
        URI uri = URI.create("jar:" + jar.toUri());
        try (FileSystem zip = FileSystems.newFileSystem(uri, Map.of())) {
            return Files.exists(zip.getPath("/" + entryName));
        }
    }

    private static void publishAtomically(Path temporary, Path output) throws IOException {
        try {
            Files.move(temporary, output,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void injectMetadata(Path jar, byte[] metadata) throws IOException {
        injectEntry(jar, CerberusReleaseMetadataCodec.ENTRY_NAME, metadata);
    }

    private static void injectEntry(Path jar, String entryName, byte[] bytes) throws IOException {
        URI uri = URI.create("jar:" + jar.toUri());
        try (FileSystem zip = FileSystems.newFileSystem(uri, Map.of())) {
            Path entry = zip.getPath("/" + entryName);
            Files.createDirectories(entry.getParent());
            Files.write(entry, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
    }
}
