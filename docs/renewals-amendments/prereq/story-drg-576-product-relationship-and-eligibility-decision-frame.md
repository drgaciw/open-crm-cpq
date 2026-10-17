# DRG-576 — Product relationship and lifecycle eligibility (decision frame)

**Status:** Decisions adopted under owner delegation on 2026-10-17; effective on merge of the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md). Implementation remains separately gated.

**Current authority:** The linked record assigns a disposition to every fork in this frame and supplies overriding contracts. All proposal labels, owner-confirmation requests, defaults and open-question wording below are the historical pre-decision frame at `285ffef8e08274600090d8ba077a0c92f489c062`. They are retained as rationale/alternatives, not additional current owner gates. Evidence and implementation claims retain their original dates and limits.

## Historical decision frame


**Date:** 2026-10-17
**Base:** `main` at `0d71251d` (merge of [#647](https://github.com/drgaciw/open-crm-cpq/pull/647)). Ticket evidence was recorded at `dd90501`. `git diff --stat dd90501..0d71251d` for `backend/services/product-service/src/main/java/com/opencrm/product` is empty. The re-read below is of `0d71251d`.
**Project:** OPEN-CRM-CPQ, Linear team DRG, milestone Renewals & Amendments.
**Story:** [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576) (RA-42, High). Linear read 2026-10-17: Backlog. Title: catalog product relationship and lifecycle-eligibility model. Relations read the same day: DRG-590, DRG-607, DRG-561, DRG-599.

**This note authorizes no code, schema migration, event, flag, topic, gateway route, or deploy.** No flag is enabled. Nothing is deployed. Accepting this authorizes no code, migration or deploy. DRG-576 stays In Review until the owner merges.

The ticket's suggested next step (an effective-dated `ProductRelationship`, four flags, publication with catalog versions, and a read API) is an option in the forks below. It is not a decision.

[`specs/catalog-model.md`](specs/catalog-model.md) stays a proposal (`read` its status line). This note does not edit that file and does not accept it.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file, migration, issue, or pull-request text named in the same sentence was opened in this pass. |
| `unverified` | Not opened, not executed, or not queried. A search that found no row is a search, not a live database result. |

Nothing was executed. This pass did not start Maven, a database, Testcontainers, or an HTTP call. Whether Flyway has applied any migration in an environment is `unverified`.

## Standing constraints

| Constraint | Record on this `main` |
|---|---|
| ADR-017 and ADR-024 through ADR-027 stay Proposed and are not prerequisites | `read` status lines of [`ADR-017`](../../adr/ADR-017-synchronous-inter-service-communication.md), [`ADR-024`](../../adr/ADR-024-order-orchestration-context.md), [`ADR-025`](../../adr/ADR-025-saga-process-manager.md), [`ADR-026`](../../adr/ADR-026-command-messages.md), and [`ADR-027`](../../adr/ADR-027-compensation-and-point-of-no-return.md). Each opens **Status: Proposed on 2026-10-04. Not accepted.** This note does not set a timeout, a retry, a saga, a command topic, or a compensation path. |
| Proration stays in the Accepted ADR and is not reopened | `read` these two header lines of [`adr-amendment-proration-formula.md`](adr-amendment-proration-formula.md), quoted exactly. The formula is not restated. |

> **Status:** Accepted 2026-10-16 (owner approval in chat, 4:26 PM CT)
> **Number:** none. Do not assign one in this draft. Do not copy this file into `docs/adr/` and do not add a row to `docs/adr/README.md` until the owner accepts it and assigns a number.

| Constraint | Record on this `main` |
|---|---|
| OQ5 option A is unchanged | `read` OQ5 in [`renewals-amendments-open-questions-register.md`](../renewals-amendments-open-questions-register.md): adopted 2026-10-16, first slice only. [`partial-apply-owner-decision.md`](partial-apply-owner-decision.md) says S6 is not authorized. A contested mapping is not an OQ5 `BLOCKED` unit. |
| OQ7 thin contract is unchanged | `read` [`s7-v1-8-header-backfill.md`](../s7-v1-8-header-backfill.md): "One thin contract per existing subscription. No synthetic amendment history and no retroactive events. A later merge, tracked on DRG-554, is the place to regroup. The backfill does not regroup." |
| S3 and S6 are not authorized | S3 / DRG-581 needs its own authorization. S6 / DRG-584 is not authorized (`read` the partial-apply owner decision). This note starts neither. |
| OQ12 action cut is unchanged | `read` the register: Add, Update, Remove only. Swap, Upgrade, and Downgrade wait for RA-42 / DRG-576. This note does not make those three executable. |
| DRG-609 design is not accepted | `read` [`story-drg-609-pricing-service-continuation-design.md`](story-drg-609-pricing-service-continuation-design.md): a design proposal, not an ADR. Any conversion price, credit amount, or priced mapping is pending that design. |
| ADR-008 | `read` the catalog row in [`docs/adr/README.md`](../../adr/README.md): Accepted. Integration is HTTP or Kafka. This note does not add a cross-schema read. |

## Overlap, marked explicitly

### DRG-599 and [#650](https://github.com/drgaciw/open-crm-cpq/pull/650) (Cluster A)

`read` the open draft file `docs/renewals-amendments/prereq/cluster-a-amendment-modes-and-policy-decision-frame.md` on branch `cursor/cluster-a-amendment-policy-frame-d86b` (PR #650, draft, base `main`, one file). That file is not on this `main`.

**SKU mapping is business policy and an owner option.** #650 does not adopt a mapping table, a dispute flow, or a hold. This note does not adopt them either. It does not restate forks 599-F1 through 599-F5 as decisions, and it does not contradict them.

| #650 fork (unadopted) | What #650's default says | What this note does |
|---|---|---|
| 599-F1 Where the mapping lives | One effective-dated catalog relationship, dispute status on that version. Alternatives: a separate table; both a catalog row and a commercial overlay. | The relationship vocabulary below is one possible home. It does not close 599-F1. A separate table and a commercial overlay stay open. |
| 599-F2 Dispute status and resolver | `Active`, `Contested`, `Resolved`; product resolves. | Not repeated. No dispute status is added here. |
| 599-F3 Where the hold bites | Whole document, before `IN_REVIEW` and again before apply. | Not repeated. This note adds no hold and does not change OQ5. |
| 599-F4 Which lines need a mapping | Only when the source SKU and the target SKU differ. | 576-F3 points the same way for `SUCCESSOR` and does not adopt 599-F4. |
| 599-F5 Pin versus live row | Pin the relationship version at pricing. Alternative: read the live row at apply. | 576-F10 points the same way and does not adopt 599-F5. The live-row read stays an alternative. |

#650 also says conversion price on the relationship is pending the DRG-609 design (not accepted). This note says the same and does not price a mapping.

### DRG-539

`read` [`story-drg-539-pricebook-read-for-list-bases.md`](story-drg-539-pricebook-read-for-list-bases.md) on this `main` (merged in [#645](https://github.com/drgaciw/open-crm-cpq/pull/645)). That note is the price-book read for `CurrentList`, `ListLessContractedDiscount`, and `notAboveList`. Its recommendation section says product substitution and `ReconfigureFrom` stay on DRG-539 and are not designed in that file. This note does not redesign that read and does not design `ReconfigureFrom`. A successor SKU, once chosen, still needs that read for a list basis. The continuation price of the substitution is pending the DRG-609 design (not accepted).

### [#652](https://github.com/drgaciw/open-crm-cpq/pull/652) (Cluster B, DRG-556)

`read` the open draft file `docs/renewals-amendments/prereq/cluster-b-onprem-to-saas-and-maintenance-decision-frame.md` on branch `cursor/cluster-b-onprem-saas-maintenance-d0ee` (PR #652, draft, base `main`). That file is not on this `main`.

#652's DRG-556 default (unadopted) stores `relatedPerpetualLineId` on the sold line when the licence model is `Maintenance`, and it leaves [`specs/catalog-model.md`](specs/catalog-model.md) a proposal. Its alternatives are a catalogue `MAINTENANCE_FOR` row alone, or both the instance id and `MAINTENANCE_FOR`. This note does not replace the instance id and does not require `MAINTENANCE_FOR` before that id.

#652's quantity-map default (unadopted) stores a map version pin on the priced line and leaves the line unresolved when the pin is absent. Rounding (`Nearest`, minimum 1) is that note's fork. This note does not restate that rounding and does not confirm the catalogue as the source of truth.

Maintenance percent and surrender price in #652 are pending the DRG-609 design (not accepted). This note does not compute them.

### [#651](https://github.com/drgaciw/open-crm-cpq/pull/651) (Cluster E, DRG-552)

`read` the open draft file `docs/renewals-amendments/prereq/cluster-e-lifecyclechange-model-decision-frame.md` on branch `cursor/cluster-e-lifecyclechange-model-3473` (PR #651, draft, base `main`), at the DRG-552 section and the OQ12 row. That file is not on this `main`.

Cluster E owns the action vocabulary. This note does not add `SUCCESSOR`, `MAINTENANCE_FOR`, or `ADD_ON_OF` to `chk_amendment_line_action`. `read` that check in [`V1_7__contract_documents.sql`](../../../backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql): the allowed tokens include `UPGRADE`, `DOWNGRADE`, and `SWAP`. OQ12 says those three wait for DRG-576. The check is not a relationship table. #651's DRG-552 default leaves a swap as action `SWAP` for DRG-576. This note does not change that token and does not make it executable.

### Named beside this story, not duplicated

| Item | Relation |
|---|---|
| [DRG-421](https://linear.app/drgamtd-workspace/issue/DRG-421) | Linear read 2026-10-17: Backlog. Publish catalog versions. Not landed as a version pin in the product migrations listed below. 576-F10 does not block on it and does not claim it is done. |
| [DRG-422](https://linear.app/drgamtd-workspace/issue/DRG-422) | Linear read 2026-10-17: Backlog. Eligibility by account, segment, and region. That is not `renewable`, `amendableMidTerm`, `termRequired`, or `coTermable`. |
| [DRG-429](https://linear.app/drgamtd-workspace/issue/DRG-429) | Linear read 2026-10-17: Backlog. Reconfigure from a hash. Not a product-to-product type. DRG-539's merged note also leaves `ReconfigureFrom` undesigned. |
| [DRG-423](https://linear.app/drgamtd-workspace/issue/DRG-423) | Linear read 2026-10-17: Backlog. Catalog serving API, the named Open Host Service story. Used only as the sketch in 576-F11. |
| DRG-590 | Parked rules review. Related on Linear. Conditional rules are not specified here. Body not re-opened (`unverified`). |

## Owner-decision table

Every row is Proposed, unaccepted, owner decision required. The default is the recommendation. The alternatives are the other options in that fork.

| Fork | Default | Alternatives |
|---|---|---|
| 576-F1 Relationship type set | The six ticket types: `UPGRADE`, `DOWNGRADE`, `SWAP`, `SUCCESSOR`, `MAINTENANCE_FOR`, `ADD_ON_OF` | Drop `ADD_ON_OF`; fold `SUCCESSOR` into `UPGRADE` |
| 576-F2 `MAINTENANCE_FOR` | In the vocabulary. Direction is maintenance SKU to perpetual SKU. It does not replace `relatedPerpetualLineId` and it is not a gate | `MAINTENANCE_FOR` alone, no instance id; both required before the instance id is accepted |
| 576-F3 `SUCCESSOR` | Required only when the renewal target SKU differs. Same-SKU renew needs no row. Does not adopt 599-F4 | Every renew needs a row, including same SKU; use `UPGRADE` at renewal and drop `SUCCESSOR` |
| 576-F4 `ADD_ON_OF` and first-slice Add | Does not gate OQ12 Add, Update, or Remove | An add-on Add requires the row in the first slice |
| 576-F5 How many targets | Many `toSku` values for one `fromSku` and type. The line names `toSku` | At most one active target per `fromSku`, type, and date |
| 576-F6 Endpoint status | New `toSku` may be `ACTIVE`, or `DEPRECATED` only for `SUCCESSOR`. `RETIRED` is refused as a new `toSku`. An existing pin stays readable | Any non-retired status for every type; only `ACTIVE` at both ends |
| 576-F7 Where the flags live | A versioned eligibility row, not columns on the mutable product | Four columns on `products`; both a live column and a versioned row |
| 576-F8 Missing flags | Missing `renewable` or `amendableMidTerm` is unresolved, not an allow. `termRequired` and `coTermable` apply only when the row says true | Missing means allow all four; missing means refuse all four |
| 576-F9 Which lines read the flags | First-slice Add, Update, and Remove do not read them. `amendableMidTerm` is for Upgrade, Downgrade, and Swap once those are in scope | The flags refuse a first-slice quantity Update; the flags never refuse |
| 576-F10 What the line pins | A relationship-row version, and the eligibility-row version when a flag was read. `products.version` is not that pin. A catalog version id is filled when DRG-421 supplies one. Does not adopt 599-F5 | Block until DRG-421; pin `products.version`; read the live row at apply |
| 576-F11 Read API | A later read-only product-service query. Sketch only, with the Open Host Service block. No route is added | Extend `GET /api/v1/products/sku/{sku}`; quote-service reads the product schema; no read until DRG-423 is accepted |
| 576-F12 Events | No new event. Options only beside this default | A new outbox event; extend `PRODUCT_UPDATED` |
| 576-F13 `quantityMapping` and `creditPolicyRef` | Both optional and opaque. No ratio is evaluated and no price is returned. Price is pending the DRG-609 design (not accepted) | Both required; product-service evaluates the ratio |

## Already on main

`read` unless a sentence says otherwise.

### Product row

[`Product.java`](../../../backend/services/product-service/src/main/java/com/opencrm/product/catalog/Product.java): `sku` is `nullable = false`, `unique = true`, length 100. Fields read on the entity: `name`, `description`, `productType`, `category`, `status` (builder default `DRAFT`), `listPrice`, `currency`, `billingFrequency`, `unitOfMeasure`, `configurable`, `bundle`, `metadata`, `retiredAt`, `retirementReason`. No relationship collection. No `renewable`, `amendableMidTerm`, `termRequired`, or `coTermable` field.

[`V1__create_product_schema.sql`](../../../backend/services/product-service/src/main/resources/db/migration/V1__create_product_schema.sql): `products.sku VARCHAR(100) NOT NULL UNIQUE`. `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`. The Java builder default is `DRAFT`. The SQL default is `ACTIVE`. `version BIGINT NOT NULL DEFAULT 0`.

[`BaseEntity.java`](../../../backend/libs/common/src/main/java/com/opencrm/common/entity/BaseEntity.java): `version` is `@Version`. That is an optimistic lock. It is not a catalog generation.

[`V1_4__add_product_retirement_fields.sql`](../../../backend/services/product-service/src/main/resources/db/migration/V1_4__add_product_retirement_fields.sql) adds `retired_at` and `retirement_reason` only.

A search of `backend/services/product-service` for `ProductRelationship`, `product_relationship`, `renewable`, `amendableMidTerm`, `termRequired`, and `coTermable` returned no matches. A live database catalog was not queried (`unverified`).

### Type and status

[`ProductType.java`](../../../backend/services/product-service/src/main/java/com/opencrm/product/catalog/ProductType.java) values read: `SAAS_SEAT_NAMED`, `SAAS_SEAT_CONCURRENT`, `SAAS_USAGE_METERED`, `SAAS_FLAT_RATE`, `ONPREM_PERPETUAL`, `ONPREM_CORE`, `ONPREM_SUBSCRIPTION`, `ONPREM_SUPPORT`, `HYBRID_PORTABLE`, `HYBRID_MIGRATION_RAMP`, `SUPPORT_BASIC`, `SUPPORT_PREMIUM`, `SUPPORT_ENTERPRISE`, `PROFESSIONAL_SERVICES`, `TRAINING`, `ADD_ON_API`, `ADD_ON_STORAGE`, `ADD_ON_ANALYTICS`, `ADD_ON_SECURITY`, `ADD_ON_INTEGRATION`, `HARDWARE`, `CUSTOM`.

[`V1_8__allow_canonical_ramp_product_types.sql`](../../../backend/services/product-service/src/main/resources/db/migration/V1_8__allow_canonical_ramp_product_types.sql) adds `ONPREM_SUPPORT` and `HYBRID_MIGRATION_RAMP` to `chk_products_product_type`. V1's check, before that migration, does not list those two (`read` V1).

[`ProductStatus.java`](../../../backend/services/product-service/src/main/java/com/opencrm/product/catalog/ProductStatus.java): `DRAFT`, `ACTIVE`, `DEPRECATED`, `RETIRED`.

Entity methods on `Product`: `activate` allows `DRAFT` only; `deprecate` allows `ACTIVE` only; `retire` allows `ACTIVE` or `DEPRECATED`, and sets `retiredAt` and `retirementReason`.

[`ProductService.java`](../../../backend/services/product-service/src/main/java/com/opencrm/product/catalog/ProductService.java) does not call `product.activate()`. `activate` refuses only `RETIRED`, then sets `ACTIVE` and publishes `PRODUCT_UPDATED` with change type `PUBLISHED`. `deprecate` requires `ACTIVE`, sets `DEPRECATED`, and publishes no event. `retire` calls `product.retire(reason)` and publishes `PRODUCT_RETIRED`. `update` loads the existing row, calls `productMapper.updateEntity`, saves, and publishes `PRODUCT_UPDATED` with change type `UPDATED`. The product is updated in place. `ProductMapper.updateEntity` was not opened (`unverified` which request fields it copies).

[`ProductUpdateRequest.java`](../../../backend/services/product-service/src/main/java/com/opencrm/product/catalog/ProductUpdateRequest.java) has no `sku` field. It does have `status`, `productType`, and `listPrice`.

### Events that exist

[`EventContractRegistry.java`](../../../backend/libs/common/src/main/java/com/opencrm/common/event/EventContractRegistry.java): `PRODUCT_UPDATED_V1` is published, name `ProductUpdated`, owner `product-service`, topic `KafkaTopics.PRODUCT_UPDATED`, payload `ProductCatalogLifecycleEvent`. `PRODUCT_RETIRED_V1` is published, name `ProductRetired`, topic `KafkaTopics.PRODUCT_STATUS_RETIRED`, payload `ProductRetiredEvent`. `BUNDLE_STRUCTURE_CHANGED_V1` is a separate published contract. No relationship contract is in the `ALL` list that was read.

[`KafkaTopics.java`](../../../backend/libs/common/src/main/java/com/opencrm/common/event/KafkaTopics.java): `PRODUCT_UPDATED` is `cpq.product.updated`. `PRODUCT_STATUS_RETIRED` is `cpq.product.status.retired`.

`publishProductUpdated` passes product id, change type, and `Instant.now()`. It does not pass a SKU pair or a relationship version (`read` `ProductService.publishProductUpdated`).

### Packages and other product links

Directories under `com.opencrm.product` on this `main`, and the same directories in `git ls-tree` at `dd90501`: `attribute`, `bundle`, `catalog`, `config`, `configrule`, `option`. The ticket text lists five and does not mention `config`. `config` is present. There is no relationship package.

[`BundleItem.java`](../../../backend/services/product-service/src/main/java/com/opencrm/product/bundle/BundleItem.java): a bundle line has `bundle`, `productId`, `quantity`, `optional`, `sortOrder`. That is composition. It is not an upgrade, downgrade, swap, successor, or maintenance type.

[`ProductOption.java`](../../../backend/services/product-service/src/main/java/com/opencrm/product/option/ProductOption.java): an option may carry `productId` and `priceAdjustment`. That is an option price. It is not a lifecycle relationship. The rest of the option file was not required for this claim.

[`ConfigRuleType.java`](../../../backend/services/product-service/src/main/java/com/opencrm/product/configrule/ConfigRuleType.java): `REQUIRES`, `EXCLUDES`, `RECOMMENDS`, `DEFAULT`, `VALIDATES`. None of those is a lifecycle type.

Product-service migrations on this tree: `V1__create_product_schema.sql`, `V1_3__create_outbox_events.sql`, `V1_4__add_product_retirement_fields.sql`, `V1_5__outbox_relay_status_and_leasing.sql`, `V1_6__outbox_event_envelope_columns.sql`, `V1_7__outbox_status_skipped.sql`, `V1_8__allow_canonical_ramp_product_types.sql`. `V1_3`, `V1_5`, `V1_6`, and `V1_7` were not opened (`unverified` beyond the file names). No migration name in that list creates a relationship table.

### HTTP that exists

[`ProductController.java`](../../../backend/services/product-service/src/main/java/com/opencrm/product/catalog/ProductController.java): class mapping `/api/v1/products`. Methods read: `POST`, `GET`, `GET /search`, `GET /{id}`, `GET /sku/{sku}`, `PUT /{id}`, `POST /{id}/activate`, `POST /{id}/deprecate`, `POST /{id}/retire`, `DELETE /{id}`. No allowed-transition route.

[`ProductDto.java`](../../../backend/services/product-service/src/main/java/com/opencrm/product/catalog/ProductDto.java) fields include `sku`, `productType`, `status`, `listPrice`, and `version`. They do not include a relationship or the four flags. `retiredAt` is on the entity and is not on this DTO.

A search of `backend/services/quote-service` for `ProductClient` and `/api/v1/products` returned no matches. quote-service has no product client in that search.

A search of [`ADR-017`](../../adr/ADR-017-synchronous-inter-service-communication.md) for `product-service` returned no matches. The edge table was not re-read row by row (`unverified` that some other wording names this edge). ADR-017 stays Proposed and is not a prerequisite.

### Policy text this pin has to meet later

`read` [`mid-term-amendment-policy-spec.md`](../mid-term-amendment-policy-spec.md) V9: product external id, price-book version, and configuration hash are pinned at pricing and unchanged at apply. `read` [`amendment-document-design-spike.md`](../amendment-document-design-spike.md) at the product-service sentence: product-service supplies allowed transitions. The same spike sentence says the document pins a product relationship version. Those sentences are inputs. They are not an accepted relationship model.

`read` the first-slice cut in the same mid-term spec: Add, Update, Remove only. Swap, Upgrade, and Downgrade wait for RA-42 / DRG-576.

## Forks

### 576-F1. Relationship type set

| Option | Meaning |
|---|---|
| A. Six types | `UPGRADE`, `DOWNGRADE`, `SWAP`, `SUCCESSOR`, `MAINTENANCE_FOR`, `ADD_ON_OF`, as the ticket suggests. |
| B. Drop `ADD_ON_OF` | Bundles and options stay the only add-on structure. |
| C. Fold `SUCCESSOR` into `UPGRADE` | One type covers mid-term upgrade and renewal substitution. |

**Recommendation:** A. Mid-term upgrade and renewal substitution are different moments. Maintenance pairing is not an upgrade. An add-on attachment is not a swap. Dropping or folding a type is still an owner option.

**Default:** Proposed, unaccepted, owner decision required. The six ticket types (option A). Alternatives written out: drop `ADD_ON_OF` (option B); fold `SUCCESSOR` into `UPGRADE` (option C). This default does not adopt #650 and does not accept [`specs/catalog-model.md`](specs/catalog-model.md).

### 576-F2. `MAINTENANCE_FOR`

| Option | Meaning |
|---|---|
| A. Vocabulary only | The row, when present, says maintenance SKU M is the maintenance SKU for perpetual SKU F (`fromSku` M, `toSku` F). The sold line still uses the instance id from #652. |
| B. Catalogue row alone | No `relatedPerpetualLineId`. |
| C. Both required first | The catalogue row is required before the instance id is accepted. |

**Recommendation:** A. Option B and option C are #652's alternatives. #652's unadopted default keeps the instance id and leaves the catalogue row a proposal. Picking B or C here would contradict that default.

**Default:** Proposed, unaccepted, owner decision required. `MAINTENANCE_FOR` is in the vocabulary and points from the maintenance SKU to the perpetual licence SKU (option A). It does not replace `relatedPerpetualLineId`, it does not compute a maintenance percent, and it is not a gate on the DRG-556 line. Alternatives written out: the catalogue row alone, with no instance id (option B); both required before the instance id is accepted (option C). Percent and surrender price stay pending the DRG-609 design (not accepted). #652 is not edited.

### 576-F3. `SUCCESSOR`

| Option | Meaning |
|---|---|
| A. Only when the SKU changes | A renew that keeps the source SKU needs no row. A renew that substitutes SKU E needs an effective `SUCCESSOR` from the source to E. |
| B. Every renew | Including a same-SKU renew. |
| C. No `SUCCESSOR` type | Renewal substitution uses `UPGRADE`. |

**Recommendation:** A. This is the same direction as unadopted 599-F4 (a mapping only when the SKUs differ). It does not adopt 599-F4.

**Default:** Proposed, unaccepted, owner decision required. `SUCCESSOR` is required only when the renewal target SKU differs from the source SKU (option A). Alternatives written out: every renew needs a row, including a same-SKU renew (option B); renewal substitution uses `UPGRADE` and there is no `SUCCESSOR` type (option C). The list unit of a chosen target SKU stays the DRG-539 read. This note does not redesign that read. The continuation price is pending the DRG-609 design (not accepted). 599-F4 is not adopted.

### 576-F4. `ADD_ON_OF` and the first slice

| Option | Meaning |
|---|---|
| A. Not a first-slice gate | OQ12 Add, Update, and Remove do not read `ADD_ON_OF`. |
| B. Gate add-on Adds now | An Add of an add-on product type requires an active row. |

**Recommendation:** A. OQ12 is confirmed. A new gate on Add would change the first slice.

**Default:** Proposed, unaccepted, owner decision required. `ADD_ON_OF` does not gate OQ12 Add, Update, or Remove (option A). Alternatives written out: an Add of an add-on type requires the row in the first slice (option B). Bundles and options stay as they are on `main`.

### 576-F5. How many targets

| Option | Meaning |
|---|---|
| A. Many targets | More than one active `UPGRADE` from A on the same date is allowed. The change line names `toSku`. A line that names a type and no `toSku` is refused. |
| B. One target | A second active row for the same `fromSku`, type, and date is rejected. |

**Recommendation:** A. SKU A can upgrade to B or to C. The seller's line is the choice.

**Default:** Proposed, unaccepted, owner decision required. Many `toSku` values are allowed, and the line names `toSku` (option A). Alternatives written out: at most one active target per `fromSku`, type, and date (option B).

### 576-F6. Endpoint status

| Option | Meaning |
|---|---|
| A. `ACTIVE`, plus `DEPRECATED` only as a `SUCCESSOR` target | `RETIRED` is refused as a new `toSku`. A pin already stored on a document stays readable. `fromSku` may be `ACTIVE` or `DEPRECATED`. |
| B. Any non-retired status for every type | `DEPRECATED` may be the target of an upgrade, a swap, or a maintenance pair. |
| C. `ACTIVE` only | A `DEPRECATED` SKU cannot be a new endpoint. |

**Recommendation:** A. Deprecation is the moment a successor is named. It is not a reason to open a new mid-term upgrade onto that SKU.

**Default:** Proposed, unaccepted, owner decision required. A new `toSku` may be `ACTIVE`, or `DEPRECATED` only when the type is `SUCCESSOR`; `RETIRED` is refused as a new `toSku`; an existing pin stays readable (option A). Alternatives written out: any non-retired status for every type (option B); only `ACTIVE` at both ends (option C). `ProductService.activate` can set `ACTIVE` from a non-retired status without calling `Product.activate` (`read`). This default does not change that method.

### 576-F7. Where the flags live

The four flags are `renewable`, `amendableMidTerm`, `termRequired`, and `coTermable`.

| Option | Meaning |
|---|---|
| A. Versioned eligibility row | Its own effective dates and its own version. The product row stays mutable. |
| B. Columns on `products` | A later in-place update changes the flag the next reader sees. |
| C. Both | Live columns plus a versioned row. The line pins the row. |

**Recommendation:** A. The product is updated in place, so a flag stored only on that row is not a pin.

**Default:** Proposed, unaccepted, owner decision required. The four flags live on a versioned eligibility row (option A). Alternatives written out: four columns on `products` (option B); both a live column and a versioned row (option C). DRG-422's account, segment, and region rules are not these flags.

### 576-F8. Missing flags

| Option | Meaning |
|---|---|
| A. Split | A missing row leaves `renewable` and `amendableMidTerm` unresolved. It does not allow the action. `termRequired` and `coTermable` are consulted only when the row says true. A missing row does not invent a term requirement or a co-term refusal. |
| B. Missing means allow | All four default to permissive. |
| C. Missing means refuse | All four refuse when the row is absent. |

**Recommendation:** A. An allow default would treat every SKU as renewable and mid-term amendable. A refuse-all default would reject a line for lacking a term or a co-term flag the catalogue never set.

**Default:** Proposed, unaccepted, owner decision required. Missing `renewable` or `amendableMidTerm` is unresolved, not an allow; `termRequired` and `coTermable` apply only when the row says true (option A). Alternatives written out: a missing row allows all four (option B); a missing row refuses all four (option C). This default is not a check on the first slice. 576-F9 says which lines read the flags. No code reads them from this note.

### 576-F9. Which lines read the flags

| Option | Meaning |
|---|---|
| A. Later actions only | First-slice Add, Update, and Remove do not read the flags. `amendableMidTerm` is read for Upgrade, Downgrade, and Swap when those actions are in scope. `renewable` is read on a Renew line. `coTermable` does not choose a co-term mode. |
| B. First slice too | A false or missing `amendableMidTerm` refuses a quantity Update now. |
| C. Never refuse | The flags are labels. Validation ignores them. |

**Recommendation:** A. OQ12 is confirmed. Cluster A (#650) owns co-term modes (554-F1 through 554-F8) and this note does not re-decide them.

**Default:** Proposed, unaccepted, owner decision required. First-slice Add, Update, and Remove do not read the flags; `amendableMidTerm` applies to Upgrade, Downgrade, and Swap once those are in scope; `renewable` applies to a Renew line; `coTermable` does not choose a co-term mode (option A). Alternatives written out: the flags refuse a first-slice quantity Update (option B); the flags never refuse (option C). #650's co-term forks stay the owner options they are. OQ12 is unchanged.

### 576-F10. What the line pins

| Option | Meaning |
|---|---|
| A. Relationship version now, catalog version when DRG-421 exists | The change line stores the relationship-row version. When a flag was read, it also stores the eligibility-row version. `products.version` is not the pin. A catalog version id is recorded on the row when DRG-421 supplies one. Until then the catalog version id is empty and the relationship version is the pin. |
| B. Block on DRG-421 | No pin until a catalog version exists. |
| C. Pin `products.version` | The optimistic lock on the product row. |
| D. Live row at apply | Apply reads whatever the catalogue says then. |

**Recommendation:** A. DRG-421 is Backlog and is not a version column in the product migrations listed above. Waiting for it leaves Upgrade, Downgrade, and Swap with no pin. Using `products.version` pins an in-place commercial edit. Option D is unadopted 599-F5's alternative. This recommendation matches the direction of unadopted 599-F5 option A (pin at pricing) and does not adopt it. The dispute hold in 599-F3 is not added.

**Default:** Proposed, unaccepted, owner decision required. The line pins the relationship-row version, and the eligibility-row version when a flag was read; `products.version` is not that pin; a catalog version id is filled when DRG-421 supplies one (option A). Alternatives written out: block until DRG-421 (option B); pin `products.version` (option C); read the live row at apply (option D). 599-F5 is not adopted. V9's product, price-book, and configuration pins are unchanged. This note adds no column to `V1_7__contract_documents.sql`.

### 576-F11. Read API

| Option | Meaning |
|---|---|
| A. Later read-only query | product-service would answer whether `fromSku` may take `type` toward `toSku` on a date, at an optional pinned version. quote-service would call it. The sketch below is not a route. |
| B. Extend `GET /api/v1/products/sku/{sku}` | The existing product DTO gains relationships. That changes a surface that returns the mutable product. |
| C. quote-service reads `product` tables | A second reader of the product schema. |
| D. No read until DRG-423 is accepted | The catalog serving API stays the only place a read may be added. |

**Recommendation:** A, as a sketch. Option C conflicts with Accepted ADR-008. Option B mixes the mutable product with the versioned row. Option D leaves quote-service with no read while DRG-423 stays Backlog. ADR-017 is Proposed and is not a prerequisite, so this sketch sets no timeout.

**Default:** Proposed, unaccepted, owner decision required. A later read-only query on product-service, sketch only (option A). Alternatives written out: extend `GET /api/v1/products/sku/{sku}` (option B); quote-service reads the product schema (option C); no read until DRG-423 is accepted (option D). No route, client, or gateway entry is added. The sketch returns no price.

#### Open Host Service block (sketch only)

This block is a sketch of what a later implementation would have to satisfy. It is not an endpoint. It authorizes no route, no client, and no deploy.

DoD v0.2 addition 1, fetched 2026-10-17 from [CPQ Definition of Done v0.2](https://app.notion.com/p/3fbf7cb4e4df8156a5bfe5c6c61d4338) (approved by the owner 2026-10-16 4:30 PM CT): "Open Host Service: any new or changed integration surface follows the Open Host Service contract." The page does not define that contract's fields. `verification.state` on the fetch was `unverified`.

[DRG-423](https://linear.app/drgamtd-workspace/issue/DRG-423) (BC-01-06), Linear read 2026-10-17, Backlog, is the named catalog serving API. Its acceptance criteria, from that issue: responses are DTOs, not persistence models; ETag or version headers enable client caching; p95 latency under 100 ms for GetProduct. The issue says delivery acceptance has not been reverified against the current implementation. This sketch does not claim those criteria are met, and it does not implement GetProduct, ListSellableProducts, or ResolveBundle.

| Sketch item | What a later implementation would show | This note |
|---|---|---|
| Open Host Service | A new read is an integration surface. The response is a DTO. ADR-006, Accepted, says entities are not the API body (`read` the catalog row). An ETag or version header would carry the relationship version from 576-F10, not `products.version`. | Not built. |
| Read only | No product write, no `PRODUCT_UPDATED`, no `PRODUCT_RETIRED`, no price, no credit amount. | Not built. |
| No shared database | ADR-008 is Accepted. quote-service does not read the `product` schema. | Not built. |
| Schema-registry | No new event in the default. See 576-F12. | N/A for this sketch. |
| Outbox | A read publishes nothing. | N/A for this sketch. |
| Commercial-decision audit | A read is not a commercial decision. | N/A for this sketch. |
| Event replay | No event is produced by the read. | N/A for this sketch. |
| ADR-017 | Proposed. Not a prerequisite. No timeout is set. | Not applied. |

Path sketch, not a route: `GET /internal/v1/catalog/allowed-transitions` with `fromSku`, `toSku`, `type`, `onDate`, and an optional `relationshipVersion`. A hit returns `allowed`, the relationship version, and the opaque `quantityMapping` and `creditPolicyRef` when those fields are present. A miss returns `allowed: false` and a reason. Unknown SKUs are a refusal. The call does not write.

### 576-F12. Events

Any new event is an option only. This fork does not register one.

| Option | Meaning |
|---|---|
| A. No new event | Readers use the read in 576-F11 when that read exists. `PRODUCT_UPDATED` and `PRODUCT_RETIRED` stay as registered. |
| B. New outbox event | A relationship publication would be a new contract, written through the outbox, registered, and replay-tested. |
| C. Extend `PRODUCT_UPDATED` | The current payload is product id, change type, and an instant (`read`). Adding a SKU pair would change that contract. |

**Recommendation:** A. Options B and C are event changes. They are not chosen.

**Default:** Proposed, unaccepted, owner decision required. No new event (option A). Alternatives written out, as options only: a new outbox event for relationship publication (option B); extend `PRODUCT_UPDATED` (option C). Neither alternative is authorized. No schema-registry edit, no outbox write, and no replay test is added. S6 is not authorized to carry either alternative.

### 576-F13. `quantityMapping` and `creditPolicyRef`

| Option | Meaning |
|---|---|
| A. Optional and opaque | The relationship may store a quantity-mapping version and a credit-policy reference. This note does not evaluate a ratio and does not return a price. The line may store the mapping version beside the relationship version. |
| B. Both required | A relationship without them is refused. |
| C. product-service evaluates the ratio | The catalogue returns a quantity. |

**Recommendation:** A. #652 already has an unadopted default for the map pin and for rounding. Restating `Nearest` and minimum 1 here would duplicate that fork. A price or a credit amount is pending the DRG-609 design (not accepted). The Accepted proration ADR is not reopened and its formula is not restated.

**Default:** Proposed, unaccepted, owner decision required. `quantityMapping` and `creditPolicyRef` are optional and opaque; no ratio is evaluated and no price is returned (option A). Alternatives written out: both fields are required (option B); product-service evaluates the ratio (option C). The map pin and the rounding stay the unadopted #652 forks. Conversion price is pending the DRG-609 design (not accepted). Cluster A 550-F2 (`NoCredit` and its alternatives) is not re-decided. The proration ADR is cited and not restated.

## What an owner decision on this note still does not do

A decision on any row in the owner-decision table:

- accepts no ADR and edits no ADR;
- leaves ADR-017 and ADR-024 through ADR-027 Proposed;
- leaves [`adr-amendment-proration-formula.md`](adr-amendment-proration-formula.md) Accepted and unchanged;
- does not adopt #650's SKU-mapping forks, and does not contradict them;
- does not settle #652's maintenance link or quantity-map rounding;
- does not change #651's action vocabulary or OQ12;
- does not redesign the DRG-539 price-book read and does not design `ReconfigureFrom`;
- does not accept the DRG-609 design;
- does not change OQ5 option A or the OQ7 thin contract;
- does not authorize S3 or S6;
- adds no event, no route, no migration, no flag, and no deploy.

Accepting this authorizes no code, migration or deploy. DRG-576 stays In Review until the owner merges.
