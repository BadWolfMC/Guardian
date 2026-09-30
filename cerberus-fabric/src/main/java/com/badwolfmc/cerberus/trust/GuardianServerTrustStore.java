package com.badwolfmc.cerberus.trust;

import com.badwolfmc.guardian.protocol.Challenge;
import com.badwolfmc.guardian.protocol.GuardianChallengeCrypto;
import com.badwolfmc.guardian.protocol.GuardianChallengeTrustAnchors;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Client-pinned Guardian server-authentication roots embedded in this Cerberus mod. */
public final class GuardianServerTrustStore {
    public static final String ENTRY_NAME = GuardianChallengeTrustAnchors.ENTRY_NAME;
    private static final List<PublicKey> TRUSTED_KEYS = loadEmbedded();

    private GuardianServerTrustStore() {}

    public static boolean requiresAuthenticatedChallenge() {
        return !TRUSTED_KEYS.isEmpty();
    }

    public static boolean verify(Challenge challenge, UUID localPlayerId) {
        return verify(challenge, localPlayerId, Clock.systemUTC(), TRUSTED_KEYS);
    }

    static boolean verify(Challenge challenge, UUID localPlayerId, Clock clock, List<PublicKey> trustedKeys) {
        if (localPlayerId == null || trustedKeys == null || trustedKeys.isEmpty()) return false;
        return GuardianChallengeCrypto.verify(trustedKeys, challenge, localPlayerId, clock);
    }

    static List<PublicKey> loadEmbedded() {
        ModContainer cerberus = FabricLoader.getInstance().getModContainer("cerberus").orElse(null);
        if (cerberus == null) return List.of();
        Path resource = cerberus.findPath(ENTRY_NAME).orElse(null);
        if (resource == null) return List.of();
        try {
            return readTrustAnchors(resource);
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException("embedded Guardian server trust anchors are invalid", ex);
        }
    }

    static List<PublicKey> readTrustAnchors(Path resource) throws IOException, GeneralSecurityException {
        BasicFileAttributes attributes = Files.readAttributes(
            resource, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || Files.isSymbolicLink(resource)) {
            throw new IOException("embedded Guardian server trust anchors are not a regular file");
        }
        if (attributes.size() < 1 || attributes.size() > GuardianChallengeTrustAnchors.MAX_TEXT_BYTES) {
            throw new IOException("embedded Guardian server trust anchors have an invalid size");
        }

        byte[] bytes = new byte[(int) attributes.size()];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        try (SeekableByteChannel channel = Files.newByteChannel(
            resource, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer);
                if (read < 0) break;
            }
            if (buffer.hasRemaining() || channel.read(ByteBuffer.allocate(1)) != -1) {
                throw new IOException("embedded Guardian server trust anchors changed while reading");
            }
        }

        BasicFileAttributes after = Files.readAttributes(
            resource, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!sameSnapshot(attributes, after)) {
            throw new IOException("embedded Guardian server trust anchors changed while reading");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException ex) {
            throw new IOException("embedded Guardian server trust anchors are not valid UTF-8", ex);
        }
        return GuardianChallengeTrustAnchors.parse(text);
    }

    private static boolean sameSnapshot(BasicFileAttributes before, BasicFileAttributes after) {
        Object beforeKey = before.fileKey();
        Object afterKey = after.fileKey();
        boolean sameKey = beforeKey == null || afterKey == null || beforeKey.equals(afterKey);
        return sameKey
            && before.size() == after.size()
            && before.lastModifiedTime().equals(after.lastModifiedTime())
            && after.isRegularFile();
    }
}
