package com.badwolfmc.guardian.core.artifact;

import com.badwolfmc.guardian.protocol.ArtifactSha256;
import com.badwolfmc.guardian.protocol.GuardianProtocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Read-only scanner for administrator-supplied candidate Fabric JARs. */
public final class ApprovedArtifactScanner {
    public static final int MAX_IMPORT_JARS = 256;
    public static final int MAX_FABRIC_METADATA_BYTES = 128 * 1024;
    public static final int MAX_ARCHIVE_ENTRIES = 16_384;
    public static final long MAX_TOTAL_IMPORT_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final String FABRIC_METADATA = "fabric.mod.json";
    private final int maxArchiveEntries;

    public ApprovedArtifactScanner() {
        this(MAX_ARCHIVE_ENTRIES);
    }

    ApprovedArtifactScanner(int maxArchiveEntries) {
        if (maxArchiveEntries < 1) throw new IllegalArgumentException("maxArchiveEntries must be positive");
        this.maxArchiveEntries = maxArchiveEntries;
    }

    public ScanResult scan(Path directory) throws ArtifactCatalogException {
        validateDirectory(directory);
        List<Path> candidates = listCandidates(directory);
        if (candidates.size() > MAX_IMPORT_JARS) {
            throw new ArtifactCatalogException(
                "artifact-import contains " + candidates.size() + " JARs; maximum is " + MAX_IMPORT_JARS);
        }
        validateAggregateSize(candidates);

        ArrayList<ApprovedArtifact> artifacts = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        for (Path candidate : candidates) {
            try {
                artifacts.add(inspect(candidate));
            } catch (ArtifactCatalogException ex) {
                errors.add(candidate.getFileName() + ": " + ex.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new ArtifactCatalogException(
                "artifact scan rejected; catalog was not modified: " + String.join("; ", errors));
        }
        artifacts.sort(Comparator.naturalOrder());
        return new ScanResult(candidates.size(), List.copyOf(artifacts));
    }

    private static void validateAggregateSize(List<Path> candidates) throws ArtifactCatalogException {
        long total = 0L;
        for (Path candidate : candidates) {
            final long size;
            try {
                size = Files.size(candidate);
            } catch (IOException ex) {
                throw new ArtifactCatalogException(
                    "could not read size of artifact candidate '" + candidate.getFileName() + "'", ex);
            }
            if (size > GuardianProtocol.MAX_ARTIFACT_BYTES) {
                throw new ArtifactCatalogException(
                    "artifact candidate '" + candidate.getFileName() + "' exceeds "
                        + GuardianProtocol.MAX_ARTIFACT_BYTES + " byte safety limit");
            }
            if (total > MAX_TOTAL_IMPORT_BYTES - size) {
                throw new ArtifactCatalogException(
                    "artifact-import exceeds " + MAX_TOTAL_IMPORT_BYTES + " aggregate bytes");
            }
            total += size;
        }
    }

    private static void validateDirectory(Path directory) throws ArtifactCatalogException {
        if (Files.isSymbolicLink(directory)) {
            throw new ArtifactCatalogException("artifact-import directory must not be a symbolic link");
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new ArtifactCatalogException("artifact-import path is not a directory");
        }
    }

    private static List<Path> listCandidates(Path directory) throws ArtifactCatalogException {
        ArrayList<Path> candidates = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
                if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new ArtifactCatalogException(
                        "artifact candidate '" + name + "' must be a regular non-symlink file");
                }
                candidates.add(path);
            }
        } catch (IOException ex) {
            throw new ArtifactCatalogException("could not list artifact-import directory", ex);
        }
        candidates.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return candidates;
    }

    private ApprovedArtifact inspect(Path jar) throws ArtifactCatalogException {
        final BasicFileAttributes before;
        try {
            before = Files.readAttributes(jar, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException ex) {
            throw new ArtifactCatalogException("could not read artifact attributes", ex);
        }
        if (!before.isRegularFile()) {
            throw new ArtifactCatalogException("artifact stopped being a regular file during inspection");
        }
        long size = before.size();
        if (size > GuardianProtocol.MAX_ARTIFACT_BYTES) {
            throw new ArtifactCatalogException(
                "artifact exceeds " + GuardianProtocol.MAX_ARTIFACT_BYTES + " byte safety limit");
        }

        FabricModMetadataParser.Metadata metadata;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            if (zip.size() > maxArchiveEntries) {
                throw new ArtifactCatalogException(
                    "JAR contains " + zip.size() + " entries; maximum is " + maxArchiveEntries);
            }
            ZipEntry entry = zip.getEntry(FABRIC_METADATA);
            if (entry == null || entry.isDirectory()) {
                throw new ArtifactCatalogException("root fabric.mod.json is missing");
            }
            if (entry.getSize() > MAX_FABRIC_METADATA_BYTES) {
                throw new ArtifactCatalogException(
                    "fabric.mod.json exceeds " + MAX_FABRIC_METADATA_BYTES + " byte safety limit");
            }
            byte[] bytes;
            try (InputStream input = zip.getInputStream(entry)) {
                bytes = readBounded(input, MAX_FABRIC_METADATA_BYTES);
            }
            metadata = FabricModMetadataParser.parse(decodeUtf8(bytes));
        } catch (ZipException ex) {
            throw new ArtifactCatalogException("malformed JAR/ZIP archive", ex);
        } catch (IOException ex) {
            throw new ArtifactCatalogException("could not inspect JAR metadata", ex);
        }

        try {
            ArtifactSha256 hash = ArtifactSha256.hashRegularFile(jar, GuardianProtocol.MAX_ARTIFACT_BYTES);
            BasicFileAttributes after = Files.readAttributes(
                jar, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!sameFileSnapshot(before, after)) {
                throw new ArtifactCatalogException("artifact changed while it was being inspected; retry the scan");
            }
            return new ApprovedArtifact(metadata.modId(), metadata.version(), hash);
        } catch (IOException | IllegalArgumentException ex) {
            throw new ArtifactCatalogException("could not hash artifact candidate: " + ex.getMessage(), ex);
        }
    }


    private static boolean sameFileSnapshot(BasicFileAttributes before, BasicFileAttributes after) {
        if (!after.isRegularFile()) return false;
        if (before.size() != after.size()) return false;
        if (!before.lastModifiedTime().equals(after.lastModifiedTime())) return false;
        Object beforeKey = before.fileKey();
        Object afterKey = after.fileKey();
        return beforeKey == null || afterKey == null || beforeKey.equals(afterKey);
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws IOException, ArtifactCatalogException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[4096];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maxBytes) {
                throw new ArtifactCatalogException(
                    "fabric.mod.json exceeds " + maxBytes + " byte safety limit while reading");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String decodeUtf8(byte[] bytes) throws ArtifactCatalogException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException ex) {
            throw new ArtifactCatalogException("fabric.mod.json is not valid UTF-8", ex);
        }
    }

    public record ScanResult(int jarCount, List<ApprovedArtifact> artifacts) {
        public ScanResult {
            artifacts = List.copyOf(artifacts);
        }
    }
}
