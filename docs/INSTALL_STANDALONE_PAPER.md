# Standalone Guardian-Paper deployment

This guide is for a Paper 26.2 server where Guardian-Paper itself owns Admission. No Velocity module is required.

## 1. Install

1. Run Java 25 and Paper 26.2.
2. Place the Guardian-Paper JAR in `plugins/`.
3. Start once and stop cleanly so `plugins/Guardian/` is created.
4. Confirm `config.yml` contains `admission.authority: standalone`.
5. Review `policy.yml` before opening the server to players.
6. Start and run `/guardian status` followed by `/guardian validate`.

Guardian writes packaged defaults only when files are absent. It does not overwrite administrator-owned valid files on ordinary startup.

## 2. Cerberus policy

The default Fabric action is `REQUIRE_CERBERUS`. The default policy is intentionally restrictive: policy-addressable top-level mods must be approved under the configured mod-policy semantics.

Use the two-step artifact workflow when exact hashes are desired:

1. place administrator-approved Fabric JARs in `plugins/Guardian/artifact-import/`;
2. run `/guardian artifacts scan`;
3. review `artifacts.yml` and `artifact-import-rules.yml`;
4. deliberately copy/merge the desired rule into `policy.yml`;
5. run `/guardian validate`; then `/guardian reload`.

**Scanning identifies; policy approves.** Importing an artifact never allows it automatically.

## 3. Optional Guardian server authentication

This protects Cerberus manifest privacy by making Cerberus disclose its manifest only after authenticating the Guardian challenge.

Generate a server identity using the release helper, install the private `guardian-server-auth.key` only on this Guardian-Paper host, and distribute only the corresponding public trust anchor inside the Cerberus release. Then set:

```yaml
admission:
  standalone:
    server-authentication:
      enabled: true
```

Run `/guardian validate` before reload/restart. Rotation is staged old+new trust anchors; see `KEY_MANAGEMENT.md`.

## 4. Signed Cerberus release identity

If you operate only official Cerberus builds, set `cerberus-release-trust.required: true` in `policy.yml` and add the Cerberus **release-signing public key**. Do not put the release-signing private key on the Minecraft server.

This is exact-artifact/compliance hardening, not remote attestation.

## 5. Optional integrations

- LuckPerms: profile and bypass resolution; see `LUCKPERMS_PROFILES.md`.
- Geyser/Floodgate: Bedrock classification before Java/Cerberus handling; see `GEYSER_FLOODGATE.md`.
- Protection: enabled by default and independently configurable under `features.protection.enabled`; see `GUARDIAN_PROTECTION.md`.

## 6. Operator checks

`/guardian status` should identify `standalone` authority and explicitly report Guardian server-authentication state and whether signed Cerberus release identity is REQUIRED or OPTIONAL.

`/guardian inspect <player>` is authoritative only for the player's current active standalone connection. Guardian does not persist historical manifests.

For production backup/rollback/recovery steps, see `PRODUCTION_RUNBOOK.md`.

## 7. Firewall and exposure

Standalone mode has no proxy assertion key. Standard Paper network/security hardening still applies. Guardian is not a replacement for firewalling, authentication, or an anti-cheat.
