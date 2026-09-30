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

## Signing an official Cerberus release

Official Cerberus release provenance is optional and uses an **offline Ed25519 release private key**. Never commit that private key, place it in a Guardian data directory, copy it to a Minecraft client/server, or add it to Gradle properties checked into the repository. Guardian servers need only the corresponding base64 X.509 SubjectPublicKeyInfo public key in `policy.yml`.

After the normal build is green, create the signed release artifact with:

```powershell
.\gradlew.bat :cerberus-fabric:signCerberusRelease `
  -PcerberusReleasePrivateKey=C:\secure\cerberus-release-private.pem
```

The key must be an Ed25519 PKCS#8 private key in DER or PEM `PRIVATE KEY` form. The task signs the remapped Cerberus artifact and writes `cerberus-fabric-<version>-signed.jar` under `cerberus-fabric/build/libs/`. The signing utility is a separate `releaseTool` source set; it is not part of the client mod artifact. It reads the private key as a bounded regular non-symlink file and publishes the signed JAR only after the embedded metadata and canonical digest revalidate.

The corresponding shared-policy form is:

```yaml
cerberus-release-trust:
  required: true
  ed25519-public-keys:
    - "<base64-X.509-Ed25519-public-key-DER>"
```

Do not enable `required: true` until the signed Cerberus JAR has been distributed and the correct public key is present on every Admission authority. During key rotation, the policy may contain both old and new public keys temporarily.

The signed release mechanism is provenance/compliance hardening only. It proves that the **reported** canonical release identity has a valid release signature; a hostile modified client can still copy/replay valid official release metadata while executing different code.

## Generating and deploying the Guardian server-authentication identity

Guardian-to-Cerberus server authentication uses a **different Ed25519 key pair** from Cerberus release signing. The Guardian Admission authority keeps the server-auth private key; official Cerberus releases pin only the corresponding public trust anchor. Never reuse the Cerberus release-signing private key for this purpose.

Generate the identity into a secure directory outside the repository:

```powershell
.\gradlew.bat :cerberus-fabric:generateGuardianServerIdentity `
  -PguardianServerIdentityDirectory=C:\secure\guardian-server-identity
```

The task refuses to overwrite existing files and creates:

- `guardian-server-auth.key` — Ed25519 PKCS#8 PEM private key; keep this on Admission authorities only.
- `guardian-server-auth.pub` — canonical public trust-anchor file; this is safe to embed in Cerberus.

To ship an official Cerberus release that requires Guardian authentication, pass the public trust-anchor file to the **release-signing** task:

```powershell
.\gradlew.bat :cerberus-fabric:signCerberusRelease `
  -PcerberusReleasePrivateKey=C:\secure\cerberus-release-private.pem `
  -PguardianServerAuthPublicKeys=C:\secure\guardian-server-identity\guardian-server-auth.pub
```

The release tool injects the server-auth public keys before calculating/signing the canonical Cerberus release digest, so the official release signature covers those trust anchors. Do not manually add or replace `META-INF/guardian/trusted-server-keys.txt` after release signing.

Copy `guardian-server-auth.key` to the Guardian plugin data directory of each **Admission authority** that should represent the same trust identity, then enable the corresponding configuration:

- standalone Paper: `admission.standalone.server-authentication.enabled: true`;
- Guardian-Velocity: `admission.server-authentication.enabled: true`;
- Velocity-authority Paper backend: no server-auth private key is required because Paper verifies the proxy assertion rather than challenging Cerberus.

A staged rotation is: release Cerberus with old+new public keys, deploy/reload Guardian with the new private key, then release Cerberus again with only the new public key. If a private key is compromised, clients that still pin that key will continue to trust it until they update; there is no network revocation service in this design.

This feature protects the manifest-disclosure boundary of **stock Cerberus**. It does not provide remote attestation, and a modified client can choose to ignore its own server-authentication check. The signed challenge is bound to the authenticated player UUID, nonce, capabilities, and a short validity window to block ordinary cross-player relay; a captured challenge for the same player may remain reusable within that narrow window.

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
