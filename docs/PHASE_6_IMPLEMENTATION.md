# Phase 6 implementation — security and adversarial hardening

## Baseline and scope

Phase 6 starts from the completed `0.1.0-phase5` architecture and advances the implementation candidate to `0.1.0-phase6`. Phase 5's authority boundaries remain intact: Guardian-Velocity is BadWolfMC's preferred network Admission authority, standalone Guardian-Paper remains supported, backend Paper remains assertion-only in Velocity mode, Guardian Protection remains Paper-local, and `/guardian` versus `/guardianv` remain distinct host-owned administrator surfaces.

Phase 6 does not claim hostile-client remote attestation. Its purpose is to harden the supported protocol/session/filesystem/provider boundaries, make ordinary tampering more detectable, tighten manifest privacy, and document the residual limits precisely.

## Admission/session lifecycle hardening

Paper and Velocity now treat the concrete connection object—not only UUID—as the mutable Admission-session identity.

- Paper keeps explicit CONFIGURATION and PLAY bindings through `PaperAdmissionSessions` and rejects ambiguous same-UUID PLAY handoff rather than guessing which connection owns state.
- Velocity uses exact-`Player` identity registries for in-flight Admission sessions and successful grants. A delayed disconnect or asynchronous completion from an older same-UUID connection cannot remove or populate state for a newer connection.
- Terminal decisions are first-wins. Timeout/deny cannot be replaced by a late successful response.
- Velocity retains a denied terminal session until that exact proxy connection disconnects, preventing a late packet from creating a fresh Admission attempt after final denial.
- Successful mutable sessions are disposed after the bounded inspection projection/minimal immutable grant is captured.
- Paper and Velocity active inspection are exact-connection owned, so same-UUID reconnects cannot inherit or delete another connection's evidence.

## Protocol/input hardening

Protocol v1 keeps the unreleased in-place version but has a stricter canonical contract:

- exact aggregate payload and field boundaries;
- strict UTF-8 and rejection of diagnostic control/format/line-separator characters;
- exact manifest count ceiling before allocating entries;
- canonical ordering;
- duplicate mod-ID rejection;
- parent/nested consistency, containment cycle detection, and maximum containment depth;
- strict artifact SHA-256 placement/length;
- exact capability-mask validation and downgrade resistance;
- non-canonical boolean encoding rejection;
- truncation/trailing/oversized payload rejection; and
- deterministic realistic large-manifest coverage.

Protocol and policy diagnostics consume bounded/sanitized text rather than trusting client strings as log/message formatting.

## Proxy assertion boundary

The Velocity → Paper assertion remains HMAC-based because both endpoints are infrastructure Guardian controls. Phase 6 hardens that existing boundary rather than replacing it with asymmetric cryptography.

- UUID, session ID, origin, issue time, expiry/lifetime, and HMAC remain authenticated.
- Timestamp and clock-skew arithmetic fail closed on overflow/extreme values.
- Paper keeps a bounded memory-only replay guard keyed by the authenticated assertion MAC until signed expiry; an exact assertion is one-time on a backend.
- Direct backend entry without a valid assertion fails closed with `PROXY_ASSERTION_REQUIRED`.
- Each Paper configuration session accepts at most one valid proxy assertion.
- Key replacement is candidate/runtime-snapshot scoped. Files-only validation does not activate new key bytes; successful reload atomically activates the new runtime; failed reload leaves the prior runtime/key active.
- Assertion-key reads are bounded, stable, no-follow regular-file reads, and candidate construction compares actual secret bytes rather than display fingerprints.

## Standalone Paper PLAY quarantine

The short PLAY fallback handshake is isolated behind `PaperQuarantineGuard` rather than expanded inside transport code. While an exact standalone session is quarantined, Guardian blocks the relevant movement/teleport, world/entity interaction, block mutation, inventory/container, command/chat, item state, combat/damage, projectile, vehicle/mount, fishing/bucket/shearing/leash, spectator and flight/sneak/sprint event families. Specialized events with distinct handler lists are registered explicitly.

This remains an event-layer containment boundary for the short supported fallback window, not a packet sandbox. Guardian does not introduce NMS, CraftBukkit implementation access, server Mixins, reflection into implementation networking, or packet-library workarounds.

## Geyser/Floodgate failure behavior

The Phase 4 origin contract remains intact but provider lifecycle is generation-aware.

- Positive trusted Bedrock evidence still precedes Java/Cerberus handling.
- Provider exceptions and availability changes are explicit evidence states.
- If a previously available provider disappears or changes generation during an observation, that observation is discarded/fails closed unless a stable positive supported provider independently establishes Bedrock.
- A stable positive provider retains the existing cross-provider precedence behavior.
- Backend Floodgate remains diagnostic defense-in-depth in Velocity authority mode and cannot become a second Admission authority.
- Untrusted Java brand/user input cannot manufacture Bedrock origin.

## Profile-provider failure semantics

`AdmissionProfileProviderGate` makes the pre-login provider failure contract explicit for both authorities.

A configured/detected provider that times out, throws, becomes unavailable, disappears, or yields no usable snapshot does **not** silently fall back to the default profile. The connection fails closed with `PROFILE_RESOLUTION_FAILED`, because provider state may have selected a stricter profile. An explicit UUID identity override may continue because its profile is already independently selected; provider-derived bypass permissions are not invented.

## Active inspection bounds and reload semantics

Long-lived inspection no longer retains the complete canonical manifest.

- snapshots keep complete Loader-known/policy-addressable counts plus at most 64 deterministic policy-addressable mod summaries;
- each authority store is bounded to 2,048 active snapshots;
- snapshots remain memory-only and active-session-only;
- exact connection ownership prevents stale same-UUID inspection inheritance/deletion;
- successful runtime activation increments a host-local generation;
- staff output identifies admission-time evidence as current or pre-reload;
- Velocity artifact/catalog labels are evaluated against the currently loaded catalog while the decision/profile/mod evidence remains explicitly admission-time state; and
- no persistent historical manifest storage is introduced.

## Diagnostic/log injection hardening

`DiagnosticText` provides bounded one-line escaping for staff/log surfaces. Protocol text itself rejects control/format/line-separator characters. Velocity debug interpolation sanitizes every untrusted argument, Paper debug logging sanitizes the completed line, MiniMessage values originating in client/user text use unparsed placeholders, and `DEBUG` still does not dump complete manifests.

Ordinary inspection output is bounded independently of protocol-v1's manifest ceiling.

## Fabric development/unusual origins

Cerberus retains Loader-known containment but treats origin shape conservatively:

- one safe top-level regular path → `ARCHIVE` + exact outer-JAR SHA-256;
- nested Loader relationship with consistent parent/container evidence → `NESTED`;
- Loader/game-provider builtin metadata → `BUILTIN` only when not simultaneously claiming a nested container;
- a development directory → `DIRECTORY`, deliberately unhashed;
- multiple roots, unknown kinds, symlinks, inconsistent nested relationships, or archive-hash failure → `MIXED_OR_UNKNOWN`, unhashed.

No directory-tree digest is invented. Production defaults can reject development/ambiguous origins while an administrator may deliberately allow them through policy.

## Administrator filesystem/reload hardening

Runtime/configuration files are treated as bounded stable filesystem snapshots rather than check-then-reopen paths.

- final-path symlinks/non-regular objects are rejected;
- text inputs are strict UTF-8 with explicit byte ceilings;
- Guardian-owned child directories such as `locales/` must be real directories rather than symlink redirects;
- the plugin data root remains a deployment-owned mount boundary;
- policy/catalog/config/key candidate loading detects ordinary concurrent changes rather than mixing generations;
- validation remains non-activating; reload remains atomic; failed activation retains the previous runtime; and
- startup recovery only backs up/replaces a bounded stable ordinary file.

The shared exact-artifact SHA-256 primitive now opens the final archive path no-follow, enforces the byte ceiling while reading, and verifies regular-file identity/size/mtime before and after hashing. This applies to both Cerberus archive collection and administrator artifact import.

## Signed official Cerberus release identity

Phase 6 adopts optional Ed25519 release provenance for official Cerberus artifacts.

The offline release signer signs the Cerberus release version plus a canonical logical-JAR SHA-256 identity. The canonicalizer is bounded by total uncompressed content, entry count, and entry-name size; rejects duplicate entries and non-canonical/path-confusable/control-character entry names; excludes the signature metadata entry to avoid circularity; and deliberately ignores ZIP ordering/compression/timestamps while committing to every signed logical file's name, length, and content digest.

Shared policy may require the signed-release capability and trust one or more public Ed25519 release keys. Stock Cerberus advertises that capability only when its embedded metadata matches its Fabric release version and recomputed canonical JAR identity.

**Trust claim:** this establishes that the reported release metadata corresponds to logical artifact bytes signed by a configured release key. It improves detection of ordinary modification/repackaging/unofficial builds. It does not prove that a hostile process actually executed those bytes or reported them honestly. The release private key is offline-only and never packaged.

## Guardian → Cerberus server authentication

Phase 6 also adopts optional Ed25519 Guardian server authentication to protect manifest privacy from arbitrary servers.

- Guardian authority keeps `guardian-server-auth.key` (private Ed25519 key).
- official Cerberus releases carry one or more public Guardian trust anchors in `META-INF/guardian/trusted-server-keys.txt`;
- those anchors are injected before the canonical release digest, so release signing commits to them;
- Cerberus resolves the trust-anchor resource through its own Fabric `ModContainer`, not a shared classloader lookup;
- trust-anchor reads are bounded, provider-aware, and stable regular-file reads; ordinary filesystem resources retain no-follow channel semantics while signed JAR/ZIPFS resources use the provider-supported read-only channel form;
- Guardian signs protocol version, required capability mask, fresh nonce, authenticated player UUID, issued time, and expiry;
- Cerberus verifies the signature, UUID binding, validity/skew, and pinned key before collecting/disclosing the manifest; and
- multiple public keys permit staged key rotation without embedding any reusable client secret.

The player UUID binding prevents an ordinary malicious online-mode server from obtaining a BadWolfMC signature for its own identity and relaying it to a different victim merely to harvest that victim's manifest.

**Trust claim:** this authenticates possession of a configured Guardian server private key for a short-lived player-bound challenge. It is not hostname authentication or hostile-client attestation. Same-player capture within the small accepted validity/skew window remains a documented residual limit; closing that would require a client-first freshness round trip not claimed by this phase.

## Key/release hygiene

`proxy-assertion.key` and `guardian-server-auth.key` are conventional private infrastructure filenames and are explicitly ignored by Git. Neither private key belongs in source control, distributable artifacts, logs, configuration examples, or support bundles. The Cerberus release private key is supplied only to the offline signing task.

## Maintainability review

Phase 6 deliberately extracted focused security components where new invariants would otherwise expand the lifecycle adapters: `PaperAdmissionSessions`, `PaperQuarantineGuard`, exact-identity Velocity registries, inspection services/stores, safe filesystem primitives, profile-provider gate, replay guard, Guardian challenge signing/key resolution, and protocol release/authentication models. The proven transport/lifecycle classes remain large, but they were not mechanically refactored merely to reduce line count.

Guardian server code still contains no NMS/CraftBukkit implementation dependency, implementation reflection, server Mixin, ProtocolLib/packet-event workaround, or private networking access. Cerberus's Minecraft networking types are the normal Fabric/Minecraft custom-payload API surface.

## Final closeout status

The final Phase 6 source/executed inventory contains **355 `@Test` cases**:

```text
guardian-core        117
guardian-paper        91
guardian-protection   20
guardian-protocol     50
guardian-velocity     58
cerberus-fabric       19
```

The operator-executed Java 25 / Gradle 9.7.1 gate is green at **355 tests, 0 failures, 0 errors, 11 skipped**. The 11 skips are documented Windows symlink-privilege assumption skips (Core 4, Paper 4, Velocity 3); all other tests executed. The additional Cerberus regression covers trust-anchor loading from the JDK ZIP filesystem after live signing exposed that ZIPFS rejects `LinkOption.NOFOLLOW_LINKS` as a channel-open option. The final implementation keeps no-follow descriptor semantics for ordinary filesystem resources and uses provider-compatible read-only access for immutable signed-JAR entries while retaining pre/post stable-file checks.

Focused live verification then passed the signed standalone happy path, bounded PLAY quarantine timeout, deliberate wrong Guardian server-authentication identity with manifest nondisclosure/fail-closed recovery, and the Velocity-authoritative/backend-assertion path. Built Paper, Velocity, and Cerberus Phase 6 JARs were inspected with no private-key entry found. Phase 6 is closed; `PHASE_6_VERIFICATION.md` records the detailed evidence and `PHASE_7_HANDOFF.md` is the active next-phase handoff.
