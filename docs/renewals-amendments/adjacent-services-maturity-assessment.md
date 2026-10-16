# Adjacent Services Maturity Assessment: New Contract, Renewal, Amendment

**Project:** open-crm-cpq universal renewals-and-amendments (repo `drgaciw/open-crm-cpq`, `main` @ `dd90501`, read via the GitHub connector on 2026-10-16). Documentation only; no code changes.
**Supersedes:** the transcript entry "Deferred item" (adjacent services evaluation deferred to day two).
**Inputs reused:** `cpq-project-transcript.md`, `cpq-renewals-amendments-research.md`, `amendment-document-design-spike.md`, `rca-sda-amendment-placement.md`, `cpq-pricing-policy-sanity-check.md`, `mid-term-amendment-policy-spec.md`, `amendment-entity-field-schema.md`, `renewal-policy-stub.md`, `price-lock-policy-stub.md`, Linear DRG-535..575, Notion RA-01..41.

**Legend.** [V] verified in repo source or live backlog today. [I] inference. Verdicts: **Mature** (usable as is for the spine), **Partial** (works for a subset, gaps known), **Not ready** (no implementation).

## Interpretation of "coding"
The repo has no service named "coding". [V] `backend/services/` holds ai-service, approval-routing, configuration-engine, contract-asset-management, crm-service, document-generation, gateway-service, pricebook-service, pricing-service, product-service and quote-service. I read "coding" as **product, SKU and configuration coding**: product-service (catalog, `sku`, `ProductType`), with configuration-engine and pricebook-service as the supporting services that encode what a line *is*. [I]

## Summary matrix

| Service | New contract | Renewal | Amendment |
|---|---|---|---|
| Coding (product / config / pricebook) | Mature [V] | Partial [V] | Not ready [V] |
| Contract-asset-management (CAM) | Partial [V] (SaaS path Mature) | Partial [V] | Partial [V] |
| Ordering | Not ready [V] | Not ready [V] | Not ready [V] |
| Fulfillment | Partial [V] (SaaS activation only) | Not ready [V] | Not ready [V] |

## 1. Coding (product-service, configuration-engine, pricebook-service)

**Evidence [V].**
- `product-service/.../catalog/Product.java`: unique `sku`, `ProductType` (22 values incl. ONPREM_PERPETUAL, ONPREM_CORE, ONPREM_SUPPORT, SAAS_SEAT_NAMED, SAAS_USAGE_METERED, HYBRID_MIGRATION_RAMP), `billingFrequency`, `unitOfMeasure`, state machine DRAFT > ACTIVE > DEPRECATED > RETIRED.
- `ProductService.java`: outbox events PRODUCT_UPDATED_V1 and PRODUCT_RETIRED_V1; `getBySku` cached; `update` mutates in place (no version).
- Packages are attribute, bundle, catalog, configrule, option only. No relationship entity.
- Tests: `ProductLifecycleEventTest`, `ProductRetireTest`, `ProductTypeConstraintIntegrationTest`, `HybridProductCatalogHttpTest`, `ConfigurationValidateParityFixtureTest`.
- configuration-engine: `ConfigurationSession` (IN_PROGRESS > COMPLETED, `selections` map, `valid` flag), `StatelessConfigurationEvaluator`, consumers for catalog lifecycle and retirement events with a redelivery test.
- pricebook-service: CRUD plus `/internal` batch resolve; outbox wiring test.
- `QuoteAcceptedEvent.LineItemSnapshot` carries `productExternalId`, `productType`, `quantity` (Integer) and a `ConfigurationSnapshot.committedVersion`, but no pricebook version or configuration hash.

| Case | Verdict | Gaps | Needed | Backlog |
|---|---|---|---|---|
| New contract | Mature | No catalog version; no frozen configuration hash | Version pinning for audit | DRG-421, DRG-428 |
| Renewal | Partial | Catalog not consulted (DRG-539); no successor or renews-into mapping; perpetual vs maintenance SKUs unlinked | Successor SKU, renewable flag, pinned List price for uplift basis | DRG-539, DRG-556, DRG-567, **RA-42 / DRG-576 (new)** |
| Amendment | Not ready | No upgrade, downgrade or swap relationships; no reconfigure from hash; capacity quantity is Integer | Relationship model and pinning so V-rules can validate Upgrade/Downgrade/Swap | **RA-42 / DRG-576 (new)**, DRG-429, DRG-557, DRG-552 |

## 2. Contract-asset-management (CAM)

**Evidence [V].**
- `event/QuoteAcceptedEventConsumer.java`: `KafkaInbox.claim` dedup, unreadable payload to DLT, no external call in the listener.
- `service/AcceptedQuoteSubscriptionService.java`: accepts NEW, AMENDMENT, EXPANSION, RENEWAL; SHA-256 commercial digest with reuse rejection; advisory lock per quote; `observedRevision` check against `commercial_revision`; NEW creates `SUB-<quoteId>` PENDING with typed rate periods; RENEWAL requires ACTIVE and start = end + 1 day, then extends `end_date`; AMENDMENT requires unique `sourceSubscriptionLineUuid`, co-terms and end-dates replaced periods; ERP sync re-queued.
- `V1_1`: `subscriptions` statuses PENDING, ACTIVE, SUSPENDED, CANCELLED, EXPIRED; one `plan_name` per subscription; no contract table.
- `V1_6`: `subscription_rate_periods` with `product_external_id`, `provenance`, `retired`; `applied_quote_acceptances` with UNIQUE `accepted_quote_id` and one `subscription_id`.
- `SubscriptionController`: list/get, activate, suspend, cancel. `POST /{id}/renew` stays registered and returns **410 Gone** (DRG-543, merged 2026-10-16). Headers are `Deprecation: true` and `Link: </api/v1/quotes/continuations>; rel="successor-version"`. The body error code is `SUBSCRIPTION_RENEW_DEPRECATED`. The call leaves dates, rates, and price unchanged. `Subscription.renew` and `SubscriptionService.renew` were removed. Priced renewal is `POST /api/v1/quotes/continuations`, applied on acceptance by `AcceptedQuoteSubscriptionService`. The `dd90501` read recorded this route as a date-only renew that skipped pricing; that behavior is retired (DRG-608).
- `RenewalScheduler`: nightly `renewalDate <= today AND ACTIVE`, publishes SUBSCRIPTION_RENEWAL_ELIGIBLE_V1 with no dedup.
- Events emitted: RampApplied and RenewalEligible only. Nothing sets EXPIRED.
- Tests: `AcceptedQuoteSubscriptionServiceTest`, `QuoteAcceptedSubscriptionKafkaIntegrationTest`, `SubscriptionRatePersistenceIntegrationTest`, `ErpSyncJobTest`, `RampScheduleServiceTest`, fixtures `accepted-v1/*.json`.

| Case | Verdict | Gaps | Needed | Backlog |
|---|---|---|---|---|
| New contract | Partial (SaaS Mature) | No Contract header; one subscription per quote; perpetual and capacity lines have no ledger target; legacy fallback starts "today" | Thin Contract header, entitlement ledger | DRG-458, DRG-561, DRG-556, DRG-557, DRG-460 |
| Renewal | Partial | Eligibility event unconsumed and re-published; date-only `POST /{id}/renew` is 410 Gone (DRG-543) and priced renewal is `POST /api/v1/quotes/continuations`; no renewal from SUSPENDED/EXPIRED; no expiry or grace transition; EXTENSION stored as RENEWAL; no applied-change event | Renewal policy on the spine; term-end and grace handling | DRG-540, DRG-544, DRG-545, DRG-546, DRG-555, DRG-558 |
| Amendment | Partial | No AmendmentDocument; applies on QuoteAccepted, not EXECUTED; one subscription per acceptance; no hash-chained audit; no partial application | Spine module in CAM, apply after EXECUTED (OQ2 resolved) | DRG-535, DRG-536, DRG-561, DRG-562, DRG-563, DRG-564, DRG-565, DRG-566 |

[I] CAM's typed apply (digest, revision, inbox) is the strongest asset in the codebase and is the right single writer for the spine.

## 3. Ordering

**Evidence [V].**
- No order service, package or `orders` schema.
- `docs/adr/ADR-024` (order-service, port 8091), ADR-025 (saga), ADR-026 (commands) and ADR-027 (compensation, point of no return) are **Proposed 2026-10-04, not accepted**. ADR-024 says outright: "There is no order code in `backend/` or `frontend/`."
- Today `QuoteAccepted` is the de facto order, consumed independently by CAM, document-generation and crm-service.
- ADR-024 claims CAM "ignores `dealType`" and so creates a second subscription on amendment. That is **stale**: the current code applies typed changes to `subscriptionChange.targetSubscriptionNumber`.

| Case | Verdict | Gaps | Needed | Backlog |
|---|---|---|---|---|
| New contract | Not ready | No Order aggregate, holds or decomposition | Accept ADR-024 after reconciliation; order skeleton | DRG-290, DRG-464, DRG-465, DRG-505 |
| Renewal | Not ready | No RENEW order action wired to renewal policy | Action mapping from ChangeLine | DRG-467, **RA-43 / DRG-577 (new)** |
| Amendment | Not ready | ADR-024 order key and `ActivateSubscription` switch conflict with executed AmendmentDocument, gapless numbering and apply after EXECUTED | Amend ADR-024: document as source, `ApplyAmendmentUnit` command, mapping table | **RA-43 / DRG-577 (new)**, DRG-467, DRG-562, DRG-561 |

## 4. Fulfillment

**Evidence [V].**
- No fulfilment service.
- `ErpSyncJob` claims QUEUED rows, calls `ErpAdapter` and activates PENDING subscriptions. The adapter is simulated (`Thread.sleep`, returns `ERP-<number>`).
- document-generation renders QUOTE_PDF, CONTRACT_PDF and ORDER_FORM; it has no AMENDMENT type (spike).
- ADR-027 classes licence issue, shipment and ERP posting as non-compensatable.

| Case | Verdict | Gaps | Needed | Backlog |
|---|---|---|---|---|
| New contract | Partial | SaaS activation via simulated ERP only; no entitlement or licence provisioning | Entitlement provisioning, real ERP adapter | DRG-460, DRG-466, DRG-495 |
| Renewal | Not ready | No extend or regenerate step | Fulfilment-required event; renew operations | DRG-558, **RA-44 / DRG-578 (new)** |
| Amendment | Not ready | No action-aware decomposition; revocation on downgrade or remove is uncovered outside conversions | Decomposition keyed on action | **RA-44 / DRG-578 (new)**, DRG-465, DRG-574 |

## Overall readiness
[I] Only CAM is close to ready, and only for SaaS subscriptions. It can already apply typed new, renewal and co-termed amendment changes idempotently, which covers the 40% SaaS book for a single subscription. Coding is solid for selling but cannot yet express the transitions an amendment needs. Ordering and fulfilment exist only as proposed ADRs, and those ADRs predate the spine. The 60% on-prem book (perpetual, maintenance, capacity) has no ledger target and no fulfilment path, so every on-prem renewal or amendment is blocked downstream of CAM. Mid-term SaaS amendments can ship first without ordering or fulfilment, provided the seam is defined now so order-service can slot in later without a second apply path.

## Recommended build order (spine plus policies)
1. **Decide the seams:** placement ADR (DRG-561), then reconcile ADR-024 to ADR-027 with the spine (RA-43 / DRG-577) before anyone accepts them.
2. **Spine in CAM:** thin Contract header (DRG-458), AmendmentDocument module with the hash-chained log, multi-subscription apply ledger (DRG-563), apply after EXECUTED (DRG-562), pricing decision audit (DRG-564).
3. **Coding minimum for the mid-term policy:** catalog versions and configuration hash (DRG-421, DRG-428), product relationships (RA-42 / DRG-576), catalog in the flow (DRG-539).
4. **Mid-term amendment policy release one** (SaaS, single contract), then the **renewal policy** (DRG-540, 544, 545, 555, 567) with term-end and grace handling (DRG-558).
5. **Ordering skeleton** behind the strangler switch: QuoteAccepted source first, then the executed-document source (DRG-464, 465, 467).
6. **Fulfilment and on-prem:** entitlement ledger and provisioning (DRG-460, 558), action-aware decomposition (RA-44 / DRG-578), orchestrator (DRG-466), licence determination (DRG-495), perpetual and capacity (DRG-556, 557). Price-lock policy (DRG-568) after renewal.

## New backlog items (created 2026-10-16, highest IDs checked first: RA-41 / DRG-575)
| RA | DRG | Priority | Title |
|---|---|---|---|
| RA-42 | DRG-576 | High | Catalog product relationship and lifecycle-eligibility model for amendments and renewals |
| RA-43 | DRG-577 | High | Reconcile proposed order model (ADR-024 to ADR-027) with the amendment spine and apply-after-EXECUTED |
| RA-44 | DRG-578 | Medium | Action-aware fulfilment decomposition for amendments and renewals |

All three are in Linear (team DRG, project OPEN-CRM-CPQ, label `renewals-amendments`) and Notion (Open-CRM-CPQ Backlog), cross-linked. No other gaps needed new items; each maps to an existing ID above.


## Addendum 2026-10-16 (dev-readiness pass; nothing above changed)
First slice (mid-term SaaS amendment) needs only CAM and quote-service; ordering and fulfilment stay out of the critical path. Coding is needed only for minimal version pinning (DRG-421, DRG-428). Implementation stories RA-45..RA-52 / DRG-579..DRG-586 were created. Pricing-service amendment modes were not part of this assessment and are unverified (OQ9).
