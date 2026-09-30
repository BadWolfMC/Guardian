package com.badwolfmc.guardian.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolCodecTest {
    @Test
    void presenceRoundTripsNegotiationMetadata() throws Exception {
        Presence presence = new Presence(1, 2, GuardianProtocol.REQUIRED_CAPABILITIES, "1.0.0");
        assertEquals(presence, ProtocolCodec.decodePresence(ProtocolCodec.encodePresence(presence)));
    }

    @Test
    void protocolVersionBoundsMatchUnsignedShortWireEncoding() throws Exception {
        Presence maximum = new Presence(
            GuardianProtocol.MAX_PROTOCOL_VERSION,
            GuardianProtocol.MAX_PROTOCOL_VERSION,
            GuardianProtocol.REQUIRED_CAPABILITIES,
            "1.0.0"
        );
        assertEquals(maximum, ProtocolCodec.decodePresence(ProtocolCodec.encodePresence(maximum)));

        assertThrows(IllegalArgumentException.class,
            () -> new Presence(0, 1, GuardianProtocol.REQUIRED_CAPABILITIES, "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> new Presence(1, GuardianProtocol.MAX_PROTOCOL_VERSION + 1,
                GuardianProtocol.REQUIRED_CAPABILITIES, "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> new Challenge(0, GuardianProtocol.REQUIRED_CAPABILITIES, nonce()));
        assertThrows(IllegalArgumentException.class,
            () -> new Response(GuardianProtocol.MAX_PROTOCOL_VERSION + 1,
                GuardianProtocol.REQUIRED_CAPABILITIES, nonce(), minimalManifest()));
    }

    @Test
    void artifactHashCapabilityIsRequiredByProtocolV1() {
        assertEquals(1L << 3, GuardianProtocol.CAP_ARTIFACT_SHA256);
        assertEquals(
            GuardianProtocol.CAP_ARTIFACT_SHA256,
            GuardianProtocol.REQUIRED_CAPABILITIES & GuardianProtocol.CAP_ARTIFACT_SHA256
        );
    }

    @Test
    void challengeRoundTripsCapabilitiesAndNonce() throws Exception {
        Challenge challenge = new Challenge(1, GuardianProtocol.REQUIRED_CAPABILITIES, nonce());
        Challenge decoded = ProtocolCodec.decodeChallenge(ProtocolCodec.encodeChallenge(challenge));
        assertEquals(challenge.protocolVersion(), decoded.protocolVersion());
        assertEquals(challenge.requiredCapabilities(), decoded.requiredCapabilities());
        assertArrayEquals(challenge.nonce(), decoded.nonce());
    }

    @Test
    void canonicalManifestRoundTripsDeterministicallyWithArtifactIdentity() throws Exception {
        Manifest manifest = ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2",
            "0.19.5",
            "0.2.0",
            GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(
                new ManifestEntry("z-child", "2", "a-parent", OriginKind.NESTED),
                new ManifestEntry("a-parent", "1", null, OriginKind.ARCHIVE, hash(7)),
                new ManifestEntry("builtin-java", "25", null, OriginKind.BUILTIN),
                new ManifestEntry("devmod", "1", null, OriginKind.DIRECTORY),
                new ManifestEntry("unknownmod", "1", null, OriginKind.MIXED_OR_UNKNOWN)
            )
        ));
        Response response = new Response(1, GuardianProtocol.REQUIRED_CAPABILITIES, nonce(), manifest);
        byte[] encoded = ProtocolCodec.encodeResponse(response);
        Response decoded = ProtocolCodec.decodeResponse(encoded);

        assertEquals(manifest, decoded.manifest());
        assertArrayEquals(encoded, ProtocolCodec.encodeResponse(decoded));
        assertEquals(hash(7), decoded.manifest().entries().stream()
            .filter(entry -> entry.modId().equals("a-parent")).findFirst().orElseThrow().artifactSha256());
        assertNull(decoded.manifest().entries().stream()
            .filter(entry -> entry.modId().equals("z-child")).findFirst().orElseThrow().artifactSha256());
    }

    @Test
    void malformedDigestLengthIsRejected() {
        Response response = new Response(
            GuardianProtocol.VERSION,
            GuardianProtocol.REQUIRED_CAPABILITIES,
            nonce(),
            minimalManifest()
        );
        byte[] encoded = ProtocolCodec.encodeResponse(response);
        // A one-entry response ends with: hasDigest=true, digestLength=32, 32 digest bytes.
        encoded[encoded.length - ArtifactSha256.BYTES - 1] = (byte) (ArtifactSha256.BYTES - 1);
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(encoded));
    }

    @Test
    void artifactDigestEncodingAndPlacementAreStrict() {
        assertThrows(IllegalArgumentException.class, () -> new ArtifactSha256("A".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new ArtifactSha256("0".repeat(63)));
        assertThrows(IllegalArgumentException.class, () -> ArtifactSha256.fromBytes(new byte[31]));

        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(new ManifestEntry("archive", "1", null, OriginKind.ARCHIVE)))));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(new ManifestEntry("builtin", "1", null, OriginKind.BUILTIN, hash(1))))));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(
                archive("parent", "1", 1),
                new ManifestEntry("nested", "1", "parent", OriginKind.NESTED, hash(2))
            ))));
    }

    @Test
    void nestedOriginAndParentRelationshipMustBeStructurallyConsistent() {
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(new ManifestEntry("nested-without-parent", "1", null, OriginKind.NESTED)))));

        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(
                archive("parent", "1", 1),
                new ManifestEntry("child", "1", "parent", OriginKind.DIRECTORY)
            ))));
    }

    @Test
    void ambiguousTopLevelOriginRemainsStructurallyValidButUnhashed() {
        Manifest manifest = ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(new ManifestEntry("devmod", "1", null, OriginKind.MIXED_OR_UNKNOWN))
        ));
        assertEquals(OriginKind.MIXED_OR_UNKNOWN, manifest.entries().getFirst().originKind());
        assertNull(manifest.entries().getFirst().artifactSha256());
    }

    @Test
    void realisticLargeNestedManifestRoundTrips() throws Exception {
        List<ManifestEntry> entries = new ArrayList<>();
        entries.add(archive("root", "1.0.0", 9));
        for (int i = 1; i < 300; i++) {
            entries.add(new ManifestEntry("m" + String.format("%03d", i), "1.2.3+build." + i,
                "root", OriginKind.NESTED));
        }

        Manifest manifest = ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "0.19.5", "0.1.0-phase2.5", GuardianProtocol.REQUIRED_CAPABILITIES, entries));
        byte[] encoded = ProtocolCodec.encodeResponse(
            new Response(GuardianProtocol.VERSION, GuardianProtocol.REQUIRED_CAPABILITIES, nonce(), manifest));

        Response decoded = ProtocolCodec.decodeResponse(encoded);
        ManifestCanonicalizer.validateCanonical(decoded.manifest());
        assertEquals(300, decoded.manifest().entries().size());
        assertEquals(manifest, decoded.manifest());
    }

    @Test
    void manifestWireModelContainsOnlyPrivacySafePolicyFields() {
        assertEquals(
            List.of("minecraftVersion", "fabricLoaderVersion", "cerberusVersion", "capabilities", "entries"),
            Arrays.stream(Manifest.class.getRecordComponents()).map(component -> component.getName()).toList()
        );
        assertEquals(
            List.of("modId", "version", "parentModId", "originKind", "artifactSha256"),
            Arrays.stream(ManifestEntry.class.getRecordComponents()).map(component -> component.getName()).toList()
        );

        byte[] encoded = ProtocolCodec.encodeResponse(new Response(
            GuardianProtocol.VERSION,
            GuardianProtocol.REQUIRED_CAPABILITIES,
            nonce(),
            minimalManifest()
        ));
        assertFalse(new String(encoded, StandardCharsets.ISO_8859_1).contains("/home/"));
        assertFalse(new String(encoded, StandardCharsets.ISO_8859_1).contains("C:\\Users\\"));
    }

    @Test
    void exactFieldBoundariesAreAcceptedAndOneByteOverIsRejected() {
        String maxId = "a" + "b".repeat(GuardianProtocol.MAX_MOD_ID_BYTES - 1);
        String maxVersion = "v".repeat(GuardianProtocol.MAX_VERSION_BYTES);
        String maxRelease = "r".repeat(GuardianProtocol.MAX_RELEASE_METADATA_BYTES);

        Manifest accepted = ManifestCanonicalizer.canonicalize(new Manifest(
            maxRelease,
            maxRelease,
            maxRelease,
            GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(new ManifestEntry(maxId, maxVersion, null, OriginKind.ARCHIVE, hash(3)))
        ));
        assertEquals(maxId, accepted.entries().getFirst().modId());

        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(new ManifestEntry("a" + "b".repeat(GuardianProtocol.MAX_MOD_ID_BYTES), "1", null,
                OriginKind.ARCHIVE, hash(1))))));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(new ManifestEntry("valid-id", "v".repeat(GuardianProtocol.MAX_VERSION_BYTES + 1), null,
                OriginKind.ARCHIVE, hash(1))))));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "r".repeat(GuardianProtocol.MAX_RELEASE_METADATA_BYTES + 1), "loader", "cerberus",
            GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(archive("valid-id", "1", 1)))));
    }

    @Test
    void fabricModIdSyntaxIsEnforced() {
        assertThrows(IllegalArgumentException.class, () -> canonicalSingle("a"));
        assertThrows(IllegalArgumentException.class, () -> canonicalSingle("1bad"));
        assertThrows(IllegalArgumentException.class, () -> canonicalSingle("BAD-ID"));
        assertThrows(IllegalArgumentException.class, () -> canonicalSingle("bad.id"));
    }

    @Test
    void manifestEntryCountBoundaryIsEnforced() {
        assertEquals(GuardianProtocol.MAX_MANIFEST_ENTRIES,
            ManifestCanonicalizer.canonicalize(manifestWithEntries(GuardianProtocol.MAX_MANIFEST_ENTRIES))
                .entries().size());
        assertThrows(IllegalArgumentException.class,
            () -> ManifestCanonicalizer.canonicalize(manifestWithEntries(GuardianProtocol.MAX_MANIFEST_ENTRIES + 1)));
    }

    @Test
    void containmentCycleAndDepthLimitsAreEnforced() {
        Manifest cycle = new Manifest("26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES, List.of(
            new ManifestEntry("aa", "1", "bb", OriginKind.NESTED),
            new ManifestEntry("bb", "1", "aa", OriginKind.NESTED)
        ));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(cycle));

        ManifestCanonicalizer.canonicalize(containmentChain(GuardianProtocol.MAX_RELATIONSHIP_DEPTH));
        assertThrows(IllegalArgumentException.class,
            () -> ManifestCanonicalizer.canonicalize(containmentChain(GuardianProtocol.MAX_RELATIONSHIP_DEPTH + 1)));
    }

    @Test
    void oversizedPayloadIsRejectedOnDecode() {
        assertThrows(ProtocolException.class,
            () -> ProtocolCodec.decodeResponse(new byte[GuardianProtocol.MAX_PAYLOAD_BYTES + 1]));
    }

    @Test
    void aggregatePayloadLimitIsEnforcedOnEncode() {
        List<ManifestEntry> entries = new ArrayList<>();
        for (int i = 0; i < GuardianProtocol.MAX_MANIFEST_ENTRIES; i++) {
            entries.add(new ManifestEntry("m" + String.format("%03d", i),
                "v".repeat(GuardianProtocol.MAX_VERSION_BYTES), null, OriginKind.ARCHIVE, hash(i)));
        }
        Manifest manifest = ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES, entries));
        Response response = new Response(GuardianProtocol.VERSION, GuardianProtocol.REQUIRED_CAPABILITIES,
            nonce(), manifest);

        assertThrows(IllegalArgumentException.class, () -> ProtocolCodec.encodeResponse(response));
    }

    @Test
    void truncatedResponseIsRejected() {
        Response response = new Response(GuardianProtocol.VERSION, GuardianProtocol.REQUIRED_CAPABILITIES,
            nonce(), minimalManifest());
        byte[] encoded = ProtocolCodec.encodeResponse(response);
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(truncated));
    }

    @Test
    void malformedUtf8IsRejected() {
        byte[] payload = ProtocolCodec.encodePresence(new Presence(1, 1, GuardianProtocol.REQUIRED_CAPABILITIES, "x"));
        payload[payload.length - 1] = (byte) 0x80;
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodePresence(payload));
    }

    @Test
    void protocolV1CapabilityMaskRejectsUnknownBits() {
        assertTrue(GuardianProtocol.supportsProtocolV1Capabilities(GuardianProtocol.REQUIRED_CAPABILITIES));
        assertTrue(GuardianProtocol.supportsProtocolV1Capabilities(GuardianProtocol.KNOWN_CAPABILITIES));
        assertFalse(GuardianProtocol.hasUnknownCapabilities(GuardianProtocol.KNOWN_CAPABILITIES));
        assertFalse(GuardianProtocol.supportsProtocolV1Capabilities(
            GuardianProtocol.KNOWN_CAPABILITIES | (1L << 40)));
        assertFalse(GuardianProtocol.supportsProtocolV1Capabilities(
            GuardianProtocol.REQUIRED_CAPABILITIES & ~GuardianProtocol.CAP_ARTIFACT_SHA256));
    }

    @Test
    void controlAndFormattingCharactersAreRejectedFromProtocolMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new Presence(
            1, 1, GuardianProtocol.REQUIRED_CAPABILITIES, "cerberus\nforged"));
        assertThrows(IllegalArgumentException.class, () -> new Presence(
            1, 1, GuardianProtocol.REQUIRED_CAPABILITIES, "cerberus\u202Eevil"));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(archive("safe-mod", "1\r\n[INFO] forged", 1)))));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2\u2028next", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(archive("safe-mod", "1", 1)))));
    }

    @Test
    void decoderRejectsControlCharactersEvenWhenWireUtf8IsWellFormed() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(GuardianProtocol.MAGIC);
            out.writeByte(GuardianProtocol.TYPE_PRESENCE);
            out.writeShort(GuardianProtocol.VERSION);
            out.writeShort(GuardianProtocol.VERSION);
            out.writeLong(GuardianProtocol.REQUIRED_CAPABILITIES);
            writeRawUtf8(out, "cerberus\n[INFO] forged");
        }
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodePresence(bytes.toByteArray()));
    }

    @Test
    void responseDecoderRejectsNonCanonicalBooleanFlags() throws Exception {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(rawOneEntryResponse(2, 1)));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(rawOneEntryResponse(0, 2)));
    }

    @Test
    void responseDecoderRejectsManifestCountAboveLimitBeforeAllocatingEntries() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(GuardianProtocol.MAGIC);
            out.writeByte(GuardianProtocol.TYPE_RESPONSE);
            out.writeShort(GuardianProtocol.VERSION);
            out.writeLong(GuardianProtocol.REQUIRED_CAPABILITIES);
            out.write(nonce());
            writeRawUtf8(out, "26.2");
            writeRawUtf8(out, "loader");
            writeRawUtf8(out, "cerberus");
            out.writeShort(GuardianProtocol.MAX_MANIFEST_ENTRIES + 1);
        }
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(bytes.toByteArray()));
    }

    @Test
    void exactMaximumPayloadIsAccepted() throws Exception {
        List<ManifestEntry> entries = new ArrayList<>();
        for (int i = 0; i < 218; i++) {
            entries.add(archive("m" + String.format("%03d", i),
                "v".repeat(GuardianProtocol.MAX_VERSION_BYTES), i));
        }
        entries.add(archive("m218", "v".repeat(47), 218));
        Manifest manifest = ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "l", "c", GuardianProtocol.REQUIRED_CAPABILITIES, entries));
        byte[] encoded = ProtocolCodec.encodeResponse(new Response(
            GuardianProtocol.VERSION, GuardianProtocol.REQUIRED_CAPABILITIES, nonce(), manifest));

        assertEquals(GuardianProtocol.MAX_PAYLOAD_BYTES, encoded.length);
        assertEquals(manifest, ProtocolCodec.decodeResponse(encoded).manifest());
    }

    @Test
    void duplicateAndImpossibleRelationshipsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "l", "c", GuardianProtocol.REQUIRED_CAPABILITIES, List.of(
                archive("aa", "1", 1),
                archive("aa", "2", 2)))));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "l", "c", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(new ManifestEntry("aa", "1", "missing", OriginKind.NESTED)))));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "l", "c", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(new ManifestEntry("bad id", "1", null, OriginKind.ARCHIVE, hash(1))))));
    }

    @Test
    void nonCanonicalWireManifestIsDetectable() {
        Manifest manifest = new Manifest("26.2", "l", "c", GuardianProtocol.REQUIRED_CAPABILITIES, List.of(
            archive("zz", "1", 1),
            archive("aa", "1", 2)
        ));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.validateCanonical(manifest));
    }

    private static byte[] rawOneEntryResponse(int parentFlag, int digestFlag) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(GuardianProtocol.MAGIC);
            out.writeByte(GuardianProtocol.TYPE_RESPONSE);
            out.writeShort(GuardianProtocol.VERSION);
            out.writeLong(GuardianProtocol.REQUIRED_CAPABILITIES);
            out.write(nonce());
            writeRawUtf8(out, "26.2");
            writeRawUtf8(out, "loader");
            writeRawUtf8(out, "cerberus");
            out.writeShort(1);
            writeRawUtf8(out, "modid");
            writeRawUtf8(out, "1");
            out.writeByte(parentFlag);
            if (parentFlag == 1) writeRawUtf8(out, "parent");
            out.writeByte(OriginKind.ARCHIVE.ordinal());
            out.writeByte(digestFlag);
            if (digestFlag == 1) {
                out.writeByte(ArtifactSha256.BYTES);
                out.write(new byte[ArtifactSha256.BYTES]);
            }
        }
        return bytes.toByteArray();
    }

    private static void writeRawUtf8(DataOutputStream out, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static Manifest canonicalSingle(String id) {
        return ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(new ManifestEntry(id, "1", null, OriginKind.ARCHIVE, hash(1)))));
    }

    private static Manifest minimalManifest() {
        return ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "0.19.5", "0.1.0-phase2.5", GuardianProtocol.REQUIRED_CAPABILITIES,
            List.of(archive("fabricloader", "0.19.5", 5))));
    }

    private static Manifest manifestWithEntries(int count) {
        List<ManifestEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(new ManifestEntry("m" + String.format("%03d", i), "1", null, OriginKind.ARCHIVE, hash(i)));
        }
        return new Manifest("26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES, entries);
    }

    private static Manifest containmentChain(int depth) {
        List<ManifestEntry> entries = new ArrayList<>();
        entries.add(archive("m00", "1", 1));
        for (int i = 1; i <= depth; i++) {
            entries.add(new ManifestEntry("m" + String.format("%02d", i), "1",
                "m" + String.format("%02d", i - 1), OriginKind.NESTED));
        }
        return new Manifest("26.2", "loader", "cerberus", GuardianProtocol.REQUIRED_CAPABILITIES, entries);
    }

    private static ManifestEntry archive(String id, String version, int seed) {
        return new ManifestEntry(id, version, null, OriginKind.ARCHIVE, hash(seed));
    }

    private static ArtifactSha256 hash(int seed) {
        byte[] bytes = new byte[ArtifactSha256.BYTES];
        Arrays.fill(bytes, (byte) seed);
        return ArtifactSha256.fromBytes(bytes);
    }

    private static byte[] nonce() {
        byte[] nonce = new byte[GuardianProtocol.NONCE_BYTES];
        for (int i = 0; i < nonce.length; i++) nonce[i] = (byte) i;
        return nonce;
    }
}
