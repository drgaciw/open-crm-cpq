---
status: done
id: x1-upgrade-closeout
wave: X1
pr: X1
---

# x1-upgrade-closeout

## Summary

X1 (FE docs only): land **Java 25** and **Angular 22** in the architecture SSOT after JDK2 ∧ NG4 on `origin/main`. Spring Boot parent stays **4.0.6**. Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. Signals + HttpClient; NgRx still **held**. `newgrp docker` retained on the §8 IT example. JAVA_HOME example is mise **Temurin 25** (not 21.0.2, not 27). Did **not** edit `docs/forge/00-orchestrator-status.md`, `backend/pom.xml`, or `frontend/package.json`.

Depends: JDK2 `#518` (`3993e0ef`) ∧ NG4 `#521` (`c4e0d721`) on `origin/main`.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/x1` (`docs/forge-x1-upgrade-closeout`)  
Index: `04909db` (stale vs HEAD `c4e0d721`) — skip analyze

| Target | Risk | Notes |
|--------|------|-------|
| `java.version` | **UNKNOWN** | Maven property, not a Java symbol. Impact: `Target 'java.version' not found`. X1 does **not** edit `backend/pom.xml`. |
| `@angular/core` | **UNKNOWN** | Package name, not a TS symbol. X1 does **not** edit `frontend/package.json`. |

UNKNOWN ≠ unused. Docs-only hunks: confirm with `rg`, not an empty caller set.

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "java.version" \
  --direction upstream --repo /home/username01/Projects/open-crm-cpq
```

**rg confirm (pre-edit):** Java 21 SSOT rows only in `docs/ARCHITECTURE.md` (Backend row + `backend/pom.xml` file map + §8 `JAVA_HOME` 21.0.2) and `AGENT.md` landed Backend row. Frontend rows had no Angular major. Dual-read still `true` in quote-service YAML. `backend/pom.xml` already `java.version` **25**. `@angular/core` already **22.1.6**.

## Depends (already on `main`)

| ID | Result | Tip |
|----|--------|-----|
| JDK1 | Parent `java.version` / compiler source+target **25**; Boot **4.0.6** | `0bd53e5` (#517) |
| JDK2 | GHA Java **25** temurin; Node **22.22.3**; Temurin 25 JRE images | `3993e0ef` (#518) |
| NG4 | experimental WebMCP on quotes-list; `@angular/core` **22.1.6** | `c4e0d721` (#521) |

## Files

| Path | Change |
|------|--------|
| `docs/ARCHITECTURE.md` | Backend landed row Java **25** (Boot **4.0.6** kept). Frontend landed row Angular **22** (Signals + HttpClient; NgRx held). File map `backend/pom.xml` Java **25**. §8 `JAVA_HOME` → mise `temurin-25`; `newgrp docker` retained. Holds / dual-read **untouched**. |
| `AGENT.md` | Landed table **only**: Backend Java **25**; Frontend Angular **22**. File-reference table **not** edited (exclusive: landed table). |
| `docs/forge/impl/x1-upgrade-closeout.md` | This note |

Did **not** edit `docs/forge/00-orchestrator-status.md`, `backend/pom.xml`, `frontend/package.json`, CI, or application code.

## Toolchain (docs example)

```text
mise ls java → temurin-25.0.4+101.0.LTS (also 21.0.2 and 27.0.0 / latest)
JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25
openjdk version "25.0.4.1" 2026-08-18 LTS
```

§8 uses the `temurin-25` symlink (mise 25 LTS). Do **not** point `JAVA_HOME` at `21.0.2` or `27.0.0`.

## Holds (unchanged)

- Dual-read default `${APPROVALS_LEGACY_LOCAL_READ:true}` — YAML/env **untouched**
- NgRx; Activities API; CI #238; Kafka #241; **R2** list-row delete
- Subscriptions FE; GLM-5 runtime; Track 2 Spring AI
- Boot 4.1 / Boot 3; JDK 27
- `docs/forge/00-orchestrator-status.md`

## Acceptance

- [x] JDK2 **and** NG4 on `origin/main`
- [x] ARCHITECTURE.md Backend Java **25**; Boot **4.0.6**
- [x] ARCHITECTURE.md Frontend Angular **22**; Signals + HttpClient; NgRx held
- [x] File map `backend/pom.xml` Java **25**
- [x] §8 JAVA_HOME mise **25** (`temurin-25`); not 21.0.2; not 27; `newgrp docker` retained
- [x] AGENT.md landed table matches (Java 25 / Angular 22)
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] Holds unchanged
- [x] No `00-orchestrator-status.md`, no `backend/pom.xml`, no `frontend/package.json`

## Verify

```bash
rg -n "Java 21|Java \\*\\*21|21\\.0\\.2|java/27" docs/ARCHITECTURE.md AGENT.md
rg -n "Java \\*\\*25|Angular \\*\\*22|temurin-25" docs/ARCHITECTURE.md AGENT.md
rg -n "APPROVALS_LEGACY_LOCAL_READ" backend/services/quote-service/src/main/resources/application.yml
rg -n "<java.version>" backend/pom.xml
rg -n '"@angular/core"' frontend/package.json
```

Expected: no Java 21 / 21.0.2 / JDK 27 in ARCHITECTURE.md; AGENT.md landed table Java 25 + Angular **22**; dual-read `true`; pom `25`; core `22.1.6`. AGENT.md file-reference row may still say Java 21 (not in X1 exclusive set).
