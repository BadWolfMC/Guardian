package com.badwolfmc.guardian.core.operations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationSchemaMigratorTest {
    @TempDir Path temp;
    @Test void phase6PolicyMigratesToPublicSchemaAndBackfillsReleaseTrust() throws Exception {
        Path path=temp.resolve("policy.yml");
        String original="""
            # administrator comment must survive
            schema-version: 1 # keep this comment too
            default-profile: staff
            identity-overrides: {}
            profiles:
              staff:
                priority: 42
                clients:
                  bedrock: ALLOW
            """;
        Files.writeString(path, original, StandardCharsets.UTF_8);
        var prepared=ConfigurationSchemaMigrator.prepare(path,64*1024,ConfigurationSchemaMigrator.Surface.ADMISSION_POLICY).orElseThrow();
        String migrated=new String(prepared.migratedBytes(),StandardCharsets.UTF_8);
        assertTrue(migrated.contains("schema-version: 2 # keep this comment too"));
        assertTrue(migrated.contains("default-profile: staff")); assertTrue(migrated.contains("priority: 42"));
        assertTrue(migrated.contains("cerberus-release-trust:")); assertTrue(migrated.contains("required: false")); assertTrue(migrated.contains("ed25519-public-keys: []"));
        assertTrue(migrated.contains("# administrator comment must survive"));
        assertEquals(original,Files.readString(path));
    }
    @Test void publishPreservesExactOriginalBytes() throws Exception {
        Path path=temp.resolve("config.yml"); byte[] original="schema-version: 1\r\nserver-name: \"alpha\"\r\n".getBytes(StandardCharsets.UTF_8); Files.write(path,original);
        var prepared=ConfigurationSchemaMigrator.prepare(path,64*1024,ConfigurationSchemaMigrator.Surface.PAPER_CONFIG).orElseThrow();
        var published=ConfigurationSchemaMigrator.publish(prepared,Clock.fixed(Instant.parse("2026-10-01T12:34:56Z"),ZoneOffset.UTC));
        assertEquals(1,published.fromSchema()); assertEquals(2,published.toSchema()); assertArrayEquals(original,Files.readAllBytes(published.backupPath()));
        assertTrue(Files.readString(path).contains("schema-version: 2")); assertTrue(Files.readString(path).contains("server-name: \"alpha\""));
    }
    @Test void currentAndNewerSchemasAreNotRewritten() throws Exception {
        Path current=temp.resolve("current.yml"); Files.writeString(current,"schema-version: 2\n"); assertTrue(ConfigurationSchemaMigrator.prepare(current,1024,ConfigurationSchemaMigrator.Surface.PAPER_CONFIG).isEmpty());
        Path newer=temp.resolve("newer.yml"); Files.writeString(newer,"schema-version: 3\n"); assertTrue(ConfigurationSchemaMigrator.prepare(newer,1024,ConfigurationSchemaMigrator.Surface.PAPER_CONFIG).isEmpty()); assertEquals("schema-version: 3\n",Files.readString(newer));
    }
    @Test void malformedYamlIsLeftForNormalRecoveryPath() throws Exception {
        Path path=temp.resolve("broken.yml"); String broken="schema-version: [\n"; Files.writeString(path,broken); assertTrue(ConfigurationSchemaMigrator.prepare(path,1024,ConfigurationSchemaMigrator.Surface.VELOCITY_CONFIG).isEmpty()); assertEquals(broken,Files.readString(path));
    }
    @Test void publishFailsClosedIfAdministratorFileChangesAfterPreparation() throws Exception {
        Path path=temp.resolve("config.yml"); Files.writeString(path,"schema-version: 1\nserver-name: \"alpha\"\n"); var prepared=ConfigurationSchemaMigrator.prepare(path,1024,ConfigurationSchemaMigrator.Surface.PAPER_CONFIG).orElseThrow(); Files.writeString(path,"schema-version: 1\nserver-name: \"beta\"\n");
        assertThrows(java.io.IOException.class,()->ConfigurationSchemaMigrator.publish(prepared)); assertTrue(Files.readString(path).contains("beta"));
    }
}
