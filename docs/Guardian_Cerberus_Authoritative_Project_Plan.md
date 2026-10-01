# Guardian & Cerberus
## Authoritative Project Plan and Implementation Contract

**Project:** BadWolfMC Guardian / Cerberus
**Document status:** Living implementation contract; Phase 0 complete; Phase 1 architecture revised for Guardian Protection
**Initial target:** Minecraft / Paper 26.2, Java 25
**Future target:** 26.3 after Paper 26.3 reaches a stable API
**Date:** 2026-09-24

---

## 1. Purpose and authority

This document records the agreed architecture, constraints, threat model, implementation phases, deployment modes, and acceptance criteria for the Guardian/Cerberus project.

It is intended to serve as the primary project-source reference during implementation and as handoff context between development chats.

Where this document uses **MUST**, **MUST NOT**, **SHOULD**, or **MAY**, those terms describe project requirements rather than informal suggestions.

If later implementation work intentionally changes a decision in this document, the change should be recorded in this document or a successor implementation contract rather than allowed to drift silently.

This is a **broad project contract**, not a frozen wire-protocol specification. Some low-level decisions are intentionally deferred until the Phase 0 feasibility prototypes establish which public APIs are cleanly usable.

---

## 2. Project summary

Guardian is a server-side policy and protection ecosystem intended to replace BadWolfMC's existing BrandBlocker plugin and selected still-useful server-protection functionality from BadWolfMC's eZProtector fork.

Guardian has two independent domains:

- **Admission** — client classification, Cerberus attestation, client/mod policy, Bedrock classification, and trusted Velocity → Paper admission;
- **Protection** — Paper-side command execution policy, command/argument visibility policy, namespaced-command policy, and associated feedback/notifications.

Cerberus is Guardian Admission's companion client-side Fabric mod.

The central Admission problem is that a Paper server can usually identify a client's self-reported brand but cannot directly inspect a Fabric client's installed mods. Cerberus will use Fabric Loader's public API to enumerate the client's loaded mods and report a constrained manifest to Guardian when Guardian policy requires attestation.

Guardian Admission will evaluate that manifest against the player's configured policy and either admit or deny the connection.

Guardian Protection will provide a modern successor to the selected command-control and command-disclosure behavior BadWolfMC still uses from eZProtector. Protection MUST remain logically and operationally independent from Admission: ordinary Paper-side protection MUST NOT require Cerberus, Guardian-Velocity, Geyser, Floodgate, or a trusted proxy admission.

The Admission design preserves the practical purpose of BrandBlocker: stopping accidental, inattentive, or low-effort client-policy violations. It is **not** intended to provide hardware-backed remote attestation or to defeat a determined attacker who modifies their client specifically to lie to Guardian.

---

## 3. Project goals

Guardian/Cerberus MUST:

1. Preserve the useful client-brand enforcement currently provided by BrandBlocker.
2. Allow approved Fabric clients when Cerberus is present and the reported mod manifest satisfies Guardian policy.
3. Clearly distinguish Fabric-without-Cerberus from Fabric-with-Cerberus-but-disallowed-mods.
4. Support Java vanilla, approved Java client brands, Fabric + Cerberus, and Bedrock through Geyser/Floodgate as distinct first-class client classifications.
5. Support a standalone Paper deployment.
6. Support an enhanced Velocity + Paper deployment without making Velocity mandatory.
7. Prefer pre-world admission decisions using supported CONFIGURATION-stage APIs.
8. Avoid NMS, reflection into implementation internals, server-side Mixins, packet-library dependencies, and version-specific internal classes.
9. Use only supported Paper, Velocity, Fabric, Java, Geyser/Floodgate, and optional permissions-provider APIs.
10. Keep the player experience simple: a normal Fabric user should only need to install Cerberus and approved mods.
11. Provide specific, understandable denial reasons.
12. Keep all security-sensitive trust assumptions explicit.
13. Be open-source and preserve appropriate BrandBlocker attribution/license obligations.
14. Be maintainable across Minecraft/Paper/Fabric updates.
15. Minimize collection and retention of client information.
16. Provide an independent Guardian Protection domain for selected modern eZProtector successor functionality.
17. Keep Admission and Protection independently enableable and independently testable.
18. Support explicit allowlist and denylist modes for command visibility and namespaced-command policy.
19. Keep all player/staff-facing message and feedback strings in translatable locale resources rather than Java source.
20. Use Adventure components and MiniMessage for Guardian-controlled player/staff-facing text, with safe typed internal placeholders.
21. Use versioned, validated configuration files with explicit recovery semantics: an invalid administrator file is never silently overwritten; recoverable initial-startup syntax/validation failures are preserved to timestamped backups before packaged defaults are restored, while reload failures leave both the edited file and prior active snapshot untouched.
22. Use new `guardian.*` permission nodes and provide an explicit eZProtector → Guardian permission migration guide rather than retaining legacy permission aliases.
23. Keep Guardian permissions grouped by stable product domains (`guardian.admission.*`, `guardian.protection.*`, and `guardian.command.*`) rather than exposing Gradle/module names as the administrator-facing permission API.
24. Make client-admission policy explicit per normalized client classification, with deterministic `ALLOW`, `DENY`, or `REQUIRE_CERBERUS` behavior rather than ambiguous implicit defaults.
25. Provide a flexible Fabric mod-policy model with explicit allowlist/denylist semantics, orthogonal required-mod rules, per-mod constraints, deterministic conflict validation, and no need to whitelist irrelevant runtime/bootstrap entries merely to use allowlist mode.

---

## 4. Explicit non-goals

Guardian/Cerberus MUST NOT be represented as:

- a tamper-proof anti-cheat;
- proof that the player's complete JVM or operating system is unmodified;
- proof that no Java agent, native injection, custom launcher, or hidden modification exists;
- hardware-backed remote attestation;
- a guarantee that an adversarial client cannot spoof a Java client brand;
- a replacement for ordinary server/network security;
- a replacement for a firewall around Velocity backend servers;
- a general-purpose "everything security-ish" grab bag;
- a revival of eZProtector's historical client-specific plugin-message countermeasures;
- a claim that hiding a command from the client is equivalent to securely preventing its execution;
- a permanent eZProtector configuration or permission compatibility layer.

A determined user who patches Cerberus, Fabric Loader, the Minecraft client, or the networking path can potentially falsify what the server sees.

The product goal is **effective policy enforcement for cooperative and ordinary clients plus a meaningful barrier against low-effort circumvention**, which matches the historical purpose of BrandBlocker.

---

## 5. BrandBlocker lineage

Guardian is a hard fork and substantial rewrite of BrandBlocker.

The existing BrandBlocker implementation currently:

- operates as a Paper plugin;
- checks client brand after `PlayerJoinEvent`;
- delays the check by 20 ticks;
- uses configured brand substrings for whitelist/blacklist behavior;
- supports a bypass permission;
- uses a configured Geyser username prefix as a bypass;
- executes a console kick command for denied clients.

Guardian MUST preserve the useful product behavior while replacing the implementation architecture.

Guardian SHOULD NOT preserve the following legacy implementation patterns:

- post-join delayed enforcement when an earlier supported connection phase is available;
- username-prefix trust for Bedrock detection;
- substring matching as the only client classification mechanism;
- console-command dispatch as the primary disconnect mechanism;
- generic denial messages for materially different failure modes;
- configuration settings that are present but not actually enforced.

The Guardian README MUST retain appropriate attribution to the original BrandBlocker project and author.

### 5.1 eZProtector lineage and selected successor scope

BadWolfMC also maintains a GPLv3 fork of eZProtector. According to the BadWolfMC project history, that fork has not incorporated code from later upstream continuations since the original project was abandoned around 2021.

Guardian MUST treat the supplied BadWolfMC GPLv3 eZProtector fork as the only source lineage for any eZProtector-derived implementation work unless licensing is intentionally revisited.

Guardian Protection SHOULD replace only the still-useful server-protection concepts:

- configurable command execution restrictions;
- command visibility/root-command filtering;
- argument suggestion suppression associated with hidden command roots;
- namespaced-command policy;
- bypass policy;
- permission-gated staff notifications;
- structured protection decisions/actions.

Guardian MUST NOT port or preserve eZProtector's historical:

- Fabric/Forge/LiteLoader/Rift brand-based mod blocking;
- 5zig/BetterSprinting/Schematica/WorldDownloader/BetterPvP/VoxelMap countermeasures;
- fake `/plugins` output;
- fake `/version` output;
- raw config reload implementation;
- legacy Waterfall/Velocity implementation techniques.

Those client/mod features are superseded by Guardian Admission where a modern equivalent is appropriate; otherwise they are retired.

Guardian SHOULD provide a migration guide from eZProtector configuration concepts and permission nodes to Guardian Protection. Guardian MUST NOT require permanent support for the old eZProtector YAML schema or `ezprotector.*` permission nodes.

The Guardian README SHOULD contain an Acknowledgements/Provenance section covering both BrandBlocker and eZProtector lineage.

---

## 6. Repository and module architecture

The project SHOULD use a single Gradle multi-project repository.

Recommended structure:

```text
Guardian/
├── guardian-core/
├── guardian-protocol/
├── guardian-protection/
├── guardian-paper/
├── guardian-velocity/
├── cerberus-fabric/
└── docs/
```

### 6.1 guardian-core

`guardian-core` is the platform-neutral **Admission** domain.

It MUST contain Admission logic, including:

- client classifications;
- Admission policy models;
- Admission policy resolution results;
- manifest models;
- manifest evaluation;
- Admission denial/allow reason models;
- Admission validation rules;
- Admission configuration-domain objects.

It MUST NOT become a generic dumping ground for Protection behavior merely because both domains ship in the same plugin.

It MUST NOT depend on Paper, Bukkit, Velocity, Fabric, Minecraft implementation classes, Geyser, or Floodgate.

Phase 3 admission-policy parsing, validation, normalization, and immutable snapshot/domain construction MUST also be platform-neutral. Adapter-specific code MAY own data-directory discovery, file I/O triggers, logging, and lifecycle wiring, but the path from admission-policy source data to a validated immutable policy snapshot MUST NOT depend on Bukkit/Paper `YamlConfiguration`, Velocity implementation classes, or another adapter-specific configuration API. If a separate internal shared module is introduced for this boundary, it must obey the same dependency rules as `guardian-core`.

### 6.2 guardian-protocol

`guardian-protocol` MUST contain platform-neutral protocol definitions, including:

- protocol version;
- message types;
- canonical manifest representation;
- challenge/response structures;
- nonce/session identifiers;
- limits;
- serialization rules;
- compatibility/capability negotiation.

It SHOULD have no Paper, Velocity, Fabric, Bukkit, or Minecraft dependency.

### 6.3 guardian-protection

`guardian-protection` is the platform-neutral **Protection** domain.

It SHOULD model concepts such as:

- command execution rules;
- command visibility rules;
- namespaced-command rules;
- bypass decisions;
- protection outcomes/reasons;
- notification/action requests.

It MUST NOT depend on Paper/Bukkit event classes and MUST NOT depend on `guardian-core` merely to gain access to Admission behavior.

It is an internal library module and is not an administrator-installed JAR.

### 6.4 guardian-paper

`guardian-paper` is the standalone-capable Paper adapter and the runtime host for both Guardian domains.

It MUST be capable of acting as the authoritative Admission enforcement point when no Guardian-Velocity instance is providing a trusted admission result.

It MUST also adapt Guardian Protection to supported Paper command/Brigadier APIs.

Admission and Protection MUST be independently enableable. `guardian-paper` MUST be capable of loading with:

- Admission enabled and Protection disabled;
- Protection enabled and Admission disabled;
- both enabled.

Paper-specific configuration loading, locale/message rendering, lifecycle integration, and command-tree refresh behavior MAY be shared by the two adapters inside `guardian-paper`, but the domain modules MUST remain independent.

### 6.5 guardian-velocity

`guardian-velocity` is an optional Velocity adapter.

When configured as authoritative, it SHOULD perform initial classification, Cerberus attestation, and policy evaluation once per proxy connection before the player is admitted to a backend.

Guardian-Velocity remains Admission-focused for the initial product. Proxy-side Protection MAY be added later only if a concrete network-global or proxy-owned-command requirement justifies it.

### 6.6 cerberus-fabric

`cerberus-fabric` is the client-side Fabric mod.

It MUST:

- use public Fabric/Fabric Loader APIs;
- inventory Loader-known mods;
- respond only to supported Guardian protocol requests;
- avoid unnecessary UI or player interaction;
- avoid collecting or reporting unrelated machine information.

---

## 7. Supported deployment modes

Guardian MUST support at least the following deployment modes.

### 7.1 Standalone Paper

```text
Client
  ↕
Guardian-Paper
  ↕
Paper
```

Guardian-Paper is authoritative.

Fabric clients requiring attestation communicate directly with Guardian-Paper.

### 7.2 Velocity-authoritative network

```text
Client
  ↕
Guardian-Velocity
  ↕
Velocity
  ↕
Guardian-Paper
  ↕
Paper backends
```

Guardian-Velocity is authoritative for the network login.

Guardian-Paper verifies a trusted proxy admission assertion and MUST NOT independently initiate a redundant Cerberus attestation for ordinary backend switching.

### 7.3 Paper behind Velocity without Guardian-Velocity

If technically supported by the Phase 0 prototypes, Guardian-Paper MAY operate behind an otherwise transparent Velocity proxy without the Guardian-Velocity module.

This is useful for generic deployments but MUST NOT compromise correctness or force unsupported packet/NMS workarounds.

### 7.4 Configuration authority

A deployment MUST have one authoritative policy evaluator for a connection.

The **policy system itself is shared**. Phase 3 MUST define one platform-neutral admission-policy schema/model, parser/validator, immutable snapshot representation, profile-resolution contract, and evaluator that can be consumed by either Guardian-Paper or Guardian-Velocity. The two adapters MUST NOT grow independent policy semantics merely because they own different connection lifecycles. Platform-local operational configuration (for example transport timings, data-directory location, proxy assertion provisioning, or diagnostics) MAY remain adapter-specific.

In Velocity-authoritative mode:

- policy definitions are loaded by Guardian-Velocity using the same shared Phase 3 policy format and evaluator used by standalone Guardian-Paper;
- backend Guardian-Paper instances verify the proxy's trusted admission result;
- backend copies MUST NOT independently reinterpret the same connection using potentially divergent policy files.

This prevents proxy/backend split-brain while also preventing a Phase 5 policy rewrite.

Standalone fallback behavior MAY have its own local copy of the same shared admission-policy format, but the active deployment mode must be explicit and diagnosable. Adapter-specific operational configuration MUST remain separate from the shared admission-policy semantics.

---

## 8. Client classification model

Guardian MUST classify the connection before deciding whether Cerberus is required.

Conceptual flow:

```text
Connection
    ↓
Determine trusted connection origin
    ├── BEDROCK
    │       ↓
    │   Bedrock policy
    │   Cerberus not applicable
    │
    └── JAVA
            ↓
       Determine Java client class
            ├── VANILLA
            ├── OPTIFINE
            ├── FABRIC
            └── UNKNOWN
                    ↓
               policy action
```

Initial client classes SHOULD include:

```text
BEDROCK
JAVA_VANILLA
JAVA_OPTIFINE
JAVA_FABRIC
JAVA_UNKNOWN
```

The classification model SHOULD be extensible so future client types can be added without rewriting the policy engine.

Cerberus remains Fabric-only unless a future project decision explicitly changes that.

---

## 9. Geyser and Floodgate

Geyser/Floodgate support is a first-class Guardian integration, not a bypass hack.

Guardian MUST NOT trust the configured Bedrock username prefix as proof that a connection is Bedrock.

When the relevant APIs are available:

- `GeyserApi#isBedrockPlayer(UUID)` MAY be used to identify a Geyser Bedrock connection;
- `FloodgateApi#isFloodgatePlayer(UUID)` MAY be used to identify a Floodgate player;
- backend Floodgate data MAY be used when proxy Floodgate forwarding is configured correctly.

Provider capability absence and provider query failure are distinct states. Positive supported API evidence MUST classify the current connection as Bedrock even if another optional provider is absent, negative, or unhealthy. If no provider supplies positive Bedrock evidence and an integration that Guardian expected to query fails, connection origin is indeterminate and Admission MUST fail closed rather than silently reinterpret the connection as Java. Contradictory explicit Geyser/Floodgate answers MUST remain diagnostically visible.

Phase 6 provider-lifecycle hardening distinguishes a provider that was never present from one that disappears after Guardian had observed it as available. A provider that was never present remains ordinary `UNAVAILABLE`. A previously available provider that disappears becomes an integration error for subsequent authoritative origin decisions until it is restored. Provider observations are accepted only if that provider's lifecycle generation remains stable across the combined origin check; a lifecycle change during that observation makes the affected signal `ERROR` and therefore fails closed unless another stable supported provider supplied positive Bedrock evidence. Once one Admission session has completed a stable origin classification, that admission-time evidence remains its immutable decision input; later provider changes affect later origin checks rather than silently reclassifying an in-flight or already-admitted connection.

A positively classified Bedrock connection MUST NOT receive a Cerberus challenge.

A Java player whose username resembles the configured Floodgate prefix MUST NOT be classified as Bedrock solely because of that name.

In BadWolfMC's Velocity-authoritative deployment, Geyser/Floodgate classification at Guardian-Velocity is authoritative for connection origin.

Guardian-Paper MAY sanity-check the proxy assertion against backend Floodgate state when backend Floodgate information is available, but that sanity check MUST NOT become a second independent policy authority. A valid authenticated Guardian-Velocity assertion remains authoritative for Admission; backend disagreement or provider failure is security-relevant diagnostic evidence, not an invitation for Paper to re-run player policy.

Unexpected disagreement between trusted proxy classification and backend Floodgate classification MUST be logged prominently and handled according to that authority boundary.

---

## 10. Policy model

Guardian policy determines what a client classification must do.

Conceptually:

```text
Client classification
        ↓
Resolved Guardian profile
        ↓
Classification action
        ├── ALLOW
        ├── DENY
        └── REQUIRE_CERBERUS
```

The canonical policy model MUST support an explicit action for each normalized client class. A default profile might conceptually represent:

```yaml
clients:
  bedrock: ALLOW
  vanilla: ALLOW
  optifine: ALLOW
  fabric: REQUIRE_CERBERUS
  unknown: DENY
```

The exact YAML spelling/file layout remains deferred, but the internal model is not: a two-state boolean such as `allow-fabric: true/false` is insufficient because Fabric has a meaningful third state, `REQUIRE_CERBERUS`. An implementation MAY offer simple boolean sugar for truly two-state classes, but configuration MUST normalize to the explicit action model above and MUST reject contradictory representations.

Known normalized client classes SHOULD initially include:

```text
bedrock
vanilla
optifine
fabric
unknown
```

These are administrator-facing policy keys corresponding to the internal classifications from Section 8. Future client classes MAY be added without changing the policy-evaluation architecture.

### 10.1 Brand-rule compatibility and unknown Java clients

Guardian MUST preserve the useful product behavior of BrandBlocker without making raw brand strings a trust boundary.

For Java connections that remain `JAVA_UNKNOWN` after supported classification, policy MAY provide an explicit normalized brand-rule layer with `ALLOWLIST` or `DENYLIST` behavior. Brand rules:

- MUST use deterministic normalized exact values and/or explicitly configured patterns;
- MUST NOT rely on accidental substring matching;
- MUST NOT override a trusted Bedrock origin;
- MUST NOT downgrade a positively classified Fabric client from `REQUIRE_CERBERUS` to ordinary `ALLOW`;
- MUST be treated as administrator policy over self-reported client metadata, not proof of client identity;
- MUST have explicit no-match behavior.

This provides the useful legacy ability to allow or deny unusual Java brands while preserving Guardian's stronger classification model.

### 10.2 Fabric mod-policy model

A valid Cerberus manifest is evaluated by a separate mod-policy layer.

The mod-policy model MUST support:

- explicit `ALLOWLIST` and `DENYLIST` modes, or an equivalent unambiguous default-unlisted action;
- required mods as an orthogonal requirement rather than overloading allow/deny membership;
- explicit per-mod `ALLOW`/`DENY` rules where needed;
- unknown/unlisted-mod behavior that is deterministic and visible to administrators;
- acceptable versions/version predicates;
- optional artifact hash constraints;
- rules for contained/nested mods;
- protocol compatibility requirements;
- Cerberus minimum/maximum supported versions where necessary.

Conceptually:

```text
valid Cerberus manifest
        ↓
identify policy-addressable entries
        ↓
required-mod checks
        ↓
per-mod explicit rules
        ↓
unlisted/default mode
        ↓
version/hash/contained-mod constraints
        ↓
ALLOW or MANIFEST_DENIED
```

`ALLOWLIST` mode conceptually means an otherwise policy-addressable unlisted mod is denied. `DENYLIST` mode conceptually means an otherwise policy-addressable unlisted mod is allowed. Required-mod checks remain active in either mode.

A required-mod declaration SHOULD also make that mod permitted for membership purposes; administrators should not need to duplicate the same mod in both `required` and `allowed` merely to express “this mod must be present.” A required mod MAY carry its own version/hash constraints. An explicit unconditional deny of the same required mod is a configuration conflict and MUST fail validation.

Guardian MUST distinguish policy-addressable client mods from baseline/bootstrap/runtime manifest entries that an administrator should not have to enumerate merely to make allowlist mode usable. The exact baseline treatment is deferred until real manifests are characterized in Phase 2, but it MUST be explicit, deterministic, documented, and tested. Nested/contained entries MUST NOT become an invisible bypass.

Configuration conflicts such as the same mod being simultaneously required and unconditionally denied, duplicate rule IDs, invalid version expressions, or incompatible rule definitions MUST fail validation rather than depend on undocumented precedence.

Per-mod rules MUST key primarily by canonical Fabric mod ID, not display name. Fabric mod IDs are bounded identifiers and are therefore suitable for deterministic policy and permission suffixes.

### 10.3 Admission evaluation precedence

Client and mod policy are profile-scoped. Evaluation MUST use one documented precedence rather than allowing event/listener order to change the result.

Conceptually:

```text
trusted origin classification
        ↓
normalized Java client classification (when applicable)
        ↓
resolve admission profile
        ↓
evaluate client-class action
        ├── DENY
        │    └── applicable client-policy bypass may exempt
        ├── ALLOW
        │    └── no Cerberus interrogation required
        └── REQUIRE_CERBERUS
             ↓
        complete and structurally validate Cerberus protocol
             ↓
        evaluate mod policy
             ↓
        apply applicable mod-policy bypasses
             ↓
        ALLOW or DENY
```

Raw brand rules are subordinate classifier/policy input only where Section 10.1 permits them. They MUST NOT be evaluated late as a generic override capable of reversing trusted origin, protocol, or manifest-integrity decisions.

Policy evaluation MUST be deterministic and independently testable in `guardian-core`.


---

## 11. Identity, permission, and Protection policy resolution

### 11.1 Admission profile resolution

Pre-world enforcement occurs before a normal Bukkit `Player` permission context necessarily exists.

Therefore Guardian MUST NOT assume that Bukkit permissions can always resolve a pre-login profile.

Recommended resolution order:

```text
1. Explicit configured identity/UUID override, if any
2. Highest-priority matching named admission profile from a supported pre-login-capable permission/provider path
3. Default Guardian profile
```

LuckPerms SHOULD be a first-class optional integration because its API can asynchronously load user data by UUID before a player is fully online.

Named admission profiles SHOULD use a stable permission shape:

```text
guardian.admission.profile.<profile-id>
```

Profile IDs used in permission nodes MUST be normalized, bounded administrator-defined identifiers rather than arbitrary display text.

If more than one named profile matches a player, resolution MUST be deterministic. Profiles SHOULD carry an explicit numeric priority; an ambiguous tie between simultaneously matching profiles MUST either be rejected by validation or resolved by a separately documented deterministic rule. Guardian MUST NOT depend on provider iteration order.

If no pre-login-capable provider is installed:

- Guardian MUST still function using a default policy;
- permission-selected profiles MUST NOT silently pretend to work;
- administrative emergency overrides SHOULD use a mechanism that is valid before login, such as explicit UUID configuration.

If a pre-login provider has been selected because it is present, but resolution for a connection
throws, times out, returns no usable snapshot, becomes unavailable, or disappears after startup,
Guardian MUST NOT silently reinterpret that player under the default profile. The connection MUST
fail closed with `PROFILE_RESOLUTION_FAILED` because the unavailable provider may have selected a
stricter profile. An explicit UUID identity override remains independently authoritative during such
a failure; Guardian MAY continue using that configured profile with an empty provider-permission
snapshot, which deliberately removes rather than invents provider-derived bypasses.

The exact generic profile-provider SPI MAY be defined during implementation.


### 11.2 Guardian Protection policy model

Guardian Protection SHOULD use platform-neutral policy objects rather than placing rule semantics directly in Paper listeners.

The initial Protection feature set SHOULD include:

1. **Command execution policy**
   - deny configured command roots belonging to Guardian or third-party plugins where appropriate;
   - execution denial is an enforcement/security boundary;
   - Guardian-owned commands SHOULD use their native Paper command permissions first rather than relying on interception.

2. **Command visibility policy**
   - control which command roots are advertised to the client;
   - support explicit `ALLOWLIST` and `DENYLIST` modes;
   - visibility is an information-disclosure/UX control and MUST NOT be represented as sufficient execution security.

3. **Argument suggestion policy**
   - if a command root is hidden by the active visibility policy, downstream argument suggestions for that root MUST also be suppressed;
   - a player who guesses a hidden root MUST NOT gain its argument suggestions merely by typing it;
   - Phase 1B MAY remain root-command scoped; arbitrary deep subcommand filtering is not required unless implementation evidence justifies it.

4. **Namespaced-command policy**
   - govern direct invocation of roots such as `plugin:command`;
   - support explicit `ALLOWLIST` and `DENYLIST` modes;
   - allowlist mode MUST permit explicitly required namespaced aliases for legitimate plugin-command conflicts.

All rule matching MUST normalize command roots deterministically and case-insensitively. Slash/no-slash representation MUST NOT change the decision.

### 11.3 Permission namespace and bypass semantics

Guardian's administrator-facing permission API MUST be organized by product domain, not by implementation module:

```text
guardian.admission.*
guardian.protection.*
guardian.command.*
```

The initial semantic hierarchy SHOULD include at least:

```text
guardian.admission.profile.<profile-id>

guardian.admission.client.bypass
guardian.admission.client.bypass.<client-key>

guardian.admission.mod.bypass
guardian.admission.mod.bypass.<modid>

guardian.protection.bypass
guardian.protection.command.bypass
guardian.protection.namespace.bypass
guardian.protection.visibility.bypass
guardian.protection.visibility.bypass.<command>
guardian.protection.notify

guardian.command.<administrative-command>
```

The exact administrative command leaves may be finalized with the command implementation, but the three domain roots above are fixed.

Guardian MUST provide explicit aggregate permissions such as `guardian.protection.bypass`, `guardian.admission.client.bypass`, and `guardian.admission.mod.bypass`. Correct behavior MUST NOT depend on a permissions provider expanding `*` wildcard assignments. Administrators MAY still use provider-supported wildcards such as `guardian.protection.*` for convenience.

Admission bypass semantics MUST remain policy-scoped:

- `guardian.admission.client.bypass` and its per-client form MAY bypass a classification-level `DENY`;
- a client-class bypass MUST NOT silently convert `REQUIRE_CERBERUS` into ordinary `ALLOW`;
- `guardian.admission.mod.bypass` and its per-mod form apply only after a compatible Cerberus exchange has produced a structurally valid manifest;
- mod bypass permissions MAY exempt configured mod-policy violations but MUST NOT bypass nonce/session validation, protocol compatibility, manifest structural validation, payload limits, proxy-assertion authentication, or other protocol/integrity failures;
- no broad `guardian.admission.bypass` permission is required for the initial design.

Protection bypass decisions MUST be centralized rather than duplicated independently across root-command filtering, argument-suggestion filtering, and execution listeners.

Protection semantics are:

- `guardian.protection.bypass` — aggregate exemption from Protection enforcement/visibility rules;
- `guardian.protection.command.bypass` — execution-policy exemption;
- `guardian.protection.namespace.bypass` — namespaced-command-policy exemption;
- `guardian.protection.visibility.bypass` — command-root and downstream suggestion visibility exemption;
- `guardian.protection.visibility.bypass.<command>` — visibility/suggestion exemption for one normalized command root;
- `guardian.protection.notify` — receives Protection violation notifications and grants no bypass authority.

A visibility bypass MUST apply consistently to both:

- whether the root is present in the command tree;
- whether argument suggestions for that root are suppressed.

Notification authority MUST be independent from bypass authority. Permission to observe a Protection violation MUST NOT imply exemption from the rule, and exemption MUST NOT automatically grant notification visibility.

When Guardian itself changes an active visibility configuration for online players, the Paper adapter MUST refresh affected client command trees using a supported API. Permission-change refresh behavior SHOULD use supported platform/provider mechanisms where available; stale client visibility MUST never be treated as the execution security boundary.

Guardian MUST NOT retain `ezprotector.*` aliases in the initial implementation.

### 11.4 Permission-name length and dynamic suffix constraints

Paper's permission APIs operate on permission names as strings and do not document a small fixed node-length limit. However, permissions-provider storage can impose practical limits. LuckPerms' current MySQL schema stores permission strings in `VARCHAR(200)` columns.

Guardian therefore MUST keep all Guardian-defined permission nodes comfortably below 200 characters. The project SHOULD impose a conservative maximum total permission length of **128 characters** for its own generated/documented nodes.

Dynamic suffixes used in permission names MUST be canonical bounded identifiers rather than arbitrary user-visible strings. In particular:

- Fabric mod IDs are suitable directly because Fabric constrains them to 2–64 ASCII identifier characters;
- client keys are Guardian-controlled bounded identifiers;
- admission profile IDs MUST be bounded normalized identifiers;
- per-command visibility bypass suffixes MUST use a normalized bounded command key and MUST NOT embed arbitrary command arguments or display text.

If a future feature cannot express a stable dynamic permission within this bound, it SHOULD introduce a short configured rule ID rather than lengthening the permission namespace indefinitely.

The current anticipated nodes are far below the conservative limit; for example, `guardian.protection.visibility.bypass.worldedit` is 47 characters and `guardian.admission.client.bypass.java_fabric` would be 44 characters.


---

## 12. Admission state machine

Guardian SHOULD model admission as an explicit state machine rather than scattered event handlers.

Conceptually:

```text
CONNECTED
   ↓
AUTHENTICATED
   ↓
ORIGIN_CLASSIFIED
   ↓
PROFILE_RESOLVED
   ↓
POLICY_SELECTED
   ↓
┌──────────────────────────────┐
│ Attestation required?       │
└───────────────┬──────────────┘
        no      │       yes
        ↓       │        ↓
    EVALUATE    │   CHALLENGE_SENT
        │       │        ↓
        │       │   RESPONSE_RECEIVED
        │       │        ↓
        │       └── MANIFEST_VALIDATED
        │                ↓
        └────────── POLICY_EVALUATED
                         ↓
                 ALLOW or DENY
```

Per-connection state MUST be cleaned up on disconnect, timeout, cancellation, proxy transfer termination, and plugin shutdown.

A fresh connection SHOULD require a fresh admission session.

Guardian MUST distinguish initial configuration from later Minecraft reconfiguration so a harmless reconfiguration does not accidentally cause duplicate or contradictory attestation behavior.

---

## 13. Guardian decision model

The core result SHOULD separate outcome from reason.

Example:

```text
GuardianDecision
├── outcome
│   ├── ALLOW
│   └── DENY
├── reason
└── structured details
```

Initial reason values SHOULD include at least:

```text
BEDROCK_POLICY
VANILLA_POLICY
OPTIFINE_POLICY
CERBERUS_VERIFIED
CERBERUS_REQUIRED
CERBERUS_TIMEOUT
CERBERUS_PROTOCOL_UNSUPPORTED
MANIFEST_DENIED
MANIFEST_INVALID
CLIENT_DENIED
PROFILE_RESOLUTION_FAILED
PROXY_ASSERTION_INVALID
CONFIGURATION_ERROR
```

The public/player-facing message is not itself the security decision.

Messages SHOULD be mapped from structured reasons so localization or wording changes cannot alter policy behavior.

---

## 14. Required distinction: missing Cerberus vs denied manifest

This is a hard UX and behavioral requirement.

Guardian MUST distinguish:

### Fabric detected, Cerberus absent

Result:

```text
DENY
reason: CERBERUS_REQUIRED
```

The player-facing message should clearly state that Fabric is supported but Cerberus is required.

### Cerberus present, manifest disallowed

Result:

```text
DENY
reason: MANIFEST_DENIED
```

The player-facing message should state that Cerberus responded successfully but one or more mods or versions are not permitted.

These conditions MUST NOT collapse into the same generic denial.

Timeout, unsupported protocol, and invalid response SHOULD likewise be independently diagnosable.

---

## 15. Cerberus manifest

Cerberus SHOULD derive the manifest from Fabric Loader's public API, including `FabricLoader#getAllMods()` and `ModContainer` metadata/origin relationships.

Protocol v1's Phase 2.5 canonical manifest contains only information relevant to policy enforcement:

- Minecraft version;
- Fabric Loader version;
- Cerberus version;
- Guardian protocol version/capabilities;
- mod ID;
- mod version;
- relevant parent/contained relationship;
- origin classification; and
- the exact SHA-256 digest of each applicable top-level `ARCHIVE` artifact.

`CAP_ARTIFACT_SHA256` is part of the required production protocol-v1 capability set. The digest algorithm is fixed by protocol v1 rather than client-selected. The wire representation carries exactly 32 digest bytes and malformed digest lengths are rejected.

Cerberus MUST NOT report:

- absolute filesystem paths;
- Windows/macOS/Linux usernames embedded in paths;
- launch arguments;
- unrelated hardware identifiers;
- IP information;
- arbitrary files;
- unrelated machine telemetry.

### 15.1 Nested/contained mods

Cerberus reports the complete relevant Loader-known mod relationship rather than silently deleting nested mods from the manifest.

Only a top-level `ARCHIVE` entry carries its own SHA-256. A `NESTED` entry carries no separate digest because the exact bytes of the containing nested JAR/resource are already committed by the digest of its enclosing top-level archive. Parent/child relationships remain explicit so Phase 3 can define administrator-friendly policy semantics without making nested entries invisible.

The exact Phase 3 policy semantics for contained mods remain deferred and MUST be covered by tests before release.

### 15.2 Development and ambiguous origins

Fabric development environments can have directory/classpath origins rather than ordinary release JARs. Protocol v1 deliberately does not invent directory-tree hashing.

The deterministic Phase 2.5 origin/digest contract is:

- top-level `ARCHIVE` -> exact outer-file SHA-256 is required;
- `NESTED` -> no separate digest; identity is covered by the containing archive plus containment relationship;
- `BUILTIN` -> no artifact digest;
- `DIRECTORY` -> no artifact digest;
- `MIXED_OR_UNKNOWN` -> no artifact digest and conservative future policy handling.

A top-level path origin is classified as `ARCHIVE` only when it resolves to exactly one regular non-symlink file. Multiple roots, symlinks, and other ambiguous path shapes remain `MIXED_OR_UNKNOWN` rather than being given a misleading archive identity.

Production policies MAY deny development/ambiguous origins by default while allowing a deliberately configured developer/staff exception.

---

## 16. Exact artifact identity and approved-artifact catalog

Phase 2.5 makes exact SHA-256 artifact identity a stable protocol-v1 primitive. SHA-256 verifies the exact artifact bytes reported by a cooperating Cerberus client; it does not independently prove that a hostile/replaced Cerberus client reported those bytes truthfully.

Guardian-Paper provides an administrator-managed, inert import surface:

```text
plugins/Guardian/
├── artifact-import/           # temporary input JARs; never executed or installed
├── artifacts.yml              # durable Guardian-managed exact-artifact catalog
└── artifact-import-rules.yml  # generated, non-loaded copy/paste direct-hash rules
```

`artifact-import/` is a flat directory of candidate `.jar` files. Guardian never classloads, executes, installs, copies to a mods directory, invokes Fabric Loader against, or extracts these files. The scanner uses read-only ZIP/JAR access, reads only bounded root `fabric.mod.json` metadata, derives mod ID/version from that metadata rather than the filename, and hashes the exact whole JAR bytes.

Import is explicit through `/guardian artifacts scan`, not automatic at startup. Startup creates the input directory and validates an existing catalog; the potentially heavier JAR inspection/hashing work runs only on administrator request and off the Paper primary thread.

The durable catalog is intentionally separate from Phase 3 policy. It is Guardian-managed deterministic YAML with a strict schema and generated-format ownership: administrators may inspect/edit/version-control it, but arbitrary comments/formatting are not promised to survive a subsequent Guardian rewrite. A scan validates the existing catalog and every candidate before mutation, atomically rewrites only on a successful merge, adds newly discovered exact identities, deduplicates existing hashes, and never removes historical identities merely because an input JAR disappeared. A successful scan also refreshes `artifact-import-rules.yml`, a non-loaded convenience file containing direct exact-hash ALLOW rule blocks for the JARs currently in `artifact-import/`. Guardian MUST NOT silently edit `policy.yml` as part of identity scanning because policy permission is profile-specific while artifact identity is global.

The catalog supports multiple versions of one mod ID and multiple approved hashes for the same ID/version. It records identity only; it does not make an admission decision. Phase 3 decides whether a catalogued identity is required, optional, allowed, or irrelevant under a particular administrator policy.

Hashing can help detect:

- an unexpected artifact with the same advertised mod ID/version;
- accidental local modification;
- installation of a non-approved or repacked build.

Hashing cannot prove that a hostile Cerberus client honestly hashed what is executing.

### 16.1 Signed Cerberus release identity — Phase 6 adopted design

Phase 6 adopts an **optional** signed official Cerberus release identity. The feature remains disabled by default and is configured in the portable shared `policy.yml`, so standalone Paper and Guardian-Velocity Admission authorities use the same trust rule.

The adopted release model is:

1. build the normal production Cerberus JAR;
2. compute a domain-separated SHA-256 **canonical logical JAR-content digest** over sorted entry names, uncompressed content lengths, and per-entry content hashes; ZIP ordering, compression, and timestamps are not identity inputs;
3. exclude only `META-INF/guardian/cerberus-release.bin` from that digest to avoid a circular signature dependency;
4. sign the Cerberus release version plus canonical digest using Ed25519 and a release-only private key;
5. embed the version/digest/signature in bounded binary `CRS1` release metadata inside the signed JAR;
6. let stock Cerberus advertise the optional signed-release capability only after its own Fabric metadata version and recomputed canonical digest agree with the embedded release metadata; and
7. when shared policy requires it, have the authoritative Guardian host require that capability and verify the reported identity against one of the configured Ed25519 public keys.

The release private key is consumed only by the offline Gradle release-tool source set and MUST NOT be committed, embedded in Cerberus, copied to Guardian-Paper/Guardian-Velocity, or placed in their data directories. Guardian policy contains public keys only. Multiple trusted public keys may overlap during a simple key rotation; the protocol does not require a private key or shared secret on the client.

The offline signer snapshots the unsigned JAR through a bounded no-follow read into a temporary sibling before inspecting or signing it. Release metadata/trust-anchor preflight checks, canonical digest calculation, and signature injection operate on that stable snapshot; a source JAR that changes identity, size, or modification time during the copy fails closed. This prevents a check-to-copy race from causing different bytes than the reviewed unsigned input snapshot to receive the release signature.

This provides useful compliance hardening against:

- casual modification of an official Cerberus JAR;
- accidental local changes;
- ordinary self-compiled/unsigned builds that do not possess the release private key.

It MUST NOT be represented as proof that the currently executing client code is identical to the signed artifact. The server receives signed **reported metadata** from a cooperating client. A hostile modified client can bypass Cerberus's local self-check and replay or falsely report the version/digest/signature from an official signed release while executing different code. The signature is intentionally reusable release metadata and is not bound to Guardian's fresh challenge nonce. Guardian's nonce still binds the surrounding manifest response to the current handshake, but it cannot transform release metadata into hostile-client remote attestation.

The canonical signed-release digest is also distinct from the Phase 2.5 exact-artifact SHA-256 policy identity. Exact mod-artifact hashing means the reported top-level archive bytes; signed Cerberus release identity intentionally tolerates ZIP repacking that preserves the same logical JAR files.

This mechanism therefore establishes **signed release-artifact provenance for the identity the client reports**, not hardware-backed attestation, DRM, anti-tamper execution proof, or proof of client honesty. No long-term client secret, TOTP secret, or HMAC proof is added.

Guardian-to-Cerberus server authentication is separately adopted under Section 17.3. Its server-identity key pair remains distinct from this release-signing identity; a valid Cerberus release signature does not by itself authenticate a Guardian server.

---

## 17. Protocol security and trust model

### 17.1 Nonces and replay protection

Every Cerberus attestation MUST be bound to a fresh connection/session challenge.

The challenge/response SHOULD include:

- protocol version;
- random nonce;
- authenticated player identity where appropriate;
- connection/session identifier;
- bounded timestamp or expiration where useful;
- manifest or manifest digest.

Guardian MUST reject stale, reused, mismatched, malformed, or oversized responses.

### 17.2 No embedded client secret as proof of honesty

Cerberus MUST NOT rely on a long-term secret embedded in the client as proof that Cerberus is genuine.

Any secret shipped inside open client software should be assumed recoverable.

TOTP-like schemes or HMAC secrets embedded in Cerberus would not solve the hostile-client trust problem and SHOULD NOT be used for that purpose.

### 17.3 Server authentication to Cerberus

Phase 6 adopts optional Guardian-to-Cerberus server authentication as a **manifest-privacy boundary**, not as client attestation. The design uses a Guardian-authority-held Ed25519 private key and one or more client-pinned Ed25519 public trust anchors. No long-term secret is embedded in Cerberus.

The public trust-anchor bundle is stored in an official Cerberus release as `META-INF/guardian/trusted-server-keys.txt`. The bundle is injected before the canonical Cerberus release digest is calculated, so signed official Cerberus release identity covers the exact server-authentication public keys shipped to clients. Stock Cerberus resolves the bundle from its own Fabric `ModContainer` with `findPath(...)`, not from an unscoped shared-classloader resource lookup; a different mod therefore cannot substitute a same-named trust-anchor resource. Up to eight keys may be pinned concurrently for staged rotation.

When trust anchors are embedded, stock Cerberus requires an authenticated Guardian challenge before collecting or disclosing its mod manifest. The signed challenge is domain-separated and covers protocol version, required capabilities, fresh Guardian nonce, the server-authenticated player UUID, issue time, and expiration. Cerberus MUST verify both the signature and that the signed UUID equals its own authenticated Minecraft session UUID before manifest collection. UUID binding is required so a malicious server cannot obtain a valid BadWolfMC challenge for one account and relay it to a different victim merely to harvest that victim's manifest.

Guardian server authentication is opt-in at the authority. Standalone Paper uses `plugins/Guardian/guardian-server-auth.key`; Guardian-Velocity uses the same filename in its own plugin data directory. Velocity-authority Paper backends do not hold this key because they are not the Cerberus Admission authority. If Cerberus advertises that it requires authenticated challenges and the authority has no signer configured, Guardian MUST fail explicitly rather than encourage manifest disclosure to an unauthenticated server. If Guardian enables server authentication, it MUST require the corresponding client capability so an older client cannot silently downgrade the privacy contract.

Key rotation SHOULD overlap trust roots: distribute a Cerberus release containing old+new public keys, switch the Guardian authority to the new private key, then later remove the old public key in a subsequent Cerberus release. Compromise of a server-auth private key allows impersonation of that Guardian trust identity to clients that continue to pin the compromised public key until they update; this design has no online revocation service.

This mechanism authenticates possession of a configured server key, not a DNS hostname. A deliberately modified Cerberus can disable its own privacy check. A captured signed challenge for the same player UUID can remain reusable within the bounded validity/clock-skew window because v1 does not include a client-contributed freshness nonce; eliminating that residual replay would require an additional client-first protocol step. Initial Presence metadata remains visible before server authentication; the protected disclosure is the manifest. None of these limitations weaken Guardian's server-side Admission decision, and none turn the client into remote attestation.

Private server-authentication keys MUST NOT be committed to source control or embedded in Cerberus. The server-authentication key pair, Cerberus release-signing key pair, and Velocity proxy-assertion secret are separate trust domains.

### 17.4 Proxy-to-backend trust

In Velocity-authoritative mode, Guardian-Velocity and Guardian-Paper MAY use a server-controlled shared secret or asymmetric trust mechanism to authenticate proxy admission assertions.

Unlike a secret embedded in Cerberus, this secret has real security value because both ends are infrastructure controlled by the server operator.

Proxy assertion data SHOULD be connection-scoped and short-lived.

A client MUST NOT be able to submit a payload that Guardian-Paper interprets as a valid proxy assertion.

Guardian-Velocity MUST consume/intercept its client protocol messages rather than blindly forwarding security-sensitive channels to backend servers.

**Phase 6 hardening:** Guardian-Paper treats each successfully authenticated proxy assertion payload as one-time within its signed lifetime. The backend keeps only a bounded, memory-only replay fingerprint until expiry and rejects an exact replay even for the same UUID. Assertion expiry is strict at `expiresAt`; verification uses the immutable key/runtime snapshot captured for that exact Paper configuration connection so an administrative key reload cannot change verifier state halfway through admission. A Paper configuration session accepts at most one proxy assertion. The wire format remains unchanged because the authenticated payload itself supplies a stable replay fingerprint.

---

## 18. Velocity backend security

Guardian does not replace network security.

Velocity-backed Paper servers SHOULD use Velocity modern forwarding where applicable and SHOULD be protected so untrusted clients cannot connect directly to backend servers.

Guardian-Paper MUST NOT treat a claimed proxy assertion as trustworthy merely because a packet/channel name resembles Guardian-Velocity traffic.

The Guardian threat model assumes the operator follows normal Velocity backend hardening practices.

---

## 19. Protocol versioning and compatibility

Guardian protocol version MUST be separate from plugin/mod release versions.

Example:

```text
Guardian-Paper 1.4.2
Guardian-Velocity 1.4.2
Cerberus 1.3.7
Protocol 3
```

Compatible release versions MAY speak the same protocol.

The handshake SHOULD support capability negotiation so optional additions do not automatically require a hard protocol break.

Incompatible Cerberus versions MUST produce a clear `CERBERUS_PROTOCOL_UNSUPPORTED` result rather than a generic timeout.

The disconnect message SHOULD provide an administrator-configured download/help URL or other clear remediation information.

---

## 20. Minecraft version compatibility

Initial implementation target:

```text
Minecraft 26.2
Paper 26.2 stable API
Java 25
Fabric/Fabric API compatible with 26.2
```

The project SHOULD NOT carry speculative 26.3-alpha compatibility code during initial development.

After 26.3 Paper API stabilizes:

1. complete the 26.2 architecture and tests;
2. assess API changes;
3. port Guardian/Cerberus;
4. update the compatibility matrix;
5. avoid retaining unnecessary compatibility shims for unreleased or abandoned APIs.

Cerberus releases are expected to be Minecraft-version-aware because Fabric client mod compatibility is inherently tied to the target Minecraft environment.

---

## 21. Standalone Paper networking — Phase 0A result

Phase 0A established the supported Paper 26.2 / Fabric 26.2 behavior rather than assuming full bidirectional CONFIGURATION interoperability.

### 21.1 Proven CONFIGURATION behavior

Using supported public APIs:

- Fabric 26.2 can send Guardian/Cerberus custom payloads to Paper 26.2 during CONFIGURATION.
- Cerberus can therefore announce a bounded presence payload before world entry.
- Guardian-Paper can classify the client and distinguish:
  - Fabric without Cerberus;
  - Cerberus with an unsupported protocol;
  - compatible Cerberus that is eligible to continue to the nonce handshake.
- Paper can deny missing/incompatible Cerberus before world entry with a structured reason and direct supported disconnect message.

### 21.2 Paper -> Fabric CONFIGURATION limitation on 26.2

The all-CONFIGURATION challenge/response design is **not** cleanly available through the supported high-level APIs tested on stock Paper 26.2 and stock Fabric API 26.2.

Paper's supported plugin-message send path requires the client to have advertised the outgoing channel in Paper's listening-channel set.

In the tested Paper/Fabric CONFIGURATION flow, Fabric's client channel registration is not exposed to Paper in time to satisfy that requirement. Fabric contains implementation-level machinery capable of forcing registration, but Guardian/Cerberus MUST NOT depend on Fabric `impl` classes, Minecraft implementation payload classes, Mixins, NMS, reflection, or packet-library workarounds merely to preserve an all-CONFIGURATION aesthetic.

### 21.3 Selected standalone hybrid

The selected Paper 26.2 standalone design is therefore:

```text
CONFIGURATION
    ↓
classify client
    ↓
Fabric?
    ├── no  → ordinary classification policy
    └── yes
         ↓
    Cerberus CONFIGURATION presence
         ├── absent              → CERBERUS_REQUIRED → deny pre-world
         ├── protocol unsupported→ CERBERUS_PROTOCOL_UNSUPPORTED → deny pre-world
         └── compatible
                ↓
           enter PLAY quarantined
                ↓
           wait bounded time for PLAY channel registration
                ↓
           fresh nonce challenge
                ↓
           Cerberus response + test/manifest validation
                ↓
           ALLOW and release quarantine
           or DENY with structured reason
```

The PLAY quarantine MUST remain bounded and MUST prevent meaningful interaction until the final decision is made.

Handshake timeout and PLAY channel-registration wait MUST be configurable and bounded. Exact production defaults remain subject to later hardening.

This hybrid is an intentional supported architecture, not a reintroduction of BrandBlocker's delayed post-join enforcement. Only a client that has already positively identified compatible Cerberus during CONFIGURATION is permitted to enter the temporary PLAY quarantine.

NMS MUST NOT be introduced merely to move the nonce exchange back into CONFIGURATION.

The 26.3 port SHOULD re-test CONFIGURATION interoperability rather than permanently assuming the 26.2 limitation still exists.

---

## 22. Velocity configuration-phase networking

In Velocity-authoritative mode, Guardian-Velocity performs the authoritative Cerberus handshake during Velocity's supported configuration-stage lifecycle.

Phase 0B live testing proved:

- client ↔ proxy custom payload exchange;
- deterministic CONFIGURATION hold/release behavior;
- clear proxy-origin disconnect reasons;
- security-sensitive Guardian/Cerberus channels are consumed at the proxy rather than leaked to backends;
- short-lived authenticated proxy admission state reaches Guardian-Paper before its final pre-world decision;
- server switching reuses the admission for the current proxy connection rather than unnecessarily re-attesting;
- a full disconnect/reconnect creates a fresh admission session.

Velocity is the preferred BadWolfMC production admission authority. Velocity support remains optional for Guardian generally; standalone Guardian-Paper remains supported.

---

## 23. Session and caching semantics

A successful Cerberus attestation SHOULD be scoped to a single client/proxy connection.

It SHOULD NOT be persisted as a long-lived "this player is trusted" database record.

Reason:

- the mod environment can change when Minecraft restarts;
- a new connection is cheap to attest;
- long-lived trust records would weaken the relationship between the current process and the current decision.

In Velocity-authoritative mode, the successful result SHOULD be cached only for the lifetime of the current proxy connection so Alpha → Beta → Gamma → Delta server changes do not repeatedly inventory the same running client.

A new proxy login MUST create a fresh admission session. If the new connection requires Cerberus, it MUST perform a fresh challenge rather than reuse the previous connection's admission.

---

## 24. Failure semantics

Guardian MUST define failure behavior explicitly.

### 24.1 Attestation-required client

If policy requires Cerberus and Guardian cannot obtain a valid response within the configured limit, the connection SHOULD fail closed with a specific reason.

### 24.2 Invalid manifest/protocol data

Malformed, duplicate, impossible, oversized, replayed, or invalidly signed/session-bound data MUST be rejected.

### 24.3 Unsupported protocol

An unsupported but otherwise recognizable Cerberus protocol MUST produce a compatibility-specific denial.

### 24.4 Invalid policy configuration

Policy/configuration files MUST be validated before becoming active.

Reload SHOULD use an atomic "parse → validate → replace active snapshot" pattern.

A failed reload MUST leave the prior known-good configuration active.

Startup behavior for a malformed or structurally invalid initial configuration MUST be explicit and prominently logged. Guardian-Paper MUST preserve the invalid administrator file to a timestamped backup before restoring a packaged default and must parse/validate the restored candidate from scratch before activation. Unsupported schema versions are not automatically replaced or downgraded.

This recovery MUST NOT silently fall back to permissive behavior: the restored packaged configuration is the documented safe default, and activation still requires full validation.

### 24.4.1 Configuration file safety and schema versioning

Administrator-owned Guardian configuration files MUST be versioned from their first production implementation.

A simple integer schema/version field is sufficient initially. The initial schema MAY remain at version `1` until a real migration is needed; there is no requirement to invent version churn before release.

The parser MUST distinguish at least:

- file missing;
- valid supported schema;
- malformed YAML/syntax;
- structurally invalid values;
- unsupported newer schema;
- older schema requiring a defined migration.

A missing file MAY cause Guardian to create documented defaults.

An **existing malformed or invalid file MUST NOT be silently overwritten, reset, regenerated, or replaced with defaults** merely because parsing failed.

On initial startup, Guardian MAY recover from a malformed or structurally invalid administrator file only if all of the following are true:

1. the original bytes are first preserved to a distinct timestamped backup adjacent to the file;
2. the recovery is logged prominently with both the original and backup paths;
3. packaged defaults are restored only for resources Guardian actually ships (for example `config.yml` and the required fallback locale);
4. an invalid optional locale with no packaged default may be backed up and removed so the required fallback locale can take effect;
5. the regenerated/fallback candidate is parsed and validated from scratch before activation; and
6. unsupported schema versions are **not** automatically recovered or downgraded. They require an explicit migration/administrator decision.

This recovery is a startup availability/safety mechanism, not a reload mechanism.

Reload MUST:

1. read the administrator-owned files without modifying them;
2. parse;
3. validate;
4. construct immutable candidate snapshots;
5. activate all relevant candidate snapshots atomically only after successful validation.

Administrator-controlled runtime files are read through bounded, stable, no-follow regular-file snapshots. `config.yml`, `policy.yml`, loaded locale resources, `artifacts.yml`, and `proxy-assertion.key` MUST NOT be accepted through a symbolic-link final path or other non-regular filesystem object. Text inputs are strict UTF-8. Candidate construction brackets the relevant files with content fingerprints so ordinary concurrent edits are detected and the candidate is rejected rather than combining file generations. This is an administrator-race consistency control, not protection from a hostile local administrator capable of changing and restoring file contents/metadata arbitrarily.

Paper startup recovery is narrower than validation: only an existing bounded, stable ordinary file may be preserved and replaced/removed. A symlink, directory, oversized recovery candidate, or unstable file is not followed or auto-recovered; Guardian remains disabled and requires administrator correction.

If reload fails, the prior known-good runtime snapshot MUST remain active.

Configuration and locale files SHOULD report actionable diagnostics including the file and key/path involved and, where the YAML parser exposes it, line/column information.

Automatic schema migration MAY be added when a real migration exists. Until then, unsupported schema versions should fail validation clearly rather than guessing.

### 24.5 Dependency/integration loss

If an optional integration such as LuckPerms, Geyser, or Floodgate disappears or becomes unavailable, Guardian MUST have deterministic behavior and a clear diagnostic.

It MUST NOT reinterpret a Bedrock player as "Fabric missing Cerberus" because an integration failed mid-classification.

---

## 25. Payload and denial-of-service limits

The protocol MUST define hard limits before production release, including at least:

- maximum protocol payload size;
- maximum mod count;
- maximum mod ID length;
- maximum version string length;
- maximum nested relationship depth;
- maximum hash count/size;
- maximum outstanding handshake time;
- maximum duplicate/retry behavior.

The implementation MUST reject oversized input before expensive processing where possible.

Manifest serialization SHOULD be compact and deterministic.

Compression MAY be considered only if necessary; it SHOULD NOT be added merely because it is available.

---

## 26. Client-brand behavior

Brand information is a useful policy/classification signal, not cryptographic proof.

Guardian SHOULD normalize brands deterministically and MUST avoid accidental substring behavior that lets an unrelated string satisfy an allow rule merely because it contains an approved token.

Known built-in client classifications are governed by the explicit per-class action model in Section 10. Optional brand allowlist/denylist behavior is intended primarily to preserve useful policy control over otherwise unknown Java brands; it MUST NOT supersede trusted Bedrock origin classification or remove the Cerberus requirement from a positively classified Fabric client.

Exact normalized values and explicitly requested patterns are preferable to implicit substring matching.

A null/empty/unknown brand MUST have explicit policy behavior.

Unknown brand must not default to "allow because detection failed" unless the administrator deliberately configures that outcome.


---

## 27. Privacy and production logging

Guardian/Cerberus should collect the least information necessary to enforce policy.

Before or at the beginning of Phase 5, logging becomes an explicit shared operational policy rather than remaining feasibility-era adapter output. The intentionally small supported model is:

```yaml
logging:
  level: NORMAL
```

with two levels only for the initial production implementation:

```text
NORMAL
DEBUG
```

### 27.1 NORMAL

`NORMAL` is the production/default mode. Successful Admission SHOULD produce approximately one useful summary from the authoritative host rather than a line for every protocol transition. Conceptual examples are:

```text
[Guardian] Alice ALLOW: JAVA_VANILLA, brand=vanilla, profile=default
[Guardian] Bob ALLOW: JAVA_FABRIC, profile=staff, CERBERUS_VERIFIED, mods=42
[Guardian] Carol DENY: REQUIRED_MOD_MISSING, mod=examplemod, profile=default
```

In Velocity-authoritative deployments Guardian-Velocity SHOULD normally own the successful Admission summary. Guardian-Paper SHOULD NOT duplicate a verbose successful trail merely because it verified the trusted proxy assertion.

Security, integrity, integration, and configuration anomalies MUST remain visible regardless of logging mode. This includes at least invalid trusted proxy assertions, Geyser/Floodgate disagreement or provider failure, malformed protocol, optional-provider failures that affect classification, invalid reload/configuration, unexpected Guardian-channel leakage, and other conditions indicating a real operational/security problem.

### 27.2 DEBUG

`DEBUG` MAY include the detailed lifecycle information useful during development and troubleshooting, including configuration presence, normalized classification inputs, profile/source selection, challenge send/receive, channel-registration waits, manifest evaluation, trusted proxy assertion creation/verification, backend sanity checks, and timing/lifecycle diagnostics.

`DEBUG` MUST NOT automatically dump the complete Fabric manifest. Deliberate player-specific inspection is the preferred mechanism for staff to request mod information.

Default logging MUST NOT expose absolute paths, unrelated system information, launch arguments, hardware identifiers, or full manifest contents when not needed.

No external telemetry is required for the initial project. A database is not required for the core product unless later operational requirements justify one.

---

## 28. Player/staff-facing messages and localization

All denial states SHOULD have distinct, useful messages.

Messages SHOULD include enough information to remediate ordinary mistakes without exposing unnecessary security internals.

Representative Admission categories:

- unsupported client;
- Fabric requires Cerberus;
- Cerberus timed out;
- Cerberus version/protocol incompatible;
- one or more mods not approved;
- required mod missing;
- approved mod version mismatch;
- manifest invalid;
- Bedrock denied by policy;
- internal/configuration issue.

Representative Protection categories:

- command execution denied;
- namespaced command denied;
- command hidden/suggestion suppressed where feedback is appropriate;
- staff notification of a Protection violation;
- configuration/permission diagnostic feedback from Guardian commands.

### 28.1 Locale resources

All **player-visible and staff-facing Guardian feedback strings** MUST come from translatable locale resources and MUST NOT be embedded as literal message text in Java source.

This includes:

- disconnect/deny reasons controlled by Guardian;
- Protection denial feedback;
- Protection staff notifications;
- `/guardian` and `/guardianv` command feedback;
- user-facing validation/reload/status feedback where applicable.

Purely internal technical logger diagnostics are not required to be player-localized, but SHOULD remain structured and administrator-readable.

The project SHOULD ship a complete default locale (initially English) and a deterministic fallback chain. A missing key in a selected locale MAY fall back to the configured/default locale; a key missing from the required default locale SHOULD be caught by validation/tests rather than replaced by a hard-coded Java fallback sentence.

Exact file names and per-player locale selection semantics remain Phase 1 implementation details.

### 28.2 Adventure, MiniMessage, and placeholders

Guardian-controlled rich text MUST use Adventure components and SHOULD use MiniMessage templates in locale resources.

Internal dynamic values SHOULD be provided through typed/safe placeholder resolvers rather than ad-hoc string replacement.

Initial internal placeholder concepts SHOULD include, where relevant:

- player name;
- command;
- normalized command root;
- rule ID;
- structured reason;
- client classification;
- mod ID/version;
- server name/deployment mode;
- configured help/download URL.

Untrusted/player-controlled placeholder values MUST be inserted as literal/unparsed content by default so that a player name, command text, or mod metadata cannot inject MiniMessage formatting.

PlaceholderAPI MUST NOT be required for Guardian Protection or Admission. Optional PlaceholderAPI expansion in ordinary rendered messages MAY be considered later as a convenience integration.

### 28.3 Direct supported actions

A configurable help/download URL SHOULD be supported.

The project SHOULD NOT rely on console `kick` commands when a direct supported disconnect/deny API exists.

Protection notifications SHOULD be permission-gated ordinary Adventure chat messages by default. A structured action model MAY later support additional integrations, but arbitrary punishment commands are not required for BadWolfMC's Phase 1B migration.

---

## 29. Administrative surfaces and authority-aware inspection

Basic administrator operability is authority-explicit. Phase 5 deliberately exposes different command roots for the two platform authorities rather than relying on proxy/backend command precedence:

```text
Guardian-Paper:    /guardian
Guardian-Velocity: /guardianv
```

The distinction is semantic: `/guardian` operates on this Paper host; `/guardianv` operates on the Guardian-Velocity network Admission authority. Guardian-Paper MUST NOT proxy administrative requests to Velocity, and Phase 5 MUST NOT invent a Paper ↔ Velocity command RPC merely to retrieve remote Admission state.

Both roots keep the same intentionally small command shape:

```text
/guardian status
/guardian validate
/guardian reload
/guardian inspect <player>
/guardian artifacts scan

/guardianv status
/guardianv validate
/guardianv reload
/guardianv inspect <player>
/guardianv artifacts scan
```

Separate `brand` or `mods` commands SHOULD NOT be added unless later operational evidence demonstrates a genuine need. `inspect <player>` is the canonical one-stop current-player diagnostic.

### 29.1 Host ownership

**Standalone Guardian-Paper** owns complete local Admission + Protection administration. Its `status`, `validate`, `reload`, `inspect`, and artifact scan operate on the authoritative standalone runtime.

**Guardian-Paper behind Guardian-Velocity** still exposes `/guardian` for backend console/direct administration, Paper configuration/locales, Guardian Protection, and trusted-proxy verifier diagnostics. In `authority: velocity` mode:

- `status` reports Paper-local operational state and proxy-verifier configuration;
- `validate` validates only Paper-owned files/state and does not load proxy-owned Admission policy;
- `reload` atomically replaces only Paper-owned runtime state and does not reload network Admission policy;
- `inspect <player>` reports only locally retained trusted assertion/backend sanity evidence, clearly identifies Velocity as authoritative, and directs staff to `/guardianv inspect <player>` for the authoritative Admission snapshot; and
- `artifacts scan` refuses to mutate a backend catalog as though it controlled network Admission and directs the administrator to `/guardianv artifacts scan`.

**Guardian-Velocity** owns authoritative network Admission administration. `/guardianv` directly manages the proxy-owned configuration, shared policy/catalog, current inspection snapshots, artifact import, integration diagnostics, assertion configuration, and production logging state.

A moderator connected to any backend may execute `/guardianv inspect <player>` directly at the proxy. Velocity performs proxy-wide player lookup, so the target may be on a different backend. The command does not require either player's backend to receive the target's Fabric manifest.

### 29.2 Active inspection snapshots

Successful mutable Admission sessions MUST be discarded when Admission completes. Inspection MUST NOT retain those state-machine sessions merely to support commands.

The authoritative host maintains a separate bounded immutable/read-only **active inspection snapshot** for currently connected players. It is in-memory only by default, privacy-conscious, removed on disconnect/session end, and distinct from mutable Admission state.

Phase 6 binds each platform snapshot to the exact live connection object that produced it rather than UUID alone. Same-UUID reconnects MUST NOT inherit an older connection's inspection evidence before the new admission captures its own snapshot, and delayed disconnect callbacks from an older connection MUST NOT remove a newer connection's snapshot. UUID remains the staff lookup key only after the platform has confirmed that the current live connection owns that evidence.

Phase 6 tightens that bound in both dimensions. The active store is capped independently of Admission state, and an authoritative snapshot MUST NOT retain the complete canonical manifest after Admission completes. It retains complete Loader-known and policy-addressable counts plus at most 64 deterministic policy-addressable/top-level mod summaries for ordinary staff inspection; additional entries are reported as omitted rather than retained/output without bound. Backend-only Paper evidence is subject to the same active-snapshot count cap.

Authority boundaries are mandatory:

- standalone Paper -> Guardian-Paper stores the authoritative snapshot;
- Velocity authority -> Guardian-Velocity stores the authoritative Admission/manifest snapshot;
- backend Paper -> stores only local trusted-assertion/backend sanity evidence and never receives the full Fabric manifest solely for administrator convenience.

A normal authoritative inspection SHOULD provide player identity, current backend, classification/origin, Java brand where applicable, profile/source, Cerberus version/protocol where applicable, final decision/reason, policy-addressable/top-level mods, total Loader-known count, useful exact-artifact/hash state, and Geyser/Floodgate evidence. It SHOULD summarize policy-addressable mods rather than dump every nested Loader entry. Offline/no-snapshot targets report no active inspection data; Phase 5 does not add historical persistent manifest storage or distributed cross-proxy inspection.

### 29.3 Reload and validation

`/guardian reload` and `/guardianv reload` use host-owned atomic runtime seams. Each parses/normalizes/validates a complete candidate first, activates only a fully valid immutable replacement, and preserves the previous runtime when candidate validation fails. Paper reload reconciles Paper-local Admission/Protection enablement and command visibility as applicable. Velocity reload changes authoritative network Admission configuration/policy for new decisions immediately. Neither command fans out to the other platform.

Each successfully activated host runtime has a monotonically increasing in-memory generation for operational diagnostics. Active inspection snapshots record the generation that made the admission decision. Staff inspection MUST label that admission-time generation separately from the currently loaded generation so a pre-reload decision/profile/mod snapshot cannot be mistaken for a re-evaluation under current policy. Files-only validation does not advance or activate a generation. On Velocity, exact-artifact membership shown during inspection is explicitly evaluated against the **current** catalog and labelled as such; it is diagnostic context, not a claim about the admission-time catalog state.

`/guardian validate` and `/guardianv validate` are files-only/non-activating and respect ownership. Paper in Velocity-authoritative mode MUST NOT pretend to validate the proxy's policy; Velocity validates its authoritative operational configuration, policy, catalog, and locale state.

### 29.4 Status

Both status commands remain concise. `/guardian status` reports Paper-local version/protocol/server/authority/domain state, locale/logging, optional backend integrations, proxy assertion verifier state, and local snapshot counts. `/guardianv status` reports network-authoritative version/protocol/deployment state, loaded profiles/default profile, artifact catalog state, logging/timing, optional proxy integrations, assertion key source/fingerprint, and authoritative active snapshot count.

### 29.4.1 Proxy assertion key provisioning

Guardian-Velocity owns creation of the server-controlled shared assertion key. On first startup, if `proxy-assertion.key` does not already exist as a regular file in the Guardian-Velocity data directory, Velocity generates 32 random bytes with a cryptographically secure RNG and writes their Base64 representation to that file without overwriting existing key material.

For every Guardian-Paper backend configured with `admission.authority: velocity`, the operator copies the same `proxy-assertion.key` unchanged into that backend's Guardian data directory. Guardian-Paper MUST NOT generate a replacement key when the file is missing; it fails closed and reports the provisioning requirement. Standalone Paper does not require proxy assertion key material.

The key location is conventional/fixed rather than configured through JVM flags, environment variables, or inline YAML. Guardian never logs key material; status exposes only a non-secret fingerprint suitable for confirming that the proxy and backends loaded the same key. Key rotation is coordinated across the proxy/backends rather than silently accepting multiple active keys.

### 29.5 Permissions and command routing

Paper administrative permissions are:

```text
guardian.command.status
guardian.command.validate
guardian.command.reload
guardian.command.inspect
guardian.command.artifacts.scan
```

Velocity administrative permissions are deliberately separate:

```text
guardian.velocity.command.status
guardian.velocity.command.validate
guardian.velocity.command.reload
guardian.velocity.command.inspect
guardian.velocity.command.artifacts.scan
```

Granting Paper-local Guardian administration MUST NOT imply network-authoritative Guardian-Velocity administration. Runtime correctness MUST NOT depend on wildcard expansion. The obsolete unreleased `guardian.artifacts.scan` permission is removed without a compatibility alias.

The `/guardianv` root MUST be claimed by Guardian-Velocity even when the source lacks a particular subcommand permission; granular permission denial is handled inside Guardian so Velocity does not forward an unhandled administrative root to a backend server.

New command routing, inspection storage, logging policy, and proxy operational configuration SHOULD be factored into focused services rather than continuing to expand `PaperAdmissionAdapter` or `GuardianVelocityPlugin` into monolithic classes.

The artifact-import rule generator MUST guarantee that every valid Fabric mod ID yields a valid deterministic policy rule ID even when `allow-<mod-id>` would exceed the policy rule-ID length bound; deterministic shortening with a stable suffix is required.

---

## 30. Public API/events

A small read-only Guardian API MAY be useful for other BadWolfMC plugins and third-party integrations.

If implemented, it SHOULD expose stable domain concepts such as:

- current client classification;
- resolved policy/profile;
- whether attestation was required;
- final Guardian decision;
- relevant immutable manifest summary.

Other plugins SHOULD NOT be given mutable access to internal admission state.

If Protection state is later exposed publicly, it SHOULD likewise use read-only domain concepts such as the matched rule/reason and effective outcome rather than mutable listener internals.

This API is not required for Phase 0 or the first minimal production implementation.

---

## 31. Build and release artifacts

The multi-project build SHOULD produce distinct deployable artifacts:

```text
guardian-paper-<version>.jar
guardian-velocity-<version>.jar
cerberus-fabric-<version>.jar
```

Shared core/protocol/protection modules SHOULD normally be implementation dependencies rather than separate administrator-installed artifacts.

CI SHOULD include:

- compilation;
- unit tests;
- protocol tests;
- policy tests;
- configuration and locale validation tests;
- Protection policy/bypass tests;
- guard tests preventing hard-coded player/staff-facing message fallbacks where practical;
- platform boundary tests where practical;
- reproducible artifact naming;
- checksums for release artifacts.

Release documentation MUST clearly identify which JAR belongs on Paper, Velocity, and the Fabric client.

---

## 32. Licensing, attribution, and provenance

Guardian should remain compatible with BrandBlocker's GPLv3 lineage and BadWolfMC's GPLv3 eZProtector lineage.

The project SHOULD retain GPLv3 for Guardian unless legal review later establishes and justifies another compatible arrangement.

The README SHOULD maintain an Acknowledgements/Provenance section that:

- states that Guardian began as a hard fork and substantial rewrite of BrandBlocker by Menacho and links to the original project;
- acknowledges eZProtector by DoNotSpamPls and the BadWolfMC fork/contributors for selected Guardian Protection concepts/source where applicable;
- clearly distinguishes current BadWolfMC Guardian development from both legacy projects.

Any eZProtector code incorporated into Guardian MUST come from the identified BadWolfMC GPLv3 lineage unless licensing is intentionally reconsidered first. Later AGPL-licensed continuation code MUST NOT be casually copied into Guardian.

Before source transplantation begins, the project SHOULD record the exact BadWolfMC eZProtector commit used as the provenance boundary when that commit information is available.

Cerberus and shared modules SHOULD use a licensing arrangement compatible with the combined repository and distribution model.

Existing copyright and license notices that must legally be retained MUST remain present.

---

# 33. Implementation phases

## Phase 0A — Standalone Paper/Fabric feasibility spike — COMPLETE

**Status:** Completed 2026-09-22.

**Goal:** Prove the architectural heart of the project using supported APIs only.

Phase 0A established the following live Paper 26.2 / Fabric 26.2 results:

```text
Vanilla
→ classified
→ ALLOW / VANILLA_POLICY
→ admitted normally

Fabric without Cerberus
→ no CONFIGURATION presence
→ DENY / CERBERUS_REQUIRED
→ denied before world entry

Fabric + incompatible Cerberus protocol
→ CONFIGURATION presence received
→ DENY / CERBERUS_PROTOCOL_UNSUPPORTED
→ denied before world entry

Fabric + compatible Cerberus
→ CONFIGURATION presence received
→ enter bounded PLAY quarantine
→ fresh nonce challenge/response
→ minimal test manifest validated
→ ALLOW / CERBERUS_VERIFIED
→ quarantine released

Fabric + compatible Cerberus + deliberate deny marker
→ handshake succeeds
→ DENY / MANIFEST_DENIED
→ distinct message

Fabric + compatible Cerberus + suppressed response
→ bounded quarantine
→ DENY / CERBERUS_TIMEOUT

Fabric + compatible Cerberus + malformed response
→ DENY / MANIFEST_INVALID
```

Phase 0A proved:

- Fabric 26.2 → Paper 26.2 CONFIGURATION custom-payload delivery using supported APIs;
- configuration-stage client classification/presence sufficient for pre-world missing/incompatible-Cerberus decisions;
- supported bidirectional PLAY challenge/response under quarantine;
- no NMS, reflection, Mixins, Fabric implementation classes, or packet-library dependency is required;
- distinct Cerberus absence, timeout, incompatible protocol, denied manifest, and invalid protocol-data outcomes;
- supported connection-state cleanup paths;
- client-brand availability by configuration finalization.

Phase 0A disproved the stronger assumption that the entire standalone Paper 26.2 ↔ Fabric 26.2 challenge/response can be completed bidirectionally during CONFIGURATION through the tested supported high-level APIs.

The accepted standalone architecture is the hybrid defined in Section 21.

Phase 0A intentionally excluded:

- LuckPerms;
- real whitelist files;
- hash enforcement;
- Geyser;
- Floodgate;
- Velocity;
- signing;
- production commands;
- databases;
- GUIs.

**Exit criterion:** Satisfied. The intended all-CONFIGURATION path was tested and its 26.2 limitation identified; the contract-approved supported hybrid fallback was implemented and live-tested successfully.

---

## Phase 0B — Velocity/Geyser/Floodgate feasibility spike

**Goal:** Prove the production-network architecture with one Velocity instance and one Paper backend.

Minimal topology:

```text
Java / Fabric / Bedrock clients
           ↓
Velocity
Guardian-Velocity
Geyser + Floodgate
           ↓
Paper
Guardian-Paper
Floodgate
```

Required tests:

- Cerberus ↔ Guardian-Velocity configuration-stage handshake;
- Velocity can hold/release connection cleanly;
- proxy-origin disconnect reasons display correctly;
- security-sensitive Guardian client channels do not leak to Paper;
- Velocity → Paper trusted admission assertion works;
- backend cannot accept a spoofed client assertion;
- switching/reconnecting semantics are understood;
- Bedrock is identified through Geyser/Floodgate APIs;
- Bedrock receives no Cerberus challenge;
- Java username with the Floodgate prefix does not become Bedrock;
- backend Floodgate state can sanity-check proxy classification;
- Guardian-Paper still functions in standalone authority mode when Guardian-Velocity is absent.

**Exit criterion:** Decide whether Guardian-Velocity is suitable as the preferred BadWolfMC production authority while preserving standalone Paper operation.

### Phase 0B checkpoint — 2026-09-22

Live testing of the first Velocity prototype has confirmed several previously open architectural questions:

- Guardian-Velocity and Cerberus can complete the bounded nonce challenge/response bidirectionally during Velocity's supported CONFIGURATION lifecycle.
- Velocity's awaited configuration event can hold the client before PLAY/world entry until Guardian reaches a structured admission decision.
- A successful Fabric + Cerberus decision releases normally without the standalone Paper PLAY quarantine.
- Guardian-Velocity can consume the registered Cerberus security channels so they do not leak to Guardian-Paper; with both adapters installed, Paper observed no Cerberus presence while Velocity completed the handshake.
- The proxy's backend connection is already established/configuring while the awaited Guardian decision runs. The supported invariant is therefore **before PLAY/world entry**, not **before any backend network contact**.
- Plain Velocity currently exposes a timing difference to Guardian-Paper: Paper's async configuration hook can observe `clientBrand == null` even though Velocity has learned the Fabric brand. Current Velocity source stores the client brand and mirrors it to the backend later when backend configuration is finishing. A supported late Paper classification retry is therefore worth testing; transparent Velocity compatibility is not yet declared proven.

These results are sufficient to prefer the Velocity CONFIGURATION transport over the standalone Paper transport for continued Phase 0B work, but **Phase 0B is not complete**. The next checkpoint must prove a trusted, client-unforgeable Velocity → Paper admission assertion and its ordering relative to Paper's final pre-world validation. Geyser/Floodgate classification and backend switching remain subsequent acceptance items.

### Phase 0B closeout — 2026-09-23

Subsequent implementation and live testing completed the remaining Phase 0B acceptance items.

Trusted Velocity → Paper admission:

- Guardian-Velocity sends a short-lived HMAC-authenticated admission assertion over a dedicated proxy-to-backend channel.
- The shared infrastructure secret exists only on Guardian-Velocity/Guardian-Paper and is not present in Cerberus.
- Matching secrets produced `ALLOW / PROXY_ADMISSION_VERIFIED` before world entry.
- Deliberately mismatched secrets caused Guardian-Paper to reject the assertion with `PROXY_ASSERTION_INVALID` before world entry.
- Guardian-Paper in `velocity` authority mode verifies the proxy result rather than independently repeating the Cerberus/policy decision.

Geyser/Floodgate classification:

- A real Minecraft for Windows client was identified by supported Geyser and Floodgate APIs as `BEDROCK` at Guardian-Velocity.
- The same connection was not classified from its Floodgate username prefix; username prefix is not an input to the Bedrock classifier.
- Positive Bedrock classification bypassed Cerberus interrogation and was allowed under the Phase 0 test policy.
- The authenticated proxy assertion carried `origin=BEDROCK` to Guardian-Paper.
- Backend Floodgate independently reported the same player as Bedrock, and Guardian-Paper logged the sanity-check agreement while retaining the generic `ALLOW / PROXY_ADMISSION_VERIFIED` result.
- A live prefix-authority regression used a configured Floodgate prefix matching the beginning of a Java username. The Java Fabric connection remained `JAVA_FABRIC`, Geyser/Floodgate both reported not-Bedrock, and the ordinary Cerberus flow still ran.

Proxy-session and backend-switch semantics:

- Fabric + Cerberus and Bedrock connections both switched between two Paper backends while retaining the same proxy-session ID and without a second Cerberus attestation.
- Vanilla Java likewise reused the same admission across a backend switch.
- After a full disconnect/reconnect, a new proxy-session ID was created. Fabric performed a fresh Cerberus challenge; vanilla received a fresh Java admission session.
- This proves the intended boundary: cache admission only for the lifetime of one proxy connection; never persist it as a long-lived player trust record.

Regression and standalone results:

- Java vanilla remained allowed through Velocity.
- Java Fabric + Cerberus remained `CERBERUS_VERIFIED`.
- Java Fabric without Cerberus remained `CERBERUS_REQUIRED` and was denied before world entry after the configured feasibility timeout.
- Standalone Guardian-Paper without Velocity/Geyser/Floodgate still admitted vanilla pre-world and successfully completed the Phase 0A Fabric + Cerberus hybrid CONFIGURATION/PLAY-quarantine flow.

The current 10-second no-Cerberus wait is a feasibility-spike timeout, not a finalized production UX value. Production timing remains subject to later hardening/configuration work.

**Exit criterion:** Satisfied. Guardian-Velocity is suitable as the preferred BadWolfMC production admission authority, with Guardian-Paper verifying trusted proxy admission on backends. Standalone Guardian-Paper remains a supported independent authority mode.

---

## Phase 1 — Guardian foundation, BrandBlocker rewrite, and Protection foundation

Phase 1 is divided into two implementation slices so Guardian's two domains are established cleanly before later policy complexity is added.

### Phase 1A — Guardian foundation and BrandBlocker rewrite

**Goal:** Replace the BrandBlocker architecture with Guardian's production Admission foundation while establishing the shared Paper runtime/lifecycle needed by independent Admission and Protection domains.

Implement/refine:

- Gradle multi-project structure;
- `com.badwolfmc.guardian` package namespace;
- `guardian-core` as the platform-neutral Admission domain;
- `guardian-protocol`;
- new `guardian-protection` platform-neutral domain module;
- `guardian-paper` as the runtime host/adaptor for independently enableable Admission and Protection;
- structured Admission decision model;
- Java client brand classification;
- modern direct disconnect handling;
- default Admission policy infrastructure;
- versioned config parse/validate/immutable-snapshot lifecycle;
- startup malformed-file recovery with timestamped preservation before packaged-default restoration;
- atomic reload preserving prior known-good snapshots;
- locale catalog loading/validation;
- Adventure/MiniMessage rendering with safe internal placeholder resolution;
- logs and diagnostics;
- attribution/provenance updates for both BrandBlocker and eZProtector.

Carry forward the practical ability to allow/deny non-Fabric client brands.

Phase 1A MUST preserve the invariant that Guardian-Paper can load with Admission only, Protection only, or both enabled.

Do not yet implement the full Fabric policy engine unless needed for the existing prototype integration.

#### Phase 1A implementation checkpoint — 2026-09-24

The current Phase 1A implementation candidate now establishes the production foundation described above while deliberately retaining the proven Phase 0 transport adapters. In particular:

- `guardian-core` remains Admission-only and platform-neutral;
- `guardian-protection` now exists as a separate platform-neutral module and does not depend on `guardian-core`;
- Guardian-Paper is a small runtime host which can enable Admission, Protection, both, or neither;
- Protection activation in Phase 1A installs no command execution, visibility, namespace, suggestion, or notification behavior;
- Paper configuration begins at `schema-version: 1` and normalizes client classes to explicit `ALLOW` / `DENY` / `REQUIRE_CERBERUS` actions;
- exact normalized allowlist/denylist brand rules apply only to `JAVA_UNKNOWN` and therefore cannot override a positive Fabric classification;
- the default locale is a versioned English properties catalog rendered through Adventure/MiniMessage with unparsed internal placeholders;
- the configuration loader/runtime manager remains non-mutating: reload candidates replace the active immutable snapshot only after configuration and locale validation succeed, and failed reload files remain untouched;
- Guardian-Paper startup recovery preserves malformed/structurally invalid administrator files to UTC timestamped `.bak` files before restoring packaged defaults. Invalid optional locale files are similarly preserved and then omitted so the required fallback catalog can take effect. Recovery always reparses/revalidates before activation, and unsupported schema versions remain fatal rather than being silently downgraded;
- the Phase 0B `GUARDIAN_PHASE0B_PROXY_SECRET` environment variable remains a transitional feasibility-provisioning mechanism. Phase 1A does not promote its naming/provisioning UX into the final Phase 5 contract;
- the Phase 0 test-manifest evaluator remains an explicitly transitional integration component until the real manifest/policy phases replace it. Phase 1A does not disguise it as the final mod-policy engine.
- active implementation bridges and their owning replacement phases/retirement conditions are tracked in `docs/IMPLEMENTATION_BRIDGES.md`; phase closeout MUST review that register so feasibility scaffolding cannot silently become permanent.

#### Phase 1A completion record — 2026-09-24

Phase 1A is complete. The clean Java 25 build is green with 49 automated tests and no failures/errors/skips. Live operator verification passed for packaged-default generation, malformed config backup/recovery, invalid fallback-locale backup/recovery, unsupported-schema fail-closed behavior, all four Admission/Protection enable combinations, standalone Admission regression, and the focused Velocity/Geyser/Floodgate regression.

The Phase 1A bridge register was reviewed at closeout. BRIDGE-001 through BRIDGE-005 remain intentionally active under their later roadmap owners; Phase 1A completion does not promote feasibility-era behavior into the final contract.

The authoritative implementation baseline handed to Phase 1B is recorded in `docs/PROVENANCE.md`, and the Phase 1B-specific handoff is recorded in `docs/PHASE_1B_HANDOFF.md`.

### Phase 1B — Guardian Protection / eZProtector successor

**Goal:** Replace the selected still-useful eZProtector server-protection behavior with a clean Guardian Protection implementation built against supported Paper 26.2 APIs.

Implement:

- platform-neutral `ProtectionDecision`/reason/rule models;
- command execution policy for configured command roots;
- command visibility policy with explicit allowlist/denylist modes;
- namespaced-command policy with explicit allowlist/denylist modes;
- root-command normalization;
- root-command visibility filtering through supported command-tree APIs/events;
- downstream argument-suggestion suppression whenever the root is hidden;
- centralized bypass resolution shared by root visibility, argument suggestions, namespaced-command enforcement, and command execution as applicable;
- global/feature-scoped bypass concepts and optional per-command visibility bypass;
- permission-gated staff notifications independent from bypass permissions;
- locale-backed player denial feedback and staff notifications;
- command-tree refresh after Guardian visibility configuration changes for online players using supported Paper APIs;
- tests covering whitelist/allowlist and blacklist/denylist modes, guessed hidden roots, argument suggestions, bypasses, permission/config refresh, and rule normalization;
- a documented eZProtector → Guardian configuration concept mapping;
- a documented eZProtector → Guardian permission migration table using only new `guardian.*` nodes.

Do not carry forward:

- eZProtector client-brand/mod enforcement;
- 5zig/BetterSprinting/Schematica/WorldDownloader/BetterPvP/VoxelMap countermeasures;
- fake plugin-list/version responses;
- legacy Waterfall/Velocity techniques;
- legacy `ezprotector.*` permission aliases;
- eZProtector's raw config reload behavior.

BadWolfMC does not require punishment commands for the initial Protection migration. A generic structured action/integration mechanism MAY later support direct disconnects or configured console commands where justified, but Phase 1B's required operational behavior is denial feedback, staff notification, and logging.

**Phase 1B implementation contract finalized during implementation:**

- `protection.execution` is a root-scoped player-command denylist and is the execution security boundary.
- `protection.visibility` supports `ALLOWLIST`/`DENYLIST`, filters Paper's advertised root commands, and uses the same platform-neutral visibility decision for downstream suggestion suppression.
- `protection.namespaces` supports `ALLOWLIST`/`DENYLIST` over complete normalized `namespace:command` roots.
- Protection command execution interception is deliberately limited to Paper's player-command path; Guardian does not subscribe to non-player server-command execution events.
- the packaged namespace default is a conservative denylist of selected Bukkit information aliases rather than eZProtector's blanket colon blocker; administrators may opt into allowlist semantics explicitly.
- per-command visibility bypass leaves use a deterministic bounded encoded command key.
- the supported Paper reload lifecycle refreshes online command trees with `Player.updateCommands()` whenever Guardian activates a changed visibility policy; the user-facing `/guardian reload` command remains owned by the later operations phase.
- the exact Phase 1B YAML and migration semantics are documented in `docs/GUARDIAN_PROTECTION.md` and `docs/EZPROTECTOR_MIGRATION.md`.

#### Phase 1B completion record — 2026-09-26

Phase 1B is complete. The accepted closeout source is project version `0.1.0-phase1b` and contains 73 automated tests. The operator reports the clean Java 25 / Gradle 9.7.1 gate green with all 73 tests passing. Live verification from a blank-slate Guardian installation passed the full Phase 1B matrix, including all four Admission/Protection enable combinations, player-only command enforcement, continued console/command-block/plugin command operation, allowlist/denylist behavior, visibility-bypass scoping, and independent `guardian.protection.notify` notification authority.

The Phase 1B bridge register was reviewed at closeout. No new Protection bridge remains active; BRIDGE-001 through BRIDGE-005 retain their later-phase owners. The authoritative closeout baseline and archive hash are recorded in `docs/PROVENANCE.md`, and Phase 2 handoff context is recorded in `docs/PHASE_2_HANDOFF.md`.

---

## Phase 2 — Cerberus and protocol v1

**Goal:** Build the real Fabric client and stable initial handshake.

Implement:

- Fabric Loader manifest enumeration;
- canonical manifest format;
- parent/contained relationships;
- Minecraft/Fabric Loader/Cerberus/protocol metadata;
- nonce challenge/response;
- payload limits;
- timeout behavior;
- version/capability negotiation;
- explicit missing-Cerberus behavior;
- privacy constraints;
- deterministic serialization.

No client-side secret may be treated as proof of honesty.

---

## Phase 2.5 — Artifact identity and approved-artifact catalog

**Goal:** Stabilize exact top-level artifact identity before policy semantics consume it.

Implement:

- required protocol-v1 `CAP_ARTIFACT_SHA256` capability;
- exact 32-byte SHA-256 identity for every top-level `ARCHIVE` manifest entry;
- deterministic unhashed semantics for `NESTED`, `BUILTIN`, `DIRECTORY`, and `MIXED_OR_UNKNOWN` entries;
- one cached Loader/environment snapshot per Cerberus process so artifact hashes are not recomputed per network challenge;
- bounded, inert scanning of administrator-supplied Fabric JARs in `artifact-import/`;
- metadata-derived mod ID/version identity;
- a durable deterministic `artifacts.yml` supporting multiple versions and multiple hashes per ID/version;
- add-only merge semantics that retain historical entries after input JAR deletion;
- explicit asynchronous `/guardian artifacts scan` administration;
- transactional catalog mutation and startup catalog validation; and
- protocol/catalog/privacy regression coverage.

Do **not** implement Phase 3 allowlist/denylist, required-mod, profile, LuckPerms, or bypass semantics here. The catalog is identity data, not policy.

**Trust boundary:** SHA-256 verifies the exact artifact bytes reported by a cooperating Cerberus client; it does not independently prove that a hostile/replaced Cerberus client reported those bytes truthfully.

---

## Phase 3 — Guardian policy engine

**Goal:** Turn normalized client classifications and reported manifests into one flexible, deterministic, **platform-neutral** admission policy system that is already consumable by both Paper and Velocity. Phase 5 must productionize Velocity as an authority, not port the policy engine to it.

Implement:

- one shared admission-policy schema/model and administrator-facing file format;
- a platform-neutral parser/normalizer/validator that produces immutable policy snapshots without Bukkit/Paper or Velocity configuration dependencies;
- default policy;
- named profiles with deterministic explicit priority;
- canonical per-client-class `ALLOW` / `DENY` / `REQUIRE_CERBERUS` actions;
- optional normalized allowlist/denylist brand rules for otherwise unknown Java brands;
- mod-policy `ALLOWLIST` / `DENYLIST` semantics (or an equivalent explicit unlisted default);
- orthogonal required-mod rules;
- per-mod allow/deny rules;
- unknown/unlisted-mod handling;
- version rules;
- contained/nested-mod semantics;
- explicit baseline/bootstrap/runtime manifest-entry treatment;
- exact-artifact policy semantics consuming the Phase 2.5 SHA-256 manifest field and durable artifact catalog;
- classification-specific actions;
- explicit decision reasons;
- validation that rejects contradictory rules rather than relying on hidden precedence;
- policy-scoped admission bypass permissions from Section 11.3;
- atomic parse → validate → immutable snapshot activation;
- files-only validation;
- standalone Guardian-Paper consumption of the shared snapshot/evaluator as authoritative policy; and
- Guardian-Velocity consumption of the **same** shared snapshot/evaluator during its existing CONFIGURATION admission flow, replacing the feasibility-era hard-coded classification-policy branch without otherwise pulling forward Phase 5 operational productionization.

Implement profile resolution:

- a platform-neutral pre-login `AdmissionProfileProvider`-style contract that receives identity/policy context and resolves profile/bypass inputs asynchronously without embedding Paper or Velocity permission APIs in the evaluator;
- explicit identity overrides;
- optional LuckPerms integration for both supported authoritative adapters where LuckPerms is available;
- `guardian.admission.profile.<profile-id>` selection;
- deterministic profile priority when multiple permissions match;
- default fallback profile.

A Velocity admission profile is resolved for the proxy admission session. Backend switching MUST NOT silently re-resolve a different mod policy merely because backend contextual permissions differ; any future backend-specific admission policy requires an explicit architectural design rather than accidental context leakage.

Phase 3 MUST include a **portability gate**:

- the complete policy model/evaluator/version/hash/containment/bypass semantics compile without Paper or Velocity dependencies;
- admission-policy parsing/validation does not use Bukkit/Paper `YamlConfiguration` or a Velocity-specific config model;
- Guardian-Paper and Guardian-Velocity both invoke the same evaluator;
- equivalent normalized inputs plus the same immutable policy snapshot produce the same `GuardianDecision` regardless of adapter;
- Velocity live/focused tests include at least one ordinary allow, one policy denial, one required-mod failure, and one exact-artifact/hash failure through the shared Phase 3 evaluator; and
- Paper in Velocity-authoritative mode continues to consume only the authenticated final proxy admission result and does not re-evaluate the player's policy.

Acceptance tests MUST cover both client-class policy and mod-policy modes, including an allowlisted unusual Java brand, a denied unusual Java brand, Fabric remaining attestation-required despite brand rules, required mods under both mod modes, unlisted mods in both modes, conflicting invalid rules, admission bypass permissions that do not bypass protocol/integrity validation, and cross-adapter evaluator parity.

### Phase 3 implementation decisions — `0.1.0-phase3` candidate

The Phase 3 implementation candidate selects one portable file, `policy.yml`, as the administrator-facing Admission policy source for either authoritative adapter. Paper `config.yml` remains operational/local configuration; `artifacts.yml` remains identity data only. Shared policy parsing uses a platform-neutral strict YAML parser in `guardian-core`, not Bukkit `YamlConfiguration`.

The selected policy-addressable manifest model is:

- top-level non-`BUILTIN` entries are independently subject to membership/rules;
- built-in Java/Minecraft entries are intrinsic runtime baseline;
- administrator `baseline` IDs and required mods count as allowlist membership;
- nested entries remain structurally visible and eligible for explicit required/deny/version constraints, but are not rejected solely for being unlisted beneath a containing artifact;
- the same ID installed top-level becomes independently addressable; and
- `DIRECTORY` / `MIXED_OR_UNKNOWN` are explicit origin-policy decisions rather than filename/path guesses.

The bounded version predicate language is `*`, exact strings, one trailing prefix wildcard, and conjunctions of dotted-numeric comparisons (for example `>=1.2 <2.0`). Multiple acceptance clauses provide OR semantics. Artifact clauses explicitly choose `VERSION_ONLY` or `HASH_REQUIRED`; exact-hash rules may use direct SHA-256 declarations, `artifacts.yml` catalog matches, or both. Catalog membership by itself has no policy effect.

Profile resolution is exact UUID override → highest unique-priority provider match → default. Optional LuckPerms 5.5 adapters exist for both Paper and Velocity and use pre-login/proxy-static query options. Provider absence/failure falls back to identity/default resolution without granting provider-derived bypasses. Velocity retains the resolved profile for the proxy admission session across backend switching.

Client/mod bypasses are policy exemptions only. Client bypasses cannot remove `REQUIRE_CERBERUS`; mod bypasses run only after protocol/session/manifest integrity validation.

Guardian-Velocity's independent feasibility-era client-class policy branch is removed in the candidate and replaced by the same shared evaluator used by standalone Paper. Early Cerberus traffic is recorded without outrunning asynchronous profile/client-policy resolution, preserving the documented evaluation order. Paper in Velocity-authoritative mode remains assertion-only.

**Phase 3 completion record — 2026-09-27:** the final Java 25 / Gradle 9.7.1 gate is green at 134 tests (Core 53, Paper 31, Protection 20, Protocol 21, Velocity 9), with zero failures/errors/skips. Live verification covers Velocity/standalone ordinary allow, explicit deny, required-version mismatch, genuinely absent required mod (`REQUIRED_MOD_MISSING`), exact-hash denial, trusted proxy assertion without backend re-attestation, actionable player-facing denial text with help URL, and the hardened artifact-import workflow against real-world Fabric distributions including Replay Mod. The Phase 3 shared-policy portion of BRIDGE-004 is closed; the bridge remains active only for Phase 5 Velocity production hosting/operations work.

---

## Phase 4 — Geyser/Floodgate integration

**Goal:** Productionize the already-proven Bedrock origin path and retire BRIDGE-005 without absorbing Phase 5 Velocity operations.

Phase 0B and Phases 1–3 already supplied much of the foundation, so Phase 4 is intentionally a focused hardening/productionization slice rather than a new subsystem.

Implement/verify:

- supported Geyser API integration where available on the authoritative host;
- supported Floodgate API integration where available on the authoritative host;
- standalone Guardian-Paper origin discovery through Geyser/Floodgate as optional integrations;
- Bedrock classification before Java brand/Cerberus requirements;
- no username-prefix trust;
- configurable Bedrock policy through the shared Phase 3 `clients.bedrock` action;
- explicit capability/query-failure semantics;
- mismatch diagnostics;
- backend Floodgate sanity checks where relevant; and
- regression coverage proving Java/Fabric Admission and Guardian Protection remain unaffected.

Production origin-evidence semantics are:

1. Any positive supported Geyser/Floodgate API evidence classifies the current connection as `BEDROCK`. This takes precedence over Java brand and prevents a positively identified Bedrock connection from ever receiving a Cerberus challenge.
2. If both available APIs return explicit contradictory answers, Guardian logs a prominent disagreement diagnostic while positive supported evidence remains authoritative for the current connection.
3. An integration that is not installed/enabled is ordinary `UNAVAILABLE` capability state, not an error.
4. If an integration is present/expected but its API query fails, Guardian records an integration error. If no other provider supplies positive Bedrock evidence, origin becomes indeterminate and Admission fails closed with a configuration/integration error rather than silently reinterpreting the connection as Java.
5. If no provider reports positive evidence and every queried/available provider answers cleanly (or no provider exists), Guardian proceeds with normal Java brand classification.

In Velocity-authoritative mode Guardian-Velocity remains the sole Admission authority. Guardian-Paper's backend Floodgate comparison is defense-in-depth observability only: disagreement/provider failure is security-relevant and prominently logged, but Paper MUST NOT independently overturn an authenticated proxy Admission decision or re-evaluate the proxy's player policy.

BadWolfMC's legacy username prefix may be recognized for migration diagnostics only; it MUST NOT be a trusted identity signal.

Phase 4 also records the shared operations/observability architecture in Sections 27 and 29 so Phase 5 implements authority-aware commands, bounded inspection snapshots, and production logging controls as part of Velocity productionization rather than retrofitting them afterward.

**Phase 4 completion record — 2026-09-28:** the final Java 25 / Gradle 9.7.1 gate is green at 141 tests (Core 57, Paper 34, Protection 20, Protocol 21, Velocity 9), with zero failures/errors/skips. Live verification covers Velocity Bedrock with agreeing Geyser/Floodgate and backend Floodgate evidence, Velocity Java Fabric/Cerberus regression, standalone Bedrock allow, and standalone Bedrock deny through shared `policy.yml`. Bedrock never entered the Cerberus path. BRIDGE-005 is retired; BRIDGE-003 and BRIDGE-004 remain Phase 5 work.

---

## Phase 5 — Velocity authoritative mode — COMPLETE

**Goal:** Productionize Guardian-Velocity as the preferred network-edge Admission host without making it mandatory and **without reimplementing or porting the Phase 3 policy engine**.

Phase 5 implements the final authority-aware operations design from Section 29:

- Guardian-Paper owns `/guardian`; Guardian-Velocity owns `/guardianv`;
- Velocity has a strict proxy-local `config.yml`, explicit `deployment.authority: velocity`, configurable handshake timing, locale/logging settings, and shared policy/catalog ownership in its data directory;
- Guardian-Velocity automatically generates `proxy-assertion.key` on first startup; operators copy that same bounded, exact-32-byte Base64 key file to each Velocity-authority Paper backend, while standalone Paper requires no proxy assertion key; status exposes only a non-secret fingerprint for deployment diagnostics;
- reload/validation use complete immutable candidates and preserve the previous active runtime when validation fails;
- one completed Admission is retained only as a minimal immutable proxy-session grant for backend assertions, while mutable admission sessions are discarded and authoritative inspection uses a separate bounded immutable in-memory snapshot;
- backend switching reuses the original proxy Admission decision without re-attesting the client;
- security-sensitive Guardian channels remain consumed at Velocity rather than forwarded to Paper;
- Guardian-Paper in Velocity mode remains assertion-only for network Admission and retains only backend/assertion inspection evidence;
- production logging supports `NORMAL` and `DEBUG`, with the authoritative host normally emitting one successful Admission summary and backend Paper suppressing duplicate success lifecycle chatter;
- `/guardianv inspect <player>` performs proxy-wide connected-player lookup and exposes the authoritative current Admission/mod state without manifest forwarding to Paper;
- `/guardian artifacts scan` refuses in Velocity-authoritative Paper mode while `/guardianv artifacts scan` manages the authoritative catalog; and
- generated artifact policy rule IDs are deterministically shortened when necessary to remain within the 64-character rule-ID bound.

Key replacement/rotation is an administrator-coordinated operation because protocol v1 accepts one active shared key per host. Velocity is the only host that generates key material; Paper consumes an operator-copied key file and never generates an independent replacement. Zero-downtime dual-key rotation is intentionally not introduced in Phase 5.

Phase 5 MUST NOT introduce a Velocity-specific admission-policy schema/evaluator, duplicate Paper policy logic, require administrator policy files to be rewritten merely because authority moves from standalone Paper to Guardian-Velocity, forward full manifests to backends for command convenience, or turn backend Floodgate sanity evidence into a second Admission authority.

The implementation advances the project version to `0.1.0-phase5`.

**Phase 5 completion record — 2026-09-29:** the final Java 25 / Gradle 9.7.1 gate is green at **178 tests** (Core 64, Paper 44, Protection 20, Protocol 21, Velocity 29), with zero failures/errors/skips. Focused live verification confirms normal Velocity Fabric/Cerberus Admission with one concise authoritative summary, assertion-only backend Paper behavior, authority-aware Paper/proxy inspection, host-local atomic validation/reload, artifact authority, Velocity-generated assertion-key provisioning and mismatch recovery, disconnect snapshot cleanup, and in-game `/guardianv` operation through the Velocity permission provider. BRIDGE-003 and BRIDGE-004 are retired; no active implementation bridge enters Phase 6.

---

## Phase 6 — Security and adversarial hardening

**Goal:** Attack the protocol and assumptions before public release.

Test and harden:

- replay;
- duplicate nonce;
- stale session;
- malformed packet;
- oversized packet;
- extreme mod count;
- extreme/nested metadata;
- duplicate mod IDs;
- fake version strings;
- invalid hashes;
- protocol downgrade attempts;
- response after timeout;
- disconnect mid-handshake;
- repeated reconnect;
- proxy assertion spoofing;
- backend direct-connect attempts;
- unknown/empty brand;
- integration disappearance, including LuckPerms/profile-provider timeout or outage under profiles that are stricter than the default;
- permission/profile-provider fail-safe semantics when provider-derived profile/bypass state cannot be obtained;
- reconfiguration events;
- development-environment mod origins;
- malformed configuration;
- key rotation scenarios;
- PLAY-quarantine escape/interaction attempts;
- delayed PLAY channel registration and timeout boundaries;
- active-inspection memory pressure and output bounds under maximum-size/top-level manifests;
- untrusted version/release metadata containing control characters or log-forging sequences; and
- inspection diagnostics after policy/catalog reload so output cannot be mistaken for the admission-time decision basis.

**Phase 6 protocol/input hardening:** protocol v1 now treats the capability mask as closed for that protocol version: all required v1 bits must be present and any unknown bit is rejected rather than silently ignored. Wire booleans are canonical and must be encoded as exactly `0` or `1`; alternative non-zero encodings are malformed. Human-readable protocol metadata is strict UTF-8, bounded by the existing field limits, and rejects control, Unicode format, line-separator, and paragraph-separator code points before it can enter policy/inspection diagnostics. The aggregate payload limit remains inclusive: a valid payload of exactly `MAX_PAYLOAD_BYTES` is accepted and one byte over is rejected. Invalid response manifests are not retained as active inspection evidence.

Log/staff-facing diagnostic values are independently converted to bounded single-line text. CR/LF/tab, terminal/control characters, Unicode formatting controls, and Unicode line/paragraph separators are rendered as visible escapes; MiniMessage placeholders remain `unparsed`. This is output hardening only and does not reinterpret the raw client brand used for classification.

**Phase 6 Velocity session-lifecycle hardening:** in-flight Admission sessions and retained proxy grants are keyed by the exact Velocity `Player` connection object rather than by UUID/equality alone. A same-UUID reconnect therefore receives independent mutable state, a delayed disconnect from the older connection cannot erase the newer connection's session/grant, and asynchronous completions verify that the exact connection still owns the session before applying a result. A terminal denied session remains connection-bound until disconnect so a late client packet cannot recreate a fresh Admission session after the final deny decision. Reconfiguration on an already admitted proxy connection continues to reuse only that exact connection's immutable grant.
Paper's corresponding terminal-state invariant is first-decision-wins: once a timeout/deny is recorded, a delayed success cannot replace it. In Velocity-authority mode, a direct backend connection that reaches final configuration validation without an authenticated proxy assertion fails closed with `PROXY_ASSERTION_REQUIRED`; there is no direct-connect fallback to standalone policy evaluation.
Proxy assertion timestamp arithmetic also fails closed on overflow instead of widening clock-skew acceptance at pathological time values.

**Phase 6 Fabric-origin hardening:** Guardian now enforces that protocol-v1 containment is structurally canonical: `NESTED` and `parentModId` must either both be present or both be absent. Cerberus cross-checks Fabric Loader's `ModContainer#getContainingMod()` relationship against `ModOrigin.Kind.NESTED` and `ModOrigin#getParentModId()`; inconsistent Loader observations are reported conservatively as top-level `MIXED_OR_UNKNOWN` rather than receiving nested-policy treatment. One ordinary regular non-symlink PATH remains `ARCHIVE`; one directory remains `DIRECTORY`; multiple/empty/unsupported path shapes remain `MIXED_OR_UNKNOWN`; Loader/game-provider `builtin` metadata remains `BUILTIN` only when it is not simultaneously claiming a nested container. If an observed archive cannot be safely hashed at collection time, Cerberus downgrades that entry to `MIXED_OR_UNKNOWN` with no digest rather than emitting an impossible unhashed `ARCHIVE` or silently dropping the entire challenge into a timeout. Production defaults continue to deny `DIRECTORY` and `MIXED_OR_UNKNOWN`, while an administrator may deliberately allow those coarse origins. Active staff inspection retains and displays the admission-time origin classification so an unhashed development/ambiguous entry is not mistaken for an ordinary archive whose hash is merely missing. Fabric Loader documents `ModOrigin` as the installation/initial-loading origin and not necessarily the runtime code source; Guardian therefore does not claim that this origin hash independently attests the classes executing in a hostile client. No client filesystem path is added to the wire or staff output.

**Phase 6 standalone Paper quarantine hardening:** the bounded PLAY handshake quarantine is enforced by a dedicated event listener bound to the exact `Player` identity for the admission session. It cancels movement plus the distinct teleport/portal/End-gateway event families, inventory open/click/drag and direct item-state changes, world/entity interactions, commands/chat, item and experience pickup/drop, bucket/fishing/shearing/leash actions, mount/vehicle transitions, outgoing direct/projectile combat, projectile launch, and spectator/flight/sneak/sprint state changes until the final allow decision releases the session. Incoming damage to the quarantined player is also cancelled. Specialized interaction/state-mutation events that own distinct Bukkit/Paper handler lists are registered explicitly rather than assumed to inherit the parent event listener. Spectator-target transitions are blocked explicitly rather than relying only on teleport cancellation. This is event-layer containment for the short supported handshake window, not packet-level sandboxing.

**Phase 6 active-inspection hardening:** completed Admission no longer leaves the full canonical manifest reachable through the long-lived active snapshot. Snapshot retention is reduced to complete counts plus at most 64 deterministic top-level/policy-addressable mod summaries, and the active snapshot stores are capped at 2,048 entries per authority surface. Ordinary inspect output therefore has a fixed upper bound independent of the protocol-v1 512-entry manifest maximum. Successful runtime activation increments a host-local generation; snapshots retain their admission generation and staff output marks whether the evidence belongs to the current generation or predates a reload. Velocity catalog labels are evaluated against and explicitly describe the current catalog, while the decision/profile/mod evidence remains admission-time state. Same-UUID Velocity inspection cleanup is additionally bound to the exact `Player` object that owns the current snapshot so a delayed disconnect from an older connection cannot erase a newer snapshot.

**Phase 6 administrator-file hardening:** runtime loaders now consume bounded stable regular-file snapshots with no-follow semantics rather than validating a path and then reopening it through a separate unbounded/following read. Paper and Velocity `config.yml` are capped at 64 KiB; loaded locale resources at 256 KiB; `policy.yml`, `artifacts.yml`, and `proxy-assertion.key` retain their existing explicit bounds. These text resources are strict UTF-8. Final-path symlinks and non-regular objects are rejected. Guardian-owned nested directory components such as `locales/` are also required to be real directories before startup provisioning or runtime reads, preventing a symlinked child directory from redirecting resource writes/reads outside the plugin data tree; the plugin data root itself remains a deployment-owned mount boundary. Paper startup recovery also refuses to follow an unsafe path and backs up only a bounded stable ordinary file before replacement/removal. In Velocity-authority Paper mode, candidate construction re-resolves the assertion key and compares the actual 32-byte secret with the secret already loaded into the candidate, closing the narrow swap window between config parsing and generation fingerprinting. Successful reload remains parse/validate/immutable-candidate/atomic-activation; failed validation leaves the prior runtime active and the edited files untouched.

**Phase 6 artifact-hash filesystem hardening:** the shared exact-artifact SHA-256 primitive now opens archive paths with no-follow semantics, enforces the byte ceiling during the read, and verifies regular-file identity/size/mtime before and after hashing. This closes the check-to-open replacement window shared by Cerberus archive collection and administrative artifact import. A client archive that cannot be stably hashed is downgraded to conservative `MIXED_OR_UNKNOWN`; an unstable administrative candidate is rejected without catalog mutation. Paths remain local-only and are never placed on the wire.

**Phase 6 signed official Cerberus release identity:** adopted as described in Section 16.1. Official release metadata uses an Ed25519 signature over the release version plus a canonical logical JAR-content SHA-256 digest; the release-only private key is consumed by an offline signing source set and never ships in Cerberus or Guardian. Shared policy may optionally require the signed-release capability and trust one or more public keys. Regression coverage explicitly demonstrates the residual hostile-client limit: the same valid signed release metadata can be reported inside responses bound to different fresh nonces, so the signature proves only the reported official release identity and is not remote attestation.

**Phase 6 Guardian-to-Cerberus server authentication:** adopted as described in Section 17.3. Stock Cerberus may pin one or more Guardian Ed25519 public keys and, when pins are present, verifies a short-lived challenge signature bound to the fresh nonce, required capability mask, and its own authenticated player UUID before collecting or disclosing the manifest. The private key remains only on the Admission authority. This is manifest-privacy/server-authentication hardening, not remote attestation; same-player replay within the bounded validity/skew window and private-key compromise remain documented residual limits. The embedded trust-anchor resource itself is read through a bounded provider-aware channel and revalidated for stable regular-file identity/size/mtime. Ordinary filesystem resources retain no-follow channel semantics; signed production JAR entries use the JDK ZIP filesystem provider's supported read-only channel form while remaining committed by the signed canonical JAR identity.

Server-authentication key replacement is runtime-snapshot scoped: files-only validation may inspect a replacement key but cannot activate it; successful reload atomically activates the new signer for later sessions, while sessions already holding an immutable runtime snapshot retain their prior signer. Repeated failed reloads leave the active generation untouched. Both runtime and offline release-signing PEM readers reject truncated/invalid PEM material explicitly.

Document residual limitations honestly.

---

## Phase 7 — Operations, UX, and release hardening

**Goal:** Make the project maintainable by real administrators.

Refine/polish (basic command/inspection/logging implementation is owned by Phase 5):

- `/guardian status` / `validate` / `reload` / `inspect` UX and documentation;
- player-facing messages;
- help/download URLs;
- clear installation docs;
- standalone Paper guide;
- Velocity network guide;
- Geyser/Floodgate guide;
- LuckPerms/profile guide, including Paper-versus-Velocity permission-provider ownership, shared-storage expectations, and `/lp` versus `/lpv` troubleshooting;
- Guardian Protection guide;
- eZProtector → Guardian configuration migration guide;
- eZProtector → Guardian permission migration table;
- locale/message customization guide;
- security/threat-model documentation;
- release checksums;
- clean-install tests;
- upgrade tests;
- CI release workflow;
- attribution and GPL materials;
- bundled third-party dependency license/NOTICE audit for the final distributable JARs.

Perform a final adversarial release-candidate audit.

---

## Phase 8 — Minecraft 26.3 port

**Goal:** Port only after the Paper 26.3 API is stable enough to target intentionally.

Tasks:

- update Paper target;
- update Fabric/Fabric API target;
- test CONFIGURATION behavior;
- retest Velocity configuration behavior;
- retest Geyser/Floodgate compatibility;
- remove any obsolete 26.2-only workaround rather than accumulating unnecessary compatibility code;
- rerun complete security and deployment matrices.

---

# 34. Core acceptance matrix

Before first public production release, testing SHOULD cover at minimum:

| Client / condition | Expected result |
|---|---|
| Java vanilla, allowed | Allow |
| Approved OptiFine brand | Allow |
| Unknown Java brand under deny policy | Deny |
| Fabric, no Cerberus | `CERBERUS_REQUIRED` |
| Fabric + compatible Cerberus + approved mods | Allow |
| Fabric + Cerberus + unapproved mod | `MANIFEST_DENIED` |
| Fabric + old/incompatible Cerberus | `CERBERUS_PROTOCOL_UNSUPPORTED` |
| Fabric + Cerberus timeout | `CERBERUS_TIMEOUT` |
| Fabric + malformed manifest | `MANIFEST_INVALID` |
| Fabric + replayed prior response | Deny |
| Fabric + excessive payload | Deny |
| Bedrock through Geyser/Floodgate | Bedrock policy; no Cerberus |
| Java user with Bedrock-style prefix | Remains Java |
| Bedrock denied by explicit policy | Deny |
| Velocity-approved Java switches backend | No redundant attestation |
| Same Phase 3 policy snapshot + equivalent normalized inputs through Paper and Velocity adapters | Same `GuardianDecision` |
| Velocity Phase 3 shared-policy denial / required-mod failure / exact-hash failure | Same policy reason/semantics as standalone evaluator; denial occurs at proxy |
| Spoofed client proxy assertion | Deny |
| Backend direct-connect path | Rejected by network/security posture |
| Invalid config reload | Prior config remains active |
| Optional integration missing | Deterministic documented behavior |
| Client class action = ALLOW | Matching normalized class admitted without unnecessary attestation |
| Client class action = DENY | Matching normalized class denied |
| Client class action = REQUIRE_CERBERUS | Matching Fabric class cannot be admitted as ordinary allow |
| Unknown Java brand allowed by configured brand allowlist | Allow under explicit unknown-brand policy |
| Unknown Java brand denied by configured brand denylist | Deny under explicit unknown-brand policy |
| Fabric brand matches permissive raw brand rule | Still follows `JAVA_FABRIC` classification and Cerberus requirement |
| Mod policy allowlist, unlisted policy-addressable mod | `MANIFEST_DENIED` |
| Mod policy denylist, unlisted policy-addressable mod | Allow unless another rule fails |
| Required mod missing in either mod-policy mode | `MANIFEST_DENIED` |
| Required mod present but not redundantly listed as allowed in allowlist mode | Treated as permitted/required, subject to its constraints |
| Contradictory mod policy | Validation failure; prior snapshot retained on reload |
| `guardian.admission.client.bypass.<client>` on class-level DENY | Classification policy denial bypassed for that client key |
| Client bypass on `REQUIRE_CERBERUS` class | Cerberus requirement remains |
| `guardian.admission.mod.bypass.<modid>` with valid manifest | Only applicable mod-policy violation bypassed |
| Admission bypass with malformed/replayed/invalid protocol state | Deny; policy bypass does not bypass integrity validation |
| Protection disabled, Admission enabled | Admission remains functional |
| Admission disabled, Protection enabled | Paper Protection remains functional without Velocity/Cerberus/Geyser/Floodgate |
| Visibility allowlist mode | Only listed/permitted roots exposed, subject to bypass |
| Visibility denylist mode | Listed roots hidden, subject to bypass |
| Namespaced allowlist mode | Unlisted namespaced roots denied; configured aliases allowed |
| Namespaced denylist mode | Listed namespaced roots denied; others unaffected |
| Hidden root guessed manually | No downstream argument suggestions exposed |
| Global visibility bypass | Root and argument suggestions both bypass filtering |
| Per-command visibility bypass | Applies consistently to root and arguments for that command |
| Protection config reload invalid | Prior Protection snapshot remains active; file untouched |
| Malformed existing YAML at startup | Original preserved to timestamped backup; packaged default restored and fully revalidated before activation |
| Malformed/invalid reload candidate | File remains untouched; prior known-good runtime snapshot remains active |
| Unsupported config/locale schema version | No automatic recovery/downgrade; activation or reload fails with an actionable diagnostic |
| Guardian visibility config changes | Online command trees are refreshed through supported API |
| Notification permission without bypass | Receives configured notices but remains subject to rules |
| Bypass without notification permission | Bypasses applicable rule without gaining notices |
| Missing required locale key | Validation/test failure or defined locale fallback; no hard-coded message fallback |
| Legacy `ezprotector.*` permission only | No implicit Guardian bypass/notification authority |

---

# 35. Architecture invariants

The following are hard project invariants unless explicitly revised:

1. **No NMS.**
2. **No server-side reflection into Minecraft implementation internals.**
3. **No packet-library dependency merely to reach unsupported networking internals.**
4. **No username-prefix trust for Bedrock identity.**
5. **No claim that Cerberus provides tamper-proof client attestation.**
6. **No long-term client secret treated as proof of Cerberus authenticity.**
7. **No silent permissive fallback when an attestation-required client fails attestation.**
8. **No generic message that conflates missing Cerberus with a denied manifest.**
9. **No duplicate independent policy authority in Velocity mode.**
10. **No unnecessary collection of machine or filesystem information.**
11. **No long-lived cross-restart trust cache for client manifests.**
12. **No proxy assertion channel that a normal client can impersonate.**
13. **No automatic assumption that CONFIGURATION-stage Paper↔Fabric messaging works until Phase 0A proves it.**
14. **No speculative 26.3-alpha compatibility burden in the initial 26.2 implementation.**
15. **No loss of standalone Paper capability merely because Velocity becomes the preferred production deployment.**
16. **Admission and Protection are independent domains; neither may require the other to be enabled.**
17. **Guardian Protection must operate on standalone Paper without Guardian-Velocity, Cerberus, Geyser, Floodgate, or trusted proxy admission.**
18. **Command execution policy is an enforcement boundary; command visibility/suggestion filtering is not a substitute for execution permissions/enforcement.**
19. **A hidden command root must not leak downstream argument suggestions merely because the player guesses the root.**
20. **Protection bypass semantics must be centralized and consistent across root visibility and argument-suggestion filtering.**
21. **Notification authority and bypass authority are independent.**
22. **No legacy eZProtector client/mod countermeasure is ported into Guardian Protection.**
23. **No legacy `ezprotector.*` permission alias is required by Guardian; migration is documentation-driven.**
24. **No player/staff-facing Guardian feedback string is hard-coded in Java source; locale resources are authoritative.**
25. **No existing malformed/invalid administrator config or locale file is silently regenerated or overwritten; recoverable startup replacement requires a preserved timestamped backup, prominent diagnostics, and full revalidation, while reload never rewrites the candidate.**
26. **Any eZProtector-derived source must come from the identified BadWolfMC GPLv3 lineage unless licensing is deliberately revisited.**
27. **Guardian permissions are domain-scoped under `guardian.admission.*`, `guardian.protection.*`, and `guardian.command.*`; runtime correctness does not depend on wildcard expansion by a permissions provider.**
28. **Admission policy bypasses may bypass configured policy outcomes only; they never bypass protocol, session, manifest-structure, payload-limit, or trusted-proxy integrity checks.**
29. **A client-class bypass does not silently turn `REQUIRE_CERBERUS` into ordinary `ALLOW`.**
30. **Known client classifications use explicit deterministic actions; unknown Java brand rules cannot override trusted Bedrock classification or a positive Fabric/Cerberus requirement.**
31. **Mod-policy allowlist/denylist semantics, required-mod semantics, and baseline/runtime-entry handling are explicit and deterministic; contradictory rules fail validation.**
32. **A required mod is not forced to appear redundantly in an allowlist merely to be considered permitted; required-plus-unconditionally-denied is invalid configuration.**
33. **Admission evaluation follows one documented precedence; raw brand rules and policy bypasses cannot act as late generic overrides of protocol/integrity decisions.**
34. **Guardian-Paper and Guardian-Velocity consume one shared admission-policy schema, parser/validator, immutable policy model, profile-resolution contract, and evaluator; adapter-specific policy forks are prohibited.**
35. **Admission-policy parsing/validation must not depend on Bukkit/Paper `YamlConfiguration`, Velocity implementation classes, or another adapter-specific configuration API.**
36. **Phase 5 productionizes Velocity hosting/ownership/operations; it does not port or redesign the Phase 3 policy engine.**
37. **Guardian-defined permission nodes remain within a conservative 128-character project limit.**

---

# 36. Decisions intentionally deferred

The following should remain open until the indicated implementation phase rather than being guessed prematurely:

- BadWolfMC-only vs broader generic trust onboarding for third-party Guardian servers;
- whether per-command execution bypasses or per-namespace bypass leaves are needed beyond the initial permission hierarchy;
- exact locale selection strategy beyond required default/fallback behavior;
- whether optional PlaceholderAPI expansion is added to ordinary rendered messages;
- whether a generic console-command Protection action ships in the initial public release;
- whether Guardian-Velocity ever gains network-global/proxy-owned command Protection;
- whether a public read-only Guardian API ships in v1.0;
- whether optional persistent audit storage is ever needed.

These are not forgotten requirements. They are deliberately deferred decisions.

---

# 37. Current recommended technical sources

The implementation should re-check current documentation when each phase begins because these APIs are version-sensitive.

### Paper

- Paper 26.2 `PlayerConfigurationConnection`
  https://jd.papermc.io/paper/26.2/io/papermc/paper/connection/PlayerConfigurationConnection.html
- Paper 26.2 `PlayerConnectionValidateLoginEvent`
  https://jd.papermc.io/paper/26.2/io/papermc/paper/event/connection/PlayerConnectionValidateLoginEvent.html
- Paper plugin messaging
  https://docs.papermc.io/paper/dev/plugin-messaging/
- Paper 26.2 `PlayerCommandPreprocessEvent`
  https://jd.papermc.io/paper/26.2/org/bukkit/event/player/PlayerCommandPreprocessEvent.html
- Paper 26.2 `PlayerCommandSendEvent`
  https://jd.papermc.io/paper/26.2/org/bukkit/event/player/PlayerCommandSendEvent.html
- Paper 26.2 `AsyncPlayerSendCommandsEvent`
  https://jd.papermc.io/paper/26.2/com/destroystokyo/paper/event/brigadier/AsyncPlayerSendCommandsEvent.html
- Paper 26.2 `AsyncPlayerSendSuggestionsEvent`
  https://jd.papermc.io/paper/26.2/com/destroystokyo/paper/event/brigadier/AsyncPlayerSendSuggestionsEvent.html
- Paper 26.2 `Player#updateCommands()`
  https://jd.papermc.io/paper/26.2/org/bukkit/entity/Player.html
- Paper 26.2 `Permissible` permission-string API
  https://jd.papermc.io/paper/26.2/org/bukkit/permissions/Permissible.html

### Adventure / MiniMessage

- Adventure MiniMessage documentation
  https://docs.papermc.io/adventure/minimessage/

### Velocity

- Velocity plugin messaging
  https://docs.papermc.io/velocity/dev/plugin-messaging/
- Velocity backend security
  https://docs.papermc.io/velocity/security/
- Velocity modern forwarding
  https://docs.papermc.io/velocity/player-information-forwarding/
- Velocity API/Javadocs, especially configuration events
  https://jd.papermc.io/velocity/

### Fabric

- Fabric documentation
  https://docs.fabricmc.net/
- Fabric 26.2 `fabric.mod.json` specification / mod-ID constraints
  https://docs.fabricmc.net/develop/loader/fabric-mod-json
- Fabric Loader API / `FabricLoader#getAllMods()`
  https://maven.fabricmc.net/docs/fabric-loader-0.19.5/net/fabricmc/loader/api/FabricLoader.html
- Fabric Loader `ModContainer`
  https://maven.fabricmc.net/docs/fabric-loader-0.19.5/net/fabricmc/loader/api/ModContainer.html
- Fabric Loader `ModOrigin`
  https://maven.fabricmc.net/docs/fabric-loader-0.19.5/net/fabricmc/loader/api/metadata/ModOrigin.html

### Geyser/Floodgate

- Geyser API
  https://geysermc.org/wiki/geyser/api/
- Floodgate API
  https://geysermc.org/wiki/floodgate/api/

### LuckPerms

- Developer API usage / offline user loading
  https://luckperms.net/wiki/Developer-API-Usage
- Current LuckPerms MySQL schema (`permission VARCHAR(200)`)
  https://github.com/LuckPerms/LuckPerms/blob/master/common/src/main/resources/me/lucko/luckperms/schema/mysql.sql

---

# 38. Phase 6 closeout / Phase 7 handoff

**Phases 0 through 6 are closed. Phase 7 is the active next phase: operations, UX, documentation, and release hardening.**

Treat this document, the current repository, `docs/PHASE_6_IMPLEMENTATION.md`, `docs/PHASE_6_VERIFICATION.md`, `docs/PHASE_7_HANDOFF.md`, `docs/IMPLEMENTATION_BRIDGES.md`, and `docs/PROVENANCE.md` as authoritative entering Phase 7.

The completed `0.1.0-phase6` baseline preserves the Phase 5 authority architecture while hardening exact-connection lifecycle ownership, proxy replay/timestamp/key boundaries, provider outages, bounded active inspection, diagnostic injection, administrator filesystem reads, development origins, protocol abuse, signed Cerberus release provenance, and Guardian-to-Cerberus challenge authentication for manifest privacy.

The final operator gate is green at **355 tests, 0 failures, 0 errors, 11 documented Windows symlink-privilege skips** (Core 117/4 skipped, Paper 91/4, Protection 20/0, Protocol 50/0, Velocity 58/3, Cerberus 19/0). Focused live closeout passed the authenticated signed-Cerberus standalone path, bounded PLAY quarantine/timeout behavior, deliberate wrong Guardian server-authentication identity with manifest nondisclosure/fail-closed recovery, and Velocity-authoritative Admission with backend switching, authoritative `/guardianv inspect`, and assertion-only backend `/guardian inspect`. The final post-live review found no Phase 6 security/privacy defect requiring additional implementation.

Phase 7 should improve install/release ergonomics and documentation without weakening these boundaries. In particular, it owns the administrator-facing signing/key workflow, public-release configuration evolution/upgrade behavior, clearer user-facing server-authentication mismatch diagnostics, and minor operations/logging polish recorded in `docs/PHASE_7_HANDOFF.md`.

Do not begin the Minecraft/Paper/Fabric 26.3 port in Phase 7; that remains Phase 8.

---

## 39. Current overall assessment

The feasibility and architecture questions that originally made Guardian/Cerberus uncertain are now closed. Standalone Paper uses the supported hybrid CONFIGURATION + bounded PLAY-quarantine path, while Guardian-Velocity can complete authoritative Fabric/Cerberus Admission during awaited CONFIGURATION and pass a short-lived authenticated result to Paper.

Guardian now has stable independent Admission and Protection domains. Protocol v1 carries a canonical Loader-known manifest with top-level archive SHA-256, the administrator artifact importer/catalog is separate from policy approval, and the shared Phase 3 policy engine provides deterministic client/profile/mod/version/hash/containment semantics to either Admission authority.

Phase 4 closes the Bedrock-origin gap. Both possible Admission authorities use supported Geyser/Floodgate evidence before Java brand/Cerberus handling; provider absence, disagreement, and provider failure have explicit semantics; standalone Paper Bedrock allow/deny is live-proven; and backend Floodgate remains diagnostic-only under Velocity authority. BRIDGE-005 is retired.

For BadWolfMC's production topology, Phase 5 now supplies the completed operational productionization: strict Velocity host configuration, production assertion-key lifecycle, distinct Paper/proxy command authorities, bounded active inspection, host-local reload/validation, authority-correct artifact administration, and production logging. The Java 25 / Gradle 9.7.1 gate and focused live matrix are green, BRIDGE-003/004 are retired, and Phase 6 can now concentrate on adversarial/security hardening rather than unfinished operations work.

Phase 6 is now closed. It preserves that architecture while hardening exact-connection lifecycle ownership, proxy replay/timestamp/key boundaries, provider outages, inspection/logging bounds, administrator filesystem reads, development origins, and protocol abuse surfaces. It also adopts two deliberately narrow Ed25519 mechanisms: signed official Cerberus release provenance and Guardian-to-Cerberus player-bound challenge authentication for manifest privacy. Neither mechanism is documented as hostile-client remote attestation. The final Java 25 / Gradle 9.7.1 gate is green at 355 tests and the focused signed-release/server-authentication/quarantine/Velocity live matrix passed. Phase 7 can therefore concentrate on installability, operator UX, documentation, configuration evolution, and release packaging rather than unfinished security architecture.
