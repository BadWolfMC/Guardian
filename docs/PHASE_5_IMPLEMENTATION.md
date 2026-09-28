# Phase 5 implementation — Guardian-Velocity productionization and authority-aware operations

## Baseline and scope

Phase 5 starts from `0.1.0-phase4` (141 retained green tests) with BRIDGE-005 retired and BRIDGE-003/BRIDGE-004 as the only active implementation bridges. The shared Phase 3 policy engine and Phase 4 Bedrock semantics are preserved; this phase productionizes the hosts and administrator surfaces rather than creating new Admission policy semantics.

The candidate version is `0.1.0-phase5`.

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
- `logging.level: NORMAL|DEBUG`;
- bounded `admission.handshake-timeout-seconds`; and
- production proxy assertion secret provisioning.

It continues to consume the exact same `admission/policy.yml`, `artifacts.yml`, and locale format as the shared architecture requires.

Velocity's runtime manager parses and validates the complete candidate (operational config, policy/catalog, locale), verifies the artifact catalog did not change concurrently while policy was being resolved, then atomically swaps one immutable runtime snapshot. `validate` builds the same candidate without activating it. Failed or internally inconsistent candidate loading leaves the previous runtime active.

## Proxy assertion secret lifecycle

The feasibility-era `GUARDIAN_PHASE0B_PROXY_SECRET` name is removed from production code. The default production environment variable is:

```text
GUARDIAN_PROXY_ASSERTION_SECRET
```

Both Paper and Velocity support:

```yaml
proxy-assertion:
  secret-source: ENVIRONMENT   # or FILE
  environment-variable: GUARDIAN_PROXY_ASSERTION_SECRET
  file: proxy-assertion.secret
```

The configured value is Base64 representing exactly 32 secret bytes. `FILE` paths must be relative to the Guardian data directory, may not escape it or traverse symbolic links, and are bounded before reading. Guardian never logs key material. Status exposes only the configured source and a short SHA-256-derived non-secret fingerprint so administrators can confirm that hosts loaded the same key.

Protocol v1 accepts one active shared assertion key per host. Rotation is therefore coordinated rather than magically zero-downtime: use a maintenance window/no server switching, provision the same replacement on the proxy/backends, validate each host, and reload/restart the affected hosts in a tightly controlled sequence. `FILE` source can be picked up by Guardian reload; an `ENVIRONMENT` change normally requires restarting the process/service because the running process environment is not mutable by Guardian. Dual-key overlap is intentionally deferred rather than expanding the assertion format in Phase 5.

## Logging

Both hosts expose only `NORMAL` and `DEBUG`. Administrator-visible operational state labels used by the command surfaces are rendered through Guardian's locale catalog rather than embedded Java strings.

`NORMAL` keeps security/configuration anomalies visible but collapses successful Admission into approximately one authoritative summary. On a Velocity network, Guardian-Velocity owns the normal success summary and backend Paper does not duplicate the successful assertion lifecycle.

`DEBUG` restores lifecycle detail (classification/profile selection, channel/challenge/response/assertion/backend sanity/timing) without dumping complete manifests. Staff request player-specific mod information through `inspect`.

## Paper ownership behavior

Paper `config.yml` gains `server-name`, `logging.level`, and production assertion secret configuration. Explicitly present optional operational settings are type/format validated rather than silently treated as absent when malformed.

In standalone authority mode, `/guardian` validates/reloads the complete Paper-owned candidate, exposes authoritative inspection, and permits artifact scanning.

In Velocity authority mode, Paper does not load/reinterpret proxy-owned Admission policy. `/guardian inspect` reports only backend trusted-assertion evidence and Floodgate sanity, and `/guardian artifacts scan` explicitly refuses and points to `/guardianv artifacts scan`.

Existing Protection reload/enablement/visibility reconciliation remains Paper-local and independent from Admission.

## Artifact administration hardening

Generated import rule IDs no longer blindly use `allow-<mod-id>`. If that deterministic form would exceed Guardian's 64-character rule-ID bound, the generator shortens the prefix and appends a deterministic SHA-256-derived suffix while retaining valid rule-ID syntax. This keeps generated fragments pasteable for every valid maximum-length Fabric mod ID.

## Maintainability boundaries

Command routing, operational config, secret handling, and inspection storage are separate focused components. The platform adapters retain lifecycle/transport responsibilities; neither adapter becomes the storage model or command router.

Production source no longer uses Phase 0B operational naming. Historical phase documents still describe Phase 0B as history and are intentionally not rewritten.

## Phase 5 bridge status

The source candidate implements the objective BRIDGE-003 and BRIDGE-004 productionization responsibilities. They remain listed as active until the Java 25 / Gradle 9.7.1 gate and focused live Phase 5 verification pass; at that point they should be retired together rather than closed merely because the code exists.
