# DRG-756: approval submission recovery pilot

Baseline: main `98ba00a`. Scope: non-ramp synchronous submit only. Do not enable automatic approve/reject retries, change policy selection, or flip legacy-read/AI flags.

## Design

- The BFF derives a submit key from quote ID, UUID and optimistic version, and sends it in `Idempotency-Key`. A rolled-back quote save leaves that identity unchanged.
- Routing keeps a submit receipt in PostgreSQL. Insert-on-conflict followed by row lock serializes concurrent uses. Receipt, canonical approval creation and frozen first response commit in the same transaction; a rollback removes all three. A changed payload or verified requester conflicts rather than disclosing another caller's receipt. Retention is indefinite in this pilot.
- BFF persists canonical submission ID and source version alongside the pending projection. Successful replays return canonical history without rewriting the quote. Existing JPA optimistic versioning rejects conflicting saves.
- `expectedVersion` is required: reject missing/stale command versions before routing. This tightens the BFF submit contract; callers must read the quote version and reuse it on retries. No current frontend caller of this BFF approval-submit endpoint was found (the quote UI uses the distinct /submit endpoint).
- Routing remains the only approval-record writer. Quote fields are a projection and recovery pointer, not another approval record.

## Verification boundaries

Unit failure injection uses an independently retained routing receipt and fresh quote instance after the failed save. It verifies identical canonical ID, one receipt, restored projection, pending replay and stale-command rejection.

The tagged PostgreSQL integration test verifies insert/lock concurrency, frozen replay after a separate caller transaction fails, payload conflict, and rollback cleanup. Its canonical writer is a SQL-backed test double; it is **not** a live two-service/JPA/HTTP end-to-end test. Normally it uses PostgreSQL 16 Testcontainers; an explicitly supplied `receipt.test.jdbc` targets a dedicated local test database, which must not contain real data.

Independent review, full CI and live seller/approver testing remain required. This does not claim that DRG-756 policy/risk evaluation is complete.

## Follow-up: eventual projection consistency (review pending)

A canonical decision can commit after the BFF's read but before quote persistence. The projection is therefore **eventually consistent**, not a distributed atomic transaction. A temporary PENDING projection is conservative: it cannot authorize quote acceptance. A durable keyset sweep of pending quote rows with canonical submission IDs rereads routing and applies APPROVED/REJECTED only to the unchanged submission/version binding. The local update is transactionally fenced by JPA `@Version`. Crashes and transport/auth failures leave candidates eligible for a later sweep; separate service tokens are already supported by the existing E3 client. No reverse service edge is added.

If routing is already terminal when a failed-save request retries, the BFF projects that terminal state, not the frozen PENDING receipt. Missing/superseded/cancelled canonical records or changed/ramp bindings are not guessed into a valid approval; they require investigation. Reconciliation is limited to non-ramp APPROVED/REJECTED transitions in this pilot.

The worker runs at most 20 candidates per 30-second tick, with a 5-second admission budget (one in-flight GET can finish within its existing E3 timeout) on a dedicated single-worker executor, never on the shared/outbox scheduler, and advances a keyset cursor so a failed item cannot starve later items. Restart resets the cursor, not the durable work. Distinct outcome counters (`approval.projection.reconcile`) and structured warnings expose stale/missing/unsupported bindings separately from pending no-ops and retryable failures. At most one worker is running or queued. Service identity, reachable routing and continued scheduling are liveness prerequisites. There is no promised wall-clock repair SLA while a dependency is unavailable.

Real quote JPA/PostgreSQL tests now force the read/decision/save interleaving, find its durable candidate and verify repair, plus rollback and version fences. Routing is an independent test double in those tests; its durable receipt transaction/concurrency is tested separately. A deployed two-service HTTP/JPA test, PostgreSQL 16/Docker path and live seller/approver UAT remain unverified. Independent review must approve this eventual-consistency contract before the draft becomes merge-ready.

## Recorded local evidence (2026-11-18)

- Java 25 affected reactor `-pl services/approval-routing,services/quote-service -am -Punit-tests verify`: PASS, including 507 quote-service and 103 approval-routing tests plus dependent libraries. Mockito agent supplied explicitly in the test process; no CI flags were changed.
- `ApprovalSubmissionReceiptIntegrationTest`: 3/3 PASS on isolated PostgreSQL 17. Docker/Testcontainers and production PostgreSQL 16 were not exercised in this run.
- Refreshed GitNexus detect-changes: **CRITICAL**, 18 files / 66 indexed symbols / 835 affected processes. Broad graph reach is not waived by passing focused tests. Independent review and broader CI remain gates.

### Reconciliation follow-up evidence

- Final affected reactor verify: PASS, **515 quote-service + 103 approval-routing tests** and dependent libraries.
- Real quote JPA/PostgreSQL 17: **4/4 PASS**, including two transactions forced to read the same version (one commits, one optimistic-locks), flush/rollback, durable repair of the exact canonical-read/decision/save interleaving, and stale result fencing.
- Recovery-column/partial-index migration executed successfully in a rolled-back isolated PostgreSQL schema.
- Independent local re-review reported no further blocking code finding. Draft status remains until final-head CI and deployment validation.
- Graph analysis has broad CRITICAL impact and whole-index flow-budget omissions. It is not an exhaustive or clean graph assurance; review and tests remain necessary.
