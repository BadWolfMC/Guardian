package com.badwolfmc.guardian.core.artifact;

import com.badwolfmc.guardian.protocol.ArtifactSha256;
import com.badwolfmc.guardian.core.operations.SafeRegularFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict reader and deterministic writer for Guardian's generated artifact catalog YAML. */
public final class ArtifactCatalogStore {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CATALOG_BYTES = 1024 * 1024;
    public static final int MAX_CATALOG_ENTRIES = 4096;

    private final Path path;

    public ArtifactCatalogStore(Path path) {
        this.path = path;
    }

    public boolean exists() {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    public ArtifactCatalog load() throws ArtifactCatalogException {
        if (!exists()) return ArtifactCatalog.empty();
        final String text;
        try {
            text = SafeRegularFile.readUtf8(path, MAX_CATALOG_BYTES);
        } catch (IOException ex) {
            throw new ArtifactCatalogException(
                "artifact catalog must be a stable regular non-symlink UTF-8 file: " + ex.getMessage(), ex);
        }
        return parse(text);
    }

    public void store(ArtifactCatalog catalog) throws ArtifactCatalogException {
        if (catalog.size() > MAX_CATALOG_ENTRIES) {
            throw new ArtifactCatalogException(
                "artifact catalog exceeds " + MAX_CATALOG_ENTRIES + " artifact entries");
        }
        if (Files.isSymbolicLink(path)) {
            throw new ArtifactCatalogException("refusing to replace symbolic-link artifact catalog");
        }
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new ArtifactCatalogException("artifact catalog has no parent directory");
        try {
            Files.createDirectories(parent);
            String content = render(catalog);
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            if (contentBytes.length > MAX_CATALOG_BYTES) {
                throw new ArtifactCatalogException(
                    "generated artifact catalog exceeds " + MAX_CATALOG_BYTES + " byte safety limit");
            }
            Path temporary = Files.createTempFile(parent, ".artifacts-", ".tmp");
            try {
                Files.write(temporary, contentBytes);
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException ex) {
            throw new ArtifactCatalogException("could not atomically write artifact catalog", ex);
        }
    }

    static ArtifactCatalog parse(String text) throws ArtifactCatalogException {
        boolean schemaSeen = false;
        boolean artifactsSeen = false;
        String currentMod = null;
        String currentVersion = null;
        boolean versionsSectionSeen = false;
        boolean sha256SectionSeen = false;
        Set<String> mods = new HashSet<>();
        Map<String, Set<String>> versionsByMod = new HashMap<>();
        Map<String, Set<ArtifactSha256>> hashesByCoordinate = new HashMap<>();
        ArrayList<ApprovedArtifact> entries = new ArrayList<>();

        List<String> lines = text.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int indent = leadingSpaces(line);
            if (line.substring(0, indent).indexOf('\t') >= 0) {
                throw lineError(i, "tabs are not supported in generated catalog indentation");
            }

            if (indent == 0 && trimmed.startsWith("schema-version:")) {
                if (schemaSeen || artifactsSeen) throw lineError(i, "schema-version must appear once before artifacts");
                if (!trimmed.equals("schema-version: " + SCHEMA_VERSION)) {
                    throw lineError(i, "unsupported schema-version; supported value is " + SCHEMA_VERSION);
                }
                schemaSeen = true;
                continue;
            }
            if (indent == 0 && trimmed.equals("artifacts:")) {
                if (!schemaSeen) throw lineError(i, "schema-version must appear before artifacts");
                if (artifactsSeen) throw lineError(i, "duplicate artifacts section");
                artifactsSeen = true;
                continue;
            }
            if (!artifactsSeen) throw lineError(i, "unexpected content before artifacts section");

            if (indent == 2 && trimmed.endsWith(":")) {
                currentMod = trimmed.substring(0, trimmed.length() - 1);
                currentVersion = null;
                versionsSectionSeen = false;
                sha256SectionSeen = false;
                try {
                    new ApprovedArtifact(currentMod, "probe", ArtifactSha256.fromBytes(new byte[32]));
                } catch (IllegalArgumentException ex) {
                    throw lineError(i, ex.getMessage());
                }
                if (!mods.add(currentMod)) throw lineError(i, "duplicate mod id '" + currentMod + "'");
                versionsByMod.put(currentMod, new HashSet<>());
                continue;
            }
            if (indent == 4 && trimmed.equals("versions:")) {
                if (currentMod == null) throw lineError(i, "versions section has no containing mod id");
                if (versionsSectionSeen) throw lineError(i, "duplicate versions section for " + currentMod);
                versionsSectionSeen = true;
                continue;
            }
            if (indent == 6 && trimmed.endsWith(":")) {
                if (currentMod == null || !versionsSectionSeen) {
                    throw lineError(i, "version must appear inside a versions section");
                }
                currentVersion = parseKey(trimmed.substring(0, trimmed.length() - 1), i);
                sha256SectionSeen = false;
                if (currentVersion.isBlank()) throw lineError(i, "version key must not be blank");
                try {
                    new ApprovedArtifact(currentMod, currentVersion, ArtifactSha256.fromBytes(new byte[32]));
                } catch (IllegalArgumentException ex) {
                    throw lineError(i, ex.getMessage());
                }
                if (!versionsByMod.get(currentMod).add(currentVersion)) {
                    throw lineError(i, "duplicate version '" + currentVersion + "' for " + currentMod);
                }
                hashesByCoordinate.put(coordinate(currentMod, currentVersion), new HashSet<>());
                continue;
            }
            if (indent == 8 && trimmed.equals("sha256:")) {
                if (currentMod == null || currentVersion == null) {
                    throw lineError(i, "sha256 section has no containing mod/version");
                }
                if (sha256SectionSeen) throw lineError(i, "duplicate sha256 section");
                sha256SectionSeen = true;
                continue;
            }
            if (indent == 10 && trimmed.startsWith("- ")) {
                if (currentMod == null || currentVersion == null || !sha256SectionSeen) {
                    throw lineError(i, "SHA-256 entry must appear inside a sha256 section");
                }
                String value = parseScalar(trimmed.substring(2).trim(), i);
                final ArtifactSha256 digest;
                try {
                    digest = new ArtifactSha256(value);
                } catch (IllegalArgumentException ex) {
                    throw lineError(i, ex.getMessage());
                }
                Set<ArtifactSha256> hashes = hashesByCoordinate.get(coordinate(currentMod, currentVersion));
                if (!hashes.add(digest)) {
                    throw lineError(i, "duplicate SHA-256 for " + currentMod + " " + currentVersion);
                }
                entries.add(new ApprovedArtifact(currentMod, currentVersion, digest));
                if (entries.size() > MAX_CATALOG_ENTRIES) {
                    throw lineError(i, "catalog exceeds " + MAX_CATALOG_ENTRIES + " artifact entries");
                }
                continue;
            }
            throw lineError(i, "unsupported or malformed generated catalog structure");
        }

        if (!schemaSeen) throw new ArtifactCatalogException("artifact catalog is missing schema-version");
        if (!artifactsSeen) throw new ArtifactCatalogException("artifact catalog is missing artifacts section");
        for (String mod : mods) {
            Set<String> versions = versionsByMod.get(mod);
            if (versions.isEmpty()) throw new ArtifactCatalogException("artifact '" + mod + "' has no versions");
            for (String version : versions) {
                if (hashesByCoordinate.get(coordinate(mod, version)).isEmpty()) {
                    throw new ArtifactCatalogException(
                        "artifact '" + mod + "' version '" + version + "' has no SHA-256 values");
                }
            }
        }
        return ArtifactCatalog.of(entries);
    }

    static String render(ArtifactCatalog catalog) {
        StringBuilder out = new StringBuilder();
        out.append("# Guardian-managed exact-artifact catalog.\n");
        out.append("# Generated deterministically by Guardian artifact scanning.\n");
        out.append("# Values may be edited, but comments/formatting are not preserved when Guardian rewrites this file.\n");
        out.append("# Policy decisions belong in Phase 3 policy files; this file records artifact identity only.\n");
        out.append("schema-version: ").append(SCHEMA_VERSION).append("\n");
        out.append("artifacts:\n");

        String lastMod = null;
        String lastVersion = null;
        for (ApprovedArtifact entry : catalog.entries()) {
            if (!entry.modId().equals(lastMod)) {
                out.append("  ").append(entry.modId()).append(":\n");
                out.append("    versions:\n");
                lastMod = entry.modId();
                lastVersion = null;
            }
            if (!entry.version().equals(lastVersion)) {
                out.append("      \"").append(escape(entry.version())).append("\":\n");
                out.append("        sha256:\n");
                lastVersion = entry.version();
            }
            out.append("          - \"").append(entry.sha256().hex()).append("\"\n");
        }
        return out.toString();
    }

    private static String parseKey(String raw, int lineIndex) throws ArtifactCatalogException {
        return parseScalar(raw.trim(), lineIndex);
    }

    private static String parseScalar(String raw, int lineIndex) throws ArtifactCatalogException {
        if (!raw.startsWith("\"") && !raw.endsWith("\"")) return raw;
        if (!(raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2)) {
            throw lineError(lineIndex, "malformed quoted scalar");
        }
        String body = raw.substring(1, raw.length() - 1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (++i >= body.length()) throw lineError(lineIndex, "truncated quoted escape");
            char escaped = body.charAt(i);
            switch (escaped) {
                case '\\', '"' -> out.append(escaped);
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                default -> throw lineError(lineIndex, "unsupported quoted escape \\" + escaped + "'");
            }
        }
        return out.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') count++;
        return count;
    }

    private static String coordinate(String modId, String version) {
        return modId + "\u0000" + version;
    }

    private static ArtifactCatalogException lineError(int zeroBasedLine, String message) {
        return new ArtifactCatalogException("line " + (zeroBasedLine + 1) + ": " + message);
    }
}
