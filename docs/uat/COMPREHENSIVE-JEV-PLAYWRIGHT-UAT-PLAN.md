# Comprehensive Open CRM/CPQ UAT Plan

Date: 2026-09-19
Status: Proposed execution plan; not a statement of completed coverage.
Target repository: drgaciw/open-crm-cpq; local C:/Users/dgorn/cpq-crm/open-crm-cpq.
Baseline: main includes 8db28801. Preserve and separately identify existing uncommitted changes.

## Objective and coverage contract

Prove that the implemented CRM and CPQ workflows produce correct persisted business outcomes through the real UI, gateway, and owning services. Separately measure whether live Jev can choose the actions needed to complete those workflows. Application correctness and model effectiveness are different acceptance criteria.

'All relevant' means every implemented route, user-visible action, component, service contract used by those actions, and material asynchronous side effect is mapped to a test or an explicit exclusion. It does not mean every Cartesian combination or future roadmap feature. Freeze the inventory at an exact commit; update the coverage ledger when it changes.

Source of scope: AGENT.md, docs/ARCHITECTURE.md, docs/adr/README.md, docs/backlog/CPQ_API_CONTRACT_MATRIX.md, frontend/apps/crm/src/app/app.routes.ts, component files, and backend/pom.xml. The contract matrix can lag source; reconcile controller mappings, frontend clients, authorization and DTOs before generating executable cases. Never infer a missing endpoint from a roadmap.

## Current baseline and blockers

Prior bounded UAT: deterministic Playwright21/21 smoke checks; four live Jev navigation goals passed. Jev product-fill passed but submission stopped below0.90 confidence. Product save, pricing YAML, pricebook outbox wiring, and failed-load empty UI were fixed with regression tests. These results are limited baseline evidence, not acceptance of the entire platform.

P0 prerequisite: protected pricing and pricebook requests currently return anonymous403. Establish a supported, authenticated local path across browser, gateway and domain services. Document how credentials/tokens are issued, forwarded, scoped, expired and refreshed. No general permitAll, production security weakening, or default-flag changes. If the repository lacks an authentication implementation, track that as prerequisite engineering; full protected UAT stays BLOCKED. An isolated test-only identity provider may be designed explicitly, with fail-closed production configuration and its own tests. A mock API is not a substitute for integrated acceptance.

Other prerequisites: bring up approval-routing, configuration-engine, document-generation, contract-asset-management and ai-service where relevant; identify their actual runtime dependencies and supported contracts. Verify Kafka/outbox consumers and document storage rather than claiming completion from producer success. Readiness means schema migrations, dependencies, representative API calls and authentication pass, not just actuator200.

## Test layers and responsibilities

| Layer | Execution | Purpose | Pass evidence |
| --- | --- | --- | --- |
| Unit/component | Vitest and Maven | Validation, calculations, status gating, UI states | Focused assertions including negative/boundary cases |
| API/contract | HTTP clients, controller tests | DTOs, statuses, pagination, auth, ownership | Exact request/response schema and persisted outcome |
| Service integration | Real PostgreSQL/Redis and required brokers | Transactions, cross-service references, event delivery | Record IDs, commit/rollback, consumed events |
| Deterministic browser | Playwright with fixed tasks | Repeatable application correctness | User-visible assertion plus independent API postcondition |
| Agentic browser | Live Jev choices + Playwright actions | Task selection, recovery, reliability | Action trace and same independent business assertions |
| Fault-injected UI | Playwright route fixtures | Timeout,403,409,500, malformed response presentation | Correct UI state, no fabricated success; explicitly mocked |

A fixture pass, confidence stop, missing service, unsupported feature and app defect have separate statuses. API tests cover backend-only services; do not invent browser UI for them.

## Service coverage and startup groups

| Service | Integration focus | Prerequisites / consumer |
| --- | --- | --- |
| gateway8080 | Routing/rewrites, origin/CSRF policy, identity forwarding, envelopes, timeout handling | Every UI/API flow; frontend4200 must proxy gateway |
| crm8081 | Organizations, contacts, leads, deals, pipeline/dashboard aggregates and links | PostgreSQL; CRM pages and quote references |
| product8083 | Product lifecycle, search, options, attributes, bundles, config rules | Catalog and quote/CE consumers |
| pricing8082 | Customer context, rule sets/DRL, execute/simulate, waterfall/audit | Auth; golden fixtures; pricebook/product integration |
| pricebook8089 | Header/entry lifecycle, effective pricing and batch resolution, outbox | Auth; real DB; pricing consumer |
| quote8084 | Header/lines, calculation, lifecycle, clone/delete, approval BFF, documents | CRM/product/pricing/approval dependencies |
| configuration-engine8088 | Stateless validate alias, sessions, selection constraints, completion/cancel | Product rule source, cache TTL; backend-first coverage |
| approval-routing8085 | Pending/tier filters, decisions, conflicts, quote reconciliation | Auth roles, quote BFF; preserve legacy dual-read default |
| document-generation8086 | Job DTOs, processing states, failure/retry, artifact integrity | Required broker/storage/template providers; quote panel path verified first |
| contract-asset-management8087 | Subscription DTO/list and implemented lifecycle/reference effects | API-only; no subscriptions frontend currently implemented |
| ai-service | Implemented AgentRouter/tool contracts, upstream errors, grounded outputs | Current runtime provider is Azure gpt-4o; optional separately metered lane |

Ports are architecture defaults; resolve actual config before launch. PostgreSQL16, Redis7, Kafka and external integrations get explicit health/ownership entries. Shared common/security/rules-engine libraries are exercised through focused tests and their consumers. Pricing gRPC currently needs9095 locally because9090 is occupied; document overrides. Do not kill unrelated services. Capture owned PIDs and startup commands, and stop only test-owned resources at teardown unless retaining them is requested.

## Workflow matrix

P0 = core correctness/data integrity; P1 = broad acceptance; P2 = supplementary robustness. Each row expands into cases with IDs, initial fixtures, exact steps, expected API/UI state, cleanup and evidence. Every create/update is verified after a fresh-session reload, not only a toast.

| ID / priority | UI surface | Business workflow and assertions | Services |
| --- | --- | --- | --- |
| CRM-01 P0 | Organizations list/form/detail | Create, search, edit, reload, link records; cancel leaves unchanged; supported delete honors relationships | gateway,CRM |
| CRM-02 P0 | Contacts list/form/detail | Required names, email validation, organization association, edit/search; missing/deleted reference handling | gateway,CRM |
| CRM-03 P0 | Leads list/form/detail | Create, amount boundaries, supported status/loss transitions and reason dialog; conversion only if implementation exposes it | gateway,CRM |
| CRM-04 P0 | Deals list/form/detail/kanban | Create, link CRM records, move stage via supported controls, win/lose rules, amount precision, reload; list and kanban agree | gateway,CRM |
| CRM-05 P1 | Dashboard | Counts and pipeline totals match seeded records and updated stages; loading/error/retry; no stale aggregate after relevant refresh | gateway,CRM |
| CAT-01 P0 | Catalog list/create/detail | Minimal and full product create, optional defaults, duplicate SKU, invalid price, update, lifecycle controls, search/category/status/page; persisted values exact | gateway,product |
| CAT-02 P1 | Option-group/option dialogs, attribute controls | Parent-child creation/removal, required/type/range constraints, invalid references, cancellation, reload | gateway,product |
| CAT-03 P0 | Config-rules panel/create dialog | Supported REQUIRES/EXCLUDES/VALIDATES/RECOMMENDS/DEFAULT scenarios; invalid rule rejected; edit/delete only if exposed; product rule vs CE evaluate parity | product,CE,gateway |
| BND-01 P1 | Bundles list/create/detail/item dialog | Create bundle, add/remove supported products, invalid quantities/references, persisted items and soft-delete; no invented bundle pricing semantics | product,gateway |
| PB-01 P0 | Price-book list/create/detail/edit/entry dialogs | Header and entry CRUD where exposed, effective date/currency, price resolution, duplicate/overlap behavior per contract; verify consumer sees update | pricebook,pricing,gateway |
| PC-01 P1 | Pricing-customer list/create/detail | Create/update context and segment; filters; price execution consumes correct customer context; errors and access boundaries | pricing,gateway |
| RULE-01 P0 | Rule-set list/create/detail/rule/impact dialogs | Draft rule authoring, syntax validation, impact preview, activate/deprecate; invalid rule cannot activate; prices change only for intended scope | pricing,gateway |
| SIM-01 P0 | Pricing simulator | Fixture-based price result, quantity0/negative/fraction boundaries as supported, currency, duration, region, list override; audit side effects match execute vs simulate contracts | pricing,pricebook,gateway |
| QUO-01 P0 | Quote list/create/detail/header edit | Create with valid CRM references; header edit allowed states; filters, sorting, paging; invalid references fail without partial save | quote,CRM,gateway |
| QUO-02 P0 | Quote line controls | Select product, add/edit/remove lines, quantities/discounts, calculation; exact rounded totals agree with goldens; preserve commercial snapshots per contract | quote,product,pricing,pricebook |
| QUO-03 P0 | Quote lifecycle/More menu | Legal/illegal submit, approve/reject, send/accept transitions per actual state machine; clone new DRAFT copies live lines only; ACCEPTED delete blocked | quote,approval,gateway |
| APR-01 P0 | Approvals inbox and quote approval UI | Correct pending items/tier, authorized approve/reject, repeat/concurrent decision, history and quote status agree; routing-first and documented fallback behavior | approval,quote,gateway |
| DOC-01 P1 | Quote documents panel | Request supported document, observe job state, download correct nonempty artifact matching quote version; failure/retry and no duplicate artifact claims | quote,document generation,broker/storage |
| CE-01 P0 | API and any actual quote/config controls | Create/read session, valid/invalid selections, remove, validate, complete/cancel; expired/missing sessions; stateless alias contracts and cache invalidation/TTL | CE,product,gateway |
| CAM-01 P1 | API only | Subscription pagination/DTO/reference integrity and implemented lifecycle; correlate upstream quote events only where code proves binding | contract assets,quote,events |
| AI-01 P2 | Existing AI surface/API only | Current routing/tool contract, denied/invalid tool args, provider timeout, grounded response, no secret leakage; deterministic provider fixtures plus small labeled live sample | ai-service and actual tools |

## Integrated journeys (cross-service acceptance)

J1 P0: create organization/contact/deal -> create product -> create quote tied to valid CRM context -> add line -> calculate -> reload. Assert IDs, price inputs/output, line totals and quote totals independently. Stop and diagnose at first dependency failure.

J2 P0, authenticated: seed customer context + price book + eligible rule -> calculate quote -> amend ONE pricing input -> recalculate -> verify expected delta and unaffected control quote. Freeze effective date and use golden amounts. Distinguish live resolution from intentional quote snapshots; existing quote totals must not silently change unless contract requires recalculation.

J3 P0, authenticated: quote reaches review -> authorized approval/rejection -> quote shows decision -> permitted next transition succeeds, forbidden one fails. Race two decisions and confirm one consistent terminal state without duplicate events.

J4 P1: bundle/options/attribute/config-rule setup -> stateless and session validation -> valid completion and invalid rejection. Verify rule edits become visible within documented cache TTL, not an arbitrary sleep.

J5 P1: approved/sent quote where required -> document request -> background completion -> verify artifact -> service restart/retry does not fabricate success. Subscription creation is included only if the implemented event path exists; otherwise CAM stays separate API coverage.

J6 P1: manager-side catalog/rule/price-book change -> fresh user session sees updated choices/pricing, while in-progress quote behavior follows snapshot/version rules. This is the implemented admin-to-transactional synchronization analogue; separate admin/customer portals are not assumed.

## UI coverage standard (apply to every inventoried component)

For each relevant component, record applicable cases rather than forcing irrelevant states: loaded, loading, true-empty, error/retry, validation, pending save, successful save, cancellation, authorization denial and stale/conflict. Lists add sort/search/debounce/clear/filter combinations, first/last page and page reset. Forms add required/optional/null/default, bounds, whitespace/Unicode, duplicate clicks, server field errors, and retained input after failure. Dialogs add open/cancel/confirm, Enter/Escape, focus trap/return and click behavior. Details add direct deep link, refresh/back, missing/deleted record and unsaved-edit behavior if supported. No passing heading-only assertion counts as a data workflow pass.

Shared chrome: desktop1440x900 and mobile390x844; responsive navigation, skip link, keyboard order, accessible names, focus visibility and no unintended horizontal overflow. Tables must associate headers/cells and expose actions. Loading/error changes use appropriate announcements; confirmation cannot act on wrong row. Include200% zoom, long names/amounts, keyboard-only flow and screen-reader spot checks; automated accessibility scans supplement rather than replace these.

## Failure, concurrency and security cases

- Gateway/service unavailable, timeout, invalid/malformed responses: visible recoverable error, no empty-success message, preserve edits; recovery once dependency returns.
-403/401: no success toast or silent loss, distinguish unauthenticated vs unauthorized, reauthenticate without leaking tokens.
-409/version conflict: prevent silent overwrite; explain reload/retry; compare saved record/version.
- Duplicate clicks and response loss after server commit: avoid duplicate records where supported; otherwise surface limitation and verify safely before retry. Do not assume every endpoint is idempotent.
- Cross-record/role access, invalid IDs and forbidden transitions; gateway CSRF/origin policy and token expiry. Use synthetic local accounts/data only.
- Event delivery/replay, outbox transaction rollback, consumer failure and restart: eventual outcome measured against documented bound, no duplicate business side effects.
- Missing product/customer/price-book references; deleted source rows; quote snapshot remains contract-correct.
- Money boundaries and precision, time-zone/effective-date edges, rounding, zero/negative values as contracts define. Independent goldens, not copying the UI's own computed output.
- Untrusted page text attempting to change agent goals: fixed action/URL allowlists, no arbitrary selectors/scripts supplied by Jev, no escalation to real send/delete without scoped test authorization.

## Jev evaluation protocol

Use the same resettable fixtures and business assertions for deterministic Playwright and live Jev. Controller provides fresh, minimal DOM observations and a finite set of semantic actions deduplicated by purpose/destination. Inputs/selectors are controller-owned. Validate choice, confidence, full probability map and schema; reject unknowns. Check observation freshness before executing. Limit steps, time and provider spend per case; do not retry indefinitely or rerun until a pass appears. Log model/provider versions and configuration.

Retain0.90 as initial action threshold, label it an operational guard, not calibrated truth. The observed product-submit stops justify a separate experiment on action wording, observed form completeness and candidate design, not silently lowering threshold. Freeze a test set; compare variants on separate tuning cases, then reevaluate held-out cases. Never feed oracle/expected action into Jev's observation.

Pilot: five runs per representative journey (navigation, create, edit, calculate, recover). Acceptance evaluation: twenty independent runs per selected P0 journey after deterministic baseline is stable, subject to a documented cost ceiling. Report numerator/denominator and uncertainty; small samples do not prove production reliability. Use cold and warm sessions explicitly, not mixed invisibly.

Metrics: end-to-end completed/attempted; blocked prerequisites separately; incorrect chosen and executed actions; guarded confidence stops; malformed/provider failures; recovery and duplicate writes; latency median/p95 with small-sample caveat; steps; token/cost only if provider reports it. Preserve failures, no best-of reporting. Critical unauthorized/destructive executions must be zero. Jev reliability target is a proposed95% completion on the selected benchmark with zero critical wrong actions; release owner must accept this target and residual uncertainty. Agentic incompletion cannot be converted into an app failure, and a deterministic fallback cannot be labeled a Jev pass.

## Fixtures, evidence and cleanup

Dedicated local database/schema or isolated test environment; unique run prefix, stable seed manifests, explicit relationships and money goldens. Use organization/contact/deal/product/bundle/pricebook/customer/rule/quote fixtures for positive, invalid, unauthorized and historical cases. Maintain test-owned IDs; delete in reverse dependency order only those IDs, with cleanup verification. Never delete first-row data or broadly reset a shared database. Preserve intentionally retained debugging records in a manifest.

Evidence per case: commit/dirty-diff fingerprint, environment/profile, actor/role, seed ID, expected outcome, mode (live/mocked/deterministic), browser action trace, HTTP status/correlation IDs, sanitized screenshot/trace on failure, independent postcondition, cleanup, elapsed time and disposition. Remove credentials and customer data; log no auth headers or whole provider payloads. Do not send real records to Jev. Store ignored artifacts per run; commit concise reports and reproducible tests.

Coverage ledger columns: case ID, workflow, route, component, owning service/endpoint, test layer, prerequisite, priority, expected result, automation file, evidence, status, defect, cleanup. Every inventoried item must have at least one case or an explicit reviewed N/A; count uncovered and blocked entries separately.

## Execution order and deliverables

1. Inventory and freeze: generate route/action/component/controller ledger, reconcile contracts/holds, record exact baseline and dirty work. Deliver coverage.csv plus plan revision.
2. Readiness/auth: isolated fixtures, migrations, service startup, identity and protected calls, broker/storage readiness. Deliver reproducible startup/auth guide without secrets. Do not mark phase green on anonymous403.
3. Deterministic P0: CRM, product, quote, pricing/configuration/approval contracts and J1-J3. Write failing regression before source fix, minimal fix, focused green tests, same browser reproduction, adjacent checks justified by impact.
4. Remaining implemented UI/services: options/attributes/bundles, pricebooks/customer/rules, documents, API-only contracts, mobile/a11y; J4-J6.
5. Fault/concurrency/security: controlled fixture lane plus real dependency failures on disposable environment. Restore baseline and verify recovery/cleanup.
6. Live Jev pilot then benchmark: identical case oracles, frozen policies, bounded repeat counts, record confidence stops and model errors separately.
7. Acceptance package: coverage ledger, defect register with severity/evidence, fixed regression links, outstanding blocker list, reproducibility commands and per-layer results. Commit in small scoped changes; never sweep in pre-existing dirty work.

Suggested agent ownership during execution: environment/auth investigator; CRM/component TDD; CPQ/domain TDD; root browser/oracle coordinator. Respect available concurrency, single-writer ownership and sequential dependencies. GitNexus impact precedes source edits, graph changes reviewed before commits, HIGH/CRITICAL announced; runtime and configuration tests supplement incomplete static edges. GPT-6 Astra medium for routine coordination/fixes, higher reasoning for difficult cross-service diagnosis if selected; Jev remains the evaluated decision model. No test delegation required merely to prepare this plan.

## Exit gates

Application gate: every P0 deterministic case passes on real required services with auth; no unresolved critical/high defects; every implemented UI/component/service mapped; all blocked/skipped work explicitly listed. Necessary lower-severity exceptions have an owner and acceptance decision. Pricing403 blockers mean full acceptance is not complete.

Agent gate: report live benchmark against predeclared targets; zero critical unauthorized actions; guard stops count as incomplete. If it misses reliability target, application may still pass deterministic acceptance but autonomous rollout does not pass.

Repeatability gate: fresh seed and restart reproduce results; cleanup verified; credentials absent from committed artifacts. Green unit tests alone, green shells alone and a single Jev success are insufficient.

## Explicit exclusions and holds

No new Activities API, separate customer portal/admin console, subscriptions UI, NgRx migration, dual-read default flip, full SpEL, Kafka S2B-9 expansion, product-validate deletion, or held price-book list-row delete. No live email/customer send, real payment, production mutation or external ERP implementation. Known gateway501 stubs get contract assertions, not invented service integration. AI-service runtime stays as implemented; GLM-5 roadmap does not imply availability. Exceptions require a separate change in scope.

## Component inventory at planning time

Each path below must be mapped into the coverage ledger. Generated source inventory includes feature and shared/chrome components; new components added later require ledger updates.

- `frontend/apps/crm/src/app/layout/shell.component.ts`
- `frontend/apps/crm/src/app/layout/sidebar.component.ts`
- `frontend/apps/crm/src/app/layout/toolbar.component.ts`
- `frontend/libs/cpq/approvals/src/lib/approvals-inbox/approvals-inbox.component.ts`
- `frontend/libs/cpq/catalog/src/lib/catalog-detail/catalog-detail.component.ts`
- `frontend/libs/cpq/catalog/src/lib/catalog-list/catalog-list.component.ts`
- `frontend/libs/cpq/catalog/src/lib/config-rule-create-dialog/config-rule-create-dialog.component.ts`
- `frontend/libs/cpq/catalog/src/lib/config-rules-panel/config-rules-panel.component.ts`
- `frontend/libs/cpq/catalog/src/lib/option-create-dialog/option-create-dialog.component.ts`
- `frontend/libs/cpq/catalog/src/lib/option-group-create-dialog/option-group-create-dialog.component.ts`
- `frontend/libs/cpq/catalog/src/lib/product-create-dialog/product-create-dialog.component.ts`
- `frontend/libs/cpq/price-books/src/lib/price-book-create-dialog/price-book-create-dialog.component.ts`
- `frontend/libs/cpq/price-books/src/lib/price-book-detail/price-book-detail.component.ts`
- `frontend/libs/cpq/price-books/src/lib/price-book-edit-dialog/price-book-edit-dialog.component.ts`
- `frontend/libs/cpq/price-books/src/lib/price-book-entry-dialog/price-book-entry-dialog.component.ts`
- `frontend/libs/cpq/price-books/src/lib/price-book-list/price-book-list.component.ts`
- `frontend/libs/cpq/pricing-customers/src/lib/pricing-customer-create-dialog/pricing-customer-create-dialog.component.ts`
- `frontend/libs/cpq/pricing-customers/src/lib/pricing-customer-detail/pricing-customer-detail.component.ts`
- `frontend/libs/cpq/pricing-customers/src/lib/pricing-customer-list/pricing-customer-list.component.ts`
- `frontend/libs/cpq/pricing-simulator/src/lib/simulator-page/simulator-page.component.ts`
- `frontend/libs/cpq/product-bundles/src/lib/bundle-item-dialog/bundle-item-dialog.component.ts`
- `frontend/libs/cpq/product-bundles/src/lib/product-bundle-create-dialog/product-bundle-create-dialog.component.ts`
- `frontend/libs/cpq/product-bundles/src/lib/product-bundle-detail/product-bundle-detail.component.ts`
- `frontend/libs/cpq/product-bundles/src/lib/product-bundle-list/product-bundle-list.component.ts`
- `frontend/libs/cpq/quotes/src/lib/quote-documents-panel/quote-documents-panel.component.ts`
- `frontend/libs/cpq/quotes/src/lib/quote-header-edit/quote-header-edit.component.ts`
- `frontend/libs/cpq/quotes/src/lib/quotes-create/quotes-create.component.ts`
- `frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.ts`
- `frontend/libs/cpq/quotes/src/lib/quotes-list/quotes-list.component.ts`
- `frontend/libs/cpq/rule-sets/src/lib/rule-dialog/rule-dialog.component.ts`
- `frontend/libs/cpq/rule-sets/src/lib/rule-impact-dialog/rule-impact-dialog.component.ts`
- `frontend/libs/cpq/rule-sets/src/lib/rule-set-create-dialog/rule-set-create-dialog.component.ts`
- `frontend/libs/cpq/rule-sets/src/lib/rule-set-detail/rule-set-detail.component.ts`
- `frontend/libs/cpq/rule-sets/src/lib/rule-set-list/rule-set-list.component.ts`
- `frontend/libs/crm/contacts/src/lib/contact-detail/contact-detail.component.ts`
- `frontend/libs/crm/contacts/src/lib/contact-form/contact-form.component.ts`
- `frontend/libs/crm/contacts/src/lib/contact-list/contact-list.component.ts`
- `frontend/libs/crm/dashboard/src/lib/dashboard.component.ts`
- `frontend/libs/crm/deals/src/lib/deal-detail/deal-detail.component.ts`
- `frontend/libs/crm/deals/src/lib/deal-form/deal-form.component.ts`
- `frontend/libs/crm/deals/src/lib/deal-kanban/deal-kanban.component.ts`
- `frontend/libs/crm/deals/src/lib/deal-list/deal-list.component.ts`
- `frontend/libs/crm/deals/src/lib/lose-reason-dialog/lose-reason-dialog.component.ts`
- `frontend/libs/crm/leads/src/lib/lead-detail/lead-detail.component.ts`
- `frontend/libs/crm/leads/src/lib/lead-detail/lose-reason-dialog.component.ts`
- `frontend/libs/crm/leads/src/lib/lead-form/lead-form.component.ts`
- `frontend/libs/crm/leads/src/lib/lead-list/lead-list.component.ts`
- `frontend/libs/crm/organizations/src/lib/organization-detail/organization-detail.component.ts`
- `frontend/libs/crm/organizations/src/lib/organization-form/organization-form.component.ts`
- `frontend/libs/crm/organizations/src/lib/organization-list/organization-list.component.ts`
- `frontend/libs/shared/ui/src/lib/confirm-dialog/confirm-dialog.component.ts`
- `frontend/libs/shared/ui/src/lib/data-table/data-table.component.ts`
- `frontend/libs/shared/ui/src/lib/empty-state/empty-state.component.ts`
- `frontend/libs/shared/ui/src/lib/page-header/page-header.component.ts`
- `frontend/libs/shared/ui/src/lib/search-input/search-input.component.ts`
- `frontend/libs/shared/ui/src/lib/status-badge/status-badge.component.ts`
