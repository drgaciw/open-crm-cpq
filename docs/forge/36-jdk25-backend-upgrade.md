# JDK 25 backend upgrade — OpenJDK 25 LTS for the Maven reactor

**Status:** **APPROVED-WITH-GATES** — 2026-09-15  
**Architect:** `opencrm-architect` (Grok 4.6; Cursor `w8` quota-blocked — plan only; **do not spawn implementers**)  
**Evidence date:** 2026-09-15  
**Baseline:** `origin/main` @ `adb928b` (tdd-be-clean #515; Boot **4.0.6**; Java **21**)  
**Sister plan:** [37-angular22-frontend-upgrade.md](37-angular22-frontend-upgrade.md) (FE; disjoint exclusive files)  
**Sources:** `rg` + read `backend/pom.xml`, GHA, Jenkins Dockerfile, service Dockerfiles. GitNexus **skip analyze** (index `04909db` vs later HEAD — stale). UNKNOWN ≠ unused; `-r /home/username01/Projects/open-crm-cpq --file`. Linear/Notion Open CRM: **none**.  
**Do not** edit `docs/forge/00-orchestrator-status.md`.  
**Closeout:** **X1 is FE-owned** after JDK2 **and** NG4 are green on `main`. This wave **must not** edit `docs/ARCHITECTURE.md`.

---

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Stay Java 21** | **REJECT** | Authorized: Open JDK **25 LTS** for the reactor |
| **JDK 27** (mise `latest` / `27.0.0`) | **REJECT** | Non-LTS (GA 2026-09-15, EoA ~Mar 2027). Boot **4.0.6** supports Java **17–25**, not 27. Host already has 27 — **do not** point `JAVA_HOME` at it |
| **JDK 26** | **REJECT** | Non-LTS; Boot 4.0.6 upper bound is 25 (4.1 supports 26 — Boot bump **out**) |
| **Downgrade Boot / stay 3.x** | **HOLD** | Landed parent **4.0.6**. **Do not** downgrade |
| **Upgrade Boot to 4.1.x** | **OUT** | Not authorized. 4.0 OSS ends ~Dec 2026 is a **later** wave |
| **Azure 7.x BOM** | **HOLD** | Optional; serializes parent. Azure 5.8 verifier already excluded |
| **Language-level JDK 25 APIs / modules as a goal** | **REJECT** | Pin **release 25**; do not churn source to preview/incubating APIs |
| **#238 / #241 / dual-read flip / Activities / R2 / Track 2 Spring AI / GLM-5 runtime** | **HOLD** | Standing |
| **✅ JDK1 parent `java.version` 25 + reactor compile/test** | **IN** | Exclusive: `backend/pom.xml` Java pins |
| **✅ JDK2 CI Java 25 + runtime images + Node 22.22.3 toolchain** | **IN** | Exclusive: CI Java (and shared CI Node pin so FE does not touch `ci.yml`) |
| **ARCHITECTURE.md Java 25 row** | **X1 FE** | After both upgrades green — **not** this worker |

**Chosen theme:** Install **Temurin/OpenJDK 25 LTS**, retarget the Maven reactor + CI + JRE images. Keep Boot **4.0.6**. Do not use mise 27.

---

## 1. Problem / evidence (read files)

### Git / GitHub / dual-read / Boot

| Item | State |
|------|--------|
| Open issues | **#238**, **#241** — **HOLD** |
| Open PRs | **none** required for this plan |
| Dual-read | `quote-service` `approvals.legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}` — **do not flip** |
| Boot | Parent **4.0.6** — **do not downgrade** |
| Azure | `spring-cloud-azure.version` **5.8.0**; verifier **excluded** — do not “fix” with Boot 3 |
| Track 2 Spring AI | **HOLD** until Path B ADR Accepted — do not bump `ai-service` Spring AI |
| GLM-5 | ADR-002 Accepted, **runtime not landed** — Azure OpenAI `gpt-4o` stays |
| ADR-006 | **Accepted** — DTOs at `/api/v1/**`; **no** subscriptions FE |

### Landed Java pins (SSOT)

`backend/pom.xml`:

```xml
<parent>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.6</version>
</parent>
<properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
```

`pluginManagement` `maven-compiler-plugin` **hardcodes** `<source>21</source><target>21</target>` (must move with the properties). MapStruct **1.5.5.Final**. Drools **10.2.0**. Testcontainers **1.21.4**.

### Host toolchain (2026-09-15)

| Tool | Landed |
|------|--------|
| Mise Java | **21.0.2** and **27.0.0** (`latest` → 27). **No JDK 25 installed** |
| `JAVA_HOME` (docs / ITs) | `/home/username01/.local/share/mise/installs/java/21.0.2` |
| `java -version` on PATH | **openjdk 27** (2026-09-15) — **wrong** for this wave |
| ITs | `newgrp docker` when login `id` lacks `docker` |

### CI / images (Java 21)

| File | Pin |
|------|-----|
| `.github/workflows/ci.yml` | `actions/setup-java@v4` `java-version: '21'` (shared-libraries, backend-unit, backend-integration, security-audit) |
| Job names | `Shared Libraries (Java 21 / Maven)`, `Backend Unit (Java 21 / Maven)` |
| `infra/jenkins/jenkins/Dockerfile` | `FROM jenkins/jenkins:2.492.3-lts-jdk21` — Maven uses image JDK 21 |
| Service Dockerfiles (7) | `FROM eclipse-temurin:21-jre-alpine` — crm, gateway, quote, product, pricing, ai, pricebook |
| `docs/ci-contract.yaml` | `last_reviewed: "2026-08-30"`; no Java version field today |
| GHA Node (related, owned here so FE does not touch `ci.yml`) | `node-version: '22.12.0'` — **too old for Angular 22** (`^22.22.3 \|\| ^24.15.0 \|\| ^26.0.0`) |

### GitNexus blast (HIGH by policy)

```text
Repository: open-crm-cpq (/home/username01/Projects/open-crm-cpq)
Worktree: /home/username01/Projects/open-crm-cpq/.worktrees/upgrade-arch
Index: 04909db (stale vs origin/main adb928b) — skip analyze
```

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "java.version" \
  --direction upstream --repo /home/username01/Projects/open-crm-cpq
```

Result: **`risk: UNKNOWN`** — Maven property is not a Java symbol. UNKNOWN ≠ unused.

**WARN — treat as HIGH:** parent `java.version` + compiler `source`/`target` serialize **all** reactor modules: `libs/{common,security,rules-engine}` + `services/{crm,gateway,pricing,pricebook,product,quote,ai,configuration-engine,approval-routing,document-generation,contract-asset-management}`. Confirm compile/IT with `rg` + Maven, not an empty caller set.

---

## 2. Decisions + rejected

| Topic | Decision | Rejected |
|-------|----------|----------|
| JDK | **OpenJDK / Temurin 25 LTS**. Install via mise: `mise install java@temurin-25` (fallback `java@25` if vendor shorthand is OpenJDK). Document actual `JAVA_HOME` under `~/.local/share/mise/installs/java/` | JDK **27** (host latest); JDK 21 stay; Oracle commercial |
| Why 25 not 27 | 25 is LTS (Adoptium EoA **at least Sep 2031**). 27 is six-month non-LTS. **Spring Boot 4.0.6 supports through Java 25**. Native-image/AOT cache already want 25 | “Use what’s installed” |
| If 25 unavailable | Only then justify 25 vs 27 in the impl note and **stop** — do not silently land 27 | Shipping 27 because mise has it |
| Boot | **Keep 4.0.6** | Boot 4.1; Boot 3 revert |
| Language | `--release 25` (properties + compiler plugin). No incubating/preview flags. No forced use of new JDK 25 APIs | Rewriting domain code to sequenced collections / structured concurrency as a goal |
| MapStruct / Lombok | Keep BOM Lombok. **Bump MapStruct only if JDK 25 javac fails** (1.5.5.Final is a known risk → 1.6.x) | Drive-by processor upgrades |
| Drools 10.2 | Retest pricing DRL under 25. Fix or pin; **do not** jump Drools major | OpenL / full SpEL |
| Restricted APIs | Expect JDK 24+ `sun.misc.Unsafe` / native-access warnings in Netty, ByteBuddy, Mockito, Testcontainers. Add `--add-opens` / Testcontainers flags **only if tests fail** | Ignoring IT failures |
| CI Java | GHA `java-version: '25'` distribution **temurin**. Jenkins image: **keep Jenkins WAR on its supported JDK** (2.492.3-lts-jdk21 is fine) and **install Temurin 25** for Maven `JAVA_HOME`. Do not invent runners (**#238 HOLD**) | Moving Jenkins core to JDK 27; new GHA push triggers |
| CI Node (same PR) | Pin GHA + Jenkins Node to **`22.22.3`** (Angular 22 official min). Sister FE plan must **not** edit `ci.yml` / Jenkins Dockerfile / `docs/ci-contract.yaml` | FE and BE both editing `ci.yml` |
| Runtime images | `eclipse-temurin:25-jre-alpine` on the seven service Dockerfiles | Distroless / JDK (not JRE) images this wave |
| ITs | `newgrp docker <<'EOF'` + `JAVA_HOME=…/java/<25-install> ./mvnw -B -pl … -am test` | Skipping ITs; “fixing” Docker group via #238 |
| Dual-read / ADR-006 | Untouched YAML; no entity leaks | Flip; subscriptions FE |
| Docs SSOT | Impl notes under `docs/forge/impl/jdk*.md`. **ARCHITECTURE.md Java 25 row = X1 FE after both green** | This worker editing ARCHITECTURE / AGENT landed table |

### Install commands (worker JDK1, before Maven)

```bash
mise install java@temurin-25
mise ls java
# Use the 25 install, never 27.0.0 / latest
export JAVA_HOME="$(mise where java@temurin-25 2>/dev/null || ls -d /home/username01/.local/share/mise/installs/java/*25* | head -1)"
"$JAVA_HOME/bin/java" -version   # must print 25.x LTS, not 27
```

If `temurin-25` has no metadata, try `mise install java@25` and record vendor + full version in the impl note.

---

## 3. PR DAG

```text
JDK1  parent java.version 25 + compiler plugin + reactor tests
        exclusive: backend/pom.xml (Java pins); service compile fixes as needed
        JAVA_HOME=mise 25; newgrp docker for ITs
        │
        ▼
JDK2  CI Java 25 + JRE images + Node 22.22.3 toolchain
        exclusive: .github/workflows/ci.yml, infra/jenkins/jenkins/Dockerfile,
                   docs/ci-contract.yaml, backend/services/*/Dockerfile
        depends: JDK1 on main
        │
        └──────────────►  X1 FE closeout (ARCHITECTURE.md) after JDK2 ∧ NG4
```

**Day-1 parallel with FE:** **JDK1 ∥ NG1** (MCP json). JDK2 serial after JDK1. NG2 must **not** start CI file edits — Node pin lives in JDK2.

| ID | Title | Branch | Exclusive files | Owner | Risk | Acceptance tests |
|----|-------|--------|-----------------|-------|------|------------------|
| **JDK1** | `build(java): retarget Maven reactor to JDK 25 LTS` | `feat/forge-jdk1-java25-reactor` | `backend/pom.xml` (`java.version`, `maven.compiler.source/target`, compiler-plugin `source`/`target`). Other `backend/**` only for **compile/test fixes** required by 25 | BE | **HIGH** | `java -version` under `JAVA_HOME` is **25.x LTS** (not 27). Parent properties **25**. Boot **4.0.6**. Dual-read YAML **unchanged**. `newgrp docker` + `JAVA_HOME=…25… ./mvnw -B -pl libs/common,libs/security,libs/rules-engine -am test`. Then unit slice `./mvnw -B -pl services/crm-service,services/gateway-service -am verify -Punit-tests`. Then per-module `./mvnw -B -pl services/<mod> -am test` for pricing, product, quote, pricebook, ce, approval-routing, document-generation, contract-asset, ai-service. No entity leaks. No Spring AI bump. Impl: `docs/forge/impl/jdk1-java25-reactor.md` |
| **JDK2** | `ci(java): JDK 25 + Node 22.22.3 toolchain` | `feat/forge-jdk2-ci-java25` | `.github/workflows/ci.yml`; `infra/jenkins/jenkins/Dockerfile`; `docs/ci-contract.yaml`; `backend/services/{crm,gateway,quote,product,pricing,ai,pricebook}-service/Dockerfile`. **Not** `backend/pom.xml`. **Not** `docs/ARCHITECTURE.md` | BE | **HIGH** (CI + images) | GHA `java-version: '25'` temurin on all Java jobs; job titles drop “Java 21”. GHA `node-version: '22.22.3'` (frontend + security-audit). Jenkins Dockerfile installs Temurin **25** and exports `JAVA_HOME` for Maven; Node **22.22.3+** (nodesource 22.x is OK if `node -v` ≥ 22.22.3 — **pin if latest 22 is not**). Service images `eclipse-temurin:25-jre-alpine`. `python3 .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py` green. `docs/ci-contract.yaml` notes JDK 25 + Node 22.22.3; `last_reviewed: "2026-09-15"`. **No** GHA push/PR re-enable. **#238** untouched. Impl: `docs/forge/impl/jdk2-ci-java25.md` |
| **X1** | FE closeout — **not this worker** | `docs/forge-x1-upgrade-closeout` | `docs/ARCHITECTURE.md` (and AGENT landed-vs-target rows) | **FE** | LOW | See [37](37-angular22-frontend-upgrade.md) §3. After JDK2 **and** NG4 on `main` |

| Pair | Rule |
|------|------|
| JDK1 vs JDK2 | Serial — CI after reactor compiles on 25 |
| JDK1 vs NG1 | **Parallel** (pom vs MCP json) |
| JDK2 vs NG2 | JDK2 owns **all** `ci.yml` / Jenkins Dockerfile / `ci-contract.yaml`. NG2 **must not** edit them |
| JDK\* vs ARCHITECTURE.md | **Forbidden** — X1 FE |
| Dual-read / product validate / Activities | **Forbidden** |

---

## 4. Blast radius

| Change | Callers / surfaces | Risk |
|--------|--------------------|------|
| Parent `java.version` 21→25 | Entire Maven reactor (11 services + 3 libs) | **HIGH** |
| Compiler plugin 21→25 | Lombok + MapStruct annotation processing every module | **HIGH** |
| MapStruct 1.5.5 (if bump) | Every `*Mapper` | **MED** (only if required) |
| Drools 10.2 on 25 | `pricing-service` DRL + Jacoco | **MED–HIGH** |
| Testcontainers + JDK 25 | backend-integration; `api.version=1.44` already set | **MED** |
| `ai-service` | AgentRouter Azure gpt-4o — compile only; **no** Spring AI upgrade | **MED** (UNKNOWN HTTP) |
| Gateway CSRF / SecurityConfig | Often GitNexus **UNKNOWN** — `rg` before touching. **Do not** change CSRF as part of JDK | — |
| Dual-read YAML | Flip = **HIGH** — **out** | — |
| JRE images 21→25 | Compose / local docker run of seven services | **MED** |
| Jenkins `JAVA_HOME` | All Maven stages | **HIGH** |

**WARN:** UNKNOWN ≠ unused. Controllers, filters, Drools facts: confirm with `rg`. Do not flip dual-read. Do not downgrade Boot. Do not treat mise 27 as a substitute.

---

## 5. Herdr layout (orchestrator spawns — architect does not)

| Pane | Agent id | Branch | PR |
|------|----------|--------|-----|
| 1 | `jdk-be-reactor` | `feat/forge-jdk1-java25-reactor` | JDK1 |
| 2 | `jdk-be-ci` | `feat/forge-jdk2-ci-java25` | JDK2 (after JDK1) |

**Chrome / FE owner:** **none** on these panes.  
**Model:** Grok 4.6 or Cursor `--model auto` when `w8` recovers. Worktree-isolated. Overlay ≠ merge SSOT.  
**Java:** `JAVA_HOME` = mise **Temurin 25**, **never** `…/java/27.0.0` or `…/java/latest`.  
**ITs:**

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/<25-dir>
newgrp docker <<'EOF'
cd backend && ./mvnw -B -pl services/<module> -am test
EOF
```

**Vitest:** none.

---

## 6. Out of scope

- Activities API / `/api/v1/activities/**`
- CI #238 (inventing runners)
- Agent Kafka #241
- Dual-read YAML/env flip; Accept ADR-005
- NgRx; subscriptions / contract-asset FE
- Full SpEL / OpenL; Kafka S2B-9; gateway domain validation
- Delete product `POST /validate`; WhatsApp #430 undo
- **R2** price-book list-row delete
- Named Wave T / T1 / T2
- GLM-5 as runtime; Track 2 Spring AI / Path B ports
- Boot 4.1 or Boot 3
- Azure 7.x BOM
- JDK 27
- `docs/ARCHITECTURE.md` / AGENT landed-vs-target rows (**X1 FE**)
- `docs/forge/00-orchestrator-status.md`
- Frontend `package.json` / Nx / MCP json (**NG\***)

---

## 7. Goal text

```text
Implement Open CRM/CPQ JDK 25 upgrade per docs/forge/36-jdk25-backend-upgrade.md. Install Temurin/OpenJDK 25 LTS via mise (mise install java@temurin-25); do not use mise 27.0.0/latest. Keep Spring Boot parent 4.0.6. JDK1: backend/pom.xml java.version + maven.compiler.source/target + compiler-plugin source/target → 25; JAVA_HOME=mise 25; newgrp docker Maven tests across the reactor; bump MapStruct only if javac fails. JDK2 (after JDK1): GHA setup-java 25 temurin; Jenkins Dockerfile install JDK 25 for Maven JAVA_HOME (Jenkins WAR may stay lts-jdk21); service Dockerfiles eclipse-temurin:25-jre-alpine; pin Node 22.22.3 in GHA+Jenkins; update docs/ci-contract.yaml; verify-drift.py green. Do not flip dual-read, leak entities, ship subscriptions FE, invent Activities, start #238/#241, Track 2 Spring AI, GLM-5 runtime, R2, Boot downgrade, or edit ARCHITECTURE.md / 00-orchestrator-status.md. X1 FE closeout owns ARCHITECTURE.md after JDK2 and NG4 are green.
```

---

## 8. Checklist

- [ ] Mise **25** installed; `java -version` is 25 LTS **not** 27
- [ ] Parent + compiler plugin **25**; Boot **4.0.6**
- [ ] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [ ] ITs under `newgrp docker` + JAVA_HOME 25
- [ ] GHA Java 25 + Node 22.22.3; Jenkins toolchain matches; drift verifier green
- [ ] JRE images 25
- [ ] No ARCHITECTURE.md in JDK1/JDK2
- [ ] No 00-orchestrator-status

---

## 9. Architect verdict

**APPROVED-WITH-GATES** — 2026-09-15.

Gates: dual-read **true**; ADR-006 DTO-only; no Activities; no #238/#241; no R2; Track 2 Spring AI **HOLD**; GLM-5 not runtime; Boot **4.0.6**; JDK **25 LTS not 27**; GitNexus `-r /home/username01/Projects/open-crm-cpq` (impact on `java.version` **UNKNOWN → HIGH**); CI Jenkins + `docs/ci-contract.yaml` gain JDK 25 **and** Node 22.22.3 in **JDK2**; ITs `newgrp docker`; exclusive files as DAG; **X1 FE** owns `docs/ARCHITECTURE.md` after both upgrades green.
