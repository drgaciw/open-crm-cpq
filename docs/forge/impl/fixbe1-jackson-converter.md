---
status: green
id: fixbe1-jackson-converter
wave: FIX-BE-1
pr: FIX-BE-1
---

# fixbe1-jackson-converter

## Summary

FIX-BE-1 (BE tests only): replace Spring Framework 7 `MappingJackson2HttpMessageConverter` (deprecated **for removal**) with `org.springframework.http.converter.json.JacksonJsonHttpMessageConverter` in four standalone `MockMvc` controller tests. Catalog: `docs/forge/impl/warnbe-java25-catalog.md` on `d4de186c` (WARN-BE `#530`). No production sources. No poms. No API contract change. Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. JaCoCo **0.80** not lowered. No `-Werror`. No `-nowarn`. JAVA_HOME **Temurin 25.0.4.1 LTS** (never 27). Did **not** steal FIX-BE-2/3/4 or FE. Did **not** edit `docs/forge/00-orchestrator-status.md`. Did **not** steal w8 `arch-pi`.

`JacksonJsonHttpMessageConverter` constructs a Jackson 3 `tools.jackson.databind.json.JsonMapper` (no-arg ctor). Tests keep `com.fasterxml.jackson.databind.ObjectMapper` only for `readTree` leak-field assertions. Constructor taking Jackson 2 `ObjectMapper` does not exist on the replacement type.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/fix-be1` (`fix/forge-fixbe1-jackson-converter`)  
Index: `04909db` on `main` (stale vs tip). UNKNOWN ≠ unused; `rg` confirm.

| Symbol | Risk | Confirmation |
|--------|------|----------------|
| `MappingJackson2HttpMessageConverter` | **UNKNOWN** (`target-not-found`; Spring type) | **rg:** four exclusive test files only (type use + constructor). Catalog + IT-2 note. No production `backend/**` hit. |

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "MappingJackson2HttpMessageConverter" --direction upstream --repo /home/username01/Projects/open-crm-cpq
rg -n 'MappingJackson2HttpMessageConverter' backend
```

**WARN:** UNKNOWN is unresolved, not low. Confirmed with `rg` before edits. Test-only Spring type; no HIGH/CRITICAL product callers.

## Files

| Path | Change |
|------|--------|
| `backend/services/configuration-engine/src/test/java/com/opencrm/configuration/session/ConfigurationSessionControllerTest.java` | `JacksonJsonHttpMessageConverter` |
| `backend/services/approval-routing/src/test/java/com/opencrm/approval/controller/ApprovalControllerTest.java` | same |
| `backend/services/document-generation/src/test/java/com/opencrm/document/controller/DocumentControllerTest.java` | same |
| `backend/services/contract-asset-management/src/test/java/com/opencrm/contract/controller/SubscriptionControllerTest.java` | same |
| `docs/forge/impl/fixbe1-jackson-converter.md` | This note |

## TDD

**RED** (`./mvnw -B -pl services/configuration-engine,services/approval-routing,services/document-generation,services/contract-asset-management -am test-compile`, Temurin 25.0.4.1 LTS): 8 javac `[WARNING]`s (2 per file: type use + constructor) on `MappingJackson2HttpMessageConverter` … `deprecated and marked for removal`. BUILD SUCCESS.

**GREEN:** those 8 `[WARNING]`s gone (`rg MappingJackson2HttpMessageConverter` in Maven log = **none**; `rg '\[WARNING\].*\.java'` = **none**). Tests still pass:

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
export PATH="$JAVA_HOME/bin:$PATH"
cd backend && newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B -pl services/configuration-engine,services/approval-routing,services/document-generation,services/contract-asset-management -am test
EOF
```

Result (2026-09-15): **BUILD SUCCESS**. JDK `openjdk version "25.0.4.1" 2026-08-18 LTS` (Temurin-25.0.4.1+1). `newgrp docker`.

| Module | Tests run | Failures | Errors | Skipped |
|--------|-----------|----------|--------|---------|
| libs/common | 86 | 0 | 0 | 0 |
| libs/security | 13 | 0 | 0 | 0 |
| configuration-engine | 76 | 0 | 0 | 0 |
| approval-routing | 20 | 0 | 0 | 0 |
| document-generation | 13 | 0 | 0 | 0 |
| contract-asset-management | 18 | 0 | 0 | 0 |

Controller slices: ConfigurationSession **6**, Approval **6**, Document **3**, Subscription **5** — all 0 fail.

## Dual-read / holds

```yaml
# backend/services/quote-service/src/main/resources/application.yml:89
legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

YAML/env **untouched**. Jacoco `<minimum>0.80</minimum>` **untouched** in crm + pricing poms.

- NgRx **held**; Activities API **not invented**
- FIX-BE-2 `UNPROCESSABLE_ENTITY` / FIX-BE-3 Outbox unchecked / FIX-BE-4 Quote captor **not stolen**
- Boot **4.0.6**; JAVA_HOME **25.0.4.1 LTS** — never 27

## detect-changes (pre-commit)

`node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq` from this worktree (tests only, before impl note): **4 files / 2 symbols / 0 processes / risk LOW**. Changed symbols: `SubscriptionControllerTest`, `setUp`. No `partial`/`truncated`. Checkout is this worktree. After impl note the fifth file is docs-only.

## Acceptance

- [x] RED: javac `[WARNING]` on `MappingJackson2HttpMessageConverter` in the four exclusive tests
- [x] GREEN: those warnings gone; slice tests pass on Temurin 25.0.4.1 LTS
- [x] Exclusive files only (4 tests + this note)
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] Jacoco **0.80** not lowered
- [x] No `-Werror` / `-nowarn`
- [x] No FIX-BE-2/3/4, no FE, no w8, no `00-orchestrator-status.md`

## Out of scope

- FIX-BE-2 `HttpStatus.UNPROCESSABLE_ENTITY` → `UNPROCESSABLE_CONTENT`
- FIX-BE-3 `OutboxRelayPublisherTest` unchecked
- FIX-BE-4 `QuoteServiceSubmitForReviewTest` raw `List` captor
- Production converters / `spring-boot-jackson2` removal
- Dual-read YAML flip; Activities; NgRx; FE
