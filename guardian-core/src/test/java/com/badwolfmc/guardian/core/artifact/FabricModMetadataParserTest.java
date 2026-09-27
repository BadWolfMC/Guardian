package com.badwolfmc.guardian.core.artifact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FabricModMetadataParserTest {
    @Test
    void readsOnlyRequiredIdentityFromRealisticMetadataShape() throws Exception {
        var metadata = FabricModMetadataParser.parse("""
            {
              "schemaVersion": 1,
              "id": "example_mod",
              "version": "1.2.3+mc26.2",
              "name": "Example",
              "entrypoints": {"client": ["example.Client"]},
              "depends": {"fabricloader": ">=0.19.0"}
            }
            """);
        assertEquals("example_mod", metadata.modId());
        assertEquals("1.2.3+mc26.2", metadata.version());
    }

    @Test
    void matchesFabricMetadataCompatibilityForLiteralNewlinesAndDuplicateKeys() throws Exception {
        var metadata = FabricModMetadataParser.parse("""
            {
              "schemaVersion": 1,
              "id": "old-id",
              "id": "actual-id",
              "version": "1.2.3",
              "description": "line one
            line two"
            }
            """);
        assertEquals("actual-id", metadata.modId());
        assertEquals("1.2.3", metadata.version());
    }

    @Test
    void rejectsUnsupportedSchemaAndInvalidIdentity() {
        assertThrows(ArtifactCatalogException.class,
            () -> FabricModMetadataParser.parse("{\"schemaVersion\":2,\"id\":\"aa\",\"version\":\"1\"}"));
        assertThrows(ArtifactCatalogException.class,
            () -> FabricModMetadataParser.parse("{\"schemaVersion\":1,\"id\":\"Bad.ID\",\"version\":\"1\"}"));
        assertThrows(ArtifactCatalogException.class,
            () -> FabricModMetadataParser.parse("{\"schemaVersion\":1,\"id\":\"aa\"}"));
    }
}
