# Guardian / Cerberus key management

Guardian uses **three independent key domains**. They are not interchangeable and solving one problem does not solve another.

| Domain | Purpose | Private/secret material lives | Public/shared material |
|---|---|---|---|
| Proxy assertion HMAC | Authenticates Velocity -> Paper Admission assertions | Velocity + every trusted Paper backend | none; same secret on controlled servers |
| Guardian server-auth Ed25519 | Lets Cerberus authenticate Guardian before manifest disclosure | active Admission authority only | trust anchors embedded into official Cerberus release |
| Cerberus release-signing Ed25519 | Lets Guardian verify official Cerberus release identity metadata | offline release-manager storage only | public key(s) in authoritative `policy.yml` |

No reusable long-term private secret belongs in Cerberus.

## 1. `proxy-assertion.key`

Guardian-Velocity generates this secret automatically on first startup. Copy it unchanged to every Paper backend configured with `admission.authority: velocity`.

- **Private?** Yes.
- **Back up?** Yes, in the same protected infrastructure secret store as other proxy/backend credentials.
- **Never:** put it in Cerberus, source control, a public release, logs, or screenshots.
- **Status:** Guardian displays only a short fingerprint for comparison.
- **Rotation:** coordinated cutover. All trusted backends must receive the new secret in the same maintenance window as Velocity; old/new dual acceptance is not a protocol feature.
- **Compromise:** isolate direct backend access, generate a new proxy secret, replace it on Velocity and every backend, restart/reload as documented, and verify matching fingerprints before reopening.

## 2. `guardian-server-auth.key`

This Ed25519 private key signs player-bound Guardian challenges. Cerberus verifies a public trust anchor before disclosing its manifest.

- **Private?** Yes.
- **Standalone:** private key lives on Guardian-Paper authority.
- **Velocity network:** private key lives on Guardian-Velocity authority; do not copy it to assertion-only backends.
- **Public material:** the corresponding X.509/SPKI public key is safe to distribute and is embedded in the Cerberus release trust-anchor resource.
- **Back up?** Yes, encrypted/protected; losing it requires a planned client trust-anchor release.

### Rotation: staged old + new anchors

Server-auth rotation deliberately uses a staged client release:

1. generate the **new** server identity;
2. create/release Cerberus that trusts **old + new public anchors**;
3. allow clients time to update;
4. switch Guardian authority to the new private key;
5. verify normal Admission;
6. later release Cerberus with only the new anchor when the transition window is over.

Never switch the server private key first unless you intentionally want old Cerberus clients to reject Guardian and fail closed.

- **Compromise:** treat the old identity as untrusted, stage a replacement Cerberus anchor set as quickly as practical, move the authority to the new private key, then remove the compromised public anchor in a later client release.

## 3. Cerberus release-signing key

This Ed25519 key signs canonical Cerberus release identity metadata.

- **Private?** Yes, highly sensitive release-manager material.
- **Lives:** offline/release-manager environment only. **Not** on Paper, Velocity, or client installations.
- **Public material:** base64 X.509/SPKI public key belongs in `policy.yml` `cerberus-release-trust.ed25519-public-keys`.
- **Back up?** Yes, offline/encrypted with recovery ownership documented.
- **Rotation:** policy supports multiple public keys. Add the new public key, deploy/reload Guardian, begin signing releases with the new private key, then remove the old public key after the old release population no longer needs acceptance.
- **Compromise:** stop signing with the key, remove its public key from authoritative policy as quickly as your client rollout permits, generate a replacement identity, sign a new Cerberus release, and document the affected release window.

Signed release identity is exact-artifact/compliance hardening. A hostile replaced client can still lie; this is **not remote attestation**.

## Generation and release helper

Use `tools/release-manager.ps1`; it requires an explicit release destination and automatically uses a version-bearing signed Cerberus filename when given an output directory. See `RELEASE_PROCESS.md` for the plain-language release walkthrough and exact commands.
