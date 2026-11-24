-- psql -X --quiet --csv -v cam_schema=contract -f ops/asset-reconciliation.sql
\set ON_ERROR_STOP on
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL search_path TO pg_catalog, :"cam_schema";

WITH inventory_date AS (
    SELECT (transaction_timestamp() AT TIME ZONE 'UTC')::date AS utc_date
),
linked_subscriptions AS (
    SELECT s.id, s.status, s.customer_id, s.contract_line_id,
           l.accepted_line_uuid AS origin_line_id, l.quote_id AS origin_quote_id, l.product_external_id,
           r.activation_id, r.status AS receipt_status, r.asset_id AS receipt_asset_id,
           r.contract_id IS DISTINCT FROM c.id
               OR r.tenant_id IS DISTINCT FROM c.tenant_id
               OR r.customer_account_id IS DISTINCT FROM c.customer_account_id
               OR r.external_customer_id IS DISTINCT FROM s.customer_id
               OR r.quote_id IS DISTINCT FROM l.quote_id
               OR r.accepted_line_uuid IS DISTINCT FROM l.accepted_line_uuid
               OR r.product_ref IS DISTINCT FROM l.product_external_id AS receipt_identity_mismatch,
           CASE
               WHEN s.is_deleted THEN 'SUBSCRIPTION_SOFT_DELETED'
               WHEN l.id IS NULL THEN 'CANONICAL_LINE_MISSING'
               WHEN c.id IS NULL OR s.contract_id IS DISTINCT FROM c.id THEN 'CONTRACT_LINK_MISMATCH'
               WHEN s.tenant_id IS NULL THEN 'TENANT_MISSING'
               WHEN s.tenant_id IS DISTINCT FROM c.tenant_id OR l.tenant_id IS DISTINCT FROM c.tenant_id
                   THEN 'TENANT_MISMATCH'
               WHEN s.customer_account_id IS NULL THEN 'CUSTOMER_ACCOUNT_MISSING'
               WHEN s.customer_account_id IS DISTINCT FROM c.customer_account_id THEN 'CUSTOMER_ACCOUNT_MISMATCH'
               WHEN l.quote_id IS NULL OR btrim(l.quote_id) = ''
                   OR btrim(l.accepted_line_uuid) IN ('', 'legacy-aggregate') THEN 'QUOTE_LINE_PROVENANCE_MISSING'
               WHEN s.quote_id IS DISTINCT FROM l.quote_id THEN 'QUOTE_LINK_MISMATCH'
               WHEN btrim(s.customer_id) = '' OR btrim(l.product_external_id) = '' THEN 'ASSET_IDENTITY_INCOMPLETE'
           END AS identity_issue
    FROM subscriptions s
    LEFT JOIN contract_lines l ON l.id = s.contract_line_id
    LEFT JOIN contracts c ON c.id = l.contract_id
    LEFT JOIN asset_activation_receipts r ON r.subscription_id = s.id AND r.contract_line_id = l.id
),
acceptance_findings AS (
    SELECT 'ACCEPTANCE' AS kind,
           CASE
               WHEN q.is_deleted THEN 'ACCEPTANCE_LEDGER_SOFT_DELETED'
               WHEN s.identity_issue IS NOT NULL THEN s.identity_issue
               WHEN q.accepted_quote_id IS DISTINCT FROM s.origin_quote_id THEN 'ACCEPTANCE_NOT_CANONICAL_ORIGIN'
               WHEN s.activation_id IS NULL THEN 'ACTIVATION_RECEIPT_MISSING'
               WHEN s.receipt_identity_mismatch THEN 'ACTIVATION_RECEIPT_EVIDENCE_MISMATCH'
               WHEN s.receipt_status <> 'APPLIED' THEN 'ACTIVATION_RECEIPT_NOT_APPLIED'
           END AS reason,
           s.id AS subscription_id, s.contract_line_id, s.activation_id,
           s.receipt_asset_id AS asset_id, q.uuid AS evidence_id, s.origin_line_id AS source_line_id,
           s.status AS state, NULL::date AS effective_date, q.operation AS detail
    FROM applied_quote_acceptances q
    JOIN linked_subscriptions s ON s.id = q.subscription_id
),
accepted_line_findings AS (
    SELECT DISTINCT 'ACCEPTED_LINE', 'ACCEPTED_LINE_WITHOUT_CANONICAL_PROOF',
           s.id, s.contract_line_id, NULL::uuid, NULL::text,
           q.uuid, p.accepted_line_uuid, s.status, NULL::date, NULL::text
    FROM applied_quote_acceptances q
    JOIN subscription_rate_periods p
      ON p.subscription_id = q.subscription_id AND p.accepted_quote_id = q.accepted_quote_id
    JOIN linked_subscriptions s ON s.id = q.subscription_id
    WHERE NOT q.is_deleted AND NOT p.is_deleted
      AND (s.identity_issue IS NOT NULL
          OR q.accepted_quote_id IS DISTINCT FROM s.origin_quote_id
          OR p.accepted_line_uuid IS DISTINCT FROM s.origin_line_id)
),
asset_findings AS (
    SELECT 'ASSET',
           CASE
               WHEN a.contract_line_id IS DISTINCT FROM s.contract_line_id::text
                   AND (a.contract_line_id IN ('legacy-aggregate', s.origin_line_id) OR EXISTS (
                       SELECT 1
                       FROM subscription_rate_periods p
                       WHERE p.subscription_id = a.subscription_id AND p.accepted_line_uuid = a.contract_line_id
                   )) THEN 'LEGACY_ASSET_LINE_ID'
               WHEN s.identity_issue IS NOT NULL THEN 'ASSET_SUBSCRIPTION_IDENTITY_UNPROVEN'
               WHEN a.contract_line_id IS DISTINCT FROM s.contract_line_id::text THEN 'ASSET_CANONICAL_LINE_MISMATCH'
               WHEN a.account_id IS DISTINCT FROM s.customer_id THEN 'ASSET_ACCOUNT_MISMATCH'
               WHEN a.product_ref IS DISTINCT FROM s.product_external_id THEN 'ASSET_PRODUCT_MISMATCH'
               WHEN s.receipt_status IS DISTINCT FROM 'APPLIED' OR s.receipt_asset_id IS DISTINCT FROM a.uuid
                   THEN 'ASSET_APPLIED_RECEIPT_MISSING'
               WHEN s.receipt_identity_mismatch THEN 'ASSET_RECEIPT_EVIDENCE_MISMATCH'
               WHEN a.is_deleted THEN 'ASSET_SOFT_DELETED'
           END,
           a.subscription_id, s.contract_line_id, s.activation_id, a.uuid, a.uuid, a.contract_line_id,
           a.status, a.effective_start, s.identity_issue
    FROM assets a
    JOIN linked_subscriptions s ON s.id = a.subscription_id
),
receipt_findings AS (
    SELECT 'RECEIPT',
           CASE
               WHEN r.status = 'NEEDS_RECONCILIATION' THEN 'ACTIVATION_NEEDS_RECONCILIATION'
               WHEN r.status = 'PENDING' AND r.effective_start > d.utc_date THEN 'ACTIVATION_PENDING_FUTURE'
               WHEN r.status = 'PENDING' AND r.effective_end < d.utc_date THEN 'ACTIVATION_PENDING_ELAPSED'
               WHEN r.status = 'PENDING' THEN 'ACTIVATION_PENDING_DUE'
               WHEN NOT EXISTS (
                   SELECT 1
                   FROM asset_history h
                   WHERE h.event_id = r.created_event_id AND h.asset_id = r.asset_id AND h.event_type = 'CREATED'
                     AND h.subscription_id = r.subscription_id AND h.contract_line_id = r.contract_line_id::text
                     AND h.effective_date = r.effective_start
               ) THEN 'ACTIVATION_HISTORY_UNPROVEN'
           END,
           r.subscription_id, r.contract_line_id, r.activation_id, r.asset_id, r.created_event_id,
           r.accepted_line_uuid, r.status, r.effective_start, r.reconciliation_reason
    FROM asset_activation_receipts r
    CROSS JOIN inventory_date d
),
outbox_findings AS (
    SELECT 'OUTBOX', 'ASSET_OUTBOX_NOT_PUBLISHED', a.subscription_id, s.contract_line_id, s.activation_id,
           o.aggregate_id, o.uuid, a.contract_line_id, o.status, NULL::date, coalesce(o.topic, o.event_type)
    FROM outbox_events o
    LEFT JOIN assets a ON a.uuid = o.aggregate_id
    LEFT JOIN linked_subscriptions s ON s.id = a.subscription_id
    WHERE o.status <> 'PUBLISHED' AND NOT o.is_deleted
      AND coalesce(o.topic, o.event_type) IN (
          'cpq.asset.created', 'cpq.asset.audit', 'cpq.asset.amended',
          'cpq.asset.terminated', 'cpq.asset.superseded'
      )
),
findings AS (
    SELECT * FROM acceptance_findings
    UNION ALL SELECT * FROM accepted_line_findings
    UNION ALL SELECT * FROM asset_findings
    UNION ALL SELECT * FROM receipt_findings
    UNION ALL SELECT * FROM outbox_findings
)
SELECT findings.*, d.utc_date AS inventory_date
FROM findings
CROSS JOIN inventory_date d
WHERE reason IS NOT NULL
ORDER BY kind, subscription_id, contract_line_id, activation_id, asset_id, evidence_id, source_line_id, reason;

ROLLBACK;
