# Phase 8 handoff — Minecraft / Paper / Fabric 26.3 port

**Do not start this handoff until Phase 7 verification is formally closed.**


## Phase 7 final-closeout note

The Phase 7 live/release matrix has passed. Before starting this handoff, verify the final closeout patch by rerunning the Java 25 / Gradle 9.7.1 gate and confirming `/guardianv status` reports the Gradle-supplied version rather than the stale Phase 6 literal. Also retain the final regenerated artifact hashes. No additional Phase 7 adversarial connection matrix is required if those checks are green.

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

1. Phase 7 Java 25 / Gradle 9.7.1 gate is green.
2. A public/release-candidate artifact set and checksums exist.
3. Clean install, supported schema upgrade, signing helper, and focused Velocity/standalone checks are closed.
4. Paper 26.3 is sufficiently stable to target deliberately.
5. Any 26.2-only workaround is identified explicitly before removal.

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
