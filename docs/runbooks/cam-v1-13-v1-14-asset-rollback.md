# CAM V1_13 and V1_14 asset rollback runbook

> **DATA-DESTRUCTIVE — never run on shared, staging, or production data.**
> Both rollback scripts permanently delete every row in `asset_history` and `assets`.
> Use only against an authorized, isolated throwaway database or under an explicit,
> owner-approved recovery plan after full backup and reconciliation.

This runbook links and documents the manual rollback scripts for the applied CAM asset
migrations (`V1_13` and `V1_14`) for residual DoD tracking (**DRG-924**, parent **DRG-652**).
It lifts **no** operational holds: the [asset write hold](asset-write-hold.md),
[S4 asset lifecycle hold](s4-asset-lifecycle-hold.md), and **DRG-657** (S7-class NO-GO)
remain in force.

## Linked scripts and applied migrations

| Artifact | Path | Notes |
| --- | --- | --- |
| Applied migration (V1_13) | [`backend/services/contract-asset-management/src/main/resources/db/migration/V1_13__create_assets.sql`](../../backend/services/contract-asset-management/src/main/resources/db/migration/V1_13__create_assets.sql) | **Do not edit.** Flyway checksums include comments; editing applied migrations breaks validation on migrated databases. |
| Applied migration (V1_14) | [`backend/services/contract-asset-management/src/main/resources/db/migration/V1_14__create_asset_history.sql`](../../backend/services/contract-asset-management/src/main/resources/db/migration/V1_14__create_asset_history.sql) | **Do not edit.** Same Flyway checksum rule. |
| Rollback script (V1_14 — run first) | [`backend/services/contract-asset-management/src/main/resources/db/rollback/V1_14__drop_asset_history.sql`](../../backend/services/contract-asset-management/src/main/resources/db/rollback/V1_14__drop_asset_history.sql) | Landed in PR #1070 (`95c3514e`). Outside `classpath:db/migration` (not a Flyway version). |
| Rollback script (V1_13 — run second) | [`backend/services/contract-asset-management/src/main/resources/db/rollback/V1_13__drop_assets.sql`](../../backend/services/contract-asset-management/src/main/resources/db/rollback/V1_13__drop_assets.sql) | Landed in PR #1070 (`95c3514e`). Outside `classpath:db/migration` (not a Flyway version). |
| Unit guard test | [`backend/services/contract-asset-management/src/test/java/com/opencrm/contract/asset/AssetRollbackScriptTest.java`](../../backend/services/contract-asset-management/src/test/java/com/opencrm/contract/asset/AssetRollbackScriptTest.java) | Docker-free verification of order, `DATA-DESTRUCTIVE` headers, object drops, and exclusion of `db/rollback` from Flyway config. |
| Throwaway PostgreSQL 16 proof | [`backend/services/contract-asset-management/src/test/java/com/opencrm/contract/asset/AssetRollbackIntegrationTest.java`](../../backend/services/contract-asset-management/src/test/java/com/opencrm/contract/asset/AssetRollbackIntegrationTest.java) | Ephemeral `postgres:16-alpine` Testcontainer pinned to `1.22` (PR #1087, `8aeba98d`). |

## Guard posture (important difference from V1_11 / V1_12)

Unlike `V1_11__drop_renewal_policy_records.sql` and `V1_12__drop_document_apply_ledger.sql`
(which check `SELECT count(*)` inside a `DO $$` block and raise an exception if any row exists),
the `V1_14__drop_asset_history.sql` and `V1_13__drop_assets.sql` scripts merged in PR #1070
execute **unconditional** `DROP INDEX IF EXISTS` / `ALTER TABLE IF EXISTS ... DROP CONSTRAINT IF EXISTS` /
`DROP TABLE IF EXISTS` statements and do **not** modify `flyway_schema_history`.

Because there is no in-script empty-table guard, the operator **must** perform the read-only
pre-checks below before executing either script, and must never point these scripts at a shared
or live database. Whether the DoD rubric accepts PR #1070's `DATA-DESTRUCTIVE` scripts plus this
runbook or requires a follow-up PR adding `DO $$` empty-table guards remains an explicit owner
decision (`NEEDS_OWNER` on DRG-924).

## Read-only pre-checks (required before any run)

1. Confirm you are connected to an **isolated, non-shared** database with a verified backup, and
   run the [CAM historical asset reconciliation inventory](asset-reconciliation-inventory.md) first
   if the schema has ever hosted asset or acceptance activity.
2. Inspect the target schema (`contract` unless `DB_SCHEMA` overrides) without writing:

   ```sql
   SET search_path TO contract;

   SELECT current_database(), current_schema();
   SELECT count(*) AS asset_history_rows FROM asset_history;
   SELECT count(*) AS asset_rows FROM assets;
   SELECT version, description, success
   FROM flyway_schema_history
   WHERE version IN ('1.13', '1.14', '1.23', '1.24', '1.25')
   ORDER BY installed_rank;
   ```

3. **Check `V1_23+` foreign-key dependency:** `V1_23__asset_activation_receipts.sql` defines
   `asset_activation_receipts.asset_id REFERENCES assets (uuid)`. On any schema migrated to
   `V1_23` or higher, `V1_13__drop_assets.sql` will fail with a foreign-key dependency error
   unless `V1_23` (and `V1_24__asset_history_replay_events.sql`, which references
   `asset_activation_receipts`) are not applied or have been separately retired under an approved
   plan. Never add `CASCADE` to `DROP TABLE assets`.
4. Stop immediately if `asset_history_rows > 0` or `asset_rows > 0` unless an explicit,
   owner-approved data-destruction plan is recorded.

## Execution order and destroyed evidence

Always run **`V1_14` before `V1_13`** in the CAM schema (`SET search_path TO contract;`):

1. **`V1_14__drop_asset_history.sql`** — permanently deletes all rows in `asset_history` along
   with indexes `idx_asset_history_successor`, `idx_asset_history_predecessor`,
   `idx_asset_history_type`, `idx_asset_history_account`, `idx_asset_history_asset` and
   constraints `chk_asset_history_type`, `uq_asset_history_event`, `asset_history_pkey`.
2. **`V1_13__drop_assets.sql`** — permanently deletes all rows in `assets` along with indexes
   `idx_asset_subscription`, `idx_asset_contract_line`, `idx_asset_account` and constraints
   `chk_asset_status`, `uq_asset_subscription_contract_line`, `assets_subscription_id_fkey`,
   `assets_uuid_key`, `assets_pkey`. Neighboring tables (`subscriptions`,
   `document_apply_ledger`, `amendment_application_units`, `signature_envelopes`) remain intact.

## Flyway history behavior

- Neither script deletes rows from `flyway_schema_history`.
- `flyway.repair()` does **not** remove successful `1.13` or `1.14` entries.
- **Never** run `flyway.clean()`: it drops the entire `contract` schema, including unrelated
  subscriptions and contract documents.
- In a **throwaway** database only, re-applying `V1_13` and `V1_14` after rollback requires
  deleting only `WHERE version IN ('1.13', '1.14')` from `contract.flyway_schema_history` and
  running Flyway with `outOfOrder=true` (as proven in `AssetRollbackIntegrationTest`).

## Verification commands (isolated / throwaway only)

```bash
# Flyway ordering guard against origin/main
bash scripts/ci/check-flyway-order.sh origin/main

# Docker-free rollback script guard test
cd backend && ./mvnw -B -Djacoco.skip=true \
  -Dtest=AssetRollbackScriptTest \
  -pl services/contract-asset-management test

# Throwaway PostgreSQL 16 Testcontainers rollback + re-migrate proof
cd backend && ./mvnw -B -Djacoco.skip=true -Dgroups=integration \
  -Dtest=AssetRollbackIntegrationTest \
  -pl services/contract-asset-management test
```
