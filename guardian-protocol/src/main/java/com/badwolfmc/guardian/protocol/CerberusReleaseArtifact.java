package com.badwolfmc.guardian.protocol;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Canonical content identity for an official Cerberus release JAR. */
public final class CerberusReleaseArtifact {
    public static final long MAX_CANONICAL_CONTENT_BYTES = 64L * 1024L * 1024L;
    public static final int MAX_FILE_ENTRIES = 4096;
    public static final int MAX_ENTRY_NAME_BYTES = 1024;
    private static final byte[] DOMAIN = "BadWolfMC Guardian Cerberus canonical JAR v1"
        .getBytes(StandardCharsets.US_ASCII);

    private CerberusReleaseArtifact() {}

    /**
     * Hashes logical JAR file content rather than ZIP ordering/compression/timestamps. The embedded
     * signature metadata entry is excluded to avoid a circular digest/signature dependency.
     */
    public static ArtifactSha256 canonicalDigest(Path jar) throws IOException {
        Objects.requireNonNull(jar, "jar");
        BasicFileAttributes before = regularJarAttributes(jar);
        if (before.size() > GuardianProtocol.MAX_ARTIFACT_BYTES) {
            throw new IOException("Cerberus artifact exceeds " + GuardianProtocol.MAX_ARTIFACT_BYTES + " bytes");
        }

        List<EntryDigest> entries = new ArrayList<>();
        HashSet<String> names = new HashSet<>();
        long totalContent = 0L;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) continue;
                if (!names.add(entry.getName())) throw new IOException("duplicate JAR entry: " + entry.getName());
                if (CerberusReleaseMetadataCodec.ENTRY_NAME.equals(entry.getName())) continue;
                if (entries.size() >= MAX_FILE_ENTRIES) {
                    throw new IOException("Cerberus JAR exceeds " + MAX_FILE_ENTRIES + " file entries");
                }
                byte[] name = canonicalEntryName(entry.getName());

                MessageDigest contentDigest = sha256();
                long contentLength = 0L;
                byte[] buffer = new byte[16 * 1024];
                try (InputStream input = new BufferedInputStream(zip.getInputStream(entry))) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        contentLength = Math.addExact(contentLength, count);
                        totalContent = Math.addExact(totalContent, count);
                        if (totalContent > MAX_CANONICAL_CONTENT_BYTES) {
                            throw new IOException("Cerberus JAR canonical content exceeds "
                                + MAX_CANONICAL_CONTENT_BYTES + " bytes");
                        }
                        contentDigest.update(buffer, 0, count);
                    }
                } catch (ArithmeticException ex) {
                    throw new IOException("Cerberus JAR content length overflow", ex);
                }
                entries.add(new EntryDigest(name, contentLength, contentDigest.digest()));
            }
        }

        BasicFileAttributes after = regularJarAttributes(jar);
        if (before.size() != after.size()
            || !before.lastModifiedTime().equals(after.lastModifiedTime())
            || !Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException("Cerberus artifact changed while computing release identity");
        }

        entries.sort(Comparator.comparing(EntryDigest::name, CerberusReleaseArtifact::compareUnsigned));
        MessageDigest canonical = sha256();
        try (OutputStream sink = new java.security.DigestOutputStream(OutputStream.nullOutputStream(), canonical);
             DataOutputStream out = new DataOutputStream(sink)) {
            out.writeShort(DOMAIN.length);
            out.write(DOMAIN);
            out.writeInt(entries.size());
            for (EntryDigest entry : entries) {
                out.writeShort(entry.name().length);
                out.write(entry.name());
                out.writeLong(entry.contentLength());
                out.write(entry.contentDigest());
            }
        }
        return ArtifactSha256.fromBytes(canonical.digest());
    }

    public static CerberusReleaseIdentity readIdentity(Path jar) throws IOException, ProtocolException {
        Objects.requireNonNull(jar, "jar");
        regularJarAttributes(jar);
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = null;
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry candidate = enumeration.nextElement();
                if (CerberusReleaseMetadataCodec.ENTRY_NAME.equals(candidate.getName())) {
                    if (entry != null) throw new IOException("duplicate Cerberus release metadata entry");
                    entry = candidate;
                }
            }
            if (entry == null || entry.isDirectory()) return null;
            long declaredSize = entry.getSize();
            if (declaredSize > CerberusReleaseMetadataCodec.MAX_BYTES) {
                throw new IOException("Cerberus release metadata exceeds safety limit");
            }
            byte[] bytes;
            try (InputStream input = zip.getInputStream(entry)) {
                bytes = input.readNBytes(CerberusReleaseMetadataCodec.MAX_BYTES + 1);
            }
            if (bytes.length > CerberusReleaseMetadataCodec.MAX_BYTES) {
                throw new IOException("Cerberus release metadata exceeds safety limit");
            }
            return CerberusReleaseMetadataCodec.decode(bytes);
        }
    }

    private static BasicFileAttributes regularJarAttributes(Path jar) throws IOException {
        if (Files.isSymbolicLink(jar) || !Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Cerberus release artifact is not a regular non-symlink file");
        }
        return Files.readAttributes(jar, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }


    private static byte[] canonicalEntryName(String entryName) throws IOException {
        if (entryName.startsWith("/") || entryName.endsWith("/") || entryName.indexOf('\\') >= 0) {
            throw new IOException("Cerberus JAR contains a non-canonical entry name");
        }
        String[] segments = entryName.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Cerberus JAR contains a non-canonical entry name");
            }
        }
        try {
            return ProtocolText.encode(entryName, MAX_ENTRY_NAME_BYTES, "Cerberus JAR entry name");
        } catch (IllegalArgumentException ex) {
            throw new IOException("invalid Cerberus JAR entry name: " + ex.getMessage(), ex);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", ex);
        }
    }

    private static int compareUnsigned(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int a = Byte.toUnsignedInt(left[i]);
            int b = Byte.toUnsignedInt(right[i]);
            if (a != b) return Integer.compare(a, b);
        }
        return Integer.compare(left.length, right.length);
    }

    private record EntryDigest(byte[] name, long contentLength, byte[] contentDigest) {}
}
