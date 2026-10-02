# Geyser / Floodgate integration

Guardian treats Bedrock as a first-class client classification, not as a bypass.

## Authority order

Supported Geyser/Floodgate API evidence is evaluated **before** Java brand classification and before any Cerberus requirement. A positively identified Bedrock connection is therefore never challenged for a Fabric manifest.

Username prefixes have no security authority. A Java username beginning with a configured-looking Floodgate prefix remains Java unless the supported API identifies the connection as Bedrock.

## Velocity deployment

For BadWolfMC's preferred topology, Geyser normally lives at the proxy and Guardian-Velocity owns authoritative Bedrock classification. Floodgate can also be present where its API/evidence is needed.

Guardian-Paper behind Velocity does not re-decide Admission. Backend Floodgate evidence is diagnostic/sanity-check evidence against the signed proxy assertion, not an independent policy authority.

If proxy and backend evidence materially disagree, treat the condition as a configuration/security anomaly rather than silently converting the player to another client class.

## Standalone Paper

When Geyser/Floodgate are installed on the standalone host, Guardian-Paper can use their supported APIs before Java/Cerberus handling. If those integrations are absent, only Java classifications are possible; Guardian does not guess Bedrock from usernames.

## Operational checks

Use `/guardian status` or `/guardianv status` to confirm provider availability on the authority that matters. `/guardian[v] inspect <player>` displays active evidence only for the current connection.

Provider absence and provider failure are distinct states. Do not add a prefix fallback to "fix" a failed integration; fix the integration itself.
