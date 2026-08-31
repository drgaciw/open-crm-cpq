# Forge orchestrator status

**Authority:** PM full autonomous forge (delegated)  
**Session state:** **Wave M FORGING** (quote header edit ∥ price-book entry CRUD ∥ clone copies line items)  
**Architect:** `/opencrm-architect` (Herdr `w8`)  
**Plan:** `docs/forge/22-wave-m-architecture.md`  
**Baseline:** Wave L COMPLETE @ `a00c4b0`; Wave M authorized 2026-08-30

## Wave M tracks

- **M1** FE quote header `PUT` form + clone copy (`quotes-detail`)
- **M2** FE price-book entry create/update/delete (`cpq-price-books`)
- **M3** BE `QuoteService.clone` copies line items (Java only)
- **M4** Docs closeout after Day-1

**Merge gate:** land **M3 before or atomically with M1** so clone copy is never a lie.

**Implementers:** Herdr Grok 4.5 panes (not idle dual-write subagents on those trees). Workflow `forge-wave-m` is the DAG/verify overlay.

## Still held

- Activities API, CI runners (#238), agent Kafka (#241)
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (HIGH)
- Full SpEL; Kafka S2B-9; contract-asset FE (entity leak + no list-all); NgRx CPQ store
