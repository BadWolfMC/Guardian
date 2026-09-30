package com.badwolfmc.guardian.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CerberusReleaseIdentityTest {
    @TempDir Path tempDir;

    @Test
    void canonicalDigestIgnoresZipOrderingTimestampsAndEmbeddedSignatureMetadata() throws Exception {
        Map<String, byte[]> firstOrder = new LinkedHashMap<>();
        firstOrder.put("fabric.mod.json", "{\"id\":\"cerberus\"}".getBytes());
        firstOrder.put("com/badwolfmc/Cerberus.class", new byte[] {1, 2, 3});
        Map<String, byte[]> reverseOrder = new LinkedHashMap<>();
        reverseOrder.put("com/badwolfmc/Cerberus.class", new byte[] {1, 2, 3});
        reverseOrder.put("fabric.mod.json", "{\"id\":\"cerberus\"}".getBytes());

        Path first = jar("first.jar", firstOrder, 1_000L);
        Path second = jar("second.jar", reverseOrder, 9_000L);
        ArtifactSha256 digest = CerberusReleaseArtifact.canonicalDigest(first);
        assertEquals(digest, CerberusReleaseArtifact.canonicalDigest(second));

        KeyPair keys = KeyPairGenerator.getInstance(CerberusReleaseCrypto.ALGORITHM).generateKeyPair();
        CerberusReleaseIdentity identity = new CerberusReleaseIdentity(
            "1.0.0", digest, sign(keys.getPrivate(), "1.0.0", digest));
        inject(second, CerberusReleaseMetadataCodec.encode(identity));

        assertEquals(digest, CerberusReleaseArtifact.canonicalDigest(second));
        CerberusReleaseIdentity decoded = CerberusReleaseArtifact.readIdentity(second);
        assertEquals(identity.releaseVersion(), decoded.releaseVersion());
        assertEquals(identity.canonicalDigest(), decoded.canonicalDigest());
        assertArrayEquals(identity.ed25519Signature(), decoded.ed25519Signature());
    }

    @Test
    void canonicalDigestChangesWhenSignedJarContentChanges() throws Exception {
        Path first = jar("first.jar", Map.of("a.txt", new byte[] {1, 2, 3}), 1_000L);
        Path changed = jar("changed.jar", Map.of("a.txt", new byte[] {1, 2, 4}), 1_000L);
        assertNotEquals(
            CerberusReleaseArtifact.canonicalDigest(first),
            CerberusReleaseArtifact.canonicalDigest(changed));
    }

    @Test
    void ed25519VerificationBindsVersionAndCanonicalDigest() throws Exception {
        KeyPair trusted = KeyPairGenerator.getInstance(CerberusReleaseCrypto.ALGORITHM).generateKeyPair();
        KeyPair other = KeyPairGenerator.getInstance(CerberusReleaseCrypto.ALGORITHM).generateKeyPair();
        ArtifactSha256 digest = ArtifactSha256.fromBytes(new byte[ArtifactSha256.BYTES]);
        CerberusReleaseIdentity identity = new CerberusReleaseIdentity(
            "1.2.3", digest, sign(trusted.getPrivate(), "1.2.3", digest));

        assertTrue(CerberusReleaseCrypto.verify(trusted.getPublic(), identity));
        assertFalse(CerberusReleaseCrypto.verify(other.getPublic(), identity));
        assertFalse(CerberusReleaseCrypto.verify(trusted.getPublic(),
            new CerberusReleaseIdentity("1.2.4", digest, identity.ed25519Signature())));
        assertFalse(CerberusReleaseCrypto.verify(trusted.getPublic(),
            new CerberusReleaseIdentity("1.2.3", ArtifactSha256.fromBytes(bytes((byte) 7)),
                identity.ed25519Signature())));
    }

    @Test
    void signedReleaseResponseRoundTripsOnlyWhenCapabilityAndIdentityAgree() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance(CerberusReleaseCrypto.ALGORITHM).generateKeyPair();
        ArtifactSha256 digest = ArtifactSha256.fromBytes(bytes((byte) 3));
        CerberusReleaseIdentity identity = new CerberusReleaseIdentity(
            "1.0.0", digest, sign(keys.getPrivate(), "1.0.0", digest));
        long capabilities = GuardianProtocol.REQUIRED_CAPABILITIES | GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE;
        Manifest manifest = ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "0.19.5", "1.0.0", capabilities,
            java.util.List.of(new ManifestEntry("cerberus", "1.0.0", null, OriginKind.ARCHIVE,
                ArtifactSha256.fromBytes(bytes((byte) 4))))));
        Response response = new Response(GuardianProtocol.VERSION, capabilities,
            new byte[GuardianProtocol.NONCE_BYTES], manifest, identity);

        Response decoded = ProtocolCodec.decodeResponse(ProtocolCodec.encodeResponse(response));
        assertEquals(identity.releaseVersion(), decoded.cerberusReleaseIdentity().releaseVersion());
        assertEquals(identity.canonicalDigest(), decoded.cerberusReleaseIdentity().canonicalDigest());
        assertArrayEquals(identity.ed25519Signature(), decoded.cerberusReleaseIdentity().ed25519Signature());

        assertThrows(IllegalArgumentException.class, () -> new Response(
            GuardianProtocol.VERSION, capabilities, new byte[GuardianProtocol.NONCE_BYTES], manifest, null));
        assertThrows(IllegalArgumentException.class, () -> new Response(
            GuardianProtocol.VERSION, GuardianProtocol.REQUIRED_CAPABILITIES,
            new byte[GuardianProtocol.NONCE_BYTES],
            new Manifest("26.2", "0.19.5", "1.0.0", GuardianProtocol.REQUIRED_CAPABILITIES, manifest.entries()),
            identity));
    }


    @Test
    void canonicalDigestRejectsMoreThanMaximumFileEntries() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < CerberusReleaseArtifact.MAX_FILE_ENTRIES + 1; i++) {
            entries.put("entries/" + i, new byte[0]);
        }
        Path jar = jar("too-many-entries.jar", entries, 1_000L);
        IOException error = assertThrows(IOException.class, () -> CerberusReleaseArtifact.canonicalDigest(jar));
        assertTrue(error.getMessage().contains("exceeds " + CerberusReleaseArtifact.MAX_FILE_ENTRIES + " file entries"));
    }

    @Test
    void canonicalDigestRejectsOverlongEntryName() throws Exception {
        String name = "a".repeat(CerberusReleaseArtifact.MAX_ENTRY_NAME_BYTES + 1);
        Path jar = jar("overlong-entry.jar", Map.of(name, new byte[] {1}), 1_000L);
        IOException error = assertThrows(IOException.class, () -> CerberusReleaseArtifact.canonicalDigest(jar));
        assertTrue(error.getMessage().contains(
            "exceeds " + CerberusReleaseArtifact.MAX_ENTRY_NAME_BYTES + " UTF-8 bytes"));
    }

    @Test
    void canonicalDigestRejectsPathConfusableAndControlCharacterEntryNames() throws Exception {
        for (String entryName : List.of(
            "/absolute.class",
            "./relative.class",
            "dir/../escape.class",
            "dir//double.class",
            "windows\\path.class",
            "line\nforge.class"
        )) {
            Path jar = jar("ambiguous-" + Math.abs(entryName.hashCode()) + ".jar",
                Map.of(entryName, new byte[] {1}), 1_000L);
            IOException error = assertThrows(IOException.class,
                () -> CerberusReleaseArtifact.canonicalDigest(jar), entryName);
            assertTrue(error.getMessage().contains("entry name")
                || error.getMessage().contains("entry-name"), entryName);
        }
    }

    @Test
    void canonicalDigestRejectsZipBombStyleUncompressedContentBeyondLimit() throws Exception {
        Path jar = tempDir.resolve("too-much-content.jar");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("payload.bin"));
            byte[] block = new byte[64 * 1024];
            long remaining = CerberusReleaseArtifact.MAX_CANONICAL_CONTENT_BYTES + 1L;
            while (remaining > 0L) {
                int count = (int) Math.min(block.length, remaining);
                out.write(block, 0, count);
                remaining -= count;
            }
            out.closeEntry();
        }
        IOException error = assertThrows(IOException.class, () -> CerberusReleaseArtifact.canonicalDigest(jar));
        assertTrue(error.getMessage().contains("canonical content exceeds"));
    }

    @Test
    void metadataCodecRejectsTrailingBytes() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance(CerberusReleaseCrypto.ALGORITHM).generateKeyPair();
        ArtifactSha256 digest = ArtifactSha256.fromBytes(bytes((byte) 1));
        CerberusReleaseIdentity identity = new CerberusReleaseIdentity(
            "1.0.0", digest, sign(keys.getPrivate(), "1.0.0", digest));
        byte[] encoded = CerberusReleaseMetadataCodec.encode(identity);
        byte[] trailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(ProtocolException.class, () -> CerberusReleaseMetadataCodec.decode(trailing));
    }

    private Path jar(String name, Map<String, byte[]> entries, long timestamp) throws IOException {
        Path path = tempDir.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(timestamp);
                out.putNextEntry(zipEntry);
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return path;
    }

    private static void inject(Path jar, byte[] metadata) throws IOException {
        try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), Map.of())) {
            Path entry = zip.getPath("/" + CerberusReleaseMetadataCodec.ENTRY_NAME);
            Files.createDirectories(entry.getParent());
            Files.write(entry, metadata);
        }
    }

    private static byte[] bytes(byte value) {
        byte[] bytes = new byte[ArtifactSha256.BYTES];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
    private static byte[] sign(PrivateKey privateKey, String version, ArtifactSha256 digest) throws Exception {
        Signature signer = Signature.getInstance(CerberusReleaseCrypto.ALGORITHM);
        signer.initSign(privateKey);
        signer.update(CerberusReleaseCrypto.signingMessage(version, digest));
        return signer.sign();
    }

}
