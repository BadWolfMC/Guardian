# Phase 6 verification

## Current candidate status

**AUTOMATED GATE PASS; FOCUSED LIVE CLOSEOUT PENDING.** The complete Java 25 / Gradle 9.7.1 gate is green on the operator-supplied Phase 6 repository. Phase 6 remains open only for the focused platform-level checks and final post-live review below.

The current source inventory and executed JUnit total are both 354 tests:

```text
guardian-core        117
guardian-paper        91
guardian-protection   20
guardian-protocol     50
guardian-velocity     58
cerberus-fabric       18
```

Executed gate on 2026-09-30:

```text
guardian-core        117 tests, 0 failures, 0 errors, 4 skipped
guardian-paper        91 tests, 0 failures, 0 errors, 4 skipped
guardian-protection   20 tests, 0 failures, 0 errors, 0 skipped
guardian-protocol     50 tests, 0 failures, 0 errors, 0 skipped
guardian-velocity     58 tests, 0 failures, 0 errors, 3 skipped
cerberus-fabric       18 tests, 0 failures, 0 errors, 0 skipped
---------------------------------------------------------------
TOTAL                354 tests, 0 failures, 0 errors, 11 skipped
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

**Result: PASS.** The operator ran the complete command with Oracle JDK 25.0.3 and Gradle 9.7.1. The JUnit XML reports 354 tests, zero failures, zero errors, and the 11 documented Windows symlink-privilege skips above.

After the build, inspect the distributables rather than only trusting compilation:

```powershell
Get-ChildItem .\guardian-paper\build\libs\
Get-ChildItem .\guardian-velocity\build\libs\
Get-ChildItem .\cerberus-fabric\build\libs\
```

No private key (`proxy-assertion.key`, `guardian-server-auth.key`, release-signing private key) or administrator-local file should be present inside the built JARs.

**Artifact inspection: PASS.** Built distributables are present and no private-key filename/PEM/private-key entry was found:

```text
guardian-paper-0.1.0-phase6.jar     SHA-256 5c932b59ccfe92e32f3e65ffafe647a9761e2943b645b87d81c73aee819a1863
guardian-velocity-0.1.0-phase6.jar  SHA-256 2c3b471a71dad738eaebd401ce27a31b9b264eb15714d0aff12d869268de9df8
cerberus-fabric-0.1.0-phase6.jar    SHA-256 50db3cb5a7fdfc09f57d664f547791175d5e1e451116317dc6f6e0340b38de12
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

### 1. Standalone Paper — quarantine + authenticated signed Cerberus happy path

Build/use an official signed Cerberus JAR carrying the public key for the local Guardian server identity. Configure standalone Paper to require server authentication and, for this smoke, signed Cerberus release identity.

Expected:

- Cerberus accepts only the player-bound signed challenge;
- manifest collection occurs after challenge authentication;
- the real manifest evaluates normally;
- quarantine releases only after `CERBERUS_VERIFIED`/policy allow; and
- `NORMAL` logging remains one concise authoritative Admission line rather than a manifest dump.

### 2. Standalone Paper — suppressed response/quarantine behavior

Use the existing Cerberus diagnostic suppression flag for one connection and, during the bounded handshake window, attempt a small representative set of actions such as movement, a command, an inventory/container interaction, and a block/entity interaction.

Expected: those actions do not meaningfully escape quarantine and the connection ends in the configured timeout path. There is no need to manually exercise every event family covered by automated architecture tests.

### 3. Guardian server-authentication mismatch

Run stock/pinned Cerberus against a Guardian authority signing with an untrusted/different server-auth private key.

Expected: Cerberus does not disclose its manifest. The connection ultimately fails closed; client diagnostics should make the rejected Guardian authentication understandable. This verifies the privacy boundary in the real Fabric networking lifecycle.

### 4. Velocity-authoritative happy path + backend assertion regression

Run one normal Fabric/Cerberus connection through Guardian-Velocity to Guardian-Paper with server authentication enabled at the proxy.

Expected:

- the proxy authenticates/challenges/evaluates once;
- Paper receives only the trusted Admission assertion and does not receive/re-evaluate the full Fabric manifest;
- backend switching reuses the proxy Admission grant; and
- `/guardianv inspect <player>` remains authoritative while `/guardian inspect <player>` on a backend remains assertion-only.

A repeat of the entire Phase 4 Bedrock allow/deny matrix is unnecessary if the same Geyser/Floodgate stack is unchanged. One optional Bedrock smoke is reasonable after final packaging, but Bedrock remains Cerberus-free by architecture/regression coverage.

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

## Security/privacy review checklist before closeout

Confirm after the green gate/live smokes:

- no complete manifest is retained after successful Admission beyond the bounded active projection;
- disconnect removes active inspection and no historical manifest store exists;
- `DEBUG` does not dump complete manifests;
- malformed client metadata cannot forge extra Guardian log lines or MiniMessage formatting;
- Bedrock remains classified before Java/Cerberus handling;
- Velocity-authority Paper remains assertion-only;
- failed validate/reload leaves active runtime unchanged;
- no private key bytes appear in status, logs, generated examples, JAR resources, or support output;
- no NMS/CraftBukkit implementation reflection/server Mixin/packet-library workaround has entered Guardian; and
- signed-release/server-authentication documentation still avoids remote-attestation claims.

## Construction-environment note

The implementation sandbox exposed OpenJDK 21 only and could not retrieve the uncached Gradle 9.7.1 wrapper distribution, so the construction pass itself did not claim the required gate. That limitation is now superseded for verification purposes by the operator-executed Java 25.0.3 / Gradle 9.7.1 result above. Focused construction-time pure-Java smokes, `git diff --check`, and cumulative `git apply --check` remain supplementary evidence.

## Closeout rule

Phase 6 closes only after:

1. ~~the complete Java 25 / Gradle 9.7.1 gate is green~~ — **PASS: 354 tests, 0 failures, 0 errors, 11 documented symlink-privilege skips**;
2. the focused live checks above pass or any omitted check is explicitly justified by equivalent evidence;
3. the final post-live security/privacy/code-quality review finds no new defect requiring implementation;
4. actual live results are recorded here and in `PROVENANCE.md`; and
5. the Phase 7 handoff is finalized from that verified repository.

A green automated suite alone is not sufficient to change this document to `PASS`.
