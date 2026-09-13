---
status: done
id: w3-be-document-job-dto
wave: W
pr: W3
---

# w3-be-document-job-dto

## Summary

Wave W3 (BE only): stop leaking the `DocumentGenerationJob` JPA entity at the HTTP boundary. Added `DocumentGenerationJobDto` + MapStruct `DocumentGenerationJobMapper` (`componentModel = "spring"`), wired through `DocumentGenerationService` / `DocumentController`. Controllers return DTOs only. Retry mutation moved out of the controller into the service (FAILED → PENDING, clear error, `generateDocumentAsync`). No new generate HTTP endpoint. No FE. No gateway route edits. ADR-006 remains **Proposed** (Accept only in W4 after W1–W3 on `main`).

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / confirmation |
|--------|------|-------------------------|
| `DocumentController` | **UNKNOWN** (0 callers + riskNote; HTTP entry) | **rg:** gateway `/api/v1/documents/**`; FE empty (`frontend/**/*.ts`); quote docs use quote-service |
| `retryJob` | **UNKNOWN** (HTTP) | same — mutation moved to service |
| `DocumentGenerationJob` | **LOW** (exact, 4) | → controller, service, repository, `QuoteAcceptedEventConsumer` (Kafka internal) |
| `DocumentGenerationService` | **LOW** (exact, 2) | → controller, `QuoteAcceptedEventConsumer` |

**WARN:** UNKNOWN ≠ unused. Confirmed gateway + FE absence with `rg`. Pre-edit impact: no HIGH/CRITICAL. Analyze skipped (stale per wave plan).

**Pre-commit `detect-changes --scope all`** (from worktree): Changes 2 files / 7 symbols; Risk **HIGH** — RetryJob + HandleQuoteAccepted + GetJobById flows list `generateDocumentAsync` / `getJobById`. Expected for DTO-boundary + retry move; async body and Kafka consumer call site unchanged; tests green. Wave plan risk remains **LOW–MED**.

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-w3` (`feat/forge-w3-be-document-job-dto`)  
Impact CLI: `node .gitnexus/run.cjs impact "…" --direction upstream --repo /home/username01/Projects/open-crm-cpq`

## Files

| Path | Change |
|------|--------|
| `…/dto/DocumentGenerationJobDto.java` | New response DTO (no `deleted`) |
| `…/mapper/DocumentGenerationJobMapper.java` | MapStruct `toDto` / `toDtoList` |
| `…/service/DocumentGenerationService.java` | Returns DTOs; `retryJob` PENDING→async |
| `…/controller/DocumentController.java` | DTO-only thin API |
| `…/controller/DocumentControllerTest.java` | MockMvc: no entity leak fields; retry message |
| `…/service/DocumentGenerationServiceTest.java` | Mockito: get/list/retry/reject non-FAILED |
| `docs/forge/impl/w3-be-document-job-dto.md` | This note |

## Acceptance

- [x] No `ApiResponse<DocumentGenerationJob>` / `List<DocumentGenerationJob>` at controller
- [x] getJobById / getJobsByQuote / getJobsByStatus / retryJob return DTO
- [x] Retry in service: FAILED → PENDING + clear error + `generateDocumentAsync`
- [x] No new `/generate` HTTP endpoint
- [x] Tests: no `deleted` / Hibernate fields in JSON; retry queues; non-FAILED rejected
- [x] No FE / portal; no gateway edits; no quote-service; no CE / approval-routing
- [x] No dual-read YAML; no `00-orchestrator-status.md`; ADR-006 left **Proposed**
- [x] Boot parent remains 4.0.6

## Verify

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
cd backend
newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
./mvnw -B -pl services/document-generation -am test
EOF
```

Result: **BUILD SUCCESS** (JDK 21.0.2). document-generation Tests run: **13**, Failures: 0, Errors: 0 (controller DTO 3 + domain lifecycle 5 + service DTO/retry 5). Reactor: common 86 + security 13 + document-generation 13.

## Out of scope

- FE / portal / chrome
- Gateway route changes
- Quote-service documents API
- configuration-engine (W1) / approval-routing (W2)
- Accept ADR-006
- Dual-read YAML flip; `00-orchestrator-status.md`
- Kafka payload rewrite
- Activities, #238, #241, NgRx, R2, named T1/T2, subscriptions FE
