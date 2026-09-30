# Guardian / Cerberus provenance record

This file records the source/provenance boundary used for the Phase 1A implementation prepared on 2026-09-24.

## Guardian / BrandBlocker lineage

Guardian is a hard fork and substantial rewrite of BrandBlocker by Menacho, with subsequent BadWolfMC development. The Phase 1A review used the supplied legacy reference archive:

- archive: `BrandBlocker(4).zip`
- SHA-256: `624184728aa379f6f9c895679e41df6dc260051fc8615c9c146ce09d579b8907`
- declared project version: `1.9.4-26.2`
- legacy plugin authors in `plugin.yml`: Menacho, mercurialmusic
- license lineage: GPLv3

BrandBlocker is behavioral/provenance reference material. Guardian does not preserve its delayed `PlayerJoinEvent` architecture, substring-based brand matching, console-command kick path, ignored `enable` setting, or username-prefix trust model.

## Guardian Protection / eZProtector lineage

The Phase 1A/1B handoff review uses two BadWolfMC eZProtector reference archives:

- current Paper reference: `ezProtector(3).zip`
  - SHA-256: `4e8d2f04ef33384cc1abe5dc2adf68c6726c43cd02b0644c34807f105643f3df`
  - this is byte-identical by SHA-256 to the previously reviewed `ezProtector(2).zip`
  - declared Paper project version: `1.4.0-26.2`
  - repository referenced by the supplied README: `BadWolfMC/eZProtector`
  - license declared by the supplied README: GPLv3
- historical proxy reference: `ezProtector-with-old-velocity-module(2).zip`
  - SHA-256: `cfd01fab6d2ef7c14c2adb7eb74bcb8b988f0cd54a3e0b70f7a0c66fd67a4624`
  - contains the older Velocity and Waterfall modules in addition to the Paper module
  - historical behavior/reference only; the authoritative Guardian contract explicitly excludes carrying legacy proxy Protection techniques into Phase 1B

Neither supplied archive contains Git metadata, so an exact BadWolfMC source commit SHA cannot be established from the archives themselves and is deliberately **not invented here**. Phase 1A incorporates no eZProtector command-protection implementation code. Phase 1B may use the identified GPLv3 BadWolfMC lineage for behavioral/provenance reference while implementing the approved Guardian Protection scope against supported current APIs.

Later AGPL eZProtector continuation code remains outside Guardian's provenance boundary unless licensing is explicitly reconsidered first.

## Phase 1A source archive

The implementation patch is based on the exact supplied Guardian archive:

- archive: `Guardian(20260924-084631).zip`
- SHA-256: `2285162a29a76007ead834c4cca9c2baa7e1e5b1834cd2ec27457eb92f262aea`

The final Phase 1A patch is verified with `git apply --check` against a fresh extraction of this archive before delivery.

## Phase 1A closeout baseline

The repository accepted for Phase 1A closeout and Phase 1B handoff is:

- archive: `Guardian(20260925-012627).zip`
- SHA-256: `5b128bfab97e1fd05a03132132b55c6ee694f1377a4c7bab1b7652a95b72180e`
- project version: `0.1.0-phase1a`
- archived automated results: 49 tests, 0 failures/errors/skips
- operator verification: startup recovery, schema fail-closed behavior, independent Admission/Protection activation, standalone Admission regression, and focused Velocity/Geyser/Floodgate regression all PASS

This is the authoritative implementation baseline for Phase 1B unless a later supplied repository explicitly supersedes it.

## Phase 1B implementation source supplied 2026-09-24

This Phase 1B implementation pass is based on the exact repository archive supplied for the pass:

- archive: `Guardian(20260925-014418).zip`
- SHA-256: `08db5a91d42b59a9d7605d670458a10c13904c4052ab82fb84b81cbb7eab5b15`
- starting project version: `0.1.0-phase1a`
- repository state represented by the archive: Phase 1A complete/live-verified per the included handoff and verification records

The exact eZProtector references supplied to this pass are:

- `ezProtector(4).zip` — SHA-256 `4e8d2f04ef33384cc1abe5dc2adf68c6726c43cd02b0644c34807f105643f3df`; byte-identical to the previously recorded current Paper reference hash
- `ezProtector-with-old-velocity-module(3).zip` — SHA-256 `cfd01fab6d2ef7c14c2adb7eb74bcb8b988f0cd54a3e0b70f7a0c66fd67a4624`; byte-identical to the previously recorded historical proxy reference hash

Phase 1B uses those archives for behavior/provenance review only. The Guardian Protection implementation is written against Guardian's domain boundaries and supported Paper APIs; no later AGPL continuation source is consulted or incorporated.


## Phase 1B closeout baseline — 2026-09-26

The repository accepted for Phase 1B closeout and Phase 2 handoff is:

- archive: `Guardian.zip`
- SHA-256: `caf98f98f1601939222b48ce497b6f0139755f1bec5b048f70d654299c6586e2`
- project version: `0.1.0-phase1b`
- repository test inventory: 73 tests
- operator-reported automated result: clean Java 25 / Gradle 9.7.1 gate PASS; all 73 tests PASS
- operator live result: full Phase 1B verification matrix PASS from a blank-slate Guardian installation

The closeout archive does not retain the terminal build log; the automated result above is therefore recorded as operator-confirmed rather than independently reconstructed from an archived report. The source tree and live verification establish Phase 1B as complete for project handoff.

This is the authoritative implementation baseline for Phase 2 unless a later supplied repository explicitly supersedes it.

## Phase 2 implementation candidate — 2026-09-26

Phase 2 replaces the Phase 0 synthetic Cerberus manifest and response validator with Guardian protocol v1 and a Fabric Loader-backed canonical manifest. Enumeration uses supported Fabric Loader APIs (`FabricLoader.getAllMods`, `ModContainer.getContainingMod`, origin metadata) and deliberately serializes no filesystem paths. The standalone Paper hybrid and Velocity-authoritative transport boundaries are retained.

BRIDGE-001 and BRIDGE-002 are retired in source by this candidate. Final Phase 2 closeout still requires the Java 25 / Gradle 9.7.1 clean gate plus live standalone Paper and Velocity-authoritative verification with representative real Fabric manifests.

## Phase 2 closeout hardening source — 2026-09-26

The final Phase 2 hardening/closeout pass is based on the exact repository archive supplied after successful live protocol-v1 testing:

- archive: `Guardian(4).zip`
- SHA-256: `3be945537ba459d6f1169305ac87760317d6e5704195f607ee4a129d3212430d`
- starting project version: `0.1.0-phase1b`
- supplied archived automated result: 73 tests, 0 failures/errors/skips
- operator result before this closeout patch: Java 25 / Gradle 9.7.1 build PASS
- operator live result: standalone Paper and Velocity-authoritative protocol-v1 matrices PASS with a representative 166-entry real Fabric manifest

The closeout hardening patch changes the project version to `0.1.0-phase2`, tightens Fabric mod-ID and unsigned-16-bit protocol-version representation bounds, expands adversarial tests to 85 total, and records the Phase 2 verification/Phase 3 handoff state. The supplied `Guardian(5).zip` establishes that this Phase 2 baseline subsequently passed the clean Java 25 / Gradle 9.7.1 gate with all 85 tests green.

## Phase 2.5 artifact-identity source — 2026-09-26

Phase 2.5 implementation is based on the exact repository archive supplied after Phase 2 closeout:

- archive: `Guardian(5).zip`
- SHA-256: `958b35fb7dbfeb06dd626a12f926efb33b8d9e8a7fe56f899ca72578ede5d48b`
- starting project version: `0.1.0-phase2`
- supplied Phase 2 automated baseline: 85 tests passing under Java 25 / Gradle 9.7.1
- supplied Phase 2 live baseline: standalone Paper and Velocity-authoritative protocol-v1 verification complete

The Phase 2.5 candidate revises unreleased protocol v1 in place with required exact top-level archive SHA-256, adds the bounded Guardian-managed approved-artifact importer/catalog, advances the project version to `0.1.0-phase2.5`, and expands the source test inventory to 102 `@Test` cases. The final clean Java 25 / Gradle 9.7.1 gate for this exact patched source remains the operator confirmation required for Phase 2.5 closeout.

## Phase 3 policy-engine source — 2026-09-26

The Phase 3 implementation candidate is based on the exact repository archive supplied for this pass:

- archive: `Guardian(8).zip`
- SHA-256: `65fcbb7d5df00549e2af7c9722eea0c8371052521c8b4569492fdd0c4c7f066f`
- starting project version: `0.1.0-phase2.5`
- supplied Phase 2.5 baseline: Java 25 / Gradle 9.7.1 clean gate green at 102 tests and live standalone/Velocity exact-artifact verification complete

The Phase 3 candidate advances the project version to `0.1.0-phase3`, adds the platform-neutral shared Admission policy schema/parser/snapshot/evaluator, optional LuckPerms profile-provider adapters for Paper and Velocity, and replaces Guardian-Velocity's feasibility-era independent admission-policy branch with the shared engine. The source test inventory is 131 `@Test` cases before the operator closeout gate.

Phase 3 introduces SnakeYAML Engine 2.10 as the platform-neutral runtime YAML parser for shared Admission policy. It is an external dependency obtained through Maven Central; no SnakeYAML source is incorporated into Guardian. Paper/Velocity package the runtime library because both may host the shared parser. LuckPerms API 5.5 is compile-only/optional and no LuckPerms implementation code is bundled.

The implementation sandbox could not download the Gradle 9.7.1 distribution and exposed Java 21 rather than the project-required Java 25. Therefore provenance records this pass as an implementation candidate only; `PHASE_3_VERIFICATION.md` defines the clean Java 25 / Gradle 9.7.1 and live checks required for closeout.

## Phase 3 closeout-hardening source — 2026-09-27

The Phase 3 live-verification and closeout-hardening pass is based on the exact repository archive supplied after the initial policy-engine patch and loader-fixture correction:

- archive: `Guardian(10).zip`
- SHA-256: `27c94d694b8dd9eadebb3c2a26c05f1b937fedfd8fb34a35b2f9045cb4ded8d7`
- project version: `0.1.0-phase3`
- operator result before this hardening patch: Java 25 / Gradle 9.7.1 build returned green after the focused Phase 3 loader-test fixture fix
- retained Gradle XML reports in the supplied archive: 131 tests, 0 failures, 0 errors, 0 skipped (Core 52, Paper 30, Protection 20, Protocol 21, Velocity 8)
- operator live result: Velocity configured allow + trusted Paper assertion, explicit mod denial, required-mod version mismatch, exact-hash denial, and standalone Paper parity all behaved according to the shared Phase 3 evaluator

Additional operator-supplied verification inputs for this pass are:

- `rejected_mods.zip` — SHA-256 `9ed714ee29ceb86ebf013989fb0ee4f3fc4fda8fc515578a90bba55cedba3059`; contains four unmodified developer-distributed Fabric mod JARs used only to exercise scanner compatibility. These third-party binaries are not incorporated into or redistributed with Guardian.
- `velocity-policy.yml` — SHA-256 `7bf4929c1a3f80041aa1ced6b05f874bea829160d142be715569e026fd7a2f0c`; operator-created Phase 3 policy used as live configuration evidence only.

The real scanner fixtures showed that the original importer was stricter than the Fabric environment it was intended to inventory: three metadata files contain literal line breaks in human-readable JSON strings, and Replay Mod contains 7,882 archive entries. The closeout hardening aligns metadata compatibility with the current Fabric Loader behavior while retaining Guardian's explicit metadata/size/depth/filesystem bounds, raises the administrative archive-entry ceiling conservatively, and adds generated non-loaded copy/paste policy fragments. No supplied third-party mod JAR is added to repository source or test resources.

## Phase 3 final closeout repository — 2026-09-27

The final Phase 3 closeout evidence is based on the operator-supplied repository after the scanner/message hardening and artifact-test documentation correction:

- archive: `Guardian(20260927-120358).zip`
- SHA-256: `f4dfcf75d76a63ff458537fff80fdb2c418552d43ec51265e12b2d0cd1325f45`
- project version: `0.1.0-phase3`
- retained Gradle XML reports: **134 tests, 0 failures, 0 errors, 0 skipped** (Core 53, Paper 31, Protection 20, Protocol 21, Velocity 9)
- operator live result: genuinely absent required mod produced `MANIFEST_DENIED / REQUIRED_MOD_MISSING` at Guardian-Velocity, with actionable player-facing missing-mod text and configured help URL
- operator live result: the four previously rejected real-world Fabric JARs all import successfully and generate valid `artifact-import-rules.yml` exact-hash blocks; admission succeeds after those reviewed blocks are copied into policy

These results satisfy the remaining Phase 3 portability and closeout conditions. Phase 3 is complete. BRIDGE-004 remains active only for its Phase 5 Velocity operational-productionization scope; BRIDGE-005 remains owned by Phase 4.

## Phase 4 Geyser/Floodgate productionization source — 2026-09-27

The Phase 4 implementation pass is based on the exact repository archive supplied after Phase 3 closeout:

- archive: `Guardian(20260927-225119).zip`
- SHA-256: `0ccb246c8741cc89bd91ab8a0d7e854cd7b7dbe657eb9239fa10917ddbb33e73`
- starting project version: `0.1.0-phase3`
- supplied Phase 3 retained Gradle result: 134 tests, 0 failures, 0 errors, 0 skipped
- supplied Phase 3 live baseline: shared-policy Velocity/standalone verification and artifact-import verification complete

The Phase 4 candidate advances the project version to `0.1.0-phase4`, adds a platform-neutral Bedrock evidence/failure model, productionizes Guardian-Velocity provider failure/disagreement semantics, and adds supported optional Geyser/Floodgate origin discovery to standalone Guardian-Paper. It preserves Velocity as the sole policy authority in proxy mode and retains backend Floodgate only as a diagnostic sanity check.

The current Geyser/Floodgate integration dependencies remain compile-only optional APIs (`Geyser API 2.11.2-SNAPSHOT`, `Floodgate API 2.2.5-SNAPSHOT`). No Geyser/Floodgate implementation code or binaries are incorporated into Guardian.

The candidate also records the pre-Phase-5 operations/observability architecture in the authoritative plan and new Phase 5 handoff: authority-aware command routing, bounded active inspection snapshots, atomic reload/validation surfaces, `NORMAL`/`DEBUG` production logging, the `guardian.command.*` permission convention, and the artifact generated-rule-ID length hardening item.

The Phase 4 source test inventory is expected to be 141 tests before the final Java 25 / Gradle 9.7.1 closeout gate. The implementation sandbox exposed OpenJDK 21 only and did not have the Gradle 9.7.1 distribution cached; network retrieval of the wrapper distribution was unavailable. The required Java 25 gate therefore was not represented as executed here. `PHASE_4_VERIFICATION.md` records the exact operator gate and focused live checks required for closeout.


## Phase 4 final closeout repository — 2026-09-28

The final Phase 4 closeout evidence is based on the operator-supplied repository after the focused architecture-test correction and successful live Bedrock verification:

- archive: `Guardian(20260928-071227).zip`
- SHA-256: `2cfb2b5256a6f679d592938d2b1d0697b78b718b82dc9858a97c762c0a018be1`
- project version: `0.1.0-phase4`
- retained Gradle XML reports: **141 tests, 0 failures, 0 errors, 0 skipped** (Core 57, Paper 34, Protection 20, Protocol 21, Velocity 9)
- operator live result: Velocity Bedrock classified from agreeing Geyser/Floodgate evidence, admitted by shared Bedrock policy, asserted to Paper, and accepted through `PROXY_ADMISSION_VERIFIED` with agreeing backend Floodgate evidence
- operator live result: Velocity Java Fabric/Cerberus remained `JAVA_FABRIC`, verified the representative 166-entry manifest, and preserved assertion-only backend Admission
- operator live result: standalone Paper Bedrock `ALLOW` entered through `BEDROCK_POLICY` without Cerberus
- operator live result: standalone Paper Bedrock `DENY` was rejected through ordinary shared `policy.yml` with `CLIENT_DENIED`

The packaged Paper `config.yml` contains no Bedrock client-policy action and explicitly delegates shared Admission policy to `policy.yml`, so no stale shipping configuration ownership was found during closeout.

These results retire BRIDGE-005. Phase 5 begins with only BRIDGE-003 and BRIDGE-004 active.


## Phase 5 productionization source — 2026-09-28

The Phase 5 implementation candidate is based on the exact operator-supplied repository archive:

- archive: `Guardian(20260928-082622).zip`
- SHA-256: `33b6a16227d57d3da1f5bfefcbfd05839fb74bbe458ca78af9fc9f7335dde5d5`
- starting project version: `0.1.0-phase4`
- retained entering Gradle XML reports: **141 tests, 0 failures, 0 errors, 0 skipped** (Core 57, Paper 34, Protection 20, Protocol 21, Velocity 9)
- entering live baseline: Phase 4 Velocity/standalone Bedrock and Java/Fabric regressions complete; BRIDGE-005 retired

The Phase 5 candidate advances to `0.1.0-phase5` and implements distinct Paper `/guardian` versus Velocity `/guardianv` administration, proxy-local production configuration, production assertion-key provisioning, configurable timing, atomic Velocity reload/validation, bounded active inspection snapshots, `NORMAL`/`DEBUG` logging, authority-correct artifact administration, and deterministic long generated rule IDs. It preserves the shared Phase 3 policy engine and Phase 4 origin semantics rather than replacing them.

This implementation sandbox exposed OpenJDK 21 only. The repository wrapper attempted to retrieve Gradle 9.7.1 but outbound DNS/network access to `services.gradle.org` was unavailable, and no cached Gradle distribution was present. Therefore **no Phase 5 Java 25/Gradle result is claimed here**. The retained 141-test Phase 4 XML is entering evidence only. `PHASE_5_VERIFICATION.md` records the required local/CI gate and focused live checks before BRIDGE-003/004 retirement.

## Phase 5 final closeout repository — 2026-09-29

The final Phase 5 closeout evidence is based on the unchanged operator-supplied green repository after the build-fix, generated assertion-key provisioning redesign, and top-level `policy.yml` layout cleanup:

- archive: `Guardian(20260929-092057).zip`
- SHA-256: `7840744780fda1c45b945df28224dc187fce7082ac2a19cb592748b71cbaddeb`
- project version: `0.1.0-phase5`
- retained Gradle XML reports: **178 tests, 0 failures, 0 errors, 0 skipped** (Core 64, Paper 44, Protection 20, Protocol 21, Velocity 29)
- built artifacts present for Guardian-Paper, Guardian-Velocity, and Cerberus-Fabric at the Phase 5 version
- operator live result: normal Velocity Fabric/Cerberus Admission emitted one concise authoritative `NORMAL` summary and Paper remained silent on successful assertion verification
- operator live result: Paper `/guardian status` and backend `/guardian inspect` reported Velocity authority and trusted backend evidence without receiving the authoritative Fabric manifest
- operator live result: `/guardianv` commands executed successfully in-game after their `guardian.velocity.command.*` nodes were granted through the Velocity LuckPerms instance; the initial denial was an isolated-lab permission-store distinction rather than Guardian routing failure
- operator live result: host-local invalid validation/reload preserved prior runtime state, valid changes applied only to the intended host, and Paper/Velocity artifact authority remained correctly separated
- operator live result: Velocity-generated `proxy-assertion.key` provisioning, matching fingerprints, deliberate backend mismatch rejection, restoration/recovery, and trusted assertions passed
- operator live result: active inspection data was removed on disconnect and did not become historical manifest storage

These results satisfy BRIDGE-003 and BRIDGE-004 retirement conditions. Phase 5 is complete with no active implementation bridge entering Phase 6.

## Phase 6 adversarial-hardening candidate — 2026-09-30

The Phase 6 implementation candidate is reconstructed and continued against the operator-supplied Phase 5 repository baseline:

- archive: `Guardian(20260930-065421).zip`
- SHA-256: `190bccb805766981c16e4edf80308c2f395304352d4068e26642187e37c9c0fc`
- entering project version: `0.1.0-phase5`
- entering authoritative closeout evidence: Phase 5 green at 178 tests with BRIDGE-003/004 retired and no active implementation bridge

During stream-recovery work the operator also supplied a later cumulative in-progress Phase 6 patch that preserved work from the preceding conversation:

- patch: `guardian-phase6-checkpoint-through-server-authentication(2).patch`
- SHA-256: `c05ffef3860f7ceb2b89d6e6df3795c7f6b9418a6534dd41a3c0e6353c9330f8`

That checkpoint was compared against the reconstructed branch and accepted as the later coherent state before further hardening. The resulting candidate advances the source version to `0.1.0-phase6` and adds adversarial session/replay/protocol/provider/inspection/logging/filesystem hardening, optional signed official Cerberus release identity, and optional player-bound Guardian Ed25519 challenge authentication to Cerberus. The trust claims and residual limits are documented in `PHASE_6_IMPLEMENTATION.md` and `GUARDIAN_ADMISSION.md` rather than described as remote attestation.

The current candidate contains a **354-test source inventory** (Core 117, Paper 91, Protection 20, Protocol 50, Velocity 58, Cerberus 18). This is not represented as an executed Phase 6 gate. The implementation sandbox exposes OpenJDK 21 only and cannot retrieve the uncached Gradle 9.7.1 wrapper distribution. Focused pure-Java protocol compilation/smokes, `git diff --check`, and cumulative `git apply --check` against the exact baseline have been used during construction. The required Java 25 / Gradle 9.7.1 gate and focused platform-level live checks remain operator closeout requirements in `PHASE_6_VERIFICATION.md`.


## Phase 6 automated verification gate — 2026-09-30

The Phase 6 automated closeout evidence is based on the operator-supplied repository after the final build-fix slice:

- archive: `Guardian(20260930-103011).zip`
- SHA-256: `513098cae43363090fa3406593a7edfe7c9c55a1779ac0e0f4b01160eb0e2945`
- project version: `0.1.0-phase6`
- environment: Oracle JDK 25.0.3 / Gradle 9.7.1 / Windows 11
- retained Gradle XML reports: **354 tests, 0 failures, 0 errors, 11 skipped**
- module totals: Core 117/0/0/4, Paper 91/0/0/4, Protection 20/0/0/0, Protocol 50/0/0/0, Velocity 58/0/0/3, Cerberus 18/0/0/0 (tests/failures/errors/skipped)
- all 11 skips are JUnit-assumption skips for symlink-hardening cases where the Windows account lacks symbolic-link creation privilege; no unexplained product test is skipped
- built artifact SHA-256 values:
  - Guardian-Paper: `5c932b59ccfe92e32f3e65ffafe647a9761e2943b645b87d81c73aee819a1863`
  - Guardian-Velocity: `2c3b471a71dad738eaebd401ce27a31b9b264eb15714d0aff12d869268de9df8`
  - Cerberus-Fabric: `50db3cb5a7fdfc09f57d664f547791175d5e1e451116317dc6f6e0340b38de12`
- built JAR inspection found no `proxy-assertion.key`, `guardian-server-auth.key`, PEM/private-key entry, or administrator-local private key material

This satisfies the Phase 6 automated Java/Gradle gate. Phase 6 is **not yet closed**: the intentionally small signed-release/server-authentication/quarantine/Velocity live matrix and final post-live review remain required.
