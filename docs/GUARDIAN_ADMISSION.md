# Guardian Admission — operator and configuration guide

Guardian Admission decides whether a connection may enter the server/network. In standalone mode Guardian-Paper is authoritative; on a Guardian-Velocity deployment the proxy is authoritative and Guardian-Paper verifies only the authenticated proxy admission assertion. Both authorities use the same Phase 3 policy schema and evaluator.

The shared Admission policy semantics were finalized in Phase 3. Phase 5 adds production authority-specific operations without changing that policy language.

## Configuration ownership

Keep operational configuration separate from admission policy:

```text
Guardian data directory/
├── config.yml                 host-local operational settings
├── policy.yml                 shared portable Admission policy
├── proxy-assertion.key        Velocity-generated; copied to Velocity-authority Paper backends only
├── artifacts.yml              exact-artifact identity catalog; not an allowlist
├── artifact-import/           optional administrative import input
└── artifact-import-rules.yml  generated copy/paste exact-hash policy fragment
```

Guardian-Velocity uses the same `policy.yml` schema. Its Phase 5 data directory also owns proxy-local `config.yml` and locales; it does not get a separate policy language.

`artifacts.yml` records identities discovered/imported by the Phase 2.5 artifact workflow. Merely appearing in the catalog never grants permission to connect. `artifact-import-rules.yml` is convenience output only: Guardian does not load it and does not modify `policy.yml` during a scan. It contains direct `HASH_REQUIRED`/`sha256` ALLOW blocks for the JARs present during the most recent successful scan, indented so an administrator can review and paste them beneath the desired profile's `mods.rules`.

### Artifact import workflow in plain English

Putting a JAR in `artifact-import/` does **not** approve that mod for players. On standalone Paper use `/guardian artifacts scan`; when Velocity is authoritative use `/guardianv artifacts scan` at the proxy. The authoritative scan does two administrative jobs only:

1. it identifies and hashes the JAR and records that exact identity in `artifacts.yml`; and
2. it writes a ready-to-paste exact-hash `ALLOW` rule into `artifact-import-rules.yml`.

To actually permit the mod, review the generated block, copy it into `profiles.<the-profile>.mods.rules` in `policy.yml`, and then validate/reload Admission policy. This explicit copy step is what chooses **which profile** receives permission. For example, a Replay Mod JAR can be scanned globally but its generated rule can be pasted only into a staff profile instead of the default profile.

`artifact-import-rules.yml` is regenerated from the JARs present during the latest successful scan and is never read as policy. `artifacts.yml` is durable identity history and may retain old versions after their import JARs are removed. Neither file, by itself, grants admission permission.

## Default policy

The packaged default is intentionally conservative:

```yaml
schema-version: 1
default-profile: default
identity-overrides: {}

profiles:
  default:
    priority: 0
    clients:
      bedrock: ALLOW
      vanilla: ALLOW
      optifine: ALLOW
      fabric: REQUIRE_CERBERUS
      unknown: DENY
    unknown-brands:
      mode: ALLOWLIST
      brands: []
    mods:
      mode: ALLOWLIST
      origins:
        directory: DENY
        mixed-or-unknown: DENY
      baseline:
        - fabricloader
        - cerberus
      required: {}
      rules: {}
```

With no added mod rules, Fabric/Cerberus clients are structurally attestable but ordinary top-level mods are not yet permitted. Administrators must deliberately define the Fabric ecosystem they want to allow.

## Profiles and resolution

Every profile has a unique integer priority. Resolution order is:

1. exact UUID entry under `identity-overrides`;
2. highest-priority profile for which the pre-login provider reports `guardian.admission.profile.<profile-id>`;
3. `default-profile`.

LuckPerms 5.5 is the optional first-class provider on Paper and Velocity. Guardian resolves using static/pre-login data rather than a backend player's live contextual permission object. If LuckPerms is absent or provider resolution fails, Guardian uses identity overrides/default profile and grants no provider-derived bypasses.

For Velocity authority, the selected profile belongs to the proxy admission session. Backend switching reuses that decision rather than silently selecting a new mod policy from backend context.

Example:

```yaml
default-profile: default
identity-overrides:
  "11111111-1111-1111-1111-111111111111": staff

profiles:
  default:
    priority: 0
    # ...
  staff:
    priority: 100
    # ...
```

The named-profile permission would be `guardian.admission.profile.staff`.

## Client policy

Each profile declares one action for every normalized client class:

- `bedrock`
- `vanilla`
- `optifine`
- `fabric`
- `unknown`

Supported actions are `ALLOW`, `DENY`, and `REQUIRE_CERBERUS`. `REQUIRE_CERBERUS` is valid only for positively identified Fabric clients.

Raw/normalized brand rules apply only after a Java connection remains `JAVA_UNKNOWN`. They never override trusted Bedrock classification and never turn positively identified Fabric into ordinary `ALLOW`.

`unknown-brands.mode: ALLOWLIST` requires `clients.unknown: DENY`; listed normalized brands become allowed exceptions. `DENYLIST` requires `clients.unknown: ALLOW`; listed normalized brands become denied exceptions. Contradictory combinations are rejected during policy validation.

## Mod policy modes

`mods.mode` is one of:

- `ALLOWLIST` — policy-addressable top-level mods must be explicitly allowed, required, or listed as baseline;
- `DENYLIST` — unlisted top-level mods are permitted unless an explicit rule denies or constrains them.

Required rules are orthogonal to membership. A required mod automatically counts as permitted for allowlist membership, so administrators do not need duplicate `required` + `ALLOW` declarations.

`baseline` is for explicit bootstrap/runtime entries that should not require duplicate ALLOW rules. The packaged default includes `fabricloader` and `cerberus`. `BUILTIN` entries such as `minecraft` and `java` are intrinsically treated as runtime baseline and do not need to be listed.

## Policy-addressable containment

Guardian continues to receive and structurally validate the complete Loader-known manifest, including nested and multi-level containment.

The Phase 3 membership rule is:

- top-level non-`BUILTIN` entries are independently policy-addressable;
- nested entries remain visible and may be explicitly denied, required, or version-constrained, but are not rejected merely because they are unlisted;
- if that same nested ID is installed independently/top-level, it becomes independently policy-addressable.

This avoids requiring administrators to maintain hundreds of Fabric API/C2ME/Kotlin/internal-library allowlist entries while preserving nested contents as policy and diagnostic evidence.

## Required mods

Required rules are keyed by administrator-owned rule IDs:

```yaml
required:
  require-fabric-api:
    mod: fabric-api
    accept:
      - version: "0.160.*"
        verification: VERSION_ONLY
```

Omit `accept` to require presence regardless of version/artifact identity. If `accept` is present, at least one acceptance clause must match.

## Explicit allow/deny rules

Rules are likewise keyed by stable administrator-owned IDs:

```yaml
rules:
  allow-sodium:
    mod: sodium
    action: ALLOW
    accept:
      - version: "0.9.*"
        verification: VERSION_ONLY

  deny-baritone:
    mod: baritone
    action: DENY
```

`DENY` is unconditional and therefore cannot contain `accept` clauses. A mod cannot be both required and unconditionally denied in the same profile.

## Version predicates

Phase 3 deliberately uses a bounded, administrator-readable language rather than assuming every Fabric version follows semantic versioning.

Supported forms are:

- `*` — any version;
- exact text, for example `0.9.1+mc26.2`;
- one trailing prefix wildcard, for example `0.9.*` or `build-2026.*`;
- whitespace-separated dotted-numeric comparisons, for example `>=1.2 <2.0`.

Numeric comparisons accept only dotted numeric observed versions. A value such as `1.9+fabric` does **not** get guessed into a numeric ordering; use exact or prefix matching instead.
Contradictory numeric conjunctions such as `>=2.0 <1.0` are rejected during parsing rather than becoming silent never-match rules.

Multiple `accept` entries are OR alternatives.

## Exact artifact policy

Every acceptance clause explicitly selects:

- `VERSION_ONLY`; or
- `HASH_REQUIRED`.

Direct exact-hash example:

```yaml
rules:
  allow-iris-exact:
    mod: iris
    action: ALLOW
    accept:
      - version: "2.0.0+mc26.2"
        verification: HASH_REQUIRED
        sha256:
          - "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
          - "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
```

Catalog-backed example:

```yaml
rules:
  allow-sodium-catalogued:
    mod: sodium
    action: ALLOW
    accept:
      - version: "0.9.*"
        verification: HASH_REQUIRED
        catalog: true
```

For `catalog: true`, Guardian resolves the matching `(mod ID, accepted version, SHA-256)` identities from `artifacts.yml` while building the immutable policy snapshot. If no matching catalog entry exists, the policy candidate is invalid and is not activated.

Direct `sha256` and `catalog: true` may be combined. Several accepted hashes for one ID/version and several accepted versions for one ID are supported.

SHA-256 means the exact top-level archive bytes reported by a cooperating Cerberus client matched an approved identity. It is not hostile-client remote attestation; a deliberately replaced/hostile client remains outside the assurance provided by Phase 3.

## Development and ambiguous origins

Top-level `DIRECTORY` and `MIXED_OR_UNKNOWN` origins have explicit profile actions:

```yaml
origins:
  directory: DENY
  mixed-or-unknown: DENY
```

Production defaults deny both. Phase 3 does not invent a directory-tree hash. A `HASH_REQUIRED` acceptance can only be satisfied by a top-level `ARCHIVE` entry carrying the Phase 2.5 SHA-256 identity.

## Bypass permissions

Admission bypasses are policy exemptions, not integrity exemptions:

| Permission | Meaning |
|---|---|
| `guardian.admission.client.bypass` | Aggregate exemption from client-policy DENY decisions. |
| `guardian.admission.client.bypass.<client-key>` | Client-class-specific DENY exemption. |
| `guardian.admission.mod.bypass` | Aggregate exemption from mod-policy violations after valid attestation. |
| `guardian.admission.mod.bypass.<mod-id>` | Mod-specific policy exemption after valid attestation. |

Client bypass cannot remove `REQUIRE_CERBERUS`. Mod bypass occurs only after Guardian has a structurally valid, nonce/session-valid Cerberus response. No bypass can exempt unsupported protocol/capabilities, malformed/canonicalization failures, nonce mismatch, replay/duplicate failure, payload limits, or an invalid trusted proxy assertion.

## Reload and files-only validation

The shared runtime activation sequence is:

```text
read candidate -> parse -> normalize -> validate -> immutable snapshot -> atomic activation
```

An invalid reload candidate never replaces the prior valid snapshot. Files-only validation uses the same parser/normalizer/validator without activation.

Paper integrates this with its existing domain-aware runtime reload/validation primitives. Guardian-Velocity contains the shared runtime reload/validation seams in Phase 3; Phase 5 owns the final proxy administrative command/UX.

Scanning `artifact-import/` records exact identities in `artifacts.yml` and refreshes the non-loaded `artifact-import-rules.yml` convenience fragment; it never edits `policy.yml` or changes an active policy snapshot. This separation is intentional because identity is global while permission is profile-specific. Copy or merge generated rules into the desired profile explicitly, then reload/validate Admission policy. For `catalog: true` rules, copy the corresponding `artifacts.yml` to whichever authority (Paper or Velocity) owns policy evaluation.

## Minimal Phase 3 live closeout

Do not repeat the full Phase 2 transport abuse matrix. `PHASE_3_VERIFICATION.md` defines the focused cases that matter for this phase: Velocity ordinary allow, explicit mod denial, missing required mod, exact-artifact/hash denial and restoration, plus one standalone Paper parity spot-check.
