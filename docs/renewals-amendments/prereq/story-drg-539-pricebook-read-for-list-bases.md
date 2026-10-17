# DRG-539 — price-book read for List and ListLessContractedDiscount

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. Date: 2026-10-16. Base: `main` at `e3ee211b`.*

**Story:** [DRG-539](https://linear.app/drgamtd-workspace/issue/DRG-539/product-pricebook-and-configuration-services-not-in-continuation-flow). This note is the price-book read for the renewal bases `CurrentList` and `ListLessContractedDiscount`, and for the `notAboveList` cap that uses the same list.

**Status:** Proposed. Not accepted. This file is not an ADR, takes no ADR number, and does not edit an Accepted ADR. Accepting this note authorizes no code, no endpoint, and no migration. It unblocks the List bases of [DRG-567](https://linear.app/drgamtd-workspace/issue/DRG-567/renewal-uplift-basis-caps-price-as-of-date-and-policy) (`CurrentList`, `ListLessContractedDiscount`, and `notAboveList`) only after the owner accepts it. Until that acceptance, those bases stay unresolved. No code, schema, Flyway file, Drools edit, flag, route, or deploy starts from this text.

**Touches, and does not decide:** [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609/story-pricing-service-support-for-addupdateremove-lines-with) and OQ9, the pricing-service continuation capability in [oq9-pricing-continuation-capability.md](oq9-pricing-continuation-capability.md). This read sits in that area: a continuation price that needs a price book. The design note merged in [#625](https://github.com/drgaciw/open-crm-cpq/pull/625) still says no code starts until the owner accepts that design. Merging the note is not that acceptance. This file does not decide OQ9, and it does not adopt the DRG-609 continuation route, its request shape, or its price-book pin sentence.

**ADR status used here.** Read on this `main`:

- [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md) opens **Status: Accepted 2026-10-16**. [#623](https://github.com/drgaciw/open-crm-cpq/pull/623) is merged. Its acceptance scope says runtime implementation and migrations remain separate work, and that the record does not accept ADR-024 through ADR-027. This note treats the uplift text as Accepted and does not amend it.
- [ADR-008](../../adr/ADR-008-postgres-schema-per-service.md) is Accepted. Integration is HTTP or Kafka. Cross-schema table reads are forbidden. pricing-service is the sole Flyway owner of the shared `pricing` schema, including `price_books`. pricebook-service keeps Flyway disabled.
- [ADR-017](../../adr/ADR-017-synchronous-inter-service-communication.md) opens **Status: Proposed on 2026-10-04. Not accepted.** This note does not treat its allow-list, timeouts, or retries as rules.
- [ADR-024](../../adr/ADR-024-order-orchestration-context.md), [ADR-025](../../adr/ADR-025-saga-process-manager.md), [ADR-026](../../adr/ADR-026-command-messages.md), and [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md) each open **Status: Proposed on 2026-10-04. Not accepted.** This note does not depend on them.

## Verification

Nothing was executed. This pass did not start Maven, a database, Testcontainers, or an HTTP call.

| Mark | Meaning here |
|---|---|
| `verified by reading code` | Source, SQL, or a doc on this `main` (`e3ee211b`). Not executed. |
| `verified by reading the issue` | Linear, this pass. |
| `verified by reading the pull request` | GitHub pull-request body, this pass. The branch was not checked out. |
| `unverified` | Not established by a file, issue, or pull request opened in this pass. |

**Claims marked as executed: none.**

## Question

How does pricing-service obtain the list unit, in the subscription currency, on the price book whose effective interval contains the price-as-of date, for `CurrentList`, `ListLessContractedDiscount`, and `notAboveList`?

The accepted uplift record names this read as DRG-539 and says it is not designed there (consequences, last bullet of the implementation list). `verified by reading code` for that file.

## Current state

`verified by reading code` unless a row says otherwise.

### The accepted formulas this read must feed

From the Accepted uplift record, D-01 and D-03. This note does not restate them as a new decision.

| Input | What the record uses it for |
|---|---|
| Current list | `CurrentList` is that unit. `ListLessContractedDiscount` is `currentList × (1 − discountFraction)`. Mode `List` uses the same unit and ignores the basis field. `notAboveList` caps against the same unit. |
| Prior list | `discountFraction = max(0, 1 − exitNet / priorList)`. Prior list is the list unit pinned on the accepted source line that produced the exit rate. Missing, or zero, while the basis is `ListLessContractedDiscount`, is `UNRESOLVED_BASIS`. |
| Price-book version | The book whose effective interval contains the price-as-of date. The snapshot stores the version id with the enum and the calendar date. A missing version is `UNRESOLVED_BASIS`. |

Illustrative scenario 1 (example numbers in `docs/renewals-amendments/cpq-pricing-policy-sanity-check.md`, not customer data): prior list $50, exit net $40, list $52 effective 2026-11-01, action date 2026-08-02. The fraction is 0.20. The list the read must return on `EffectiveDate` is 52. The $41.60 figure in the sanity check is `52 × 0.80` before the account percent. The Accepted record's worked example then applies 7% and stores 44.5120. This note supplies 52 and, when a pin exists, 50. It does not apply the percent.

### What continuation prices today

`QuoteContinuationPolicy` takes the exit net and, on renewal, multiplies by the caller percent, defaulting a null percent to zero, at scale 2. `verified by reading code` (`QuoteContinuationPolicy.java`, renewal branch).

`QuoteContinuationService.author` writes that net into `quote_line_items.list_price`, `unit_price`, and `net_price`. `verified by reading code`. The column is `DECIMAL(19,2)` (`V1__create_quote_schema.sql`). A renewal draft's list column is the exit net, at scale 2.

`QuoteService.verifyContinuationPeriods` throws `SUBSCRIPTION_CONTINUATION_RATE_CHANGED` when pricing returns a different net. `verified by reading code`. pricing-service is a preservation check on that path.

[#640](https://github.com/drgaciw/open-crm-cpq/pull/640) is an open draft. Its body says the first DRG-567 slice prices `ContractedNet` only, returns `UNRESOLVED_BASIS` for `CurrentList` and `ListLessContractedDiscount`, and does not read a price book. `verified by reading the pull request`. The class is not on this `main`. `unverified` against the diff.

### The book pricing-service can call today

pricebook-service exposes `POST /internal/v1/price-books/resolve-batch`. The body is `region` plus `productExternalIds`. The response is a surrogate `priceBookId`, `priceBookCurrency`, and per product `entryMatched`, `listPrice`, `floorPrice`, `currency`. No as-of date, no book uuid, no entry uuid. `verified by reading code` (`ResolveBatchRequest`, `ResolveBatchResponse`, `InternalPriceBookController`).

`PriceBookInternalResolutionService.findPriceBook` loads the single non-deleted book for the region and keeps it when `isEffective` is true for `LocalDate.now()`. Otherwise it loads the single default book and applies the same today-check. Otherwise it throws `NO_PRICE_BOOK`. `verified by reading code`.

`isEffective`: `effective_date` is null or on or before today, and `expiration_date` is null or on or after today. Both ends are inclusive. A null `effective_date` counts as already started. `verified by reading code`.

When two non-deleted entries share a product, the collector keeps the first and drops the rest. `verified by reading code`. SQL has no unique constraint on `(price_book_id, product_external_id)`. `verified by reading code` (`V1__create_pricing_schema.sql`). The write path rejects a duplicate in the service (`DUPLICATE_PRICE_BOOK_ENTRY`). A row inserted outside that path is `unverified`.

pricing-service calls that route with `PriceBookResolutionClient.resolveBatch(region, productExternalIds)` and a service token (`PriceBookClientConfig`). The client builder sets no connect timeout and no read timeout. The class comment says the timeouts are ADR-017's. ADR-017 is Proposed, and its context table lists this edge as the one with no timeouts. `verified by reading code` for the builder and for ADR-017's table. This note does not add timeouts.

`PricingExecutionService.getListPrice` uses, in order, `listPriceOverride`, the batch entry, then `PricingProduct.listPrice`, then zero. `verified by reading code`. `pricing_products.list_price` is `DECIMAL(19,4)` and has no effective interval and no book id (`PricingProduct.java`, `V1__create_pricing_schema.sql`). That fallback is the new-quote waterfall. It is the wrong answer for a list basis: the Accepted record requires `UNRESOLVED_BASIS` when the list or the version is missing.

### One book per region, and one default, with no history

`price_books` columns that matter: `uuid` (unique, not null), surrogate `id`, `region`, `currency` default `USD`, `is_default`, `effective_date`, `expiration_date`, optimistic `version` default 0. `price_book_entries.list_price` is `DECIMAL(19,4)`. `verified by reading code` (`V1__create_pricing_schema.sql`, `BaseEntity.version` and `uuid`).

There is no price-book version table and no entry-history table. The optimistic `version` increments on update. It is not a generation of the list. The public version id the snapshot can store, with the columns that exist, is `price_books.uuid`.

`PriceBookService.validateRegionUniqueness` rejects a second non-deleted book for the same region, at any date (`DUPLICATE_REGION_PRICE_BOOK`). `verified by reading code`. The database has an index on `region` and no unique constraint. Two rows can exist if they bypass this method. Whether any database has them is `unverified`.

`clearCurrentDefault` clears `is_default` on the current default when a new default is saved, including a default whose `effective_date` is still in the future. `verified by reading code`.

`ensureNotPublished` rejects updates and deletes once `effective_date` is non-null and on or before today (`PRICE_BOOK_PUBLISHED_IMMUTABLE`). A null `effective_date` stays editable. A future `effective_date` stays editable. `verified by reading code`.

Consequence for scenario 1, from those rules, not from a run:

- A second book in the same region for 2026-11-01 cannot be created through the service while the $50 book exists.
- A future default created for 2026-11-01 clears the current default immediately. Until 2026-11-01 the new book fails `isEffective`, and the old book is no longer the default. `findPriceBook` then throws `NO_PRICE_BOOK` when no effective regional book remains.
- An in-place edit of a published book is rejected, so $50 cannot become $52 on the same row after the book is effective.
- A book with a null `effective_date` is selectable today and remains mutable, so a pin of its uuid does not freeze the list.

### Prior list is not on the accepted rate

`subscription_rate_periods` stores `net_unit_price DECIMAL(19,4)` and `discount_rate DECIMAL(19,8)`. It has no list column and no price-book uuid. `verified by reading code` (`V1_6__subscription_rate_schedule.sql`, `SubscriptionRatePeriod.java`).

`SubscriptionScheduleView.Period`, the payload continuation reads, has net amounts and no list, no discount rate, and no book id. `verified by reading code`.

`Subscription` has customer, quote, status, and renewal date. It has no region column. `verified by reading code` (`Subscription.java`).

`RampPeriodPricer` computes `discountRate` as `(listPrice − netUnit) / listPrice` at scale 4 when it prices a ramp step. `verified by reading code`. That fraction is not the Accepted record's `discountFraction`, which is defined from the pinned prior list and the exit net. The schedule view does not carry `discountRate`. Inverting a stored rate into a prior list is a different procedure, and a null rate (the legacy backfill insert in `V1_6` does not set `discount_rate`) would invent a list. This note does not use it.

`quote_line_items.list_price` at scale 2 is the selling quote's waterfall list, and on a renewal draft it is overwritten with the exit net. It is not the scale-4 pin D-01 names.

### DRG-609 and OQ9, touched and not decided

OQ9 asks whether pricing-service can price a continuation line and return a decision identity. Its verdict on the source is that execute cannot. `verified by reading code` for `oq9-pricing-continuation-capability.md`. DRG-609 is the design note for that capability. It sketches `pricebookVersion` as a caller pin to validate, or a resolve at `policyAsOf`. Its status paragraph says the design is not accepted. `verified by reading code` for `story-drg-609-pricing-service-continuation-design.md`.

This note touches that area because a list basis is a continuation price that consults the price book. It does not decide OQ9. It does not decide DRG-609. A later acceptance of DRG-609 does not by itself choose the book-selection rules below. The owner can require one resolver for both once both texts are accepted. That requirement is not decided here.

## Options

### Option A — pricing-service reads pricebook-service at the price-as-of date (recommended)

A renewal evaluation in pricing-service calls pricebook-service over the existing service-token client. quote-service sends the product external id, the subscription currency, the price-as-of calendar date, and any region it actually has. It does not send a list amount. The response used for the basis is the book uuid, the entry uuid, the scale-4 list, the book currency, and the entry currency. The snapshot's price-book version id is the book uuid.

The call is a **new cross-service dependency**. Continuation today does not call pricebook-service. Execute already calls `POST /internal/v1/price-books/resolve-batch` for a new quote. The renewal read is a further HTTP call, sketched as `POST /internal/v1/price-books/resolve-as-of`. The sketch is not an endpoint. This note authorizes no endpoint. Execute keeps `resolveBatch(region, productIds)`, `getListPrice`, and the product fallback. ADR-017 is Proposed, so this sketch is not an allow-list amendment.

**Failure.** A timeout or an outage of pricebook-service returns `UNRESOLVED_BASIS` and no price. The read never guesses a price: not `PricingProduct.listPrice`, not `listPriceOverride`, not zero, and not a cached list. The same result covers a connection failure and an empty or unparseable body. Today's execute client maps an unreachable price book to `PRICEBOOK_UNAVAILABLE` and a bad body to `PRICEBOOK_CLIENT_ERROR` (`RestPriceBookResolutionClient`). The renewal read does not reuse those codes as a price.

**Latency and retry** are an owner fork, in the decision section. This note sets neither a timeout nor a retry.

Selection, once successor books are allowed (option A1 below):

- Currency on the book and on the entry equals the subscription currency. Any other currency, or a null currency, is `UNRESOLVED_BASIS`.
- `effective_date` is non-null and on or before the price-as-of date. `expiration_date` is null or on or after that date. Inclusive ends, the same predicate as `isEffective`, with the as-of date in place of today.
- A null `effective_date` is `UNRESOLVED_BASIS`.
- Among books that match, a supplied region prefers books with that region. If none of those contain the date, the result is `UNRESOLVED_BASIS`. The read does not substitute the default book.
- With no region, the book is the default whose interval contains the date.
- Zero matches, or more than one match, is `UNRESOLVED_BASIS`.
- Zero entries, or more than one non-deleted entry for the product, is `UNRESOLVED_BASIS`.
- `PricingProduct.listPrice`, `listPriceOverride`, and zero are not answers.

Trade-offs: one reader owns the interval rule; execute stays on today's contract; scenario 1 needs the uniqueness change in A1; a future book can still be edited until its effective date.

### Option A1 — successor books, same columns (recommended with A)

Allow more than one non-deleted book per region when their effective intervals do not overlap, and more than one book with `is_default` true on the same condition. Each book row is one frozen generation after it is published. The pin is `uuid`. No new price table.

`clearCurrentDefault` would stop clearing a default whose interval does not overlap the one being saved. That behavior change is implementation, and it waits for acceptance. This note does not make it.

Trade-offs: uses `effective_date` and `expiration_date` that already exist; scenario 1 is a second book effective 2026-11-01; published immutability already freezes a book once its effective date has arrived; the write rule is Java today, so the database will not enforce the interval until a later migration the owner asks for.

### Option A2 — dated prices on the single regional book

Keep one book per region and one default. Add rows that store list, currency, and an effective interval per product. The pin is that row's uuid. The book's own `effective_date` stops being the selector D-03 names.

Trade-offs: matches the current uniqueness rule; requires a new table (see Migration); two interval concepts on one book (the book header and the price row).

### Option B — quote-service resolves, and sends the list and the uuid

The Accepted record says quote-service sends the list at the as-of date and the price-book version. Read as a transport fact, quote-service could call pricebook-service and pass the numbers through.

Trade-offs: quote-service has no price-book client today; continuation already writes the exit net into `list_price`; a caller-supplied amount can disagree with the book; pricing-service would re-read to honor `UNRESOLVED_BASIS`, which duplicates option A. This note asks the owner to keep the Accepted sentence as the snapshot contents, with pricing-service filling the list and the uuid from the book. It does not edit the ADR.

### Option C — pricing-service queries `price_books` itself

The tables live in the `pricing` schema, and pricing-service owns Flyway. A JPA read from pricing-service would skip pricebook-service.

Trade-offs: ADR-008 says service integration is HTTP or Kafka. The resolution rules (region, default, published immutability) live in pricebook-service. A second reader forks them. Rejected under the recommendation below.

### Option D — keep the execute waterfall

Use resolve-batch as of today, then `PricingProduct.listPrice`, then zero.

Trade-offs: that is the new-quote path. It has no price-as-of date, pins a surrogate id, and invents a list the Accepted record forbids. Rejected under the recommendation below.

### Option E — invert `discount_rate` into the prior list

Treat `(list − net) / list` on the rate period as the contracted fraction and solve for the old list.

Trade-offs: the Accepted formula names the pinned prior list; the schedule payload has no discount rate; the legacy backfill stores no discount rate; a tier discount is not a contracted list discount. Rejected under the recommendation below.

## Recommendation

**Option A with option A1.** pricing-service is the reader. The HTTP call to pricebook-service is a new cross-service dependency on the renewal path. pricebook-service answers an as-of resolve. The version id is `price_books.uuid`. Successor books, with non-overlapping intervals, are how a region (and the default) moves from $50 to $52. Execute's resolve-batch and its product fallback stay as they are. A miss, a timeout, or an outage is `UNRESOLVED_BASIS` and no price. The result is never a guessed price.

Prior list is an acceptance pin, not a second as-of read at renewal. Renewal does not recompute it from the live book, from `discount_rate`, or from `quote_line_items.list_price`. When the pin is absent or zero, `ListLessContractedDiscount` is `UNRESOLVED_BASIS`. `CurrentList`, mode `List`, and `notAboveList` do not need the prior list. They need the as-of read.

`price_book_entries.floor_price` stays a catalog floor on the entry. It is not `notBelowExitNet`, `minIncreasePct`, or `minUnitAmount`.

Product substitution and `ReconfigureFrom` stay on DRG-539. This note does not design them.

## What DRG-567 needs from this read

The open draft [#640](https://github.com/drgaciw/open-crm-cpq/pull/640) prices `ContractedNet` and returns `UNRESOLVED_BASIS` for the two list bases. `verified by reading the pull request`. This note unblocks those List bases only after the owner accepts it. Acceptance authorizes no code, no endpoint, and no migration. A later DRG-567 slice is a separate act.

| DRG-567 field | Source, under the recommendation |
|---|---|
| Current list, scale 4, subscription currency | As-of resolve. Book currency and entry currency both match. |
| Price-book version id | `price_books.uuid` of the selected book, plus the entry uuid on the snapshot so a changed entry is visible. |
| Price-as-of enum and calendar date | Already D-03. The read receives the calendar date the rung resolved. It does not choose `ActionDate`, `EffectiveDate`, or `LineStart`. |
| Prior list, scale 4 | The pin stored when the exit rate was accepted. Absent on `subscription_rate_periods` today. Missing or zero → `UNRESOLVED_BASIS` for `ListLessContractedDiscount` only. |
| `notAboveList` | Same current list. Missing list → `UNRESOLVED_BASIS`, which the Accepted record already requires. |
| Mode `List` | Same current list. The mode walk remains DRG-549. |

`ContractedNet` does not call this read for its basis amount. A `notAboveList` flag on a `ContractedNet` line does call it.

## Migration

No migration ships with this note. No file is added under any `db/migration` directory. Accepting this note authorizes no code, no endpoint, and no migration. This note assigns no Flyway version.

The recommended read (option A with A1) needs no new column. Uniqueness is enforced in `PriceBookService`, not by a unique index.

| Later change, would-be only | Version |
|---|---|
| Database rejection of overlapping intervals for one region, or of overlapping default books, if the owner asks for it. pricing-service remains the Flyway owner; pricebook-service stays Flyway-disabled. An exclusion constraint needs an extension this note does not name as accepted. | Next free pricing migration at build time. |
| Option A2, a dated price table on the single regional book, if the owner chooses A2 instead of A1. | Next free pricing migration at build time. The two pricing changes are alternatives. One later migration picks one. |
| Prior-list pin on `subscription_rate_periods`, if a later story stores it (`list_unit_price DECIMAL(19,4)` null, `price_book_uuid VARCHAR(36)` null). Nullable columns, no backfill: existing rows stay null, and `ListLessContractedDiscount` stays `UNRESOLVED_BASIS` for them. | Next free CAM migration at build time. `V1_10` is would-be only. This note does not reserve it. |
| quote-service `list_price` | Not proposed. Latest quote migration on this `main` is `V1_12__continuation_operation.sql`. `list_price DECIMAL(19,2)` is the wrong pin. |

CAM `V1_8__contract_header_backfill.sql` is the header backfill and is on this `main`. Its header reserves `V1_9` for `document_apply_ledger` (S4 / DRG-582). The technical approach, section 3, assigns `V1_9` the same way. Those reservations belong to those stories. This note does not take them.

Rollback for the recommendation, while no migration exists, is to keep the list bases at `UNRESOLVED_BASIS`. A later migration pull request documents its own rollback. Existing books, entries, quotes, and rate periods are unchanged by this file.

## Owner decision needed

This note is Proposed until the owner accepts or replaces the following. Acceptance is a later status change. It is not this pull request. Accepting this note authorizes no code, no endpoint, and no migration. It unblocks the List bases of DRG-567 only after the owner accepts it.

1. **Reader.** pricing-service calls pricebook-service (option A). quote-service sends product, currency, price-as-of date, and region when it has one. pricing-service writes the list and `price_books.uuid` onto the snapshot. The Accepted sentence that quote-service sends the list and the version stays in that ADR; this item confirms pricing-service is what fills those two facts from the book.
2. **Successor books (A1) rather than a new price table (A2).** Non-overlapping intervals per region, and the same rule for `is_default`. The version id is `price_books.uuid`, not the surrogate `id` and not the optimistic `version` column.
3. **Null `effective_date` is not a list-basis book.** The result is `UNRESOLVED_BASIS`.
4. **A still-mutable future book may be pinned** when the price-as-of date falls inside its interval. `PRICE_BOOK_PUBLISHED_IMMUTABLE` uses today, so a book effective 2026-11-01 can change on 2026-08-02. The stored snapshot keeps the uuid and the list it resolved. A fresh pricing run resolves again and writes a new snapshot, which is D-03. The alternative is to refuse a book until it is immutable, which withholds the $52 book on an August quote.
5. **Inclusive interval ends,** matching `isEffective`. [#640](https://github.com/drgaciw/open-crm-cpq/pull/640) says that draft does not decide inclusivity. `verified by reading the pull request`.
6. **Currency match on the book and the entry.** Region is optional and never falls through to the default when the regional books miss the date. More than one matching book, or more than one entry, is `UNRESOLVED_BASIS`.
7. **No product-list fallback and no zero** on this read. Execute's fallback stays on execute.
8. **Prior list** comes from a pin captured at acceptance. This note does not add that column. Any later CAM migration is the next free CAM migration at build time. `V1_10` is would-be only. Until a later story captures the pin, `ListLessContractedDiscount` is `UNRESOLVED_BASIS` on every existing rate. `CurrentList` and `notAboveList` still use the as-of read.
9. **The renewal HTTP call is a new cross-service dependency.** The path name `POST /internal/v1/price-books/resolve-as-of` is a sketch. Confirming it authorizes no endpoint. resolve-batch's request and response stay as they are. A timeout or an outage returns `UNRESOLVED_BASIS` and never a guessed price.
10. **Latency and retry (owner fork).** Before returning `UNRESOLVED_BASIS`, the client may retry a failed call, or it may return on the first timeout or outage. Connect and read limits are part of the same fork. ADR-017 stays Proposed and does not decide it.
11. **DRG-609 and OQ9 stay undecided.** This note touches that pricing-capability area and does not decide it. This read is not `POST /api/v1/pricing/continuation/evaluate`.
12. **Entry `floor_price` is not an uplift floor.**

## Out of scope

Product-service, successor products, and configuration / `ReconfigureFrom` (the rest of DRG-539). Mode catalog (DRG-549). Rules-fraction conversion (DRG-541). The audit table (DRG-564). Escalator expansion (DRG-533). Multi-year locks (DRG-568). Deciding OQ9 or the DRG-609 continuation operation. ADR-017 as an accepted rule. ADR-024 through ADR-027. Any flag. Any change to `QuoteContinuationPolicy`'s zero default; that default is the gap the Accepted uplift record already names, and DRG-567 owns the change.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-539, DRG-567 | Linear, this pass |
| Uplift record, Accepted | `docs/renewals-amendments/prereq/adr-renewal-uplift-basis-and-precedence.md`; #623 merged |
| DRG-609 design, not accepted; OQ9 | `docs/renewals-amendments/prereq/story-drg-609-pricing-service-continuation-design.md`; `oq9-pricing-continuation-capability.md`; #625 merged |
| DRG-567 ContractedNet draft | #640 body |
| ADR-008, ADR-017, ADR-024..027 | `docs/adr/` headers and ADR-008 decision section; catalog row for ADR-017 in `docs/adr/README.md` |
| Resolve path | `PriceBookInternalResolutionService`, `InternalPriceBookController`, `ResolveBatchRequest`, `ResolveBatchResponse`, `PriceBookRepository`, `PriceBookEntryRepository` |
| Write rules | `PriceBookService` (`validateRegionUniqueness`, `clearCurrentDefault`, `ensureNotPublished`) |
| Execute list fallback | `PricingExecutionService.getListPrice`, `PricingProduct` |
| Client | `RestPriceBookResolutionClient`, `PriceBookClientConfig` |
| Tables | pricing-service `V1__create_pricing_schema.sql`; quote-service `V1__create_quote_schema.sql`; CAM `V1_6__subscription_rate_schedule.sql`, `V1_8__contract_header_backfill.sql` |
| Continuation | `QuoteContinuationPolicy`, `QuoteContinuationService.author`, `QuoteService.verifyContinuationPeriods`, `SubscriptionScheduleView` |
| Discount fraction on a ramp step | `RampPeriodPricer.priceStep` |
| DoD v0.2 | Notion page fetched this pass. `verification.state` was `unverified`. Title and body both say approved 2026-10-16 4:30 PM CT. |

## Unverified

- Any runtime result: HTTP JSON, which book a live resolve returns, row counts, overlapping books in a real database.
- The Java on [#640](https://github.com/drgaciw/open-crm-cpq/pull/640). The unresolved-basis claim is the pull-request body.
- Jackson unknown-property handling on the pricing price-book client.
- The next free pricing migration at build time, and the next free CAM migration at build time. This note assigns neither. `V1_10` is would-be only.
- A subscription region stored outside `Subscription.java`. The entity that was read has none.
- Notion `verification.state` for the DoD page.
