# Phase 6 handoff — security and adversarial hardening

> The current repository and `Guardian_Cerberus_Authoritative_Project_Plan.md` are authoritative. This handoff starts from the completed Phase 5 operational architecture; it does not reopen Phase 3 policy semantics or Phase 5 authority ownership without evidence of a concrete defect.

## Entering baseline

Phase 5 is complete at `0.1.0-phase5`. The final Java 25 / Gradle 9.7.1 gate is green at **178 tests, 0 failures, 0 errors, 0 skipped**:

```text
guardian-core        64
guardian-paper       44
guardian-protection  20
guardian-protocol    21
guardian-velocity    29
```

BRIDGE-003 and BRIDGE-004 are retired. There are **no active implementation bridges** entering Phase 6.

Live Phase 5 verification passed normal Velocity Fabric/Cerberus Admission, assertion-only backend behavior, authority-aware inspection, host-local atomic validation/reload, artifact authority, generated assertion-key provisioning/mismatch recovery, disconnect cleanup, and in-game `/guardianv` administration through the Velocity permission provider.

## Architecture to preserve

- Guardian-Velocity is BadWolfMC's preferred network Admission authority; Guardian-Paper remains a supported standalone authority.
- Guardian Protection remains Paper-authoritative and independent of Admission.
- Paper behind Velocity verifies trusted proxy Admission and does not receive/re-evaluate the authoritative Fabric manifest.
- One successful Velocity Admission is reused for the proxy connection lifetime/backend switches.
- Geyser/Floodgate origin evidence is resolved before Java brand/Cerberus handling; positively identified Bedrock never enters the Cerberus path.
- Guardian-Paper owns `/guardian`; Guardian-Velocity owns `/guardianv`; no Paper↔Velocity command RPC exists.
- `policy.yml` is shared platform-neutral policy; `artifacts.yml` is identity data; scanning never silently grants policy permission.
- `proxy-assertion.key` is generated only by Velocity and copied unchanged to Velocity-authority Paper backends.
- No NMS, implementation reflection, server Mixins, or packet-library workaround.

## Primary Phase 6 work

Adversarially test and harden:

- replay, duplicate/stale nonce/session, response-after-timeout, disconnect/reconnect, and asynchronous completion races;
- malformed/truncated/oversized payloads, extreme manifest counts/depth, duplicate IDs, fake versions, invalid hashes, and protocol downgrade/capability abuse;
- backend proxy-assertion spoofing, expiry/timestamp boundaries, key mismatch/rotation windows, and direct-backend connection posture;
- PLAY-quarantine escape/interaction attempts and delayed channel-registration boundaries in standalone Paper;
- Geyser/Floodgate disappearance/failure/reconfiguration while preserving the Phase 4 fail-closed origin semantics;
- LuckPerms/profile-provider timeout or outage, especially where a provider-selected profile is stricter than the default, and document the intended fail-safe behavior;
- maximum active-inspection memory/output pressure and large policy-addressable mod lists;
- control characters/log-forging sequences in untrusted version/release metadata and all diagnostic/log output boundaries;
- inspection semantics across policy/catalog reloads so staff can distinguish admission-time decision state from current catalog/config state;
- development-directory and mixed/unknown Fabric origins; and
- malformed configuration and repeated failed reload/validation attempts.

Evaluate the optional signed official Cerberus release identity described in the authoritative plan, but keep its trust claim narrow: it is compliance/tamper hardening, not remote attestation. Likewise, evaluate Guardian→Cerberus server authentication only if it materially improves the privacy/trust model without embedding a client secret.

## Code-quality focus

`PaperAdmissionAdapter` and `GuardianVelocityPlugin` remain the largest transport/lifecycle classes. Phase 5 successfully kept command routing, runtime configuration, key provisioning, and inspection storage in focused components; preserve that boundary. Do not refactor the live-proven transport merely to reduce line counts before the adversarial matrix. Extract further orchestration only when a concrete Phase 6 change would otherwise increase coupling or make a security invariant difficult to test.

## Operator/documentation lessons carried forward

Paper and Velocity command permissions are checked by their own platform permission providers. In an isolated LuckPerms lab, `/lp` grants on Paper do not grant `/guardianv`; use the Velocity LuckPerms instance (for example `/lpv`) or shared LuckPerms storage. Phase 7 must make this explicit in the deployment guide.

The generated `proxy-assertion.key` is private infrastructure material and is now explicitly ignored by Git. It must never be packaged, logged, pasted into configuration, or included in support artifacts.

## Scope boundaries

Do not pull forward:

- Minecraft/Paper/Fabric 26.3 porting (Phase 8);
- persistent historical manifest storage;
- distributed multi-proxy inspection;
- a second Velocity-specific policy schema/evaluator;
- backend manifest forwarding for command convenience; or
- network-global Guardian Protection without a new explicit requirement.

## Closeout expectation

At Phase 6 closeout, rerun the complete Java 25 / Gradle 9.7.1 gate, record the adversarial matrix and any residual trust limitations, update provenance/authoritative documentation, and register any temporary mitigation that must cross into Phase 7 as a new implementation bridge rather than leaving implicit security debt.
