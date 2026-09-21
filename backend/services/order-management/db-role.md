# Separate runtime and migration identities

The application must connect as `oms_app`, never the migration owner or a DBA.
Provision a dedicated migration owner for `order_management`; run Flyway as that owner.
Do not share this owner with other services: its global future table/sequence/function privileges
are reset here before the OMS schema-specific defaults are granted.
The Dockerfile uses the neighboring services' Java 25 Alpine runtime and an unprivileged OS user.
The OS user does not replace the database role boundary.

A DBA provisions the runtime role once, with authentication supplied by the deployment secret manager:

```sql
CREATE ROLE oms_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
  NOINHERIT NOREPLICATION NOBYPASSRLS;
```

No password belongs in this script, repository, shell history or image. Configure authentication
externally. Give this identity database CONNECT if the database's PUBLIC CONNECT privilege was
removed. Give it no role memberships, schema ownership, public schema CREATE, or privileges in
other service schemas. The script rejects elevated attributes, role memberships, object ownership
and unexpected preexisting runtime or PUBLIC column grants; DBAs remain responsible for the wider
database policy. PUBLIC has no identity-lock exception: any PUBLIC column ACL is rejected before
grants are changed. This preflight is defense in depth: PostgreSQL's table-level REVOKE also removes
column privileges, which the integration test verifies with a seeded PUBLIC receipt-column grant.

After **every** migration, connect as the actual schema/migration owner and run:

```sh
psql "$OMS_MIGRATION_DSN" -v ON_ERROR_STOP=1 -f db/runtime-grants.sql
```

Use secret-backed connection settings; do not put credentials in the DSN shown in logs. All OMS
objects must be created by this same migration identity. The script is transactional and repeatable.
It removes existing runtime/PUBLIC table privileges, grants schema USAGE and table SELECT/INSERT,
then permits UPDATE only on the listed mutable operational tables. It excludes Flyway history.
It grants sequence USAGE for outbox IDs, never sequence UPDATE. Only `outbox_events` receives DELETE,
for the shared relay's published-event retention cleanup; the relay selects eligible published rows.
No table receives TRUNCATE or TRIGGER privileges. Existing immutable commercial-field triggers remain in force on mutable orders.
Future tables get SELECT/INSERT only; reviewed mutable additions need an explicit UPDATE grant.
The optional `recovery_daily_runs` table receives UPDATE only when present after its migration.

One narrow lock privilege is required: `UPDATE(snapshot_id)` on `snapshot_inbox`. PostgreSQL requires
UPDATE on at least one column for the existing conversion `SELECT ... FOR UPDATE`. The immutable
trigger rejects every UPDATE, even an unchanged identity; runtime cannot disable or replace it.
This grants no payload/commercial-column UPDATE and preserves the existing concurrency protocol.

Configure separate runtime and Flyway connection identities in deployment. This change does not
wire those secrets, run grants on an existing environment, or prove production role isolation.
Never grant the runtime role membership in the migration role. Migration credentials should not
remain available to the running application when a separate migration job is used.

`DbRoleIntegrationTest` uses a fresh PostgreSQL container and SET ROLE on every runtime connection,
then exercises actual snapshot intake, order conversion, submit and read. It verifies denied audit,
snapshot, receipt and finance mutation, denied trigger bypass/DDL, future append-only defaults, and
actual V5 recovery assignment and daily reconciliation under the runtime role, plus rejection of
PUBLIC receipt and snapshot-identity column grants. Run with Docker using `./mvnw.cmd -pl services/order-management
-am test -Dtest=DbRoleIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false` from `backend`.
