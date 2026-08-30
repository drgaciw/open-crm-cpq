# Forge orchestrator status

**Authority:** PM full autonomous forge (delegated)  
**Session state:** **Wave F FORGING** (hybrid S2B-11; F3 HIGH acknowledged)  
**Architect:** `/opencrm-architect`  
**Plan:** `docs/forge/15-wave-f-architecture.md`  
**Baseline tip:** `origin/main` @ `c5075a8`  

## Wave E results (complete)
- ADR-004 CSRF edge-only; pricing/pricebook `csrf.disable()`; config ADR Accepted; Sentinel/Bolt hygiene  

## Wave F (authorized — hybrid S2B-11)

| ID | Intent | Risk | Branch |
|----|--------|------|--------|
| **F1** | `ConfigurationValidationResult` DTO in `libs/common` | LOW | `feat/forge-f1-validation-dto` |
| **F2** | CE `POST /internal/v1/configurations/evaluate` thin REQUIRES/EXCLUDES | MED | `feat/forge-f2-ce-internal-evaluate` |
| **F3** | Gateway validate → CE + adapt `{valid,violations[]}` | **HIGH** | `feat/forge-f3-gateway-validate-shim` |
| **F4** | Contract + product↔CE dual-run parity tests | MED | `test/forge-f4-validate-contracts` |
| **F5** | Matrix + forge closeout | LOW | `docs/forge-f5-s2b11-closeout` |

**DAG:** F1 → F2∥F4 → F3 → F5  
**Do not:** Activities, S2C-12, #238, #241, full S2B-8, delete product validate  

## Still held
- Activities API, S2C-12, CI #238, agent Kafka #241, full ConstraintSolver (S2B-8)  
