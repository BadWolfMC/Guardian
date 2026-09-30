package com.badwolfmc.guardian.protocol;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GuardianChallengeAuthenticationTest {
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void authenticatedChallengeRoundTripsAndVerifies() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        UUID player = UUID.randomUUID();
        byte[] nonce = bytes((byte) 7, GuardianProtocol.NONCE_BYTES);
        long caps = GuardianProtocol.REQUIRED_CAPABILITIES | GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE;
        Challenge challenge = signed(keys.getPrivate(), caps, nonce, player, NOW, NOW + 10_000L);

        Challenge decoded = ProtocolCodec.decodeChallenge(ProtocolCodec.encodeChallenge(challenge));
        assertEquals(player, decoded.authentication().playerId());
        assertArrayEquals(nonce, decoded.nonce());
        assertTrue(GuardianChallengeCrypto.verify(
            List.of(keys.getPublic()), decoded, player, fixedClock(NOW)));
    }

    @Test
    void uuidBindingBlocksCrossPlayerRelayAndWrongKey() throws Exception {
        KeyPair trusted = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        KeyPair other = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        UUID player = UUID.randomUUID();
        Challenge challenge = signed(
            trusted.getPrivate(),
            GuardianProtocol.REQUIRED_CAPABILITIES | GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE,
            bytes((byte) 1, GuardianProtocol.NONCE_BYTES), player, NOW, NOW + 10_000L);

        assertFalse(GuardianChallengeCrypto.verify(
            List.of(trusted.getPublic()), challenge, UUID.randomUUID(), fixedClock(NOW)));
        assertFalse(GuardianChallengeCrypto.verify(
            List.of(other.getPublic()), challenge, player, fixedClock(NOW)));
    }

    @Test
    void freshnessAndLifetimeAreFailClosed() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        UUID player = UUID.randomUUID();
        long caps = GuardianProtocol.REQUIRED_CAPABILITIES | GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE;
        byte[] nonce = bytes((byte) 2, GuardianProtocol.NONCE_BYTES);

        assertFalse(GuardianChallengeCrypto.verify(List.of(keys.getPublic()),
            signed(keys.getPrivate(), caps, nonce, player,
                NOW - GuardianProtocol.GUARDIAN_CHALLENGE_AUTH_TTL_MILLIS - 1L, NOW - 1L),
            player, fixedClock(NOW + GuardianProtocol.GUARDIAN_CHALLENGE_AUTH_CLOCK_SKEW_MILLIS)));

        assertFalse(GuardianChallengeCrypto.verify(List.of(keys.getPublic()),
            signed(keys.getPrivate(), caps, nonce, player,
                NOW, NOW + GuardianProtocol.GUARDIAN_CHALLENGE_AUTH_TTL_MILLIS + 1L),
            player, fixedClock(NOW)));

        assertFalse(GuardianChallengeCrypto.verify(List.of(keys.getPublic()),
            signed(keys.getPrivate(), caps, nonce, player,
                NOW + GuardianProtocol.GUARDIAN_CHALLENGE_AUTH_CLOCK_SKEW_MILLIS + 1L, NOW + 10_000L),
            player, fixedClock(NOW)));
    }

    @Test
    void capabilityAndAuthenticationProofMustAgree() {
        byte[] nonce = new byte[GuardianProtocol.NONCE_BYTES];
        assertThrows(IllegalArgumentException.class, () -> new Challenge(
            GuardianProtocol.VERSION,
            GuardianProtocol.REQUIRED_CAPABILITIES | GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE,
            nonce));
        assertThrows(IllegalArgumentException.class, () -> new Challenge(
            GuardianProtocol.VERSION,
            GuardianProtocol.REQUIRED_CAPABILITIES,
            nonce,
            new GuardianChallengeAuthentication(UUID.randomUUID(), NOW, NOW + 1_000L,
                new byte[GuardianProtocol.GUARDIAN_CHALLENGE_SIGNATURE_BYTES])));
    }

    @Test
    void trustAnchorParserCanonicalizesAndRejectsDuplicateKeyMaterial() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        String base64 = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        List<java.security.PublicKey> parsed = GuardianChallengeTrustAnchors.parse("# key\n" + base64 + "\n");
        assertEquals(1, parsed.size());
        assertEquals(base64 + "\n", GuardianChallengeTrustAnchors.canonicalText(parsed));
        assertThrows(java.security.GeneralSecurityException.class,
            () -> GuardianChallengeTrustAnchors.parse(base64 + "\n" + base64 + "\n"));
    }


    @Test
    void signatureBindsProtocolCapabilitiesAndNonce() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        UUID player = UUID.randomUUID();
        long caps = GuardianProtocol.REQUIRED_CAPABILITIES
            | GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE;
        byte[] nonce = bytes((byte) 4, GuardianProtocol.NONCE_BYTES);
        Challenge signed = signed(keys.getPrivate(), caps, nonce, player, NOW, NOW + 10_000L);

        byte[] changedNonce = signed.nonce();
        changedNonce[0] ^= 1;
        Challenge nonceTampered = new Challenge(
            signed.protocolVersion(), signed.requiredCapabilities(), changedNonce, signed.authentication());
        assertFalse(GuardianChallengeCrypto.verify(
            List.of(keys.getPublic()), nonceTampered, player, fixedClock(NOW)));

        Challenge capabilitiesTampered = new Challenge(
            signed.protocolVersion(),
            signed.requiredCapabilities() | GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE,
            signed.nonce(), signed.authentication());
        assertFalse(GuardianChallengeCrypto.verify(
            List.of(keys.getPublic()), capabilitiesTampered, player, fixedClock(NOW)));

        Challenge protocolTampered = new Challenge(
            GuardianProtocol.VERSION + 1,
            signed.requiredCapabilities(), signed.nonce(), signed.authentication());
        assertFalse(GuardianChallengeCrypto.verify(
            List.of(keys.getPublic()), protocolTampered, player, fixedClock(NOW)));
    }

    @Test
    void stagedKeyRotationAcceptsOverlapAndRejectsRetiredKey() throws Exception {
        KeyPair oldKey = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        KeyPair newKey = KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM).generateKeyPair();
        UUID player = UUID.randomUUID();
        long caps = GuardianProtocol.REQUIRED_CAPABILITIES
            | GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE;
        byte[] nonce = bytes((byte) 5, GuardianProtocol.NONCE_BYTES);

        Challenge oldChallenge = signed(
            oldKey.getPrivate(), caps, nonce, player, NOW, NOW + 10_000L);
        Challenge newChallenge = signed(
            newKey.getPrivate(), caps, nonce, player, NOW, NOW + 10_000L);

        List<java.security.PublicKey> overlap = List.of(oldKey.getPublic(), newKey.getPublic());
        assertTrue(GuardianChallengeCrypto.verify(overlap, oldChallenge, player, fixedClock(NOW)));
        assertTrue(GuardianChallengeCrypto.verify(overlap, newChallenge, player, fixedClock(NOW)));
        assertFalse(GuardianChallengeCrypto.verify(
            List.of(newKey.getPublic()), oldChallenge, player, fixedClock(NOW)));
        assertTrue(GuardianChallengeCrypto.verify(
            List.of(newKey.getPublic()), newChallenge, player, fixedClock(NOW)));
    }

    @Test
    void trustAnchorBundleHasExplicitRotationBound() throws Exception {
        java.util.ArrayList<java.security.PublicKey> keys = new java.util.ArrayList<>();
        for (int i = 0; i < GuardianChallengeTrustAnchors.MAX_KEYS; i++) {
            keys.add(KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM)
                .generateKeyPair().getPublic());
        }
        String canonical = GuardianChallengeTrustAnchors.canonicalText(keys);
        assertEquals(GuardianChallengeTrustAnchors.MAX_KEYS,
            GuardianChallengeTrustAnchors.parse(canonical).size());

        keys.add(KeyPairGenerator.getInstance(GuardianChallengeCrypto.ALGORITHM)
            .generateKeyPair().getPublic());
        assertThrows(java.security.GeneralSecurityException.class,
            () -> GuardianChallengeTrustAnchors.canonicalText(keys));
    }

    private static Challenge signed(
        PrivateKey key, long capabilities, byte[] nonce, UUID player, long issued, long expires
    ) throws Exception {
        Signature signer = Signature.getInstance(GuardianChallengeCrypto.ALGORITHM);
        signer.initSign(key);
        signer.update(GuardianChallengeCrypto.signingMessage(
            GuardianProtocol.VERSION, capabilities, nonce, player, issued, expires));
        return new Challenge(
            GuardianProtocol.VERSION, capabilities, nonce,
            new GuardianChallengeAuthentication(player, issued, expires, signer.sign()));
    }

    private static Clock fixedClock(long millis) {
        return Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }

    private static byte[] bytes(byte value, int length) {
        byte[] bytes = new byte[length];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}
