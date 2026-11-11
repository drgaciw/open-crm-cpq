# DRG-664 — Subscription end-date rule and PERPETUAL contract `end_date`

**Status:** Accepted story (S-T3); this note records the documented semantics and the
CHECK-change fork that remains **Proposed / NO-GO**.

**Issue:** [DRG-664](https://linear.app/drgamtd-workspace/issue/DRG-664).
**Parent ADR:** [DRG-661](https://linear.app/drgamtd-workspace/issue/DRG-661) (subscription taxonomy) — Accepted.
**Related:** [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) (maintenance linkage),
[DRG-574](https://linear.app/drgamtd-workspace/issue/DRG-574) (perpetual surrender).

## Documented date semantics (AC1)

| Field | Meaning |
|---|---|
| `subscriptions.end_date` | **Current committed period end.** Remains `NOT NULL`. Evergreen / auto-roll advances the period through the RENEWAL path; it does not leave this column null. |
| PERPETUAL contract `contracts.end_date` | **Maintenance coverage end** for the linked maintenance obligation. Perpetual licence rights live on the asset / entitlement, not on the subscription row. |

These meanings are documentation only in this story. They authorize no silent rewrite of existing rows.

## Additive `renewal_mode` (AC2)

CAM Flyway `V1_21__subscription_renewal_mode.sql` adds nullable
`subscriptions.renewal_mode` with check `AUTO_ROLL | MANUAL` (null allowed).

| Value | Behaviour in this slice |
|---|---|
| `NULL` / unknown token | Fail-safe: **today's renewal behaviour** — no auto-roll (AC5). |
| `MANUAL` | No auto-roll. |
| `AUTO_ROLL` | Intent recorded. Quote-accepted renewal math is unchanged (AC4); unattended auto-roll execution is out of scope here. |

Guarded rollback: `db/rollback/V1_21__drop_subscription_renewal_mode.sql`.

### Wave 2 slot map

| Draft | Slot |
|---|---|
| #805 DRG-665 | V1_16, V1_17 |
| #806 DRG-662 | V1_18 |
| #808 DRG-666 | V1_19 |
| DRG-663 (in flight) | V1_20 |
| **This story DRG-664** | **V1_21** |

## `chk_contracts_dates` / PERPETUAL null end — Proposed / NO-GO (AC3)

V1_7 currently requires a non-null `end_date` for every non-`EVERGREEN` term model
(including `PERPETUAL`):

```sql
-- V1_7__contract_documents.sql (chk_contracts_dates)
(term_model = 'EVERGREEN' AND end_date IS NULL)
OR (term_model <> 'EVERGREEN' AND end_date IS NOT NULL AND end_date >= start_date)
```

**Owner fork (Proposed, not shipped):** relax `chk_contracts_dates` so `PERPETUAL`
may have a null `end_date`.

**Ship gate (all required):**

1. Owner (drg) explicitly picks that fork.
2. An ADR accepting the fork is **Accepted**.
3. A dry-run count of existing `PERPETUAL` rows is attached (owner-run on a live DB when applicable).

Until that gate passes, **do not** alter `chk_contracts_dates` in any CAM migration.
This PR intentionally contains no such change.
