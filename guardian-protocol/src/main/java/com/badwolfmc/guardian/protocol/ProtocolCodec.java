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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ProtocolCodec {
    private ProtocolCodec() {}

    public static byte[] encodePresence(Presence presence) {
        return write(out -> {
            header(out, GuardianProtocol.TYPE_PRESENCE);
            out.writeShort(presence.minProtocolVersion());
            out.writeShort(presence.maxProtocolVersion());
            out.writeLong(presence.capabilities());
            writeUtf8(out, presence.cerberusVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
        });
    }

    public static Presence decodePresence(byte[] payload) throws ProtocolException {
        return read(payload, GuardianProtocol.TYPE_PRESENCE, in -> {
            Presence presence = new Presence(
                u16(in),
                u16(in),
                in.readLong(),
                readUtf8(in, GuardianProtocol.MAX_RELEASE_METADATA_BYTES)
            );
            consumed(in);
            return presence;
        });
    }

    public static byte[] encodeChallenge(Challenge challenge) {
        return write(out -> {
            header(out, GuardianProtocol.TYPE_CHALLENGE);
            out.writeShort(challenge.protocolVersion());
            out.writeLong(challenge.requiredCapabilities());
            out.write(challenge.nonce());
            if ((challenge.requiredCapabilities() & GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE) != 0L) {
                GuardianChallengeAuthentication authentication = Objects.requireNonNull(
                    challenge.authentication(),
                    "authenticated challenge capability requires proof"
                );
                out.writeLong(authentication.playerId().getMostSignificantBits());
                out.writeLong(authentication.playerId().getLeastSignificantBits());
                out.writeLong(authentication.issuedAtEpochMillis());
                out.writeLong(authentication.expiresAtEpochMillis());
                out.write(authentication.ed25519Signature());
            }
        });
    }

    public static Challenge decodeChallenge(byte[] payload) throws ProtocolException {
        return read(payload, GuardianProtocol.TYPE_CHALLENGE, in -> {
            int protocolVersion = u16(in);
            long capabilities = in.readLong();
            byte[] nonce = exact(in, GuardianProtocol.NONCE_BYTES, "challenge nonce");
            GuardianChallengeAuthentication authentication = null;
            if ((capabilities & GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE) != 0L) {
                UUID playerId = new UUID(in.readLong(), in.readLong());
                long issuedAt = in.readLong();
                long expiresAt = in.readLong();
                byte[] signature = exact(
                    in,
                    GuardianProtocol.GUARDIAN_CHALLENGE_SIGNATURE_BYTES,
                    "Guardian challenge signature"
                );
                try {
                    authentication = new GuardianChallengeAuthentication(
                        playerId,
                        issuedAt,
                        expiresAt,
                        signature
                    );
                } catch (IllegalArgumentException ex) {
                    throw new ProtocolException("invalid Guardian challenge authentication: " + ex.getMessage());
                }
            }
            consumed(in);
            try {
                return new Challenge(protocolVersion, capabilities, nonce, authentication);
            } catch (IllegalArgumentException ex) {
                throw new ProtocolException("invalid Guardian challenge: " + ex.getMessage());
            }
        });
    }

    public static byte[] encodeResponse(Response response) {
        Manifest manifest = response.manifest();
        if (manifest.entries().size() > GuardianProtocol.MAX_MANIFEST_ENTRIES) {
            throw new IllegalArgumentException("too many manifest entries");
        }
        return write(out -> {
            header(out, GuardianProtocol.TYPE_RESPONSE);
            out.writeShort(response.protocolVersion());
            out.writeLong(response.capabilities());
            out.write(response.nonce());
            writeUtf8(out, manifest.minecraftVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
            writeUtf8(out, manifest.fabricLoaderVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
            writeUtf8(out, manifest.cerberusVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
            if ((response.capabilities() & GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE) != 0L) {
                CerberusReleaseIdentity identity = Objects.requireNonNull(
                    response.cerberusReleaseIdentity(),
                    "signed release capability requires identity"
                );
                writeUtf8(out, identity.releaseVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
                out.write(identity.canonicalDigest().bytes());
                out.write(identity.ed25519Signature());
            }
            out.writeShort(manifest.entries().size());
            for (ManifestEntry entry : manifest.entries()) {
                writeUtf8(out, entry.modId(), GuardianProtocol.MAX_MOD_ID_BYTES);
                writeUtf8(out, entry.version(), GuardianProtocol.MAX_VERSION_BYTES);
                out.writeBoolean(entry.parentModId() != null);
                if (entry.parentModId() != null) {
                    writeUtf8(out, entry.parentModId(), GuardianProtocol.MAX_MOD_ID_BYTES);
                }
                out.writeByte(entry.originKind().ordinal());
                out.writeBoolean(entry.artifactSha256() != null);
                if (entry.artifactSha256() != null) {
                    out.writeByte(ArtifactSha256.BYTES);
                    out.write(entry.artifactSha256().bytes());
                }
            }
        });
    }

    public static Response decodeResponse(byte[] payload) throws ProtocolException {
        return read(payload, GuardianProtocol.TYPE_RESPONSE, in -> {
            int protocolVersion = u16(in);
            long capabilities = in.readLong();
            byte[] nonce = exact(in, GuardianProtocol.NONCE_BYTES, "response nonce");
            String minecraftVersion = readUtf8(in, GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
            String loaderVersion = readUtf8(in, GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
            String cerberusVersion = readUtf8(in, GuardianProtocol.MAX_RELEASE_METADATA_BYTES);

            CerberusReleaseIdentity releaseIdentity = null;
            if ((capabilities & GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE) != 0L) {
                String releaseVersion = readUtf8(in, GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
                ArtifactSha256 releaseDigest = ArtifactSha256.fromBytes(
                    exact(in, ArtifactSha256.BYTES, "Cerberus release canonical digest")
                );
                byte[] releaseSignature = exact(
                    in,
                    CerberusReleaseIdentity.ED25519_SIGNATURE_BYTES,
                    "Cerberus release Ed25519 signature"
                );
                releaseIdentity = new CerberusReleaseIdentity(releaseVersion, releaseDigest, releaseSignature);
            }

            int count = u16(in);
            if (count > GuardianProtocol.MAX_MANIFEST_ENTRIES) {
                throw new ProtocolException("manifest entry count exceeds limit");
            }
            List<ManifestEntry> entries = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                String modId = readUtf8(in, GuardianProtocol.MAX_MOD_ID_BYTES);
                String version = readUtf8(in, GuardianProtocol.MAX_VERSION_BYTES);
                String parentModId = readBooleanStrict(in, "parent-present")
                    ? readUtf8(in, GuardianProtocol.MAX_MOD_ID_BYTES)
                    : null;
                int origin = in.readUnsignedByte();
                if (origin >= OriginKind.values().length) {
                    throw new ProtocolException("invalid origin kind " + origin);
                }

                ArtifactSha256 digest = null;
                if (readBooleanStrict(in, "artifact-digest-present")) {
                    int digestLength = in.readUnsignedByte();
                    if (digestLength != ArtifactSha256.BYTES) {
                        throw new ProtocolException("invalid SHA-256 digest length " + digestLength);
                    }
                    digest = ArtifactSha256.fromBytes(exact(in, digestLength, "SHA-256 digest"));
                }
                entries.add(new ManifestEntry(modId, version, parentModId, OriginKind.values()[origin], digest));
            }

            consumed(in);
            Manifest manifest = new Manifest(
                minecraftVersion,
                loaderVersion,
                cerberusVersion,
                capabilities,
                entries
            );
            return new Response(protocolVersion, capabilities, nonce, manifest, releaseIdentity);
        });
    }

    private static void header(DataOutputStream out, int type) throws IOException {
        out.writeInt(GuardianProtocol.MAGIC);
        out.writeByte(type);
    }

    private static <T> T read(byte[] payload, int type, Reader<T> reader) throws ProtocolException {
        if (payload == null || payload.length == 0) {
            throw new ProtocolException("empty payload");
        }
        if (payload.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            throw new ProtocolException("payload exceeds " + GuardianProtocol.MAX_PAYLOAD_BYTES + " bytes");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != GuardianProtocol.MAGIC) {
                throw new ProtocolException("invalid protocol magic");
            }
            if (in.readUnsignedByte() != type) {
                throw new ProtocolException("unexpected message type");
            }
            return reader.read(in);
        } catch (EOFException ex) {
            throw new ProtocolException("truncated payload", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new ProtocolException("unable to decode payload", ex);
        }
    }

    private static byte[] write(Writer writer) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                writer.write(out);
            }
            byte[] payload = buffer.toByteArray();
            if (payload.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException(
                    "payload exceeds " + GuardianProtocol.MAX_PAYLOAD_BYTES + " bytes"
                );
            }
            return payload;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void writeUtf8(DataOutputStream out, String value, int maxBytes) throws IOException {
        byte[] bytes = ProtocolText.encode(value, maxBytes, "protocol text field");
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readUtf8(DataInputStream in, int maxBytes) throws IOException, ProtocolException {
        int length = u16(in);
        if (length == 0 || length > maxBytes) {
            throw new ProtocolException("invalid UTF-8 field length " + length);
        }
        byte[] bytes = exact(in, length, "UTF-8 field");
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
            ProtocolText.validate(value, maxBytes, "protocol text field");
            return value;
        } catch (CharacterCodingException ex) {
            throw new ProtocolException("malformed UTF-8", ex);
        } catch (IllegalArgumentException ex) {
            throw new ProtocolException("unsafe protocol text field", ex);
        }
    }

    private static byte[] exact(DataInputStream in, int length, String description)
        throws IOException, ProtocolException {
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new ProtocolException("truncated " + description);
        }
        return bytes;
    }

    private static boolean readBooleanStrict(DataInputStream in, String description)
        throws IOException, ProtocolException {
        int value = in.readUnsignedByte();
        if (value == 0) {
            return false;
        }
        if (value == 1) {
            return true;
        }
        throw new ProtocolException("invalid " + description + " flag " + value);
    }

    private static int u16(DataInputStream in) throws IOException {
        return Short.toUnsignedInt(in.readShort());
    }

    private static void consumed(DataInputStream in) throws IOException, ProtocolException {
        if (in.available() != 0) {
            throw new ProtocolException("unexpected trailing bytes");
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(DataInputStream in) throws IOException, ProtocolException;
    }
}
