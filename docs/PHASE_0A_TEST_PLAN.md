# Phase 0A — Test Series 5: timing/config regression

> **Historical verification record:** this document describes the Phase 0A prototype and its then-current test switches/configuration names. It is retained for provenance and is not an operator procedure for Guardian/Cerberus `1.0.0`. Use the current install, Admission, production-runbook, and release-process documentation for supported configuration and testing.

Phase 0A's hybrid transport and structured outcomes are already proven. Test Series 5 is a focused regression for the timing hardening and default configuration packaging.

## Build

Run:

```powershell
.\gradlew.bat clean test :guardian-paper:jar :cerberus-fabric:build
```

Expected:

- build succeeds under Java 25;
- `GuardianPaperResourcesTest` confirms both `plugin.yml` and `config.yml` are present;
- no Gradle deprecation warning should originate from the two project `processResources` blocks.

## Normal Fabric + Cerberus

Expected sequence:

1. CONFIGURATION presence is accepted.
2. CONFIGURATION gate passes.
3. PLAY quarantine activates.
4. PLAY presence arrives.
5. If `guardian:challenge` is not yet visible, Guardian logs a bounded wait.
6. Guardian sends the nonce challenge as soon as the channel appears.
7. Cerberus responds.
8. `ALLOW / CERBERUS_VERIFIED`.
9. Quarantine releases.

The wait is a ceiling, not an added delay. If the channel is immediately visible, the challenge should be sent immediately.

## Timeout regression

Launch Cerberus with:

```text
-Dcerberus.phase0a.suppressResponse=true
```

Expected:

- the player remains quarantined;
- after `phase0.handshake-timeout-seconds`, Guardian returns `CERBERUS_TIMEOUT`;
- the player is disconnected with the timeout-specific message.

## Configuration values

Defaults are in `guardian-paper/src/main/resources/config.yml`:

```yaml
phase0:
  handshake-timeout-seconds: 10
  challenge-channel-wait-ticks: 40
```

For the feasibility spike:

- timeout must be 1-60 seconds;
- channel wait must be at least 1 tick;
- channel wait may not exceed the total handshake timeout in ticks;
- invalid values fall back to safe defaults with a startup warning.

Exact production defaults remain a later hardening decision.
