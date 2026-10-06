# Guardian Admission — operator and configuration guide

Guardian Admission decides whether a connection may enter the server/network. In standalone mode Guardian-Paper is authoritative; on a Guardian-Velocity deployment the proxy is authoritative and Guardian-Paper verifies only the authenticated proxy admission assertion. Both authorities use the same portable policy schema and evaluator.

## Normal operator path

For routine policy work:

1. edit the authoritative host's `policy.yml`;
2. run `/guardian validate` on standalone Paper or `/guardianv validate` on Velocity;
3. if validation succeeds, run the matching `reload` command; and
4. use `inspect <player>` when you need to confirm the active profile, decision, or currently connected Fabric environment.

When Velocity is authoritative, make Admission policy/catalog changes at the proxy. Paper backends verify the authenticated proxy result and should not maintain an independent copy of player mod policy. Use the install guides for first-time deployment and `PRODUCTION_RUNBOOK.md` for production changes/rollback.

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

Guardian-Velocity uses the same `policy.yml` schema. Its data directory also owns proxy-local `config.yml` and locales; it does not get a separate policy language.

`artifacts.yml` records identities discovered/imported by the artifact import workflow. Merely appearing in the catalog never grants permission to connect. `artifact-import-rules.yml` is convenience output only: Guardian does not load it and does not modify `policy.yml` during a scan. It contains direct `HASH_REQUIRED`/`sha256` ALLOW blocks for the JARs present during the most recent successful scan, indented so an administrator can review and paste them beneath the desired profile's `mods.rules`.

### Artifact import workflow in plain English

Putting a JAR in `artifact-import/` does **not** approve that mod for players. On standalone Paper use `/guardian artifacts scan`; when Velocity is authoritative use `/guardianv artifacts scan` at the proxy. The authoritative scan does two administrative jobs only:

1. it identifies and hashes the JAR and records that exact identity in `artifacts.yml`; and
2. it writes a ready-to-paste exact-hash `ALLOW` rule into `artifact-import-rules.yml`.

To actually permit the mod, review the generated block, copy it into `profiles.<the-profile>.mods.rules` in `policy.yml`, and then validate/reload Admission policy. This explicit copy step is what chooses **which profile** receives permission. For example, a Replay Mod JAR can be scanned globally but its generated rule can be pasted only into a staff profile instead of the default profile.

`artifact-import-rules.yml` is regenerated from the JARs present during the latest successful scan and is never read as policy. `artifacts.yml` is durable identity history and may retain old versions after their import JARs are removed. Neither file, by itself, grants admission permission.

## Default policy

The packaged default is intentionally conservative:

```yaml
schema-version: 2
default-profile: default
identity-overrides: {}

cerberus-release-trust:
  required: false
  ed25519-public-keys: []

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

## Optional signed official Cerberus releases

Guardian supports an optional release-provenance check for official Cerberus builds. It is **disabled by default**. The shared `policy.yml` block is:

```yaml
cerberus-release-trust:
  required: false
  ed25519-public-keys: []
```

When `required: true`, at least one trusted Ed25519 public key is required. Each list item is the base64 encoding of an X.509 SubjectPublicKeyInfo DER public key. Up to eight keys may be configured at once so an administrator can overlap old/new public keys during a simple release-key rotation. Only the public key belongs in Guardian policy. The normal release finalizer publishes that key as `cerberus-release-signing.pub`; copy its **base64 contents** into this list. Do not substitute the public-key file SHA-256, signed/unsigned JAR SHA-256, or Cerberus canonical digest. The release private key must remain outside the repository, Minecraft client, Guardian-Paper, and Guardian-Velocity.

An official release is produced from the exact CI-built unsigned Cerberus JAR with the release-manager helper. The normal path is `finalize-release`, which validates the complete GitHub Actions release-input bundle, signs that exact Cerberus JAR with the offline key, verifies the finished result, and stages only publishable files:

```powershell
.\tools\release-manager.ps1 `
  -Action finalize-release `
  -Version 1.0.0 `
  -InputDirectory .\release-input `
  -OutputDirectory .\release-final `
  -ReleasePrivateKey C:\secure\cerberus-release-signing.key `
  -ReleasePublicKey C:\secure\cerberus-release-signing.pub `
  -GuardianServerPublicKeys C:\secure\guardian-server-auth-trust.txt
```

Do not rebuild Cerberus locally for an official release and do not point the normal release procedure at `cerberus-fabric/build/libs`. The lower-level `sign-cerberus` action remains available for unusual/manual recovery work, but it requires both an explicit unsigned JAR **and** the matching CI-built `cerberus-release-tools-<version>.jar`; see `RELEASE_PROCESS.md`. The underlying Gradle signing task also remains available for deliberate automation/recovery work with explicit input, key, version, and output paths. See `RELEASE_PROCESS.md` and `KEY_MANAGEMENT.md` for generation, rotation, backup, and compromise procedures for all three key domains.

The private key file must contain an Ed25519 PKCS#8 private key, either DER or PEM `PRIVATE KEY` form. The task reads the key only in the release-tool process, first copies the unsigned input through a bounded no-follow read into a stable temporary sibling, performs all preflight checks and signing against that snapshot, computes the canonical logical JAR-content digest, signs the release version plus digest with Ed25519, injects `META-INF/guardian/cerberus-release.bin`, rechecks the resulting JAR, and publishes only to the explicit signed output path supplied by the release manager. If the source JAR changes identity, size, or modification time while the stable snapshot is being copied, signing fails. The signer refuses to overwrite an existing final output and publishes the new file only after all checks succeed. No private-key material is packaged.

The canonical digest intentionally covers logical JAR file content rather than raw ZIP bytes: entry names, uncompressed lengths, and per-entry SHA-256 digests are sorted canonically, while ZIP ordering/compression/timestamps are ignored. The embedded Guardian release-metadata entry itself is excluded to avoid a circular digest/signature dependency. Repacking the same logical contents therefore keeps this release identity; changing a signed file entry changes it. This release digest is distinct from Guardian's ordinary mod-artifact SHA-256 rules, which continue to mean the exact top-level archive bytes reported by Cerberus.

Stock Cerberus advertises the signed-release protocol capability only when it is running from one regular JAR whose embedded release metadata matches both its Fabric metadata version and the JAR's recomputed canonical digest. If the policy requires a signed release, Guardian also requires that capability in the challenge and verifies the returned identity against one of the configured Ed25519 public keys. Missing signed metadata and untrusted/mismatched signed metadata produce distinct Admission failures. Standalone Paper and Guardian-Velocity use the same shared trust policy.

**Security boundary:** this verifies only the **reported signed release-artifact identity**. It is useful against accidental edits, casual JAR modification, and ordinary unsigned/self-compiled builds. It is not remote attestation and does not prove that a hostile client is executing the signed bytes. A deliberately modified Cerberus can bypass its own self-check and replay/report the valid version, digest, and signature from an official release while answering a fresh Guardian nonce with other code. The release signature is intentionally reusable metadata rather than a session signature. No long-term secret is embedded in Cerberus.

This release-provenance feature is independent of Guardian-Velocity's proxy-assertion key.

## Optional Guardian server authentication to Cerberus

Guardian also supports an optional privacy boundary in the opposite direction: stock Cerberus can require the Guardian Admission authority to authenticate its challenge before Cerberus collects or discloses the mod manifest. This is disabled by default until the administrator creates a server identity and distributes an official Cerberus release carrying its public trust anchor. No long-term secret is embedded in Cerberus.

Guardian server authentication uses a server-held Ed25519 private key named `guardian-server-auth.key`. Standalone Paper reads it from `plugins/Guardian/` when `admission.standalone.server-authentication.enabled: true`; Guardian-Velocity reads it from its Guardian plugin data directory when `admission.server-authentication.enabled: true`. Velocity-authority Paper backends do not need this key because the proxy is the Admission authority.

The corresponding public key is embedded in the signed Cerberus release as `META-INF/guardian/trusted-server-keys.txt`. Up to eight public keys may be embedded so key rotation can overlap old and new identities. The trust-anchor file is injected **before** the Cerberus canonical release digest is calculated, so an official release signature covers the exact server-authentication trust anchors shipped to clients. At runtime Cerberus resolves this resource through its own Fabric `ModContainer.findPath(...)` rather than a shared classloader lookup, preventing another mod with a colliding resource name from substituting the trust anchors Cerberus enforces. The server-auth private key is never included in the client artifact. The conventional `guardian-server-auth.key` filename is also ignored by Git alongside `proxy-assertion.key`; neither private infrastructure key belongs in source control, release artifacts, or support bundles.


The embedded trust-anchor resource is read through a bounded provider-aware channel and revalidated for stable regular-file identity, size, and modification time after the read. Ordinary filesystem resources use no-follow channel semantics. Signed JAR resources are exposed through the JDK ZIP filesystem, whose channel provider rejects `LinkOption.NOFOLLOW_LINKS`; those immutable archive entries therefore use the provider-supported read-only form while retaining the surrounding regular-file and pre/post stability checks. Production release anchors are additionally committed by the signed Cerberus canonical JAR digest.

When pinned trust anchors are present, Cerberus advertises the authenticated-challenge capability. Guardian signs a short-lived challenge over the protocol version, required capability mask, fresh nonce, authenticated player UUID, issue time, and expiration. Cerberus verifies the signature against its pinned public keys and verifies that the signed UUID equals its own authenticated Minecraft session UUID **before manifest collection**. This UUID binding prevents an ordinary malicious server from obtaining a legitimate trusted Guardian challenge for its own account and relaying that signature to a different victim in order to harvest the victim's mod manifest.

If pinned Cerberus requires authenticated challenges but Guardian has no server-auth signer configured, Admission fails explicitly with `CERBERUS_SERVER_AUTH_REQUIRED` rather than allowing the client to disclose its manifest to an unauthenticated server. Conversely, when Guardian enables server authentication it requires the client capability so an older client cannot silently downgrade the privacy contract.

**Security boundary:** this authenticates possession of the configured Guardian server private key; it is not DNS/TLS hostname authentication and it does not make a hostile client honest. A user can modify their own Cerberus to ignore this privacy check, but that weakens only that user's disclosure protection. A stolen Guardian server-auth private key can impersonate that Guardian trust domain to Cerberus releases that still pin the compromised public key until those clients update. Multiple public keys support staged rotation: first distribute Cerberus with old+new public keys, then switch the Guardian authority to the new private key, then later distribute a Cerberus release that removes the old public key.

The player UUID and short lifetime materially reduce challenge relay, but this is not a client-contributed-nonce protocol. A challenge captured for the **same authenticated player UUID** may remain replayable to that same player during the small validity/skew window. Closing that narrower case would require an additional client-first freshness contribution/round trip and is intentionally not claimed here. Initial Cerberus Presence also still exposes only coarse Cerberus version/capability metadata before server authentication; the protected information is the mod manifest.

This server-authentication identity, the Cerberus release-signing identity, and Guardian-Velocity's proxy-assertion key are three separate trust domains and should not be conflated. Runtime validation/reload treats a replaced `guardian-server-auth.key` as candidate state: files-only validation never activates it, while a successful reload atomically moves new connections to the new signer and existing sessions retain the immutable signer captured in their runtime snapshot. Malformed PEM key material, including a missing footer or invalid Base64 body, fails explicitly.

## Profiles and resolution

Every profile has a unique integer priority. Resolution order is:

1. exact UUID entry under `identity-overrides`;
2. highest-priority profile for which the pre-login provider reports `guardian.admission.profile.<profile-id>`;
3. `default-profile`.

LuckPerms 5.5 is the optional first-class provider on Paper and Velocity. Guardian resolves using static/pre-login data rather than a backend player's live contextual permission object. If LuckPerms is intentionally absent when Guardian initializes, the default profile and explicit UUID identity overrides remain valid. If LuckPerms is present/selected but a particular pre-login resolution later throws, times out, returns no usable snapshot, becomes unavailable, or disappears, Guardian does **not** fall back to the default profile: that connection fails closed with `PROFILE_RESOLUTION_FAILED`, because the missing provider may have selected a stricter profile. An explicit UUID identity override remains usable in that failure case because its profile is already determined independently; provider-derived bypasses are omitted.

For Velocity authority, the selected profile belongs to the proxy admission session. Backend switching reuses that decision rather than silently selecting a new mod policy from backend context.

### Bedrock origin provider lifecycle

Geyser/Floodgate classification remains authoritative only through their supported server APIs; usernames, Floodgate-style prefixes, Java brand strings, and client plugin messages are not Bedrock identity evidence. A provider that was never installed/enabled is ordinary `UNAVAILABLE`. If Guardian had observed a provider as available and that provider is later disabled/disappears, subsequent authoritative origin checks treat that provider as `ERROR` and fail closed when no other supported provider supplies positive Bedrock evidence. Re-enabling/restoring the provider makes later checks query its API again.

Provider lifecycle state is generation-tracked around each combined Geyser/Floodgate observation. If a provider changes availability while its observation is being made, that provider's observation is discarded as `ERROR`. A stable positive result from the other supported provider still wins, preserving the cross-provider precedence rule without trusting stale evidence from the provider that reconfigured. Once a stable origin classification has been captured for one Admission session, later provider changes do not retroactively rewrite that session's evidence.

On Velocity-authority Paper backends, backend Floodgate remains defense-in-depth diagnostics only. Backend `NOT_AVAILABLE`, provider/query `ERROR`, agreement, and disagreement are distinguished for inspection/logging, but none of them can overturn a valid authenticated Guardian-Velocity Admission assertion or cause Paper to re-run player policy.

### Trusted proxy assertion boundary

Guardian-Velocity signs each short-lived backend admission assertion with HMAC-SHA256 using the server-controlled `proxy-assertion.key`. Guardian-Paper verifies the HMAC, authenticated UUID, assertion version, lifetime, clock-skew boundary, and connection origin before treating the result as authoritative. Each authenticated assertion payload is one-time on a backend: the backend retains only a bounded in-memory replay fingerprint until the signed expiry and rejects an exact replay, including a replay to a later same-UUID configuration connection. Each Paper configuration session also accepts at most one assertion.

Assertion verification is bound to the immutable Guardian runtime/key snapshot captured when that exact Paper configuration connection began. Coordinated key replacement remains fail-closed as documented in `KEY_MANAGEMENT.md`; an in-flight connection does not silently switch verifier keys because an administrator reloaded the backend midway through configuration. The copied secret bytes used for HMAC work are cleared after use.
A direct connection to a Paper backend configured for Velocity authority therefore has no trusted assertion and is denied with `PROXY_ASSERTION_REQUIRED` at final configuration validation. Timeout/terminal decisions are first-wins on both authorities: a late asynchronous success cannot replace an already-recorded timeout/deny.
Proxy assertion timestamp arithmetic is itself fail-closed: lifetime, clock-skew, or other timestamp boundary overflow is invalid rather than widening an acceptance window.

This mechanism authenticates Guardian-Velocity infrastructure; it does not replace Velocity backend isolation. Security-sensitive Guardian channels are consumed at Velocity rather than forwarded from clients, and Velocity-authority Paper backends still need the normal proxy/backend hardening described in the deployment documentation.

### Protocol-v1 input and diagnostic boundary

Protocol-v1 parsing is deliberately canonical rather than permissive. A v1 Cerberus presence/response must carry every required v1 capability and no capability bits Guardian v1 does not know. Boolean wire fields are exactly `0` or `1`; malformed alternate non-zero values are rejected. UTF-8 fields remain byte-bounded by their existing protocol limits and additionally reject control characters, Unicode format controls, and Unicode line/paragraph separators. The total payload ceiling is inclusive: exactly `65,536` bytes is valid when the payload is otherwise structurally valid, while larger payloads are rejected.

Velocity Admission state is exact-connection-scoped. Mutable handshake sessions and immutable admitted grants are keyed by the exact proxy `Player` connection identity, not merely the authenticated UUID. This prevents a delayed disconnect or asynchronous completion from an older same-UUID connection from removing or completing a newer connection's state. A denied terminal session is retained until that exact connection disconnects, preventing late response/presence packets from opening a second handshake after the final deny.

Paper active-inspection evidence is exact-connection-scoped for the same reason. Standalone authoritative snapshots and Velocity-authority backend evidence are owned by the exact Bukkit `Player` instance that captured them. A new same-UUID connection therefore cannot inherit the prior connection's staff-visible snapshot while its own admission is still completing, and a delayed quit callback from the older connection cannot delete evidence already owned by the newer one.

Guardian does not retain a decoded manifest for inspection until nonce/protocol/capability/canonical validation has succeeded. Separately, values crossing into logs or administrator-facing diagnostics are bounded and forced to one visible line. Newlines, tabs, terminal/control characters, bidirectional/format controls, and Unicode line separators are escaped. Dynamic MiniMessage values continue to use `Placeholder.unparsed`, so strings such as `<click:...>` remain literal text rather than formatting/events. These controls protect diagnostic integrity; they do not change the fundamental threat model or turn Cerberus reporting into remote attestation.

Administrator-resource filesystem hardening also applies to directory components: Guardian-owned nested directories such as `locales/` must be real directories rather than symlinks before packaged defaults are installed or locale files are read. This closes parent-directory redirection without imposing a policy on how the administrator mounts the plugin data root itself.

### Standalone Paper PLAY quarantine boundary

Standalone Paper still uses the proven CONFIGURATION-presence → bounded PLAY challenge/response path for compatible Cerberus clients. During that short PLAY window Guardian treats the player as quarantined until the exact bound session receives an allow decision. The quarantine is enforced by a dedicated listener which resolves the exact `Player` identity from the admission-session registry rather than treating a UUID as a connection identity.

The quarantine covers more than ordinary movement and block interaction. Quarantined players cannot move/teleport/portal, open/click/drag inventories, alter signs/books/held inventory state, drop or collect items/experience, use buckets/fishing/shearing/leashes, issue commands/chat, mount/dismount or enter/exit/damage vehicles, launch projectiles, damage other entities directly or through a player-shot projectile, or change spectator/flight/sneak/sprint state before the decision completes. Incoming damage to the quarantined player is also cancelled. Guardian explicitly cancels portal and End-gateway teleport event families instead of assuming every teleport shares the ordinary move handler list, and it blocks spectator-target transitions rather than relying only on teleport cancellation. Specialized state-changing events with their own Paper/Bukkit handler lists (including precise entity interaction, armor-stand/item-frame mutation, flower pots, lecterns, entity naming, harvesting, block shearing, pick-item inventory swaps, and sign commands) are guarded explicitly rather than relying on Java event-class inheritance.

This is deliberately a bounded admission quarantine, not a general-purpose anti-cheat or sandbox. It covers supported server event surfaces during Guardian's short handshake window; it does not claim to provide packet-level isolation or protection against a compromised server/plugin that deliberately bypasses Paper event semantics.

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

The membership rule is:

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

Guardian deliberately uses a bounded, administrator-readable language rather than assuming every Fabric version follows semantic versioning.

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

SHA-256 means the exact top-level archive bytes reported by a cooperating Cerberus client matched an approved identity. It is not hostile-client remote attestation; a deliberately replaced/hostile client remains outside the assurance provided by cooperating-client artifact reporting.

## Directory and ambiguous origins

Top-level `DIRECTORY` and `MIXED_OR_UNKNOWN` origins have explicit profile actions:

```yaml
origins:
  directory: DENY
  mixed-or-unknown: DENY
```

Production defaults deny both. Guardian does not invent a directory-tree hash. A `HASH_REQUIRED` acceptance can only be satisfied by a top-level `ARCHIVE` entry carrying an exact archive SHA-256 identity.

The origin/containment shape is canonical. A manifest entry is `NESTED` if and only if it carries a `parentModId`; any other combination is malformed before policy bypasses are considered. Cerberus cross-checks Fabric Loader's containing-mod relationship with the Loader's nested-origin parent ID. Inconsistent relationships, multiple/unsupported PATH roots, symlink origins, and archive paths that cannot be safely hashed at collection time are reported as top-level `MIXED_OR_UNKNOWN` with no digest. This deliberately forfeits nested/unhashed convenience in favor of conservative policy handling. Builtin entries remain Loader/game-provider metadata and are never given an artifact digest.

`/guardian inspect` and `/guardianv inspect` retain the admission-time origin for each displayed policy-addressable mod. This makes `DIRECTORY` or `MIXED_OR_UNKNOWN` explicit rather than presenting every unhashed entry as though it were a normal archive. Filesystem paths remain client-private and are never included in the manifest or inspection output. Fabric Loader's public `ModOrigin` describes where a mod was installed/initially loaded from, not a remote-attestation guarantee about the runtime code source; Guardian's SHA-256 claim remains limited to the reported cooperating-client artifact bytes.

The shared exact-artifact hashing primitive is also fail-closed against unstable filesystem inputs. Archive SHA-256 reads open the final path with no-follow semantics, remain byte-bounded while reading, and compare regular-file identity/size/mtime before and after hashing. A symlink or archive path that changes during hashing therefore fails instead of silently hashing a different filesystem object. Cerberus conservatively reports an archive that cannot be stably hashed as `MIXED_OR_UNKNOWN`; the administrator artifact importer rejects an unstable candidate without mutating the catalog.

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

Administrator-file reads are part of validation. `config.yml`, `policy.yml`, loaded locale files, `artifacts.yml`, and `proxy-assertion.key` are read as bounded stable regular-file snapshots with no-follow final-path semantics; text is strict UTF-8. A symlink, directory/device, oversized file, malformed UTF-8 file, or file that changes during its read is rejected rather than reopened through a different filesystem object. Candidate loading also fingerprints the relevant files before/after the complete candidate so ordinary concurrent edits fail the operation instead of mixing generations. Paper startup recovery never follows an unsafe path: only a bounded stable ordinary config/locale file is eligible for backup-and-restore/removal.

For a Velocity-authority Paper backend, the assertion key loaded into the candidate is additionally compared against a fresh resolution of the current 32-byte key before activation. This closes the narrow case where the key changes after `config.yml` parsing but before the later generation fingerprint.

Paper and Velocity both expose this through their validated runtime reload/validation paths; `/guardian` remains Paper-local and `/guardianv` remains network-authoritative on Velocity.

Scanning `artifact-import/` records exact identities in `artifacts.yml` and refreshes the non-loaded `artifact-import-rules.yml` convenience fragment; it never edits `policy.yml` or changes an active policy snapshot. This separation is intentional because identity is global while permission is profile-specific. Copy or merge generated rules into the desired profile explicitly, then reload/validate Admission policy. For `catalog: true` rules, copy the corresponding `artifacts.yml` to whichever authority (Paper or Velocity) owns policy evaluation.

## Validation and live checks

Use `validate` before activating policy changes and `reload` only after validation succeeds. For production changes, follow the focused smoke/recovery checks in `PRODUCTION_RUNBOOK.md`; historical phase verification files remain in `docs/` for implementation provenance rather than as routine administrator instructions.
