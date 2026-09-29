# Phase 5 handoff — completed

> Phase 5 is complete. This file is retained as the historical Phase 5 requirement/handoff record; `PHASE_6_HANDOFF.md` is the active next-phase handoff.

## Entering baseline

Phase 4 is complete at `0.1.0-phase4` with 141 retained green tests and BRIDGE-005 retired. Only BRIDGE-003 and BRIDGE-004 enter Phase 5.

## Phase 5 candidate

The repository now targets `0.1.0-phase5` and implements the deliberate host distinction:

```text
Guardian-Paper
    /guardian
        Paper-local administration
        standalone authoritative Admission + Protection
        backend verifier diagnostics in Velocity mode

Guardian-Velocity
    /guardianv
        network-authoritative Admission administration
        shared policy/catalog operations
        current-player authoritative inspection
```

The command roots are intentionally different. Do not register `/guardian` at Velocity, do not make Paper secretly proxy administrator requests to Velocity, and do not introduce a command RPC merely to retrieve remote Admission state.

Phase 5 also adds strict Velocity operational configuration/data ownership, production assertion-key provisioning, configurable handshake timing, explicit deployment diagnostics, atomic Velocity reload/files-only validation, `NORMAL`/`DEBUG` logging, bounded authority-owned inspection snapshots, authority-correct artifact administration, and generated rule-ID length hardening.

The shared Phase 3 policy engine and Phase 4 Geyser/Floodgate semantics remain unchanged. Paper behind Velocity remains assertion-only for network Admission and receives no full Fabric manifest.

## Permissions

Paper:

```text
guardian.command.status
guardian.command.validate
guardian.command.reload
guardian.command.inspect
guardian.command.artifacts.scan
```

Velocity:

```text
guardian.velocity.command.status
guardian.velocity.command.validate
guardian.velocity.command.reload
guardian.velocity.command.inspect
guardian.velocity.command.artifacts.scan
```

No compatibility alias is retained for the unreleased `guardian.artifacts.scan` node.

## Closeout status

Phase 5 closed on 2026-09-29. The final Java 25 / Gradle 9.7.1 gate is green at **178 tests, 0 failures, 0 errors, 0 skipped** (Core 64, Paper 44, Protection 20, Protocol 21, Velocity 29).

The focused live matrix passed:

- normal Guardian-Velocity Fabric/Cerberus Admission produced one concise authoritative `NORMAL` summary and backend Paper remained quiet on success;
- Paper `/guardian status` and limited backend `/guardian inspect` correctly reported Velocity authority without receiving the full Fabric manifest;
- `/guardianv` administration worked in-game once its `guardian.velocity.command.*` permissions were granted through the Velocity permission provider;
- host-local validation/reload retained prior runtime state on invalid candidates and applied valid local changes only to the intended host;
- Paper refused authoritative artifact mutation in Velocity mode while `/guardianv artifacts scan` owned the proxy catalog workflow;
- Velocity-generated `proxy-assertion.key` provisioning, matching fingerprints, deliberate mismatch rejection, and restored-key recovery behaved as intended; and
- active inspection data disappeared on disconnect rather than becoming historical manifest storage.

The local permission troubleshooting also confirmed an important deployment fact: Paper and Velocity permissions are resolved by separate platform permission providers. With isolated LuckPerms stores, grant proxy permissions through the Velocity LuckPerms instance (for example `/lpv`); production networks using shared LuckPerms storage may not make this boundary obvious.

These results retire BRIDGE-003 and BRIDGE-004. There are no active implementation bridges entering Phase 6. Continue with `PHASE_6_HANDOFF.md`; do not pull the Minecraft/Paper/Fabric 26.3 port forward from Phase 8.
