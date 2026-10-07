# Phase 8 verification — Minecraft/Paper/Fabric 26.3 port

**Status:** OPEN — compatibility review and source port are complete; Java 25 automated and focused live evidence remain required.

## Entering evidence

For `Guardian(20261006-225143).zip`:

- archive SHA-256: `0c8a43e7ab7c74a2b7ee6b881eab8bee2a1fbf14860a33f49596e63ed40e799d`;
- retained source/release baseline: Guardian/Cerberus `1.0.1`, Minecraft/Paper/Fabric `26.2`;
- retained Gradle XML: **378 tests, 0 failures, 0 errors, 11 skips**;
- finalized `release-final/` provenance reports source commit `ce03f99d2f33a3256268d7814443c596533f75bb`, workflow run `37480780048` / attempt `1`;
- `release-final/SHA256SUMS.txt`: PASS when re-verified against the retained 1.0.1 release directory.

The current execution sandbox exposes Java 21 only, so it cannot honestly execute Guardian's Java 25 Gradle gate. The candidate must be tested on Java 25 before it is treated as a release candidate.

## Compatibility-review result

No demonstrated 26.3 API regression currently requires an architectural change. In particular:

- Paper 26.3 retains Guardian's supported connection/configuration/validation and Protection hooks;
- current Paper guidance still supports the dynamic `26.3.build.+` API dependency and Java 25;
- Fabric Loader 0.19.5 remains usable and Fabric API 0.162.0+26.3 is available;
- Fabric's published 26.3 removals do not target the Loader origin/containment or networking APIs Guardian uses;
- Velocity's documented API dependency remains 4.2.1-SNAPSHOT;
- Geyser/Floodgate's documented API dependencies remain 2.11.2-SNAPSHOT / 2.2.5-SNAPSHOT;
- LuckPerms API remains 5.5.

Accordingly, the first port candidate changes dependency/platform metadata and current documentation, not the working Admission/Protection implementation.

## Automated gate

Run on Java 25 from the repository root:

```powershell
.\gradlew.bat clean test `
  :guardian-paper:jar `
  :guardian-velocity:jar `
  :cerberus-fabric:build `
  :cerberus-fabric:releaseToolJar
```

Before Phase 8 can close, record:

- total tests/failures/errors/skips;
- successful Paper JAR;
- successful Velocity JAR;
- successful Cerberus build;
- successful Cerberus release-tool JAR;
- any source adaptation that became necessary only after compiling against 26.3.

Any 26.3-specific source adaptation must receive a focused regression test where practical.

## Focused live matrix

Do not repeat the Phase 6/7.5 adversarial matrix. If the automated gate is green, the smallest useful live set is:

1. **Standalone Paper 26.3 + signed Fabric/Cerberus 26.3** — verify successful Admission and observe whether the proven CONFIGURATION + bounded PLAY fallback remains necessary. Do not remove the fallback merely because internals changed.
2. **Standalone Paper 26.3 + vanilla** — verify the ordinary non-Cerberus allow path remains clean.
3. **Velocity-authoritative signed Fabric/Cerberus** — verify one proxy-side decision, trusted assertion-only Paper admission, `/guardianv inspect`, and one backend switch without re-attestation or manifest forwarding. During the same session, exercise one Guardian Protection execution denial and one visibility/suggestion check on a backend.
4. **Bedrock through current Geyser/Floodgate** — verify supported-API Bedrock classification occurs before Java/Cerberus handling and no Cerberus challenge is attempted.

OptiFine, exhaustive malformed protocol cases, key rotation, replay/fault injection, rollback rehearsal, and the larger release matrix do not need to be mechanically repeated unless the automated gate or these focused checks expose a relevant regression.

## Release/security/privacy audit before closeout

Confirm after the final build:

- no NMS/CraftBukkit implementation reflection or private/internal networking workaround was added;
- no server Mixins, ProtocolLib, or PacketEvents dependency was added;
- no `guardian.cerberus.dev.*` or equivalent public runtime fault-injection switches returned;
- no absolute/admin filesystem paths are transmitted or persisted;
- full manifests remain memory-only and are not forwarded to Paper under Velocity authority;
- `proxy-assertion.key`, `guardian-server-auth.key`, and the offline Cerberus release-signing identity remain separate trust domains;
- the release-signing private key remains absent from GitHub Actions and packaged artifacts;
- staged old+new public trust-anchor rotation remains intact;
- key documentation continues to distinguish public-key contents from SHA-256 fingerprints;
- final JARs contain no private/key-like test material or unexpected development resources;
- license and third-party notices remain correct;
- current config/policy/locale examples contain no stale 26.2 target wording;
- historical 26.2 phase records remain historical rather than being rewritten as 26.3 evidence;
- repository hygiene is clean.

## Closeout boundary

Phase 8 remains open until the Java 25 gate and focused live matrix are supplied and green. Only then should the final closeout patch update this file with evidence, create final 1.0.2 release provenance/checksums, and prepare the next-phase handoff/release commands.
