-- DRG-585 / RA-51 (S7). Contract header backfill. First slice only.
-- V1_9 is reserved for document_apply_ledger (S4 / DRG-582). Do not use it here.
--
-- One thin contract per existing subscription. No synthetic amendment history and no
-- retroactive events. A later merge, tracked on DRG-554, is the place to regroup.
-- The backfill does not regroup.
--
-- Flyway installs the backfill and does not execute it. subscriptions has no tenant_id.
-- A fabricated or default tenant is forbidden. contract_header_backfill_apply(uuid) is the
-- run, and contract_header_backfill_count(uuid) is the dry-run. The same tenant must be
-- passed on every run. A null tenant raises and writes nothing.
--
-- Eligible rows: is_deleted = false, contract_id is null, end_date >= start_date, and
-- customer_id is a canonical UUID. Soft-deleted rows, non-UUID customer ids, and inverted
-- dates are left alone. No customer UUID is hashed into existence.
--
-- Fill, from the subscription row only:
--   contract_number = 'C-' || subscriptions.uuid   (unique per tenant, stable)
--   customer_account_id = customer_id::uuid
--   term_model = TERMED (the subscription end date is not null; EVERGREEN forbids an end date)
--   start_date, end_date copied
--   status PENDING, ACTIVE, SUSPENDED -> ACTIVE; EXPIRED -> EXPIRED; CANCELLED -> TERMINATED
--   last_executed_seq = 0, prior_contract_id null, governing_agreement_ref null
-- Marker: created_by = 'V1_8_HEADER_BACKFILL'. No new column.
-- Creation after cutover uses the same number and created_by = 'S7_HEADER_AT_CREATION'.
--
-- No amendment documents, lines, events, rate periods, pricing changes, or outbox rows.
-- V1_7_2 reject_frozen_amendment_line_mutation fires only on amendment_document_lines.
-- This backfill does not insert, update, or delete that table, so frozen line membership
-- stays as V1_7_2 left it.
--
-- Rollback limit: Flyway has no down migration. contract_header_backfill_compensate()
-- deletes only rows with created_by = 'V1_8_HEADER_BACKFILL', after clearing
-- subscriptions.contract_id on the subscription whose number is 'C-' || uuid.
-- It changes nothing and raises when an amendment document, a prior_contract_id, or any
-- other subscription references one of those rows. After that point, restore from backup.
-- Creation headers (S7_HEADER_AT_CREATION) are not deleted.

CREATE OR REPLACE FUNCTION contract_header_customer_uuid(p_customer_id varchar)
RETURNS uuid
LANGUAGE plpgsql
IMMUTABLE
AS $hdr$
BEGIN
    IF p_customer_id ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN
        RETURN p_customer_id::uuid;
    END IF;
    RETURN NULL;
END;
$hdr$;

CREATE OR REPLACE FUNCTION contract_header_backfill_status(p_status varchar)
RETURNS varchar
LANGUAGE sql
IMMUTABLE
AS $hdr$
    SELECT CASE p_status
        WHEN 'PENDING' THEN 'ACTIVE'
        WHEN 'ACTIVE' THEN 'ACTIVE'
        WHEN 'SUSPENDED' THEN 'ACTIVE'
        WHEN 'EXPIRED' THEN 'EXPIRED'
        WHEN 'CANCELLED' THEN 'TERMINATED'
    END
$hdr$;

CREATE OR REPLACE FUNCTION contract_header_backfill_count(p_tenant_id uuid)
RETURNS bigint
LANGUAGE plpgsql
AS $hdr$
BEGIN
    IF p_tenant_id IS NULL THEN
        RAISE EXCEPTION 'authoritative tenant is required' USING ERRCODE = '23514';
    END IF;
    RETURN (
        SELECT count(*)
        FROM subscriptions s
        WHERE NOT s.is_deleted
          AND s.contract_id IS NULL
          AND s.end_date >= s.start_date
          AND contract_header_customer_uuid(s.customer_id) IS NOT NULL
    );
END;
$hdr$;

CREATE OR REPLACE FUNCTION contract_header_backfill_apply(p_tenant_id uuid)
RETURNS bigint
LANGUAGE plpgsql
AS $hdr$
DECLARE
    linked bigint;
BEGIN
    IF p_tenant_id IS NULL THEN
        RAISE EXCEPTION 'authoritative tenant is required' USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM subscriptions s
        JOIN contracts c
          ON c.tenant_id = p_tenant_id
         AND c.contract_number = 'C-' || s.uuid
        WHERE NOT s.is_deleted
          AND s.contract_id IS NULL
          AND s.end_date >= s.start_date
          AND contract_header_customer_uuid(s.customer_id) IS NOT NULL
          AND (
              c.customer_account_id IS DISTINCT FROM contract_header_customer_uuid(s.customer_id)
              OR c.created_by NOT IN ('V1_8_HEADER_BACKFILL', 'S7_HEADER_AT_CREATION')
          )
    ) THEN
        RAISE EXCEPTION 'contract number already belongs to a different header' USING ERRCODE = '23505';
    END IF;

    INSERT INTO contracts (
        tenant_id, contract_number, customer_account_id, term_model,
        start_date, end_date, status, last_executed_seq, created_by
    )
    SELECT
        p_tenant_id,
        'C-' || s.uuid,
        contract_header_customer_uuid(s.customer_id),
        'TERMED',
        s.start_date,
        s.end_date,
        contract_header_backfill_status(s.status),
        0,
        'V1_8_HEADER_BACKFILL'
    FROM subscriptions s
    WHERE NOT s.is_deleted
      AND s.contract_id IS NULL
      AND s.end_date >= s.start_date
      AND contract_header_customer_uuid(s.customer_id) IS NOT NULL
      AND contract_header_backfill_status(s.status) IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM contracts c
          WHERE c.tenant_id = p_tenant_id
            AND c.contract_number = 'C-' || s.uuid
      )
    ON CONFLICT (tenant_id, contract_number) DO NOTHING;

    UPDATE subscriptions s
    SET contract_id = c.id
    FROM contracts c
    WHERE NOT s.is_deleted
      AND s.contract_id IS NULL
      AND s.end_date >= s.start_date
      AND contract_header_customer_uuid(s.customer_id) IS NOT NULL
      AND c.tenant_id = p_tenant_id
      AND c.contract_number = 'C-' || s.uuid
      AND c.customer_account_id = contract_header_customer_uuid(s.customer_id)
      AND c.created_by IN ('V1_8_HEADER_BACKFILL', 'S7_HEADER_AT_CREATION');

    GET DIAGNOSTICS linked = ROW_COUNT;
    RETURN linked;
END;
$hdr$;

CREATE OR REPLACE FUNCTION contract_header_at_creation(p_subscription_id bigint, p_tenant_id uuid)
RETURNS uuid
LANGUAGE plpgsql
AS $hdr$
DECLARE
    sub subscriptions%ROWTYPE;
    header_id uuid;
BEGIN
    IF p_tenant_id IS NULL THEN
        RAISE EXCEPTION 'authoritative tenant is required' USING ERRCODE = '23514';
    END IF;

    SELECT * INTO sub FROM subscriptions WHERE id = p_subscription_id FOR UPDATE;
    IF NOT FOUND OR sub.is_deleted OR sub.end_date < sub.start_date
        OR contract_header_customer_uuid(sub.customer_id) IS NULL
        OR contract_header_backfill_status(sub.status) IS NULL THEN
        RAISE EXCEPTION 'subscription is not eligible for a contract header' USING ERRCODE = '23514';
    END IF;
    IF sub.contract_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM contracts c
            WHERE c.id = sub.contract_id
              AND c.tenant_id = p_tenant_id
              AND c.customer_account_id = contract_header_customer_uuid(sub.customer_id)
        ) THEN
            RAISE EXCEPTION 'linked contract does not match tenant and customer' USING ERRCODE = '23514';
        END IF;
        RETURN sub.contract_id;
    END IF;

    INSERT INTO contracts (
        tenant_id, contract_number, customer_account_id, term_model,
        start_date, end_date, status, last_executed_seq, created_by
    )
    VALUES (
        p_tenant_id,
        'C-' || sub.uuid,
        contract_header_customer_uuid(sub.customer_id),
        'TERMED',
        sub.start_date,
        sub.end_date,
        contract_header_backfill_status(sub.status),
        0,
        'S7_HEADER_AT_CREATION'
    )
    ON CONFLICT (tenant_id, contract_number) DO NOTHING;

    SELECT c.id INTO header_id
    FROM contracts c
    WHERE c.tenant_id = p_tenant_id
      AND c.contract_number = 'C-' || sub.uuid
      AND c.customer_account_id = contract_header_customer_uuid(sub.customer_id)
      AND c.created_by IN ('V1_8_HEADER_BACKFILL', 'S7_HEADER_AT_CREATION');
    IF header_id IS NULL THEN
        RAISE EXCEPTION 'contract number already belongs to a different header' USING ERRCODE = '23505';
    END IF;

    UPDATE subscriptions
    SET contract_id = header_id
    WHERE id = sub.id
      AND contract_id IS NULL;
    RETURN header_id;
END;
$hdr$;

CREATE OR REPLACE FUNCTION contract_header_backfill_compensate()
RETURNS bigint
LANGUAGE plpgsql
AS $hdr$
DECLARE
    removed bigint;
BEGIN
    IF EXISTS (
        SELECT 1
        FROM contracts c
        JOIN amendment_documents d ON d.contract_id = c.id
        WHERE c.created_by = 'V1_8_HEADER_BACKFILL'
    ) OR EXISTS (
        SELECT 1
        FROM contracts c
        JOIN contracts child ON child.prior_contract_id = c.id
        WHERE c.created_by = 'V1_8_HEADER_BACKFILL'
    ) OR EXISTS (
        SELECT 1
        FROM contracts c
        JOIN subscriptions s ON s.contract_id = c.id
        WHERE c.created_by = 'V1_8_HEADER_BACKFILL'
          AND c.contract_number IS DISTINCT FROM ('C-' || s.uuid)
    ) THEN
        RAISE EXCEPTION 'header backfill refuses to compensate: restore from backup' USING ERRCODE = '23514';
    END IF;

    UPDATE subscriptions s
    SET contract_id = NULL
    FROM contracts c
    WHERE s.contract_id = c.id
      AND c.created_by = 'V1_8_HEADER_BACKFILL'
      AND c.contract_number = ('C-' || s.uuid);

    DELETE FROM contracts c
    WHERE c.created_by = 'V1_8_HEADER_BACKFILL'
      AND NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.contract_id = c.id)
      AND NOT EXISTS (SELECT 1 FROM amendment_documents d WHERE d.contract_id = c.id)
      AND NOT EXISTS (SELECT 1 FROM contracts child WHERE child.prior_contract_id = c.id);

    GET DIAGNOSTICS removed = ROW_COUNT;
    RETURN removed;
END;
$hdr$;

COMMENT ON FUNCTION contract_header_backfill_compensate() IS
    'Deletes only V1_8_HEADER_BACKFILL contracts after unlinking the matching subscription. Refuses when another row references them; restore from backup in that case.';

REVOKE ALL ON FUNCTION contract_header_customer_uuid(varchar) FROM PUBLIC;
REVOKE ALL ON FUNCTION contract_header_backfill_status(varchar) FROM PUBLIC;
REVOKE ALL ON FUNCTION contract_header_backfill_count(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION contract_header_backfill_apply(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION contract_header_at_creation(bigint, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION contract_header_backfill_compensate() FROM PUBLIC;
