# Forge orchestrator status

**Authority:** PM full autonomous forge (delegated)  
**Session state:** **Wave N FORGING** (N1 + N3 merged; N2 in progress)  
**Architect:** `/opencrm-architect` (Herdr `w8`)  
**Plan:** `docs/forge/23-wave-n-architecture.md`  
**Tip:** `9dbab29` on `main`

## Wave N tracks

| ID | Status | Tip |
|----|--------|-----|
| **N1** | MERGED | `9dbab29` price-book header update/delete |
| **N3** | MERGED | `e995488` quote soft-delete |
| **N2** | FORGING | Herdr `wn-fe-pricing-customers` still writing |
| **N4** | PENDING | Closeout after N2 |

**Shell owner:** N2 only.

## Still held

- Activities API, CI runners (#238), agent Kafka (#241)
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (HIGH)
- Full SpEL; Kafka S2B-9; contract-asset FE; NgRx CPQ store; rule-set admin; product bundles
