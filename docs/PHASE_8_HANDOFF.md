# Phase 8 handoff — Minecraft / Paper / Fabric 26.3 port

**Phase 7.5 production-readiness verification formally closed on 2026-10-06. This handoff is unblocked by that prerequisite, but do not begin implementation until the stable `1.0.0` Minecraft/Paper 26.2 release has been published/retained and Paper 26.3 is sufficiently stable to target deliberately.**


## Phase 7 / 7.5 prerequisite note

Phase 7 is formally closed at 366 green tests with metadata/version parity verified across Paper, Velocity, and Cerberus. Phase 7.5 then completed final production readiness, GitHub preparedness, repository/documentation cleanup, release rehearsal, focused live hardening, and rollback verification on Minecraft 26.2. Its final gate is 376 tests with 0 failures/errors and 11 documented Windows symlink-privilege skips; the final rehearsal used `1.0.0-rc.2`, commit `ddcad249c4837fdcf68c1c3a00d8fda0ecbb2da5`, GitHub Actions run `37418021934` / attempt `1`.

Phase 8 remains a platform-version port rather than a continuation of release-preparedness work. Phase 7.5 is no longer a blocker. By operator decision, the stable `1.0.0` Minecraft/Paper 26.2 release must be published and retained before Phase 8 implementation starts; Paper 26.3 platform readiness remains the separate external start gate.


## Phase 7.5 release-process inheritance

When Phase 7.5 closes, Phase 8 inherits the CI -> offline signing -> draft GitHub Release chain. A platform port must not move the Cerberus release-signing private key into CI or reintroduce local rebuilding of the unsigned signing input. The final Paper/Velocity release JARs remain exact CI outputs; the final Cerberus release is the locally/offline signed derivative of the exact checked CI unsigned artifact.

The production runbook, packaged dual-domain defaults, project-facing support URL, Cerberus icon, and public repository maintenance files are release surfaces to preserve unless the 26.3 port has a concrete reason to change them.

## Entering invariant

Phase 8 inherits the public 1.0 architecture; it is a platform-version port, not an opportunity to redesign Guardian/Cerberus security semantics.

Preserve:

- independent Admission and Protection domains;
- Guardian-Velocity as BadWolfMC's preferred network Admission authority and Guardian-Paper standalone capability;
- assertion-only Velocity-authority Paper backends;
- `/guardian` Paper-local and `/guardianv` Velocity/network-authoritative ownership;
- supported Geyser/Floodgate classification before Java/Cerberus handling;
- bounded active-session-only inspection with no historical manifest persistence;
- shared platform-neutral Admission policy/evaluator;
- exact-artifact SHA-256 semantics;
- signed Cerberus release provenance as compliance/provenance hardening, not remote attestation;
- Guardian → Cerberus Ed25519 server authentication as manifest-disclosure protection, not hostile-client attestation;
- the three distinct key domains; and
- the prohibition on NMS/CraftBukkit implementation reflection/server Mixins/ProtocolLib/PacketEvents/private-packet workarounds.

## Preconditions

Before changing target versions:

1. **SATISFIED:** Phase 7 is formally closed and the Java 25 / Gradle 9.7.1 gate is green.
2. **SATISFIED:** Phase 7.5 production-readiness/GitHub-preparedness work is formally closed.
3. **SATISFIED:** the `1.0.0-rc.2` release-candidate/finalized artifact set and checksums exist.
4. **SATISFIED for the 26.2 baseline:** clean-install/schema/signing/deployment/rollback and focused authority/live checks are closed in the preceding phase records.
5. **REQUIRED BEFORE PHASE 8 IMPLEMENTATION:** publish and retain the final `1.0.0` Minecraft/Paper 26.2 release artifacts/provenance.
6. **REQUIRED AT PHASE 8 KICKOFF:** Paper 26.3 is sufficiently stable to target deliberately.
7. **REQUIRED DURING PORT REVIEW:** identify any 26.2-only workaround explicitly before removal.

## Port scope

Phase 8 should:

- update Paper to a deliberate 26.3 target;
- update Fabric Loader/Fabric API/Minecraft 26.3 targets;
- update Velocity/Geyser/Floodgate integration versions only where required/appropriate;
- compile against supported public APIs;
- retest CONFIGURATION/PLAY fallback behavior on standalone Paper;
- retest Velocity's configuration-stage authority and backend switching;
- retest Geyser/Floodgate classification;
- retest real Fabric manifest collection, archive hashing, nesting, and signed Cerberus metadata;
- retest Guardian server authentication and ZIPFS trust-anchor loading;
- retest Protection command execution/visibility against Paper 26.3 command behavior; and
- rerun the full security/deployment matrix because the network/platform boundary changed.

## Port discipline

Prefer deleting obsolete 26.2 workarounds to carrying compatibility branches. Do not keep dual 26.2/26.3 code merely because it is convenient unless a separately documented product requirement justifies multi-version support.

Do not change public configuration schema, policy semantics, key formats, or protocol version merely because the platform version changed. Any such change needs its own concrete requirement and migration/security review.

Do not reintroduce `guardian.cerberus.dev.*` fault-injection switches into the public Cerberus runtime. If Phase 8 needs live fault injection for port verification, keep it in test-only fixtures or a deliberately non-release test build/branch and remove it before release packaging.
