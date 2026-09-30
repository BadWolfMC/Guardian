package com.badwolfmc.guardian.core.operations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/** Bounded, no-follow read of a stable regular-file snapshot. */
public final class SafeRegularFile {
    private SafeRegularFile() {}

    public static String readUtf8(Path path, int maxBytes) throws IOException {
        byte[] bytes = read(path, maxBytes);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (java.nio.charset.CharacterCodingException ex) {
            throw new IOException("file is not valid UTF-8", ex);
        }
    }

    public static byte[] read(Path path, int maxBytes) throws IOException {
        return read(path, maxBytes, false);
    }

    /** Stable no-follow read used only when preserving an invalid administrator file may include zero bytes. */
    public static byte[] readAllowEmpty(Path path, int maxBytes) throws IOException {
        return read(path, maxBytes, true);
    }

    private static byte[] read(Path path, int maxBytes, boolean allowEmpty) throws IOException {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("expected a regular non-symlink file");
        }

        BasicFileAttributes before = Files.readAttributes(
            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        long minimum = allowEmpty ? 0L : 1L;
        if (before.size() < minimum || before.size() > maxBytes) {
            String range = allowEmpty ? "0 and " + maxBytes : "1 and " + maxBytes;
            throw new IOException("file size must be between " + range + " bytes");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(before.size(), 8192));
        try (SeekableByteChannel channel = Files.newByteChannel(
            path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            int total = 0;
            int read;
            while ((read = channel.read(buffer)) != -1) {
                if (read == 0) continue;
                total += read;
                if (total > maxBytes) throw new IOException("file grew beyond " + maxBytes + " bytes while reading");
                buffer.flip();
                byte[] chunk = new byte[read];
                buffer.get(chunk);
                output.write(chunk);
                buffer.clear();
            }
        }

        BasicFileAttributes after = Files.readAttributes(
            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!sameSnapshot(before, after) || output.size() != after.size()) {
            throw new IOException("file changed while Guardian was loading it");
        }
        return output.toByteArray();
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
}
