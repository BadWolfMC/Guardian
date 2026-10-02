package com.badwolfmc.cerberus.release;

import com.badwolfmc.guardian.protocol.CerberusReleaseCrypto;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

/** Offline helper for generating the Cerberus release-signing Ed25519 identity. */
public final class CerberusReleaseIdentityGenerator {
    private CerberusReleaseIdentityGenerator() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: CerberusReleaseIdentityGenerator <private-key-out> <public-key-out>");
        Path privateOut=Path.of(args[0]).toAbsolutePath().normalize();
        Path publicOut=Path.of(args[1]).toAbsolutePath().normalize();
        if (privateOut.equals(publicOut)) throw new IllegalArgumentException("private/public outputs must differ");
        if (Files.exists(privateOut,LinkOption.NOFOLLOW_LINKS)||Files.exists(publicOut,LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("refusing to overwrite an existing Cerberus release-signing identity file");
        if (privateOut.getParent()==null||publicOut.getParent()==null) throw new IllegalArgumentException("identity outputs must have parent directories");
        Files.createDirectories(privateOut.getParent()); Files.createDirectories(publicOut.getParent());
        var pair=KeyPairGenerator.getInstance(CerberusReleaseCrypto.ALGORITHM).generateKeyPair();
        String privatePem="-----BEGIN PRIVATE KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(pair.getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n";
        String publicBase64=Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())+"\n";
        boolean privateCreated=false;
        try {
            Files.writeString(privateOut,privatePem,StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE); privateCreated=true; restrictPrivatePermissions(privateOut);
            Files.writeString(publicOut,publicBase64,StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        } catch(Exception ex) { if(privateCreated) try{Files.deleteIfExists(privateOut);}catch(Exception ignored){} throw ex; }
        System.out.println("Cerberus release-signing identity generated.");
        System.out.println("Private key: "+privateOut); System.out.println("Public verification key: "+publicOut);
        System.out.println("Keep the private key offline; the public base64 key belongs in policy.yml cerberus-release-trust.ed25519-public-keys.");
    }
    private static void restrictPrivatePermissions(Path path) throws Exception {
        if(Files.getFileStore(path).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(path,EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));
    }
}
