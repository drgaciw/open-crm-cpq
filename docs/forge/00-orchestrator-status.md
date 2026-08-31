# Forge orchestrator status

**Authority:** PM full autonomous forge (delegated)  
**Session state:** **Wave N COMPLETE**; **hygiene N′ FORGING** (H1 errorCode ∥ H2 matrix)  
**Architect:** `/opencrm-architect` (Herdr `w8`) — recommended A; Wave O deferred  
**Plan:** `docs/forge/25-wave-n-hygiene.md`  
**Baseline:** Wave N COMPLETE @ `5f126ca`

## Hygiene tracks

- **H1** Quote delete banner uses `errorCode` (`cpq-quotes`)
- **H2** Matrix DELETE quotes row + N1 notes + dual-read J–N
- **H3** TestBed hookTimeout optional, not blocker

## Still held

- Activities API, CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (HIGH)
- Full SpEL; Kafka S2B-9; contract-asset FE; NgRx; rule-set admin; bundles
