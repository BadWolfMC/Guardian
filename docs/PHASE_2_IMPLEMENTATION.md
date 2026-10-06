# Phase 2 — Cerberus and Guardian protocol v1

> **Historical implementation note:** the `guardian.cerberus.dev.*` verification switches mentioned below were later removed from public Cerberus source during final `1.0.0` release preparation.

## Status

Phase 2 implementation and live transport verification are complete as of 2026-09-26. This closeout hardening revision adds adversarial boundary coverage and two representation-tightening changes discovered during final review: Fabric mod IDs now use Fabric's actual 2–64 character identifier constraint, and in-memory protocol versions are constrained to the unsigned-16-bit range used on the wire.

The operator already confirmed the Phase 2 implementation builds under Java 25 / Gradle 9.7.1 before this closeout hardening revision, and the supplied source archive contains the corresponding 73-test green result. After applying the closeout patch, run the clean automated gate in `PHASE_2_VERIFICATION.md`; that final gate is the only remaining closeout confirmation and requires no additional live-client matrix if green.

## Canonical manifest boundary

Cerberus converts Fabric Loader state into the platform-neutral `Manifest` / `ManifestEntry` model before serialization. The manifest contains bounded Minecraft, Fabric Loader, and Cerberus release versions; a protocol capability bitset; and every Loader-known mod as `(mod id, version, containing parent id or null, origin kind)`.

Entries are sorted lexicographically by mod ID. Duplicate IDs, missing parents, self-parenting, cycles, excessive containment depth, blank/oversized fields, excessive entry counts, non-canonical wire ordering, and malformed UTF-8 are invalid. Fabric IDs are additionally constrained to the Loader metadata contract: 2–64 lowercase ASCII identifier characters, beginning with a letter and followed by letters, digits, `_`, or `-`.

A child records its immediate containing mod; this is sufficient to reconstruct the complete containment tree while avoiding duplicated child lists on the wire.

Origin kind is deliberately coarse and privacy-safe: `ARCHIVE`, `DIRECTORY`, `NESTED`, `BUILTIN`, or `MIXED_OR_UNKNOWN`. Cerberus uses Loader origin metadata and tests path *kind* locally but never serializes a path. Multiple development/classpath roots therefore collapse deterministically to `DIRECTORY` when all are directories or `MIXED_OR_UNKNOWN` when mixed.

The required runtime containers (`minecraft`, `fabricloader`, and `cerberus`) must exist in Loader state. Cerberus no longer substitutes an invented `unknown` release value if one is unexpectedly missing; local collection fails instead of transmitting ambiguous environment metadata.

## Protocol v1

Presence advertises a min/max protocol range, capability bitset, and bounded Cerberus release version. Guardian selects protocol 1 and requires the v1 canonical-manifest, containment-relationship, and origin-kind capabilities. This lets a recognizable Cerberus incompatibility become `CERBERUS_PROTOCOL_UNSUPPORTED` before waiting for a manifest timeout.

Protocol versions use an unsigned 16-bit wire field and are constrained in-memory to `1..65535`, preventing accidental truncation before serialization.

Challenge contains selected protocol, required capabilities, and a fresh 16-byte nonce. Response echoes selected protocol/capabilities and nonce and carries the canonical manifest. Guardian validates the nonce, negotiated capabilities, canonical structure, and hard limits before producing `CERBERUS_VERIFIED`. Phase 2 deliberately does not evaluate per-mod allow/deny policy; `MANIFEST_DENIED` remains a decision reason for Phase 3 but is no longer produced by a synthetic test mod.

### Hard limits

- encoded Guardian payload: 65,536 bytes
- manifest entries: 512
- Fabric mod ID: 64 ASCII/UTF-8 bytes, with Fabric identifier syntax enforced
- mod version: 256 UTF-8 bytes
- Minecraft/Loader/Cerberus release metadata: 256 UTF-8 bytes each
- protocol version field: 1–65,535
- containment depth: 16
- nonce: 16 random bytes
- accepted responses per challenge: 1
- outstanding handshake: at most 10 seconds

The payload ceiling is intentionally the final aggregate bound: a manifest cannot use every per-field maximum simultaneously if doing so would exceed 64 KiB. Paper 26.2's supported plugin-message API allows messages up to 1,048,576 bytes, so Guardian's 64 KiB cap remains below the platform transport ceiling. The representative 166-entry BadWolfMC client manifest encoded to approximately 8,032 bytes under protocol v1.

## Observed Fabric manifest characterization

The live BadWolfMC Fabric 26.2 client produced 166 canonical Loader-known entries and successfully completed both standalone Paper and Velocity-authoritative protocol-v1 handshakes.

Observed categories include:

- ordinary installed mods as top-level `ARCHIVE` entries;
- `minecraft` and `java` as `BUILTIN` entries;
- `fabricloader` as a top-level archive;
- Fabric API as a top-level `fabric-api` archive with its Loader-known API modules represented as `NESTED` children;
- C2ME and other bundled systems with numerous immediate nested modules/libraries;
- multi-level containment, including nested children whose own parent is itself nested; and
- Cerberus itself as an ordinary top-level archive entry.

This confirms Phase 3 must distinguish administrator-policy-addressable client mods from baseline/bootstrap/runtime and bundle-internal entries. Allowlist mode must not require administrators to enumerate Java, Minecraft, every Fabric API module, and every bundled implementation library merely to admit an otherwise approved client. Conversely, nested entries cannot simply be deleted, because that would create an invisible policy blind spot.

During Phase 2 verification, the opt-in `-Dguardian.cerberus.dev.logManifest=true` output contained only IDs, versions, parent IDs, and coarse origin kinds; no filesystem paths were transmitted or logged by the protocol model. That verification switch is not present in the public `1.0.0` client source.

A dedicated local Loom/directory-origin live client was not required for closeout because the public Loader origin model is handled deterministically in source and the roadmap already reserves broader adversarial development-origin testing for Phase 6. No production policy for `DIRECTORY`/`MIXED_OR_UNKNOWN` is invented in Phase 2.

## Live failure semantics verified

The 2026-09-26 live matrix verified all Phase 2 outcomes that must remain distinct:

- Fabric without Cerberus → `CERBERUS_REQUIRED`;
- Cerberus announcing unsupported protocol 99 → `CERBERUS_PROTOCOL_UNSUPPORTED`;
- compatible Cerberus with canonical 166-entry manifest → `CERBERUS_VERIFIED`;
- Cerberus present but deliberately suppressing its response → `CERBERUS_TIMEOUT` after the bounded 10-second wait; and
- deliberately malformed/truncated response → immediate `MANIFEST_INVALID` rather than a timeout or crash.

Standalone vanilla and OptiFine regressions also remained allowed under the current Paper policy. Velocity's retained feasibility adapter still denies OptiFine because its production policy/configuration ownership belongs to Phase 5 under BRIDGE-004; that result is not a Phase 2 transport regression.

The normal Velocity/Cerberus path completed attestation during Velocity CONFIGURATION, asserted trusted admission to Guardian-Paper, and Guardian-Paper admitted the player from the verified proxy assertion without duplicate PLAY attestation.

## Automated hardening coverage

The closeout suite adds explicit coverage for:

- protocol-version wire bounds;
- exact field maximums and one-byte-over rejection;
- Fabric mod-ID syntax;
- maximum manifest entry count and one-over rejection;
- containment cycles and exact depth boundary;
- aggregate encode-side payload overflow;
- explicit truncated-response decoding;
- realistic 300-entry nested manifest round-trip;
- privacy-safe manifest record components;
- one-response-per-challenge session state on both Paper and Velocity; and
- retained nonce, capability, malformed UTF-8, duplicate-ID, missing-parent, non-canonical-order, proxy, classification, and Protection regressions.

## Phase 3 boundary

No named profiles, LuckPerms profile selection, mod allowlist/denylist policy, required-mod rules, administrator version predicates, admission policy bypasses, artifact-hash enforcement, or baseline-entry policy are implemented here. Phase 2 supplies validated canonical input and evidence from a real manifest; Phase 3 owns the policy semantics.

## Phase 2.5 protocol-v1 revision note

Phase 2.5 subsequently revised this **unreleased** protocol-v1 manifest representation in place. The Phase 2 transport/session findings remain authoritative, but a current production-v1 manifest additionally requires `CAP_ARTIFACT_SHA256` and exact SHA-256 on every top-level `ARCHIVE` entry. See `PHASE_2_5_IMPLEMENTATION.md` for the current canonical artifact-identity contract. No compatibility path for the internal Phase 2-only v1 shape is retained.
