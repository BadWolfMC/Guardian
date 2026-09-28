package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VelocityMessagesTest {
    @TempDir
    Path tempDir;

    @Test
    void externalFallbackLocaleCoversVelocityAdmissionDenialsAndCommands() throws Exception {
        VelocityMessages messages = messages();
        for (DecisionReason reason : DecisionReason.values()) {
            assertNotNull(messages.render(reason, ClientClassification.JAVA_FABRIC));
        }
        assertNotNull(messages.render("command.status.velocity.header",
            Placeholder.unparsed("version", "test")));
    }

    @Test
    void manifestDenialUsesSpecificPolicyProblemKey() {
        GuardianDecision denied = GuardianDecision.deny(
            DecisionReason.MANIFEST_DENIED,
            "internal detail",
            Map.of("policy_violation", "ARTIFACT_NOT_ACCEPTED", "mod_id", "examplemod")
        );
        assertEquals("admission.manifest-denied.artifact-not-accepted", VelocityMessages.keyFor(denied));
    }

    @Test
    void untrustedCommandPlaceholderCannotInjectMiniMessageEvents() throws Exception {
        VelocityMessages messages = messages();
        Component rendered = messages.render("command.inspect.velocity.header",
            Placeholder.unparsed("player", "<click:run_command:'/op me'>Alice</click>"));
        assertNoClickEvents(rendered);
    }

    private VelocityMessages messages() throws Exception {
        Path locales = tempDir.resolve("locales");
        Files.createDirectories(locales);
        try (InputStream input = VelocityMessagesTest.class.getClassLoader()
            .getResourceAsStream("locales/en_us.properties")) {
            assertNotNull(input);
            Files.copy(input, locales.resolve("en_us.properties"));
        }
        return VelocityMessages.load(locales, "en_us");
    }

    private static void assertNoClickEvents(Component component) {
        assertNull(component.clickEvent());
        component.children().forEach(VelocityMessagesTest::assertNoClickEvents);
    }
}
