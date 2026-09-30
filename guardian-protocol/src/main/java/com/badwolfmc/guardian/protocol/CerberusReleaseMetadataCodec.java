package com.badwolfmc.guardian.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Strict compact metadata stored inside official signed Cerberus JARs. */
public final class CerberusReleaseMetadataCodec {
    public static final String ENTRY_NAME = "META-INF/guardian/cerberus-release.bin";
    public static final int MAX_BYTES = 512;
    private static final int MAGIC = 0x43525331; // CRS1

    private CerberusReleaseMetadataCodec() {}

    public static byte[] encode(CerberusReleaseIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        byte[] version = ProtocolText.encode(
            identity.releaseVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES,
            "Cerberus signed release version");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeShort(version.length);
                out.write(version);
                out.write(identity.canonicalDigest().bytes());
                out.write(identity.ed25519Signature());
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_BYTES) {
                throw new IllegalArgumentException("Cerberus release metadata exceeds " + MAX_BYTES + " bytes");
            }
            return encoded;
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory release metadata encoding failed", impossible);
        }
    }

    public static CerberusReleaseIdentity decode(byte[] bytes) throws ProtocolException {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new ProtocolException("invalid Cerberus release metadata length " + bytes.length);
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new ProtocolException("invalid Cerberus release metadata magic");
            int versionLength = Short.toUnsignedInt(in.readShort());
            if (versionLength == 0 || versionLength > GuardianProtocol.MAX_RELEASE_METADATA_BYTES) {
                throw new ProtocolException("invalid Cerberus release version length " + versionLength);
            }
            byte[] versionBytes = in.readNBytes(versionLength);
            if (versionBytes.length != versionLength) throw new ProtocolException("truncated Cerberus release version");
            String version;
            try {
                version = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(versionBytes)).toString();
                ProtocolText.validate(version, GuardianProtocol.MAX_RELEASE_METADATA_BYTES,
                    "Cerberus signed release version");
            } catch (CharacterCodingException | IllegalArgumentException ex) {
                throw new ProtocolException("invalid Cerberus release version", ex);
            }
            byte[] digest = in.readNBytes(ArtifactSha256.BYTES);
            if (digest.length != ArtifactSha256.BYTES) throw new ProtocolException("truncated Cerberus release digest");
            byte[] signature = in.readNBytes(CerberusReleaseIdentity.ED25519_SIGNATURE_BYTES);
            if (signature.length != CerberusReleaseIdentity.ED25519_SIGNATURE_BYTES) {
                throw new ProtocolException("truncated Cerberus release signature");
            }
            if (in.available() != 0) throw new ProtocolException("unexpected trailing Cerberus release metadata");
            return new CerberusReleaseIdentity(version, ArtifactSha256.fromBytes(digest), signature);
        } catch (EOFException ex) {
            throw new ProtocolException("truncated Cerberus release metadata", ex);
        } catch (IOException ex) {
            throw new ProtocolException("unable to decode Cerberus release metadata", ex);
        }
    }
}
