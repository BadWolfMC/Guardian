package com.badwolfmc.guardian.paper.locale;

import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.paper.config.GuardianRuntimeSnapshot;
import com.badwolfmc.guardian.core.operations.DiagnosticText;
import com.badwolfmc.guardian.protection.ProtectionDecision;
import com.badwolfmc.guardian.protection.ProtectionReason;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class GuardianMessageRenderer {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Component renderDecision(
        GuardianRuntimeSnapshot snapshot,
        GuardianDecision decision,
        ClientClassification classification
    ) {
        String classificationValue = classification == null ? "unknown" : classification.policyKey();
        return render(snapshot, keyFor(decision), TagResolver.builder()
            .resolver(Placeholder.unparsed("classification", classificationValue))
            .resolver(Placeholder.unparsed("reason", decision.reason().name()))
            .resolver(Placeholder.unparsed("help_url", DiagnosticText.oneLine(snapshot.settings().helpUrl())))
            .resolver(Placeholder.unparsed("mod_id", DiagnosticText.oneLine(decision.context().getOrDefault("mod_id", "unknown"))))
            .resolver(Placeholder.unparsed("version", DiagnosticText.oneLine(decision.context().getOrDefault("version", "unknown"))))
            .build());
    }

    public Component renderProtectionDenial(
        GuardianRuntimeSnapshot snapshot,
        ProtectionDecision decision,
        String command
    ) {
        return render(snapshot, protectionDenialKey(decision.reason()), protectionResolver(decision, "", command));
    }

    public Component renderProtectionNotification(
        GuardianRuntimeSnapshot snapshot,
        ProtectionDecision decision,
        String playerName,
        String command
    ) {
        return render(
            snapshot,
            protectionNotificationKey(decision.reason()),
            protectionResolver(decision, playerName, command)
        );
    }

    public Component render(GuardianRuntimeSnapshot snapshot, String key, TagResolver resolver) {
        String template = snapshot.localeCatalog().template(key);
        return miniMessage.deserialize(template, resolver);
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

    static String keyFor(DecisionReason reason) {
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

    static String protectionDenialKey(ProtectionReason reason) {
        return switch (reason) {
            case EXECUTION_DENIED -> "protection.command-denied";
            case NAMESPACE_DENIED -> "protection.namespace-denied";
            default -> throw new IllegalArgumentException("Protection reason is not an execution denial: " + reason);
        };
    }

    static String protectionNotificationKey(ProtectionReason reason) {
        return switch (reason) {
            case EXECUTION_DENIED -> "protection.notify.command-denied";
            case NAMESPACE_DENIED -> "protection.notify.namespace-denied";
            default -> throw new IllegalArgumentException("Protection reason has no notification template: " + reason);
        };
    }

    private static TagResolver protectionResolver(
        ProtectionDecision decision,
        String playerName,
        String command
    ) {
        return TagResolver.builder()
            .resolver(Placeholder.unparsed("player", DiagnosticText.oneLine(playerName)))
            .resolver(Placeholder.unparsed("command", DiagnosticText.oneLine(command)))
            .resolver(Placeholder.unparsed("root", decision.root().value()))
            .resolver(Placeholder.unparsed("reason", decision.reason().name()))
            .build();
    }
}
