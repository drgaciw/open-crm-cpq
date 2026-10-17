# DRG-572 — One-time and retroactive charge lines: decision frame

**Status:** Proposed. Not accepted. This file is not an ADR, takes no number, and is not copied into `docs/adr/`. It does not accept `specs/lifecycle-intents.md`, `specs/partial-apply.md`, `specs/maintenance.md`, `specs/conversions.md`, `specs/capacity-meters.md`, the OpenAPI draft, or the DRG-609 pricing-service design. Accepting this note authorizes no code, no migration, no flag, no topic, no gateway route, and no deploy.

**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Story:** [DRG-572](https://linear.app/drgamtd-workspace/issue/DRG-572/one-time-and-retroactive-charge-lines-with-their-own-service-period) (RA-38). Linear status read this pass: Backlog.

**Related issues read this pass.** [DRG-553](https://linear.app/drgamtd-workspace/issue/DRG-553) (action date versus trigger dates), [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) (partial application), [DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536) (no multi-line amendment), [DRG-550](https://linear.app/drgamtd-workspace/issue/DRG-550) (amendment price, proration, credit and termination-fee policies). [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) (S3) was read the same pass: Todo, unstarted.

**Sources.** Pricing policy sanity check, Scenarios 2 and 3 (`../cpq-pricing-policy-sanity-check.md`). `specs/lifecycle-intents.md`. Landed continuation request and CAM `V1_7` line columns, read in this tree. The Accepted proration record is cited so this frame does not reopen it. The reconcile record for ADR-024 through ADR-027 is Accepted; ADR-017 and ADR-024 through ADR-027 themselves stay Proposed, and this frame does not treat them as accepted.

**Conventions.** **[S]** is an issue, a file, or a column read this pass. **[I]** is a design inference in this frame. Every recommendation is an **Owner decision**. A default is what holds until the owner picks. A default is not an acceptance.

Numbers below are the sanity check's illustrative figures, not customer data, and not tests.

## 1. Question

How does a lifecycle change carry a non-recurring line whose service window can start before that line's effective date, and what does billing receive, given the line contract `specs/lifecycle-intents.md` already proposes?

The lines DRG-572 names:

- Back-maintenance for a true-up. Scenario 2: 58 excess core-months, $2,416.67, service window 2026-04-01 through 2026-09-30, before contract effective 2026-10-01. **[S sanity Scenario 2]**
- A reinstatement fee. The amount and the lapse rule are `specs/maintenance.md`. The charge line is this story. **[S DRG-572, maintenance.md]**
- True-up arrears that do not themselves change the committed quantity. The +16 core update is a different line. **[S sanity Scenario 2]**
- A conversion credit consumed on another agreement. The credit stack is `specs/conversions.md`. The line that billing would see is this story. **[S DRG-572, sanity Scenario 3]**

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
- A rule that a OneTime service window may start before the line effective date.
- An action whose only effect is a charge or a credit, with no commitment change.
- A billing handoff: no payload, no receiver, no agreement about which service reads the line.
- The money. True-up measurement, maintenance percent, reinstatement knobs, conversion credit math, and the day-stub of a recurring amendment are other files.

Section 10 is a proposal-marked addendum for that file. It is not applied. The file on `main` is unchanged.

## 4. What is already stored, and what is not

Two layers exist. They are not the same contract.

**Continuation request, which is what pricing runs today [S].** `QuoteContinuationRequest` has one operation, one `sourceSubscriptionLineUuid`, one `quantity`, and one `effectiveDate`. It has no `chargeType`, no service period, and no second line. DRG-536 is this gap. A back-maintenance line cannot be planned on this request.

**Amendment document line, CAM `V1_7` [S].** `amendment_document_lines` already has:

- `charge_type` in `RECURRING`, `ONE_TIME`, `USAGE` (`ChargeType`). The column is not null. The SQL has no default. The field schema and the OpenAPI draft, both still proposals, say the omitted value is Recurring.
- `effective_from` (not null) and `effective_to` (null only when open), with `effective_to` absent or on or after `effective_from`.
- `apply_target_type` including `ONE_TIME`.
- `net_amount`, with no sign check in that migration.

That table has no `service_period_start` and no `service_period_end`. `effective_from` is one date. It cannot, by itself, record both "charge becomes effective 2026-10-01" and "service window 2026-04-01 through 2026-09-30". Nothing in the check constraints says a `ONE_TIME` window may precede the change's effective date. No billing service exists under `backend/services/`. The column is not a billing API.

S1 stored the charge-type column. It did not decide retroactive service periods. This frame does not alter `V1_7`.

## 5. Illustrative lines

These rows show the fields under the recommendation. They are not fixtures to implement, and they are not an acceptance of the sanity check's measurement choices.

**Scenario 2, back-maintenance, separate from the capacity update.** The update line remains Update, Delta +16, on the perpetual commitment. The forward maintenance stub of $2,016.44 stays with the Accepted proration record and with `specs/maintenance.md`. This frame does not recompute it.

| Field | Back-maintenance line |
|---|---|
| `actionCode` | Add |
| `chargeType` | ONE_TIME |
| `quantitySemantics` | Absolute |
| `startQty`, `deltaQty`, `endQty` | 0, +1, 1 (one charge, not +16 cores) |
| `servicePeriodStart` | 2026-04-01 |
| `servicePeriodEnd` | 2026-09-30 |
| `effectiveDate` | 2026-10-01 |
| Amount | $2,416.67 as stated in the sanity check |

The service window ends the day before the line effective date. That is the retroactive case. The sanity check does not adopt a rounding rule for the $41.67 core-month rate. This frame does not add one.

**Scenario 3, credits.** Unused prepaid maintenance $20,164.38 and trade-in $19,998.72 are `specs/conversions.md` figures. Each credit that must be consumed on SaaS-3003 is a candidate `ONE_TIME` line with its own service window, its own effective date, `sourceAgreement` LM-2002, and `targetAgreement` SaaS-3003. Which invoice absorbs the credit stays in that proposal.

## 6. Forks

Each fork has a recommendation and a default. The recommendation is the pick this note asks for. The default is what remains true until the owner makes that pick. Neither is an acceptance.

### Fork 1 — `chargeType` closed set

| Option | What the owner would be choosing |
|---|---|
| **A. Keep `RECURRING`, `ONE_TIME`, `USAGE`** | The three values already in `ChargeType` and in `chk_amendment_line_charge`. Usage stays a value. Usage arithmetic stays out of this story. |
| **B. `RECURRING` and `ONE_TIME` only** | Drop Usage from the line contract. |
| **C. Add `CREDIT`** | A fourth value for Scenario 3 credits. |

**Recommendation:** A. The same SKU can be recurring maintenance and a one-time back-maintenance charge, so the type is stored on the line. It is not inferred from the catalogue alone.

**Default until the owner decides:** writers that store a document line supply `RECURRING` unless they have a reason to do otherwise. No new value is added. No column is dropped. The continuation request still has no charge type.

### Fork 2 — Service period versus effective date

| Option | What the owner would be choosing |
|---|---|
| **A. Distinct `servicePeriodStart` / `servicePeriodEnd`** | Both absent, or both set with end on or after start. `ONE_TIME` may start before the line `effectiveDate`, including a window that ends before that date. `RECURRING` and `USAGE` may not start before the line `effectiveDate`. |
| **B. Reuse `effective_from` / `effective_to`** | The retroactive window is the line's effective range. There is no second pair. "Before the effective date" then means before the change-level `contractEffective` only. |
| **C. Any charge type may start before the effective date** | The OneTime restriction is dropped. |
| **D. Retroactive money is not a line** | A credit memo or a billing adjustment outside the lifecycle line. |

**Recommendation:** A. Scenario 2 needs both dates: the window being charged, and the day the charge sits on the change. Overloading `effective_from` loses that pair. The OneTime restriction keeps a recurring amendment from silently backdating its whole term.

**Default until the owner decides:** no service-period columns. A window that starts before the line effective date cannot be stored as its own pair. `V1_7` is unchanged. Retroactive lines are not priced.

This fork is not the backdating rule in `specs/lifecycle-intents.md`. That rule compares a trigger date with `actionDate`. A OneTime service window before the line effective date is a different pair of dates. This frame leaves the backdating rule on that file's open question 7.

### Fork 3 — Per-line `effectiveDate`

| Option | What the owner would be choosing |
|---|---|
| **A. Optional line `effectiveDate`, defaulting to the change `contractEffective`** | The other three dates stay on the change. On a document line, the existing `effective_from` is this date. It is not the service period. |
| **B. Copy all four dates onto every line** | `actionDate`, `contractEffective`, `serviceActivation`, and `customerAcceptance` become per-line. |
| **C. No per-line effective date** | Only the service period is per-line. |

**Recommendation:** A. DRG-553's four dates stay on the change, which is what `specs/lifecycle-intents.md` open question 6 already proposes. Scenario 2's back-maintenance line needs its own effective date so the service window can precede it. `entitlementEndDate` stays in `specs/conversions.md`.

**Default until the owner decides:** the four dates remain change-level in the lifecycle proposal. A document line still requires `effective_from` because `V1_7` requires it. This note adds no lifecycle field and no migration.

### Fork 4 — Action for a charge that does not change a commitment

The sanity check says there is no action code for a one-time retroactive charge that leaves the commitment alone. `V1_7` allows Add, Update, Remove, NoChange, Renew, Upgrade, Downgrade, Swap, ConvertMetric, Suspend, Resume, Terminate. It does not allow a Fee or Charge code. `specs/lifecycle-intents.md` open question 2 still leaves the action-code set to the owner.

| Option | What the owner would be choosing |
|---|---|
| **A. `Add` plus `ONE_TIME`** | Quantity is the charge (Absolute 1), not the core delta. The commitment update is a sibling line. No new action code. |
| **B. New action code `Charge`** | A code that must be `ONE_TIME` and must not change a commitment. That is a check-constraint change. |
| **C. `NoChange` on the source line, fee outside the document** | The charge is not a ChangeLine. |

**Recommendation:** A. A new code belongs in the lifecycle action-code decision, not in a silent extension of `chk_amendment_line_action`.

**Default until the owner decides:** no new action code. A one-time line, once an owner allows it, uses Add. This note does not allow it yet.

### Fork 5 — How billing receives the line

There is no billing service in this repo. ADR-017 and ADR-024 through ADR-027 are **Proposed**. They are not accepted. This frame does not assume an order service, a saga, a command, or a synchronous billing call.

The Accepted reconcile record (`prereq/adr-reconcile-adr-024-027.md`) already states a v1 destination class, and its own header says that acceptance does not accept ADR-024 through ADR-027:

- `ONE_TIME` on a document that also has an on-prem unit is an order line on that on-prem order. The charge is not a ledger rate period.
- `ONE_TIME` on a SaaS-only document has no order in v1. Billing stays with the still-Proposed Quote-to-Revenue saga, which that record leaves outside its scope.

This frame does not reopen that destination class.

| Option | What the owner would be choosing |
|---|---|
| **A. Fields only** | A later consumer would read line id, `chargeType`, service period, line effective date, net amount, currency, source agreement, and target agreement when the credit is consumed elsewhere. No event, no command, no HTTP route in this decision. |
| **B. Build the on-prem order line now** | Treats Proposed ADR-024 through ADR-027 as if they were accepted. |
| **C. Publish a billing event or command now** | A new integration surface. S6 is not authorized to extend `QuoteAcceptedEvent` for this. |

**Recommendation:** A. The Accepted reconcile record names where a `ONE_TIME` apply target goes once an order exists. It does not create that order. Scenario 2's back-maintenance line is the on-prem case in that record. Scenario 3's credit on SaaS-3003 is the SaaS-only case: no v1 order. `specs/partial-apply.md` may group the charge as its own `ONE_TIME` unit; that file is still a proposal. OQ5 option A, already adopted for the first slice, does not define these fields.

**Default until the owner decides:** billing receives nothing. No outbox row, no command, no flag.

### Fork 6 — Charge versus credit

| Option | What the owner would be choosing |
|---|---|
| **A. Credits stay in `ConversionPolicy` until a line is required** | If billing must show the credit, the line is `ONE_TIME` with a negative net amount, the service window the policy names, and the target agreement. No `CREDIT` value. |
| **B. `chargeType = CREDIT`** | Fork 1 option C. |
| **C. Negative amounts forbidden** | A credit cannot be a document line. It lives only inside the conversion policy. |

**Recommendation:** A. `V1_7` already allows a negative `net_amount` because it has no sign check. Conversion formulas, caps, and `FirstInvoice` versus `Spread` versus `DecliningLine` stay in `specs/conversions.md`. Reinstatement `feePct` stays in `specs/maintenance.md`, with no default. Termination-fee policy stays on DRG-550.

**Default until the owner decides:** this frame requires no negative line. Credits are not mapped onto ChangeLines.

## 7. Owner decision needed

1. **Fork 1.** Confirm `RECURRING | ONE_TIME | USAGE`, or pick B or C. Recommendation A. Default: no enum change.
2. **Fork 2.** Confirm a distinct service period that may precede the line effective date only for `ONE_TIME`, or pick B, C, or D. Recommendation A. Default: no service-period columns, and no retroactive pricing.
3. **Fork 3.** Confirm an optional per-line effective date that overrides change-level `contractEffective`, with the other three dates remaining on the change. Recommendation A. Default: lifecycle dates stay on the change.
4. **Fork 4.** Confirm Add plus `ONE_TIME` for a charge that does not change a commitment, or add a code. Recommendation A. Default: no new action code.
5. **Fork 5.** Confirm a field handoff and no receiver. Recommendation A. Default: billing receives nothing. ADR-017 and ADR-024 through ADR-027 stay Proposed.
6. **Fork 6.** Confirm that a conversion credit becomes a negative `ONE_TIME` line only when billing must show it. Recommendation A. Default: no credit line is required by this note.

Picking all six, and accepting this note, still authorizes no code, no migration, and no deploy.

## 8. What stays blocked

- **DRG-536.** The continuation request is one recurring source line. Code that plans a second, one-time line stays blocked on multi-line support. This frame does not design that request.
- **S3 / DRG-581.** [#642](https://github.com/drgaciw/open-crm-cpq/pull/642) merged to `main` on 2026-10-17 at 07:40 UTC. That merge recorded OQ6. It did not start S3. DRG-581 was Todo and unstarted when read this pass. The OQ6 record says S3 needs separate owner authorization. OQ18 stays Proposed. DRG-616 is the design-contract gap named on DRG-581 before S3 starts. This frame does not start S3.
- **S6** is not authorized. This frame does not add fields to `QuoteAcceptedEvent`.
- **DRG-609** is a design proposal. It is not accepted. One-time lines are not priced by extending that design.
- **DRG-550** still owns amendment price policy, credit policy, and termination fees, apart from the proration formula named in section 9.
- **DRG-565** still owns unit grouping. This frame does not close partial apply.

## 9. Proration

The Accepted proration record is `prereq/adr-amendment-proration-formula.md` (Accepted 2026-10-16). It covers the day-stub of an amendment or expansion inside the current billing period. Scenario 2's forward maintenance amount is that kind of charge. This frame does not reopen the formula, the rounding, the component that computes the stub, or the Drools rule.

A OneTime back-maintenance amount is a different line. It is not that stub.

## 10. Addendum — proposal to amend `specs/lifecycle-intents.md`

**Status of this addendum:** PROPOSAL. Not applied. Not accepted. `specs/lifecycle-intents.md` was not edited. If the owner later changes that file, the text below is the change this frame asks for. Applying it is a separate edit. It still authorizes no code.

Proposed additions, and only these:

1. On the proposed `ChangeLine` contract, add optional `chargeType` (`RECURRING | ONE_TIME | USAGE`), optional `servicePeriodStart`, optional `servicePeriodEnd`, and optional `effectiveDate`. Omitted `chargeType` means `RECURRING`. Omitted `effectiveDate` means the change's `contractEffective`. `actionDate`, `serviceActivation`, and `customerAcceptance` stay on the change. `entitlementEndDate` stays in `specs/conversions.md`.
2. Add an invariant: the service-period pair is both absent or both present; the end is on or after the start; a start before the line effective date is valid only when `chargeType` is `ONE_TIME`.
3. Add one sentence to open question 6: the owner confirms Fork 3, that a line effective date may override `contractEffective`, and that this does not move the other three dates onto the line.
4. State that the backdating predicate (trigger date before `actionDate`) is unchanged. A OneTime service window before the line effective date is not that predicate.
5. Add an owner question: whether Add plus `ONE_TIME` is the action for a charge that does not change a commitment (Fork 4). Do not add a `Charge` code in that file until the owner picks Fork 4 option B.

Not proposed for that file: a change to the intent enum, the quantity identity, the single `businessCategory`, the proration date (that file's open question 5), or the backdating approval rule (open question 7).

## 11. Out of scope

- Editing any existing file, including Accepted ADRs, `V1_7`, and `specs/lifecycle-intents.md`.
- A Flyway migration for service-period columns. Fork 2 option A would need one later. The default does not.
- True-up aggregation, meter snapshots, maintenance percent, reinstatement knobs, conversion credit math, surrender, and ARR classification.
- Accepting ADR-017 or ADR-024 through ADR-027.
- Starting S3, S6, or the DRG-609 design.
- A feature flag, a route, a topic, or a deploy.

## 12. Evidence

| Item | Where it was read |
|---|---|
| DRG-572, DRG-553, DRG-565, DRG-536, DRG-550, DRG-581 | Linear, this pass |
| Scenarios 2 and 3, gap G6 | `docs/renewals-amendments/cpq-pricing-policy-sanity-check.md` |
| Line contract and the four dates | `docs/renewals-amendments/prereq/specs/lifecycle-intents.md` |
| Continuation request shape | `backend/services/quote-service/.../QuoteContinuationRequest.java` |
| `charge_type`, `effective_from`, `effective_to`, no service-period columns | `backend/services/contract-asset-management/.../V1_7__contract_documents.sql`, `ChargeType.java` |
| ONE_TIME destination class | `docs/renewals-amendments/prereq/adr-reconcile-adr-024-027.md` D-R2, Accepted; ADR-024..027 remain Proposed |
| Proration left closed | `docs/renewals-amendments/prereq/adr-amendment-proration-formula.md`, Accepted |
| ADR-017, ADR-024, ADR-025, ADR-026, ADR-027 | Each opens **Proposed**. Not accepted. |
| #642 | Merged to `main` 2026-10-17T07:40:40Z. OQ6 record. Does not authorize S3. |
| GitNexus | No `.gitnexus/run.cjs` in this checkout. Impact: UNKNOWN. |

No service was started. No test was run. No flag was changed.
