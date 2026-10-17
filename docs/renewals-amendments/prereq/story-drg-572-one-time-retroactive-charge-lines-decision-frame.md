# DRG-572 — One-time and retroactive charge lines: decision frame

**Status:** Proposed, unaccepted, owner decision required. This file is not an ADR, takes no number, and is not copied into `docs/adr/`. It does not accept `specs/lifecycle-intents.md`, `specs/partial-apply.md`, `specs/maintenance.md`, `specs/conversions.md`, `specs/capacity-meters.md`, the OpenAPI draft, or the DRG-609 pricing-service design. Accepting this note authorizes no code, no migration, no flag, no topic, no gateway route, and no deploy.

**Lifecycle-intents addendum (section 10):** proposal-marked and NOT applied. `specs/lifecycle-intents.md` was not edited.

**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Story:** [DRG-572](https://linear.app/drgamtd-workspace/issue/DRG-572/one-time-and-retroactive-charge-lines-with-their-own-service-period) (RA-38). Linear status set this pass: In Review. Draft pull request: [#654](https://github.com/drgaciw/open-crm-cpq/pull/654).

**Related issues read this pass.** [DRG-553](https://linear.app/drgamtd-workspace/issue/DRG-553) (action date versus trigger dates), [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) (partial application), [DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536) (no multi-line amendment), [DRG-550](https://linear.app/drgamtd-workspace/issue/DRG-550) (amendment price, proration, credit and termination-fee policies). [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) (S3) was read the same pass: Todo, unstarted.

**Sources.** Pricing policy sanity check, Scenarios 2 and 3 (`../cpq-pricing-policy-sanity-check.md`). `specs/lifecycle-intents.md`. Landed continuation request and CAM `V1_7` line columns, read in this tree. The Accepted proration record is cited so this frame does not reopen it. The reconcile record for ADR-024 through ADR-027 is Accepted; ADR-017 and ADR-024 through ADR-027 themselves stay Proposed, and this frame does not treat them as accepted.

**Conventions.** **[S]** is an issue, a file, or a column read this pass. **[I]** is a design inference in this frame. Every recommendation and every default is **Proposed, unaccepted, owner decision required.** A default is what holds until the owner picks. It is not an acceptance. Each default is written with its alternatives beside it.

Numbers below are the sanity check's illustrative figures, not customer data, and not tests.

## 1. Question

How does a lifecycle change carry a non-recurring line, and what, if anything, is handed to billing? One business-policy question sits inside that: may a `ONE_TIME` service window start before the line's effective date?

The lines DRG-572 names:

- Back-maintenance for a true-up. Scenario 2: 58 excess core-months, $2,416.67, service window 2026-04-01 through 2026-09-30, before contract effective 2026-10-01. **[S sanity Scenario 2]** Whether that window is allowed is Fork 3.
- A reinstatement fee. The amount and the lapse rule are `specs/maintenance.md`. The charge line is this story. **[S DRG-572, maintenance.md]**
- True-up arrears that do not themselves change the committed quantity. The +16 core update is a different line. **[S sanity Scenario 2]**
- A conversion credit consumed on another agreement. The credit stack is `specs/conversions.md`. Whether billing sees a line is Fork 6 and Fork 7. **[S DRG-572, sanity Scenario 3]**

## 2. What `specs/lifecycle-intents.md` already defines

That file is a proposal. This frame does not accept it. Inside that proposal, the line contract is:

- `actionCode`: Add, Update, Delete, NoChange, Renew, Suspend, Resume, Terminate, Cancel, Surrender, Convert.
- `quantitySemantics`: Delta, Absolute, or Replace.
- `startQty`, `deltaQty`, `endQty`, with Delta identity `startQty + deltaQty = endQty` and `endQty >= 0`.
- One `businessCategory` per line.
- Four dates on the **change**, not on the line: `actionDate`, `contractEffective`, `serviceActivation`, `customerAcceptance`.
- A trigger date before `actionDate` is backdating. Customer visibility stays blocked until the owner sets the backdating rule (open question 7 in that file).

Open question 6 in that file keeps those four dates on the change and leaves `entitlementEndDate` to `specs/conversions.md`.

## 3. What `specs/lifecycle-intents.md` does not define

- `chargeType`.
- `servicePeriodStart` and `servicePeriodEnd`.
- A per-line `effectiveDate`.
- Whether a `ONE_TIME` service window may start before the line effective date. That allowance is Fork 3, an owner decision. The file does not grant it.
- An action whose only effect is a charge or a credit, with no commitment change.
- A billing handoff. Billing is external. The file names no payload and no receiver.
- The money. True-up measurement, maintenance percent, reinstatement knobs, conversion credit math, and the day-stub of a recurring amendment are other files.

Section 10 is an addendum aimed at that file. The addendum is proposal-marked and NOT applied. The file on `main` is unchanged.

## 4. What is already stored, and what is not

Two layers exist. They are not the same contract.

**Continuation request, which is what pricing runs today [S].** `QuoteContinuationRequest` has one operation, one `sourceSubscriptionLineUuid`, one `quantity`, and one `effectiveDate`. It has no `chargeType`, no service period, and no second line. DRG-536 is this gap. A back-maintenance line cannot be planned on this request.

**Amendment document line, CAM `V1_7` [S].** `amendment_document_lines` already has:

- `charge_type` in `RECURRING`, `ONE_TIME`, `USAGE` (`ChargeType`). The column is not null. The SQL has no default. The field schema and the OpenAPI draft, both still proposals, say the omitted value is Recurring.
- `effective_from` (not null) and `effective_to` (null only when open), with `effective_to` absent or on or after `effective_from`.
- `apply_target_type` including `ONE_TIME`.
- `net_amount`, with no sign check in that migration.

That table has no `service_period_start` and no `service_period_end`. `effective_from` is one date. It cannot, by itself, record both "charge becomes effective 2026-10-01" and "service window 2026-04-01 through 2026-09-30". Nothing in the check constraints says a `ONE_TIME` window may precede the line effective date. No billing service exists under `backend/services/`. Billing is external. The column is not a billing API and not a receiver.

S1 stored the charge-type column. It did not decide retroactive service periods. This frame does not alter `V1_7`.

## 5. Illustrative lines

These rows illustrate Fork 3 option A only. That option is Proposed, unaccepted, owner decision required. Fork 3 option B, the alternative, refuses the row. The rows are not fixtures to implement, and they are not an acceptance of the sanity check's measurement choices.

**Scenario 2, back-maintenance, separate from the capacity update.** The update line remains Update, Delta +16, on the perpetual commitment. The forward maintenance stub of $2,016.44 stays with the Accepted proration record and with `specs/maintenance.md`. This frame does not recompute it.

| Field | Back-maintenance line, only if Fork 3 option A is later chosen |
|---|---|
| `actionCode` | Add |
| `chargeType` | ONE_TIME |
| `quantitySemantics` | Absolute |
| `startQty`, `deltaQty`, `endQty` | 0, +1, 1 (one charge, not +16 cores) |
| `servicePeriodStart` | 2026-04-01 |
| `servicePeriodEnd` | 2026-09-30 |
| `effectiveDate` | 2026-10-01 |
| Amount | $2,416.67 as stated in the sanity check |

The service window ends the day before the line effective date. That pairing is the business-policy question in Fork 3. The sanity check does not adopt a rounding rule for the $41.67 core-month rate. This frame does not add one.

**Scenario 3, credits.** Unused prepaid maintenance $20,164.38 and trade-in $19,998.72 are `specs/conversions.md` figures. Mapping either figure onto a line is Fork 7. Handing it to billing is Fork 6. Which invoice absorbs the credit stays in the conversion proposal.

## 6. Forks

Each fork has a recommendation, a default, and the alternatives beside that default. The recommendation is the pick this note asks for. It is Proposed, unaccepted, owner decision required. The default is what remains true until the owner makes that pick. The default is Proposed, unaccepted, owner decision required.

### Fork 1 — `chargeType` closed set

| Option | What the owner would be choosing |
|---|---|
| **A. Keep `RECURRING`, `ONE_TIME`, `USAGE`** | The three values already in `ChargeType` and in `chk_amendment_line_charge`. Usage stays a value. Usage arithmetic stays out of this story. |
| **B. `RECURRING` and `ONE_TIME` only** | Drop Usage from the line contract. |
| **C. Add `CREDIT`** | A fourth value for Scenario 3 credits. |

**Recommendation:** A. The same SKU can be recurring maintenance and a one-time back-maintenance charge, so the type is stored on the line. It is not inferred from the catalogue alone. Proposed, unaccepted, owner decision required.

**Default:** Writers that store a document line supply `RECURRING` unless they have a reason to do otherwise. No new value is added. No column is dropped. The continuation request still has no charge type. Proposed, unaccepted, owner decision required.

**Alternatives beside the default:** B (drop Usage) and C (add `CREDIT`).

### Fork 2 — Where the service window is stored

This fork is the shape of the fields. Whether a `ONE_TIME` window may start before the line effective date is Fork 3, not this fork.

| Option | What the owner would be choosing |
|---|---|
| **A. Distinct `servicePeriodStart` / `servicePeriodEnd`** | Both absent, or both set with end on or after start. The pair is not `effective_from` / `effective_to`. |
| **B. Reuse `effective_from` / `effective_to`** | The service window is the line's effective range. There is no second pair. |
| **C. No service window on the line** | Retroactive money is not a ChangeLine field. An instrument outside the line would carry it. This note does not define that instrument. |

**Recommendation:** A. Scenario 2, if Fork 3 option A is chosen, needs both the window being charged and the day the charge sits on the change. Proposed, unaccepted, owner decision required.

**Default:** No service-period columns. `V1_7` is unchanged. Proposed, unaccepted, owner decision required.

**Alternatives beside the default:** A (a distinct pair, still subject to Fork 3) and B (reuse the effective range). C leaves the window off the line.

### Fork 3 — `ONE_TIME` service window before the line effective date

This is a business-policy fork. It is an owner decision. It is not the backdating rule in `specs/lifecycle-intents.md` (a trigger date before `actionDate`). It is not the Accepted proration formula. A window that starts before the line effective date is allowed only if the owner picks an allow option below. Until then it is not allowed.

| Option | What the owner would be choosing |
|---|---|
| **A. Allow for `ONE_TIME` only** | `servicePeriodStart` may fall before the line `effectiveDate`, including a window that ends before that date. Scenario 2 back-maintenance, 2026-04-01 through 2026-09-30 with effective date 2026-10-01, can be stored. `RECURRING` and `USAGE` still may not start before the line effective date. |
| **B. Refuse for every charge type (alternative)** | `ONE_TIME`, `RECURRING`, and `USAGE` all require the service window to start on or after the line effective date. Months already elapsed are not a ChangeLine service period. Back-maintenance, a reinstatement gap, and true-up arrears for a past window would need a commercial instrument outside this line. This note does not define that instrument, does not create a credit memo, and does not send anything to billing. |
| **C. Allow for every charge type** | The early window is not limited to `ONE_TIME`. A recurring line could start its service window before its effective date. |

**Recommendation:** A. Proposed, unaccepted, owner decision required. The recommendation is not the owner's choice.

**Default:** Refuse, which is option B. A service window that starts before the line effective date is not allowed and is not priced, for `ONE_TIME` as well as for every other charge type. Proposed, unaccepted, owner decision required.

**Alternatives beside the default:** A (allow for `ONE_TIME` only) and C (allow for every charge type).

### Fork 4 — Per-line `effectiveDate`

| Option | What the owner would be choosing |
|---|---|
| **A. Optional line `effectiveDate`, falling back to the change `contractEffective`** | The other three dates stay on the change. On a document line, the existing `effective_from` is this date. It is not the service period. |
| **B. Copy all four dates onto every line** | `actionDate`, `contractEffective`, `serviceActivation`, and `customerAcceptance` become per-line. |
| **C. No per-line effective date** | Only the service period, if Fork 2 adds one, is per-line. |

**Recommendation:** A. DRG-553's four dates stay on the change, which is what `specs/lifecycle-intents.md` open question 6 already proposes. `entitlementEndDate` stays in `specs/conversions.md`. Proposed, unaccepted, owner decision required.

**Default:** The four dates remain change-level in the lifecycle proposal. A document line still requires `effective_from` because `V1_7` requires it. This note adds no lifecycle field and no migration. Proposed, unaccepted, owner decision required.

**Alternatives beside the default:** A (optional line effective date) and B (all four dates on the line).

### Fork 5 — Action for a charge that does not change a commitment

The sanity check says there is no action code for a one-time retroactive charge that leaves the commitment alone. `V1_7` allows Add, Update, Remove, NoChange, Renew, Upgrade, Downgrade, Swap, ConvertMetric, Suspend, Resume, Terminate. It does not allow a Fee or Charge code. `specs/lifecycle-intents.md` open question 2 still leaves the action-code set to the owner.

| Option | What the owner would be choosing |
|---|---|
| **A. `Add` plus `ONE_TIME`** | Quantity is the charge (Absolute 1), not the core delta. The commitment update is a sibling line. No new action code. |
| **B. New action code `Charge`** | A code that must be `ONE_TIME` and must not change a commitment. That is a check-constraint change. |
| **C. `NoChange` on the source line, fee outside the document** | The charge is not a ChangeLine. |

**Recommendation:** A. A new code belongs in the lifecycle action-code decision, not in a silent extension of `chk_amendment_line_action`. Proposed, unaccepted, owner decision required.

**Default:** No new action code. This note does not allow a one-time line yet. Proposed, unaccepted, owner decision required.

**Alternatives beside the default:** A (`Add` plus `ONE_TIME`, once the owner allows the line) and B (a new `Charge` code).

### Fork 6 — How billing receives the line

Billing is external. This repository has no billing service. This frame does not choose a receiver. Any receiver — an order line, an event, a command, or an HTTP call — needs its own decision. That decision is not this note. ADR-017 and ADR-024 through ADR-027 are **Proposed**. They are not accepted. This frame does not assume an order service, a saga, a command, or a synchronous billing call.

The Accepted reconcile record (`prereq/adr-reconcile-adr-024-027.md`) already states a v1 destination class, and its own header says that acceptance does not accept ADR-024 through ADR-027:

- `ONE_TIME` on a document that also has an on-prem unit is an order line on that on-prem order, once an order exists. The charge is not a ledger rate period.
- `ONE_TIME` on a SaaS-only document has no order in v1. Billing stays with the still-Proposed Quote-to-Revenue saga, which that record leaves outside its scope.

This frame does not reopen that destination class, and it does not turn the class into a receiver.

**"Billing gets fields only, no receiver" is option A.** It is one option. It is not a decision this note has made.

| Option | What the owner would be choosing |
|---|---|
| **A. Billing gets fields only, no receiver** | The line may carry the commercial fields a later reader would need: line id, `chargeType`, service period, line effective date, net amount, currency, source agreement, and target agreement when a credit is consumed elsewhere. Nothing is sent. No order line, event, command, or HTTP call is built. A receiver, if wanted later, is a separate owner decision. |
| **B. Name a receiver in a later decision** | An on-prem order line, a billing event, a command, or a synchronous call. Billing is external, so this note cannot choose the receiver. Choosing B opens that later decision and does not authorize the receiver. ADR-017 and ADR-024 through ADR-027 would be in scope there. They stay Proposed here. |
| **C. No billing-facing field list in this story** | `charge_type` and amounts stay a commercial record on the document line. This story does not describe fields for billing to read. |

**Recommendation:** A, held as an option. Proposed, unaccepted, owner decision required.

**Default:** Billing receives nothing. No outbox row, no command, no flag, no deploy. Proposed, unaccepted, owner decision required.

**Alternatives beside the default:** A (fields only, no receiver) and B (a named receiver under its own later decision). C leaves the field list out of this story as well.

### Fork 7 — Charge versus credit

| Option | What the owner would be choosing |
|---|---|
| **A. Credits stay in `ConversionPolicy` until a line is required** | If a later decision says billing must show the credit, the line is `ONE_TIME` with a negative net amount, the service window the policy names, and the target agreement. No `CREDIT` value. That later showing is a receiver decision under Fork 6, not this fork. |
| **B. `chargeType = CREDIT`** | Fork 1 option C. |
| **C. Negative amounts forbidden** | A credit cannot be a document line. It lives only inside the conversion policy. |

**Recommendation:** A. `V1_7` already allows a negative `net_amount` because it has no sign check. Conversion formulas, caps, and `FirstInvoice` versus `Spread` versus `DecliningLine` stay in `specs/conversions.md`. Reinstatement `feePct` stays in `specs/maintenance.md`, with no default. Termination-fee policy stays on DRG-550. Proposed, unaccepted, owner decision required.

**Default:** This frame requires no negative line. Credits are not mapped onto ChangeLines. Proposed, unaccepted, owner decision required.

**Alternatives beside the default:** A (a negative `ONE_TIME` line only when a later decision requires one) and B (a `CREDIT` charge type).

## 7. Owner decision table

Every row is Proposed, unaccepted, owner decision required. Picking a row, or picking every row, authorizes no code, no migration, no flag, and no deploy.

| Fork | Decision the owner must make | Recommendation | Default | Alternatives beside the default |
|---|---|---|---|---|
| 1. `chargeType` | Keep three values, drop Usage, or add `CREDIT`. | A. Keep `RECURRING`, `ONE_TIME`, `USAGE`. Proposed, unaccepted, owner decision required. | Supply `RECURRING` when a document line is stored. No enum change. Proposed, unaccepted, owner decision required. | B drop Usage. C add `CREDIT`. |
| 2. Where the window is stored | Distinct service-period pair, reuse the effective range, or keep the window off the line. | A. Distinct `servicePeriodStart` / `servicePeriodEnd`. Proposed, unaccepted, owner decision required. | No service-period columns. `V1_7` unchanged. Proposed, unaccepted, owner decision required. | A distinct pair, still subject to Fork 3. B reuse `effective_from` / `effective_to`. |
| 3. Early `ONE_TIME` window | Business policy. Allow a `ONE_TIME` service window that starts before the line effective date, refuse it for every charge type, or allow it for every charge type. | A. Allow for `ONE_TIME` only. Proposed, unaccepted, owner decision required. | B. Refuse for every charge type, including `ONE_TIME`. Not priced. Proposed, unaccepted, owner decision required. | A allow for `ONE_TIME` only. C allow for every charge type. |
| 4. Per-line `effectiveDate` | Optional line date, all four dates on the line, or no per-line effective date. | A. Optional line `effectiveDate`, falling back to change `contractEffective`. The other three dates stay on the change. Proposed, unaccepted, owner decision required. | Four dates stay on the change. No new field. Proposed, unaccepted, owner decision required. | A optional line effective date. B all four dates on the line. |
| 5. Non-commitment charge | `Add` plus `ONE_TIME`, a new `Charge` code, or a fee outside the document. | A. `Add` plus `ONE_TIME`. No new action code. Proposed, unaccepted, owner decision required. | No new action code. The one-time line is not allowed yet. Proposed, unaccepted, owner decision required. | A `Add` plus `ONE_TIME`. B new `Charge` code. |
| 6. Billing | Fields only with no receiver, a named receiver under its own decision, or no billing field list. Billing is external. Any receiver needs its own decision. | A. Billing gets fields only, no receiver. This is an option, not a decision. Proposed, unaccepted, owner decision required. | Billing receives nothing. No flag. No deploy. Proposed, unaccepted, owner decision required. | A fields only, no receiver. B a named receiver under a later decision. |
| 7. Credit sign | Leave credits in `ConversionPolicy`, add `CREDIT`, or forbid a negative line. | A. Credits stay in the conversion policy until a line is required. Proposed, unaccepted, owner decision required. | No negative line is required. Proposed, unaccepted, owner decision required. | A negative `ONE_TIME` only if a later decision requires it. B `chargeType = CREDIT`. |

## 8. What stays blocked

- **DRG-536.** The continuation request is one recurring source line. Code that plans a second, one-time line stays blocked on multi-line support. This frame does not design that request.
- **S3 / DRG-581.** [#642](https://github.com/drgaciw/open-crm-cpq/pull/642) merged to `main` on 2026-10-17 at 07:40 UTC. That merge recorded OQ6. It did not start S3. DRG-581 was Todo and unstarted when read this pass. The OQ6 record says S3 needs separate owner authorization. OQ18 stays Proposed. DRG-616 is the design-contract gap named on DRG-581 before S3 starts. This frame does not start S3.
- **S6** is not authorized. This frame does not add fields to `QuoteAcceptedEvent`.
- **DRG-609** is a design proposal. It is not accepted. One-time lines are not priced by extending that design.
- **DRG-550** still owns amendment price policy, credit policy, and termination fees, apart from the proration formula named in section 9.
- **DRG-565** still owns unit grouping. This frame does not close partial apply.
- **Any billing receiver.** Billing is external. Fork 6 option A sends nothing. Fork 6 option B is a separate decision and is not opened here.

## 9. Proration

The Accepted proration record is `prereq/adr-amendment-proration-formula.md` (Accepted 2026-10-16). It covers the day-stub of an amendment or expansion inside the current billing period. Scenario 2's forward maintenance amount is that kind of charge. This frame does not reopen the formula, the rounding, the component that computes the stub, or the Drools rule.

A OneTime amount for a past window is Fork 3. It is not that stub. Fork 3 is Proposed, unaccepted, owner decision required.

## 10. Addendum — proposal-marked and NOT applied

**Status of this addendum:** proposal-marked and NOT applied. Not accepted. Proposed, unaccepted, owner decision required.

`specs/lifecycle-intents.md` was not edited. None of the sentences below were inserted into that file. If the owner later changes that file, the text below is the change this frame asks for, and only after the owner picks the forks it names. Applying it is a separate edit. It still authorizes no code, no migration, no flag, and no deploy.

Proposed additions, and only these, if that later edit happens:

1. On the proposed `ChangeLine` contract, add optional `chargeType` (`RECURRING | ONE_TIME | USAGE`), optional `servicePeriodStart`, optional `servicePeriodEnd`, and optional `effectiveDate`. Omitted `chargeType` means `RECURRING`. Omitted `effectiveDate` means the change's `contractEffective`. `actionDate`, `serviceActivation`, and `customerAcceptance` stay on the change. `entitlementEndDate` stays in `specs/conversions.md`. This bullet follows Forks 1, 2, and 4. It is not applied.
2. Do not add a rule that a service window may start before the line effective date unless the owner has picked Fork 3. If the owner picks Fork 3 option A, the sentence is: a start before the line effective date is valid only when `chargeType` is `ONE_TIME`. If the owner picks Fork 3 option B, the sentence is the alternative: a service window that starts before the line effective date is invalid for every charge type, including `ONE_TIME`. If the owner picks Fork 3 option C, the sentence is: any charge type may start before the line effective date. None of those three sentences is in `specs/lifecycle-intents.md`. This addendum does not apply any of them.
3. Add one sentence to open question 6: the owner confirms Fork 4, that a line effective date may override `contractEffective`, and that this does not move the other three dates onto the line. Not applied.
4. State that the backdating predicate (trigger date before `actionDate`) is unchanged. A `ONE_TIME` service window before the line effective date is Fork 3, not that predicate. Not applied.
5. Add an owner question: whether Add plus `ONE_TIME` is the action for a charge that does not change a commitment (Fork 5). Do not add a `Charge` code in that file until the owner picks Fork 5 option B. Not applied.

Not proposed for that file: a change to the intent enum, the quantity identity, the single `businessCategory`, the proration date (that file's open question 5), the backdating approval rule (open question 7), or a billing receiver.

## 11. Out of scope

- Editing any existing file, including Accepted ADRs, `V1_7`, and `specs/lifecycle-intents.md`. The section 10 addendum is proposal-marked and NOT applied.
- A Flyway migration for service-period columns. Fork 2 option A would need one later. The default does not.
- True-up aggregation, meter snapshots, maintenance percent, reinstatement knobs, conversion credit math, surrender, and ARR classification.
- Accepting ADR-017 or ADR-024 through ADR-027.
- Choosing a billing receiver. Billing is external. Any receiver needs its own decision.
- Starting S3, S6, or the DRG-609 design.
- A feature flag, a route, a topic, or a deploy.

## 12. Evidence

| Item | Where it was read |
|---|---|
| DRG-572, DRG-553, DRG-565, DRG-536, DRG-550, DRG-581 | Linear, this pass. DRG-572 set to In Review and linked to #654. |
| Scenarios 2 and 3, gap G6 | `docs/renewals-amendments/cpq-pricing-policy-sanity-check.md` |
| Line contract and the four dates | `docs/renewals-amendments/prereq/specs/lifecycle-intents.md` (not edited; section 10 is proposal-marked and NOT applied) |
| Continuation request shape | `backend/services/quote-service/.../QuoteContinuationRequest.java` |
| `charge_type`, `effective_from`, `effective_to`, no service-period columns | `backend/services/contract-asset-management/.../V1_7__contract_documents.sql`, `ChargeType.java` |
| ONE_TIME destination class | `docs/renewals-amendments/prereq/adr-reconcile-adr-024-027.md` D-R2, Accepted; ADR-024..027 remain Proposed |
| Proration left closed | `docs/renewals-amendments/prereq/adr-amendment-proration-formula.md`, Accepted |
| ADR-017, ADR-024, ADR-025, ADR-026, ADR-027 | Each opens **Proposed**. Not accepted. |
| #642 | Merged to `main` 2026-10-17T07:40:40Z. OQ6 record. Does not authorize S3. |
| GitNexus | No `.gitnexus/run.cjs` in this checkout. Impact: UNKNOWN. |

No service was started. No test was run. No flag was changed. Nothing was deployed.
