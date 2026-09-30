package com.badwolfmc.guardian.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6GuardianServerAuthenticationArchitectureTest {
    @Test
    void velocitySignsChallengeForExactAuthenticatedPlayerUuidWhenEnabled() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/GuardianVelocityPlugin.java"));
        assertTrue(source.contains("CAP_AUTHENTICATED_GUARDIAN_CHALLENGE"));
        assertTrue(source.contains("serverChallengeSigner()"));
        assertTrue(source.contains("signer.authenticatedChallenge("));
        assertTrue(source.contains("player.getUniqueId()"));
        assertTrue(source.contains("DecisionReason.CERBERUS_SERVER_AUTH_REQUIRED"));
    }

    @Test
    void velocityLoadsServerIdentityFromBoundedServerSideKeyFile() throws Exception {
        String loader = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/velocity/config/VelocityConfigLoader.java"));
        String resolver = Files.readString(Path.of(
            "../guardian-core/src/main/java/com/badwolfmc/guardian/core/operations/GuardianServerChallengeKeyResolver.java"));
        assertTrue(loader.contains("server-authentication"));
        assertTrue(loader.contains("GuardianServerChallengeKeyResolver"));
        assertTrue(resolver.contains("MAX_PRIVATE_KEY_FILE_BYTES"));
        assertTrue(resolver.contains("LinkOption.NOFOLLOW_LINKS"));
        assertFalse(loader.contains("getEncoded()"), "Velocity config/logging must not expose private key bytes");
    }
}
