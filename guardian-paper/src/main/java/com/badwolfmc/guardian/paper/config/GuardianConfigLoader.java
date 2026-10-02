package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.core.operations.OperationalLogLevel;
import com.badwolfmc.guardian.core.operations.ProxyAssertionSecret;
import com.badwolfmc.guardian.core.operations.ProxyAssertionSecretResolver;
import com.badwolfmc.guardian.core.operations.SafeRegularFile;
import com.badwolfmc.guardian.core.operations.GuardianServerChallengeKeyResolver;
import com.badwolfmc.guardian.core.operations.GuardianServerChallengeSigner;

import com.badwolfmc.guardian.paper.PaperAuthorityMode;
import com.badwolfmc.guardian.protection.ProtectionPolicy;
import com.badwolfmc.guardian.protection.ProtectionRule;
import com.badwolfmc.guardian.protection.ProtectionRuleMode;
import com.badwolfmc.guardian.protection.ProtectionSurface;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class GuardianConfigLoader {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_CONFIG_BYTES = 64 * 1024;
    private static final int MAX_HANDSHAKE_SECONDS = (int) (GuardianProtocol.MAX_HANDSHAKE_MILLIS / 1000L);
    private static final int MAX_LOCALE_ID_LENGTH = 32;

    public GuardianPaperSettings load(Path path) throws GuardianConfigurationException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw error(path, GuardianConfigurationException.Kind.MISSING, "file is missing");
        }

        final String configText;
        try {
            configText = SafeRegularFile.readUtf8(path, MAX_CONFIG_BYTES);
        } catch (IOException ex) {
            throw new GuardianConfigurationException(path, GuardianConfigurationException.Kind.INVALID,
                "configuration must be a stable regular non-symlink UTF-8 file: " + ex.getMessage(), ex);
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(configText);
        } catch (InvalidConfigurationException ex) {
            throw new GuardianConfigurationException(path, GuardianConfigurationException.Kind.MALFORMED,
                "malformed YAML: " + ex.getMessage(), ex);
        }

        int schema = requireInt(yaml, path, "schema-version");
        if (schema != SCHEMA_VERSION) {
            String relation = schema > SCHEMA_VERSION ? "newer unsupported" : "older unsupported";
            throw error(path, GuardianConfigurationException.Kind.UNSUPPORTED_SCHEMA,
                "schema-version " + schema + " is " + relation
                    + "; supported schema-version is " + SCHEMA_VERSION);
        }

        boolean admissionEnabled = requireBoolean(yaml, path, "features.admission.enabled");
        boolean protectionEnabled = requireBoolean(yaml, path, "features.protection.enabled");
        String serverName = optionalStringAllowBlank(yaml, path, "server-name", "");
        final OperationalLogLevel loggingLevel;
        try {
            loggingLevel = OperationalLogLevel.parse(optionalString(yaml, path, "logging.level", "NORMAL"));
        } catch (IllegalArgumentException ex) {
            throw error(path, ex.getMessage());
        }
        String locale = requireString(yaml, path, "locale.default").toLowerCase(Locale.ROOT);
        if (!locale.matches("[a-z0-9_-]{2," + MAX_LOCALE_ID_LENGTH + "}")) {
            throw error(path, "locale.default must match [a-z0-9_-]{2," + MAX_LOCALE_ID_LENGTH + "}");
        }
        String helpUrl = requireString(yaml, path, "messages.help-url");

        final PaperAuthorityMode authority;
        try {
            authority = PaperAuthorityMode.parse(requireString(yaml, path, "admission.authority"));
        } catch (IllegalArgumentException ex) {
            throw error(path, ex.getMessage());
        }

        ProxyAssertionSecret proxySecret = null;
        if (admissionEnabled && authority == PaperAuthorityMode.VELOCITY) {
            try {
                Path dataDirectory = path.toAbsolutePath().normalize().getParent();
                if (dataDirectory == null) throw new IllegalArgumentException("config path has no parent directory");
                proxySecret = ProxyAssertionSecretResolver.resolveFile(
                    dataDirectory, ProxyAssertionSecretResolver.DEFAULT_KEY_FILE);
            } catch (IllegalArgumentException ex) {
                throw error(path, GuardianConfigurationException.Kind.EXTERNAL_DEPENDENCY,
                    "proxy assertion key invalid: " + ex.getMessage()
                        + ". Copy proxy-assertion.key from the Guardian-Velocity data directory "
                        + "into this Paper server's plugins/Guardian/ directory.");
            }
        }

        int timeoutSeconds = requireInt(yaml, path, "admission.standalone.handshake-timeout-seconds");
        if (timeoutSeconds < 1 || timeoutSeconds > MAX_HANDSHAKE_SECONDS) {
            throw error(path, "admission.standalone.handshake-timeout-seconds must be between 1 and "
                + MAX_HANDSHAKE_SECONDS);
        }
        int challengeWait = requireInt(yaml, path, "admission.standalone.challenge-channel-wait-ticks");
        int maxWait = timeoutSeconds * 20;
        if (challengeWait < 1 || challengeWait > maxWait) {
            throw error(path, "admission.standalone.challenge-channel-wait-ticks must be between 1 and " + maxWait);
        }

        GuardianServerChallengeSigner serverChallengeSigner = null;
        boolean serverAuthenticationEnabled = requireBoolean(
            yaml, path, "admission.standalone.server-authentication.enabled");
        if (serverAuthenticationEnabled && admissionEnabled && authority == PaperAuthorityMode.STANDALONE) {
            try {
                Path dataDirectory = path.toAbsolutePath().normalize().getParent();
                if (dataDirectory == null) throw new IllegalArgumentException("config path has no parent directory");
                serverChallengeSigner = GuardianServerChallengeKeyResolver.resolveFile(dataDirectory);
            } catch (IllegalArgumentException ex) {
                throw error(path, GuardianConfigurationException.Kind.EXTERNAL_DEPENDENCY,
                    "Guardian server authentication key invalid: " + ex.getMessage());
            }
        }

        final ProtectionPolicy protectionPolicy;
        try {
            protectionPolicy = new ProtectionPolicy(
                ProtectionRule.create(
                    ProtectionSurface.COMMAND_EXECUTION,
                    requireBoolean(yaml, path, "protection.execution.enabled"),
                    ProtectionRuleMode.DENYLIST,
                    requireStringList(yaml, path, "protection.execution.blocked-roots")
                ),
                ProtectionRule.create(
                    ProtectionSurface.COMMAND_VISIBILITY,
                    requireBoolean(yaml, path, "protection.visibility.enabled"),
                    requireEnum(yaml, path, "protection.visibility.mode", ProtectionRuleMode.class),
                    requireStringList(yaml, path, "protection.visibility.roots")
                ),
                ProtectionRule.create(
                    ProtectionSurface.NAMESPACED_COMMAND,
                    requireBoolean(yaml, path, "protection.namespaces.enabled"),
                    requireEnum(yaml, path, "protection.namespaces.mode", ProtectionRuleMode.class),
                    requireStringList(yaml, path, "protection.namespaces.roots")
                ),
                requireBoolean(yaml, path, "protection.visibility.per-command-bypass"),
                requireBoolean(yaml, path, "protection.notifications.enabled")
            );
        } catch (IllegalArgumentException ex) {
            throw error(path, "protection policy invalid: " + ex.getMessage());
        }

        return new GuardianPaperSettings(
            schema,
            admissionEnabled,
            protectionEnabled,
            serverName,
            locale,
            helpUrl,
            authority,
            timeoutSeconds,
            challengeWait,
            loggingLevel,
            proxySecret,
            serverChallengeSigner,
            protectionPolicy
        );
    }

    private static <E extends Enum<E>> E requireEnum(
        YamlConfiguration yaml, Path path, String key, Class<E> type
    ) throws GuardianConfigurationException {
        String raw = requireString(yaml, path, key).trim().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException ex) {
            throw error(path, key + " has unsupported value '" + raw + "'; expected one of "
                + List.of(type.getEnumConstants()));
        }
    }

    private static int requireInt(YamlConfiguration yaml, Path path, String key)
        throws GuardianConfigurationException {
        Object value = yaml.get(key);
        if (!(value instanceof Number number)) {
            throw error(path, key + " must be an integer");
        }
        double raw = number.doubleValue();
        int result = number.intValue();
        if (raw != result) {
            throw error(path, key + " must be an integer");
        }
        return result;
    }

    private static boolean requireBoolean(YamlConfiguration yaml, Path path, String key)
        throws GuardianConfigurationException {
        Object value = yaml.get(key);
        if (!(value instanceof Boolean bool)) {
            throw error(path, key + " must be true or false");
        }
        return bool;
    }

    private static String requireString(YamlConfiguration yaml, Path path, String key)
        throws GuardianConfigurationException {
        Object value = yaml.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw error(path, key + " must be a non-blank string");
        }
        return string.trim();
    }

    private static String optionalString(YamlConfiguration yaml, Path path, String key, String fallback)
        throws GuardianConfigurationException {
        Object value = yaml.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String string) || string.isBlank()) {
            throw error(path, key + " must be a non-blank string when configured");
        }
        return string.trim();
    }

    private static String optionalStringAllowBlank(YamlConfiguration yaml, Path path, String key, String fallback)
        throws GuardianConfigurationException {
        Object value = yaml.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String string)) {
            throw error(path, key + " must be a string when configured");
        }
        return string.trim();
    }

    private static List<String> requireStringList(YamlConfiguration yaml, Path path, String key)
        throws GuardianConfigurationException {
        Object raw = yaml.get(key);
        if (!(raw instanceof List<?> list)) {
            throw error(path, key + " must be a YAML list");
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object value = list.get(i);
            if (!(value instanceof String string) || string.isBlank()) {
                throw error(path, key + "[" + i + "] must be a non-blank string");
            }
            result.add(string.trim());
        }
        return List.copyOf(result);
    }

    private static GuardianConfigurationException error(Path path, String message) {
        return error(path, GuardianConfigurationException.Kind.INVALID, message);
    }

    private static GuardianConfigurationException error(
        Path path, GuardianConfigurationException.Kind kind, String message
    ) {
        return new GuardianConfigurationException(path, kind, message);
    }
}
