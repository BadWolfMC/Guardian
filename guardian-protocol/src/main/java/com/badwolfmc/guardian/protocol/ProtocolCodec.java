package com.badwolfmc.guardian.protocol;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

public final class ProtocolCodec {
    private ProtocolCodec() {}

    public static byte[] encodePresence(Presence p) {
        return write(out -> { header(out, GuardianProtocol.TYPE_PRESENCE); out.writeShort(p.minProtocolVersion()); out.writeShort(p.maxProtocolVersion()); out.writeLong(p.capabilities()); writeUtf8(out, p.cerberusVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES); });
    }
    public static Presence decodePresence(byte[] payload) throws ProtocolException {
        return read(payload, GuardianProtocol.TYPE_PRESENCE, in -> {
            Presence p = new Presence(u16(in), u16(in), in.readLong(), readUtf8(in, GuardianProtocol.MAX_RELEASE_METADATA_BYTES)); consumed(in); return p;
        });
    }
    public static byte[] encodeChallenge(Challenge c) {
        return write(out -> {
            header(out, GuardianProtocol.TYPE_CHALLENGE);
            out.writeShort(c.protocolVersion());
            out.writeLong(c.requiredCapabilities());
            out.write(c.nonce());
            if ((c.requiredCapabilities() & GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE) != 0L) {
                GuardianChallengeAuthentication auth = Objects.requireNonNull(
                    c.authentication(), "authenticated challenge capability requires proof");
                out.writeLong(auth.playerId().getMostSignificantBits());
                out.writeLong(auth.playerId().getLeastSignificantBits());
                out.writeLong(auth.issuedAtEpochMillis());
                out.writeLong(auth.expiresAtEpochMillis());
                out.write(auth.ed25519Signature());
            }
        });
    }
    public static Challenge decodeChallenge(byte[] payload) throws ProtocolException {
        return read(payload, GuardianProtocol.TYPE_CHALLENGE, in -> {
            int v=u16(in); long caps=in.readLong(); byte[] nonce=exact(in, GuardianProtocol.NONCE_BYTES, "challenge nonce");
            GuardianChallengeAuthentication authentication = null;
            if ((caps & GuardianProtocol.CAP_AUTHENTICATED_GUARDIAN_CHALLENGE) != 0L) {
                java.util.UUID playerId = new java.util.UUID(in.readLong(), in.readLong());
                long issuedAt = in.readLong();
                long expiresAt = in.readLong();
                byte[] signature = exact(in, GuardianProtocol.GUARDIAN_CHALLENGE_SIGNATURE_BYTES,
                    "Guardian challenge signature");
                try {
                    authentication = new GuardianChallengeAuthentication(playerId, issuedAt, expiresAt, signature);
                } catch (IllegalArgumentException ex) {
                    throw new ProtocolException("invalid Guardian challenge authentication: " + ex.getMessage());
                }
            }
            consumed(in);
            try {
                return new Challenge(v,caps,nonce,authentication);
            } catch (IllegalArgumentException ex) {
                throw new ProtocolException("invalid Guardian challenge: " + ex.getMessage());
            }
        });
    }
    public static byte[] encodeResponse(Response r) {
        Manifest m=r.manifest();
        if (m.entries().size()>GuardianProtocol.MAX_MANIFEST_ENTRIES) throw new IllegalArgumentException("too many manifest entries");
        return write(out -> {
            header(out, GuardianProtocol.TYPE_RESPONSE); out.writeShort(r.protocolVersion()); out.writeLong(r.capabilities()); out.write(r.nonce());
            writeUtf8(out,m.minecraftVersion(),GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
            writeUtf8(out,m.fabricLoaderVersion(),GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
            writeUtf8(out,m.cerberusVersion(),GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
            if ((r.capabilities() & GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE) != 0L) {
                CerberusReleaseIdentity identity = Objects.requireNonNull(
                    r.cerberusReleaseIdentity(), "signed release capability requires identity");
                writeUtf8(out, identity.releaseVersion(), GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
                out.write(identity.canonicalDigest().bytes());
                out.write(identity.ed25519Signature());
            }
            out.writeShort(m.entries().size());
            for (ManifestEntry e:m.entries()) {
                writeUtf8(out,e.modId(),GuardianProtocol.MAX_MOD_ID_BYTES);
                writeUtf8(out,e.version(),GuardianProtocol.MAX_VERSION_BYTES);
                out.writeBoolean(e.parentModId()!=null);
                if(e.parentModId()!=null) writeUtf8(out,e.parentModId(),GuardianProtocol.MAX_MOD_ID_BYTES);
                out.writeByte(e.originKind().ordinal());
                out.writeBoolean(e.artifactSha256()!=null);
                if(e.artifactSha256()!=null) {
                    out.writeByte(ArtifactSha256.BYTES);
                    out.write(e.artifactSha256().bytes());
                }
            }
        });
    }
    public static Response decodeResponse(byte[] payload) throws ProtocolException {
        return read(payload, GuardianProtocol.TYPE_RESPONSE, in -> {
            int v=u16(in); long caps=in.readLong(); byte[] nonce=exact(in,GuardianProtocol.NONCE_BYTES,"response nonce");
            String mc=readUtf8(in,GuardianProtocol.MAX_RELEASE_METADATA_BYTES), loader=readUtf8(in,GuardianProtocol.MAX_RELEASE_METADATA_BYTES), cerb=readUtf8(in,GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
            CerberusReleaseIdentity releaseIdentity = null;
            if ((caps & GuardianProtocol.CAP_SIGNED_CERBERUS_RELEASE) != 0L) {
                String releaseVersion = readUtf8(in, GuardianProtocol.MAX_RELEASE_METADATA_BYTES);
                ArtifactSha256 releaseDigest = ArtifactSha256.fromBytes(
                    exact(in, ArtifactSha256.BYTES, "Cerberus release canonical digest"));
                byte[] releaseSignature = exact(in, CerberusReleaseIdentity.ED25519_SIGNATURE_BYTES,
                    "Cerberus release Ed25519 signature");
                releaseIdentity = new CerberusReleaseIdentity(releaseVersion, releaseDigest, releaseSignature);
            }
            int count=u16(in); if(count>GuardianProtocol.MAX_MANIFEST_ENTRIES) throw new ProtocolException("manifest entry count exceeds limit");
            List<ManifestEntry> entries=new ArrayList<>(count);
            for(int i=0;i<count;i++) {
                String id=readUtf8(in,GuardianProtocol.MAX_MOD_ID_BYTES), ver=readUtf8(in,GuardianProtocol.MAX_VERSION_BYTES);
                String parent=readBooleanStrict(in,"parent-present")?readUtf8(in,GuardianProtocol.MAX_MOD_ID_BYTES):null;
                int origin=in.readUnsignedByte();
                if(origin>=OriginKind.values().length) throw new ProtocolException("invalid origin kind "+origin);
                ArtifactSha256 digest = null;
                if (readBooleanStrict(in,"artifact-digest-present")) {
                    int digestLength = in.readUnsignedByte();
                    if (digestLength != ArtifactSha256.BYTES) {
                        throw new ProtocolException("invalid SHA-256 digest length " + digestLength);
                    }
                    digest = ArtifactSha256.fromBytes(exact(in, digestLength, "SHA-256 digest"));
                }
                entries.add(new ManifestEntry(id,ver,parent,OriginKind.values()[origin],digest));
            }
            consumed(in); return new Response(v,caps,nonce,new Manifest(mc,loader,cerb,caps,entries), releaseIdentity);
        });
    }
    private static void header(DataOutputStream out,int type)throws IOException{out.writeInt(GuardianProtocol.MAGIC);out.writeByte(type);}
    private static <T>T read(byte[] payload,int type,Reader<T> r)throws ProtocolException{
        if(payload==null||payload.length==0)throw new ProtocolException("empty payload"); if(payload.length>GuardianProtocol.MAX_PAYLOAD_BYTES)throw new ProtocolException("payload exceeds "+GuardianProtocol.MAX_PAYLOAD_BYTES+" bytes");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(payload))){if(in.readInt()!=GuardianProtocol.MAGIC)throw new ProtocolException("invalid protocol magic");if(in.readUnsignedByte()!=type)throw new ProtocolException("unexpected message type");return r.read(in);}catch(EOFException e){throw new ProtocolException("truncated payload",e);}catch(IOException|IllegalArgumentException e){throw new ProtocolException("unable to decode payload",e);}
    }
    private static byte[] write(Writer w){try{ByteArrayOutputStream b=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(b)){w.write(out);}byte[] p=b.toByteArray();if(p.length>GuardianProtocol.MAX_PAYLOAD_BYTES)throw new IllegalArgumentException("payload exceeds "+GuardianProtocol.MAX_PAYLOAD_BYTES+" bytes");return p;}catch(IOException e){throw new IllegalStateException(e);}}
    private static void writeUtf8(DataOutputStream out,String s,int max)throws IOException{byte[] b=ProtocolText.encode(s,max,"protocol text field");out.writeShort(b.length);out.write(b);}
    private static String readUtf8(DataInputStream in,int max)throws IOException,ProtocolException{int n=u16(in);if(n==0||n>max)throw new ProtocolException("invalid UTF-8 field length "+n);byte[] b=exact(in,n,"UTF-8 field");try{String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();ProtocolText.validate(value,max,"protocol text field");return value;}catch(CharacterCodingException e){throw new ProtocolException("malformed UTF-8",e);}catch(IllegalArgumentException e){throw new ProtocolException("unsafe protocol text field",e);}}
    private static byte[] exact(DataInputStream in,int n,String what)throws IOException,ProtocolException{byte[] b=in.readNBytes(n);if(b.length!=n)throw new ProtocolException("truncated "+what);return b;}
    private static boolean readBooleanStrict(DataInputStream in,String what)throws IOException,ProtocolException{int value=in.readUnsignedByte();if(value==0)return false;if(value==1)return true;throw new ProtocolException("invalid "+what+" flag "+value);}
    private static int u16(DataInputStream in)throws IOException{return Short.toUnsignedInt(in.readShort());}
    private static void consumed(DataInputStream in)throws IOException,ProtocolException{if(in.available()!=0)throw new ProtocolException("unexpected trailing bytes");}
    @FunctionalInterface private interface Writer{void write(DataOutputStream out)throws IOException;}
    @FunctionalInterface private interface Reader<T>{T read(DataInputStream in)throws IOException,ProtocolException;}
}
