# Database defaults and migration ownership

The default installation uses the `opencrm` PostgreSQL database. JDBC `currentSchema`, Hibernate `default_schema`, and Flyway `schemas` / `default-schema` agree for each service:

| Service | Default schema | Migration owner |
| --- | --- | --- |
| configuration-engine | `configuration` | configuration-engine |
| approval-routing | `approval` | approval-routing |
| document-generation | `document` | document-generation |
| contract-asset-management | `contract` | contract-asset-management |
| pricing-service and pricebook-service | `pricing` | **pricing-service only** |

Each of the first four services accepts a service-scoped `DB_SCHEMA` override as well as the existing `DB_NAME`, `DB_HOST`, `DB_PORT`, `DB_USERNAME`, and `DB_PASSWORD` settings. Keep overrides aligned if setting Spring datasource/JPA/Flyway properties directly. Hibernate continues to validate; Flyway owns DDL. Each service has a separate `flyway_schema_history` and `outbox_events` table. Existing migration files and checksums are unchanged.

Pricebook shares the `pricing` schema intentionally. Pricing-service's migrations create and evolve price-book tables, so run those migrations before starting pricebook-service. Pricebook-service keeps Flyway disabled; adding another history or enabling a second migration owner is not part of this alignment.

## Existing installations

Changing connection defaults does **not** move existing records. Before deploying these defaults to an existing installation, retain the old database and schema explicitly for each service:

| Service | Retain `DB_NAME` | Retain `DB_SCHEMA` |
| --- | --- | --- |
| configuration-engine | `cpq_configuration` | `public` |
| approval-routing | `cpq_approval` | `public` |
| document-generation | `cpq_document` | `public` |
| contract-asset-management | `cpq_contract` | `public` |

Use the actual deployed database/schema if previously customized. Set these variables on each service, not globally across all services. Preserve the existing Flyway history with its records and checksums. Automatic baselining is disabled: an unmanaged nonempty schema fails migration rather than silently being accepted. Installations without valid migration history require a separate reviewed baseline/reconciliation procedure.

To consolidate existing data later, use a separately scheduled maintenance operation: back up each database; stop service writers/consumers; restore into an isolated rehearsal database with each service's tables, sequences, constraints, indexes and complete Flyway history assigned to its target schema; compare row counts and sequence values; validate Flyway checksums and Hibernate mappings; exercise read/write and event processing; then approve and perform the cutover. Retain the original databases and a rollback connection configuration. Do not point an existing service at a new empty schema and interpret an empty response as a successful migration. This change runs no data migration or cutover.

The PostgreSQL init script only runs automatically for new container volumes. Existing volumes require schema provisioning through the normal deployment/migration process; restarting a container does not rerun bootstrap SQL. Flyway's default schema creation supports a fresh service schema when its migration credentials have permission. A runtime-only account without DDL privileges needs separately provisioned schemas and migrations.

## Verification

From `backend`, with Docker running:

```powershell
./mvnw.cmd -pl services/configuration-engine -am -Dtest=ServiceSchemaIsolationIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The test uses a disposable PostgreSQL 16 container and reads the four real service configurations and SQL migration directories. It verifies isolated tables/outboxes/history, repeat migration validation, legacy database/public-schema overrides retaining existing records, and refusal to baseline unknown data. It never connects to the developer's database. SQL checksums are preserved by leaving all existing migrations unchanged.
