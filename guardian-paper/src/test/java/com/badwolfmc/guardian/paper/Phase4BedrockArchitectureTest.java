package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase4BedrockArchitectureTest {
    @Test
    void standalonePaperClassifiesSupportedBedrockEvidenceBeforeJavaBrand() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        int detection = source.indexOf("bedrockDetector.detect(session.playerId())");
        int brand = source.indexOf("String brand = connection.getClientBrandName()", detection);
        assertTrue(detection >= 0 && brand > detection,
            "supported Bedrock origin evidence must be evaluated before Java brand classification");
        assertTrue(source.contains("BedrockResolution.INDETERMINATE"));
        assertTrue(source.contains("DecisionReason.CONFIGURATION_ERROR"));
        int classifier = source.indexOf("ClientOriginClassifier.classify(", brand);
        int classificationAssignment = source.indexOf("session.setClassification", classifier);
        assertTrue(classifier > brand && classificationAssignment > classifier,
            "standalone Paper must classify the connection only after supported Bedrock evidence and Java brand are available");
        String classifierCall = source.substring(classifier, classificationAssignment);
        assertTrue(classifierCall.contains("bedrock.geyser() == BedrockSignal.BEDROCK"));
        assertTrue(classifierCall.contains("bedrock.floodgate() == BedrockSignal.BEDROCK"));
        assertFalse(source.contains("startsWith(\".\")"),
            "username prefix must never become a trusted Bedrock identity signal");
    }

    @Test
    void velocityBackendFloodgateCheckRemainsDiagnosticOnly() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        int disagreement = source.indexOf("BACKEND FLOODGATE DISAGREEMENT");
        int start = source.lastIndexOf("    private ", disagreement);
        int end = source.indexOf("    private ", disagreement);
        assertTrue(disagreement >= 0 && start >= 0 && end > disagreement,
            "backend Floodgate sanity-check method must remain discoverable for the architecture guard");
        String method = source.substring(start, end);
        assertTrue(method.contains("BACKEND FLOODGATE DISAGREEMENT"));
        assertFalse(method.contains("session.decide("),
            "backend Floodgate sanity evidence must not become a second admission authority");
    }

    @Test
    void paperDeclaresBothSupportedBedrockIntegrationsAsOptional() throws Exception {
        String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String build = Files.readString(Path.of("build.gradle"));
        assertTrue(pluginYml.contains("Geyser-Spigot"));
        assertTrue(pluginYml.contains("floodgate"));
        assertTrue(build.contains("org.geysermc.geyser:api"));
        assertTrue(build.contains("org.geysermc.floodgate:api"));
    }
}
