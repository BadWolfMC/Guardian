package com.badwolfmc.cerberus.manifest;

import com.badwolfmc.guardian.protocol.OriginKind;
import net.fabricmc.loader.api.metadata.ModOrigin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricManifestCollectorOriginTest {
    @TempDir
    Path tempDir;

    @Test
    void singleRegularPathIsArchiveAndRetainsOnlyPrivateHashPath() throws IOException {
        Path jar = Files.writeString(tempDir.resolve("example.jar"), "bytes");

        var observed = FabricManifestCollector.classifyOrigin(
            "fabric", null, ModOrigin.Kind.PATH, null, List.of(jar));

        assertEquals(OriginKind.ARCHIVE, observed.originKind());
        assertEquals(jar, observed.archivePath());
        assertNull(observed.parentModId());
    }

    @Test
    void directoryDevelopmentOriginIsExplicitlyUnhashed() throws IOException {
        Path classes = Files.createDirectory(tempDir.resolve("classes"));

        var observed = FabricManifestCollector.classifyOrigin(
            "fabric", null, ModOrigin.Kind.PATH, null, List.of(classes));

        assertEquals(OriginKind.DIRECTORY, observed.originKind());
        assertNull(observed.archivePath());
        assertNull(observed.parentModId());
    }

    @Test
    void multipleDevelopmentPathsAreConservativeMixedOrigin() throws IOException {
        Path first = Files.createDirectory(tempDir.resolve("classes-main"));
        Path second = Files.createDirectory(tempDir.resolve("classes-client"));

        var observed = FabricManifestCollector.classifyOrigin(
            "fabric", null, ModOrigin.Kind.PATH, null, List.of(first, second));

        assertEquals(OriginKind.MIXED_OR_UNKNOWN, observed.originKind());
        assertNull(observed.archivePath());
    }

    @Test
    void symbolicLinkPathNeverAcquiresArchiveIdentity() throws IOException {
        Path jar = Files.writeString(tempDir.resolve("target.jar"), "bytes");
        Path link = tempDir.resolve("linked.jar");
        try {
            Files.createSymbolicLink(link, jar.getFileName());
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            return; // Platform does not permit symlink creation; other origin tests still cover the contract.
        }

        var observed = FabricManifestCollector.classifyOrigin(
            "fabric", null, ModOrigin.Kind.PATH, null, List.of(link));

        assertEquals(OriginKind.MIXED_OR_UNKNOWN, observed.originKind());
        assertNull(observed.archivePath());
    }

    @Test
    void nestedOriginRequiresMatchingContainerAndLoaderParent() {
        var observed = FabricManifestCollector.classifyOrigin(
            "fabric", "parent", ModOrigin.Kind.NESTED, "parent", List.of());

        assertEquals(OriginKind.NESTED, observed.originKind());
        assertEquals("parent", observed.parentModId());
        assertNull(observed.archivePath());
    }

    @Test
    void inconsistentNestedRelationshipsLoseNestedPolicyTreatment() {
        var missingContainer = FabricManifestCollector.classifyOrigin(
            "fabric", null, ModOrigin.Kind.NESTED, "parent", List.of());
        var mismatchedParent = FabricManifestCollector.classifyOrigin(
            "fabric", "parent", ModOrigin.Kind.NESTED, "different", List.of());
        var pathWithContainer = FabricManifestCollector.classifyOrigin(
            "fabric", "parent", ModOrigin.Kind.PATH, null, List.of(tempDir.resolve("anything.jar")));

        for (var observed : List.of(missingContainer, mismatchedParent, pathWithContainer)) {
            assertEquals(OriginKind.MIXED_OR_UNKNOWN, observed.originKind());
            assertNull(observed.parentModId());
            assertNull(observed.archivePath());
        }
    }

    @Test
    void builtinClassificationDoesNotDependOnOrdinaryArchivePathShape() throws IOException {
        Path one = Files.writeString(tempDir.resolve("client.jar"), "one");
        Path two = Files.writeString(tempDir.resolve("common.jar"), "two");

        var observed = FabricManifestCollector.classifyOrigin(
            "builtin", null, ModOrigin.Kind.PATH, null, List.of(one, two));

        assertEquals(OriginKind.BUILTIN, observed.originKind());
        assertNull(observed.parentModId());
        assertNull(observed.archivePath());
    }

    @Test
    void builtinCannotMasqueradeAsNestedAndUnknownKindsRemainMixed() {
        var builtinNested = FabricManifestCollector.classifyOrigin(
            "builtin", "parent", ModOrigin.Kind.NESTED, "parent", List.of());
        var unknown = FabricManifestCollector.classifyOrigin(
            "fabric", null, ModOrigin.Kind.UNKNOWN, null, List.of());

        assertEquals(OriginKind.MIXED_OR_UNKNOWN, builtinNested.originKind());
        assertEquals(OriginKind.MIXED_OR_UNKNOWN, unknown.originKind());
        assertTrue(builtinNested.parentModId() == null && unknown.parentModId() == null);
    }
}
