---
status: done
id: hyg1-stack-docs-java25
wave: HYG-1
pr: HYG-1
---

# hyg1-stack-docs-java25

## Summary

HYG-1 (docs-only stale-stack hygiene): retarget non-SSOT stack strings from Java **21** / Boot **3.2** / Angular **21** to Java **25** / Boot **4.0.6** / Angular **22**. Fold HOST-1 docs into `backend/README.md` toolchain (`JAVA_HOME` Temurin 25, never 21.0.2, never 27). Bump PowerShell Java floor from 21+ to **25** (CI/Docker already enforce 25). Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. Did **not** edit `docs/forge/00-orchestrator-status.md`, `docs/ARCHITECTURE.md`, `AGENT.md`, `backend/pom.xml`, `frontend/package.json`, CI, harvest `docs/plans/*`, or `MODERNIZATION_PLAN.md`.

Depends: VAL-1 `#523` (`e5d60bbb`) on `origin/main`. Architect next-steps `/tmp/jdk-ng-next-steps.md` verdict **UPGRADE COMPLETE**.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/hyg1` (`docs/forge-hyg1-stack-docs-java25`)
Index: `04909db` (stale vs HEAD `e5d60bbb`) — skip analyze (docs/script strings)

| Target | Risk | Notes |
|--------|------|-------|
| `java.version` | **UNKNOWN** | Maven property / markdown / comment strings, not a Java symbol. Impact: `Target 'java.version' not found`. HYG-1 does **not** edit `backend/pom.xml`. |

UNKNOWN ≠ unused. Confirm with `rg` on the exclusive files, not an empty caller set.

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "java.version" \
  --direction upstream --repo /home/username01/Projects/open-crm-cpq
```

**rg confirm (pre-edit / RED):**

```bash
rg -n 'Java 21|Boot 3\.2|Angular 21' \
  backend/README.md backend/Start-AiService.ps1 \
  backend/scripts/build-and-run.sh infra/jenkins/ai-assist/code_review.py
```

Hits: `backend/README.md` Boot 3.2 / Java 21 / Boot 3.2.2; `backend/Start-AiService.ps1` Java 21+ (comment + floor); `backend/scripts/build-and-run.sh` Java 21+; `infra/jenkins/ai-assist/code_review.py` Java 21 / Angular 21. Dual-read still `true` in quote-service YAML. `origin/main:backend/pom.xml` already `<java.version>25</java.version>` and Boot **4.0.6**.

## Files

| Path | Change |
|------|--------|
| `backend/README.md` | Boot **4.0.6** / Java **25**. Prerequisites + toolchain: `JAVA_HOME=…/temurin-25` (HOST-1 fold-in). Tech stack Java **25**, Boot **4.0.6**, Spring Cloud **2025.1.1** (matches parent POM). |
| `backend/Start-AiService.ps1` | Prereq floor Java **25** (comments, `-lt 25`, error strings). No other behavior change. |
| `backend/scripts/build-and-run.sh` | Comment prereq Java **25**. |
| `infra/jenkins/ai-assist/code_review.py` | Reviewer stack string: Java **25**, Boot **4.0.6**, Angular **22**. |
| `docs/forge/impl/hyg1-stack-docs-java25.md` | This note |

Did **not** edit `docs/forge/00-orchestrator-status.md`, `docs/ARCHITECTURE.md`, `AGENT.md`, `backend/pom.xml`, `frontend/package.json`, CI, harvest `docs/plans/*`, or `MODERNIZATION_PLAN.md`.

## Holds (unchanged)

- Dual-read default `${APPROVALS_LEGACY_LOCAL_READ:true}` — YAML/env **untouched**
- NgRx; Activities API; CI #238; Kafka #241; **R2** list-row delete
- Subscriptions FE; GLM-5 runtime
- Boot 4.0.6; JAVA_HOME Temurin 25 (never 27)
- VER-1 (separate pane; this PR does not start it)

## TDD (docs)

RED: `rg -n 'Java 21|Boot 3\.2|Angular 21' backend/README.md backend/Start-AiService.ps1 backend/scripts/build-and-run.sh infra/jenkins/ai-assist/code_review.py` → hits.

GREEN:

```bash
rg -n 'Java 21|Boot 3\.2|Angular 21' \
  backend/README.md backend/Start-AiService.ps1 \
  backend/scripts/build-and-run.sh infra/jenkins/ai-assist/code_review.py   # empty
rg -n 'Java 25|Boot 4\.0\.6|Angular 22' \
  backend/README.md backend/Start-AiService.ps1 \
  backend/scripts/build-and-run.sh infra/jenkins/ai-assist/code_review.py   # present in each
git diff --name-only   # exclusive list + impl note
rg -n 'APPROVALS_LEGACY_LOCAL_READ' backend/services/quote-service/src/main/resources/application.yml
```

## Acceptance

- [x] Stale `Java 21` / `Boot 3.2` / `Angular 21` empty on exclusive files
- [x] Each exclusive file mentions Java **25**; README + `code_review.py` mention Boot **4.0.6**; `code_review.py` mentions Angular **22**
- [x] PowerShell Java floor **25** (`-lt 25`)
- [x] `backend/README.md` toolchain documents `JAVA_HOME` Temurin **25**
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] No `00-orchestrator-status.md`, no pom/package/CI/ARCHITECTURE.md/AGENT.md/harvest plans
- [x] Did not start VER-1

## Verify

```bash
rg -n 'Java 21|Boot 3\.2|Angular 21' \
  backend/README.md backend/Start-AiService.ps1 \
  backend/scripts/build-and-run.sh infra/jenkins/ai-assist/code_review.py
rg -n 'APPROVALS_LEGACY_LOCAL_READ' backend/services/quote-service/src/main/resources/application.yml
```

### detect-changes (pre-commit)

```bash
cd /home/username01/Projects/open-crm-cpq/.worktrees/hyg1
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq
```

Result: **Changes: 4 files, 6 symbols. Affected processes: 0. Risk: low.** No `partial`/`truncated`. Changed symbols are README markdown sections + `SYSTEM_PROMPT` in `code_review.py`. `Start-AiService.ps1` / `build-and-run.sh` are version-string hunks with no indexed overlap. Impl note untracked at detect-changes time. Docs-only hunks OK after `rg` confirm (stale `Java 21|Boot 3.2|Angular 21` empty; Java 25 present in each exclusive file).

## Out of scope

- VER-1 tip verification
- `docs/ARCHITECTURE.md` / `AGENT.md` (already Java 25)
- `backend/pom.xml` / `frontend/package.json` / CI
- harvest `docs/plans/*` (Java 21 language by design)
- Dual-read flip; Activities API; #238; #241; R2; NgRx; GLM-5 runtime
- mise host pin (HOST-1 remains host-side HOLD; only README `JAVA_HOME` docs folded in)
