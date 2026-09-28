# Phase 5 handoff — Guardian-Velocity productionization and operations foundation

> This handoff is generated from the Phase 4 source tree. Treat the current repository and `Guardian_Cerberus_Authoritative_Project_Plan.md` as authoritative if later changes supersede this summary.

## Entering baseline

Phase 4 productionizes Geyser/Floodgate origin handling and retires BRIDGE-005. Bedrock is a first-class shared-policy client classification on both possible authorities:

- standalone Guardian-Paper can query optional Geyser/Floodgate APIs before Java brand policy;
- Guardian-Velocity retains authoritative proxy-edge origin discovery;
- positive supported API evidence classifies Bedrock and prevents Cerberus;
- provider absence and provider failure are distinct;
- provider failure with no positive Bedrock evidence fails closed as indeterminate rather than falling through to Java;
- disagreement remains a prominent diagnostic;
- Paper's backend Floodgate comparison remains diagnostic-only in Velocity authority mode.

Phase 3 policy remains shared and final. Phase 5 MUST NOT create a Velocity-specific policy schema/evaluator or re-port Paper policy logic.

The final Phase 4 Java 25 / Gradle 9.7.1 gate is green at **141 tests** with zero failures/errors/skips, and the focused live Bedrock/Java regression matrix is complete.

Active implementation debt entering Phase 5 is limited to BRIDGE-003 and BRIDGE-004.

## Phase 5 scope

Productionize Guardian-Velocity as BadWolfMC's preferred network-edge Admission host while preserving standalone Guardian-Paper.

Own the Velocity-specific operational pieces still intentionally deferred:

- final Guardian-Velocity data/config ownership and file-location UX;
- configurable operational timing where appropriate;
- final proxy assertion secret/key provisioning, validation, diagnostics, and rotation expectations;
- explicit deployment-mode diagnostics;
- Phase 0B operational wording cleanup;
- production deployment docs/tests; and
- authority-aware administrator operability described below.

Do not weaken the existing channel-consumption, one-attestation-per-proxy-session, backend assertion-only, or privacy boundaries.

## Operations/observability architecture to implement during Phase 5

### Canonical command tree

Keep it small:

```text
/guardian status
/guardian validate
/guardian reload
/guardian inspect <player>
/guardian artifacts scan
```

Do not add separate `/guardian brand` or `/guardian mods` commands unless new operational evidence establishes a genuine need. `inspect` is the one-stop staff diagnostic.

### Both platform hosts receive the command surface

Phase 5 MUST NOT interpret authority-aware administration as "commands only on Velocity." Implement the `/guardian` router on both Guardian-Paper and Guardian-Velocity, sharing platform-neutral operation/result models where practical while keeping host-specific execution explicit.

- **Standalone Paper authority:** Guardian-Paper exposes the complete command tree and owns Admission + Protection status/validation/reload plus the authoritative active inspection snapshot and artifact-import/catalog workflow.
- **Guardian-Velocity authority:** Guardian-Velocity exposes the complete network-Admission command tree and owns authoritative Admission status/validation/reload, active inspection snapshots, and the authoritative artifact-import/catalog workflow.
- **Paper backend while Velocity is authoritative:** Guardian-Paper still exposes its local command router for backend console/direct administration. `status`, `validate`, and `reload` remain useful for Paper-local configuration, locale/Protection state, and proxy-assertion verifier state. Backend `inspect` may report only locally held assertion/backend evidence and MUST clearly identify Velocity as the authoritative Admission host; it must not receive or reconstruct the full Fabric manifest. Backend `artifacts scan` MUST NOT mutate a non-authoritative local catalog as though it controlled network Admission; report that artifact policy/catalog administration belongs on Guardian-Velocity.

When the same `/guardian` root is registered on Velocity and Paper, normal player-issued commands through the proxy may naturally resolve at Velocity. That does not eliminate the Paper router: it is required for standalone deployments and backend-local console/direct administration. Do not add alternate aliases merely to bypass ordinary proxy command precedence.

The initial Phase 5 implementation does **not** need network-wide command RPC/orchestration. A Velocity reload validates/activates Velocity-owned Admission state; a Paper reload validates/activates that Paper host's local state (including Protection). Do not forward manifests or create a hidden distributed-control channel solely to make one command fan out across hosts.

### Permissions

Use:

```text
guardian.command.status
guardian.command.validate
guardian.command.reload
guardian.command.inspect
guardian.command.artifacts.scan
```

The existing `guardian.artifacts.scan` predates the convention. Guardian is unreleased, so correct it when the command router is implemented rather than retaining a permanent compatibility alias.

### Active inspection snapshots

Do not retain mutable successful Admission sessions for diagnostics.

Create a separate bounded immutable/read-only active inspection snapshot for currently connected players:

- memory-only by default;
- removed at disconnect/session end;
- privacy-conscious and bounded;
- distinct from the Admission state machine.

Authority boundary:

- standalone authority -> Guardian-Paper stores/answers the snapshot;
- Velocity authority -> Guardian-Velocity stores the authoritative manifest/admission snapshot;
- Paper backends must **not** receive full Fabric manifests solely to support `/guardian inspect`.

A normal inspection should emphasize top-level/policy-addressable mods plus total Loader-known counts rather than dumping every nested entry.

### Reload

Use existing atomic runtime seams. Parse/normalize/validate the complete candidate first and activate only a fully valid immutable replacement. Failed reload leaves the previous runtime active; do not partially reload a subset.

Paper reload must continue reconciling Admission/Protection and command visibility. Velocity should expose equivalent authority-appropriate shared-policy reload behavior.

### Validate

Files-only/non-activating. Parse and validate candidate configuration/policy/catalog and report errors without replacing active state.

### Status

Keep concise: Guardian/protocol versions, authority/deployment mode, Admission/Protection enabled state where applicable, policy/profile counts, artifact catalog state, optional integrations, logging mode, and proxy assertion state/configuration.

### Production logging

Implement only:

```yaml
logging:
  level: NORMAL
```

with `NORMAL` and `DEBUG`.

`NORMAL` should produce roughly one useful authoritative Admission summary per connection. On a Velocity network, Guardian-Velocity normally emits the successful summary and Paper avoids duplicate success chatter.

Security/configuration anomalies remain visible regardless of mode: invalid proxy assertions, Geyser/Floodgate disagreement/failure, malformed protocol, integration/provider failure, invalid configuration/reload, channel leakage, etc.

`DEBUG` may show lifecycle details but must not automatically dump the complete Fabric manifest. Deliberate `/guardian inspect` is preferred for player-specific mod information.

## Maintainability requirements

Do not add command routing, inspection storage, and logging policy directly into the already-large `PaperAdmissionAdapter` or `GuardianVelocityPlugin`. Extract focused services/components.

Also close the administrator-tooling edge where `artifact-import-rules.yml` currently generates `allow-<mod-id>` IDs that can exceed the 64-character policy rule-ID limit for a maximum-length valid Fabric mod ID. Use deterministic shortening/suffixing so every generated rule remains pasteable.

## Phase 5 boundaries

Do not pull forward Phase 6 hostile-client/signing work or the Minecraft 26.3 port.

Do not turn backend Floodgate sanity evidence into a second Admission authority.

Do not forward full manifests to Paper backends for convenience.

Do not reintroduce username-prefix trust.

Do not redesign the shared Phase 3 Admission policy.
