# Phase 7 handoff — operations, UX, and release hardening

**Active handoff.** Phase 6 is closed. The final Java 25 / Gradle 9.7.1 gate is green at 355 tests (0 failures, 0 errors, 11 documented Windows symlink-privilege skips), the focused signed-release/server-authentication/quarantine/Velocity live matrix passed, and the final post-live security/privacy/code-quality review found no Phase 6 blocker.

## Expected entering architecture

Phase 7 should inherit the verified `0.1.0-phase6` architecture without reopening security decisions casually:

- Guardian remains split into independent Admission and Protection domains.
- Guardian-Velocity is BadWolfMC's preferred network Admission authority; Guardian-Paper remains standalone-capable.
- Velocity-authority Paper remains assertion-only and never receives full Fabric manifests for commands/diagnostics.
- `/guardian` is Paper-local; `/guardianv` is Velocity/network-authoritative; there is no Paper ↔ Velocity command RPC.
- Bedrock trusted-origin classification precedes Java brand/Cerberus handling.
- active inspection is bounded, exact-connection-owned, memory-only, active-session-only, and distinguishes admission-time evidence from current runtime/catalog state.
- pre-login profile-provider failure is fail-closed when provider state could have selected a stricter profile.
- proxy assertions remain HMAC-based server-controlled infrastructure trust with replay/lifetime/UUID/session protection.
- official Cerberus release provenance uses optional Ed25519 public verification keys and is **not remote attestation**.
- optional Guardian → Cerberus Ed25519 challenge authentication protects manifest disclosure using player UUID + fresh nonce + capability + short time binding; it is also **not remote attestation**.
- no long-term shared secret is embedded in Cerberus.
- no NMS/CraftBukkit implementation reflection/server Mixins/packet-library workaround is part of Guardian.

## Phase 7 goal

Make the security-hardened project installable, understandable, supportable, and releasable by real administrators without weakening the Phase 6 boundaries.

Primary work remains the authoritative plan's Phase 7 scope:

- polish `/guardian` and `/guardianv` status/validate/reload/inspect UX and docs;
- finalize player-facing failure/help/download messaging;
- standalone Paper installation/deployment guide;
- Velocity network installation/deployment guide;
- Geyser/Floodgate guide;
- LuckPerms/profile guide, especially Paper-versus-Velocity permission-provider ownership and `/lp` versus `/lpv` troubleshooting;
- Guardian Protection guide;
- eZProtector configuration/permission migration guide;
- locale customization guide;
- consolidated security/threat-model documentation;
- clean-install and upgrade tests;
- CI/release workflow, release checksums, attribution/GPL material, and third-party dependency license/NOTICE audit; and
- final adversarial release-candidate audit.

## Phase 6 live-closeout evidence entering Phase 7

The verified Phase 6 repository adds concrete production evidence beyond the automated matrix:

- standalone Paper accepted a real 166-entry Fabric client only after authenticated Guardian challenge verification and trusted signed Cerberus release verification;
- bounded PLAY quarantine prevented representative movement/world/container interaction escape and ended in `CERBERUS_TIMEOUT` when the client deliberately suppressed its response;
- replacing the Guardian server-authentication private key with an untrusted identity caused stock pinned Cerberus to withhold its manifest and the connection to fail closed; restoring the original key restored `CERBERUS_VERIFIED` immediately;
- Guardian-Velocity performed one authoritative evaluation, `/guardianv inspect` retained the bounded authoritative view, backend switching reused the existing grant, and backend `/guardian inspect` remained assertion-only; and
- the release-signing smoke caught and closed two packaging/runtime portability defects before release: Loom 26.2 uses `jar` rather than `remapJar`, and JDK ZIPFS requires provider-compatible channel options for embedded trust-anchor reads.

## Phase 7 UX/release carry-forwards discovered during closeout

These are release-polish tasks, not Phase 6 security bridges:

- **Signing/key workflow:** preserve the safety requirement for explicit output paths and separate key domains, but replace or wrap the raw Gradle/OpenSSL choreography with a clearer release-manager workflow where practical.
- **Configuration evolution:** first-start resource copying intentionally does not merge newly introduced keys into an existing `config.yml`/`policy.yml`. That was acceptable while Guardian was unreleased, but Phase 7 must define and test the public-release upgrade/backfill/migration contract before 1.0.
- **Server-authentication mismatch UX:** the client log explicitly records an unauthenticated/untrusted Guardian challenge and manifest nondisclosure, while the ordinary player-facing disconnect currently arrives later as `CERBERUS_TIMEOUT`. Improve that presentation if it can be done cleanly without weakening the privacy/fail-closed behavior.
- **Normal log prefix:** Paper/Velocity already provide plugin logger prefixes, so the message-level `Guardian ` prefix produces cosmetic `[Guardian] Guardian ...` / `[guardian]: Guardian ...` duplication. Clean this up as part of operations UX polish.

## New Phase 6 operational material Phase 7 must document clearly

### Three independent key domains

Do not conflate:

1. `proxy-assertion.key` — symmetric Velocity ↔ Paper infrastructure assertion key;
2. `guardian-server-auth.key` — Guardian Ed25519 private key used to authenticate player-bound challenges to pinned Cerberus clients; and
3. Cerberus release-signing private key — offline Ed25519 key used only to sign official Cerberus release identity metadata.

`proxy-assertion.key` and `guardian-server-auth.key` are explicitly Git-ignored conventional private files. The release-signing key should not live in the repository/plugin data tree at all.

### Cerberus signed release workflow

Document how administrators/release managers:

- generate/secure a release-signing key;
- generate a Guardian server identity;
- embed one or more public Guardian server-auth keys into the signed Cerberus release;
- configure Guardian policy with one or more public Cerberus release-verification keys;
- rotate Guardian server identity using overlapping old+new public keys; and
- distinguish exact artifact signing from hostile-runtime attestation.

### Development clients

Document that `DIRECTORY` and `MIXED_OR_UNKNOWN` are explicit coarse origin types, not fake archives. Production policy may reject them; a development/admin profile may deliberately allow them. Guardian does not invent a directory-tree SHA-256.

### Active inspection

Explain that ordinary inspect output intentionally lists at most 64 policy-addressable mods and may report omitted entries. Full manifests are not retained after Admission. A snapshot marked pre-reload describes admission-time evidence; current artifact-catalog labels may differ after an administrator reload.

## Phase 7 guardrails

Do not undo Phase 6 hardening for convenience:

- no persistent historical manifest database;
- no backend forwarding of full Fabric manifests to make `/guardian` mirror `/guardianv`;
- no second Velocity policy evaluator/schema;
- no embedded client shared secret;
- no “tamper-proof/verified runtime/remote attestation” marketing language;
- no automatic fallback to a weaker default profile after provider failure;
- no following symlink/admin-file convenience that bypasses stable-file validation; and
- no Minecraft 26.3 compatibility branches. The 26.3 port remains Phase 8.

## Phase 7 entry gate

All entry conditions are satisfied:

- **PASS:** Java 25 / Gradle 9.7.1 full gate — 355 tests, 0 failures, 0 errors, 11 documented symlink-privilege skips;
- **PASS:** Phase 6 focused live verification complete;
- **PASS:** final test totals and artifact hashes recorded;
- **PASS:** Phase 6 live verification/provenance finalized; and
- **PASS:** no active implementation bridge/security mitigation is being carried implicitly.

Phase 7 may proceed from this repository.
