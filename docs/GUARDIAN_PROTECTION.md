# Guardian Protection — Phase 1B operator and configuration guide

Guardian Protection is Guardian's Paper-authoritative command-protection domain. It is independent from Guardian Admission and does not require Guardian-Velocity, Cerberus, Geyser, Floodgate, or a successful client admission exchange.

Phase 1B is intentionally **player-command only**. Guardian Protection intercepts third-party command execution through Paper's supported `PlayerCommandPreprocessEvent` path. It does not subscribe to `ServerCommandEvent` and therefore does not block commands issued by the console, command blocks, remote console, or ordinary plugin dispatch. Guardian also does not redispatch blocked commands through another sender.

## Security boundary versus disclosure controls

The three initial policy surfaces have different jobs:

- `protection.execution` is the command-execution security boundary. A configured player command root is cancelled before execution unless the applicable bypass is granted.
- `protection.visibility` is a disclosure/UX control. It removes command roots from the command list sent to the player and suppresses downstream argument suggestions for the same hidden root. Hiding a command is **not** execution security.
- `protection.namespaces` controls direct player invocation of `namespace:command` roots. It is evaluated before the ordinary execution denylist for namespaced commands.

All three surfaces use the same deterministic command-root normalization. Matching is case-insensitive; one normal command slash is ignored; arguments do not participate in the root decision. For example, `/PLUGINS`, `plugins`, and `/plugins anything` all resolve to the root `plugins`.

## Configuration

Protection is globally activated independently from Admission:

```yaml
features:
  protection:
    enabled: true
```

The Phase 1B policy lives under `protection`:

```yaml
protection:
  execution:
    enabled: true
    blocked-roots:
      - plugins
      - version

  visibility:
    enabled: true
    mode: DENYLIST
    roots:
      - plugins
      - version
    per-command-bypass: true

  namespaces:
    enabled: true
    mode: DENYLIST
    roots:
      - bukkit:plugins
      - bukkit:version

  notifications:
    enabled: true
```

### Execution

`execution.blocked-roots` is deliberately a denylist in Phase 1B. If enabled, listed player command roots are denied. This surface does not have an allowlist mode because Guardian-owned commands should use native permissions and Phase 1B only needs configured third-party/root blocking.

### Visibility

`visibility.mode` supports:

- `DENYLIST` — listed roots are hidden; other roots remain visible.
- `ALLOWLIST` — only listed roots remain visible, subject to Paper's own command permissions and Guardian bypasses.

If a root is hidden, Guardian applies the same visibility decision to Paper's downstream suggestion event. Guessing `/hiddenroot ` must not reveal argument suggestions for that root.

### Namespaces

`namespaces.mode` supports:

- `DENYLIST` — only listed full `namespace:command` roots are denied.
- `ALLOWLIST` — every namespaced root is denied except those explicitly listed.

Entries under `namespaces.roots` must be complete namespaced roots such as `minecraft:msg` or `essentials:warp`. Unnamespaced entries are rejected during configuration validation.

The packaged default uses a conservative `DENYLIST` of selected Bukkit information aliases. Guardian does **not** blindly block every colon-containing command by default because valid plugin functionality may rely on namespaced aliases. Administrators who intentionally want the old eZProtector broad policy can use `ALLOWLIST` and enumerate the namespaced aliases they permit.

## Permissions

Guardian does not recognize `ezprotector.*` permissions.

| Permission | Meaning |
|---|---|
| `guardian.protection.bypass` | Aggregate exemption from all Protection policy surfaces. |
| `guardian.protection.command.bypass` | Exemption from execution deny rules. |
| `guardian.protection.namespace.bypass` | Exemption from namespaced-command rules. |
| `guardian.protection.visibility.bypass` | Exemption from root visibility and downstream suggestion filtering. |
| `guardian.protection.visibility.bypass.<command-key>` | Optional visibility/suggestion exemption for one normalized root. |
| `guardian.protection.notify` | Receive Protection denial notifications; grants no bypass. |

Bypass resolution is centralized in `guardian-protection`. Notification permission is intentionally separate: notification authority does not exempt a player from rules, and a bypass does not grant staff notifications.

### Per-command visibility keys

Dynamic permission suffixes are canonical and bounded. Ordinary ASCII command identifiers stay readable. Characters unsuitable for the permission key are UTF-8 byte escaped as `_xx`; for example:

- `plugins` → `guardian.protection.visibility.bypass.plugins`
- `bukkit:plugins` → `guardian.protection.visibility.bypass.bukkit_3aplugins`
- `?` → `guardian.protection.visibility.bypass._3f`

Very long normalized roots are represented by a bounded `sha256-...` key so Guardian-generated nodes remain below the project's 128-character permission-node ceiling. The active key is deterministic for the same normalized root.

## Messages and notifications

Player denials and staff notifications are locale-backed in `locales/en_us.properties` (and any configured locale catalog). Runtime player/command/root values are inserted with Adventure MiniMessage `Placeholder.unparsed`, so command text cannot inject MiniMessage click/hover/style tags.

Notifications are sent only to online players holding `guardian.protection.notify` when `protection.notifications.enabled` is true. Guardian also writes a concise denial diagnostic to the server log with player name/UUID, normalized root, and decision reason.

## Command-tree refresh and reload lifecycle

Guardian uses `Player.updateCommands()` to refresh online client command trees whenever the active Protection visibility policy is changed, enabled, or disabled through Guardian's validated runtime activation path. The runtime path remains parse → validate complete immutable candidate → atomic activation; an invalid candidate never replaces the previous snapshot and Guardian does not rewrite the administrator's invalid reload file.

Phase 5/7 expose that validated runtime through `/guardian validate` and `/guardian reload`. `validate` performs files-only parsing/validation and activates nothing. `reload` builds a complete candidate snapshot first and swaps it atomically only after all Admission/Protection/localization validation succeeds. A failed reload leaves the prior runtime active. Protection visibility changes trigger supported command-tree refresh for online players rather than requiring a restart.

Permission-provider-specific immediate refresh hooks are not required for correctness. Stale client visibility is never treated as the execution security boundary; execution filtering remains authoritative.

## Paper 26.2 API placement

Phase 1B uses supported Paper APIs only:

- `PlayerCommandPreprocessEvent` for player-run third-party command execution interception;
- `PlayerCommandSendEvent` for removing advertised root commands;
- `AsyncPlayerSendSuggestionsEvent` for suppressing downstream suggestions for a root Guardian has hidden;
- `Player.updateCommands()` to resend the player's command tree after Guardian visibility-policy activation changes.

The suggestion event may be asynchronous. Guardian therefore does not perform live Bukkit permission lookups from an asynchronous suggestion callback; it reuses the visibility-bypass permission snapshot captured while Paper constructs the player's command tree. No NMS, CraftBukkit implementation classes, reflective internal access, or packet library is used.
