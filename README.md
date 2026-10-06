# Guardian / Cerberus

Guardian is a client-admission and Paper server-protection project for Minecraft servers. Cerberus is the cooperative Fabric client used when a Guardian Admission policy requires an approved Fabric environment.

Guardian has two independent domains:

- **Admission** classifies connections, evaluates shared client/mod policy, supports Geyser/Floodgate Bedrock classification, and can use Guardian-Velocity as the network authority.
- **Protection** is Paper-local command execution, visibility, namespace, bypass, and staff-notification policy.

For a Velocity network, Guardian-Velocity can act as the Admission authority with Guardian-Paper on each backend. Guardian-Paper also works as a standalone Admission authority, and Guardian Protection never requires Velocity or Cerberus.

> Guardian/Cerberus is a client-policy compliance system, not hostile-client remote attestation. See [`docs/SECURITY_THREAT_MODEL.md`](docs/SECURITY_THREAT_MODEL.md) for known limitations.

## Requirements

- Java 25
- Paper 26.2 for Guardian-Paper
- Velocity 4.x for Guardian-Velocity deployments
- Fabric Loader 0.19.5+ and Fabric API for Cerberus on Minecraft 26.2
- Optional integrations: LuckPerms, Geyser, and Floodgate

## Install

Choose the deployment guide that matches your network:

- [Standalone Paper](docs/INSTALL_STANDALONE_PAPER.md)
- [Velocity + Paper network](docs/INSTALL_VELOCITY_NETWORK.md)

For normal administration, also see [Admission policy](docs/GUARDIAN_ADMISSION.md), [Protection](docs/GUARDIAN_PROTECTION.md), and [upgrades](docs/UPGRADING.md). The detailed [security/threat model](docs/SECURITY_THREAT_MODEL.md) and [key-management guide](docs/KEY_MANAGEMENT.md) are references rather than prerequisites for a basic install.

Packaged defaults enable both Admission and Protection. On a Velocity deployment, set the Paper backends to `admission.authority: velocity` and provision the generated `proxy-assertion.key` as documented in the network install guide.

## Commands

Paper owns `/guardian`; Velocity owns `/guardianv`. The roots intentionally do not proxy to each other.

```text
/guardian status
/guardian validate
/guardian reload
/guardian inspect <player>
/guardian artifacts scan

/guardianv status
/guardianv validate
/guardianv reload
/guardianv inspect <player>
/guardianv artifacts scan
```

On a Velocity-authoritative network, `/guardianv inspect` is the authoritative Admission view. Backend `/guardian inspect` remains assertion-only and does not receive the full Fabric manifest. Active inspection data is memory-only and disappears when the exact connection ends.

## Permissions

Paper administration:

```text
guardian.command.status
guardian.command.validate
guardian.command.reload
guardian.command.inspect
guardian.command.artifacts.scan
```

Velocity administration:

```text
guardian.velocity.command.status
guardian.velocity.command.validate
guardian.velocity.command.reload
guardian.velocity.command.inspect
guardian.velocity.command.artifacts.scan
```

Admission policy/profile permissions:

```text
guardian.admission.profile.<profile-id>
guardian.admission.client.bypass
guardian.admission.client.bypass.<client-key>
guardian.admission.mod.bypass
guardian.admission.mod.bypass.<mod-id>
```

Assign Admission profile/bypass permissions on the authority that evaluates Admission: Paper-side LuckPerms for standalone Paper, or proxy-side LuckPerms when Guardian-Velocity is authoritative.

Protection bypass/notification permissions:

```text
guardian.protection.bypass
guardian.protection.command.bypass
guardian.protection.namespace.bypass
guardian.protection.visibility.bypass
guardian.protection.visibility.bypass.<normalized-command-key>
guardian.protection.notify
```

`guardian.protection.visibility.bypass` is itself the aggregate visibility bypass; no trailing `.*` is required. The per-command form is used only when `protection.visibility.per-command-bypass` is enabled. See [`docs/GUARDIAN_ADMISSION.md`](docs/GUARDIAN_ADMISSION.md) and [`docs/GUARDIAN_PROTECTION.md`](docs/GUARDIAN_PROTECTION.md) for exact semantics.

## Build

Use the repository Gradle wrapper with Java 25. The wrapper is pinned to Gradle 9.7.1.

Windows:

```powershell
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Linux/macOS:

```bash
./gradlew clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

The source default is `1.0.1`. An explicit SemVer build version can still be supplied with `-PguardianVersion=<version>`; the source version does not by itself publish a GitHub release.

## Release signing

Official Cerberus releases are signed with an offline Ed25519 release identity. The private signing key must never be stored in GitHub Actions, the repository, a Minecraft server, or Cerberus itself.

The supported release flow is:

1. run the manual **Release Candidate** GitHub Actions workflow for the exact SemVer version being released;
2. download the resulting `guardian-<version>-release-input` artifact from the GitHub web UI;
3. on the offline/release machine, finalize the exact CI-built artifacts with `tools/release-manager.ps1`;
4. upload the contents of `release-final/` to a **draft GitHub Release**; and
5. publish the draft only after final verification/smoke testing.

Example local finalization:

```powershell
.\tools\release-manager.ps1 `
  -Action finalize-release `
  -Version 1.0.1 `
  -InputDirectory .\release-input `
  -OutputDirectory .\release-final `
  -ReleasePrivateKey D:\GuardianKeys\cerberus-release\cerberus-release-signing.key `
  -ReleasePublicKey D:\GuardianKeys\cerberus-release\cerberus-release-signing.pub `
  -GuardianServerPublicKeys D:\GuardianKeys\server-auth-trust.txt
```

The final directory contains the CI-built Paper/Velocity JARs, the signed Cerberus JAR, the public Cerberus release-verification key, license/notices, release provenance, and final SHA-256 checksums. The unsigned Cerberus input is deliberately not a public release asset. When signed-release trust is enabled, copy the **base64 contents** of `cerberus-release-signing.pub` into `policy.yml`; the SHA-256 values are release/provenance checks and are not the policy trust value. See [`docs/RELEASE_PROCESS.md`](docs/RELEASE_PROCESS.md) for the full operator procedure.

## Documentation

- [Standalone install](docs/INSTALL_STANDALONE_PAPER.md)
- [Velocity network install](docs/INSTALL_VELOCITY_NETWORK.md)
- [Production deployment / rollback runbook](docs/PRODUCTION_RUNBOOK.md)
- [Admission policy](docs/GUARDIAN_ADMISSION.md)
- [Protection](docs/GUARDIAN_PROTECTION.md)
- [Geyser/Floodgate](docs/GEYSER_FLOODGATE.md)
- [LuckPerms profiles](docs/LUCKPERMS_PROFILES.md)
- [Configuration upgrades](docs/UPGRADING.md)
- [Key management and rotation](docs/KEY_MANAGEMENT.md)
- [Release process](docs/RELEASE_PROCESS.md)
- [Security/threat model](docs/SECURITY_THREAT_MODEL.md)
- [Development](docs/DEVELOPMENT.md)

Historical phase documents remain in `docs/` for implementation provenance; administrators do not need them for normal deployment.

## Security and support

Please use the repository issue tracker for ordinary bugs and support questions. Do not publish sensitive vulnerability details in a public issue; follow [`SECURITY.md`](SECURITY.md) instead.

## License and provenance

Guardian/Cerberus is licensed under GPL-3.0-only; see [`LICENSE`](LICENSE). Bundled third-party notices are in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

Guardian began as a hard fork and substantial rewrite of [BrandBlocker by Menacho](https://github.com/Menacho15/BrandBlocker). Guardian Protection also incorporates selected behavior/source lineage from BadWolfMC's GPLv3 fork of [eZProtector by DoNotSpamPls](https://github.com/DoNotSpamPls/eZProtector). The precise provenance boundary is recorded in [`docs/PROVENANCE.md`](docs/PROVENANCE.md).
