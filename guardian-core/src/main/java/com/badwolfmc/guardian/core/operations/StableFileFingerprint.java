package com.badwolfmc.guardian.core.operations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Content fingerprint used only to detect administrator-file changes during candidate loading. */
public final class StableFileFingerprint {
    private static final String ABSENT = "<absent>";

    private StableFileFingerprint() {}

    public static String required(Path path, int maxBytes) throws IOException {
        return digest(SafeRegularFile.read(path, maxBytes));
    }

    public static String optional(Path path, int maxBytes) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return ABSENT;
        return required(path, maxBytes);
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
