# CAM V1_13 and V1_14 asset rollback runbook

> **DATA-DESTRUCTIVE — never run on shared, staging, or production data.**
> Both rollback scripts permanently delete every row in `asset_history` and `assets`.
> Use only against an authorized, isolated throwaway database or under an explicit,
> owner-approved recovery plan after full backup and reconciliation.

> **PROHIBITED AT SCHEMA VERSION `1.23` OR HIGHER (`V1_23+`):**
> **Do not run either `V1_14__drop_asset_history.sql` or `V1_13__drop_assets.sql` on any
> database migrated to CAM Flyway version `1.23` or higher** (current `main` is at `1.25`).
> `V1_23__asset_activation_receipts.sql` adds `asset_activation_receipts.asset_id REFERENCES
> assets (uuid)` (and `V1_24__asset_history_replay_events.sql` references
> `asset_activation_receipts`). Neither script is eligible at `1.23+`. Never add `CASCADE`
> to `DROP TABLE assets`.

This runbook links and documents the manual rollback scripts for the applied CAM asset
migrations (`V1_13` and `V1_14`) for residual DoD tracking (**DRG-924**, parent **DRG-652**).
It lifts **no** operational holds: the [asset write hold](asset-write-hold.md),
[S4 asset lifecycle hold](s4-asset-lifecycle-hold.md), and **DRG-657** (S7-class NO-GO)
remain in force.

## 1. Mandatory FK and schema-eligibility pre-check (run first, read-only)

Run this read-only pre-check **before** considering either rollback script. Confirm you are
connected to an **isolated, non-shared** database with a verified backup, and run the
[CAM historical asset reconciliation inventory](asset-reconciliation-inventory.md) first if
the schema has ever hosted asset or acceptance activity.

```sql
SET search_path TO contract;

-- 1. Confirm target database, schema, and highest applied CAM Flyway version
SELECT current_database(), current_schema();
SELECT installed_rank, version, description, success
FROM flyway_schema_history
WHERE version IN ('1.13', '1.14', '1.22', '1.23', '1.24', '1.25')
   OR (version ~ '^[0-9]+(\.[0-9]+)*$'
       AND string_to_array(version, '.')::int[] >= ARRAY[1, 23])
ORDER BY installed_rank;

-- 2. Check for any incoming foreign-key constraints referencing assets or asset_history
SELECT conrelid::regclass AS referencing_table,
       conname AS constraint_name,
       confrelid::regclass AS referenced_table,
       pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE contype = 'f'
  AND confrelid IN (
      to_regclass(current_schema() || '.assets'),
      to_regclass(current_schema() || '.asset_history')
  );

-- 3. Check current row counts in both target tables
SELECT count(*) AS asset_history_rows FROM asset_history;
SELECT count(*) AS asset_rows FROM assets;
```

### Mandatory stop conditions

- **STOP (prohibited at `1.23+`):** If `flyway_schema_history` shows any version `>= 1.23`
  **or** the `pg_constraint` query returns any referencing foreign key (such as
  `asset_activation_receipts.asset_id REFERENCES assets (uuid)`), **do not execute either
  `V1_14__drop_asset_history.sql` or `V1_13__drop_assets.sql`**. Both scripts are prohibited
  at schema `1.23+`.
- **STOP (non-empty tables):** If `asset_history_rows > 0` or `asset_rows > 0`, stop
  immediately unless an explicit, owner-approved data-destruction plan is recorded.

## 2. Partial destructive failure hazard and required atomic execution (eligible `<= 1.22` schemas only)

### Why running the two scripts separately is unsafe

`V1_14__drop_asset_history.sql` and `V1_13__drop_assets.sql` are unconditional
`DROP ... IF EXISTS` scripts. If an operator runs them as two separate commands/transactions
and `V1_13__drop_assets.sql` fails (for example, because of an incoming foreign key such as
`V1_23`'s `asset_activation_receipts.asset_id`, a lock failure, or insufficient privileges),
**`V1_14__drop_asset_history.sql` has already committed — permanently deleting
`asset_history` while leaving `assets` behind** in a partially destroyed, inconsistent state.

### Required atomic `psql` invocation (eligible `<= 1.22` isolated schemas only)

On an eligible schema (`<= 1.22`, zero incoming FKs, zero rows, isolated non-shared
database), run **both scripts in a single atomic `psql` transaction** with
`-1` (`--single-transaction`) and `-v ON_ERROR_STOP=1`, always ordering `V1_14` before
`V1_13`:

```bash
PGOPTIONS='-c search_path=contract' \
psql -X -1 -v ON_ERROR_STOP=1 \
  -f backend/services/contract-asset-management/src/main/resources/db/rollback/V1_14__drop_asset_history.sql \
  -f backend/services/contract-asset-management/src/main/resources/db/rollback/V1_13__drop_assets.sql
```

PostgreSQL DDL (`DROP INDEX`, `ALTER TABLE`, `DROP TABLE`) is transactional. With
`-1 -v ON_ERROR_STOP=1`, any error in `V1_13__drop_assets.sql` aborts immediately and rolls
back the `V1_14__drop_asset_history.sql` drop as well, preventing partial destructive failure.

### Destroyed objects and evidence

1. **`V1_14__drop_asset_history.sql`** (runs first) — permanently deletes all rows in
   `asset_history` along with indexes `idx_asset_history_successor`,
   `idx_asset_history_predecessor`, `idx_asset_history_type`, `idx_asset_history_account`,
   `idx_asset_history_asset` and constraints `chk_asset_history_type`,
   `uq_asset_history_event`, `asset_history_pkey`.
2. **`V1_13__drop_assets.sql`** (runs second) — permanently deletes all rows in `assets`
   along with indexes `idx_asset_subscription`, `idx_asset_contract_line`,
   `idx_asset_account` and constraints `chk_asset_status`,
   `uq_asset_subscription_contract_line`, `assets_subscription_id_fkey`, `assets_uuid_key`,
   `assets_pkey`. Neighboring tables (`subscriptions`, `document_apply_ledger`,
   `amendment_application_units`, `signature_envelopes`) remain intact.

## 3. Linked scripts and applied migrations

| Artifact | Path | Notes |
| --- | --- | --- |
| Applied migration (V1_13) | [`backend/services/contract-asset-management/src/main/resources/db/migration/V1_13__create_assets.sql`](../../backend/services/contract-asset-management/src/main/resources/db/migration/V1_13__create_assets.sql) | **Do not edit.** Flyway checksums include comments; editing applied migrations breaks validation on migrated databases. |
| Applied migration (V1_14) | [`backend/services/contract-asset-management/src/main/resources/db/migration/V1_14__create_asset_history.sql`](../../backend/services/contract-asset-management/src/main/resources/db/migration/V1_14__create_asset_history.sql) | **Do not edit.** Same Flyway checksum rule. |
| Rollback script (V1_14 — run first) | [`backend/services/contract-asset-management/src/main/resources/db/rollback/V1_14__drop_asset_history.sql`](../../backend/services/contract-asset-management/src/main/resources/db/rollback/V1_14__drop_asset_history.sql) | Landed in PR #1070 (`95c3514e`). Outside `classpath:db/migration` (not a Flyway version). Prohibited at `1.23+`. |
| Rollback script (V1_13 — run second) | [`backend/services/contract-asset-management/src/main/resources/db/rollback/V1_13__drop_assets.sql`](../../backend/services/contract-asset-management/src/main/resources/db/rollback/V1_13__drop_assets.sql) | Landed in PR #1070 (`95c3514e`). Outside `classpath:db/migration` (not a Flyway version). Prohibited at `1.23+`. |
| Unit guard test | [`backend/services/contract-asset-management/src/test/java/com/opencrm/contract/asset/AssetRollbackScriptTest.java`](../../backend/services/contract-asset-management/src/test/java/com/opencrm/contract/asset/AssetRollbackScriptTest.java) | Docker-free verification of order, `DATA-DESTRUCTIVE` headers, object drops, and exclusion of `db/rollback` from Flyway config. |
| Throwaway PostgreSQL 16 proof | [`backend/services/contract-asset-management/src/test/java/com/opencrm/contract/asset/AssetRollbackIntegrationTest.java`](../../backend/services/contract-asset-management/src/test/java/com/opencrm/contract/asset/AssetRollbackIntegrationTest.java) | Ephemeral `postgres:16-alpine` Testcontainer pinned to `1.22` (PR #1087, `8aeba98d`). |

## 4. Guard posture (difference from V1_11 / V1_12)

Unlike `V1_11__drop_renewal_policy_records.sql` and `V1_12__drop_document_apply_ledger.sql`
(which check `SELECT count(*)` inside a `DO $$` block and raise an exception if any row exists),
the `V1_14__drop_asset_history.sql` and `V1_13__drop_assets.sql` scripts merged in PR #1070
execute **unconditional** `DROP ... IF EXISTS` statements and do **not** modify
`flyway_schema_history`.

Because there is no in-script empty-table or `V1_23+` FK guard, operators must rely on the
mandatory pre-check (Section 1) and the single atomic `psql -X -1 -v ON_ERROR_STOP=1`
invocation (Section 2). Whether the DoD rubric accepts PR #1070 plus this runbook or requires
a follow-up ticket adding `DO $$` empty-table / FK guards remains an explicit owner gate
(`NEEDS_OWNER` on DRG-924).

## 5. Flyway history behavior

- Neither script deletes rows from `flyway_schema_history`.
- `flyway.repair()` does **not** remove successful `1.13` or `1.14` entries.
- **Never** run `flyway.clean()`: it drops the entire `contract` schema, including unrelated
  subscriptions and contract documents.
- In a **throwaway** `<= 1.22` database only, re-applying `V1_13` and `V1_14` after rollback
  requires deleting only `WHERE version IN ('1.13', '1.14')` from
  `contract.flyway_schema_history` and running Flyway with `outOfOrder=true` (as proven in
  `AssetRollbackIntegrationTest`).

## 6. Verification commands (isolated / throwaway only)

```bash
# Flyway ordering guard against origin/main
bash scripts/ci/check-flyway-order.sh origin/main

# Docker-free rollback script guard test
cd backend && ./mvnw -B -Djacoco.skip=true \
  -Dtest=AssetRollbackScriptTest \
  -pl services/contract-asset-management test

# Throwaway PostgreSQL 16 Testcontainers rollback + re-migrate proof (pinned to 1.22)
cd backend && ./mvnw -B -Djacoco.skip=true -DexcludedGroups= \
  -Dtest=AssetRollbackIntegrationTest \
  -pl services/contract-asset-management test
```
