package com.badwolfmc.guardian.protocol;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Parser/canonicalizer for client-pinned Guardian server-authentication public keys. */
public final class GuardianChallengeTrustAnchors {
    public static final String ENTRY_NAME = "META-INF/guardian/trusted-server-keys.txt";
    public static final int MAX_KEYS = 8;
    public static final int MAX_TEXT_BYTES = 4096;

    private GuardianChallengeTrustAnchors() {}

    public static List<PublicKey> parse(String text) throws GeneralSecurityException {
        if (text == null) throw new GeneralSecurityException("Guardian server trust-anchor text is null");
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new GeneralSecurityException("Guardian server trust-anchor text exceeds " + MAX_TEXT_BYTES + " bytes");
        }
        List<PublicKey> keys = new ArrayList<>();
        Set<String> canonical = new HashSet<>();
        int lineNumber = 0;
        for (String rawLine : text.split("\\R", -1)) {
            lineNumber++;
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            PublicKey key;
            try {
                key = GuardianChallengeCrypto.decodePublicKey(line);
            } catch (GeneralSecurityException ex) {
                throw new GeneralSecurityException("invalid Guardian server trust anchor on line " + lineNumber + ": " + ex.getMessage(), ex);
            }
            String encoded = Base64.getEncoder().encodeToString(key.getEncoded());
            if (!canonical.add(encoded)) {
                throw new GeneralSecurityException("duplicate Guardian server trust anchor on line " + lineNumber);
            }
            keys.add(key);
            if (keys.size() > MAX_KEYS) {
                throw new GeneralSecurityException("Guardian server trust anchors exceed maximum of " + MAX_KEYS);
            }
        }
        return List.copyOf(keys);
    }

    public static String canonicalText(List<PublicKey> keys) throws GeneralSecurityException {
        if (keys == null || keys.isEmpty()) {
            throw new GeneralSecurityException("at least one Guardian server trust anchor is required");
        }
        if (keys.size() > MAX_KEYS) {
            throw new GeneralSecurityException("Guardian server trust anchors exceed maximum of " + MAX_KEYS);
        }
        Set<String> seen = new HashSet<>();
        StringBuilder out = new StringBuilder();
        for (PublicKey key : keys) {
            if (key == null || !GuardianChallengeCrypto.isEd25519KeyAlgorithm(key.getAlgorithm())) {
                throw new GeneralSecurityException("Guardian server trust anchor must be Ed25519");
            }
            String encoded = Base64.getEncoder().encodeToString(key.getEncoded());
            if (!seen.add(encoded)) throw new GeneralSecurityException("duplicate Guardian server trust anchor");
            out.append(encoded).append('\n');
        }
        String text = out.toString();
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new GeneralSecurityException("canonical Guardian server trust anchors exceed " + MAX_TEXT_BYTES + " bytes");
        }
        return text;
    }
}
