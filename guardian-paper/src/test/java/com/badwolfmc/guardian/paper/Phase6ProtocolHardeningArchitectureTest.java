package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6ProtocolHardeningArchitectureTest {
    @Test
    void standalonePaperRejectsUnknownProtocolV1CapabilitiesAndSanitizesDebugOutput() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        assertTrue(source.contains("GuardianProtocol.supportsProtocolV1Capabilities(presence.capabilities())"));
        assertTrue(source.contains("plugin.getLogger().info(DiagnosticText.oneLine(message))"));
    }

    @Test
    void invalidManifestIsNotRetainedAsInspectionEvidence() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        int validate = source.indexOf("ProtocolV1ResponseValidator.validate(");
        int retain = source.indexOf("session.setManifest(response.manifest())", validate);
        assertTrue(validate >= 0 && retain > validate,
            "Paper must validate protocol/canonical integrity before retaining manifest evidence");
    }
}
