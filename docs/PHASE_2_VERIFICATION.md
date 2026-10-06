# Phase 2 verification — Cerberus and Guardian protocol v1

> **Historical verification note:** the `guardian.cerberus.dev.*` fault-injection/manifest switches referenced below existed only to manufacture Phase 2/6 live-test cases. They were removed from the public Cerberus source during final `1.0.0` release preparation.

## Current gate status — 2026-09-26

**Live protocol/transport matrix: PASS.**

**Final closeout automated gate: PASS.** The Phase 2 closeout baseline reached project version `0.1.0-phase2` with all 85 tests green under Java 25 / Gradle 9.7.1. No additional Phase 2 live-client permutations are required.

## 1. Final clean automated gate — PASS

The accepted local verification command was:

```powershell
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Accepted result:

- 85 tests pass with no failures/errors/skips;
- `guardian-paper`, `guardian-velocity`, and `cerberus-fabric` build successfully at project version `0.1.0-phase2`;
- protocol-v1 boundary/adversarial tests remain green;
- Guardian Protection regression tests remain green; and
- no Phase 3 policy behavior is introduced.

## 2. Standalone Paper live matrix — PASS

Observed on Paper 26.2 / Fabric 26.2:

| Client | Result | Notes |
|---|---|---|
| Fabric API + compatible Cerberus | PASS — `CERBERUS_VERIFIED` | CONFIGURATION presence accepted; bounded PLAY quarantine; real 166-entry canonical manifest validated; quarantine released. |
| Fabric API without Cerberus | PASS — `CERBERUS_REQUIRED` | Denied before world entry with distinct missing-Cerberus message. |
| Fabric API + Cerberus protocol 99 | PASS — `CERBERUS_PROTOCOL_UNSUPPORTED` | Recognized Cerberus incompatibility; no generic timeout. |
| Vanilla | PASS — `VANILLA_POLICY` | Allowed normally. |
| OptiFine | PASS — `OPTIFINE_POLICY` | Allowed normally by current standalone policy. |

The CONFIGURATION presence interoperability fallback remained functional: Cerberus attempted presence even when Paper did not advertise the presence channel, and Paper received the payload. The reverse challenge remained on the proven PLAY fallback rather than introducing unsupported CONFIGURATION transport assumptions.

## 3. Velocity-authoritative live matrix — PASS

Observed with Guardian-Velocity + Guardian-Paper:

| Client / condition | Result | Notes |
|---|---|---|
| Compatible Fabric/Cerberus | PASS — `CERBERUS_VERIFIED` | 166-entry manifest validated in Velocity CONFIGURATION; trusted admission asserted to Paper; Paper admitted via `PROXY_ADMISSION_VERIFIED` without duplicate attestation. |
| Fabric without Cerberus | PASS — `CERBERUS_REQUIRED` | Bounded 10-second CONFIGURATION wait then distinct missing-Cerberus denial. |
| Cerberus protocol 99 | PASS — `CERBERUS_PROTOCOL_UNSUPPORTED` | Compatibility-specific denial. |
| Cerberus with `guardian.cerberus.dev.suppressResponse=true` | PASS — `CERBERUS_TIMEOUT` | Presence was known and challenge sent; timeout remained distinct from missing Cerberus. |
| Cerberus with `guardian.cerberus.dev.malformedResponse=true` | PASS — `MANIFEST_INVALID` | Truncated payload rejected immediately; no crash and no timeout fall-through. |

Velocity + OptiFine currently produces `CLIENT_DENIED` in the retained Phase 0B feasibility adapter. At Phase 2 closeout this was correctly treated as BRIDGE-004 behavior rather than a transport defect. The later Phase 3 portability-contract revision splits BRIDGE-004 ownership: Phase 3 replaces the independent feasibility policy branch with the shared policy engine, while Phase 5 retains final Velocity production configuration/operations and diagnostics.

## 4. Real manifest characterization — PASS

The representative client produced 166 canonical entries. Observed structure included:

- top-level ordinary archive mods;
- built-in `minecraft` and `java` entries;
- `fabricloader`;
- top-level Fabric API plus nested API modules;
- C2ME plus nested modules and bundled libraries;
- nested libraries under other top-level mods; and
- multi-level containment.

The complete protocol-v1 response for this manifest is approximately 8,032 bytes, comfortably below Guardian's 65,536-byte cap. Current Paper 26.2 exposes a 1,048,576-byte plugin-message ceiling, so Guardian's application-level cap is the tighter boundary.

The sanitized manifest log contained no filesystem paths, usernames from paths, launch arguments, hardware data, IP data, or arbitrary file contents.

## 5. Manual testing intentionally not required

The following cases are covered more safely and repeatably by the automated suite rather than additional client/JVM manipulation:

- exact field/count maximums;
- payload over-limit encoding/decoding;
- truncated wire data;
- malformed UTF-8;
- duplicate mod IDs;
- missing parents;
- self-parenting/cycles/excessive containment depth;
- non-canonical ordering;
- nonce mismatch;
- capability mismatch;
- duplicate response state; and
- realistic large synthetic manifests.

A dedicated development-directory/Loom-origin live run is also not required for Phase 2 closeout. Phase 2 defines deterministic coarse origin behavior without transmitting paths; broader adversarial development-origin behavior remains appropriate for the later hardening phase.

## 6. Bridge review

- BRIDGE-001 — retired by protocol-v1 response validation and real canonical manifests.
- BRIDGE-002 — retired by Loader-backed manifests and the verification diagnostics available at that historical checkpoint; those runtime diagnostics were removed before the public `1.0.0` release.
- BRIDGE-003 — unchanged; Phase 5 owns production proxy-secret provisioning.
- BRIDGE-004 — active. Historical Phase 2 scope did not change it; the later Phase 3 portability revision assigns shared-policy consumption to Phase 3 and leaves final Velocity production configuration/operations plus Phase 0B-labelled diagnostic cleanup to Phase 5.
- BRIDGE-005 — unchanged; Phase 4 owns final Geyser/Floodgate production behavior.

No new Phase 2 bridge is introduced by the closeout hardening pass.

## Completion record

Phase 2 is complete at `0.1.0-phase2` with the Java 25 / Gradle 9.7.1 gate and live matrix green. Phase 2.5 intentionally revises the unreleased protocol-v1 artifact-identity shape before Phase 3 begins.

## Phase 2.5 historical note

This document remains the Phase 2 transport/manifest closeout record. Phase 2.5 later revised the unreleased protocol-v1 manifest shape in place to add required top-level archive SHA-256. Current protocol verification therefore continues in `PHASE_2_5_VERIFICATION.md`; no compatibility promise exists for the earlier internal Phase 2 v1 bytes.
