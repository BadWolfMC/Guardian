# Production deployment / rollback runbook

This is the short operational path for a Guardian/Cerberus production change. It is written for an administrator working under time pressure. Detailed architecture and threat-model material lives elsewhere.

BadWolfMC topology:

```text
Velocity + Guardian-Velocity
        |
        +--> Alpha + Guardian-Paper
        +--> Beta  + Guardian-Paper
        +--> Gamma + Guardian-Paper
        +--> Delta + Guardian-Paper
```

Guardian-Velocity is the Admission authority. Guardian-Paper remains Paper-local for Protection and verifies authenticated proxy Admission assertions.

## Before the maintenance window

1. Download/verify the intended release assets and `SHA256SUMS.txt`.
2. Back up each Guardian data directory, including:
   - `config.yml`;
   - `policy.yml` where that host owns one;
   - `artifacts.yml` and artifact-import output;
   - locale files;
   - `proxy-assertion.key`;
   - `guardian-server-auth.key` where present; and
   - any `.pre-schema...bak` migration backups you may need for rollback.
3. Back up the **offline** Cerberus release-signing identity separately. Do not copy it onto a game host as part of deployment.
4. Record current `/guardianv status` plus `/guardian status` on each backend, including proxy-assertion fingerprints.
5. Preserve the currently working Guardian/Cerberus JARs so rollback does not depend on re-downloading anything.
6. If rotating a key, follow the matching section below before replacing any active key.

## Normal network deployment

This rolling order is for releases whose notes say the existing Velocity -> Paper assertion format remains compatible. If a future release changes the assertion protocol/key format or explicitly requires lockstep deployment, keep the network closed and upgrade the proxy/backends as one coordinated maintenance operation instead.

### 1. Stage the proxy first

1. Stop Velocity cleanly.
2. Replace only the Guardian-Velocity JAR.
3. Apply intended Velocity `config.yml`, authoritative `policy.yml`, catalog, locale, and key changes.
4. Start Velocity.
5. Run:

```text
/guardianv status
/guardianv validate
```

Do **not** proceed to the backends if validation/status is not clean.

Why proxy first: it owns the network Admission decision and the policy format used for new connections. Backends only verify the resulting assertion.

### 2. Stage the backends one at a time

For Alpha, then Beta, Gamma, Delta:

1. stop the backend cleanly;
2. replace Guardian-Paper;
3. confirm `admission.authority: velocity`;
4. confirm the backend has the expected `proxy-assertion.key`;
5. start the backend;
6. run `/guardian status` and `/guardian validate`;
7. compare the short proxy-assertion fingerprint with Velocity; and
8. verify Protection reports enabled when that is the intended deployment.

Do not rotate/copy `guardian-server-auth.key` onto assertion-only backends. In this topology it belongs only on the Velocity Admission authority.

### 3. Smoke-test before reopening broadly

Use one known-good account/client at a time:

- Vanilla Java joins normally.
- Signed Fabric + Cerberus joins normally.
- `/guardianv inspect <player>` shows the authoritative Admission snapshot.
- Backend `/guardian inspect <player>` remains assertion-only.
- Switch one connected player between two backends; no second Cerberus inventory should occur.
- Exercise one representative Guardian Protection blocked command and confirm it is also hidden when visibility policy says it should be.
- Run `/guardianv validate` and each backend `/guardian validate` once more.

If this release did not touch Bedrock/Geyser/Floodgate behavior, a full Bedrock matrix is unnecessary. If it did, include one normal Bedrock join and confirm no Cerberus challenge is attempted.

## Reload versus restart

Use reload for administrator-owned policy/config changes that Guardian explicitly supports:

```text
/guardianv validate
/guardianv reload
```

for network Admission policy/catalog changes, and:

```text
/guardian validate
/guardian reload
```

for Paper-local settings/Protection changes.

A failed reload leaves the previous known-good runtime snapshot active.

Prefer a full process restart when:

- replacing Guardian/Cerberus binaries;
- changing `proxy-assertion.key`;
- changing `guardian-server-auth.key`;
- changing Geyser/Floodgate/LuckPerms plugin installation or plugin versions;
- changing proxy/backend forwarding/firewall topology; or
- recovering from an uncertain partially completed deployment.

## Fast rollback

If the new release is unhealthy but configuration/schema is still compatible:

1. stop the affected process;
2. restore the previous Guardian JAR;
3. restore the previous matching configuration/data backup if the new release changed it;
4. restore the previous key file(s) if the deployment changed keys;
5. restart;
6. run status/validate; and
7. test one known-good join before reopening.

### Schema migration rollback

Do **not** expect an older binary to understand a newer schema.

For the supported schema-1 -> schema-2 migration:

1. stop the host/proxy;
2. restore the previous plugin JAR;
3. restore the exact `.pre-schema2-...bak` copy of the administrator file before startup; and
4. keep the newer schema-2 file separately for diagnosis.

The migration backup is part of rollback, not clutter to delete immediately after upgrade.

## Emergency: Velocity Guardian cannot admit players

Do not delete keys or weaken policy blindly.

1. Keep the backends firewalled from direct public access.
2. Read the Velocity Guardian startup/status error and run `/guardianv validate` from console if the proxy is usable.
3. If this started immediately after deployment, restore the previous Guardian-Velocity JAR + matching config/policy/key backup and restart Velocity.
4. If the error is a proxy assertion mismatch, compare fingerprints and restore the known-good `proxy-assertion.key` on Velocity and all backends as one coordinated set.
5. If the error is Guardian server-authentication mismatch, restore the last known-good `guardian-server-auth.key`; do not replace Cerberus trust anchors ad hoc.
6. If signed-Cerberus release trust is the problem, restore the previous known-good `policy.yml`/public release key set rather than disabling verification without understanding why.
7. Re-test one known-good Vanilla and one known-good signed Cerberus client.

If immediate service restoration matters more than completing the new deployment, roll back to the known-good release. Do not turn the Paper backends into standalone authorities merely to bypass a broken proxy unless you deliberately stage the standalone policy/key configuration and understand the direct-backend exposure implications.

## Diagnosing key mismatches

### Proxy assertion key

Symptoms:

- Velocity admits the client but Paper rejects the trusted assertion;
- `/guardian status` reports verifier/key problems; or
- proxy/backend fingerprints differ.

Action: restore the same `proxy-assertion.key` to Velocity and every trusted backend. Never copy this key to Cerberus.

### Guardian server-authentication key

Symptoms:

- Cerberus logs that the Guardian challenge is untrusted;
- Cerberus withholds its manifest; and
- Guardian eventually fails closed (normally `CERBERUS_TIMEOUT`).

Action: verify the active authority is using the private key corresponding to a public trust anchor embedded in the deployed Cerberus release. Use staged old+new public anchors for rotation.

### Cerberus release-signing key

Symptoms:

- Guardian receives a Cerberus release identity but policy rejects its signature/artifact provenance.

Action: verify authoritative `policy.yml` contains the public key corresponding to the offline key that signed that exact Cerberus release. Never copy the release-signing private key to Velocity/Paper to fix this.

## Key rotation rehearsal

### `proxy-assertion.key`

There is no old+new dual acceptance. Treat rotation as a coordinated maintenance event, not a live reload:

1. stop Velocity and the trusted Paper backends (or otherwise keep players from reaching the mixed-key topology);
2. back up the current `proxy-assertion.key` outside the active data directories;
3. move/remove the active Velocity copy, start Velocity in the closed maintenance topology once so Guardian generates a fresh key, then stop Velocity again;
4. copy that exact newly generated file to Alpha, Beta, Gamma, and Delta;
5. start Velocity/backends;
6. compare the short fingerprint everywhere before reopening;
7. test one normal Admission assertion; and
8. retire the old secret only after recovery evidence is recorded.

Do not independently generate four backend keys. The proxy and every trusted backend must share the exact same 32-byte secret.

### `guardian-server-auth.key`

Use the staged old+new public-anchor model:

1. generate the new identity offline/securely;
2. release Cerberus containing **old + new** public server-auth anchors;
3. allow clients to update;
4. switch the Guardian authority to the new private key;
5. test Admission; and
6. later release Cerberus with only the new public anchor.

### Cerberus release-signing key

1. generate a new offline release identity;
2. add the new **public** key alongside the old one in authoritative `policy.yml`;
3. validate/reload Guardian;
4. sign new Cerberus releases with the new private key;
5. verify a new client release; and
6. remove the old public key only after the old release population no longer needs acceptance.

## Compromise response

- **Proxy assertion key compromised:** rotate the secret across proxy + all backends and verify backend isolation/firewalls.
- **Guardian server-auth private key compromised:** stage a new client trust-anchor release, move the authority to the new key, then remove the compromised public anchor in a later Cerberus release.
- **Cerberus release-signing private key compromised:** stop signing, remove/retire its public key according to rollout needs, generate a replacement identity, and publish a newly signed Cerberus release. Record the affected release window.

Never reuse one key domain to repair another.

## Closeout record

After deployment, retain:

- release version/tag and `RELEASE_PROVENANCE.txt`;
- `SHA256SUMS.txt`;
- the final release assets;
- pre-deployment config/key backup location;
- status/fingerprint notes; and
- the result of the small smoke matrix.

That record is enough to make the next rollback/recovery decision without reconstructing the release under pressure.
