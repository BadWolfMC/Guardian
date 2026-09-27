# Phase 2.5 verification — artifact identity and approved-artifact catalog

## Current gate status — 2026-09-26

**Phase 2.5 is complete.** The operator confirmed that the clean Java 25 / Gradle 9.7.1 build/test gate remains green after the Phase 2.5 patch, and the focused live artifact-catalog plus standalone/Velocity admission regressions all passed.

The source tree contains **102 `@Test` cases**, up from the Phase 2 baseline of 85.

## 1. Required clean automated gate

From the repository root with Java 25 active:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Required result:

- Gradle wrapper reports 9.7.1;
- JVM reports Java 25;
- all 102 tests pass with zero failures/errors;
- Guardian Paper and Velocity artifacts build;
- Cerberus Fabric builds; and
- produced artifacts report project version `0.1.0-phase2.5`.

## 2. New automated coverage

Phase 2.5 adds or extends coverage for:

- stable SHA-256 of known bytes and file-size bounding;
- required protocol-v1 artifact-hash capability;
- digest round-trip and deterministic canonical serialization;
- malformed digest length/model rejection;
- required hashes for top-level archives and forbidden hashes on non-applicable entries;
- realistic large nested manifests with only the top-level archive hashed;
- built-in, directory, nested, and mixed-origin serialization;
- path-privacy regression;
- multiple versions and multiple hashes for one ID/version;
- idempotent rescanning;
- add-new and delete-input-with-history-retained behavior;
- malformed/non-Fabric and malformed-ZIP rejection;
- bounded artifact count, ZIP-entry count, and `fabric.mod.json` reading;
- flat-directory behavior;
- duplicate/conflicting catalog input;
- deterministic catalog output; and
- existing Phase 2 nonce/canonical/protocol response validation with the revised required capability mask.

Tests create realistic fixture JARs at runtime. No third-party mod artifact is committed under test resources.

## 3. Source-level checks completed in the implementation sandbox

Because Java 25/Gradle 9.7.1 could not be provisioned without network access, these checks are supporting evidence only and do not replace Section 1:

- `guardian-protocol` and `guardian-core` main sources compile directly under the available Java 21 compiler, demonstrating no syntax/type regression in the pure-Java boundary;
- all **48** protocol/core `@Test` methods execute successfully under a lightweight local JUnit-compatible runner against those compiled classes;
- the two Paper resource tests execute successfully with the raw packaged resources, including the new `/guardian artifacts scan` command/permission declaration;
- a standalone smoke harness imported generated Fabric JAR fixtures, verified deterministic/idempotent catalog merge and retained entries after input deletion, and round-tripped a hashed manifest; and
- repository inspection found no bundled approved-artifact JARs or third-party mod fixtures.

## 4. Focused manual verification — PASS

The operator completed the narrow live verification matrix successfully:

1. `artifact-import/` was created automatically without importing anything on startup. Six real Fabric JARs were placed in the flat input directory and `/guardian artifacts scan` imported all six. Every generated mod ID, version, and SHA-256 was independently compared with the source artifact and matched. Deleting all input JARs and rescanning left the durable catalog unchanged. Additional versions of existing IDs were later imported successfully, confirming deterministic multi-version merge behavior.
2. A normal standalone Paper Fabric + Cerberus connection advertised protocol `1..1` with capabilities `15`, completed the established CONFIGURATION-presence → bounded PLAY challenge/response flow, validated the representative 166-entry manifest under the revised protocol-v1 hash contract, produced `ALLOW / CERBERUS_VERIFIED`, and released quarantine normally.
3. A normal Velocity-authoritative Fabric + Cerberus connection completed CONFIGURATION attestation against the same 166-entry manifest, produced `ALLOW / CERBERUS_VERIFIED`, sent the trusted proxy admission assertion, and Guardian-Paper accepted `PROXY_ADMISSION_VERIFIED` without performing a redundant backend Cerberus attestation.

The Phase 2 failure matrix does not need to be repeated unless later policy integration materially changes these transport paths.

## 5. Security/hardening closeout criteria — PASS

The Phase 2.5 closeout criteria are satisfied:

- no absolute client filesystem path appears in manifest encoding/logging;
- no approved-artifact JAR is packaged in Guardian/Cerberus outputs;
- the scanner contains no classloading/execution/extraction/install path;
- malformed input cannot partially rewrite `artifacts.yml`;
- deleting scanner inputs never deletes catalog history;
- BRIDGE-003 and BRIDGE-005 retain their later-phase ownership; BRIDGE-004 remains active under the revised split ownership recorded in `IMPLEMENTATION_BRIDGES.md` (Phase 3 shared-policy consumption, Phase 5 final Velocity productionization); and
- documentation continues to state that reported SHA-256 is not hostile-client remote attestation.
