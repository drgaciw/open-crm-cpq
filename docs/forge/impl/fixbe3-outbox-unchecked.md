---
status: green
id: fixbe3-outbox-unchecked
wave: WARN-BE
pr: FIX-BE-3
---

# fixbe3-outbox-unchecked

## Summary

FIX-BE-3 (test only): type Mockito matchers in `OutboxRelayPublisherTest` so javac `-Xlint:unchecked` is clean. `any()` → `anyList()` on `saveAll` / `deleteAll`; `mock(SendResult.class)` → `SendResult<String, String> sendResult = mock()`. **No production change.** Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. JaCoCo line **0.80** not lowered. JAVA_HOME **Temurin 25.0.4.1 LTS**. Did **not** edit `docs/forge/00-orchestrator-status.md`. Did **not** steal w8 `arch-pi`.

## GitNexus

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/fix-be3` (`fix/forge-fixbe3-outbox-unchecked`)  
Index: stale vs tip (plan: **skip analyze**). UNKNOWN ≠ unused; `rg` confirm before commit.

Pre-edit impact on `OutboxRelayPublisherTest`: **UNKNOWN** (0 callers + `riskNote`). Confirmed with `rg`: only this test class plus catalog/IT notes. Exclusive files only. Dual-read YAML and Jacoco `<minimum>0.80</minimum>` hunks empty.

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "OutboxRelayPublisherTest" --direction upstream --repo /home/username01/Projects/open-crm-cpq
rg -n 'OutboxRelayPublisherTest' --glob '!docs/forge/impl/fixbe3-outbox-unchecked.md'
rg -n 'legacy-local-read-enabled' backend/services/quote-service/src/main/resources/application.yml
rg -n '<minimum>0.80</minimum>' backend/services/crm-service/pom.xml backend/services/pricing-service/pom.xml
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq
```

Pre-commit `detect-changes --scope all`: **1 file / 3 symbols / 0 processes / risk LOW**. No `partial`/`truncated`. Symbols: `OutboxRelayPublisherTest`, `publishPendingEvents_NoEvents`, `publishPendingEvents_SuccessfulPublish` (test methods only). Impl note was untracked at this run. Dual-read YAML and Jacoco hunks empty.

## Files

| Path | Change |
|------|--------|
| `backend/libs/common/src/test/java/com/opencrm/common/event/OutboxRelayPublisherTest.java` | Typed `anyList()` + `SendResult<String,String> mock()` |
| `docs/forge/impl/fixbe3-outbox-unchecked.md` | This note |

## TDD

**RED** (Temurin 25.0.4.1 LTS, pre-edit):

Maven `test-compile` INFO (parent does not pass `-Xlint:unchecked`):

```
OutboxRelayPublisherTest.java uses unchecked or unsafe operations.
Recompile with -Xlint:unchecked for details.
```

`javac -Xlint:unchecked` on this file only (3 warnings, all line 62 `mock(SendResult.class)`):

```
unchecked method invocation: method completedFuture ... found: SendResult
unchecked conversion ... found: SendResult
unchecked conversion ... found: CompletableFuture
```

Raw `any()` on `saveAll` / `deleteAll` did not emit `-Xlint:unchecked` on this javac; still typed to `anyList()` per catalog.

**GREEN** (Temurin 25.0.4.1 LTS, this tree):

- `javac -Xlint:unchecked -Werror` on this file: **EXIT 0**, zero warnings
- Maven `test-compile` no longer emits “uses unchecked or unsafe operations” / “Recompile with `-Xlint:unchecked`” for this file
- `./mvnw -B -pl libs/common -am test`: **BUILD SUCCESS**. `OutboxRelayPublisherTest` Tests run: **6**, Failures: 0. `libs/common` Tests run: **86**, Failures: 0, Errors: 0, Skipped: 0

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
./mvnw -B -pl libs/common -am test
```

## Dual-read / holds

```yaml
# backend/services/quote-service/src/main/resources/application.yml:89
legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

YAML/env **untouched**. Jacoco `<minimum>0.80</minimum>` **untouched**. No `-Werror`. No `-nowarn`. No production sources.

## Acceptance

- [x] Exclusive files only (this test + this impl note)
- [x] `anyList()` on `saveAll` / `deleteAll` never-verifies
- [x] Typed `SendResult<String, String> sendResult = mock()`
- [x] `-Xlint:unchecked` clean on this test file
- [x] `libs/common` tests pass on Temurin 25.0.4.1 LTS
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] Jacoco **0.80** not lowered
- [x] No production change; no w8 steal; no orchestrator-status edit
