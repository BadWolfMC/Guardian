package com.badwolfmc.guardian.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Exact SHA-256 identity for one archive artifact. */
public record ArtifactSha256(String hex) implements Comparable<ArtifactSha256> {
    public static final int BYTES = 32;
    public static final int HEX_CHARS = BYTES * 2;
    private static final Pattern LOWER_HEX = Pattern.compile("[0-9a-f]{" + HEX_CHARS + "}");
    private static final HexFormat HEX = HexFormat.of();

    public ArtifactSha256 {
        Objects.requireNonNull(hex, "hex");
        if (!LOWER_HEX.matcher(hex).matches()) {
            throw new IllegalArgumentException(
                "SHA-256 digest must be exactly " + HEX_CHARS + " lowercase hexadecimal characters");
        }
    }

    public static ArtifactSha256 fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != BYTES) {
            throw new IllegalArgumentException("SHA-256 digest must be exactly " + BYTES + " bytes");
        }
        return new ArtifactSha256(HEX.formatHex(bytes));
    }

    public byte[] bytes() {
        return HEX.parseHex(hex);
    }

    public static ArtifactSha256 hashRegularFile(Path path, long maxBytes) throws IOException {
        Objects.requireNonNull(path, "path");
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        if (Files.isSymbolicLink(path)
            || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("artifact is not a regular non-symlink file: " + path.getFileName());
        }
        BasicFileAttributes before = Files.readAttributes(
            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile()) {
            throw new IOException("artifact is not a regular file: " + path.getFileName());
        }
        if (before.size() > maxBytes) {
            throw new IOException("artifact exceeds " + maxBytes + " byte safety limit: " + path.getFileName());
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", ex);
        }

        long totalRead = 0L;
        ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
        try (SeekableByteChannel channel = Files.newByteChannel(
            path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            int count;
            while ((count = channel.read(buffer)) != -1) {
                if (count == 0) continue;
                totalRead += count;
                if (totalRead > maxBytes) {
                    throw new IOException(
                        "artifact exceeded " + maxBytes + " byte safety limit while hashing: "
                            + path.getFileName());
                }
                digest.update(buffer.array(), 0, count);
                buffer.clear();
            }
        }

        BasicFileAttributes after = Files.readAttributes(
            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!sameSnapshot(before, after) || totalRead != after.size()) {
            throw new IOException("artifact changed while it was being hashed: " + path.getFileName());
        }
        return fromBytes(digest.digest());
    }

    private static boolean sameSnapshot(BasicFileAttributes before, BasicFileAttributes after) {
        Object beforeKey = before.fileKey();
        Object afterKey = after.fileKey();
        boolean sameKey = beforeKey == null || afterKey == null || beforeKey.equals(afterKey);
        return sameKey
            && before.size() == after.size()
            && before.lastModifiedTime().equals(after.lastModifiedTime())
            && after.isRegularFile();
    }

    @Override
    public int compareTo(ArtifactSha256 other) {
        return hex.compareTo(other.hex);
    }
}
