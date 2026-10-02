package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.core.operations.DiagnosticText;
import com.badwolfmc.guardian.core.operations.SafeRegularFile;
import com.badwolfmc.guardian.core.operations.SafeDirectory;
import com.badwolfmc.guardian.velocity.config.VelocityConfigurationException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Immutable locale catalog/renderer owned by one active Guardian-Velocity runtime snapshot. */
public final class VelocityMessages {
    static final int SCHEMA_VERSION = 1;
    static final String FALLBACK_LOCALE = "en_us";
    static final int MAX_LOCALE_BYTES = 256 * 1024;
    private static final Set<String> REQUIRED_KEYS = Set.of(
        "admission.denied",
        "admission.proxy-assertion-required",
        "admission.proxy-assertion-invalid",
        "admission.cerberus-required",
        "admission.cerberus-timeout",
        "admission.cerberus-protocol-unsupported",
        "admission.cerberus-server-auth-required",
        "admission.cerberus-release-required",
        "admission.cerberus-release-untrusted",
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
        "admission.profile-resolution-failed",
        "admission.configuration-error",
        "meta.help-url",
        "command.usage.velocity",
        "command.no-permission",
        "command.value.enabled",
        "command.value.disabled",
        "command.value.available",
        "command.value.unavailable",
        "command.value.configured",
        "command.value.required",
        "command.value.optional",
        "command.value.not-applicable",
        "command.value.scope.paper-local",
        "command.value.scope.velocity-admission",
        "command.value.artifact.no-hash",
        "command.value.artifact.catalogued",
        "command.value.artifact.not-catalogued",
        "command.value.runtime.current",
        "command.value.runtime.pre-reload",
        "command.validate.success",
        "command.validate.failed",
        "command.reload.success",
        "command.reload.failed",
        "command.inspect.no-active-data",
        "command.inspect.runtime-context",
        "command.inspect.mods-omitted",
        "command.inspect.velocity.header",
        "command.inspect.velocity.identity",
        "command.inspect.velocity.client",
        "command.inspect.velocity.profile",
        "command.inspect.velocity.cerberus",
        "command.inspect.velocity.decision",
        "command.inspect.velocity.mods-summary",
        "command.inspect.velocity.mod",
        "command.inspect.velocity.bedrock",
        "command.status.velocity.header",
        "command.status.velocity.runtime",
        "command.status.velocity.policy",
        "command.status.velocity.integrations",
        "command.status.velocity.assertion",
        "command.status.velocity.security",
        "command.status.velocity.snapshots",
        "artifacts.command.usage.velocity",
        "artifacts.command.no-permission",
        "artifacts.scan.started",
        "artifacts.scan.already-running",
        "artifacts.scan.success",
        "artifacts.scan.unchanged",
        "artifacts.scan.failed"
    );

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, String> selected;
    private final Map<String, String> fallback;

    private VelocityMessages(Map<String, String> selected, Map<String, String> fallback) {
        this.selected = selected;
        this.fallback = fallback;
    }

    static VelocityMessages load(Path localesDirectory, String selectedLocale)
        throws VelocityConfigurationException {
        try {
            SafeDirectory.requireRealDirectory(localesDirectory);
        } catch (IOException ex) {
            throw new VelocityConfigurationException(localesDirectory,
                "locales path must be a real directory rather than a symlink or other file type: "
                    + ex.getMessage(), ex);
        }
        Path fallbackPath = localesDirectory.resolve(FALLBACK_LOCALE + ".properties");
        Map<String, String> fallback = loadFile(fallbackPath, true);
        if (FALLBACK_LOCALE.equals(selectedLocale)) {
            return new VelocityMessages(fallback, fallback);
        }
        Path selectedPath = localesDirectory.resolve(selectedLocale + ".properties");
        if (!Files.exists(selectedPath, LinkOption.NOFOLLOW_LINKS)) {
            return new VelocityMessages(Map.of(), fallback);
        }
        return new VelocityMessages(loadFile(selectedPath, false), fallback);
    }

    Component render(DecisionReason reason, ClientClassification classification) {
        return render(new GuardianDecision(com.badwolfmc.guardian.core.DecisionOutcome.DENY, reason, ""), classification);
    }

    Component render(GuardianDecision decision, ClientClassification classification) {
        String classificationValue = classification == null ? "unknown" : classification.policyKey();
        return render(keyFor(decision), TagResolver.builder()
            .resolver(Placeholder.unparsed("classification", classificationValue))
            .resolver(Placeholder.unparsed("reason", decision.reason().name()))
            .resolver(Placeholder.unparsed("help_url", DiagnosticText.oneLine(template("meta.help-url"))))
            .resolver(Placeholder.unparsed("mod_id", DiagnosticText.oneLine(decision.context().getOrDefault("mod_id", "unknown"))))
            .resolver(Placeholder.unparsed("version", DiagnosticText.oneLine(decision.context().getOrDefault("version", "unknown"))))
            .build());
    }

    Component render(String key, TagResolver resolver) {
        return miniMessage.deserialize(template(key), resolver);
    }

    private String template(String key) {
        String value = selected.get(key);
        if (value != null) return value;
        value = fallback.get(key);
        if (value == null) throw new IllegalStateException("missing required Guardian locale key " + key);
        return value;
    }

    static String keyFor(GuardianDecision decision) {
        if (decision.reason() != DecisionReason.MANIFEST_DENIED) return keyFor(decision.reason());
        return switch (decision.context().getOrDefault("policy_violation", "")) {
            case "REQUIRED_MOD_MISSING" -> "admission.manifest-denied.required-mod-missing";
            case "EXPLICIT_MOD_DENY" -> "admission.manifest-denied.explicit-mod-deny";
            case "UNLISTED_MOD" -> "admission.manifest-denied.unlisted-mod";
            case "VERSION_NOT_ACCEPTED" -> "admission.manifest-denied.version-not-accepted";
            case "ARTIFACT_NOT_ACCEPTED" -> "admission.manifest-denied.artifact-not-accepted";
            case "DIRECTORY_ORIGIN_DENIED" -> "admission.manifest-denied.directory-origin";
            case "MIXED_OR_UNKNOWN_ORIGIN_DENIED" -> "admission.manifest-denied.mixed-origin";
            default -> "admission.manifest-denied";
        };
    }

    private static String keyFor(DecisionReason reason) {
        return switch (reason) {
            case PROXY_ASSERTION_REQUIRED -> "admission.proxy-assertion-required";
            case PROXY_ASSERTION_INVALID -> "admission.proxy-assertion-invalid";
            case CERBERUS_REQUIRED -> "admission.cerberus-required";
            case CERBERUS_TIMEOUT -> "admission.cerberus-timeout";
            case CERBERUS_PROTOCOL_UNSUPPORTED -> "admission.cerberus-protocol-unsupported";
            case CERBERUS_SERVER_AUTH_REQUIRED -> "admission.cerberus-server-auth-required";
            case CERBERUS_RELEASE_REQUIRED -> "admission.cerberus-release-required";
            case CERBERUS_RELEASE_UNTRUSTED -> "admission.cerberus-release-untrusted";
            case MANIFEST_DENIED -> "admission.manifest-denied";
            case MANIFEST_INVALID -> "admission.manifest-invalid";
            case CLIENT_DENIED -> "admission.client-denied";
            case PROFILE_RESOLUTION_FAILED -> "admission.profile-resolution-failed";
            case CONFIGURATION_ERROR -> "admission.configuration-error";
            default -> "admission.denied";
        };
    }

    private static Map<String, String> loadFile(Path path, boolean requireAll)
        throws VelocityConfigurationException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new VelocityConfigurationException(path, "required locale file is missing");
        }
        final String text;
        try {
            text = SafeRegularFile.readUtf8(path, MAX_LOCALE_BYTES);
        } catch (IOException ex) {
            throw new VelocityConfigurationException(path,
                "locale must be a stable regular non-symlink UTF-8 file: " + ex.getMessage(), ex);
        }
        Properties properties = new Properties();
        try (StringReader reader = new StringReader(text)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException ex) {
            throw new VelocityConfigurationException(path, "malformed locale resource: " + ex.getMessage(), ex);
        }
        String schema = properties.getProperty("schema-version");
        if (schema == null || !Integer.toString(SCHEMA_VERSION).equals(schema.trim())) {
            throw new VelocityConfigurationException(path, "locale must declare schema-version=" + SCHEMA_VERSION);
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            if ("schema-version".equals(name)) continue;
            String value = properties.getProperty(name);
            if (value == null || value.isBlank()) {
                throw new VelocityConfigurationException(path, "locale key '" + name + "' is blank");
            }
            values.put(name, value);
        }
        if (requireAll) {
            for (String key : REQUIRED_KEYS) {
                if (!values.containsKey(key)) {
                    throw new VelocityConfigurationException(path, "missing required locale key '" + key + "'");
                }
            }
        }
        return Map.copyOf(values);
    }
}
