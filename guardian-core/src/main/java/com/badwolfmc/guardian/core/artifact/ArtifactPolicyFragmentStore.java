package com.badwolfmc.guardian.core.artifact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic convenience output for administrators who want direct exact-hash policy rules.
 * The generated file is never loaded automatically and never mutates policy.yml.
 */
public final class ArtifactPolicyFragmentStore {
    public static final int MAX_FRAGMENT_BYTES = 1024 * 1024;

    private final Path path;

    public ArtifactPolicyFragmentStore(Path path) {
        this.path = path;
    }

    public void store(List<ApprovedArtifact> importedArtifacts) throws ArtifactCatalogException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new ArtifactCatalogException("artifact policy fragment has no parent directory");
        if (Files.isSymbolicLink(path)) {
            throw new ArtifactCatalogException("refusing to replace symbolic-link artifact policy fragment");
        }
        byte[] content = render(importedArtifacts).getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_FRAGMENT_BYTES) {
            throw new ArtifactCatalogException(
                "generated artifact policy fragment exceeds " + MAX_FRAGMENT_BYTES + " byte safety limit");
        }
        try {
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, ".artifact-policy-", ".tmp");
            try {
                Files.write(temporary, content);
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException ex) {
            throw new ArtifactCatalogException("could not atomically write artifact policy fragment", ex);
        }
    }

    static String render(List<ApprovedArtifact> importedArtifacts) {
        ArrayList<ApprovedArtifact> sorted = new ArrayList<>(importedArtifacts);
        sorted.sort(Comparator.naturalOrder());

        Map<String, Map<String, List<ApprovedArtifact>>> byMod = new LinkedHashMap<>();
        for (ApprovedArtifact artifact : sorted) {
            byMod.computeIfAbsent(artifact.modId(), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(artifact.version(), ignored -> new ArrayList<>())
                .add(artifact);
        }

        StringBuilder out = new StringBuilder();
        out.append("# Guardian artifact-import policy fragment.\n");
        out.append("# Generated from the JARs currently present in artifact-import/.\n");
        out.append("# This file is NOT loaded automatically and does not modify policy.yml.\n");
        out.append("# Review, then copy the indented rule blocks below profiles.<profile>.mods.rules:.\n");
        out.append("# Direct SHA-256 values are emitted so the copied rules do not depend on artifacts.yml.\n");
        if (sorted.isEmpty()) {
            out.append("# No candidate JARs were present during the last scan.\n");
            return out.toString();
        }

        for (Map.Entry<String, Map<String, List<ApprovedArtifact>>> modEntry : byMod.entrySet()) {
            String modId = modEntry.getKey();
            out.append("        ").append(ruleId(modId)).append(":\n");
            out.append("          mod: ").append(modId).append("\n");
            out.append("          action: ALLOW\n");
            out.append("          accept:\n");
            for (Map.Entry<String, List<ApprovedArtifact>> versionEntry : modEntry.getValue().entrySet()) {
                out.append("            - version: \"").append(escape(versionEntry.getKey())).append("\"\n");
                out.append("              verification: HASH_REQUIRED\n");
                out.append("              sha256:\n");
                for (ApprovedArtifact artifact : versionEntry.getValue()) {
                    out.append("                - \"").append(artifact.sha256().hex()).append("\"\n");
                }
            }
        }
        return out.toString();
    }

    static String ruleId(String modId) {
        String direct = "allow-" + modId;
        if (direct.length() <= 64) return direct;
        try {
            String suffix = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(modId.getBytes(StandardCharsets.UTF_8)), 0, 6);
            int prefixLength = 64 - "allow-".length() - 1 - suffix.length();
            return "allow-" + modId.substring(0, prefixLength) + "-" + suffix;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
