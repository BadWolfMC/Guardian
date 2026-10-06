# Phase 7.5 verification — production readiness / public release hardening

## Status

**IMPLEMENTED; implementation-environment sanity checks passed, but the authoritative Java 25/Gradle, real Actions release-input, local PowerShell finalization, and focused live gates remain required before formal closeout.**

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

The implementation environment provides Java 21 only and cannot download the uncached Gradle 9.7.1 distribution, so it cannot truthfully record the authoritative Java 25 / Gradle 9.7.1 result. PowerShell is also unavailable there, so the complete `finalize-release` wrapper must be exercised on the intended Windows release machine using the exact artifact downloaded from the real GitHub Actions run.

## Required automated gate

Run with Java 25 and the repository Gradle 9.7.1 wrapper:

```powershell
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Record module totals, failures/errors/skips, and final artifact hashes here after execution. With the Phase 7.5 follow-up applied, a default build should report version `1.0.0` consistently in Paper, Velocity, and Cerberus metadata; an explicit `-PguardianVersion=...` still overrides that value for RC/rehearsal builds.

### First operator gate after reviewed checkpoint

The first Java 25 / Gradle 9.7.1 run against the reviewed checkpoint executed **373 tests** across the six modules and exposed two stale test expectations: Core expected literal per-key `.gitignore` lines even though Phase 7.5 had moved to the stronger `*.key` ignore rule, and Paper expected the old `Protection=false` packaged default even though Phase 7.5 intentionally changed it to `true`. All other reported module results were green. The follow-up patch corrects those expectations and adds one release-manager parser regression test, so the next complete gate is expected to contain **374 tests** before accounting for any further test additions.

The same operator attempt to use the documented lower-level signing command exposed a PowerShell parser error caused by interpolating variables immediately before `:` in double-quoted strings. All three such release-manager strings now use `${name}:`, and the normal Admission documentation points administrators to the full `finalize-release` CI-input path rather than local `build/libs` signing.

## Release-input rehearsal

From a clean Git checkout with no retained `build/` or `.gradle/` project state:

1. run the Release Candidate workflow (or reproduce its commands locally with an explicit RC version);
2. confirm the CI/input directory contains exactly the expected Paper/Velocity/unsigned-Cerberus inputs, the CI-built release-tool JAR, license/notices, `RELEASE_INPUT.json`, and `SHA256SUMS-CI.txt`;
3. verify the input manifest binds the exact source commit, repository, workflow run, and run attempt;
4. confirm private-key material is absent; and
5. copy/download the input to the release machine without rebuilding Cerberus.

## Offline finalization rehearsal

Using disposable or release-manager test identities, run:

```powershell
.\tools\release-manager.ps1 `
  -Action finalize-release `
  -Version 1.0.0-rc.1 `
  -InputDirectory .\release-input `
  -OutputDirectory .\release-final `
  -ReleasePrivateKey <offline-private-key> `
  -ReleasePublicKey <matching-public-key> `
  -GuardianServerPublicKeys <server-auth-public-trust-file>
```

Expected final set:

```text
guardian-paper-1.0.0-rc.1.jar
guardian-velocity-1.0.0-rc.1.jar
cerberus-fabric-1.0.0-rc.1-signed.jar
LICENSE
THIRD_PARTY_NOTICES.md
RELEASE_PROVENANCE.txt
SHA256SUMS.txt
```

Verify that:

- no unsigned Cerberus JAR is present;
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

Until then, do not begin the 26.3 Phase 8 port.
