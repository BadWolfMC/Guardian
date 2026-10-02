# Consolidated security and threat model

## Security goals

Guardian aims to:

- make deterministic allow/deny decisions for supported client classes;
- require cooperative Fabric clients to report a bounded canonical Loader-known manifest when policy requires Cerberus;
- validate protocol/session freshness, limits, structure, versions, hashes, and policy;
- keep the full manifest only at the active Admission authority and only for the active connection;
- authenticate Velocity -> Paper Admission assertions on controlled infrastructure;
- optionally authenticate Guardian to Cerberus before manifest disclosure;
- optionally require an official signed Cerberus release identity;
- enforce selected Paper player-command rules independently through Guardian Protection.

## Explicit non-goals

Guardian/Cerberus is **not** a general anti-cheat and does not provide hostile-client remote attestation. A player controls the client JVM and can replace/patch Cerberus, Fabric Loader, networking code, or the reported environment. Java agents/native cheats outside Fabric Loader's model are also outside the manifest claim.

Artifact SHA-256 means "the cooperating Cerberus client reported these exact bytes." Signed Cerberus provenance means "the reported canonical Cerberus artifact identity was signed by an approved release key." Neither proves that hostile client code reported truthfully.

## Trust boundaries

### Admission vs Protection

Admission and Protection are independent domains. Protection success/failure does not establish client trust; Admission does not rely on command filtering.

### Velocity authority

On the BadWolfMC network, Guardian-Velocity owns Admission policy, full manifest inspection, and signed-Cerberus evaluation. Paper receives only a bounded authenticated final assertion. Paper must not gain the full manifest merely for command convenience.

Security-sensitive client Guardian/Cerberus channels are consumed at the proxy rather than blindly forwarded to Paper.

### Bedrock

Supported Geyser/Floodgate API evidence precedes Java classification. Username prefixes are never trusted as Bedrock proof. Bedrock receives no Cerberus challenge.

### Active inspection

Inspection state is bounded, memory-only, and owned by the exact active connection. It is removed on disconnect/replacement and is never a historical manifest database.

## Three cryptographic domains

See `KEY_MANAGEMENT.md`. The proxy HMAC, Guardian server-auth Ed25519 key, and Cerberus release-signing Ed25519 key have distinct purposes and storage/rotation rules.

## Fail-closed behavior

Examples that deny or prevent progression include malformed/oversized protocol payloads, replay/session mismatch, incompatible protocol, required Cerberus absence, timeout after presence, invalid policy manifests, failed trusted proxy assertions, required signed-release failure, and required Guardian server-authentication not being configured.

A wrong/untrusted Guardian server-auth key is intentionally special from a privacy perspective: stock Cerberus rejects the challenge and **withholds its manifest**. The server later observes a timeout. The player-facing timeout text tells users to honor an "untrusted Guardian server" warning from Cerberus and contact staff rather than weakening this boundary with a manifest-bearing fallback.

## Filesystem/admin-input posture

Administrator YAML, key files, artifact imports, and JAR resources are treated as bounded inputs. Sensitive readers use stable pre/post attributes and no-follow semantics on ordinary filesystem paths. JAR trust-anchor resources use provider-aware read-only access because JDK ZIPFS does not accept `LinkOption.NOFOLLOW_LINKS` as a channel-open option.

Artifact import never classloads, executes, installs, or extracts supplied mod JARs.

## Protocol/platform prohibitions

Guardian intentionally avoids NMS/CraftBukkit implementation reflection, server Mixins, ProtocolLib/PacketEvents, and private-packet workarounds. The standalone hybrid CONFIGURATION + bounded PLAY quarantine and Velocity awaited-CONFIGURATION paths are built from supported APIs.

## Residual risks operators must own

- direct backend exposure can undermine proxy topology assumptions;
- compromised server/key storage can defeat the corresponding server-side trust domain;
- social engineering can persuade users to ignore Cerberus warnings;
- misconfigured profiles/permissions can intentionally or accidentally permit clients/mods;
- a hostile modified client may fabricate a compliant report;
- Guardian Protection is not a substitute for permission configuration in the plugins whose commands it filters.

Release decisions should be based on these documented limits, not on claims that Guardian can prove the state of hostile player hardware.
