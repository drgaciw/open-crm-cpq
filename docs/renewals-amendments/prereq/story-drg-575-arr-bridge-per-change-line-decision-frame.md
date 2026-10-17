# DRG-575 — ARR bridge per change line: decision frame

**Status:** Proposed, unaccepted, owner decision required.

**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Base:** `main` at `0d71251d`.

**Story:** [DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575) (RA-41, Low). Title: ARR bridge per change line: price, quantity, cross-sell, conversion and non-renewal effects. Linear status when read on 2026-10-17: Backlog. Priority: Low. Labels: `renewals-amendments`, `Open-CRM-CPQ`. Comments were not read. **Unverified.**

**Relations read the same day:** [DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552) (related), [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607) (related), [DRG-600](https://linear.app/drgamtd-workspace/issue/DRG-600) (related). Not re-fetched after that read.

Accepting this note authorizes no code, migration, flag or deploy. DRG-575 stays In Review until the owner merges. DRG-600 stays blocked and out of scope. This file does not make DRG-575 Done.

## How to read this frame

Every fork has options, a recommendation, and a default. Each default is **Proposed, unaccepted, owner decision required.** The alternatives sit beside that default. Adoption is a later act by the owner. This file does not perform it.

**Read** means the cited file was opened and the cited lines were read against `main` at `0d71251d`. **Search** means a text search, not a running process. **Unverified** means the claim was not opened, not executed, or the search can miss it.

This note records a classification frame. It does not calculate a price, an ARR figure, a credit, or a rollup from source quantities. Illustrative amounts below are quoted from the sanity check as published labels. They are example figures, not customer data, and they are not recomputed here.

Boundaries that stay as they are:

- [ADR-035](../adr-035-amendment-document-placement.md) is **Accepted** for placement and for the first-slice schema and audit stories only (S1, S2). Read: status line in `docs/adr/README.md`. Later stories still need their own gates. This frame does not extend that acceptance.
- [ADR-017](../../adr/ADR-017-synchronous-inter-service-communication.md) and [ADR-024](../../adr/ADR-024-order-orchestration-context.md) through [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md) stay **Proposed**. Read: each status line, and the catalog rows in `docs/adr/README.md`. They are not prerequisites. This frame does not choose a transport, an order saga, a command topic, or a compensation path.
- The amendment proration formula in [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) is **Accepted** (2026-10-16). Read: status line and the "Number: none" line. This frame does not restate the formula and does not reopen it.
- OQ5 option A is unchanged. Read: the OQ5 row of [renewals-amendments-open-questions-register.md](../renewals-amendments-open-questions-register.md) and section 9 of [partial-apply-owner-decision.md](partial-apply-owner-decision.md). Option A for the first slice is one unit per target subscription, atomic units, no automatic rollback, `PARTIALLY_APPLIED` when a later unit fails, `APPLY_BLOCKED` when no unit has committed, correcting amendment as remedy. `specs/partial-apply.md` stays not accepted.
- OQ7's thin contract is unchanged. Read: [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md) ("Adopted default, first slice only") and the header comment of `backend/services/contract-asset-management/src/main/resources/db/migration/V1_8__contract_header_backfill.sql`: one thin contract per existing subscription, no synthetic amendment history, no retroactive events. The register row for OQ7 still says **DEFAULT PROPOSED** for the slice (read). This note does not edit that row and does not regroup contracts.
- S3 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) is not authorized. Read: the OQ6 row of the register. S6 is not authorized. Read: the OQ5 row and section 9 of the partial-apply record. This frame defines no event type, topic, registry entry, or outbox contract, and it does not start S3.
- The [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) design in [story-drg-609-pricing-service-continuation-design.md](story-drg-609-pricing-service-continuation-design.md) is a design proposal. Read: its status paragraph. It is not an ADR and it is not accepted. Pricing amounts and the SaaS conversion price in this note are pending that design. This frame does not amend that design and does not specify a pricing call. ADR-017 is not the authority for one.
- [DRG-600](https://linear.app/drgamtd-workspace/issue/DRG-600) (automatic CRM re-forecast) stays blocked and out of scope. Linear status when read on 2026-10-17: Backlog. The issue text says the capability is outside S1–S8 and needs a new integration event. This frame does not design that event, does not push a forecast, and does not report the bridge to CRM. DRG-575 stays In Review until the owner merges.

## Problem

[DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575) says a change line has one `businessCategory`. That single value cannot express a renewal that is also uplifted and downsized. A line that is simply not renewed has no disposition. Using `Remove` for that line would invite a credit or a termination-fee policy.

Illustrative Scenario 4 in [cpq-pricing-policy-sanity-check.md](../cpq-pricing-policy-sanity-check.md) (read, "Audit trail" under Scenario 4) publishes this bridge, as labels, not as a calculation performed here:

- uplift +$6,720 (Analytics) and +$840 (AI Assist, annualised, after the lock);
- cross-sell +$16,200 ARR;
- maintenance uplift +$2,000;
- list repricing +$2,000;
- non-renewal −$3,000.

Illustrative Scenario 3 (read, heading "Scenario 3 (illustrative): mid-term on-prem to SaaS conversion") is a conversion. The ticket says that conversion must not be reported as churn plus new business.

[DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552) defines business categories. It does not define this decomposition and it does not define non-renewal. That overlap is marked below. This frame does not contradict [Cluster E draft PR #651](https://github.com/drgaciw/open-crm-cpq/pull/651).

The ticket's suggested next step is an **option only**: add `renewalDisposition {Renew|NonRenew|Replace}` and `arrBridge {base, priceEffect, qtyEffect, crossSell, conversion, churn}` on the change line, computed at pricing time. "Computed at pricing time on a future pricing response" is an option, not a decision of this note. That option implies a pricing-service contract change, and that contract change needs its own owner decision. This frame does not perform the addition and does not make that contract decision.

## Sources

| Source | What this frame uses it for | How it was checked |
|---|---|---|
| DRG-575 description | The gap, the Scenario 4 labels, the conversion reporting rule, and the suggested next step as an option | Read on Linear, 2026-10-17 |
| [cpq-pricing-policy-sanity-check.md](../cpq-pricing-policy-sanity-check.md) Scenarios 3 and 4 | Illustrative labels and the sentence that one category cannot express a compound renewal | Read. Amounts are not recomputed |
| [cpq-renewals-amendments-research.md](../cpq-renewals-amendments-research.md) [S15], [S35] | RCA category list and `TotalRenewalsAmount`; Zuora one order combining Renew and RemoveProduct | The research sentences were read. The live Salesforce and Zuora pages were not re-fetched. **Unverified** against those pages |
| [specs/arr-bridge.md](specs/arr-bridge.md) | The existing proposal on `main`. Cited below. Not accepted | Read in full |
| [specs/lifecycle-intents.md](specs/lifecycle-intents.md) | DRG-552 proposal: one category, decomposition left to the bridge | Read: status line, the "Landed today" paragraph, AC-7, the `arr-bridge.md` dependency row, open question 3 |
| Cluster E [#651](https://github.com/drgaciw/open-crm-cpq/pull/651) | DRG-552 forks L4 and L7, and the stored category column | The file `cluster-e-lifecyclechange-model-decision-frame.md` on that draft branch was read for the overlap. It is not on `main` |
| Cluster B [#652](https://github.com/drgaciw/open-crm-cpq/pull/652) | Conversion price stays pending DRG-609; the Scenario 3 amount stays open | That draft file was read for the conversion section and the closing list. It is not on `main` |
| Cluster A [#650](https://github.com/drgaciw/open-crm-cpq/pull/650) | Amendment credit and `Remove` stay on DRG-550 / DRG-594. This frame does not change them | That draft file was read for 550-F2, 594-F1, and the money-column note. It is not on `main` |

Salesforce RCA, as the research file states it: an AssetAction has one category from a list that covers Initial Sale, Renewals, Upsells, Downsells, Cross-Sells, Upgrades, Downgrades, Swaps, Cancellations, Terms And Conditions Changes, transfers, and Other, and it keeps rollups such as `TotalRenewalsAmount` [S15]. That is evidence that a rollup can sit beside one category. It is not a schema to copy, and it does not define `NonRenew`.

Zuora, as the research file states it: one Order can hold Renew together with RemoveProduct [S35]. That is evidence for one change that renews some products and ends others. It is not a reason to store a line that is simply not renewed as action `REMOVE`.

## Already on main

| Fact | File path | Migration |
|---|---|---|
| `amendment_document_lines.business_category` is `varchar(40) NOT NULL`. One column. One value | `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql` line 222 | `V1_7__contract_documents.sql` |
| The closed category set is `INITIAL_SALE`, `RENEWAL`, `UPSELL`, `DOWNSELL`, `CROSS_SELL`, `UPGRADE`, `DOWNGRADE`, `SWAP`, `CANCELLATION`, `TERMS_AND_CONDITIONS`, `TRANSFER`, `TRUE_UP`. There is no `CONVERSION` token and no compound token | Same file, `chk_amendment_line_category`, lines 265–268. Java enum `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/documents/BusinessCategory.java` (read) | `V1_7__contract_documents.sql` |
| Line actions include `REMOVE` and `RENEW`. They do not include a non-renewal disposition | Same file, `chk_amendment_line_action`, lines 261–263 | `V1_7__contract_documents.sql` |
| `quantity_semantics` includes `REPLACE`. That token is quantity identity (`chk_amendment_line_qty`, line 276). It is not a renewal disposition | Same file, line 270 | `V1_7__contract_documents.sql` |
| `policy_slot` includes `CONVERSION`. That token is a slot, not a business category | Same file, `chk_amendment_line_policy_slot`, lines 273–275 | `V1_7__contract_documents.sql` |
| The document stores one `arr_delta numeric(19, 4) NOT NULL`, beside `tcv_delta`, `one_time_total`, and `credit_total`. The line table read (lines 214–255) has no bridge columns | Same file, line 72. Entity field `AmendmentDocument.arrDelta`, `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/documents/AmendmentDocument.java` lines 114–115 (read) | `V1_7__contract_documents.sql` |
| No `renewal_disposition` and no `arr_bridge` in backend Java, SQL, or YAML under the tokens `renewalDisposition`, `renewal_disposition`, `arrBridge`, `arr_bridge`, `priceEffect`, `qtyEffect` | Search of `backend/` on 2026-10-17. A differently named column would miss this search. **Unverified** beyond those tokens. A database catalog was not queried. **Unverified by execution** | No migration adds them |
| [specs/arr-bridge.md](specs/arr-bridge.md) is on `main` | `docs/renewals-amendments/prereq/specs/arr-bridge.md` | No migration. The file is a proposal |

`specs/lifecycle-intents.md` line 10 says there is no per-line business category. That sentence is the proposal's "Landed today" paragraph. The S1 migration above stores the column. This frame follows the migration. The proposal file is not edited and is not accepted.

## What `specs/arr-bridge.md` already proposes, and what stays unverified

Read in full. Status line: proposal only. It does not accept a reporting model, write an ADR, or authorize implementation, a flag, or a deploy. This decision frame does not accept it and does not edit it.

The file carries the ticket's suggested option into a proposed contract: `renewalDisposition` of `Renew`, `NonRenew`, or `Replace`, and `arrBridge` of `base`, `priceEffect`, `qtyEffect`, `crossSell`, `conversion`, `churn`, plus `currency`, `computedAt`, and `policyVersionIds[]`. It labels the component meanings **[I], not accepted**. It says each component is an annualized run-rate delta, `base` is prior ARR, and the other fields are signed deltas.

Its open questions 1 through 8 are still open. This frame turns those questions into the forks below. It does not close them by citing the file.

| Claim in that file | Treatment here |
|---|---|
| Six component names and the three disposition values match the ticket's suggested next step | Read. They are the option in forks D1 and F1. They are not stored |
| Scenario 4 movements match the sanity-check list read above | Read. Fork F3 maps them. This frame does not adopt the file's further statement that `priceEffect` sums to +$11,560. That sum was not recomputed. **Unverified as arithmetic**, and not a default |
| A renewal that is also uplifted and downsized keeps one category and splits price from quantity | Agrees with Cluster E's one-category rule. The split order is fork F4 and stays unset |
| Conversion movement sits on `conversion`, with churn 0 on the surrendered lines | The classification rule is fork C1. The dollar figure is fork C2 and stays unset |
| "Computed at pricing" and "quote-service does not recompute" | Fork T1 records "computed at pricing time on a future pricing response" as an option, not a decision. That option implies a pricing-service contract change that needs its own owner decision. Fork T2 does not choose the writer. No pricing response in the tree was read that writes a bridge. The token search above found no writer. **Unverified** that no other name writes the same numbers |
| AC-3's worked line (prior ARR 96000, uplift to 102720, `priceEffect` 6720) | That pair is the sanity check's +$6,720 label applied to a line the spec constructs. This frame does not adopt the acceptance criteria |
| Dependencies on DRG-567, DRG-568, `maintenance.md`, `conversions.md`, `co-termination.md`, and DRG-310 | Named in that file. Those bodies were not re-read for this note, except the status lines of [conversions.md](specs/conversions.md) and [lifecycle-intents.md](specs/lifecycle-intents.md). **Unverified** beyond those status lines and the dependency table |
| The parenthetical "null on a non-renewal intent" on the disposition field | Ambiguous in the file. Fork D4 states the null rule this frame would use. It does not copy the parenthetical as a settled meaning |

## Overlap with Cluster E (#651) and DRG-552

Cluster E is draft PR [#651](https://github.com/drgaciw/open-crm-cpq/pull/651), file `docs/renewals-amendments/prereq/cluster-e-lifecyclechange-model-decision-frame.md`. It is not on `main`. Its status line is Proposed, unaccepted, owner decision required. This frame does not edit it and does not treat its defaults as accepted.

DRG-552, as Cluster E records it, stores one `business_category`. The overlap is:

- Fork L4's default is one stored value, derived for a future first-slice writer from the action and the sign of `delta_qty`. Compound categories, a `CONVERSION` category, and `OTHER` are alternatives, and they are migrations. Cluster E says a product add that is cross-sell rather than upsell is DRG-575, not a second category on the line.
- Fork L7's default writes no conversion line in the first slice and adds no category token. `policy_slot` may be `CONVERSION`. The category check has no `CONVERSION` token.
- Cluster E's "still open" list leaves ARR decomposition of one category to DRG-575. It says DRG-575 can assume one category column per line (`V1_7__contract_documents.sql` line 222).
- Cluster E does not define a non-renewal disposition. That gap is this ticket.
- `specs/lifecycle-intents.md` AC-7 and open question 3 say the same thing as a proposal: one category, and the compound renewal is not solved by adding compound categories. The ARR bridge decomposes the money. That file stays a proposal.

This frame follows those points. `arrBridge.conversion` is a money component. It is not category `CONVERSION` and it is not a new token in `chk_amendment_line_category`. `renewalDisposition` is not a second `business_category` and it is not action `REMOVE`. Cross-sell versus upsell is a bridge component (fork F5), beside the one category Cluster E would store. Nothing here enables `RENEW` on a first-slice submit. Cluster E's L2 default leaves `RENEW` outside that submit set.

## Cluster B (#652) and Cluster A (#650)

Cluster B is draft PR [#652](https://github.com/drgaciw/open-crm-cpq/pull/652). It is not on `main`. Its conversion-pricing forks are pending the DRG-609 design, which is not accepted. It says `specs/arr-bridge.md` still leaves the Scenario 3 conversion amount open. This frame does not choose that amount (fork C2). It does not choose a parity multiple, a credit stack, a surrender action, or a maintenance percent. Those stay on Cluster B. Where this note says "conversion", it means which bridge component holds a movement whose price is still unpriced.

Cluster A is draft PR [#650](https://github.com/drgaciw/open-crm-cpq/pull/650). It is not on `main`. Its 550-F2 default is credit policy `NoCredit` for the amendment price policy, Proposed, unaccepted, owner decision required. Its 594-F1 default treats `REMOVE` of a product line as outside a seats-only legal skip. This frame does not change either fork. A renewal-time `NonRenew` is a disposition so that the line is not action `REMOVE`. It does not adopt `NoCredit`, and it does not write a credit.

## Owner-decision table

Every row is **Proposed, unaccepted, owner decision required.** The default is the choice this note would use if the owner later adopts it. Alternatives are written in the same row. None of them is accepted by this file. No row adds a column, a migration, a flag, or a deploy. Fork T1 does not decide the writer: "computed at pricing time on a future pricing response" is an option, and it implies a pricing-service contract change that needs its own owner decision. Forks D2 and H1 are business-policy forks. DRG-575 stays In Review. DRG-600 stays blocked and out of scope.

| Fork | Question | Recommendation | Default | Alternatives |
|---|---|---|---|---|
| D1 | Disposition vocabulary | A. The ticket's three values, as their own field | Proposed, unaccepted, owner decision required. `renewalDisposition` is `Renew`, `NonRenew`, or `Replace`. It is not `quantity_semantics` `REPLACE` and not a new category | B. Only `Renew` and `NonRenew`. C. Store the dropped line as action `REMOVE`. D. Add category `NON_RENEWAL` (migration; contradicts Cluster E L4) |
| D2 | Business policy — NonRenew treatment | A. Stays an option for the owner. A renewal-time disposition with no credit and no termination fee from this field | Proposed, unaccepted, owner decision required. Business policy. `NonRenew` is not action `REMOVE`. This disposition writes no credit and no termination fee. Cluster A 550-F2 and 594-F1 stay as they are. This row does not adopt the policy | B. `NonRenew` still runs the Remove credit and termination-fee policies. C. `NonRenew` is action `REMOVE` with credit policy `NoCredit` |
| H1 | Business policy — churn treatment | A. Stays an option for the owner. Removed ARR on a `NonRenew` line is `churn`. A conversion is not also `churn` | Proposed, unaccepted, owner decision required. Business policy. Removed ARR on a `NonRenew` line is the `churn` component. A conversion movement stays on `conversion` and is not also stored as `churn` (fork C1). Scenario 4's −$3,000 label is that label and is not recomputed. This row does not adopt the policy | B. A non-renewal is not reported as churn. C. The ended side of a conversion is `churn`, and the new line is new business. D. Every negative ARR movement, including a downsell, is `churn` |
| D3 | What `Replace` means | A. The commercial relationship continues, so the line is not churn plus a new logo | Proposed, unaccepted, owner decision required. `Replace` is the disposition when a successor SKU continues the relationship. The money bucket is F3. This default does not enable a first-slice action | B. `Replace` is `NonRenew` of the old SKU plus `crossSell` of the new SKU. C. Leave `Replace` out of this decision until DRG-576 |
| D4 | When the disposition is null | A. Null off a renewal; on a renewal a dropped line is `NonRenew` | Proposed, unaccepted, owner decision required. Null when the line is not on a renewal change. On a renewal, a line that continues is `Renew` or `Replace`, and a line that is not renewed is `NonRenew`. A conversion line stays null | B. Required on every line. C. Null means "not renewed" |
| C1 | How a conversion stays out of churn plus new business | A. One component holds the movement | Proposed, unaccepted, owner decision required. The arriving line stores the movement on `conversion` only. Source lines store `churn` 0 and `crossSell` 0. The new amount is not also stored as new business. The price of that movement is pending DRG-609 (not accepted) | B. Ended on-prem ARR is `churn` and the new SaaS ARR is new business. C. Maintenance is `churn` and the SaaS line is `conversion` |
| C2 | Which figure `conversion` holds | A. Leave the figure unset | Proposed, unaccepted, owner decision required. This frame does not choose a figure. Cluster B and the unaccepted DRG-609 design own the price. The sanity check's illustrative parity ARR, year-1 net, and maintenance ARR are not selected and not recomputed | B. The new line's parity ARR. C. Year-1 net after credits. D. The delta of new ARR against prior maintenance ARR |
| F1 | Shape of the bridge beside one category | A. The ticket's six components, beside the one stored category | Proposed, unaccepted, owner decision required. `arrBridge` is `base`, `priceEffect`, `qtyEffect`, `crossSell`, `conversion`, `churn`, beside the one `business_category` from `V1_7`. No compound category. No category `CONVERSION` | B. Keep the story inside the one category and add no bridge. C. Add compound categories (migration; Cluster E L4 alternative). D. A single signed line delta and no components |
| F2 | What `base` and the deltas mean | A. Prior ARR, and signed annualized deltas, in the line currency | Proposed, unaccepted, owner decision required. `base` is the line's prior ARR. The other components are signed annualized deltas in the line currency. A document that mixes currencies does not sum them in this decision. This meaning is the spec's **[I]** and is not accepted by citing it | B. `base` is prior TCV and deltas follow the term. C. Components are absolute magnitudes with a separate direction |
| F3 | Where Scenario 4's published labels sit | A. Price movements in `priceEffect`, the new product in `crossSell`, the dropped line in `churn` | Proposed, unaccepted, owner decision required. Analytics +$6,720, AI Assist +$840, maintenance uplift +$2,000, and list repricing +$2,000 are `priceEffect`. Data Catalog +$16,200 is `crossSell`. Legacy Connector −$3,000 is `churn` with disposition `NonRenew`. No sum is stated | B. Maintenance uplift is its own component. C. List repricing is its own component. D. The −$3,000 is a credit line |
| F4 | Price and quantity both change | A. Leave the split order unset | Proposed, unaccepted, owner decision required. No order is chosen. No formula is written. No Scenario 4 line is used as a worked example of both effects. A numeric test waits on a line the owner supplies | B. Price, then quantity. C. Quantity, then price. D. One residual after the other component |
| F5 | Cross-sell money versus the one category | A. The bridge distinguishes the money; the category stays singular | Proposed, unaccepted, owner decision required. ARR of a product the customer did not hold is `crossSell`. A quantity change on a held SKU is `qtyEffect`. The line still has one `business_category`, including whatever Cluster E L4 would store. This fork does not change L4 | B. `crossSell` is allowed only when the category is already `CROSS_SELL`. C. A quantity increase on a held SKU is `crossSell` |
| F6 | List-reprice visibility | A. Do not name a flag in this frame | Proposed, unaccepted, owner decision required. The sanity check says List mode dropping a prior discount needs a visible marker. The marker's name is not in DRG-575. This frame names no field. The money stays `priceEffect` under F3 | B. Add a named boolean on the line (name still an owner choice, and a migration). C. Encode the dropped discount only as category text |
| F7 | Document rollup versus the line bridge | A. The bridge is per line. `arr_delta` stays the document column it already is | Proposed, unaccepted, owner decision required. Components are stored per line when a later story writes them. `amendment_documents.arr_delta` in `V1_7__contract_documents.sql` is not redefined as their sum. This frame adds no document rollup | B. Persist a document-level sum of each component. C. Treat `arr_delta` as that sum |
| T1 | Where the bridge is computed | Not a decision of this note. "Computed at pricing time on a future pricing response" is an option only | Proposed, unaccepted, owner decision required. Option, not a decision: computed at pricing time on a future pricing response. That option implies a pricing-service contract change, because the pricing response would carry the bridge. That contract change needs its own owner decision. This note does not make it and does not accept the DRG-609 design. DRG-575 stays In Review. DRG-600 stays blocked and out of scope | B. A later reporting or CRM job (DRG-600), which stays blocked and out of scope. C. Quote-service computes the bridge after prices return. D. The seller types the components. E. Leave the bridge uncomputed until that separate pricing-service contract decision |
| T2 | Reprice and a manual override | A. Only if the owner later chooses the T1 option: the pricing response replaces the bridge on the new revision | Proposed, unaccepted, owner decision required. Quote-service does not recompute the bridge. This row does not decide T1. If the owner later chooses the pricing-time option, a reprice writes the new revision's bridge and leaves the previous revision's bridge as it was. That option implies a pricing-service contract change that needs its own owner decision. A manual override is outside this default | B. Quote-service recomputes on every read. C. A manual override recomputes in the same edit. D. A manual override marks the bridge stale and does not recompute |
| T3 | An unpriced draft | A. Null until a pricing response writes the bridge | Proposed, unaccepted, owner decision required. A line with no pricing trace has a null bridge. Zeros are not written to stand in for "not priced" | B. Write zeros on every draft. C. Omit the object and also omit `renewalDisposition` until execute |

## Forks

### D1 — Disposition vocabulary

| Option | Meaning |
|---|---|
| A | `renewalDisposition` is `Renew`, `NonRenew`, or `Replace`. Own field. The ticket's suggested next step. |
| B | Only `Renew` and `NonRenew`. SKU substitution uses another fork. |
| C | A line that is not renewed is action `REMOVE`. |
| D | Add business category `NON_RENEWAL`. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, only `Renew` and `NonRenew`; C, action `REMOVE`; D, category `NON_RENEWAL` by a migration. `quantity_semantics` `REPLACE` in `V1_7__contract_documents.sql` line 270 stays a quantity token. This default does not reuse it.

### D2 — Business policy — NonRenew treatment

Business policy. This fork stays an option for the owner. It is not adopted. The ticket's reason for a disposition is that `Remove` would invite a credit or a termination-fee policy. Scenario 4's Legacy Connector is "not renewed" and is described as a non-renewal disposition (sanity check, read).

| Option | Meaning |
|---|---|
| A | `NonRenew` writes no credit and no termination fee from this disposition. It is not action `REMOVE`. |
| B | `NonRenew` is a label and the Remove credit and fee policies still run. |
| C | The line is action `REMOVE` and the credit policy is `NoCredit`. |

Recommendation: **A**, as an option for the owner. Proposed, unaccepted, owner decision required.

**Default:** A. Proposed, unaccepted, owner decision required. Business policy. Alternatives: B, the Remove policies still run; C, action `REMOVE` with `NoCredit`. Cluster A 550-F2 and 594-F1 are unchanged. The Accepted proration formula is not the credit policy and is not restated. This default does not adopt the policy.

### H1 — Business policy — churn treatment

Business policy. This fork stays an option for the owner. It is not adopted. It is the reporting meaning of `churn`. Fork C1 still keeps a conversion on `conversion` alone. Fork F3 still maps Scenario 4's published −$3,000 label and does not recompute it.

| Option | Meaning |
|---|---|
| A | Removed ARR on a `NonRenew` line is the `churn` component. A conversion movement stays on `conversion` and is not also stored as `churn`. |
| B | A non-renewal is not reported as churn. |
| C | The ended side of a conversion is `churn`, and the new line is new business. |
| D | Every negative ARR movement, including a downsell, is `churn`. |

Recommendation: **A**, as an option for the owner. Proposed, unaccepted, owner decision required.

**Default:** A. Proposed, unaccepted, owner decision required. Business policy. Alternatives: B, a non-renewal is not churn; C, conversion is churn plus new business; D, every negative ARR movement is churn. C would undo fork C1. This default does not adopt the policy. DRG-600 stays blocked and out of scope, so this fork does not publish the classification.

### D3 — `Replace`

| Option | Meaning |
|---|---|
| A | `Replace` means the commercial relationship continues. The movement is not churn plus a new logo. Which component holds a price difference is F3, and F3's default uses `priceEffect` when the owner treats the relationship as continued. |
| B | The old SKU is `NonRenew` (`churn`) and the new SKU is `crossSell`. |
| C | `Replace` waits for DRG-576. This decision does not include it. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, `NonRenew` plus `crossSell`; C, defer `Replace` to DRG-576. DRG-576's body was not re-read. **Unverified.** This default does not add an action code and does not authorize a first-slice `RENEW` submit.

### D4 — When the field is null

`specs/arr-bridge.md` writes "null on a non-renewal intent" beside the enum. That parenthetical can be read as "null when the change is not a renewal" or as "null when the line is not renewed". The second reading would make `NonRenew` unused. This fork picks one reading as the default.

| Option | Meaning |
|---|---|
| A | Null when the line is not on a renewal change. On a renewal, a continued line is `Renew` or `Replace`, and a line that is not renewed is `NonRenew`. A conversion line stays null. |
| B | Every line, including a mid-term amendment and a conversion, carries one of the three values. |
| C | Null itself means the line was not renewed. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, required on every line; C, null means not renewed. A conversion stays on forks C1 and C2, with a null disposition under this default, which keeps `Replace` from meaning "converted".

### C1 — Conversion is one movement

Scenario 3 is a restructure from perpetual cores plus maintenance to a SaaS agreement (sanity check, read). The ticket's reporting rule is that this movement is not churn plus new business. Cluster B owns the price and leaves the amount open. Cluster E L7's default writes no conversion line in the first slice and adds no category `CONVERSION`.

| Option | Meaning |
|---|---|
| A | The arriving line holds the movement on `conversion` only. Source lines store `churn` 0 and `crossSell` 0. The same amount is not also stored as new business. |
| B | The ended on-prem amount is `churn` and the new SaaS amount is new business. |
| C | Maintenance ARR is `churn` and the SaaS line is `conversion`. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, churn plus new business; C, churn the maintenance and convert the rest. The SaaS conversion price is pending the DRG-609 design, which is not accepted. This default adds no category token and does not change Cluster E L7. It does not choose surrender, terminate, or add as action codes. Those stay on Cluster B.

### C2 — The conversion figure

The sanity check publishes illustrative figures for Scenario 3, including a parity ARR, a year-1 net, and prior maintenance ARR. `specs/arr-bridge.md` open question 3 lists three different stories for which of those kinds of figure `conversion` would hold. This frame does not recompute any of them and does not choose among them.

| Option | Meaning |
|---|---|
| A | The figure stays unset. A later accepted pricing design names it. The component rule in C1 still applies to whatever figure that is. |
| B | The new line's parity ARR. |
| C | Year-1 net after credits. |
| D | The delta of the new ARR against prior maintenance ARR. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, parity ARR; C, year-1 net; D, delta against prior maintenance ARR. B, C, and D are pricing choices. They are pending the DRG-609 design, which is not accepted, and they stay open on Cluster B.

### F1 — Decomposition fields

| Option | Meaning |
|---|---|
| A | Six components: `base`, `priceEffect`, `qtyEffect`, `crossSell`, `conversion`, `churn`. One `business_category` remains the category. |
| B | No bridge. The one category carries the whole story. |
| C | Compound categories, so one line can be "renewal + uplift + downsell" as categories. |
| D | One signed ARR delta on the line, with no components. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, no bridge; C, compound categories by a migration; D, one signed delta and no components. C is Cluster E L4's alternative and is not the recommendation. The document `arr_delta` column is not this bridge (fork F7).

### F2 — `base` and the deltas

DRG-575 names the six fields and does not define whether `base` is ARR or TCV, or whether deltas are annualized. `specs/arr-bridge.md` open question 1 records that gap and marks the annualized reading as an assumption. The Scenario 4 audit trail labels its figures as ARR effects, including "AI Assist annualised".

| Option | Meaning |
|---|---|
| A | `base` is prior ARR. Other components are signed annualized deltas in the line currency. Mixed currencies are not summed here. |
| B | `base` is prior TCV. Deltas follow the charged term, including Scenario 4's 14-month labels if the owner later prices them. |
| C | Each component is an absolute magnitude plus a direction. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, prior TCV and term deltas; C, absolute magnitudes with a direction. Term TCV for a 14-month line stays on the line's own amount columns when a later story prices it. This default does not annualize a TCV and does not reopen the proration formula. Co-termination dates stay on their own story. `specs/co-termination.md` was not re-read. **Unverified.**

### F3 — Scenario 4 labels

The six labels are the sanity check's published list. They are not recomputed. No total is stated.

| Option | Meaning |
|---|---|
| A | `priceEffect`: Analytics +$6,720, AI Assist +$840, maintenance uplift +$2,000, list repricing +$2,000. `crossSell`: Data Catalog +$16,200. `churn`: Legacy Connector −$3,000, disposition `NonRenew`. `qtyEffect` and `conversion` are unused by these labels. |
| B | Maintenance uplift is a seventh component. |
| C | List repricing is a seventh component. |
| D | The −$3,000 is a credit on a `REMOVE` line. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, a separate maintenance component; C, a separate list-reprice component; D, a credit line. Maintenance percent and the uplift percent are prices. They are pending the DRG-609 design where they are part of a priced line, and they are not set here. The +$840 figure is the sanity check's "annualised, after the lock" label. This frame does not derive it from the lock or from the 14-month amount.

### F4 — Price and quantity together

The ticket's gap is a renewal that is also uplifted and downsized. Scenario 4's published labels do not include one line that changes both price and quantity. `specs/arr-bridge.md` open question 2 says the order of the split changes the two components, and that no Scenario 4 line exercises both.

| Option | Meaning |
|---|---|
| A | The order stays unset. This frame writes no formula. |
| B | Apply the price change at the prior quantity, then the quantity change at the new price. |
| C | Apply the quantity change at the prior price, then the price change at the new quantity. |
| D | Compute one component and put the remainder in the other. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, price then quantity; C, quantity then price; D, a residual. A later numeric test needs a worked line from the owner. This frame does not invent that line.

### F5 — Cross-sell and the single category

Cluster E L4, if the owner adopts it, can store `UPSELL` on an `ADD`. The same note says cross-sell versus upsell is DRG-575, not a second category. The stored set already contains `CROSS_SELL` (`chk_amendment_line_category`). This fork does not decide who types that token.

| Option | Meaning |
|---|---|
| A | `crossSell` holds ARR of a product the customer did not hold. `qtyEffect` holds a quantity change on a held SKU. The line still has exactly one `business_category`. |
| B | The `crossSell` component is used only when the category is `CROSS_SELL`. |
| C | A quantity increase on a held SKU is `crossSell`. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, the component follows the category token only; C, a quantity increase on a held SKU is `crossSell`. L4 is unchanged. Data Catalog in Scenario 4 is the sanity check's cross-sell label (fork F3). This fork does not derive a category for it.

### F6 — List-reprice marker

Scenario 4 says Reporting Server renews in List mode and the prior discount is dropped, so the movement needs a visible flag (sanity check, read). DRG-575's field list has no such flag. `specs/arr-bridge.md` open question 4 says the same and proposes no accepted name.

| Option | Meaning |
|---|---|
| A | No flag is named. The money is `priceEffect` under F3. The marker stays unnamed until a later owner choice. |
| B | A later migration adds a boolean. This frame still does not choose the name. |
| C | The dropped discount is represented by category text. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, a boolean whose name is chosen later; C, category text. The list price itself is a price-book read. [story-drg-539-pricebook-read-for-list-bases.md](story-drg-539-pricebook-read-for-list-bases.md) was not re-read. **Unverified.** Any priced list is pending the DRG-609 design, which is not accepted.

### F7 — Line bridge and document `arr_delta`

DRG-575 asks for the bridge per line. `specs/arr-bridge.md` open question 7 leaves a persisted document sum unset. `V1_7__contract_documents.sql` already has `amendment_documents.arr_delta`.

| Option | Meaning |
|---|---|
| A | Components live on the line. `arr_delta` remains the document column it is. This frame does not define it as the sum of the bridge. |
| B | A later story also persists a document-level sum of each component. |
| C | `arr_delta` is that sum. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, a persisted document sum; C, redefine `arr_delta` as the sum. No reporting warehouse is added. DRG-600 stays blocked and out of scope.

### T1 — Where the bridge is computed

"Computed at pricing time on a future pricing response" is an **option**, not a decision of this note. The ticket's suggested next step names pricing time. Choosing that option implies a pricing-service contract change: the pricing response would have to carry the bridge. That contract change needs its own owner decision. This note does not make it.

The DRG-609 design note, read, describes how pricing-service could price Add, Update, and Remove continuation lines and return a policy decision id, a policy version, and a trace id. It does not describe `arrBridge`. Its status is a design proposal, not an ADR, and it is not accepted. This fork does not extend that design.

| Option | Meaning |
|---|---|
| A | Computed at pricing time on a future pricing response. The response would carry the bridge. This is a pricing-service contract change and needs its own owner decision. |
| B | Computed later by a reporting or CRM job. That path is DRG-600, which stays blocked and out of scope. |
| C | Quote-service computes the bridge after prices return. |
| D | The seller types the components. |
| E | Leave the bridge uncomputed until the separate pricing-service contract decision. |

This note does not pick A. Proposed, unaccepted, owner decision required.

**Default:** Proposed, unaccepted, owner decision required. Option, not a decision: computed at pricing time on a future pricing response. Alternatives: B, a later CRM or reporting job, and DRG-600 stays blocked and out of scope; C, quote-service as a second calculation; D, seller entry; E, leave the bridge uncomputed until the pricing-service contract has its own owner decision. No flag is turned on. No route is added. ADR-017 does not authorize a call. DRG-575 stays In Review.

### T2 — Reprice and override

| Option | Meaning |
|---|---|
| A | Quote-service does not recompute. A reprice replaces the bridge on the new revision and leaves the previous revision's bridge unchanged. |
| B | Quote-service recomputes whenever the bridge is read. |
| C | A manual override recomputes the bridge in the same edit. |
| D | A manual override marks the bridge stale and leaves the numbers until the next price. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, recompute on read; C, an override recomputes immediately; D, an override marks the bridge stale. This default describes a reprice only if the owner later chooses the T1 option. T1 is an option, not a decision, and it implies a pricing-service contract change that needs its own owner decision. OQ6's revision rule, as the register records it, clears pricing pins when a pre-execution revision is superseded. This fork does not change OQ6 and does not start S3. The register's OQ6 row was read. The OQ6 decision file was not re-opened. **Unverified** beyond the register row.

### T3 — Unpriced draft

| Option | Meaning |
|---|---|
| A | Null bridge until a pricing response writes one. |
| B | Zeros on every new draft. |
| C | The field is absent until execute, and so is `renewalDisposition`. |

Recommendation: **A**.

**Default:** A. Proposed, unaccepted, owner decision required. Alternatives: B, zeros; C, both fields absent until execute. Zeros would read as a computed bridge of no movement. "A pricing response writes one" is the T1 option, not a decision, and that option implies a pricing-service contract change that needs its own owner decision. This default does not reject or accept a document. S3 is not authorized.

## Ticket status

DRG-575 stays In Review until the owner merges. This note does not make it Done.

DRG-600 stays blocked and out of scope. This note does not design a CRM re-forecast, a reporting push, or an integration event.

## What an owner decision would still not start

An owner choice among these forks is input to a later story. Accepting this note authorizes no code, migration, flag or deploy. DRG-575 stays In Review. DRG-600 stays blocked and out of scope.

It does not:

- add columns, checks, or a Flyway script;
- accept `specs/arr-bridge.md`, `specs/lifecycle-intents.md`, or `specs/conversions.md`;
- accept or edit the DRG-609 design;
- decide that the bridge is computed at pricing time on a future pricing response (that remains an option; it implies a pricing-service contract change that needs its own owner decision);
- set a conversion price, an uplift, a maintenance percent, a credit, or a parity multiple;
- adopt the business-policy forks for churn treatment (H1) or NonRenew treatment (D2);
- change Cluster E L4 or L7, Cluster A 550-F2 or 594-F1, or Cluster B's conversion forks;
- change OQ5 option A or the OQ7 thin contract;
- reopen the Accepted proration formula;
- treat ADR-017 or ADR-024 through ADR-027 as accepted, or as prerequisites;
- authorize S3 or S6;
- push a forecast or any other CRM integration (DRG-600 stays blocked and out of scope);
- enable a flag or deploy anything.

## Unverified

- Linear comments on DRG-575. Not read.
- Live Salesforce AssetAction page and live Zuora modify-subscriptions page. The research file's [S15] and [S35] sentences were read. The pages were not re-fetched.
- Any backend name for this bridge other than the tokens searched under "Already on main".
- A database catalog. Not queried.
- DRG-567, DRG-568, DRG-310, DRG-576, `maintenance.md`, and `co-termination.md` bodies. Not re-read.
- `story-drg-539-pricebook-read-for-list-bases.md` and `oq6-revision-model-owner-decision.md`. Not re-opened. The register OQ6 row was read.
- The arithmetic behind the sanity check's labels, including +$6,720, +$840, +$16,200, +$2,000, +$2,000, and −$3,000, and behind Scenario 3's illustrative parity and net figures. Quoted, not recomputed.
- The sum `specs/arr-bridge.md` states for `priceEffect`. Not recomputed and not adopted.
- GitNexus impact. No index is present in this checkout (no `.gitnexus` directory). Impact is UNKNOWN.
