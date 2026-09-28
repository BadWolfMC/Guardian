# Phase 5 handoff — implementation candidate and closeout gate

> The current repository and `Guardian_Cerberus_Authoritative_Project_Plan.md` are authoritative. This handoff records the Phase 5 candidate state and verification required before Phase 6.

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

Phase 5 also adds strict Velocity operational configuration/data ownership, production assertion-secret provisioning, configurable handshake timing, explicit deployment diagnostics, atomic Velocity reload/files-only validation, `NORMAL`/`DEBUG` logging, bounded authority-owned inspection snapshots, authority-correct artifact administration, and generated rule-ID length hardening.

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

The source candidate implements the BRIDGE-003/004 retirement responsibilities, but those bridges MUST remain active until the final Java 25 / Gradle 9.7.1 gate and the focused live tests in `PHASE_5_VERIFICATION.md` pass.

This implementation sandbox had Java 21 and no cached Gradle 9.7.1 distribution; wrapper download was blocked by network/DNS. Do not record the Phase 5 candidate as green based on the retained Phase 4 XML.

After the gate/live matrix passes, retire BRIDGE-003 and BRIDGE-004 and begin Phase 6. Do not pull signed Cerberus identity, hostile-client resistance, or the Minecraft/Paper/Fabric 26.3 port backward into Phase 5.
