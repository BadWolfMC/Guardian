package com.badwolfmc.cerberus.release;

import com.badwolfmc.guardian.protocol.CerberusReleaseArtifact;
import com.badwolfmc.guardian.protocol.CerberusReleaseCrypto;
import com.badwolfmc.guardian.protocol.CerberusReleaseIdentity;
import com.badwolfmc.guardian.protocol.GuardianChallengeTrustAnchors;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Map;
import java.util.Objects;

/** Offline verifier for a finished signed Cerberus release artifact. */
public final class CerberusReleaseVerifier {
    private static final long MAX_RELEASE_PUBLIC_KEY_BYTES = 4096L;

    private CerberusReleaseVerifier() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 && args.length != 4) {
            throw new IllegalArgumentException(
                "usage: CerberusReleaseVerifier <signed-jar> <expected-version> <release-public-key> [guardian-server-public-keys]");
        }
        Path signedJar = Path.of(args[0]).toAbsolutePath().normalize();
        String expectedVersion = args[1];
        Path releasePublicKey = Path.of(args[2]).toAbsolutePath().normalize();
        Path expectedServerTrust = args.length == 4 ? Path.of(args[3]).toAbsolutePath().normalize() : null;

        CerberusReleaseIdentity identity = CerberusReleaseArtifact.readIdentity(signedJar);
        if (identity == null) throw new IllegalArgumentException("signed Cerberus JAR has no release identity metadata");
        if (!expectedVersion.equals(identity.releaseVersion())) {
            throw new IllegalArgumentException("signed Cerberus release identity version '" + identity.releaseVersion()
                + "' does not match expected version '" + expectedVersion + "'");
        }
        var actualDigest = CerberusReleaseArtifact.canonicalDigest(signedJar);
        if (!actualDigest.equals(identity.canonicalDigest())) {
            throw new IllegalArgumentException("signed Cerberus canonical digest does not match embedded release identity");
        }

        PublicKey key = CerberusReleaseCrypto.decodePublicKey(readBoundedUtf8(releasePublicKey, MAX_RELEASE_PUBLIC_KEY_BYTES).trim());
        if (!CerberusReleaseCrypto.verify(key, identity)) {
            throw new GeneralSecurityException("Cerberus release signature does not verify with the supplied release public key");
        }

        if (expectedServerTrust != null) {
            String expected = GuardianChallengeTrustAnchors.canonicalText(
                GuardianChallengeTrustAnchors.parse(readBoundedUtf8(expectedServerTrust, GuardianChallengeTrustAnchors.MAX_TEXT_BYTES)));
            String embedded = readJarEntryUtf8(signedJar, GuardianChallengeTrustAnchors.ENTRY_NAME,
                GuardianChallengeTrustAnchors.MAX_TEXT_BYTES);
            if (embedded == null) {
                throw new IllegalArgumentException("signed Cerberus JAR is missing Guardian server-authentication trust anchors");
            }
            String canonicalEmbedded = GuardianChallengeTrustAnchors.canonicalText(
                GuardianChallengeTrustAnchors.parse(embedded));
            if (!expected.equals(canonicalEmbedded)) {
                throw new IllegalArgumentException("embedded Guardian server-authentication trust anchors do not match the supplied trust file");
            }
        }

        System.out.println("Cerberus release verification passed: " + signedJar.getFileName());
        System.out.println("Release version: " + identity.releaseVersion());
        System.out.println("Canonical SHA-256: " + identity.canonicalDigest().hex());
    }

    private static String readBoundedUtf8(Path path, long maxBytes) throws IOException {
        BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || Files.isSymbolicLink(path) || before.size() < 1 || before.size() > maxBytes) {
            throw new IOException("verification key/trust file must be a bounded regular non-symlink file");
        }
        if (maxBytes > Integer.MAX_VALUE - 1L) {
            throw new IOException("verification key/trust size limit is too large for bounded read");
        }
        ByteBuffer buffer = ByteBuffer.allocate((int) maxBytes + 1);
        try (SeekableByteChannel channel = Files.newByteChannel(
            path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // Continue until EOF or the bounded buffer is full.
            }
        }
        if (buffer.position() == 0 || buffer.position() > maxBytes) {
            throw new IOException("verification key/trust file exceeds size limit");
        }
        BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile() || Files.isSymbolicLink(path)
            || before.size() != after.size()
            || !before.lastModifiedTime().equals(after.lastModifiedTime())
            || !Objects.equals(before.fileKey(), after.fileKey())
            || after.size() != buffer.position()) {
            throw new IOException("verification key/trust file changed while it was being read");
        }
        buffer.flip();
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(buffer).toString();
    }

    private static String readJarEntryUtf8(Path jar, String entryName, int maxBytes) throws IOException {
        URI uri = URI.create("jar:" + jar.toUri());
        try (FileSystem zip = FileSystems.newFileSystem(uri, Map.of())) {
            Path entry = zip.getPath("/" + entryName);
            if (!Files.exists(entry)) return null;
            long size = Files.size(entry);
            if (size < 1 || size > maxBytes) throw new IOException("JAR entry " + entryName + " has invalid size");
            byte[] bytes = Files.readAllBytes(entry);
            if (bytes.length > maxBytes) throw new IOException("JAR entry " + entryName + " exceeds size limit");
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        }
    }
}
