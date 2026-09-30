package com.badwolfmc.guardian.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6ProxyAssertionArchitectureTest {
    @Test
    void securitySensitiveChannelsTerminateAtVelocity() throws Exception {
        String plugin = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));

        assertTrue(plugin.contains("event.setResult(PluginMessageEvent.ForwardResult.handled())"));
        assertTrue(plugin.contains("if (identifier.equals(PROXY_ADMISSION))"));
        assertTrue(plugin.contains("Consumed client-origin proxy-admission assertion attempt"));
        assertTrue(plugin.contains("Consumed unexpected backend-origin Guardian channel"));
    }

    @Test
    void copiedAssertionSecretIsZeroedAfterSigning() throws Exception {
        String plugin = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        int start = plugin.indexOf("private boolean sendProxyAdmission");
        int end = plugin.indexOf("private void captureInspection", start);
        String method = plugin.substring(start, end);

        assertTrue(method.contains("proxyAssertionSecret().copyBytes()"));
        assertTrue(method.contains("finally"));
        assertTrue(method.contains("Arrays.fill(proxySecret, (byte) 0)"));
    }
}
