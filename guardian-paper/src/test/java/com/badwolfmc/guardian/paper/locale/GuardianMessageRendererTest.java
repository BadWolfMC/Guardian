package com.badwolfmc.guardian.paper.locale;

import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.paper.PaperAuthorityMode;
import com.badwolfmc.guardian.core.operations.OperationalLogLevel;
import com.badwolfmc.guardian.paper.config.GuardianPaperSettings;
import com.badwolfmc.guardian.paper.config.GuardianRuntimeSnapshot;
import com.badwolfmc.guardian.protection.ProtectionDecision;
import com.badwolfmc.guardian.protection.ProtectionOutcome;
import com.badwolfmc.guardian.protection.ProtectionPolicy;
import com.badwolfmc.guardian.protection.ProtectionReason;
import com.badwolfmc.guardian.protection.ProtectionRule;
import com.badwolfmc.guardian.protection.ProtectionRuleMode;
import com.badwolfmc.guardian.protection.ProtectionSurface;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GuardianMessageRendererTest {
    @Test
    void untrustedPlaceholderValuesCannotInjectMiniMessageEvents() {
        String attemptedInjection = "<click:run_command:'/op me'>click me</click>";
        GuardianPaperSettings settings = new GuardianPaperSettings(
            1, false, false, "", "en_us", attemptedInjection,
            PaperAuthorityMode.STANDALONE, 10, 40, OperationalLogLevel.NORMAL, null, ProtectionPolicy.disabled());
        GuardianLocaleCatalog catalog = new GuardianLocaleCatalog(
            "en_us",
            Map.of("admission.cerberus-required", "<red>Need Cerberus</red> <help_url>"),
            Map.of("admission.cerberus-required", "fallback")
        );
        GuardianRuntimeSnapshot snapshot = new GuardianRuntimeSnapshot(settings, catalog, null);

        Component rendered = new GuardianMessageRenderer().renderDecision(
            snapshot, GuardianDecision.deny(DecisionReason.CERBERUS_REQUIRED, "test"),
            ClientClassification.JAVA_FABRIC);

        assertNoClickEvents(rendered);
    }



    @Test
    void manifestDenialUsesSpecificLocalizedPolicyProblemKey() {
        GuardianDecision denied = GuardianDecision.deny(
            DecisionReason.MANIFEST_DENIED,
            "internal detail",
            Map.of("policy_violation", "UNLISTED_MOD", "mod_id", "examplemod")
        );
        assertEquals("admission.manifest-denied.unlisted-mod", GuardianMessageRenderer.keyFor(denied));
    }

    @Test
    void protectionPlaceholdersCannotInjectMiniMessageEvents() {
        String attemptedInjection = "<click:run_command:'/op me'>click me</click>";
        GuardianPaperSettings settings = new GuardianPaperSettings(
            1, false, true, "", "en_us", "https://example.invalid/",
            PaperAuthorityMode.STANDALONE, 10, 40, OperationalLogLevel.NORMAL, null, ProtectionPolicy.disabled());
        GuardianLocaleCatalog catalog = new GuardianLocaleCatalog(
            "en_us",
            Map.of(
                "protection.notify.command-denied",
                "<gray>[Guardian]</gray> <player> tried <command> (<root>)"
            ),
            Map.of("protection.notify.command-denied", "fallback")
        );
        GuardianRuntimeSnapshot snapshot = new GuardianRuntimeSnapshot(settings, catalog, null);
        ProtectionRule rule = ProtectionRule.create(
            ProtectionSurface.COMMAND_EXECUTION, true, ProtectionRuleMode.DENYLIST, java.util.Set.of("plugins"));
        ProtectionDecision decision = new ProtectionDecision(
            ProtectionOutcome.DENY, ProtectionReason.EXECUTION_DENIED,
            ProtectionSurface.COMMAND_EXECUTION,
            com.badwolfmc.guardian.protection.CommandRootNormalizer.normalize("plugins"),
            rule, java.util.Optional.empty());

        Component rendered = new GuardianMessageRenderer().renderProtectionNotification(
            snapshot, decision, attemptedInjection, attemptedInjection);

        assertNoClickEvents(rendered);
    }

    @Test
    void protectionExecutionDenialReasonsHaveLocaleKeyMappings() {
        assertEquals("protection.command-denied",
            GuardianMessageRenderer.protectionDenialKey(ProtectionReason.EXECUTION_DENIED));
        assertEquals("protection.namespace-denied",
            GuardianMessageRenderer.protectionDenialKey(ProtectionReason.NAMESPACE_DENIED));
        assertEquals("protection.notify.command-denied",
            GuardianMessageRenderer.protectionNotificationKey(ProtectionReason.EXECUTION_DENIED));
        assertEquals("protection.notify.namespace-denied",
            GuardianMessageRenderer.protectionNotificationKey(ProtectionReason.NAMESPACE_DENIED));
    }

    @Test
    void everyDenyReasonHasALocaleKeyMapping() {
        for (DecisionReason reason : DecisionReason.values()) {
            assertNotNull(GuardianMessageRenderer.keyFor(reason));
        }
    }

    private static void assertNoClickEvents(Component component) {
        assertNull(component.clickEvent(), "safe placeholder must not create a click event");
        component.children().forEach(GuardianMessageRendererTest::assertNoClickEvents);
    }
}
