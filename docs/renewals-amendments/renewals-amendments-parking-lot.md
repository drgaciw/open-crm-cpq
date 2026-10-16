# Renewals and amendments: parking lot (not active, future scope)

Status: **PARKED. None of the items below is an active development story.** They are not in the first slice (S1 to S8), have no estimate, and must not be started without a new decision. Recorded 2026-10-16 from a product discussion. Tracked as RA-53 / DRG-587 (on-prem usage, PL-1 to PL-5) and RA-54 / DRG-588 (forecasting review, PL-6), each a "[PARKED]" item.

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

## Entry criteria to unpark
First slice shipped; P2 policy defined; product confirms attester roles; Legal confirms the evidence retention rule (relates to OQ8).

PL-6 has its own unpark condition: the user brings it up and confirms the review scope.
