package com.badwolfmc.guardian.core.artifact;

import com.badwolfmc.guardian.core.policy.AdmissionPolicyLoader;
import com.badwolfmc.guardian.protocol.ArtifactSha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactCatalogWorkflowTest {
    @TempDir Path temp;

    @Test
    void scannerUsesFabricMetadataNotFilenameAndSupportsMultipleExactArtifacts() throws Exception {
        Path approved = temp.resolve("artifact-import");
        Files.createDirectories(approved);
        writeFabricJar(approved.resolve("not-sodium-at-all.jar"), "sodium", "0.9.1+mc26.2", "build-a");
        writeFabricJar(approved.resolve("second-copy.jar"), "sodium", "0.9.1+mc26.2", "build-b");
        writeFabricJar(approved.resolve("future.jar"), "sodium", "0.9.2+mc26.2", "build-c");

        ApprovedArtifactScanner.ScanResult result = new ApprovedArtifactScanner().scan(approved);
        assertEquals(3, result.jarCount());
        assertEquals(3, result.artifacts().size());
        assertTrue(result.artifacts().stream().allMatch(entry -> entry.modId().equals("sodium")));
        assertEquals(List.of("0.9.1+mc26.2", "0.9.1+mc26.2", "0.9.2+mc26.2"),
            result.artifacts().stream().map(ApprovedArtifact::version).toList());
        assertEquals(2, result.artifacts().stream()
            .filter(entry -> entry.version().equals("0.9.1+mc26.2"))
            .map(ApprovedArtifact::sha256).distinct().count());

        String fragment = ArtifactPolicyFragmentStore.render(result.artifacts());
        assertTrue(fragment.contains("allow-sodium:"));
        assertTrue(fragment.contains("verification: HASH_REQUIRED"));
        assertTrue(fragment.contains(result.artifacts().getFirst().sha256().hex()));
        assertFalse(fragment.contains("catalog: true"),
            "generated copy/paste rules must use direct hashes rather than catalog references");

        Path policy = temp.resolve("policy.yml");
        Files.writeString(policy, """
            schema-version: 1
            default-profile: default
            identity-overrides: {}
            profiles:
              default:
                priority: 0
                clients:
                  bedrock: ALLOW
                  vanilla: ALLOW
                  optifine: ALLOW
                  fabric: REQUIRE_CERBERUS
                  unknown: DENY
                unknown-brands:
                  mode: ALLOWLIST
                  brands: []
                mods:
                  mode: ALLOWLIST
                  origins:
                    directory: DENY
                    mixed-or-unknown: DENY
                  baseline: []
                  required: {}
                  rules:
            """ + fragment.lines()
                .filter(line -> !line.startsWith("#") && !line.isBlank())
                .collect(java.util.stream.Collectors.joining("\n", "", "\n")));
        var snapshot = new AdmissionPolicyLoader().load(policy, temp.resolve("missing-artifacts.yml"));
        assertTrue(snapshot.defaultProfile().modPolicy().rulesByModId().containsKey("sodium"));
    }

    @Test
    void generatedRuleIdIsDeterministicAndNeverExceedsPolicyLimit() {
        String maxModId = "a" + "b".repeat(63);
        String first = ArtifactPolicyFragmentStore.ruleId(maxModId);
        String second = ArtifactPolicyFragmentStore.ruleId(maxModId);
        assertEquals(first, second);
        assertTrue(first.startsWith("allow-"));
        assertEquals(64, first.length());
        assertTrue(first.matches("[a-z0-9._-]{1,64}"));
    }

    @Test
    void rescanningIsIdempotentAndDeletedInputsDoNotDeleteCatalogHistory() throws Exception {
        ArtifactImportService service = new ArtifactImportService(temp);
        service.ensureInputDirectory();
        Path approved = temp.resolve("artifact-import");
        Path v1 = approved.resolve("first.jar");
        writeFabricJar(v1, "examplemod", "1.0.0", "one");

        ArtifactImportResult first = service.scanAndMerge();
        assertTrue(Files.readString(temp.resolve("artifact-import-rules.yml")).contains("allow-examplemod:"));
        assertEquals(1, first.addedCatalogEntries());
        assertEquals(1, first.totalCatalogEntries());
        assertTrue(first.catalogChanged());

        ArtifactImportResult second = service.scanAndMerge();
        assertEquals(0, second.addedCatalogEntries());
        assertEquals(1, second.totalCatalogEntries());
        assertFalse(second.catalogChanged());

        writeFabricJar(approved.resolve("second.jar"), "examplemod", "2.0.0", "two");
        ArtifactImportResult third = service.scanAndMerge();
        assertEquals(1, third.addedCatalogEntries());
        assertEquals(2, third.totalCatalogEntries());

        Files.delete(v1);
        Files.delete(approved.resolve("second.jar"));
        ArtifactImportResult fourth = service.scanAndMerge();
        assertEquals(0, fourth.scannedJars());
        assertEquals(0, fourth.addedCatalogEntries());
        assertEquals(2, fourth.totalCatalogEntries());
        assertFalse(fourth.catalogChanged());

        assertEquals(2, new ArtifactCatalogStore(temp.resolve("artifacts.yml")).load().size());
    }

    @Test
    void anyMalformedCandidateRejectsWholeImportWithoutCatalogMutation() throws Exception {
        ArtifactImportService service = new ArtifactImportService(temp);
        service.ensureInputDirectory();
        Path approved = temp.resolve("artifact-import");
        writeFabricJar(approved.resolve("accepted.jar"), "accepted", "1.0.0", "one");
        service.scanAndMerge();
        byte[] before = Files.readAllBytes(temp.resolve("artifacts.yml"));

        writeFabricJar(approved.resolve("new-valid.jar"), "newvalid", "1.0.0", "two");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(approved.resolve("not-fabric.jar")))) {
            put(zip, "readme.txt", "not a Fabric mod");
        }

        ArtifactCatalogException error = assertThrows(ArtifactCatalogException.class, service::scanAndMerge);
        assertTrue(error.getMessage().contains("not-fabric.jar"));
        assertTrue(error.getMessage().contains("catalog was not modified"));
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(temp.resolve("artifacts.yml"))));
        assertEquals(1, service.validateCatalog().size());
    }

    @Test
    void malformedArchiveAndOversizedMetadataAreRejected() throws Exception {
        Path approved = temp.resolve("artifact-import");
        Files.createDirectories(approved);
        Files.writeString(approved.resolve("broken.jar"), "this is not a zip");
        assertThrows(ArtifactCatalogException.class, () -> new ApprovedArtifactScanner().scan(approved));

        Files.delete(approved.resolve("broken.jar"));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(approved.resolve("oversized.jar")))) {
            put(zip, "fabric.mod.json", " ".repeat(ApprovedArtifactScanner.MAX_FABRIC_METADATA_BYTES + 1));
        }
        ArtifactCatalogException error = assertThrows(
            ArtifactCatalogException.class,
            () -> new ApprovedArtifactScanner().scan(approved)
        );
        assertTrue(error.getMessage().contains("safety limit"));

        Files.delete(approved.resolve("oversized.jar"));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(approved.resolve("too-many-entries.jar")))) {
            put(zip, "fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"entrybound\",\"version\":\"1\"}");
            for (int i = 0; i < 8; i++) {
                put(zip, "fixture/entry-" + i, "");
            }
        }
        ArtifactCatalogException entriesError = assertThrows(
            ArtifactCatalogException.class,
            () -> new ApprovedArtifactScanner(8).scan(approved)
        );
        assertTrue(entriesError.getMessage().contains("entries; maximum is 8"));
    }

    @Test
    void scannerIsFlatAndIgnoresNonJarFilesAndNestedTrees() throws Exception {
        Path approved = temp.resolve("artifact-import");
        Files.createDirectories(approved.resolve("nested"));
        Files.writeString(approved.resolve("notes.txt"), "administrator note");
        writeFabricJar(approved.resolve("root.jar"), "rootmod", "1", "root");
        writeFabricJar(approved.resolve("nested/ignored.jar"), "nestedmod", "1", "nested");

        ApprovedArtifactScanner.ScanResult result = new ApprovedArtifactScanner().scan(approved);
        assertEquals(1, result.jarCount());
        assertEquals("rootmod", result.artifacts().getFirst().modId());
    }

    @Test
    void scannerRejectsCandidateCountAboveBoundBeforeArchiveInspection() throws Exception {
        Path approved = temp.resolve("artifact-import");
        Files.createDirectories(approved);
        for (int i = 0; i <= ApprovedArtifactScanner.MAX_IMPORT_JARS; i++) {
            Files.write(approved.resolve("candidate-%03d.jar".formatted(i)), new byte[0]);
        }

        ArtifactCatalogException error = assertThrows(
            ArtifactCatalogException.class,
            () -> new ApprovedArtifactScanner().scan(approved)
        );
        assertTrue(error.getMessage().contains("maximum is " + ApprovedArtifactScanner.MAX_IMPORT_JARS));
    }

    @Test
    void catalogOutputIsDeterministicAndValidatesDuplicateInput() throws Exception {
        ApprovedArtifact a = artifact("zmod", "2", 2);
        ApprovedArtifact b = artifact("amod", "1", 1);
        ApprovedArtifact c = artifact("amod", "1", 3);
        String first = ArtifactCatalogStore.render(ArtifactCatalog.of(List.of(a, b, c)));
        String second = ArtifactCatalogStore.render(ArtifactCatalog.of(List.of(c, a, b)));
        assertEquals(first, second);
        assertTrue(first.indexOf("  amod:") < first.indexOf("  zmod:"));
        assertEquals(3, ArtifactCatalogStore.parse(first).size());

        String duplicateHash = """
            schema-version: 1
            artifacts:
              amod:
                versions:
                  "1":
                    sha256:
                      - "%s"
                      - "%s"
            """.formatted(b.sha256().hex(), b.sha256().hex());
        assertThrows(ArtifactCatalogException.class, () -> ArtifactCatalogStore.parse(duplicateHash));

        String duplicateVersion = """
            schema-version: 1
            artifacts:
              amod:
                versions:
                  "1":
                    sha256:
                      - "%s"
                  "1":
                    sha256:
                      - "%s"
            """.formatted(b.sha256().hex(), c.sha256().hex());
        assertThrows(ArtifactCatalogException.class, () -> ArtifactCatalogStore.parse(duplicateVersion));
    }


    @Test
    void malformedCatalogUtf8IsRejectedBeforeMutation() throws Exception {
        ArtifactImportService service = new ArtifactImportService(temp);
        service.ensureInputDirectory();
        Files.write(temp.resolve("artifacts.yml"), new byte[] {(byte) 0xC3, (byte) 0x28});
        writeFabricJar(temp.resolve("artifact-import/new.jar"), "newmod", "1", "new");

        ArtifactCatalogException error = assertThrows(ArtifactCatalogException.class, service::scanAndMerge);
        assertTrue(error.getMessage().contains("not valid UTF-8"));
        assertEquals(2, Files.size(temp.resolve("artifacts.yml")));
    }

    @Test
    void generatedCatalogExplicitlyOwnsFormattingAndSeparatesPolicy() {
        String rendered = ArtifactCatalogStore.render(ArtifactCatalog.empty());
        assertTrue(rendered.contains("comments/formatting are not preserved"));
        assertTrue(rendered.contains("Policy decisions belong in Phase 3 policy files"));
    }

    private static ApprovedArtifact artifact(String id, String version, int seed) {
        byte[] bytes = new byte[ArtifactSha256.BYTES];
        java.util.Arrays.fill(bytes, (byte) seed);
        return new ApprovedArtifact(id, version, ArtifactSha256.fromBytes(bytes));
    }

    private static void writeFabricJar(Path path, String id, String version, String marker) throws IOException {
        Files.createDirectories(path.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            String metadata = "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + version + "\"}";
            put(zip, "fabric.mod.json", metadata);
            put(zip, "fixture/" + marker + ".txt", marker);
        }
    }

    private static void put(ZipOutputStream zip, String name, String value) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
