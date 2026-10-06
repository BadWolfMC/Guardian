# Implementation bridge register

This register tracks code that is intentionally present because it bridges a proven earlier-phase implementation into the current architecture, but is **not** the final implementation intended for public release. It exists so temporary scaffolding cannot silently become permanent.

The authoritative project plan remains the source of truth for scope and architecture. This file is the operational companion for concrete implementation debt that already exists in source.

## Maintenance rule

Add or update an entry whenever a change introduces or discovers any of the following:

- feasibility/prototype code retained across a phase boundary;
- temporary configuration or secret provisioning;
- deliberately simplified policy or validation logic standing in for a later subsystem;
- hard-coded operational values that a later phase is expected to own;
- supported-API integration implemented early but not yet productionized under its scheduled phase; or
- a temporary compatibility/testing hook that must not be mistaken for public behavior.

Every active bridge must identify the source locations, the reason it exists, the phase that owns its replacement/review, and an objective retirement condition. Closing a bridge means either removing/replacing it or explicitly promoting it into the authoritative contract as final behavior. Do not simply delete entries because the code has become familiar.

## Active bridges

**None.** Phase 5 retired BRIDGE-003 and BRIDGE-004 after the final Java 25 / Gradle 9.7.1 gate and focused live operational matrix passed on 2026-09-29. New temporary implementation debt introduced in Phase 6 or later must be registered here before it can cross a phase boundary.

## Resolved bridges

### BRIDGE-003 — Proxy assertion key provisioning

**Resolution:** Guardian-Velocity owns production assertion-key creation. On first startup it creates `proxy-assertion.key` with 32 cryptographically random bytes encoded as Base64 using no-overwrite semantics; administrators copy that file unchanged to Velocity-authority Guardian-Paper backends. Standalone Paper requires no key. Both hosts perform bounded/symlink-safe key-file validation, fail closed on missing/mismatched material, and expose only a non-secret fingerprint. The feasibility-era environment-variable bootstrap is absent from production source.

**Resolved in:** Phase 5 final closeout, 2026-09-29.

**Live evidence:** generated-key provisioning, matching status fingerprints, deliberate backend key mismatch rejection, restoration/recovery, and normal trusted Velocity → Paper assertions all passed.

### BRIDGE-004 — Guardian-Velocity retained feasibility adapter

**Resolution:** Guardian-Velocity is now the production network Admission host: it consumes the shared Phase 3 policy engine, owns strict proxy-local configuration/data, configurable timing, generated assertion-key provisioning, locale/logging state, atomic reload/files-only validation, proxy-owned `/guardianv` administration, bounded authoritative inspection snapshots, network-authoritative artifact scanning, and final production diagnostics. Security-sensitive Guardian channels terminate at Velocity, mutable Admission sessions are discarded at terminal decisions, backend switching reuses one proxy-session Admission grant, and Paper remains assertion-only without receiving the full Fabric manifest. Feasibility-era Phase 0B operational naming is absent from production source.

**Resolved in:** Phase 5 final closeout, 2026-09-29.

**Live evidence:** the 178-test Java 25 / Gradle 9.7.1 gate passed together with normal Fabric/Cerberus Admission, authority-correct Paper/proxy inspection, host-local validation/reload, artifact authority, assertion provisioning, disconnect cleanup, and in-game `/guardianv` administration through the Velocity permission provider.

### BRIDGE-005 — Early Geyser/Floodgate integration behavior

**Resolution:** Phase 4 promoted the proven origin integration into explicit production semantics. Guardian-Velocity and standalone Guardian-Paper both query supported optional Geyser/Floodgate APIs before Java brand/Cerberus handling. Provider absence is distinct from provider query failure; positive supported API evidence wins and prevents a Cerberus challenge; contradictory explicit provider answers produce a prominent diagnostic; and a provider failure with no positive Bedrock evidence fails closed as an indeterminate origin instead of silently reclassifying the connection as Java. Shared `clients.bedrock` policy remains configurable through the Phase 3 evaluator. Guardian-Paper's Velocity-mode Floodgate comparison is retained as diagnostic defense-in-depth only and never becomes a second policy authority.

**Resolved in:** Phase 4 final closeout, 2026-09-28.

**Regression ownership:** Phase 5 preserved these semantics while productionizing Guardian-Velocity operational configuration/logging/commands. Phase 6 should adversarially test provider loss/reconfiguration, and Phase 8 should retest current Geyser/Floodgate behavior during the Minecraft 26.3 port.

### BRIDGE-001 — Phase 0 response validator and test-manifest evaluator

**Resolution:** Phase 2 replaced `Phase0ResponseValidator`, `Phase0ManifestEvaluator`, and the synthetic deny-mod entry with protocol-v1 negotiation, canonical manifest structural validation, nonce binding, and real Fabric Loader manifest input. `MANIFEST_DENIED` remains reserved for the Phase 3 policy engine rather than being synthesized in Phase 2.

**Resolved in:** Phase 2 implementation candidate, 2026-09-26.

### BRIDGE-002 — Cerberus Phase 0 manifest and JVM diagnostic switches

**Resolution:** Cerberus now enumerates Loader-known mods, preserves containment relationships, reports bounded environment/release metadata and privacy-safe origin kinds, and speaks protocol v1. Feasibility-era `cerberus.phase0a.*` switches and Fabric metadata were removed in Phase 2. The remaining `guardian.cerberus.dev.*` live-test switches used during later verification were removed during final `1.0.0` release preparation, so the public client has no runtime diagnostic fault-injection switches.

**Resolved in:** Phase 2 implementation candidate, 2026-09-26.


Resolved entries remain here as provenance for decisions that changed during implementation. They are no longer counted as active implementation debt.

### BRIDGE-006 — Initial-startup invalid-file recovery

**Resolution:** Phase 1A live testing showed that strict startup refusal can leave Guardian absent if an administrator misses the startup error. The authoritative contract now defines explicit recovery instead: malformed/structurally invalid initial config and locale files are preserved to UTC timestamped `.bak` files before safe packaged defaults/fallback behavior is used and fully revalidated. Unsupported schema versions are deliberately excluded from automatic recovery. Reload remains non-mutating and retains the prior valid snapshot.

**Resolved in:** Phase 1A closeout, 2026-09-24.

**Regression ownership:** Phase 7 should retain this behavior in release/upgrade tests, but there is no remaining implementation bridge.

## Phase 1A closeout state

As of 2026-09-24, **Phase 1A is complete**.

- **Clean Java 25 Gradle build/tests:** PASS — supplied closeout repository contains 49 tests with 0 failures/errors/skips; operator reports the clean build remains green.
- **Patch/application integrity:** PASS.
- **Source hygiene:** PASS; the previously reported unused import was ordinary cleanup rather than an implementation bridge.
- **Local Paper/configuration safety:** PASS — first-start generation, malformed config backup/recovery, invalid fallback-locale backup/recovery, and unsupported-schema fail-closed behavior were operator-confirmed.
- **Independent domain activation:** PASS — Admission/Protection on/off combinations were exercised.
- **Focused standalone Admission regression:** PASS.
- **Focused Velocity/Geyser/Floodgate regression:** PASS.

The active bridge register was reviewed at closeout. BRIDGE-001 through BRIDGE-005 remain intentionally active and retain their existing later-phase owners and retirement conditions. No Phase 1A completion result promotes those bridges into final public behavior.

Phase 1B may proceed from this baseline. Any temporary Protection implementation introduced during Phase 1B must be added to this register with an owner and retirement condition rather than left as an implicit TODO.


## Phase 1B bridge review

The Phase 1B Guardian Protection implementation introduces **no new temporary implementation bridge**. The player-command execution, command-tree visibility, downstream-suggestion suppression, bypass-resolution, notification, configuration, and tree-refresh paths are intended production architecture for the Phase 1B scope.

The package-private Guardian-Paper atomic reload/reconciliation method is a lifecycle seam for the later supported administrative surface, not a raw/replacement reload mechanism and not a temporary compatibility path. The public `/guardian reload` command itself remains deliberately roadmap-owned by the operations phase. BRIDGE-001 through BRIDGE-005 remain unchanged and are outside Phase 1B ownership.


## Phase 1B closeout state

As of 2026-09-26, **Phase 1B is complete**. The active bridge register was reviewed at closeout. Guardian Protection introduced no temporary bridge and its player-command execution, command visibility/suggestion, namespace, bypass, notification, configuration, and command-tree refresh paths are intended production architecture for the Phase 1B scope.

BRIDGE-001 through BRIDGE-005 remain intentionally active under their existing later-phase owners. In particular, Phase 2 now owns the retirement of BRIDGE-001 and BRIDGE-002 as the feasibility manifest/validator and Cerberus Phase 0 client behavior are replaced by the stable protocol-v1 implementation.

## Phase 2 closeout bridge review

As of 2026-09-26, the Phase 2 implementation and live protocol-v1 verification are complete. BRIDGE-001 and BRIDGE-002 were retired by the real Loader-backed canonical manifest and production protocol-v1 negotiation/validation. The Phase 2 verification diagnostics were later removed from public Cerberus source during final `1.0.0` release preparation. The closeout hardening pass introduces no new implementation bridge.

At the Phase 2 closeout point, BRIDGE-003, BRIDGE-004, and BRIDGE-005 remained later-phase work. The retained Velocity adapter's Phase 0B-labelled diagnostics and OptiFine denial were correctly treated as bridge behavior rather than Phase 2 protocol failures. The subsequent Phase 3 portability revision below now deliberately assigns BRIDGE-004's **independent policy-behavior** portion to Phase 3 while leaving the genuinely Velocity-specific production work in Phase 5.

The Java 25 / Gradle 9.7.1 Phase 2 closeout gate passed at project version `0.1.0-phase2`.

## Phase 2.5 bridge review

Phase 2.5 exact artifact identity and approved-artifact catalog work introduces no new implementation bridge. The artifact digest is part of the unreleased protocol-v1 canonical contract and the catalog is a permanent administrator identity-data surface, not temporary compatibility scaffolding.

BRIDGE-003 and BRIDGE-005 retain their existing Phase 5 / Phase 4 owners. BRIDGE-004 remains active, but its ownership is now deliberately split: Phase 3 removes the feasibility-era independent Velocity policy behavior by wiring Guardian-Velocity to the shared policy engine; Phase 5 retains final Velocity configuration ownership/UX, timings, diagnostics/naming, deployment-mode behavior, and assertion-key productionization. Phase 2.5 itself did not rewrite any of those surfaces.

## Phase 3 implementation-candidate bridge review

The `0.1.0-phase3` implementation candidate introduced no new bridge. Phase 4 subsequently retires BRIDGE-005; BRIDGE-003 and BRIDGE-004 remain active for Phase 5.

BRIDGE-004's Phase 3 checkpoint is implemented in source: both authoritative adapters now consume the same platform-neutral policy parser/snapshot/profile-resolution/evaluator system, and Guardian-Velocity's independent feasibility-era admission policy branch is removed. BRIDGE-004 remains active solely because its final retirement condition is Phase 5 operational productionization: final Velocity data/config ownership UX, configurable timing where appropriate, assertion provisioning, deployment diagnostics, and removal of Phase 0B naming/logging.

Phase 3 subsequently closed with the Java 25 / Gradle 9.7.1 gate green at 134 tests and the focused live policy matrix complete; the Phase 3 checkpoint above is therefore operator-verified.


## Phase 4 closeout bridge review

As of 2026-09-28, **Phase 4 is complete**. The retained Java 25 / Gradle 9.7.1 reports are green at 141 tests with zero failures/errors/skips, and the focused live matrix confirms Velocity Bedrock, Velocity Java/Fabric regression, standalone Bedrock allow, and standalone Bedrock policy denial.

BRIDGE-005 is retired. The only active implementation bridges entering Phase 5 are BRIDGE-003 and BRIDGE-004. Phase 5 must preserve the Phase 4 Bedrock evidence/failure semantics while finalizing Velocity hosting, assertion provisioning, diagnostics, operations, and authority-aware administrator surfaces.


## Phase 5 closeout bridge review

As of 2026-09-29, **Phase 5 is complete**. The final Java 25 / Gradle 9.7.1 gate is green at 178 tests with zero failures/errors/skips, and the focused live matrix passed. BRIDGE-003 and BRIDGE-004 are retired under their objective conditions above. Phase 5 introduced no replacement bridge, so the active register is empty entering Phase 6.

## Phase 7 implementation-candidate bridge review

The Phase 7 operations/release-hardening candidate introduces **no new implementation bridge**. The schema-1 → schema-2 migration is a deliberate first-public-release compatibility contract rather than temporary scaffolding; the release-manager helper and CI/release workflows are intended permanent operational surfaces. Phase 8 remains a separately gated Minecraft 26.3 port and must not absorb unfinished Phase 7 verification work.
