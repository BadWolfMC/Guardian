package com.badwolfmc.guardian.core.operations;

import com.badwolfmc.guardian.protocol.GuardianChallengeCrypto;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GuardianServerChallengeSignerTest {
    @TempDir Path tempDir;

    @Test
    void resolverLoadsPkcs8AndSignerBindsAuthenticatedPlayer() throws Exception {
        var pair = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        Files.write(tempDir.resolve(GuardianServerChallengeKeyResolver.DEFAULT_KEY_FILE), pair.getPrivate().getEncoded());
        long now = 1_800_000_000_000L;
        var signer = GuardianServerChallengeKeyResolver.resolveFile(
            tempDir, GuardianServerChallengeKeyResolver.DEFAULT_KEY_FILE,
            Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC));
        UUID player = UUID.randomUUID();
        var challenge = signer.authenticatedChallenge(
            GuardianProtocol.VERSION, GuardianProtocol.REQUIRED_CAPABILITIES,
            new byte[GuardianProtocol.NONCE_BYTES], player);

        assertTrue((challenge.requiredCapabilities()
            & GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE) != 0L);
        assertTrue(GuardianChallengeCrypto.verify(
            List.of(pair.getPublic()), challenge, player,
            Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)));
    }

    @Test
    void pemPrivateKeyIsAccepted() throws Exception {
        var pair = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
            + java.util.Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(pair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
        Files.writeString(tempDir.resolve(GuardianServerChallengeKeyResolver.DEFAULT_KEY_FILE), pem);
        assertNotNull(GuardianServerChallengeKeyResolver.resolveFile(tempDir));
    }


    @Test
    void malformedPemPrivateKeyFailsAtExplicitPemBoundary() throws Exception {
        Path key = tempDir.resolve(GuardianServerChallengeKeyResolver.DEFAULT_KEY_FILE);
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\nAAAA\n");
        IllegalArgumentException missingEnd = assertThrows(IllegalArgumentException.class,
            () -> GuardianServerChallengeKeyResolver.resolveFile(tempDir));
        assertTrue(missingEnd.getMessage().contains("missing END PRIVATE KEY"));

        Files.writeString(key,
            "-----BEGIN PRIVATE KEY-----\nnot-base64!\n-----END PRIVATE KEY-----\n");
        IllegalArgumentException badBody = assertThrows(IllegalArgumentException.class,
            () -> GuardianServerChallengeKeyResolver.resolveFile(tempDir));
        assertTrue(badBody.getMessage().contains("not valid Base64"));
    }

    @Test
    void symlinkPrivateKeyIsRejected() throws Exception {
        var pair = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        Path real = tempDir.resolve("real.key");
        Files.write(real, pair.getPrivate().getEncoded());
        Path configured = tempDir.resolve(GuardianServerChallengeKeyResolver.DEFAULT_KEY_FILE);
        try {
            Files.createSymbolicLink(configured, real.getFileName());
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ex) {
            return; // Platform does not permit symlinks in this test environment.
        }
        assertThrows(IllegalArgumentException.class,
            () -> GuardianServerChallengeKeyResolver.resolveFile(tempDir));
    }

    @Test
    void sameKeyUsesPrivateKeyIdentityWithoutExposingEncodedSecret() throws Exception {
        var first = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        var second = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        Path key = tempDir.resolve(GuardianServerChallengeKeyResolver.DEFAULT_KEY_FILE);
        Files.write(key, first.getPrivate().getEncoded());
        var one = GuardianServerChallengeKeyResolver.resolveFile(tempDir);
        var oneAgain = GuardianServerChallengeKeyResolver.resolveFile(tempDir);
        Files.write(key, second.getPrivate().getEncoded());
        var two = GuardianServerChallengeKeyResolver.resolveFile(tempDir);
        assertTrue(one.sameKey(oneAgain));
        assertFalse(one.sameKey(two));
    }
}
