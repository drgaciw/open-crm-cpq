# Track 1 capability matrix stub (T1-G0)

**Scope:** G0 pin + fixture inventory only. Full matrix / harvest backlog = **T1-D1** (later).  
**Dual-read:** unchanged (`APPROVALS_LEGACY_LOCAL_READ` default `true`).  
**Track 2:** HOLD. No AI “do now” rows.

---

## Source SHA pin

| Field | Value |
|-------|-------|
| Source repo | [drgaciw/pricing-ai-rules-service](https://github.com/drgaciw/pricing-ai-rules-service) |
| Local clone | `/home/username01/Projects/pricing-ai-rules-service` |
| Pinned commit | `3aa45bf164469364389e096df07e591c81079f6a` |
| Pin message | `chore: remove Omaha Sports Complex frontend — unrelated to pricing service (#8)` |
| Pin date | 2026-09-01 |
| Verify | `git -C /home/username01/Projects/pricing-ai-rules-service rev-parse HEAD` |

Target harvest contract remains `POST /api/v1/pricing/execute` (map source `/v1/pricing/evaluate` → execute; do not rename paths).

---

## Fixture inventory

**Source goldens:** `.kilo/artifacts/golden-fixtures/` @ pin above  
**Target fixtures:** `backend/services/pricing-service/src/test/resources/fixtures/pricing/{execute,drl}/`

### Source → target map

| Source golden | Source endpoint / intent | Target fixture (execute / drl) | Mapping note |
|---------------|--------------------------|--------------------------------|--------------|
| `pricing-evaluate-001.json` | `POST /v1/pricing/evaluate` — single-quote happy path + ARR/TCV | `execute/01-list-price-baseline.json` (closest); also informs metrics assertions on `01`–`03` | Shape differs (nested `quote` vs flat execute DTO). Closest existing execute golden for list-price / duration-12 baseline. Harvest adaptation = T1-1. |
| `error-pricing-missing-quote-001.json` | `POST /v1/pricing/evaluate` — missing quote error | *(none under execute/ or drl/)* | Error-path golden; no target counterpart yet. Candidate for T1-1 execute error fixture. |
| `pricing-batch-001.json` | `POST /v1/pricing/batch` | *(none)* | Maps conceptually to target bulk execute if present; not in `{execute,drl}` today. |
| `calculate_waterfall.json` | CPQ waterfall totals / discount / approval flag | `execute/01-list-price-baseline.json`, `execute/02-multi-line-override.json`, `execute/03-override-only.json` | Waterfall vectors live in execute goldens (stubbed price book / overrides). No 1:1 filename match. |
| `evaluate_approvals.json` | CPQ approvals evaluate — discount/margin tier | `drl/approval-margin-below-floor.json`, `drl/approval-margin-above-floor.json` | Closest target DRL goldens for margin-floor → approval routing. Source uses CPQ approvals DTO + `DISCOUNT_GT_35`; target uses `approval-margin-exception.drl` facts. |
| `simulate_amendment.json` | CPQ amendment simulate / proration | *(none)* | No execute/drl counterpart. |
| `reprice_renewal.json` | CPQ renewal reprice | *(none)* | No execute/drl counterpart. |
| `rule-validate-001.json` | `POST /v1/rules/rules/{id}/validate` success | *(none under execute/ or drl/)* | Rule lifecycle; not pricing execute/drl fixtures. |
| `error-rule-validate-invalid-001.json` | rule validate failure | *(none)* | Same — outside `{execute,drl}`. |
| `rule-compile-001.json` | `POST /v1/rules/sets` (compile side-effect) | *(none)* | Same — outside `{execute,drl}`. |
| `validate_configuration_invalid.json` | CPQ configuration validate invalid qty | *(none)* | Config/CE domain; not pricing execute/drl. |

### Target → source reverse map

| Target fixture | Target intent | Source golden(s) | Mapping note |
|----------------|---------------|------------------|--------------|
| `execute/01-list-price-baseline.json` | Single-line list-price waterfall; no Drools | `pricing-evaluate-001.json`, `calculate_waterfall.json` | Partial overlap only; adapt evaluate→execute in T1-1. |
| `execute/02-multi-line-override.json` | Multi-line + override + 24-month metrics | `calculate_waterfall.json` (weak); metrics vectors from evaluate family | No dedicated source multi-line golden. |
| `execute/03-override-only.json` | All lines `listPriceOverride` | *(none direct)* | Target-only Phase 0 fixture. |
| `drl/volume-named-seat-500.json` | Named-seat volume discount @ qty 500 | *(none in golden-fixtures/)* | Target DRL unit golden; source DRL corpus separate from this inventory dir. |
| `drl/approval-margin-below-floor.json` | Margin below floor → DEAL_DESK | `evaluate_approvals.json` (closest intent) | Different rule/reason model. |
| `drl/approval-margin-above-floor.json` | Margin above floor → no fire | `evaluate_approvals.json` (inverse case; source only captures required=true) | Target has explicit non-fire case. |

### Inventory counts

| Location | Count | Files |
|----------|------:|-------|
| Source `.kilo/artifacts/golden-fixtures/` | 11 | listed above |
| Target `fixtures/pricing/execute/` | 3 | `01`–`03` |
| Target `fixtures/pricing/drl/` | 3 | volume + approval above/below |

**G0 exit:** clone pinned + this inventory. No harvest implementation in this PR.
