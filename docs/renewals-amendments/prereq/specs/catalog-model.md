# Catalogue relationships and lifecycle eligibility: first-slice spec proposal

**Status:** Proposal only. This file does not accept a model, write an ADR, or authorize implementation, a flag change, or a deploy.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `catalog-model`. **Date:** 2026-10-16.

**Source.** [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576) (RA-42). Design inputs it names: `../amendment-document-design-spike.md` §6 (product-service supplies allowed transitions) and `../mid-term-amendment-policy-spec.md` (Upgrade, Downgrade, Swap, and version pinning).

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
