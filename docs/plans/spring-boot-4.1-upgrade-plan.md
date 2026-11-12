# Spring Boot 4.1.2 residual alignment plan (post-upgrade)

**Status:** Proposed  
**Owner:** *(blank)*  
**Linear:** [DRG-672](https://linear.app/drgamtd-workspace/issue/DRG-672/boot41-plan-proposed-spring-boot-41-upgrade-plan-before-31-dec-2026)  
**Framing:** Wave 4 **Disposition B** — residual risk / remaining alignment **after** Boot 4.1.2 is already on `main`. This is **not** a pre-4.1 upgrade plan.  
**Repo read:** `main` @ `c315b4c3` (2026-11-12). Parent POM evidence from `backend/pom.xml` + read-only `./mvnw -q help:evaluate` under Temurin 25.  
**Migrations in this PR:** **0**.

---

## 1. Disposition B — what already landed

| Event | Evidence |
| --- | --- |
| Parent bumped `4.0.9` → **`4.1.2`** | Renovate [#907](https://github.com/drgaciw/open-crm-cpq/pull/907) (`5fd25258`) |
| Gateway + CAM startup restored on 4.1.2 | [#935](https://github.com/drgaciw/open-crm-cpq/pull/935) (`de439639`): `springdoc.version` → **3.2.0**; CAM `resilience4j-spring-boot4` |
| Architecture index already states Boot **4.1.2** | [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md) §2 |
| Boot 4.0 OSS EOL **31 Dec 2026** no longer drives an upgrade clock for this repo | [endoflife.date/spring-boot](https://endoflife.date/spring-boot) — **4.1** OSS support ends **31 Jul 2027**; latest listed **4.1.2** (22 Oct 2026) |

**Do not** rewrite this document as “upgrade from 4.0.x to 4.1”. The parent is already `spring-boot-starter-parent` **4.1.2**. Remaining work is alignment, shim removal, and pin hygiene.

---

## 2. Evidence snapshot (read-only, 2026-11-12)

Commands (from `backend/`, Temurin 25):

```bash
./mvnw -q help:evaluate -Dexpression=project.parent.version -DforceStdout
./mvnw -q help:evaluate -Dexpression=project.properties -DforceStdout
```

Selected effective values:

| Property / coordinate | Effective on `main` | Notes |
| --- | --- | --- |
| `project.parent.version` | **4.1.2** | Parent `spring-boot-starter-parent` |
| `java.version` | **25** | Explicit in reactor properties |
| `spring-cloud.version` | **2025.1.4** | Explicit import BOM |
| `springdoc.version` | **3.2.0** | Explicit; Boot 4.1 line |
| `spring-cloud-azure.version` | **5.8.0** | Explicit; **pre–Boot-4** Azure line |
| `flyway.version` | **11.14.1** | Explicit override of Boot management |
| `grpc.version` | **1.85.1** | Explicit; overrides Boot `grpc-java.version` **1.84.1** |
| `protobuf.version` | **3.25.9** | Explicit; Boot also manages `protobuf-java.version` **4.36.2** |
| `grpc-spring-boot-starter.version` | **3.1.0.RELEASE** | `net.devh` (third-party) |
| `spring-grpc.version` (BOM) | **1.1.2** | First-party Spring gRPC managed by Boot 4.1 — **unused by app code today** |
| `postgresql.version` (BOM) | **42.7.14** | No reactor property override (pin removed in [#697](https://github.com/drgaciw/open-crm-cpq/pull/697)) |
| `kafka.version` (BOM) | **4.2.2** | No reactor override (prior `3.6.1` override dropped) |
| `testcontainers.version` | **1.21.4** | Explicit Testcontainers BOM import |
| `jackson-bom.version` (BOM) | **3.1.7** | Jackson 3 via Boot 4 |
| `spring-framework.version` (BOM) | **7.0.10** | From Boot 4.1.2 |
| OWASP `dependency-check-maven` | **12.1.3** (pluginManagement) | Pin rationale in parent POM (DRG-673); keyless NVD issue on 13.0.0 |

---

## 3. Property matrix — current → recommended next step (cited)

Every currently relevant reactor / Boot-managed property that DRG-672 and post-4.1.2 residuals care about.

| Property / pin | Current (`main`) | Recommended next step | Compatible w/ Boot 4.1.2? | Source |
| --- | --- | --- | --- | --- |
| Parent `spring-boot-starter-parent` | **4.1.2** | Stay on **4.1.x latest**; track patch bumps via Renovate. No 4.0 rollback unless a critical residual forces a temporary hold. | **Yes** (landed) | [#907](https://github.com/drgaciw/open-crm-cpq/pull/907); [Boot tags](https://github.com/spring-projects/spring-boot/tags); [endoflife.date](https://endoflife.date/spring-boot) |
| `java.version` / compiler `25` | **25** | Keep Java **25** LTS. No change for residual alignment. | **Yes** | `backend/pom.xml`; Boot 4.1 JavaVersion enum includes 25+ ([4.1.2 notes](https://github.com/spring-projects/spring-boot/releases/tag/v4.1.2)) |
| `spring-cloud.version` | **2025.1.4** | Keep Oakwood **2025.1.x**; prefer latest SR on the train. Confirm each Renovate bump against the Boot 4.1 row. | **Yes** (2025.1.2+ supports Boot 4.1.x) | [spring.io/projects/spring-cloud](https://spring.io/projects/spring-cloud) release-train table; [#746](https://github.com/drgaciw/open-crm-cpq/pull/746) |
| `spring-cloud-azure.version` | **5.8.0** | Spike → bump to **Spring Cloud Azure 7.x** (Boot 4 line). Then remove load-bearing verifier / autoconfig excludes. Prefer a 7.x release that lists Boot **4.1.x** in its notes; re-verify against **4.1.2** + Cloud **2025.1.4**. | **No** as-is (5.x is Boot 3-era; shims keep the JVM up) | [Azure Spring Versions Mapping](https://github.com/Azure/azure-sdk-for-java/wiki/Spring-Versions-Mapping); [7.4.0 notes](https://github.com/Azure/azure-sdk-for-java/releases/tag/spring-cloud-azure_7.4.0) (Boot 4.0.0–4.1.0 tested); CHANGELOG `7.5.0-beta.1` targets Boot 4.1.1 / Cloud 2025.1.3; [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md) §16.5 |
| `springdoc.version` | **3.2.0** | Stay on **3.x** (Boot 4). Track patch/minor on 3.x; do **not** regress to 2.x. | **Yes** (3.1.x–3.2.x ↔ Boot 4.1.x) | [springdoc.org §13.80](https://springdoc.org/); [#935](https://github.com/drgaciw/open-crm-cpq/pull/935); springdoc **3.2.0** CHANGELOG upgrades Boot to **4.1.2** |
| `grpc-spring-boot-starter.version` (`net.devh`) | **3.1.0.RELEASE** | **Decide** (Proposed): keep third-party short-term **or** migrate configuration-engine + pricing-service to first-party `spring-boot-starter-grpc-{server,client}` / Spring gRPC 1.1.x. | **Unknown** long-term on Boot 4.1 first-party path; works today via net.devh | [Boot 4.1 Release Notes — Spring gRPC](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes); [Boot gRPC reference](https://docs.spring.io/spring-boot/reference/io/grpc.html); [ecosystem migration wiki](https://github.com/spring-projects/spring-grpc/wiki/Spring-gRPC-Migration-from-Ecosystem-Projects) |
| `grpc.version` | **1.85.1** | After first-party migration, prefer Boot-managed `grpc-java.version` (**1.84.1** today) unless a CVE forces a deliberate override. Until then, keep pin documented. | **Yes** (override of BOM) | Effective `grpc-java.version=1.84.1` from Boot BOM; reactor pin in `backend/pom.xml`; [#909](https://github.com/drgaciw/open-crm-cpq/pull/909) |
| `protobuf.version` | **3.25.9** | Align strategy with Spring gRPC / `protoc` plugins. Boot manages `protobuf-java.version` **4.36.2** — resolve 3.x pin vs 4.x BOM during gRPC story; do not silent-mix. | **Partial** (explicit 3.25.9 vs BOM 4.36.2) | Effective properties; `configuration-engine` / `pricing-service` poms |
| `flyway.version` | **11.14.1** | Keep Flyway **11** (Boot 4 modular `spring-boot-starter-flyway`). Revisit only if Boot BOM moves and CI proves safe to drop the override. | **Yes** | Parent comment + Boot Flyway starter usage across services |
| `postgresql.version` | **42.7.14** (BOM) | Leave unmanaged (no reactor pin). Services that still write `${postgresql.version}` inherit Boot. | **Yes** | [#697](https://github.com/drgaciw/open-crm-cpq/pull/697); effective BOM |
| `kafka.version` | **4.2.2** (BOM) | Leave unmanaged. | **Yes** | Prior override removal note in history |
| `mapstruct.version` | **1.6.3** | Keep; bump only with compiler/APT verification. | **Yes** (orthogonal) | [#913](https://github.com/drgaciw/open-crm-cpq/pull/913) |
| `jjwt.version` | **0.12.3** | Keep until security bump; not Boot-managed. | **Yes** | `backend/pom.xml` |
| `commons-lang3.version` / `commons-io.version` | **3.21.0** / **2.22.0** | Keep; Renovate OK. | **Yes** | [#695](https://github.com/drgaciw/open-crm-cpq/pull/695), [#906](https://github.com/drgaciw/open-crm-cpq/pull/906) |
| `drools.version` / `openl-tablets.version` | **10.2.0** / **5.27.6** | Rules-engine follow-up only if Boot 4.1.x classpath breaks; no forced bump for residual. | **Unknown** (domain pin) | `backend/pom.xml`; OpenL hold in ARCHITECTURE §17 |
| `openhtmltopdf.version` | **1.0.10** | Document-generation only; bump on CVE / render break. | **Yes** (orthogonal) | `backend/pom.xml` |
| `testcontainers.version` | **1.21.4** | Keep Testcontainers BOM import; retain Docker API `1.44` Surefire property for Engine 29+. | **Yes** | Parent Surefire `api.version=1.44` comment |
| `lombok.version` (BOM) | **1.18.48** | Inherit Boot. | **Yes** | Effective properties |
| `jackson-bom.version` (BOM) | **3.1.7** | Jackson **3** is the Boot 4 default. Continue migrating remaining `com.fasterxml.jackson` call sites / Jackson2 bridges toward `tools.jackson` where production paths still dual-stack. | **Yes** (platform) | Boot 4 Jackson 3; mixed imports still present (e.g. CRM lead scoring vs event listeners) |
| `dependency-check.failBuildOnCVSS` + plugin **12.1.3** | **7** / **12.1.3** | **Threshold alignment landed** via DRG-674 [#974](https://github.com/drgaciw/open-crm-cpq/pull/974) (`5d7c8b6c`): `scripts/ci/security-audit.sh` reads the pom property (**7**) with **no** `-DfailBuildOnCVSS` CLI override. Residual: stale parent comment still mentions Jenkins `-DfailBuildOnCVSS=9` (comment-only); do not raise plugin to **13.x** until keyless NVD works (DRG-673 pin). | **N/A** (tooling) | `scripts/ci/security-audit.sh`; `backend/pom.xml` property + pluginManagement; [#974](https://github.com/drgaciw/open-crm-cpq/pull/974), [#972](https://github.com/drgaciw/open-crm-cpq/pull/972) |
| CAM `resilience4j-spring-boot4` | **2.4.0** | Keep Boot **4** artifact (not `spring-boot3`). | **Yes** | [#935](https://github.com/drgaciw/open-crm-cpq/pull/935) |
| Obsolete Surefire comment “HOLD: keep Spring Boot 4.0.x” | Still in `backend/pom.xml` | Doc/comment cleanup in an Azure or hygiene story — **wording is stale** after #907. | N/A | `backend/pom.xml` Surefire systemPropertyVariables |

---

## 4. Residual risk themes (ordered)

### R1 — Spring Cloud Azure 5.8.0 shims (highest)

Azure **5.8.0** still requires:

- `spring.cloud.azure.compatibility-verifier.enabled=false`
- broad `spring.autoconfigure.exclude` for token credential, compatibility verifier, AAD B2C (and Event Hubs Kafka OAuth2 on several services)

These are **load-bearing** ([ARCHITECTURE §16.5](../ARCHITECTURE.md)). Removing them without **7.x** breaks Boot 4 startup. 5.x’s “compatible with Boot 3.0–3.2” message is a **verifier mismatch**, not a signal to downgrade Boot.

### R2 — gRPC strategy: net.devh vs first-party Spring gRPC

Boot **4.1** ships first-party gRPC ([reference](https://docs.spring.io/spring-boot/reference/io/grpc.html); managed `spring-grpc.version` **1.1.2**). This repo still uses `net.devh:grpc-*-spring-boot-starter` on **configuration-engine** and **pricing-service**, with `grpc.server.port` properties and `@GrpcService` from `net.devh`. Migration touches annotations, properties (`grpc.*` → `spring.grpc.*`), test transport, and protobuf pin strategy.

### R3 — springdoc (mostly closed)

**3.2.0** closed the Boot 4.1 gateway failure from springdoc **2.10** WebFlux `WebFluxProperties` package move. Residual: stay on 3.x; verify OpenAPI UI on gateway + servlet services after any 3.x bump.

### R4 — BOM override hygiene

Explicit overrides that still diverge from Boot 4.1.2 management:

| Override | Reactor | Boot BOM (effective) | Risk |
| --- | --- | --- | --- |
| `grpc.version` | 1.85.1 | `grpc-java.version` 1.84.1 | Dual grpc stacks if first-party starters added without dropping pin |
| `protobuf.version` | 3.25.9 | `protobuf-java.version` 4.36.2 | Plugin/runtime skew |
| `flyway.version` | 11.14.1 | managed Flyway 11 line | Low if kept intentional |
| `spring-cloud-azure.version` | 5.8.0 | *(not Boot-managed)* | High — see R1 |
| `springdoc.version` | 3.2.0 | *(not Boot-managed)* | Low after #935 |

### R5 — Test / tooling follow-ups

- Surefire Azure excludes + Testcontainers Docker API **1.44** remain required until R1 completes.
- OWASP **CVSS threshold alignment is landed** (DRG-674 / [#974](https://github.com/drgaciw/open-crm-cpq/pull/974)): fail gate is pom `dependency-check.failBuildOnCVSS=7`; `scripts/ci/security-audit.sh` does not override. Remaining tooling risk is **comment hygiene** (pom still cites Jenkins `-DfailBuildOnCVSS=9`) and the **12.1.3** pin until keyless NVD allows 13.x — not a threshold gap.
- Mixed Jackson 2 (`com.fasterxml`) vs Jackson 3 (`tools.jackson`) call sites — incremental cleanup, not a parent bump.

### R6 — Spring Cloud train

**2025.1.4** already matches Boot 4.1.x (train supports 4.1 from **2025.1.2**). Residual is **stay current**, not “pick a train for 4.1”.

---

## 5. Ordered follow-up stories (Proposed, owner blank, **not filed**)

None of the following are filed in Linear by this PR. Each row is a sketch only.

| Order | Working title | Allowlist sketch (future PR) | Gate |
| --- | --- | --- | --- |
| F1 | **Azure 7.x spike** — pick Spring Cloud Azure 7.x that claims Boot 4.1.x; matrix against parent 4.1.2 + Cloud 2025.1.4; record autoconfig that can drop | `docs/plans/*` and/or spike branch touching `backend/pom.xml` + one service `application*.yml` (not this PR) | Proposed · Owner: *(blank)* |
| F2 | **Azure 7.x land + shim removal** — bump `spring-cloud-azure.version`; delete Surefire/system and per-service excludes for verifier/token/B2C/Event Hubs when green | `backend/pom.xml`, `backend/services/*/src/main/resources/application*.yml`, matching test YAML/Java | Proposed · Owner: *(blank)* · security-sensitive |
| F3 | **gRPC strategy ADR-or-plan** — keep net.devh vs migrate to `spring-boot-starter-grpc-*` | `docs/plans/*` or `docs/adr/*` (**ADR only if owner opens a separate story**; this residual plan does not create an ADR) | Proposed · Owner: *(blank)* |
| F4 | **First-party Spring gRPC migration** — configuration-engine + pricing-service; drop `grpc-spring-boot-starter.version`; align `grpc.version` / `protobuf.version` with Boot | `backend/pom.xml`, `backend/services/{configuration-engine,pricing-service}/**` | Proposed · Owner: *(blank)* |
| F5 | **springdoc 3.x patch watch** — bump within 3.x after release notes; smoke gateway WebFlux + servlet UIs | `backend/pom.xml` (+ service OpenAPI configs if needed) | Proposed · Owner: *(blank)* |
| F6 | **Jackson 3 call-site sweep** — remove remaining Jackson2-only production paths / clarify intentional bridges | targeted `backend/libs/**`, `backend/services/**` | Proposed · Owner: *(blank)* |
| F7 | **Parent comment hygiene** — remove stale “HOLD keep Boot 4.0.x” Surefire comment; remove/replace stale Jenkins `-DfailBuildOnCVSS=9` comment (threshold already **7** via [#974](https://github.com/drgaciw/open-crm-cpq/pull/974)); document Azure shim until F2 | `backend/pom.xml` (comment-only) | Proposed · Owner: *(blank)* |
| F8 | **OWASP plugin 13.x / keyless NVD** — *threshold alignment already landed* (DRG-674 [#974](https://github.com/drgaciw/open-crm-cpq/pull/974)). Residual only: bump dependency-check beyond **12.1.3** when keyless NVD works; any workflow/Jenkins change remains **owner-gated G05/G06** | `.github/**`, `Jenkinsfile`, `scripts/ci/**`, `backend/pom.xml` pluginManagement | Proposed · Owner: *(blank)* · **owner-gated** · **partially landed** |

---

## 6. Decisions (Proposed · owner blank)

| ID | Decision | Options | Proposed default | Owner |
| --- | --- | --- | --- | --- |
| D1 | Azure alignment path | (a) stay on 5.8.0 + shims (b) spike/land 7.x and remove shims | **(b)** via F1→F2 | *(blank)* |
| D2 | gRPC stack | (a) keep net.devh (b) migrate to Boot first-party Spring gRPC | **(b)** after F3 design note; schedule F4 | *(blank)* |
| D3 | protobuf major | (a) keep 3.25.9 pin (b) move to Boot `protobuf-java` 4.x with plugin bump | **Defer to F4** | *(blank)* |
| D4 | Rollback posture while residuals open | (a) remain on 4.1.2 (b) emergency revert parent to 4.0.9 | **(a)** unless Azure/gRPC residual is unblockable; see §7 | *(blank)* |

---

## 7. Rollback / risk notes

| Scenario | Action | Cost |
| --- | --- | --- |
| New 4.1.x patch breaks a module | Pin parent to last known good **4.1.x**; do not invent a 4.0 plan | Low |
| Azure 7.x spike fails | Keep **5.8.0** + existing excludes; document failure in F1 evidence | Medium (shims remain) |
| gRPC migration regresses pricing/CE | Feature-flag or revert F4 only; parent stays 4.1.2 | Medium |
| Emergency “Boot too new” | Revert parent toward **4.0.9** only as last resort — re-introduces 4.0 OSS EOL (**31 Dec 2026**) and likely re-breaks springdoc/resilience4j paths fixed in #935 | **High** — treat as incident, not plan default |

**This docs PR changes no runtime.** Rollback of the document alone is revert of the single markdown file.

---

## 8. Timeline (residual work, not a 4.0→4.1 upgrade)

Boot **4.1.2** is already on `main`, so the original DRG-672 “upgrade before 31 Dec 2026” clock is **satisfied for the parent bump**. Remaining schedule is residual alignment before Boot **4.1** OSS EOL (**31 Jul 2027**):

| Window | Focus |
| --- | --- |
| Immediate | Merge this plan (draft → owner merge). File F1–F8 only when owner prioritizes. |
| Next delivery wave | F1 Azure 7.x spike evidence |
| After green spike | F2 shim removal |
| Parallel / after Azure | F3–F4 gRPC decision + migration |
| Continuous | F5 springdoc 3.x, F6 Jackson sweep, F7 comment hygiene (incl. stale CVSS-9 comment), F8 plugin 13.x when NVD allows (owner-gated) |

---

## 9. AC checklist (docs-only, Disposition B)

| AC | Met? |
| --- | --- |
| Residual-after-4.1.2 framing (not a pre-4.1 upgrade plan) | Yes — §1 |
| Every currently relevant pom property has current → next step with cited source | Yes — §3 |
| Follow-ups listed with allowlist sketches; **none filed** | Yes — §5 |
| Decisions Proposed, owner blank | Yes — §6 |
| Draft PR; CI not-run / blocked note OK | PR body |
| 0 migrations | Yes |
| Allowlist path only: `docs/plans/spring-boot-4.1-upgrade-plan.md` | This file |

---

## 10. Local evidence for this docs PR

```bash
git diff --name-only origin/main...HEAD
# expected:
# docs/plans/spring-boot-4.1-upgrade-plan.md
```

Optional property excerpts (already captured in §2) from `backend/` with `./mvnw -q help:evaluate` — **read-only**, no reactor install required for the plan itself.
