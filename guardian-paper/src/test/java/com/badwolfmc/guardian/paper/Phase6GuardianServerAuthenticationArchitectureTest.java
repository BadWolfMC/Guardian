package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6GuardianServerAuthenticationArchitectureTest {
    @Test
    void standalonePaperSignsChallengeForExactPlayerUuidWhenEnabled() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java"));
        assertTrue(source.contains("CAP_AUTHENTICATED_GUARDIAN_CHALLENGE"));
        assertTrue(source.contains("serverChallengeSigner()"));
        assertTrue(source.contains("signer.authenticatedChallenge("));
        assertTrue(source.contains("player.getUniqueId()"));
        assertTrue(source.contains("DecisionReason.CERBERUS_SERVER_AUTH_REQUIRED"));
    }

    @Test
    void paperLoadsServerIdentityOnlyFromServerSideKeyFile() throws Exception {
        String loader = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/config/GuardianConfigLoader.java"));
        String resolver = Files.readString(Path.of(
            "../guardian-core/src/main/java/com/badwolfmc/guardian/core/operations/GuardianServerChallengeKeyResolver.java"));
        assertTrue(loader.contains("server-authentication"));
        assertTrue(loader.contains("GuardianServerChallengeKeyResolver"));
        assertTrue(resolver.contains("guardian-server-auth.key"));
        assertTrue(resolver.contains("SafeRegularFile.read"));
        assertFalse(loader.contains("getEncoded()"), "Paper config/logging must not expose private key bytes");
    }
}
