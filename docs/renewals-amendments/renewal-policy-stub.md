# Renewal policy: stub

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only; no code changes. 2026-10-16. Stub.*

**Conventions.** **[S]** = sourced from a project file; **[I]** = inference or design proposal. File keys: *spec* = `mid-term-amendment-policy-spec.md`; *spike* = `amendment-document-design-spike.md`; *rca* = `rca-sda-amendment-placement.md`; *sanity* = `cpq-pricing-policy-sanity-check.md`; *transcript* = `cpq-project-transcript.md`. *schema* = `amendment-entity-field-schema.md`, whose field names are used here.

## 1. Purpose and intent type

Renewal is a policy on the amendment spine, not a separate data model: an amendment that also extends the term and applies a renewal pricing policy [S transcript 4.1, 8]. It reuses the LifecycleChange root, AmendmentDocument, lines, revisions, ApplicationUnits and thin Contract header [S spec 7].

- **Intent:** `Renew` on LifecycleChange [S transcript 4.3]. Document kind `RENEWAL` [S spike 1].
- **Allowed action codes:** `Renew`, plus `Add`, `Update` and `Remove` on the same change so a renewal can carry upsell, downsell and cross-sell lines; a not-renewed line uses a non-renewal disposition, not `Remove` [S sanity scenario 4, G10].
- **Out of scope:** price locks and escalators (price-lock policy), true-up, conversion, maintenance percent [S spec 1].
- **Ownership:** LifecycleChange, pricing orchestration and document render stay in quote-service; the AmendmentDocument and Contract header live in CAM; CAM alone writes applied state [S transcript architecture review; rca 3]. The spike instead places rendering in document-generation [S spike 6.1]; reconcile in the RA-27 ADR [I].

## 2. Binding to the six extension points

| # | Extension point [S spec 7] | Renewal binding | Rules inherited or tightened |
|---|---|---|---|
| 1 | Intent hook | **Adds** intent `Renew`, document kind `RENEWAL`, allowed codes Renew/Add/Update/Remove and a non-renewal disposition [I on code set] | Tightens **V1**: intent is Renew and every target belongs to the one contract being renewed. Inherits A1, A6 |
| 2 | Term hook | **Overrides** the amendment default (which forbids term change): extends line `effective_to` and the Contract header end date by the renewal term [S spec 7] | Replaces **V5** with RN1 and RN2 below. Inherits **V6** (action and effective dates separate) [S rca 3] |
| 3 | Pricing hook | **Overrides** the slot with RenewalPolicy: modes Same, List, UpliftPct, UpliftAmt, Override, Renegotiate; basis, cap, floor, price-as-of, rounding; precedence contract clause > line > agreement > account > rules default; snapshotted per line [S sanity 5, G1, G9] | Inherits **V8**, **V9**, **V14**, **A5**, **A8**. Tightens V8 with RN3 |
| 4 | Segment hook | **Leaves default**: one flat period per renewed line, as today [S transcript 3.1]. Escalators and locks are written by the price-lock policy [I] | Inherits V12 |
| 5 | Trigger hook | **Adds** lead-time, notice-window and term-end triggers on the same command API [S spec 2, 7] | Adds RN4 (one open renewal per contract term). Inherits A6, A7 |
| 6 | Validation hook | **Adds** RN1 to RN5. Cannot remove spine rules V3, V8, V10, V12, V13 [S spec 7] | Inherits V2, V3, V4, V7, V10 to V13, A1 to A8 |

**Policy rules [I]** (local numbering, not backlog IDs):
- **RN1** Renewed `effective_from` is the day after current term end [S sanity scenario 1], unless a co-term anchor applies (RA-20 / DRG-554).
- **RN2** New `contracts.end_date` equals the latest renewed `effective_to`.
- **RN3** Each renewed line's PolicyDecision records uplift basis, cap applied, price-as-of date, rounding and the precedence level used [S sanity scenario 1].
- **RN4** At most one non-terminal renewal LifecycleChange per contract and term; a repeated trigger is a no-op (guards against RA-11 / DRG-545 re-publishing).
- **RN5** A line with `isPermanent` (perpetual) is never renewed; its linked maintenance line is [S transcript 4.7].

## 3. Triggers

| Trigger | Basis |
|---|---|
| Lead-time job (`initiation_trigger = LeadTime`): opens a DRAFT renewal LifecycleChange | [S transcript 4.6; RA-21 / DRG-555; RA-10 / DRG-544] |
| Notice window: evaluates `notice_period_days` and `term_end_action` before cut-off; records non-renew notices | [S spike 1 legal terms; research cutOffDays via transcript 4.3] |
| Term-end auto action (Renew, RenewOnce, Evergreen, Cancel) if no seller action | [S transcript 4.3 RenewalPolicy] |
| Manual by seller or headless API (`initiateRenewal`-style) | [S rca 1; spec 2] |

Today `RenewalScheduler` publishes an unconsumed event (RA-06 / DRG-540) nightly for the same subscription (RA-11 / DRG-545), and the date-only renew endpoint skips pricing (RA-09 / DRG-543) [S transcript 3.2]. This policy is the intended consumer [I].

## 4. Required inputs

| From quote-service (pricing side) | From CAM (record side) |
|---|---|
| Resolved RenewalPolicy snapshot and per-line PolicyDecision, evaluated by pricing-service with a trace [S sanity 4] | Asset ledger: exit rate per line from `subscription_rate_periods` with `accepted_line_uuid` and provenance [S spike 0] |
| Term extension: renewal term months, co-term anchor if any [S spike 0; sanity scenario 4] | `commercial_revision` per target for `observedRevision` (V3) [S spike 0] |
| Uplift basis {ContractedNet, CurrentList, ListLessContractedDiscount}, cap, floor, rounding [S sanity G1] | Contract header: `contract_number`, `term_model`, `status`, `prior_contract_id` [S schema 2.2]. Notice period and `term_end_action` are not on the header [I gap] |
| Price-as-of date {ActionDate, EffectiveDate, LineStart} and pinned pricebook version [S sanity G1; spike 6.2] | Term dates: `contracts.start_date`, `end_date`; line `effective_from`, `effective_to` [S schema 2.2, 2.5] |
| Action date and effective dates [S transcript 4.3] | Subscription status (ACTIVE, or InGrace if allowed) [S transcript 4.4] |

## 5. Outputs and audit events

- AmendmentDocument of kind `RENEWAL`, numbered per OQ4, with lines, PolicyDecisions and ApplicationUnits [S spike 1].
- On apply, CAM writes future-dated rate periods and the extended header end date in one local transaction [S spike 4 rule 4; rca 3].
- Spine events reused: `AmendmentDrafted`, `AmendmentSubmitted`, `AmendmentApproved`, `AmendmentSentForSignature`, `AmendmentExecuted`, `AmendmentUnitApplied`, `AmendmentApplied`, `AmendmentApplyBlocked`, `AmendmentWithdrawn`, `AmendmentSuperseded`, `AmendmentVoided` [S spike 6.6].
- Policy payloads [I]: trigger record (job, lead time, run date), notice-window result, per-line non-renewal disposition, ARR bridge (RA-41 / DRG-575).
- Audit on every path, including silent term end and non-renewal: append-only, hash-chained, in CAM, same transaction [S transcript decisions 4, 5].

## 6. Open questions and dependencies

Spike decisions [S spike; spec 8]. OQ2 and OQ4 were resolved on 2026-10-16 [S transcript, resolved decisions]; the rest stay open:
- **OQ1 Placement (RA-27 / DRG-561).** Decides where scheduled triggers live.
- **OQ2 Apply point, resolved:** apply only after EXECUTED, single `status`. Auto-renew therefore needs NOTICE_ONLY or NOT_REQUIRED to reach EXECUTED [I].
- **OQ3 Contract header now vs after DRG-458.** Renewal needs term end and notice data on a header.
- **OQ4 Numbering, resolved:** provisional number at DRAFT, final `{contract_number}-A{nnn}` at EXECUTED, no reset on renewal.
- **OQ5 Partial application (RA-31 / DRG-565):** co-termed multi-asset renewals may half-apply.
- **OQ6 Revision model:** renegotiation creates revisions under one renewal number.
- **OQ7 Backfill (RA-32 / DRG-566):** past RENEWAL acceptances become BACKFILLED documents.
- **OQ8 Retention (DRG-313):** each renewal moves the contract end, and so the retention clock.

Dependencies: RA-15 / DRG-549, RA-33 / DRG-567 (G1), RA-07 / DRG-541 (Drools default), RA-12 / DRG-546, RA-30 / DRG-564 [S sanity 6]. Policy-specific [I]: renewal from InGrace or Expired; whether an open unapplied amendment projects into the renewal [S sanity scenario 4].

## Clarification 2026-10-16 (call decisions: auto-renew, notice, kickoff, quote modes)
Source: transcript addendum "Renewals and amendments decisions audit"; backlog DRG-596 (RA-62), DRG-593 (RA-59), DRG-554 (RA-20), DRG-555 (RA-21).
- **Conflict with the term-end trigger above.** The stub lets a term-end auto action (Renew, RenewOnce, Evergreen, Cancel) run if no seller action and evaluates notice windows from clause fields. Decision: **auto-renew is opt-in per contract and off by default**; **clauses are notice triggers only**. No automatic renewal happens without the opt-in.
- **Notice period:** one **global default** with a **per-contract override** (settings, not clause content).
- **Auto-kickoff:** opt-in, **90 days before end date by default and configurable**.
- **Renewal quote creation:** auto-generate from contract terms or start from scratch; prior-term usage is optional, opt-in context (DRG-593).
- **Coterm:** blended or rates-carry-forward (carry-forward default); shared end date is the new anchor (extends RN1 co-term anchor); all-or-nothing per customer; optional renewal group for one consolidated renewal quote (DRG-554). Approver deferred (DRG-591).
