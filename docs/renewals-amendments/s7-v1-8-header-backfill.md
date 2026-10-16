# S7 contract header backfill (DRG-585)

This change **departs from a migration that backfills**. CAM Flyway **V1_8** ships functions only. No data is written at deploy. The backfill is a **manual owner step**. Running `contract_header_backfill_apply` against any real environment is an owner decision, and no bot runs it.

V1_9 stays reserved for `document_apply_ledger` (S4 / DRG-582).

Adopted default, first slice only, wording unchanged:

> One thin contract per existing subscription. No synthetic amendment history and no retroactive events. A later merge, tracked on DRG-554, is the place to regroup. The backfill does not regroup.

History synthesis stays on DRG-566. The functions write no amendment documents, no outbox rows, and no prices.

After merge, DRG-585 must state that the data backfill itself is not applied.

## Owner sequence

The owner runs these steps, in order, and only after choosing the tenant. A null tenant raises and writes nothing. No bot runs apply or compensate.

1. **Count (dry run).** `contract_header_backfill_count(tenant)` returns how many subscriptions would get a header. It writes nothing.
2. **Review.** Read that count and the eligible set before any write.
3. **Apply.** `contract_header_backfill_apply(tenant)` inserts the headers and sets `subscriptions.contract_id`. A second call links nothing.
4. **Compensate.** `contract_header_backfill_compensate()` deletes only rows marked `created_by = V1_8_HEADER_BACKFILL`, after clearing `contract_id` on the subscription whose number is `C-` plus that subscription's uuid. It does nothing if an amendment document, a `prior_contract_id`, or another subscription references a marked row: the function raises and leaves the rows in place. Restore from backup is the fallback. Creation headers (`S7_HEADER_AT_CREATION`) are not deleted. The S1 empty-table drop does not apply once these rows exist. Flyway has no down migration.

## Fill rules

`subscriptions` has no `tenant_id`. A fabricated or default tenant is not inserted. Flyway does not call the functions.

Eligible rows are `is_deleted = false`, `contract_id` null, `end_date >= start_date`, and `customer_id` a canonical UUID. Soft-deleted rows, non-UUID customer ids, and inverted dates are skipped. Non-UUID customer ids are not hashed.

| Column | Value |
|---|---|
| `contract_number` | `C-` plus `subscriptions.uuid`. Unique per tenant and stable across a re-run. |
| `customer_account_id` | `customer_id` cast to uuid |
| `term_model` | `TERMED`. Existing subscription end dates are not null. `EVERGREEN` forbids an end date. Nothing on the row says `PERPETUAL`. |
| `start_date`, `end_date` | Copied |
| `status` | `PENDING`, `ACTIVE`, `SUSPENDED` → `ACTIVE`. `EXPIRED` → `EXPIRED`. `CANCELLED` → `TERMINATED`. |
| `last_executed_seq` | `0` |
| `prior_contract_id`, `governing_agreement_ref` | null |

Backfilled rows use `created_by = V1_8_HEADER_BACKFILL`. No new column.

## AC-S7-3

`contract_header_at_creation` creates one header for a subscription that already exists and has no `contract_id`. It uses `created_by = S7_HEADER_AT_CREATION` and the same contract number. AC-S7-3 is covered by the integration test (`ContractHeaderBackfillIntegrationTest`).

## Open item

`AcceptedQuoteSubscriptionService` is unedited because the accepted event carries no tenant. Wiring that NEW path is an open item for the later tenant design.

## V1_7_2

`reject_frozen_amendment_line_mutation` runs only on `amendment_document_lines`. The functions do not insert, update, or delete that table, so a frozen revision's lines stay frozen.
