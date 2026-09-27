package com.badwolfmc.guardian.core.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Transactional scan -> validate -> merge service with atomic catalog and policy-fragment outputs. */
public final class ArtifactImportService {
    private final Path artifactImportDirectory;
    private final ArtifactCatalogStore catalogStore;
    private final ArtifactPolicyFragmentStore policyFragmentStore;
    private final ApprovedArtifactScanner scanner;

    public ArtifactImportService(Path dataDirectory) {
        this(
            dataDirectory.resolve("artifact-import"),
            new ArtifactCatalogStore(dataDirectory.resolve("artifacts.yml")),
            new ArtifactPolicyFragmentStore(dataDirectory.resolve("artifact-import-rules.yml")),
            new ApprovedArtifactScanner()
        );
    }

    ArtifactImportService(
        Path artifactImportDirectory,
        ArtifactCatalogStore catalogStore,
        ArtifactPolicyFragmentStore policyFragmentStore,
        ApprovedArtifactScanner scanner
    ) {
        this.artifactImportDirectory = artifactImportDirectory;
        this.catalogStore = catalogStore;
        this.policyFragmentStore = policyFragmentStore;
        this.scanner = scanner;
    }

    public synchronized void ensureInputDirectory() throws ArtifactCatalogException {
        if (Files.exists(artifactImportDirectory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(artifactImportDirectory)
                || !Files.isDirectory(artifactImportDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw new ArtifactCatalogException(
                    "artifact-import must be a real directory, not a symlink or other file type");
            }
            return;
        }
        try {
            Files.createDirectories(artifactImportDirectory);
        } catch (IOException ex) {
            throw new ArtifactCatalogException("could not create artifact-import directory", ex);
        }
    }

    /** Validates existing durable content without importing candidate JARs. */
    public synchronized ArtifactCatalog validateCatalog() throws ArtifactCatalogException {
        return catalogStore.load();
    }

    /** No catalog bytes are changed unless every candidate JAR validates and convenience output is written successfully. */
    public synchronized ArtifactImportResult scanAndMerge() throws ArtifactCatalogException {
        ensureInputDirectory();
        ArtifactCatalog existing = catalogStore.load();
        ApprovedArtifactScanner.ScanResult scan = scanner.scan(artifactImportDirectory);
        ArtifactCatalog merged = existing.merge(scan.artifacts());
        int added = merged.size() - existing.size();
        boolean changed = added != 0 || !catalogStore.exists();
        policyFragmentStore.store(scan.artifacts());
        if (changed) {
            catalogStore.store(merged);
        }
        return new ArtifactImportResult(
            scan.jarCount(),
            scan.artifacts().size(),
            added,
            merged.size(),
            changed
        );
    }
}
