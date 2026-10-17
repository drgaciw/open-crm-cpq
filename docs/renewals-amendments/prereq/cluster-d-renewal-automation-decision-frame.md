# Cluster D — renewal automation decision frame

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-10-17.*

**Status:** Accepted as documentation on 2026-10-17. See the [owner acceptance record](documentation-acceptance-2026-10-17.md) for scope; individual forks and implementation gates retain their stated statuses.

**Original proposal scope (before acceptance):** This file is a decision frame. Merging it records the forks, the recommendation, and the default. It does not adopt a default, and it does not change any ADR status. DRG-555, DRG-593, and DRG-596 stay In Review until the owner merges.

**The renewal outbox relay stays disabled.** `opencrm.outbox.relay.enabled` stays false (`OutboxProperties` default). This file does not enable the relay, does not register a new publisher, and does not turn the quote-side listener on.

**Base.** `main` at `3ee3ca0f` (merge of [#642](https://github.com/drgaciw/open-crm-cpq/pull/642)).

**Stories in this frame.** One section each: [DRG-555](https://linear.app/drgamtd-workspace/issue/DRG-555) (RA-21, auto-renew and term-end actions), [DRG-593](https://linear.app/drgamtd-workspace/issue/DRG-593) (RA-59, quote creation modes), [DRG-596](https://linear.app/drgamtd-workspace/issue/DRG-596) (RA-62, automation settings). Linear status when read on 2026-10-17: all three Backlog, each blocked by [DRG-606](https://linear.app/drgamtd-workspace/issue/DRG-606) (Done; the proposal is [renewal-policy-spec.md](renewal-policy-spec.md), merged in [#607](https://github.com/drgaciw/open-crm-cpq/pull/607)).

**Dependents, linked and left blocked.** [DRG-549](https://linear.app/drgamtd-workspace/issue/DRG-549) and [DRG-568](https://linear.app/drgamtd-workspace/issue/DRG-568). See Dependents. This frame does not unblock either story.

**Does not authorize.** Code, a schema change, a Flyway migration, a topic, a gateway route, a feature flag, a deploy, or a merge of an implementation pull request. No flag or deploy is involved. `opencrm.pricing.ramp.enabled` stays false (`quote-service` `application.yml` and `RampPricingProperties`). No `opencrm.jev.*.enabled` flag is turned on. The renewal outbox relay stays disabled.

## Holds this frame keeps

These are constraints on every fork below. A recommendation in a later section does not lift them.

| Hold | What is true on this `main` |
|---|---|
| Relay | The renewal outbox relay stays disabled. ADR-018 (Accepted) makes `opencrm.outbox.relay.enabled` opt-in, default false, because enabling it replays the unpublished backlog. `RenewalScheduler` still inserts the outbox row inside the same transaction as the publication receipt. The row is not delivered while the relay is off. No fork below enables it. |
| DRG-611 | The quote-side backstop is not launched. [DRG-611](https://linear.app/drgamtd-workspace/issue/DRG-611) was read In Review. Its description asks for a migration. That sentence is outside this docs-only frame, so DRG-611 has no section here. The CAM receipt from [#622](https://github.com/drgaciw/open-crm-cpq/pull/622) is a different store and is already on `main` (see Settled). |
| S6 | [DRG-584](https://linear.app/drgamtd-workspace/issue/DRG-584) is not authorized. Linear status when read: Todo, blocked by DRG-581 and DRG-582. Its description adds `QuoteAcceptedEvent` fields, registers amendment event schemas, and names a per-tenant flag. This frame does not start that work. |
| S3 | [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) is not started. [#642](https://github.com/drgaciw/open-crm-cpq/pull/642) merged on 2026-10-17 and records OQ6 under delegation in [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md). That record says S3 requires separate owner authorization, and OQ18 stays Proposed until the owner confirms it. Linear still lists DRG-616, DRG-605, DRG-579, and DRG-580 as blockers. This frame does not start S3. |
| DRG-609 | The pricing-service continuation design is not an accepted design. [#625](https://github.com/drgaciw/open-crm-cpq/pull/625) merged the note [story-drg-609-pricing-service-continuation-design.md](story-drg-609-pricing-service-continuation-design.md). The note's own status is a design proposal. Linear status when read: In Review. Auto-generate in this frame does not assume Add, Update, or Remove pricing from that note. |
| ADR-017, ADR-024, ADR-025, ADR-026, ADR-027 | Each file under `docs/adr/` opens Proposed on 2026-10-04. This frame does not treat them as accepted and does not route renewal through `order-service`, a saga, or a command message. |
| Proration | The day-stub formula stays on the Accepted record [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) (status line: Accepted 2026-10-16, via [#623](https://github.com/drgaciw/open-crm-cpq/pull/623)). Renewal planning in `QuoteContinuationPolicy` uses one flat period and does not run that stub. This frame does not reopen the formula. |
| RenewalPolicy spec | [renewal-policy-spec.md](renewal-policy-spec.md) is a proposal on `main`. Its header says it authorizes no code. This frame cites it as the working proposal. It does not accept RN1–RN5. |

## Settled on this `main`

Read from the tree at `3ee3ca0f`. These facts are the floor under the forks. They are not new decisions.

1. **Publication receipt ([#622](https://github.com/drgaciw/open-crm-cpq/pull/622), merged 2026-10-16).** CAM migration `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7_1__durable_renewal_eligibility_publications.sql` creates `renewal_eligibility_publications` (`dedupe_key` primary key, `subscription_id`, `term_end`, `renewal_window`, `published_at`). The comment in that file says the receipt outlives outbox retention. `RenewalScheduler.publishOnce` inserts the receipt and the outbox row in one `REQUIRES_NEW` transaction (`backend/services/contract-asset-management/src/main/java/com/opencrm/contract/scheduler/RenewalScheduler.java`). A conflict on `dedupe_key` returns without a second outbox row. The key is `subscriptionId:termEnd:window` from `SubscriptionRenewalEligibleEvent.dedupeKey` (`backend/libs/common/src/main/java/com/opencrm/common/event/dto/SubscriptionRenewalEligibleEvent.java`). A moved window is a new key. The DRG-611 comment on 2026-10-17 records that deviation and leaves the key shape for the owner. This frame does not change the key.
2. **Who gets a receipt.** `SubscriptionRepository.findRenewalEligible` selects `ACTIVE` subscriptions with `renewalDate <= today`. `AcceptedQuoteSubscriptionService` sets `renewalDate` to `endDate.minusDays(30)` on create and on a later end-date write. The nightly cron is `0 0 2 * * *`. That 30-day field is the publication window. It is not the 90-day kickoff in DRG-596.
3. **Idempotent publish before the receipt.** [#612](https://github.com/drgaciw/open-crm-cpq/pull/612) (DRG-545) and [#628](https://github.com/drgaciw/open-crm-cpq/pull/628) are on `main`. The durable receipt is the marker that survives relay retention. The relay itself stays off, so survival of the marker is not a reason to enable it.
4. **Quote-side consumer, registered only while the ramp flag is on.** [#615](https://github.com/drgaciw/open-crm-cpq/pull/615) (DRG-540, DRG-544) added `SubscriptionRenewalEligibleEventConsumer`. The class is `@ConditionalOnProperty(name = "opencrm.pricing.ramp.enabled", havingValue = "true")`. The committed value is false, so the listener is absent. `QuoteContinuationService.draftRenewal` opens one `RENEWAL` draft from the single accepted recurring line, with no `upliftPercent` and no `termMonths`, so `QuoteContinuationPolicy.plan` uses zero uplift and 12 months, starting the day after the subscription end (`QuoteContinuationPolicy.java`, the renewal branch). A live renewal quote for that start date is returned unchanged. When it creates a new draft, the method writes one `commercial_decision_audit` row and publishes no draft-quote event. The existing-live-renewal return happens before `recordDecision`, so that path writes no new commercial audit row. `usageBand` is stored on that audit input. It is not prior-term usage context.
5. **Date-only renew is gone.** The renewal stub records `POST /api/v1/subscriptions/{id}/renew` as 410 Gone, successor `POST /api/v1/quotes/continuations`. `draftRenewal` is the eligibility writer on that continuation path. It prices locally in `QuoteContinuationPolicy` at zero uplift. It does not call `RenewalUpliftResolver` or pricing-service.
6. **Uplift slice, separate from this cluster.** [#640](https://github.com/drgaciw/open-crm-cpq/pull/640) merged `RenewalUpliftResolver` (`backend/services/pricing-service/src/main/java/com/opencrm/pricing/renewal/RenewalUpliftResolver.java`). It prices `ContractedNet` plus `UpliftPct` against the Accepted uplift record [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md). `RenewalUpliftModeOutOfSliceException` says other modes stay with DRG-549. `draftRenewal` does not call the resolver. The uplift record's D-06 says a later escalator does not grow periods until the DRG-533 procedure is accepted. That procedure file is still Proposed (Dependents).
7. **Policy text already on `main`, still a proposal.** [renewal-policy-stub.md](../renewal-policy-stub.md) binds renewal to the amendment spine (intent `Renew`, document kind `RENEWAL`) and carries the 2026-10-16 clarification: auto-renew opt-in and off by default, clauses as notice triggers only, one global notice default with a per-contract override, kickoff opt-in at 90 days. [renewal-policy-spec.md](renewal-policy-spec.md) promotes RN1–RN5 to acceptance criteria and writes the three clocks. Section 1.4 of that spec still says nothing consumes the eligibility event. That sentence describes the tree before [#615](https://github.com/drgaciw/open-crm-cpq/pull/615). The consumer class is on `main` and is not registered. This frame does not edit the spec.
8. **Apply after EXECUTED** is the resolved OQ2 in the stub. The state machine that would reach `EXECUTED` is S3, which this frame does not start. Unattended term-end therefore has no path to apply on this tree.

## How to read a fork

Each fork lists options, one recommendation, and one default. The recommendation is the option this frame asks the owner to confirm. Every default is labeled **Proposed, unaccepted, owner decision required**, and the alternatives are written beside that default. A default is not in force. The owner-decision table later in this file lists every fork.

---

## DRG-555 — auto-renew, lead time, and term-end actions

**Linear.** [DRG-555](https://linear.app/drgamtd-workspace/issue/DRG-555). Title: auto-renew with lead time and term-end actions (renew, renew once, evergreen, cancel). Priority High. Backlog. Blocked by DRG-606. Related: DRG-540, DRG-544, DRG-545, DRG-462, DRG-547, DRG-596.

**Problem.** RA-21. There is no renewal policy the jobs can read: no lead time, no auto-renew flag, and no term-end action. Market research (Conga Auto and OnDemand, Chargebee `action_at_term_end`, Zuora term type and `autoRenew`, Revenera) is cited on the issue. The 2026-10-16 comment on the issue narrows the research: auto-renew is opt-in per contract and off by default; clauses are notice triggers only; the four actions must not fire unless the contract has opted in. The settings model is DRG-596. The issue stays the research and policy carrier.

**Open question only.** The DRG-555 description says "Add RenewalPolicy and drive the scheduler." That sentence is an open question. It is not a decision, not a default, and not a fork. See OQ-RN-DRIVE. This section does not drive the scheduler. The renewal outbox relay stays disabled.

### OQ-RN-DRIVE — "drive the scheduler" (open question only)

The issue text is: add `RenewalPolicy {leadTimeDays, autoRenew, termEndAction, renewalTerm, cutOffDays, gracePeriod}` and drive the scheduler from it.

**Status of this sentence:** open question only. Proposed, unaccepted, owner decision required. It has no selected option and no default.

Alternatives the owner may later choose, none of them selected here:

1. Leave `RenewalScheduler` as the nightly publication scan it is on this `main`. The renewal outbox relay stays disabled.
2. After the owner accepts the forks in a later decision, a separate story teaches the scheduler the policy clocks. That story is not this file.
3. Change the scheduler in this frame. This file authorizes no code, so this alternative is not taken.

### Settled for this ticket

The publication window, the receipt, the unregistered consumer, and the flat 12-month zero-uplift draft are in Settled. The four action names and the "run only when `auto_renew` is true" rule are written in the Proposed spec §1.5. No settings row exists in CAM or quote-service. `renewalDate = end − 30 days` is the only lead-like field in code, and it selects who is published. It does not execute Renew, RenewOnce, Evergreen, or Cancel.

[DRG-462](https://linear.app/drgamtd-workspace/issue/DRG-462) (renewal opportunity creation) was read. Its description is an implementation story (`CreateRenewalOpportunity`, a `RenewalOpportunityCreated` event). That contradicts this docs-only frame, so DRG-462 has no section. The Proposed spec already places it out of scope.

### Fork RN-A — when a term-end action may run

| Option | Behavior |
|---|---|
| A | The four actions (`Renew`, `RenewOnce`, `Evergreen`, `Cancel`) are stored on the contract. They execute only when `auto_renew` is true and the as-of date is the term end. While `auto_renew` is false the stored value is inert. An open non-terminal renewal for that term is left untouched. |
| B | The stub's earlier trigger: if the seller has not acted, the term-end action runs on its own. |
| C | Drop the enum. A boolean renews or lapses, with no RenewOnce, Evergreen, or Cancel. |

**Recommendation.** A. It matches the 2026-10-16 comment and the Proposed spec §1.5.

**Default.** Proposed, unaccepted, owner decision required. Option A: the four actions are stored and run only when `auto_renew` is true; while it is false, no action runs. The enum is the stored shape when a later story adds the row. Alternatives beside this default: B, the stub trigger that runs the term-end action when the seller has not acted; C, drop the enum and use a boolean that renews or lapses, with no RenewOnce, Evergreen, or Cancel.

**Owner decision.** Confirm A, or pick B or C. B contradicts the comment already on DRG-555. C drops actions the title of DRG-555 names.

### Fork RN-B — lead time versus the 30-day publication window

| Option | Behavior |
|---|---|
| A | Keep `renewalDate = end − 30 days` as the CAM publication window and the receipt key's window component. `lead_time_days` (stored 90) is a second clock. It opens a draft only when kickoff is on (DRG-596). The 30-day event cannot trigger a draft 90 days before end. A separately authorized trigger that runs by the configured lead date is still required; this option supplies no such trigger. |
| B | Change `RenewalScheduler` so the first publication is `lead_time_days` before end, and stop using 30. |
| C | Treat 30 as the lead time and ignore the 90-day call decision. |

**Recommendation.** A. It leaves the receipt key and the scheduler alone while the policy clock stays a decision.

**Default.** Proposed, unaccepted, owner decision required. Option A: keep `renewalDate = end − 30 days` as the publication window, and keep `lead_time_days` 90 as a second clock. No scheduler edit follows from this frame. Alternatives beside this default: B, change `RenewalScheduler` so the first publication is `lead_time_days` before end and stop using 30; C, treat 30 as the lead time and ignore the 90-day call decision. The renewal outbox relay stays disabled under every alternative.

**Owner decision.** Confirm that 30 and 90 stay different clocks. The 90-day kickoff remains an unresolved delivery gap while first publication is at 30 days; evaluating a 90-day predicate on that late event cannot recover the missed kickoff. A later accepted design must provide an earlier trigger or select option B. This frame authorizes neither implementation. C contradicts DRG-596.

### Fork RN-C — Evergreen null end versus a perpetual line (spec OQ-RN-5)

| Option | Behavior |
|---|---|
| A | Header `term_model = Evergreen` with a null `end_date` is the distinguisher. A perpetual line (`isPermanent`) is still RN5: it is not Renew and not Remove. Lead-time and term-end stop because there is no end date. |
| B | Require a non-null sentinel end, or a line-level flag, before Evergreen can be stored. |
| C | Remove Evergreen from the enum until the schema question is closed. |

**Recommendation.** A, carried from the Proposed spec §1.5, with OQ-RN-5 left open for the owner if header `term_model` is not enough.

**Default.** Proposed, unaccepted, owner decision required. Option A: header `term_model = Evergreen` with a null `end_date` distinguishes evergreen from a perpetual line. No schema change. Evergreen is not executed while `auto_renew` is false (fork RN-A). Alternatives beside this default: B, require a non-null sentinel end, or a line-level flag, before Evergreen can be stored; C, remove Evergreen from the enum until the schema question is closed.

**Owner decision.** Whether header `term_model` is a sufficient distinguisher. Until that confirmation, a later story does not write a null `end_date`.

### Fork RN-D — how an unattended term-end reaches EXECUTED (spec OQ-RN-3)

| Option | Behavior |
|---|---|
| A | Signature policy `NOTICE_ONLY` for the system renewal. Apply stays after `EXECUTED` (resolved OQ2). |
| B | Signature policy `NOT_REQUIRED`. Same apply point. |
| C | No unattended renewal reaches `EXECUTED` until Legal picks A or B, and until S3 is separately authorized. |

**Recommendation.** C as the gate. A and B are the Legal menu. This frame does not pick A or B.

**Default.** Proposed, unaccepted, owner decision required. Option C: no unattended renewal reaches `EXECUTED` until Legal picks a signature policy and until S3 is separately authorized. S3 is not started here. Alternatives beside this default: A, signature policy `NOTICE_ONLY`, apply after `EXECUTED`; B, signature policy `NOT_REQUIRED`, same apply point. The renewal outbox relay stays disabled.

**Owner decision.** Legal chooses A or B later. That choice is not an authorization of S3, S6, or the relay.

### Dependencies

DRG-606's proposal is on `main` and stays Proposed. DRG-596 owns the opt-in flags this ticket reads. DRG-540, DRG-544, and DRG-545 are Done in Linear; their code is the receipt, the unregistered consumer, and `draftRenewal`, all gated as in Settled. The amendment spine that would hold a Renew LifecycleChange is S3, not started. ADR-024 through ADR-027 stay Proposed, so term-end does not become an order saga.

### What accepting DRG-555's forks would unblock

A later implementation story could add the settings row and the term-end job, still with the relay disabled until the owner enables it in its own change, and still with S3 unauthorized. Accepting this frame does not open that story. It does not unblock DRG-549, DRG-568, DRG-611, S3, or S6.

---

## DRG-593 — renewal quote creation modes

**Linear.** [DRG-593](https://linear.app/drgamtd-workspace/issue/DRG-593). Title: renewal quote creation modes (auto-generate from terms or start from scratch) with optional prior-term usage context. Priority Medium. Backlog. Blocked by DRG-606. Related: DRG-544, DRG-549, DRG-567, DRG-587.

**Problem.** RA-59. Call decisions 2026-10-16, items 5 and 9, as written on the issue: a renewal quote is created in one of two ways, and the rep chooses at creation. Auto-generate copies the contract's term, lines, and renewal pricing policy. Start from scratch opens the renewal with no copied lines. Prior-term usage is opt-in per quote, informational, and does not change price unless a pricing policy explicitly consumes it. The issue says this is outside the amendment first slice. The description's scope is design. This section is that design frame.

### Settled for this ticket

`draftRenewal` is one automatic shape: exactly one accepted recurring line, exit quantity, exit net, uplift zero, term 12 months, start the day after end. A second recurring line fails the method. There is no creation-mode field and no start-from-scratch path. `usageBand` on the event is copied into the audit inputs. [DRG-587](https://linear.app/drgamtd-workspace/issue/DRG-587) (on-prem self-reported usage) is parked. The DRG-609 design note is not an accepted design, so this ticket cannot assume pricing-service will price Add, Update, or Remove lines. The Accepted uplift record and the [#640](https://github.com/drgaciw/open-crm-cpq/pull/640) resolver cover `ContractedNet` / `UpliftPct` only. `draftRenewal` does not call that resolver. Proration stays on the Accepted amendment formula and is not the renewal plan.

### Fork QM-A — who chooses the mode

| Option | Behavior |
|---|---|
| A | The rep chooses at manual or API create. A lead-time kickoff, when kickoff is on, always uses auto-generate. Start from scratch is never the job's mode. |
| B | Eligibility always starts from scratch. The rep rebuilds every line. |
| C | Auto-generate is the only mode. Start from scratch is dropped. |

**Recommendation.** A. It is the call text on DRG-593, and the Proposed spec §1.8 already writes it.

**Default.** Proposed, unaccepted, owner decision required. Option A: the rep chooses at manual or API create; a lead-time kickoff uses auto-generate; start from scratch is never the job's mode. This frame adds no create API. The unregistered consumer is not turned on. Alternatives beside this default: B, eligibility always starts from scratch and the rep rebuilds every line; C, auto-generate is the only mode and start from scratch is dropped.

**Owner decision.** Confirm A. B and C each drop half of the call decision.

### Fork QM-B — what auto-generate copies, given the current draft

| Option | Behavior |
|---|---|
| A | The only auto-generate that exists today stays the single-line, zero-uplift, 12-month draft. Multi-line copy, a non-zero policy percent, and list bases wait on other stories. This frame does not widen `draftRenewal`. |
| B | Auto-generate now copies every renewable line and the resolved RenewalPricingPolicy (mode, basis, cap, price-as-of, precedence). |
| C | Auto-generate calls pricing-service for Add, Update, and Remove using the DRG-609 note. |

**Recommendation.** A. B is the DRG-549 mode catalog, which this frame does not unblock. C depends on a design the owner has not accepted.

**Default.** Proposed, unaccepted, owner decision required. Option A: auto-generate stays the single-line, zero-uplift, 12-month draft already on this `main`. `draftRenewal` is not widened. Alternatives beside this default: B, copy every renewable line and the resolved RenewalPricingPolicy (mode, basis, cap, price-as-of, precedence) now; C, call pricing-service for Add, Update, and Remove using the DRG-609 note. B and C are not started.

**Owner decision.** Confirm that "auto-generate from terms" in the call is the target behavior, and that the landed single-line draft is the interim, not the finished copy. Choosing B or C here would start work this frame refuses.

### Fork QM-C — prior-term usage context

| Option | Behavior |
|---|---|
| A | Opt-in per quote. The reference is stored. The pricing input snapshot omits it unless the resolved policy declares a usage input. Attaching it does not change the price by itself. `usageBand` on the eligibility event is not this context. |
| B | Always attach prior-term usage, and let it change the unit price. |
| C | Design the on-prem self-reported source in this frame. |

**Recommendation.** A. It is the call text. The Proposed spec AC-RN-25 matches it. The SaaS meter shape stays spec OQ-RN-8.

**Default.** Proposed, unaccepted, owner decision required. Option A with the opt-in off: prior-term usage is informational context, stored only when the rep opts in, and omitted from the pricing snapshot unless the policy declares a usage input. No usage reference is stored by this frame. Alternatives beside this default: B, always attach prior-term usage and let it change the unit price; C, design the on-prem self-reported source in this frame. On-prem usage stays parked on DRG-587, so C is not taken.

**Owner decision.** Confirm A, and whether OQ-RN-8 (the SaaS payload shape) must be answered before any opt-in control is built. The default builds nothing.

### Fork QM-D — entry points while the consumer is unregistered

| Option | Behavior |
|---|---|
| A | Manual and API create are the entry points this decision describes. The eligibility consumer remains a later entry, and only on the DRG-596 matrix (kickoff on, inside the lead window, RN4). The listener stays unregistered. The relay stays disabled. |
| B | Register `SubscriptionRenewalEligibleEventConsumer` now by turning `opencrm.pricing.ramp.enabled` on, so eligibility opens the single-line draft. |
| C | Launch the DRG-611 quote-side backstop (a quote-service migration) so the consumer can be registered later. |

**Recommendation.** A.

**Default.** Proposed, unaccepted, owner decision required. Option A: manual and API create are the entry points; the eligibility consumer stays a later entry on the DRG-596 matrix; the listener stays unregistered. The ramp flag stays false. The renewal outbox relay stays disabled. DRG-611 is not launched. Alternatives beside this default: B, turn `opencrm.pricing.ramp.enabled` on so eligibility opens the single-line draft; C, launch the DRG-611 quote-side backstop (a quote-service migration) so the consumer can be registered later. B and C are not done.

**Owner decision.** Confirm that quote creation modes are specified before either flag moves. B would also turn on ramp authoring for the rest of quote-service. C is the migration the DRG-611 description asks for, which this docs-only frame skips.

### Dependencies

DRG-606 proposal. DRG-544's consumer class, gated off. DRG-567's Accepted uplift record and the merged ContractedNet slice ([#640](https://github.com/drgaciw/open-crm-cpq/pull/640)); DRG-567 was In Progress in Linear when read and does not by itself supply list bases (the resolver says those wait on DRG-539). This frame does not unblock DRG-549 (Dependents). DRG-587 parked. DRG-609 design not accepted. DRG-564's audit note is a separate proposal; `draftRenewal` writes `commercial_decision_audit` when creating a new system draft, but not on its early return of an existing live renewal and that row is not the RN3 PolicyDecision.

### What accepting DRG-593's forks would unblock

A later story could add the mode field on manual create, with auto-generate still meaning the landed single-line draft until DRG-549 and DRG-609 are accepted on their own. Accepting this frame does not open that story and does not unblock DRG-549 or DRG-568.

---

## DRG-596 — automation settings

**Linear.** [DRG-596](https://linear.app/drgamtd-workspace/issue/DRG-596). Title: renewal automation settings — auto-renew opt-in (default off), notice-period default with override, opt-in 90-day auto-kickoff. Priority High. Backlog. Blocked by DRG-606. Related: DRG-555, DRG-540, DRG-544, DRG-545.

**Problem.** RA-62. Call decisions 2026-10-16, items 10, 11, and 12, as written on the issue. Auto-renew is opt-in per contract and off by default. A contract clause can start a notice window or an alert. It cannot turn auto-renew on and it cannot renew by itself. Notice period is one global default plus a per-contract override. Auto-kickoff is opt-in, on a configurable schedule, default 90 days before end. The issue records the conflict with the earlier stub wording and with DRG-555, and says DRG-555 is not renumbered. The description's scope is the settings model, the scheduler/consumer, and an audit of opt-in changes. This section frames the model. Every default below is Proposed, unaccepted, owner decision required. It does not add the consumer. The renewal outbox relay stays disabled.

### Settled for this ticket

No settings table and no global notice number exist in code. The spike's clause fields `notice_period_days` and `term_end_action` are described in the stub and the Proposed spec as legal terms, separate from settings. The running clocks are the 02:00 scan and `renewalDate = end − 30 days`. `draftRenewal`, if the listener were registered, would open a draft with no reading of `kickoff_opt_in` or `auto_renew`, because those fields are not in the tree. The Proposed spec §1.3 and §1.7 already write the opposite matrix: kickoff off means the event is acknowledged and no draft is opened. That matrix is a proposal.

### Fork AS-A — opt-in defaults

| Option | Behavior |
|---|---|
| A | `auto_renew` default false, per contract, opt-in. `kickoff_opt_in` default false, per contract, opt-in. `lead_time_days` stored as 90 and used only when kickoff is true. A clause write cannot flip either flag. |
| B | Kickoff defaults on at 90 days. Auto-renew stays off. |
| C | Both default on. |

**Recommendation.** A. It is the call text on DRG-596 and the DRG-555 comment.

**Default.** Proposed, unaccepted, owner decision required. Option A: `auto_renew` false and `kickoff_opt_in` false; `lead_time_days` stored as 90 and unused until kickoff is true; a clause cannot flip either flag. Absence of a settings row means the same. No row is created by this frame. Alternatives beside this default: B, kickoff defaults on at 90 days and auto-renew stays off; C, both default on.

**Owner decision.** Confirm A. B and C would open drafts, or renew, for contracts that never opted in.

### Fork AS-B — the global notice number (spec OQ-RN-1)

| Option | Behavior |
|---|---|
| A | Leave the number unset. A per-contract override may be stored later. Until a global number exists, the notice job writes no notice record. The clause field can still be a trigger once a job exists, and that trigger does not renew. |
| B | Global default 30, aligned with `renewalDate = end − 30 days`. |
| C | Global default 90, aligned with `lead_time_days`. |
| D | The owner supplies a different integer. |

**Recommendation.** A until the owner picks B, C, or D. The call requires one global default and does not give the integer.

**Default.** Proposed, unaccepted, owner decision required. Option A: the global notice number stays unset, and no notice record is written. 30 remains the publication window only (fork RN-B). Alternatives beside this default: B, global default 30, aligned with `renewalDate = end − 30 days`; C, global default 90, aligned with `lead_time_days`; D, an integer the owner supplies. B, C, and D do not merge the notice clock with publication or with kickoff.

**Owner decision.** The integer, or an explicit decision to keep A. Picking B or C does not merge the notice clock with publication or with kickoff. The Proposed spec keeps those three clocks distinct.

### Fork AS-C — where settings live, versus clause columns

| Option | Behavior |
|---|---|
| A | A renewal-settings row, proposed as a sibling of the CAM contract header, holding the flags, lead time, term-end action, renewal term, notice override, cut-off, and grace. Clause columns stay notice triggers and are not the day count. |
| B | Store the same fields on the quote, per renewal quote. |
| C | Reuse the spike header columns `notice_period_days` and `term_end_action` as the settings. |

**Recommendation.** A, as the Proposed spec §1.3 suggests. The home is still a proposal. Header enrichment (stub OQ3, DRG-458) can lag the sibling row.

**Default.** Proposed, unaccepted, owner decision required. Option A as the target shape: a CAM settings row beside the contract header, clause columns left as notice triggers, and no table added by this frame. Alternatives beside this default: B, store the same fields on the quote, per renewal quote; C, reuse the spike header columns `notice_period_days` and `term_end_action` as the settings. The call says settings are not clause content, so C is not taken. Audit-log alternatives inside this fork (OQ-RN-9): join the CAM amendment hash chain, or use a separate append-only log. This frame builds neither log.

**Owner decision.** Confirm the CAM sibling, or pick B. Also OQ-RN-9: settings audit rows join the CAM amendment hash chain, or they use a separate append-only log. Either log satisfies an audit of opt-in changes. The default builds neither.

### Fork AS-D — the landed draft method versus the kickoff matrix

| Option | Behavior |
|---|---|
| A | A future consumer applies the Proposed spec §1.7 matrix: kickoff off yields no draft; kickoff on and inside the lead window yields one auto-generate draft; a second open renewal for the key is a no-op. `draftRenewal` on this `main` does not read those flags. It stays unreachable while the ramp flag is false and the relay is off. |
| B | Treat `draftRenewal` as the DRG-596 consumer and register it. Every delivered eligibility event would open the single-line draft, including contracts that have not opted in. |
| C | Delete the consumer until the settings row exists. |

**Recommendation.** A. The matrix is the decision. The class can remain in the tree, unregistered.

**Default.** Proposed, unaccepted, owner decision required. Option A: a future consumer applies the Proposed spec §1.7 matrix, and `draftRenewal` stays unreachable while the ramp flag is false. No flag changes. The renewal outbox relay stays disabled. DRG-611's quote-side migration is not started, so there is still no quote-service marker beyond the inbox claim the unregistered listener would use. Alternatives beside this default: B, treat `draftRenewal` as the DRG-596 consumer and register it, so every delivered eligibility event opens the single-line draft, including contracts that have not opted in; C, delete the consumer until the settings row exists.

**Owner decision.** Confirm that registering the listener is a later change, after the settings row exists and an earlier kickoff trigger is accepted (RN-B), and that it must implement the matrix rather than call `draftRenewal` unconditionally. B would renew the "eligibility never opens a draft" gap in the wrong direction: it would open drafts for every published subscription.

### Dependencies

DRG-555 forks RN-A and RN-B (actions and the two clocks). DRG-593 fork QM-A (kickoff uses auto-generate). DRG-540, DRG-544, DRG-545 code, gated. The relay hold. DRG-611 not launched. S3 not started, so a kickoff "LifecycleChange" in the Proposed spec has no document API yet. The quote that `draftRenewal` writes is a quote-service `RENEWAL` draft, which is the interim artifact, not that LifecycleChange.

### What accepting DRG-596's forks would unblock

A later story could add the settings row and the notice number the owner picked, and could teach a consumer the matrix. Accepting this frame does not open that story, does not enable the relay, and does not unblock DRG-549 or DRG-568.

---

## Dependents

These two issues are related to the cluster. Their descriptions include implementation next steps (a pricing-policy type fed into continuation; a renewal segment schedule on the change line). Those sentences contradict a docs-only start, so this frame does not give them decision sections and does not unblock them.

### DRG-549 — pricing mode catalog

[DRG-549](https://linear.app/drgamtd-workspace/issue/DRG-549) (RA-15) is the hierarchy Same / List / Uplift percent / Uplift amount / override. It was the story waiting on [#640](https://github.com/drgaciw/open-crm-cpq/pull/640). That pull request merged on 2026-10-16. The merge prices `ContractedNet` and `UpliftPct` only, and the exception type names DRG-549 as the owner of the other modes. The wait for the merge is over. The catalog is not. This frame's auto-generate fork (QM-B) leaves that catalog where #640 left it. Accepting cluster D does not authorize the remaining modes, a list-price read, or a change to `draftRenewal`.

### DRG-568 — multi-year escalators and price locks

[DRG-568](https://linear.app/drgamtd-workspace/issue/DRG-568) (RA-34) needs the escalator procedure. [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533) is Done in Linear. [#644](https://github.com/drgaciw/open-crm-cpq/pull/644) merged [DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md](DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md) onto `main`. That file's status line is Proposed. The file says it unblocks DRG-568 only after the owner accepts it and a status-change pull request merges, and that this file is not that status change. Linear Done is not that acceptance. The Accepted uplift record D-06 still withholds later periods until that procedure is accepted. This frame does not accept the draft, does not amend ADR-023, and does not unblock DRG-568.

## Owner-decision table

Every fork. Each default is Proposed, unaccepted, owner decision required. The alternatives are written in the same row. OQ-RN-DRIVE is an open question and has no default, so it is listed under the table and is not a row that selects an option.

| Fork | Owner decides | Default (Proposed, unaccepted, owner decision required) | Alternatives beside the default |
|---|---|---|---|
| RN-A | When a term-end action may run | A. Store Renew, RenewOnce, Evergreen, Cancel. Run them only when `auto_renew` is true. While it is false, no action runs. | B. Run the action when the seller has not acted. C. Drop the enum; a boolean renews or lapses. |
| RN-B | Lead time versus the 30-day publication window | A. Keep `renewalDate = end − 30 days` as the publication window. Keep `lead_time_days` 90 as a second clock. The 30-day event cannot meet that kickoff; an earlier trigger remains unresolved. No scheduler edit. The renewal outbox relay stays disabled. | B. Publish first at `lead_time_days` and stop using 30. C. Treat 30 as the lead time and ignore the 90-day call decision. |
| RN-C | Evergreen null end versus a perpetual line (OQ-RN-5) | A. Header `term_model = Evergreen` and a null `end_date` are the distinguisher. No schema change. Evergreen does not execute while `auto_renew` is false. | B. Require a non-null sentinel end or a line-level flag. C. Remove Evergreen from the enum until the schema question is closed. |
| RN-D | Unattended path to EXECUTED (OQ-RN-3) | C. No unattended renewal reaches `EXECUTED` until Legal picks a signature policy and S3 is separately authorized. | A. `NOTICE_ONLY`, apply after `EXECUTED`. B. `NOT_REQUIRED`, same apply point. |
| QM-A | Who chooses the creation mode | A. The rep chooses at manual or API create. Kickoff uses auto-generate. Start from scratch is never the job's mode. No create API is added. | B. Eligibility always starts from scratch. C. Auto-generate is the only mode. |
| QM-B | What auto-generate copies | A. Keep the landed single-line, zero-uplift, 12-month draft. Do not widen `draftRenewal`. | B. Copy every renewable line and the resolved pricing policy now. C. Price Add, Update, and Remove from the DRG-609 note. |
| QM-C | Prior-term usage | A. Opt-in, off. Context only. Omitted from the pricing snapshot unless the policy declares a usage input. Nothing is stored. | B. Always attach usage and let it change the unit price. C. Design the on-prem source here (parked on DRG-587). |
| QM-D | Entry points while the consumer is unregistered | A. Manual and API create only. Listener stays unregistered. Ramp flag stays false. The renewal outbox relay stays disabled. DRG-611 is not launched. | B. Turn `opencrm.pricing.ramp.enabled` on. C. Launch the DRG-611 quote-side migration. |
| AS-A | Opt-in defaults | A. `auto_renew` false. `kickoff_opt_in` false. `lead_time_days` stored as 90 and unused until kickoff is true. A clause cannot flip either flag. No row is created. | B. Kickoff defaults on at 90 days; auto-renew stays off. C. Both default on. |
| AS-B | Global notice number (OQ-RN-1) | A. Leave the number unset. Write no notice record. 30 stays the publication window only. | B. Global default 30. C. Global default 90. D. An integer the owner supplies. |
| AS-C | Where settings live, and the audit log (OQ-RN-9) | A. Target shape is a CAM settings row beside the contract header. Clause columns stay notice triggers. No table is added. Neither audit log is built. | B. Store the fields on the quote. C. Reuse the spike header columns as the settings. Audit alternatives: CAM amendment hash chain, or a separate append-only log. |
| AS-D | Landed `draftRenewal` versus the kickoff matrix | A. A future consumer applies the spec §1.7 matrix. `draftRenewal` stays unreachable. No flag changes. The renewal outbox relay stays disabled. | B. Register `draftRenewal` for every delivered eligibility event. C. Delete the consumer until the settings row exists. |

**OQ-RN-DRIVE (open question only, no default).** The DRG-555 sentence "Add RenewalPolicy and drive the scheduler" is not a row above. Alternatives, none selected: leave the nightly scan as publication only, with the renewal outbox relay disabled; teach the scheduler the policy clocks in a later story after the owner accepts the forks; change the scheduler in this file. The third alternative is not taken, because this file authorizes no code.

**Withheld from this table.** S3 start, S6 authorization, the DRG-609 design, enabling the relay, the ramp flag, the DRG-611 quote-side migration, DRG-549's remaining modes, and acceptance of the DRG-533 draft. The renewal outbox relay stays disabled.

## What this frame unblocks

Nothing in code, schema, or runtime. After the owner confirms the forks, a separate implementation story can be opened for the settings row and the creation-mode field. That story is not authorized by merging this file. DRG-549 and DRG-568 stay blocked for the reasons in Dependents. S3 stays unstarted. S6 stays unauthorized. The renewal outbox relay stays disabled. DRG-555, DRG-593, and DRG-596 stay In Review until the owner merges.

## Evidence read for this note

Linear, 2026-10-17, team DRG: DRG-555 (plus its 2026-10-16 comment), DRG-593, DRG-596, DRG-549, DRG-568, DRG-606, DRG-540, DRG-544, DRG-545, DRG-611 (plus comments), DRG-567, DRG-533, DRG-581, DRG-584, DRG-609, DRG-462. GitHub pull request metadata: #607, #612, #615, #622, #623, #625, #628, #640, #642, #644, all merged to `main`. Files cited in Settled and in the holds. Notion [CPQ Definition of Done v0.2](https://app.notion.com/p/3fbf7cb4e4df8156a5bfe5c6c61d4338) fetched this pass; `verification.state` was `unverified`; the title and the body both say approved by the owner on 2026-10-16 at 4:30 PM CT. No GitNexus index is present in this tree, so impact is UNKNOWN.
