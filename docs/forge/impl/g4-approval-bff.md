---
status: done
id: g4-approval-bff
---

# g4-approval-bff

## Summary

Wave G4 (S2C-12): quote-service `ApprovalService` is a BFF that **single-writes** approval records to approval-routing `POST /api/v1/approvals` (G3). On submit it updates only Quote projection fields (`status`, `approvalState`, `approvalTier`) — **no** local `ApprovalRequest` insert. Dual-read GETs prefer routing pending and may fall back to legacy local rows (`approvals.legacy-local-read-enabled`, default `true`). Agent paths under `/api/v1/quotes/**/approvals*` are unchanged.

## GitNexus (pre-edit)

| Symbol | Risk | Notes |
|--------|------|-------|
| quote `ApprovalService` | **UNKNOWN** (0 edges) | Text: `ApprovalController` + agents 010/011 — treat **HIGH** |
| routing `ApprovalService` | **LOW** (controller) | G3 submit already landed |
| Index | 21 commits behind | Text confirmation used; PM authorized G4 HIGH |

**Wave risk:** **HIGH** (dual-store / agent-contract). No conflicting PENDING writes on submit (proven by unit tests).

## Write / read policy

| Operation | Approval record | Quote projection |
|-----------|-----------------|------------------|
| Submit | routing `POST /api/v1/approvals` only | `PENDING_APPROVAL` / `PENDING` / tier |
| Approve / reject | routing first; legacy local only on 404 | `APPROVED` / `REJECTED` |
| GET pending / by quote | Prefer routing; merge legacy when flag on | n/a |

## Files

| Path | Change |
|------|--------|
| `.../client/ApprovalRoutingClient.java` | RestClient to routing submit/get/pending/approve/reject |
| `.../client/RoutingApprovalDto.java` | Wire DTO |
| `.../client/RoutingApprovalSubmitRequest.java` | Submit payload |
| `.../config/RestClientConfig.java` | `approvalRoutingRestClient` bean |
| `.../approval/ApprovalService.java` | Single-writer + dual-read BFF |
| `application.yml` (+ test yml) | `services.approval-routing.url`, legacy-read flag |
| `.../ApprovalServiceSingleWriterTest.java` | No dual-write on submit; dual-read pending |
| `.../ApprovalRoutingClientTest.java` | MockRestServiceServer |
| `docs/forge/impl/g4-approval-bff.md` | This note |
| `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` | S2C-12 single-writer note |

## Acceptance

- [x] External `/api/v1/quotes/**/approvals*` unchanged
- [x] Canonical approval record created in approval-routing on submit
- [x] Submit does **not** call `approvalRepository.save`
- [x] Quote status/approvalState/approvalTier updated on submit
- [x] Dual-read pending prefers routing; legacy fallback without duplicate quote+tier
- [x] Unit tests green (JDK 21)

## Evidence (single-writer)

`ApprovalServiceSingleWriterTest.submitDoesNotDualWriteLocalApprovalRequest`:

- verifies `approvalRoutingClient.submit(...)`
- verifies `approvalRepository.save` / local finders **never** called
- asserts Quote → `PENDING_APPROVAL` / `PENDING` / tier

## Out of scope

- Dropping quote `approval_requests` table
- Changing agent BFF URL shapes
- G9 matrix/orchestrator closeout
