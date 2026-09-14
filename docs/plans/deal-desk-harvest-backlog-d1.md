# Track D1 harvest backlog (C2)

**Parent plan:** [deal-desk-reuse-gap-analysis.md](./deal-desk-reuse-gap-analysis.md) §5 Track D1 / §6.1.  
**Capability matrix:** [deal-desk-capability-matrix-d1.md](./deal-desk-capability-matrix-d1.md).  
**Source pin:** `d9bf654a487846d54964c13d1f9363fe99f747aa` @ `/home/username01/Projects/deal-desk-service`.  
**Dual-read:** leave `${APPROVALS_LEGACY_LOCAL_READ:true}` — do not flip.  
**Queue D / TCV:** **HOLD** until C-GATE. Do **not** reopen closed Queue A T1-1…T1-5.

Ranked serial PRs below match parent pilots. **Do not** start D1-1…D1-3 code until this matrix + backlog **merge** (C3 hold). **Do not** add `/api/v1/deals`. **Do not** put Easy Rules in `approval-routing` or `quote-service`.

---

## Maven env (every **C3** code PR — not this docs PR)

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
# Unit tests (no docker):
cd backend && ./mvnw -B -pl services/approval-routing,services/quote-service -am test -DskipITs
# ITs when Testcontainers needed and login `id` lacks docker:
newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
cd backend && ./mvnw -B -pl services/approval-routing,services/quote-service -am test
EOF
```

**GitNexus (every C3 code PR):**

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "<Symbol>" --direction upstream \
  --repo /home/username01/Projects/open-crm-cpq --file path/to/File.java
# UNKNOWN ≠ unused — confirm with rg
# Two ApprovalService classes — always pass --file
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all \
  --repo /home/username01/Projects/open-crm-cpq
```

---

## DAG overview

```
C0 pin (parent D0) → C1+C2 (this docs PR, T1-D1 pattern)
                   ↘
                     C3 HOLD until merge:
                       D1-1 → D1-2 → D1-3
                     C-GATE → Queue D (TCV handoff file only)
```

| ID | Status | Owner | Branch (suggested) | Depends |
|----|--------|-------|--------------------|---------|
| **C0** | Done on this PR | docs + local clone | `docs/harvest-c-docs` | — |
| **C1+C2** | This PR | docs | same | C0 |
| **D1-1** | Queued / **C3 HOLD** | `approval-routing` + `quote-service` tests | `test/deal-desk-d1-transition-spec` | C1–C2 merge |
| **D1-2** | Queued / **C3 HOLD** | `approval-routing`, `quote-service` BFF | `feat/approval-invalid-state-codes` | D1-1 |
| **D1-3** | Queued / **C3 HOLD** | `quote-service` + `approval-routing` | `feat/quote-cancel-cascades-approval` | D1-2 |
| **C-GATE** | After D1-1…D1-3 | — | — | D1-3 |
| **D0 (Queue D)** | **GATED** | docs | `docs/deal-desk-tcv-handoff` | C-GATE |

**Rejected shapes:** Easy Rules runtime; Mongo `/api/v1/deals` approve; dual-read YAML/env flip; Salesforce client; TCV calculator in routing; CRM Deal conflation; Activities; NgRx; editing `docs/forge/00-orchestrator-status.md` or `docs/plans/00-harvest-index.md` from this queue.

---

## C0 — Pin & controller inventory (done on this PR)

| Field | Value |
|-------|-------|
| Acceptance | Clone @ `d9bf654…`; parent D0 evidence; secret-scan; no `.env` copy |
| Evidence | Parent [deal-desk-reuse-gap-analysis.md](./deal-desk-reuse-gap-analysis.md) D0 evidence |
| Exclusive | Local clone path; parent D0 pin fields only |

---

## C1+C2 — Matrix + backlog (this deliverable)

| Field | Value |
|-------|-------|
| Owner | docs |
| Exclusive files | `docs/plans/deal-desk-reuse-gap-analysis.md` (D0 pin only), `docs/plans/deal-desk-capability-matrix-d1.md`, `docs/plans/deal-desk-harvest-backlog-d1.md` |
| Depends | C0 |
| Acceptance | Matrix rows for transitions / guards / BFF / policy-as-spec / exception map only; pin SHA recorded; backlog ranked D1-1…D1-3; no C3 code |
| Out of scope | Harvest Java/TS, dual-read flip, TCV file, forge status, harvest index |

---

## D1-1 — Transition living spec (rank 1)

| Field | Value |
|-------|-------|
| Owner | `approval-routing` + `quote-service` tests |
| Branch | `test/deal-desk-d1-transition-spec` |
| Exclusive files (approx.) | `backend/services/approval-routing/src/test/**`, `backend/services/quote-service/src/test/java/com/opencrm/quote/approval/**` (and quote cancel tests if needed). Prefer **test-only**. |
| Depends | C1–C2 merge |
| Mode | C — encode matrix §0–§1 as tests |
| Spec source | Source `DealServiceEdgeCaseTest` + `DealStatus` vs `ApprovalRequest` / Quote BFF |

### Acceptance

- [ ] Table of source DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELLED vs Quote + `ApprovalRequest` statuses is asserted (comments may cite matrix IDs T1–T4).
- [ ] Existing happy paths still pass: submit DEAL_DESK → PENDING QUOTE; approve/reject PENDING; BFF single-writer (no local `ApprovalRequest` insert on submit).
- [ ] Invalid approve/reject of non-PENDING still fail (today `IllegalStateException` until D1-2).
- [ ] **No** production behavior change required; if a test cannot be written without a prod fix, file it as D1-2/D1-3 and keep this PR test-only.
- [ ] Dual-read YAML/env **untouched**. No new `/api/v1/**` path. No Easy Rules. No TCV.

### GitNexus / risk

- Prefer test-only; if production symbols change, impact first (`ApprovalRequest`, both `ApprovalService` classes with `--file`).
- Confirm any `UNKNOWN` with `rg`.

---

## D1-2 — Invalid-state guards + error codes (rank 2)

| Field | Value |
|-------|-------|
| Owner | `approval-routing`, `quote-service` |
| Branch | `feat/approval-invalid-state-codes` |
| Exclusive files (approx.) | `ApprovalRequest.java` (routing), routing `ApprovalService.java`, routing `GlobalExceptionHandler` only if needed, quote `ApprovalService.java`, `ApprovalRoutingClient.java`, tests. **No** `approvals.legacy-local-read-enabled` / env. |
| Depends | D1-1 |
| Mode | C — port **missing** guards / clearer codes, not ProblemDetail |

### Acceptance

- [ ] Routing invalid approve/reject/cancel throws `BusinessException` (or equivalent) with a **stable** `errorCode` (`INVALID_STATE_TRANSITION` or `APPROVAL_NOT_PENDING` — pick one family and use it consistently). Include from/to or action in the message (source `INVALID_DEAL_STATE` details).
- [ ] Quote BFF does **not** map routing invalid-state HTTP to `APPROVAL_ROUTING_APPROVE_FAILED` / `…_REJECT_FAILED` **502**. Invalid-state stays 4xx with the routing code.
- [ ] BFF reject requires a non-blank reason/comments (source `validateRequiredText(reason)`). Stop defaulting to `"Rejected via quote BFF"` for empty input.
- [ ] ADR-006: DTOs + `ApiResponse<T>` only; do **not** switch APIs to RFC-7807 ProblemDetail.
- [ ] Dual-read default remains `true`. FE still quote BFF only. No `/api/v1/deals`. No Easy Rules.
- [ ] Unit tests green under Maven env above (`-DskipITs` sufficient if no new ITs).

### GitNexus / risk

- Impact `ApprovalRequest.approve` / `reject` / `cancel` with `--file backend/services/approval-routing/.../ApprovalRequest.java`.
- 2026-09-14 `approve` walk: **LOW**, epistemic lower-bound (1 untyped caller dropped) — **not** an all-clear; `rg` `request.approve(` / `ApprovalService.approve`.
- HIGH/CRITICAL → warn and stop for architect.

---

## D1-3 — Quote cancel cascades PENDING approval (rank 3)

| Field | Value |
|-------|-------|
| Owner | `quote-service` + `approval-routing` |
| Branch | `feat/quote-cancel-cascades-approval` |
| Exclusive files (approx.) | routing `ApprovalService.cancel` (new method on existing resource), routing controller **only if** internal path required; `ApprovalRoutingClient`; `QuoteService.cancel`; tests. Prefer **no** new FE-facing approval URL — hook existing `POST /api/v1/quotes/{id}/cancel`. |
| Depends | D1-2 (error codes exist for illegal cancel) |
| Mode | C — source `cancelDeal` analog without a deal resource |

### Acceptance

- [ ] Cancelling a quote that has a routing `PENDING` approval moves that approval to `CANCELLED` (`ApprovalRequest.cancel()` already exists; **service + BFF wiring** do not).
- [ ] `ACCEPTED` quote still `CANNOT_CANCEL_ACCEPTED`.
- [ ] Already APPROVED/REJECTED routing rows still cannot cancel (entity guard).
- [ ] BFF `ApprovalStatus` mapping: CANCELLED must not silently become `null` on any new read path this PR touches (today `mapStatus` drops CANCELLED — either skip cancelled in pending lists or add enum value **only if** product needs it; default: keep pending lists PENDING-only).
- [ ] **No** new agent-contract path unless matrix updated in the **same** C3 PR (prefer none). Dual-read untouched. No Salesforce. No TCV.
- [ ] Tests: cancel DRAFT (no approval) still works; cancel `PENDING_APPROVAL` cascades; illegal cancel codes from D1-2.

### GitNexus / risk

- Impact `QuoteService.cancel` and `ApprovalRequest.cancel` before edit. Empty/`UNKNOWN` callers on `cancel()` are expected (unwired) — confirm with `rg ApprovalRequest` / `quoteService.cancel`.
- Quote cancel is used by `QuoteController` — d=1 WILL BREAK if signature/semantics change; keep `QuoteDto cancel(Long id)`.

---

## Explicitly out of Track D1 backlog

| Item | Disposition |
|------|-------------|
| Easy Rules / SpEL DynamicRule / `RuleDefinitionController` | Exclude |
| Mongo deal service / `POST /api/v1/deals/**` approve | Exclude |
| Dual-read default → false | Exclude (ADR-005 hold) |
| Salesforce sync / `.env` copy | Parked |
| Profitability / payment-threshold Easy Rules as Drools | Won't-do for D1 (ACI payment domain ≠ CPQ margin DRL) |
| TCV / NPV / repricing implementation | Queue D handoff after C-GATE; pricing residual only |
| Reopen T1-1…T1-5 | Forbidden |
| CRM Deal stages / Activities / NgRx | Exclude |
| AI Deal Desk orchestrator prompts | Defer past C-GATE |
| Edit `docs/plans/00-harvest-index.md` / `docs/forge/00-orchestrator-status.md` | Forbidden for this queue |

---

## Track D1 done gate (before Queue D)

Residual approval gaps closed, ticketed, or won't-do. Then:

1. Write `docs/plans/deal-desk-tcv-handoff-to-pricing-t1.md` (D0 in Queue D).
2. Tickets go to a **pricing residual** backlog — **no** parallel TCV PRs.

**C2 exit:** This backlog. No C3 implementation in this PR.
