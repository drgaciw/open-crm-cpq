# CRM field registry publication

CRM Flyway V21 installs the typed definition, option, and value tables from [ADR-031](../adr/ADR-031-extensible-field-registry-and-saved-views.md). V22 adds constraints for existing and new registry data. The service continues returning `FIELD_REGISTRY_NOT_READY` for field registry requests outside its integration fixtures. See the [main remediation runbook](main-remediation-runbook.md) for release ownership.

## Schema rollout

On the intended CRM database, run the [operator preflight](../../backend/services/crm-service/src/main/ops/field-registry-v21-preflight.sql) from the repository root. Set `PGSERVICE` to the environment's configured libpq service, with read access to the Flyway history and registry tables.

```bash
PGSERVICE=your-crm-service psql -X --no-password \
  --file backend/services/crm-service/src/main/ops/field-registry-v21-preflight.sql
```

The command runs in a read-only transaction. Check the exit code and the status printed by `psql`.

| Status | Interpretation |
| --- | --- |
| `CRM_V21_READY_TO_MIGRATE` | Successful V20 history and all three registry table names are free. |
| `CRM_V22_READY_TO_MIGRATE` | The official V21 history and registry tables are present, and existing registry data satisfies the proposed constraints. |
| `CRM_V22_ALREADY_APPLIED` | The official V21 and V22 scripts and checksums match the applied history. |
| `CRM_V21_BLOCKED_RETIRED_CANDIDATE` | The alternate `V21__create_field_registry.sql` was applied. Use a fresh database for disposable test runs. Ask the database owner to reconcile durable data and schema before retrying. |
| `CRM_V21_BLOCKED_OBJECT_COLLISION` | A table, view, or type occupies a registry name without V21 history. Review its ownership and data with the CRM and database owners. |
| `CRM_V21_BLOCKED_NO_V20_HISTORY` | The CRM Flyway history table or its expected successful V20 entry is absent. Verify the target and schema history. |
| `CRM_V21_BLOCKED_HISTORY_MISMATCH` | Migration history disagrees with the pinned versions or contains a failed or unexpected entry. Review the full Flyway history. |
| `CRM_V21_BLOCKED_TABLES_INCOMPLETE` | The V21 entry matches but a registry table is missing or has an unexpected relation type. Review the schema against the pinned migration. |
| `CRM_V22_BLOCKED_INVALID_REGISTRY_DATA` | Existing definitions or values would violate V22. Use the reported counts to coordinate an owner-approved reconciliation. |

Stop the rollout on a nonzero exit and preserve existing objects and history for investigation. For V20 or V21 upgrades, drain CRM and other registry writers and repeat the preflight before starting migrations. V22 adds stored generated columns and validates constraints. Allow a maintenance window based on the target table sizes and the locks these operations require. After startup, confirm `CRM_V22_ALREADY_APPLIED`, full Flyway validation, CRM health, and read-back of existing CRM records. Fresh installs use the normal CRM Flyway startup path.

One value references one entity row and one storage class. Foreign keys keep the value's entity and storage class aligned with its definition and bind option values to their own definition. Text storage supports the registry's full long-text limit. Its index contains a bounded prefix, so any future text filter or sort that depends on the remainder must compare the full value.

## Previous binary rehearsal

Use isolated PostgreSQL and Redis instances. Start the previous CRM binary, populate synthetic organization, contact, and deal records, and capture their API responses. Stop it, start the release candidate against the same database, and verify schema history, health, and record read-back. Stop the candidate and restart the previous binary against the migrated schema. Verify its view of the records and that the field registry guard still reports `FIELD_REGISTRY_NOT_READY`.

The 2026-11-23 rehearsal used the retired V21 candidate on PostgreSQL 16.15 with Redis 7 and a previous CRM JAR built from `cf767f29e9c89ee2d9feeea4074a889569ddaf00`. All three starts reported `UP`, returned the same synthetic organization, contact, and deal data over HTTP, and returned `503 FIELD_REGISTRY_NOT_READY`. Repeat the rehearsal with the official V21 and V22 artifacts selected for the target rollout.

## Authority required before publication

CRM and Identity owners need to approve the customer-tenant authority and scoped editor policy. The workforce Entra issuer and `tid` establish authentication, and the coarse roles and current role-grant tables have no explicit customer-tenant binding. CRM records expose record identifiers, account relationships, and owner IDs. [ADR-036](../adr/ADR-036-account-tenant-ownership.md) documents an account-to-tenant proposal whose authority and binding protocol are still open. [DRG-306](https://linear.app/drgamtd-workspace/issue/DRG-306/bc-19-02-provision-tenant-and-enforce-tenancy-isolation)

1. Identify the system that verifies customer-tenant existence and the system that grants the authenticated actor access to that tenant. Define distinct privileges for definition and option administration, definition reads, and record-value reads and writes. The query and body can identify a CRM record or definition. Resolve the authorization scope from server-verified context.
2. Define the trusted ownership of organization, contact, and deal records, including records without a verified account link. Require a current ownership decision for every value operation and bind it to the read or write transaction. Define how relinking, deletion, and merges handle both authorization and retained values.
3. Reconcile existing CRM records with authoritative assignments. Decide how to verify user-reference values against tenant membership. Deny unresolved or stale assignments. Route any future agent writes through the fact ledger once its tenant boundary is verified.
4. Use the designated signed-token environment to prove allowed operations, missing membership, unauthorized tenant and record access, actor attribution, archive and typed-value behavior, and concurrent ownership changes. Exercise both the gateway path and the CRM service boundary before removing the publication guard.
