# Phase 7 verification — operations, UX, documentation, and release hardening

## Status

**PASS — Phase 7 complete.** The final Java 25 / Gradle 9.7.1 gate and all changes-sensitive live checks passed on the operator's Windows test environment. Version-parity/IDE cleanup was rebuilt and live-verified after the initial closeout review. Phase 7 is formally closed.

Phase 7.5 is the next phase for production readiness, GitHub preparedness, final hardening/testing, and documentation cleanup on Minecraft 26.2. Do not begin the 26.3 port until Phase 7.5 closes.

## Java 25 / Gradle 9.7.1 gate — PASS

Evidence retained in operator-supplied repository:

- final closeout archive: `Guardian(20261005-120941).zip`
- archive SHA-256: `caf5b57930b6cad2c0a36e7c6d941e4f0ff099814280ec8bebde2448007b5b24`
- project source default: `0.1.0-phase7`
- Gradle wrapper: `9.7.1`
- Java target/toolchain: `25`

Retained XML results:

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| guardian-core | 122 | 0 | 0 | 4 |
| guardian-paper | 95 | 0 | 0 | 4 |
| guardian-protection | 20 | 0 | 0 | 0 |
| guardian-protocol | 50 | 0 | 0 | 0 |
| guardian-velocity | 60 | 0 | 0 | 3 |
| cerberus-fabric | 19 | 0 | 0 | 0 |
| **Total** | **366** | **0** | **0** | **11** |

The 11 skips remain the documented Windows symbolic-link privilege assumption skips. No product test is failing or unexpectedly skipped.

The final version-parity/IDE cleanup added one Paper regression test, raising the authoritative total from 365 to 366. The retained XML in the final closeout archive is authoritative.

## Final built artifact evidence

The retained successful build contains:

```text
guardian-paper-0.1.0-phase7.jar
guardian-velocity-0.1.0-phase7.jar
cerberus-fabric-0.1.0-phase7.jar
```

SHA-256 values:

```text
Guardian-Paper:                  7c11d9b3e052c11e595a29c2a24cc7f04d696143750e18fb6e1a579652d08578
Guardian-Velocity:               6cf3b7dabea8b88b1415d57d0ebcf2158d05e95970d90a1aea15b46429184cf4
Cerberus-Fabric unsigned:        946d1d9277333ee0acb457bf34a7743eb8f48bdf49f0579ed70d6a6779a1b683
```

The final unsigned closeout archive intentionally does not retain a newly signed Cerberus JAR because the already-established signing workflow was not rerun after the metadata-only Velocity fix. The latest post-helper smoke produced `cerberus-fabric-0.1.0-phase7-smoke-signed.jar` with finished-file SHA-256 `fda05ddb12560d313941ff7e7dfcda0dd5e4e10b98acc9ef1170f03e3c2f4aaa`; its Fabric metadata reported `0.1.0-phase7-smoke`. The signer printed canonical SHA-256:

```text
103ebc6bf2a424f49ffc92d7772f9be94278cb6babae3a1d34b4c6982e9971ef
```

The canonical digest and finished-file digest are intentionally different values.

Artifact entry inspection confirmed the expected GPL/third-party notice material, Apache-2.0 material in the Paper/Velocity shaded-runtime JARs, and no conventionally named private-key resource in any retained JAR.

## Clean standalone Paper — PASS

A clean Paper install loaded `0.1.0-phase7` successfully. `/guardian status` correctly reported standalone authority, optional integrations, proxy assertion not applicable, server authentication disabled, and signed Cerberus release optional. `/guardian validate` succeeded without runtime mutation.

After minor administrator configuration edits, `/guardian reload` succeeded and atomically activated the complete validated Paper-local runtime candidate, including Guardian Protection. This confirms the Phase 7 status/validate/reload UX on a clean Paper installation.

## Clean Velocity / version parity — PASS

A clean Velocity install successfully exercised:

- `/guardianv status`;
- `/guardianv validate`;
- administrator configuration changes followed by `/guardianv reload`; and
- normal runtime activation.

The original clean test exposed a release-blocking metadata defect: `/guardianv status` reported `0.1.0-phase6` even though Gradle built a Phase 7 JAR. The final fix removes all hard-coded/generated-Java release constants. Gradle expands `guardianVersion` into `velocity-plugin.json`, and runtime status reads Velocity's loaded plugin metadata. Paper already uses the parallel `plugin.yml`/`PluginMeta` model.

Final post-fix verification passed:

```text
Guardian Velocity version=0.1.0-phase7, protocol=1, authority=Velocity
```

The built JAR's `velocity-plugin.json` also reports `0.1.0-phase7`, the full Gradle gate remains green, and the prior VS Code constant-expression/generated-source errors are gone.

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

## Formal closeout — PASS

The final post-fix Java 25 / Gradle 9.7.1 build is green at 366 tests, `guardianv status` reports the Gradle-supplied `0.1.0-phase7`, the built Velocity metadata contains the same version, and the release helper automatically produced `cerberus-fabric-0.1.0-phase7-smoke-signed.jar`. No further Phase 7 live matrix is justified.

Final source/build artifact SHA-256 values retained in `Guardian(20261005-120941).zip`:

```text
Guardian-Paper:            7c11d9b3e052c11e595a29c2a24cc7f04d696143750e18fb6e1a579652d08578
Guardian-Velocity:         6cf3b7dabea8b88b1415d57d0ebcf2158d05e95970d90a1aea15b46429184cf4
Cerberus-Fabric unsigned:  946d1d9277333ee0acb457bf34a7743eb8f48bdf49f0579ed70d6a6779a1b683
```

Artifact metadata inspection confirmed Paper `plugin.yml`, Velocity `velocity-plugin.json`, and Cerberus `fabric.mod.json` all report `0.1.0-phase7`. Conventional private-key filenames/resources are absent from the retained distributable JARs.

**Phase 7 is closed. Phase 7.5 is next; Phase 8 remains deferred.**
