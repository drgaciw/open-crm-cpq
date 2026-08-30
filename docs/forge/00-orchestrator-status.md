# Forge orchestrator status

**Discovery workspace:** Herdr `w7` (`forge`) — plan mode, complete  
**Implement workspace:** Herdr `w8` (`forge-impl`) — Grok 4.5 always-approve, **Batch 0 in progress**  
**Workflow:** `.grok/workflows/forge-next-steps.rhai`  
**Authority:** PM delegated future decisions to orchestrator (2026-08-30)

## Discovery (complete)

| Tab | Agent | Plan |
|-----|-------|------|
| `01-crm-api-parity` | `crm-api` | `docs/forge/01-crm-api-parity.md` |
| `02-crm-frontend` | `crm-fe` | `docs/forge/02-crm-frontend.md` |
| `03-quote-deal-e2e` | `quote-e2e` | `docs/forge/03-quote-deal-e2e.md` |
| `04-cpq-contracts` | `cpq-api` | `docs/forge/04-cpq-contracts.md` |
| `05-fe-cpq-ui` | `fe-cpq` | `docs/forge/05-fe-cpq-ui.md` |
| `06-gateway-ci` | `gateway-ci` | `docs/forge/06-gateway-ci.md` |

Synthesized backlog: `docs/forge/07-wave-backlog.md`

## Batch 0 implementation (active)

Worktrees under `.forge-worktrees/`. Local commits only; no push/PR until orchestrator decides.

| Agent | Branch | Task |
|-------|--------|------|
| `impl-search` | `feat/forge-b0-crm-search-shape` | CRM search List+PageInfo + FE unwrap |
| `impl-idem` | `fix/forge-b0-quote-idempotency` | Quote-accepted idempotency harden |
| `impl-qit` | `test/forge-b0-quote-accepted-it` | Redis IT deal WON + idempotency |
| `impl-gw` | `ci/forge-b0-gateway-backend-unit` | Gateway tests on backend-unit path |
| `impl-drift` | `ci/forge-b0-contract-drift-gate` | Drift verify in toolchain |
| `impl-docs` | `docs/forge-b0-cpq-matrix-pricebooks` | Matrix price-books + skill paths |

## Batch 0 — complete (merged to local `main`, not pushed)

See `docs/forge/08-batch0-results.md`. Drift verify OK after merge.

## Batch 1 — in progress (Herdr `w8`)

| Agent | Branch | Task |
|-------|--------|------|
| `impl-b1-fe` | `feat/forge-b1-crm-fe-lifecycle` | Lead list qualify/convert UX |
| `impl-b1-smoke` | `test/forge-b1-cpq-agent-contract-smoke` | Agent-contract HTTP smoke |
| `impl-b1-price` | `test/forge-b1-pricing-execute-fixtures` | Pricing execute golden fixtures |
| `impl-b1-cpqui` | `chore/forge-b1-fe-cpq-nx-scaffold` | Nx `libs/cpq` scaffold |

## Later

- Hold HIGH items (S2B-11 validate compat, S2C-12 approval BFF) for dedicated waves.
- Push/PR only when orchestrator decides CI is green enough on a branch.
