# Release process

This process keeps the Cerberus release-signing private key out of CI while still producing reproducible server artifacts, checksums, embedded license material, and a reviewable signed client artifact.

## 1. Automated release-candidate gate

Run the manual **Release Candidate** GitHub Actions workflow with an explicit version such as `1.0.0-rc.1`. Release versions are intentionally restricted to 1–64 characters matching `[0-9A-Za-z][0-9A-Za-z._-]*` so version input cannot become a path or shell fragment.

It performs:

```text
Java 25 / Gradle wrapper validation
clean test
Guardian-Paper JAR
Guardian-Velocity JAR
unsigned Cerberus-Fabric build
private-key/resource leakage scan
SHA256SUMS.txt for staged JARs
release-candidate artifact upload
```

CI deliberately has no Cerberus release-signing private key.

## 2. Generate release-signing identity (once / rotation only)

```powershell
.\tools\release-manager.ps1 `
  -Action generate-release-key `
  -OutputDirectory D:\GuardianKeys\cerberus-release
```

Outputs:

- `cerberus-release-signing.key` — PKCS#8 PEM private key; offline/private.
- `cerberus-release-signing.pub` — base64 X.509/SPKI public key; place in authoritative `policy.yml` as needed.

The task refuses to overwrite an existing identity.

## 3. Generate Guardian server identity (initial/rotation only)

```powershell
.\tools\release-manager.ps1 `
  -Action generate-server-identity `
  -OutputDirectory D:\GuardianKeys\server-auth-next
```

Keep the private key on the Admission authority. Build the public anchor set used by Cerberus according to the staged rotation procedure in `KEY_MANAGEMENT.md`.

## 4. Sign Cerberus offline

Use the **same source/commit/version** that passed the release-candidate gate. Signing requires an explicit release version, private key, and output path. The version is passed into Gradle and becomes both the built Cerberus artifact version and signed release-identity version:

```powershell
.\tools\release-manager.ps1 `
  -Action sign-cerberus `
  -Version 1.0.0 `
  -ReleasePrivateKey D:\GuardianKeys\cerberus-release\cerberus-release-signing.key `
  -GuardianServerPublicKeys D:\GuardianKeys\server-auth-trust.txt `
  -SignedOutput D:\GuardianRelease\cerberus-fabric-1.0.0-signed.jar
```

The underlying Gradle task signs the Loom `jar` output for Minecraft 26.2. Do not switch back to `remapJar`; Phase 6 live release signing established that this non-obfuscated Loom target uses `jar`.

The signer prints a **Canonical SHA-256** used inside release identity metadata. The helper separately prints the SHA-256 of the **finished signed JAR file**. They are different values for different purposes.

## 5. Stage and checksum final artifacts

Put the release Paper JAR, Velocity JAR, **signed** Cerberus JAR, `LICENSE`, and `THIRD_PARTY_NOTICES.md` in one clean release directory. Then:

```powershell
.\tools\release-manager.ps1 -Action checksums -ArtifactDirectory D:\GuardianRelease
```

Publish `SHA256SUMS.txt` with the release.

## 6. Required artifact inspection

Before publication:

- inspect all JAR entry lists;
- reject any `proxy-assertion.key`, `guardian-server-auth.key`, release private key, PEM/private-key resource, developer absolute path, or administrator-local config;
- confirm Paper/Velocity JARs contain `META-INF/LICENSE-GPL-3.0.txt`, `META-INF/THIRD-PARTY-NOTICES.md`, and `META-INF/licenses/Apache-2.0.txt`;
- confirm Cerberus contains GPL/notice material and only intended public server-auth trust anchors/release identity metadata;
- verify version metadata in plugin descriptors/Fabric metadata.

## 7. Minimal release live checks

Phase 7 should not recreate the Phase 6 adversarial matrix. For a final RC, perform only changes-sensitive checks:

1. clean standalone startup from packaged defaults and `/guardian status`/`validate`;
2. representative schema-1 upgrade on a copy of a Phase 6 config/policy and verify backup + preserved values/comments;
3. signed Cerberus happy path with the final signed JAR;
4. one wrong Guardian server-auth key check if client-facing wording/trust anchors changed;
5. Velocity-authoritative happy path if Velocity packaging/config changed.

## 8. License/notice audit

Guardian's distributable Paper and Velocity JARs shade SnakeYAML Engine. Its Apache-2.0 license is bundled. Paper/Velocity/Fabric/Geyser/Floodgate/LuckPerms APIs are build/provided integrations rather than shaded runtime payloads in Guardian's final JARs; their license references remain documented in `THIRD_PARTY_NOTICES.md`.
