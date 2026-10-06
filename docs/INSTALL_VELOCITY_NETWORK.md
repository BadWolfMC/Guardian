# Guardian on a Velocity network

For a Velocity network, use Guardian-Velocity as the single network Admission authority and Guardian-Paper on each backend as an authenticated assertion consumer plus optional Protection host. This is also the topology used by BadWolfMC.

## 1. Topology

```text
Java/Fabric/Cerberus or Bedrock
             |
       Velocity + Guardian-Velocity
             |
       authenticated short-lived admission assertion
             |
     Paper backend(s) + Guardian-Paper
```

The full Fabric manifest stays at Guardian-Velocity. Backend Paper does not receive or reevaluate it.

## 2. Proxy installation

1. Place Guardian-Velocity in Velocity's `plugins/` directory.
2. Start once and stop cleanly.
3. Review the generated Guardian Velocity `config.yml` and shared `policy.yml`.
4. Keep `deployment.authority: velocity`.
5. Guardian-Velocity generates `proxy-assertion.key` on first startup. Treat it as private server infrastructure material.
6. Configure optional LuckPerms/Geyser/Floodgate integrations as needed.
7. Start and run `/guardianv status` and `/guardianv validate`.

## 3. Backend installation

On every Guardian-Paper backend:

1. install the Guardian-Paper JAR;
2. set `admission.authority: velocity`;
3. copy the **same** `proxy-assertion.key` from Guardian-Velocity to `plugins/Guardian/proxy-assertion.key`;
4. keep the backend isolated so clients cannot directly connect around Velocity;
5. start and run `/guardian status`.

A matching short fingerprint is shown in status output so operators can compare hosts without printing the secret.

## 4. Command ownership

This is deliberately similar to LuckPerms' backend/proxy distinction:

- `/guardian ...` = this Paper host: Protection, backend assertion health, Paper-local config.
- `/guardianv ...` = Velocity/network Admission authority: policy, authoritative active inspection, artifact catalog.

There is no Paper-to-Velocity command RPC. A moderator connected to any backend can run `/guardianv inspect <player>` because the command itself belongs to Velocity.

## 5. Policy and reload ownership

Velocity owns `policy.yml` in this deployment. Backends do not load/reinterpret the proxy's Admission policy.

- edit network Admission policy at Velocity;
- `/guardianv validate` validates without activation;
- `/guardianv reload` atomically activates a valid candidate;
- backend `/guardian reload` reloads only Paper-local state (for example Protection).

Invalid reload candidates leave the prior runtime snapshot active.

## 6. Guardian server authentication and signed Cerberus

When enabled, install `guardian-server-auth.key` at **Velocity**, because Velocity signs the Cerberus challenge. Do not copy that private key to Paper merely because Paper hosts Guardian too.

Signed Cerberus release trust is also evaluated by Velocity through its authoritative `policy.yml`.

See `KEY_MANAGEMENT.md` for all three trust domains.

## 7. Backend switching

One proxy session receives one authoritative Admission evaluation. Moving Alpha -> Beta -> Gamma does not inventory the client again. Paper validates the trusted assertion for each backend connection; it remains assertion-only.

## 8. Network security

The proxy assertion HMAC is defense in depth, not a substitute for backend isolation. Firewalls/network policy should prevent untrusted direct backend connections.


## 9. Production deployment and rollback

For the recommended proxy-first/backend-by-backend deployment order, backups, rollback, emergency Admission recovery, and key mismatch diagnosis, follow `PRODUCTION_RUNBOOK.md`.
