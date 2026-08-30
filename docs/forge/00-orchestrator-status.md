# Forge orchestrator status

**Authority:** PM full autonomous forge (delegated)  
**Session state:** **Wave H FORGING** (VALIDATES expression ∥ F6 AI validate ∥ CRM Bolt hygiene ∥ S2B-9 thin cache)  
**Architect:** `/opencrm-architect`  
**Plan:** `docs/forge/17-wave-h-architecture.md`  
**Baseline:** `e427847` (Waves A–G COMPLETE)

## Wave G results (prior)
- **G1** `ProductRuleEvaluationCore` (RECOMMENDS soft / DEFAULT / VALIDATES target-only)  
- **G2** `ConstraintSolver` consumes product rules via shared core  
- **G3** approval-routing `POST /api/v1/approvals` create/submit  
- **G4** quote ApprovalService single-writes routing (HIGH dual-store; dual-read legacy default **true**)  
- **G5** quote `submitForReview` preflight via gateway validate  
- **G6–G8** FE catalog / quotes / simulator → existing HttpClient services  
- **G9** matrix + forge closeout  

## Wave H plan (architect pick)
- **H1** VALIDATES `conditionExpression` constrained `attr:key=value` dialect (+ session attrs)  
- **H2** F6 AI `CpqAgentToolClients` structured `ConfigurationValidationResult` parse  
- **H3** CRM JPA projections (#437; supersedes #411/#417)  
- **H4** Deal kanban O(N) (#436)  
- **H5** SearchInput deals+orgs (#403 remainder)  
- **H6** S2B-9 thin TTL rule cache  
- **H7** Dual-read flag-off **prep** (default stays true)  
- **H8** Docs closeout  

**Day-1 panes:** H1 ∥ H2 ∥ H3 ∥ H4 ∥ H5 ∥ H6  

## Still held
- Activities API, CI runners (#238), agent Kafka (#241)  
- Delete product-service validate; gateway domain validation  
- Flip `approvals.legacy-local-read-enabled` default → false (HIGH gate)  
- Bolt #430 WhatsApp; #412 product validate micro-opt; full SpEL; Kafka S2B-9 events  
