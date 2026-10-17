# Cluster B — on-prem to SaaS and maintenance: decision frame

**Status:** Proposed. This note records options, a recommendation, and a default for each fork. Merging it leaves every fork Proposed. It does not accept a policy, an ADR, a spec, or a story.
**Date:** 2026-10-17.
**Base:** `main` at `3ee3ca0f` (merge of [#642](https://github.com/drgaciw/open-crm-cpq/pull/642)).
**Project:** OPEN-CRM-CPQ. Linear team DRG.
**File role:** one cluster note and one section per ticket. New file only.

**Authorizes:** nothing. Accepting this note authorizes no code, migration, flag, topic, gateway route, or deploy. S6 (RA-50 / [DRG-584](https://linear.app/drgamtd-workspace/issue/DRG-584)) stays unauthorized. S3 (RA-47 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581)) stays unstarted: [#642](https://github.com/drgaciw/open-crm-cpq/pull/642) merged on 2026-10-17 and recorded OQ6, and the OQ6 record still requires separate owner authorization before DRG-581 starts (`oq6-revision-model-owner-decision.md`). The [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) continuation design stays unaccepted (`story-drg-609-pricing-service-continuation-design.md`).

**How to read a default.** The default is the proposed fallback named so a later story has one starting point. It is in force only after the owner confirms that fork. Until then the default prices nothing and changes no schema.

**Illustrative figures.** Scenario 2, 3, and 4 amounts in `../cpq-pricing-policy-sanity-check.md` are example numbers. This note does not adopt 2.5×, 20%, 184/365, or 92/365 as a default.

## Cluster note

Cluster B is the on-prem book beside the subscription continuation that already runs: a perpetual licence with linked maintenance, a maintenance price that follows that licence, and one lifecycle change that can move the customer to SaaS.

Tickets read from Linear on 2026-10-17. Each was Backlog. None had comments. Relations are `relatedTo` only; none of the five blocks or is blocked by another issue.

| Ticket | RA | Title | Related issues read |
|---|---|---|---|
| [DRG-573](https://linear.app/drgamtd-workspace/issue/DRG-573) | RA-39 | Conversion pricing policy for on-prem to SaaS | DRG-559, DRG-341, DRG-548, DRG-607 |
| [DRG-574](https://linear.app/drgamtd-workspace/issue/DRG-574) | RA-40 | Perpetual licence surrender and entitlement revocation | DRG-558, DRG-559, DRG-341, DRG-607 |
| [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) | RA-22 | Perpetual licence plus maintenance linkage | DRG-571, DRG-607, DRG-547 |
| [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559) | RA-25 | On-prem to SaaS restructure in one lifecycle change | DRG-341, DRG-573, DRG-574, DRG-565, DRG-563, DRG-607, DRG-547 |
| [DRG-571](https://linear.app/drgamtd-workspace/issue/DRG-571) | RA-37 | Maintenance pricing policy | DRG-556, DRG-607 |

Descriptions were read in full. Each description asks for a commercial or model choice (a policy, an action, or a linkage). The suggested next steps that say "add a field" are the later implementation of that choice. None of the five descriptions asks this pull request to change code, run a migration, enable a flag, or deploy. No ticket is skipped.

Sibling write-ups already on `main` are proposals. This frame does not accept them and does not edit them:

- `specs/conversions.md` (DRG-559, DRG-573, DRG-574)
- `specs/maintenance.md` (DRG-556, DRG-571)
- `specs/lifecycle-intents.md` (DRG-548 and related)
- `specs/catalog-model.md` (DRG-576)
- `renewal-policy-spec.md` rule RN5

Closed on `main`, and left closed here:

- Amendment proration is the Accepted day-stub in `adr-amendment-proration-formula.md`. This note does not reopen it and does not offer another formula.
- Renewal uplift basis, cap, as-of date, precedence, and unit rounding are the Accepted record in `adr-renewal-uplift-basis-and-precedence.md`. This note does not amend that record.
- [ADR-035](../adr-035-amendment-document-placement.md) is Accepted for the first-slice schema and audit stories only (S1, S2).
- OQ5 option A is adopted for the first slice only (`partial-apply-owner-decision.md`). `specs/partial-apply.md` stays unaccepted. S4 and S6 stay unauthorized.
- OQ6 is recorded for the first slice (`oq6-revision-model-owner-decision.md`, [#642](https://github.com/drgaciw/open-crm-cpq/pull/642)). That record does not start S3.

Still Proposed, and not assumed here:

- [ADR-017](../../adr/ADR-017-synchronous-inter-service-communication.md) (Proposed on 2026-10-04).
- [ADR-024](../../adr/ADR-024-order-orchestration-context.md), [ADR-025](../../adr/ADR-025-saga-process-manager.md), [ADR-026](../../adr/ADR-026-command-messages.md), and [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md) (each Proposed on 2026-10-04). `adr-reconcile-adr-024-027.md` is an Accepted reconciliation. Its acceptance scope says it does not accept ADR-024 through ADR-027. On-prem fulfilment can be specified against that reconciliation. It does not authorize an order-service.

[DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607) is the spec umbrella (In Review on 2026-10-17). This frame does not check off that umbrella.

## DRG-573 — conversion pricing

**Linear:** [DRG-573](https://linear.app/drgamtd-workspace/issue/DRG-573) (RA-39). Backlog. Priority High. No comments.

### Problem

A conversion from perpetual cores plus maintenance to a SaaS metric needs its own versioned policy: a quantity map, a price basis, an optional parity ramp, a credit stack with caps and an application target, a cross-agreement credit transfer, and a price-lock term. Agreement-level restructure (DRG-559) and a metric-only `ConvertMetric` do not carry that policy. The landed hybrid credit (DRG-341) is one credit shape. The ticket's worked example is sanity-check Scenario 3.

### Settled on main

- Continuation operations are `AMENDMENT`, `EXPANSION`, `RENEWAL`, and `EXTENSION` (`backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationRequest.java`).
- A renewal or extension prices one flat period from the exit net unit price. A null uplift percent becomes zero. An amendment or expansion keeps the target term and the accepted unit rates (`QuoteContinuationPolicy.java`, `plan`).
- Pricing that moves the net unit off that plan is rejected with `SUBSCRIPTION_CONTINUATION_RATE_CHANGED` (`QuoteService.java`).
- The planner selects exactly one accepted source line (`QuoteContinuationPolicy.source`).
- Recurring continuation sources are the fixed product types in `AcceptedRampSnapshots` (`SAAS_SEAT_NAMED`, `SAAS_SEAT_CONCURRENT`, `ONPREM_SUPPORT`, `HYBRID_MIGRATION_RAMP`). `ONPREM_PERPETUAL` is a catalog type in product-service and pricing-service and is outside that set.
- The landed migration credit is 30% of book value, scale 2 half-up, capped by the declining line's gross TCV and by its first period amount, with no spill onto the rising line (`MigrationCreditPolicy.java`). The declining member is `ONPREM_SUPPORT` and the rising member is `HYBRID_MIGRATION_RAMP` (`HybridMigrationPolicy.java`).
- Accepted proration (`adr-amendment-proration-formula.md`, D-01) charges the requested quantity forward: a partial billing period by inclusive days, then whole months. D-04 of that record assigns an unused-days credit to DRG-550. The formula prices the requested quantity forward. Scenario 3's `40,000 × 184/365` stays an illustration in the sanity check. This note does not adopt it as a formula.
- Accepted renewal uplift D-01 defaults the renewal basis to `ContractedNet`. A conversion parity multiple is a different question. This note does not add it to that basis enum.
- `specs/conversions.md` proposes a `ConversionPolicy` shape and Scenario 3 acceptance criteria. That file is a proposal.
- The DRG-609 design is unaccepted, so this policy is not priced by the sketched continuation route.

### Options, recommendation, and default

Every row is Proposed.

| Fork | Options | Recommendation | Default |
|---|---|---|---|
| Price basis | `SpendParity(multiple)`; `TargetListLessDiscount`; `Override` | `SpendParity` is the named basis for the first customer-facing conversion. The other two stay selectable rungs. | `SpendParity`. The multiple is a required input. A missing multiple leaves the line unresolved. 2.5 is not filled in. |
| What rounding holds fixed | Hold the rounded unit and let ARR drift; hold the target ARR and let the unit carry the fraction | Hold the rounded unit and store the drift, because the Accepted proration snapshot and the continuation plan both store a unit and derive amounts. | Hold the unit at scale 2 half-up, matching `QuoteContinuationPolicy.money` and the Accepted proration snapshot. ARR is derived. This default does not amend uplift D-05 (renewal unit scale 4) and does not reopen proration. |
| Credit rows | Unused prepaid only; trade-in only; both; a second copy of the 30% book credit | One credit list that can name `UnusedPrepaid`, `TradeInPctOfBook`, and `Fixed`. The phased 30% credit stays `MigrationCreditPolicy`. | The list is empty until a rung adds a row. Rows are not combined by silence. `MigrationBookPct` is not reimplemented here. |
| Application target | `FirstInvoice`; `Spread`; `DecliningLine` | `FirstInvoice` when the source maintenance line ends and there is no hybrid pair. `DecliningLine` when `HybridMigrationPolicy` accepts the pair. | `FirstInvoice` for that big-bang shape. `DecliningLine` for a landed hybrid pair. `Spread` is available and is not the default. |
| Where the quantity map lives | On `ConversionPolicy`; on a catalogue `ProductRelationship`; a pin to a catalogue version | The catalogue row is the source of truth when DRG-576 is confirmed. The priced line stores the version pin. | A conversion line stores a map version pin. No pin leaves the line unresolved. `specs/catalog-model.md` stays a proposal. |
| Map rounding and minimum | `Down`, `Nearest`, `Up`; a minimum quantity | `Nearest`, half-up to a whole quantity, minimum 1, so an uneven ratio cannot yield zero. | `Nearest`, minimum 1. Scenario 3's 80 × 2 = 160 does not exercise this default. |
| Credit transfer and expiry | Target agreement only; remainder may return to the source; remainder lives until a named expiry | Consumption is on the target agreement. The source agreement is stored and is not reduced again. | Each row stores source agreement and target agreement. Unused remainder expires at the end of the row's application window. `FirstInvoice` means that invoice. |
| Price-lock length | A `priceLockMonths` value on this policy; leave length to the price-lock story | The field may exist. The length belongs to DRG-568 and `price-lock-policy-stub.md`. | No lock. The field is unset. An unset lock does not suppress the Accepted renewal uplift. |
| `ConvertMetric` beside this policy | Fold metric-only changes into `ConversionPolicy`; keep `ConvertMetric` as its own intent | Keep `ConvertMetric` as its own intent. | A metric change that stays on the same licence model does not use `ConversionPolicy`. |
| Parity ramp | One multiple for the term; a list of year multiples | The ramp is optional and explicit. | `parityRamp` is empty. One multiple covers the term. |
| Unused-prepaid day count | *(closed)* | The Accepted proration ADR already chooses the day-stub and assigns an unused-days credit to DRG-550. | This policy does not compute `184/365`. An `UnusedPrepaid` row, when a rung adds one, carries an explicit money amount, or it calls the Accepted day-stub where that stub applies to a recurring billing period. DRG-550 remains the home of the credit policy. |

### Owner decision needed

Confirm or replace each default in the table. The confirmation that matters first is the price basis and the rule that the multiple is required, then whether unused-prepaid and trade-in start empty, then that the landed 30% credit stays in `MigrationCreditPolicy`.

### Dependencies

| Dependency | Why it is named |
|---|---|
| DRG-559 | The restructure intent is the change that points at this policy. |
| DRG-556 | The source maintenance line has to be a linked line before a credit can name it. |
| DRG-574 | Surrender and entitlement end set the dates a credit window can use. |
| DRG-341 | Phased credit and the 1–12 month window already run. This frame keeps that code. Linear DRG-341 was In Progress on 2026-10-17 (rollout). |
| DRG-576 / `specs/catalog-model.md` | Proposed home of `quantityMapping`. Unaccepted. |
| DRG-539 | A `TargetListLessDiscount` rung needs a price-book read. The DRG-539 note does not decide DRG-609. |
| DRG-550 | Unused-days credit, as assigned by the Accepted proration record. |
| DRG-568 | Price-lock length. |
| DRG-609 | Touched only to leave it unaccepted. ADR-017 is not the authority for a new pricing call. |
| OQ5 first-slice adoption | Create-before-terminate ordering can be named. `specs/partial-apply.md` stays unaccepted. S4 stays unauthorized. |

### What it unblocks

An owner confirmation lets a later implementation story write tests against these defaults, including the Scenario 3 arithmetic as a fixture for the forks the owner confirmed. Confirmation does not start that story. It does not start S3, S4, S5, or S6. It does not accept the DRG-609 route. It does not choose the ARR component in `specs/arr-bridge.md` (that file still leaves the Scenario 3 conversion amount open).

## DRG-574 — licence surrender

**Linear:** [DRG-574](https://linear.app/drgamtd-workspace/issue/DRG-574) (RA-40). Backlog. Priority Medium. No comments.

The ticket's suggested next step names an action code, a status, and two fields. This section records the commercial choice those names would store. It adds no field and no fulfilment step.

### Problem

A perpetual licence does not end by the subscription states Terminated or Expired. A conversion or trade-in needs the customer to surrender it, usually at the end of a dual-use window, and then needs the keys revoked. Scenario 3 keeps on-prem use through 2026-12-31 after SaaS starts on 2026-07-01. The change has an action date and three trigger dates. The perpetual line needs its own entitlement end.

### Settled on main

- A search of `backend/` for `Surrender`, `Surrendered`, `entitlementEndDate`, `isPermanent`, and `licenseModel` finds no production Java or SQL.
- Catalog enums include `ONPREM_PERPETUAL` (`backend/services/product-service/src/main/java/com/opencrm/product/catalog/ProductType.java` and `backend/services/pricing-service/src/main/java/com/opencrm/pricing/product/ProductType.java`). That enum value is not a commitment status.
- The landed dual-entitlement window is the hybrid pair's overlap. `MigrationWindowProperties` defaults `opencrm.pricing.ramp.migration-window-months` to 12 and rejects a value outside 1–12. `HybridMigrationPolicy` requires the declared window to equal the contiguous positive-quantity overlap. That window is not a surrender date on a perpetual line.
- DRG-558 (Backlog) proposes commitment states from PendingActive through Terminated, Expired, and Superseded. Its description does not include Surrendered.
- ADR-024 through ADR-027 remain Proposed. The Accepted reconciliation says on-prem fulfilment can be specified and does not authorize order-service. Revocation does not assume ADR-024's action list.
- Accepted proration prices a forward stub. It does not end an entitlement.
- `specs/lifecycle-intents.md` lists `Surrender` as a proposal. `specs/conversions.md` proposes status `Surrendered`. Both files stay proposals.
- OQ6, recorded by #642, governs document revisions around `EXECUTED`. It does not define entitlement end.

### Options, recommendation, and default

Every row is Proposed.

| Fork | Options | Recommendation | Default |
|---|---|---|---|
| Action on the perpetual line | New action `Surrender`; reuse `Terminate`; reuse `Cancel` | `Surrender` is its own action. | `Surrender`. `Terminate` is the action on the maintenance line's commercial end. |
| Commitment status | New status `Surrendered`; reuse `Terminated`; reuse `Expired`; reuse `Superseded` from the DRG-558 list | `Surrendered` is distinct from `Terminated` and from `Expired`. | `Surrendered`. DRG-558's proposed list is unchanged until that story absorbs this status. |
| Entitlement end | One shared end date; a per-line `entitlementEndDate` | The perpetual line stores `entitlementEndDate` separately from the maintenance commercial end. | `entitlementEndDate` is its own line field. Action date, contract effective, service activation, and customer acceptance stay change-level dates. This default does not add a fifth change-level trigger. |
| Dual-use length | A silent six months from Scenario 3; the landed 1–12 month hybrid cap for every conversion; an explicit date; dual use forbidden on a big-bang conversion | The date is explicit on the line. The 12-month cap stays on the landed hybrid pair. | A surrender line with no `entitlementEndDate` is refused. Six months is not filled in. The hybrid constructor's 12-month cap applies when the quote is a hybrid pair. |
| When revocation is requested | At pricing time; at maintenance end; a fulfilment request after entitlement end; key revoke inside CPQ | A fulfilment request after `entitlementEndDate`, for DRG-558 to consume. CPQ does not revoke keys. | The change records line id, `entitlementEndDate`, and `convertedFromLineIds`. Quote-service and pricing-service do not call a licence server. S6 is not authorized to carry the request as an event. ADR-024 through ADR-027 are not assumed. |
| Status during the window | Perpetual stays entitled until `entitlementEndDate` while SaaS is active; perpetual moves to a pending-end state when SaaS starts | Both entitlements stay active until the entitlement end. | Status becomes `Surrendered` at `entitlementEndDate`. SaaS start does not surrender the perpetual line. |

### Owner decision needed

Confirm that the perpetual line uses `Surrender` / `Surrendered` with its own entitlement end, that the dual-use length is an explicit date, and that revocation is a fulfilment request DRG-558 would later consume.

### Dependencies

| Dependency | Why it is named |
|---|---|
| DRG-573 | Credit windows read these dates. This section does not price the credit. |
| DRG-559 | Surrender is one line inside the single restructure change. |
| DRG-556 | The line being surrendered is the perpetual licence the maintenance line points at. |
| DRG-558 | Consumes the revocation request and owns the state list this default proposes to extend. |
| DRG-341 | The 1–12 month overlap cap stays on the hybrid pair. |
| DRG-563 | The apply ledger still has one `subscription_id` per acceptance (`V1_6__subscription_rate_schedule.sql`, `applied_quote_acceptances`). A later apply of surrender plus a new SaaS subscription waits on that story. This note does not start it. |
| S6 / DRG-584 | Unauthorized. The revocation request is not an event contract. |

### What it unblocks

An owner confirmation lets DRG-558's later design include `Surrendered` with the commercial meaning above. It does not start DRG-558, does not add the action to the lifecycle-intents proposal, and does not start S3 or S6.

## DRG-556 — licence plus maintenance

**Linear:** [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) (RA-22). Backlog. Priority High. No comments.

The ticket's suggested next step names `licenseModel`, `isPermanent`, and `relatedPerpetualLineId`. This section records when those facts are required. It adds no column. The ticket's "about 60%" on-prem rationale is the issue's priority text. It is not a measured figure in this repository.

### Problem

Sold lines today are recurring subscription lines. A perpetual licence with a separate maintenance line, renewed on the maintenance line, is not modeled. The renewal the seller books for that pair is the maintenance (or a term licence), and the perpetual line stays in place.

### Settled on main

- No `licenseModel`, `isPermanent`, or `relatedPerpetualLineId` in backend Java or SQL.
- Catalog product types include `ONPREM_PERPETUAL`, `ONPREM_CORE`, `ONPREM_SUBSCRIPTION`, and `ONPREM_SUPPORT` (product-service `ProductType`; pricing-service `ProductType` has the same perpetual and support names plus further on-prem values). A product type does not point a sold maintenance line at a sold perpetual line.
- Continuation plans one recurring source line and keeps or uplifts its accepted unit rate (`QuoteContinuationPolicy`). `ONPREM_SUPPORT` can be that source when it is in `AcceptedRampSnapshots`' fixed set. `ONPREM_PERPETUAL` is outside that set.
- `renewal-policy-spec.md` rule RN5 says a line with `isPermanent` is not `Renew` and is not `Remove`, and that its linked maintenance line is renewed. That file's status is a proposal. This note does not accept it.
- `amendment-entity-field-schema.md` allows a null `effective_to` for a perpetual line as a design field. That schema is not a landed column for this linkage.
- `specs/maintenance.md` proposes the same three fields. It is a proposal.

### Options, recommendation, and default

Every row is Proposed.

| Fork | Options | Recommendation | Default |
|---|---|---|---|
| Where the licence model lives | A `licenseModel` on the sold line (`Perpetual`, `TermLicense`, `Maintenance`, `Subscription`); catalog `ProductType` alone; a separate agreement type | `licenseModel` on the sold line. Catalog type stays the product classification. | Those four values. `isPermanent` is true only when `licenseModel` is `Perpetual`. Any other combination is refused. A SaaS line is `Subscription`. |
| How maintenance finds its licence | `relatedPerpetualLineId` on the maintenance line; a catalogue `MAINTENANCE_FOR` row alone; both | The sold line stores the instance id. A catalogue row, when DRG-576 is confirmed, authorizes the SKU pair and does not compute the percent. | `relatedPerpetualLineId` is required when `licenseModel` is `Maintenance`. A missing id refuses the line. `specs/catalog-model.md` stays a proposal. |
| What a renewal may do | Renew maintenance only; renew the perpetual line as well; treat a version-date refresh as a renewal | Renew the maintenance line. Renew a term-licence line when that is what was sold. | The perpetual line may appear as `NoChange`. A commercial `Renew` of a `Perpetual` line is refused. A commercial `Remove` of that line is refused. This default matches the commercial rule RN5 states, and it does not accept `renewal-policy-spec.md`. |
| Quantity match | Maintenance quantity equals the perpetual quantity; a subset of cores is allowed | The linked maintenance quantity equals the perpetual quantity. | A mismatch is refused. A subset rule is absent until the owner adds one. |
| Version-date refresh | A fulfilment action on the perpetual line; a maintenance entitlement; left out of this decision | Left out. DRG-558 owns fulfilment actions. | No commercial action on the perpetual line performs a version-date refresh. |

### Owner decision needed

Confirm the four-value `licenseModel`, the required instance link, the renewal rule (maintenance renews; the perpetual line does not), and equal quantities. Confirm that a version-date refresh stays out of this decision.

### Dependencies

| Dependency | Why it is named |
|---|---|
| DRG-571 | Prices the linked maintenance line. Linkage comes first. |
| DRG-574 | Surrenders the perpetual line this link names. |
| DRG-548 / `specs/lifecycle-intents.md` | Proposed vocabulary for `Renew` and `NoChange`. Unaccepted. |
| DRG-576 / `specs/catalog-model.md` | Proposed `MAINTENANCE_FOR` SKU check. Unaccepted. |
| `renewal-policy-spec.md` RN5 | Same commercial rule, still a proposal. |
| DRG-572 | One-time charges. Not required to store the link. |

### What it unblocks

An owner confirmation gives DRG-571 a line to price and gives DRG-574 a line to surrender. It lets a later renewal story test the perpetual line as `NoChange` with maintenance on `Renew`. It does not accept the renewal policy spec, and it does not start S3.

## DRG-559 — restructure in one lifecycle change

**Linear:** [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559) (RA-25). Backlog. Priority Medium. No comments. The description says the motion depends on the linkage item (RA-22 / DRG-556) and on earlier intent items. DRG-341 is the related hybrid ramp.

### Problem

There is no single lifecycle change that ends perpetual maintenance, surrenders the perpetual licence, adds the SaaS lines, and applies a credit policy. DealHub's RESTRUCTURE order type and SAP's new-contract change are research evidence in the ticket. Using them for this motion is the research's design inference, which this note treats as one option.

### Settled on main

- Continuation is one operation on one source line inside one subscription (`QuoteContinuationRequest`, `QuoteContinuationPolicy.source`).
- An amendment or expansion cannot carry a net price that differs from the accepted unit rate (`SUBSCRIPTION_CONTINUATION_RATE_CHANGED`).
- The landed hybrid quote is a declining `ONPREM_SUPPORT` line and a rising `HYBRID_MIGRATION_RAMP` line in one quote, with the 30% credit on the declining line's first period (`HybridMigrationPolicy`, `MigrationCreditPolicy`). It does not store `priorAgreementId`, and it does not surrender `ONPREM_PERPETUAL`.
- `applied_quote_acceptances` has `accepted_quote_id` unique and a single `subscription_id` (`backend/services/contract-asset-management/src/main/resources/db/migration/V1_6__subscription_rate_schedule.sql`). DRG-563 (Backlog) records that one acceptance cannot span the new SaaS subscription and the maintenance subscription.
- ADR-035 is Accepted for S1 and S2 only. The amendment document lives in contract-asset-management. That acceptance does not authorize a Restructure API.
- OQ5 option A is adopted for the first slice: one unit per target subscription, no automatic rollback, correcting amendment as the remedy (`partial-apply-owner-decision.md`). Restructure test scenarios stay deferred (`partial-apply-rules-oq5-option-a.md`). `specs/partial-apply.md` stays unaccepted. S4 stays unauthorized.
- OQ6 is recorded (#642). S3 still needs its own authorization (`oq6-revision-model-owner-decision.md`).
- `specs/lifecycle-intents.md` and `specs/conversions.md` propose intent `Restructure` and `priorAgreementId`. Both stay proposals.

### Options, recommendation, and default

Every row is Proposed.

| Fork | Options | Recommendation | Default |
|---|---|---|---|
| How many commercial changes | One `Restructure` lifecycle change; two quotes plus a manual credit; a new contract that cancels the old one as a separate flow | One `Restructure` intent. | One change. It is a new intent. It is not continuation `AMENDMENT` or `EXPANSION`, because those keep one source line and the accepted unit rate. |
| Agreement shape | New agreement with `priorAgreementId`; amend the source agreement in place; merge into an existing SaaS agreement | New agreement, `priorAgreementId` required. | `priorAgreementId` points at the source agreement. In-place amend is a different intent. Merge stays with DRG-554 (`specs/co-termination.md`), which is a proposal. |
| Lines inside the change | Terminate maintenance, surrender perpetual, add SaaS; add SaaS only; only the landed hybrid pair | The three lines, in one change. | Maintenance `Terminate`, perpetual `Surrender`, SaaS `Add` with absolute quantity. The landed hybrid pair stays a hybrid quote and is not rewritten into this intent. |
| Which credit policy | An unnamed credit; a ref to the DRG-573 policy; only `MigrationCreditPolicy` | `creditPolicyRef` points at the conversion policy. | A `Restructure` with no conversion-policy version is unresolved and is not priced. The 30% hybrid credit stays on the hybrid pair. |
| Apply shape | All-or-nothing across agreements; separate units under the adopted OQ5 first slice | Name three units (create SaaS, end maintenance, revoke entitlement), create before terminate, remedy by a correcting amendment. | The three units are named. They are not built. This default adds no S4 acceptance criterion. DRG-563's single-subscription ledger remains the gap. S3 and S4 stay unauthorized. |

### Owner decision needed

Confirm one `Restructure` change, a new agreement with `priorAgreementId`, the three line actions, and a required ref to the conversion policy. Confirm that the landed hybrid pair stays on its own path.

### Dependencies

| Dependency | Why it is named |
|---|---|
| DRG-573 | Supplies the credit policy this intent references. |
| DRG-574 | Supplies surrender and the entitlement end. |
| DRG-556 | Supplies the maintenance line `Terminate` ends. |
| DRG-548 / `specs/lifecycle-intents.md` | Proposed intent vocabulary. Unaccepted. This frame does not accept that file. |
| DRG-563 | Ledger cannot record one acceptance across the new SaaS subscription and the source subscription. |
| DRG-565 | OQ5 option A is adopted for the first slice. Broader restructure scenarios stay deferred. S4 stays unauthorized. |
| DRG-341 | Related credit and window. Not a substitute for this intent. |
| ADR-035 | Authorizes S1 and S2 only. |
| S3 / DRG-581 | #642 merged. S3 still requires separate owner authorization. This note does not start it. |
| S6 / DRG-584 | Unauthorized. |

### What it unblocks

An owner confirmation lets a later story treat `specs/conversions.md` scope item 1 as the intent to implement, still only after that spec is accepted on its own. Confirmation does not accept that spec. It does not open the apply path, and it does not start S3, S4, or S6.

## DRG-571 — maintenance pricing

**Linear:** [DRG-571](https://linear.app/drgamtd-workspace/issue/DRG-571) (RA-37). Backlog. Priority High. No comments. The ticket says DRG-556 covers linkage and does not cover pricing.

### Problem

Maintenance is sold as a percent of the related licence (the sanity check uses 20% of a $2,500 core net, which is $500 per core per year). Nothing in the pricing policy derives a line from a related line. A true-up that changes the licence quantity has to recompute maintenance. A renewal can uplift maintenance by a percent that differs from the SaaS uplift on another agreement in the same change (Scenario 4's illustration is 5% on $40,000 maintenance beside a 7% SaaS account uplift). Lapsed-maintenance reinstatement (back-maintenance for the gap, plus an optional fee) is also unmodeled.

### Settled on main

- No `PercentOfRelatedLine` mode in backend Java or SQL.
- Accepted uplift (`adr-renewal-uplift-basis-and-precedence.md`) D-01 defaults a renewal unit to `ContractedNet`. D-04 takes each field from the first rung that sets it: contract clause, then line, then agreement, then account, then the rules default. An absent uplift percent on an `UpliftPct` mode is `UNRESOLVED_UPLIFT`. The Accepted record leaves that price unresolved. Today's continuation code still substitutes zero when `upliftPercent` is null (`QuoteContinuationPolicy.java`). That zero is the gap the Accepted record names. This note does not copy the zero onto maintenance, and it does not amend D-01.
- Accepted proration is the day-stub for a partial billing period of the requested quantity (`adr-amendment-proration-formula.md`, D-01). The same record says Scenario 2's `16 × 500 × 92/365 = 2,016.44` matches the rejected full-term ratio only when the delta is an annual amount and the full term is 365 days. This note does not adopt `92/365` and does not reopen the formula.
- DRG-572 is the one-time charge with its own service period (back-maintenance and a reinstatement fee). It is outside this ticket.
- `specs/maintenance.md` proposes `MaintenancePolicy` and leaves the percent, the recalc default, and reinstatement open. It is a proposal.
- The DRG-609 design is unaccepted. This percent is not computed by that sketched route. ADR-017 does not authorize a new call to carry it.

### Options, recommendation, and default

Every row is Proposed. The forward-stub row is closed because proration is already Accepted.

| Fork | Options | Recommendation | Default |
|---|---|---|---|
| Basis | Percent of licence net; percent of licence list; a flat amount | Percent of the related licence net, with list as an explicit rung. | `LicenceNet`. The percent is required. A missing percent leaves the line unresolved. 20% is not filled in. |
| Recalc when the licence quantity or net changes | Yes; No | Yes, so a true-up does not leave maintenance on the old quantity. | `recalcOnLicenceChange = Yes`. A rung may set No, and then the stored annual amount is kept. |
| Uplift field | Reuse the Accepted renewal uplift percent; a separate maintenance uplift percent | A separate percent on the maintenance policy. SaaS lines in the same change still use the Accepted renewal precedence. | The maintenance line has its own `upliftPct`. If that percent is unset, the line is unresolved. The continuation zero is not applied. This default does not amend uplift D-01. |
| Precedence for the maintenance fields | The Accepted D-04 rung order; a maintenance-only stack | Use the D-04 order for these fields. | Clause, then line, then agreement, then account, then rules default. Unset fields fall through. The basis enum of D-01 stays the renewal-unit enum. |
| Reinstatement | Include lapse reinstatement in this decision; leave it off | Leave it off. The three knobs have no safe silent value. | Reinstatement is off. `backMaintenance`, `feePct`, and `maxLapseMonths` stay unset. A lapsed line is not auto-reinstated. A later yes still needs DRG-572 for the one-time charge. |
| Forward partial period | *(closed)* | The Accepted day-stub applies when the maintenance line is recurring and has a billing anchor. | This policy does not compute `92/365`. It calls the Accepted formula for that partial billing period. It adds no `Daily` option. |
| Quantity used when recalc is Yes | The linked perpetual quantity (DRG-556 default); a separately entered maintenance quantity | Follow the linked perpetual quantity. | The recomputed quantity equals the perpetual quantity. That depends on the DRG-556 equal-quantity default. |

### Owner decision needed

Confirm percent of licence net with a required percent, recalc default Yes, a separate uplift percent that stays unresolved when missing, D-04 rung order for those fields, and reinstatement off.

### Dependencies

| Dependency | Why it is named |
|---|---|
| DRG-556 | The related licence line and the equal-quantity default. |
| Accepted uplift record | Precedence and the unresolved-uplift rule. Not amended. |
| Accepted proration record | Forward stub. Not reopened. |
| DRG-539 | A list basis needs a price-book read. That note leaves DRG-609 undecided. |
| DRG-572 | One-time back-maintenance and a reinstatement fee, if the owner later turns reinstatement on. |
| `specs/capacity-meters.md` | Proposed true-up measurement. This policy prices the maintenance consequence of a quantity delta and does not choose the meter. |
| DRG-609, ADR-017 | Unaccepted. This note does not route the percent through them. |

### What it unblocks

An owner confirmation lets a later maintenance-pricing story use these defaults as its fixture rules, including Scenario 4's split between a maintenance percent and a SaaS uplift once the owner sets the two percents. Confirmation does not seed 20% or 5%. It does not start the true-up policy, DRG-572, the DRG-609 route, or S3.

## What this frame leaves untouched

- The Accepted proration formula and the Accepted renewal uplift record.
- ADR-017 and ADR-024 through ADR-027, which remain Proposed.
- S3 / DRG-581, which #642 did not start.
- S6 / DRG-584, which stays unauthorized.
- The DRG-609 design, which stays unaccepted.
- `specs/conversions.md`, `specs/maintenance.md`, `specs/lifecycle-intents.md`, `specs/catalog-model.md`, `specs/partial-apply.md`, and `renewal-policy-spec.md`, which stay proposals.
