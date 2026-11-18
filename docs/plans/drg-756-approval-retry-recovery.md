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

## Independent review blocker (draft only)

**P1: canonical reread / quote-save race remains.** The BFF now refuses to project a frozen PENDING receipt if the canonical record is already terminal at reread. A canonical decision can still commit after that read but before quote persistence. These stores have no shared transaction; this patch does not provide atomic cross-service lifecycle consistency or a reconciliation worker. Do not merge or deploy as a completed recovery solution until the architect closes this race and an independent reviewer verifies a real two-service/JPA failure-and-concurrency test.

The PostgreSQL receipt tests validate the durable deduplication boundary, not the unresolved cross-service race. Live seller/approver UAT and full repository CI have not been performed.

## Recorded local evidence (2026-11-18)

- Java 25 affected reactor `-pl services/approval-routing,services/quote-service -am -Punit-tests verify`: PASS, including 507 quote-service and 103 approval-routing tests plus dependent libraries. Mockito agent supplied explicitly in the test process; no CI flags were changed.
- `ApprovalSubmissionReceiptIntegrationTest`: 3/3 PASS on isolated PostgreSQL 17. Docker/Testcontainers and production PostgreSQL 16 were not exercised in this run.
- Refreshed GitNexus detect-changes: **CRITICAL**, 18 files / 66 indexed symbols / 835 affected processes. Broad graph reach is not waived by passing focused tests. Independent review and broader CI remain gates.
