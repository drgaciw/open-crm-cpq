# Open-CRM-CPQ Order Management Requirements

| Document control | Value |
|---|---|
| Status | Proposed requirements, subject to product and architecture approval |
| Research date | 20 September 2026 |
| Repository baseline inspected | `drgaciw/open-crm-cpq`, `main`, commit `8db288016c6da39bea8c29322613718f15faa459` |
| Scope | Extend CRM and CPQ into order capture, fulfillment orchestration, visibility, and downstream handoff |
| Intended repository location | `docs/requirements/order-management-requirements.md` |
| Delivery | Research-backed requirements only; no application code, GitHub issues, or repository PR created |
| Priority convention | P0: minimum production pilot; P1: proposed expansion after pilot validation; P2: optional advanced capability |

## Problem statement

Open-CRM-CPQ needs a durable operational record between an accepted commercial quote and the work required to deliver it. The proposed order-management capability should answer what was purchased, what must happen next, what has actually been fulfilled, what is blocked, and which downstream systems have acknowledged the result. This document assumes an initial B2B, quote-led use case covering physical products, subscriptions, and manually delivered services; that market assumption requires owner confirmation before implementation.

The current architecture identifies quote, product, pricing, approval, document, and contract-asset services, but does not identify an order-management service or order workspace among its landed capabilities ([project architecture](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/docs/ARCHITECTURE.md)). The existing subscription event consumer creates a default subscription after quote acceptance and attempts activation after ERP synchronization, while the inspected ERP adapter explicitly simulates that synchronization rather than calling a real ERP ([subscription consumer](https://github.com/drgaciw/open-crm-cpq/blob/main/backend/services/contract-asset-management/src/main/java/com/opencrm/contract/event/QuoteAcceptedEventConsumer.java), [ERP adapter](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/backend/services/contract-asset-management/src/main/java/com/opencrm/contract/acl/ErpAdapter.java)). Consequently, this proposal treats reliable order-to-fulfillment and order-to-activation evidence as new work, not as an existing production capability.

No customer interviews, order-volume measurements, fulfillment baselines, or quantified business-loss data were available for this review. Operational benefits and performance objectives below are proposed targets, not observed results.

## Goals

| Goal | Proposed outcome | Measurement |
|---|---|---|
| Preserve the commercial agreement | Every pilot order references one accepted quote revision and an immutable commercial snapshot | Snapshot validation on every submission; daily reconciliation |
| Eliminate duplicate business effects | Zero duplicate fulfillment, activation, or billing requests attributable to retries in the pilot | Unique business-key checks, connector ledger, reconciliation reports |
| Make fulfillment visible | Every submitted order has a current progress view and every blocked task has an owner and reason | Operational completeness report |
| Reduce manual investigation | Reduce median exception diagnosis time by 30% against a measured pre-pilot baseline | First two pilot weeks establish baseline; evaluate after six further weeks |
| Make downstream handoff trustworthy | Every completed fulfillment obligation has evidence and every required downstream handoff has an acknowledgment | Completion checks and daily unmatched-record report |

## Non-goals

- **Full ERP replacement:** Do not build general ledger, accounts receivable, accounts payable, manufacturing planning, or revenue recognition inside OMS.
- **Full warehouse system:** Do not build warehouse slotting, labor optimization, mobile scanning, wave picking, or carrier-label infrastructure in the pilot.
- **New CPQ/pricing engine:** Do not duplicate product eligibility, discount calculation, configuration validation, or quote approval ownership.
- **Immediate omnichannel retail parity:** Marketplaces, BOPIS, ship-from-store, sophisticated sourcing optimization, and exchanges are not pilot launch requirements.
- **Autonomous commercial actions:** AI must not independently change commercial commitments, issue refunds, waive controls, or mark orders fulfilled.

## User stories

| ID | Persona | Story | Priority |
|---|---|---|---|
| US-01 | Sales representative | As a sales representative, I want an accepted quote converted into an order without rekeying so that the customer's approved agreement is preserved. | P0 |
| US-02 | Order administrator | As an order administrator, I want invalid orders held with actionable reasons so that bad data does not reach fulfillment systems. | P0 |
| US-03 | Fulfillment operator | As a fulfillment operator, I want to see pending quantities and tasks so that I can deliver the right items without duplicate work. | P0 |
| US-04 | Customer service agent | As a customer service agent, I want commercial, fulfillment, and downstream status on one timeline so that I can explain progress accurately. | P0 |
| US-05 | Integration operator | As an integration operator, I want to reconcile ambiguous responses before retrying so that outages do not duplicate shipments or activations. | P0 |
| US-06 | Customer service agent | As a customer service agent, I want to cancel eligible remaining quantities while preserving shipped quantities so that requests are handled safely. | P0 |
| US-07 | Fulfillment designer | As a fulfillment designer, I want a commercial bundle decomposed into physical and technical obligations so that dependencies are executed in the correct order. | P1 |
| US-08 | Returns specialist | As a returns specialist, I want return receipt, disposition, and financial reimbursement tracked separately so that returned goods are not confused with refunded payments. | P1 |
| US-09 | Revenue operations analyst | As a revenue operations analyst, I want fulfillment and subscription activation reconciled so that billing starts only at the agreed milestone. | P0 |
| US-10 | Sales representative | As a sales representative, I want amendments routed through CPQ so that revised commercial terms receive the right pricing and approvals. | P1 |
| US-11 | Operations manager | As an operations manager, I want exception aging and delivery performance reports so that I can identify recurring process failures. | P1 |
| US-12 | Administrator | As an administrator, I want scoped permissions and a durable audit trail so that operational actions are attributable and controlled. | P0 |

## Research findings and design implications

### Research scope and evidence quality

The review uses official product documentation, developer guides, and directly inspected repository source. It compares documented design patterns rather than attempting a procurement ranking, an exhaustive edition matrix, or a performance benchmark. “Not established” means the selected sources did not establish a capability; it does not mean the product lacks it.

The project knowledge and file repositories were empty after synchronization, so no earlier project requirements document was available to reconcile. Repository inspection was read-only, and no runtime or integration tests were executed; the repository web crawler could not retrieve the GitHub pages, so implementation observations were verified from the Git checkout instead. Source links identify the inspected code, and commit-pinned links are used for the most consequential findings.

### Representative software review

| Reference | Verified capabilities relevant to this project | Pattern to adopt | Fit and caution |
|---|---|---|---|
| Salesforce Order Management | Distinct fulfillment orders with supporting line, tax, and adjustment records; location-capacity hold, confirmation, and release operations ([developer guide](https://developer.salesforce.com/docs/atlas.en-us.order_management_developer_guide_html.meta/order_management_developer_guide_html/order_management_fulfillment_orders.htm)). | Separate the commercial order from execution records; reserve capacity explicitly. | Useful physical-fulfillment model; do not confuse location capacity with stock availability. |
| Salesforce Dynamic Revenue Orchestrator | Commercial products decompose into technical products through rules and mappings; technical items may represent products, services, or processes ([decomposition documentation](https://help.salesforce.com/s/articleView?id=ind.dro_design_time_decomposition.htm&language=en_US&type=5)). | Versioned commercial-to-technical mappings with lineage. | Closest conceptual fit for mixed CPQ bundles; a distinct reference from Salesforce commerce OMS. |
| Oracle Fusion Order Management | Orchestration sequences scheduling, reservation, shipment, and billing services; change handling applies constraints, computes deltas, holds tasks, and compensates affected steps ([orchestration](https://docs.oracle.com/en/cloud/saas/supply-chain-and-manufacturing/26b/faiom/orchestration-processes.html), [change management](https://docs.oracle.com/en/cloud/saas/supply-chain-and-manufacturing/26b/faiom/overview-of-managing-change-that-occurs-during-order-fulfillment.html)). | Durable steps, explicit external acknowledgments, and constrained changes. | Strong enterprise model; implement a bounded subset rather than recreating its configuration surface. |
| IBM Sterling Distributed Order Management | A central order repository, rule-based sourcing, split orders, dynamic allocation, partner fulfillment, and operational alerts ([IBM documentation](https://www.ibm.com/docs/en/order-management?topic=features-sterling-distributed-order-management)). | Central visibility, explicit sourcing decisions, actionable exception queues. | Valuable advanced target; full distributed sourcing is beyond the initial pilot. |
| Microsoft Dynamics 365 Intelligent Order Management | Provider-driven flows support validation, fulfillment-source assignment, DOM handoff, custom actions, branching, merging, and invoicing handoff ([flow documentation](https://learn.microsoft.com/en-us/dynamics365/intelligent-order-management/orchestration-flows)). | Adapter contracts and validated, publishable flow definitions. | Use as an orchestration reference, not as a verified current procurement recommendation. |
| Odoo | Sales connects quotations, sales orders, delivery, and invoicing; inventory offers confirmation-time, manual, and scheduled reservation, while returns distinguish reverse transfers from credit-note processing ([Sales](https://www.odoo.com/documentation/19.0/applications/sales/sales.html), [reservation methods](https://www.odoo.com/documentation/19.0/applications/inventory_and_mrp/inventory/shipping_receiving/reservation_methods.html), [returns](https://www.odoo.com/documentation/19.0/applications/sales/sales/products_prices/returns.html)). | Clear business documents and separate fulfillment versus financial effects. | Good operational UX reference; documentation does not establish that every reviewed feature belongs to a particular free/community edition. |
| ERPNext | Submitted quotations can become sales orders; orders support partial delivery/billing, holds, closure, and constrained amendment, with separate stock reservation entries ([sales orders](https://docs.frappe.io/erpnext/sales-order), [reservation](https://docs.frappe.io/erpnext/stock-reservation)). | Preserve quote lineage; separate delivery, billing, reservation, and closure. | Useful reference for the repository's Frappe-related heritage; this proposal does not assume ERPNext is installed or integrated. |
| Medusa | The fulfillment module isolates fulfillment data and supports third-party providers, shipping/pickup, and geographic/rule-based shipping options ([module documentation](https://docs.medusajs.com/resources/commerce-modules/fulfillment)). | A narrow provider interface and replaceable adapters. | Reuse concepts rather than importing another domain runtime into the Java service architecture. |
| Saleor | Multiple fulfillments can serve one order, including split shipments and partial quantities; returns, replacements, refunds, and stock effects have distinct behavior ([fulfillment documentation](https://docs.saleor.io/developer/order/order-fulfillment)). | Quantity-aware fulfillment records and separate after-sales processes. | Particularly useful for edge-case tests; do not copy its stock-bypass option into the pilot. |
| Apache OFBiz | Documents quotes, order management, multiwarehouse inventory, shipments, returns, invoices, and payments as integrated capabilities ([business-user overview](https://ofbiz.apache.org/business-users.html)). | Use its breadth as a scope checklist. | Overview-level evidence only; insufficient here for detailed workflow or component-reuse conclusions. |

Recency checking found an August 2026 SAP description emphasizing API-first composability, order visibility, exception handling, and AI recommendations; these are vendor-described capabilities, not independently measured performance outcomes ([SAP announcement](https://news.sap.com/2026/08/sap-order-management-services-a-leader-idc-marketscape-worldwide-ai-enabled-order-orchestration-fulfillment-applications-retail-b2c-2026-vendor-assessment/)). The Microsoft release-history page reviewed lists May 2025 as its latest entry and contains no retirement notice, which does not establish present commercial availability or a future support commitment ([Microsoft release history](https://learn.microsoft.com/en-us/dynamics365/intelligent-order-management/whats-new/whats-new-home-page)).

### Synthesis

- **Commercial and execution records should be distinct:** Salesforce's fulfillment-order model and ERPNext's separate sales, delivery, and invoice documents provide independent examples of this separation ([Salesforce](https://developer.salesforce.com/docs/atlas.en-us.order_management_developer_guide_html.meta/order_management_developer_guide_html/order_management_fulfillment_orders.htm), [ERPNext](https://docs.frappe.io/erpnext/sales-order)).
- **Order progress must be quantity-aware:** Saleor models partial fulfillment across records, and ERPNext tracks delivery and billing independently ([Saleor](https://docs.saleor.io/developer/order/order-fulfillment), [ERPNext](https://docs.frappe.io/erpnext/sales-order)).
- **Changes require more than editing a row:** Oracle evaluates constraints and compensates affected work; Salesforce documents dependent fulfillment lanes and product-specific scenarios ([Oracle](https://docs.oracle.com/en/cloud/saas/supply-chain-and-manufacturing/26b/faiom/overview-of-managing-change-that-occurs-during-order-fulfillment.html), [Salesforce dependency tutorial](https://trailhead.salesforce.com/content/learn/modules/complex-order-decomposition-and-orchestration-with-revenue-cloud/define-dependencies-and-fulfillment-scenarios)).
- **Returns and refunds are different processes:** Odoo requires financial reversal in addition to physical return when an invoice has already been validated; Saleor exposes return, refund, and replacement distinctions ([Odoo](https://www.odoo.com/documentation/19.0/applications/sales/sales/products_prices/returns.html), [Saleor](https://docs.saleor.io/developer/order/order-fulfillment)).
- **Integration boundaries are first-class product capabilities:** Microsoft models provider actions and Medusa isolates fulfillment providers, supporting a connector-oriented design rather than OMS-owned warehouse or ERP internals ([Microsoft](https://learn.microsoft.com/en-us/dynamics365/intelligent-order-management/orchestration-flows), [Medusa](https://docs.medusajs.com/resources/commerce-modules/fulfillment)).

Recommendation: build a native order-management bounded context, with a small persistent workflow executor and adapter interfaces. Borrow domain patterns, not entire vendor schemas; consider external OSS systems as future integration targets rather than silently importing them or their code. Any code reuse requires a separate repository, maintenance, security, and license review.

TM Forum is a useful future interoperability reference: TMF622 defines catalog-offer-based product ordering, while Oracle's documented TMF622/TMF641 implementation distinguishes product orders from downstream service orders ([TMF622 v5 guide overview](https://www.tmforum.org/resources/specifications/tmf622-product-ordering-management-api-user-guide-v5-0-0/), [Oracle TMF ordering](https://docs.oracle.com/en/industries/communications/order-service-management/7.5/concepts/tmf-orders1.html)). This proposal adopts that conceptual separation but makes no TM Forum compliance or certification claim.

## Current-project fit and prerequisites

### Verified baseline versus new work

| Area | Observed baseline | Required treatment |
|---|---|---|
| Application architecture | Java 25 / Spring Boot 4.0.6, Angular 22 / Nx, signals and HttpClient, PostgreSQL schemas per service, and Redis are identified as landed ([architecture](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/docs/ARCHITECTURE.md)). | Extend the existing stack; do not introduce a parallel frontend or ERP runtime. |
| Quote acceptance | `accept()` publishes CRM and outbox events; another `acceptQuote()` method constructs a legacy-shaped event ([QuoteService](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteService.java)). | Freeze the supported acceptance contract and test all callable paths before connecting OMS. |
| Accepted-quote payload | The richer event factory carries summary pricing, terms, correlation, configuration metadata, and line count, but not complete order-line snapshots; its committed configuration version is the literal `"v1"` ([event factory](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteAcceptedKafkaEventFactory.java)). | Add a trustworthy immutable snapshot contract; do not treat that literal as a verified configuration revision. |
| Quote identifiers | The factory prefers quote UUID with quote-number fallback; the alternative acceptance method supplies quote number ([event factory](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteAcceptedKafkaEventFactory.java), [QuoteService](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteService.java)). | Define canonical internal, public, and external identifiers; preserve aliases during migration. |
| Subscription creation | The consumer uses “Standard Plan,” dates derived from the processing date, and event total as ARR before attempting ERP sync and activation ([consumer](https://github.com/drgaciw/open-crm-cpq/blob/main/backend/services/contract-asset-management/src/main/java/com/opencrm/contract/event/QuoteAcceptedEventConsumer.java)). | Replace defaults for OMS-managed orders with line-level contract terms and authoritative activation evidence. |
| ERP integration | `ErpAdapter` sleeps and fabricates an ERP identifier; it does not implement a live ERP call ([adapter](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/backend/services/contract-asset-management/src/main/java/com/opencrm/contract/acl/ErpAdapter.java)). | Treat as a test double; never advertise its responses as production fulfillment or accounting success. |
| Outbox reliability | The relay marks an event published after initiating an asynchronous send; its completion callback logs asynchronous failure without visibly reversing that published mark ([relay](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/backend/libs/common/src/main/java/com/opencrm/common/event/OutboxRelayPublisher.java)). | Static delivery-loss risk requiring acknowledgment, crash, and retry tests before reuse; not a demonstrated production incident. |
| Governance | Kafka/event-bus work, subscription UI, approval dual-read changes, and certain other expansions remain held; DTO boundaries and gateway-only CSRF are explicit architectural rules ([architecture](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/docs/ARCHITECTURE.md)). | Obtain a new authorized wave/ADR for relevant changes; requirements do not override existing holds. |

The root README and operating architecture disagree about which CI system is primary; the inspected workflow is manual-only and agrees with the operating architecture's Jenkins-primary model. Implementation planning should reconcile that documentation drift rather than assuming both systems automatically run the same gates; the operating architecture also points to the shared CI contract and scripts ([operating architecture](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/docs/ARCHITECTURE.md)).

### Boundary proposal

| Responsibility | Proposed owner | Explicit boundary |
|---|---|---|
| Customer, contacts, organization, deal | Existing CRM | OMS stores identifiers and a submitted snapshot, not a second customer master |
| Sellable catalog, bundles, configuration eligibility | Existing product and configuration services | Technical fulfillment mapping references catalog versions |
| Commercial calculation and commercial approval | Existing pricing, pricebook, quote, and approval services | OMS preserves accepted economics; changed terms return to CPQ |
| Commercial order and execution progress | New `order-management` service | Owns orders, fulfillment obligations, tasks, exceptions, revisions, and integration ledger |
| Physical inventory and warehouse execution | Selected external system or explicitly approved inventory adapter | OMS does not claim an inventory master merely because it stores reservations |
| Subscription/asset lifecycle | Existing contract-asset service, extended by approved handoff | OMS reports fulfillment; contract-asset owns entitlement activation state |
| Invoicing, tax authority, payment capture, refunds | External finance/tax/payment systems | OMS stores requests, statuses, allocations, acknowledgments, and references |
| Operator UI | Existing Angular CRM shell and shared UI | New order workspace follows current signals/HttpClient conventions |
| AI assistance | Existing AI service, if later approved | Read/recommend first; deterministic domain commands enforce all controls |

## Requirements

All requirements below are proposals, not claims about current implementation. “Shall” identifies an acceptance obligation; identifiers remain stable when requirements are split into engineering stories. Every mutation also inherits NFR-01, NFR-02, NFR-03, and NFR-07.

### Commercial capture and validation

| ID / priority | Requirement | Acceptance criteria | Dependencies |
|---|---|---|---|
| OM-001 / P0 | Create an order from a supported accepted quote revision through a single conversion command. | Given an accepted and eligible quote revision, when conversion succeeds, then exactly one commercial order links to it; given an unaccepted or expired-before-acceptance quote, conversion fails with an actionable reason. The valid acceptance instant governs eligibility, not a later import date. | D1, OM-002 |
| OM-002 / P0 | Preserve an immutable accepted snapshot with complete lines, pricing, configuration evidence, terms, customer/address data, and source revision. | Given subsequent catalog or address edits, when the order is reread, then submitted values and snapshot hash remain unchanged; given missing mandatory fulfillment data, submission is held rather than populated with invented defaults. | D1 |
| OM-003 / P0 | Enforce idempotent conversion and canonical identity. | Given 100 concurrent identical conversion requests, then one order is created and all successful callers receive its ID; given the same idempotency key with a different body hash, return conflict. UUID and legacy-number aliases resolve to the same source identity. | D1, NFR-02 |
| OM-004 / P0 | Validate required data and record hold reasons before any external execution. | Given an unknown product, unsupported currency/UOM, invalid address, missing term, or absent approval evidence, then no fulfillment command is sent and each blocking reason has a code, owner, and resolution action; after correction, validation reruns. | OM-002, D2 |
| OM-005 / P0 | Preserve accepted pricing, discounts, tax treatment, charge type, and rounding without silently repricing. | Given a quote with one-time and recurring charges, then order totals reconcile exactly to the accepted snapshot using its rounding policy; changing catalog prices does not change the order. Unsupported precision or currency semantics produce a hold, not truncation. | OM-002, D1 |
| OM-006 / P1 | Support approved non-quote intake, including API/batch orders and customer purchase-order references. | Given a source-system/order key already imported, then reimport does not create a duplicate; given invalid rows, then the batch reports per-row results with no silent omissions. Non-quote orders receive equivalent commercial validation. | OM-001–005, D2 |

### Lifecycle, quantities, and control

| ID / priority | Requirement | Acceptance criteria | Dependencies |
|---|---|---|---|
| OM-007 / P0 | Enforce command-driven order and line state transitions with optimistic concurrency. | Given a stale version, a mutation returns conflict and changes nothing; given a terminal order, a prohibited transition is rejected. Direct client assignment of `COMPLETED` or `FULFILLED` is not accepted. | OM-001, lifecycle model |
| OM-008 / P0 | Track quantities and partial fulfillment separately from order-header state. | Given a ten-unit physical line and a confirmed shipment of four, then gross fulfilled is four and remaining to fulfill is six; duplicate confirmation changes neither value. Aggregate progress does not double-count bundle parents and their technical children. | OM-007, OM-010 |
| OM-009 / P0 | Support order/line holds and cancellation of eligible unfulfilled quantities. | Given work not yet dispatched, an authorized cancellation releases its reservations and records quantity/reason; given dispatched or externally ambiguous work, cancellation remains pending or is rejected until reconciled. Shipped quantities are not erased. | OM-007, OM-012, OM-016 |
| OM-010 / P0 | Generate a version-pinned fulfillment plan using simple approved mappings. | Given a supported physical, digital, or manual-service line, then the selected template creates deterministic obligations with source-line lineage; missing or ambiguous mappings hold the order. Repeating plan generation does not duplicate obligations. | OM-002, D3 |
| OM-011 / P1 | Support effective-dated decomposition of bundles into technical products/services, with quantity and attribute mappings. | Given a commercial bundle, then each technical obligation traces to source lines, rule version, and transformed inputs; a cycle, invalid UOM conversion, or equally ranked conflicting rule prevents publication. A preview has no side effects. | OM-010, D3 |
| OM-012 / P0 | Execute durable tasks with restart recovery and explicit success, failure, and unknown-outcome states. | Given a worker restart after dispatch, then it resumes or reconciles the existing operation rather than starting a duplicate; given a timeout after an external side effect may have occurred, then the task becomes `UNKNOWN_OUTCOME` and is not blindly retried. | D4, OM-016 |
| OM-013 / P1 | Execute dependency graphs with parallel lanes and cross-line dependencies. | Given activation dependent on delivery, then activation cannot start before authoritative delivery evidence; independent lanes may proceed, and a blocked lane does not falsely complete the order. Cycles are rejected before execution. | OM-011, OM-012 |
| OM-014 / P1 | Provide versioned change orders with impact preview and approved compensation. | Given an in-flight quantity/date/address change, then preview identifies affected tasks, price/approval changes, and irreversible work; approved execution creates a revision and compensates only eligible work. The original snapshot remains auditable. | OM-009, OM-013, D1, D6; revised-quote contract |

### Fulfillment and downstream handoff

| ID / priority | Requirement | Acceptance criteria | Dependencies |
|---|---|---|---|
| OM-015 / P0 | Represent sourcing and reservation through an authoritative adapter or explicitly controlled manual operation. | Given a stock-controlled line, then dispatch requires a valid reservation/allocation reference for its eligible quantity; a stale availability response cannot itself authorize dispatch. Cancellation or expiry reconciles and releases unused reservations. | D5, OM-010 |
| OM-016 / P0 | Provide adapter commands, callbacks, operation keys, and a persistent integration ledger. | Given a repeated command or callback, then its operation key produces one business effect; invalid signatures, wrong account/company scope, and replay outside policy are rejected. Unsupported adapter capabilities are visible before dispatch. | D2, D4 |
| OM-017 / P0 | Track physical fulfillment independently of delivery confirmation. | Given a fulfillment record, then warehouse/provider, line quantities, tracking/reference, dispatch time, and evidence are stored; a shipping-label creation does not count as shipment, and shipment does not count as delivery. Multiple partial records are supported. | OM-008, OM-015, OM-016 |
| OM-018 / P0 | Support digital/service fulfillment and controlled subscription/asset handoff. | Given a completed provisioning or manual-service task with required evidence, then the correct line-level activation request is emitted once; quote acceptance alone does not activate OMS-managed subscriptions. One-time charges are never treated as ARR. | D6, OM-012, OM-016 |
| OM-019 / P0 | Create reconciled finance handoff requests, not OMS-owned accounting entries. | Given the configured billable milestone, then a request carries line economics, charge type, quantities, tax references, and one stable request key; only an external acknowledgment changes handoff status to acknowledged. A timeout does not imply invoice creation. | D5, OM-005, OM-016 |
| OM-020 / P1 | Support multi-location sourcing, splits, backorders, and promise revisions. | Given two eligible locations and partial availability, then the selected policy produces an explainable split and a promise per fulfillment group; no quantity is reserved twice. The original promise remains available for performance reporting. | OM-013, OM-015 |
| OM-021 / P1 | Support supplier drop-ship and procurement handoff. | Given a sourced-to-supplier line, then procurement is requested once and fulfillment waits for supplier evidence; rejected or delayed supplier responses create an assigned exception. OMS does not fabricate a purchase order acknowledgment. | OM-016, OM-020 |
| OM-022 / P1 | Support configurable payment, credit, fraud, and compliance holds using external authorities. | Given an external hold, then affected dispatch is blocked; release requires a verified response or specifically authorized override with reason. Payment authorization, capture, refund, and settlement states remain distinct. | OM-009, OM-016, D5 |

### Exceptions, after-sales, and visibility

| ID / priority | Requirement | Acceptance criteria | Dependencies |
|---|---|---|---|
| OM-023 / P0 | Provide an exception queue with assignment, reason, next action, aging, and controlled recovery. | Given a nonretryable or ambiguous failure, then an exception is visible within 60 seconds with correlation and task references; retry/reconcile/waive actions are permission-checked and audited. A waiver cannot invent delivery, payment, or activation evidence. | OM-012, OM-016 |
| OM-024 / P0 | Provide order list/detail, search, filters, quantities, milestones, and an execution timeline in Angular. | Given an authorized user, then order number, quote, account, external reference, status, and exception filters work with pagination; detail shows immutable commercial data separately from fulfillment, returns, and finance. Failed loads are not rendered as empty successful results. | OM-007–010, OM-023, D7 |
| OM-025 / P0 | Reconcile order obligations with adapter and downstream records. | Given unmatched, duplicate, stale, or contradictory records, then reconciliation produces actionable findings without silently overwriting history; every pilot order is checked daily and every unknown outcome before redispatch. | OM-016–019 |
| OM-026 / P1 | Route commercial amendments, renewals, and asset changes through CPQ and contract ownership. | Given a priced or contractual amendment, then approved revised terms precede order execution; activation dates and proration come from authoritative terms rather than processing time. Usage-based changes preserve required usage/rate references. | OM-014, OM-018, D6 |
| OM-027 / P1 | Provide return authorization and receipt/disposition tracking. | Given a delivered quantity of four with one already returned, then a new return cannot authorize more than three remaining eligible units; receipt and inspection do not automatically refund or restock. Return reasons and source fulfillments remain linked. | OM-017, OM-025 |
| OM-028 / P1 | Request partial refunds, credit notes, and replacement orders with caps and approvals. | Given a received/approved return or authorized refund-only case, then the financial request uses original allocations and remaining refundable amount; duplicate requests cannot exceed the cap. Replacements create linked new obligations rather than resetting old shipment history. | OM-019, OM-027 |
| OM-029 / P1 | Provide operational dashboards and milestone notifications. | Given a defined date range, then exception age, order cycle time, partial fulfillment, cancellations, and original-promise performance reconcile to underlying records; notifications deduplicate by milestone and recipient and respect communication preferences. | OM-024–025, approved communications integration |
| OM-030 / P1 | Publish controlled workflow and mapping versions with effective dates and validation. | Given a draft definition, then only an authorized publisher can activate it; active orders remain pinned to their prior version unless explicitly migrated after impact review. Rollback selects a prior published version for new orders without rewriting history. | OM-011–014, D3 |

### Advanced capabilities

| ID / priority | Requirement | Acceptance criteria | Dependencies |
|---|---|---|---|
| OM-031 / P2 | Optimize sourcing against documented cost, capacity, promise, and split penalties. | Given the same inputs and policy version, then the optimizer yields reproducible decisions and explains constraint failures; it cannot violate inventory authority or fulfillment restrictions to improve a score. | OM-020, measured routing baseline |
| OM-032 / P2 | Offer customer self-service status, eligible cancellations, and returns. | Given an authenticated customer, then only their authorized orders are visible; self-service uses the same cancellation/return policy and cannot bypass execution cutoffs. | OM-009, OM-027, approved portal/identity scope |
| OM-033 / P2 | Offer AI-assisted exception diagnosis and recommended actions. | Given an exception, then recommendations cite order/task evidence and show uncertainty; consequential commands require human approval and normal domain authorization. Malicious text in notes or callbacks cannot authorize a tool action. | OM-023, approved AI integration |
| OM-034 / P2 | Offer standards-oriented product/service order adapters. | Given a selected TMF version or another external schema, then contract tests verify supported fields, states, and error mappings; unsupported operations are explicit and no untested compliance claim is made. | OM-016, D3, separate standards review |
| OM-035 / P2 | Support service appointments and resource-capacity scheduling through external systems. | Given a service obligation requiring an appointment, then a confirmed slot and resource reference are required; cancellations release the external booking exactly once and respect completed-work cutoffs. | OM-013, OM-016 |

### Nonfunctional requirements

The numeric values below are proposed pilot budgets and must be ratified after representative load measurement. They are not vendor benchmarks or measured characteristics of this repository.

| ID / priority | Requirement | Acceptance criteria |
|---|---|---|
| NFR-01 / P0 | Scoped authorization, least privilege, and secure boundary enforcement | Tests deny unauthorized view, edit, submit, cancel, retry, waive, publish, and finance actions; company/account scope is enforced server-side on reads, writes, searches, callbacks, and background jobs. Production bypass configuration is rejected. |
| NFR-02 / P0 | Atomic persistence and duplicate-safe business effects | Order mutation, local audit, and outbox insertion commit atomically; acknowledgments gate published status; consumer inbox and business-operation uniqueness survive restarts. Duplicate delivery is tolerated, but duplicate business effects are not. |
| NFR-03 / P0 | Auditable state and data protection | Every accepted command records actor/service identity, timestamp, reason, correlation, and before/after version; normal application roles cannot alter audit records. Secrets/payment credentials do not enter payload logs; sensitive addresses and personal data follow access and retention policy. |
| NFR-04 / P0 | Bounded performance and backpressure | On an agreed representative environment with 100,000 orders, up to 200 lines per order, 25 concurrent UI users and 10 intake requests/second for 30 minutes, local order reads meet p95 ≤500 ms and local command acknowledgment p95 ≤1 s, excluding external completion. Rate limits and queue bounds prevent silent dropping under overload. |
| NFR-05 / P0 | Recoverability and operational visibility | Proposed pilot SLO: 99.5% monthly API availability, zero loss of acknowledged local transactions after process restart, infrastructure-loss RPO ≤15 minutes and RTO ≤4 hours. Backup restoration, stuck-task alarms, oldest-outbox age, and connector failure alerts are demonstrated. |
| NFR-06 / P0 | Consistent accessible UX | Order workflows are usable by keyboard, expose focus and status/error announcements, and meet WCAG 2.2 AA as the proposed design target; automated checks plus manual keyboard/screen-reader testing cover critical tasks. Destructive actions show affected quantity and consequences before confirmation. |
| NFR-07 / P0 | Versioned contracts and safe changes | OpenAPI/event schemas, DTO mappings, money/UOM conventions, pagination, stable error codes, and compatibility tests are committed; no JPA entity crosses the public API boundary. A supported older consumer can process additive contract changes. |
| NFR-08 / P0 | Verification through the project's existing CI contract | State-machine, idempotency, quantity, adapter-contract, integration, UI, security, and recovery tests pass through the approved CI path. No existing coverage floor is reduced, integration tests are not silently skipped, and new stages are reflected in the shared CI contract. |

## Domain and lifecycle model

### Core entities

| Entity | Essential fields and invariants |
|---|---|
| `Order` | ID, number, company/deployment scope, customer/deal/quote references, accepted snapshot ID/hash, currency, source channel, customer PO, current revision, lifecycle state, aggregate version |
| `OrderRevision` | Immutable version, reason, source amendment quote, approval references, commercial effective dates, superseded revision link |
| `OrderLine` | Stable source-line ID, commercial parent, product/catalog/configuration versions, charge type, UOM, ordered quantity, monetary snapshot, service period, fulfillment policy |
| `FulfillmentObligation` | Order/revision/source-line lineage, technical product/task type, mapped quantity/UOM, required evidence, cancellation cutoff, completion predicate |
| `FulfillmentGroup` | Compatible destination/location/provider/service window; must not combine incompatible tax, legal-entity, or delivery constraints |
| `FulfillmentPlan` / `Task` | Pinned template version, inputs hash, dependencies, state, attempts, deadline, lease/fencing token, output/evidence references |
| `Reservation` | Authority, external reservation ID, SKU/location/UOM, quantity, expiry, version, release/consume state; not a shadow inventory ledger |
| `Shipment` / `ServiceCompletion` | Fulfillment quantities or completed obligations, provider reference, timestamps, tracking/provisioning evidence, external event IDs |
| `IntegrationOperation` | Stable operation key, request/response hashes, external ID, acknowledgment state, attempt count, last error, reconciliation result |
| `ExceptionCase` | Category, severity, owner, task/order reference, first/last occurrence, next action, deadline, resolution evidence |
| `FinancialHandoff` | Invoice/credit/refund request type, original commercial allocations, eligibility milestone, acknowledged external references; no GL ownership |
| `ReturnAuthorization` / `ReturnLine` | Original fulfillment references, requested/authorized/received/accepted quantities, disposition, refund/replacement links |
| `InboxEvent` / `OutboxEvent` / `AuditEntry` | Unique event/operation identity, schema version, correlation/causation, durable receipt/publication state, actor and change evidence |

For the pilot, retain the accepted CPQ quantity semantics rather than silently introducing fractional quantities. Any fractional UOM extension must update the source quote/configuration contracts first; order-to-technical quantity transformations must be explicit and versioned.

### Independent state dimensions

Do not encode every combination of payment, fulfillment, exception, and return status into one order-status enum. Use independent dimensions with server-derived projections and a documented transition table.

| Dimension | Proposed states / interpretation |
|---|---|
| Commercial lifecycle | `DRAFT`, `SUBMITTED`, `IN_PROGRESS`, `COMPLETED`, `PARTIALLY_COMPLETED`, `CANCELLED`, `REJECTED` |
| Processing control | `RUNNING`, `ON_HOLD`, `CANCELLATION_PENDING`, `AMENDMENT_PENDING` |
| Fulfillment progress | `NOT_STARTED`, `PARTIAL`, `FULFILLED`, `CLOSED_WITH_REMAINDER_CANCELLED` |
| Task | `PENDING`, `READY`, `RUNNING`, `WAITING_EXTERNAL`, `SUCCEEDED`, `FAILED_RETRYABLE`, `FAILED_TERMINAL`, `UNKNOWN_OUTCOME`, `CANCELLED`, `COMPENSATION_PENDING`, `COMPENSATED` |
| Finance handoff | `NOT_ELIGIBLE`, `PENDING`, `SENT`, `ACKNOWLEDGED`, `FAILED`, `UNKNOWN_OUTCOME` |
| Return | `NONE`, `REQUESTED`, `AUTHORIZED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CLOSED`, `REJECTED` |

Proposed lifecycle rules:

- **Submission:** `DRAFT → SUBMITTED` only after validation, snapshot freezing, and approved mapping selection; dispatch then moves the order to `IN_PROGRESS`.
- **Completion:** `COMPLETED` means all required fulfillment obligations succeeded and all policy-required activation/finance handoffs were acknowledged; it does not necessarily mean invoices are paid.
- **Partial terminal closure:** `PARTIALLY_COMPLETED` means some obligations succeeded and all remaining obligations were explicitly cancelled or closed by an authorized policy; an unresolved failure is not terminal closure.
- **Cancellation:** `CANCELLED` means nothing was fulfilled and all external work/reservations are reconciled as cancelled; a cancellation request alone is insufficient.
- **Holds:** A hold blocks new affected dispatch, not necessarily an external operation already in progress; releasing a hold does not skip validation.
- **Returns:** A later return updates after-sales state and financial projections without rewriting historical shipment or completed-order facts.
- **Rejection:** A pre-execution business rejection is distinct from a technical failure; transient outages create recoverable tasks/exceptions rather than pretending the order was commercially rejected.

### Quantity and monetary invariants

For a non-decomposed physical line, enforce `ordered = cancelledBeforeFulfillment + grossFulfilled + remainingToFulfill`, with all quantities nonnegative. Active reserved quantity must not exceed the eligible unfulfilled quantity; gross returned quantity must not exceed gross fulfilled quantity, and open return authorizations must not overbook the still-returnable quantity.

For decomposed products, apply quantity conservation within each obligation's own product and UOM. A commercial bundle is complete only when its configured completion predicate is met; adding the counts of unrelated technical components is not commercial completion.

- **Return accounting:** Keep `grossFulfilled` immutable as historical fulfillment; calculate a separate `netRetained` value and do not reopen shipment eligibility merely because a return arrived.
- **Money:** Use decimal arithmetic and the accepted currency's approved scale/rounding policy; never use binary floating-point money or assume every currency has two minor units.
- **Allocations:** Allocate order-level discounts, shipping, and tax to lines/fulfillments through a deterministic rule that preserves exact totals, with any remainder assigned predictably and auditable.
- **Refund caps:** Sum of acknowledged refunds plus outstanding reserved refund requests must not exceed the original refundable balance; releasing a failed request's reservation requires reconciled failure.
- **Subscription economics:** Separate one-time charges, recurring amounts, usage/rate references, and term-derived ARR; invoice value and order total are not universal substitutes for ARR.
- **Closed periods:** Historical invoiced economics are corrected through approved adjustment/credit processes, not destructive edits to the original commercial snapshot.

## Architecture and interface proposal

### Deployment shape

Start with one new Spring service containing separately organized order, execution, connector, and read-model modules. Use a dedicated `order_management` schema in the existing PostgreSQL deployment, with Flyway migrations and no direct cross-service table writes. Keep Redis optional for caching, not as the sole source of order, reservation, or idempotency truth.

```text
CRM / Angular order workspace
             |
        Existing gateway
             |
     Order-management service
       |        |          |
       |        |          +-- Outbox / inbox / audit / integration ledger
       |        +------------- Durable task executor + exception queue
       +---------------------- Order snapshots + revisions + progress
             |
      Versioned adapter interfaces
       |            |             |
 Warehouse /     Provisioning /   ERP / tax /
 inventory       contract-asset  payments

Quote service --> accepted snapshot + conversion command --> OMS
OMS -----------> verified milestone handoffs -------------> downstream owners
```

This is a proposed logical topology, not an instruction to enable held infrastructure. The architecture currently holds event-bus work and subscription UI, so transport selection and affected scope must be explicitly authorized first ([project architecture](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/docs/ARCHITECTURE.md)).

Two feasible transport choices should be evaluated in D4: an approved event-bus rollout after reliability hardening, or a bounded durable HTTP/outbox dispatcher with a pull-based reconciliation path. In either case, quote acceptance must atomically preserve its snapshot and pending handoff; a best-effort HTTP call after acceptance is not sufficient. Do not implement both transport stacks by default.

Use a persisted executor with leases/fencing, deadlines, retries, and explicit reconciliation for the pilot's simple templates. Evaluate a dedicated workflow engine only if later dependency, duration, throughput, or operational requirements justify its added platform cost; choosing Temporal, Camunda, or another engine is not a requirement of this document.

### Proposed API families

These routes are illustrative, not landed contracts. Exact names and envelopes must be registered in the project's API contract matrix before implementation.

| Surface | Proposed operations | Rules |
|---|---|---|
| Order intake | `POST /api/v1/orders/from-quote`; later `POST /api/v1/orders/import-jobs` | Idempotency key, accepted source revision, scope, snapshot identity |
| Order query | `GET /api/v1/orders`; `GET /api/v1/orders/{id}` | Paginated DTOs, stable filters, scoped access |
| Lifecycle commands | `POST /api/v1/orders/{id}/submit`, `/holds`, `/cancellation-requests` | Expected aggregate version; never arbitrary status PATCH |
| Plan visibility | `GET /api/v1/orders/{id}/fulfillment-plan`; `GET /api/v1/orders/{id}/timeline` | Explain rule/template version and evidence |
| Exception handling | `GET /api/v1/order-exceptions`; task-specific retry/reconcile commands | Operator permission, reason, safe-operation checks |
| Fulfillment evidence | Controlled `POST` commands for shipment/service evidence | Authorized adapter/operator, stable event ID, quantities, timestamps |
| Later amendments | Change-request preview and submit endpoints | Immutable diff, approval references, concurrency precondition |
| Later returns | Return authorization, receipt, disposition, and financial-request endpoints | Distinct workflows and quantity/value caps |
| Callbacks | Provider-specific endpoints behind approved authenticated ingress | Signed payload or approved service authentication; replay protection |

Represent durable asynchronous requests with `202 Accepted` and a status resource; use `201 Created` for synchronous creation and documented success semantics for replay. Use `409` for version/idempotency conflicts, `422` for semantic validation, `403` or non-disclosing `404` for denied scope according to platform policy, and retryable service errors without losing the durable operation.

### Event and adapter contracts

Proposed domain events include `OrderSubmitted`, `OrderHeld`, `FulfillmentPlanned`, `TaskOutcomeRecorded`, `ShipmentConfirmed`, `ServiceCompleted`, `CancellationRequested`, `OrderCompleted`, and `ReturnReceived`. Names, topic routing, and schema versions require contract approval; they must not be presented as existing topics.

Minimum event envelope:

```json
{
  "eventId": "opaque-unique-id",
  "eventType": "OrderSubmitted",
  "schemaVersion": 1,
  "occurredAt": "ISO-8601 UTC timestamp",
  "scopeId": "authorized-company-or-deployment-scope",
  "aggregateId": "order-id",
  "aggregateVersion": 3,
  "correlationId": "end-to-end-request-id",
  "causationId": "preceding-command-or-event-id",
  "payload": {
    "orderRevision": 1,
    "acceptedSnapshotId": "immutable-snapshot-id",
    "acceptedSnapshotHash": "verified-content-hash"
  }
}
```

The example illustrates field semantics, not a production event instance. If line snapshots are referenced rather than embedded, the receiving service must retrieve the exact immutable version under an authenticated contract, verify its hash, and retain it durably before dispatch.

Each adapter must declare capabilities such as `reserve`, `release`, `dispatch`, `cancelBeforeCutoff`, `statusLookup`, and `supportsIdempotency`. Its contract must define stable business keys, accepted versus completed responses, timeout semantics, provider rate limits, retry classification, and reconciliation behavior.

Prefer at-least-once delivery plus idempotent consumers and provider operations, not an unsupported promise of universal exactly-once transport. Where a provider cannot guarantee idempotency or status lookup, an ambiguous dispatch must go to manual reconciliation before another side-effecting attempt.

## Detailed acceptance scenarios

These cross-cutting scenarios complement the per-requirement criteria and form the initial end-to-end test catalog. They must run against controlled adapters and then against the selected pilot provider's sandbox where available.

| Test ID | Scenario and expected result | Requirements |
|---|---|---|
| E2E-01 | Accept a quote with hardware, a recurring subscription, and a manual service; preserve all terms, generate obligations, and require independent fulfillment evidence before completion. | OM-001, 002, 005, 010, 018 |
| E2E-02 | Replay conversion concurrently 100 times, including UUID and legacy-number aliases; create one order and one initial plan. | OM-003, 010 |
| E2E-03 | Change product price and address after submission; historical order values remain unchanged and a new commercial request requires a revision. | OM-002, 005, 014 |
| E2E-04 | Send two simultaneous reservations for the final available unit through the selected inventory authority; at most one succeeds, the other is held/backordered. | OM-015, NFR-02 |
| E2E-05 | Fulfill four of ten physical units, replay confirmation, then cancel two eligible units; totals become four gross fulfilled, two cancelled, four remaining. | OM-008, 009, 017 |
| E2E-06 | Provider completes shipment but HTTP response times out; mark unknown outcome, resolve through status lookup/evidence, and do not ship again. | OM-012, 016, 025 |
| E2E-07 | Broker/dispatcher acknowledgment fails asynchronously; the event remains recoverable and is not permanently marked delivered. Restart and replay produce one business effect. | NFR-02, OM-016 |
| E2E-08 | Receive delivery before a delayed shipment event; retain both facts, reconcile the valid causal sequence, and do not regress delivery status. | OM-017, 025 |
| E2E-09 | Cancellation races with shipment confirmation; optimistic concurrency and external reconciliation preserve the shipment and reject cancellation of already shipped quantity. | OM-007, 009, 017 |
| E2E-10 | Fail provisioning for one line after another line succeeds; preserve the completed work, assign an exception, and retry only the eligible failed operation. | OM-012, 018, 023 |
| E2E-11 | Replay fulfillment completion while the legacy quote-to-subscription consumer also receives the original quote event; migration controls produce one subscription/activation per intended recurring line. | OM-018, D6 |
| E2E-12 | Return one of four delivered units and attempt duplicate refund; preserve gross fulfillment, track net retained separately, and enforce the remaining refundable balance. | OM-027, 028 |
| E2E-13 | Forge a callback, change its scope, or reuse an unauthorized customer's order ID; reject without leaking data or creating any side effect. | OM-016, NFR-01 |
| E2E-14 | Restart executor and restore backup in the recovery drill; reconcile external effects before resuming side-effecting operations. | NFR-02, 05 |
| E2E-15 | Publish a new mapping while old orders are running; old orders retain their original plan version and new orders use the new version. | OM-010, 030 |
| E2E-16 | Send only partial finance acknowledgment or reject one invoice request; keep fulfillment history intact and expose unresolved handoff status without claiming the invoice exists. | OM-019, 025 |
| E2E-17 | Test different currency scales and a discount remainder across partial fulfillment/refund; exact order and financial allocations reconcile without rounding drift. | OM-005, 028, NFR-07 |
| E2E-18 | Disable OMS rollout after pilot orders have begun; prevent new intake while preserving operation, visibility, and reconciliation of existing pilot orders. | D6, NFR-05 |

For an inventory authority that lacks atomic reservation, E2E-04 cannot pass merely by testing the OMS UI. That provider must either gain a supported concurrency-safe reservation contract or be restricted to a manual allocation pilot that makes no automated availability guarantee.

## Delivery plan and launch gates

No calendar estimate is given because team capacity, deployment ownership, provider contracts, and order volumes have not been established. Each phase has an exit gate, and later work must not be interpreted as permission to override existing architecture holds.

| Phase | Scope | Exit gate |
|---|---|---|
| Foundation and decisions | Pilot-blocking D1–D13 decisions; immutable accepted snapshot; identifier contract; event/transport reliability; single-writer subscription design; threat model | Architecture approval, fixtures and contracts agreed, baseline risks reproduced in tests where applicable |
| Production pilot | All P0 requirements: quote-led intake, simple pinned plans, single-location physical fulfillment, digital/manual-service evidence, partial progress, pre-dispatch cancellation, operator queue, basic finance handoff, reconciliation | All P0 acceptance tests pass; one explicitly selected real provider or controlled manual operating process; no simulated success presented as live evidence |
| Orchestration expansion | P1 decomposition, dependency graphs, version publishing, change orders, multi-location/backorders, drop-ship, returns/refunds, commercial amendments, dashboards | Pilot operational review; provider and change/return contracts proven; appropriate holds explicitly lifted |
| Advanced optimization | P2 optimized sourcing, customer self-service, AI assistance, standards adapters, appointments | Measured demand and separate security/architecture approval |

### Pilot launch checklist

- [ ] Product owner approves initial customer segment, product types, currencies, legal entity, and manual versus integrated fulfillment model.
- [ ] Architect authorizes the new service, routes, transport, and any changes to held areas.
- [ ] Accepted quote snapshots are complete, immutable, reconstructable, and source-identity consistent.
- [ ] Async publication/acknowledgment failure and duplicate-delivery tests pass.
- [ ] OMS-managed subscriptions have one activation authority; legacy and new flows cannot both activate them.
- [ ] Warehouse/provisioning/finance authorities are named; mock adapters are visibly labeled and disabled for production success reporting.
- [ ] Order/list/detail/exception UI and gateway access controls pass accessibility and negative-security tests.
- [ ] Cancellation cutoffs, unknown-outcome recovery, operator escalation, and finance reconciliation have runbooks.
- [ ] Baseline volumes and pilot performance thresholds are ratified and load-tested.
- [ ] Backup/restore and existing-order drain/rollback are demonstrated.
- [ ] Applicable CI gates pass without lowering coverage or skipping integration tests.

## Migration, rollout, and rollback

Historical accepted quotes must not automatically become new orders merely because OMS is deployed. Inventory all existing accepted quotes, subscriptions, and downstream references; classify them as already fulfilled, in flight, cancelled, or unknown before deciding which records can be imported.

- **Eligibility fence:** Introduce an approved OMS-managed marker or equivalent routing decision tied to a deterministic cutover cohort. Exactly one path may create fulfillment/activation effects for a given source revision and line.
- **Shadow phase:** Build read-only candidate orders and reconciliation reports without external side effects. Compare source lines, terms, totals, identifiers, and expected subscription mappings.
- **Historical imports:** Import already fulfilled records as historical/read-only or explicitly completed-with-evidence, not as fresh tasks. Unknown history enters a review queue.
- **In-flight cutover:** Transfer ownership only after external operations and subscriptions have been reconciled; preserve their business keys and external IDs.
- **Controlled pilot:** Enable selected customers/products/operators, then expand cohorts after exception and reconciliation review.
- **Rollback:** Stop new OMS intake, but continue or safely pause and reconcile existing OMS-owned orders. Do not return those orders to a legacy creator that could duplicate subscriptions or shipments.
- **Data migration:** Use additive schema/contract changes where possible; a rollback of application code must not discard audit, order, reservation, or integration evidence.

## Success metrics and observability

Measure API acceptance separately from completed order capture and completed fulfillment. Avoid reporting an improved cycle time by excluding held orders or resetting the promise date.

| Metric | Definition | Proposed pilot target / review |
|---|---|---|
| Capture correctness | Orders matching their accepted source snapshot ÷ all converted orders | 100%; continuous validation and daily review |
| Conversion completion | Eligible accepted quote revisions converted within 60 seconds ÷ eligible accepted revisions | ≥99% under the agreed load; exclude only documented business holds, report those separately |
| Duplicate effects | Confirmed duplicate external shipments, activations, or financial requests caused by replay | 0; every instance blocks expansion pending root cause |
| Exception visibility | Time from detected terminal/ambiguous failure to assigned visible exception | ≤60 seconds p95 |
| Diagnosis time | Median interval from exception detection to identified next corrective action | Establish baseline for two weeks, then target 30% reduction over six weeks |
| Reconciliation coverage | Active/pilot orders checked in the previous 24 hours ÷ active/pilot orders | 100%; do not equate a completed scan with zero discrepancies |
| Original-promise delivery | Delivered eligible physical lines by original committed date ÷ due eligible physical lines | Establish baseline; agree target after provider selection |
| Activation accuracy | Activated recurring lines with correct terms and evidence ÷ activated recurring lines | 100%; investigate any default or fabricated term |
| Recovery | Proven backup restore and resolution of ambiguous operations | Meet approved RPO/RTO in a documented drill |
| Adoption | Eligible quote-led orders processed through OMS ÷ eligible orders in enabled cohorts | ≥90% by end of pilot, with reason codes for bypasses |

Operational telemetry shall include oldest pending outbox age, inbox duplicates, task leases, tasks overdue, unknown outcomes, callback signature failures, reservation expiry, provider latency/error rate, reconciliation mismatches, and aging by exception owner. Every metric must have a documented owner, alert threshold, and drill-down to order/task evidence.

## Open decisions and dependencies

Role names below are proposed accountable owners, not assignments to specific people. Blocking decisions must be resolved before their dependent capability is implemented or released.

| ID | Decision / dependency | Proposed default | Owner | Blocking? |
|---|---|---|---|---|
| D1 | Which accepted quote identity/revision and snapshot contract is authoritative? | One canonical immutable quote snapshot with stable line IDs; normalize legacy aliases; versioned handoff | CPQ lead + architect | Yes, pilot |
| D2 | Single-company deployment or shared multitenant deployment; identity and legal-entity scope? | Pilot one legal entity per deployment, with explicit authorization scope; do not claim shared multitenant isolation until implemented/tested | Product owner + security lead | Yes, pilot |
| D3 | Catalog ownership of technical products, mappings, completion rules, and plan versions? | Product owns product identity; OMS owns versioned fulfillment mappings/templates; approved references join them | Catalog lead + OMS architect | Yes, simple mapping for pilot; full designer for P1 |
| D4 | Which durable transport is approved, and how are held Kafka/event-bus items handled? | Choose one approved transport; fix acknowledgment/replay guarantees before downstream reliance | Architect + platform lead | Yes, pilot |
| D5 | Which inventory, warehouse, provisioning, tax, finance, and payment systems are authoritative? | One selected integration or controlled manual workflow per pilot domain; unsupported capabilities remain held | Operations + finance + integration lead | Yes, pilot |
| D6 | What milestone activates each subscription and how is the legacy creator fenced? | Line-specific verified fulfillment milestone, one creation/activation writer, no order-total-as-ARR shortcut | Contract-asset lead + revenue operations | Yes, pilot |
| D7 | Is one new order workspace authorized under current shell and wave rules? | Single order workspace using current Angular conventions; no subscription portal or NgRx expansion | Product owner + frontend lead | Yes, pilot |
| D8 | Are physical products, subscriptions, and manual services all needed initially? | Support those explicitly validated by pilot users; cut unneeded adapters rather than pretending generic support | Product owner | Yes, scope |
| D9 | Can one quote revision legitimately create multiple commercial orders? | One commercial order per accepted revision; split into fulfillment groups instead. Relax only with explicit remaining-value/quantity rules | Sales operations | Yes, identity semantics |
| D10 | Which reservation, cancellation, amendment, and billing cutoffs apply? | Contracted provider cutoffs and explicit billable milestones, never a generic “undo” | Operations + finance | Yes, relevant release |
| D11 | Which currencies, quantity scales, taxes, and recurring billing patterns are supported? | Match verified CPQ snapshot semantics; hold unsupported combinations | Pricing lead + finance | Yes, relevant release |
| D12 | Data retention, PII handling, audit retention, and administrator access? | Configurable policies approved for deployment jurisdiction; no invented statutory retention period | Security/privacy owner | Yes, production |
| D13 | Pilot workload and operational SLO ownership? | Ratify NFR budgets after measuring expected volumes and infrastructure | Platform/SRE + product owner | Yes, performance sign-off |
| D14 | Build native inventory or integrate an existing authority? | Integrate; reconsider only if inventory ownership becomes a separately funded product scope | Product owner + architect | No for a controlled manual pilot; yes before automated ATP claims |
| D15 | Formal TM Forum compatibility required? | No; use concepts only until a versioned adapter and compliance scope are funded | Architect + integration lead | No |

## Evidence-to-requirement traceability

This matrix records why requirements were selected, not a claim that any vendor's implementation should be copied. Repository risks are separated from market-derived design patterns.

| Evidence or finding | Requirements influenced | Reason |
|---|---|---|
| Salesforce commercial/technical decomposition and dependent fulfillment lanes ([decomposition](https://help.salesforce.com/s/articleView?id=ind.dro_design_time_decomposition.htm&language=en_US&type=5), [dependencies](https://trailhead.salesforce.com/content/learn/modules/complex-order-decomposition-and-orchestration-with-revenue-cloud/define-dependencies-and-fulfillment-scenarios)) | OM-010, 011, 013, 030 | Model product delivery work separately, preserve rule lineage, and enforce sequence |
| Oracle orchestration and constrained compensation ([processes](https://docs.oracle.com/en/cloud/saas/supply-chain-and-manufacturing/26b/faiom/orchestration-processes.html), [changes](https://docs.oracle.com/en/cloud/saas/supply-chain-and-manufacturing/26b/faiom/overview-of-managing-change-that-occurs-during-order-fulfillment.html)) | OM-009, 012, 014, 019 | Persist execution and acknowledge the limits of reversing external work |
| ERPNext order, partial-delivery/billing, and reservation documents ([orders](https://docs.frappe.io/erpnext/sales-order), [reservations](https://docs.frappe.io/erpnext/stock-reservation)) | OM-001, 008, 015, 019 | Preserve source agreement while separating commitment, reservation, delivery, and finance |
| IBM sourcing, splitting, and alerts ([IBM documentation](https://www.ibm.com/docs/en/order-management?topic=features-sterling-distributed-order-management)) | OM-020, 021, 023, 031 | Stage distributed sourcing after visibility and exception handling |
| Microsoft provider flows and Medusa module/provider isolation ([Microsoft](https://learn.microsoft.com/en-us/dynamics365/intelligent-order-management/orchestration-flows), [Medusa](https://docs.medusajs.com/resources/commerce-modules/fulfillment)) | OM-016, 030 | Build explicit adapter capabilities and controlled flow publication |
| Odoo and Saleor return/financial distinctions ([Odoo](https://www.odoo.com/documentation/19.0/applications/sales/sales/products_prices/returns.html), [Saleor](https://docs.saleor.io/developer/order/order-fulfillment)) | OM-027, 028 | Separate physical receipt, stock disposition, refund, and replacement |
| Salesforce usage-asset amendment restrictions ([amendment API](https://developer.salesforce.com/docs/atlas.en-us.revenue_lifecycle_management_dev_guide.meta/revenue_lifecycle_management_dev_guide/connect_resources_assets_amend.htm)) | OM-026 | Keep amendment pricing and usage/rate references in the CPQ/contract flow |
| Incomplete quote-line event snapshot ([event factory](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteAcceptedKafkaEventFactory.java)) | OM-002, 003, 005; D1 | Require a complete immutable handoff rather than reconstructing from mutable catalog state |
| Default subscription terms and simulated ERP synchronization ([consumer](https://github.com/drgaciw/open-crm-cpq/blob/main/backend/services/contract-asset-management/src/main/java/com/opencrm/contract/event/QuoteAcceptedEventConsumer.java), [adapter](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/backend/services/contract-asset-management/src/main/java/com/opencrm/contract/acl/ErpAdapter.java)) | OM-018, 019, 025; D6 | Require authentic milestones and protect against double activation |
| Outbox acknowledgment risk and existing architecture holds ([relay](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/backend/libs/common/src/main/java/com/opencrm/common/event/OutboxRelayPublisher.java), [architecture](https://github.com/drgaciw/open-crm-cpq/blob/8db288016c6da39bea8c29322613718f15faa459/docs/ARCHITECTURE.md)) | NFR-02, NFR-08; D4 | Reliability and authorization precede broader orchestration |

## Recommendation

Approve a foundation phase and a bounded quote-to-fulfillment pilot, not an attempt to match every enterprise OMS feature at once. Prioritize immutable commercial snapshots, duplicate-safe execution, trustworthy fulfillment evidence, operator recovery, and a single subscription-activation authority; then add deeper decomposition, in-flight changes, distributed sourcing, and after-sales automation once those foundations are proven.

The next planning artifact should split the approved P0 requirements into dependency-ordered engineering stories with explicit code ownership and test fixtures. Before doing that, resolve the pilot product types, downstream authorities, quote snapshot contract, transport approval, and subscription cutover decisions.
