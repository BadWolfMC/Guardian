# Guardian / Cerberus release process

This is the normal release path for Guardian-Paper, Guardian-Velocity, and an official signed Cerberus-Fabric client. It is designed around GitHub Actions + the GitHub web UI + the local PowerShell release manager; GitHub CLI is not required.

The Cerberus release-signing private key remains offline and **never enters GitHub Actions**.

## Normal path

### 1. Choose the release version

Use SemVer for public releases and release candidates, for example:

```text
1.0.0
1.0.1-rc.1
1.1.0
```

Internal historical `0.1.0-phase*` versions are development provenance only and are not a compatibility promise.

### 2. Run the GitHub Release Candidate workflow

In the repository's **Actions** tab:

1. choose **Release Candidate**;
2. click **Run workflow**;
3. enter the exact version; and
4. wait for the complete Java 25 / Gradle 9.7.1 gate to pass.

The workflow builds/tests one exact commit and uploads an artifact named:

```text
guardian-<version>-release-input
```

It contains:

```text
guardian-paper-<version>.jar
guardian-velocity-<version>.jar
cerberus-fabric-<version>-unsigned.jar
cerberus-release-tools-<version>.jar
LICENSE
THIRD_PARTY_NOTICES.md
RELEASE_INPUT.json
SHA256SUMS-CI.txt
```

`RELEASE_INPUT.json` records the version, exact Git commit SHA, repository, GitHub Actions run ID, and run attempt. `SHA256SUMS-CI.txt` commits to every file that the local finalizer consumes, including the release-input manifest and the CI-built release-tool JAR.

The unsigned Cerberus JAR and release-tool JAR are **signing inputs**, not public client release assets. The local finalizer uses the CI-built signer/verifier from that same candidate instead of recompiling release code from whatever happens to be in the local checkout.

### 3. Download the exact CI artifact

Open the successful workflow run in the GitHub web UI and download the release-input artifact. Extract it into a clean local directory such as:

```text
<repo>\release-input\
```

Do not rebuild Cerberus locally. The release manager deliberately signs the exact CI-built unsigned JAR using the checksummed CI-built release-tool JAR from the same workflow run. This keeps the normal signing path independent of GitHub CLI, a local Gradle build, or locally recompiled signer code. Java 25 is still required to run the release tool.

### 4. Finalize the release offline/locally

Use the release-signing private key, its matching public verification key, and the Guardian server-authentication trust-anchor set intended for this Cerberus release:

```powershell
.\tools\release-manager.ps1 `
  -Action finalize-release `
  -Version 1.0.0 `
  -InputDirectory .\release-input `
  -OutputDirectory .\release-final `
  -ReleasePrivateKey D:\GuardianKeys\cerberus-release\cerberus-release-signing.key `
  -ReleasePublicKey D:\GuardianKeys\cerberus-release\cerberus-release-signing.pub `
  -GuardianServerPublicKeys D:\GuardianKeys\server-auth-trust.txt
```

The finalizer fails closed unless all of the following are true:

- the CI input manifest/version is valid;
- all CI checksums match;
- Paper, Velocity, and unsigned Cerberus metadata all report the requested version;
- the output directory is empty;
- the signed output does not already exist;
- Cerberus signing consumes the explicit CI-built unsigned JAR rather than rebuilding it;
- signing and signature verification run from the checksummed CI-built release-tool JAR;
- the finished Cerberus signature verifies against the supplied release public key;
- the exact supplied public verification key is copied into the final release as `cerberus-release-signing.pub` and remains byte-identical;
- optional embedded Guardian server-authentication trust anchors exactly match the supplied trust file;
- required license/notice/icon/release-identity resources are present;
- no private-key-like resources, PEM private keys, or obvious machine-local build paths are present in publishable text resources; and
- the final directory contains only the intended public release files.

A successful run ends with:

```text
READY TO UPLOAD
```

### 5. Inspect `release-final/`

The final directory is deliberately small:

```text
guardian-paper-<version>.jar
guardian-velocity-<version>.jar
cerberus-fabric-<version>-signed.jar
cerberus-release-signing.pub
LICENSE
THIRD_PARTY_NOTICES.md
RELEASE_PROVENANCE.txt
SHA256SUMS.txt
```

`RELEASE_PROVENANCE.txt` records the exact CI source commit/repository/run identity plus the unsigned/final artifact hashes, CI release-tool hash, release public-key hash, and server-auth trust-file hash (when used). The final `SHA256SUMS.txt` covers every publishable file except itself.

`cerberus-release-signing.pub` is intentionally public. For `policy.yml` `cerberus-release-trust.ed25519-public-keys`, copy the file's **single base64 X.509/SPKI key line**. Do **not** paste the public-key file SHA-256, the signed Cerberus JAR SHA-256, the unsigned CI JAR SHA-256, or the canonical signed digest into that trust list. Those hashes serve provenance/integrity roles; Guardian's signed-release verifier needs the actual Ed25519 public key.

Publishing the public key beside the release makes normal installation easier, but it does not create a separate trust channel by itself. Obtain it from the official BadWolfMC/Guardian release/project surface you already trust; `SHA256SUMS.txt` then protects accidental corruption within that release set.

The unsigned Cerberus JAR must **not** be copied into this directory.

You can rerun the final verification without signing again:

```powershell
.\tools\release-manager.ps1 `
  -Action verify-release `
  -Version 1.0.0 `
  -ArtifactDirectory .\release-final `
  -ReleaseToolJar .\release-input\cerberus-release-tools-1.0.0.jar `
  -ReleasePublicKey D:\GuardianKeys\cerberus-release\cerberus-release-signing.pub `
  -GuardianServerPublicKeys D:\GuardianKeys\server-auth-trust.txt
```

### 6. Create a draft GitHub Release

Use GitHub's web interface:

1. open **Releases** -> **Draft a new release**;
2. create/select tag `v<version>` at the exact source commit recorded in `RELEASE_PROVENANCE.txt`;
3. mark RC versions as pre-releases;
4. drag the **contents of `release-final/`** into the release assets area; and
5. save it as a **draft** first.

Do not commit release JARs into the repository. `release-input/`, `release-final/`, and `release-artifacts/` are ignored local staging directories; binary release assets belong in GitHub Actions artifacts or GitHub Releases.

### 7. Smoke-test the draft artifacts

Test the actual draft assets, not a locally rebuilt replacement. For every release, use the small risk-based production matrix in `PRODUCTION_RUNBOOK.md`.

At minimum verify:

- clean Guardian-Paper startup/status/validate;
- clean Guardian-Velocity startup/status/validate for proxy releases;
- signed Fabric/Cerberus Admission using the finished signed JAR;
- representative Guardian Protection execution/visibility; and
- one backend switch on a Velocity-authoritative deployment.

### 8. Publish the draft

Only after the final verifier and smoke tests pass, publish the draft GitHub Release. Keep the final release directory and `RELEASE_PROVENANCE.txt` with your release records.

---

## Lower-level signing only

For unusual/manual recovery work, `sign-cerberus` remains available. It **requires an explicit unsigned input JAR** and will not build one for you:

```powershell
.\tools\release-manager.ps1 `
  -Action sign-cerberus `
  -Version 1.0.0 `
  -UnsignedCerberusJar .\release-input\cerberus-fabric-1.0.0-unsigned.jar `
  -ReleaseToolJar .\release-input\cerberus-release-tools-1.0.0.jar `
  -ReleasePrivateKey D:\GuardianKeys\cerberus-release\cerberus-release-signing.key `
  -GuardianServerPublicKeys D:\GuardianKeys\server-auth-trust.txt `
  -OutputDirectory .\release-final
```

The helper refuses to overwrite an existing signed output. The finished-file SHA-256 printed by the helper is different from the canonical SHA-256 embedded/signed by Cerberus release identity; both are expected.

## First-time or rotation-only key generation

These are not per-release steps.

### Cerberus release-signing identity

```powershell
.\tools\release-manager.ps1 `
  -Action generate-release-key `
  -OutputDirectory D:\GuardianKeys\cerberus-release
```

Outputs:

- `cerberus-release-signing.key` — PKCS#8 PEM private key; offline/private.
- `cerberus-release-signing.pub` — base64 X.509/SPKI public key; safe to distribute publicly. Its base64 contents belong in authoritative `policy.yml` as needed. The normal finalizer also publishes this exact public key as a release asset.

The generator refuses to overwrite an existing identity.

### Guardian server-authentication identity

```powershell
.\tools\release-manager.ps1 `
  -Action generate-server-identity `
  -OutputDirectory D:\GuardianKeys\server-auth-next
```

Keep the private key only on the active Guardian Admission authority. Build the public old+new trust-anchor set according to `KEY_MANAGEMENT.md`.

## CI trust boundary

The Release Candidate workflow has read-only repository permissions and no release-signing private key. Its job ends after producing a checksummed signing-input artifact for one exact commit: the tested Guardian/Cerberus binaries plus the release signer/verifier compiled from that same source.

The local release manager crosses the deliberate manual security boundary: it verifies those exact CI bytes, runs the CI-built release tool, signs only the Cerberus input, constructs the publishable set, and verifies the result. GitHub receives only the final public assets when the operator uploads them to a draft release.
