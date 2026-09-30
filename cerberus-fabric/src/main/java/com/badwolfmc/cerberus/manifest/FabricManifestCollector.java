package com.badwolfmc.cerberus.manifest;

import com.badwolfmc.guardian.protocol.ArtifactSha256;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Manifest;
import com.badwolfmc.guardian.protocol.ManifestCanonicalizer;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.OriginKind;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Collects one immutable Loader environment snapshot for the lifetime of the client process. */
public final class FabricManifestCollector {
    private static volatile Manifest cachedManifest;

    private FabricManifestCollector() {}

    public static Manifest collect(long capabilities) {
        Manifest snapshot = cachedManifest;
        if (snapshot != null) {
            return withCapabilities(snapshot, capabilities);
        }
        synchronized (FabricManifestCollector.class) {
            snapshot = cachedManifest;
            if (snapshot == null) {
                snapshot = collectFresh(FabricLoader.getInstance(), capabilities);
                cachedManifest = snapshot;
            }
            return withCapabilities(snapshot, capabilities);
        }
    }

    private static Manifest withCapabilities(Manifest snapshot, long capabilities) {
        if (snapshot.capabilities() == capabilities) return snapshot;
        return new Manifest(snapshot.minecraftVersion(), snapshot.fabricLoaderVersion(),
            snapshot.cerberusVersion(), capabilities, snapshot.entries());
    }

    private static Manifest collectFresh(FabricLoader loader, long capabilities) {
        List<ManifestEntry> entries = new ArrayList<>();
        for (ModContainer mod : loader.getAllMods()) {
            OriginObservation origin = observeOrigin(mod);
            ArtifactSha256 digest = artifactDigest(origin);
            if (origin.originKind() == OriginKind.ARCHIVE && digest == null) {
                // If the path stopped being a stable readable archive between observation and hashing,
                // report the conservative ambiguous origin instead of emitting a misleading ARCHIVE
                // without the protocol-required digest or dropping the whole challenge into a timeout.
                origin = OriginObservation.mixed();
            }
            entries.add(new ManifestEntry(
                mod.getMetadata().getId(),
                mod.getMetadata().getVersion().getFriendlyString(),
                origin.parentModId(),
                origin.originKind(),
                digest
            ));
        }
        Manifest manifest = new Manifest(
            version(loader, "minecraft"),
            version(loader, "fabricloader"),
            version(loader, "cerberus"),
            capabilities,
            entries
        );
        return ManifestCanonicalizer.canonicalize(manifest);
    }

    private static OriginObservation observeOrigin(ModContainer mod) {
        try {
            String containingModId = mod.getContainingMod()
                .map(container -> container.getMetadata().getId())
                .orElse(null);
            ModOrigin origin = mod.getOrigin();
            ModOrigin.Kind loaderKind = origin.getKind();

            return switch (loaderKind) {
                case NESTED -> classifyOrigin(
                    mod.getMetadata().getType(),
                    containingModId,
                    loaderKind,
                    origin.getParentModId(),
                    List.of()
                );
                case PATH -> classifyOrigin(
                    mod.getMetadata().getType(),
                    containingModId,
                    loaderKind,
                    null,
                    List.copyOf(origin.getPaths())
                );
                default -> classifyOrigin(
                    mod.getMetadata().getType(),
                    containingModId,
                    loaderKind,
                    null,
                    List.of()
                );
            };
        } catch (RuntimeException ex) {
            // Loader origin accessors are kind-specific. If a provider returns an internally inconsistent
            // origin object, keep filesystem details private and report the conservative policy-visible state.
            return OriginObservation.mixed();
        }
    }

    /**
     * Converts public Fabric Loader origin metadata into Guardian's intentionally coarse wire model.
     * Package-private for adversarial tests; paths never leave the client and only an ARCHIVE path is retained
     * long enough to compute the required SHA-256.
     */
    static OriginObservation classifyOrigin(
        String metadataType,
        String containingModId,
        ModOrigin.Kind loaderKind,
        String reportedParentModId,
        List<Path> paths
    ) {
        boolean hasContainingMod = containingModId != null && !containingModId.isBlank();
        boolean builtin = "builtin".equals(metadataType);

        if (loaderKind == ModOrigin.Kind.NESTED) {
            if (builtin
                || !hasContainingMod
                || reportedParentModId == null
                || !containingModId.equals(reportedParentModId)) {
                return OriginObservation.mixed();
            }
            return new OriginObservation(containingModId, OriginKind.NESTED, null);
        }

        // Fabric's containing-mod relationship is specifically a nested-JAR relationship. A container that
        // claims a non-NESTED origin while also claiming a parent is internally inconsistent and must not gain
        // nested-policy treatment merely because one half of the Loader metadata says so.
        if (hasContainingMod) {
            return OriginObservation.mixed();
        }

        // Builtins are Loader/game-provider supplied metadata. Their path shape may legitimately differ from
        // ordinary installable mods (including multiple game paths), so classify them before PATH cardinality.
        if (builtin) {
            return new OriginObservation(null, OriginKind.BUILTIN, null);
        }

        if (loaderKind != ModOrigin.Kind.PATH || paths == null || paths.size() != 1) {
            return OriginObservation.mixed();
        }

        Path path = paths.getFirst();
        if (path == null || Files.isSymbolicLink(path)) {
            return OriginObservation.mixed();
        }
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return new OriginObservation(null, OriginKind.DIRECTORY, null);
        }
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return new OriginObservation(null, OriginKind.ARCHIVE, path);
        }
        return OriginObservation.mixed();
    }

    private static ArtifactSha256 artifactDigest(OriginObservation origin) {
        if (origin.originKind() != OriginKind.ARCHIVE || origin.archivePath() == null) {
            return null;
        }
        try {
            return ArtifactSha256.hashRegularFile(origin.archivePath(), GuardianProtocol.MAX_ARTIFACT_BYTES);
        } catch (IOException | SecurityException ex) {
            // Do not attach the exception: provider exceptions can contain an absolute client path.
            // Returning no digest causes collectFresh to downgrade this observation to MIXED_OR_UNKNOWN.
            return null;
        }
    }

    private static String version(FabricLoader loader, String id) {
        return loader.getModContainer(id)
            .map(ModContainer::getMetadata)
            .map(m -> m.getVersion().getFriendlyString())
            .orElseThrow(() -> new IllegalStateException("Required Fabric Loader mod container missing: " + id));
    }

    record OriginObservation(String parentModId, OriginKind originKind, Path archivePath) {
        OriginObservation {
            if (originKind == OriginKind.NESTED && parentModId == null) {
                throw new IllegalArgumentException("NESTED origin requires a containing mod");
            }
            if (originKind != OriginKind.NESTED && parentModId != null) {
                throw new IllegalArgumentException("only NESTED origin may carry a containing mod");
            }
            if ((originKind == OriginKind.ARCHIVE) != (archivePath != null)) {
                throw new IllegalArgumentException("only ARCHIVE origin may carry a hash path");
            }
        }

        static OriginObservation mixed() {
            return new OriginObservation(null, OriginKind.MIXED_OR_UNKNOWN, null);
        }
    }
}
