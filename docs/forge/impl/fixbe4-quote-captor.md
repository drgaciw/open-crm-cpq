---
status: green
id: fixbe4-quote-captor
wave: FIX-BE-4
pr: FIX-BE-4
---

# fixbe4-quote-captor

## Summary

FIX-BE-4 (BE test only): replace raw `ArgumentCaptor.forClass(List.class)` in `QuoteServiceSubmitForReviewTest` with typed `ArgumentCaptor<List<Long>>` via `ArgumentCaptor.captor()` (Mockito **5.20.0**, Boot 4.0.6). No production change. Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. JaCoCo line **0.80** not lowered. JAVA_HOME **Temurin 25.0.4.1 LTS** (never 27). No `-Werror`. No `-nowarn`. No `-T1C`. Did **not** edit `docs/forge/00-orchestrator-status.md`. Did **not** steal w8 `arch-pi`. Did **not** implement FIX-BE-1..3.

Catalog: [warnbe-java25-catalog.md](warnbe-java25-catalog.md) FIX-BE-4 (IT-1 `#528`).

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/fix-be4` (`fix/forge-fixbe4-quote-captor`)  
Index: `04909db`, **36 commits behind** HEAD — analyze skipped (WARN-BE / plan 38). UNKNOWN ≠ unused.

| Symbol | Risk | Confirmation |
|--------|------|----------------|
| `QuoteServiceSubmitForReviewTest` | **UNKNOWN** (0 callers + riskNote) | **rg:** only this class + catalog / IT-1 / tdd-be-clean / g5 docs. JUnit discovery, not production. |
| `submitForReviewDeduplicatesProductIds` | **UNKNOWN** | same; test method |

**WARN:** UNKNOWN is unresolved, not low. Text search confirms JUnit-only. Test-only one-line matcher change; no `QuoteService` / dual-read YAML / Jacoco pom edits.

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "QuoteServiceSubmitForReviewTest" --direction upstream --repo /home/username01/Projects/open-crm-cpq
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "submitForReviewDeduplicatesProductIds" --direction upstream --repo /home/username01/Projects/open-crm-cpq
rg -n 'QuoteServiceSubmitForReviewTest' --glob '*.{java,md,xml,yml}'
rg -n 'legacy-local-read-enabled' backend/services/quote-service/src/main/resources/application.yml
rg -n '<minimum>0.80</minimum>' backend/services/crm-service/pom.xml backend/services/pricing-service/pom.xml
```

## Files

| Path | Change |
|------|--------|
| `backend/services/quote-service/src/test/java/com/opencrm/quote/quote/QuoteServiceSubmitForReviewTest.java` | `ArgumentCaptor.forClass(List.class)` → `ArgumentCaptor.captor()`; type remains `ArgumentCaptor<List<Long>>` |
| `docs/forge/impl/fixbe4-quote-captor.md` | This note |

`QuoteServiceCloneTest` / `QuoteServiceCalculateTest` raw captors **untouched** (not exclusive). Production `QuoteService` **untouched**.

## TDD

**RED** (before edit, Temurin 25.0.4.1 LTS, `newgrp docker`):

```text
./mvnw -B -pl services/quote-service -am test-compile
[INFO] Compiling 9 source files with javac [debug parameters release 25] to target/test-classes
[INFO] .../QuoteServiceSubmitForReviewTest.java uses unchecked or unsafe operations.
[INFO] .../QuoteServiceSubmitForReviewTest.java: Recompile with -Xlint:unchecked for details.
BUILD SUCCESS
```

Site: line 144 `ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);` (raw `List`).

**GREEN** (after `ArgumentCaptor.captor()`):

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
export PATH="$JAVA_HOME/bin:$PATH"
cd backend && newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B -pl services/quote-service -am test
EOF
```

| Check | Result |
|-------|--------|
| Default javac INFO on this test | **gone** (9 test sources recompiled; no `QuoteServiceSubmitForReviewTest` unchecked note) |
| `-DcompilerArgument=-Xlint:unchecked` test-compile | **BUILD SUCCESS**; no unchecked INFO/WARNING on this file |
| `QuoteService.submitForReview S2D-3a preflight` | Tests run: **4**, Failures: 0, Errors: 0 |
| quote-service surefire | Tests run: **31**, Failures: 0, Errors: 0 |
| Reactor (`-am`) | common **86**, security **13**, quote **31** — all SUCCESS |
| Wall | 2026-09-15T09:11:23-05:00, **01:32 min**, **BUILD SUCCESS** |

JAVA_HOME:

```text
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
```

Mockito `ArgumentCaptor.captor()` is on Boot 4.0.6 `mockito.version` **5.20.0**. `@Captor` was the allowed alternative; local `captor()` keeps the field out of the class.

## Dual-read / holds

```yaml
# backend/services/quote-service/src/main/resources/application.yml:89
legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

YAML/env **untouched**. Jacoco `<minimum>0.80</minimum>` **untouched** in crm + pricing poms (`git diff` hunks empty).

- NgRx **held**; Activities API **not invented**; CI `#238` / Kafka `#241` **not started**
- **R2** price-book list-row delete **held**; subscriptions FE **held**; GLM-5 **not runtime**
- Boot **4.0.6**; JAVA_HOME **25.0.4.1 LTS** — never 27
- Did **not** edit `docs/forge/00-orchestrator-status.md`
- Did **not** steal FE panes or w8 `arch-pi`
- Did **not** flip dual-read; did **not** add `-Werror` / `-nowarn` / `--add-opens`

### detect-changes (pre-commit)

`node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq` from this worktree:

```text
Changes: 1 files, 2 symbols
Affected processes: 0
Risk level: low
Changed symbols:
  Class QuoteServiceSubmitForReviewTest
  Method submitForReviewDeduplicatesProductIds
```

No `partial` / `truncated`. Checkout confirmed: this worktree (`fix/forge-fixbe4-quote-captor`); primary `main` was not the diff. Untracked impl note is docs-only (not an indexed symbol). Dual-read YAML and Jacoco `0.80` hunks empty.

## Acceptance

- [x] `ArgumentCaptor.forClass(List.class)` removed from exclusive test
- [x] Typed `ArgumentCaptor<List<Long>>` via `ArgumentCaptor.captor()`
- [x] No production / pom / YAML / Jacoco change
- [x] RED: unchecked INFO on this test
- [x] GREEN: that INFO gone; `-Xlint:unchecked` clean for this file; tests pass
- [x] Exclusive files only (this test + this note)
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] Jacoco **0.80** not lowered
