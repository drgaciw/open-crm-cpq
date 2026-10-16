# Lifecycle intents, change lines, and trigger dates: first-slice spec proposal

**Status:** Proposal only. This file does not accept a policy, write an ADR, or authorize implementation, a flag change, or a deploy. DRG-548 asks for an ADR; that ADR is a later document. This file is the slice the ADR would accept or replace.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `lifecycle-intents`. **Date:** 2026-10-16.

**Sources.** [DRG-548](https://linear.app/drgamtd-workspace/issue/DRG-548) (RA-14, unified intent), [DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552) (RA-18, change-line action, quantity, category), [DRG-553](https://linear.app/drgamtd-workspace/issue/DRG-553) (RA-19, action date versus trigger dates). Market vocabularies in those issues are evidence, not the enum to copy.

**Conventions.** **[S]** is taken from those issues or the repo facts they rely on. **[I]** is a design inference carried forward as a proposal. Every closed set below is an **Owner decision**.

**Landed today [S repo].** `QuoteContinuationRequest.Operation` is `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION`. `QuoteContinuationService` stores both `RENEWAL` and `EXTENSION` as `DealType.RENEWAL`. The request has one `effectiveDate` and no action date. There is no per-line action code, quantity semantics, or business category.

## Scope

### In this slice

1. One lifecycle-change root whose intent selects the default policy set. The proposed intent set adds Cancel, Restructure, Suspend, Resume, Transfer, TrueUp, and Convert beside the four operations already stored. **[S DRG-548]**
2. A `ChangeLine` contract: `actionCode`, `quantitySemantics`, `startQty`, `deltaQty`, `endQty`, and `businessCategory`. **[S DRG-552]**
3. Dates stored separately on the change: `actionDate`, `contractEffective`, `serviceActivation`, `customerAcceptance`. Rate periods keep their own start and end. **[S DRG-553]**
4. A mapping table from each intent to the action codes it may emit, and to the policy slot it selects (Amendment, Renewal, TrueUp, Maintenance, Conversion). The slot is a name. The policy bodies live in the other cluster proposals.
5. `EXTENSION` remains distinguishable from `RENEWAL` on the stored change, which today's `DealType` collapse does not do. **[S DRG-548 related DRG-546]** How the existing deal-type field is migrated is an owner decision.

### Out of this slice

Policy arithmetic (true-up, conversion, maintenance, co-term, ARR bridge, ramp amendment). The amendment document, its states, and partial apply (`partial-apply.md`, DRG-535, DRG-536). Per-line entitlement end and surrender (`conversions.md`). Renewal uplift inputs (DRG-567). This slice defines the words those policies hang on.

### Proposed contract

```text
LifecycleChange
  intent
    Amend | Renew | Extend | Expand | Cancel | Restructure
    | Suspend | Resume | Transfer | TrueUp | Convert
  actionDate
  contractEffective
  serviceActivation
  customerAcceptance
  targetAgreementIds[]

ChangeLine
  actionCode
    Add | Update | Delete | NoChange | Renew | Suspend | Resume
    | Terminate | Cancel | Surrender | Convert
  quantitySemantics          { Delta | Absolute | Replace }
  startQty, deltaQty, endQty
  businessCategory
    Upsell | Downsell | Swap | Renewal | CrossSell | TrueUp
    | Conversion | Cancel | Other(code)
  policySlot                 name of the typed policy, plus version once priced
```

`Surrender` is listed because conversions need it. The surrender status and entitlement end date are specified in `conversions.md`. `TrueUp` as an intent is specified as a word here; its policy is `capacity-meters.md`.

**Proposed invariants [I], for the owner to accept with the vocabulary:**

- `startQty + deltaQty = endQty` when semantics are Delta, and `endQty >= 0`.
- Absolute sets `endQty` and stores `deltaQty = endQty − startQty`.
- Replace names the source line and the replacement line. It is the swap shape, not a second quantity math.
- `NoChange` carries the line through a multi-line document without a price effect.
- `actionDate` is when the actor submitted the change. The three trigger dates are when the contract, the service, and the customer acceptance take effect. They may fall on different days. A trigger date before `actionDate` is backdating and stays blocked until an owner sets the backdating rule (mid-term policy V6, still open).

Vendor lists in DRG-548 and DRG-552 (DealHub order types, Salesforce initiate calls, Zuora order actions, Oracle ABO, TMF622 add/modify/delete/noChange, Conga absolute quantity) justify having one root and a per-line action. They are not merged into a single enum here.

## Acceptance criteria

1. **Intent round-trip.** A change saved as Extend is reloaded as Extend. It is not reloaded as Renew. A change saved as Renew is reloaded as Renew.
2. **Closed intent set.** A payload whose intent is outside the accepted set is rejected and names the field. Adding a value is a schema change, not a free string.
3. **Default policy slot.** For a fixture policy table, Renew selects the renewal slot, TrueUp selects the true-up slot, Restructure selects the conversion slot, and Amend selects the amendment slot. The test supplies the table. This slice does not seed production defaults.
4. **Delta identity.** Update, Delta, start 64, delta +16, end 80 is valid. The same line with end 79 is rejected.
5. **Absolute identity.** Update, Absolute, start 64, end 80 stores delta +16. End quantity below zero is rejected.
6. **Replace.** A Replace line names one source line and one new line, and does not also carry a Delta on the source quantity.
7. **Category is singular and stored.** A line has one `businessCategory`. A renewal that is also an uplift and a downsell still has one category; the ARR bridge (`arr-bridge.md`) decomposes the money. This slice must not overload the category to carry that decomposition.
8. **Dates are independent.** A fixture with action date 2026-10-05, contract effective 2026-10-01, service activation 2026-10-01, and customer acceptance 2026-10-20 persists all four. Pricing reads the trigger date the policy names, and the audit row stores all four.
9. **Backdating is visible.** Contract effective before action date is stored as backdated. Until the owner sets a reason code and an approval rule, the transition to a customer-visible state is refused. The refusal identifies the two dates.
10. **Existing operations still plan.** A continuation request that today uses AMENDMENT, EXPANSION, RENEWAL, or EXTENSION still produces a lifecycle change whose intent is the matching new value, and whose quantity and dates match the current planner's inputs. This slice does not change those planners' prices.
11. **Intent allows the action.** A Cancel change that contains only Add lines is rejected. The allowed pairs come from the owner-accepted mapping table. The test uses a table supplied with the test, and fails if the table is empty.
12. **Multi-line document can mix categories.** One change may contain an Upsell line and a TrueUp line, each with its own action code and semantics. Applying that mix is `partial-apply.md`.

## Dependencies

| Dependency | Why this slice needs it |
|---|---|
| DRG-535, DRG-536 | The change root and multi-line document that carry these fields |
| DRG-546 | Extension versus renewal is already indistinguishable in stored deal type; AC-1 is the correction |
| DRG-472 | Cancellation behavior for a renewal; this slice names Cancel and does not define the fee |
| DRG-461 | Co-termination behavior that a Transfer or merge intent will call; see `co-termination.md` |
| `capacity-meters.md`, `conversions.md`, `maintenance.md` | Consumers of TrueUp, Restructure, Convert, and Surrender |
| `arr-bridge.md` | Needs `businessCategory` plus a separate disposition, because one category is not enough |
| Mid-term policy spec §3 and §4 | Document states and V6 backdating stay open (OQ2, OQ9) and gate AC-9 |

Pricing of each slot stays in pricing-service. Quote-service passes the intent, the line contract, and the dates. **[I sanity §4]**

## Open questions

1. **Owner decision — the intent enum.** The list in the proposed contract is the union of DRG-548's gaps and the current four operations. The owner may drop Transfer, split Convert from Restructure, or keep TrueUp as an Amend with a policy slot. AC-3 and AC-11 cannot be implemented until this set is fixed.
2. **Owner decision — the action-code enum.** Research cites TMF622, Oracle, Salesforce, Conga, and DealHub, and they disagree (absolute versus delta versus replacement). The proposed list is a superset. The owner picks the closed set and the per-intent allow-list.
3. **Owner decision — business categories.** Upsell, downsell, swap, renewal, cross-sell, true-up, conversion, and cancel are the names the sibling specs use. Whether they are system-derived or seller-entered is unset. DRG-575 says one category cannot express a compound renewal; do not solve that by adding compound categories.
4. **Owner decision — EXTENSION migration.** New changes store Extend distinctly. Whether historical rows that collapsed Extend into Renew are backfilled, left as Renew, or marked unknown is unset. This slice does not rewrite history by default.
5. **Owner decision — which trigger date prices the line.** DRG-553 requires all three dates. It does not say whether proration uses contract effective, service activation, or customer acceptance. Sibling policies need a per-policy answer.
6. **Owner decision — change-level dates versus per-line dates.** DRG-553 adds the dates on the change. Sanity §4 says Scenarios 2–4 need per-line start, end, service period, and entitlement end. This proposal keeps the four dates on the change and lets `conversions.md` add `entitlementEndDate` on the line. The owner confirms that split.
7. **Owner decision — backdating policy.** Limits, reason codes, and approval thresholds are mid-term OQ9. AC-9 refuses customer visibility until that decision exists.
8. **Owner decision — default policy per intent.** DRG-548 asks for a default policy per intent. The defaults are commercial choices (for example, Cancel's credit and fee). This file reserves the slot and leaves the default empty.
9. **Owner decision — ADR scope.** An ADR is the suggested next step on DRG-548. Whether that ADR is a separate document that cites this proposal, and who the deciders are, is unset. This file is not that ADR.
