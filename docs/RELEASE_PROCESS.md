# Release process

Guardian's release flow is intentionally conservative, but the normal path is short once the three key domains are already provisioned.

## Normal release day — plain-language walkthrough

For an ordinary release, think of the process as **build → sign Cerberus → checksum → inspect → publish**.

### Step 1 — choose the release version

Pick the version you intend to publish, for example:

```text
1.0.0
```

Use that same value everywhere. Guardian-Paper, Guardian-Velocity, the unsigned Cerberus build, and the signed Cerberus release identity must all agree on it.

### Step 2 — run the Release Candidate workflow

In GitHub Actions, run **Release Candidate** and enter the version from step 1.

That workflow runs the Java 25 / Gradle wrapper gate, builds the three unsigned artifacts, checks for accidentally packaged private-key material, writes checksums for the staged JARs, and uploads the release-candidate bundle.

CI intentionally does **not** have the Cerberus release-signing private key.

### Step 3 — sign Cerberus on the release-manager machine

The release manager keeps the Cerberus signing key offline/private. Point the helper at the release output directory rather than inventing a filename yourself:

```powershell
.\tools\release-manager.ps1 `
  -Action sign-cerberus `
  -Version 1.0.0 `
  -ReleasePrivateKey D:\GuardianKeys\cerberus-release\cerberus-release-signing.key `
  -GuardianServerPublicKeys D:\GuardianKeys\server-auth-trust.txt `
  -OutputDirectory D:\GuardianRelease\1.0.0
```

The helper creates:

```text
D:\GuardianRelease\1.0.0\cerberus-fabric-1.0.0-signed.jar
```

It also prints the SHA-256 of the finished JAR. The signer's **Canonical SHA-256** is a different value used by Cerberus release identity metadata; both are expected to exist.

If an exact file path is genuinely useful, `-SignedOutput` remains available, but its filename must contain the requested release version.

### Step 4 — put the final artifacts in one directory

Place the release Paper JAR, Velocity JAR, **signed** Cerberus JAR, `LICENSE`, and `THIRD_PARTY_NOTICES.md` together in the final release directory.

Do not publish the unsigned Cerberus JAR as the official client artifact when signed-release enforcement is part of the intended deployment.

### Step 5 — write final checksums

```powershell
.\tools\release-manager.ps1 `
  -Action checksums `
  -ArtifactDirectory D:\GuardianRelease\1.0.0
```

This writes `SHA256SUMS.txt` for every JAR in that directory.

### Step 6 — inspect, smoke-test, and publish

Before publication:

- verify every artifact reports the intended version;
- inspect JAR entry lists for private-key/config leakage;
- confirm expected license/NOTICE resources are present;
- perform the small release-candidate live checks in `PHASE_7_VERIFICATION.md`; and
- publish the final artifacts plus `SHA256SUMS.txt`, `LICENSE`, and `THIRD_PARTY_NOTICES.md`.

That is the normal release flow. The sections below cover first-time key creation, rotation, and lower-level details.

---

## First-time or rotation-only key generation

These are **not** steps you repeat for every release.

### Cerberus release-signing identity

```powershell
.\tools\release-manager.ps1 `
  -Action generate-release-key `
  -OutputDirectory D:\GuardianKeys\cerberus-release
```

Outputs:

- `cerberus-release-signing.key` — PKCS#8 PEM private key; offline/private.
- `cerberus-release-signing.pub` — base64 X.509/SPKI public key; place in authoritative `policy.yml` as needed.

The generator refuses to overwrite an existing identity.

### Guardian server-authentication identity

```powershell
.\tools\release-manager.ps1 `
  -Action generate-server-identity `
  -OutputDirectory D:\GuardianKeys\server-auth-next
```

Keep the private key on Guardian Admission authorities only. Build the public trust-anchor set used by Cerberus according to the staged rotation procedure in `KEY_MANAGEMENT.md`.

## What the helper guarantees

For `sign-cerberus`, the helper requires:

- an explicit validated release version;
- an explicit Cerberus release-signing private-key path; and
- either an explicit output directory, which produces `cerberus-fabric-<version>-signed.jar`, or an explicit version-bearing `SignedOutput` file path.

It passes the release version into Gradle, so the Fabric metadata and signed release identity use the same version as the output artifact. The underlying signer refuses to overwrite the unsigned input path.

The source remains correct for the Phase 6 Loom finding: Minecraft 26.2 non-obfuscated Fabric uses `jar`, not `remapJar`, as the signing input.

## Automated release-candidate gate

The manual **Release Candidate** workflow accepts versions matching:

```text
[0-9A-Za-z][0-9A-Za-z._-]{0,63}
```

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

## Required artifact inspection

Before publication:

- inspect all JAR entry lists;
- reject any `proxy-assertion.key`, `guardian-server-auth.key`, release private key, PEM/private-key resource, developer absolute path, or administrator-local config;
- confirm Paper/Velocity JARs contain `META-INF/LICENSE-GPL-3.0.txt`, `META-INF/THIRD-PARTY-NOTICES.md`, and `META-INF/licenses/Apache-2.0.txt`;
- confirm Cerberus contains GPL/notice material and only intended public server-auth trust anchors/release identity metadata; and
- verify version metadata in Paper plugin metadata, Velocity plugin metadata/status, Fabric metadata, and the signed Cerberus release identity.

## Minimal release live checks

Phase 7 should not recreate the Phase 6 adversarial matrix. For a final RC, perform only changes-sensitive checks:

1. clean standalone startup from packaged defaults and `/guardian status`/`validate`;
2. representative schema-1 upgrade on a copy of a Phase 6 config/policy and verify backup + preserved values/comments;
3. signed Cerberus happy path with the final signed JAR;
4. one wrong Guardian server-auth key check if client-facing wording/trust anchors changed; and
5. Velocity-authoritative happy path if Velocity packaging/config changed.

## License/notice audit

Guardian's distributable Paper and Velocity JARs shade SnakeYAML Engine. Its Apache-2.0 license is bundled. Paper/Velocity/Fabric/Geyser/Floodgate/LuckPerms APIs are build/provided integrations rather than shaded runtime payloads in Guardian's final JARs; their license references remain documented in `THIRD_PARTY_NOTICES.md`.
