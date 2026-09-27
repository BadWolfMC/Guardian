# Phase 2.5 — Artifact identity and approved-artifact catalog

## Status

Implementation candidate on 2026-09-26 at project version `0.1.0-phase2.5`.

Phase 2.5 deliberately changes the unreleased protocol-v1 manifest shape in place. It does not introduce protocol v2 or compatibility code for the internal Phase 2 representation.

## Protocol-v1 artifact identity

Protocol v1 now requires `CAP_ARTIFACT_SHA256` in addition to the canonical-manifest, containment, and origin-kind capabilities.

A canonical manifest entry has one optional `ArtifactSha256` value with strict applicability:

- top-level `ARCHIVE`: digest required;
- `NESTED`: no digest; bytes are committed by the containing top-level archive plus the existing parent relationship;
- `BUILTIN`: no digest;
- `DIRECTORY`: no digest;
- `MIXED_OR_UNKNOWN`: no digest.

The algorithm is fixed as SHA-256 for protocol v1. The wire format uses a presence flag followed, when present, by a length byte that must be exactly 32 and then exactly 32 digest bytes. The Java model uses canonical lowercase 64-character hexadecimal solely for human-readable representation and catalog storage.

Cerberus classifies a top-level PATH origin as `ARCHIVE` only when Fabric Loader exposes exactly one regular non-symlink source file. A directory remains `DIRECTORY`; multiple roots, symlinks, and other ambiguous origin shapes remain `MIXED_OR_UNKNOWN` rather than receiving a misleading archive digest.

The Loader manifest is collected and cached once for the lifetime of the Cerberus client process. Artifact bytes are therefore hashed once per environment snapshot rather than on every Guardian challenge.

## Trust boundary

SHA-256 verifies the exact artifact bytes reported by a cooperating Cerberus client; it does not independently prove that a hostile/replaced Cerberus client reported those bytes truthfully.

Signed official Cerberus release identity and hostile-client hardening remain Phase 6 work.

## Approved-artifact administration

Guardian-Paper creates `plugins/Guardian/artifact-import/` as an administrator input surface and maintains `plugins/Guardian/artifacts.yml` as the durable identity catalog.

Import is explicit:

```text
/guardian artifacts scan
```

Permission:

```text
guardian.artifacts.scan
```

The command executes JAR inspection/hashing asynchronously. Startup creates the input directory and validates an existing catalog but does not automatically hash or merge candidate JARs. This keeps plugin enable deterministic and avoids moving administrator-controlled archive I/O onto the Paper primary thread.

The scanner treats every JAR as untrusted data. It:

- scans only the flat `artifact-import/` directory;
- considers `.jar` files only;
- rejects symlink/non-regular JAR candidates;
- never recursively walks subdirectories;
- never classloads, executes, installs, extracts, or invokes Fabric Loader against supplied files;
- opens JARs read-only;
- reads only root `fabric.mod.json` for identity metadata;
- bounds each candidate archive at 512 MiB and one scan at 2 GiB aggregate input;
- bounds each archive at 4,096 ZIP entries;
- bounds decompressed `fabric.mod.json` at 128 KiB;
- parses JSON with a bounded depth and duplicate-key rejection;
- derives mod ID/version from metadata, never the filename;
- computes SHA-256 over the exact complete JAR;
- rechecks basic file attributes after hashing and rejects a candidate changed during inspection; and
- rejects the whole import transaction if any candidate is malformed.

A scan is limited to 256 candidate JARs. Scanner diagnostics use candidate filenames rather than absolute paths.

## Durable catalog

`artifacts.yml` is intentionally catalog-owned rather than pretending arbitrary YAML formatting/comments can be round-tripped safely. The generated file states that policy belongs elsewhere and uses a strict schema:

```yaml
schema-version: 1
artifacts:
  sodium:
    versions:
      "0.9.1+mc26.2":
        sha256:
          - "...64 lowercase hex characters..."
```

The catalog supports multiple versions per mod ID and multiple exact SHA-256 values per ID/version. It is sorted deterministically by mod ID, version, and digest.

Existing catalog content is validated before candidate inspection is merged. Successful writes use a temporary sibling plus atomic replacement when supported. Import is add-only: rescans deduplicate exact identities, adding another JAR adds another identity, and deleting a previously imported JAR never removes its historical catalog entry. Guardian never makes an admission decision from the catalog in Phase 2.5.

## Phase 3 boundary

Phase 3 consumes two stable inputs:

1. the validated canonical manifest, including exact top-level archive identity; and
2. the durable artifact catalog.

Phase 3 owns all policy questions: whether hashes are required by a particular rule, version-only rules, allowed/denied/unlisted mods, required mods, nested inheritance semantics, named profiles, LuckPerms resolution, and bypasses. Phase 2.5 contains none of those decisions.
