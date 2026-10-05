# UAT-Level Functional Testing for Salesforce Revenue Cloud Advanced (RCA) — Dynamic Revenue Orchestrator (DRO)

**Prepared:** October 5, 2026
**Scope:** Best-practice test types for UAT-level functional validation of Dynamic Revenue Orchestrator within Revenue Cloud Advanced (now Agentforce Revenue Management), suitable for embedding in an external test tool.
**Sources:** Salesforce Help documentation (DRO design-time, time-aware orchestration, amendment/renewal/cancellation scenarios, release notes through Winter '27), plus published QA playbooks for Revenue Cloud / RCA testing.

---

## 1. What DRO actually does (so tests target the right seams)

Dynamic Revenue Orchestrator decomposes a commercial order into the technical products, services, and tasks needed to fulfill it, then runs an orchestration fulfillment plan: prioritization rules, SLAs, external callouts, automated tasks, manual tasks, and order fallout contingencies. It is the single post-order coordination layer — provisioning, billing triggers, ERP updates — with every step carrying a status and automatic jeopardy surfacing when an SLA is at risk.

Key capabilities that drive test design:

- **Decomposition** — commercial order line items break into fulfillment line items via decomposition rules, field mappings, and execution rules; expression sets can enrich fulfillment line attributes during decomposition.
- **Orchestration plans** — built in a visual fulfillment workspace; steps are automated, manual, or dependent; fulfillment scenarios auto-add step groups by product classification.
- **Time-awareness** — for multi-year / ramp deals, DRO groups instantiated steps by time period and sequences groups by source start/end dates, adding dependencies from the final steps of one period to the first steps of the next. Steps within a period run in parallel unless a dependency forces sequence.
- **Negative delays** — proactive steps can fire *before* a context date (e.g., reminders before an invoice due date).
- **Custom parameters on auto tasks** — step-specific config values passed into Flow, enabling reusable flow templates with conditional branching.
- **In-flight changes** — ongoing plans adapt to order amendments; each step definition has a point of no return, plus compensation and rollback steps for impacted steps.
- **Cross-plan dependencies** — steps in one fulfillment plan can depend on steps in another.
- **Custom scopes** — fulfillment line items and steps grouped by location, tenant, ramp, etc.
- **Non-sales transactions** — DRO now orchestrates any business process (dunning/collections, obligations, compliance) via the usage-type framework and custom context definitions, not only Product2-derived sales transactions.
- **Hybrid fulfillment** — DRO and Order Management can fulfill the same order in one coordinated process (physical goods vs. digital/service items).
- **Asset transfers** — amend + add orders across source/destination accounts are decomposed and orchestrated together.
- **Monitoring** — fulfillment orchestration dashboard, fulfillment reports, step history, SLA jeopardy.

## 2. The test types that matter most (ranked for UAT value)

### 2.1 End-to-end lifecycle / golden-path tests — highest value
Drive a complete business transaction from quote/order submission through decomposition, plan instantiation, step execution, assetization, and billing, asserting state at every seam. These are the UAT backbone because DRO defects almost always surface at a seam, not inside one step.

Minimum golden paths to encode:

1. **New order → fulfillment → assetization → billing.** Submit order, verify decomposition produces the expected fulfillment line items, plan instantiates with correct steps/dependencies/scopes, automated steps execute, manual tasks route correctly, assets are created, billing schedules generate.
2. **Amendment mid-flight.** Amend an in-flight order; verify the plan adapts — compensation/rollback steps fire for steps past their point of no return, unaffected steps continue, quantities/dates stay accurate.
3. **Renewal.** Verify renewal quote carries correct term, uplift, and co-term date; decomposition and fulfillment re-run cleanly against the renewed asset.
4. **Cancellation / credit.** Verify asset retirement, billing stop, and correct credit note amounts.
5. **Asset transfer between accounts.** Verify the amend order on the source and add order on the destination are both decomposed and orchestrated, staying in sync.
6. **Hybrid physical + digital order.** Order Management fulfills goods while DRO fulfills service/digital items; both sides report completion and the order closes only when all are done.
7. **Non-sales transaction (dunning).** Trigger DRO from a Collections Plan / dunning context; verify personalized workflows fire by customer segment.

### 2.2 Decomposition-correctness tests
- Commercial-to-technical product mapping: every order line decomposes to the expected fulfillment lines with correct quantities (including decimal quantities), attributes, and field mappings.
- Expression-set enrichment: fulfillment line attributes transformed correctly during decomposition.
- Custom scopes: lines/steps grouped by location, tenant, or ramp as configured.
- Decomposition rules fire only for matching products/classifications; non-matching lines are excluded.
- Decomposition Viewer output matches the configured rules (regression-friendly: snapshot the viewer output).

### 2.3 Time-aware / ramp-deal sequencing tests
- Multi-year order: steps for period N+1 do not start until period N's required steps complete; within a period, independent steps run in parallel and dependent steps sequence.
- Ramp deals: staged assetization per ramp segment; quantities and effective dates align to the ramp schedule.
- Negative-delay steps: a step scheduled with a negative delay fires before its context date (e.g., reminder 7 days before invoice due).
- Custom-date scheduling: a step's execution date resolves from a mapped context field (customer's requested activation date).
- Future-dated steps: steps scheduled for a future date/time execute at that time and not before.

### 2.4 Dependency and cross-plan tests
- Intra-plan dependencies: a dependent step stays blocked until its predecessor completes (success and failure paths).
- Cross-plan dependencies: a step in plan A waits on a step in plan B.
- Parallelism: steps with no dependency between them execute concurrently; no unintended serialization.
- Point-of-no-return: amending an order before the point of no return re-plans cleanly; after it, compensation/rollback steps execute for impacted steps.

### 2.5 SLA jeopardy and fallout tests
- Jeopardy condition met → step flagged on the fulfillment dashboard with correct severity; notification/escalation fires.
- Order fallout contingency: a failed automated step (e.g., external callout failure) triggers the configured fallout path rather than silently stalling.
- Paused callout steps during external-system downtime resume correctly when the system returns.
- Manual task assignment rules: tasks route to the right user/queue/least-workload assignee per rule criteria.

### 2.6 Priority and throughput tests
- High-priority transactions process within established hourly limits; default and bulk transactions are managed without starving high-priority work.
- Bulk submission: many orders submitted together decompose and plan without timeouts or lost steps.

### 2.7 Data-driven / snapshot regression tests
Encode each scenario as data (JSON/CSV fixtures) with an explicit expected value, so a run confirms not just that a plan was created but that quantities, dates, amounts, and step sequences match to the cent / to the second. Snapshot the Decomposition Viewer and fulfillment-plan output as golden masters; diff on every run. This is the pattern behind purpose-built RCA regression tools (e.g., Rev Cloud Blueprint's snapshot-based pricing validation) and it is what makes a UAT suite durable across catalog and rule changes.

### 2.8 API-level contract tests
DRO is API-first. Hit the submission, decomposition, and orchestration invocable actions and REST resources directly (Composite API for multi-step flows) and assert on the returned payloads and created records. This catches serialization and auth mismatches that UI tests miss, and it is the fastest way to regression-test the seams your tool wraps.

### 2.9 UI / Playwright tests (thin layer)
Drive the Configurator, Transaction Line Editor, fulfillment workspace, and fulfillment dashboard through the browser for the paths a human actually walks. Use UTAM page objects where available (Salesforce's UTAM recipes are compatible through Spring '26). Keep this layer thin — most DRO logic is better asserted at the API/record layer.

### 2.10 Monitoring and reporting tests
- Fulfillment orchestration dashboard reflects live step statuses.
- Fulfillment reports render with correct data; rule and condition data migrates cleanly between orgs (Data API / Bulk API).
- Step history captures every status transition.

## 3. Practical guidance for embedding in your tool

- **Assert at the record layer, not just the UI.** DRO's truth lives in FulfillmentPlan, FulfillmentStep, FulfillmentLineItem, FulfillmentAsset, and related objects. Query them after each action and compare against expected state.
- **Control time explicitly.** Future-dated steps, negative delays, and ramp sequencing are untestable without the ability to set or fast-forward the org's clock (or inject context dates). Build a time-control seam into your tool.
- **Separate decomposition from orchestration in tests.** DRO exposes invocable actions to run them independently — use that to unit-test each half before composing end-to-end.
- **Seed a known catalog.** A fixed product catalog, pricing procedure, decomposition ruleset, and fulfillment workspace make every test deterministic. Version that seed data alongside your tests.
- **One scenario per test, explicit expected values.** A scenario either produces the exact expected state or it does not — same discipline as pricing tests, applied to orchestration state.
- **Tag by lifecycle action** (new / amend / renew / cancel / transfer / dunning) and by DRO feature (time-aware, negative delay, cross-plan dependency, jeopardy, fallout) so you can run targeted UAT slices.

## 4. Suggested UAT suite skeleton

| # | Scenario | Primary assertions |
|---|----------|--------------------|
| 1 | New order golden path | Decomposition lines, plan steps, dependencies, scopes, assetization, billing schedule |
| 2 | Amendment before point of no return | Clean re-plan; no compensation steps |
| 3 | Amendment after point of no return | Compensation/rollback steps fire; unaffected steps continue |
| 4 | Renewal | Term, uplift, co-term date; re-decomposition |
| 5 | Cancellation | Asset retired; billing stopped; credit note amount |
| 6 | Asset transfer | Source amend + destination add both orchestrated |
| 7 | Hybrid OM + DRO order | Both sides complete; order closes only when all done |
| 8 | Ramp / multi-year time-aware | Period sequencing; staged assetization; parallel within period |
| 9 | Negative-delay step | Fires before context date |
| 10 | Custom-date step | Execution date resolves from context field |
| 11 | Cross-plan dependency | Step in plan A blocked on plan B |
| 12 | SLA jeopardy | Dashboard flag + escalation |
| 13 | Callout failure / fallout | Fallout path; pause-and-resume on downtime |
| 14 | Manual task routing | Correct assignee per rule |
| 15 | Priority mix | High-priority within limits; bulk not starved |
| 16 | Non-sales (dunning) | Collections Plan triggers correct workflow by segment |
| 17 | Data migration | Rules/conditions round-trip between orgs |
| 18 | Dashboard + reports | Live statuses; report data matches records |

## 5. References

- Salesforce Help: Dynamic Revenue Orchestrator overview (ind.dro_dynamic_revenue_orchestrator)
- Salesforce Help: Design Your Order Orchestration (ind.dro_design_time_orchestration)
- Salesforce Help: Orchestrate Orders with Time-Awareness (ind.dro_time_aware_plan_sequence)
- Salesforce Help: Dynamic Revenue Orchestrator for Business Processes (ind.dro_orchestrate_a_business_process)
- Salesforce Help: Amendment, Renewal, and Cancellation Scenarios (ind.qocal_amendment_renewal_cancellation_scenarios)
- Salesforce Help: Set Up Orchestrated Dunning for Collections
- Salesforce Help: Example — Automate Order Submission for Fulfillment
- Release notes: Dynamic Revenue Orchestrator (Spring '25 through Winter '27)
- SyntraFlow, "Salesforce Revenue Cloud Testing" — data-driven scenario patterns
- Stratus Carta, "Automation-First QA Strategy for Salesforce Communications & Revenue Cloud" — SIT/UAT layering
- Grazitti case study — Playwright + TypeScript hybrid framework extended for RCA compatibility
- Rev Cloud Blueprint (Forceweaver) — snapshot-based RCA regression extension
- Salesforce UTAM JS recipes (compatible through Spring '26)

---

*This document is research output for test-tool design. It is not affiliated with or endorsed by Salesforce.*
