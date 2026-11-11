# DRG-520 activity timeline — delivery contract and evidence

Date: 2026-11-05. Branch: `feat/parallel-story-wave-20261105`.

Scope: implement the bounded activity timeline, including all four activity types, a CRM API/table, gateway predicate and frontend HTTP client. Provider sync remains DRG-521. This is implementation for review, not production rollout or a story-Done claim.

## Sources and readiness

- [Linear DRG-520](https://linear.app/drgamtd-workspace/issue/DRG-520/bc-12-06-activity-timeline-note-task-email-meeting): Backlog, 8 points, depends on DRG-510 (reported Done by the backlog review), blocks DRG-521.
- [Notion design](https://app.notion.com/p/3faf7cb4e4df8188a280ee019e1fe7e0) and [staffing row](https://app.notion.com/p/410f7cb4e4df819692e6ef53c0246020); no existing worker claimed this lane when inspected.
- [ADR-030](../adr/ADR-030-activity-timeline-and-mailbox-ingestion.md) Accepted; Activities hold lifted. D-01 requires controller/table/gateway/client together. D-02 defines NOTE, TASK, EMAIL and MEETING, at least one organization/contact/deal link and a verified actor.
- [ADR-016](../adr/ADR-016-identity-authentication-service-trust.md) D7 retains coarse authenticated CRM access; record/territory authorization is deferred. [ADR-036](../adr/ADR-036-account-tenant-ownership.md) is a Proposed commercial customer-tenant mapping, not a blanket CRM timeline prohibition. It remains unchanged and supplies no authority to this implementation.

An initial readiness draft conflated commercial account tenancy with workforce context. Independent review corrected that interpretation before implementation: this timeline uses verified workforce `tid` only as its partition, validates active linked CRM records and follows the existing coarse CRM access contract. It does not claim commercial tenant verification.

## API contract

| Method/path | Behavior |
|---|---|
| `POST /api/v1/activities` | Create NOTE, TASK, EMAIL or MEETING; 201 DTO |
| `GET /api/v1/activities` | Require at least one organizationId/contactId/dealId; supplied filters intersect; array plus PageInfo |
| `GET /api/v1/activities/{id}` | Read within caller's workforce partition; unavailable rows are 404 |
| `POST /api/v1/activities/{id}/complete` | TASK only; repeated/concurrent requests preserve first completion time |

Create fields: type, nonblank title (max255), occurredAt; optional organizationId/contactId/dealId (at least one, all supplied subjects must exist and be active), note (max10000), reference (max512), dueAt. NOTE requires text; TASK may carry text and dueAt; EMAIL/MEETING require an opaque thread/event reference, reject note/body storage and accept no dueAt. References are data and never dereferenced, verified against a provider or rendered as trusted URLs by this client.

Response fields: id, uuid, type, title, optional note/subject ids/reference/dueAt/completedAt, occurredAt, authorId, createdAt and `source: MANUAL`. Null fields are omitted. No entity, workforce partition, optimistic-lock internals, provider tokens or mailbox body is exposed. The server derives author from the verified human principal; request identity fields cannot change it. Missing human identity/workforce context fails closed (401); service/agent callers are refused (403) until a separately authorized agent capability exists. ADR-034 is not implemented.

List size is bounded 1–100 with nonnegative page; ordering is fixed occurredAt DESC then id DESC. Each activity query/write uses the verified workforce partition. Linked deleted subjects hide the activity consistently in list/get/complete. Existing CRM coarse access permits any authenticated human with workforce context to record/read/complete timeline tasks; no new owner-only or territory policy is invented.

## Privacy and scope

EMAIL and MEETING are **manual reference records**, not ingested thread/event entities or verified first-party evidence. Their metadata is manually supplied and always marked MANUAL, so future fact-ledger work must not treat it as provider-confirmed evidence. The API accepts no mail-body field and has no body retrieval endpoint. NOTE/TASK content is human-entered CRM text.

DRG-521 separately owns per-user Google/Microsoft grants, encrypted refresh tokens, cursors, personal-mail ingest filtering, consent/retention/purge, provider metadata ingestion and owner/share body access. Those capabilities are not delivered or enabled here. Opaque reference storage does not promise a working provider deep link. No service/agent scope, OAuth app, token or external fetch is introduced.

## Parallel ownership and migration

The activity lane owns new `com.opencrm.crm.activity` production/tests plus `V16__activity_timeline.sql`, this plan and parity inventory. A child implemented new `frontend/libs/crm/activities` client/models/tests and independently reviewed backend semantics. A second child wrote PostgreSQL/Flyway/concurrency tests and challenged timestamp precision. The root owns gateway predicates, client discoverability, test-script inclusion and steering-doc integration. Existing duplicate/custom-field worker packages are untouched.

The root initially allocated V16 locally. During the main-branch sync, CRM V16 and V17 were already allocated, so the activity migration moved to `V18__activity_timeline.sql`. No deployed migration or data apply was recorded. Additive rollback is reverting application code while retaining the unused table; destructive table removal requires checking and preserving any activity data first.

## Validation evidence and remaining gaps

Local GitNexus query/context used the current checkout, HEAD `7492fd099e71b55ecc20ab783367fa546c227b5e`, index refreshed 2026-11-05 19:22 CT (30307 nodes, 63422 edges). New activity symbols were absent: impact UNKNOWN, confirmed by text search before creation. That is new-symbol verification, not a LOW-risk graph verdict. Root runs graph change analysis on the assembled wave.

- `ActivityServiceTest`: all types, verified identity/workforce partition, missing/service/anonymous context, invalid combinations, missing/deleted/multiple subjects, bounded paging and idempotent task completion with microsecond precision.
- `ActivityControllerTest`: actual HTTP binding/controller/service/generated mapper/advice with principal fixtures; validation, array/PageInfo envelope, actor spoofing resistance, metadata-only response and completion routes. These standalone tests do not exercise the full security filter chain.
- `ActivityTimelineIntegrationTest`: five Docker-backed cases cover actual PostgreSQL/Flyway fresh/upgrade, four types, workforce isolation, ordering, rollback, real row-lock contention/idempotence and deleted linked subjects. **Not run without Docker.**
- Frontend client tests exercise endpoint/parameter/response/error contracts with the real response interceptor. No UI was added; no component, keyboard, accessibility or browser UAT claim.
- Gateway predicate tests include the actual PathRoutePredicateFactory and default/dev configurations (root evidence).

The first unit attempt failed on an unauthenticated one-argument JWT test fixture, which was corrected to the authenticated constructor; this is not claimed as a failing-first feature regression. Gateway failing-first evidence is separate. Final command outcomes are reported by the orchestrator after validation.

Known integration limit: organization merge currently soft-deletes the source account; timeline active-subject filtering then hides activities still linked to it. No historical activity-repointing/merge change is included. The root must decide its follow-up scope before claiming complete merge compatibility. Browser UAT/chaos and Docker integration are separate from unit success; CPQ services were not started. No feature flag, deployment, production data, secrets, CI workflow or remote delivery status changed.
