# Phase 7.5 handoff — production readiness, GitHub preparedness, and final 26.2 hardening

## Entering state

Phase 7 is formally complete at source default `0.1.0-phase7`. The final Java 25 / Gradle 9.7.1 gate is green at **366 tests, 0 failures, 0 errors, and 11 documented Windows symlink-privilege skips**.

The accepted baseline is `Guardian(20261005-120941).zip`, SHA-256 `caf5b57930b6cad2c0a36e7c6d941e4f0ff099814280ec8bebde2448007b5b24`, excluding the root-level `velocity-plugin.json` that was manually extracted only for operator inspection.

Phase 7 verified clean standalone Paper and Velocity operation, schema-1 → schema-2 migration, release/key/checksum helpers, version-bearing signed Cerberus output, signed-Cerberus Velocity Admission, server-authentication mismatch UX/privacy, and metadata/version parity across Paper, Velocity, and Cerberus. No active implementation bridge remains.

## Purpose

Phase 7.5 exists because BadWolfMC remains on Minecraft 26.2 long enough to perform a final production-readiness pass before introducing the platform-change risk of the 26.3 port. It is not a feature phase.

The work should focus on:

- final source/repository hygiene and dead-code/TODO/generated-file review;
- GitHub/public-project preparedness, including CI/release workflow usability, repository metadata, contribution/security/support expectations, and release-page/checksum material where appropriate;
- release-manager and release-candidate rehearsal from a clean checkout;
- production deployment, backup, rollback, key-rotation, compromise-response, and recovery runbooks;
- clean install plus representative upgrade/reload/rollback testing on 26.2;
- focused live BadWolfMC deployment/testing justified by the production configuration;
- final documentation cleanup/rewrite so installation and routine operations are approachable without sacrificing the detailed security reference;
- final license/provenance/dependency-notice/release-artifact inspection; and
- small concrete hardening defects discovered by those activities.

## Guardrails

Preserve the completed architecture:

- Admission and Protection remain independent domains.
- Guardian-Velocity remains BadWolfMC's preferred network Admission authority; Guardian-Paper remains standalone-capable.
- Velocity-authority Paper remains assertion-only and does not receive full Fabric manifests.
- `/guardian` remains Paper-local and `/guardianv` remains Velocity/network-authoritative; do not introduce command RPC for convenience.
- Bedrock classification through supported Geyser/Floodgate APIs precedes Java/Cerberus handling.
- inspection remains bounded, memory-only, active-session-only, and exact-connection-owned.
- signed Cerberus release provenance remains compliance/provenance hardening, not remote attestation.
- Guardian → Cerberus Ed25519 server authentication remains manifest-disclosure protection, not hostile-client attestation.
- `proxy-assertion.key`, `guardian-server-auth.key`, and the Cerberus release-signing key remain distinct trust domains.
- no reusable long-term secret belongs in Cerberus.
- no NMS/CraftBukkit implementation reflection/server Mixins/ProtocolLib/PacketEvents/private-packet workaround.
- do not begin the Minecraft/Paper/Fabric 26.3 port.

## Suggested review questions

Before making changes, distinguish genuine first-public-release blockers from optional polish. In particular:

1. Can a new administrator follow the README/install/release/runbook path without reconstructing architecture from historical phase documents?
2. Can a release manager produce, verify, checksum, sign, and stage the three deliverables from a clean checkout with no undocumented local state?
3. Are GitHub workflows least-privilege and suitable for public visibility/forks/Dependabot/release use?
4. Are backup/rollback and all three key-domain rotation/compromise procedures operationally testable, not merely described?
5. Are repository-generated files/build outputs/secrets protected from accidental commit or release packaging?
6. Do public-facing docs clearly separate ordinary operator instructions from security/threat-model reference material?
7. Is any remaining phase-history wording, stale version label, TODO, diagnostic flag, example, or development-only artifact inappropriate for a first public release?
8. Can the real BadWolfMC 26.2 production topology be deployed/rolled back with a small, explicit test matrix rather than repeating Phase 6 adversarial work?

## Expected closeout

Phase 7.5 should close with:

- a green Java 25 / Gradle 9.7.1 gate;
- a clean-checkout release rehearsal;
- final production deployment/rollback evidence on 26.2;
- final GitHub/repository/public-doc readiness review;
- final distributable/license/provenance/private-key leakage inspection;
- no active implementation bridges;
- a documented decision on the first public release/versioning state; and
- an updated Phase 8 handoff containing only the remaining 26.3 platform-port work.
