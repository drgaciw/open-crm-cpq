# Renewals and amendments: development readiness (first slice)

*open-crm-cpq. Documentation only. Date: 2026-10-16 (CT). First slice: mid-term amendment of ACTIVE SaaS subscriptions in CAM (spine) plus quote-service LifecycleChange (Amend). Companion files: `amendment-first-slice-requirements-and-acceptance.md`, `amendment-technical-approach-and-dependencies.md`, `renewals-amendments-open-questions-register.md`.*

## 1. Verdict
**Conditionally ready.** Design depth is sufficient to start S1 and S2 (schema and audit chain) within one to two days, provided ADR-035 is accepted at a short decision meeting and the pricing-service capability (OQ9) is verified before S5 begins. The slice is about one working week of engineering for two to three engineers [I]. Not ready: renewals, on-prem, Swap/Upgrade/Downgrade, ordering, fulfilment (all remain design-stage by intent).

## 2. Gap analysis by dimension (what was missing before this pass, and what now exists)
| Dimension | State before | Gap for the first slice | Closed by |
|---|---|---|---|
| Requirements | Policy spec (scope, triggers, states, V1..V14, A1..A8), field schema | No numbered, story-sized requirements; slice scope not cut (action codes, signature policies); backfill and flag behaviour unspecified | FR-01..FR-20 and slice definition in the requirements file |
| Acceptance criteria | Rules testable in principle; no Given/When/Then per backlog item; existing gap items DRG-535, 536, 562..566 have problem statements only | Testable criteria per story and per rule; edge cases (partial apply, idempotency, concurrency, tamper) | AC-S1-1 to AC-S8-5 |
| Technical approach | Placement (ADR-035 Proposed), API resource names, migration sketch in spike 6.3 | Concrete migration order and next version (V1_7), apply-ledger conflict with `applied_quote_acceptances` unique key, flag routing, event compatibility, digest ownership, error codes | Technical approach sections 2 to 5 |
| Dependencies | Build order in adjacent assessment | Slice-level graph, what is not needed (ordering, fulfilment, e-sign, RA-42), minimal pinning acceptable | Sections 2 and 6 of technical file |
| Risks | Spike 6.4 list | No likelihood, mitigations tied to stories; pricing-service unknown; rollout risk | Section 7 of technical file |
| Test strategy | None | Defined by layer, reuse of existing fixtures | Section 8 |
| Open decisions | Eight spike decisions scattered over four files | Single register with status, defaults, owners, needed-by | Open-questions register |
| Backlog | Gap-level items only | Story-sized implementation items with ACs and dependencies | RA-45..52 / DRG-579..586 |

## 3. Readiness checklist
| Check | Status |
|---|---|
| Scope cut and written | Done |
| Placement decided | **Proposed only; needs acceptance (OQ1)** |
| Schema defined | Done (field schema plus migration order) |
| State machine and rules | Done (spec) |
| API names and error codes | Done at resource level; OpenAPI to be written first thing in S3 |
| Pricing integration verified | **Not verified (OQ9)** |
| Catalog pinning | Minimal pin sufficient; DRG-421, DRG-428 not blocking |
| Test strategy and fixtures | Defined; fixtures to be authored in S8 |
| Rollout and rollback | Flag per tenant; expand-only migrations |
| Retention N | Open; blocks go-live only |
| Backlog items with ACs | Created |

## 4. Conditions to start (all within 1 to 2 days)
1. Accept ADR-035 (or accept with named changes). Decision-maker: user or architect through the Jev-routed Choice. Day 0.
2. Confirm the register defaults OQ3, OQ10, OQ12 in the same meeting.
3. One-day read-only spike on pricing-service amendment modes and PolicyDecision trace ids (OQ9) before S5; S1, S2, S7 can proceed in parallel.
4. Confirm OQ5 (partial apply default) before S4 starts, around Day 2.
5. Legal answer on OQ8 before any production go-live; not before dev.

## 5. What would change the verdict
Rejection of ADR-035 in favour of quote-service ownership or a new service resets S1 to S3 (about two days of rework in documents, none in code). Pricing-service lacking modes adds a pricing story ahead of S5 and pushes quote-service work by several days but does not block the CAM stories.

## 6. Out of slice, tracked elsewhere
Renewal policy (DRG-549, 567, 540, 544, 545, 555), price lock (DRG-568), true-up (DRG-569, 570), maintenance (DRG-571), conversion (DRG-573, 574), catalog relationships (DRG-576), ordering reconcile (DRG-577), fulfilment (DRG-578), history backfill (DRG-566), e-signature (DRG-457).

## 7. Parking lot (not active, future scope)
On-premise self-reported usage (manual entry, rep or customer-ops attestation, link to the P2 capacity true-up, audit trail, uplift justification) is parked. See `renewals-amendments-parking-lot.md` (PL-1 to PL-5), RA-53 / DRG-587, and OQ21. These are not active stories and do not affect the readiness verdict.

## Addendum 2026-10-16 (call decisions audited against the backlog)
The first-slice verdict is unchanged: SaaS only, amend-in-place, add, update and remove lines (S1 to S8, DRG-579 to DRG-586). Decisions from the call that fall **outside** the first slice and now have backlog items: new-contract mode with stored precedence flag (DRG-592), renewal quote modes and usage context (DRG-593), legal-review-free change classes (DRG-594), visibility (DRG-595), renewal automation settings (DRG-596), separate queues (DRG-597), phased migration (DRG-598), amendment SKU mapping table (DRG-599), CRM re-forecast on amendment save (DRG-600). Product questions that touch the first slice: (1) whether new-contract mode (preferred) must be in slice 1; (2) whether SaaS Add and Remove lines need legal review under the pricing, term, seats rule (DRG-594); (3) pricing-service support for flat adjustment and prorated delta (OQ9, DRG-550).
