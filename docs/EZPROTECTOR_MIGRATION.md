# eZProtector → Guardian Protection migration

This guide maps the selected useful behavior from the supplied GPLv3 BadWolfMC eZProtector lineage to Guardian Protection. It is a **concept migration**, not a legacy-schema compatibility layer. Guardian does not parse the old eZProtector configuration and does not recognize `ezprotector.*` permissions.

Do not run eZProtector's overlapping command-protection features and Guardian Protection at the same time. Migrate the desired policy, migrate permissions, then retire eZProtector.

## Configuration concept mapping

| eZProtector concept | Guardian Protection | Migration note |
|---|---|---|
| `custom-commands.blocked` + `custom-commands.commands` | `protection.execution.enabled` + `blocked-roots` | Root-scoped player execution denial. Arguments are not part of matching. |
| `custom-commands.error-message` | locale key `protection.command-denied` | Guardian keeps player-facing strings in locale resources. |
| `custom-commands.notify-admins` | `protection.notifications.enabled` + `guardian.protection.notify` | One consistent notification permission replaces feature-specific notify nodes. |
| `custom-commands.punish-player` | No Phase 1B migration | Punishment commands are deliberately out of initial scope. |
| `tab-completion.blocked` | `protection.visibility.enabled` | Visibility/disclosure control only. |
| `tab-completion.whitelist: false` | `protection.visibility.mode: DENYLIST` | Listed roots are hidden. |
| `tab-completion.whitelist: true` | `protection.visibility.mode: ALLOWLIST` | Only listed roots are exposed. |
| `tab-completion.commands` | `protection.visibility.roots` | Guardian applies the same root decision to downstream suggestions. |
| eZ per-command tab bypass | `guardian.protection.visibility.bypass.<command-key>` | Uses Guardian's normalized/bounded command key; see `GUARDIAN_PROTECTION.md`. |
| `hidden-syntaxes.blocked` | `protection.namespaces.enabled` | Guardian governs complete `namespace:command` roots, not arbitrary colon-containing argument text. |
| `hidden-syntaxes.whitelisted` | `protection.namespaces.mode: ALLOWLIST` + `roots` | Closest match to eZProtector's broad “block all namespaced roots except these” behavior. Audit carefully before enabling. |
| `hidden-syntaxes.error-message` | locale key `protection.namespace-denied` | Locale-backed Adventure/MiniMessage. |
| `hidden-syntaxes.notify-admins` | `protection.notifications.enabled` + `guardian.protection.notify` | Notification does not grant bypass. |
| `hidden-syntaxes.punish-player` | No Phase 1B migration | Deliberately excluded. |
| `custom-plugins` fake `/plugins` response | No fake-response migration | If disclosure/execution should be denied, hide/block the applicable roots instead. |
| `custom-version` fake version response | No fake-response migration | If disclosure/execution should be denied, hide/block the applicable roots instead. |
| `mods.*`, 5zig, BetterPvP, BetterSprinting, Fabric/Forge/LiteLoader/Rift, Schematica, VoxelMap, WDL | No Protection migration | Historical client/mod countermeasures are retired. Guardian Admission/Cerberus owns modern client policy where applicable. |
| eZ raw `/ezp reload` | `/guardian validate` then `/guardian reload` | Validate files without activation first; reload publishes only a complete valid Paper runtime snapshot. |

### Important namespace migration difference

eZProtector's `hidden-syntaxes` historically blocked commands containing `:` unless whitelisted. Guardian deliberately models this as a full-root namespaced policy.

For a strict legacy-style transition:

```yaml
protection:
  namespaces:
    enabled: true
    mode: ALLOWLIST
    roots:
      - minecraft:msg
      - essentials:warp
```

This denies all other namespaced roots. It can also break legitimate plugin conflict-resolution aliases, so the packaged Guardian default is safer: `DENYLIST` only the aliases BadWolfMC actually wants to deny and add more deliberately after testing.

## Permission migration

The table below intentionally lists only new Guardian permission nodes. No old node is an alias.

| eZProtector permission/intention | Guardian permission | Notes |
|---|---|---|
| broad Protection bypass (`ezprotector.bypass` / broad command bypass) | `guardian.protection.bypass` | Aggregate Protection exemption. |
| custom blocked-command bypass | `guardian.protection.command.bypass` | Execution only. |
| hidden-syntax bypass | `guardian.protection.namespace.bypass` | Namespaced policy only. |
| tab-completion global bypass | `guardian.protection.visibility.bypass` | Applies to both root visibility and downstream suggestions. |
| tab-completion per-command bypass | `guardian.protection.visibility.bypass.<command-key>` | Optional; enable `protection.visibility.per-command-bypass`. |
| command/admin violation notification nodes | `guardian.protection.notify` | Single notification authority; never a bypass. |
| mod bypass/notify nodes | No Protection mapping | Retired legacy behavior; do not translate into Protection permissions. |
| fake plugin/version bypass | No direct mapping | Fake responses are retired. Grant native command permission and adjust Guardian execution/visibility rules instead. |
| eZ reload | `guardian.command.validate` / `guardian.command.reload` | Paper-local administrative authority. |

Provider wildcards such as `guardian.protection.*` may be convenient, but Guardian runtime correctness does not depend on wildcard expansion. Explicit aggregate/feature bypasses are real permission nodes.

## Suggested BadWolfMC transition order

1. Inventory the eZProtector command roots and namespaced aliases BadWolfMC actually still relies on.
2. Translate them into Guardian `execution`, `visibility`, and `namespaces` rules rather than copying the entire historical file.
3. Translate LuckPerms assignments to the new `guardian.protection.*` nodes. Remove old `ezprotector.*` assignments once verified.
4. Enable Guardian Protection on a test Paper backend with eZProtector command interception disabled/removed.
5. Test ordinary players, bypass users, notification-only staff, namespaced legitimate commands, and console/plugin-dispatched commands.
6. Remove eZProtector after the migrated policy is accepted.


## Release-era migration closeout

Guardian 1.0 does not ship an eZProtector compatibility parser or permission aliases. Migrate the desired rules once, grant the corresponding `guardian.protection.*` and `guardian.command.*` permissions, validate the resulting Guardian files, then remove eZProtector before enabling overlapping Protection rules in production. Running both enforcement systems concurrently produces ambiguous denials/notifications and is not a supported steady state.

On a Velocity deployment, `/guardian` remains **Paper-local** (Protection, backend assertion diagnostics, local validation/reload) while `/guardianv` is the **Velocity/network Admission authority**. Do not use `/guardianv` as a replacement for Paper-side Protection administration, and do not expect `/guardian inspect` on an assertion-only backend to expose the full Fabric manifest.
