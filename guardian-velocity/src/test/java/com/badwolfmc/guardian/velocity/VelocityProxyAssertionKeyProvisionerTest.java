package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.operations.ProxyAssertionSecret;
import com.badwolfmc.guardian.core.operations.ProxyAssertionSecretResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VelocityProxyAssertionKeyProvisionerTest {
    @TempDir
    Path tempDir;

    @Test
    void velocityGeneratesKeyOnceAndNeverOverwritesIt() throws Exception {
        VelocityProxyAssertionKeyProvisioner provisioner = new VelocityProxyAssertionKeyProvisioner();

        assertTrue(provisioner.ensureGenerated(tempDir));
        Path key = tempDir.resolve(ProxyAssertionSecretResolver.DEFAULT_KEY_FILE);
        assertTrue(Files.isRegularFile(key));
        byte[] first = Files.readAllBytes(key);

        ProxyAssertionSecret loaded = ProxyAssertionSecretResolver.resolveFile(
            tempDir, ProxyAssertionSecretResolver.DEFAULT_KEY_FILE);
        assertEquals("file:proxy-assertion.key", loaded.sourceDescription());
        assertEquals(16, loaded.fingerprint().length());

        assertFalse(provisioner.ensureGenerated(tempDir));
        assertArrayEquals(first, Files.readAllBytes(key));
    }

    @Test
    void existingNonRegularKeyPathFailsInsteadOfReplacingIt() throws Exception {
        Files.createDirectory(tempDir.resolve(ProxyAssertionSecretResolver.DEFAULT_KEY_FILE));
        assertThrows(IOException.class,
            () -> new VelocityProxyAssertionKeyProvisioner().ensureGenerated(tempDir));
    }
}
