# S7 contract header backfill (DRG-585)

CAM Flyway **V1_8** installs the header backfill. V1_9 stays reserved for `document_apply_ledger` (S4 / DRG-582).

Adopted default, first slice only, wording unchanged:

> One thin contract per existing subscription. No synthetic amendment history and no retroactive events. A later merge, tracked on DRG-554, is the place to regroup. The backfill does not regroup.

History synthesis stays on DRG-566. This change writes no amendment documents, no outbox rows, and no prices.

## Fill rules

`subscriptions` has no `tenant_id`. A fabricated or default tenant is not inserted. `contract_header_backfill_count(uuid)` and `contract_header_backfill_apply(uuid)` require the caller to pass the tenant. Flyway does not call them. A null tenant raises and writes nothing.

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

Backfilled rows use `created_by = V1_8_HEADER_BACKFILL`. No new column. A subscription created after cutover gets a header from `contract_header_at_creation`, marked `S7_HEADER_AT_CREATION`, with the same number. `AcceptedQuoteSubscriptionService` is not called: the accepted event has no tenant, and that class is outside this change.

## V1_7_2

`reject_frozen_amendment_line_mutation` runs only on `amendment_document_lines`. The backfill does not insert, update, or delete that table, so a frozen revision's lines stay frozen.

## Rollback

Flyway has no down migration. `contract_header_backfill_compensate()` deletes only `V1_8_HEADER_BACKFILL` rows, after clearing `subscriptions.contract_id` on the subscription whose number is `C-` plus that subscription's uuid.

The function changes nothing and raises when an amendment document, a `prior_contract_id`, or any other subscription references a backfilled header. After that, restore from backup. Creation headers are not deleted. The S1 empty-table drop does not apply once these rows exist.
