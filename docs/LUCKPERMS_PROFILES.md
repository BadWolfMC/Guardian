# LuckPerms profiles and permission ownership

Guardian's Admission policy is shared, but the **host that owns Admission must resolve its own LuckPerms permissions**.

## Profile selection

Profile permission nodes are:

```text
guardian.admission.profile.<profile-id>
```

Selection precedence is:

1. exact UUID override in `policy.yml`;
2. matching LuckPerms profile permission(s), resolved by deterministic profile priority;
3. `default-profile`.

Admission bypass permissions are policy-scoped; they do not bypass malformed protocol state, replay checks, proxy assertion verification, or other integrity boundaries.

## Standalone Paper

Guardian-Paper is the Admission authority, so profile/bypass grants must be visible to the **Paper** LuckPerms instance/context used by that server.

Use ordinary `/lp` administration for that backend's LuckPerms installation.

## Velocity authority

Guardian-Velocity resolves the network-session Admission profile before backend attachment. Therefore the required profile/bypass grants must be visible to the **Velocity** LuckPerms instance.

Use `/lpv` (or the proxy console) when administering proxy-side LuckPerms. Granting a node only in a backend's separate LuckPerms store does not automatically make Guardian-Velocity see it.

If you use shared LuckPerms storage, still remember that proxy and backend contexts can differ. Verify the assignment from the authority that consumes it rather than assuming a backend `/lp` check proves the proxy view.

## Troubleshooting checklist

If `/guardianv inspect` reports the default profile when you expected another profile:

1. confirm LuckPerms is available in `/guardianv status`;
2. check the permission through the Velocity LuckPerms instance (`/lpv`);
3. verify profile IDs and unique priorities in `policy.yml`;
4. check whether an exact UUID override intentionally wins;
5. avoid using backend/server contextual permissions to create accidental per-backend Admission policy. A proxy-session Admission profile is network-scoped for that connection.

If standalone `/guardian inspect` resolves incorrectly, perform the analogous checks on the Paper host with `/lp`.

## Command administration permissions

Paper commands use `guardian.command.*`. Velocity commands use `guardian.velocity.command.*`. These namespaces are intentionally separate so a backend administrative grant does not imply network-authority administration.
