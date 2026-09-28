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

### BRIDGE-003 — Phase 0B proxy assertion secret provisioning

**Source:**

- `GUARDIAN_PHASE0B_PROXY_SECRET` use in Guardian-Velocity and Guardian-Paper
- current HMAC proxy-admission assertion bootstrap

**Why it exists:** Phase 0B proved authenticated Velocity → Paper admission assertions using a server-controlled shared secret. The cryptographic trust boundary is valid, but the environment-variable name and provisioning UX are feasibility-era scaffolding.

**Owner:** Phase 5, with Phase 7 documentation/release UX follow-through.

**Retirement condition:** Proxy assertion secret/key provisioning, validation, rotation expectations, diagnostics, and deployment documentation use production Guardian configuration/naming. No production path depends on the `PHASE0B` environment-variable contract unless the authoritative plan explicitly promotes it.

### BRIDGE-004 — Guardian-Velocity retained feasibility adapter

**Source:**

- `guardian-velocity/.../GuardianVelocityPlugin.java`
- Phase 0B-labelled logs/Javadocs and fixed `HANDSHAKE_TIMEOUT_SECONDS`
- feasibility-era Velocity operational/configuration hosting retained around the now-shared Phase 3 policy engine

**Why it exists:** The Velocity CONFIGURATION lifecycle, channel-consumption boundary, session reuse, Bedrock classification, and proxy assertion flow were live-proven in Phase 0B and intentionally retained during Phase 1A. The adapter has not yet received its full production configuration/diagnostics pass. Phase 3 additionally owns removal of the feasibility-era **independent policy behavior** so Guardian-Velocity consumes the same platform-neutral policy schema, immutable snapshot, profile-resolution contract, and evaluator as standalone Guardian-Paper. Phase 5 must productionize the Velocity host rather than port or reimplement Phase 3 policy.

**Owners:** Phase 3 for shared-policy consumption; Phase 5 for the remaining Velocity production adapter/operations work.

**Phase 3 checkpoint:** **CLOSED in `0.1.0-phase3` on 2026-09-27.** Guardian-Velocity no longer makes admission decisions from its own hard-coded client-class switch/branching. It loads the portable shared `admission/policy.yml`, resolves the shared profile model, and invokes the canonical Phase 3 evaluator used by standalone Paper. The parser/validator/snapshot path lives in `guardian-core` and has no Bukkit/Paper or Velocity configuration dependency. The final Java 25 / Gradle 9.7.1 gate is green at 134 tests, and live parity verification includes ordinary allow, explicit deny, required-version mismatch, genuinely absent required mod, exact-hash denial, trusted backend assertion without re-attestation, and standalone Paper parity.

**Final retirement condition (Phase 5):** Velocity authority has production configuration ownership and file-location/UX, configurable operational timing where appropriate, final diagnostics/naming, finalized assertion provisioning, explicit deployment-mode diagnostics, and tests for the Phase 5 acceptance matrix. Phase 0B wording is removed from production logs/Javadocs. Phase 5 MUST NOT require a second Velocity-specific policy schema/evaluator or a port of Paper-owned policy logic.

## Resolved bridges

### BRIDGE-005 — Early Geyser/Floodgate integration behavior

**Resolution:** Phase 4 promoted the proven origin integration into explicit production semantics. Guardian-Velocity and standalone Guardian-Paper both query supported optional Geyser/Floodgate APIs before Java brand/Cerberus handling. Provider absence is distinct from provider query failure; positive supported API evidence wins and prevents a Cerberus challenge; contradictory explicit provider answers produce a prominent diagnostic; and a provider failure with no positive Bedrock evidence fails closed as an indeterminate origin instead of silently reclassifying the connection as Java. Shared `clients.bedrock` policy remains configurable through the Phase 3 evaluator. Guardian-Paper's Velocity-mode Floodgate comparison is retained as diagnostic defense-in-depth only and never becomes a second policy authority.

**Resolved in:** Phase 4 final closeout, 2026-09-28.

**Regression ownership:** Phase 5 must preserve these semantics while productionizing Guardian-Velocity operational configuration/logging/commands. Phase 8 should retest current Geyser/Floodgate behavior during the Minecraft 26.3 port.

### BRIDGE-001 — Phase 0 response validator and test-manifest evaluator

**Resolution:** Phase 2 replaced `Phase0ResponseValidator`, `Phase0ManifestEvaluator`, and the synthetic deny-mod entry with protocol-v1 negotiation, canonical manifest structural validation, nonce binding, and real Fabric Loader manifest input. `MANIFEST_DENIED` remains reserved for the Phase 3 policy engine rather than being synthesized in Phase 2.

**Resolved in:** Phase 2 implementation candidate, 2026-09-26.

### BRIDGE-002 — Cerberus Phase 0 manifest and JVM diagnostic switches

**Resolution:** Cerberus now enumerates Loader-known mods, preserves containment relationships, reports bounded environment/release metadata and privacy-safe origin kinds, and speaks protocol v1. Feasibility-era `cerberus.phase0a.*` switches and Fabric metadata were removed; retained diagnostics use the explicit `guardian.cerberus.dev.*` namespace.

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

As of 2026-09-26, the Phase 2 implementation and live protocol-v1 verification are complete. BRIDGE-001 and BRIDGE-002 are retired by the real Loader-backed canonical manifest, production protocol-v1 negotiation/validation, and renamed `guardian.cerberus.dev.*` diagnostics. The closeout hardening pass introduces no new implementation bridge.

At the Phase 2 closeout point, BRIDGE-003, BRIDGE-004, and BRIDGE-005 remained later-phase work. The retained Velocity adapter's Phase 0B-labelled diagnostics and OptiFine denial were correctly treated as bridge behavior rather than Phase 2 protocol failures. The subsequent Phase 3 portability revision below now deliberately assigns BRIDGE-004's **independent policy-behavior** portion to Phase 3 while leaving the genuinely Velocity-specific production work in Phase 5.

The Java 25 / Gradle 9.7.1 Phase 2 closeout gate passed at project version `0.1.0-phase2`.

## Phase 2.5 bridge review

Phase 2.5 exact artifact identity and approved-artifact catalog work introduces no new implementation bridge. The artifact digest is part of the unreleased protocol-v1 canonical contract and the catalog is a permanent administrator identity-data surface, not temporary compatibility scaffolding.

BRIDGE-003 and BRIDGE-005 retain their existing Phase 5 / Phase 4 owners. BRIDGE-004 remains active, but its ownership is now deliberately split: Phase 3 removes the feasibility-era independent Velocity policy behavior by wiring Guardian-Velocity to the shared policy engine; Phase 5 retains final Velocity configuration ownership/UX, timings, diagnostics/naming, deployment-mode behavior, and assertion-secret productionization. Phase 2.5 itself did not rewrite any of those surfaces.

## Phase 3 implementation-candidate bridge review

The `0.1.0-phase3` implementation candidate introduced no new bridge. Phase 4 subsequently retires BRIDGE-005; BRIDGE-003 and BRIDGE-004 remain active for Phase 5.

BRIDGE-004's Phase 3 checkpoint is implemented in source: both authoritative adapters now consume the same platform-neutral policy parser/snapshot/profile-resolution/evaluator system, and Guardian-Velocity's independent feasibility-era admission policy branch is removed. BRIDGE-004 remains active solely because its final retirement condition is Phase 5 operational productionization: final Velocity data/config ownership UX, configurable timing where appropriate, assertion provisioning, deployment diagnostics, and removal of Phase 0B naming/logging.

Phase 3 subsequently closed with the Java 25 / Gradle 9.7.1 gate green at 134 tests and the focused live policy matrix complete; the Phase 3 checkpoint above is therefore operator-verified.


## Phase 4 closeout bridge review

As of 2026-09-28, **Phase 4 is complete**. The retained Java 25 / Gradle 9.7.1 reports are green at 141 tests with zero failures/errors/skips, and the focused live matrix confirms Velocity Bedrock, Velocity Java/Fabric regression, standalone Bedrock allow, and standalone Bedrock policy denial.

BRIDGE-005 is retired. The only active implementation bridges entering Phase 5 are BRIDGE-003 and BRIDGE-004. Phase 5 must preserve the Phase 4 Bedrock evidence/failure semantics while finalizing Velocity hosting, assertion provisioning, diagnostics, operations, and authority-aware administrator surfaces.
