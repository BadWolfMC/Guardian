# Phase 3 verification — Guardian Admission policy engine

## Current status — complete

Project version: `0.1.0-phase3`

Source test inventory after the scanner/message closeout hardening: **134 `@Test` cases** (Phase 2.5 baseline: 102; initial Phase 3 policy-engine candidate: 131).

The operator completed the final Java 25 / Gradle 9.7.1 gate against the closeout-hardening source. The supplied closeout repository retains Gradle XML reports showing **134 tests, 0 failures, 0 errors, 0 skipped** across Guardian Core (53), Paper (31), Protection (20), Protocol (21), and Velocity (9).

The final live portability case also passed: a deliberately absent required mod produced `MANIFEST_DENIED / REQUIRED_MOD_MISSING` at Guardian-Velocity, the player-facing disconnect named `phase3_missing_test` and included the configured help URL, and Guardian-Paper remained assertion-only. The artifact importer was then re-verified with the four previously rejected real-world JARs; all scanned successfully and produced valid copy/paste exact-hash rules.

**Phase 3 is therefore complete at `0.1.0-phase3`.**

## 1. Required clean automated gate

From the repository root on Windows with Java 25 active:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Required result:

- JVM reports Java 25;
- repository wrapper reports Gradle 9.7.1;
- all tests pass with zero failures/errors;
- Guardian Paper and Velocity JARs build;
- Cerberus Fabric builds; and
- produced Guardian/Cerberus artifacts report `0.1.0-phase3` where project version metadata applies.

## 2. Focused automated coverage added

Phase 3 adds coverage for:

- every client class under shared policy, including Fabric `REQUIRE_CERBERUS`;
- unknown-brand allowlist and denylist behavior;
- unknown-brand rules not changing positively identified Fabric;
- client bypass scope and inability to bypass `REQUIRE_CERBERUS`;
- allowlist membership, required/baseline membership, built-ins, nested and multi-level containment;
- an otherwise nested ID becoming independently policy-addressable when installed top-level;
- denylist unlisted behavior plus explicit deny;
- required-mod presence and required-mod version predicates;
- version-only artifact rules;
- exact-hash artifact rules;
- multiple accepted hashes for one ID/version;
- multiple accepted versions for one ID;
- known version with wrong/unknown hash;
- `DIRECTORY` and `MIXED_OR_UNKNOWN` handling;
- mod bypasses;
- bypass inability to override protocol nonce/integrity failure;
- exact UUID override → highest-priority provider profile → default profile precedence;
- strict policy parsing, catalog references, and the rule that catalog membership is not implicit permission;
- contradictory required+deny rejection;
- profile-priority tie rejection;
- malformed version/artifact declarations and duplicate YAML keys;
- atomic reload retention and non-activating files-only validation;
- Paper shared-evaluator/Velocity-assertion-only architecture; and
- Velocity shared-evaluator architecture with no retained independent client-class admission switch; and
- identical normalized profile/manifest inputs producing an identical shared `GuardianDecision` independent of adapter identity.

Existing Phase 1B Protection and Phase 2/2.5 protocol/session/artifact tests remain part of the full gate.

## 3. Supporting checks completed in the implementation sandbox

These checks are useful supporting evidence but do not replace Section 1:

- scanner subset compilation succeeds with the available Java compiler;
- all four externally supplied real Fabric JARs rejected by the previous scanner now import successfully without extracting or executing content;
- their scanner-produced SHA-256 values were independently recomputed from the exact JAR bytes and matched;
- BetterGrassify, Entity Model Features and Entity Texture Features exercise Fabric-compatible literal line breaks in human-readable metadata strings;
- Replay Mod exercises the raised archive-entry ceiling with 7,882 entries;
- source scan continues to find no Paper/Velocity imports in `guardian-core/.../policy`;
- source scan continues to find no Bukkit/Paper `YamlConfiguration` dependency in the shared policy parser;
- source scan finds no hard-coded BadWolfMC mod allowlist in Guardian main Java source; and
- generated artifact policy fragments use direct SHA-256 rules and are never loaded automatically.

The implementation sandbox exposes Java 21 rather than the project's required Java 25, so these checks do not replace the repository's authoritative Java 25 Gradle gate.

## 4. Portability/security review checklist

Before closeout, confirm from the built source/artifacts:

- `guardian-core` policy model/parser/evaluator compiles without Paper or Velocity dependencies;
- both adapters package the same shared `policy.yml` resource and SnakeYAML runtime dependency;
- standalone Paper loads/evaluates shared policy;
- Velocity loads/evaluates the same shared policy;
- Paper in Velocity authority mode does not re-evaluate the player's mod policy;
- policy changes are configuration/catalog-only and require no Guardian source rebuild;
- `artifacts.yml` entries grant nothing unless referenced by policy;
- direct `sha256` and `catalog: true` exact-artifact rules both work;
- policy bypasses occur only after protocol/session integrity validation;
- no absolute client filesystem path is encoded into policy decisions/protocol messages; and
- Guardian Protection tests remain green.

## 5. Focused live verification record

### A. Velocity default-policy fail-closed behavior — PASS

A representative Fabric/Cerberus client against the packaged default ALLOWLIST policy was denied before admission with `MANIFEST_DENIED / UNLISTED_MOD`. This confirms the default policy does not implicitly permit catalogued or merely installed top-level mods.

### B. Velocity configured ordinary allow + backend assertion — PASS

With all policy-addressable top-level mods allowed, the same client produced `ALLOW / CERBERUS_VERIFIED`. Guardian-Velocity created the trusted admission assertion and Guardian-Paper accepted `PROXY_ADMISSION_VERIFIED` without running a redundant backend Cerberus attestation. A direct `HASH_REQUIRED`/`sha256` rule was also exercised successfully in the allowed policy.

### C. Velocity explicit mod denial — PASS

An installed mod with an explicit `DENY` rule produced `MANIFEST_DENIED / EXPLICIT_MOD_DENY` and named the configured rule/mod in diagnostics. Closeout hardening removes the redundant secondary `UNLISTED_MOD` violation for the same explicitly denied top-level entry.

### D. Velocity required-mod version mismatch — PASS

A required `fabric-api` rule deliberately excluded the installed `0.160.0+26.2` version. Guardian produced `MANIFEST_DENIED / VERSION_NOT_ACCEPTED`. This proves required-mod version predicates, but is **not** the absent-mod case below.

### E. Velocity exact-artifact/hash denial — PASS

An installed top-level mod configured with an intentionally incorrect exact SHA-256 produced `MANIFEST_DENIED / ARTIFACT_NOT_ACCEPTED`. Restoring an accepted hash returned the client to the ordinary allow path.

### F. Standalone Paper parity — PASS

Standalone Guardian-Paper exercised the configured happy path plus explicit deny, required-version mismatch and wrong exact-hash cases. It preserved the established CONFIGURATION presence -> bounded PLAY challenge/quarantine transport while producing the same shared policy reason families as Velocity.

### G. Velocity genuinely absent required mod — PASS

A temporary required rule for absent mod ID `phase3_missing_test` produced proxy denial `MANIFEST_DENIED / REQUIRED_MOD_MISSING`. The player-facing disconnect named the missing mod and included the configured help URL. Guardian-Paper did not perform an independent backend admission-policy evaluation. The temporary rule was removed after testing.

No repeat of the Phase 2 malformed-packet/replay/nonce/oversize matrix was required; the final automated gate remained green.

## 6. Closeout condition

All Phase 3 closeout conditions are satisfied:

1. the final Java 25 / Gradle 9.7.1 gate is green at 134 tests;
2. live case G produced `REQUIRED_MOD_MISSING`;
3. Guardian Paper/Velocity continue to package and consume the shared policy runtime;
4. live denial rendering exposed the first actionable problem plus configured help URL;
5. no BRIDGE-004 independent-policy behavior remains; and
6. the closeout result is recorded in the authoritative plan, README, bridge register and provenance.
