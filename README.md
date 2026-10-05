# Guardian / Cerberus

Guardian is BadWolfMC's GPLv3 client-admission and Paper server-protection project for Minecraft 26.2. Cerberus is its cooperative Fabric client component.

Guardian has two independent domains:

- **Admission** — client classification, optional Cerberus manifest evaluation, shared profiles/policy, Bedrock classification, and trusted Velocity-to-Paper admission.
- **Protection** — Paper-local command execution, visibility, namespace, bypass, and staff-notification policy.

BadWolfMC's preferred network deployment is **Guardian-Velocity as the Admission authority** with Guardian-Paper on each backend. Guardian-Paper also remains fully supported as a standalone Admission authority. Guardian Protection never requires Velocity or Cerberus.

> Guardian/Cerberus is a client-policy compliance system, not hostile-client remote attestation. A sufficiently modified client can lie about its environment. See `docs/SECURITY_THREAT_MODEL.md`.

## Current release-hardening state

Phases 0 through 6 are closed. Phase 7 is in final closeout. The verified Phase 7 candidate is green at **365 tests, 0 failures, 0 errors, 11 documented Windows symlink-privilege skips**, and its clean-install, supported schema-upgrade, release-helper, signed-Cerberus, server-authentication-mismatch, and Velocity-authoritative checks have passed.

The source default is `0.1.0-phase7`; final release candidates may supply an explicit version using `-PguardianVersion=<version>`. One final rebuild/status confirmation is required after the closeout patch that removes the stale hard-coded Velocity Phase 6 version and makes the release helper generate a version-bearing signed Cerberus filename automatically. See `docs/PHASE_7_VERIFICATION.md`.

The authoritative architecture remains `docs/Guardian_Cerberus_Authoritative_Project_Plan.md`.

## Requirements

- Java 25
- Paper 26.2 for Guardian-Paper
- Velocity 3.4.x for Guardian-Velocity deployments
- Fabric Loader/Fabric API matching the Cerberus 26.2 build
- LuckPerms, Geyser, and Floodgate are optional integrations unless your deployment uses the corresponding features

Do **not** begin a 26.3 port on this branch; that is Phase 8.

## Administrator documentation

- Standalone Paper: `docs/INSTALL_STANDALONE_PAPER.md`
- Velocity network: `docs/INSTALL_VELOCITY_NETWORK.md`
- Geyser/Floodgate: `docs/GEYSER_FLOODGATE.md`
- LuckPerms profiles and `/lp` vs `/lpv`: `docs/LUCKPERMS_PROFILES.md`
- Admission policy: `docs/GUARDIAN_ADMISSION.md`
- Protection: `docs/GUARDIAN_PROTECTION.md`
- eZProtector migration: `docs/EZPROTECTOR_MIGRATION.md`
- Locale/messages: `docs/LOCALE_CUSTOMIZATION.md`
- Configuration upgrades: `docs/UPGRADING.md`
- Three key domains and rotation: `docs/KEY_MANAGEMENT.md`
- Security/threat model: `docs/SECURITY_THREAT_MODEL.md`
- Release manager workflow: `docs/RELEASE_PROCESS.md`

## Commands

Paper owns `/guardian`; Velocity owns `/guardianv`. They intentionally do not proxy to one another.

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

On a Velocity-authoritative network, `/guardianv inspect` is the authoritative Admission view and may show the active Fabric manifest. Backend `/guardian inspect` remains assertion-only. Active inspection is memory-only and disappears when the exact connection ends.

## Configuration schema and upgrades

The public-release configuration schema is **2** for Paper `config.yml`, Velocity `config.yml`, and shared `policy.yml`. Guardian contains one deliberately narrow automatic upgrade from the final Phase 6 / pre-1.0 release-candidate schema 1 form to schema 2. It preserves administrator text/comments, creates an exact pre-migration backup, changes the schema marker, and adds the safe disabled `cerberus-release-trust` block when missing from `policy.yml`.

Guardian does not claim compatibility with arbitrary unreleased historical schemas and will not auto-downgrade a newer schema. See `docs/UPGRADING.md`.

## Build and verification

Use the repository wrapper with Java 25 / Gradle 9.7.1:

```powershell
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

The normal CI workflow runs the same gate. Release candidates use the manual release-candidate workflow and an explicit `guardianVersion`.

## Release signing

Cerberus release signing is intentionally offline. CI builds **unsigned** Cerberus artifacts and never receives the release-signing private key.

On Windows, `tools/release-manager.ps1` wraps the approved generation/signing/checksum tasks. The normal signing path takes an explicit output directory and creates a version-bearing signed JAR name automatically:

```powershell
.\tools\release-manager.ps1 -Action generate-release-key -OutputDirectory D:\GuardianKeys\cerberus-release
.\tools\release-manager.ps1 -Action generate-server-identity -OutputDirectory D:\GuardianKeys\server-auth-next
.\tools\release-manager.ps1 -Action sign-cerberus -Version 1.0.0 -ReleasePrivateKey D:\GuardianKeys\cerberus-release\cerberus-release-signing.key -GuardianServerPublicKeys D:\GuardianKeys\server-auth-trust.txt -OutputDirectory D:\GuardianRelease\1.0.0
.\tools\release-manager.ps1 -Action checksums -ArtifactDirectory D:\GuardianRelease
```

The SHA-256 of the finished signed JAR is release/provenance metadata. It is intentionally distinct from the canonical SHA-256 printed by the Cerberus signing tool.

## License and provenance

Guardian/Cerberus is GPLv3. Guardian began as a hard fork and substantial rewrite of BrandBlocker and Guardian Protection incorporates selected behavior from the known GPLv3 BadWolfMC eZProtector lineage. Exact provenance is recorded in `docs/PROVENANCE.md`.

Bundled third-party notices are in `THIRD_PARTY_NOTICES.md`; final Paper/Velocity JARs embed the GPL material, notices, and the Apache-2.0 license for bundled SnakeYAML Engine.
