# Phase 4 verification — Geyser/Floodgate productionization

Date: 2026-09-27
Project version: `0.1.0-phase4`


## Final closeout result — 2026-09-28

**PASS.** The operator-supplied closeout repository retains the Java 25 / Gradle 9.7.1 JUnit XML results for **141 tests, 0 failures, 0 errors, 0 skipped**:

- Core: 57
- Paper: 34
- Protection: 20
- Protocol: 21
- Velocity: 9

The focused live matrix is also complete:

1. **Velocity + real Bedrock + Geyser/Floodgate + backend Floodgate — PASS.** Guardian-Velocity observed `geyser=BEDROCK`, `floodgate=BEDROCK`, selected `classification=BEDROCK`, applied shared Bedrock `ALLOW`, sent the trusted admission assertion, and Guardian-Paper accepted through `PROXY_ADMISSION_VERIFIED` with an agreeing backend Floodgate sanity check. No Cerberus challenge occurred.
2. **Velocity + Java Fabric/Cerberus — PASS.** The connection remained `JAVA_FABRIC`, shared policy required Cerberus, the 166-entry manifest was verified, Velocity produced `CERBERUS_VERIFIED`, and Paper accepted only the trusted proxy assertion without backend re-attestation.
3. **Standalone Paper + real Bedrock / `clients.bedrock: ALLOW` — PASS.** Supported origin evidence classified `BEDROCK` before Java brand handling and admitted through `BEDROCK_POLICY` without Cerberus.
4. **Standalone Paper + real Bedrock / `clients.bedrock: DENY` — PASS.** The same Bedrock connection was denied through ordinary shared policy as `CLIENT_DENIED`; no bypass or Cerberus fallback occurred.

The Bedrock client action is owned by the shared `policy.yml`, not Paper-local `config.yml`. The packaged `config.yml` already documents that boundary and contains no stale Bedrock allow/deny setting.

These results satisfy the Phase 4 automated and live acceptance criteria. BRIDGE-005 is retired.

## Automated gate

Run from the repository root with Java 25 and the repository Gradle 9.7.1 wrapper:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

The Phase 3 baseline was 134 tests. Phase 4 adds seven net tests, for an expected source inventory of **141 tests**:

- Core: 57
- Paper: 34
- Protection: 20
- Protocol: 21
- Velocity: 9

Final closeout requires 0 failures, 0 errors, and 0 skipped tests.

## Focused automated acceptance

The suite must prove:

- positive Geyser/Floodgate evidence resolves Bedrock before Java brand handling;
- provider absence is distinct from provider query failure;
- provider failure with no positive Bedrock evidence becomes indeterminate/fail-closed;
- explicit provider disagreement remains diagnosable while positive evidence prevents Cerberus;
- standalone Paper has optional supported Geyser and Floodgate integrations;
- configurable shared Bedrock policy can allow or deny Bedrock;
- Velocity-mode backend Floodgate comparison is diagnostic-only and cannot become a second policy authority;
- no username-prefix trust is introduced;
- existing Phase 3 shared-policy architecture remains intact; and
- Guardian Protection tests remain unchanged/green.

## Minimal live tests worth performing

Only the paths touched materially by Phase 4 need another live pass.

### 1. Standalone Paper + Geyser/Floodgate + real Bedrock client — new path, mandatory

Run Guardian-Paper as standalone authority with Geyser/Floodgate installed on the Paper server.

Expected:

- supported API evidence identifies the connection as `BEDROCK` before Java brand classification;
- the configured `clients.bedrock` policy is used;
- no Cerberus challenge is sent;
- default `ALLOW` admits normally;
- a temporary `clients.bedrock: DENY` change denies through ordinary Guardian policy, proving Bedrock is not a bypass.

### 2. Velocity + Geyser/Floodgate + backend Floodgate + real Bedrock client — focused regression

Use the normal BadWolfMC-style topology. For backend Floodgate API visibility, retain Floodgate's documented `send-floodgate-data: true` and matching `key.pem` configuration.

Expected:

- Guardian-Velocity classifies `BEDROCK` from supported API evidence;
- no Cerberus challenge occurs;
- the proxy makes the shared-policy decision and sends the trusted admission assertion;
- Guardian-Paper accepts through `PROXY_ADMISSION_VERIFIED`;
- backend Floodgate sanity evidence agrees (or, if deliberately made inconsistent for a test, produces a prominent diagnostic without becoming a second policy decision).

### 3. One ordinary Java Fabric + Cerberus admission regression

Use either the standalone or Velocity topology already used for Phase 3 verification; Velocity is preferred because it exercises the production network path.

Expected:

- the connection remains `JAVA_FABRIC`;
- normal Cerberus/policy requirements still apply;
- approved manifest reaches `CERBERUS_VERIFIED` and admission succeeds;
- Bedrock integration changes do not suppress or bypass Java/Fabric enforcement.

Do not spend operator time manually simulating provider API exceptions, every Geyser/Floodgate disagreement permutation, or username-prefix spoofing. Those semantics are deterministic and covered by focused automated tests; manually manufacturing integration failures is more likely to test the lab setup than Guardian.

## Scope regression checks

No Phase 4 change should alter:

- Guardian Protection execution/visibility behavior;
- protocol v1 or Cerberus manifest/hash semantics;
- Phase 3 policy schema/evaluator semantics other than exercising the already-existing configurable Bedrock class;
- trusted proxy assertion format;
- Phase 5 proxy-secret provisioning;
- Phase 5 Velocity timing/configuration UX; or
- Phase 6 hostile-client work.
