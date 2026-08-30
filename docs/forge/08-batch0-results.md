# Batch 0 results

**Authority:** PM delegated autonomous decisions.  
**Herdr:** `forge-impl` (w8) + worktrees under `.forge-worktrees/`  
**Push/PR:** not done (local branches only)

| ID | Branch | Commit | Status |
|----|--------|--------|--------|
| B0-1 | `feat/forge-b0-crm-search-shape` | see worktree | done |
| B0-2 | `fix/forge-b0-quote-idempotency` | `0eaaa7f` | done |
| B0-3 | `test/forge-b0-quote-accepted-it` | `0ed9557` | done |
| B0-4 | `ci/forge-b0-gateway-backend-unit` | `e3e58e5` | done |
| B0-5 | `ci/forge-b0-contract-drift-gate` | `d18e65b` | done |
| B0-6 | `docs/forge-b0-cpq-matrix-pricebooks` | `19d1522` | done |

## Merge order (orchestrator decision)

1. `docs/forge-b0-cpq-matrix-pricebooks` (docs-only)
2. `ci/forge-b0-gateway-backend-unit` then rebase `ci/forge-b0-contract-drift-gate` (both touch `run-core.sh` / `ci-contract.yaml`)
3. `fix/forge-b0-quote-idempotency` then rebase `test/forge-b0-quote-accepted-it`
4. `feat/forge-b0-crm-search-shape` (crm-service + FE; avoid overlapping quote event files)

## Next (Batch 1) — starting after merge prep

- CRM FE lifecycle UX (qualify/convert list actions)
- CPQ agent-contract HTTP smoke tests
- Pricing execute golden fixtures
- FE CPQ Nx scaffold PR-FE-CPQ-01 (libs only)
