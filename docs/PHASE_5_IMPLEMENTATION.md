# Phase 5 implementation — Guardian-Velocity productionization and authority-aware operations

## Baseline and scope

Phase 5 starts from `0.1.0-phase4` (141 retained green tests) with BRIDGE-005 retired and BRIDGE-003/BRIDGE-004 as the only active implementation bridges. The shared Phase 3 policy engine and Phase 4 Bedrock semantics are preserved; this phase productionizes the hosts and administrator surfaces rather than creating new Admission policy semantics.

The Phase 5 version is `0.1.0-phase5`.

## Command authority

Guardian now deliberately exposes different roots:

```text
Paper:    /guardian
Velocity: /guardianv
```

Paper permissions:

```text
guardian.command.status
guardian.command.validate
guardian.command.reload
guardian.command.inspect
guardian.command.artifacts.scan
```

Velocity permissions:

```text
guardian.velocity.command.status
guardian.velocity.command.validate
guardian.velocity.command.reload
guardian.velocity.command.inspect
guardian.velocity.command.artifacts.scan
```

The old unreleased `guardian.artifacts.scan` node is removed without an alias. Guardian-Velocity always claims `/guardianv` and performs subcommand permission checks internally so permission denial cannot turn the network-authoritative administrative root into a backend-forwarded command.

No Paper ↔ Velocity command RPC exists. `/guardianv inspect <player>` executes directly at the proxy and uses Velocity's connected-player/current-server state, so moderator and target may be on different backends.

## Active inspection lifecycle

Phase 5 introduces a platform-neutral immutable `ActiveInspectionSnapshot` and bounded in-memory `ActiveInspectionStore`.

The authoritative snapshot includes player identity, backend, classification, observed brand, resolved profile/source, Cerberus presence/version/protocol when applicable, final decision, Bedrock evidence, and the already-bounded canonical manifest. Ordinary inspection lists only policy-addressable/top-level mods plus the complete Loader-known count.

Mutable successful Admission sessions are not retained for commands:

- standalone Paper creates an authoritative snapshot and discards the mutable Admission session normally;
- Velocity creates an authoritative snapshot plus a minimal immutable `VelocityAdmissionGrant` containing only the proxy-session ID/origin required for subsequent backend assertions, then discards the mutable Admission session;
- backend Paper in Velocity mode retains only trusted-assertion/backend sanity evidence, never the Fabric manifest;
- all inspection/grant state is removed on disconnect; and
- asynchronous Velocity Admission completions are session-identity guarded, so a future that finishes after disconnect or session replacement cannot recreate stale admission/grant/inspection state.

## Velocity production configuration

Guardian-Velocity now owns a strict proxy-local `config.yml` beneath its injected Velocity data directory. It validates:

- `schema-version: 1`;
- explicit `deployment.authority: velocity`;
- locale;
- `logging.level: NORMAL|DEBUG`; and
- bounded `admission.handshake-timeout-seconds`.

It continues to consume the exact same `policy.yml`, `artifacts.yml`, and locale format as the shared architecture requires.

Velocity's runtime manager parses and validates the complete candidate (operational config, policy/catalog, locale), verifies the artifact catalog did not change concurrently while policy was being resolved, then atomically swaps one immutable runtime snapshot. `validate` builds the same candidate without activating it. Failed or internally inconsistent candidate loading leaves the previous runtime active.

## Proxy assertion key lifecycle

The feasibility-era `GUARDIAN_PHASE0B_PROXY_SECRET` environment variable is removed from production code. Phase 5 uses one familiar file-based provisioning model:

1. Guardian-Velocity generates `proxy-assertion.key` automatically on first startup.
2. The file contains Base64 for exactly 32 random key bytes.
3. The administrator copies that file unchanged to `plugins/Guardian/proxy-assertion.key` on every Paper backend configured with `admission.authority: velocity`.
4. Standalone Guardian-Paper does not require or generate the file.

The key file is intentionally **not** configured in `config.yml`; there is one fixed conventional location on each host. This removes an unnecessary secret-source/path configuration surface and avoids requiring administrators to place secrets in JVM flags, startup scripts, or environment variables.

Velocity creates the key with `CREATE_NEW` semantics and never overwrites an existing regular file. On POSIX filesystems Guardian attempts owner-read/write-only permissions; on Windows and other non-POSIX filesystems the host's inherited ACLs apply. Guardian never logs the key material. Both hosts perform bounded reads, reject symlinked key paths, require exact 32-byte Base64 key material, and expose only a short SHA-256-derived non-secret fingerprint through status diagnostics.

A missing key on a Velocity-authority Paper backend is an **external provisioning failure**, not a corrupt `config.yml`. Guardian-Paper therefore fails closed without backing up/replacing the config or silently changing Admission authority. The error tells the administrator to copy `proxy-assertion.key` from Guardian-Velocity.

Protocol v1 accepts one active shared assertion key per host. Rotation is coordinated: during a maintenance window, stop/cordon backend switching, replace or remove the Velocity key so a new one is generated, copy the new file to every Velocity-authority Paper backend, verify matching fingerprints, then resume normal traffic. Existing live proxy sessions should not be assumed to survive a key mismatch during rotation.

Treat `proxy-assertion.key` as private infrastructure key material. Do not commit it to source control, publish it, paste it into support logs, or distribute it with Guardian.

## Logging

Both hosts expose only `NORMAL` and `DEBUG`. Administrator-visible operational state labels used by the command surfaces are rendered through Guardian's locale catalog rather than embedded Java strings.

`NORMAL` keeps security/configuration anomalies visible but collapses successful Admission into approximately one authoritative summary. On a Velocity network, Guardian-Velocity owns the normal success summary and backend Paper does not duplicate the successful assertion lifecycle.

`DEBUG` restores lifecycle detail (classification/profile selection, channel/challenge/response/assertion/backend sanity/timing) without dumping complete manifests. Staff request player-specific mod information through `inspect`.

## Paper ownership behavior

Paper `config.yml` gains `server-name` and `logging.level`; proxy assertion key material lives outside YAML in the fixed `proxy-assertion.key` file. Explicitly present optional operational settings are type/format validated rather than silently treated as absent when malformed.

In standalone authority mode, `/guardian` validates/reloads the complete Paper-owned candidate, exposes authoritative inspection, and permits artifact scanning.

In Velocity authority mode, Paper does not load/reinterpret proxy-owned Admission policy. `/guardian inspect` reports only backend trusted-assertion evidence and Floodgate sanity, and `/guardian artifacts scan` explicitly refuses and points to `/guardianv artifacts scan`.

Existing Protection reload/enablement/visibility reconciliation remains Paper-local and independent from Admission.

## Artifact administration hardening

Generated import rule IDs no longer blindly use `allow-<mod-id>`. If that deterministic form would exceed Guardian's 64-character rule-ID bound, the generator shortens the prefix and appends a deterministic SHA-256-derived suffix while retaining valid rule-ID syntax. This keeps generated fragments pasteable for every valid maximum-length Fabric mod ID.

## Maintainability boundaries

Command routing, operational config, secret handling, and inspection storage are separate focused components. The platform adapters retain lifecycle/transport responsibilities; neither adapter becomes the storage model or command router.

Production source no longer uses Phase 0B operational naming. Historical phase documents still describe Phase 0B as history and are intentionally not rewritten.

## Phase 5 closeout status

Phase 5 is complete. The final Java 25 / Gradle 9.7.1 gate is green at 178 tests with zero failures/errors/skips (Core 64, Paper 44, Protection 20, Protocol 21, Velocity 29). The focused live matrix passed, including normal Velocity Fabric/Cerberus Admission, Paper-local versus proxy-authoritative inspection, atomic host-local validation/reload, artifact authority, generated assertion-key provisioning and mismatch recovery, disconnect cleanup, and in-game `/guardianv` execution.

BRIDGE-003 and BRIDGE-004 satisfy their objective retirement conditions and are retired together. No active implementation bridge enters Phase 6.

One operator lesson is now explicit: Paper and Velocity command permissions are checked by their respective platform permission providers. In an isolated LuckPerms lab, Paper `/lp` changes do not grant Velocity `/guardianv` permissions; use the Velocity LuckPerms instance (for example `/lpv`) or shared LuckPerms storage. This is a deployment distinction, not a Guardian command-routing fallback.
