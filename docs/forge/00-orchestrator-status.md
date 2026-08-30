# Forge orchestrator status

**Authority:** PM full autonomous forge (delegated)  
**Discovery:** Herdr `w7` (`forge`) — complete  
**Implement:** Herdr `w8` (`forge-impl`) — Batch 1 finishing + Batch 2 running  
**Workflows:** `forge-next-steps` (done), `forge-autonomous-loop` (active)

## Batch 0 — merged to local `main` (not pushed)

See `08-batch0-results.md`. Includes CRM search shape, quote idempotency + Redis IT, gateway unit path, CI drift gate, CPQ price-books docs.

## Batch 1 — finishing

| Agent | Branch | Focus |
|-------|--------|-------|
| `impl-b1-fe` | `feat/forge-b1-crm-fe-lifecycle` | Lead qualify/convert list UX |
| `impl-b1-smoke` | `test/forge-b1-cpq-agent-contract-smoke` | Agent-contract smoke / Appendix A |
| `impl-b1-price` | `test/forge-b1-pricing-execute-fixtures` | Pricing execute golden fixtures |
| `impl-b1-cpqui` | `chore/forge-b1-fe-cpq-nx-scaffold` | Nx `libs/cpq` scaffold |

## Batch 2 — running (6 panes)

| Agent | Branch | Focus |
|-------|--------|-------|
| `impl-b2-proxy` | `chore/forge-b2-proxy-gateway-8080` | FE proxy → gateway :8080 |
| `impl-b2-parity` | `docs/forge-b2-crm-parity-activities` | Parity docs + activities deferral |
| `impl-b2-deals` | `feat/forge-b2-deal-pipeline-ux` | Deal pipeline UX |
| `impl-b2-models` | `feat/forge-b2-cpq-shared-models` | CPQ shared TS models |
| `impl-b2-smoke2` | `docs/forge-b2-staging-smokes` | Staging smoke scripts |
| `impl-b2-erp` | `test/forge-b2-gateway-erp-501` | Gateway ERP 501 contract tests |

## Held for later

- Activities API implementation  
- S2B-11 configuration validate compatibility (HIGH)  
- S2C-12 quote→approval-routing BFF (HIGH)  
