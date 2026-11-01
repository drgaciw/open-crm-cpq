# Catalogue relationships and lifecycle eligibility: first-slice spec proposal

**Status:** Proposal only. This file does not accept a model, write an ADR, or authorize implementation, a flag change, or a deploy.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `catalog-model`. **Date:** 2026-10-16.

**Source.** [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576) (RA-42). Design inputs it names: `../../amendment-document-design-spike.md` §6 (product-service supplies allowed transitions) and `../../mid-term-amendment-policy-spec.md` (Upgrade, Downgrade, Swap, and version pinning).

**Conventions.** **[S]** is taken from DRG-576 or the code it cites. **[I]** is a design inference carried forward as a proposal. Relationship types and flag defaults are **Owner decisions**.

**Landed today [S DRG-576, checked in this tree].** `Product` has a unique `sku`, a `ProductType` (`ONPREM_PERPETUAL`, `ONPREM_CORE`, `ONPREM_SUBSCRIPTION`, `ONPREM_SUPPORT`, `SAAS_*`, support, services, add-ons, and others), and `ProductStatus` `DRAFT`, `ACTIVE`, `DEPRECATED`, `RETIRED`. Updates and retirements emit `PRODUCT_UPDATED` and `PRODUCT_RETIRED`. Packages under product-service are attribute, bundle, catalog, configrule, and option. There is no product-to-product relationship and no renewable, amendable, term-required, or co-termable flag. A product row is updated in place, so a relationship stored only on the live row would not be pinned to a catalogue version.

## Scope

### In this slice

1. An effective-dated `ProductRelationship` from one SKU to another, with a type, a quantity mapping, and a reference to a credit policy. Proposed types: `UPGRADE`, `DOWNGRADE`, `SWAP`, `SUCCESSOR`, `MAINTENANCE_FOR`, `ADD_ON_OF`. **[S DRG-576]**
2. Eligibility flags on the product (or on a versioned eligibility row, if the owner pins flags with the catalogue version): `renewable`, `amendableMidTerm`, `termRequired`, `coTermable`. **[S DRG-576]**
3. Relationships published with the catalogue version (DRG-421) so a change line pins `relationshipVersion` and still validates after the live row moves.
4. A read API that quote-service calls to ask whether a source SKU may take a given action toward a target SKU on a given date. The API returns the pinned relationship or a refusal. It does not price the line.
5. Validation hooks the mid-term policy can call for Upgrade, Downgrade, and Swap, and that renewal can call for a successor SKU.

### Out of this slice

Instance links on a sold line (`relatedPerpetualLineId` in `maintenance.md`, `convertedFromLineIds` in `conversions.md`). The conversion price and credit arithmetic (`conversions.md`). Contested mapping disputes and the hold they place on an amendment (DRG-599). Account, segment, and region eligibility (DRG-422). Reconfigure-from-hash (DRG-429). The action-code vocabulary (`lifecycle-intents.md`). This slice does not delete or relocate product-service `POST /validate`; that removal is a standing hold.

### Proposed contract

```text
ProductRelationship
  fromSku, toSku
  type                 { UPGRADE | DOWNGRADE | SWAP | SUCCESSOR | MAINTENANCE_FOR | ADD_ON_OF }
  quantityMapping      { ratio, rounding, minimum, version }   optional
  creditPolicyRef      optional reference, not a price
  effectiveFrom, effectiveTo
  catalogVersion
  status               { DRAFT | ACTIVE | RETIRED }

ProductEligibility
  sku
  renewable, amendableMidTerm, termRequired, coTermable
  catalogVersion
  effectiveFrom, effectiveTo

Read
  GET allowed-transition?fromSku&toSku&type&onDate&catalogVersion
  -> { allowed, relationshipVersion, quantityMapping, creditPolicyRef }
     or { allowed: false, reason }
```

A change line that upgrades, downgrades, swaps, succeeds, or attaches maintenance stores `fromSku`, `toSku`, `relationshipVersion`, and the catalogue version. Apply re-checks that pin. A live-row edit after pricing does not change the pin. **[S DRG-576; mid-term V9]**

`MAINTENANCE_FOR` points from the maintenance SKU to the perpetual licence SKU. It authorizes the instance link. It does not compute the percent. `SUCCESSOR` is the renewal substitution. `ADD_ON_OF` authorizes an add-on on a base SKU. Credit policy refs are opaque ids owned by the conversion or amendment policy.

Products stay mutable in place for commercial edits. The relationship and eligibility rows are the versioned facts. **[I]**

## Acceptance criteria

1. **No relationship, no transition.** Upgrade from SKU A to SKU B with no active relationship on the as-of date is rejected. The refusal names A, B, and the date.
2. **Typed relationship.** An active `UPGRADE` from A to B allows an Upgrade line and rejects a Downgrade line that uses that same row. A separate `DOWNGRADE` row is required for the reverse direction.
3. **Effective dating.** A relationship effective 2026-01-01 through 2026-06-30 allows a change dated 2026-04-01 and rejects a change dated 2026-07-01. The later change may use a successor row with a higher catalogue version.
4. **Pin survives a live edit.** A quote priced against relationship version v3 still applies against v3 after v4 is published. Apply fails when the line's pin is missing or does not match the priced digest.
5. **Successor at renewal.** A Renew line that substitutes SKU E for SKU A is accepted only when an active `SUCCESSOR` row from A to E covers the renewal start date. A Renew that keeps A does not need a successor row.
6. **Maintenance pairing.** A maintenance line for perpetual SKU F is accepted only when the maintenance SKU has an active `MAINTENANCE_FOR` row toward F. The percent and the instance id are checked by `maintenance.md`.
7. **Eligibility flags.** A SKU with `renewable = false` cannot appear on a Renew line. `amendableMidTerm = false` cannot appear on a mid-term Upgrade, Downgrade, Swap, or quantity Update. `termRequired = true` rejects a line with no term. `coTermable = false` rejects a line whose end date is moved to another line's end. Each refusal names the flag and the SKU.
8. **Retired product.** A `RETIRED` target SKU is rejected for a new relationship's `toSku`. An existing pin already on an executed document remains readable.
9. **Read API is a read.** The allowed-transition call does not write a product, emit `PRODUCT_UPDATED`, or return a price. Unknown SKUs return a refusal, not an empty success.
10. **Quantity mapping pin.** When the relationship carries a quantity mapping, the change line stores that mapping's version. Evaluating the ratio is the conversion policy's job (`conversions.md`); this slice only returns the mapping it stored.
11. **Add-on.** An Add line for an add-on SKU is accepted only with an active `ADD_ON_OF` row from the add-on to the base SKU already on the agreement. A missing base is a refusal.

## Dependencies

| Dependency | Why this slice needs it |
|---|---|
| DRG-421 | Catalogue versions. Without a version id, AC-4 cannot pin a relationship |
| DRG-552 / `lifecycle-intents.md` | Action codes the relationship type must match |
| `maintenance.md` (DRG-556) | Instance link that `MAINTENANCE_FOR` authorizes |
| `conversions.md` (DRG-573) | Consumer of `quantityMapping` and `creditPolicyRef`. Which store is authoritative is an owner decision in that file |
| DRG-539 | Continuation flow does not yet read the catalogue; this API is the read it would call |
| DRG-422, DRG-429 | Adjacent eligibility and reconfigure work. This slice does not implement them |
| DRG-599 | A contested mapping can hold an amendment. The hold workflow is out of this slice; the read API must be able to return a non-allowing reason so that hold has a place to land |
| Mid-term policy V9 | Product external id, price-book version, and configuration hash stay pinned at pricing and apply |

## Open questions

1. **Owner decision — relationship type set.** Upgrade, downgrade, swap, successor, maintenance-for, and add-on-of are the proposed set from DRG-576. The owner may drop add-on-of or split successor-at-renewal from upgrade. AC-2, AC-5, AC-6, and AC-11 wait on that set.
2. **Owner decision — flag defaults.** DRG-576 does not say whether a SKU is renewable or mid-term amendable when the flag row is missing. A missing row can mean allow or refuse. This proposal does not pick. AC-7 is written for an explicit row.
3. **Owner decision — where the flags live.** On the mutable `Product`, or on a versioned eligibility row beside the relationship. Only the versioned row satisfies the pinning sentence in DRG-576. Confirming that split is the owner's call because it adds a table the current product aggregate does not have.
4. **Owner decision — many relationships of one type.** Whether two active `UPGRADE` rows from A on the same date are allowed. If they are, the seller must choose, and a missing choice fails validation. If they are not, the write API rejects the second row.
5. **Owner decision — quantity mapping and credit ref.** Both are optional on the relationship. The conversion policy also carries a quantity map and a credit stack. One source of truth is required. See `conversions.md` open question 6. Until that choice, this slice stores the fields and does not evaluate them.
6. **Owner decision — same-SKU maintenance.** Some support SKUs attach to many licence SKUs. Whether `MAINTENANCE_FOR` is one row per pair or a typed group is unset.
7. **Owner decision — deprecated SKUs.** `DEPRECATED` is a landed status. Whether a deprecated SKU may be a renewal successor, a mid-term target, or neither is unset. AC-8 covers `RETIRED` only.
8. **Owner decision — who may publish a relationship.** Product-team ownership is DRG-599's subject. This slice assumes product-service is the writer and quote-service is a reader. The dispute and hold flow is not specified here.
9. **Owner decision — catalogue version gap.** DRG-421 is not landed as the version pin this API needs. If that work is still open, the owner says whether this slice is blocked or whether a relationship-level version is enough for the first slice.

## Lifecycle-intent eligibility (DRG-647)

**Status: Proposed.** **Owner decision: ___ (drg, time).** This section lists eligible product-relationship types for six lifecycle intents and the refusal each negative fixture expects. It does not accept the relationship type set in open question 1. It does not accept `conversions.md`. It does not authorize an API, a flag, a migration, or a deploy. Nothing in this section is adopted.

Checked against `main` `bf41c230f6309964a25ef5c2c12e84c41b0c37de`. The six intent names are the tokens Cancel, Restructure, Suspend, Resume, Transfer, and Convert in [lifecycle-intents.md at that main sha](https://github.com/drgaciw/open-crm-cpq/blob/bf41c230f6309964a25ef5c2c12e84c41b0c37de/docs/renewals-amendments/prereq/specs/lifecycle-intents.md). Date words (action date, contract effective, service activation, customer acceptance) and the date fixtures are in the Dates section of [lifecycle-intents.md at DRG-646 head `04fb230f3939b3eff79816f273462f6e97a25251`](https://github.com/drgaciw/open-crm-cpq/blob/04fb230f3939b3eff79816f273462f6e97a25251/docs/renewals-amendments/prereq/specs/lifecycle-intents.md) ([PR #702](https://github.com/drgaciw/open-crm-cpq/pull/702)). This file does not edit `lifecycle-intents.md`. A back-link from that file to this section is a later follow-up, after both DRG-646 and DRG-647 have merged.

The types considered are the six already named in the proposed contract above: `UPGRADE`, `DOWNGRADE`, `SWAP`, `SUCCESSOR`, `MAINTENANCE_FOR`, `ADD_ON_OF`. This section adds no seventh type.

### Fixture table

The fixtures are the expected values. The sentences under the table restate those values. Every row is **Status: Proposed**. **Owner decision: ___ (drg, time).**

| Fixture | Intent | Product (fromSku → toSku) | Relationship type presented | Eligible product-relationship types | Expected |
|---|---|---|---|---|---|
| `fixtures/catalog-model/cancel-ineligible.json` | Cancel | `SKU-CORE-A` → `SKU-SAAS-B`, product `ACTIVE` | `UPGRADE`, relationship `ACTIVE`, effective `2026-01-01` through `2026-12-31` | (none) | `allowed: false`. Product rejected. Error code `CANCEL_NOT_ELIGIBLE`. |
| `fixtures/catalog-model/restructure-ineligible.json` | Restructure | `SKU-PERP-F` → `SKU-SAAS-E`, product `ACTIVE` | `SUCCESSOR`, relationship `ACTIVE`, effective `2026-01-01` through `2026-12-31` | (none) | `allowed: false`. Product rejected. Error code `RESTRUCTURE_NOT_ELIGIBLE`. |
| `fixtures/catalog-model/suspend-ineligible.json` | Suspend | `SKU-CORE-A` → `SKU-CORE-C`, product `ACTIVE` | `DOWNGRADE`, relationship `ACTIVE`, effective `2026-01-01` through `2026-12-31` | (none) | `allowed: false`. Product rejected. Error code `SUSPEND_NOT_ELIGIBLE`. |
| `fixtures/catalog-model/resume-ineligible.json` | Resume | `SKU-CORE-A` → `SKU-CORE-D`, product `ACTIVE` | `SWAP`, relationship `ACTIVE`, effective `2026-01-01` through `2026-12-31` | (none) | `allowed: false`. Product rejected. Error code `RESUME_NOT_ELIGIBLE`. |
| `fixtures/catalog-model/transfer-ineligible.json` | Transfer | `SKU-ADDON-H` → `SKU-BASE-G`, product `ACTIVE` | `ADD_ON_OF`, relationship `ACTIVE`, effective `2026-01-01` through `2026-12-31` | (none) | `allowed: false`. Product rejected. Error code `TRANSFER_NOT_ELIGIBLE`. |
| `fixtures/catalog-model/convert-ineligible.json` | Convert | `SKU-MAINT-M` → `SKU-PERP-F`, product `ACTIVE` | `MAINTENANCE_FOR`, relationship `ACTIVE`, effective `2026-01-01` through `2026-12-31` | (none) | `allowed: false`. Product rejected. Error code `CONVERT_NOT_ELIGIBLE`. |

Each fixture sets `onDate` to `2026-10-05`. That date falls inside the relationship window, and the product status is `ACTIVE`, so the refusal is the intent rule in this section. The window is the effective-dating shape already in acceptance criterion 3. The `ACTIVE` product is the status shape already in acceptance criterion 8. Each refusal names the intent, the relationship type, `fromSku`, `toSku`, and `onDate`. The read writes no product and returns no price, which is the read already described in acceptance criterion 9.

`ADD_ON_OF` on the Transfer row points from the add-on SKU to the base SKU, which is the direction already stated for that type. `MAINTENANCE_FOR` on the Convert row points from the maintenance SKU to the perpetual SKU, which is the direction already stated for that type. The refusal is the intent eligibility, and the direction matches the type.

### Eligible types

**Status: Proposed.** **Owner decision: ___ (drg, time).**

| Intent | Eligible product-relationship types |
|---|---|
| Cancel | (none) |
| Restructure | (none) |
| Suspend | (none) |
| Resume | (none) |
| Transfer | (none) |
| Convert | (none) |

For each intent the eligible list is empty. `UPGRADE`, `DOWNGRADE`, `SWAP`, `SUCCESSOR`, `MAINTENANCE_FOR`, and `ADD_ON_OF` were considered. None of them is on an eligible list. A product presented with one of those types on one of these intents is rejected. The six fixtures are that rejection, one intent each.

Cancel, Suspend, Resume, and Transfer, as named on [DRG-548](https://linear.app/drgamtd-workspace/issue/DRG-548), keep the same SKU: end the line, pause it, resume it, or move the owner. A from-SKU to to-SKU relationship does not authorize those four intents. **[S DRG-548]**

Restructure, as named in `conversions.md`, is the agreement-level change that ends maintenance, surrenders the perpetual line, and adds the target subscription. That file says this catalogue is where an allowed source-to-target relationship would be declared, and it leaves the type unset (its open question 6, and open question 1 and open question 5 in this file). This section keeps the eligible list for Restructure empty until the owner names a type. The `SUCCESSOR` row in the fixture is rejected. **[S conversions.md]**

Convert, as distinguished in `conversions.md` open question 10, is the metric change that stays on the same licence model. None of the six relationship types is a metric-only type. This section keeps the eligible list for Convert empty and adds no type for it. The `MAINTENANCE_FOR` row in the fixture is rejected. **[S conversions.md]**

### Error codes

**Status: Proposed.** **Owner decision: ___ (drg, time).**

OQ-1, ruled by CPQ Decision 2026-10-27 (`decision-scores-2026-10-27.md`): reuse the existing `SCREAMING_SNAKE` style with no new prefix. The ruling gives `INTENT_PRODUCT_NOT_ELIGIBLE` and a per-intent code such as `CANCEL_NOT_ELIGIBLE` as the two shapes. It marks each code Proposed. The fixtures use the per-intent shape. The shared code is the alternative. The owner slot above is blank.

| Intent | Error code on the negative fixture | Status |
|---|---|---|
| Cancel | `CANCEL_NOT_ELIGIBLE` | Proposed |
| Restructure | `RESTRUCTURE_NOT_ELIGIBLE` | Proposed |
| Suspend | `SUSPEND_NOT_ELIGIBLE` | Proposed |
| Resume | `RESUME_NOT_ELIGIBLE` | Proposed |
| Transfer | `TRANSFER_NOT_ELIGIBLE` | Proposed |
| Convert | `CONVERT_NOT_ELIGIBLE` | Proposed |

Alternative, also **Status: Proposed**, and not the code any fixture expects: one shared code `INTENT_PRODUCT_NOT_ELIGIBLE` for all six intents. **Owner decision: ___ (drg, time).**

### Read

The existing read stays a read. The proposed query gains an `intent` parameter. **Status: Proposed.** **Owner decision: ___ (drg, time).**

```text
GET allowed-transition?fromSku&toSku&type&onDate&catalogVersion&intent
-> { allowed: false, errorCode, reason }
```

`onDate` is the caller's as-of date. These fixtures pass `2026-10-05` and do not fill it from `actionDate` or from a trigger date. Which trigger would fill `onDate` on a live read remains open question 5 of [lifecycle-intents.md at main `bf41c230f6309964a25ef5c2c12e84c41b0c37de`](https://github.com/drgaciw/open-crm-cpq/blob/bf41c230f6309964a25ef5c2c12e84c41b0c37de/docs/renewals-amendments/prereq/specs/lifecycle-intents.md). The comparison of a trigger with the action date is the Dates section at [DRG-646 head `04fb230f3939b3eff79816f273462f6e97a25251`](https://github.com/drgaciw/open-crm-cpq/blob/04fb230f3939b3eff79816f273462f6e97a25251/docs/renewals-amendments/prereq/specs/lifecycle-intents.md).

### Owner decisions in this section

1. **Owner decision — eligible types per intent.** Default: the eligible list is empty for Cancel, Restructure, Suspend, Resume, Transfer, and Convert. Alternative: the owner names a subset of `UPGRADE`, `DOWNGRADE`, `SWAP`, `SUCCESSOR`, `MAINTENANCE_FOR`, and `ADD_ON_OF` for an intent. **Status: Proposed.** **Owner decision: ___ (drg, time).**
2. **Owner decision — error-code shape.** Default: the six per-intent codes in the table above. Alternative: `INTENT_PRODUCT_NOT_ELIGIBLE`. Both are `SCREAMING_SNAKE` with no new prefix. **Status: Proposed.** **Owner decision: ___ (drg, time).**
3. **Owner decision — `intent` on the allowed-transition read.** Default: the read takes `intent` and returns `allowed: false` with the code and the reason when the presented type is outside the eligible list. **Status: Proposed.** **Owner decision: ___ (drg, time).**

No row in this section is adopted. The owner slot stays `___ (drg, time)`.
