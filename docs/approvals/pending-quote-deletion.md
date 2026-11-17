# Pending quote deletion containment (DRG-758)

`DELETE /api/v1/quotes/{id}` refuses a quote whose stored status is
`PENDING_APPROVAL`, before soft deletion or persistence:

```http
HTTP/1.1 409 Conflict
Content-Type: application/json
```

```json
{"success":false,"message":"Cannot delete a quote while approval is pending","errorCode":"QUOTE_APPROVAL_PENDING"}
```

The standard `ApiResponse` envelope may also contain its normal metadata. Clients
should use the stable `errorCode`, not parse the message. Quote detail already
renders this server message and code in its action error banner and retains the
selected quote; navigation and removal occur only on a successful delete.

An ACCEPTED quote retains HTTP 400 / `QUOTE_ACCEPTED_NO_DELETE`. Other eligible
statuses retain HTTP 204 and soft deletion. Missing/already-deleted quotes retain
404 / `RESOURCE_NOT_FOUND`; authentication and the existing repository lookup
are unchanged. This patch does not introduce tenant filtering or expand access.

The refusal makes no write to the quote, canonical routing request, local approval
projection or business-event outbox. The service/controller regression uses the
real HTTP controller, exception advice, service and JWT filter, snapshots all
quote fields, and checks that only the existing repository read occurs. Its
outgoing collaborators are mocks, so this is boundary evidence, not a live
cross-service or database integration test.

## Validation

- `QuoteDeleteTest`: pending 409 and no mutation, existing ACCEPTED 400, every
  eligible status (including DRAFT) 204, missing quote 404, unauthenticated/invalid
  token 401 before any repository access. Stored tenant provenance is retained.
- `ApprovalServiceSingleWriterTest.decisionOnAMissingQuoteNeverReachesRouting`
  retains isolated historical-orphan approve/reject coverage: no remote decision
  or quote save. The new guard does not remove or repair historical orphans.
- Quote-detail component regression: pending error text/code is visible, the
  selected quote remains, and no list navigation occurs.
- UAT `approvals.pending.delete-refused-keeps-request`: create a draft with a line,
  route approval, attempt deletion, expect 409/code, compare quote and original
  pending request before/after, and verify its inbox row and quote link remain.
  This replaces creating an orphan through a now-refused public API operation.
  The fixture stays tracked; teardown reports pending residue instead of silently
  cancelling or deleting approval history.

## Limits and rollback

This is interim containment, not atomic cross-service cancellation or race safety.
Concurrent submit/delete can still race; cancel followed by a late decision is
not addressed. Reliable routing retries/reconciliation (ADR-017), minimum policy
and seller-to-approver validation (DRG-756), and authorized historical orphan
inventory/retirement belong to separate reviewed changes. No historical requests
are automatically dismissed, cancelled, or removed by this patch.

Rollback is a revert of this commit/PR; no migration, flag or data repair is
required. Reverting restores the prior risk of orphaning a live pending approval.
