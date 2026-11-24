# CAM historical asset reconciliation inventory

Run this inventory before the held asset rollout in an authorized nonproduction CAM database. The database
must have the asset-activation receipt migration `V1_23`. Use a database role with schema usage and SELECT on
`subscriptions`, `contract_lines`, `contracts`, `applied_quote_acceptances`, `subscription_rate_periods`,
`assets`, `asset_activation_receipts`, `asset_history`, and `outbox_events`. The report covers the selected
schema, including rows with missing tenant or account links, so the operator needs authorization for that
database-wide scope.

Connect through the approved secure PostgreSQL endpoint. Provide connection settings through the normal psql
environment and authentication mechanism, and set `cam_schema` to the selected CAM schema.

```bash
psql -X --quiet --csv -v cam_schema=contract \
  -f backend/services/contract-asset-management/ops/asset-reconciliation.sql
```

The file uses a repeatable-read, read-only transaction. Keep output in the authorized reconciliation workspace
because the CSV contains internal business IDs. Record the database environment and schema, migration
version, report UTC date, finding counts, and approved disposition for each finding. If psql fails, retain the
error and rerun after correcting the connection, schema, grants, or migration. A failed command gives no
reconciliation result.

| Finding | Evidence to review |
| --- | --- |
| `ACCEPTANCE` | An applied-quote ledger entry lacks matching canonical line or applied activation evidence. Check the actual agreement, quote, typed account, tenant provenance, and line. `evidence_id` identifies the stored acceptance row. |
| `ACCEPTED_LINE` | A distinct retained quote-rate line lacks the proven canonical link. Multiple ramp periods on one line are grouped. `source_line_id` identifies the stored quote-line reference. |
| `ASSET` | Review a legacy line ID, conflicting link/account/product, missing applied receipt, or deleted asset. `asset_id` identifies the affected row and `source_line_id` shows its stored line reference. |
| `RECEIPT` | Inspect future, due, or elapsed pending work, a reconciliation reason, or an applied receipt with incomplete created-history evidence. `activation_id` identifies the receipt. A due receipt may require an examination of the worker and its approval state. |
| `OUTBOX` | Inspect retained asset events with a status other than PUBLISHED, including deliberate SKIPPED decisions. `evidence_id` is the outbox event ID and `detail` is its topic. |

Each row gives the first applicable reason for that item. A subscription can contribute findings in several
categories. A different historical quote associated with the same subscription requires comparison to the
actual historical contract changes. Matching a SKU or an external account string alone cannot establish a
canonical line or trusted tenant.

Compare the results to authoritative source records and the retained publication archive. The snapshot
covers acceptance ledger rows still present in CAM and nonpublished outbox rows retained in the selected
schema. Outbox retention and deleted or unavailable source records require separate evidence. A clean CSV
on a synthetic database verifies the procedure only. Release still requires authorized historical customer
reconciliation and the remaining lifecycle and S4 prerequisites. [Asset write hold](asset-write-hold.md),
[S4 hold](s4-asset-lifecycle-hold.md), [Initial asset activation](effective-asset-activation.md).
