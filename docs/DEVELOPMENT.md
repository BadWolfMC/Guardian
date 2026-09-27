# Development workspace notes

## Required toolchain

Guardian/Cerberus targets Java 25 and uses the repository Gradle wrapper.

Use:

```powershell
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Do not replace the wrapper with a system Gradle installation. The authoritative wrapper is:

- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`

The wrapper currently resolves Gradle 9.7.1.

## VS Code Java project import

The repository contains a Gradle multi-project build. `guardian-protocol` is a normal project dependency of both platform adapters; it should not be added manually as a referenced JAR.

If VS Code shows unresolved `com.badwolfmc.guardian.protocol.*` imports while Gradle builds successfully:

1. Confirm `java -version` is Java 25 and `JAVA_HOME` points to that JDK.
2. Open the **repository root** in VS Code, not an individual subproject.
3. Run **Java: Clean Java Language Server Workspace** and choose **Restart and delete**.
4. Run **Java: Import Java Projects into Workspace** (or **Java: Reload Projects**, depending on the installed extension version).
5. Run **Java: Update Project Configuration** if the Java language server still shows a stale classpath.

The checked-in `.vscode/settings.json` forces Standard mode, automatic Gradle project updates, and use of the repository wrapper. It intentionally does not hard-code a machine-specific JDK path.

If the language server still launches on the wrong JDK, set both `java.jdt.ls.java.home` and `java.import.gradle.java.home` in your **user** settings to the absolute path of your local JDK 25 installation.

## Local Gradle/Loom caches

`.gradle/` and every `build/` directory are local generated state and are ignored by Git. They are safe to delete when Gradle/VS Code is not actively using them.

The `.gradle/8.9`, `.gradle/9.2.0`, and `.gradle/9.7.1` folders are **not bundled Gradle distributions**. They are per-project caches from builds run with those Gradle versions. Loom also stores large mapped Minecraft artifacts under `.gradle/loom-cache/`.

To reclaim the space on Windows:

```powershell
.\gradlew.bat --stop
Remove-Item -Recurse -Force .gradle, build -ErrorAction SilentlyContinue
Get-ChildItem -Directory -Recurse -Filter build |
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
```

The next build will regenerate the caches it needs. Do **not** delete `gradle/wrapper/` unless intentionally regenerating the wrapper.


## Guardian domain boundaries

`guardian-core` is the platform-neutral Admission domain. `guardian-protection` is the independent platform-neutral Protection domain. Neither module may import Paper, Velocity, Fabric, Geyser/Floodgate, or the other domain merely for convenience. Guardian-Paper is the host/adaptor that composes them. Architecture tests enforce these boundaries.

Phase 1B adds Paper-only Protection adapters in `guardian-paper`. Command execution interception must remain player-only; do not add `ServerCommandEvent`, console/command-block interception, NMS, CraftBukkit implementation access, reflection into server internals, or packet-library hooks. Root visibility and downstream suggestion suppression must continue to call the same platform-neutral visibility decision.

## Implementation bridge discipline

`docs/IMPLEMENTATION_BRIDGES.md` is the required register for temporary implementation scaffolding that crosses phase boundaries. Update it whenever prototype code, temporary provisioning, simplified policy logic, hard-coded operational values, or early integrations are retained intentionally. Each entry must name an owning phase and a concrete retirement condition.

At every phase closeout, review the active bridge register before declaring the phase complete. A bridge may be removed only when its retirement condition is satisfied or when the authoritative project plan explicitly promotes that behavior to the final contract.

## Phase 3 shared Admission policy development

Administrator Admission policy lives in `shared-resources/admission/policy.yml` and is copied to the platform data directory as `admission/policy.yml`. Do not move client/mod/profile policy back into Paper `config.yml` or add a Velocity-specific policy schema.

The parse/normalize/validate/snapshot/evaluate path belongs in `guardian-core` and must stay free of Paper/Velocity types. Paper/Velocity may own data-directory discovery, lifecycle/reload invocation, optional provider adapters, transport state, scheduling, and logging.

`artifacts.yml` is exact-artifact identity data only. Adding a catalog entry must not change admission by itself. `HASH_REQUIRED` policy must explicitly use `catalog: true` and/or direct `sha256` declarations. Catalog scans do not mutate an already active immutable policy snapshot; reload/validation is a separate operation.

Supported Phase 3 version predicates are deliberately bounded: `*`, exact strings, one trailing prefix wildcard, or whitespace-separated dotted-numeric comparison terms such as `>=1.2 <2.0`. Do not add a general-purpose expression language or silently impose semver ordering on arbitrary Fabric version strings.

The cross-adapter invariant is strict: equivalent profile/classification/manifest inputs against the same shared snapshot must reach the same policy result from standalone Paper and Velocity. Paper in `velocity` authority mode is not a second admission-policy authority.
