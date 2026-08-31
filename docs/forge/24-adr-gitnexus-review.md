# Wave-adjacent ADR GitNexus review (2026-08-31)

**Architect:** `opencrm-architect`  
**Worktree:** `.worktrees/architect` / `docs/forge-architect`  
**Code tip merged:** `origin/main` @ `7874eac` (Wave N N1+N3; N2 still forging)  
**GitNexus:** `node …/run.cjs` `-r /home/username01/Projects/open-crm-cpq`  
**Index:** `e09a4a3` (**~5 commits behind** `7874eac` — **WARN**; CLI may also flag untracked `.worktrees/` as false stale)  
**UNKNOWN ≠ unused** — confirm with `rg`.

**Out of scope:** Wave N implementation; dual-read YAML flip; Activities API; #238/#241; product-validate deletion; gateway domain validation; WhatsApp #430; full SpEL; Kafka S2B-9; NgRx; contract-asset FE. No Java in this PR.

---

## Table

| ADR | Status | Code match | GitNexus | Action taken | Residual risk |
|-----|--------|------------|----------|--------------|---------------|
| **ADR-001** Primary LLM Claude Sonnet 4.6 | **Superseded** by 002 | Neither 001 nor 002 implemented. Runtime = Azure OpenAI `gpt-4o` | N/A (docs) | Noted runtime never left Azure OpenAI | Low docs; LLM cutover still open |
| **ADR-002** Primary LLM GLM-5 | **Accepted** (decision) | **Mismatch:** YAML `spring.ai.azure.openai` / `gpt-4o`; pom `spring-ai-azure-openai-spring-boot-starter` **0.8.1**; `AGENT.md` Azure OpenAI + Claude — **not** Zhipu `glm-5` / starter 1.1.0 | N/A (config/docs) | Documented **runtime vs decision**; action items remain unchecked | **MED** ops confusion if readers treat ADR as live runtime. Data-residency flag still applies **if** GLM-5 is implemented later |
| **ADR-003** CI LLM / Jenkins | **Accepted** | **Match:** GHA `workflow_dispatch` only, no `ai-assist`; Jenkins `AI_CI_ENABLED`; `ci-contract.yaml` forbids GHA ai-assist without ADR amend | N/A (CI YAML) | Verification date on ADR | Low. Do not point `LLM_ENDPOINT` at `ai-service:8086` |
| **ADR-004** CSRF edge-only | **Accepted** | **Was stale** (claimed pricing/pricebook Cookie CSRF ON). **Now match:** all 9 domain `SecurityConfig` `csrf.disable()`; servlet `CookieCsrfTokenRepository` **empty**; gateway `CookieServerCsrfTokenRepository` + `CsrfProtectionFilter` only | `CsrfProtectionFilter` **UNKNOWN** (0, Spring `WebFilter`). `query CSRF` → gateway + domain `SecurityConfig` **definitions**. Index 5 behind | Moved to `docs/adr/`; stub at old path; inventory E2 **LANDED**; `CRM_GATEWAY_ENV.md` link | **UNKNOWN** on filter/config — `rg` required before SecurityConfig edits. Direct browser-to-domain-port unsupported |
| **S2A-8 / config API** `docs/backlog/ADR-configuration-api-reconciliation.md` | **Accepted** | Hybrid S2B-11 **landed** (GW validate → CE `/internal/.../evaluate` + `ConfigurationValidationResult`). Product `validateConfiguration`: `List<Long>` → `List<String>`, **I3 O(rules)**, REQUIRES/EXCLUDES only. CE core: REQUIRES/EXCLUDES/VALIDATES + RECOMMENDS/DEFAULT | `validateConfiguration` **LOW** lower-bound (1 → controller; 4 dropped receivers) — `rg` controller + F4 parity | Runtime/I3/CE core/sequence diagram updated; **product validate retained** | Transitional dual-run drift until any future retirement (**no date**). Full S2B-8 ConstraintSolver still follow-on. S2B-9 Kafka hold |
| **ADR-005** Dual-read default `true` | **Proposed** (new) | YAML `${APPROVALS_LEGACY_LOCAL_READ:true}` **still true** | Not a Java symbol — `rg` | Drafted; J5 checklist is the gate; **not Accepted as flip** | **HIGH** if anyone flips YAML/env without J5 A–C |
| **ADR-006** Entities never at API boundary | **Proposed** (new) | `SubscriptionController` returns `ApiResponse<Subscription>` **entity**; no DTO; no list-all; `activate` → ERP | `SubscriptionController` **UNKNOWN** (HTTP) | Drafted; FE still held | **MED** API leak; UNKNOWN ≠ unused; ERP on activate |

---

## GitNexus / `rg` notes

```text
Indexed commit: e09a4a3
HEAD (primary): 7874eac
Status: stale (~5) + possible false stale from untracked .worktrees/
```

| Target | `--file` | Risk | Confirmation |
|--------|----------|------|----------------|
| `CsrfProtectionFilter` | gateway `CsrfProtectionFilter.java` | **UNKNOWN** exact 0 | `rg` class + `CsrfProtectionFilterTest`; `@Component` WebFilter |
| Domain `SecurityConfig` | e.g. pricing/pricebook | typically **UNKNOWN** | `rg csrf.disable()` all nine servlet configs |
| `CookieCsrfTokenRepository` | — | — | **zero** servlet hits; gateway uses `CookieServerCsrfTokenRepository` |
| `ConfigurationRuleService.validateConfiguration` | product-service configrule | **LOW** + 4 dropped | `rg` → `ConfigurationRuleController` + parity tests |
| `SubscriptionController` | contract-asset | **UNKNOWN** | `rg` gateway path + no FE |

---

## Files in this review

| Path | Change |
|------|--------|
| `docs/adr/ADR-004-csrf-edge-boundary.md` | Canonical ADR-004; post-E2 inventory |
| `docs/ADR-004-csrf-edge-boundary.md` | Stub → canonical |
| `docs/CRM_GATEWAY_ENV.md` | Link to `docs/adr/ADR-004-…` |
| `docs/adr/ADR-001-*.md` | Runtime note |
| `docs/adr/ADR-002-*.md` | Runtime vs GLM-5 decision |
| `docs/adr/ADR-003-*.md` | 2026-08-31 CI verify |
| `docs/backlog/ADR-configuration-api-reconciliation.md` | Hybrid + I3 + CE core; retain product validate |
| `docs/adr/ADR-005-approvals-legacy-dual-read.md` | **Proposed** |
| `docs/adr/ADR-006-entities-never-at-api-boundary.md` | **Proposed** |
| `docs/adr/README.md` | Catalog |
| `docs/forge/24-adr-gitnexus-review.md` | This table |

`docs/forge/00-orchestrator-status.md` **not** edited.
