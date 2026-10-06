# Historical Phase 0A sanity report

> **Historical snapshot:** this document records the Phase 0A feasibility state only. It is not a description of the current Guardian/Cerberus implementation. For current release readiness use `PHASE_7_5_VERIFICATION.md`; for current architecture/operations use the README and administrator documentation.

## Live result

The hybrid CONFIGURATION + quarantined PLAY architecture is proven in live Paper 26.2 / Fabric 26.2 testing.

Observed structured outcomes include:

- `VANILLA_POLICY`
- `CERBERUS_REQUIRED`
- `CERBERUS_PROTOCOL_UNSUPPORTED`
- `CERBERUS_VERIFIED`
- `MANIFEST_DENIED`
- `CERBERUS_TIMEOUT`
- `MANIFEST_INVALID`

## Build result

The user's Java 25 / Gradle 9.7.1 environment completed:

```text
clean test :guardian-paper:jar :cerberus-fabric:build
```

successfully with 19 actionable tasks.

The Gradle 10 compatibility warnings in that build report were traced to the two project `processResources` blocks reading `project.version` during task execution. This revision captures the version during configuration instead.

## Default resource packaging

Test Series 5 uses `saveDefaultConfig()` and therefore requires an embedded `config.yml`.

The previous archive was missing that resource even though Java compilation succeeded. This revision restores the intended default configuration and adds `GuardianPaperResourcesTest` so the Gradle test suite verifies both `plugin.yml` and `config.yml` are packaged.

## VS Code project model

The Gradle compiler successfully resolves the multi-project `guardian-protocol` dependency. The VS Code diagnostics showing unresolved protocol imports, an unavailable `List.of(...)`, and an unmanaged `tools/Phase0SelfTest.java` are therefore consistent with a stale/incomplete Java language-server project import rather than compiler failures.

This revision:

- removes the redundant unmanaged `tools/Phase0SelfTest.java` now that JUnit coverage exists;
- explicitly declares Java 25 source/target compatibility in addition to the Java 25 toolchain;
- checks in minimal VS Code settings for Standard mode, automatic Gradle model updates, and wrapper use;
- excludes `.gradle` and `build` directories from Java language-server resource refreshes.

See `DEVELOPMENT.md` for the one-time Java language-server reset steps.

## Historical scope checks

At the time of Phase 0A, the selected architecture contained no:

- server NMS/CraftBukkit dependency;
- reflection into Minecraft implementation internals;
- Fabric `impl` dependency;
- Mixins used to force networking behavior;
- packet-library dependency;
- Velocity/Geyser/Floodgate implementation;
- LuckPerms integration;
- database;
- production policy engine;
- artifact hashing/signing implementation.

Signed official Cerberus artifact identity was recorded only as a future Phase 6 exploration item at this historical checkpoint; it is implemented in the current codebase.
