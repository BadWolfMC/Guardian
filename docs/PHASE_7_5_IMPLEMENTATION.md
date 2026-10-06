# Phase 7.5 implementation — production readiness, GitHub preparedness, and final 26.2 hardening

## Scope

Phase 7.5 is a release/operations hardening pass on the completed 26.2 architecture. It does not alter Guardian's Admission/Protection split, protocol v1 semantics, manifest ownership, three-key-domain model, or supported public API boundary.

## Release provenance and offline signing

The release-candidate workflow now produces an explicitly named **offline-signing input** rather than a publishable-looking release directory. It contains the CI-built Paper/Velocity JARs, an explicitly `-unsigned.jar` Cerberus input, a CI-built `cerberus-release-tools-<version>.jar`, GPL/third-party notices, `RELEASE_INPUT.json`, and `SHA256SUMS-CI.txt`.

`RELEASE_INPUT.json` binds the candidate to:

- the requested SemVer/RC version;
- the exact Git commit SHA built by Actions;
- the GitHub repository; and
- the GitHub Actions run ID + attempt.

The Cerberus Gradle signing task no longer depends on or discovers the local `jar` task output. `signCerberusRelease` requires `-PcerberusUnsignedJar=<explicit CI artifact>`, validates its Fabric ID/version against the requested release version, refuses an existing output, and then signs those exact bytes.

A new offline `verifyCerberusRelease` task verifies:

- embedded release identity;
- expected release version;
- canonical digest;
- Ed25519 signature against the supplied Cerberus release public key; and
- optional embedded Guardian server-authentication trust anchors against the intended public trust file.

The release-candidate build also packages the offline signer/verifier with the shared protocol classes. Normal finalization therefore uses release code compiled and checksummed by the same CI run as the unsigned client rather than recompiling signer code locally.

The Windows release manager adds two normal-path actions:

- `finalize-release` — verify the downloaded CI input, sign exact Cerberus bytes, construct the public artifact directory, generate provenance/checksums, and run final verification;
- `verify-release` — rerun final verification without signing again.

The final directory is intentionally restricted to the two Guardian JARs, signed Cerberus JAR, LICENSE, third-party notices, release provenance, and final checksums. The verifier also rejects private-key-like resources/PEM material and obvious machine-local build paths in packaged text resources. The unsigned Cerberus input cannot accidentally become a final release asset through this workflow.

The public workflow deliberately remains compatible with GitHub Desktop + GitHub web UI. No GitHub CLI, API token, or release-signing secret is required in Actions. The operator uploads `release-final/` to a draft GitHub Release after local/offline verification.

## Public repository readiness

The repository landing material is rewritten around the product rather than development phase history:

- concise Guardian/Cerberus purpose and trust limitation;
- supported targets;
- installation paths;
- commands and permission nodes;
- Java 25 / wrapper build instructions;
- optional offline signing flow;
- administrator documentation navigation; and
- brief BrandBlocker/eZProtector provenance acknowledgement.

Added public-maintenance material is deliberately minimal:

- `SECURITY.md` for private vulnerability-reporting guidance;
- `CONTRIBUTING.md` for build/security-boundary expectations; and
- weekly Dependabot checks for Gradle and GitHub Actions targeting `develop`.

Both CI workflows use read-only repository permissions, disable persisted checkout credentials after source checkout, and pin third-party Actions to immutable release commit SHAs, with the corresponding release version retained as a comment for operator readability. Dependabot remains responsible for proposing controlled updates instead of allowing mutable major-version tags to move underneath a release build.

Local release staging directories are ignored by Git, and PNG assets are treated as binary repository content. Production source comments/generated catalog guidance no longer refer to implementation phases, while the deliberately gated `guardian.cerberus.dev.*` test switches remain available without being advertised in normal Cerberus startup logging.

## Production defaults and Cerberus presentation

The packaged Paper default now enables both independent domains:

```text
Admission:  enabled
Protection: enabled
```

Administrators may still disable either domain independently. The packaged help destination is project-facing rather than pointing third-party installations at BadWolfMC's general website.

The supplied `cerberus-voxels-128.png` is now packaged as the Fabric mod icon and referenced from `fabric.mod.json`.

## Production operations

`PRODUCTION_RUNBOOK.md` provides the short operational path for the real BadWolfMC topology:

- backups and pre-change evidence;
- Velocity-first/backend-by-backend deployment;
- proxy assertion provisioning/fingerprint checks;
- Guardian server-auth and release-signing key placement;
- validate/reload versus restart choices;
- small smoke matrix;
- binary/config/schema rollback;
- emergency recovery when Velocity Admission is unavailable;
- key mismatch diagnosis; and
- rotation/compromise procedures for all three key domains.

`KEY_MANAGEMENT.md`, install guides, upgrade guidance, and release documentation link to this operator path rather than requiring an administrator to reconstruct the procedure from architectural material.

## Versioning decision

During the Phase 7.5 follow-up, the source default advances from the internal phase label to `1.0.0` across all modules. This is a release-readiness/versioning cleanup, **not** publication of a GitHub release. Explicit release-candidate or rehearsal builds may still override the version with `-PguardianVersion=<version>` (for example `1.0.0-rc.1`).

Historical internal `0.1.0-phase*` versions and pre-release protocol/config shapes do not create arbitrary compatibility obligations. The existing deliberate schema-1 -> schema-2 migration remains the only pre-public compatibility bridge.


## 2026-10-05 operator follow-up

The first Java 25 gate after the reviewed Phase 7.5 checkpoint exposed two stale test expectations rather than runtime defects: the filesystem-hardening test still expected literal per-key `.gitignore` entries after the repository moved to the stronger `*.key` rule, and the Paper runtime-default test still expected Protection to be disabled after Phase 7.5 intentionally enabled it. Both expectations were corrected. The same operator run exposed invalid PowerShell interpolation (`$name:`) in three release-manager error strings; those strings now use `${name}:`, and a static regression check guards the script against reintroducing that parser hazard.
