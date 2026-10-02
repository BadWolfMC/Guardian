# Phase 7 verification — operations, UX, documentation, and release hardening

## Status

**Implementation candidate; final Java 25 / Gradle 9.7.1 gate and focused live closeout still required.**

The entering repository retains the final Phase 6 XML evidence:

- guardian-core: 117 tests / 0 failures / 0 errors / 4 skipped
- guardian-paper: 91 / 0 / 0 / 4
- guardian-protection: 20 / 0 / 0 / 0
- guardian-protocol: 50 / 0 / 0 / 0
- guardian-velocity: 58 / 0 / 0 / 3
- cerberus-fabric: 19 / 0 / 0 / 0
- total: **355 tests, 0 failures, 0 errors, 11 documented Windows symlink-privilege skips**

Those are **entering Phase 6 results only** and must not be represented as execution of the Phase 7 candidate.

Phase 7 adds ten ordinary test methods: five configuration-migration tests, one public-default schema/resource regression, one representative Phase 6 → public schema-2 Paper/policy upgrade/load test, one representative Phase 6 → schema-2 Velocity config upgrade/load test, and two normal-summary prefix regressions (Paper and Velocity). The final executed total must be taken from the new Gradle XML rather than inferred.

## Sandbox limitation

The implementation environment exposes OpenJDK 21 only and has no cached Gradle 9.7.1 distribution. Outbound DNS/bootstrap access to `services.gradle.org` is unavailable. Therefore no Java 25/Gradle 9.7.1 result is claimed from this sandbox.

Before closeout, run on the supported toolchain:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Expected toolchain: Java 25 and Gradle 9.7.1.

## Completed sandbox-level checks

These checks **do not replace** the supported Java 25 / Gradle gate, but were completed against the candidate source:

- all `guardian-protocol`, `guardian-core`, and `guardian-protection` main Java sources plus all Cerberus `releaseTool` sources compiled together in a dependency-isolated smoke using the available JDK;
- direct schema-migration smoke preserved comments/CRLF/original backup bytes, added the disabled release-trust block, and was idempotent on schema 2;
- direct Cerberus release-key generation refused overwrite;
- direct Guardian server-identity generation plus Cerberus signing succeeded against the retained Phase 6 unsigned JAR, embedded one public Guardian trust anchor, and leaked no private-key-like resource name;
- both GitHub workflow YAML files parsed successfully;
- production-source scans found no NMS/CraftBukkit implementation, ProtocolLib/PacketEvents, or reflection workaround patterns introduced by Phase 7;
- locale required-key/duplicate-key checks passed for the candidate English catalog;
- normal Paper/Velocity Admission summary source no longer prepends its own `Guardian ` product label; and
- the retained post-ZIPFS Phase 6 signed JAR independently re-hashed to the operator-supplied finished SHA-256.

The sandbox does not provide PowerShell, so `tools/release-manager.ps1` itself still requires the documented Windows execution check even though the Java generators/signer it orchestrates were smoke-tested directly.

## Automated Phase 7 coverage

The added migration tests verify:

- policy schema-1 → schema-2 migration preserves administrator comments and values;
- the missing signed-release trust block is backfilled disabled;
- exact CRLF pre-migration bytes are preserved in the backup;
- schema 2 and newer schema markers are not rewritten by the migrator;
- malformed YAML is left to normal strict recovery;
- publication fails closed if an administrator changes the source after migration preparation; and
- representative final Phase 6 Paper config/policy with administrator-owned values migrates, then successfully loads through the real schema-2 config and Admission policy loaders; and
- a representative final Phase 6 Velocity config preserves DEBUG/timing administrator values and loads through the real schema-2 Velocity loader after migration.

Existing loader/resource/command/session tests continue to own the underlying validated reload, inspection, authority, policy, filesystem, and protocol behavior.

## Clean-install test

Automated resource coverage verifies that packaged Paper defaults include schema-2 `config.yml` and `policy.yml` while the locale/catalog schemas remain independently versioned. Final platform verification should additionally start clean Paper and Velocity data directories from the newly built artifacts and run the corresponding `status` and `validate` commands.

## Upgrade test

Use a copy of a final Phase 6/pre-1.0 data directory, not a production original. Confirm:

1. schema-1 config/policy are upgraded once;
2. exact `.pre-schema2-...bak` files are created;
3. administrator-owned values/comments remain intact;
4. a missing `cerberus-release-trust` block is added as `required: false` with no keys;
5. `validate` succeeds after migration; and
6. a second restart performs no second schema migration.

Older arbitrary internal development schemas are not a supported automatic-compatibility contract.

## Release helper / signing verification

Use throwaway secure directories:

```powershell
.\tools\release-manager.ps1 -Action generate-release-key -OutputDirectory C:\temp\guardian-release-key
.\tools\release-manager.ps1 -Action generate-server-identity -OutputDirectory C:\temp\guardian-server-key
.\tools\release-manager.ps1 -Action sign-cerberus `
  -Version 0.1.0-phase7-smoke `
  -ReleasePrivateKey C:\temp\guardian-release-key\cerberus-release-signing.key `
  -GuardianServerPublicKeys C:\temp\guardian-server-key\guardian-server-auth.pub `
  -SignedOutput C:\temp\guardian-release\cerberus-fabric-phase7-signed.jar
.\tools\release-manager.ps1 -Action checksums -ArtifactDirectory C:\temp\guardian-release
```

Verify generation refuses overwrite and signing refuses a missing/invalid version or missing/implicit output. Compare the helper's **finished JAR SHA-256** with `Get-FileHash`; do not confuse it with the signer's canonical logical-JAR SHA-256.

## Artifact / private-key inspection

After the clean build, inspect every final JAR. Required checks:

- no `proxy-assertion.key`;
- no `guardian-server-auth.key`;
- no `cerberus-release-signing.key`;
- no private-key-like `.pem`, `.key`, `.p8`, or `.pk8` resource;
- no PEM private-key block in non-class resources;
- no administrator-local absolute filesystem path/configuration;
- Paper/Velocity contain GPL, third-party notice, and Apache-2.0 material; and
- Cerberus contains GPL/notice material plus only intended **public** server trust anchors/release metadata.

The Phase 6 signed JAR retained in the supplied archive independently re-hashes to:

`20ea9b03da3e9f9c03739eb361304a398e34d9677f465169f26b82b29166e860`

That is the finished JAR hash supplied by the operator and remains distinct from Cerberus's canonical signing digest.

## Dependency-license / NOTICE audit

Final Guardian-Paper and Guardian-Velocity distribution logic shades SnakeYAML Engine 2.10 and embeds its Apache-2.0 license. The platform APIs remain provided/compile-only integrations. `THIRD_PARTY_NOTICES.md` records the final license inventory and should be reviewed against the resolved release dependency graph from the successful Gradle build before publication.

## Focused live closeout

Do **not** repeat the Phase 6 adversarial matrix. Only changes-sensitive checks are justified:

1. clean standalone Paper startup, `/guardian status`, `/guardian validate`;
2. representative schema-1 upgrade using a copied test data directory;
3. signed Cerberus happy path using the Phase 7 release helper output;
4. one server-authentication key mismatch to confirm stock Cerberus still withholds its manifest and the player-facing timeout text now directs an untrusted-Guardian report to staff; and
5. one Velocity-authoritative happy path with `/guardianv status` plus a normal connection, confirming one authoritative summary without duplicated `Guardian` product text and assertion-only backend behavior.

No full mod-policy/quarantine/replay/fuzz matrix is required unless the final Java/Gradle gate or these focused checks expose a regression.

## Final closeout fields

Record after execution:

- Java version:
- Gradle version:
- final test totals by module:
- final Paper JAR SHA-256:
- final Velocity JAR SHA-256:
- final unsigned Cerberus JAR SHA-256:
- final signed Cerberus JAR SHA-256:
- release candidate version:
- clean-install result:
- schema-upgrade result:
- signed-client result:
- server-auth mismatch UX result:
- Velocity/log-prefix result:
- final adversarial audit result:

Phase 7 closes only when those fields are backed by actual execution evidence.
