# Deal Desk TCV handoff → pricing residual (Queue D / D0)

**Status:** Unlocked by C-GATE (Decision `pass`, confidence 0.90, `gate_waiver` false, 2026-11-16).  
**Parent:** [deal-desk-reuse-gap-analysis.md](./deal-desk-reuse-gap-analysis.md) §D2 catalog.  
**D1 done gate:** [deal-desk-harvest-backlog-d1.md](./deal-desk-harvest-backlog-d1.md).  
**Source pin:** `d9bf654a487846d54964c13d1f9363fe99f747aa` (deal-desk-service). Read-only; Mode B harvest (formulas/fixtures), not module copy.

This file is **catalog only**. It does **not** reopen closed Queue A T1-1…T1-5. It does **not** authorize a second TCV stack in `approval-routing` or quote BFF. Owner of residual code is `pricing-service` only.

---

## Hard constraints

| Must | Must not |
|------|----------|
| Land contingent revenue + NPV as **additive** commercial metrics on existing `FinancialMetrics` | Replace ARR/TCV/ACV/ramp math from T1-2 |
| Keep Drools/waterfall as SoR for list→net | Port Easy Rules / `TCVRuleExecutorService` / SpEL |
| Adapt goldens from Deal Desk **behavior**, not Mongo models | Import `Deal`, `TCVCalculation` documents, or `/api/v1/deals` |
| Exclude Salesforce-coupled `RepricingTriggerService` until a **product ADR** | Copy `.env`, Boot-3 `deal-desk-rules`, or Salesforce clients |

---

## Source catalog → residual tickets

Deal Desk `TCVCalculationStrategy.calculateTCV` is already covered by OpenCRM waterfall TCV (monthly × duration; ramp = sum of period amounts). Residual is what that stack never grew: contingent inclusion/exclusion and NPV (strategy method has **no implementation** in Deal Desk).

| ID | Source symbol | Deal Desk behavior | Target residual | Disposition |
|----|---------------|--------------------|-----------------|-------------|
| **PR-1** | `ContingentRevenue` (`includedInTCV`, `amount`) + `TCVCalculationRules.calculateContingentRevenue` | If `includedInTCV` and amount ≠ null → add to final TCV; else amount recorded as 0 on the TCV document | `FinancialMetrics.contingentRevenue`; when `PricingContext.contingentIncludedInTcv` add amount to **commercial** TCV only (ARR/ACV/CLV/exitArr unchanged) | **Do now** (Mode B) |
| **PR-2** | `TCVCalculationStrategy.calculateNPV(calculation, discountRate)` | Interface only; no Deal Desk impl. Convention: `NPV = TCV / (1+r)^(months/12)`, default `r = 0.10` | `FinancialMetrics.npv`; `PricingContext.npvDiscountRate` (default 0.10) | **Do now** (Mode B) |
| **PR-3** | `TCVCalculationStrategy.applyDiscounts` (0–1 rate × amount) + `TCVCalculationRules.calculateDiscounts` (volume / early-payment % or amount + other adjustments) | Second discount stack on a Deal aggregate | **Already SoR** in Drools waterfall + `totalDiscount` / `discountRate` on `FinancialMetrics` | **Map only** — do not rebuild. Ticket if product wants named volume/early-payment *labels* on the metric DTO |
| **PR-4** | `TCVCalculationStrategy.validateComponents` + `TCVComponent` (qty × unitPrice, component types) | Mongo line analog | Quote line items + configuration engine | **Won't-do** as a TCV port |
| **PR-5** | `ContractTerm` (`durationMonths`, monthly/total value, auto-renew, termination fee) | Deal-level term math | Quote `durationInMonths` + ramp periods already drive TCV | **Map only**. Termination-fee metric is a future product story, not this residual |
| **PR-6** | `DiscountsAndAdjustments` approval flags (`approvedBy`, dates) | Deal-desk discount approval | Quote BFF + `approval-routing` (Track D1) | **Won't-do** in pricing |
| **PR-7** | `PricingModel` (`averagePayment`, `creditPercentage`, `repricingTriggers`) | Payment-domain extras | Out of CPQ list→net | **Won't-do** |
| **PR-8** | `RepricingTriggerService` + `RepricingTriggers` | Salesforce CPQ quote validate + `syncPriceRules` | Parked with Salesforce | **Exclude until product ADR** |
| **PR-9** | `TCVRuleExecutorService` / Easy Rules `TCVRule` / `TCVCalculationUnit.drl` | Alternate TCV runtime | Drools already SoR | **Exclude forever** for this migration |
| **PR-10** | `TCVMapper` + `/api/v1` TCV DTOs | Deal TCV HTTP | `POST /api/v1/pricing/execute` `financialMetrics` | **Won't-do** a parallel TCV API |

---

## Mode B tickets (authorized after this file)

Serial, `pricing-service` only. Do not open T1-1…T1-5 branches.

### PR-1+PR-2 — Contingent revenue + NPV goldens (this residual)

| Field | Value |
|-------|-------|
| Owner | `pricing-service` |
| Branch (suggested) | `feat/pricing-contingent-npv-metrics` |
| Exclusive files | `FinancialMetrics.java`, `MetricsCalculator.java`, `PricingContext.java`, `PricingExecutionService.java` (call site only), `PricingFinancialMetricsView` (quote bind, additive), new unit goldens under `src/test/java/com/opencrm/pricing/execution/` |
| Depends | C-GATE pass + this D0 file |
| Mode | B — formulas/fixtures from Deal Desk, not Mongo types |

**Acceptance**

- [ ] Golden: contingent **excluded** → waterfall TCV unchanged; `contingentRevenue` = amount; NPV from unadjusted TCV.
- [ ] Golden: contingent **included** → commercial TCV = waterfall TCV + amount; ARR/ACV/CLV/exitArr unchanged; NPV from commercial TCV.
- [ ] Golden: NPV vectors at default `r = 0.10` for 12- and 24-month terms: `NPV = TCV / (1+r)^(months/12)`.
- [ ] Existing T1-1 execute goldens still match listed ARR/TCV/ACV/CLV fields (extra `npv` is additive).
- [ ] Quote calculate bind DTO can carry `npv` and `contingentRevenue` (`PricingFinancialMetricsView`).
- [ ] No Easy Rules, no Mongo deal aggregate, no Salesforce, no `/api/v1/deals`.

**GitNexus:** `MetricsCalculator.calculate` 2-arg was HIGH (many execute callers). Do **not** change that signature. Additive fields + a commercial-metrics apply step after existing TCV/ARR/ACV. `PricingExecutionService.execute` HIGH (controller + simulate); keep the execute signature; additive JSON only.

### Later (not this residual)

| ID | When |
|----|------|
| PR-3 labels | Product asks for volume/early-payment *named* metric fields |
| PR-5 termination fee | Product ADR |
| PR-8 repricing triggers | Product ADR that replaces Salesforce CPQ sync |

---

## Explicitly out of Queue D

- Reopen T1-1 fixtures-evaluate→execute, T1-2 ARR/TCV/ACV/CLV spec, T1-3 DRL policy, T1-4 validator, T1-5 Drools 10.2.0.
- A second TCV calculator in `approval-routing`.
- Easy Rules runtime, Mongo `Deal` / `TCVCalculation` documents, Salesforce `.env` / `RepricingTriggerService`.
- Editing `docs/forge/00-orchestrator-status.md`.
