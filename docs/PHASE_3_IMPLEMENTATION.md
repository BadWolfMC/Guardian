# Phase 3 implementation — Guardian Admission policy engine

## Status

This document records the Phase 3 implementation plus closeout hardening at project version `0.1.0-phase3`. The operator-reported Java 25 / Gradle 9.7.1 gate is green for the policy-engine baseline; the scanner/message hardening patch must receive the same gate before final closeout. The focused live matrix is complete except for one deliberately absent required-mod Velocity case recorded in `PHASE_3_VERIFICATION.md`.

Phase 3 does not change Guardian/Cerberus protocol v1, the Phase 2.5 SHA-256 contract, or either proven transport path. It consumes normalized classifications and structurally valid protocol-v1 manifests after the existing protocol/session validation boundary.

## 1. Shared policy ownership

The administrator-facing Admission policy is now `admission/policy.yml`, packaged from `shared-resources/admission/policy.yml` and consumed with identical schema/semantics by standalone Guardian-Paper and Guardian-Velocity.

Paper `config.yml` remains operational/local configuration. It owns feature enablement, standalone/Velocity authority selection, standalone handshake timing, localization, and Guardian Protection. It no longer owns client-class or unknown-brand Admission policy.

`artifacts.yml` remains the Phase 2.5 exact-artifact identity catalog. Catalog presence never grants admission by itself; policy must explicitly reference a catalog identity with `catalog: true` in a `HASH_REQUIRED` acceptance. The administrator import directory is named `artifact-import/` rather than `approved-artifacts/` so the filesystem name does not imply that scanning itself grants policy permission. A successful scan also writes non-loaded `artifact-import-rules.yml` with reviewable, copy/paste direct-hash ALLOW blocks; it never rewrites administrator-owned `admission/policy.yml`.

## 2. Platform-neutral parse/activation boundary

`guardian-core` now owns the complete path:

```text
policy source
  -> strict YAML parse
  -> normalization
  -> validation
  -> immutable AdmissionPolicySnapshot
  -> AdmissionProfileResolver
  -> AdmissionPolicyEvaluator
```

The shared parser uses SnakeYAML Engine rather than Bukkit `YamlConfiguration`. The policy package has no Paper or Velocity imports.

Validation includes:

- schema version and unknown-key rejection;
- bounded policy size, profiles, brand rules, rule counts, acceptance clauses, and identifiers;
- duplicate YAML-key rejection and disabled collection aliases;
- normalized Fabric mod IDs and bounded profile/rule IDs;
- a required default profile;
- valid identity-override targets;
- unique profile priorities;
- duplicate normalized unknown-brand rejection;
- duplicate rule IDs within a profile and duplicate rules for one mod;
- required + unconditional-deny conflicts;
- invalid version predicates;
- invalid/incompatible artifact verification declarations;
- malformed SHA-256 values; and
- requested catalog references that do not resolve to at least one matching catalog entry.

`AdmissionPolicyRuntimeManager` activates only a fully parsed candidate. `reload()` replaces the active immutable snapshot only after validation succeeds; `validateFiles()` parses/normalizes/validates without activation.

## 3. Policy schema

Each profile declares:

- unique integer `priority`;
- client actions for `bedrock`, `vanilla`, `optifine`, `fabric`, and `unknown`;
- normalized unknown-brand `ALLOWLIST` or `DENYLIST` rules, applicable only to `JAVA_UNKNOWN`;
- mod mode `ALLOWLIST` or `DENYLIST`;
- explicit `DIRECTORY` and `MIXED_OR_UNKNOWN` actions;
- explicit baseline/bootstrap/runtime mod IDs;
- orthogonal required-mod rules; and
- explicit per-mod `ALLOW` or unconditional `DENY` rules.

`REQUIRE_CERBERUS` remains valid only for positively identified `JAVA_FABRIC`; this preserves the Fabric-only Cerberus contract and prevents unknown-brand rules from inventing an attestation path.

### Version predicates

The bounded version language supports:

- `*` — any version;
- exact strings such as `0.9.1+mc26.2`;
- one trailing prefix wildcard such as `0.9.*`; and
- whitespace-separated dotted-numeric comparisons such as `>=1.2 <2.0`.

Numeric comparisons deliberately do not guess ordering for non-numeric Fabric version strings. Those remain addressable with exact or prefix predicates. Multiple acceptance clauses provide OR semantics.
Contradictory numeric conjunctions are rejected at load time rather than accepted as silent never-match predicates.

### Artifact verification

An acceptance clause chooses one of:

- `VERSION_ONLY`; or
- `HASH_REQUIRED`.

`HASH_REQUIRED` may list direct `sha256` values, set `catalog: true`, or combine both. Multiple hashes for one ID/version and multiple accepted versions for one mod are supported. A matching version with an unaccepted/missing top-level archive hash produces `ARTIFACT_NOT_ACCEPTED`; no matching version produces `VERSION_NOT_ACCEPTED`.

`VERSION_ONLY` intentionally ignores the artifact catalog. `catalog: true` is invalid unless `HASH_REQUIRED` is selected.

## 4. Policy-addressable manifest entries

The Phase 3 policy-addressable model follows the real containment evidence from Phase 2:

1. Top-level non-`BUILTIN` entries are independently subject to mod membership/rules.
2. `BUILTIN` entries such as `minecraft` and `java` are intrinsically baseline runtime entries and are not subject to allowlist membership.
3. Administrator-configured `baseline` IDs and required mods count as permitted membership in `ALLOWLIST` mode; duplicate ALLOW rules are unnecessary.
4. Nested entries remain in the complete structurally validated manifest and remain eligible for explicit deny, required, version, or version-only constraints, but are not independently rejected merely for being unlisted.
5. If an otherwise nested mod/library is installed independently, its manifest entry becomes top-level and therefore independently policy-addressable.
6. Exact artifact SHA-256 applies to top-level `ARCHIVE` entries under the Phase 2.5 protocol contract. `HASH_REQUIRED` cannot be satisfied by an unhashed `DIRECTORY`, `NESTED`, `BUILTIN`, or `MIXED_OR_UNKNOWN` entry.
7. `DIRECTORY` and `MIXED_OR_UNKNOWN` top-level origins have explicit profile policy actions instead of filename/path heuristics.

Nested entries therefore remain security/diagnostic evidence without forcing administrators to maintain an allowlist of every Fabric API/C2ME/Kotlin/internal library entry.

## 5. Profile resolution and LuckPerms

Profile precedence is shared and deterministic:

1. exact configured UUID override;
2. highest-priority matching provider profile permission;
3. default profile.

The stable selection permission is:

`guardian.admission.profile.<profile-id>`

`AdmissionProfileProvider` is platform-neutral and asynchronous. It receives authenticated player identity plus the immutable policy snapshot as context, while policy interpretation remains in `guardian-core`. Both platform adapters provide optional LuckPerms 5.5 implementations that load the user by UUID and use static query options suitable for the pre-login/proxy-session decision. If LuckPerms is absent or provider resolution fails/times out, Guardian resolves only identity overrides/default profile and grants no provider-derived bypasses.

Velocity resolves the profile once for the proxy admission session. A backend switch reuses that session decision; it does not silently re-resolve a backend-context-specific mod policy.

## 6. Bypasses and integrity boundary

Phase 3 retains the stable permissions:

- `guardian.admission.client.bypass`
- `guardian.admission.client.bypass.<client-key>`
- `guardian.admission.mod.bypass`
- `guardian.admission.mod.bypass.<mod-id>`

Client bypasses may exempt client-class/unknown-brand `DENY` decisions. They cannot remove `REQUIRE_CERBERUS`.

Mod bypasses are applied only to structured policy violations after a structurally valid Cerberus response has passed protocol/session validation. They may exempt required-mod, explicit deny, unlisted, version, exact-artifact, or development-origin policy failures.

They cannot exempt unsupported protocol/capabilities, malformed payload/canonicalization, nonce/session mismatch, duplicate/replay handling, payload limits, or proxy-assertion authentication. Those decisions occur outside and before the policy evaluator.

## 7. Structured policy results

Mod-policy failures are represented as `PolicyViolation` records with `PolicyViolationCode`, rule ID, mod ID, and detail. Current codes are:

- `REQUIRED_MOD_MISSING`
- `EXPLICIT_MOD_DENY`
- `UNLISTED_MOD`
- `VERSION_NOT_ACCEPTED`
- `ARTIFACT_NOT_ACCEPTED`
- `DIRECTORY_ORIGIN_DENIED`
- `MIXED_OR_UNKNOWN_ORIGIN_DENIED`

`PolicyEvaluation` preserves active and bypassed violations independently from the final `GuardianDecision`. For player-facing denials, the final decision also carries a small immutable context for the deterministic first active violation (violation code, mod ID, and observed version when present). Paper and Velocity use that context only to select/render localized disconnect text; complete violation details remain available in server logs. Explicit `DENY` is terminal for that mod so it is not redundantly reported again as an unlisted allowlist violation.

## 8. Paper integration

Standalone Guardian-Paper snapshots include the shared immutable policy. During asynchronous CONFIGURATION it resolves the pre-login profile, classifies the client, and invokes the shared client evaluator.

- terminal `ALLOW`/`DENY` remains in CONFIGURATION;
- `REQUIRE_CERBERUS` consumes the existing CONFIGURATION presence/protocol evidence and then preserves the proven bounded PLAY challenge/quarantine fallback;
- PLAY response decoding and `ProtocolV1ResponseValidator` run before the shared manifest evaluator; and
- in-flight sessions retain the snapshot/profile they started with, while new sessions see a successfully reloaded snapshot.

In `velocity` authority mode Paper does not load or evaluate player admission policy. It continues to verify only the authenticated proxy admission assertion and retains the existing backend Floodgate sanity check.

## 9. Velocity integration and BRIDGE-004 checkpoint

Guardian-Velocity now loads the same `admission/policy.yml`, creates the same `AdmissionPolicySnapshot`, resolves the same profile model, and invokes the same `AdmissionPolicyEvaluator` for client and manifest decisions.

The former feasibility-era hard-coded admission branch has been removed. Cerberus presence can arrive before asynchronous profile resolution; early protocol failures are recorded but become connection-fatal only if shared client policy selects `REQUIRE_CERBERUS`, preserving the authoritative evaluation order.

BRIDGE-004 therefore satisfies its Phase 3 shared-policy checkpoint. The bridge remains active for Phase 5 because Velocity still intentionally retains feasibility-era operational details such as fixed handshake timing, `GUARDIAN_PHASE0B_PROXY_SECRET`, final data/config ownership UX, deployment diagnostics, and Phase 0B-labelled logging/Javadocs.

## 10. Reload and files-only validation seams

Paper's existing atomic runtime reload now includes the shared policy when Paper is standalone authority. `GuardianRuntimeManager.validateFiles()` provides non-activating files-only validation.

Guardian-Velocity owns a shared `AdmissionPolicyRuntimeManager` with minimal `reloadAdmissionPolicy()` and `validateAdmissionPolicyFiles()` seams. Final Velocity administrative command/UX remains Phase 5 by contract.

Scanning/importing `artifact-import/` records identities in `artifacts.yml` and regenerates `artifact-import-rules.yml`; an already active policy snapshot is unchanged until a policy reload succeeds. `artifact-import-rules.yml` uses direct SHA-256 declarations and is never loaded automatically. This preserves the identity/policy boundary while giving administrators copy/paste-ready exact-hash rules without forcing Guardian to rewrite a multi-profile, comment-owned `policy.yml`.

## 11. Artifact-import compatibility hardening

The Phase 3 closeout pass exercised the Phase 2.5 scanner against real Fabric 26.2 distributions that exposed two overly strict import assumptions:

- several otherwise loadable Fabric mods contain literal line breaks inside human-readable `fabric.mod.json` string values; Guardian now mirrors Fabric Loader's metadata-reader compatibility for those strings while retaining UTF-8, metadata-size, depth, identity and schema bounds;
- Replay Mod contains 7,882 archive entries, exceeding the original 4,096-entry scanner ceiling even though Guardian never extracts those entries. The administrative scanner ceiling is therefore raised conservatively to 16,384 entries. Whole-JAR size, aggregate import size, bounded root metadata reads, non-symlink/regular-file checks and exact whole-file hashing remain in force.

The scanner continues to classload/execute/extract nothing from imported JARs.

## 12. Dependency/package note

SnakeYAML Engine is packaged into the Paper and Velocity Guardian JARs because policy parsing is required at runtime. LuckPerms remains `compileOnly`/optional on both adapters.

## 13. Deliberately unchanged/deferred work

Phase 3 does not alter:

- protocol v1 or `CAP_ARTIFACT_SHA256`;
- Cerberus manifest collection/hash behavior;
- standalone Paper transport;
- Velocity CONFIGURATION transport/channel isolation;
- trusted Velocity → Paper assertion format;
- BRIDGE-003 proxy-secret provisioning (Phase 5);
- BRIDGE-005 Geyser/Floodgate productionization (Phase 4);
- Phase 5 final Velocity operational configuration/timing/diagnostics; or
- Phase 6 signed Cerberus/hostile-client hardening.

Guardian Protection is unchanged except for regression exposure through the shared Paper runtime host.

## 14. Final closeout — 2026-09-27

Phase 3 is complete at `0.1.0-phase3`. The operator's final Java 25 / Gradle 9.7.1 gate passed with 134 tests and zero failures/errors/skips. The final missing-required-mod Velocity case produced `REQUIRED_MOD_MISSING`, named the missing mod to the player, and included the configured help URL while Guardian-Paper remained assertion-only. The hardened artifact importer also accepted the four previously rejected real-world Fabric distributions, including Replay Mod, and generated valid direct-hash copy/paste policy fragments.

No Phase 3 policy work is deferred to Phase 5. BRIDGE-004 now remains open only for Velocity-specific production hosting/operations concerns already assigned to Phase 5.
