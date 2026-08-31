# Forge orchestrator status

**Authority:** PM full autonomous forge (delegated)  
**Session state:** **Wave N FORGING** (price-book header ∥ pricing-customers portal ∥ quote soft-delete)  
**Architect:** `/opencrm-architect` (Herdr `w8`)  
**Plan:** `docs/forge/23-wave-n-architecture.md`  
**Baseline:** Wave M COMPLETE @ `e09a4a3`; Wave N authorized 2026-08-31

## Wave N tracks

- **N1** FE price-book header update/delete (`cpq-price-books`, no shell)
- **N2** FE pricing-customers portal + shell (`@frontend/cpq-pricing-customers`)
- **N3** FE quote soft-delete on detail (no shell)
- **N4** Docs closeout after Day-1

**Shell owner:** N2 only (`app.routes` + sidebar + `test:cpq`).

**Implementers:** Herdr Grok 4.5 panes (not idle dual-write subagents on those trees). Workflow `forge-wave-n` is the DAG/verify overlay.

## Still held

- Activities API, CI runners (#238), agent Kafka (#241)
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (HIGH)
- Full SpEL; Kafka S2B-9; contract-asset FE; NgRx CPQ store; rule-set admin; product bundles
