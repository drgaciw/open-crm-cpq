# ADR-013: Quote-service is the approvals BFF — single-writer to approval-routing, legacy local reads behind a flag

**Status:** Accepted *(reverse-engineered from code 2026-09-19 — the BFF single-writer pattern (Wave G4) predates ADR-005, which records only the *dual-read default* question; the BFF decision itself had no ADR)*
**Date (decision embedded):** ~2026-08 (Wave G4, extended K4) · **Documented:** 2026-09-19
**Deciders:** forge Wave G4 (de facto) · **Documented by:** architecture reverse-engineering pass
**Provenance:** Reverse-engineered ADR. Companion to [ADR-005](ADR-005-approvals-legacy-dual-read.md) (Proposed — dual-read default stays `true`): that ADR governs the flag's fate; **this** ADR records the landed topology the flag sits inside. Neither authorizes flipping the flag or giving the FE access to `/api/v1/approvals/**`.

---

## Context

Two services touch approvals: quote-service (where approval requests are born from quote state) and approval-routing (the canonical workflow engine). The system needed one write path, one read API for the FE inbox, and a migration story for rows written before approval-routing existed.

Verified on the tree (2026-09-19):

- **Write path — single writer:** quote-service `ApprovalService` routes approval decisions/writes to **approval-routing** (`services.approval-routing.url`, default `:8085`) as the canonical store (Wave G4). approval-routing exposes `/api/v1/approvals/**` (gateway route + `ApprovalRequestDto` per [ADR-006](ADR-006-entities-never-at-api-boundary.md)).
- **Read path — BFF:** quote-service's own `ApprovalController` (`/api/v1/quotes/approvals/**` under the quote route) serves the FE approval inbox; `@frontend/cpq-approvals` (Wave K4) calls quote BFF only — it never calls `/api/v1/approvals/**` and never sees this service's internals.
- **Legacy reads behind a flag:** `approvals.legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}` (`application.yml:89`); `ApprovalService` holds it via `@Value` and branches at 7 sites (`:94,:110,:135,:140,:180,:185`) to merge leftover local `quote.approval_requests` rows (PENDING/history) with routing reads — the pre-cutover compatibility window. [ADR-005](ADR-005-approvals-legacy-dual-read.md) (Proposed) + the J5 checklist gate any flip as **HIGH**.
- Approval lifecycle events exist in the published language (`ApprovalRejectedEvent`, `DiscountApprovalRequiredEvent` + `v1`) so downstream services react without polling approvals tables.

## Decision

**quote-service is the Backend-for-Frontend for approvals. It is the only service the Angular approval inbox talks to. All approval state changes are written single-writer to approval-routing; quote-service keeps a local, flag-gated read of its legacy `quote.approval_requests` rows solely to surface pre-cutover records. `/api/v1/approvals/**` is canonical but FE-forbidden.**

1. FE contract: `/api/v1/quotes/approvals/**` only (list, pending, history, approve/reject through the BFF).
2. Canonical writes: quote-service → approval-routing REST client; no other service writes approvals; approval-routing never writes quote state.
3. Dual-read is server-side BFF behavior only — no FE toggle, no FE awareness of the flag ([ADR-005](ADR-005-approvals-legacy-dual-read.md) governs the flip procedure via J5).
4. Cross-service notifications travel as published events (`DiscountApprovalRequiredEvent`, `ApprovalRejectedEvent`), not synchronous fan-out.

## Options considered

| Option | Verdict |
|--------|---------|
| **A. Quote BFF + single-writer routing + flag-gated legacy reads (landed)** | One FE surface, one write owner, rollback-friendly migration. Con: two approval read code paths while the flag is `true`; BFF must keep merge semantics consistent. |
| B. FE calls approval-routing directly (`/api/v1/approvals/**`) | Exposes canonical internals, splits quote-context enrichment (totals, requester) into the FE, and breaks the [ADR-006](ADR-006-entities-never-at-api-boundary.md) DTO discipline the FE relies on. Rejected. |
| C. Two writers (quote-service keeps writing local rows) | Re-introduces the divergence Wave G4 eliminated; pre-cutover rows exist only as read-only legacy. Rejected. |
| D. Event-sourced approvals only (no REST BFF) | Inbox needs synchronous list/pending views; events complement but cannot replace the BFF read model. Rejected as replacement. |

## Consequences

- **Positive:** FE approval inbox has one stable contract; single-writer guarantees one approval truth; legacy rows remain visible until the J5-gated flip; rollback = set the env flag back to `true`.
- **Negative / residual:** While `APPROVALS_LEGACY_LOCAL_READ=true`, reads merge two stores — IDs that exist only locally can still 404 on approve/reject against routing (the exact HIGH risk ADR-005/J5 document); BFF is a required hop (one more service on the inbox path).
- **Governance:** This ADR does not authorize the dual-read flip (ADR-005 stays Proposed), does not authorize FE calls to `/api/v1/approvals/**`, and does not authorize deleting `quote.approval_requests`.

## Verification evidence (2026-09-19)

- `backend/services/quote-service/src/main/resources/application.yml:84-89` — `services.approval-routing.url` + `approvals.legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}`.
- `backend/services/quote-service/.../approval/ApprovalService.java` — `@Value("${approvals.legacy-local-read-enabled:true}")` with branches at lines 94, 110, 135, 140, 180, 185.
- `backend/services/quote-service/.../approval/ApprovalController.java` — BFF controller (DTO-only).
- `backend/services/gateway-service/src/main/resources/application.yml` — `/api/v1/quotes/**` → quote-service; `/api/v1/approvals/**` → approval-routing.
- `backend/libs/common/.../event/dto/{DiscountApprovalRequiredEvent,ApprovalRejectedEvent}.java` (+ `v1`).
- `docs/forge/impl/g4-approval-bff.md`, `docs/forge/impl/k4-*` (FE inbox), `docs/forge/impl/j5-dual-read-readiness.md` (flip gate).

## References

- [docs/adr/ADR-005-approvals-legacy-dual-read.md](ADR-005-approvals-legacy-dual-read.md) — flag default + J5 cutover gate (Proposed)
- [docs/adr/ADR-006-entities-never-at-api-boundary.md](ADR-006-entities-never-at-api-boundary.md) — `ApprovalRequestDto` at the routing boundary
- [docs/ARCHITECTURE.md §14](../ARCHITECTURE.md) — CPQ contracts (runtime)
