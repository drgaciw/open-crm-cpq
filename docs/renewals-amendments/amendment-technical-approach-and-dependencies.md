# First slice: technical approach, dependencies, sequencing, risks and test strategy

**Delegated decisions (2026-10-17):** OQ18 places the shared commercial digest utility in S3. Nullable storage/envelopes and the durable CAM/source delivery contracts are adopted design; source implementation and story authorization remain separate. See the [decision record](prereq/delegated-renewals-amendments-decisions-2026-10-17.md).

*open-crm-cpq, universal renewals-and-amendments. Documentation only; no code. Date: 2026-10-16. Companion: `amendment-first-slice-requirements-and-acceptance.md` (requirement and story ids), `renewals-amendments-open-questions-register.md`. Conventions: [V] verified in the repo on 2026-10-16 through the GitHub connector or in the adjacent services assessment; [S] from a project file; [I] proposal.*

## 1. Approach in one paragraph
Build the spine in CAM as a separate contract-documents module (ADR-035 D-01, Accepted 2026-10-16; implementation authorized for S1 and S2 only), add a thin `contracts` header, and make CAM's existing typed apply (digest, `observedRevision`, Kafka inbox, advisory lock) the single writer of applied state [V, assessment 2]. quote-service gains a minimal LifecycleChange for intent Amend, and the existing continuation endpoint becomes a facade. The legacy QuoteAccepted apply path stays alive behind a per-tenant flag until the document path is proven. Everything is expand-then-contract and additive.

## 2. Service touch points
| Service | Change | Stories |
|---|---|---|
| contract-asset-management | New module (package `contractdocuments`), migration V1_7 [V next free version after V1_6], event writer and verifier, amendment API, apply orchestrator reusing `AcceptedQuoteSubscriptionService` internals, flag routing in `QuoteAcceptedEventConsumer` | S1, S2, S3, S4, S6, S7 |
| quote-service | LifecycleChange (Amend), `lifecycle-changes` API, facade on `QuoteContinuationController`, pricing client calls, pinning, QuoteAccepted emission with `amendmentDocumentId` | S5, S6 |
| pricing-service | Evaluate AmendmentPolicy modes RetainContracted, List, Override and proration Daily, MonthDay, None, NextCycle; return PolicyDecision and trace id. **Not verified**: a code search for these mode names returned nothing, so existence of the modes must be confirmed (OQ9) | S5 |
| backend/libs/common | Shared canonical commercial digest utility and fixtures: S3. QuoteAcceptedEvent fields and amendment event schemas: S6, separately gated. | S3 digest; S6 events |
| approval-routing | Use existing ApprovalType CONTRACT_MODIFICATION for overrides and backdating [S spike 6] | S5 |
| audit trail (DRG-309) | Consume outbox events | S2 |
| document-generation | Not touched in slice (no AMENDMENT render type; render out of slice) | none |
| product-service, configuration-engine, pricebook-service | Read only: version pinning inputs (DRG-421, DRG-428). No relationship model in slice | S5 |
| ordering, fulfilment | Not touched; seam documented (apply emits `AmendmentUnitApplied`) | none |

## 3. Schema and migration order (CAM)
1. **V1_7 expand** (S1): create `contracts`; add nullable `subscriptions.contract_id`; create `amendment_documents`, `amendment_document_revisions`, `amendment_document_lines`, `amendment_application_units`, `amendment_document_events`; indexes and uniqueness per schema section 4; INSERT-only grant and reject trigger on events; add nullable `amendment_document_line_id` and `revision_no` on `subscription_rate_periods` (A4).
2. **V1_8 backfill** (S7): one contract per subscription, set `contract_id`. Idempotent. No documents, no events.
3. **V1_9 apply ledger** (S4): new `document_apply_ledger` with a unique key (`document_id`, `unit_no`) and stored `apply_key_digest`; a replay with the same digest is a no-op and a different digest is rejected and audited; keep `applied_quote_acceptances` unchanged for the legacy path [I] (avoids altering a UNIQUE constraint under load; resolves the DRG-563 conflict by bypass, not mutation).
4. **Later contract phase** (not in slice): `contract_id` NOT NULL, retire legacy path, `retention_until` computation after OQ8.
Rules: every migration is compatible with the previous app version; no data rewrite of existing rate periods; rollback is app-level via the flag, not down-migrations.

## 4. API surface
CAM (resource names from spike 6.6 [S]): `POST/GET /contracts/{id}/amendments`, `GET /amendments/{id}`, `/revisions`, `/lines`, `/decisions`, `/application-units`, `/events`, actions `:submit`, `:approve` (internal, from approval callback), `:withdraw`, `:accept` (click-through), internal `:execute` and `:apply`. Errors use problem+json with stable codes (`VALIDATION_V4`, `SIGNATURE_NOT_SUPPORTED_IN_SLICE`, `ACTION_NOT_SUPPORTED_IN_SLICE`, `REVISION_STALE`). All writes accept an `Idempotency-Key`. quote-service: `lifecycle-changes`, `/revisions`, `/pricing-decisions`, `:submit`, `:withdraw`. Events: AmendmentDrafted, Submitted, Approved, Executed, UnitApplied, Applied, ApplyBlocked, Withdrawn [S spike 6.6].

## 5. Control flow (happy path, click-through SaaS)
1. Seller creates LifecycleChange (Amend) in quote-service; targets and `observedRevision` captured (V1..V3).
2. quote-service prices through pricing-service; pins product, pricebook, configuration; stores PolicyDecisions (V8, V9).
3. quote-service calls CAM to create the document (idempotent on `lifecycle_change_id`), lines copied by value with digest.
4. Submit validates V1..V14, status IN_REVIEW, approval requested if override, backdating or threshold.
5. Approval, then customer click-through acceptance; CAM executes in one transaction: assign `amendment_seq`, freeze revision, write event.
6. CAM apply orchestrator creates units and applies each in dependency order under the subscription advisory lock, re-checking V3, V9, V10, V13; writes rate periods and events; emits `AmendmentUnitApplied`, `AmendmentApplied`; ERP sync is re-queued as today [V].
7. Outbox publishes to the audit trail.

## 6. Dependency graph and sequencing
```
ADR-035 accepted (OQ1) ──► S1 schema ──► S2 event chain ──► S3 API/state machine ──► S4 apply ──► S8 e2e
                                │                                   ▲                  ▲
                                └──► S7 contract backfill           │                  │
Pricing modes verified (OQ9) ──► S5 quote-service LifecycleChange ──┴──► S6 event contract/flag ┘
```
Critical path: ADR-035 > S1 > S2 > S3 > S4 > S8. S5 and S7 run in parallel after S1 and the S3 contract is fixed (OpenAPI first). S6 needs S3 and S4 shapes.

Suggested calendar (two to three engineers) [I]: Day 0 decision meeting (accept ADR-035, confirm defaults, verify OQ9). Day 1: S1 and S2 in parallel with S5 design and OpenAPI. Day 2: S3, S7. Days 3 to 5: S4, S5, S6. Days 5 to 6: S8 hardening. Starting work (not finishing) within one to two days is realistic; shipping the slice is about one working week.

External dependencies: DRG-421 and DRG-428 (version pinning) are needed for the real V9 pins. Owner decision (option B, 2026-10-16): the slice does not block on DRG-428. Product external id and pricebook version are pinned directly. `configuration_hash` stays mandatory and non-null and is populated from an interim non-null `configuration_hash` pin (INTERIM: lowercase hex SHA-256 of the canonical string `product_external_id|pricebook_version|<option codes sorted ascending, joined by comma>`; DRG-428 replaces it with the real configuration hash). The pin is deterministic (option codes sorted before hashing), computed at pricing, stored on the line and re-checked at apply (V9). When DRG-428 lands it replaces the interim value; the column, not-null rule and V9 check do not change. There is no nullable configuration-hash alternative. DRG-457 (e-sign adapter) not needed. DRG-458 enrichment later. DRG-561 equals ADR-035 acceptance. RA-43 / DRG-577 and RA-44 / DRG-578 are not on the slice path.

## 7. Risks and mitigations
| # | Risk | Likelihood / impact | Mitigation |
|---|---|---|---|
| R1 | Scope expands beyond the ADR-035 acceptance | Medium / High | ADR-035 is Accepted for S1 and S2 only; S3 to S8 require their own readiness and authorization gates |
| R2 | Pricing-service lacks amendment modes or PolicyDecision trace (OQ9) | Medium / High | One day spike before S5; fallback is RetainContracted only with explicit unit price supplied by quote-service and flagged override |
| R3 | Legacy apply and document apply double-apply the same acceptance | Low / High | Routing by `amendmentDocumentId` presence; inbox dedup; test AC-S4-9 and AC-S6-2 |
| R4 | Digest canonicalization drift between quote-service and CAM | Medium / High | Shared utility in `libs/common`; golden-file contract tests |
| R5 | Gapless numbering contention on contract row lock | Low / Medium | Lock held only inside the EXECUTED transaction; concurrency test AC-S3-6 |
| R6 | Behaviour change: ledger updates wait for EXECUTED | Medium / Medium | Auto-execute for click-through and notice-only; per-tenant flag |
| R7 | Partial apply semantics surprise users | Medium / Medium | Default FR-12, visible state, correcting amendment path; confirm OQ5 |
| R8 | Hash chain write amplification and lock hot spots | Low / Medium | One chain per document; append inside same transaction; benchmark in S2 |
| R9 | Retention N unknown at go-live | High / Medium | `retention_until` nullable; production go-live gated on OQ8, not dev start |
| R10 | Scope creep into Swap, Upgrade, on-prem | High / Medium | Explicit rejection codes; RA-42, DRG-556, DRG-557 stay out |
| R11 | Backfill grouping assumptions wrong (one contract per subscription) | Medium / Medium | Header only, re-groupable via later merge intent DRG-554 |
| R12 | Open ADR-024..027 conflict later with apply seam | Low / Medium | Seam is the `AmendmentUnitApplied` event and `:apply` command; RA-43 reconciles |

## 8. Test strategy
- **Unit:** one failing and one passing case per rule V1..V14 (named by id); state machine transition table test; hash computation golden vectors.
- **Database:** migration test from V1_6 snapshot; constraint tests (unique, partial unique, trigger, grants) using Testcontainers PostgreSQL.
- **Integration:** Kafka with Testcontainers: consume, dedup, flag routing; reuse and extend fixtures `accepted-v1/*.json`; outbox-to-audit at-least-once publication with stable event IDs and exactly one committed audit-side effect after inbox deduplication.
- **Contract:** schema compatibility for `QuoteAcceptedEvent` in both directions: preserve the pre-extension consumer/parser contract and feed it a newly extended event to prove unknown-field tolerance; separately run the current consumer against the existing `accepted-v1/*.json` fixtures to prove new-consumer/old-event compatibility. OpenAPI contract tests between quote-service and CAM.
- **Concurrency:** parallel executes (numbering), parallel appends (chain), duplicate apply messages.
- **Property/replay:** replay the stream twice yields the same ledger; verifier detects injected tamper or gap.
- **Regression:** existing `AcceptedQuoteSubscriptionServiceTest`, `QuoteAcceptedSubscriptionKafkaIntegrationTest`, `SubscriptionRatePersistenceIntegrationTest`, `ErpSyncJobTest` must pass unchanged with the flag off [V names from assessment].
- **Non-functional:** apply latency and chain append benchmark; migration time on a copy of a production-sized table.
- **Evidence:** acceptance criteria ids are test names so Linear stories can link results.

## 9. Observability and operations
Metrics: documents by state, apply latency, blocked units, chain verification failures, legacy vs document path counts. Alerts: any chain break, any APPLY_BLOCKED older than 1 hour. Runbook: how to raise a correcting amendment from APPLY_BLOCKED (T4), how to disable the flag. Dashboards use `correlation_id`.

## 10. Items verified or flagged on 2026-10-16
Verified [V]: next CAM migration version is V1_7 (files V1_1..V1_6 exist on the docs branch checkout). PR #599 is already merged into `main` (merged 2026-10-16 17:23 UTC, 12:23 CT), so new doc commits on the branch do not appear in that PR. Not verified: pricing-service amendment modes (code search returned no results; confirm by reading the module).
