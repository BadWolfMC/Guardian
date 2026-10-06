# Contributing

Small, focused fixes and documentation improvements are welcome. For substantial architecture or protocol changes, open an issue first so the change can be checked against Guardian's Admission/Protection boundaries and threat model.

## Development requirements

- Java 25
- repository Gradle wrapper (Gradle 9.7.1)
- Minecraft/Paper/Fabric 26.2 until the separately gated platform-port work changes that target

Run the full gate before proposing a code change:

```text
./gradlew clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Do not commit build outputs, Gradle state, release staging directories, private keys, third-party approved mod JARs, or administrator-local configuration.

Changes must preserve the documented privacy/security boundaries: no historical Fabric-manifest persistence, no client-held long-term shared secret, and no NMS/CraftBukkit implementation reflection/server Mixins/ProtocolLib/PacketEvents workaround for Admission.

By contributing, you agree that your contribution is provided under the repository's GPL-3.0-only license.
