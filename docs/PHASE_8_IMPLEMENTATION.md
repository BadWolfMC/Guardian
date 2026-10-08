# Phase 8 implementation — Minecraft/Paper/Fabric 26.3 port

**Status:** implementation candidate; Java 25 automated gate and focused live verification remain open.

## Purpose

Phase 8 moves the maintained Guardian/Cerberus line from Minecraft/Paper/Fabric 26.2 to 26.3 without changing the established Admission, Protection, protocol, policy, privacy, or release-security architecture. The stable 1.0.1/26.2 release remains the frozen prior release line; the Phase 8 development/release line is 1.0.2 and targets 26.3 only.

No dual-version runtime probing, reflection, compatibility adapter, protocol branch, or configuration migration is introduced merely to keep one source tree running on both 26.2 and 26.3.

## Entering baseline

The operator-supplied Phase 8 archive is `Guardian(20261006-225143).zip`, SHA-256:

```text
0c8a43e7ab7c74a2b7ee6b881eab8bee2a1fbf14860a33f49596e63ed40e799d
```

The archive is internally consistent with a finalized 1.0.1/26.2 baseline:

- root source default: `1.0.1`;
- retained `release-final/` version: `1.0.1`;
- release provenance source commit: `ce03f99d2f33a3256268d7814443c596533f75bb`;
- release workflow run/attempt: `37480780048` / `1`;
- `release-final/SHA256SUMS.txt` re-verifies completely;
- retained Gradle XML reports: **378 tests, 0 failures, 0 errors, 11 documented skips**.

The Phase 8 handoff's earlier `1.0.0` publication prerequisite is retained as historical process evidence. The operator has confirmed that the stable 1.0.0/26.2 release was published and retained. The supplied development archive itself contains the later finalized 1.0.1 release set and does not independently prove the external publication event; Phase 8 does not require copying historical 1.0.0 binaries into the source tree.

## Compatibility review

| Component | 26.2 baseline | Phase 8 target | Guardian API surface reviewed | Assessment |
|---|---|---|---|---|
| Paper API | `26.2.build.+` | `26.3.build.+` | configuration/login connection APIs, plugin messaging, validation, quarantine events, connection identity, command interception/visibility, Adventure, lifecycle | Public API surface remains available; no source adaptation identified before compile/live verification. |
| Minecraft | `26.2` | `26.3` | Cerberus mapped public networking/payload classes | Target update required; no Guardian protocol redesign identified. |
| Fabric Loader | `0.19.5` | `0.19.5` | `FabricLoader`, `ModContainer`, origin and containment APIs | Retained. Loader origin/containment contract used by canonical manifest collection remains available. |
| Fabric API | `0.161.0+26.2` | `0.162.0+26.3` | CONFIGURATION and PLAY client networking, payload registration | 26.3 artifact available; published 26.3 API removals do not target Guardian's networking surfaces. |
| Fabric Loom | `1.17.21` | `1.18.2` | build/remap/JAR tooling | Build-tool target update only. |
| Velocity API | `4.2.1-SNAPSHOT` | unchanged | awaited configuration, plugin messaging, session lifecycle, backend switching, proxy commands | Current official dependency remains `4.2.1-SNAPSHOT`; no source adaptation identified. |
| Geyser API | `2.11.2-SNAPSHOT` | unchanged | supported Bedrock connection lookup/classification | Current official dependency unchanged. |
| Floodgate API | `2.2.5-SNAPSHOT` | unchanged | `FloodgateApi` supported Bedrock classification | Current official dependency unchanged. |
| LuckPerms API | `5.5` | unchanged | asynchronous user/profile resolution | Current API release remains 5.5. |
| Gradle wrapper | `9.7.1` | unchanged | repository build/release tooling | Retained to minimize unrelated build churn. |
| Java | `25` | unchanged | all modules/release tooling | Paper 26.3 continues to document Java 25. |

### Paper review

Guardian-Paper currently relies on supported public Paper/Bukkit surfaces including `PlayerConfigurationConnection`, `PlayerConnection`, `PlayerConnectionValidateLoginEvent`, `AsyncPlayerConnectionConfigureEvent`, `PlayerConnectionInitialConfigureEvent`, Bukkit/Paper player events used by the bounded PLAY quarantine, lifecycle command registration, `PlayerCommandPreprocessEvent`, `PlayerCommandSendEvent`, `AsyncPlayerSendSuggestionsEvent`, and Adventure components.

The 26.3 public API continues to expose the connection/configuration/validation and command-disclosure surfaces Guardian uses. No concrete API defect was found that justifies replacing the proven standalone hybrid CONFIGURATION + bounded quarantined PLAY flow. Phase 8 therefore preserves the 26.2 behavior and requires a focused 26.3 live check before considering removal of the PLAY fallback.

### Velocity review

Guardian-Velocity continues to use supported proxy configuration and lifecycle APIs: awaited `PlayerConfigurationEvent`, `PluginMessageEvent`, proxy-owned commands, player/server connection state, and the existing trusted Guardian-Velocity -> Guardian-Paper assertion boundary. The current official Velocity development dependency remains `4.2.1-SNAPSHOT`.

No change is made to one-decision-per-proxy-connection behavior, backend switching, `/guardianv`, manifest ownership, or the assertion-only backend contract.

### Fabric/Cerberus review

Cerberus continues to use Fabric Loader's public mod/origin/containment APIs and Fabric API CONFIGURATION/PLAY networking. The full canonical Loader-known manifest, parent/child relationships, exact top-level outer-archive SHA-256, Java/Minecraft built-ins, signed Cerberus release identity, and Guardian challenge authentication remain unchanged.

The 26.3 Fabric port notes remove unrelated gameplay registries rather than the networking/Loader APIs Guardian uses. Directory origins remain deliberately unhashed; no directory-tree hashing is introduced.

### Optional integrations

Geyser, Floodgate, and LuckPerms remain optional `compileOnly` integrations. Bedrock classification continues to use supported Geyser/Floodgate evidence before Java/Cerberus handling; usernames and prefixes remain non-authoritative.

## Implemented port slice

The candidate makes only platform/release-target changes that are justified by the review:

- source default `1.0.1` -> `1.0.2`;
- Minecraft target `26.2` -> `26.3`;
- Paper API `26.2.build.+` -> `26.3.build.+`;
- Fabric API `0.161.0+26.2` -> `0.162.0+26.3`;
- Fabric Loom `1.17.21` -> `1.18.2`;
- Cerberus `fabric.mod.json` Minecraft range `~26.2` -> `~26.3`;
- Guardian-Paper `plugin.yml` API version `26.2` -> `26.3`;
- release-smoke defaults/examples move to `1.0.2`;
- current operator/development/policy examples move to 26.3 while historical Phase 0-7.5 records remain unchanged;
- focused architecture/resource tests pin the reviewed Phase 8 target contract.

No Guardian Admission, Protection, protocol, policy, assertion, signing, key, inspection, or runtime source behavior is redesigned in this slice.

## Version choice

`1.0.2` is the appropriate release version for this port. The project is preserving protocol v1, schema 2, command families, security/trust boundaries, policy semantics, and externally observable behavior; the meaningful change is the supported Minecraft/Paper/Fabric platform line. A `1.1.0` minor bump would imply a feature-level surface change that Phase 8 intentionally does not contain.

## Open verification boundary

This implementation document does not declare Phase 8 closed. Before release, the Java 25 automated gate, focused 26.3 live matrix, final JAR-content/security/privacy audit, and final release provenance/checksums must be completed as recorded in `PHASE_8_VERIFICATION.md`.
