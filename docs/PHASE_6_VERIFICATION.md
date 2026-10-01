# Phase 6 verification

## Current candidate status

**PASS — PHASE 6 CLOSED.** The complete Java 25 / Gradle 9.7.1 gate, focused real Paper/Velocity/Fabric live matrix, release-signing smoke, and final post-live security/privacy/code-quality review are complete.

The final source inventory and retained executed JUnit total are both 355 tests:

```text
guardian-core        117
guardian-paper        91
guardian-protection   20
guardian-protocol     50
guardian-velocity     58
cerberus-fabric       19
```

Final gate supplied on 2026-10-01:

```text
guardian-core        117 tests, 0 failures, 0 errors, 4 skipped
guardian-paper        91 tests, 0 failures, 0 errors, 4 skipped
guardian-protection   20 tests, 0 failures, 0 errors, 0 skipped
guardian-protocol     50 tests, 0 failures, 0 errors, 0 skipped
guardian-velocity     58 tests, 0 failures, 0 errors, 3 skipped
cerberus-fabric       19 tests, 0 failures, 0 errors, 0 skipped
---------------------------------------------------------------
TOTAL                355 tests, 0 failures, 0 errors, 11 skipped
```

All 11 skips are symlink-hardening tests that use JUnit assumptions when Windows cannot create symbolic links for the current account (`A required privilege is not held by the client`). They are environment-limited filesystem cases, not unexplained product skips. Core accounts for 4, Paper 4, and Velocity 3. All other tests executed.

## Required Java 25 / Gradle 9.7.1 gate

Run from the repository root on Windows with JDK 25 selected:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Expected environment:

```text
Java:   25
Gradle: 9.7.1 (repository wrapper)
```

**Result: PASS.** The final operator-supplied repository retains a fresh Java 25 / Gradle 9.7.1 gate with 355 tests, zero failures, zero errors, and the 11 documented Windows symlink-privilege skips above. The additional Cerberus test is the ZIPFS trust-anchor regression discovered during the live signing smoke.

After the build, inspect the distributables rather than only trusting compilation:

```powershell
Get-ChildItem .\guardian-paper\build\libs\
Get-ChildItem .\guardian-velocity\build\libs\
Get-ChildItem .\cerberus-fabric\build\libs\
```

No private key (`proxy-assertion.key`, `guardian-server-auth.key`, release-signing private key) or administrator-local file should be present inside the built JARs.

**Artifact inspection: PASS.** Built distributables are present and no private-key file/resource entry or administrator-local key material is packaged. Runtime code and examples necessarily contain conventional key *filenames* such as `guardian-server-auth.key` and `proxy-assertion.key`, but not the key bytes.

```text
guardian-paper-0.1.0-phase6.jar     SHA-256 832a91355ba5ad51896ec2b3f6439987c9c1747236aa3e1d966e637deab606df
guardian-velocity-0.1.0-phase6.jar  SHA-256 b514d745a5e6f417f8863a4c027890fa8f7f7fac3e46513b1522663dedd67a41
cerberus-fabric-0.1.0-phase6.jar    SHA-256 c8fe1cab3b2e4742efd5ec58d951dc37a351b4c55a72dfa2fc04ec6407ab2099
```

## Automated adversarial matrix represented by the candidate

The automated/source regression matrix covers:

- duplicate/conflicting presence and first-terminal-decision semantics;
- response-after-timeout/late completion;
- exact-connection same-UUID replacement/disconnect behavior on Paper and Velocity;
- stale active-inspection ownership across same-UUID reconnects;
- malformed/truncated/oversized/exact-maximum protocol payloads;
- malformed UTF-8/control/format characters;
- extreme manifest count, realistic large manifests, containment cycles/depth, duplicate IDs, invalid parent relationships, non-canonical ordering, invalid hashes, invalid capability masks, and protocol downgrade attempts;
- proxy assertion HMAC/UUID/lifetime/skew/replay/direct-backend/key-rotation boundaries;
- standalone Paper quarantine listener coverage for the supported PLAY fallback event surface;
- Geyser/Floodgate provider disappearance/re-enable/error/reconfiguration semantics;
- profile-provider timeout/throw/unavailable/null/disappearance fail-closed semantics, including identity-override handling;
- active-inspection count/projection/store bounds and runtime-generation semantics;
- log/diagnostic control-character injection boundaries;
- DIRECTORY/MIXED_OR_UNKNOWN/nested/builtin Fabric origin behavior;
- malformed/symlinked/non-regular/oversized/concurrently changing administrator files;
- stable/no-follow exact artifact hashing;
- signed Cerberus release verification and explicit non-attestation residual test;
- canonical signed-JAR content/entry bounds, duplicate entries, path-confusable/control entry names, and ZIP-bomb-style uncompressed content;
- Guardian Ed25519 challenge UUID/nonce/capability/time binding, wrong-key/expired/unsigned rejection, and trust-anchor bounds; and
- server-authentication key replacement/repeated failed reload semantics on Paper and Velocity.

## Focused manual/live checks still worth performing

Do **not** manufacture a giant hostile-client matrix manually. Most Phase 6 abuse cases are deliberately automated. The useful remaining checks are limited to behavior that depends on the real Paper/Velocity/Fabric lifecycle or final signed artifact packaging.

### 1. Standalone Paper — quarantine + authenticated signed Cerberus happy path — PASS

The operator generated a distinct Guardian server-authentication identity and Cerberus release-signing identity, embedded the public Guardian trust anchor in an officially signed Cerberus candidate, configured standalone Paper to require server authentication plus trusted signed Cerberus release identity, and connected with the real 166-entry Fabric environment.

Observed final result after the live ZIPFS fix:

```text
[Guardian] Guardian mercurialmusic ALLOW: JAVA_FABRIC, profile=default, CERBERUS_VERIFIED, mods=166
```

The player entered normally and the authoritative `NORMAL` log remained a concise summary rather than a manifest dump. The release-signing smoke also exposed two release-path defects before closeout: the 26.2 non-obfuscated Loom task is `jar`, not `remapJar`, and the JDK ZIP filesystem rejects `LinkOption.NOFOLLOW_LINKS` as a channel-open option. Both were fixed; the latter now has a real JAR-filesystem regression test.

### 2. Standalone Paper — suppressed response/quarantine behavior — PASS

With `-Dguardian.cerberus.dev.suppressResponse=true`, the player remained in the bounded PLAY quarantine for approximately ten seconds. Repeated movement attempts and representative bed/container interactions did not escape containment. Guardian then produced `CERBERUS_TIMEOUT` and disconnected the client with the configured timeout/help message.

### 3. Guardian server-authentication mismatch — PASS with Phase 7 UX follow-up

The operator generated a second temporary Guardian server identity, temporarily replaced only the standalone Paper private key, and connected with the already-signed Cerberus release that pinned only the original public key. Two attempts remained quarantined until `CERBERUS_TIMEOUT`; restoring the original private key and reloading immediately restored the normal `CERBERUS_VERIFIED` happy path.

This proves the real lifecycle privacy/fail-closed boundary: an untrusted Guardian identity did not receive a manifest response. Stock Cerberus also emits an explicit client-log warning that the Guardian challenge was unauthenticated/untrusted and that the manifest was not disclosed. The player-facing disconnect currently remains Guardian's generic timeout because the client intentionally sends no manifest/response after rejecting the challenge. Making that mismatch clearer in ordinary player-facing UX is a Phase 7 polish item, not a Phase 6 privacy-boundary defect.

### 4. Velocity-authoritative happy path + backend assertion regression — PASS

The signed Cerberus client connected through Guardian-Velocity and produced one network-authoritative result:

```text
Guardian mercurialmusic ALLOW: JAVA_FABRIC, brand=fabric, profile=default, CERBERUS_VERIFIED, mods=166
```

`/guardianv inspect mercurialmusic` reported the authoritative 45 policy-addressable / 166 Loader-known view, current backend, profile source, Cerberus/protocol state, artifact-catalog labels, and Geyser/Floodgate evidence. Switching backends reused the existing proxy grant without reconfiguration/re-attestation. Backend `/guardian inspect` remained assertion-only and directed authoritative inspection to `/guardianv inspect`, so the full Fabric manifest did not cross the established proxy/backend privacy boundary.

A repeat of the entire Phase 4 Bedrock allow/deny matrix was intentionally omitted because the Geyser/Floodgate stack and origin semantics were unchanged and remain covered by the Phase 4 live result plus Phase 6 automated provider-loss/reconfiguration tests.

## Release-signing smoke

Generate server identity material in a secure temporary directory (never commit either output):

```powershell
.\gradlew.bat :cerberus-fabric:generateGuardianServerIdentity `
  -PguardianServerIdentityDirectory=C:\secure\guardian-server-identity
```

The generator produces:

```text
guardian-server-auth.key   # private; Guardian authority only
guardian-server-auth.pub   # public; embed in Cerberus release
```

Create/sign an official Cerberus candidate using a separately managed Ed25519 **release-signing** private key and the public Guardian server-auth anchor file:

```powershell
.\gradlew.bat :cerberus-fabric:signCerberusRelease `
  -PcerberusReleasePrivateKey=C:\secure\cerberus-release-private.pem `
  -PguardianServerAuthPublicKeys=C:\secure\guardian-server-identity\guardian-server-auth.pub
```

The release-signing private key and Guardian server-auth private key are different trust domains and must remain private/off-repository.

## Final security/privacy/code-quality review — PASS

The post-live review against the final supplied repository found no Phase 6 blocker:

- the complete manifest is discarded with the mutable Admission session; active inspection retains only counts plus at most 64 deterministic policy-addressable summaries;
- disconnect cleanup remains exact-connection owned and there is no historical manifest store;
- Guardian `DEBUG` lifecycle output does not dump the complete manifest;
- protocol text/diagnostic sanitization and unparsed MiniMessage placeholders preserve log/message integrity;
- Bedrock origin handling still precedes Java/Cerberus handling;
- Velocity-authority Paper remains assertion-only, confirmed again by live backend inspection;
- files-only validation remains non-activating and failed reloads retain the current runtime;
- the final repository contains no administrator private-key file, and built JAR entry inspection found no private-key resource;
- Guardian server code still contains no NMS/CraftBukkit implementation dependency, implementation reflection, server Mixin, ProtocolLib/PacketEvents workaround, or private packet access;
- signed-release/server-authentication documentation continues to state the residual limits and explicitly rejects remote-attestation claims; and
- `IMPLEMENTATION_BRIDGES.md` remains empty of active bridges.

Phase 7 owns non-blocking release/UX polish discovered during live closeout: simplify the signing/key workflow, define public-release config evolution/backfill behavior, improve the ordinary player-facing server-auth mismatch message, and remove the cosmetic duplicate product name in normal logger output (`[Guardian] Guardian ...` / `[guardian]: Guardian ...`).

## Construction-environment note

The implementation sandbox exposed OpenJDK 21 only and could not retrieve the uncached Gradle 9.7.1 wrapper distribution, so the construction pass itself did not claim the required gate. That limitation is now superseded for verification purposes by the operator-executed Java 25.0.3 / Gradle 9.7.1 result above. Focused construction-time pure-Java smokes, `git diff --check`, and cumulative `git apply --check` remain supplementary evidence.

## Closeout result

All Phase 6 closeout conditions are satisfied:

1. **PASS:** final Java 25 / Gradle 9.7.1 gate — 355 tests, 0 failures, 0 errors, 11 documented symlink-privilege skips;
2. **PASS:** focused standalone signed-release/server-authentication/quarantine and Velocity-authoritative live checks;
3. **PASS:** final post-live security/privacy/code-quality review found no defect requiring additional Phase 6 implementation;
4. **PASS:** live results and final repository provenance are recorded here and in `PROVENANCE.md`; and
5. **PASS:** `PHASE_7_HANDOFF.md` is finalized from the verified closeout repository.

**Phase 6 is complete.**
