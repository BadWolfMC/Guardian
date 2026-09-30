package com.badwolfmc.guardian.velocity.config;

import com.badwolfmc.guardian.core.operations.OperationalLogLevel;
import com.badwolfmc.guardian.core.operations.ProxyAssertionSecret;
import com.badwolfmc.guardian.core.operations.ProxyAssertionSecretResolver;
import com.badwolfmc.guardian.core.operations.GuardianServerChallengeKeyResolver;
import com.badwolfmc.guardian.core.operations.GuardianServerChallengeSigner;
import com.badwolfmc.guardian.core.operations.SafeRegularFile;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.schema.CoreSchema;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict parser for Guardian-Velocity's proxy-local operational settings. */
public final class VelocityConfigLoader {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CONFIG_BYTES = 64 * 1024;
    private static final int MAX_LOCALE_ID_LENGTH = 32;
    private static final int MAX_HANDSHAKE_SECONDS = (int) (GuardianProtocol.MAX_HANDSHAKE_MILLIS / 1000L);
    private static final Set<String> ROOT_KEYS = Set.of(
        "schema-version", "deployment", "locale", "logging", "admission");
    private static final Set<String> DEPLOYMENT_KEYS = Set.of("authority");
    private static final Set<String> LOCALE_KEYS = Set.of("default");
    private static final Set<String> LOGGING_KEYS = Set.of("level");
    private static final Set<String> ADMISSION_KEYS = Set.of("handshake-timeout-seconds", "server-authentication");
    private static final Set<String> SERVER_AUTH_KEYS = Set.of("enabled");
    public VelocityOperationalSettings load(Path path) throws VelocityConfigurationException {
        Map<String, Object> root = loadYaml(path);
        rejectUnknown(root, ROOT_KEYS, path, "root");
        int schema = integer(root, "schema-version", path, "root");
        if (schema != SCHEMA_VERSION) {
            throw error(path, "unsupported schema-version " + schema + "; supported value is " + SCHEMA_VERSION);
        }

        Map<String, Object> deployment = map(required(root, "deployment", path, "root"), path, "deployment");
        rejectUnknown(deployment, DEPLOYMENT_KEYS, path, "deployment");
        String authority = string(deployment, "authority", path, "deployment").trim().toLowerCase(Locale.ROOT);
        if (!"velocity".equals(authority)) {
            throw error(path, "deployment.authority must be 'velocity' for Guardian-Velocity");
        }

        Map<String, Object> localeMap = map(required(root, "locale", path, "root"), path, "locale");
        rejectUnknown(localeMap, LOCALE_KEYS, path, "locale");
        String locale = string(localeMap, "default", path, "locale").trim().toLowerCase(Locale.ROOT);
        if (!locale.matches("[a-z0-9_-]{2," + MAX_LOCALE_ID_LENGTH + "}")) {
            throw error(path, "locale.default must match [a-z0-9_-]{2," + MAX_LOCALE_ID_LENGTH + "}");
        }

        Map<String, Object> logging = map(required(root, "logging", path, "root"), path, "logging");
        rejectUnknown(logging, LOGGING_KEYS, path, "logging");
        final OperationalLogLevel logLevel;
        try {
            logLevel = OperationalLogLevel.parse(string(logging, "level", path, "logging"));
        } catch (IllegalArgumentException ex) {
            throw error(path, ex.getMessage());
        }

        Map<String, Object> admission = map(required(root, "admission", path, "root"), path, "admission");
        rejectUnknown(admission, ADMISSION_KEYS, path, "admission");
        int timeout = integer(admission, "handshake-timeout-seconds", path, "admission");
        if (timeout < 1 || timeout > MAX_HANDSHAKE_SECONDS) {
            throw error(path, "admission.handshake-timeout-seconds must be between 1 and " + MAX_HANDSHAKE_SECONDS);
        }

        Map<String, Object> serverAuthentication = map(
            required(admission, "server-authentication", path, "admission"), path, "admission.server-authentication");
        rejectUnknown(serverAuthentication, SERVER_AUTH_KEYS, path, "admission.server-authentication");
        boolean serverAuthenticationEnabled = bool(
            serverAuthentication, "enabled", path, "admission.server-authentication");

        final ProxyAssertionSecret secret;
        try {
            Path dataDirectory = path.toAbsolutePath().normalize().getParent();
            if (dataDirectory == null) throw new IllegalArgumentException("config path has no parent directory");
            secret = ProxyAssertionSecretResolver.resolveFile(
                dataDirectory, ProxyAssertionSecretResolver.DEFAULT_KEY_FILE);
        } catch (IllegalArgumentException ex) {
            throw error(path, "proxy assertion key invalid: " + ex.getMessage());
        }

        final GuardianServerChallengeSigner serverChallengeSigner;
        if (serverAuthenticationEnabled) {
            try {
                Path dataDirectory = path.toAbsolutePath().normalize().getParent();
                if (dataDirectory == null) throw new IllegalArgumentException("config path has no parent directory");
                serverChallengeSigner = GuardianServerChallengeKeyResolver.resolveFile(dataDirectory);
            } catch (IllegalArgumentException ex) {
                throw error(path, "Guardian server authentication key invalid: " + ex.getMessage());
            }
        } else {
            serverChallengeSigner = null;
        }

        return new VelocityOperationalSettings(schema, locale, timeout, logLevel, secret, serverChallengeSigner);
    }

    private static Map<String, Object> loadYaml(Path path) throws VelocityConfigurationException {
        final String text;
        try {
            text = SafeRegularFile.readUtf8(path, MAX_CONFIG_BYTES);
        } catch (IOException ex) {
            throw new VelocityConfigurationException(path,
                "configuration must be a stable regular non-symlink UTF-8 file: " + ex.getMessage(), ex);
        }
        try {
            LoadSettings settings = LoadSettings.builder()
                .setSchema(new CoreSchema())
                .setCodePointLimit(MAX_CONFIG_BYTES)
                .setMaxAliasesForCollections(0)
                .setAllowDuplicateKeys(false)
                .build();
            Object raw = new Load(settings).loadFromString(text);
            return map(raw, path, "root");
        } catch (VelocityConfigurationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new VelocityConfigurationException(path, "malformed YAML: " + ex.getMessage(), ex);
        }
    }

    private static Object required(Map<String, Object> map, String key, Path path, String context)
        throws VelocityConfigurationException {
        if (!map.containsKey(key) || map.get(key) == null) throw error(path, context + "." + key + " is required");
        return map.get(key);
    }

    private static String string(Map<String, Object> map, String key, Path path, String context)
        throws VelocityConfigurationException {
        Object value = required(map, key, path, context);
        if (!(value instanceof String string) || string.isBlank()) {
            throw error(path, context + "." + key + " must be a non-blank string");
        }
        return string.trim();
    }

    private static boolean bool(Map<String, Object> map, String key, Path path, String context)
        throws VelocityConfigurationException {
        Object value = required(map, key, path, context);
        if (!(value instanceof Boolean bool)) throw error(path, context + "." + key + " must be true or false");
        return bool;
    }

    private static int integer(Map<String, Object> map, String key, Path path, String context)
        throws VelocityConfigurationException {
        Object value = required(map, key, path, context);
        if (!(value instanceof Number number)) throw error(path, context + "." + key + " must be an integer");
        double raw = number.doubleValue();
        int result = number.intValue();
        if (raw != result) throw error(path, context + "." + key + " must be an integer");
        return result;
    }

    private static Map<String, Object> map(Object value, Path path, String context)
        throws VelocityConfigurationException {
        if (!(value instanceof Map<?, ?> raw)) throw error(path, context + " must be a YAML mapping");
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw error(path, context + " contains a non-string key");
            result.put(key, entry.getValue());
        }
        return Map.copyOf(result);
    }

    private static void rejectUnknown(Map<String, Object> map, Set<String> allowed, Path path, String context)
        throws VelocityConfigurationException {
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) throw error(path, context + " contains unknown key '" + key + "'");
        }
    }

    private static VelocityConfigurationException error(Path path, String message) {
        return new VelocityConfigurationException(path, message);
    }
}
