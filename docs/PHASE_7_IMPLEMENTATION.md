# Phase 7 implementation — operations, UX, documentation, and release hardening

**Candidate implementation.** This document records the Phase 7 source changes constructed against the exact operator-supplied repository `Guardian(20261001-114756).zip` (SHA-256 `1382269cd860e8c4d7c4368e4b187e883b7e38677cef23697235d52264775d28`). Phase 7 is not formally closed until the Java 25 / Gradle 9.7.1 verification gate and the small changes-sensitive live matrix in `PHASE_7_VERIFICATION.md` pass.

## Readiness review

The Phase 6 architecture was release-ready and did not require reopening protocol, authority, inspection, or hostile-client assumptions. Phase 7 identified three actual pre-1.0 release blockers:

1. no explicit public configuration-upgrade contract;
2. no reproducible CI/release-candidate/checksum/license pipeline; and
3. incomplete operator/security/deployment documentation for the hardened architecture.

Command routing, Admission/Protection separation, Velocity authority, active inspection, signed Cerberus provenance, Guardian server authentication, and proxy assertions were already structurally sound. Phase 7 therefore treats command work as UX/diagnostic polish rather than a redesign.

## Public configuration schema and migration

Paper `config.yml`, Velocity `config.yml`, and shared `policy.yml` advance to **schema 2** for the public-release baseline.

Guardian implements exactly one automatic compatibility path: the final Phase 6 / pre-1.0 release-candidate schema-1 shape to schema 2. It intentionally does **not** accumulate compatibility code for arbitrary internal Phase 1-5 development snapshots.

`ConfigurationSchemaMigrator`:

- reads through the existing bounded stable regular-file primitive;
- accepts only a valid top-level numeric `schema-version: 1`;
- changes the schema marker surgically rather than reserializing administrator YAML;
- preserves comments, order, whitespace/newline style, and administrator values;
- backfills only the reviewed `cerberus-release-trust` policy block when absent, disabled by default;
- reparses the migrated candidate before publication;
- compares exact source bytes both before backup and immediately before publication;
- writes exact original bytes to a timestamped `.pre-schema2-...bak` file; and
- uses atomic replacement where supported.

Malformed files remain owned by the existing strict startup-recovery path. Newer schemas are never silently downgraded.

Both Paper and Velocity run the migration after first-start resource provisioning and before runtime activation.

## Operations UX

The existing `/guardian` and `/guardianv` authority split is preserved.

Status output now includes the local security posture relevant to the host:

- Guardian server-authentication enabled/disabled;
- signed Cerberus release trust required/optional; and
- `N/A` on assertion-only Paper where the proxy owns Admission.

No command RPC or manifest forwarding was introduced.

Normal authoritative Admission summaries no longer prepend `Guardian ` inside the message text, removing the cosmetic `[Guardian] Guardian ...` / `[guardian]: Guardian ...` duplication produced by platform logger prefixes.

## Server-authentication mismatch UX

No new client-to-server failure oracle or protocol semantic was added. A stock Cerberus client that rejects an unauthenticated Guardian challenge still withholds the manifest and the server still reaches the bounded formal `CERBERUS_TIMEOUT` outcome.

The locale timeout message now explicitly tells the player that, **if Cerberus reports an untrusted Guardian server identity, they should not bypass that warning and should contact staff**. This improves diagnosis while preserving the existing privacy/fail-closed boundary and avoiding a new unauthenticated client claim that Guardian would have to trust.

## Release-manager ergonomics

Phase 7 adds `tools/release-manager.ps1` as the supported release-manager wrapper. It provides four explicit actions:

- generate the offline Cerberus release-signing Ed25519 identity;
- generate the separate Guardian server-authentication Ed25519 identity;
- sign Cerberus with an explicit private-key path and explicit finished output path; and
- generate deterministic SHA-256 checksums for staged JARs.

The helper removes the need for external OpenSSL key-generation choreography but does **not** weaken key separation or destination requirements. The wrapper requires an explicit validated release version and passes it as `guardianVersion`; an explicit output directory now produces `cerberus-fabric-<version>-signed.jar` automatically, while an advanced exact `SignedOutput` override must itself contain the requested version. The underlying signing Gradle task still requires `cerberusReleasePrivateKey` plus `cerberusSignedOutput` and refuses an output path equal to the unsigned input. Gradle archive tasks also disable preserved file timestamps and enforce reproducible entry ordering so unsigned build artifacts are deterministic for equivalent inputs.

The source remains correct for the Phase 6 Loom finding: Minecraft 26.2 non-obfuscated Fabric uses `jar`, not `remapJar`, as the signing input.

## CI and release-candidate workflow

Phase 7 adds:

- a normal Java 25 CI gate using the checked-in Gradle wrapper;
- a manual Release Candidate workflow with an explicit, shell-safe validated artifact version;
- staged unsigned server/proxy/Cerberus build artifacts;
- SHA-256 generation;
- archive resource-name checks for conventional private-key filenames/extensions; and
- extracted non-class-resource scanning for PEM private-key material.

CI intentionally does not receive the Cerberus release-signing private key. Official Cerberus signing remains an offline release-manager operation after the source/build candidate is green.

## License and NOTICE material

Guardian-Paper and Guardian-Velocity shade SnakeYAML Engine. Their JAR tasks now embed:

- Guardian GPLv3 license material;
- `THIRD_PARTY_NOTICES.md`; and
- the Apache-2.0 license used by bundled SnakeYAML Engine.

Cerberus embeds Guardian GPL/notice material but does not bundle SnakeYAML Engine.

The remaining Paper, Velocity, Fabric, LuckPerms, Geyser, and Floodgate APIs are platform/build integrations rather than shaded Guardian runtime payloads. The audit is recorded in `THIRD_PARTY_NOTICES.md` and `PHASE_7_VERIFICATION.md`.

## Documentation delivered

Phase 7 adds or completes:

- standalone Paper deployment;
- Velocity network deployment;
- Geyser/Floodgate integration;
- LuckPerms/profile ownership including `/lp` versus `/lpv`;
- Guardian Protection operations;
- eZProtector migration;
- locale/message customization;
- configuration upgrades;
- all three key domains and rotation/compromise handling;
- consolidated threat/security boundaries; and
- release-manager/checksum/release-candidate procedure.

README and development documentation point administrators/release managers to those surfaces.

## Deliberate non-changes

Phase 7 does not:

- persist historical manifests;
- forward authoritative manifests to Paper backends;
- add Paper↔Velocity command RPC;
- alter the Phase 6 wire protocol or policy semantics;
- add a reusable Cerberus secret;
- characterize signed Cerberus as remote attestation;
- add NMS/CraftBukkit implementation reflection/server Mixins/packet libraries; or
- begin the Minecraft 26.3 port.

No new implementation bridge is required by this candidate.


## 2026-10-05 closeout review

Operator verification closed the planned Phase 7 live matrix at 365 green tests and passed clean Paper/Velocity operation, the supported schema-1 upgrade, release-key/server-key/signing/checksum helpers, the Velocity-authoritative signed-Cerberus happy path, and the Guardian server-authentication mismatch UX.

The clean Velocity status check exposed one late release-metadata defect: `GuardianVelocityPlugin.VERSION` was still hard-coded to `0.1.0-phase6`. The closeout patch replaces it with a generated compile-time constant sourced from the Gradle project version. This preserves Velocity annotation processing while making `-PguardianVersion` authoritative across the Paper descriptor, Velocity plugin metadata/status, Cerberus Fabric metadata, and signed Cerberus release identity.

Release-manager feedback also resulted in a small ergonomics improvement: the normal signing path now accepts an explicit output directory and automatically creates `cerberus-fabric-<version>-signed.jar`; an advanced exact `SignedOutput` override remains available but must contain the requested version. `RELEASE_PROCESS.md` now begins with a plain-language normal-release walkthrough.

These changes do not alter Admission/Protection authority, protocol semantics, policy semantics, key-domain separation, manifest privacy, or signed-release trust claims.
