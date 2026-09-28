# Phase 4 — Geyser/Floodgate productionization

Date: 2026-09-27
Project version: `0.1.0-phase4`

## Scope and gap analysis

Phase 4 is smaller than the original roadmap suggested because Phase 0B already proved most of the proxy-side integration and Phases 1–3 preserved it.

Already present before this pass:

- Guardian-Velocity queries supported Geyser/Floodgate APIs before Java brand policy;
- positive supported API evidence can classify a connection as Bedrock;
- the Phase 3 shared policy already exposes configurable `clients.bedrock` `ALLOW`/`DENY` behavior;
- Bedrock does not enter the Cerberus path when classified correctly;
- Guardian-Paper in Velocity authority mode verifies the trusted proxy assertion and compares proxy origin with backend Floodgate state without re-evaluating policy;
- username prefixes have no classification authority.

The genuine Phase 4 gaps were:

1. standalone Guardian-Paper did not yet query Geyser/Floodgate and therefore remained Java-brand-only;
2. provider absence and provider API failure were conflated as one `UNAVAILABLE` state;
3. a failed origin-provider query could therefore fall through into Java classification;
4. mismatch/failure semantics were not yet promoted from feasibility behavior into an explicit production contract;
5. BRIDGE-005 lacked focused production tests/documentation; and
6. the operations/observability architecture needed to be recorded before Phase 5.

## Current API verification

The implementation continues to use supported public APIs only.

- Geyser documents `GeyserApi#isBedrockPlayer(UUID)` and explicitly states it can be used in pre-login events. `GeyserApi.api()` may be null until Geyser has enabled. Current project dependency: `org.geysermc.geyser:api:2.11.2-SNAPSHOT`.
- Floodgate documents `FloodgateApi#isFloodgatePlayer(UUID)` and likewise states it can be used in pre-login events. On backend servers behind a proxy, Floodgate documents that `send-floodgate-data: true` and the same `key.pem` are required for backend API data. Current project dependency: `org.geysermc.floodgate:api:2.2.5-SNAPSHOT`.
- Geyser's Paper/Spigot plugin identifies itself as `Geyser-Spigot`, so Guardian-Paper declares it as an optional soft dependency.

No NMS, implementation reflection, packet library, server Mixin, or implementation-only Geyser/Floodgate API is introduced.

## Production origin-evidence model

`guardian-core` now owns a small platform-neutral origin-evidence model:

- `BedrockSignal.UNAVAILABLE` — integration is not installed/enabled on this host;
- `BedrockSignal.NOT_BEDROCK` — supported API answered cleanly and negatively;
- `BedrockSignal.BEDROCK` — supported API positively identifies the connection;
- `BedrockSignal.ERROR` — an integration expected to answer could not be queried reliably.

`BedrockEvidence` resolves the combined Geyser/Floodgate evidence as follows:

1. any positive supported API evidence -> `BEDROCK`;
2. otherwise, any provider query error -> `INDETERMINATE`;
3. otherwise -> `JAVA`.

This deliberately makes positive trusted origin evidence stronger than a negative/absent/unhealthy secondary provider while preventing an integration failure with no positive evidence from silently becoming Java.

Contradictory explicit Geyser/Floodgate answers remain a prominent diagnostic. Positive supported API evidence still classifies that connection as Bedrock, preserving the invariant that a positively identified Bedrock client must never receive a Cerberus challenge.

## Guardian-Velocity

The existing `BedrockDetector` now distinguishes provider absence from provider failure.

During `PlayerConfigurationEvent`:

- origin evidence is resolved before Java brand policy;
- explicit Geyser/Floodgate disagreement is warned;
- `INDETERMINATE` origin fails closed with `CONFIGURATION_ERROR` instead of proceeding as Java;
- resolved Bedrock feeds the same Phase 3 shared client-policy evaluator as every other class;
- Bedrock therefore remains a normal configurable Guardian classification, not a bypass.

This pass intentionally does not change Phase 5-owned Velocity operational timing, proxy-secret provisioning, data-directory UX, or the remaining Phase 0B operational logging/naming tracked by BRIDGE-004.

## Standalone Guardian-Paper

Standalone Paper now performs the same supported origin discovery before Java brand classification.

`PaperBedrockDetector` queries optional `Geyser-Spigot` and Floodgate installations. `guardian-paper` now declares the Geyser API as `compileOnly` and adds `Geyser-Spigot` to `softdepend` alongside Floodgate and LuckPerms.

The standalone sequence is now:

```text
supported Geyser/Floodgate evidence
        ↓
BEDROCK? ── yes ──> shared clients.bedrock policy ──> allow/deny; never Cerberus
        │
        no
        ↓
provider failure with no positive evidence?
        ├── yes -> CONFIGURATION_ERROR / fail closed
        └── no  -> Java brand classification -> ordinary Phase 3 policy
```

A Java connection remains Java regardless of any Bedrock-style username prefix.

## Backend Floodgate sanity check

In Velocity authority mode Guardian-Paper still compares the authenticated proxy assertion's `ConnectionOrigin` against backend Floodgate state when available.

The production rule is now explicit:

- agreement may be logged as ordinary diagnostic information;
- backend Floodgate absence/query failure is observable but does not create a second policy authority;
- disagreement is prominently warned as security-relevant network/integration evidence;
- Guardian-Paper does **not** overturn or independently re-evaluate a valid Guardian-Velocity admission solely because of this backend sanity check.

This preserves the established privacy/authority boundary: the proxy owns player Admission policy and the backend receives only the trusted final assertion, not the full Fabric manifest.

## Tests added/updated

Phase 4 adds focused coverage for:

- positive supported Bedrock evidence winning over a contradictory or unhealthy secondary provider;
- clean provider absence/negative evidence resolving Java;
- provider query failure with no positive evidence resolving indeterminate/fail-closed;
- disagreement only when both providers returned explicit contradictory answers;
- standalone Paper performing supported Bedrock discovery before Java brand classification;
- standalone Paper optional Geyser/Floodgate dependencies;
- Velocity provider failure semantics and removal of feasibility-specific Bedrock wording;
- backend Paper Floodgate sanity check remaining diagnostic-only; and
- shared Bedrock policy remaining administrator-configurable, including explicit denial.

## Operations/observability architecture recorded now

The authoritative plan now records the architecture that Phase 5 must implement while productionizing Guardian-Velocity:

- small canonical command tree: `status`, `validate`, `reload`, `inspect`, `artifacts scan`;
- no separate `brand`/`mods` commands without later evidence of need;
- permissions under `guardian.command.*`;
- bounded in-memory active inspection snapshots separate from mutable Admission sessions;
- authoritative inspection state on Paper in standalone mode and on Velocity in proxy-authoritative mode;
- no full-manifest forwarding to Paper merely to make backend inspection easier;
- atomic reload and non-activating files-only validation;
- concise status output;
- `NORMAL`/`DEBUG` production logging semantics;
- authoritative-host-only normal success summaries on Velocity networks; and
- future correction of the pre-convention `guardian.artifacts.scan` permission plus deterministic shortening for generated `allow-<mod-id>` rule IDs that would exceed policy bounds.

This is architecture/sequencing work only. Phase 4 does not implement the full Phase 5 command router, inspection snapshot storage, logging control, final Velocity configuration UX, or proxy-secret provisioning.

## BRIDGE-005

The final Java 25 / Gradle 9.7.1 gate and focused live regression checks are green as of 2026-09-28. BRIDGE-005 is therefore retired by Phase 4.

BRIDGE-003 and BRIDGE-004 remain active and owned by Phase 5. No Phase 4 source debt remains.
