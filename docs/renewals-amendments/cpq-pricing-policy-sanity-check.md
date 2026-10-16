# Pricing policy sanity check: renewals and amendments
*16 Oct 2026. Design and analysis only. Companion to `/workspace/cpq-renewals-amendments-research.md` (source IDs S1–S74 refer to that file's list). Every scenario is an **illustrative scenario with example numbers, not customer data**. **[INFERENCE]** marks my own analysis or design. `/workspace/amendment-document-design-spike.md` appeared while this was being finished; it was used as a cross-check (section 4) and for de-duplication.*

## 1. What was tested
Three proposed policies were run against the LifecycleChange meta model (research section 7) and against today's open-crm-cpq behaviour (one typed continuation, `QuoteContinuationPolicy`, pricing-service echo check, unexecuted Drools rules):

- **P1 Standard uplift policy** (SaaS renewals): renewal mode Same / List / Uplift% / Uplift amount / override, inherited account > agreement > line.
- **P2 Capacity-meter policy** (on-prem): computes a true-up from usage data against committed capacity.
- **P3 Conversion policy** (restructure): reprices an on-prem licence into a SaaS subscription equivalent.

The customer is a legacy software vendor whose book is about 60% on-prem (transactional/perpetual and capacity licences) and about 40% SaaS. Scenarios 1–3 are linked by one illustrative customer, "Acme Corp" (fictional). Agreements: SaaS master **MSA-1001**; licence and maintenance agreement **LM-2002**; new SaaS agreement **SaaS-3003**.

## 2. Summary of findings

| # | Illustrative scenario | Policy fired | Meta model verdict | What breaks or is missing | Fix (gap ID) |
|---|---|---|---|---|---|
| 1 | Pure SaaS renewal with 7% uplift (and a 3-year variant) | P1 | Fits. Intent, ChangeLine and policy hierarchy hold | No **uplift basis** (net vs list vs list-less-discount), no **cap/floor**, no **price-as-of date**, no **rounding rule**, no precedence between contract price protection, line override and the Drools default; one flat period cannot express **annual escalators**; resolved policy is not **snapshotted** | G1, G2, G9 |
| 2 | Capacity true-up: 64 cores committed, peak use 80 | P2 | Partly fits. TrueUp intent and MeterReading exist (RA-23) | P2 has no **measurement method** (peak 16 cores vs average 6), tolerance, pack rounding, true-down rule, overage rate basis or **backdating** method; maintenance cannot be **derived** as % of licence net; no **one-time / retroactive charge line** with its own service period; meter readings lack **provenance and a frozen snapshot** | G3, G4, G5, G6 |
| 3 | Mid-term perpetual + maintenance to SaaS conversion | P3 | Breaks. Neither `Restructure` nor `ConvertMetric` carries licence-model conversion pricing | No **ConversionPolicy** (quantity mapping, parity price, credit stack, caps, cross-agreement credit); no **Surrender** action or state for a perpetual line; per-line dates (maintenance end, SaaS start, dual-use end, revocation) do not fit three change-level dates | G7, G8 |
| 4 | Co-terminated multi-asset renewal across SaaS and on-prem, with add and remove | P1 + maintenance | Fits structurally (multi-agreement targets, mixed modes per line) | Needs **time-bounded price locks** inside the renewal term (G2), maintenance % (G5), per-line price-as-of date (G1), and an **ARR bridge** that separates uplift, quantity, cross-sell and non-renewal (G10) | G1, G2, G5, G10 |

**Bottom line [INFERENCE]:** the LifecycleChange skeleton holds in all four scenarios. The three policies are under-specified rather than wrong. P1 lacks inputs, P2 is a name rather than a policy, and P3 does not exist as a distinct policy in the model. The biggest model change is to stop treating PricingPolicy as one flat record. It should be a set of typed policies (Renewal, Amendment, TrueUp, Maintenance, Conversion), each version-pinned on the ChangeLine.

## 3. Scenarios

### Scenario 1 (illustrative): pure SaaS renewal with uplift
**Setup (example numbers).** MSA-1001 has "Analytics Cloud", 200 seats, list $50/seat/month, contracted net $40 (20% discount), term 2025-11-01 to 2026-10-31. It also has "Premium Support" at $1,000/month (net = list). Account policy: UpliftPct 7%. Line override on Premium Support: Same. The new price book effective 2026-11-01 lists Analytics Cloud at $52.

**Meta model walk-through.**
- *Intent:* `Renew`. *Trigger:* `LeadTime` (90 days, run 2026-08-02).
- *Source:* MSA-1001 lines CL-1 (Analytics Cloud, Subscription, seat) and CL-2 (Premium Support).
- *Target:* MSA-1001 with a new term. Same agreement, new state periods; or a linked agreement with `priorAgreementId`, per HubSpot [S44].
- *ChangeLines:*
  - CL-1: `Renew`, `Absolute` endQty 200, startQty 200, deltaQty 0, category Renewal.
  - CL-2: `Renew`, `Absolute` 1.
- *Policy resolution:* CL-1 inherits account UpliftPct 7%. CL-2 uses its line override, Same. The line override wins, as with SF CPQ subscription-over-contract [S6].
- *P1 inputs:* exit net unit price, list price (and its price-book version and as-of date), uplift %, **uplift basis**, cap/floor, rounding, churn score (if the Drools default applies), renewal term.
- *Dates:* actionDate 2026-08-02. customerAcceptance 2026-10-20. contractEffective = serviceActivation = 2026-11-01, the day after term end, matching the repo and SF CPQ [S4][S5]. New term ends 2027-10-31.

**Worked example.** Assumption: basis = contracted net, per-unit rounding to 2 dp.
- CL-1: 40.00 × 1.07 = **$42.80**. ARR 200 × 42.80 × 12 = **$102,720** (prior $96,000, +$6,720).
- CL-2: Same, $12,000 ARR.
- **Basis sensitivity:**
  - List-less-contracted-discount: 52 × 0.8 = $41.60, ARR $99,840.
  - List: $52.00, ARR $124,800.
  - The seeded Drools rule at churn score > 70 gives 40 × 1.03 = $41.20, ARR $98,880.
  - These are four defensible answers from one "7% uplift" policy.
- **3-year variant** (customer asks for 36 months with an annual 7% escalator), per-year rounding:
  - Y1 $42.80, Y2 $45.80, Y3 $49.01. TCV $330,264.
  - Compounding without intermediate rounding gives Y3 = 40 × 1.07³ = $49.00, so the rounding rule changes the result.

**Audit trail it should produce.**
- LifecycleChange (Renew, LeadTime, actionDate, three effective dates).
- Per ChangeLine:
  - start, delta and end qty;
  - resolved policy snapshot: mode, basis, %, cap applied y/n, which level supplied it (account / agreement / line / rules default), policy version id;
  - inputs: exit price 40.00 with source state-period id, list 52.00 with price-book version and as-of date, churn score with timestamp and model version;
  - output unit price and extended amount.
- Approval (if any), then the Applied state periods carrying `sourceChangeLineId` [S1][S16].

**Where it breaks.**
1. **Missing fields** on P1:
   - `upliftBasis {ContractedNet | CurrentList | ListLessContractedDiscount}`
   - `upliftCap {maxPct, notAboveList}` and `floor`
   - `priceAsOf {ActionDate | EffectiveDate | LineStart}`
   - `rounding {precision, stage: Unit | Extended, perStep}`
   **[INFERENCE]** Enterprise contracts commonly carry "renewal increase capped at X%" price-protection clauses. These have to beat an account default, so the precedence order must be explicit: contract clause > line override > agreement > account > rules-engine default. → **G1**
2. **Multi-year renewal.** The repo writes one flat ABSOLUTE period, and the meta model's `ramp/segments` is a single field with no schedule semantics. RCA's `initiateRenewal` takes `rampOptionsDetails` for exactly this case [S13]. → **G2**
3. **Repo:** uplift is caller-supplied (default 0). The List and ListLessDiscount bases need a price-book read that the continuation flow does not make today (already RA-05/DRG-539). Pricing-service only checks that the accepted net is unchanged (`SUBSCRIPTION_CONTINUATION_RATE_CHANGED`), so it validates P1's output but cannot compute it. **[INFERENCE]** P1 should run in pricing-service, with quote-service passing the resolved policy.
4. **No pinned snapshot.** If the account policy changes from 7% to 5% after the quote is presented, nothing records which value priced the quote. → **G9** (already covered by RA-30 / DRG-564 from the parallel spike; not duplicated)

**Worth adopting:** Maxio's *Lock-in Date* [S50] (https://docs.maxio.com/billing/subscriptions/configuration/term-renewal/term-subscription-renewals) freezes the auto-renewal price before term end. It gives a natural point to snapshot the resolved policy. Conga's *Uplift Amount* [S25] covers flat per-unit escalators that a % cannot express.

---

### Scenario 2 (illustrative): capacity true-up where on-prem usage exceeded commitment
**Setup (example numbers).** LM-2002 has:
- **CL-10** "DataEngine Enterprise": perpetual capacity licence, metric = core, committed 64, bought at $2,500/core.
- **CL-11**: maintenance at 20% of licence net = $500/core/year, i.e. $32,000/year. Term 2026-01-01 to 2026-12-31.

The contract has an annual true-up with a 9-month measurement window (2026-01-01 to 2026-09-30). Monthly peak core readings: 60, 62, 64, 66, 70, 72, 76, 78, 80.

**Meta model walk-through.**
- *Intent:* `TrueUp`. *Trigger:* `TrueUpCycle`. A `Threshold` warning would have fired at 64 in March, per Thales peak-capacity thresholds [S59].
- *Source and target:* LM-2002, CL-10 and CL-11.
- *ChangeLines:*
  - CL-10: `Update`, `Delta` +16 (equivalently `Absolute` 80), category TrueUp.
  - CL-11: `Update`, `Delta` +16, prorated.
  - Optional third line: **back-maintenance** for the months the excess was in use. There is no action code for a one-time retroactive charge that does not change a commitment.
- *P2 inputs:* frozen set of MeterReadings (ids, source, attestation), aggregation method, window, tolerance, pack size, overage rate basis, backdating method, true-down rule.
- *Dates:*
  - actionDate 2026-10-05 (report run);
  - measurement window ends 2026-09-30;
  - contractEffective 2026-10-01 for the new capacity;
  - back-maintenance service period 2026-04-01 to 2026-09-30, which falls before the effective date and so needs its own service period;
  - fulfilment (licence regeneration to 80 cores) after Applied.

**Worked example.**

| Assumption | Excess cores | Licence charge @ $2,500 | Maintenance forward (92/365 days of $500/core/yr) | Back-maintenance |
|---|---|---|---|---|
| Peak (high-water mark), forward-only | 16 | $40,000 | 16 × 500 × 92/365 = **$2,016.44** | none |
| Peak, backdated monthly from first exceedance | 16 | $40,000 | $2,016.44 | excess core-months 2+6+8+12+14+16 = 58 × $41.67 = **$2,416.67** |
| Average of readings (69.8, rounded to 70) | 6 | $15,000 | $756.16 | n/a |
| Peak, pack size 8 cores, 13 excess (variant) | rounds to 16 | $40,000 | as row 1 | n/a |

A 5% tolerance (ignore readings up to 67.2) does not change the 80-core result, but it would have suppressed a 66-core peak. The measurement method alone moves the charge from $15,756 to $44,433, a factor of about 2.8. P2 is not a policy until it pins these choices.

**Audit trail.**
- TrueUp change record holding the **frozen meter snapshot id** and a hash of the readings used.
- Aggregation result (80), commitment (64), excess (16), tolerance and pack rounding applied.
- Rate source (contracted rate $2,500 from the CL-10 state period).
- Maintenance recomputation trace (20% × licence net).
- Back-maintenance service period.
- Fulfilment event: Revenera `upsellFulfillment` against an UPSELL line [S56], or Thales "recharge" with mode **Add** for Delta or **Overwrite** for Absolute [S57][S58].

**Where it breaks.**
1. **P2 needs its own typed policy:**
   - `TrueUpPolicy { aggregation: Peak | P95 | Average | EndOfPeriod; window; tolerancePct; packSize; trueDown: Never | AtRenewal | Allowed; overageRate: Contracted | List | Multiplier(x); backdate: None | FromFirstExceedance | FromWindowStart; prorationMethod }`
   RA-23 (DRG-557) covers the meter model and the intent, not these semantics. → **G3**
2. **Maintenance as a derived price.** CL-11 must be recomputed whenever CL-10's quantity or net changes. PricingPolicy has no `PercentOfRelatedLine` mode, and research section 7.4 only notes "Maintenance % of licence net" as an inference. RA-22 (DRG-556) covers the linkage, not the pricing. → **G5**
3. **One-time and retroactive lines.**
   - Back-maintenance, reinstatement fees and true-up arrears are non-recurring charges with a service period that can precede the change's effective date.
   - The repo has one recurring line per continuation, and ChangeLine has no `chargeType {Recurring | OneTime | Usage}` or `servicePeriod`.
   → **G6**
4. **Meter provenance.**
   - On-prem usage comes from licence-server logs or customer self-declarations, and both can be disputed.
   - The true-up must price a **frozen, attested snapshot** so that a re-run after a correction produces a revision, not a silent change.
   - DRG-470 covers idempotent usage ingestion, not attestation, dispute or freezing.
   → **G4**
5. **Repo:** there is no capacity or meter model at all, and the unexecuted `amendment-proration.drl` (`deltaAmount × remainingTermDays/fullTermDays`) would give $2,016.44 for the forward maintenance. That matches daily proration here only because the "full term" is 365 days (RA-08 already tracks the formula conflict).

**Worth adopting:** DealHub `isDryRun` [S19] (https://developers.dealhub.io/docs/amend-a-subscription) previews the true-up before the customer sees it. Thales Add vs Overwrite licence-update modes [S58] (https://docs.sentinel.thalesgroup.com/softwareandservices/ems/EMSdocs/LDK-UG/creatingEntitlement.htm) map one-to-one onto Delta vs Absolute quantity semantics. Thales notes that updates fail for perpetual models, which supports regenerating rather than editing perpetual keys.

---

### Scenario 3 (illustrative): mid-term on-prem to SaaS conversion
**Setup (example numbers).** After Scenario 2, LM-2002 holds:
- CL-10: 80 perpetual cores, historical book value $200,000.
- CL-11: maintenance $40,000/year (80 × $500), paid annually in advance for 2026.

The customer converts to "DataEngine Cloud" (metric = compute unit, CU, list $70/CU/month). The new agreement is SaaS-3003, 36 months from 2026-07-01. The customer keeps on-prem running until 2026-12-31 to migrate (dual-use).

**Meta model walk-through.**
- *Intent:* `Restructure` with `Convert` semantics. *Trigger:* `Manual`.
- *Source:* LM-2002 CL-10 and CL-11. *Target:* new SaaS-3003, with `priorAgreementId` = LM-2002. This is the SAP "New Contract" pattern [S43] and DealHub RESTRUCTURE [S18].
- *ChangeLines:*
  - (a) CL-11 maintenance: `Terminate`, effective 2026-07-01, credit policy Prorated.
  - (b) CL-10 perpetual: **no fitting action code**. It is not `Terminate` (perpetual rights do not "end"), and not `ConvertMetric` (the metric and the licence model both change). Proposed: `Surrender`, effective 2026-12-31 (end of dual use), state `Surrendered`, then entitlement revocation [S60].
  - (c) SaaS-3003 CL-30: `Add`, `Absolute` 160 CU, Subscription, linked via `convertedFromLineIds` = [CL-10, CL-11].
- *P3 inputs:* quantity mapping table (1 core = 2 CU, version v3), price basis, parity multiple, target list, credit stack (unused prepaid maintenance, licence trade-in % of book value), caps, credit target (first invoice, spread, or declining line), price-lock term.
- *Dates (four per-line dates, not three change-level dates):*
  - actionDate 2026-05-15; customerAcceptance 2026-06-10;
  - maintenance end 2026-06-30;
  - SaaS contractEffective = serviceActivation 2026-07-01;
  - perpetual surrender and revocation 2026-12-31.

**Worked example.** Assumptions: parity = 2.5 × current maintenance ARR [INFERENCE: SaaS includes hosting]; prorated maintenance credit; trade-in 10% of book capped at 20% of year-1 fees.
- Quantity: 80 cores × 2 = **160 CU**.
- Parity ARR: 40,000 × 2.5 = $100,000, so 100,000 / 12 / 160 = $52.0833, rounded to **$52.08/CU/month**. ARR = 160 × 52.08 × 12 = **$99,993.60**. Rounding drift is −$6.40, so the policy must say whether the unit price or the ARR is held fixed. The effective discount vs list ($134,400 ARR) is **25.6%**, which may trigger approval.
- Unused maintenance credit: 40,000 × 184/365 = **$20,164.38**.
- Trade-in credit: min(10% × 200,000 = 20,000; 20% × 99,993.60 = 19,998.72) = **$19,998.72**.
- Year-1 net = 99,993.60 − 20,164.38 − 19,998.72 = **$59,830.50**. Years 2–3 at $99,993.60, unless a parity ramp (e.g. 1.5×/2.0×/2.5×) is chosen.

**Audit trail.**
- Restructure change with links old → new (`convertedFromLineIds`, `priorAgreementId`).
- Mapping table version, parity multiple, rounding decision.
- Each credit with its basis, cap and **source agreement**, consumed on the **target agreement** (a cross-agreement credit transfer).
- Approval for the 25.6% discount and the credit cap.
- State changes: CL-11 Terminated 2026-06-30; CL-10 Active (dual-use) then Surrendered 2026-12-31; CL-30 PendingActive then Active.
- Revocation event for the on-prem keys.

**Where it breaks.**
1. **P3 does not exist as a distinct policy.** Proposed:
   - `ConversionPolicy { sourceModel, targetModel, quantityMap(ratio, rounding, minimum, version), priceBasis: SpendParity(multiple) | TargetListLessDiscount | Override, parityRamp[], credits[{type: UnusedPrepaid | TradeInPctOfBook | Fixed, basis, cap, application: FirstInvoice | Spread | DecliningLine}], creditTransfer{sourceAgreement, targetAgreement, expiry}, priceLockMonths }`
   RA-25 (DRG-559) asks for restructure "with a credit policy" generically. DRG-341 implements one credit variant: 30% of book value on the *declining* line's first period, capped at that line's TCV, never on the SaaS line. That fits a phased hybrid ramp but not this big-bang conversion, where the declining line is terminated. **[INFERENCE]** ConversionPolicy should cover both through `application`. → **G7**
2. **Perpetual licence surrender** needs:
   - an action code;
   - a CommitmentLine status `Surrendered` (distinct from Terminated or Expired);
   - an `entitlementEndDate` separate from the commercial end;
   - a revocation step.
   RA-24 lists the states PendingActive to Terminated, without surrender. → **G8**
3. **Repo:** expansion keeps inherited unit rates and co-terms to the master end, and pricing-service rejects any net-price move. A conversion therefore cannot be a continuation today and must be a new quote plus manual credits. One-recurring-line-per-continuation also blocks the three-line change (RA-02).

**Worth adopting:**
- Oracle BRM transition proration (`PIN_TRANS_PRO_NORMAL_ON_TRANSITION` prorates the last old fee and the first new fee; purchase fees can be waived) [S69] (https://docs.oracle.com/en/industries/communications/billing-revenue/15.2/opcode-guide/subscription-opcode-workflows.html). This is the right primitive for the maintenance-out / SaaS-in boundary.
- Snow's base-to-upgrade quantity mapping, including metric changes (installations → processors) [S62] (https://docs.flexera.com/snow-license-manager-ui/concepts-terminology-and-features/license/upgrade-and-downgrade). This is the model for a versioned quantity map.
- Chargebee termination credits none / prorated / full [S47] for the maintenance credit.

---

### Scenario 4 (illustrative): co-terminated multi-asset renewal with add and remove
**Setup (example numbers).** Acme wants one anniversary, 2027-12-31, across both agreements.
- MSA-1001: Analytics Cloud as in Scenario 1, ending 2026-10-31. "AI Assist", 50 seats at $20, added mid-term 2026-04-01 and co-termed, with a **promotional price lock until 2027-03-31**.
- LM-2002 (no conversion in this variant): DataEngine maintenance $40,000 and "Reporting Server" term licence $28,000/year, both ending 2026-12-31. "Legacy Connector" maintenance $3,000/year, not to be renewed.
- New product: "Data Catalog", 100 seats.

**Meta model walk-through.**
- *Intent:* `Renew` across `targetAgreementIds` [MSA-1001, LM-2002].
- *CoTerm:* align to an explicit anchor 2027-12-31. Neither LatestEnd nor EarliestEnd gives this; RA-20 already covers it.
- *ChangeLines:*
  - Analytics Cloud: `Renew`, 14-month term 2026-11-01 to 2027-12-31, UpliftPct 7% (account).
  - AI Assist: `Renew`, Same until 2027-03-31, then UpliftPct 7%. This is **two segments in one renewal**.
  - Data Catalog: `Add`, list $15 less 10%, CrossSell.
  - DataEngine maintenance: `Renew`, 2027 calendar year, UpliftPct 5% (agreement LM-2002 override) on a % of licence net basis.
  - Reporting Server: `Renew`, mode List, list as of **2027-01-01** (line start), not 2026-11-01 (change effective date).
  - Legacy Connector: not renewed. This is a **non-renewal disposition**, not `Remove`, which would invite a credit or termination fee.
- *Dates:* change actionDate 2026-08-15. Per-line starts: 2026-11-01 for SaaS, 2027-01-01 for on-prem.

**Worked example.**
- Analytics Cloud: 200 × 42.80 × 14 = **$119,840**.
- AI Assist: 50 × 20 × 5 (Nov–Mar) + 50 × 21.40 × 9 (Apr–Dec) = 5,000 + 9,630 = **$14,630**.
- Data Catalog: 100 × 13.50 × 14 = **$18,900**.
- SaaS 14-month TCV: **$153,370**.
- DataEngine maintenance 2027: 40,000 × 1.05 = **$42,000**.
- Reporting Server at 2027 list: **$30,000** (+$2,000; the prior discount is dropped by List mode, so this needs a visible flag).
- Legacy Connector: −$3,000 ARR, classified as churn (non-renewal).

**Audit trail.** One change record spanning two agreements. Per-line resolved policy with source level. Segment schedule for AI Assist. Price-book version and as-of date per line. An ARR bridge:
- uplift effect +$6,720 (Analytics) + $840 (AI Assist annualised, after the lock);
- cross-sell +$16,200 ARR;
- maintenance uplift +$2,000;
- list repricing +$2,000;
- non-renewal −$3,000.

**Where it breaks.**
- The structure holds; the gaps are again in policy.
- Time-bounded locks and segments (G2).
- Maintenance % with its own uplift (G5).
- Per-line price-as-of date (G1).
- One `businessCategory` per line cannot express "renewal + uplift + downsell" on one line, and non-renewal needs its own disposition for churn reporting (G10). RCA keeps rollups such as `TotalRenewalsAmount` on AssetAction [S15].
- Zuora's single order combining Renew with RemoveProduct [S35] confirms the one-change approach.
- **[INFERENCE]** If an unapplied amendment (e.g. AI Assist +10) is still open when this renewal is priced, the renewal must project from the stacked open order, as Oracle's Projected Asset Cache does [S40] (https://help.bigmachines.com/cpq/Content/ABO/Subscription_Ordering.htm). RA-17/RA-18 touch versioning, but projection is worth checking in the amendment-document spike.

## 4. Cross-cutting observations
- **Action date vs effective date** is necessary but not sufficient. Scenarios 2–4 need **per-line** start, end, service-period and entitlement-end dates (RA-19 should extend to ChangeLine; noted, not a new item).
- **Quantity semantics:** true-up is natural as Delta (+16) and conversion as Absolute (160 CU). The model's per-line `quantitySemantics` handles both; keep it per line, not per change.
- **Pricing location [INFERENCE]:** all three policies need the price book, the rules and the state-period history. That argues for pricing-service owning policy evaluation, with quote-service passing a resolved-policy snapshot and pricing-service returning a trace (ties to DRG-311 calculation trace and DRG-310 decision recording).
- **Cross-check with the amendment-document design spike:** the spike already plans ONE_TIME lines routed to order/billing, an ENTITLEMENT apply unit for capacity raises, a `delta_qty = max(0, peak − entitled)` true-up line (which implicitly picks Peak, one of G3's choices), a per-line PolicyDecision with "uplift source (caller, account, contract, line)", and pinned metric-conversion maps [S62]. That last decision trail is RA-30 / DRG-564, so G9 below is **not** a new item. The spike's restructure row reuses the existing `migration-credit.drl` / DRG-341 credit, which this check finds insufficient for a big-bang conversion (G7).
- **Drools seeds:** `renewal-uplift.drl` (8%, 3% if churn > 70) becomes the *lowest-precedence default* in G1. `amendment-proration.drl` is superseded by TrueUpPolicy/AmendmentPolicy proration (RA-08).

## 5. Prioritised required changes

**Meta model**
1. **Split PricingPolicy into typed, versioned policies** (RenewalPolicy, AmendmentPolicy, TrueUpPolicy, MaintenancePolicy, ConversionPolicy), each resolved account > agreement > line with contract-clause precedence, and **snapshot the resolved policy on each ChangeLine** (G1, G9). *High.*
2. **ChangeLine additions:**
   - `chargeType {Recurring | OneTime | Usage}`, `servicePeriodStart/End`, per-line `effectiveDate` and `entitlementEndDate`, `priceAsOf`, `convertedFromLineIds[]`;
   - `renewalDisposition {Renew | NonRenew | Replace}`;
   - ARR bridge components `{base, priceEffect, qtyEffect, crossSell, conversion, churn}`.
   (G6, G8, G10, G1)
3. **Action code `Surrender` and status `Surrendered`** for perpetual lines. MeterReading gains `source`, `attestation`, `status {Provisional | Attested | Disputed | Corrected}` and `snapshotId` (G8, G4).
4. **Segment schedule** on a renewal ChangeLine: an ordered list of `{from, to, mode, value}` (G2).

**Policies**

5. **P1:** add `upliftBasis`, `cap`/`floor`, `priceAsOf`, rounding, `upliftAmount`, multi-year escalator and time-bounded locks (G1, G2). *High.*
6. **P2:** define TrueUpPolicy: aggregation, window, tolerance, pack size, true-down, overage rate, backdating, proration; price only from a frozen attested snapshot (G3, G4). *High.*
7. **Maintenance:** `PercentOfRelatedLine` pricing with recomputation on licence change, its own uplift, and lapse reinstatement / back-maintenance (G5). *High.*
8. **P3:** define ConversionPolicy: versioned quantity map, parity or target-list basis, parity ramp, credit stack with caps and application target, cross-agreement credit transfer. Reconcile with the DRG-341 migration credit (G7). *High.*

## 6. Backlog items
New items were created in Linear (team DRG, project OPEN-CRM-CPQ, label renewals-amendments) and in Notion ("Open-CRM-CPQ Backlog"), cross-linked. See the table appended below. Existing items that already cover the remaining findings, and were therefore not duplicated:
- RA-02 multi-line amendments
- RA-05 price book not in the continuation flow
- RA-07 Drools uplift never executes
- RA-08 proration formula conflict
- RA-15 renewal mode hierarchy
- RA-16 amendment price, credit and fee
- RA-17 dry run
- RA-19 action vs effective date
- RA-20 co-term anchor / merge
- RA-23 meter model and TrueUp intent
- RA-24 fulfilment
- RA-25 restructure intent
- DRG-341 hybrid migration credit and dual-entitlement window
- DRG-534 ramp amendments
- DRG-532 / DRG-533 escalator semantics and generation (RA-34 is scoped to renewal escalators and price locks only)
- RA-30 / DRG-564 resolved PolicyDecision per line (this check's G9 snapshot gap; skipped as a duplicate)

### Created items

| Gap | ID | Title | Priority | Linear | Notion |
|---|---|---|---|---|---|
| G1 | RA-33 / DRG-567 | Renewal uplift basis, caps, price-as-of date and policy precedence | High | https://linear.app/drgamtd-workspace/issue/DRG-567 | https://app.notion.com/p/3fbf7cb4e4df81dbafe9c3cc82c56acd |
| G2 | RA-34 / DRG-568 | Multi-year renewal escalators and time-bounded price locks in renewal policy | Medium | https://linear.app/drgamtd-workspace/issue/DRG-568 | https://app.notion.com/p/3fbf7cb4e4df81a7b50de01a26861164 |
| G3 | RA-35 / DRG-569 | Capacity true-up policy: measurement, tolerance, pack rounding, overage rate and backdating | High | https://linear.app/drgamtd-workspace/issue/DRG-569 | https://app.notion.com/p/3fbf7cb4e4df8145b792c117361bb794 |
| G4 | RA-36 / DRG-570 | Meter reading provenance, dispute and frozen true-up snapshot | Medium | https://linear.app/drgamtd-workspace/issue/DRG-570 | https://app.notion.com/p/3fbf7cb4e4df817b97bce854c3c873ad |
| G5 | RA-37 / DRG-571 | Maintenance pricing policy: percent of licence net, recalculation and reinstatement | High | https://linear.app/drgamtd-workspace/issue/DRG-571 | https://app.notion.com/p/3fbf7cb4e4df81a49d66e37d731304bd |
| G6 | RA-38 / DRG-572 | One-time and retroactive charge lines with their own service period | Medium | https://linear.app/drgamtd-workspace/issue/DRG-572 | https://app.notion.com/p/3fbf7cb4e4df816d8118e5268c89d3cd |
| G7 | RA-39 / DRG-573 | Conversion pricing policy for on-prem to SaaS (quantity mapping, parity price, credit stack) | High | https://linear.app/drgamtd-workspace/issue/DRG-573 | https://app.notion.com/p/3fbf7cb4e4df81b4866ee57ac7b2ba67 |
| G8 | RA-40 / DRG-574 | Perpetual licence surrender and entitlement revocation in conversions | Medium | https://linear.app/drgamtd-workspace/issue/DRG-574 | https://app.notion.com/p/3fbf7cb4e4df81308597ea9bf1615ad8 |
| G10 | RA-41 / DRG-575 | ARR bridge per change line: price, quantity, cross-sell, conversion and non-renewal effects | Low | https://linear.app/drgamtd-workspace/issue/DRG-575 | https://app.notion.com/p/3fbf7cb4e4df813eb74aeb6d28978920 |
