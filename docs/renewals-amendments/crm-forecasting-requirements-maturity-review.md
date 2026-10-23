# CRM forecasting requirements maturity review (PL-6)

**Status:** REVIEW STARTED 2026-10-23; FQ1 to FQ3 decided by the owner 2026-10-23. Documentation only. This review assesses requirements; it does not decide a forecast model, accept an ADR, or authorize any story. Tracked as PL-6, Notion RA-54, Linear DRG-588, open question OQ22.

**Scope boundary:** this is the overall CRM forecasting requirements review. It is SEPARATE from the automatic re-forecast on amendment save (RA-66 / DRG-600). This review informs that feature but does not design, replace or gate it.

**Evidence base:** `main` of drgaciw/open-crm-cpq at `bf41c230`, read 2026-10-23. Statements without a tag are facts from the cited file. Statements tagged **[INFERENCE]** are the reviewer's reading and need confirmation.

## 1. Maturity scale

| Level | Meaning |
|---|---|
| Absent | No requirement text and no code. |
| Implied | Mentioned in a plan, an agent prompt or a vendor note, with no definition. |
| Drafted | Prose that names inputs or outputs, but no calculation rule, owner or acceptance criteria. |
| Specified | Definition, inputs, calculation rule and acceptance criteria written, not yet accepted. |
| Accepted | Specified and accepted by the owner (ADR or accepted spec), with tests. |

## 2. What forecasting exists today

### 2.1 Code (facts)
- **Deal entity**, `backend/services/crm-service/src/main/java/com/opencrm/crm/deal/Deal.java`: fields `amount`, `currency`, `probability` (default 10), `expected_close_date`, `actual_close_date`, `type` (`DealType`, default `NEW_BUSINESS`), `weighted_amount`, and ADR-032 fields `base_amount`, `base_currency`, `base_weighted_amount`, `fx_rate`, `fx_rate_as_of`, `fx_source`.
- **Weighted amount rule:** `weighted_amount = amount * probability / 100`, HALF_UP to 2 decimals, recomputed on every persist and update (`recalculateWeightedAmounts`). Same for the base-currency weighted amount.
- **Stage-driven probability:** `advanceStage()` walks PROSPECTING, QUALIFICATION, NEEDS_ANALYSIS, PROPOSAL, NEGOTIATION and sets probability 10, 20, 40, 60, 80. `markWon()` sets 100, `markLost()` sets 0. The mapping is hardcoded in the entity.
- **Pipeline summary:** `DealRepository.findPipelineSummary(baseCurrency)` groups non-deleted deals by stage and returns count, total base amount, weighted base amount and an `unconvertedCount` (ADR-032 D-06). Exposed as `GET /api/v1/deals/pipeline` in `DealController`. Tests: `DealPipelineCurrencyIntegrationTest`, `DealCurrencyConverterTest`, `DealServiceTest`.
- **Quote to deal link:** `crm-service/.../event/QuoteAcceptedDealProcessor.java` reacts to quote acceptance. [INFERENCE] it moves the deal toward WON; not read in detail.
- **AI agents:** `ai-service/.../intelligence/WinProbabilityAnalysisAgent.java` (cpq-agent-026) and `RenewalOpportunityDetectionAgent.java` (cpq-agent-028) are thin LLM prompt wrappers. They send the caller's messages to a chat client and return text. They do not read the pipeline endpoint, compute numbers, or persist anything.

### 2.2 Docs (facts)
- `MODERNIZATION_PLAN.md`: Deal Service owns "Opportunities, pipeline stages, forecasting"; AI section lists "Forecast Generation: Revenue predictions". No definition.
- `agents/win-probability-analysis.md` and `agents/cpq-agent-026.md`: "Pipeline Forecast Contribution" applies the predicted win rate to each deal's expected ARR, rolls up by segment, region, deal type and close quarter from `GET /api/v1/deals/pipeline`, with confidence intervals. Agent prose only.
- `agents/renewal-opportunity-detection.md` and `agents/cpq-agent-028.md`: renewal forecast by month, segment, risk tier, expected ARR; retention offers using `ENABLE_12M_PRICE_PROTECTION` in `renewal-uplift.drl`. Agent prose only.
- `agents/usage-based-pricing.md` and `agents/cpq-agent-021.md`: prepaid credit burn rate and `projectedExhaustionDate`, committed spend shortfall. Agent prose only.
- `docs/adr/ADR-032-multi-currency-pipeline-reporting.md`: **Accepted 2026-10-15**. D-06: "Pipeline and forecast queries sum `base_amount` and group by stage." Interim base currency from `crm.fx.base-currency`; floating open pipeline (D-03) not built.
- `docs/renewals-amendments/prereq/specs/arr-bridge.md`: **Proposal only** (DRG-575, Low). Per-line ARR components (base, priceEffect, qtyEffect, crossSell, churn, conversion) computed at pricing time. Says DRG-600 "may read the bridge later"; a CRM forecast push is out of scope. Eight owner decisions open.
- `cpq-renewals-amendments-research.md`: vendor research (Salesforce-style Renewal Forecast trigger on contract; Tacton installed base with usages and forecasts). Reference only.
- Trackers: RA-66 / DRG-600 (auto CRM re-forecast on amendment save, audit decision 18); RA-54 / DRG-588 (this review); RA-53 / DRG-587 (on-prem usage, PL-1 to PL-5).

## 3. Forecast types and requirements maturity

| # | Forecast type | Level | Evidence | Missing for next level |
|---|---|---|---|---|
| F1 | Stage pipeline summary (count, total by stage) | **Accepted** | ADR-032 D-06, `findPipelineSummary`, integration tests | Ownership of stage list; tenant scoping (BC-19-02) |
| F2 | Weighted pipeline (stage probability) | **Specified, partly** [INFERENCE: specified by code, not by a requirement] | `Deal.recalculateWeightedAmounts`, stage switch; summed by ADR-032 | Written rule and owner for stage probabilities; whether reps may override probability; configurable per tenant |
| F3 | AI win-probability forecast rollup | **Drafted** | `agents/win-probability-analysis.md`; agent class is a prompt wrapper | Model inputs, confidence interval method, accuracy measure, where results are stored, acceptance criteria |
| F4 | Forecast categories (commit, best case, pipeline, omitted) and rep or manager submissions | **Absent** | No code or doc found | Everything |
| F5 | Period forecast (by close month or quarter, versus quota) | **Implied** | Agent prose mentions close quarter; `expected_close_date` exists; no quota entity found | Period definition, fiscal calendar, quota model |
| F6 | Renewal forecast (expected renewal ARR by month and risk tier) | **Implied to Drafted** | `agents/renewal-opportunity-detection.md`; renewal policy stub; RA-59 / DRG-593 renewal quote auto-generation | Source of renewal amount (contract ARR versus renewal quote), risk tier definition, relationship to renewal deals of `DealType` |
| F7 | ARR movement (bridge: price, quantity, cross-sell, churn, conversion) | **Drafted** | `prereq/specs/arr-bridge.md` proposal, DRG-575 | Eight owner decisions in that spec; acceptance |
| F8 | Usage and spend projection (consumption) | **Drafted** for SaaS, **Absent** for on-prem | `agents/usage-based-pricing.md`; PL-1 to PL-5, OQ21 | Meter reading source; on-prem self-report record |
| F9 | Re-forecast on amendment save | **Drafted** (out of scope here) | RA-66 / DRG-600 | Target forecast model, which this review is meant to stabilize |
| F10 | Forecast accuracy and snapshots (history, forecast versus actual) | **Absent** | No snapshot table or job found | Snapshot cadence, retention, accuracy metric |

## 4. Gaps
1. **No forecast definition document.** No owner, consumer (finance, sales leadership) or cadence is written for any forecast type. F1 is the only accepted piece, and it is a currency rule, not a forecast definition.
2. **Two value bases with no bridge.** Deals carry a one-number `amount` with no term or ARR versus TCV semantics; renewals and amendments speak in ARR per line (arr-bridge proposal). [INFERENCE] a forecast that mixes deal amount and contract ARR will double-count or mismatch until a rule says which wins.
3. **No forecast categories** (commit, best case) and no submission or override workflow (F4).
4. **Hardcoded stage probabilities** in the entity, not configuration; no per-tenant or per-deal-type values.
5. **No snapshots or accuracy measurement** (F10), so no forecast history to compare.
6. **AI agents are prose plus prompt wrappers.** Their described rollups are not implemented or testable.
7. **Renewal forecast source of truth undefined:** contract record (CAM), renewal quote, or renewal deal.
8. **Tenancy and currency:** base currency is a global property until BC-19-02; floating open pipeline not built (ADR-032 interim choices).
9. **On-prem usage input absent** (PL-1 to PL-5, OQ21).

## 5. Forecast inputs an amendment or renewal changes
For the later DRG-600 feature, these inputs move when an amendment or renewal is executed (facts from the amendment and renewal docs, mapping to forecast is [INFERENCE]):

| Input | Changed by | Forecast types affected |
|---|---|---|
| Line ARR (price, quantity) | Amendment add, update, remove (first slice action codes) | F6, F7, F9, and F1/F2 if a deal mirrors it |
| Contract end date and term | Renewal, coterm (RA-20 / DRG-554), new-contract-preferred (RA-58 / DRG-592) | F5, F6 |
| Renewal date and auto-renew flag | Auto-renew opt-in (RA-62 / DRG-596) | F6 |
| Churn (non-renewal, remove line) | Renewal disposition, amendment remove | F6, F7 |
| Conversion (maintenance to SaaS) | `conversions.md` | F7 |
| Currency and FX rate at execution | Any priced document | F1, F2 (ADR-032) |
| Precedence between old and new contract | `precedence_flag` (OQ27) | F6 [INFERENCE: avoids counting both contracts] |
| Deal stage and probability | Quote acceptance (`QuoteAcceptedDealProcessor`) | F1, F2 |

## 6. Dependencies
- ADR-032 (accepted) and its interim choices; BC-19-02 tenancy; BC-03-07 rate service.
- DRG-575 arr-bridge decisions (component dictionary, annualization).
- Renewal policy stub and RA-59 / DRG-593 (renewal quote), RA-62 / DRG-596 (auto-renew).
- ADR-035 thin contract header and OQ27 precedence column (contract ARR source).
- PL-1 to PL-5 / RA-53 / DRG-587 for on-prem usage.
- RA-66 / DRG-600 consumes the output; not a dependency of this review.

## 7. Open questions (not decided here)
### 7.1 Owner decisions (2026-10-23, about 21:39 Europe/Dublin)
- **FQ1. DECIDED:** sales leadership owns the forecast definition and is its consumer.
- **FQ2. DECIDED:** the forecast unit is TCV.
- **FQ3. DECIDED:** the source of truth for the renewal forecast is contract ARR in CAM.

### 7.2 Still open
- **FQ4.** Are forecast categories (commit, best case, pipeline, omitted) needed, and who may submit or override?
- **FQ5.** Should stage probabilities become tenant configuration, and may a rep override probability per deal?
- **FQ6.** Fiscal calendar and period definition; is quota in scope?
- **FQ7.** Snapshot cadence and accuracy metric, if any.
- **FQ8.** Are the AI agent rollups (F3, F6) requirements or illustrative prose?
- **FQ9.** Does the arr-bridge (F7) become the reporting model the forecast reads?
- **FQ10. NEEDS DECISION (not decided here).** The forecast unit is TCV (FQ2) but the renewal source is contract ARR (FQ3), so the spec needs a rule converting renewal ARR to TCV. Candidate: renewal ARR times the renewal term (in years). Not chosen. Related: F2 weighted pipeline uses deal `amount` (no term semantics) and the arr-bridge (F7) stores annualized ARR components, so both will need a mapping to TCV. Open points include term source for renewals, partial-year and coterm terms, and ramps.

## 8. Recommended next step
FQ1 to FQ3 are decided (section 7.1). Next step: draft the "CRM forecast definitions" spec, owned by sales leadership, in TCV, with renewal forecast sourced from contract ARR in CAM. One section per type F1 to F10 with owner, unit, inputs, rule, cadence and acceptance criteria. The spec must carry FQ10 (ARR to TCV rule) as an open decision until the owner decides it. No code and no ADR until the spec is reviewed. DRG-600 can continue its design against section 5 in the meantime. The spec is not drafted yet.
