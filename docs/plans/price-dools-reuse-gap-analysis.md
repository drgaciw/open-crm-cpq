# Gap Analysis Plan: Reuse from `price-dools-service` → `open-crm-cpq`

**Status:** Plan / harvest parent (Decision 2026-11-16)  
**Date:** 2026-11-16  
**Source:** local clone [`/home/username01/MyCode/price-dools-service`](/home/username01/MyCode/price-dools-service) (Maven artifact `price-rules-ai-drools`) @ **`cf994fc055ad2b9723040ff1be73796ba3708014`**  
**Target:** `pricing-service` (deterministic). `ai-service` only after Path B ADR.  
**Decision:** TypeSafe Jev via OpenRouter — docs-first then deterministic gaps + metrics history; AI hold intact (noul 0.89).  
**Related:** [00-harvest-index.md](./00-harvest-index.md), [pricing-ai-rules-reuse-gap-analysis.md](./pricing-ai-rules-reuse-gap-analysis.md)

---

## 1. Executive verdict

`price-dools-service` is a **Spring Boot 3.4 / Drools 8.44 sidecar** (rule deploy/execute, ARR·TCV·ACV·CLV·churn, templates, conflicts, test suites, AI create/improve + Context7/Sequential/semantic cache, local JWT). It is **not** a second pricing SoR.

Open CRM/CPQ already owns execute/simulate, rule CRUD, waterfall metrics (`MetricsCalculator`), and flagged AI workflows/experiments (pricing-ai-rules T1 + Waves 1–3). Harvest **algorithms only**. Do **not** merge the monolith, import Boot 3.4/Liquibase/local JWT, or add a competing `/api/v1/drools` execute path.

**Locked order:** Track **PD1** (conflicts → templates → test-suite runner) then **PD2** (metrics history/snapshots/churn-risk). **AI** (Context7, Sequential Thinking, semantic cache/routing) stays **HOLD** until Path B ADR + harvest T2 unhold.

---

## 2. Source vs target

| Concern | Source (`price-dools`) | Target (`open-crm-cpq`) |
|---------|------------------------|-------------------------|
| Execute / validate | `/api/v1/drools/**` | `POST /api/v1/pricing/execute` + `/simulate` + `/api/v1/rules/{id}/validate` |
| ARR / TCV / ACV / CLV | `FinancialMetricsCalculator` | `MetricsCalculator` (T1 closed — **do not reimplement**) |
| Rule conflicts | `/api/v1/rule-conflicts` | **PD1-1** under `/api/v1/rules/**` |
| Rule templates | `/api/v1/rule-templates` (filesystem DRL) | **PD1-2** DRAFT via `RuleService.create` — never activate |
| Rule test suites | `/api/v1/rule-tests` | **PD1-3** wrapping simulate |
| Metrics history / churn | `/api/financial-metrics/history`, `/churn-risk/{id}` | **PD2** `/api/v1/pricing/metrics-history/**` |
| AI create / Context7 / Sequential / cache | `/ai/rules`, Redis embeddings | **HOLD** — `ai-service` Path B only |
| Auth | Local JWT `/auth` | Entra via gateway |
| Persistence | Liquibase | Flyway `pricing` schema |

---

## 3. Non-goals

- Wholesale monorepo merge; Boot 3.4 / Drools 8.44 runtime import  
- Liquibase dual DB; `/auth` login/signup  
- Context7 / Sequential Thinking / semantic cache / AI routing  
- Any AI inside `pricing-service`  
- Competing execute/evaluate API  
- Snapshot scheduler cron unless product asks later (on-demand snapshot only)

---

## 4. Architecture alignment

Harvest index §0 **PASS** constraints apply. Controllers: DTO + `ApiResponse<T>` (ADR-006). Gateway already routes `/api/v1/rules/**` and `/api/v1/pricing/**`. Feature flags default **false**: `opencrm.pricing.rule-tools.enabled`, `opencrm.pricing.metrics-history.enabled`.

---

## 5. Secret-scan note (pin)

Source `.env.example` lists `OPENAI_API_KEY` / `JWT_SECRET` placeholders. **Do not copy `.env` into this monorepo.** Pin SHA only.
