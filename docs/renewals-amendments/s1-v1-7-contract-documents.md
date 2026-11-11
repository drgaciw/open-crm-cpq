# S1 contract-documents schema (DRG-579)

CAM Flyway **V1_7** (`V1_7__contract_documents.sql`) adds the thin contract header and the amendment document tables. ADR-035 authorizes this story and the audit-chain story (S2 / DRG-580) only. This change does not add renewal behavior, an API, or an apply path.

## What landed

- `contracts` thin header (id, number, tenant, customer, term model, start, end, governing agreement ref, status, `last_executed_seq`, `prior_contract_id`). No `precedence_flag` (DRG-592).
- Nullable `subscriptions.contract_id`.
- `amendment_documents`, `amendment_document_revisions`, `amendment_document_lines`, `amendment_application_units`, `amendment_document_events`.
- Nullable `subscription_rate_periods.amendment_document_line_id` and `revision_no` (both null, or both set).
- Uniqueness from the field schema section 4, including one EXECUTED revision per document and unique `(contract_id, amendment_seq)`.
- Append-only `amendment_document_events`: role `cam_app` has SELECT and INSERT only, and triggers reject UPDATE, DELETE and TRUNCATE for every role, including the table owner.
- `recorded_at` is overwritten by the database on insert.
- Write-once guards on `executed_revision_no`, `executed_at`, `amendment_seq` and `amendment_number`. Frozen revisions and their lines cannot be updated or deleted. V1_7_2 also rejects adding or moving lines into a frozen revision and locks the revision during a line mutation.

`applied_quote_acceptances` is unchanged. There is no `apply_status` column and no `document_apply_ledger`.

Stored enum tokens are the Java enum names (`AMEND`, `NOT_REQUIRED`, `TERMS_AND_CONDITIONS`). Logical `text` and `char(n)` columns are `varchar` with length checks. PostgreSQL `char` blank-pads, which breaks SHA-256 equality, so digests are `varchar(64)` constrained to lowercase hex.

`cam_app` is `NOLOGIN`. A deployment that wants the grant to apply creates a login and runs `GRANT cam_app TO <app_login>`. That login must not own the tables and must not be a superuser. The trigger still blocks mutation when the service connects as the owner. The running service user is unchanged.

## Rollback

V1_7 is expand-only. Flyway has no down migration. The previous application ignores the new tables and the new nullable columns. Roll back only while every new table, including `renewal_eligibility_publications`, is empty. Renewal publication can populate receipts even while the relay is off. If any document or publication-receipt row exists, restore from backup instead of dropping tables.

Run in the CAM schema (`contract` unless `DB_SCHEMA` says otherwise):

```sql
BEGIN;
DROP TABLE IF EXISTS amendment_document_events;
ALTER TABLE IF EXISTS subscription_rate_periods
    DROP CONSTRAINT IF EXISTS chk_rate_period_amendment_trace;
ALTER TABLE IF EXISTS subscription_rate_periods
    DROP COLUMN IF EXISTS amendment_document_line_id,
    DROP COLUMN IF EXISTS revision_no;
DROP TABLE IF EXISTS amendment_document_lines;
DROP TABLE IF EXISTS amendment_application_units;
ALTER TABLE IF EXISTS amendment_documents
    DROP CONSTRAINT IF EXISTS fk_amendment_document_current_revision;
DROP TABLE IF EXISTS amendment_document_revisions;
DROP TABLE IF EXISTS amendment_documents;
ALTER TABLE IF EXISTS subscriptions DROP COLUMN IF EXISTS contract_id;
DROP TABLE IF EXISTS renewal_eligibility_publications;
DROP TABLE IF EXISTS contracts;
DROP FUNCTION IF EXISTS reject_amendment_document_event_mutation();
DROP FUNCTION IF EXISTS stamp_amendment_document_event_recorded_at();
DROP FUNCTION IF EXISTS reject_amendment_document_write_once();
DROP FUNCTION IF EXISTS reject_frozen_amendment_revision_mutation();
DROP FUNCTION IF EXISTS reject_frozen_amendment_line_mutation();
DELETE FROM flyway_schema_history WHERE version IN ('1.7', '1.7.1', '1.7.2');
COMMIT;
```

`DROP ROLE cam_app` is optional and cluster-wide. Run it only when no other database on the instance still uses the role, after `REVOKE ALL ON SCHEMA <schema> FROM cam_app`.

## Follow-ups

- DRG-580 (S2): hash-chain writer, gapless `seq_no`, verifier, outbox publication. The genesis `prev_hash` constant is chosen there.
- DRG-581 (S3): document API and state machine.
- DRG-582 (S4): apply, `document_apply_ledger` (V1_9). `applied_quote_acceptances` stays as it is.
- DRG-584 (S6): schema-registry event contracts.
- DRG-585 (S7): header backfill (V1_8). `subscriptions.customer_id` is varchar today; `contracts.customer_account_id` is uuid.
- DRG-458: richer contract header fields. Related Decision brief (Proposed): [prereq/story-drg-458-generate-contract-decision-brief.md](prereq/story-drg-458-generate-contract-decision-brief.md).
- DRG-592: `precedence_flag`, not part of this header.
- DRG-313 / OQ8: computing `retention_until`. The column stays null.
- Ops: point the CAM login at `cam_app` when the service should lose owner rights on the event table. Not required for the trigger.

## Not in this story

Open Host Service, schema-registry events, outbox writes, commercial-decision audit entries and event-replay tests are not part of S1. The event table is storage only. No event is published and no commercial decision is made.

V1_7_1 seeds durable renewal receipts from existing composite outbox keys before the scheduler switches stores. Legacy bare subscription ids contain no term end and cannot be safely reconstructed; historical emissions already removed from the outbox are likewise unobservable. The migration leaves those unknown histories unclaimed rather than suppress a possibly new term.
