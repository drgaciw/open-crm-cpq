# Mid-term amendment policy: specification

*open-crm-cpq (drgaciw/open-crm-cpq), universal renewals-and-amendments project. Documentation only; no code changes. Date: 2026-10-16.*

**Conventions.** **[S]** = sourced from a project file (named in brackets) or the repo facts those files cite. **[I]** = inference or design proposal. Short file keys: *spike* = `amendment-document-design-spike.md`; *rca* = `rca-sda-amendment-placement.md`; *sanity* = `cpq-pricing-policy-sanity-check.md`; *research* = `cpq-renewals-amendments-research.md`; *transcript* = `cpq-project-transcript.md`.

**Architecture frame.** Spine plus policies. The amendment entity (AmendmentDocument with lines, revisions, application units, plus the thin Contract header) is the spine and the only data model. Mid-term amendment, renewal and price-lock extension are policies (workflows plus rule sets) that run on that spine [I, from caller brief]. Mid-term amendment is built first because it is the most general case: renewal is an amendment that also extends the term and applies a renewal pricing policy [S research 4.1; transcript 8]. The architectural guide is the Salesforce Revenue Cloud (RCA) pillar model; no official "SDA" exists [S rca 0].

## 1. Scope

**In scope**
1. Mid-term changes to an active contract's lines, before term end: Add, Update (quantity up or down), Remove/Terminate, Swap, Upgrade/Downgrade on SaaS subscriptions and on-prem maintenance or term lines [S research 4.3 action codes].
2. Multi-line, multi-subscription changes within one contract, grouped into atomic ApplicationUnits [S spike 4].
3. Co-termed proration of changed lines to the existing end date [S transcript 3.1].
4. Pricing via the shared pricing service using AmendmentPolicy modes RetainContracted, List, Override and proration Daily, MonthDay, None, NextCycle [S research 4.3; sanity 5].
5. Audit, sequencing and retention requirements for every path of this policy.

**Out of scope (owned by later policies or other items)**
1. Term extension and renewal pricing (RenewalPolicy; RA-15, RA-33/DRG-567).
2. Time-bounded price locks and multi-year escalators (price-lock policy; RA-34/DRG-568).
3. Capacity true-up computation (TrueUpPolicy; RA-35, RA-36) and on-prem to SaaS conversion (ConversionPolicy, Surrender; RA-39, RA-40). A mid-term amendment may carry a manually priced capacity Add line, but not meter-derived true-up [I].
4. Maintenance as percent of licence net (RA-37/DRG-571).
5. Multi-contract changes; use a merge intent (DRG-554) [S spike 6.5, proposed].
6. Clause authoring, redlining and full CLM [S rca 4].
7. Fulfilment and licence regeneration (RA-24/DRG-558).

## 2. Triggers

| # | Trigger | Basis |
|---|---|---|
| T1 | Manual by a seller from a contract or subscription | [S research 4.6] |
| T2 | Headless API command `initiateAmendment`-style, keyed by subscription ids with optional contract id, async with a request identifier | [S rca 3] |
| T3 | Customer self-service change request (click-through) | [I] |
| T4 | Correcting amendment raised from APPLY_BLOCKED, or reversing amendment after apply | [S spike 3, 4] |
| T5 | Renewal or price-lock policies invoking the amendment workflow as a sub-step | [I] |

Scheduled triggers (lead-time job, term-end auto) belong to the renewal policy, not to this one [S research 4.6; I on allocation].

## 3. Lifecycle states and transitions

The policy uses the spine's document states; it does not add its own [I]. States are the spike's proposal [S spike 3], mapped to LifecycleChange states in quote-service [S transcript 4.4].

| From | To | Guard (policy-level) |
|---|---|---|
| (none) | DRAFT | LifecycleChange created with intent Amend; amendment number reserved |
| DRAFT | IN_REVIEW | Priced by pricing service; content digest computed; validation rules V1 to V12 pass |
| IN_REVIEW | APPROVED | Approval granted in approval-routing (`CONTRACT_MODIFICATION`) or auto-approved under threshold |
| IN_REVIEW, OUT_FOR_SIGNATURE | DRAFT (new revision) | Edit or customer redline; prior revision frozen as SUPERSEDED |
| IN_REVIEW | REJECTED | Approver rejects (terminal) |
| APPROVED | OUT_FOR_SIGNATURE | Rendition generated; envelope sent via DRG-457 adapter |
| APPROVED | EXECUTED | Signature policy is NOTICE_ONLY, NOT_REQUIRED, or CLICK_THROUGH accepted |
| OUT_FOR_SIGNATURE | EXECUTED | All signatures complete; signed-copy hash stored |
| OUT_FOR_SIGNATURE | EXPIRED | Offer validity passed (terminal) |
| DRAFT, IN_REVIEW, APPROVED, OUT_FOR_SIGNATURE | WITHDRAWN | Seller withdraws (terminal) |
| EXECUTED or approved apply point | APPLIED, PARTIALLY_APPLIED, APPLY_BLOCKED | Ledger owner applies units |
| EXECUTED (unapplied) | VOID | Authorised legal void (terminal) |
| APPLIED | SUPERSEDED | Later amended-and-restated document executes |

**Apply point is an open decision (OQ2).** The spike recommends gating apply on EXECUTED [S spike, decision 2]; the RCA analysis supports applying on acceptance or order with EXECUTED as a parallel legal status [S rca 2]. This spec keeps the apply point configurable per signature policy and does not decide it.

## 4. Validation rules

Each rule is testable as a pass or fail on a given input. Failure blocks the named transition and emits an audited rejection event.

- **V1** The LifecycleChange intent is Amend and every target subscription belongs to the document's single contract. [S spike 6.5 proposed; I]
- **V2** Each target subscription is ACTIVE at the action date. [S spike 0, policy requires ACTIVE target]
- **V3** For each target, `observedRevision` equals current `commercial_revision`; checked at pricing and again per unit at apply. [S spike 0, 4 rule 7]
- **V4** Every Update, Remove, Swap, Upgrade or Downgrade line names an explicit, unique `source_accepted_line_uuid` that exists, is not retired, and has `ACCEPTED_PRICED` provenance. [S spike 0]
- **V5** Effective date is on or after contract start and on or before contract end; amended lines co-term to the existing end date unless a co-term policy says otherwise. [S transcript 3.1; I on bound]
- **V6** Action date and effective date are both present and stored separately; effective date before action date (backdating) requires an explicit backdating reason code and approval. [S rca 3; I on backdating rule]
- **V7** Quantity semantics are declared per line (Delta, Absolute, Replace) and `start_qty + delta_qty = end_qty` holds; `end_qty >= 0`. [S research 4.3; sanity 4]
- **V8** Every priced line carries a PolicyDecision id, policy id and version, and the pricing-service trace id; no line is priced inside quote-service or CAM. [S spike 5; rca 3]
- **V9** Product external id, pricebook version and configuration hash are pinned at pricing and are unchanged at apply. [S spike 6.2]
- **V10** Content digest at apply equals the digest of the executed (or approved) revision; mismatch blocks apply. [S spike 3]
- **V11** At most one revision per amendment number reaches EXECUTED. [S spike, decision 6 proposed]
- **V12** Units apply in dependency order (create before terminate); a unit writes all its rows or none. [S spike 4]
- **V13** Applying the same (document id, unit no, content digest) twice is a no-op; same key with a different digest is rejected. [S spike 3]
- **V14** Manual price overrides record actor, reason code and the overridden value, and route to approval. [I]

## 5. Audit requirements

Financial-industry grade; audit is mandatory on every path, including rejected, withdrawn, expired, void and blocked outcomes [S transcript 8; I on grade-specific controls].

- **A1 Immutable records.** Executed revisions, lines, PolicyDecisions, signed copies and applied rate periods are never updated or deleted; corrections are new events or new amendments. Soft delete is forbidden after EXECUTED. [S spike 5]
- **A2 Tamper-evident sequencing.** `amendment_document_events` is append-only with a per-document monotonic sequence number and a hash chain (`prev_hash`, `event_hash`). A gap or broken link is detectable by a verification job. [S spike 5 for hash chain; I for monotonic sequence and verifier]
- **A3 Gap-free numbering.** Executed amendments per contract have no gaps in `amendment_seq`. [S spike, decision 4 proposed]
- **A4 Source traceability.** Every applied rate period references its document line, LifecycleChange line and revision; every document line references its source accepted line. [S rca 3; spike 5]
- **A5 Decision trail.** Each line records which policy fired, why, inputs snapshot digest, rules fired, outputs and who decided (system or override). [S spike 5; RA-30/DRG-564]
- **A6 Actor and time.** Every transition records actor, role, timestamp (UTC), from and to state and payload digest. [S spike 5]
- **A7 Export.** Events publish through the outbox to the audit trail service (DRG-309) and are queryable by evidence chain (DRG-312) with `amendmentId`. [S spike 5]
- **A8 Replay.** Frozen inputs allow a priced result to be recomputed and compared (DRG-311). [S spike 5]

## 6. Retention

- **R1** Retention of executed amendments, signed copies, decisions and event logs must outlive the contract; proposed contract end plus N years. [S spike 5; caller brief]
- **R2** Legal hold suspends any disposal. [S spike 5; DRG-313]
- **R3** Records for terminal non-executed outcomes (rejected, withdrawn, expired) are retained for the same window as audit evidence. [I]
- **R4** The window N, hold rules and disposal method are an open decision requiring Legal (OQ8). If retention must diverge from CAM's, that is a trigger to extract the document module to a separate service. [S rca 4]

## 7. Composition with the spine

| Concern | Spine owns (AmendmentDocument module in CAM, plus ledger) | Mid-term amendment policy owns |
|---|---|---|
| Data model | Document, lines, revisions, units, Contract header, events | Nothing persistent of its own beyond a policy config record [I] |
| States | State machine and transition mechanics | Guards and which signature policy applies |
| Applied state | Single writer: CAM ledger only [S rca 3] | Never writes rate periods |
| Pricing | Stores pinned results by digest | Selects AmendmentPolicy modes; pricing service evaluates [S sanity 4] |
| Intent | LifecycleChange stays in quote-service [S rca 4] | Amend intent defaults and allowed action codes |
| Audit, numbering, retention | Hash chain, sequence, retention hooks | Supplies reason codes and decision payloads |

**Extension points for later policies [I]**
1. **Intent hook:** a policy registers an intent (Renew) and allowed action codes on top of Amend.
2. **Term hook:** renewal policy may extend `effective_to` and the contract end date; the amendment policy forbids it.
3. **Pricing hook:** typed policy slot per line (Amendment, Renewal, PriceLock, TrueUp, Maintenance, Conversion), resolved account > agreement > line with contract-clause precedence and snapshotted [S sanity 5].
4. **Segment hook:** an ordered segment schedule `{from, to, mode, value}` on a line, used by price-lock and escalator policies [S sanity 5, G2].
5. **Trigger hook:** scheduled triggers attach to the same command API.
6. **Validation hook:** policies add rules; they cannot remove spine rules V3, V8, V10, V12, V13.

## 8. Open questions

The eight spike decisions remain open and are flagged, not decided [S spike; transcript 6]:
- **OQ1** Placement (current recommendation: separate module in CAM; RA-27).
- **OQ2** Apply on EXECUTED vs on acceptance or order (RA-28).
- **OQ3** Thin Contract header now vs after DRG-458.
- **OQ4** Numbering `{contract_number}-A{nnn}`, and whether it resets per renewal term.
- **OQ5** Partial application with PARTIALLY_APPLIED (RA-31).
- **OQ6** Revision model.
- **OQ7** Backfill as legacy evidence (RA-32).
- **OQ8** Retention window and legal hold (Legal; DRG-313).

Policy-specific [I]:
- **OQ9** Backdating limits and approval thresholds (V6).
- **OQ10** Whether a mid-term downsell is permitted before a minimum commitment ends, and its credit or fee policy (RA-16).
- **OQ11** Who may VOID an executed amendment [S spike 6.5].
- **OQ12** Which pricing gaps block the first release: G1 (price-as-of, precedence) and G6 (one-time lines) look necessary for amendments; G2, G3, G4, G5, G7, G8 and G10 can wait for their policies [I; gaps from sanity].


## Addendum 2026-10-16 (dev-readiness pass; nothing above changed)
- **OQ2 and OQ4 are resolved** (apply only after EXECUTED; provisional number at DRAFT, final gapless number at EXECUTED). Section 3 note on OQ2 is superseded by those decisions.
- **First-slice cut:** actions Add, Update, Remove only; signature policies NOT_REQUIRED, NOTICE_ONLY, CLICK_THROUGH only; SaaS subscriptions only. Swap, Upgrade, Downgrade wait for RA-42 / DRG-576. See `amendment-first-slice-requirements-and-acceptance.md`.
- Rules V1..V14 are mapped to stories and test names (rule id in the test name) in that file and in the technical approach file.

## Clarification 2026-10-16 (call decisions, audit of backlog; amendment modes, legal review, pricing modes, coterm)
Source: call decisions recorded in `cpq-project-transcript.md` addendum "Renewals and amendments decisions audit". Backlog: DRG-592 (RA-58), DRG-594 (RA-60), DRG-550 (RA-16), DRG-554 (RA-20).
- **Amendment modes (conflict).** This spec models amend-in-place on one contract. The call decided two modes: **new contract (preferred)** and **amend in place**. In new-contract mode both contracts stay active, the new contract carries a stored precedence flag and the original stays open as a reference. First slice (SaaS only: add, update, remove lines) stays amend-in-place; new-contract mode is tracked in DRG-592 and needs a product call on timing. V-rules for new-contract mode are not yet defined.
- **Legal review gate (gap).** No rule here says which amendments skip legal review. Decision: only changes to **pricing, term or seats** are legal-review-free; everything else needs legal review. Adding or removing a product line is outside that set unless product treats it as seats (open). Tracked in DRG-594.
- **Pricing modes.** Amendment pricing supports **flat adjustment** and **prorated delta**; the existing stub-proration rule is the prorated delta mode. Pricing-service support still unverified (OQ9). Tracked on DRG-550.
- **Coterm (V5).** V5 still lets amended lines co-term to the existing end date. Customer-level coterm events (blended or rates-carry-forward with carry-forward default, shared end date as new anchor, all-or-nothing per customer, optional renewal group) are a separate mechanism tracked on DRG-554; approver deferred (DRG-591).
- **Audit and visibility.** Audit of every amendment (A1 to A8) is confirmed. Visibility is contract owner plus read-only sales ops and finance (DRG-595); not yet in this spec.

**C1 resolved 2026-10-16 (owner), amendment modes.** Both modes are supported. This spec's amend-in-place model is the one built first, in the initial slice. New-contract-preferred (RA-58 / DRG-592) follows as the next increment, and the two coexist once both are in. The conflict note in the 2026-10-16 clarification above is therefore resolved as sequencing, not as a replacement. See OQ26 in `renewals-amendments-open-questions-register.md`.
