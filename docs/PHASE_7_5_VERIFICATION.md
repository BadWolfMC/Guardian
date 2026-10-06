# Phase 7.5 verification — production readiness / public release hardening

## Status

**CLOSED — 2026-10-06. Phase 7.5 production-readiness/public-release hardening is formally complete. The fresh Java 25 gate, normal GitHub CI including the real Windows PowerShell release-tooling smoke, `1.0.0-rc.2` Release Candidate/offline-finalization rehearsal, final artifact/security/privacy/license audit, real-Git hygiene checks, focused live matrix, and rollback/recovery rehearsal all passed.**

Phase 7 remains the entering runtime/security baseline. Phase 7.5 changes are intentionally concentrated in release provenance, packaged defaults, repository/public documentation, release tooling, and operations/runbook material.

## Implementation-environment checks completed

The Phase 7.5 implementation worktree has passed the following non-authoritative sanity checks before handoff to the real Java 25/Windows/GitHub environment:

- `git diff --check` is clean.
- A synthetic Git inspection initialized from the exact operator archive confirms the repository ignore rules exclude `build/`, `.gradle/`, release-staging directories, ZIP/patch artifacts, and conventional private-key filenames. The real upstream Git checkout still requires the explicit hygiene check below.
- `ci.yml`, `release-candidate.yml`, and `dependabot.yml` parse as YAML; source Fabric/Velocity metadata parses as JSON after normal Gradle version substitution.
- repository-relative Markdown links resolve.
- the packaged Cerberus icon is byte-identical to the supplied `cerberus-voxels-128.png` (`0dbbe543c8261be37278ba58e12ba2eec075fffe280b5ea909a347c8f67db352`).
- `guardian-protocol` plus the Cerberus release-tool sources compile under the available Java 21 compiler as a lower-bound syntax/type smoke. This is **not** a substitute for the Java 25 Gradle gate.
- the five new Phase 7.5 release-workflow architecture checks execute successfully in an isolated JUnit-compatible smoke harness.
- the two new packaged-public-default checks execute successfully against the actual `config.yml` and `en_us.properties` resources.
- the current signer/verifier was exercised directly with disposable Ed25519 identities against an unsigned retained Cerberus artifact; signing, canonical-digest verification, release-signature verification, and embedded Guardian server-authentication trust-anchor verification all passed.

The original implementation environment provided Java 21 only and could not truthfully record the authoritative Java 25 / Gradle 9.7.1 or PowerShell result. Those operator/Actions gates were subsequently exercised as recorded below, including the final-hardening rerun and Windows PowerShell CI smoke.

## Required automated gate

Run with Java 25 and the repository Gradle 9.7.1 wrapper:

```powershell
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Record module totals, failures/errors/skips, and final artifact hashes here after execution. With the Phase 7.5 follow-up applied, a default build should report version `1.0.0` consistently in Paper, Velocity, and Cerberus metadata; an explicit `-PguardianVersion=...` still overrides that value for RC/rehearsal builds.

### First operator gate after reviewed checkpoint

The first Java 25 / Gradle 9.7.1 run against the reviewed checkpoint executed **373 tests** across the six modules and exposed two stale test expectations: Core expected literal per-key `.gitignore` lines even though Phase 7.5 had moved to the stronger `*.key` ignore rule, and Paper expected the old `Protection=false` packaged default even though Phase 7.5 intentionally changed it to `true`. All other reported module results were green. The follow-up patch corrects those expectations and adds one release-manager parser regression test, so the next complete gate is expected to contain **374 tests** before accounting for any further test additions.

The same operator attempt to use the documented lower-level signing command exposed a PowerShell parser error caused by interpolating variables immediately before `:` in double-quoted strings. All three such release-manager strings now use `${name}:`, and the normal Admission documentation points administrators to the full `finalize-release` CI-input path rather than local `build/libs` signing.

### Green operator/Actions gate and real release rehearsal

After those corrections, the complete Java 25 / Gradle 9.7.1 gate returned to green at **374 tests, 0 failures/errors, and the same 11 documented Windows symlink-privilege skips**. GitHub CI also completed successfully after restoring the tracked executable bit on `gradlew` (`100755`); the earlier Linux `./gradlew: Permission denied` failure was repository mode metadata from a Windows branch reconstruction, not a Gradle/runtime defect.

A real GitHub **Release Candidate** rehearsal then completed for `1.0.0-rc.1`:

- repository: `BadWolfMC/Guardian`;
- source commit: `b4c34e136118eb84590d109e52d7a0531dd7764c`;
- workflow run: `37404931565`, attempt `1`;
- CI unsigned Cerberus SHA-256: `9b79ea54aa5b7ff5943e5cdacdb60cac771cda3151e24d5a13743563183bb52b`;
- CI release-tool SHA-256: `40528cb96982b60d7e1364d3dbfa2a144e8163e5602fb60158327c6058777260`;
- final signed Cerberus SHA-256: `be8454982a7d4b3e4a732dd18ad22de0192573471c17287d501caf7c8381ca03`;
- final Paper SHA-256: `8788b71e01b9daea3a23d5caa84096b73ec32b7d0110739f0c0db2ce43924f8d`; and
- final Velocity SHA-256: `bddf7bb6c7511371623fc46bfaf16a06da563fae87c3f854e5a0a8b53dc0560b`.

The downloaded `release-input/` checksums matched, local `finalize-release` succeeded using the exact CI-built unsigned client and CI-built release tool, final Paper/Velocity remained byte-identical to CI, and independent inspection found the signed Cerberus logical contents differed from the unsigned input only by the intended Guardian release-identity and server-authentication trust-anchor resources. The release public-key fingerprint recorded by that rehearsal was `0afc70cd9cf8ad4f5b61ab109b8d93acd4f164cb04cf5bb358783949d4b0ca3b`; that SHA-256 is a fingerprint, **not** the value configured in `policy.yml`.

### Final hardening follow-up — historical pre-closeout requirement

The post-rehearsal review made a small final set of release/tooling/code-quality changes:

- SemVer validation is authoritative in the root Gradle build and is aligned with the PowerShell release manager; Velocity no longer carries a divergent version regex, and SemVer build metadata is accepted consistently.
- normal finalization now publishes the exact `cerberus-release-signing.pub` verification key as a checksummed public release asset; policy documentation explicitly requires the file's base64 key contents rather than any SHA-256 value;
- CI now exercises the actual PowerShell finalization/verification path on `windows-latest` with disposable generated keys, including a build-metadata version;
- missing JUnit XML is diagnostic-only (`warn`) when an earlier build step prevents tests from starting, while real build/test failures remain authoritative; Release Candidate runs now retain test XML as diagnostics too;
- the Phase 0A sanity report is explicitly historical; and
- `ProtocolCodec`, `ManifestCanonicalizer`, and `CerberusReleaseIdentityGenerator` receive readability-only formatting/structure cleanup without protocol or cryptographic semantic changes.

These changes superseded the `1.0.0-rc.1` rehearsal as the eventual publishable candidate. At that checkpoint, the required next evidence was a **376-test** Gradle gate, the separate Windows PowerShell release-workflow smoke job, and a fresh RC/finalization rehearsal confirming the new eight-file public release set including `cerberus-release-signing.pub`. That evidence is recorded in the closeout section below.

## Final hardening closeout evidence — `1.0.0-rc.2`

The final closeout audit is based on the operator-supplied source/rehearsal archive:

- archive: `Guardian(20261006-092842).zip`;
- archive SHA-256: `eb62745b680bbe9bce75027421ce232903a95fc1a7076ac72c7da53efb7992f1`;
- source default remains `1.0.0`; and
- retained Java 25 Gradle XML reports: **376 tests, 0 failures, 0 errors, 11 skipped**.

Final module totals are:

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Guardian Core | 122 | 0 | 0 | 4 |
| Guardian Paper | 97 | 0 | 0 | 4 |
| Guardian Protection | 20 | 0 | 0 | 0 |
| Guardian Protocol | 50 | 0 | 0 | 0 |
| Guardian Velocity | 60 | 0 | 0 | 3 |
| Cerberus Fabric | 27 | 0 | 0 | 0 |
| **Total** | **376** | **0** | **0** | **11** |

All 11 skips remain the documented Windows symbolic-link privilege assumption skips; no functional/product test is unexpectedly skipped.

Normal GitHub CI completed the Java 25 build/test gate and the dependent `windows-latest` job exercising the real `test-release-workflow.ps1` -> `finalize-release` -> `verify-release` path with disposable keys. Post-job `actions/setup-java` Gradle cache cleanup emitted a non-gating Windows warning while trying to archive open Gradle `.lock` files after the job had succeeded. That cache-save warning is accepted runner/tooling noise and does not weaken the build or release-tooling gate.

The fresh GitHub **Release Candidate** rehearsal is authoritative for the release-producing provenance chain:

- repository: `BadWolfMC/Guardian`;
- source commit: `ddcad249c4837fdcf68c1c3a00d8fda0ecbb2da5`;
- workflow run ID / attempt: `37418021934` / `1`;
- rehearsal version: `1.0.0-rc.2`;
- `RELEASE_INPUT.json` SHA-256: `dade8110079feedd60002934b26dab3c0c5a18902f112febaf814fba7a54e47d`;
- `SHA256SUMS-CI.txt` SHA-256: `da4ad3514b9f9f6ffce8bf55643bc943db30bae374c739d6cfc185dd2e628896`;
- CI unsigned Cerberus SHA-256: `da717d484df47532c12025ee8f49782671cd23fc0fcbe642618c65ba57fb0152`;
- CI release-tool SHA-256: `1c30f9337d095ee56b0cb43e01d7882bd73850fe43a85bca449ebe957eaa0867`;
- final signed Cerberus SHA-256: `81341cc1ef23755ccadadc26901f0ad44187ef668d30f24ea8ab77e6ae2ad185`;
- final Guardian-Paper SHA-256: `72ce503a173ed43e52d42fa89770bfad2bfdf98b07264417d9436192a7ff0dcd`;
- final Guardian-Velocity SHA-256: `f29a883bee9b553191b9847ab0b4aa6061f7607f3a4177dbe887e174707a8e3c`;
- final public release-key file SHA-256: `0afc70cd9cf8ad4f5b61ab109b8d93acd4f164cb04cf5bb358783949d4b0ca3b`;
- final `RELEASE_PROVENANCE.txt` SHA-256: `2e015217335b59c1619e1667c2118150532c7d8d95a5a67fbb1a191e34f6dba5`; and
- final `SHA256SUMS.txt` SHA-256: `84456fb63f1dfb3400a02937d09a1dca8c9de06573cc85a279743af8f15a474c`.

The final release directory contains exactly the intended eight public assets. `SHA256SUMS-CI.txt` and `SHA256SUMS.txt` reverify completely. Final Paper and Velocity are byte-identical to the checked CI inputs. The signed Cerberus logical archive differs from the unsigned CI input only by the intended `META-INF/guardian/cerberus-release.bin`, `META-INF/guardian/trusted-server-keys.txt`, and their directory entry; no existing logical entry changed. The published `cerberus-release-signing.pub` contains the policy-usable base64 public key `MCowBQYDK2VwAyEAL1zDo4KrC9B+CBtPJ3F5q0mo5lhlXvKJk0loCIWH7hs=`; its SHA-256 fingerprint is not a `policy.yml` key value.

### Focused live / rollback matrix

The rc.2 deployment/recovery checks passed on the intended Velocity -> Paper topology:

- Guardian-Velocity `status`/`validate`: PASS; Velocity authority, LuckPerms/Geyser/Floodgate availability, proxy assertion configuration, Guardian server authentication, and signed-Cerberus requirement all reported correctly.
- Guardian-Paper behind Velocity `status`/`validate`: PASS; Velocity authority, independent Protection enablement, Floodgate availability, and assertion verifier state reported correctly while server-auth/signed-Cerberus responsibilities remained not-applicable on the assertion-only backend.
- Vanilla Java: PASS (`JAVA_VANILLA` / `VANILLA_POLICY`).
- OptiFine Java: PASS (`JAVA_OPTIFINE` / `OPTIFINE_POLICY`).
- Signed Fabric/Cerberus: PASS. A newly installed unapproved `yet_another_config_lib_v3` version was first denied as `MANIFEST_DENIED`; after the administrator updated policy and atomically reloaded Guardian-Velocity, the same client was admitted as `CERBERUS_VERIFIED`. `/guardianv inspect` reported the active authoritative snapshot for Cerberus `1.0.0-rc.2`, protocol `1..1`, 28 policy-addressable / 144 Loader-known mods, and non-Bedrock evidence. A subsequent Alpha -> Beta switch reused the proxy-session admission rather than triggering backend manifest authority.
- Guardian Protection execution/namespace policy: PASS. A non-bypass/non-OP account was denied `/pl` as `EXECUTION_DENIED` and `/velocity:callback` as `NAMESPACE_DENIED`; command-tree visibility contained only configured commands. Guardian bypass permissions also behaved as intended for Guardian-owned filtering.
- Atomic reload: PASS on both authorities. Velocity policy reload was exercised during the Fabric test; Paper `validate`/`reload` succeeded repeatedly and reconfigured Protection without restart.
- Rollback/recovery: PASS. Alpha was rolled back from `1.0.0-rc.2` to `1.0.0-rc.1`, validated/status-checked successfully with the existing configuration/key material, then restored to `1.0.0-rc.2` and validated/status-checked successfully.

No repeated Bedrock matrix was required because Phase 7.5 did not alter the Geyser/Floodgate classification implementation or configuration semantics.

### Real-Git hygiene closeout

The operator's real checkout produced clean `git status --short` and `git diff --check` output. `release-input/` and `release-final/` resolve to the repository ignore rules; `release-artifacts/` is likewise explicitly ignored in `.gitignore`. A tracked-file scan found no `.gradle/`, `build/`, release-staging, private-key, ZIP, or patch artifact. `gradlew` is tracked as executable mode `100755`.

### Final audit / closeout decision

The final source/artifact/security/privacy/license/repository-hygiene audit found no first-public-release blocker and no reason to reopen the frozen Phase 7.5 architecture. Admission/Protection independence, Velocity authority with standalone-Paper fallback, assertion-only backends, command ownership, Geyser/Floodgate precedence, active-session-only inspection, exact-artifact semantics, Ed25519 trust boundaries, three-key-domain separation, and the prohibition on server implementation/NMS/packet workarounds remain intact. No 26.3 implementation work is included in this closeout.

**Phase 7.5 is formally CLOSED. Phase 8 is unblocked by the Phase 7.5 prerequisite, subject to Phase 8's separate platform-readiness requirement that Paper 26.3 be sufficiently stable to target deliberately.**

## Release-input rehearsal

From a clean Git checkout with no retained `build/` or `.gradle/` project state:

1. run the Release Candidate workflow (or reproduce its commands locally with an explicit RC version);
2. confirm the CI/input directory contains exactly the expected Paper/Velocity/unsigned-Cerberus inputs, the CI-built release-tool JAR, license/notices, `RELEASE_INPUT.json`, and `SHA256SUMS-CI.txt`;
3. verify the input manifest binds the exact source commit, repository, workflow run, and run attempt;
4. confirm private-key material is absent; and
5. copy/download the input to the release machine without rebuilding Cerberus.

## Offline finalization rehearsal

For the next rehearsal after the `1.0.0-rc.1` evidence above, use a fresh identifier such as `1.0.0-rc.2` so the resulting assets cannot be confused with the superseded candidate:

```powershell
.\tools\release-manager.ps1 `
  -Action finalize-release `
  -Version 1.0.0-rc.2 `
  -InputDirectory .\release-input `
  -OutputDirectory .\release-final `
  -ReleasePrivateKey <offline-private-key> `
  -ReleasePublicKey <matching-public-key> `
  -GuardianServerPublicKeys <server-auth-public-trust-file>
```

Expected final set for that rehearsal:

```text
guardian-paper-1.0.0-rc.2.jar
guardian-velocity-1.0.0-rc.2.jar
cerberus-fabric-1.0.0-rc.2-signed.jar
cerberus-release-signing.pub
LICENSE
THIRD_PARTY_NOTICES.md
RELEASE_PROVENANCE.txt
SHA256SUMS.txt
```

Verify that:

- no unsigned Cerberus JAR is present;
- `cerberus-release-signing.pub` is present, byte-identical to the supplied verification key, covered by final checksums, and its base64 contents (not its SHA-256) are suitable for `policy.yml`;
- Paper/Velocity files are byte-identical to the checked CI inputs;
- signed Cerberus derives from the checked unsigned input;
- signing/verification uses the checked CI-built release-tool JAR rather than locally rebuilt release code;
- all metadata reports the requested version;
- signed release identity verifies against the release public key;
- intended Guardian server-auth public anchors are embedded;
- the Cerberus icon is present;
- required license/NOTICE resources are present;
- no private-key resource/material or administrator-local path is present; and
- rerunning `verify-release` with the CI release-tool JAR succeeds without rebuilding/re-signing.

## Git/public repository hygiene

Before public release, perform these checks from the real Git checkout (the operator ZIP does not contain `.git` history):

```text
git status --short
git ls-files
git check-ignore -v release-input release-final release-artifacts
```

Confirm no tracked `.gradle/`, `build/`, release staging, private key, third-party approved artifact, local path, or extracted inspection artifact exists.

## Small production/live matrix

Do not replay the Phase 6 adversarial suite. After Phase 7.5 artifacts/config are final, the changes-sensitive evidence is:

1. normal Vanilla Admission;
2. normal signed Fabric/Cerberus Admission using the finalized signed JAR;
3. one backend switch under Velocity authority;
4. one representative Guardian Protection execution + visibility check because Protection now defaults enabled;
5. validate/reload after a harmless policy/config change; and
6. one non-destructive rollback/recovery rehearsal from `PRODUCTION_RUNBOOK.md`.

Repeat Bedrock only if Phase 7.5 changes touch its actual integration/configuration beyond documentation.

## Formal closeout conditions

Phase 7.5 closes only when:

- the complete Java 25 / Gradle 9.7.1 gate is green;
- clean-checkout/release finalization is rehearsed;
- final artifacts/metadata/licenses/private-key leakage are inspected;
- the real Git repository is hygiene-checked;
- the small production matrix above passes;
- the production backup/rollback/key procedure is rehearsed; and
- no first-public-release blocker remains.

All Phase 7.5 closeout conditions above are now satisfied by the final evidence recorded in this document. Phase 8 remains a separate platform-port phase and may begin only under its own readiness preconditions.


## Post-closeout `1.0.0` stable-release preparation

After formal Phase 7.5 closeout, one final public-release hygiene pass removes the runtime `guardian.cerberus.dev.*` fault-injection/manifest/protocol switches that existed solely to manufacture earlier live-test failure cases, removes the remaining production-source test-only Velocity session convenience constructor, and cleans stable-product wording in current operator defaults/docs. The source default remains `1.0.0`; protocol v1, schema 2, policy semantics, trust/key boundaries, and platform authority are unchanged.

This is intentionally **after** the `1.0.0-rc.2` rehearsal, so the rc.2 artifacts are not promoted to the final release. Before publishing `1.0.0`, run the full Java 25 gate and Windows release-tooling smoke on the final commit, run the Release Candidate workflow with exact version `1.0.0`, finalize/verify that downloaded release-input offline, and smoke-test the resulting draft assets. Record the final commit/run IDs and hashes in release provenance rather than reusing rc.2 values.
