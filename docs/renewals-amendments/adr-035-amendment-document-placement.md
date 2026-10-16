# ADR-035: Placement and ownership of the amendment document entity

**Status:** Accepted on 2026-10-16. Scope: authorizes the first-slice schema and audit stories only (S1, S2).
**Date:** 2026-10-16
**Deciders:** Project owner (drgaciw), approved 2026-10-16.
**Backlog:** RA-27 / [DRG-561](https://linear.app/drgamtd-workspace/issue/DRG-561/adr-placement-and-ownership-of-the-amendment-document-entity). Notion Open-CRM-CPQ Backlog RA-27.
**Relates to:** RA-28..RA-32 / DRG-562..DRG-566; DRG-458 (thin Contract header); DRG-313 (retention); RA-43 / DRG-577 (reconcile ADR-024..027 with the spine); ADR-008, ADR-010, ADR-018, ADR-024..027.
**Does not authorize:** any code, schema migration, service, topic or gateway route. Each needs its own PR after this ADR is accepted. Design and documentation only until then.

**Inputs (workspace, 2026-10-16):** `rca-sda-amendment-placement.md`, `amendment-document-design-spike.md`, `mid-term-amendment-policy-spec.md`, `amendment-entity-field-schema.md`, `adjacent-services-maturity-assessment.md`, `cpq-project-transcript.md`, call decisions of 2026-10-16.

---

## Context

open-crm-cpq is building universal renewals and amendments on a **spine-plus-policies** model: one amendment document entity is the spine; mid-term amendment, renewal and price-lock extension are policies on it. Mid-term amendment is first.

Today:

- Continuations live in **quote-service** (`QuoteContinuationRequest` / `QuoteContinuationPolicy`). Apply happens in **contract-asset-management (CAM)** on `QuoteAccepted` (`AcceptedQuoteSubscriptionService`).
- CAM has subscriptions and rate periods but **no Contract header** and **no AmendmentDocument**.
- **document-generation** has QUOTE_PDF, CONTRACT_PDF, ORDER_FORM, INVOICE; no AMENDMENT type.
- ADR-024..027 (order-service) are **Proposed**, not accepted, and predate the spine ([RA-43 / DRG-577](https://linear.app/drgamtd-workspace/issue/DRG-577)).

Salesforce Revenue Cloud (RCA) is the architectural guide. RCA has no standalone Amendment entity: amend is a QuoteAction/OrderAction; the committed result is AssetAction plus AssetStatePeriod (Transaction Management / ALM); the legal document is ContractDocumentVersion in the separate Salesforce Contracts pillar. Full mapping: `rca-sda-amendment-placement.md`.

Open decisions already resolved for the schema (2026-10-16): apply only after EXECUTED (single `status`); provisional then final gapless numbering `{contract_number}-A{nnn}` with no reset on renewal. Placement itself is still open and blocks CAM spine work (DRG-562, DRG-563, DRG-564) and the build-order seams step.

## Decision

### D-01 — AmendmentDocument lives in CAM as a distinct contract-documents module

**AmendmentDocument**, its revisions, lines, application units and append-only hash-chained event log live in **contract-asset-management**, in a separate **contract-documents** module (own aggregate, schema tables and API path `/contracts/{id}/amendments`).

The module talks to the CAM ledger only through **internal commands and events**. CAM remains the **single writer of applied state**. The module boundary is enforced so the documents context can later be extracted to a separate service (D-04).

### D-02 — Thin Contract header lives with the documents module in CAM

A thin **Contract** header is created in CAM now (keyed so DRG-458 can enrich it). Contracts link to subscriptions; they do not own the asset ledger. Amendment numbering and retention attach to the contract.

### D-03 — LifecycleChange, pricing evaluation and render requests stay in quote-service

- **LifecycleChange** (intent root: Renew, Amend, Cancel, Restructure, Transfer, Suspend, Resume, TrueUp, Convert) stays in quote-service.
- Pricing policy evaluation stays a call into **pricing-service** (trace / PolicyDecision); the document only pins results.
- Document **render** is requested from quote-service (per 2026-10-16 architecture review). Reconcile the spike's document-generation `AMENDMENT` type in implementation tickets; this ADR does not move render ownership into CAM.

### D-04 — Extract to a separate contract-document service only on named triggers

Stay as a CAM module until one of:

1. Independent retention / legal-hold rules require a separate store (DRG-313),
2. Full CLM scope (clauses, redlining, non-sales contract types), or
3. Multi-contract or tenant-specific document isolation.

Extraction path: module already has its own aggregate and API; promote to a service without rewriting the ledger seam.

### D-05 — One AmendmentDocument per LifecycleChange; policies add no tables

All nine intents share the same document spine. Mid-term, renewal and price-lock policies bind via extension points only (`mid-term-amendment-policy-spec.md`, stubs). No policy-specific tables.

### D-06 — Action date stays separate from effective date

Document signed/executed time is not the change effective date. Every applied period keeps source traceability to the LifecycleChange line and document revision (RCA AssetActionSource analogue).

## Options considered

| Option | Verdict |
|---|---|
| **A. CAM contract-documents module + thin Contract header (chosen)** | Matches RCA's ledger-next-to-asset split; keeps a single writer and strong idempotency; avoids a premature network boundary. Cost: CAM grows; module discipline must be enforced. |
| B. quote-service owns AmendmentDocument | Weakest RCA fit: quote is intent only. Mixes mutable quote state with executed legal evidence. Rejected. |
| C. New contract-document service now | Most faithful to the Contracts pillar. Adds saga risk between EXECUTED and applied, and a new deployable, before the spine exists. Deferred to D-04 triggers. |
| D. Wait for ADR-024 order-service and hang documents there | ADR-024 is Proposed and conflicts with apply-after-EXECUTED and gapless numbering until RA-43 reconciles it. Documents are not order orchestration. Rejected for v1. |

## Consequences

- **Positive:** Unblocks CAM spine tickets (DRG-562..564) and mid-term policy without waiting on order-service. Clear home for audit (embedded hash chain, same transaction as apply). Aligns with adjacent-services build order: seams (this ADR, RA-43) before spine before policies.
- **Negative:** CAM becomes the largest service in the renewals path until an extract. Reviewers must reject ledger writes from outside CAM and document writes from outside the module.
- **Downstream:** RA-28..32, schema fields marked dep OQ1, and ADR-024 amend (RA-43) treat this placement as given once accepted. document-generation gains an AMENDMENT (or equivalent) type via quote-service render, not by owning the entity.

## Fitness functions (on acceptance)

- No type named `AmendmentDocument*` (or contract-documents package) is declared outside CAM and shared event DTOs.
- Ledger apply paths reject payloads that are not tied to an EXECUTED document revision (after strangler cutover from QuoteAccepted-only apply).
- Event contract registry lists document lifecycle events with owner `contract-asset-management` (documents module).

## Rollout (docs then code; no code until accepted)

1. Accept this ADR (or record a competing choice with rationale).
2. Land schema and module skeleton behind flags; no production apply-path change.
3. Mid-term AmendmentPolicy on the spine; then renewal and price-lock policies.
4. Reconcile ADR-024..027 (DRG-577) so order commands consume executed amendment units.
5. Extract per D-04 only if a trigger fires.

## Open questions (out of scope for this ADR)

- OQ3 Contract header timing detail vs DRG-458 enrichment fields (thin header is decided; field set is not).
- OQ5 Partial application / PARTIALLY_APPLIED (spike recommended yes).
- OQ6 Revision model edge cases when LifecycleChange is superseded mid-flight.
- OQ7 Backfill strategy for pre-spine acceptances.
- OQ8 Retention N years and legal hold (Legal / DRG-313).
- Render home: quote-service request vs document-generation type ownership (implementation ticket).
- Whether price-lock extension registers its own intent (schema open question; stub says no).

## Evidence

- RCA placement research: `/workspace/rca-sda-amendment-placement.md` (Trailhead Transaction Management; Revenue Management Dev Guide initiateAmendment, QuoteAction, AssetAction, AssetStatePeriod, AssetActionSource; Salesforce Contracts ContractDocumentVersion).
- Spike recommendation Option A and alternatives: `/workspace/amendment-document-design-spike.md` §6.1.
- Call architecture (2026-10-16): documents + thin Contract in CAM; LifecycleChange and pricing in quote-service; audit embedded in CAM.
- Field schema binding: `/workspace/amendment-entity-field-schema.md`.
- Maturity / build order: `/workspace/adjacent-services-maturity-assessment.md` (seams DRG-561, RA-43 first).
- Repo facts cited in those files from `drgaciw/open-crm-cpq` `main` @ `dd90501` (GitHub connector reads).


## Addendum 2026-10-16 (dev-readiness pass; written while status was Proposed)
This ADR is the single hard blocker to starting the first slice (OQ1 in `renewals-amendments-open-questions-register.md`). Implementation stories that depend on acceptance: RA-45..RA-52 / DRG-579..DRG-586. Practical notes from the readiness review: next CAM migration version is V1_7 (verified); document-driven applies use a new apply ledger rather than altering `applied_quote_acceptances` (see `amendment-technical-approach-and-dependencies.md` section 3). Acceptance may be conditional on OQ3, OQ10 and OQ12 defaults. No text above was changed.

## Acceptance 2026-10-16
The project owner (drgaciw) accepted this ADR on 2026-10-16 (Fri). Scope: this acceptance authorizes the first-slice schema and audit stories only (S1, S2; RA-45 / RA-46, DRG-579 / DRG-580). It does not authorize later stories, which still need their own gates. The Decision text above is unchanged. Recorded by the docs PR #600; merging that PR records the acceptance on main.

Confirmed defaults:
- **OQ3, header scope:** thin Contract header now with id, number, tenant, customer reference, start, end, status, last_executed_seq and prior_contract_id. Richer fields are deferred to DRG-458 (contract enrichment).
- **OQ10, signature policy:** NOT_REQUIRED, NOTICE_ONLY and CLICK_THROUGH ship now. E_SIGN and WET come later through the DRG-457 e-sign adapter.
- **OQ12, action codes:** Add, Update and Remove only. Swap, Upgrade and Downgrade are deferred to RA-42 / DRG-576 (product relationships in the catalog).

Still open and not part of this acceptance: OQ9 (pricing-service amendment modes, gates S5 only), OQ8 (retention, Legal), hash chain key management.
