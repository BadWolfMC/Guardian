package com.badwolfmc.cerberus.trust;

import com.badwolfmc.guardian.protocol.Challenge;
import com.badwolfmc.guardian.protocol.GuardianChallengeAuthentication;
import com.badwolfmc.guardian.protocol.GuardianChallengeCrypto;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardianServerTrustStoreTest {
    @TempDir Path tempDir;
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void trustedChallengeMustBeBoundToLocalAuthenticatedUuid() throws Exception {
        KeyPair pair = keyPair();
        UUID player = UUID.randomUUID();
        Challenge challenge = signed(pair.getPrivate(), player, NOW, NOW + 10_000L);

        assertTrue(GuardianServerTrustStore.verify(
            challenge, player, fixedClock(NOW), List.of(pair.getPublic())));
        assertFalse(GuardianServerTrustStore.verify(
            challenge, UUID.randomUUID(), fixedClock(NOW), List.of(pair.getPublic())));
    }

    @Test
    void wrongKeyUnsignedAndExpiredChallengesAreRejected() throws Exception {
        KeyPair trusted = keyPair();
        KeyPair wrong = keyPair();
        UUID player = UUID.randomUUID();
        Challenge challenge = signed(trusted.getPrivate(), player, NOW, NOW + 10_000L);

        assertFalse(GuardianServerTrustStore.verify(
            challenge, player, fixedClock(NOW), List.of(wrong.getPublic())));
        assertFalse(GuardianServerTrustStore.verify(
            new Challenge(GuardianProtocol.VERSION, GuardianProtocol.REQUIRED_CAPABILITIES,
                new byte[GuardianProtocol.NONCE_BYTES]),
            player, fixedClock(NOW), List.of(trusted.getPublic())));
        assertFalse(GuardianServerTrustStore.verify(
            challenge, player,
            fixedClock(NOW + 10_000L + GuardianProtocol.GUARDIAN_CHALLENGE_AUTH_CLOCK_SKEW_MILLIS),
            List.of(trusted.getPublic())));
    }

    @Test
    void absenceOfPinnedTrustAnchorsNeverAuthenticatesAChallenge() throws Exception {
        KeyPair pair = keyPair();
        UUID player = UUID.randomUUID();
        Challenge challenge = signed(pair.getPrivate(), player, NOW, NOW + 10_000L);
        assertFalse(GuardianServerTrustStore.verify(challenge, player, fixedClock(NOW), List.of()));
    }


    @Test
    void trustAnchorFileIsBoundedStrictUtf8AndRegularFileOnly() throws Exception {
        KeyPair pair = keyPair();
        String encoded = java.util.Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n";
        Path anchors = tempDir.resolve("trusted-server-keys.txt");
        Files.writeString(anchors, encoded, StandardCharsets.UTF_8);
        assertEquals(1, GuardianServerTrustStore.readTrustAnchors(anchors).size());

        Files.write(anchors, new byte[] {(byte) 0xC3, (byte) 0x28});
        assertThrows(java.io.IOException.class, () -> GuardianServerTrustStore.readTrustAnchors(anchors));

        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            Path target = tempDir.resolve("target.txt");
            Files.writeString(target, encoded, StandardCharsets.UTF_8);
            Path link = tempDir.resolve("anchors-link.txt");
            Files.createSymbolicLink(link, target.getFileName());
            assertThrows(java.io.IOException.class, () -> GuardianServerTrustStore.readTrustAnchors(link));
        }
    }

    private static Challenge signed(PrivateKey key, UUID player, long issued, long expires) throws Exception {
        long caps = GuardianProtocol.REQUIRED_CAPABILITIES
            | GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE;
        byte[] nonce = new byte[GuardianProtocol.NONCE_BYTES];
        Signature signature = Signature.getInstance(GuardianChallengeCrypto.ALGORITHM);
        signature.initSign(key);
        signature.update(GuardianChallengeCrypto.signingMessage(
            GuardianProtocol.VERSION, caps, nonce, player, issued, expires));
        return new Challenge(
            GuardianProtocol.VERSION,
            caps,
            nonce,
            new GuardianChallengeAuthentication(player, issued, expires, signature.sign()));
    }

    private static KeyPair keyPair() throws Exception {
        return KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
    }

    private static Clock fixedClock(long millis) {
        return Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }
}
