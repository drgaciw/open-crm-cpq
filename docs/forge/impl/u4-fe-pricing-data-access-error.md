---
status: done
id: u4-fe-pricing-data-access-error
wave: U
pr: U4
---

# u4-fe-pricing-data-access-error

## Summary

Wave U4: H1-shaped `errorCode` on pricing-data-access `error.set` for `RuleService`, `RuleSetService`, `PricingCustomerService`, and `PricingExecutionService`. Additive `message [CODE]` so existing string assertions still match. **Do not** edit `price-book.service.ts` (S4). **Do not** edit rule-sets / pricing-customers components. No shared extract, T2/N1 widen, R2, Java, dual-read flip, or `00-orchestrator-status`.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-u4` (`fix/forge-u4-fe-pricing-data-access-error`)  
CLI: `bunx gitnexus@latest impact … -r /home/username01/Projects/open-crm-cpq -f frontend/libs/cpq/pricing-data-access`  
Index stale (~34 commits behind). **Analyze skipped.** UNKNOWN ≠ unused — confirmed with `rg`.

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| FE `RuleService` | **UNKNOWN** → **LOW** via `rg` | `rule-set-detail` binds `ruleService.error()` |
| FE `RuleSetService` | **UNKNOWN** → **LOW** via `rg` | `rule-set-list` / detail bind `service.error()` |
| FE `PricingCustomerService` | **UNKNOWN** → **LOW** via `rg` | `pricing-customer-list` binds `service.error()` |
| FE `PricingExecutionService` | **UNKNOWN** → **LOW** via `rg` | simulator injects service; UI uses local H1 helper (S4); signal still updated |

**WARN:** UNKNOWN + `partial: true` (binder degraded). Resolved via `rg` — additive banner string formatting only. No HIGH/CRITICAL in-scope.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/pricing-data-access/.../rule.service.ts` (+spec) | Local H1 `httpErrorMessage` on listByRuleSet / getById `error.set` |
| `frontend/libs/cpq/pricing-data-access/.../rule-set.service.ts` (+spec) | Local H1 on list / getById `error.set` |
| `frontend/libs/cpq/pricing-data-access/.../pricing-customer.service.ts` (+spec) | Local H1 on list / getById `error.set` |
| `frontend/libs/cpq/pricing-data-access/.../pricing-execution.service.ts` (+spec) | Local H1 on execute / simulate / simulateBulk / getAuditTrail `error.set` |
| `docs/forge/impl/u4-fe-pricing-data-access-error.md` | This note |

## Acceptance

- [x] list/get (and execution) `error.set` is H1-shaped `message [CODE]`
- [x] Specs mock `{ message, errorCode }` → `message [CODE]`
- [x] **No** `price-book.service.ts` edits
- [x] **No** rule-sets / pricing-customers **component** edits
- [x] No shared `httpErrorMessage` extract; no T2/N1 `bookHttpErrorMessage` widen; no R2
- [x] No Java, NgRx, dual-read flip, `00-orchestrator-status.md`
- [x] Vitest `cpq-pricing-data-access` green (also `cpq-rule-sets` / `cpq-pricing-customers` if string assertions break)

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-pricing-data-access --skip-nx-cache
# only if string assertions break:
# NX_DAEMON=false npx nx test cpq-rule-sets --skip-nx-cache
# NX_DAEMON=false npx nx test cpq-pricing-customers --skip-nx-cache
```

## Out of scope

- `price-book.service.ts` (S4 done)
- rule-sets / pricing-customers UI components
- T2 / N1 `bookHttpErrorMessage` widen
- Price-book list-row delete (R2 **HELD**)
- Shared `httpErrorMessage` extract
- Java / NgRx / dual-read flip / Activities / #238 / #241 / WhatsApp #430 / subscriptions FE / Accept ADR-006
- Named Wave T / T1 / T2
- `docs/forge/00-orchestrator-status.md`
