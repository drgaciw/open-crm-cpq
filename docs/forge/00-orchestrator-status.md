# Forge orchestrator status

**Authority:** PM full autonomous forge (delegated)  
**Session state:** **Wave G FORGING** (tracks 1∥2∥3; G4 HIGH acknowledged)  
**Architect:** `/opencrm-architect`  
**Plan:** `docs/forge/16-wave-g-architecture.md`  
**Baseline:** `3952b20` (Waves A–F COMPLETE)

## Wave G (authorized)

| ID | Track | Risk | Day |
|----|-------|------|-----|
| **G1** | Shared rule evaluation core (S2B-8) | MED | 1 ∥ |
| **G3** | approval-routing POST create/submit | MED | 1 ∥ |
| **G5** | Quote preflight → gateway validate | MED | 1 ∥ |
| **G6** | FE catalog → ProductService | LOW | 1 ∥ |
| **G7** | FE quotes → QuoteService | LOW | 1 ∥ |
| **G8** | FE simulator → PricingExecutionService | LOW–MED | 1 ∥ |
| **G2** | ConstraintSolver session wire | MED | after G1 |
| **G4** | quote ApprovalService → routing BFF | **HIGH** | after G3 |
| **G9** | Matrix + forge closeout | LOW | after G2+G4+G5 |

## Still held
- Activities API, CI #238, agent Kafka #241  
- Delete product-service validate; gateway domain validation  
