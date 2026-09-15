---
status: done
id: val1-agent-md-file-ref-java25
wave: VAL-1
pr: VAL-1
---

# val1-agent-md-file-ref-java25

## Summary

VAL-1 (docs only): align `AGENT.md` file-reference row for `backend/pom.xml` from Java **21** to Java **25** so agent SSOT matches `docs/ARCHITECTURE.md` and parent `java.version` **25**. Spring Boot parent stays **4.0.6**. Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. Did **not** edit `docs/forge/00-orchestrator-status.md`, `backend/pom.xml`, `frontend/package.json`, CI, or `docs/ARCHITECTURE.md`.

Depends: X1 `#522` (`302bcd9c`) on `origin/main`. Architect pi validation `/tmp/jdk-ng-plan-validation.md` verdict **IMPLEMENTED-WITH-GAPS** (this one-line drift).

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/val1` (`docs/agent-md-file-ref-java25`)  
Index: `04909db` (stale vs HEAD `302bcd9c`) — skip analyze (docs-only)

| Target | Risk | Notes |
|--------|------|-------|
| `java.version` | **UNKNOWN** | Maven property / markdown, not a Java symbol. Impact: `Target 'java.version' not found`. VAL-1 does **not** edit `backend/pom.xml`. |

UNKNOWN ≠ unused. Confirm with `rg`, not an empty caller set.

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "java.version" \
  --direction upstream --repo /home/username01/Projects/open-crm-cpq
rg -n 'Java 21' AGENT.md docs/ARCHITECTURE.md
```

**rg confirm (pre-edit / RED):** `AGENT.md:246` `backend/pom.xml` file-reference still Java 21. `docs/ARCHITECTURE.md` has **no** `Java 21`. Dual-read still `true` in quote-service YAML. `origin/main:backend/pom.xml` already `<java.version>25</java.version>` and Boot **4.0.6**.

## Files

| Path | Change |
|------|--------|
| `AGENT.md` | File-reference `backend/pom.xml` Java **21** → **25**. Landed table already Java 25 / Angular 22 (X1). |
| `docs/forge/impl/val1-agent-md-file-ref-java25.md` | This note |

Did **not** edit `docs/forge/00-orchestrator-status.md`, `backend/pom.xml`, `frontend/package.json`, CI, or `docs/ARCHITECTURE.md`.

## Holds (unchanged)

- Dual-read default `${APPROVALS_LEGACY_LOCAL_READ:true}` — YAML/env **untouched**
- NgRx; Activities API; CI #238; Kafka #241; **R2** list-row delete
- Subscriptions FE; GLM-5 runtime
- Boot 4.0.6; JAVA_HOME Temurin 25 (never 27)

## TDD (docs)

RED: `rg -n 'backend/pom.xml.*Java 21' AGENT.md` matched line 246.

GREEN:

```bash
rg -n 'backend/pom.xml.*Java 21' AGENT.md   # empty
rg -n 'backend/pom.xml.*Java 25' AGENT.md   # match
git show origin/main:backend/pom.xml | rg 'java.version|4.0.6'
rg -n 'APPROVALS_LEGACY_LOCAL_READ' backend/services/quote-service/src/main/resources/application.yml
```

## Acceptance

- [x] AGENT.md file-reference `backend/pom.xml` Java **25**
- [x] `rg` Java 21 on that row empty
- [x] `origin/main` pom `java.version` **25**; Boot **4.0.6**
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] No `00-orchestrator-status.md`, no pom/package/CI/ARCHITECTURE.md edits
