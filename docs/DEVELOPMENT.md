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

## Release identities and official Cerberus signing

Phase 7 provides `tools/release-manager.ps1` as the supported human-facing wrapper around the offline release tasks. It deliberately keeps private-key and output paths explicit; CI never receives the Cerberus release-signing private key.

Generate a release-signing identity outside the repository:

```powershell
.\tools\release-manager.ps1 `
  -Action generate-release-key `
  -OutputDirectory C:\secure\cerberus-release
```

Generate the **separate** Guardian server-authentication identity similarly:

```powershell
.\tools\release-manager.ps1 `
  -Action generate-server-identity `
  -OutputDirectory C:\secure\guardian-server-identity
```

Sign the normal production Loom `jar` output with an explicit destination:

```powershell
.\tools\release-manager.ps1 `
  -Action sign-cerberus `
  -Version 1.0.0 `
  -ReleasePrivateKey C:\secure\cerberus-release\cerberus-release-signing.key `
  -GuardianServerPublicKeys C:\secure\guardian-server-identity\guardian-server-auth.pub `
  -SignedOutput C:\release\cerberus-fabric-1.0.0-signed.jar
```

The underlying Gradle tasks remain available for automation, but signing requires an explicit `-PguardianVersion=...`, `-PcerberusReleasePrivateKey=...`, and `-PcerberusSignedOutput=...`. The release-signing private key, Guardian server-authentication private key, and Velocity/Paper `proxy-assertion.key` are three distinct trust domains and must not be reused. See `KEY_MANAGEMENT.md` and `RELEASE_PROCESS.md` for storage, deployment, staged rotation, compromise response, checksums, and the distinction between the signer's canonical digest and the finished-JAR SHA-256.

The signed release mechanism is provenance/compliance hardening only. It verifies the **reported** official release identity; a hostile modified client can still lie. Guardian server authentication protects stock Cerberus manifest disclosure and is likewise not remote attestation.

## VS Code Java project import

The repository contains a Gradle multi-project build. `guardian-protocol` is a normal project dependency of both platform adapters; it should not be added manually as a referenced JAR.

If VS Code shows unresolved `com.badwolfmc.guardian.protocol.*` imports while Gradle builds successfully:

1. Check the shell, `JAVA_HOME`, and the wrapper JVM separately:

   ```powershell
   Get-Command java -All | Select-Object Source
   java -version
   $env:JAVA_HOME
   & "$env:JAVA_HOME\bin\java.exe" -version
   .\gradlew.bat --version
   ```

   `gradlew.bat` deliberately prefers `JAVA_HOME\bin\java.exe` when `JAVA_HOME` is set. A bare `java` command can therefore report an older PATH installation even while Gradle is correctly running on JDK 25. Fix PATH as well rather than relying on that split indefinitely.
2. Open the **repository root** in VS Code, not an individual subproject.
3. Run **Java: Clean Java Language Server Workspace** and choose **Restart and delete**.
4. Run **Java: Import Java Projects into Workspace** (or **Java: Reload Projects**, depending on the installed extension version).
5. Run **Java: Update Project Configuration**. With the Gradle for Java extension installed, also refresh the Gradle project/build-server model if the dependency graph still looks stale.
6. If cross-module symbols still resolve against an older Guardian API, close VS Code, stop Gradle, remove generated `.gradle/` and `build/` directories as described below, reopen the repository root, and repeat the import/update steps.

The checked-in `.vscode/settings.json` forces Standard mode, automatic Gradle project updates, and use of the repository wrapper. It intentionally does not hard-code a machine-specific JDK path.

If the language server or Gradle importer still selects the wrong JDK, set `java.jdt.ls.java.home` and `java.import.gradle.java.home` in your **user** settings to the absolute path of your local JDK 25 installation. It is also reasonable to register JDK 25 under `java.configuration.runtimes` so VS Code can map `JavaSE-25` explicitly.

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

Administrator Admission policy lives in `shared-resources/policy.yml` and is copied to the platform data directory as `policy.yml`. Do not move client/mod/profile policy back into Paper `config.yml` or add a Velocity-specific policy schema.

The parse/normalize/validate/snapshot/evaluate path belongs in `guardian-core` and must stay free of Paper/Velocity types. Paper/Velocity may own data-directory discovery, lifecycle/reload invocation, optional provider adapters, transport state, scheduling, and logging.

`artifacts.yml` is exact-artifact identity data only. Adding a catalog entry must not change admission by itself. `HASH_REQUIRED` policy must explicitly use `catalog: true` and/or direct `sha256` declarations. Catalog scans do not mutate an already active immutable policy snapshot; reload/validation is a separate operation.

Supported Phase 3 version predicates are deliberately bounded: `*`, exact strings, one trailing prefix wildcard, or whitespace-separated dotted-numeric comparison terms such as `>=1.2 <2.0`. Do not add a general-purpose expression language or silently impose semver ordering on arbitrary Fabric version strings.

The cross-adapter invariant is strict: equivalent profile/classification/manifest inputs against the same shared snapshot must reach the same policy result from standalone Paper and Velocity. Paper in `velocity` authority mode is not a second admission-policy authority.
