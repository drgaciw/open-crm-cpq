# Renewals and amendments: parking lot (not active, future scope)

Status: **PARKED. None of the items below is an active development story.** They are not in the first slice (S1 to S8), have no estimate, and must not be started without a new decision. Recorded 2026-10-16 from a product discussion. Tracked as RA-53 / DRG-587 (on-prem usage, PL-1 to PL-5) and RA-54 / DRG-588 (forecasting review, PL-6) and RA-55 / DRG-589 (mid-term cancellation, PL-7) and RA-56 / DRG-590 (rules requirements review, PL-8) and RA-57 / DRG-591 (approvals category, PL-9; skeleton RA-67 / DRG-601), each a "[PARKED]" item.

## Context
ACI's on-premise customers are largely financial institutions that will not allow usage-monitoring connectivity, so they self-report usage. Today there is no systematic way to see on-premise usage in the product. The self-report workflow idea is a manual usage-number entry plus an attestation step by a rep or customer-ops.

Relevant existing content: the P2 capacity-meter policy and the Scenario 2 capacity true-up in `cpq-pricing-policy-sanity-check.md` (gaps G3 to G6: measurement method, provenance, frozen snapshot, backdating), MeterReading and TrueUp intent (RA-23), and the hash-chained audit log from S2 (`amendment-technical-approach-and-dependencies.md`).

## Parked items

### PL-1 Usage self-report entry record
- **Description:** a record per customer, contract line and period holding the metric (for example cores, users, transactions), the reported number, unit, period start and end, and who entered it.
- **Dependencies:** MeterReading model (RA-23); P2 measurement-method decision (sanity check G3); contract and line identifiers from the contract header (S7).
- **Not an active dev story.**

### PL-2 Rep or customer-ops attestation step
- **Description:** a reported number is not usable until an attester confirms it. Captures attester, date, and an evidence note (for example a customer email or signed certificate reference). Open choice: attester role and whether a customer-side sign-off is also needed.
- **Dependencies:** PL-1; role model; provenance field on meter readings (sanity check G6).
- **Not an active dev story.**

### PL-3 Usage to true-up and capacity-meter link
- **Description:** an attested report becomes a frozen MeterReading snapshot that feeds the P2 capacity true-up (Scenario 2) and renewal sizing.
- **Dependencies:** PL-1, PL-2; P2 policy definition (measurement method, tolerance, rounding, backdating); TrueUp intent (RA-23); the typed-policy split proposed in the sanity check.
- **Not an active dev story.**

### PL-4 Audit trail for usage reports
- **Description:** every create, edit and attestation of a usage report is appended to the existing hash-chained audit log, so a self-reported number can be shown unchanged later.
- **Dependencies:** S2 audit log; OQ14 chain scope; PL-1, PL-2.
- **Not an active dev story.**

### PL-5 Usage-based renewal uplift justification
- **Description:** show attested self-reported usage next to a proposed renewal uplift so a rep can justify it to the customer. Where no attested report exists, the uplift cannot cite usage.
- **Dependencies:** PL-1 to PL-3; renewal policy (RA-34 area); see OQ21.
- **Not an active dev story.**

## PL-6 CRM forecasting requirements maturity review (PARKED, not an active dev story)

Status: **PARKED. Not an active development story.** Recorded 2026-10-16. Tracked as RA-54 / DRG-588 ("[PARKED]"). Sits next to the on-prem self-reported usage items (PL-1 to PL-5, RA-53 / DRG-587).

**Distinction (explicit):** this is a review of *overall CRM forecasting requirements* and how mature they are. It is SEPARATE from the net-new automatic re-forecast-on-amendment-save functionality already discussed for the renewals and amendments work. That re-forecast behavior is its own feature; this item does not design, replace, or block it.

- **Description:** assess the overall forecasting requirements for the CRM (pipeline, weighted forecast, renewal forecast, and related roll-ups) and judge how complete, consistent and testable they are today.
- **Why parked:** the first slice (S1 to S8) does not depend on it, and the user wants the review handled as a deliberate, separate conversation instead of being folded into the amendment work.
- **Suggested review scope:**
  1. What forecasting exists today in the repo and docs. Known starting points (from a code search of drgaciw/open-crm-cpq, to be verified in the review): Deal Service "Opportunities, pipeline stages, forecasting" in `MODERNIZATION_PLAN.md`; AI forecast generation in the same plan; `agents/win-probability-analysis.md` (probability-weighted pipeline forecast rollup by segment, region, deal type, close quarter, with confidence intervals); `agents/renewal-opportunity-detection.md` (renewal forecast by month, segment, risk tier, expected ARR; reconciled with `GET /api/v1/deals/pipeline`); `agents/usage-based-pricing.md` (spend projection forecast); ADR-032 multi-currency pipeline reporting (forecast queries sum `base_amount`); the Salesforce-style Renewal Forecast contract trigger in `cpq-renewals-amendments-research.md`.
  2. Requirements maturity: for each forecast type, is there a stated owner, definition, inputs, calculation rule, cadence, acceptance criteria and test data, or only agent prose.
  3. Gaps: missing definitions (forecast categories, commit versus best case), no single source of truth, who consumes the forecast (finance, sales leadership), accuracy measurement, currency handling beyond ADR-032, on-prem usage input (see PL-1 to PL-5 and OQ21).
  4. Relationship to amendment re-forecast: list which forecast inputs an amendment changes (ARR, term, renewal date, lines) so the later re-forecast-on-amendment-save feature has a stable forecast model to write to. The review informs, but does not gate, that feature.
- **Dependencies:** none for the review itself. Inputs: existing docs and agents above; ADR-032; renewal policy (RA-34 area); PL-1 to PL-5 for on-prem usage inputs. Output feeds the amendment re-forecast design.
- **Revisit:** the user wants this brought up in a future conversation. Prompt the user to revisit "CRM forecasting requirements maturity review" at the next planning or renewals and amendments conversation, and when the first slice ships, whichever comes first. There is no automated reminder; this note and RA-54 / DRG-588 are the prompt.
- **Not an active dev story.** No estimate, no code.

## PL-7 Mid-term cancellation: proration and refunds (PARKED, not an active dev story)

Status: **PARKED. Not an active development story.** Recorded 2026-10-16. Tracked as RA-55 / DRG-589 ("[PARKED]"). Out of scope for the renewals and amendments work.

- **Description:** the system may need to handle proration and refunds when a customer cancels mid-term. Today the amendment spine can record the intent but nothing computes or issues the financial consequence.
- **Why parked / out of scope:** the first slice (S1 to S8) covers add, update and remove lines on SaaS contracts and does not compute credits or refunds. Refund and proration rules depend on billing and finance decisions that are not made, and on contract terms that vary by customer. Folding them in would grow the slice without a settled policy.
- **Suggested review scope:**
  1. Cancel intent: it already exists in the amendment spine as intent type Cancel. Decide what the slice does with it (record only) and what a full cancellation flow adds.
  2. Proration of fees: method (daily, monthly, none), rounding, handling of one-time versus recurring fees, discounts and price locks.
  3. Credit versus refund handling: when a credit memo applies versus cash refund, who approves, and currency handling (see ADR-032).
  4. Effective date versus action date: cancellation effective immediately, at period end, or backdated; how that sets the proration window.
  5. Billing and finance dependency: invoicing, credit memos, revenue recognition, and the system of record for refunds.
  6. Contract terms: termination for convenience, notice periods, minimum commitments, early termination fees, non-refundable prepaid amounts.
  7. Relation to the mid-term amendment Remove/Terminate action code: define how a line level Remove or Terminate (see `mid-term-amendment-policy-spec.md`) differs from a whole contract cancellation, and whether they share proration logic.
- **Dependencies:** first slice shipped (S1 to S8); billing and finance decisions; contract terms model (S7 header); mid-term amendment policy spec; audit log (S2); ADR-032 for currency.
- **Unpark condition:** the first slice has shipped and product confirms, with Finance and Legal input, that cancellation proration and refunds are in scope, and the review scope above is agreed.
- **Not an active dev story.** No estimate, no code.

## PL-8 Rules requirements review: products and pricing (Drools) (PARKED, not an active dev story)

Status: **PARKED. Not an active development story.** Recorded 2026-10-16. Tracked as RA-56 / DRG-590 ("[PARKED]"). Sits with PL-6 and PL-7.

- **Description:** review the overall rules-related requirements across the product side and the pricing side, and judge how complete they are before any rules work is built. Drools is already in the ACI stack.
- **Principle (from the discussion):** structural product mapping (sales SKU to delivery BOM composition) stays as dumb composition data. Conditional logic, meaning which delivery artifacts apply based on region, license type or customer tier, belongs in Drools and is evaluated at quote or order time. Composition data says what a SKU is made of. Rules say which parts apply in a given context.
- **Why parked:** the first slice (S1 to S8) needs neither. Rules scope spans products and pricing and needs a deliberate review first.
- **Suggested review scope, product side:**
  1. SKU to delivery BOM composition model (data only, no conditions).
  2. Delivery artifact selection rules by region, license type, customer tier; inputs, outputs, owner.
  3. Where and when rules are evaluated (quote versus order time), and how the result is stored so it can be reproduced later.
  4. Rule authoring, versioning, testing and ownership (product versus engineering).
- **Suggested review scope, pricing side:**
  1. Proration rules (see PL-7 and RA-08).
  2. Amendment pricing modes (AmendmentPolicy, e.g. RetainContracted); see OQ9.
  3. Renewal uplift calculations (uplift basis, cap or floor, rounding, precedence; sanity check G1, G2, G9).
  4. Precedence between contract price protection, line override and Drools defaults.
- **Relation to existing repo facts:**
  - The pricing sanity check notes that the seeded Drools rules (`renewal-uplift.drl`, `amendment-proration.drl`) never execute today (RA-07), and proposes the Drools uplift as the lowest-precedence default.
  - `QuoteContinuationPolicy` in quote-service is a typed Java policy, not a rule; it reads rates only from the accepted subscription schedule (see `amendment-document-design-spike.md`). The review should decide what stays typed code and what moves to rules.
  - Pricing-service support for amendment modes is unverified (OQ9, blocks S5 only).
  - Links: PL-3 (P2 true-up policy), PL-5 (usage based uplift justification), PL-7 (proration and refunds).
- **Dependencies:** none for the review itself. Inputs: sanity check, OQ9 spike result, PL-3, PL-5, PL-7, current Drools setup in pricing-service.
- **Unpark condition:** product confirms the review scope, OQ9 is answered, and a decision is made on typed policy versus Drools for the policies above.
- **Not an active dev story.** No estimate, no code.

## PL-9 Approvals for renewals and amendments (general category) (PARKED, not an active dev story)

Status: **PARKED. Not an active development story.** Recorded 2026-10-16. Tracked as RA-57 / DRG-591 ("[PARKED]"). Sits with PL-6 to PL-8. To be discussed in a separate future conversation.

- **Description:** a general category for all approvals around renewals and amendments. Coterming events require approval, but who approves (sales ops, the contract owner, or someone higher) is deferred. This item captures that approvals for renewals and amendments, including who signs off on coterm events, amendment processing, and any other approval gate, will be reviewed as their own topic later.
- **Known gates so far:**
  1. Coterm events (aligning contracts to a shared end date): approval required, approver undecided.
  2. Amendment execution and signature policy per intent: slice default is NOT_REQUIRED, NOTICE_ONLY and CLICK_THROUGH only (OQ10); other policies are not executable in the slice. Approval thresholds that trigger `CONTRACT_MODIFICATION` for non-override amendments have a proposed default (OQ17).
  3. Discount or uplift overrides: manual price overrides route to approval (V14, FR-18, S5).
  4. Price-lock overrides (price-lock policy stub, OQ15).
  5. Cancellations (PL-7): who approves cancellation, credits and refunds.
  6. Partial application (OQ5): who approves the remedy or correcting amendment after PARTIALLY_APPLIED or APPLY_BLOCKED.
  7. Backdating (V6, FR-17) and VOID of an executed document (OQ13): already noted elsewhere, to be folded into the review.
- **Deferred question:** who approves each gate: sales ops, contract owner, or higher (for example finance or legal by threshold). Not decided here.
- **Relation to existing repo capability:** approval-routing already exists and `ApprovalType` includes `CONTRACT_MODIFICATION` (see `amendment-document-design-spike.md`; ADR-035 keeps approvals in approval-routing). The slice reuses it with default thresholds. Not verified in the repo for this note: whether approval-routing supports per-gate approver roles, delegation, or a coterm-specific type. The review should check that.
- **Dependencies:** none for the review itself. Inputs: spike approval flow, OQ5, OQ10, OQ15, OQ17, PL-7, coterm research (`coterm-renewal-quote-research.md`), approval-routing capability check.
- **Unpark condition:** a separate conversation is scheduled for this topic and product confirms the scope. Until then the slice defaults stand.
- **Starting point (skeleton, not decided):** `approval-requirements-skeleton.md` is the generic, configuration-driven skeleton to open the separate approvals conversation with. It covers the approver model, triggers, chains, thresholds, escalation, lifecycle interaction and a rule-set data model, and leaves every approver and threshold undecided. Tracked as RA-67 / DRG-601 ("[PARKED]"), alongside RA-57 / DRG-591.
- **Not an active dev story.** No estimate, no code.

## Entry criteria to unpark
First slice shipped; P2 policy defined; product confirms attester roles; Legal confirms the evidence retention rule (relates to OQ8).

PL-6 has its own unpark condition: the user brings it up and confirms the review scope.

PL-7 has its own unpark condition: see the PL-7 section above.

PL-8 has its own unpark condition: see the PL-8 section above.

PL-9 has its own unpark condition: see the PL-9 section above.
