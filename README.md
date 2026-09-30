# Guardian / Cerberus

Guardian/Cerberus is being developed against the architecture described in:

`docs/Guardian_Cerberus_Authoritative_Project_Plan.md`

That document is the living project contract and should be updated when an implementation result intentionally changes an architectural assumption.

## Phase 0A status: complete

Live Paper 26.2 / Fabric 26.2 testing established:

- Fabric -> Paper CONFIGURATION payload delivery works.
- Missing Cerberus and incompatible Cerberus protocol can be denied before world entry.
- Stock supported Paper/Fabric 26.2 high-level APIs do not provide the clean reverse CONFIGURATION channel-registration path needed for the entire nonce exchange.
- The selected standalone design is therefore hybrid:
  - classification + Cerberus presence/protocol gate in CONFIGURATION;
  - compatible Cerberus enters immediate bounded PLAY quarantine;
  - nonce challenge/response completes in PLAY;
  - the player is released only after verification.

The hybrid has successfully produced `CERBERUS_VERIFIED`, `MANIFEST_DENIED`, `CERBERUS_TIMEOUT`, and `MANIFEST_INVALID` in live tests.

No NMS, reflection, Fabric implementation internals, Mixins, or packet-library workaround is used.

## Phase 0B status: complete

Live Velocity/Paper/Geyser/Floodgate testing has confirmed the intended network architecture:

- Guardian-Velocity completes Fabric + Cerberus admission entirely during Velocity's awaited CONFIGURATION lifecycle and denies missing Cerberus before world entry.
- Security-sensitive Cerberus channels are consumed at the proxy rather than forwarded to Paper.
- Guardian-Velocity sends a short-lived HMAC-authenticated `guardian:proxy-admission` assertion to Guardian-Paper.
- Guardian-Paper in `velocity` authority mode verifies the trusted proxy result and does not independently re-attest or reevaluate client policy.
- Supported Geyser/Floodgate APIs classify real Bedrock connections before Java brand classification; Bedrock receives no Cerberus challenge.
- Floodgate username prefixes have no classification authority.
- Backend Floodgate can sanity-check a proxy assertion claiming `BEDROCK` when Floodgate forwarding/key sharing is configured.
- Backend switches reuse one admission for the lifetime of the proxy connection, while a full disconnect/reconnect creates a new admission session and, when required, a new Cerberus challenge.
- Standalone Guardian-Paper remains valid without Velocity, Geyser, or Floodgate and retains the Phase 0A hybrid CONFIGURATION + bounded PLAY-quarantine path.

For BadWolfMC's Velocity network, Guardian-Velocity is therefore the preferred admission authority; standalone Guardian-Paper remains a supported independent deployment mode.

## Phase 1A status: complete

Phase 1A replaces the feasibility-oriented Paper host with Guardian's production foundation while retaining the live-proven Phase 0 transport boundaries. The completed slice includes:

- `guardian-core` as the platform-neutral Admission domain;
- `guardian-protection` as a separate platform-neutral Protection domain;
- Guardian-Paper as the host/adaptor that can enable Admission, Protection, both, or neither;
- schema-versioned Paper configuration using explicit `ALLOW`, `DENY`, and `REQUIRE_CERBERUS` client actions;
- exact normalized allowlist/denylist rules for otherwise unknown Java brands;
- versioned locale resources rendered with Adventure/MiniMessage and safe unparsed internal placeholders;
- parse -> validate -> immutable candidate -> atomic activation infrastructure, with timestamped startup backups before safe default recovery and strict non-mutating reload failure that retains the prior valid snapshot;
- Guardian permission roots under `guardian.admission.*` and `guardian.protection.*`; and
- provenance/verification documentation for the BrandBlocker rewrite and the Phase 1B eZProtector boundary.

The default Paper configuration is now:

```yaml
schema-version: 1
features:
  admission:
    enabled: true
  protection:
    enabled: false
admission:
  authority: standalone
```

The Phase 0B proxy-secret bootstrap has now been superseded by the Phase 5 production provisioning model. Guardian-Velocity automatically generates `proxy-assertion.key` on first startup. For a Velocity-authoritative network, copy that file unchanged into `plugins/Guardian/proxy-assertion.key` on every Guardian-Paper backend. Standalone Paper does not require the file. Guardian never logs the key; status exposes only a short non-secret fingerprint so administrators can confirm that every host loaded the same key.

Phase 1A deliberately does **not** implement Guardian Protection command filtering or the later full Cerberus/mod-policy engine. Protection enforcement is Phase 1B; named admission profiles, permission/profile resolution, and full mod policy are later Admission phases.

Phase 1A is complete: the Java 25 clean build/test gate is green at 49 tests, startup recovery and unsupported-schema behavior were live-verified, all four Admission/Protection enable combinations were exercised, and the standalone plus Velocity/Geyser/Floodgate regressions passed. Active temporary implementation bridges are tracked in `docs/IMPLEMENTATION_BRIDGES.md` and remain owned by their later phases.

## Phase 1B status: complete

Phase 1B implements Guardian Protection while preserving the Phase 1A Admission boundaries:

- platform-neutral command-root/rule/decision/bypass models in `guardian-protection`;
- player-only Paper command execution denial for configured roots;
- explicit command-visibility and namespaced-command `ALLOWLIST`/`DENYLIST` modes;
- shared root visibility decisions for command-tree filtering and downstream suggestion suppression;
- global, feature-scoped, and optional per-command visibility bypasses under `guardian.protection.*`;
- permission-gated, locale-backed staff notifications independent from bypass authority;
- validated Protection configuration integrated into Guardian's immutable runtime snapshot; and
- supported online command-tree refresh behavior when the active visibility contract changes.

The public administrative command surface remains owned by the later operations phase; Phase 1B does not reintroduce eZProtector's raw reload command behavior. Phase 1B is complete: the operator reports the clean Java 25 / Gradle 9.7.1 gate green with all 73 repository tests passing, and the full local/live verification matrix in `docs/PHASE_1B_VERIFICATION.md` passed from a blank-slate Guardian installation, including all four Admission/Protection enable combinations, player-only execution enforcement, non-player command independence, allowlist/denylist behavior, visibility bypass scope, and notification permission scope.

## Phase 2 status: complete

Phase 2 replaces the synthetic feasibility manifest with Guardian/Cerberus protocol v1 and a real Fabric Loader-backed canonical manifest while preserving the proven Paper and Velocity transport boundaries. Live verification on 2026-09-26 passed the standalone and Velocity-authoritative happy paths plus distinct missing-Cerberus, timeout, unsupported-protocol, and malformed-response outcomes. A representative BadWolfMC Fabric client produced a 166-entry canonical manifest with top-level, built-in, nested, and multi-level containment relationships and no transmitted filesystem paths.

The Phase 2 closeout baseline is green at 85 tests. Phase 2.5 now revises the still-unreleased protocol v1 in place to add exact top-level archive SHA-256 and a durable administrator-managed approved-artifact catalog before Phase 3 policy work.

BRIDGE-001 and BRIDGE-002 are retired. BRIDGE-003 through BRIDGE-005 remain intentionally owned by later phases.

## Phase 2.5 status: complete

Phase 2.5 requires `CAP_ARTIFACT_SHA256`, hashes each top-level archive once per Cerberus environment snapshot, and keeps nested/built-in/development/ambiguous origins explicitly unhashed. Guardian-Paper adds `artifact-import/`, the asynchronous `/guardian artifacts scan` command, and deterministic add-only `artifacts.yml` identity storage. The catalog is separate from admission policy and no supplied JAR is executed, installed, extracted, or loaded.

For administrators, the artifact workflow is deliberately two-step: **scan identifies; policy approves**. A successful scan also writes `artifact-import-rules.yml` with self-contained direct-SHA-256 `ALLOW` blocks that can be copied into the appropriate profile in `policy.yml`. Guardian never treats an imported/catalogued artifact as automatically allowed.

Phase 2.5 is complete at `0.1.0-phase2.5`: the Java 25 / Gradle 9.7.1 gate is green at 102 tests and live verification confirmed the administrator artifact workflow, durable historical catalog entries, multiple versions per mod ID, revised 166-entry SHA-256 manifest, standalone admission, and Velocity-authoritative trusted backend admission.

## Phase 3 status: complete

Phase 3 adds one portable Admission policy system for both possible authorities:

- shared `policy.yml` schema parsed/validated in `guardian-core` without Bukkit/Velocity configuration types;
- immutable policy snapshots and atomic candidate activation;
- default/named profiles with deterministic priorities and exact UUID overrides;
- optional asynchronous LuckPerms 5.5 profile/bypass providers on Paper and Velocity;
- client `ALLOW` / `DENY` / `REQUIRE_CERBERUS` policy plus `JAVA_UNKNOWN` brand rules;
- `ALLOWLIST` / `DENYLIST`, baseline, required, explicit allow/deny, containment, development-origin, bounded version, and exact SHA-256/catalog mod semantics;
- structured policy violations and policy-scoped bypasses that cannot defeat protocol/session/proxy-assertion integrity;
- standalone Guardian-Paper consumption of the shared evaluator; and
- Guardian-Velocity consumption of the same evaluator, removing its feasibility-era independent admission-policy branch while leaving Phase 5 operational productionization bridges intact.

Phase 3 is complete at `0.1.0-phase3`. The final Java 25 / Gradle 9.7.1 gate is green at **134 tests** with zero failures/errors/skips (Core 53, Paper 31, Protection 20, Protocol 21, Velocity 9). Live verification confirmed Velocity ordinary allow, explicit deny, required-mod version mismatch, genuinely absent required mod (`REQUIRED_MOD_MISSING`), exact-hash denial, trusted proxy assertion without backend re-attestation, standalone Paper parity, player-facing actionable denial text plus help URL, and successful import of the four previously rejected real-world Fabric JARs including Replay Mod. Configuration semantics are documented in `docs/GUARDIAN_ADMISSION.md`.

## Phase 4 status: complete

Phase 4 is intentionally narrow because the proxy-side Bedrock foundation was already live-proven. `0.1.0-phase4` productionizes Geyser/Floodgate origin semantics, distinguishes provider absence from provider failure, fails closed when origin becomes indeterminate, and adds the previously missing supported Geyser/Floodgate classification path to standalone Guardian-Paper. Positive supported API evidence remains authoritative for Bedrock, Bedrock never enters the Cerberus path, and backend Floodgate remains diagnostic-only in Velocity authority mode.

The final Java 25 / Gradle 9.7.1 gate is green at **141 tests** with zero failures/errors/skips (Core 57, Paper 34, Protection 20, Protocol 21, Velocity 9). Live closeout verification confirmed Velocity Bedrock with agreeing proxy/backend Floodgate evidence, Velocity Java Fabric/Cerberus regression, standalone Paper Bedrock allow, and standalone Paper Bedrock deny through the shared `policy.yml`. BRIDGE-005 is retired; BRIDGE-003 and BRIDGE-004 remain Phase 5 work.

## Phase 5 status: complete

`0.1.0-phase5` productionizes Guardian-Velocity and adds authority-explicit administrator operations. Guardian-Paper owns `/guardian` with `guardian.command.*`; Guardian-Velocity owns `/guardianv` with `guardian.velocity.command.*`. Velocity now has strict data-directory operational configuration, configurable handshake timing, production proxy-assertion key provisioning, atomic reload/files-only validation, bounded authoritative inspection snapshots, network-authoritative artifact scanning, and `NORMAL`/`DEBUG` logging. Paper behind Velocity remains assertion-only and retains no full Fabric manifest.

Phase 5 is complete. The final Java 25 / Gradle 9.7.1 gate is green at **178 tests** with zero failures/errors/skips (Core 64, Paper 44, Protection 20, Protocol 21, Velocity 29). Focused live verification passed normal Velocity Fabric/Cerberus Admission, authority-aware Paper/proxy inspection, host-local validation/reload, artifact authority, generated assertion-key provisioning/mismatch recovery, disconnect cleanup, and in-game `/guardianv` execution through the Velocity permission provider. BRIDGE-003 and BRIDGE-004 are retired; there are no active implementation bridges entering Phase 6. See `docs/PHASE_5_IMPLEMENTATION.md`, `docs/PHASE_5_VERIFICATION.md`, and `docs/PHASE_6_HANDOFF.md`.

## Phase 6 status: automated gate green; live closeout pending

`0.1.0-phase6` is the current security/adversarial-hardening candidate. It preserves the Phase 5 authority architecture while hardening exact-connection session ownership, proxy assertion replay/timestamp/key boundaries, profile/Bedrock provider failures, bounded active inspection, log/diagnostic injection, development origins, stable administrator-file reads/reloads, exact artifact hashing, and standalone Paper PLAY quarantine. It also adds two optional Ed25519 mechanisms with deliberately narrow trust claims: signed official Cerberus release provenance and player-bound Guardian challenge authentication before stock Cerberus discloses its manifest. Neither is remote attestation, and no reusable secret is embedded in Cerberus.

The Java 25 / Gradle 9.7.1 gate is now green at **354 tests, 0 failures, 0 errors, 11 documented Windows symlink-privilege skips** (Core 117/4 skipped, Paper 91/4, Protection 20/0, Protocol 50/0, Velocity 58/3, Cerberus 18/0). Built Phase 6 Paper/Velocity/Cerberus JARs were inspected with no private-key entry present. Phase 6 is not yet closed: the focused signed-release/server-auth/quarantine/Velocity live smokes and final post-live review remain pending. See `docs/PHASE_6_IMPLEMENTATION.md`, `docs/PHASE_6_VERIFICATION.md`, and the candidate `docs/PHASE_7_HANDOFF.md`.

See:

- `docs/PHASE_0A_FINDINGS.md`
- `docs/PHASE_0A_TEST_PLAN.md`
- `docs/SANITY_REPORT.md`
- `docs/PHASE_1A_VERIFICATION.md`
- `docs/PHASE_1B_HANDOFF.md`
- `docs/PHASE_1B_VERIFICATION.md`
- `docs/PHASE_2_HANDOFF.md`
- `docs/PHASE_2_IMPLEMENTATION.md`
- `docs/PHASE_2_VERIFICATION.md`
- `docs/PHASE_2_5_IMPLEMENTATION.md`
- `docs/PHASE_2_5_VERIFICATION.md`
- `docs/PHASE_3_HANDOFF.md`
- `docs/PHASE_3_IMPLEMENTATION.md`
- `docs/PHASE_3_VERIFICATION.md`
- `docs/PHASE_5_HANDOFF.md`
- `docs/PHASE_6_HANDOFF.md`
- `docs/PHASE_6_IMPLEMENTATION.md`
- `docs/PHASE_6_VERIFICATION.md`
- `docs/PHASE_7_HANDOFF.md`
- `docs/PHASE_5_IMPLEMENTATION.md`
- `docs/PHASE_5_VERIFICATION.md`
- `docs/PHASE_4_VERIFICATION.md`
- `docs/PHASE_4_IMPLEMENTATION.md`
- `docs/GUARDIAN_ADMISSION.md`
- `docs/GUARDIAN_PROTECTION.md`
- `docs/EZPROTECTOR_MIGRATION.md`
- `docs/PROVENANCE.md`
- `docs/IMPLEMENTATION_BRIDGES.md`
- `docs/DEVELOPMENT.md`
