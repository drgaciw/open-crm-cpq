# open-crm-cpq: Universal Renewals-and-Amendments Project Transcript

**Purpose.** A portable, tool-agnostic running record of the open-crm-cpq universal product renewals-and-amendments project: the call arc, decisions, repo findings, research, meta model, backlog, design spikes, sanity checks and open questions. It must stay readable without Linear, Notion, GitHub or Cursor. Documentation only; no code changes are implied by this file.

- Repo: https://github.com/drgaciw/open-crm-cpq (the repo behind PR #598)
- Origin: voice call, 2026-10-16 (America/Chicago)
- Companion files (all in `/workspace/`):
  - `cpq-renewals-amendments-research.md` (research, 74 sources, S1–S74)
  - `amendment-document-design-spike.md` (design spike)
  - `cpq-pricing-policy-sanity-check.md` (sanity check)

## How to maintain
1. **Append-only.** Add new dated sections at the **bottom** (newest last). Do not rewrite history; if something changes, append a correction that references the earlier section.
2. Each appended section uses the heading `## YYYY-MM-DD: <title>` and lists **Source files** (paths) and **Links** (URLs, ticket IDs) it was drawn from.
3. Mark design proposals as **[INFERENCE]**; keep facts, sources and inference distinct.
4. Standing instruction from the caller (call, 2026-10-16): every future work item on this project appends its outputs here.
5. Sections 1–9 below are the baseline as of 2026-10-16; the in-flight section list and open questions are updated by appended sections, not by editing the baseline.

---

## 1. Timeline / Arc (2026-10-16)
1. Call began as an **exploration of open-crm-cpq** (the repo behind PR #598).
2. It shifted into a **product development lifecycle exercise** whose goal is a **universal product renewals-and-amendments feature** across CPQ services.
3. Repo map completed (section 3) → market research (section 4) → research scope widened to vertical and adjacent solution sets (on-prem entitlement and capacity, telecom, manufacturing/hardware, subscription billing, SAM tools) → backlog created (section 5; 26 items) → amendment document design spike (section 6) and pricing policy sanity check (section 7) run.
4. Standing instruction to keep this transcript continuously updated.

## 2. Customer context and goal
- **Goal:** one universal renewals-and-amendments capability across CPQ services.
- **Customer:** a legacy software company with a **60/40 split**: ~60% on-premise traditional software (transactional/perpetual and capacity models), ~40% SaaS subscription.
- **Weighting:** subscription-heavy platforms (Salesforce CPQ/Revenue Cloud, DealHub) are the primary reference; on-prem transactional and capacity licensing is the harder contrast case.

## 3. Repo map findings
Source: read-only cloud agent `bc-cceff2de-eb2d-5928-8dfb-98cada429cd5`.

### 3.1 How it works today
- Renewals and amendments are **one typed continuation** in quote-service: `QuoteContinuationPolicy`, `QuoteContinuationService` / `Controller` / `Request`, `QuoteService.calculate`, `QuoteAcceptedKafkaEventFactory`. It is applied on acceptance by `AcceptedQuoteSubscriptionService` in contract-asset-management.
- **Pricing is locked at acceptance** from the accepted rate schedule. pricing-service only echoes unit rates; a mismatch raises `SUBSCRIPTION_CONTINUATION_RATE_CHANGED`.
- **Renewal:** starts the day after the end date; uplift is caller-supplied (default 0); EXTENSION forces 0; one flat ABSOLUTE ramp period.
- **Amendment / expansion:** keep end date and rates; day-prorated stub; co-termed. Amendment requires `sourceSubscriptionLineUuid` and clips/retires source periods. Expansion appends without clipping.
- **Concurrency/idempotency:** optimistic `observedRevision` + `commercial_revision`; `applied_quote_acceptances` is idempotent on quote id + commercial digest.

### 3.2 Gaps
- Nightly `RenewalScheduler` publishes a renewal-eligible event **nobody consumes**, and **re-publishes the same subscription nightly**.
- Date-only `POST /subscriptions/{id}/renew` **skips pricing**.
- Eligibility **never opens a draft quote**.
- Seeded Drools `renewal-uplift.drl` (8%, 3% if churn > 70) and `amendment-proration.drl` (a different formula from the policy) **never execute**.
- **No amendment document entity.** **No multi-line support.** **No UI.**
- CRM `DealType` vs quote `DealType` unmapped.
- EXTENSION and RENEWAL are both stored as `DealType.RENEWAL`.
- product / pricebook / configuration services are **outside** the continuation path.

### 3.3 Key files
| Area | Path / artifact | Role |
|---|---|---|
| quote-service | `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationPolicy.java` | Typed continuation rules (renewal/extension/amendment/expansion), rate locking |
| quote-service | same dir: `QuoteContinuationService`, `QuoteContinuationController`, `QuoteContinuationRequest` | Continuation API and orchestration |
| quote-service | same dir: `QuoteService.calculate` | Quote calculation, pricing-service echo check |
| quote-service | same dir: `QuoteAcceptedKafkaEventFactory` | Builds the acceptance event |
| contract-asset-management | `AcceptedQuoteSubscriptionService` | Applies accepted quote to the subscription; revision check; idempotency |
| contract-asset-management | `RenewalScheduler` | Nightly eligibility scan, publishes renewal-eligible event |
| contract-asset-management | `SubscriptionService` / `SubscriptionController`; `RampScheduleController` | Subscription and ramp APIs (incl. date-only renew) |
| migrations | quote-service `V1_10`; contract-asset-management `V1_1`, `V1_6` | Continuation schema; subscriptions; rate lineage / `applied_quote_acceptances` |
| pricing-service rules | `renewal-uplift.drl`, `amendment-proration.drl`, `churn-risk-adjustment.drl` | Seeded rules, not executed by quote calculation |
| events | `SubscriptionRenewalEligibleEvent`, `QuoteAcceptedEvent` | Renewal-eligible (unconsumed) and acceptance events |

(Exact file locations beyond the quote-service directory were summarised from the cloud agent report; verify paths in the repo before relying on them.)

### 3.4 Earlier context
- PR #598 (DRG-509): review fixes landed by cloud agent `bc-4c944bad-508f-5a17-9f1e-52313fc00684`; PR still open, awaiting CI integration tests (Docker was unavailable locally).
- Pre-existing related items: DRG-461 (amendments/co-termination), DRG-462, DRG-472, DRG-534 (ramp amendments), DRG-429, DRG-443, DRG-341 (hybrid migration credit).

## 4. Market research
Full file: `/workspace/cpq-renewals-amendments-research.md` (74 cited sources, S1–S74; dated 2026-10-16).

### 4.1 Headline
1. Subscription-native platforms run renewals and amendments on the **same backbone** (installed record, change transaction quote → order, effective-dated ledger), though many keep different entry points and pricing rules. Most unified: Salesforce Revenue Cloud, Zuora, DealHub, Conga, Oracle. Separate on a shared record: Salesforce CPQ (managed package), HubSpot, Chargebee, Maxio. No lifecycle capability (document/revision only): PandaDoc, QuoteWerks.
2. On-prem entitlement engines (Revenera FlexNet Operations, Thales Sentinel EMS) model renewal, upgrade and upsell as **typed child lines of a parent entitlement**; licence regeneration (fulfilment) is a second step; perpetual licences are not renewed, their maintenance is; capacity/usage are meters or attributes. Snow/Flexera model maintenance periods and upgrade rights.
3. Telecom (TMF622/637): add/modify/delete/noChange actions; renewal is not its own action.
4. **Recommendation [INFERENCE]:** build one capability. A renewal is an amendment that also extends the term and applies a renewal pricing policy (Salesforce Revenue Cloud AssetAction/AssetStatePeriod, DealHub order types, Zuora order actions, Oracle delta engine; Oracle: "Renew … essentially a Modify").

### 4.2 Capability map (L1)
1 Installed Commitment Management; 2 Lifecycle Change Initiation; 3 Change Scoping & Projection; 4 Change Configuration; 5 Change Pricing; 6 Approval & Acceptance; 7 Order & Apply; 8 Fulfilment & Entitlement Sync; 9 Lifecycle Analytics.

### 4.3 Proposed meta model [INFERENCE] (verbatim from research section 7.2)
```
Account 1─* CommercialAgreement (Contract)
  CommercialAgreement: id, type{MasterSubscription|LicenseAndMaintenance|ServiceContract|Hybrid},
     termModel{Termed|Evergreen|Perpetual}, startDate, endDate, renewalPolicy, coTermPolicy,
     priorAgreementId (renewal chain), mergedFromIds[]                     ← S44, S43, S36
  CommercialAgreement 1─* CommitmentLine
  CommitmentLine: id, productRef, licenseModel{Subscription|TermLicense|Perpetual|Maintenance|
     Capacity|Usage|Hardware}, metric{seat|core|PVU|TB|session|unit}, quantity/capacity,
     isPermanent, parentLineId, relatedPerpetualLineId (maintenance→licence),
     status{PendingActive|Active|Suspended|InGrace|PendingTerminate|Terminated|Expired|Superseded}
                                                                            ← S53, S59, S64, S67
  CommitmentLine 1─* CommitmentStatePeriod (version/segment): validFrom, validTo, qty, unitPrice,
     netPrice, MRR/ARR, priceBasis, sourceChangeLineId                      ← S16, S35, S1
  CommitmentLine 1─* EntitlementFulfillment: licenseKey/activationId, hostId, regeneratedFrom   ← S56, S57
  CommitmentLine *─* MeterReading/UsageRecord (capacity & usage)            ← S59, S72

LifecycleChange (the single capability's root entity)
  id, intent{Renew|Amend|Cancel|Restructure|Transfer|Suspend|Resume|TrueUp|Convert},
  initiationTrigger{Manual|LeadTime|TermEndAuto|Threshold|TrueUpCycle|EndOfLife|API},
  actionDate, effectiveDate(s){contractEffective, serviceActivation, customerAcceptance},
  targetAgreementIds[], outputMode{Quote|DirectOrder}, dryRun, status (see 4.4),
  targetVersion, supersedesChangeId (revision)                              ← S12, S19, S35, S34
  LifecycleChange 1─* ChangeLine
  ChangeLine: commitmentLineRef?, actionCode{Add|Update|Remove|NoChange|Renew|Upgrade|Downgrade|
     Swap|ConvertMetric|Suspend|Resume|Terminate}, quantitySemantics{Delta|Absolute|Replace},
     startQty, deltaQty, endQty, termStart, termEnd, businessCategory{InitialSale|Renewal|Upsell|
     Downsell|CrossSell|Upgrade|Downgrade|Swap|Cancellation|T&C|Transfer|TrueUp}
                                                                            ← S15, S17, S19, S26, S40, S65
  ChangeLine *─1 PricingPolicy: renewalMode{Same|List|UpliftPct|UpliftAmt|RenewalPriceOverride|
     Renegotiate}, amendMode{RetainContracted|List|Override}, prorationMethod{Daily|MonthDay|
     None|NextCycle}, creditPolicy{None|Prorated|Full}, terminationFee, ramp/segments
                                                                            ← S6, S25, S50, S44, S47, S13
  LifecycleChange 1─* Quote (revisioned) 1─0..1 Order ─▶ applies to CommitmentStatePeriod
                                                                            ← S1, S17, S66
Policies (configurable, inherited Account→Agreement→Line, most specific wins):
  RenewalPolicy{leadTimeDays, autoRenew, termEndAction{Renew|RenewOnce|Evergreen|Cancel},
     renewalTerm, cutOffDays, gracePeriod}                                  ← S25, S48, S47, S59
  CoTermPolicy{LatestEnd|EarliestEnd|None|AlignToAgreement}                ← S5, S43
```
"Most specific wins" follows Salesforce CPQ (subscription-level uplift overrides contract-level) [S6].

### 4.4 Lifecycle states
- **LifecycleChange:** `Draft → Projected/Priced → InApproval → Approved → Presented → {Accepted | Rejected | Expired | Superseded(revision)} → Ordered → Applied → (Reverted)`.
- **CommitmentLine:** `PendingActive → Active ⇄ Suspended → PendingTerminate → Terminated`, plus `InGrace`, `Expired`, `Superseded`.

### 4.5 Pricing modes
Retain contracted price (amendments); Same / List / Uplift % (renewal); uplift amount; renewal factor; free renegotiation; daily or hybrid proration with credit memo; transition proration / waived fee on plan swap; termination credit/fee; **[INFERENCE]** maintenance % of licence net, capacity true-up at contracted rate, metric-conversion credit.

### 4.6 Triggers
Manual (user/API); renewal lead-time job; term-end auto policy; capacity/usage threshold; expiry/grace notifications; workflow on renewal dates; contract checkbox. **[INFERENCE]** add periodic true-up and vendor end-of-life events.

### 4.7 Hard on-prem case [INFERENCE]
- **Perpetual:** `isPermanent=true`, never renewed; its linked **Maintenance** line is renewed.
- **Capacity:** `metric=core/PVU/TB`; amendments are Add/Update with Delta semantics, plus a **TrueUp** intent fed by MeterReading.
- **Upgrade / metric conversion:** `actionCode=Upgrade|ConvertMetric`; needs fulfilment regeneration.
- **On-prem to SaaS:** one **Restructure** change that terminates maintenance lines and adds subscription lines, with a credit policy.

### 4.8 Primary-reference mapping
| Universal element | Salesforce CPQ | Salesforce RCA | DealHub |
|---|---|---|---|
| CommercialAgreement | Contract | Contract | Subscription |
| CommitmentLine | SBQQ__Subscription__c / Asset | Asset | Subscription charge |
| StatePeriod | additional Subscription records (ledger) | AssetStatePeriod | subscription version |
| LifecycleChange | Amendment/Renewal Quote (+Opportunity) | initiate* action → Quote | Order (orderType) |
| ChangeLine.action | implied by delta lines | AssetAction category | lineItem.action |
| Apply | Order "Contracted" | Order Activate | Execute order |

### 4.9 Caveats
- **The model is my inference/design synthesis;** no vendor publishes a formal capability model for this.
- Some details come from search-result summaries rather than full pages (S25, S28, S36, S41/42, S48, S49, S51, S58–S60, S65, S67–S70, S72); some pages did not render.
- S2, S3, S7, S8, S11, S59 are third-party sources.
- DealHub's developer docs are branded "Subskribe API" [S19].

## 5. Backlog (26 items, in sync)
- **Linear:** team DRG, project OPEN-CRM-CPQ, label `renewals-amendments`, DRG-535..DRG-560. URL pattern `https://linear.app/drgamtd-workspace/issue/DRG-###`.
- **Notion:** database "Open-CRM-CPQ Backlog" https://app.notion.com/p/f79790ab5eac4183a2adc6edeb3d76b8, rows RA-01..RA-26.
- Repo-gap items RA-01..13 = DRG-535..547 (RA-13/DRG-547 is the research intake tracker); research items RA-14..26 = DRG-548..560.

| RA | DRG | Title | Priority |
|---|---|---|---|
| RA-01 | 535 | No amendment document entity | High |
| RA-02 | 536 | No multi-line amendment support | High |
| RA-03 | 537 | No UI for renewals/amendments | High |
| RA-04 | 538 | CRM vs quote DealType mapping | Medium |
| RA-05 | 539 | Product/pricebook/config not in continuation flow | Medium |
| RA-06 | 540 | Nightly renewal-eligible event has no consumer | Medium |
| RA-07 | 541 | Renewal uplift Drools rule never executes | Medium |
| RA-08 | 542 | Amendment proration rule conflicts with continuation formula | Medium |
| RA-09 | 543 | Date-only renew endpoint skips pricing | Medium |
| RA-10 | 544 | Eligibility never opens a draft renewal quote | High |
| RA-11 | 545 | Renewal-eligible event re-published nightly | Medium |
| RA-12 | 546 | Extension vs renewal indistinguishable in stored deal type | Low |
| RA-13 | 547 | Research gap intake (tracker) | Medium |
| RA-14 | 548 | Unified LifecycleChange intent model | High |
| RA-15 | 549 | Renewal pricing policy hierarchy | High |
| RA-16 | 550 | Amendment price/proration/credit/termination-fee policies | Medium |
| RA-17 | 551 | Dry run and revert | Medium |
| RA-18 | 552 | ChangeLine action codes, quantity semantics, categories | Medium |
| RA-19 | 553 | Effective date vs action date | Medium |
| RA-20 | 554 | Co-termination and agreement merge | Medium |
| RA-21 | 555 | Auto-renew with lead time and term-end actions | High |
| RA-22 | 556 | On-prem perpetual + maintenance linkage | High |
| RA-23 | 557 | Capacity and meter readings with true-up intent | High |
| RA-24 | 558 | Entitlement fulfilment and commitment state model | Medium |
| RA-25 | 559 | On-prem to SaaS restructure in one change | Medium |
| RA-26 | 560 | Approvals and revisions for lifecycle changes | Medium |

Related pre-existing items (linked, not duplicated): DRG-461, DRG-462, DRG-472, DRG-534, DRG-429, DRG-443, DRG-341.

### 5.1 Items added after the baseline (from the spike files; verify against the live backlog)
- From the design spike (`amendment-document-design-spike.md`, section 7): RA-27 ADR placement/ownership of the amendment document entity; RA-28 gate apply on executed amendment, not quote acceptance; RA-29 apply ledger cannot record one amendment across multiple subscriptions; RA-30 continuation pricing decisions are not audited; RA-31 partial application rules for mixed-line amendments; RA-32 backfill amendment history. **Confirmed ID pairs (RA/DRG, priority):**
  - RA-27 / DRG-561, High: ADR: placement and ownership of the amendment document entity
  - RA-28 / DRG-562, High: Gate subscription apply on executed amendment, not quote acceptance
  - RA-29 / DRG-563, High: Apply ledger cannot record one amendment across multiple subscriptions
  - RA-30 / DRG-564, High: Continuation pricing decisions are not audited
  - RA-31 / DRG-565, Medium: Partial application rules for mixed-line amendment documents
  - RA-32 / DRG-566, Medium: Backfill amendment history for existing subscriptions
- From the sanity check: new gaps G1–G10 are described in `cpq-pricing-policy-sanity-check.md`; its file does not list the resulting RA/DRG IDs. **Missing: record IDs when known by appending a section.**

## 6. Amendment document entity design spike
File: `/workspace/amendment-document-design-spike.md` (design only; 2026-10-16). Sections: Decisions needed; 0 Context; 1 Entity shape and fields; 2 Relationships; 3 Document lifecycle states and mapping; 4 Multi-line and 60/40 mix; 5 Auditability; 6 Open questions, risks, placement, integration, migration, API; 7 Backlog mapping.

Key points (read the file for detail):
- **Placement recommendation [INFERENCE]: Option A**, AmendmentDocument plus a thin Contract header in **contract-asset-management**; LifecycleChange stays in quote-service; rendering in document-generation (`AMENDMENT` type); approvals in approval-routing (`CONTRACT_MODIFICATION`); e-sign via the DRG-457 adapter. Alternatives: quote-service (B), new contract-document service (C). Follow-up ADR: RA-27.
- **Document states [INFERENCE]:** DRAFT → IN_REVIEW → APPROVED → OUT_FOR_SIGNATURE → EXECUTED → APPLIED; side exits REJECTED, WITHDRAWN, EXPIRED, SUPERSEDED, VOID; apply sub-states PARTIALLY_APPLIED, APPLY_BLOCKED.
- **Product/pricebook/config integration:** the document pins product external id, relationship version, pricebook version and configuration hash at Priced and freezes them; apply never re-reads (overlaps RA-05).
- **Migration:** thin `contracts` header, synthesized ORIGINAL and BACKFILLED documents flagged LEGACY, no retroactive events (RA-32).
- **Risks:** gating apply on EXECUTED delays ledger/ERP sync; contract header may conflict with DRG-458; digest canonicalization drift; multi-subscription documents break one-row-per-quote idempotency; thin "policy fired" trail until pricing is unified; perpetual/capacity lines have no ledger target yet.
- **Decisions needed (recommendations in file):** placement; apply on Executed vs Accepted; thin contract header now vs after DRG-458; per-contract numbering `{contract_number}-A{nnn}`; partial application; revision model; backfill scope; retention (BC-20-05 / DRG-313).
- **Notion spike page:** https://app.notion.com/p/3fbf7cb4e4df8114b601d10e246ed82a
- **Spike recommendation (summary):** placement in contract-asset-management with a thin Contract header; LifecycleChange stays in quote-service; apply on executed amendment, not quote acceptance.
- **8 decisions needed, status: AWAITING USER DECISION (all):** (1) placement; (2) apply on signature vs acceptance; (3) thin Contract header now vs wait for DRG-458; (4) numbering `{contract_number}-A{nnn}`; (5) partial application with PARTIALLY_APPLIED; (6) revision model; (7) backfill as legacy; (8) retention window (needs Legal).
- **Backlog mapping to existing:** see file section 7 (RA-01, 02, 14, 17, 19, 22–26, 05; BC-07-01 DRG-458, BC-07-04 DRG-461, BC-06-04 DRG-457, BC-20-01 DRG-309, BC-20-03 DRG-311, BC-20-04 DRG-312, BC-20-05 DRG-313).

## 7. Pricing policy sanity check
File: `/workspace/cpq-pricing-policy-sanity-check.md` (2026-10-16; **illustrative scenarios with example numbers, not customer data**). Tests three policies against the LifecycleChange model: P1 standard uplift (SaaS renewals), P2 capacity-meter true-up (on-prem), P3 conversion (on-prem licence → SaaS equivalent in a restructure). Note: the file states the amendment spike did not exist when written, so it was not used.

| # | Scenario | Policy | Verdict | Main breaks |
|---|---|---|---|---|
| 1 | SaaS renewal with 7% uplift (and 3-year variant) | P1 | Fits | No uplift basis, cap/floor, price-as-of date, rounding, precedence rules, annual escalators, or policy snapshot (G1, G2, G9) |
| 2 | Capacity true-up, 64 cores committed, peak 80 | P2 | Partly fits | No measurement method, tolerance, pack rounding, true-down, overage rate, backdating; no derived maintenance %; no one-time retroactive charge; meter readings lack provenance/frozen snapshot (G3–G6) |
| 3 | Mid-term perpetual + maintenance → SaaS | P3 | Breaks | No ConversionPolicy; no Surrender action/state; per-line dates don't fit three change-level dates (G7, G8) |
| 4 | Co-termed multi-asset renewal with add/remove | P1 + maintenance | Fits structurally | Needs time-bounded price locks, maintenance %, per-line price-as-of, ARR bridge (G1, G2, G5, G10) |

**Bottom line [INFERENCE]:** the skeleton holds; the policies are under-specified. Split PricingPolicy into typed, versioned policies (Renewal, Amendment, TrueUp, Maintenance, Conversion), resolved account > agreement > line with contract-clause precedence, snapshotted on each ChangeLine. Prioritised changes: ChangeLine additions (`chargeType`, service period dates, per-line effective/entitlement-end dates, `priceAsOf`, `convertedFromLineIds[]`, `renewalDisposition`, ARR bridge); `Surrender` action/status; MeterReading provenance/status/snapshot; segment schedule on renewal lines; P1 additions; TrueUpPolicy; maintenance as percent of related line; ConversionPolicy (reconcile with DRG-341). Pricing-service should own policy evaluation and return a trace (DRG-310/311). `renewal-uplift.drl` becomes the lowest-precedence default; `amendment-proration.drl` is superseded (RA-08).

## 8. Decisions and standing instructions
- Bounded decisions are routed through the **Jev router** before reaching the user; escalate to the user on a human-call or low-confidence response.
- Standing instruction (call, 2026-10-16): keep this transcript continuously updated; every future work item appends its outputs.
- **Audit elevated to a first-class requirement** (decision trail: which policy fired and why).
- **Design and documentation only; no code changes.**
- Recommendation adopted for design work: one LifecycleChange capability, renewal as an amendment plus term extension plus renewal pricing policy.
- PR #598 (DRG-509) remains open for the user to merge after CI integration tests.

## 9. Open questions (collected)
From the spike: placement (contract-asset-management vs quote-service vs new service; RA-27); how product/pricebook/configuration integrate (pin-and-freeze proposal; RA-05); apply on Executed vs Accepted; thin contract header now vs after DRG-458; whether AMENDED_AND_RESTATED replaces or adds to governing terms; whether resellers/partners sign; whether amendment numbering resets per renewal term; whether one document may target several contracts (proposed no, use merge intent); who may VOID an executed document; retention and legal hold windows (DRG-313); contract grouping rules for backfill.
From the sanity check: all gaps G1–G10 above need policy design decisions; reconcile ConversionPolicy with DRG-341; where policy evaluation lives (proposed pricing-service).
From the research: model is inference and needs validation against real customer data; several sources are third-party or summary-only.

## 10. In-flight / pending
- G1–G10 backlog IDs recorded (see 2026-10-16 G1–G10 section); G9 maps to RA-30 / DRG-564.
- Spike OQ2 (apply after EXECUTED) and OQ4 (numbering) resolved 2026-10-16. Remaining: OQ1 placement (ADR draft at `adr-035-amendment-document-placement.md`, Proposed), OQ3 Contract header enrichment vs DRG-458, OQ5 partial application, OQ6 revision model, OQ7 backfill, OQ8 retention N.
- RA-27..RA-44 / DRG-561..DRG-578 exist in Linear/Notion. Adjacent services assessment done (supersedes deferred day-two note).
- PR #598 (DRG-509) still open on `main` (stack base #597 merged); GitHub reports mergeable clean but **no check runs** on head `ceda2ab` as of 2026-10-16 ~11:47 CDT.
- Any further spikes, ADRs and research updates: append below.

---
<!-- Append new dated sections below this line. -->

## 2026-10-16: RCA "SDA" placement of the amendment entity
**Source files:** `/workspace/rca-sda-amendment-placement.md` (new); context from `cpq-renewals-amendments-research.md`, `amendment-document-design-spike.md`.
**Links:** Trailhead Transaction Management pillars and Salesforce Contracts units; Revenue Management Dev Guide (initiateAmendment, QuoteAction, AssetAction, AssetStatePeriod, AssetActionSource); Data Model Gallery "Transaction Management: Asset/Contract"; Salesforce Contracts ContractDocumentVersion; Conga CLM glossary. Full URLs are in the file.

Key points:
- **"SDA" is not a published Salesforce term.** The best-supported proxy is the Agentforce Revenue Management (Revenue Cloud) pillars: Product Catalog, Pricing, Transaction Management (CPQ + Assets), Salesforce Contracts, DRO and Billing, plus the dev-guide and Data Model Gallery groupings.
- **RCA has no standalone amendment entity.** An amendment is a transaction type: QuoteAction/OrderAction Type = Amend on a normal Quote or Order (CPQ component). Its committed result is AssetAction (read-only, system-populated, business category) plus AssetStatePeriod and AssetActionSource (ALM component). Both components sit in the **Transaction Management** pillar. Contract and AssetContractRelationship are also in Transaction Management. The **legal amendment document** (Amend Contract → ContractDocumentVersion, state model, DocuSign) sits in the separate **Salesforce Contracts** pillar. Pricing (pricing procedures, `skipPricing`) and DRO are called as services.
- **Mapping:** quote-service ≈ CPQ (intent/pricing); CAM ≈ ALM ledger + Contract record; AmendmentDocument ≈ Salesforce Contracts. Option B (quote-service) has the least support.
- **[INFERENCE]** RCA applies changes to the asset at order activation, and contract-document execution runs on a separate track. This supports "apply on Accepted/order, EXECUTED as a parallel legal status" in the open apply-gating decision.
- **Correction to spike §0 market context:** Salesforce Contracts and Conga CLM do document versioned/numbered, executed amendment documents. Narrow the "market gap" claim to the *integration* of the executed amendment with the effective-dated ledger.
- **Conventions to adopt:** single writer of applied state (CAM); action date ≠ effective date; source traceability on every applied period; pricing as a shared service (supports moving continuation pricing to pricing-service, RA-08); orchestration downstream of the order (outbox/saga, not in the aggregate); headless async commands keyed by asset/subscription with an optional contract id; contracts link to assets rather than own them.
- **Recommendation [INFERENCE], confidence medium:** refine spike option A. Put AmendmentDocument plus the thin Contract header in CAM as a distinct contract-documents module, with its own aggregate, schema and `/contracts/{id}/amendments` API, so it can be extracted to option C later. Extract triggers: independent retention/legal hold (DRG-313), full CLM scope, or multi-contract/tenant-specific documents. Feed into the RA-27 ADR.

## 2026-10-16: Deferred item

- Adjacent services maturity evaluation (coding, contract-asset-management, ordering, fulfillment) against new-contract, renewal and amendment cases is DEFERRED to day two, pending the spine and mid-term amendment policy work. Decided by the user on the call. No evaluation performed and no backlog items created for it.

## 2026-10-16: Mid-term amendment policy spec
**Source files:** `/workspace/mid-term-amendment-policy-spec.md` (new); context from `rca-sda-amendment-placement.md`, `amendment-document-design-spike.md`, `cpq-pricing-policy-sanity-check.md`, `cpq-renewals-amendments-research.md`.
**Links:** RA-27..RA-41 / DRG-561..DRG-575; DRG-309, DRG-311, DRG-312, DRG-313, DRG-457, DRG-458, DRG-554. No new backlog items created.

Key points:
- **Frame [INFERENCE]:** spine plus policies. AmendmentDocument (lines, revisions, application units, thin Contract header) is the only data model; mid-term amendment, renewal and price-lock extension are policies on it. Mid-term amendment is the first policy.
- **Scope:** in = mid-term Add/Update/Remove/Swap/Upgrade/Downgrade on one contract, multi-line and multi-subscription, co-termed proration, AmendmentPolicy pricing via the shared pricing service. Out = renewal/term extension, price locks and escalators, true-up, conversion/surrender, maintenance %, multi-contract (merge intent), CLM, fulfilment.
- **Triggers:** manual, headless API (`initiateAmendment`-style), customer click-through [INFERENCE], correcting/reversing amendments, invocation by later policies. Scheduled triggers belong to renewal.
- **States:** reuses the spike's document state machine; apply point left configurable because OQ2 is open.
- **Validation:** 14 numbered testable rules (V1 to V14), including revision match, explicit source lines with ACCEPTED_PRICED provenance, separate action/effective dates with audited backdating, per-line quantity semantics, pricing only in pricing-service with a PolicyDecision, pinned product/pricebook/config, digest match at apply, one executed revision, atomic dependency-ordered units, idempotent apply.
- **Audit (A1 to A8):** immutable records, append-only hash-chained events with a per-document monotonic sequence and verifier [INFERENCE on the last two], gap-free executed numbering, source traceability, decision trail (RA-30), actor/time on every transition, outbox export (DRG-309/312), replay (DRG-311). Applies to every outcome, including rejected, withdrawn, expired, void and blocked.
- **Retention:** outlives the contract (contract end plus N years), legal hold, non-executed outcomes retained as evidence [INFERENCE]; N needs Legal (DRG-313).
- **Composition:** CAM ledger is the single writer of applied state; policy owns guards, modes, allowed actions and reason codes. Extension points: intent, term, typed pricing slot, segment schedule (price locks), trigger and validation hooks; policies cannot remove core spine rules.

Open questions: the 8 spike decisions stay open (placement, apply point, Contract header timing, numbering, partial application, revision model, backfill, retention). Policy-specific: backdating limits and thresholds; mid-term downsell before minimum commitment (RA-16); who may VOID; which pricing gaps gate release one (proposed G1 and G6 needed; others deferred to their policies) [INFERENCE].

## 2026-10-16: Decisions and architecture design review (from call)

### Decisions
1. Salesforce Revenue Cloud (RCA) is the architectural guide. No official "SDA" exists; RCA's amendment placement research is in `rca-sda-amendment-placement.md`.
2. Spine-plus-policies: the amendment entity is the spine. Mid-term amendments, renewals and price-lock extensions are policies on top of it. This supports all three approaches without picking one.
3. Mid-term amendment is built first because it is the most extensible foundation.
4. Audit is mandatory on every path, financial-industry grade: immutable records, tamper-evident sequencing, retention past contract end.
5. No separate audit microservice in v1. Use an embedded hash-chained append-only log in contract-asset-management (CAM), written in the same transaction as the change. Go standalone only if cross-service aggregation is needed later.
6. Adjacent services maturity evaluation (coding, CAM, ordering, fulfillment) is deferred to day two.

### Architecture
- Amendment document plus a thin Contract header live in CAM as a separate module (extractable later).
- The change itself (LifecycleChange), pricing and document render stay in quote-service.
- Root is LifecycleChange, differentiated by intent type (Renew, Amend, Cancel, Restructure, Transfer, Suspend, Resume, TrueUp, Convert).
- CAM is the only writer of applied state. Action date is kept separate from effective date.

### Rationale
- RCA treats an amendment as a transaction type whose committed result is a ledger entry; the legal document sits in a separate contracts pillar. Both map cleanly onto quote-service (change) and CAM (ledger plus document).
- An embedded same-transaction log avoids a distributed-consistency problem and keeps the hash chain gapless; a separate service adds failure modes with no v1 benefit.

### Mid-term policy spec (`mid-term-amendment-policy-spec.md`)
14 validation rules (V1-V14), 8 audit requirements (A1-A8), hash-chained append-only history, six extension points for renewal and price-lock policies.

### Still open
Eight spike decisions (placement ADR RA-27/DRG-561, apply timing, Contract header timing, numbering, partial application, revision model, backfill, retention N from Legal) plus the spec's four extra questions.

## 2026-10-16: Amendment entity field schema
**Source files:** `/workspace/amendment-entity-field-schema.md` (new); binding target `mid-term-amendment-policy-spec.md`; context from `amendment-document-design-spike.md`, `rca-sda-amendment-placement.md`, `cpq-pricing-policy-sanity-check.md`, `cpq-renewals-amendments-research.md`.
**Links:** RA-29/DRG-563, RA-30/DRG-564, RA-15, RA-33/DRG-567, RA-34/DRG-568, RA-38/DRG-572, DRG-458. Highest backlog IDs checked: RA-41 / DRG-575. No new backlog items created.

Key points:
- **Placement as instructed:** LifecycleChange, pricing calls and render requests stay in quote-service; ContractHeader, AmendmentDocument, revisions, lines, application units and the append-only event log live in a separate contract-documents module in CAM. CAM ledger stays the single writer of applied state. Salesforce RCA analogues noted (QuoteAction, Contract plus AssetContractRelationship, ContractDocumentVersion, AssetStatePeriod, AssetActionSource).
- **Spine:** one AmendmentDocument per LifecycleChange for all nine intents; policies add no tables.
- **Field tables** (type, nullability, constraint, V#/A# source, [S]/[I]) for the LifecycleChange link, ContractHeader, AmendmentDocument, AmendmentRevision, AmendmentLine/ChangeLine, ApplicationUnit and AmendmentEvent (`seq_no`, `prev_hash`, `content_hash`, actor, UTC timestamps, INSERT-only plus verifier).
- **Constraints:** unique `lifecycle_change_id` and inbox `source_event_id` (idempotency); partial unique index for one EXECUTED revision; gapless `amendment_seq` assigned from `contracts.last_executed_seq` in the EXECUTED transaction [INFERENCE]; apply key (`document_id`, `unit_no`, digest); per-document gapless event sequence.
- **Policy binding table:** mid-term amendment, renewal and price-lock extension mapped to the fields they read, write and constrain, and the extension points they use.
- **Spike decisions not decided:** fields tied to OQ2 to OQ8 are marked "dep OQn" with the spike default.

Open questions: provisional number at DRAFT vs final gapless number at EXECUTED (OQ4); single `status` vs parallel `apply_status` (OQ2); hash chain scope (per document vs contract or global anchor); whether price-lock extension registers its own intent; ContractHeader status ownership vs DRG-458; retention N years (Legal, OQ8).

## 2026-10-16: Resolved decisions (amendment schema open items)

1. **Numbering (OQ4), resolved:** drafts carry a provisional number stable across revisions. The final amendment sequence is assigned only at execution as last executed + 1, inside the same transaction. Format `{contract_number}-A{nnn}`. No reset on renewal. Rationale: provisional numbering avoids gaps with in-flight drafts.
2. **Apply status (OQ2), resolved:** single `status` column only. Changes are applied only after the amendment is executed. No parallel apply-status track; `apply_status` dropped from v1. Rationale: avoids dual-state consistency overhead under financial-grade audit; pre-signature application is handled as an edge case, not a modeled lifecycle.
Schema doc `amendment-entity-field-schema.md` updated. Remaining spike decisions still open: placement ADR, Contract header timing, partial application, revision model, backfill, retention N.

## 2026-10-16: Renewal and price-lock policy stubs
**Source files:** `/workspace/renewal-policy-stub.md` (new), `/workspace/price-lock-policy-stub.md` (new); inputs `mid-term-amendment-policy-spec.md`, `amendment-entity-field-schema.md`, `amendment-document-design-spike.md`, `rca-sda-amendment-placement.md`, `cpq-pricing-policy-sanity-check.md`.
**Links:** RA-06/DRG-540, RA-07/DRG-541, RA-09/DRG-543, RA-10/DRG-544, RA-11/DRG-545, RA-12/DRG-546, RA-15/DRG-549, RA-20/DRG-554, RA-21/DRG-555, RA-27..RA-34 / DRG-561..DRG-568, RA-41/DRG-575, DRG-313, DRG-458, DRG-532, DRG-533, DRG-534. No new backlog items created.

Key points:
- **Both are policies on the spine, adding no tables.** Each stub binds to the spec's six extension points and lists inherited or tightened V#/A# rules; neither removes V3, V8, V10, V12, V13; both inherit A1 to A8 with audit in CAM, hash-chained, same transaction.
- **Renewal:** intent `Renew`, kind `RENEWAL`. Overrides the term hook (extends `effective_to` and `contracts.end_date`) and the pricing hook (RenewalPolicy with uplift basis, cap, floor, price-as-of, rounding, clause-first precedence, per-line snapshot); leaves the segment hook to price-lock; adds lead-time, notice-window and term-end triggers. Local rules RN1 to RN5 [INFERENCE]: start day after term end unless co-term anchor, header end equals latest renewed line, decision records basis and precedence, one open renewal per term (guards RA-11), perpetual lines never renewed. Intended consumer of the unconsumed renewal-eligible event (RA-06).
- **Price-lock:** no intent of its own [INFERENCE]; runs under `Renew` (lock or escalator in the renewal term) or `Amend` (promotional lock; lock extension as `Update` on `segment_schedule`, schema default). Overrides the pricing hook (`policy_slot = PriceLock`) and the segment hook; triggers at quote, at term extension and on lock extension; lock expiry needs no job because segments apply as future-dated rate periods. Local rules PL1 to PL5 [INFERENCE]: contiguous segments covering the line term, no lock past contract end without a Renew, declared rounding per step, pinned pricebook for post-lock List, shortening a live lock is a V14 override. Anchored on RA-34/DRG-568; general escalators stay with DRG-532/533, ramps with DRG-534.
- **Inputs split:** quote-service supplies pricing decisions (evaluated by pricing-service), term extension, uplift basis and price-as-of; CAM supplies ledger rate periods, `commercial_revision`, Contract header and term dates.
- **Flagged [INFERENCE]:** notice period and `term_end_action` are not on the schema's ContractHeader; the 2026-10-16 review puts render in quote-service while the spike names document-generation (reconcile in RA-27).

Open questions: OQ2 (apply after EXECUTED) and OQ4 (provisional then final number, no reset on renewal) are recorded as resolved per the section above; OQ1, OQ3, OQ5 to OQ8 stay open, with renewal and price-lock impacts listed in each stub. Renewal: renewal from InGrace or Expired; projecting an open unapplied amendment into the renewal. Price-lock: lock behaviour on mid-term quantity increase; locks spanning renewal terms; expiry notice; own intent vs Amend (schema open question 4).

## 2026-10-16: Sanity-check gaps G1–G10 backlog IDs (recorded)

Source: `/workspace/ppsc/items.json` and Notion batches under `/workspace/ppsc/` (verified against live Linear titles DRG-567..575 and DRG-564). Closes the "Missing: record IDs when known" note in §5.1 / §10.

| Gap | RA | DRG | Priority | Title |
|---|---|---|---|---|
| G1 | RA-33 | DRG-567 | High | Renewal uplift basis, caps, price-as-of date and policy precedence |
| G2 | RA-34 | DRG-568 | Medium | Multi-year renewal escalators and time-bounded price locks in renewal policy |
| G3 | RA-35 | DRG-569 | High | Capacity true-up policy: measurement, tolerance, pack rounding, overage rate and backdating |
| G4 | RA-36 | DRG-570 | Medium | Meter reading provenance, dispute and frozen true-up snapshot |
| G5 | RA-37 | DRG-571 | High | Maintenance pricing policy: percent of licence net, recalculation and reinstatement |
| G6 | RA-38 | DRG-572 | Medium | One-time and retroactive charge lines with their own service period |
| G7 | RA-39 | DRG-573 | High | Conversion pricing policy for on-prem to SaaS (quantity mapping, parity price, credit stack) |
| G8 | RA-40 | DRG-574 | Medium | Perpetual licence surrender and entitlement revocation in conversions |
| G9 | RA-30 | DRG-564 | Medium | Resolved pricing policy snapshot and provenance on each change line (no new issue; maps to existing continuation-pricing audit item) |
| G10 | RA-41 | DRG-575 | Low | ARR bridge per change line: price, quantity, cross-sell, conversion and non-renewal effects |

G9 was not created as a separate Linear/Notion row in the ppsc batches; it overlaps RA-30 / DRG-564.

## 2026-10-16: Adjacent services maturity assessment (supersedes "Deferred item")
**Source files:** `/workspace/adjacent-services-maturity-assessment.md` (new); repo `drgaciw/open-crm-cpq` `main` @ `dd90501` read via GitHub connector (product-service, configuration-engine, pricebook-service, contract-asset-management, `libs/common` QuoteAcceptedEvent, `docs/adr/ADR-024` and `ADR-027`).
**Links:** RA-42/DRG-576, RA-43/DRG-577, RA-44/DRG-578 (new); existing DRG-290, 421, 428, 429, 458, 460, 464..467, 495, 535..566, 567, 574.

**Supersedes** the 2026-10-16 "Deferred item" entry and decision 6 of the design review (evaluation deferred to day two). The evaluation is now done.

- **"Coding"** read as product, SKU and configuration coding (product-service plus configuration-engine and pricebook-service); no service by that name exists [I].
- **Verdicts (new contract / renewal / amendment):** Coding Mature / Partial / Not ready; CAM Partial (SaaS Mature) / Partial / Partial; Ordering Not ready on all three; Fulfillment Partial (SaaS activation via simulated ERP) / Not ready / Not ready [V].
- **Key facts [V]:** no order or fulfilment service exists; ADR-024..027 are Proposed (2026-10-04), not accepted, and predate the spine; ADR-024's claim that CAM ignores `dealType` is stale; `ErpAdapter` is simulated; CAM never sets EXPIRED; product-service has no product relationships and updates products in place.
- **Readiness [I]:** only CAM is near ready, and only for SaaS. On-prem (60%) is blocked downstream of CAM. Mid-term SaaS amendments can ship without ordering or fulfilment if the seam is fixed now.
- **Build order [I]:** seams (DRG-561, RA-43) > CAM spine (DRG-458, 562, 563, 564) > coding minimum (DRG-421, 428, RA-42, DRG-539) > mid-term policy then renewal policy > ordering skeleton > fulfilment and on-prem (DRG-460, 558, RA-44, 466, 495, 556, 557).
- **New backlog (highest IDs checked first: RA-41/DRG-575):** RA-42/DRG-576 catalog product relationships (High); RA-43/DRG-577 reconcile ADR-024..027 with the spine (High); RA-44/DRG-578 action-aware fulfilment decomposition (Medium). Linear and Notion in sync and cross-linked.

## 2026-10-16: RA-27 / DRG-561 placement ADR draft (Proposed)

**Source files:** `/workspace/adr-035-amendment-document-placement.md` (new); inputs `rca-sda-amendment-placement.md`, `amendment-document-design-spike.md`, `mid-term-amendment-policy-spec.md`, `amendment-entity-field-schema.md`, `adjacent-services-maturity-assessment.md`, call decisions of 2026-10-16.
**Links:** RA-27 / DRG-561; RA-43 / DRG-577; DRG-458; DRG-313. No new backlog items. Documentation only; not committed to the repo.

Key points:
- **Status:** Proposed. Does not authorize code.
- **D-01:** AmendmentDocument (revisions, lines, application units, hash-chained event log) in CAM as a distinct **contract-documents** module with API `/contracts/{id}/amendments`.
- **D-02:** Thin Contract header in CAM now; DRG-458 enriches later.
- **D-03:** LifecycleChange, pricing-service evaluation and render requests stay in quote-service.
- **D-04:** Extract to a separate service only for retention/CLM/multi-contract triggers.
- **D-05 / D-06:** One document per LifecycleChange; policies add no tables; action date ≠ effective date.
- **Rejected:** quote-service ownership (B); new service now (C); hanging documents on Proposed ADR-024 (D).
- **Still open outside this ADR:** OQ3 field enrichment, OQ5 partial apply, OQ6 revision edges, OQ7 backfill, OQ8 retention N, render ownership detail, price-lock intent.

Closes the "awaiting placement ADR draft" gap for OQ1. Acceptance of the ADR remains a human (or Jev-routed Choice) decision.

## 2026-10-16: Renewals and amendments dev-readiness deep dive (first slice)
**Source files (new):** `/workspace/renewals-amendments-dev-readiness.md`, `/workspace/amendment-first-slice-requirements-and-acceptance.md`, `/workspace/amendment-technical-approach-and-dependencies.md`, `/workspace/renewals-amendments-open-questions-register.md`. **Appended (addendum, history kept):** `mid-term-amendment-policy-spec.md`, `amendment-entity-field-schema.md`, `amendment-document-design-spike.md`, `adr-035-amendment-document-placement.md`, `adjacent-services-maturity-assessment.md`.
**Inputs read:** the project docs above; live repo on branch `docs/renewals-amendments-design-2026-10-16` and `main` through the GitHub connector (CAM migrations V1_1..V1_6, so next is V1_7). Highest IDs re-checked before creating items: DRG-578 (Linear) and RA-44 (Notion).
**Documentation only.** No code changes, no PRs merged, no commits to main.

**Verdict:** conditionally ready. Start S1 and S2 within one to two days if ADR-035 is accepted (OQ1) and pricing-service amendment modes are verified before S5 (OQ9). Slice is about one working week for two to three engineers [I].

**Slice cut:** SaaS only; Add, Update, Remove; click-through, notice-only or not-required signature; no Swap, Upgrade, Downgrade (needs RA-42), no on-prem, no ordering, no fulfilment, no history backfill.

**Gap analysis highlights:** no numbered requirements or Given/When/Then criteria existed; the unique key on `applied_quote_acceptances` conflicts with multi-subscription documents (resolved by a new apply ledger, legacy untouched); no event-contract or legacy-routing plan; pricing-service support for amendment modes unverified; open decisions were scattered across four files.

**New backlog (Linear team DRG, project OPEN-CRM-CPQ, labels renewals-amendments and Open-CRM-CPQ; Notion Open-CRM-CPQ Backlog):**
| RA | DRG | Title |
|---|---|---|
| RA-45 | DRG-579 | S1 CAM contract-documents schema migration |
| RA-46 | DRG-580 | S2 hash-chained event writer, verifier, audit publication |
| RA-47 | DRG-581 | S3 amendment document API and state machine |
| RA-48 | DRG-582 | S4 document-driven apply after EXECUTED |
| RA-49 | DRG-583 | S5 quote-service LifecycleChange (Amend), pricing, pinning |
| RA-50 | DRG-584 | S6 event contract and per-tenant legacy gating |
| RA-51 | DRG-585 | S7 contract header backfill (no history) |
| RA-52 | DRG-586 | S8 end-to-end acceptance suite |
Dependencies set as Linear blocking relations. Comments added to existing DRG-535, DRG-561, DRG-562, DRG-563. No issue renumbered, renamed, moved or deleted.

**Open questions:** register has OQ1..OQ20. Blocking: OQ1 (accept ADR-035) and OQ9 (pricing-service modes; gates S5 only). Needed before go-live: OQ8 retention N (Legal). Defaults proposed for OQ3, OQ5, OQ6, OQ7, OQ10, OQ11, OQ12, OQ14, OQ17, OQ18, OQ19.

**Repo copy:** PR #599 was already merged into `main` on 2026-10-16 (17:23 UTC) before this pass. New and updated docs were committed only to branch `docs/renewals-amendments-design-2026-10-16` under `docs/renewals-amendments/`; they will not appear in PR #599 and are not on `main` until a new PR is opened (not done; needs user approval).
