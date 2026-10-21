# Configuration upgrades

## Public release baseline

Guardian's public-release host/policy schema begins at **2**:

- Guardian-Paper `config.yml`: schema 2
- Guardian-Velocity `config.yml`: schema 2
- shared Admission `policy.yml`: schema 2

Locale files and `artifacts.yml` have independent schemas and remain at their existing schema versions.


## Current platform line

Guardian/Cerberus `1.0.2` and subsequent `1.0.3` target Minecraft/Paper/Fabric 26.3 on Java 25. Configuration schema 2, protocol v1, key files, Admission policy semantics, and Protection semantics remain unchanged by the platform port.

Treat a move from the prior 26.2 binary line as a coordinated platform/binary update rather than a configuration migration: update Paper backends and the Cerberus client environment to 26.3, deploy the matching Guardian-Paper/Guardian-Velocity/Cerberus release set, and preserve the existing Guardian data directory and three key domains. Do not attempt to make a 26.3 Cerberus build serve both Minecraft 26.2 and 26.3 through compatibility shims.

For the `1.0.3` diagnostic rollout only, a correctly signed Cerberus `1.0.3` client may connect to an existing Guardian `1.0.2` authority before the server JARs are upgraded: the wire protocol and required capabilities remain v1, and Guardian does not demand equal product versions. This requires the **same trusted Cerberus release-signing key**, the **same embedded Guardian server trust anchors**, and an admission policy that permits Cerberus `1.0.3` (including version/hash checks, if configured). This is not a promise of compatibility between different Minecraft platform lines or future protocol changes.

## Legacy schema-1 migration

Guardian contains exactly one automatic compatibility migration: **legacy schema 1 -> public schema 2**. This primarily exists for installations that ran an internal pre-1.0 build before upgrading to the public release line.

On startup, before normal activation:

1. Guardian safely reads the administrator file without following symlinks;
2. only a valid top-level numeric `schema-version: 1` is eligible;
3. it prepares a surgical text migration rather than reserializing the whole document;
4. administrator comments/order/formatting and values are preserved;
5. `policy.yml` receives a disabled `cerberus-release-trust` block only if that block is absent;
6. the migrated candidate is reparsed before publication;
7. Guardian verifies the source file did not change after preparation;
8. exact original bytes are written to a timestamped `.pre-schema2-...bak` file;
9. the migrated file is atomically replaced where the filesystem supports it;
10. ordinary strict loaders then perform the full product validation.

The safe backfill is deliberately:

```yaml
cerberus-release-trust:
  required: false
  ed25519-public-keys: []
```

so upgrading does not silently begin requiring signed Cerberus releases.

## Unsupported historical/future schemas

Guardian does **not** carry compatibility code for arbitrary historical internal schemas. Schemas other than the single reviewed `1 -> 2` path continue through normal strict validation/recovery behavior. A newer schema is never silently downgraded.

Before a future public schema bump, add a deliberate migration from the immediately supported public predecessor, tests using representative administrator-owned values/comments, release notes, and backup/rollback documentation.

## Recommended operator upgrade procedure

1. Back up the Guardian data directory.
2. Replace the plugin JAR(s), but do not delete administrator files.
3. Start one test host/proxy first.
4. Review any `upgraded ... schema 1 to 2` warning and the generated `.bak` path.
5. Compare the migrated file with the backup.
6. Run `validate` and `status`.
7. On a Velocity network, upgrade/test the proxy authority before rolling the same release to backends.
8. Preserve key files unchanged unless the release specifically calls for a planned rotation.

If startup cannot migrate safely, Guardian fails closed rather than guessing at administrator intent.

## Rollback across schema 1 -> 2

The schema-2 host/policy files are not intended to be consumed by the older internal schema-1 binaries. If rollback to a schema-1 build is required:

1. stop Guardian/Paper/Velocity cleanly;
2. restore the previous plugin JAR(s);
3. restore the matching `.pre-schema2-...bak` `config.yml` / `policy.yml` copies before startup; and
4. keep the newer files separately for diagnosis rather than asking the older binary to reinterpret them.

Do not rotate or replace any of the three key domains merely because a schema rollback is being performed.


For network deployment order, rollback under pressure, and key mismatch recovery, see `PRODUCTION_RUNBOOK.md`.


### Profile inheritance (Guardian 1.0.3+)

Admission schema 2 supports an optional single-parent `inherit` key. For example:

```yaml
profiles:
  default:
    priority: 0
    # Full baseline: clients, unknown-brands, mods
  staff:
    inherit: default
    priority: 100
    mods:
      rules:
        allow-staff-tools:
          mod: stafftools
          action: ALLOW
  admin:
    inherit: staff
    priority: 200
    mods:
      rules:
        allow-admin-tools:
          mod: admintools
          action: ALLOW
```

`default → staff → admin` means staff has all public allowances plus stafftools,
and admin has both plus admintools. Assign **only** `guardian.admission.profile.staff`
to a staff member: inheritance is not permission inheritance. Identity overrides and
priority-based selection still choose exactly one effective profile. Every profile
must declare its own unique `priority`. Clients merge by client class; unknown-brand
mode and brand list merge by field; mod mode/origins inherit with explicit field
overrides; baselines union; required and explicit mod rules accumulate.

An inherited rule cannot be silently replaced: a child override must use the **same
rule ID, mod ID, collection** (`required` versus `rules`), and `override: true`.
Rules with another ID for the same inherited mod are invalid. Rule deletion is not
supported. Replacing a required rule does not remove its required status. Inherited
version predicates and SHA-256 restrictions are unchanged unless intentionally
replaced through the explicit override mechanism. Root profiles must include every
required section. Unknown parents, cycles, malformed `inherit`, and chains exceeding
16 levels fail validation. Bad reloads never replace the active policy snapshot.
Older Guardian binaries do not recognize `inherit`; expand profiles before rollback.
