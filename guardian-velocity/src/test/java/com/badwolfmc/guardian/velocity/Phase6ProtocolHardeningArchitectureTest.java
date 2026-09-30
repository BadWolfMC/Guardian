package com.badwolfmc.guardian.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6ProtocolHardeningArchitectureTest {
    @Test
    void velocityRejectsUnknownProtocolV1CapabilitiesAndSanitizesDebugArguments() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        assertTrue(source.contains("GuardianProtocol.supportsProtocolV1Capabilities(presence.capabilities())"));
        assertTrue(source.contains("logger.info(format, safeDiagnosticArgs(args))"));
        assertTrue(source.contains("DiagnosticText.oneLine(String.valueOf(args[i]))"));
    }

    @Test
    void invalidManifestIsNotRetainedAsInspectionEvidence() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        int validate = source.indexOf("ProtocolV1ResponseValidator.validate(");
        int retain = source.indexOf("session.setManifest(response.manifest())", validate);
        assertTrue(validate >= 0 && retain > validate,
            "Velocity must validate protocol/canonical integrity before retaining manifest evidence");
    }
}
