# Track 1 capability matrix (T1-D1)

**Scope:** Deterministic pricing / Drools parity only — execute, simulate, metrics, DRL, validate/compile.  
**Parent plan:** [pricing-ai-rules-reuse-gap-analysis.md](./pricing-ai-rules-reuse-gap-analysis.md) (architect **APPROVED-WITH-GATES**).  
**Harvest backlog:** [pricing-ai-rules-harvest-backlog-t1.md](./pricing-ai-rules-harvest-backlog-t1.md).  
**Dual-read:** unchanged (`APPROVALS_LEGACY_LOCAL_READ` / `${APPROVALS_LEGACY_LOCAL_READ:true}`). Do not flip.  
**Track 2:** **HOLD** — no AI / Spring AI / agent rows as “do now.”  
**Paths:** Target stays `POST /api/v1/pricing/execute` (+ simulate / bulk). Do not rename to source `/evaluate`.

---

## Source SHA pin

| Field | Value |
|-------|-------|
| Source repo | [drgaciw/pricing-ai-rules-service](https://github.com/drgaciw/pricing-ai-rules-service) |
| Local clone | `/home/username01/Projects/pricing-ai-rules-service` |
| Pinned commit | `3aa45bf164469364389e096df07e591c81079f6a` |
| Pin message | `chore: remove Omaha Sports Complex frontend — unrelated to pricing service (#8)` |
| Pin date | 2026-09-01 |
| Verify | `git -C /home/username01/Projects/pricing-ai-rules-service rev-parse HEAD` → `3aa45bf…` |

---

## Matrix legend

| Column | Meaning |
|--------|---------|
| **Disposition** | `landed` · `port-method (B)` · `port-pattern (C)` · `defer` · `won't-do` · `hold-t2` |
| **Do now?** | Track 1 authorized work only. AI = never “do now” here. |
| **PR** | Backlog ID from §10 / harvest backlog (`T1-1`…`T1-5`) |

Hybrid metrics ownership (§5.1a):

| Concern | Owner |
|---------|-------|
| Derive ARR / TCV / ACV / CLV (and similar pure financials) | Java `MetricsCalculator` → `FinancialMetrics` |
| Policy that consumes metrics (tiers, floors, ARR-gated discounts) | DRL in `pricing-service` |
| Source `Calculate ARR/TCV/ACV/CLV` DRL | Spec for Java parity tests — **do not port as DRL** |

---

## 1. Execute / evaluate / batch

| ID | Capability | Source (`3aa45bf`) | Target (open-crm-cpq) | Gap | Disposition | Do now? | PR |
|----|------------|--------------------|------------------------|-----|-------------|---------|-----|
| E1 | Single-quote price | `POST /v1/pricing/evaluate` (`PricingController`) → effective `/api/v1/pricing/evaluate` | `POST /api/v1/pricing/execute` (`PricingExecutionController`) | Name + DTO shape (nested `quote` vs flat execute DTO); goldens differ | `port-method (B)` — adapt fixtures/assertions; **keep execute path** | Yes | T1-1 |
| E2 | Batch / multi-quote | `POST /v1/pricing/batch` | `POST /api/v1/pricing/simulate/bulk` (closest); no dedicated bulk-execute golden | Semantics differ (source batch evaluate vs target bulk simulate) | `defer` — ticket if FE/product uses bulk | No | — |
| E3 | Recalculate | `POST /v1/pricing/recalculate` | Covered by re-`execute` / simulate with same inputs | No dedicated target path | `won't-do` — no new external path | No | — |
| E4 | Missing-quote / error contract | Golden `error-pricing-missing-quote-001.json` | No execute error golden under `fixtures/pricing/execute/` | Error-path coverage gap | `port-method (B)` — add execute-shaped error fixture | Yes | T1-1 |
| E5 | Waterfall audit trail | `POST /v1/cpq/pricing/waterfall` + golden `calculate_waterfall.json` | Waterfall embedded in execute result + `GET /api/v1/pricing/audit/{quoteExternalId}` | Fixture vectors not 1:1 | `port-method (B)` — assert waterfall via execute goldens | Yes | T1-1 |
| E6 | Approvals evaluate (CPQ) | `POST /v1/cpq/approvals/evaluate` + `evaluate_approvals.json` | Margin-floor DRL + `drl/approval-margin-*.json` (no separate approvals evaluate API on pricing) | Different reason model (`DISCOUNT_GT_35` vs `MARGIN_BELOW_FLOOR`) | `landed` (partial) + optional fixture tighten | Prefer T1-1/T1-3 | T1-1 / T1-3 |

**Contract rule:** Map source evaluate → **execute**. Do not add `/api/v1/pricing/evaluate`. Gateway matrix update only if a *new* external path appears (Track 1 expects **none**).

---

## 2. Simulate / amendment / renewal

| ID | Capability | Source | Target | Gap | Disposition | Do now? | PR |
|----|------------|--------|--------|-----|-------------|---------|-----|
| S1 | What-if simulate | (evaluate with alt inputs; CPQ amendment simulate) | `POST /api/v1/pricing/simulate` | Landed; amendment golden not mapped | `landed` + fixture deepen | Yes (fixtures) | T1-1 |
| S2 | Amendment / proration | `POST /v1/cpq/amendments/simulate` + `simulate_amendment.json` | `amendment-proration.drl` seeded; no execute/drl golden from source | Fixture gap; rule already seeded | `port-method (B)` — adapt golden → simulate/execute or drl fixture | Yes | T1-1 (fixture); T1-3 if policy delta |
| S3 | Renewal reprice | `POST /v1/cpq/renewals/reprice` + `reprice_renewal.json` | `renewal-uplift.drl` seeded; no matching golden | Fixture gap | `port-method (B)` | Yes | T1-1 |
| S4 | Bulk simulate | — | `POST /api/v1/pricing/simulate/bulk` | Source batch ≠ bulk simulate | `defer` | No | — |

---

## 3. Financial metrics (hybrid §5.1a)

| ID | Capability | Source | Target | Gap | Disposition | Do now? | PR |
|----|------------|--------|--------|-----|-------------|---------|-----|
| M1 | ARR derivation | DRL `Calculate ARR` in `PricingRules.drl` | `MetricsCalculator` → `FinancialMetrics.arr` (`monthlyPrice * 12`) | Confirm parity vs source vectors (evaluate golden uses quote-level arr/tcv) | `port-method (B)` — **Java only**; treat DRL as spec | Yes | T1-2 |
| M2 | TCV derivation | DRL `Calculate TCV` | `MetricsCalculator.tcv` (`monthlyPrice * duration`) | Same | `port-method (B)` — Java | Yes | T1-2 |
| M3 | ACV derivation | DRL `Calculate ACV` | `MetricsCalculator.acv` (`tcv / 12`) | Same | `port-method (B)` — Java | Yes | T1-2 |
| M4 | CLV derivation | DRL `Calculate CLV` | `MetricsCalculator.clv` (duration×2 proxy) | Phase 0 deferred analytics; field already exists | `port-method (B)` — parity tests only; **not** DRL | Yes (tests) | T1-2 |
| M5 | Discount / multiplier / margin fields | Source response + waterfall | `discountRate`, `totalDiscount`, `priceMultiplier`, `estimatedMarginPct`, `churnScore` | Margin still placeholder (40%) | `defer` margin cost model; keep existing fields | Partial | T1-2 if vectors require |
| M6 | Ops metrics endpoint | `GET /v1/pricing/metrics` | No equivalent public pricing metrics scrape API | Ops/Prometheus elsewhere | `won't-do` for Track 1 | No | — |
| M7 | **Do not** port Calculate-* as DRL | Source calculator rules | Would duplicate `MetricsCalculator` | Violates §5.1a | `won't-do` | No | — |

---

## 4. DRL corpus & metric-gated policy

Target catalog (8 files, seeded 1:1 via `V2__seed_default_rules.sql`):

| DRL file | Seeded rule | Salience |
|----------|-------------|---------:|
| `volume-discount-named-seat.drl` | Named Seat Volume Discount | 620 |
| `usage-overage-charge.drl` | Usage Overage Charge | 610 |
| `core-license-pack.drl` | Core License Pack Floor | 590 |
| `perpetual-maintenance.drl` | Perpetual Maintenance Price | 580 |
| `migration-credit.drl` | Perpetual to SaaS Migration Credit | 560 |
| `approval-margin-exception.drl` | Route Approval for Margin Exception | 930 |
| `amendment-proration.drl` | Amendment Proration | 540 |
| `renewal-uplift.drl` | Renewal Uplift and Retention Offer | 530 |

| ID | Capability | Source (`PricingRules.drl`) | Target | Gap | Disposition | Do now? | PR |
|----|------------|-----------------------------|--------|-----|-------------|---------|-----|
| D1 | Named-seat / volume discount | `Volume Discount Based on ARR` (ARR-gated) | Qty/term `Named Seat Volume Discount` | Policy model differs — reconcile before second discount writer | `overlap` — do **not** port verbatim | Product reconcile → then T1-3 | T1-3 |
| D2 | TCV pricing tiers | `TCV-based Pricing Tiers` | None | Additive policy; needs M2 facts in context | `port-method (B)` — **policy DRL only** | Yes | T1-3 |
| D3 | ACV minimum commitment | `ACV-based Minimum Commitment` | None | Additive policy; needs M3 | `port-method (B)` — policy DRL | Yes | T1-3 |
| D4 | Churn adjustment | `Churn Risk Adjustment` | Partial via `Renewal Uplift and Retention Offer` | Overlap; avoid double-write | `overlap` — adapt only if product gap remains | Conditional | T1-3 |
| D5 | Enterprise bundle pricing | `Enterprise Subscription Bundle Pricing` | None | Bundle fact model missing | `defer` | No | — |
| D6 | Sports / vertical packs | `SportsPricing*` / vertical DRL | None | Out of CPQ scope | `won't-do` | No | — |
| D7 | Phase 0 seeded eight | — | Landed | Baseline; preserve names/explanation codes | `landed` | — | — |
| D8 | Drools engine version | Source **10.2.0** | Parent BOM **8.44.0.Final** (Phase 0 spike validated 10.2.0) | Version drift | `port-method (B)` — **last** Track 1 code PR | Yes (last) | T1-5 |

---

## 5. Validate / compile / cache

| ID | Capability | Source | Target | Gap | Disposition | Do now? | PR |
|----|------------|--------|--------|-----|-------------|---------|-----|
| V1 | Rule validate | `POST /v1/rules/rules/{id}/validate` → `DroolsService.validate*` / `compileRule` | `POST /api/v1/rules/{id}/validate` → `RuleService.validate` | Edge-case messages / invalid DRL coverage thinner | `port-method (B/C)` — adapt JDK 21 | Yes | T1-4 |
| V2 | Rule-set compile on write | `POST/PUT` sets/rules trigger `compileRuleSet` / `compileRule` | Rule CRUD + engine compile via `rules-engine` | Strengthen failure surfacing from source patterns | `port-pattern (C)` | Yes | T1-4 |
| V3 | Validate goldens | `rule-validate-001.json`, `error-rule-validate-invalid-001.json`, `rule-compile-001.json` | Not under `fixtures/pricing/{execute,drl}` (lifecycle tests elsewhere) | Bring vectors into RuleService / rules-engine tests | `port-method (B)` | Yes | T1-4 (+ optional T1-1 inventory only) |
| V4 | Cache clear | `POST /v1/rules/cache/clear` | Session/cache handled in engine; no public clear API | Ops convenience | `defer` / `won't-do` unless product asks | No | — |
| V5 | Rule impact | — | `GET /api/v1/rules/{id}/impact` | Target-ahead | `landed` | — | — |
| V6 | New external validate paths | — | — | Track 1 must not add paths | `won't-do` | No | — |

---

## 6. Fixture inventory (from T1-G0; retained)

**Source goldens:** `.kilo/artifacts/golden-fixtures/` @ pin above  
**Target fixtures:** `backend/services/pricing-service/src/test/resources/fixtures/pricing/{execute,drl}/`

### Source → target map

| Source golden | Source endpoint / intent | Target fixture (execute / drl) | Mapping note |
|---------------|--------------------------|--------------------------------|--------------|
| `pricing-evaluate-001.json` | `POST /v1/pricing/evaluate` — single-quote + ARR/TCV | `execute/01-list-price-baseline.json` (closest) | Shape differs; harvest adaptation = T1-1; metrics vectors feed T1-2 |
| `error-pricing-missing-quote-001.json` | evaluate missing quote | *(none)* | T1-1 execute error fixture |
| `pricing-batch-001.json` | `POST /v1/pricing/batch` | *(none)* | Defer (E2) |
| `calculate_waterfall.json` | CPQ waterfall | `execute/01`–`03` | Assert via execute goldens |
| `evaluate_approvals.json` | CPQ approvals | `drl/approval-margin-*.json` | Closest margin-floor goldens |
| `simulate_amendment.json` | amendment simulate | *(none)* | T1-1 / amendment DRL |
| `reprice_renewal.json` | renewal reprice | *(none)* | T1-1 / renewal DRL |
| `rule-validate-001.json` | rule validate success | *(none under execute/drl)* | T1-4 |
| `error-rule-validate-invalid-001.json` | rule validate failure | *(none)* | T1-4 |
| `rule-compile-001.json` | compile side-effect | *(none)* | T1-4 |
| `validate_configuration_invalid.json` | CPQ config validate | *(none)* | CE/product domain — **won't-do** for pricing Track 1 |

### Target → source reverse map

| Target fixture | Target intent | Source golden(s) | Mapping note |
|----------------|---------------|------------------|--------------|
| `execute/01-list-price-baseline.json` | Single-line list-price waterfall | `pricing-evaluate-001.json`, `calculate_waterfall.json` | Partial; adapt in T1-1 |
| `execute/02-multi-line-override.json` | Multi-line + override + 24-mo metrics | `calculate_waterfall.json` (weak) | No dedicated source multi-line golden |
| `execute/03-override-only.json` | All lines override | *(none direct)* | Target-only Phase 0 |
| `drl/volume-named-seat-500.json` | Named-seat @ qty 500 | *(none in golden-fixtures/)* | Target DRL unit golden |
| `drl/approval-margin-below-floor.json` | Margin below → DEAL_DESK | `evaluate_approvals.json` | Different reason model |
| `drl/approval-margin-above-floor.json` | Margin above → no fire | `evaluate_approvals.json` (inverse) | Target has explicit non-fire |

### Inventory counts

| Location | Count |
|----------|------:|
| Source `.kilo/artifacts/golden-fixtures/` | 11 |
| Target `fixtures/pricing/execute/` | 3 |
| Target `fixtures/pricing/drl/` | 3 |

---

## 7. Explicitly not “do now” (Track 2 HOLD / excludes)

Documented for clarity — **not** Track 1 matrix work:

| Theme | Status |
|-------|--------|
| RuleAuthor / RuleValidator / RuleOptimizer **agents** | **HOLD** — Path B ADR Accepted required |
| Spring AI 1.1.7 upgrade / ChatModel adapters | **HOLD** |
| AI orchestration / AI metrics / RL pricing | **HOLD** |
| Rule pipeline / prompt feedback productization | Defer past Track 2 first cut |
| Sequential-thinking MCP client | Track 2 optional later |
| AI inside `pricing-service` | **Exclude** |
| Source Keycloak as IdP | **Exclude** |
| Next.js / sports FE / sports DRL | **Exclude** |
| Dual-read flip, NgRx, Java 25, forge holds in plan §11 | **Hold / exclude** |

---

## 8. Ranked Track 1 candidates (top deterministic)

| Rank | Candidate | Mode | Land in | PR |
|-----:|-----------|------|---------|-----|
| 1 | Golden fixtures evaluate→execute (+ amendment/renewal/error) | B | `pricing-service` tests | T1-1 |
| 2 | Metric derivation parity (ARR/TCV/ACV/CLV) from Calculate-* **spec** | B | `MetricsCalculator` + goldens | T1-2 |
| 3 | Metric-gated policy DRL (TCV tiers, ACV floor; no Calculate-* DRL) | B | `resources/rules` + drl goldens | T1-3 |
| 4 | Compile/validate edge cases from `DroolsService` | B/C | `rules-engine` + `RuleService` | T1-4 |
| 5 | Drools BOM **8.44.0.Final → 10.2.0** | B | parent `pom.xml` | T1-5 |
| — | Batch/recalculate path rename / Keycloak / AI | — | — | won't-do / HOLD |

**GitNexus notes (every code PR):** impact before symbol edits; `MetricsCalculator` impact may be `UNKNOWN` — confirm with `rg`; always `-r /home/username01/Projects/open-crm-cpq`.

**T1-D1 exit:** This matrix + harvest backlog. No harvest implementation, Drools BOM bump, or T1-1 fixtures in this PR.
