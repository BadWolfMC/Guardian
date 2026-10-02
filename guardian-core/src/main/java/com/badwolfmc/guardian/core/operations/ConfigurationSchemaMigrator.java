package com.badwolfmc.guardian.core.operations;

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.schema.CoreSchema;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Narrow one-time schema 1 -> 2 migration for the first public Guardian release. */
public final class ConfigurationSchemaMigrator {
    public static final int PUBLIC_SCHEMA_VERSION = 2;
    private static final DateTimeFormatter BACKUP_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final Pattern SCHEMA_ONE_LINE = Pattern.compile(
        "(?m)^(schema-version[ \\t]*:[ \\t]*)1([ \\t]*(?:#.*)?)(\\r?)(?=\\n|$)");

    public enum Surface { PAPER_CONFIG, VELOCITY_CONFIG, ADMISSION_POLICY }

    public record PreparedMigration(Path path, byte[] originalBytes, byte[] migratedBytes, int fromSchema, int toSchema) {
        public PreparedMigration { originalBytes = originalBytes.clone(); migratedBytes = migratedBytes.clone(); }
        @Override public byte[] originalBytes() { return originalBytes.clone(); }
        @Override public byte[] migratedBytes() { return migratedBytes.clone(); }
    }
    public record PublishedMigration(int fromSchema, int toSchema, Path backupPath) { }
    private ConfigurationSchemaMigrator() { }

    public static Optional<PreparedMigration> prepare(Path path, int maxBytes, Surface surface) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        byte[] original = SafeRegularFile.read(path, maxBytes);
        String source;
        Map<String, Object> root;
        try {
            source = decodeUtf8(original);
            root = parseRoot(source, maxBytes);
        } catch (RuntimeException | CharacterCodingException ex) {
            return Optional.empty();
        }
        Object schemaRaw = root.get("schema-version");
        if (!(schemaRaw instanceof Number number) || number.doubleValue() != number.intValue()) return Optional.empty();
        if (number.intValue() != 1) return Optional.empty();

        Matcher schemaLine = SCHEMA_ONE_LINE.matcher(source);
        if (!schemaLine.find()) throw new IOException("schema-version 1 is valid YAML but is not in the supported top-level scalar form");
        String migrated = schemaLine.replaceFirst("$1" + PUBLIC_SCHEMA_VERSION + "$2$3");
        if (surface == Surface.ADMISSION_POLICY && !root.containsKey("cerberus-release-trust")) {
            String newline = source.contains("\r\n") ? "\r\n" : "\n";
            if (!migrated.endsWith("\n") && !migrated.endsWith("\r")) migrated += newline;
            migrated += newline
                + "# Added by Guardian's public schema 1 -> 2 migration. Keep false unless signed" + newline
                + "# Cerberus release identity is intentionally required for this deployment." + newline
                + "cerberus-release-trust:" + newline
                + "  required: false" + newline
                + "  ed25519-public-keys: []" + newline;
        }
        byte[] rendered = migrated.getBytes(StandardCharsets.UTF_8);
        if (rendered.length < 1 || rendered.length > maxBytes) throw new IOException("migrated configuration exceeds " + maxBytes + " byte safety limit");
        Map<String,Object> reparsed = parseRoot(migrated, maxBytes);
        Object reparsedSchema = reparsed.get("schema-version");
        if (!(reparsedSchema instanceof Number reparsedNumber) || reparsedNumber.intValue() != PUBLIC_SCHEMA_VERSION) {
            throw new IOException("migrated configuration did not reparse as public schema " + PUBLIC_SCHEMA_VERSION);
        }
        return Optional.of(new PreparedMigration(path, original, rendered, 1, PUBLIC_SCHEMA_VERSION));
    }

    public static PublishedMigration publish(PreparedMigration migration) throws IOException { return publish(migration, Clock.systemUTC()); }
    static PublishedMigration publish(PreparedMigration migration, Clock clock) throws IOException {
        Path path = migration.path().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path)) throw new IOException("refusing to migrate a symbolic-link configuration file");
        byte[] current = SafeRegularFile.read(path, Math.max(migration.originalBytes().length, 1));
        if (!Arrays.equals(current, migration.originalBytes())) throw new IOException("administrator file changed after migration was prepared; retry startup");
        Path parent = path.getParent();
        if (parent == null) throw new IOException("configuration file has no parent directory");
        Path backup = nextBackupPath(path, clock.instant());
        Files.write(backup, migration.originalBytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Path temporary = Files.createTempFile(parent, ".guardian-schema-", ".tmp");
        boolean published = false;
        try {
            Files.write(temporary, migration.migratedBytes(), StandardOpenOption.TRUNCATE_EXISTING);
            byte[] beforePublish = SafeRegularFile.read(path, Math.max(migration.originalBytes().length, 1));
            if (!Arrays.equals(beforePublish, migration.originalBytes())) {
                throw new IOException("administrator file changed while migration was being published; retry startup");
            }
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            published = true;
        } finally { if (!published) Files.deleteIfExists(temporary); }
        return new PublishedMigration(migration.fromSchema(), migration.toSchema(), backup);
    }

    private static Path nextBackupPath(Path path, Instant now) throws IOException {
        String timestamp = BACKUP_TIMESTAMP.format(now); Path parent = path.getParent();
        String base = path.getFileName() + ".pre-schema2-" + timestamp + ".bak"; Path candidate = parent.resolve(base);
        for (int i=0; Files.exists(candidate, LinkOption.NOFOLLOW_LINKS); i++) {
            if (i >= 999) throw new IOException("could not allocate a unique schema-migration backup path");
            candidate = parent.resolve(base + "." + (i+1));
        }
        return candidate;
    }
    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static Map<String,Object> parseRoot(String source, int maxBytes) {
        LoadSettings settings = LoadSettings.builder().setSchema(new CoreSchema()).setCodePointLimit(maxBytes).setMaxAliasesForCollections(0).setAllowDuplicateKeys(false).build();
        Object raw = new Load(settings).loadFromString(source);
        if (!(raw instanceof Map<?,?> map)) throw new IllegalArgumentException("root must be a YAML mapping");
        LinkedHashMap<String,Object> result = new LinkedHashMap<>();
        for (Map.Entry<?,?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("root contains non-string key");
            result.put(key, entry.getValue());
        }
        return result;
    }
}
