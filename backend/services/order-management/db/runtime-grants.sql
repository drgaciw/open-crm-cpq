-- Run as the actual migration/schema owner AFTER every migration, with ON_ERROR_STOP.
-- Provision oms_app and its authentication externally; this script contains no credentials.
BEGIN;
DO $$
DECLARE runtime_oid oid;
BEGIN
 SELECT oid INTO runtime_oid FROM pg_roles WHERE rolname='oms_app';
 IF runtime_oid IS NULL THEN RAISE EXCEPTION 'Provision the separate oms_app runtime role first'; END IF;
 IF EXISTS (SELECT 1 FROM pg_roles WHERE oid=runtime_oid AND
   (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR rolinherit))
   OR EXISTS (SELECT 1 FROM pg_auth_members WHERE member=runtime_oid) THEN
   RAISE EXCEPTION 'oms_app must have no elevated attributes or role memberships';
 END IF;
 IF current_user='oms_app' OR NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='order_management' AND nspowner=(SELECT oid FROM pg_roles WHERE rolname=current_user)) THEN
   RAISE EXCEPTION 'Execute as the actual order_management schema/migration owner';
 END IF;
 IF EXISTS (SELECT 1 FROM pg_class WHERE relowner=runtime_oid)
   OR EXISTS (SELECT 1 FROM pg_proc WHERE proowner=runtime_oid)
   OR EXISTS (SELECT 1 FROM pg_namespace WHERE nspowner=runtime_oid)
   OR EXISTS (SELECT 1 FROM pg_database WHERE datdba=runtime_oid) THEN
   RAISE EXCEPTION 'Runtime role must not own database objects';
 END IF;
 IF EXISTS (SELECT 1 FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid
   JOIN pg_namespace n ON n.oid=c.relnamespace CROSS JOIN LATERAL aclexplode(a.attacl) x
   WHERE n.nspname='order_management' AND x.grantee=runtime_oid
     AND NOT (c.relname='snapshot_inbox' AND a.attname='snapshot_id'
       AND x.privilege_type='UPDATE' AND NOT x.is_grantable)) THEN
   RAISE EXCEPTION 'Remove preexisting runtime column grants before applying this policy';
 END IF;
END $$;

REVOKE ALL ON SCHEMA order_management FROM PUBLIC, oms_app;
GRANT USAGE ON SCHEMA order_management TO oms_app;
REVOKE ALL ON ALL TABLES IN SCHEMA order_management FROM PUBLIC, oms_app;
GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA order_management TO oms_app;
-- Migration metadata is not an application ledger.
REVOKE ALL ON order_management.flyway_schema_history FROM oms_app;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA order_management FROM PUBLIC, oms_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA order_management TO oms_app;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA order_management FROM PUBLIC, oms_app;

-- Append-only evidence, snapshots, finance, receipts, lineage and audit receive no UPDATE.
-- Only the shared relay retention job needs DELETE, on published outbox rows.
-- No runtime table receives TRUNCATE, REFERENCES, TRIGGER or ownership.
GRANT UPDATE ON order_management.orders, order_management.order_lines,
 order_management.order_tasks, order_management.order_holds, order_management.outbox_events,
 order_management.task_operations, order_management.order_exceptions,
 order_management.fulfillment_evidence_state, order_management.allocation_lots TO oms_app;
GRANT DELETE ON order_management.outbox_events TO oms_app;
-- PostgreSQL row locking requires UPDATE on at least one column. Conversion locks the
-- source row; this identity-only grant permits FOR UPDATE, but its immutable trigger
-- rejects every UPDATE (including no-op updates). Payload/commercial columns stay denied.
GRANT UPDATE(snapshot_id) ON order_management.snapshot_inbox TO oms_app;
DO $$ BEGIN
 IF to_regclass('order_management.recovery_daily_runs') IS NOT NULL THEN
   GRANT UPDATE ON order_management.recovery_daily_runs TO oms_app;
 END IF;
END $$;

-- Defaults apply only to objects subsequently created by this migration owner.
-- Clear global defaults too: schema-local REVOKE cannot remove globally inherited ACLs.
ALTER DEFAULT PRIVILEGES REVOKE ALL ON TABLES FROM PUBLIC, oms_app;
ALTER DEFAULT PRIVILEGES REVOKE ALL ON SEQUENCES FROM PUBLIC, oms_app;
ALTER DEFAULT PRIVILEGES REVOKE ALL ON FUNCTIONS FROM PUBLIC, oms_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA order_management REVOKE ALL ON TABLES FROM PUBLIC, oms_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA order_management GRANT SELECT, INSERT ON TABLES TO oms_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA order_management REVOKE ALL ON SEQUENCES FROM PUBLIC, oms_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA order_management GRANT USAGE ON SEQUENCES TO oms_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA order_management REVOKE ALL ON FUNCTIONS FROM oms_app;
COMMIT;
