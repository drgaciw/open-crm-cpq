---
status: green
id: it1-java25-docker-front
wave: IT-1
pr: IT-1
---

# it1-java25-docker-front

## Summary

IT-1 (BE evidence, docs-only): first local Temurin-25 + Docker **full `verify`** of crm / quote / gateway after VER-1 (`-Punit-tests`) left ITs and `jacoco:check` unrun. Tip `origin/main` @ `10093e56` (plan `#526`). **BUILD SUCCESS**. Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. JaCoCo line **0.80** not lowered. JAVA_HOME **Temurin 25.0.4.1 LTS** (never 27). No `-Punit-tests`. No `-Werror`. No `-nowarn`. No `-T1C`. Did **not** chmod `docker.sock`. Did **not** start IT-2 / WARN-FE / UAT-1 / WARN-BE. Did **not** edit `docs/forge/00-orchestrator-status.md`. No NgRx. No Activities.

Red (VER-1): 0 IT classes on tip (`verify -Punit-tests`; failsafe empty; `jacoco.skip` from that profile). Green (this run): `newgrp docker` + JAVA_HOME 25 + `./mvnw -B clean verify -pl services/crm-service,services/quote-service,services/gateway-service -am` → **BUILD SUCCESS**; crm ITs executed; crm `jacoco:check` **PASS** (line **1007/1171 ≈ 0.860** ≥ 0.80).

## GitNexus

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/be-it1` (`test/forge-it1-java25-docker`)  
Docs-only impl note — no indexed code symbols. Plan 38: GitNexus index stale vs tip — **skip analyze**. UNKNOWN ≠ unused; `rg` confirm before commit.

```bash
rg -n 'APPROVALS_LEGACY_LOCAL_READ' backend/services/quote-service/src/main/resources/application.yml
rg -n '<minimum>0.80</minimum>' backend/services/crm-service/pom.xml
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all --repo .
```

Pre-commit (this worktree, file staged; `--repo /home/username01/Projects/open-crm-cpq` with cwd = this worktree so `git diff` sees the linked index): **Diff touched 1 file(s) but no indexed symbols overlap those hunks — not a clean tree.** No `partial`/`truncated`. Checkout confirmed: worktree `docs/forge/impl/it1-java25-docker-front.md` only (primary `main` working tree was not the diff). Docs-only hunk after `rg` confirm.

## Toolchain

```text
JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS, mixed mode, sharing)
```

`newgrp docker` wrapped `./mvnw` (primary gid **docker**: `uid=1000(username01) gid=967(docker)`). Did **not** chmod `docker.sock`. javac `[debug parameters release 25]`. Spring Boot plugin `spring-boot:4.0.6:repackage`. Maven 3.9.9 wrapper.

### Docker engine

```text
Client: 29.7.2  API 1.55
Server Engine: 29.7.2  API 1.55  overlayfs  Omarchy
containerd v2.3.5  runc 1.5.1
Testcontainers connected: Server Version 29.7.2 / API 1.55 / Total Memory 15865 MB
Testcontainers version: 1.21.4
```

IT-2 was also running Testcontainers on this host (random host ports). This slice used `postgres:16-alpine` JDBC `localhost:32784`. Host did not choke; no serialize.

## Files

| Path | Change |
|------|--------|
| `docs/forge/impl/it1-java25-docker-front.md` | This note |

## Command

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B clean verify -pl services/crm-service,services/quote-service,services/gateway-service -am
EOF
```

Wall: 2026-09-15T08:32:32 → 08:40:02-05:00. Maven **07:23 min**. Tip at run: `10093e56`. **No** `-Punit-tests` ⇒ `excludedGroups` unset, `jacoco.skip` unset. Failsafe reports: **none** (ITs are surefire `@Tag("integration")`, not failsafe).

## Reactor (`-am` downstream libs)

| Module | Tests run | Failures | Errors | Skipped | Status | Wall |
|--------|----------:|---------:|-------:|--------:|--------|------|
| Open CRM Backend (pom) | — | — | — | — | SUCCESS | 0.355 s |
| Common Library | 86 | 0 | 0 | 0 | SUCCESS | 53.986 s |
| Security Library | 13 | 0 | 0 | 0 | SUCCESS | 20.048 s |
| CRM Service | **202** | 0 | 0 | 0 | SUCCESS | 04:08 min |
| Gateway Service | **54** | 0 | 0 | 0 | SUCCESS | 42.191 s |
| Quote Service | **31** | 0 | 0 | 0 | SUCCESS | 01:16 min |
| **Reactor** | **386** | **0** | **0** | **0** | **BUILD SUCCESS** | **07:23 min** |

`rules-engine` is not on this `-am` graph (crm/quote/gateway do not depend on it).

## Per-class IT execution (crm)

Plan named “crm 8 `@Tag("integration")` classes”. Tip corpus:

| Class | `@Tag("integration")` | Runnable | Tests | Result |
|-------|:---------------------:|:--------:|------:|--------|
| `BaseIntegrationTest` | yes (`docker`+`integration`) | **abstract** (not a test class) | — | shared TC postgres:16-alpine + redis:7-alpine |
| `LeadToDealLifecycleTest` | yes | yes | 3 | PASS |
| `ContactControllerIntegrationTest` | yes | yes | 12 | PASS |
| `DealControllerIntegrationTest` | yes | yes | 18 | PASS |
| `QuoteAcceptedRedisIntegrationTest` | yes (`docker`+`integration`) | yes | 2 | PASS |
| `LeadControllerIntegrationTest` | yes | yes | 15 | PASS |
| `OrganizationControllerIntegrationTest` | yes | yes | 9 | PASS |
| `BaseIntegrationTestUrlTest` | **no** (unit helpers) | yes | 2 | PASS |

**7** tagged sources (1 abstract + **6 concrete, all executed**). The 8th related class is `BaseIntegrationTestUrlTest` (JDBC URL helpers; not tagged `integration`; still ran under full `verify`). Concrete IT tests **59** + URL helpers **2**.

crm surefire total **202** = VER-1 units **139** + ITs **59** + (this tip’s XML also includes the same unit classes; 139+59=198; remaining 4 are nested DisplayName group accounting vs VER-1 XML). Failures **0**.

## Quote ITs

Quote-service has Testcontainers deps (`postgresql` + `junit-jupiter`) but **zero** `@Tag("integration")` / `@SpringBootTest` / `PostgreSQLContainer` tests on tip. Full `verify` executed **9 unit classes / 31 tests** (same as VER-1 quote slice):

| Class / DisplayName | Tests | Result |
|---------------------|------:|--------|
| ApprovalService S2C-12 single-writer BFF | 4 | PASS |
| ApprovalRoutingClient | 4 | PASS |
| PricingServiceClient typed execute/simulate (Wave J1) | 3 | PASS |
| ProductServiceClient S2D-3a gateway validate | 3 | PASS |
| `QuoteAcceptedCrmMessageFactoryTest` | 1 | PASS |
| `QuoteAcceptedKafkaEventFactoryTest` | 1 | PASS |
| QuoteService.calculate typed pricing apply (Wave J1) | 6 | PASS |
| QuoteService.clone copies line items (Wave M3) | 5 | PASS |
| QuoteService.submitForReview S2D-3a preflight | 4 | PASS |

No quote Testcontainers/Ryuk activity (expected).

## Gateway units

Gateway has **no** Testcontainers dep (plan: unit-only by design). **54** tests, 0 fail:

| Class / DisplayName | Tests | Result |
|---------------------|------:|--------|
| `CsrfProtectionFilterTest` | 12 | PASS |
| F3/F4 gateway validate-before-configuration/** route predicate | 4 | PASS |
| `CpqGatewayRoutesTest` | 38 | PASS |

## crm `jacoco:check` ≥ 0.80 PASS

- Floor **0.80** unchanged (`crm-service/pom.xml` `<minimum>0.80</minimum>`). Plugin **0.8.14**.
- `jacoco:0.8.14:prepare-agent` set `argLine` (not skipped). Log has **no** `jacoco.skip` / `Skipping JaCoCo`.
- `jacoco:0.8.14:report` (test phase): analyzed **30** classes.
- `jacoco:0.8.14:check` (verify phase): **All coverage checks have been met.**
- Line coverage **1007/1171 ≈ 0.860** (csv). Instruction **4385/5101 ≈ 0.860**. Branch **259/409 ≈ 0.633** (not gated). HTML: 85% instructions / 164 missed of 1,171 lines.

## Ryuk / Testcontainers warning catalog (WARN-IT fold)

Documented only — **did not** set `testcontainers.reuse.enabled=true`; **rg** `testcontainers.reuse` in `backend/` = **zero**. No committed `~/.testcontainers.properties` change.

| Signal | Level | Quote / notes |
|--------|-------|----------------|
| Ryuk start | INFO | `Ryuk started - will monitor and terminate Testcontainers containers on JVM exit` (`testcontainers/ryuk:0.12.0`, 5.05 s) |
| Reuse warning | **none observed** | No `reuse` log line. Plan expected Ryuk/reuse noise on first full Docker run; this host already had images, so Ryuk was INFO-only. |
| Docker socket strategy | INFO | `Loaded org.testcontainers.dockerclient.UnixSocketClientProviderStrategy from ~/.testcontainers.properties` — host file, not in repo |
| RegistryAuthLocator | INFO | `Failure when attempting to lookup auth config` for `testcontainers/ryuk:0.12.0` — `~/.docker/config.json` missing; fallback docker-java. Not a test failure. |
| Images | INFO | `postgres:16-alpine` (12.86 s, JDBC `:32784`); `redis:7-alpine` |

Third-party JDK 25 runtime noise (not in-repo; WARN-BE baseline, not fixed here):

| Signal | Source |
|--------|--------|
| Jansi native-access | `org.fusesource.jansi.internal.JansiLoader` / Maven 3.9.9 `jansi-2.4.1.jar` — `Use --enable-native-access=ALL-UNNAMED` |
| Guava `sun.misc.Unsafe` | `com.google.common.util.concurrent.AbstractFuture$UnsafeAtomicHelper` / `guava-33.2.1-jre.jar` |
| Mockito/ByteBuddy dynamic agent | `byte-buddy-agent-1.17.8.jar` — `Mockito is currently self-attaching`; `-XX:+EnableDynamicAgentLoading` |
| Netty native-access | `io.netty.util.internal.NativeLibraryUtil` / `netty-common-4.2.12.Final` (crm IT JVM) |

In-repo javac notes (catalog only; WARN-BE): `OutboxRelayPublisherTest` unchecked; `quote ... GlobalExceptionHandler` deprecated API; `QuoteServiceSubmitForReviewTest` unchecked. No `-Werror` / `-nowarn` applied.

## Dual-read / holds

```yaml
# backend/services/quote-service/src/main/resources/application.yml:89
legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

YAML/env **untouched**. Default still **true**.

- NgRx **held**; Activities API **not invented**; CI `#238` / Kafka `#241` **not started**
- **R2** price-book list-row delete **held**; subscriptions FE **held**; GLM-5 **not runtime**
- Boot **4.0.6**; JaCoCo floor **0.80** not lowered; JAVA_HOME **25.0.4.1 LTS** — never 27
- Did **not** edit `docs/forge/00-orchestrator-status.md`
- Did **not** start IT-2 / WARN-FE / UAT-1 / WARN-BE; no FIX-* (verify GREEN)

## Acceptance

- [x] `"$JAVA_HOME/bin/java" -version` → **25.0.4.1 LTS** (not 27)
- [x] Docker engine **29.7.2**; `newgrp docker`; did **not** chmod `docker.sock`
- [x] `./mvnw -B clean verify -pl services/crm-service,services/quote-service,services/gateway-service -am` **BUILD SUCCESS**
- [x] No `-Punit-tests`; crm ITs executed (6 concrete `@Tag("integration")` + abstract base + UrlTest helper)
- [x] Quote tests executed (31 units; no tagged ITs on tip)
- [x] Gateway units **54** PASS (no TC)
- [x] crm `jacoco:check` **All coverage checks have been met** (line ≈ **0.860** ≥ 0.80)
- [x] `-am` libs: common 86 + security 13
- [x] Ryuk/TC catalog recorded; reuse **not** enabled in repo
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] Exclusive file: this impl note only
