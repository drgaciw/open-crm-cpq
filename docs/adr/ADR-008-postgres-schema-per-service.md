# ADR-008: Single PostgreSQL database, schema-per-service isolation

**Status:** Accepted *(reverse-engineered from code 2026-09-19 — implemented since the first service waves; recorded retroactively)*
**Date (decision embedded):** ~2026-08 (foundation waves A–C) · **Documented:** 2026-09-19
**Deciders:** forge foundation waves (de facto) · **Documented by:** architecture reverse-engineering pass
**Provenance:** Reverse-engineered ADR — documents the landed data-isolation model. Does not authorize new schemas or a multi-DB split.

---

## Context

Eleven backend services in one Maven reactor need relational storage. The target-state plan envisioned Azure-hosted PostgreSQL with service-level databases, and legacy Frappe ran everything in one MariaDB 10.6. The landed compromise had to give services independent migration ownership (Flyway) without operating 11 database servers locally.

Verified on the tree (2026-09-19):

- `backend/scripts/init-db.sql` — **one database `opencrm`**, schemas `crm, cpq, pricing, product, quote, communication, ai`; extensions `uuid-ossp` + `pg_trgm`; **pgvector commented out**; bootstrap role `opencrm_app` with schema-scoped grants.
- Shared-database defaults align JDBC schema, Hibernate schema and Flyway history. Configuration-engine, approval-routing, document-generation and contract-asset-management use `configuration`, `approval`, `document` and `contract`; existing standalone installations retain explicit `DB_NAME` and `DB_SCHEMA=public` until a reviewed data move.
- `docker-compose.yml` — single `postgres:16-alpine` + `redis:7-alpine`; compose sets `SPRING_FLYWAY_SCHEMAS` per service.
- Hibernate runs `ddl-auto: validate` — schema drift fails fast; all DDL flows through Flyway (`V{version}__{description}.sql`).
- Pricing-service owns migrations for the shared `pricing` schema, including price-book tables. Pricebook-service intentionally keeps Flyway disabled. See [migration ownership and compatibility](../database-schema-alignment.md).

## Decision

**Operate one shared PostgreSQL instance and one database (`opencrm`). Each service owns exactly one PostgreSQL schema, its Flyway history, and all tables in it. No cross-schema table access — service integration happens over HTTP (gateway/client) or Kafka events, never by joining another service's tables.**

1. Schema names: `crm` (crm-service), `pricing` (pricing-service **and** pricebook-service — shared schema name, distinct table sets), `product`, `quote`; `cpq`, `communication`, `ai` reserved/used per service (ai-service Hikari `schema: ai`).
2. Flyway per owned schema (`spring.flyway.schemas` + `classpath:db/migration`); pricing-service is the sole migration owner for both pricing and pricebook. Hibernate uses `ddl-auto: validate`.
3. Redis 7 is the shared cache/rate-limit tier (quote 10-min TTL cache; gateway Redis rate limiter).
4. pgvector stays disabled until the RAG wave needs it.
5. Cross-schema reads are forbidden; the approvals dual-read ([ADR-005](ADR-005-approvals-legacy-dual-read.md)) is a same-schema legacy-table exception inside quote-service, not a cross-service read.

## Options considered

| Option | Verdict |
|--------|---------|
| **A. One DB, schema-per-service (landed)** | Flyway isolation + ownership per service; trivial local dev (one container); cheap schema-scoped grants. Con: shared instance is a single failure domain; noisy-neighbor risk; schema name `pricing` reused by two services. |
| B. Database-per-service | Existing standalone configurations remain supported through explicit overrides; the default installation uses shared `opencrm`. Consolidating existing records is a separate reviewed operation. |
| C. Single shared schema (legacy Frappe style) | No ownership boundaries; Flyway conflicts; rejected — contradicts microservice ownership model. |

## Consequences

- **Positive:** Services migrate independently; local stack is one container; backup/restore story is simple pre-production; `ddl-auto: validate` makes drift visible in tests.
- **Negative / residual:** Shared PostgreSQL is a common failure domain. Pricing/pricebook require one migration owner. Existing standalone databases must be pinned to their previous connection/schema until a rehearsed migration and rollback are approved.
- **Migration path:** Moving any service to its own database later is a connection-string + schema dump exercise because ownership is already schema-clean.

## Verification evidence (2026-09-19)

- `backend/scripts/init-db.sql` (schemas, grants, pgvector comment).
- `backend/services/quote-service/src/main/resources/application.yml:11` (`currentSchema=quote`), `:30` (`flyway.schemas: quote`), `:19` (`ddl-auto: validate`).
- `backend/docker-compose.yml` (`postgres:16-alpine`, per-service `SPRING_DATASOURCE_URL ... currentSchema=...`, `SPRING_FLYWAY_SCHEMAS`).
- `backend/services/ai-service/src/main/resources/application.yml` (Hikari + Hibernate `default_schema: ai`).

## References

- [docs/ARCHITECTURE.md §6](../ARCHITECTURE.md) — Data architecture
- [docs/adr/ADR-005-approvals-legacy-dual-read.md](ADR-005-approvals-legacy-dual-read.md) — sanctioned same-schema exception
- [docs/adr/ADR-011-shared-maven-libs-published-language.md](ADR-011-shared-maven-libs-published-language.md) — BaseEntity persistence conventions
