# Phase 5 verification

## Required automated gate

Run from the repository root with Java 25 and the repository Gradle 9.7.1 wrapper:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Expected prerequisites:

- Java reports 25;
- Gradle reports 9.7.1 from the wrapper;
- every test suite completes with zero failures/errors/skips;
- Paper, Velocity, and Cerberus artifacts build successfully.

This implementation environment exposed OpenJDK 21 only and could not retrieve the uncached Gradle 9.7.1 distribution because outbound DNS/network access to `services.gradle.org` was unavailable. Therefore this document does **not** claim a new Phase 5 green Gradle result. The final gate must be run locally/CI before bridge retirement.

## Automated coverage added by Phase 5

The candidate adds coverage for:

- `NORMAL`/`DEBUG` parsing;
- production assertion key environment/file sources, bounds, traversal rejection, exact key length, defensive copies, and fingerprints;
- bounded immutable inspection stores and policy-addressable mod filtering;
- minimal immutable Velocity grants;
- strict Velocity deployment/config parsing and timing validation;
- complete Velocity runtime candidate load, failed-reload preservation, and non-activating validation;
- Paper Velocity-mode assertion-secret requirements and rejection of explicitly malformed optional operational settings;
- final Paper/Velocity command roots and independent permission namespaces;
- backend Paper inspection privacy and authority-correct artifact scanning;
- no retained feasibility secret/name in production adapters;
- stale asynchronous Velocity Admission completions cannot repopulate state after disconnect/session replacement;
- locale-backed administrator operational state labels used by the new command surfaces;
- generated policy rule IDs for maximum-length valid mod IDs; and
- existing Phase 0–4 transport/policy/Bedrock regressions through the complete repository suite.

## Focused static/security checks

Before closeout, confirm:

```powershell
git grep -n "GUARDIAN_PHASE0B_PROXY_SECRET" -- "guardian-*/src/main/**"
git grep -n "Phase 0B" -- "guardian-*/src/main/**"
git grep -n "guardian.artifacts.scan" -- "guardian-*/src/main/**"
```

All three should return no production-source matches.

Also inspect that:

- no secret value is logged;
- assertion file configuration cannot escape the Guardian data directory or follow a symlinked configured path;
- full manifests are absent from proxy assertion payload construction;
- backend Paper does not load proxy-owned policy in Velocity mode;
- client Guardian channels remain consumed at Velocity;
- mutable Velocity Admission sessions are discarded at terminal decisions while only minimal grant + immutable inspection data remain;
- disconnect removes grant/snapshot/session data and stale asynchronous completions cannot recreate it;
- untrusted command/player/mod values use unparsed MiniMessage placeholders; and
- no NMS, implementation reflection, server Mixins, or packet-library workaround was introduced.

## Minimal live tests worth performing

Do not repeat the full Phase 4 matrix. The useful Phase 5 live checks are:

1. **Velocity authoritative Java Fabric/Cerberus + cross-backend inspect.** Connect a normal approved Fabric/Cerberus client. Confirm one concise Velocity NORMAL summary, no duplicate Paper success lifecycle, then place moderator and target on different backends and run `/guardianv inspect <target>`. Verify backend, profile, Cerberus details, decision, policy-addressable mods/Loader count, hash state, and Geyser/Floodgate evidence are correct. Run `/guardian inspect <target>` on the target's Paper backend and confirm it exposes only trusted proxy/backend evidence and points to `/guardianv` rather than showing a manifest.
2. **Atomic host-local reload/validation.** Introduce a deliberately invalid Paper-local edit and verify `/guardian validate`/`reload` reject it while active Paper state remains unchanged; restore it. Repeat with a deliberately invalid Velocity `admission/policy.yml` or `config.yml` and `/guardianv validate`/`reload`, confirming the active network policy remains unchanged. Then make one harmless valid change (for example DEBUG ↔ NORMAL) and confirm only the intended host reloads.
3. **Artifact authority.** In Velocity mode, run `/guardian artifacts scan` on a backend and confirm it refuses without mutating network-authoritative state. Run `/guardianv artifacts scan` with a known test/import JAR and confirm the authoritative catalog workflow executes normally.
4. **Assertion provisioning/rotation sanity.** Start all hosts with the same production-named key and compare the non-secret fingerprint from `/guardian status` and `/guardianv status`. Deliberately give one test backend a different key and confirm proxy assertion verification fails prominently. Restore the matching key and confirm admission works again. Do not perform a production key rotation mid-traffic; protocol v1 has one active key per host.
5. **Disconnect cleanup.** Inspect an online target successfully, disconnect the target, then run `/guardianv inspect <target>` and confirm Guardian reports no active inspection data rather than historical state.

A single real Bedrock connection is optional rather than mandatory if the complete Phase 4 Bedrock live closeout was performed against the same networking stack; the full repository regression suite plus Phase 4 evidence protects the Cerberus-free/policy-controlled Bedrock invariant. If desired, one Bedrock connection is a useful smoke test after proxy config changes, but another allow/deny matrix is unnecessary.

## Bridge closeout rule

Only after the automated gate and focused live checks pass:

- move BRIDGE-003 and BRIDGE-004 from active to retired;
- record the exact final repository/archive/commit and test totals in `PROVENANCE.md`;
- update this document with the operator/CI results; and
- proceed to Phase 6 adversarial/security hardening.
