---
status: green
id: warnbe-java25-catalog
wave: WARN-BE
pr: WARN-BE
---

# warnbe-java25-catalog

## Summary

WARN-BE (BE, **docs-only**): catalog every javac / Maven / plugin / ByteBuddy / Mockito / deprecation / Ryuk warning captured on Temurin **25.0.4.1 LTS** by IT-1 `#528` and IT-2 `#527` (`origin/main` tip `34007835`). Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. JaCoCo line **0.80** not lowered. No `-Werror`. No `-nowarn`. No `-T1C`. No `--add-opens`. No `testcontainers.reuse.enabled`. Did **not** edit `docs/forge/00-orchestrator-status.md`. Did **not** implement FIX-* in this PR.

**RED:** IT-1 + IT-2 notes (and their Maven logs) mention warnings; no in-repo catalog existed on tip.

**GREEN:** this note lists each warning with owner (us vs third-party vs INFO-only) and recommended action. In-repo-fixable items are recorded as **FIX-BE-*** with exclusive files. This PR does **not** change `backend/**` sources or poms.

## GitNexus

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/be-warn` (`fix/forge-warnbe-java25`)  
Index: stale vs tip (plan 38: **skip analyze**). UNKNOWN ≠ unused; `rg` confirm before commit.

Docs-only impl note — no indexed code symbols. Pre-edit impact on `warnbe-java25-catalog`: **UNKNOWN** (`target-not-found`). Confirmed with `rg` + `git diff`: dual-read YAML and Jacoco `<minimum>0.80</minimum>` hunks are empty. No source/pom edits.

```bash
rg -n 'legacy-local-read-enabled' backend/services/quote-service/src/main/resources/application.yml
rg -n '<minimum>0.80</minimum>' backend/services/crm-service/pom.xml backend/services/pricing-service/pom.xml
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq
```

## Inputs (on origin/main)

| Source | PR | Evidence |
|--------|----|----------|
| `docs/forge/impl/it1-java25-docker-front.md` | IT-1 `#528` | crm / quote / gateway `./mvnw -B clean verify -pl … -am` **BUILD SUCCESS**; Maven log `call-b5cb8c83-…-32.log` |
| `docs/forge/impl/it2-java25-docker-back.md` | IT-2 `#527` | remaining CPQ services same shape; Maven log `.grok-it2/mvn-verify.log` |

Tip at catalog: `34007835` (IT-1 `#528` + IT-2 `#527` on `main`). JAVA_HOME **Temurin 25.0.4.1 LTS** (never 27).

## Files

| Path | Change |
|------|--------|
| `docs/forge/impl/warnbe-java25-catalog.md` | This note |

## Classification key

| Class | Meaning | Action this PR |
|-------|---------|----------------|
| **in-repo-fixable** | Our source emits javac `[WARNING]` or INFO “uses deprecated/unchecked” | Record **FIX-BE-*** exclusive files. **Do not implement here.** |
| **third-party** | JDK 25 runtime noise from Maven / Guava / Mockito / ByteBuddy / Netty / Protobuf | Document + upstream link. No `--add-opens`, no `-XX:+EnableDynamicAgentLoading`, no surefire agent until a test **fails**. |
| **INFO-only** | Testcontainers Ryuk / Docker lookup / image pull. Not a warning to fix. | Document. Do **not** set `testcontainers.reuse.enabled=true`. |

Maven plugin `[WARNING]` lines in either IT log: **none** (compiler / surefire / jacoco 0.8.14 / spring-boot:4.0.6:repackage are INFO). JaCoCo floor **0.80** met on both slices.

---

## In-repo-fixable (FIX-BE-*)

### FIX-BE-1 — `MappingJackson2HttpMessageConverter` deprecated **for removal**

**Owner:** us (test sources). **Class:** in-repo-fixable. **IT-2** `[WARNING]` (8 diagnostics, 2 per file: type use + constructor). Production compile of this slice had **zero** `[WARNING]`.

| File:line | Quote |
|-----------|-------|
| `backend/services/configuration-engine/src/test/java/com/opencrm/configuration/session/ConfigurationSessionControllerTest.java:63` | `org.springframework.http.converter.json.MappingJackson2HttpMessageConverter … has been deprecated and marked for removal` (cols 9 and 61) |
| `backend/services/approval-routing/src/test/java/com/opencrm/approval/controller/ApprovalControllerTest.java:70` | same |
| `backend/services/document-generation/src/test/java/com/opencrm/document/controller/DocumentControllerTest.java:60` | same |
| `backend/services/contract-asset-management/src/test/java/com/opencrm/contract/controller/SubscriptionControllerTest.java:67` | same |

**Recommended action:** replace with Spring Framework 7 `org.springframework.http.converter.json.JacksonJsonHttpMessageConverter` in standalone `MockMvc` setup. Tests only; no API contract change.

**Exclusive files (follow-up PR, not this one):** the four test files above.

### FIX-BE-2 — `HttpStatus.UNPROCESSABLE_ENTITY` deprecated API

**Owner:** us (main + one test). **Class:** in-repo-fixable. javac INFO (not `[WARNING]`; parent compiler does not pass `-Xlint:deprecation`). Same constant in all listed files (`ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)`). Spring Framework 7 replacement: `HttpStatus.UNPROCESSABLE_CONTENT` (RFC 9110).

| File:line | Slice |
|-----------|-------|
| `backend/services/crm-service/src/main/java/com/opencrm/crm/config/GlobalExceptionHandler.java:106,133` | IT-1 log (IT-1 note abbreviated to quote only) |
| `backend/services/quote-service/src/main/java/com/opencrm/quote/config/GlobalExceptionHandler.java:61,72` | IT-1 note + log |
| `backend/services/pricing-service/src/main/java/com/opencrm/pricing/config/GlobalExceptionHandler.java:106,133` | IT-2 log |
| `backend/services/pricing-service/src/test/java/com/opencrm/pricing/config/GlobalExceptionHandlerTest.java:60,74` | IT-2 log “Some input files use or override a deprecated API” |
| `backend/services/pricebook-service/src/main/java/com/opencrm/pricebook/config/GlobalExceptionHandler.java:61,73` | IT-2 log |
| `backend/services/product-service/src/main/java/com/opencrm/product/config/GlobalExceptionHandler.java:61,72` | IT-2 log |

HTTP status **422** is unchanged; this is a constant rename. Handlers in configuration-engine / approval-routing / document-generation / contract-asset do **not** use `UNPROCESSABLE_ENTITY` and did not emit this INFO.

**Exclusive files (follow-up PR, not this one):** the six files above.

### FIX-BE-3 — `OutboxRelayPublisherTest` unchecked

**Owner:** us (test). **Class:** in-repo-fixable. javac INFO: “uses unchecked or unsafe operations” / “Recompile with `-Xlint:unchecked` for details”.

| File | Likely sites (no `-Xlint` on the IT runs) |
|------|------------------------------------------|
| `backend/libs/common/src/test/java/com/opencrm/common/event/OutboxRelayPublisherTest.java` | `mock(SendResult.class)` (:62); raw `any()` on `saveAll` / `deleteAll` (:52, :136) |

IT-1 and IT-2 both compiled this test (`-am` common).

**Recommended action:** type the Mockito matchers (`anyList()`, `mock(SendResult.class)` → `ArgumentMatchers.<SendResult<String,String>>any()` / typed mock). No production change.

**Exclusive files:** `backend/libs/common/src/test/java/com/opencrm/common/event/OutboxRelayPublisherTest.java` only.

### FIX-BE-4 — `QuoteServiceSubmitForReviewTest` unchecked

**Owner:** us (test). **Class:** in-repo-fixable. IT-1 note + log.

| File:line | Likely site |
|-----------|-------------|
| `backend/services/quote-service/src/test/java/com/opencrm/quote/quote/QuoteServiceSubmitForReviewTest.java:144` | `ArgumentCaptor.forClass(List.class)` (raw `List`) |

**Recommended action:** `ArgumentCaptor<List<Long>>` via `@Captor` or `ArgumentCaptor.captor()`. No production change.

**Exclusive files:** that test file only.

### Not observed on IT-1 / IT-2 Temurin-25 logs (do not spawn FIX-*)

- CRM `ContactControllerIntegrationTest` deprecated API — listed on the **Java 21** `tdd-be-clean-2026-09-15` note; **absent** from IT-1 javac INFO on 25.
- Gateway production/test compile: **no** deprecated / unchecked INFO.
- Parent `maven-compiler-plugin` has no `-Werror` / `-nowarn` / `-Xlint:none` today — leave it that way.

---

## Third-party (document only)

Tests **EXIT=0**. Do **not** add `--add-opens`, `-XX:+EnableDynamicAgentLoading`, Maven `MAVEN_OPTS --enable-native-access`, or a committed surefire Mockito agent in this wave (plan: flags only if a test fails; JDK1/IT precedent: none needed).

| ID | Signal | Where | Upstream |
|----|--------|-------|----------|
| TP-JANSI | `java.lang.System::load` by `org.fusesource.jansi.internal.JansiLoader` / Maven 3.9.9 `jansi-2.4.1.jar`; `Use --enable-native-access=ALL-UNNAMED` | Maven JVM (IT-1 + IT-2, before reactor) | [Jansi](https://github.com/fusesource/jansi); Maven wrapper 3.9.9 ships it. Wait for a Maven distro that enables native-access or drops Jansi native load. |
| TP-GUAVA | `sun.misc.Unsafe::objectFieldOffset` by `com.google.common.util.concurrent.AbstractFuture$UnsafeAtomicHelper` / `guava-33.2.1-jre.jar` (terminally deprecated) | Maven JVM (IT-1 + IT-2) | [Guava](https://github.com/google/guava); bundled inside the Maven 3.9.9 distro, not our BOM. |
| TP-MOCKITO | `Mockito is currently self-attaching to enable the inline-mock-maker` + `byte-buddy-agent-1.17.8.jar` dynamic agent; `Dynamic loading of agents will be disallowed by default in a future release` | Test JVMs | [Mockito 0.3 mockito-agent](https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3). IT-1: **3** load sites (common, crm, quote). IT-2: **9** load sites (one per Mockito module on that `-am` graph). |
| TP-NETTY | `java.lang.System::loadLibrary` by `io.netty.util.internal.NativeLibraryUtil` / `netty-common-4.2.12.Final` | crm IT JVM (IT-1 only) | [Netty](https://github.com/netty/netty) native-access under JDK 25. Boot 4.0.6 managed version. |
| TP-PROTOBUF | `sun.misc.Unsafe::arrayBaseOffset` by `com.google.protobuf.UnsafeUtil$MemoryAccessor` / `protobuf-java-3.25.1` | pricing gRPC tests (IT-2) | [protobuf-java](https://github.com/protocolbuffers/protobuf). Transitive; not a source edit. |

---

## INFO-only (Ryuk / Testcontainers / Docker)

Documented in IT-1 WARN-IT fold and IT-2 absence. **Did not** set `testcontainers.reuse.enabled=true` (`rg` `testcontainers.reuse` in `backend/` = **zero**). No committed `~/.testcontainers.properties`.

| Signal | Level | Slice | Quote / notes |
|--------|-------|-------|----------------|
| Ryuk start | INFO | IT-1 crm | `Ryuk started - will monitor and terminate Testcontainers containers on JVM exit` (`testcontainers/ryuk:0.12.0`) |
| Reuse warning | **none** | IT-1 | No `reuse` log line. Host already had images. |
| Ryuk / reuse | **0 lines** | IT-2 | No container start (`@Container` / `PostgreSQLContainer` usage in those seven services: **0**). Absence is not a suppressed log. |
| Docker socket strategy | INFO | IT-1 | `Loaded org.testcontainers.dockerclient.UnixSocketClientProviderStrategy from ~/.testcontainers.properties` — host file, not in repo |
| RegistryAuthLocator | INFO | IT-1 | `Failure when attempting to lookup auth config` for `testcontainers/ryuk:0.12.0` — `~/.docker/config.json` missing; fallback docker-java. Not a test failure. |
| Images | INFO | IT-1 | `postgres:16-alpine`, `redis:7-alpine` |

---

## Dual-read / holds

```yaml
# backend/services/quote-service/src/main/resources/application.yml:89
legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

YAML/env **untouched**. Default still **true**. Jacoco `<minimum>0.80</minimum>` **untouched** in crm + pricing poms.

- NgRx **held**; Activities API **not invented**; CI `#238` / Kafka `#241` **not started**
- **R2** price-book list-row delete **held**; subscriptions FE **held**; GLM-5 **not runtime**
- Boot **4.0.6**; JAVA_HOME **25.0.4.1 LTS** — never 27
- Did **not** edit `docs/forge/00-orchestrator-status.md`
- Did **not** steal FE panes or w8 `arch-pi`
- Did **not** implement FIX-BE-1..4 in this PR

## TDD

RED: IT-1 note names OutboxRelay / quote `GlobalExceptionHandler` / `QuoteServiceSubmitForReviewTest` plus Jansi / Guava / ByteBuddy / Netty / Ryuk INFO; IT-2 note names four `MappingJackson2HttpMessageConverter` `[WARNING]`s plus Jansi / Guava / ByteBuddy×9 / Protobuf and zero Ryuk.

GREEN: every named signal is in the tables above with owner + action; in-repo items have FIX-BE-* exclusive files; this tree changes only this catalog.

```bash
rg -n 'FIX-BE-1|FIX-BE-2|FIX-BE-3|FIX-BE-4|TP-JANSI|TP-GUAVA|TP-MOCKITO|TP-NETTY|TP-PROTOBUF|Ryuk' \
  docs/forge/impl/warnbe-java25-catalog.md
rg -n 'legacy-local-read-enabled' backend/services/quote-service/src/main/resources/application.yml
git diff --name-only   # this catalog only
```

## Acceptance

- [x] Catalog sourced from IT-1 `#528` + IT-2 `#527` notes and their Temurin-25 Maven logs
- [x] Every javac / Maven / plugin / ByteBuddy / Mockito / deprecation / Ryuk signal classified (in-repo vs third-party vs INFO-only)
- [x] In-repo-fixable recorded as FIX-BE-1..4 with exclusive files — **not implemented here**
- [x] Third-party documented with upstream links; no `-nowarn` / `-Werror` / `--add-opens` / reuse enable
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] Jacoco **0.80** not lowered
- [x] JAVA_HOME **Temurin 25.0.4.1 LTS** (catalog evidence; never 27)
- [x] Exclusive file: this impl note only
