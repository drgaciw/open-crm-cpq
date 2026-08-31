# Forge orchestrator status

**Authority:** PM full autonomous forge (delegated)  
**Session state:** **Wave M FORGING** (M3 + M1 merged; M2 in progress)  
**Architect:** `/opencrm-architect` (Herdr `w8`)  
**Plan:** `docs/forge/22-wave-m-architecture.md`  
**Tip:** `51805d7` on `main`

## Wave M tracks

| ID | Status | Tip |
|----|--------|-----|
| **M3** | MERGED | `6d25bae` clone copies line items |
| **M1** | MERGED | `51805d7` header PUT + clone copy (after M3) |
| **M2** | FORGING | Herdr `wm-pb-entries` still writing |
| **M4** | PENDING | Closeout after M2 |

**Merge gate honored:** M3 landed before M1.

## Still held

- Activities API, CI runners (#238), agent Kafka (#241)
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (HIGH)
- Full SpEL; Kafka S2B-9; contract-asset FE; NgRx CPQ store
