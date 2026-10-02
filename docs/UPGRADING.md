# Configuration upgrades

## Public release baseline

Guardian's public-release host/policy schema begins at **2**:

- Guardian-Paper `config.yml`: schema 2
- Guardian-Velocity `config.yml`: schema 2
- shared Admission `policy.yml`: schema 2

Locale files and `artifacts.yml` have independent schemas and remain at their existing schema versions.

## Supported pre-1.0 upgrade

Guardian contains exactly one automatic compatibility migration: **final Phase 6 / pre-1.0 release-candidate schema 1 -> public schema 2**.

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

Guardian does **not** carry compatibility code for arbitrary development snapshots. Schemas other than the single reviewed `1 -> 2` path continue through normal strict validation/recovery behavior. A newer schema is never silently downgraded.

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

## Rollback before public release closeout

The schema-2 host/policy files are not intended to be consumed by the older Phase 6 binaries. If a release-candidate rollback is required:

1. stop Guardian/Paper/Velocity cleanly;
2. restore the previous plugin JAR(s);
3. restore the matching `.pre-schema2-...bak` `config.yml` / `policy.yml` copies before startup; and
4. keep the newer files separately for diagnosis rather than asking the older binary to reinterpret them.

Do not rotate or replace any of the three key domains merely because a schema rollback is being performed.
