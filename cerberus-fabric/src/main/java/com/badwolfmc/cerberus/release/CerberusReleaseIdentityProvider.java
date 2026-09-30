package com.badwolfmc.cerberus.release;

import com.badwolfmc.guardian.protocol.CerberusReleaseArtifact;
import com.badwolfmc.guardian.protocol.CerberusReleaseIdentity;
import com.badwolfmc.guardian.protocol.ProtocolException;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Loads and self-checks optional official release metadata from the Cerberus JAR. */
public final class CerberusReleaseIdentityProvider {
    private static volatile boolean initialized;
    private static volatile CerberusReleaseIdentity cached;

    private CerberusReleaseIdentityProvider() {}

    public static CerberusReleaseIdentity current() {
        if (initialized) return cached;
        synchronized (CerberusReleaseIdentityProvider.class) {
            if (!initialized) {
                cached = load(FabricLoader.getInstance());
                initialized = true;
            }
            return cached;
        }
    }

    static CerberusReleaseIdentity load(FabricLoader loader) {
        try {
            ModContainer container = loader.getModContainer("cerberus").orElse(null);
            if (container == null) return null;
            ModOrigin origin = container.getOrigin();
            if (origin.getKind() != ModOrigin.Kind.PATH) return null;
            var paths = origin.getPaths();
            if (paths.size() != 1) return null;
            Path jar = paths.getFirst();
            if (jar == null || Files.isSymbolicLink(jar)
                || !Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)) return null;

            CerberusReleaseIdentity identity = CerberusReleaseArtifact.readIdentity(jar);
            if (identity == null) return null;
            String runtimeVersion = container.getMetadata().getVersion().getFriendlyString();
            if (!runtimeVersion.equals(identity.releaseVersion())) return null;
            if (!CerberusReleaseArtifact.canonicalDigest(jar).equals(identity.canonicalDigest())) return null;
            return identity;
        } catch (IOException | ProtocolException | RuntimeException ex) {
            // The client must not expose local paths in protocol/log diagnostics. A malformed/tampered release
            // simply loses the optional signed-release capability and will be denied if the server requires it.
            return null;
        }
    }
}
