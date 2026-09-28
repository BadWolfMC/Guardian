package com.badwolfmc.guardian.paper.locale;

import com.badwolfmc.guardian.paper.config.GuardianConfigurationException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class GuardianLocaleLoader {
    public static final int SCHEMA_VERSION = 1;
    public static final String FALLBACK_LOCALE = "en_us";

    public static final Set<String> REQUIRED_KEYS = Set.of(
        "admission.denied",
        "admission.proxy-assertion-required",
        "admission.proxy-assertion-invalid",
        "admission.cerberus-required",
        "admission.cerberus-timeout",
        "admission.cerberus-protocol-unsupported",
        "admission.manifest-denied",
        "admission.manifest-denied.required-mod-missing",
        "admission.manifest-denied.explicit-mod-deny",
        "admission.manifest-denied.unlisted-mod",
        "admission.manifest-denied.version-not-accepted",
        "admission.manifest-denied.artifact-not-accepted",
        "admission.manifest-denied.directory-origin",
        "admission.manifest-denied.mixed-origin",
        "admission.manifest-invalid",
        "admission.client-denied",
        "admission.configuration-error",
        "protection.command-denied",
        "protection.namespace-denied",
        "protection.notify.command-denied",
        "protection.notify.namespace-denied",
        "artifacts.command.usage",
        "artifacts.command.no-permission",
        "artifacts.scan.started",
        "artifacts.scan.already-running",
        "artifacts.scan.success",
        "artifacts.scan.unchanged",
        "artifacts.scan.failed",
        "command.usage.paper",
        "command.no-permission",
        "command.value.enabled",
        "command.value.disabled",
        "command.value.available",
        "command.value.unavailable",
        "command.value.configured",
        "command.value.not-applicable",
        "command.value.scope.paper-local",
        "command.value.scope.velocity-admission",
        "command.value.artifact.no-hash",
        "command.value.artifact.catalogued",
        "command.value.artifact.not-catalogued",
        "command.validate.success",
        "command.validate.failed",
        "command.reload.success",
        "command.reload.failed",
        "command.inspect.no-active-data",
        "command.status.paper.header",
        "command.status.paper.runtime",
        "command.status.paper.integrations",
        "command.status.paper.assertion",
        "command.status.paper.snapshots",
        "command.inspect.paper.header",
        "command.inspect.paper.standalone.client",
        "command.inspect.paper.standalone.profile",
        "command.inspect.paper.standalone.cerberus",
        "command.inspect.paper.standalone.decision",
        "command.inspect.paper.standalone.mods",
        "command.inspect.paper.standalone.mod",
        "command.inspect.paper.backend",
        "command.inspect.paper.backend.sanity",
        "command.artifacts.velocity-owned"
    );

    public GuardianLocaleCatalog load(Path localesDirectory, String selectedLocale)
        throws GuardianConfigurationException {
        Path fallbackPath = localesDirectory.resolve(FALLBACK_LOCALE + ".properties");
        Map<String, String> fallback = loadFile(fallbackPath, true);

        if (FALLBACK_LOCALE.equals(selectedLocale)) {
            return new GuardianLocaleCatalog(selectedLocale, fallback, fallback);
        }

        Path selectedPath = localesDirectory.resolve(selectedLocale + ".properties");
        if (!Files.exists(selectedPath)) {
            return new GuardianLocaleCatalog(selectedLocale, Map.of(), fallback);
        }
        Map<String, String> selected = loadFile(selectedPath, false);
        return new GuardianLocaleCatalog(selectedLocale, selected, fallback);
    }

    private static Map<String, String> loadFile(Path path, boolean requireAllKeys)
        throws GuardianConfigurationException {
        if (!Files.isRegularFile(path)) {
            throw new GuardianConfigurationException(
                path, GuardianConfigurationException.Kind.MISSING, "required locale file is missing");
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException ex) {
            throw new GuardianConfigurationException(
                path, GuardianConfigurationException.Kind.MALFORMED,
                "malformed locale resource: " + ex.getMessage(), ex);
        }

        String schema = properties.getProperty("schema-version");
        if (schema == null) {
            throw new GuardianConfigurationException(
                path, GuardianConfigurationException.Kind.INVALID, "missing schema-version");
        }
        final int parsedSchema;
        try {
            parsedSchema = Integer.parseInt(schema.trim());
        } catch (NumberFormatException ex) {
            throw new GuardianConfigurationException(
                path, GuardianConfigurationException.Kind.INVALID, "schema-version must be an integer", ex);
        }
        if (parsedSchema != SCHEMA_VERSION) {
            throw new GuardianConfigurationException(
                path, GuardianConfigurationException.Kind.UNSUPPORTED_SCHEMA,
                "unsupported schema-version " + parsedSchema
                    + "; supported schema-version is " + SCHEMA_VERSION);
        }

        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            if ("schema-version".equals(name)) {
                continue;
            }
            String value = properties.getProperty(name);
            if (value == null || value.isBlank()) {
                throw new GuardianConfigurationException(
                    path, GuardianConfigurationException.Kind.INVALID,
                    "locale key '" + name + "' is blank");
            }
            values.put(name, value);
        }

        if (requireAllKeys) {
            for (String required : REQUIRED_KEYS) {
                if (!values.containsKey(required)) {
                    throw new GuardianConfigurationException(
                        path, GuardianConfigurationException.Kind.INVALID,
                        "missing required locale key '" + required + "'");
                }
            }
        }
        return Map.copyOf(values);
    }
}
