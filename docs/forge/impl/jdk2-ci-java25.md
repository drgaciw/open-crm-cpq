---
status: done
id: jdk2-ci-java25
wave: JDK25
pr: JDK2
---

# jdk2-ci-java25

## Summary

JDK2 (BE / CI only): retarget GitHub Actions, Jenkins Maven toolchain, and service JRE images from Java 21 to **Temurin 25 LTS**. Pin Node to **22.22.3** (Angular 22 min) in GHA + Jenkins so NG2 does not edit `ci.yml`. Jenkins WAR stays **`2.492.3-lts-jdk21`**. Boot parent stays **4.0.6**. Dual-read YAML unchanged. No `backend/pom.xml`. No `frontend/**`. No `ARCHITECTURE.md`. No `00-orchestrator-status.md`. No GHA push/PR re-enable. **#238** untouched.

Depends: JDK1 on `origin/main` (`0bd53e5`, #517) — parent `java.version` is already **25**.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / confirmation |
|--------|------|-------------------------|
| `java.version` | **UNKNOWN** → treat **HIGH** | Maven property, not a Java symbol. JDK2 does **not** edit `backend/pom.xml`. **rg:** CI pins only in `ci.yml` / Jenkins Dockerfile / `ci-contract.yaml` / seven service Dockerfiles |
| GHA `java-version` / Jenkins `JAVA_HOME` | **UNKNOWN** → **HIGH** | Serializes all Maven stages. **rg:** four GHA `setup-java` jobs + Jenkins `ENV JAVA_HOME=/opt/java/temurin-25` |
| `eclipse-temurin` JRE images | **UNKNOWN** → **MED** | Seven service Dockerfiles. **rg:** crm, gateway, quote, product, pricing, ai, pricebook only |

**WARN:** UNKNOWN ≠ unused. Jenkins `JAVA_HOME` serializes every Maven stage; JRE images serialize seven runtime containers. Confirmed with `rg`, not an empty caller set.

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/jdk2` (`feat/forge-jdk2-ci-java25`)  
Index: `04909db` (stale vs HEAD `0bd53e5`) — skip analyze  
Impact CLI: `node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "java.version" --direction upstream --repo /home/username01/Projects/open-crm-cpq` → `Target 'java.version' not found`, `risk: UNKNOWN`

## Files

| Path | Change |
|------|--------|
| `.github/workflows/ci.yml` | `java-version: '25'` temurin on shared-libraries, backend-unit, backend-integration, security-audit. Job titles drop “Java 21”. `node-version: '22.22.3'` on frontend + security-audit. `on: workflow_dispatch` only |
| `infra/jenkins/jenkins/Dockerfile` | Keep `FROM jenkins/jenkins:2.492.3-lts-jdk21`. Install Node **22.22.3** tarball. Install Temurin **25** at `/opt/java/temurin-25` and `ENV JAVA_HOME` for Maven. Do **not** prepend `JAVA_HOME/bin` to `PATH` (jenkins.sh `exec java` must stay image JDK 21) |
| `docs/ci-contract.yaml` | `last_reviewed: "2026-09-15"`. `toolchain` notes JDK 25 + Node 22.22.3 + WAR jdk21 + `eclipse-temurin:25-jre-alpine` |
| `backend/services/{crm,gateway,quote,product,pricing,ai,pricebook}-service/Dockerfile` | `FROM eclipse-temurin:25-jre-alpine` |
| `docs/forge/impl/jdk2-ci-java25.md` | This note |

## Acceptance

- [x] GHA `java-version: '25'` temurin on all Java jobs; job titles drop “Java 21”
- [x] GHA `node-version: '22.22.3'` (frontend + security-audit)
- [x] Jenkins Dockerfile installs Temurin 25 and exports `JAVA_HOME` for Maven; Node **22.22.3** (pinned tarball)
- [x] Jenkins WAR stays `2.492.3-lts-jdk21` — not moved to 27
- [x] Service images `eclipse-temurin:25-jre-alpine`
- [x] `python3 .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py` green
- [x] `docs/ci-contract.yaml` notes JDK 25 + Node 22.22.3; `last_reviewed: "2026-09-15"`
- [x] **No** GHA push/PR re-enable. **#238** untouched
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] `backend/pom.xml` untouched (java.version 25 from JDK1; Boot 4.0.6)
- [x] No `frontend/**`, no `ARCHITECTURE.md`, no `00-orchestrator-status.md`

## Verify

```bash
python3 .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py
rg -n "java-version:|node-version:|Java 21" .github/workflows/ci.yml
rg -n "eclipse-temurin" backend/services/*/Dockerfile
rg -n "APPROVALS_LEGACY_LOCAL_READ" backend/services/quote-service/src/main/resources/application.yml
```

### detect-changes (pre-commit)

```bash
cd /home/username01/Projects/open-crm-cpq/.worktrees/jdk2
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq
```

Result: **Diff touched 10 file(s) but no indexed symbols overlap those hunks — not a clean tree.** No `partial`/`truncated`. YAML / Dockerfile / contract pin hunks are not Java symbols — proceed after `rg` confirm (GHA `java-version: '25'` + `node-version: '22.22.3'`; Jenkins `JAVA_HOME=/opt/java/temurin-25` + Node 22.22.3; seven `eclipse-temurin:25-jre-alpine`; contract `last_reviewed: "2026-09-15"`). Impl note is docs-only (untracked at detect-changes time).

## Out of scope

- `backend/pom.xml` (JDK1)
- `frontend/**` (NG2)
- `docs/ARCHITECTURE.md` (X1 FE after JDK2 ∧ NG4)
- `docs/forge/00-orchestrator-status.md`
- Dual-read flip; Activities API; #238; #241; R2; NgRx; Track 2 Spring AI; GLM-5 runtime
- Boot 4.1 / Boot 3; Spring AI bump; JDK 27
