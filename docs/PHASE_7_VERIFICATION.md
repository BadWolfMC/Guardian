# Phase 7 verification — operations, UX, documentation, and release hardening

## Status

**Closeout candidate.** The Java 25 / Gradle 9.7.1 gate and all changes-sensitive live checks have passed on the operator's Windows test environment. Final closeout is waiting only for one post-review rebuild/status check after the small Velocity version-plumbing and release-filename ergonomics patch described below.

Do not begin Phase 8 until that final post-patch check is recorded.

## Java 25 / Gradle 9.7.1 gate — PASS

Evidence retained in operator-supplied repository:

- archive: `Guardian(20261005-110553).zip`
- archive SHA-256: `41575549b9d1075c1d93906c4276bb44a1b249fc7c922ad62c431a98f72915f1`
- project source default: `0.1.0-phase7`
- Gradle wrapper: `9.7.1`
- Java target/toolchain: `25`

Retained XML results:

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| guardian-core | 122 | 0 | 0 | 4 |
| guardian-paper | 94 | 0 | 0 | 4 |
| guardian-protection | 20 | 0 | 0 | 0 |
| guardian-protocol | 50 | 0 | 0 | 0 |
| guardian-velocity | 60 | 0 | 0 | 3 |
| cerberus-fabric | 19 | 0 | 0 | 0 |
| **Total** | **365** | **0** | **0** | **11** |

The 11 skips remain the documented Windows symbolic-link privilege assumption skips. No product test is failing or unexpectedly skipped.

The closeout patch does not add a new test method; it strengthens an existing Velocity architecture test. A final rerun should therefore remain at 365 tests if no other source changes are made, but the executed XML remains authoritative.

## Built artifact evidence before the final metadata patch

The retained successful build contains:

```text
guardian-paper-0.1.0-phase7.jar
guardian-velocity-0.1.0-phase7.jar
cerberus-fabric-0.1.0-phase7.jar
cerberus-fabric-phase7-signed.jar
```

SHA-256 values:

```text
Guardian-Paper:                  7c11d9b3e052c11e595a29c2a24cc7f04d696143750e18fb6e1a579652d08578
Guardian-Velocity:               3585ca25c5fffec25e87e3fc95435cb022959ae40925f3e0ab3c08703605d969
Cerberus-Fabric unsigned:        946d1d9277333ee0acb457bf34a7743eb8f48bdf49f0579ed70d6a6779a1b683
Cerberus-Fabric signed finished: 2353916140070080393cf51e93a6c82e14392e688e4dab9b3826969995eced36
```

The signed smoke JAR reports Fabric version `0.1.0-phase7-smoke`, proving that the helper passes the requested release version into Cerberus metadata/signing. The signer printed canonical SHA-256:

```text
103ebc6bf2a424f49ffc92d7772f9be94278cb6babae3a1d34b4c6982e9971ef
```

The canonical digest and finished-file digest are intentionally different values.

Artifact entry inspection confirmed the expected GPL/third-party notice material, Apache-2.0 material in the Paper/Velocity shaded-runtime JARs, and no conventionally named private-key resource in any retained JAR.

## Clean standalone Paper — PASS

A clean Paper install loaded `0.1.0-phase7` successfully. `/guardian status` correctly reported standalone authority, optional integrations, proxy assertion not applicable, server authentication disabled, and signed Cerberus release optional. `/guardian validate` succeeded without runtime mutation.

After minor administrator configuration edits, `/guardian reload` succeeded and atomically activated the complete validated Paper-local runtime candidate, including Guardian Protection. This confirms the Phase 7 status/validate/reload UX on a clean Paper installation.

## Clean Velocity — functional PASS; version-reporting defect found and patched

A clean Velocity install successfully exercised:

- `/guardianv status`;
- `/guardianv validate`;
- administrator configuration changes followed by `/guardianv reload`; and
- normal runtime activation.

The test exposed one release-blocking metadata defect: `/guardianv status` reported `0.1.0-phase6` even though the built JAR filename/project version was `0.1.0-phase7`. `GuardianVelocityPlugin.VERSION` was still a hard-coded Phase 6 constant.

The final closeout patch removes that hard-coded version. Guardian-Velocity now generates a compile-time constant from the Gradle project version, preserving Velocity's annotation requirement while making `-PguardianVersion=<version>` authoritative for both JAR naming and embedded Velocity plugin/status metadata. The existing Velocity operations architecture test now asserts that the hard-coded Phase 6 value cannot return.

Required final confirmation after applying the closeout patch:

```text
/guardianv status
```

must report the same version supplied by Gradle (for the current branch, `0.1.0-phase7`).

## Supported schema-1 upgrade — PASS

A copied final Phase 6/pre-1.0 installation upgraded successfully to schema 2.

Verified:

- existing administrator config entries were preserved;
- comments/text layout were retained by the surgical migration;
- the pre-schema2 backup contained the old configuration;
- the missing signed-release trust block was safely backfilled where applicable;
- runtime activation reported schema 2;
- `/guardian validate` succeeded; and
- a second startup performed no second migration.

The test also verified Velocity-authority Paper behavior remained correct after migration: server authentication and signed-Cerberus release verification are reported as not applicable on the assertion-only backend.

This closes the intended compatibility contract: final Phase 6/pre-public schema 1 → public schema 2. Arbitrary older internal development schemas remain unsupported.

## Release helper / signing workflow — PASS with final ergonomics improvement

The Windows release-manager wrapper was exercised directly.

PASS:

- `generate-release-key` generated a Cerberus release-signing private/public identity;
- `generate-server-identity` generated a distinct Guardian server-authentication identity;
- `sign-cerberus` produced a valid signed JAR with one embedded public Guardian trust anchor;
- the requested `0.1.0-phase7-smoke` version appeared in signed Cerberus Fabric metadata;
- the helper printed the finished-file SHA-256 separately from the signer's canonical SHA-256; and
- `checksums` wrote the expected `SHA256SUMS.txt`.

Operator feedback correctly identified that the exact `SignedOutput` filename used in the smoke omitted the version even though the JAR metadata itself was correct. The final closeout patch improves the normal path:

```powershell
.\tools\release-manager.ps1 `
  -Action sign-cerberus `
  -Version 1.0.0 `
  -ReleasePrivateKey D:\GuardianKeys\cerberus-release\cerberus-release-signing.key `
  -GuardianServerPublicKeys D:\GuardianKeys\server-auth-trust.txt `
  -OutputDirectory D:\GuardianRelease\1.0.0
```

now creates:

```text
cerberus-fabric-1.0.0-signed.jar
```

An advanced exact `-SignedOutput` remains available, but the helper rejects it when the filename omits the requested version. `RELEASE_PROCESS.md` now starts with a plain-language normal-release walkthrough before the key-management/security reference detail.

## Velocity-authoritative signed Cerberus connection — PASS

The normal network path produced one concise authoritative proxy summary:

```text
mercurialmusic ALLOW: JAVA_FABRIC, brand=fabric, profile=default, CERBERUS_VERIFIED, mods=166
```

There was no duplicated product label in the message body. The result confirms the Phase 6 authority boundary remains intact through Phase 7: Velocity evaluates Admission and Paper remains assertion-only.

## Guardian server-authentication mismatch UX — PASS

With an intentionally untrusted Guardian server-authentication identity, stock Cerberus withheld its manifest and Guardian failed closed after the bounded handshake timeout.

The player-facing disconnect now explains the important diagnostic without weakening the privacy boundary:

```text
Cerberus was detected, but did not complete the Guardian handshake. If Cerberus reports an untrusted Guardian server identity, do not bypass that warning; contact staff.
```

The final decision remained `CERBERUS_TIMEOUT`; Guardian did not invent a client-supplied manifest-disclosure oracle or weaken the authenticated-challenge requirement.

## Dependency-license / NOTICE audit — PASS

Guardian-Paper and Guardian-Velocity shade SnakeYAML Engine 2.10 and embed the Apache-2.0 license plus Guardian GPL/third-party notice material. Cerberus embeds Guardian GPL/notice material without SnakeYAML. Paper, Velocity, Fabric, LuckPerms, Geyser, and Floodgate remain build/provided integrations rather than newly shaded Guardian runtime payloads.

## Gradle deprecation notice

The Cerberus signing smoke emitted Gradle's generic future-Gradle-10 deprecation notice. No Phase 7 runtime/security failure accompanied it, and Gradle 9.7.1 remains the authoritative wrapper target. Before a future Gradle 10 migration, run the relevant Cerberus task with `--warning-mode all` and distinguish Guardian build-script usage from Fabric Loom/plugin deprecations. This is not a Minecraft 26.2 / Phase 7 release blocker unless the detailed warning identifies Guardian-owned deprecated behavior requiring correction.

## Final post-closeout-patch check

After applying the final Phase 7 closeout patch, run:

```powershell
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Then verify:

```text
/guardianv status
```

reports `Guardian Velocity version=0.1.0-phase7` (or the explicitly supplied `guardianVersion`). Optionally rerun the signing helper using `-OutputDirectory` and confirm the created filename contains the version automatically.

If those checks are green, no further Phase 7 live matrix is justified. Record the final regenerated JAR hashes, mark Phase 7 closed, and hand off to Phase 8.
