# JDK 25 parity audit — open-crm-cpq

**Date:** 2026-11-11, 1:26 PM CST (America/Chicago)
**Repo:** [drgaciw/open-crm-cpq](https://github.com/drgaciw/open-crm-cpq)
**Source commit (`origin/main`):** `61f27e07cf7913d59fe2c82e400558e21e113dce` (ADR-038, #959)
**#969 (pending, not merged):** open draft PR [ci(DRG-671): pin Jenkins controller image to 2.580.2-lts-jdk25](https://github.com/drgaciw/open-crm-cpq/pull/969), head `3cde312c237a14e653ac3eba6040ad3a70c1c0fc`. Moves the Jenkins controller image from `2.580.2-lts-jdk21` to `2.580.2-lts-jdk25`. Does **not** edit `docs/ARCHITECTURE.md`.
**#968:** open draft, Node 24 only; no Java pin changes.
**Method:** Reused the 1:08 PM CST box audit and re-verified every live pin against current `origin/main` on the open-crm-cpq Sprite. No merges, no PR opened from this branch, no other branches touched.

## Summary

| | Count |
|---|---|
| Components checked (live config + current docs; 11 service images counted individually, 16 child poms counted as one inherited group) | **52** |
| ❌ Mismatches (live config + current docs only) | **13** |
| Historical docs (forge journals, forge-wave prompts, modernization plan, harvest *source*-stack lines) | listed separately; **not counted as mismatches** |
| Build/runtime toolchain mismatch among pom, service images, and GHA | **0** (all Java 25) |

**Short mismatch list:**

1. `infra/jenkins/jenkins/Dockerfile:4` — controller `FROM jenkins/jenkins:2.580.2-lts-jdk21` (project Java is 25; known split until #969).
2. `infra/jenkins/jenkins/Dockerfile:3,34` — comments still cite `2.492.3` / “image JDK 21” (tag is already `2.580.2-lts-jdk21` on main). #969 rewrites the comments.
3. `docs/ci-contract.yaml:10,15,215-216` — `jenkins_war_image: jenkins/jenkins:2.492.3-lts-jdk21` (live Dockerfile is `2.580.2-lts-jdk21`). #969 fixes this.
4. `docs/ARCHITECTURE.md:218` — **P0.** Same stale WAR tag `2.492.3-lts-jdk21`. Not in #968 or #969.
5. `docs/CRM_CUTOVER_CHECKLIST.md:113` — Azure DevOps mirror still says “JDK 21”.
6. `docs/plans/jenkins-ai-ci-plan-summary.md:7` — “toolchain matching GHA (Java 21, …)”.
7. `docs/plans/00-harvest-index.md:25,30` — “Java **21**” / `JAVA_HOME` **21.0.2** as this repo’s target.
8. `docs/plans/pricing-ai-rules-reuse-gap-analysis.md` — several lines still prescribe Java 21 as the harvest *target*.
9. `docs/plans/pricing-ai-rules-harvest-backlog-t1.md` and `docs/plans/pricing-ai-rules-capability-matrix-t1.md` — “target/adapt JDK 21”.
10. `docs/plans/trycompai-crm-reuse-gap-analysis.md:48` — constraint “Java 21 / Boot 4.0.6”.
11. `docs/plans/price-book-reuse-gap-analysis.md:19` — this repo’s `pricebook-service` described as “Java 21”.
12. `.claude/settings.local.json:54,57` — leftover Microsoft `jdk-21.0.7.6-hotspot` allowlist path.
13. `infra/jenkins/README.md` — no controller image / JDK row (missing current pin).

**Sister-repo search:** cursor-github MCP **worked** for both `drgaciw/agent-factory-poc-00` and `drgaciw/cmano-clone` (`search_repositories`, `search_code`, `search_commits`, and a file read). Neither repo contains JDK 25 audit or version-mismatch notes for this project (detail below).

---

## Prior JDK 25 audit / migration notes

| Source | What was found |
|---|---|
| This repo, git `origin/main` | No prior file under `docs/audits/` or `artifacts/`. Those paths do not exist on main. Dated working docs usually live in `docs/plans/`; one research note is in `docs/research/`. This audit is therefore saved as `docs/audits/jdk25-parity-audit-2026-11-11.md` (new folder, audit snapshots rather than plans). |
| Box artifact (earlier today, not in git) | `/workspace/open-crm-cpq-artifacts/jdk-parity-audit-2026-11-11.md` (268 lines, 1:08 PM CST, same SHA `61f27e07`). Reused and re-verified; this table is the in-repo deliverable. |
| `docs/forge/` (historical journals) | 38 files mention Java 21/25 / Temurin, including `docs/forge/36-jdk25-backend-upgrade.md`, `docs/forge/impl/jdk1-java25-reactor.md`, `docs/forge/impl/jdk2-ci-java25.md`, plus related hygiene/IT/validation notes. Implementation journals, not current pins. |
| `docs/plans/2026-09-21-implementation-gap-and-story-plan.md:57,137,214` | Already records that the harvest index (Java 21) disagrees with landed Java 25 and tells implementers to follow `docs/ARCHITECTURE.md` until the index is corrected. Accurate meta-note. |
| `drgaciw/agent-factory-poc-00` (MCP accessible) | No hits for Temurin, “JDK 25”, or “Java 25”. `jdk` only in `package-lock.json`. `java` hits were JavaScript / archify skill files and `TECH-SPEC.md`, not a JDK pin. Commit search for JDK / Temurin / Java 25: none. **No JDK 25 audit or migration notes.** |
| `drgaciw/cmano-clone` (MCP accessible) | .NET 8 / Unity repo. Queries for Temurin, “Java 25”, “JDK 25”, and `jdk25` returned none; commit search returned none. A generic `jdk` search listed three markdown files; `docs/reports/future-sprint-roadmap-09152026.md` was read and contains no Java/JDK version text (false positive). **No JDK 25 audit or version-mismatch notes.** |

Linear / Notion JDK wording was inventoried in the 1:08 PM CST box audit (stale “from 2.492.3-lts-jdk21” baseline on Linear DRG-671 and the Notion CI-J25 page). Those connectors were not re-queried in this pass.

---

## How to read the table

**Expected on `origin/main` today:** backend and GHA and service images are Java / Temurin **25**. Jenkins Maven is Temurin **25** at `/opt/java/temurin-25`. The Jenkins *controller* image is `jenkins/jenkins:2.580.2-lts-jdk21` until #969 merges.

**Status:** ✅ match = agrees with that expected column. ❌ MISMATCH = live config or a current-state doc disagrees with the live pin or with the Java 25 project standard. Historical material is in its own section and is not scored as a mismatch.

**Actual/runtime:** taken from the file’s own pin (image tag, `setup-java`, compiler level) plus, where cheap, the last green `Main verify` run on this SHA.

---

## 1. Live build, CI, images, and toolchain

| Component/file | Declared JDK | Actual/runtime (if detectable) | Status | Notes |
|---|---|---|---|---|
| `backend/pom.xml:40-42,171-172` | `java.version` / `maven.compiler.source` & `target` / compiler plugin **25**. No `<release>`, no Enforcer, no `maven-toolchains-plugin` | Compiler level 25 (no extra JVM pin in the POM) | ✅ match | Source of truth for the backend build. |
| Child Maven modules (16 `**/pom.xml` under `backend/`, excluding the parent) | none (no `java.version` / compiler override) | inherit **25** | ✅ match | `libs/{common,jev-decision,rules-engine,security}`, `shared`, and all 11 services. |
| `backend/services/ai-service/Dockerfile` | `FROM eclipse-temurin:25-jre-alpine` | image tag **25-jre-alpine** (digest not pulled) | ✅ match | |
| `backend/services/approval-routing/Dockerfile` | `FROM eclipse-temurin:25-jre-alpine` | image tag **25-jre-alpine** | ✅ match | |
| `backend/services/configuration-engine/Dockerfile` | `FROM eclipse-temurin:25-jre-alpine` | image tag **25-jre-alpine** | ✅ match | |
| `backend/services/contract-asset-management/Dockerfile` | `FROM eclipse-temurin:25-jre-alpine` | image tag **25-jre-alpine** | ✅ match | |
| `backend/services/crm-service/Dockerfile` | `FROM eclipse-temurin:25-jre-alpine` | image tag **25-jre-alpine** | ✅ match | |
| `backend/services/document-generation/Dockerfile` | `FROM eclipse-temurin:25-jre-alpine` | image tag **25-jre-alpine** | ✅ match | |
| `backend/services/gateway-service/Dockerfile` | `FROM eclipse-temurin:25-jre-alpine` | image tag **25-jre-alpine** | ✅ match | |
| `backend/services/pricebook-service/Dockerfile` | `FROM eclipse-temurin:25-jre-alpine` | image tag **25-jre-alpine** | ✅ match | |
| `backend/services/pricing-service/Dockerfile` | `FROM eclipse-temurin:25-jre-alpine` | image tag **25-jre-alpine** | ✅ match | |
| `backend/services/product-service/Dockerfile` | `FROM eclipse-temurin:25-jre-alpine` | image tag **25-jre-alpine** | ✅ match | |
| `backend/services/quote-service/Dockerfile` | `FROM eclipse-temurin:25-jre-alpine` | image tag **25-jre-alpine** | ✅ match | 11 service images; no `account-service` Dockerfile on main (ADR-038 is Proposed). |
| `.github/workflows/ci.yml` | `actions/setup-java@v6`, `java-version: '25'`, `distribution: temurin` (4 jobs, lines 22-25, 49-52, 74-77, 125-128) | pin is Temurin 25 | ✅ match | Nightly / manual fallback. |
| `.github/workflows/pr-check.yml:29-32` | same: Temurin **25** | pin is Temurin 25 | ✅ match | |
| `.github/workflows/main-verify.yml:37-40` | same: Temurin **25** | **Temurin 25.0.5-8** at `/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/25.0.5-8/x64`. Green run `43065625388` (“Main verify” / Backend Unit) on this SHA, 2026-11-11 11:11 AM CST. ArchUnit logged “Detected Java version 25.0.5”. | ✅ match | No raw `java -version` line in that job (setup-java + compiler evidence used instead). |
| `.github/workflows/preview-sprites.yml` | no Java pin | n/a | ✅ match | No Java required. |
| `infra/jenkins/jenkins/Dockerfile:4` (`FROM`) | `jenkins/jenkins:2.580.2-lts-jdk21` | image tag **jdk21** (controller not smoke-tested in this audit) | ❌ MISMATCH | Live controller JDK is 21 vs project Java 25. Intentional split today; Maven already uses Temurin 25. #969 (unmerged) moves this to `2.580.2-lts-jdk25`. Tag itself was already bumped from `2.492.3-lts-jdk21` by #896 (`ebe8f588`). |
| `infra/jenkins/jenkins/Dockerfile:3,34` (comments) | “Jenkins WAR stays on image JDK 21 (2.492.3 supports 17/21, not 25/27)”; “must keep resolving the image JDK 21” | live `FROM` is **2.580.2-lts-jdk21**, not 2.492.3 | ❌ MISMATCH | Stale comment (wrong WAR tag). #969 rewrites both comments. |
| `infra/jenkins/jenkins/Dockerfile:33-54` (Maven toolchain) | downloads Adoptium Temurin **25** into `/opt/java/temurin-25`; `ENV JAVA_HOME=/opt/java/temurin-25` (not prepended to `PATH`, so `jenkins.sh` keeps the image JDK for the WAR) | install path `/opt/java/temurin-25` (image not built in this audit) | ✅ match | Kept even after #969. |
| `infra/jenkins/docker-compose.jenkins.yml` + `docker-compose.build-agent.yml` | `image: opencrm-jenkins:local` (no numeric JDK) | inherits the controller Dockerfile | ✅ match | |
| `Jenkinsfile`; `infra/jenkins/jenkins/casc/jenkins.yaml`; `infra/jenkins/env/jenkins.env.example`; `infra/jenkins/scripts/quickstart.{sh,ps1}` | no JDK version pin | none (use image + `JAVA_HOME`) | ✅ match | |
| `scripts/ci/toolchain-check.sh` | runs `java -version` (no numeric pin) | whatever `JAVA_HOME` provides (25 in GHA / Jenkins Maven) | ✅ match | |
| `docs/ci-contract.yaml:12-13,16-17` and notes at `:131,137,141,152` | `java_version: "25"`, `java_distribution: temurin`, `jenkins_maven_java_home: /opt/java/temurin-25`, `service_runtime_image: eclipse-temurin:25-jre-alpine` | matches live pom / GHA / service images / Jenkins Maven | ✅ match | |
| `docs/ci-contract.yaml:10,15,215-216` (Jenkins WAR) | comment “Jenkins WAR stays on lts-jdk21”; `jenkins_war_image: jenkins/jenkins:2.492.3-lts-jdk21`; “Controller WAR is 2.492.3-lts-jdk21” | live Dockerfile is **2.580.2-lts-jdk21** | ❌ MISMATCH | Stale WAR tag (and, after #969, the JDK). #968 leaves this text; #969 rewrites all three lines to `2.580.2-lts-jdk25`. |
| `cpq_beta/.github/workflows/*`, `cpq_beta/.devcontainer/devcontainer.json` | no Java pin | Python / Frappe devcontainer | ✅ match | No Java required. |
| Root / frontend / `cpq_beta` `package.json` engines | no JDK; `infra/alchemy/package.json` has `engines.node: ">=22"` only | n/a (Node, not JDK) | ✅ match | Checked; not a JDK pin. |

**Absent pin files** (confirmed missing on `origin/main`, including under `backend/`): `.sdkmanrc`, `.java-version`, `.tool-versions`, `.mvn/jvm.config`, `toolchains.xml`. No Gradle build. Root `README.md` states no numeric JDK (only “Java/Spring backend”). Not counted as mismatches.

---

## 2. Current project docs and agent instructions

| Component/file | Declared JDK | Actual/runtime (if detectable) | Status | Notes |
|---|---|---|---|---|
| `docs/ARCHITECTURE.md:16,37,225,266,374` | Java **25** / Temurin 25 `JAVA_HOME` example | matches pom / CI | ✅ match | |
| `docs/ARCHITECTURE.md:218` | “Temurin **25** (Maven), Node **22.23.4**, Jenkins WAR `2.492.3-lts-jdk21`; service runtime `eclipse-temurin:25-jre-alpine`” | Maven 25 and the runtime image are right. Live Jenkins WAR is **`2.580.2-lts-jdk21`**; after #969 it will be `2.580.2-lts-jdk25` | ❌ MISMATCH | **P0.** Neither #968 nor #969 edits this file. If updated before #969, use `2.580.2-lts-jdk21`. |
| `AGENT.md:12,246` | Spring Boot 4.1.2 / Java 25 | matches pom | ✅ match | |
| `AGENTS.md:121` | “The image Java is 21. This repo needs Temurin 25 at `/usr/lib/jvm/temurin-25-jdk-amd64`…” | describes the Cloud VM default (21) and the repo requirement (25) | ✅ match | Host warning, not a project pin. |
| `CLAUDE.md:55` | Java 25 / Spring Boot 4.1.2 | matches pom | ✅ match | |
| `.cursor/agents/opencrm-architect.agent.md:4` | Spring Boot 4.1.2 / Java 25 | matches pom | ✅ match | |
| `backend/README.md:3,35-46,208` | Java **25** / Temurin 25 LTS; “never 21.0.2, never 27” | matches pom / CI | ✅ match | Adjacent (not scored as JDK): still says Spring Boot **4.0.6**, which is stale vs Boot **4.1.2** on main. |
| `backend/scripts/build-and-run.sh:6`; `backend/Start-AiService.ps1:6,33,56-62` | require Java 25 / Temurin 25; fail if `java` < 25 | runtime check against local `java` | ✅ match | |
| `backend/services/gateway-service/LOCAL_RUNTIME.md:3,34` | “Build … with Java 25”; ran “under Java 25 / Boot 4.0.6” | Java 25 | ✅ match | Boot 4.0.6 is adjacent stale. |
| `docs/adr/ADR-011-shared-maven-libs-published-language.md:25,45` | “compiled against the same Java 25 toolchain” | matches pom | ✅ match | |
| `docs/uat/README.md:7`; `docs/uat/cpq-product-management-lifecycle.md:291`; `docs/uat/lead-to-fulfillment-onprem.md:254`; `docs/uat/lead-to-fulfillment-saas.md:143`; `docs/uat/pricing-lifecycle-management.md:291` | “Java 25 / Spring Boot 4” | matches pom | ✅ match | |
| `docs/ai-model-selection.md:7`; `docs/adr-gap-implementation.md:49`; `backend/services/ai-service/.../execution/README.md:22` | Java 25 | matches pom | ✅ match | |
| `docs/plans/ramp-lifecycle-20261010-evidence.md:65`; `docs/plans/ramp-wave-20261009-r1-verification.md:22` | observed host used JDK 25 / “Java is Temurin 25.0.4.1” | observational, not a pin | ✅ match | Dated evidence; not current-state drift. |
| `docs/plans/2026-09-21-implementation-gap-and-story-plan.md:57,137,214` | records Java 21 vs 25 disagreement; follow `ARCHITECTURE.md` | meta, accurate vs main | ✅ match | |
| `docs/CRM_CUTOVER_CHECKLIST.md:113` | Azure DevOps would mirror: **JDK 21**, Maven, Docker… (same sentence correctly points at GHA `ci.yml`) | GHA `ci.yml` is Temurin **25** | ❌ MISMATCH | Current-state CI parity should say JDK 25. |
| `docs/plans/jenkins-ai-ci-plan-summary.md:7` | “toolchain matching GHA (**Java 21**, Node 22, …)” | GHA Java is 25; Jenkins Maven is 25; controller is 21 today, 25 after #969 | ❌ MISMATCH | |
| `docs/plans/00-harvest-index.md:25,30` | “Boot **4.0.6** / Java **21** stay”; “Java 21 / Boot 4.0.6 … `JAVA_HOME` **21.0.2** for Maven.” | landed target is Java **25** | ❌ MISMATCH | Already flagged by the 2026-09-21 plan. |
| `docs/plans/pricing-ai-rules-reuse-gap-analysis.md:25,30,142,218,249,448` | “Target stays Java **21** (not source 25)”; ITs `JAVA_HOME` 21.0.2; “Java 25 APIs may not build on target JDK 21”; “target harvest still compiles on **JDK 21**”; “Check JDK 21 compatibility”; “adapted to JDK 21” | this repo’s target is Java **25** (line 65 correctly describes a *source* stack as Java 25) | ❌ MISMATCH | Invert the risk: the source may be 21/25; the repo builds on 25. |
| `docs/plans/pricing-ai-rules-harvest-backlog-t1.md:169,176`; `docs/plans/pricing-ai-rules-capability-matrix-t1.md:115` | “adapt source … to JDK **21**”; “Target remains Java 21 (no Java 25 APIs from source).” | target is Java 25 | ❌ MISMATCH | |
| `docs/plans/trycompai-crm-reuse-gap-analysis.md:48` | constraint “Java 21 / Boot 4.0.6 …” scored PASS for this repo | target is Java 25 / Boot 4.1.2 | ❌ MISMATCH | |
| `docs/plans/price-book-reuse-gap-analysis.md:19` | this repo’s `pricebook-service` “(Boot 4 / **Java 21** / PostgreSQL / Flyway)” | this service is Java 25 | ❌ MISMATCH | Leave line 63 / 384 (“Stack: Java 21, Boot 3.2.5…”, “Java **21.0.2**”) as the *source* prototype stack (historical, below). |
| `.claude/settings.local.json:54,57` | allows `JAVA_HOME=.../Microsoft/jdk-21.0.7.6-hotspot` | not a project pin; leftover JDK 21 path | ❌ MISMATCH | Misleading allowlist; replace with a Temurin 25 path or remove. |
| `infra/jenkins/ai-assist/code_review.py:14` | “Stack: Java 25 Spring Boot 4.0.6 …” | Java 25 | ✅ match | Boot 4.0.6 is adjacent stale. |
| `infra/jenkins/README.md` | **no** JDK / Jenkins image tag stated | live controller is `2.580.2-lts-jdk21` | ❌ MISMATCH | Missing current pin. #969 adds a design-decisions row for `2.580.2-lts-jdk25`. |
| `docs/ci-jenkins-cutover.md` | no JDK version stated | n/a | ✅ match | No Java claim. |

---

## 3. Historical docs (not counted as mismatches)

These are past plans, harvest *source*-stack descriptions, or forge-era journals. Do not treat them as the current toolchain.

| Component/file | Declared JDK | Notes |
|---|---|---|
| `docs/forge/**` (38 files with Java 21/25 / Temurin mentions) | mixed 21 / 25 (upgrade journals) | Historical implementation journals, including `36-jdk25-backend-upgrade.md`, `impl/jdk1-java25-reactor.md`, `impl/jdk2-ci-java25.md`. Not current SSOT. |
| `.grok/workflows/forge-wave-{e,f,g,h,j,k}.rhai` (8 files) | “Prefer JDK 21” / `JAVA_HOME=.../java/21.0.2` | Historical forge-wave agent prompts. |
| `MODERNIZATION_PLAN.md:143,1088` | “Java 21 (LTS)”; `java: "21"` with Boot 3.5.9 | Already called historical target text by the 2026-09-21 plan. |
| `docs/plans/price-book-reuse-gap-analysis.md:63,384` | source prototype “Java 21, Boot 3.2.5”; build evidence “Java **21.0.2**” | Source-stack description, not this repo’s service. |
| `docs/plans/deal-desk-reuse-gap-analysis.md:56,173,189,236` | source stack Java 21; “Build with JDK 21”; portability check | Source-build instructions. The “Check JDK 21 / Boot 4 portability” line is mildly dated. |

---

## What #969 will and will not fix

After #969 (still unmerged): pom / GHA / service images stay Java 25; Jenkins controller becomes `2.580.2-lts-jdk25`; Maven stays `/opt/java/temurin-25`; `docs/ci-contract.yaml`, the Dockerfile comments, and the Jenkins README row are updated.

Still stale after #969 unless edited separately: **`docs/ARCHITECTURE.md:218` (P0)** and the P2 current-doc mismatches above (cutover, jenkins-ai plan, harvest index, pricing-AI / trycompai / price-book this-repo lines, Claude allowlist).

#969 does not prove the new jdk25 controller *starts*. A manual Jenkins smoke test remains a merge-checklist item; this audit did not run one.

---

## Limits

- Box `gh` was not used (401). Inspection and the branch push used the open-crm-cpq Sprite. The Sprite token has Contents/PR write for this repo; workflow files were only *read*.
- Service and Jenkins images were not built or pulled; runtime for those rows is the declared tag.
- Controller / JCasC boot was not smoke-tested.
- `docs/forge/**` was classified as historical as a directory (38 files), not line-audited as current SSOT.
- Linear and Notion were not re-queried in this pass (see the 1:08 PM CST box audit).
- Sister-repo search used GitHub code search on each repo’s default branch plus commit search. Code search can miss unindexed paths; findings above are what the MCP returned, plus one file read on cmano-clone.

No secrets or tokens are included in this file.
