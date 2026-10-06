# Security policy

Guardian/Cerberus handles connection admission, client-reported environment data, proxy assertions, and several private-key domains. Please do not publish exploit details, private keys, manifests containing sensitive operational information, or a working bypass in a public issue before maintainers have had a chance to review it.

## Supported versions

Security fixes are made against the current maintained release line. Pre-release/RC builds may change before the first stable release and should not be treated as a long-term compatibility promise.

## Reporting a vulnerability

Prefer GitHub's **Private vulnerability reporting / Report a vulnerability** feature on this repository when it is available. Include:

- affected Guardian/Cerberus version and Minecraft/platform version;
- deployment mode (standalone Paper or Velocity authority);
- a concise reproduction;
- expected versus observed security boundary; and
- whether the report involves key disclosure, manifest disclosure, assertion forgery, admission bypass, or Protection execution bypass.

If private vulnerability reporting is unavailable, contact the repository maintainers privately through the repository owner's published contact channel before sharing sensitive details publicly.

For ordinary configuration errors, crashes, documentation problems, or non-sensitive bugs, use the public issue tracker.

## Security boundary

Guardian/Cerberus is a policy-compliance system, not hostile-client remote attestation. A sufficiently modified client can misrepresent its environment. The supported claims and residual risks are documented in `docs/SECURITY_THREAT_MODEL.md`.
