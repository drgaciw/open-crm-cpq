# RenewalPolicy specification (proposal)

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. Proposal, not an accepted decision and not an implementation. Date: 2026-10-16.*

**Status.** Dev-ready prerequisite for the renewal policy. It promotes stub rules RN1–RN5 to testable acceptance criteria and records lead time, term-end actions, default settings, and the contract with DRG-544 and DRG-545. Implementing it unblocks [DRG-555](https://linear.app/drgamtd-workspace/issue/DRG-555) (RA-21), [DRG-596](https://linear.app/drgamtd-workspace/issue/DRG-596) (RA-62) and [DRG-593](https://linear.app/drgamtd-workspace/issue/DRG-593) (RA-59). This document authorizes no code, merge, deploy, or feature flag.

**Conventions.** **[S]** = sourced from a project file or from the code those files describe. **[I]** = proposal made here. **[D]** = call decision 2026-10-16, already recorded on DRG-596 and the DRG-555 comment. File keys match `renewal-policy-stub.md`: *stub*, *spec* (`mid-term-amendment-policy-spec.md`), *schema*, *spike*, *sanity*, *research*, *transcript*.

Renewal stays a policy on the amendment spine: intent `Renew`, document kind `RENEWAL`, no table of its own [S stub 1; schema 5]. Spine rules V3, V8, V10, V12 and V13 stay in force [S spec 7].

## 1. Scope

### 1.1 In scope

1. The RenewalPolicy settings a contract resolves before any renewal job or seller action: lead time, kickoff opt-in, auto-renew opt-in, term-end action, renewal term, notice period, cut-off and grace.
2. Defaults and the split between those settings and clause fields [D].
3. Lead-time kickoff, the notice window, and the term-end action, including when each one is a no-op.
4. Stub rules RN1–RN5, as acceptance criteria.
5. How the existing renewal-eligible event is consumed: at most one draft per contract term (DRG-544), and a repeated nightly publish is a no-op (DRG-545) [S stub 3].
6. The policy boundary for quote creation modes and optional prior-term usage context [D on DRG-593].

### 1.2 Out of scope

1. Code, schema migrations, scheduler edits, flags, and deployment. Those belong to the implementation stories this proposal unblocks.
2. The amendment first slice (intents other than Renew). Renewal binds to that spine after it exists [S spec 1].
3. Uplift mathematics, price-lock segments, true-up, and maintenance percent. This policy records the pricing decision (RN3); DRG-549 and DRG-567 define how the number is produced [S stub 1, 6].
4. Co-term policy design (DRG-554). RN1 only names the anchor as an override.
5. On-prem usage entry (DRG-587, parked). Prior-term usage here is a context flag, not a metering design.
6. Renewal opportunity creation (DRG-462).

### 1.3 Settings and defaults

Two stores stay separate [D; S spike 1; S stub 4].

| Store | Fields | Role |
|---|---|---|
| Renewal settings | `auto_renew`, `kickoff_opt_in`, `lead_time_days`, `term_end_action`, `renewal_term_months`, `notice_period_days` (per-contract override), global `notice_period_days`, `cut_off_days`, `grace_period_days` | Drive jobs and term-end behaviour |
| Clause legal terms | `notice_period_days`, `term_end_action` on the spike header | Notice triggers only. A clause may start a notice window or an alert. It does not set `auto_renew`, does not open a renewal, and does not execute a term-end action |

Recorded defaults [D]:

| Setting | Default | Who may change it |
|---|---|---|
| `auto_renew` | `false`. Opt-in per contract | A contract settings write. A clause cannot turn it on |
| `kickoff_opt_in` | `false`. Opt-in per contract | A contract settings write |
| `lead_time_days` | `90` | Per contract, used only when `kickoff_opt_in` is true |
| Notice period | One global default, value not chosen (OQ-RN-1). A per-contract `notice_period_days` replaces it for that contract | Settings only. The clause field is a trigger, not the day count |

Proposed defaults for the fields the call left unnamed [I]:

| Setting | Proposal |
|---|---|
| `term_end_action` | Required only when `auto_renew` is true. Enum `Renew`, `RenewOnce`, `Evergreen`, `Cancel` [S research 7.2]. While `auto_renew` is false the value is stored and not executed |
| `renewal_term_months` | Empty means “same length as the current term” (see AC-RN-02) |
| `cut_off_days` | Empty means there is no seller cut-off before `end_date` |
| `grace_period_days` | Empty means no grace. Renewal from `InGrace` or `Expired` stays closed until OQ-RN-4 |

Settings resolution, distinct from pricing precedence: per-contract value when set, otherwise the global default. Clause fields are not in this chain [I; D].

Pricing precedence is unchanged and still applies to uplift: contract clause, then line, then agreement, then account, then rules default [S stub 2]. A price clause may win uplift precedence. It still cannot opt the contract into auto-renew [D].

Every settings write is append-only and records actor, role, UTC timestamp, field, old value and new value [I; S spec A6]. Global default changes use the same audit shape at tenant scope.

Proposed home for the settings row: the CAM contract-documents module, as a sibling of the thin contract header, so notice and term-end data exist even while header enrichment (OQ3, DRG-458) is open [I; S stub 6]. The legal-terms columns are not reused as the settings store [D].

### 1.4 Lead time and notice

Three clocks. They are not interchangeable [I; D; S stub 3].

| Clock | When it runs | What it does | What it refuses |
|---|---|---|---|
| Lead-time kickoff | `kickoff_opt_in` and `end_date - as_of_date <= lead_time_days` | Opens one DRAFT renewal LifecycleChange with `initiation_trigger = LeadTime` | A second open renewal for that contract and term (RN4). Any open when kickoff is off |
| Notice window | `end_date - as_of_date <=` effective notice period, or a clause notice trigger fires | Writes a notice record (window start, effective days, source = global, contract override, or clause trigger) | Opening a LifecycleChange, setting `auto_renew`, executing `term_end_action` |
| Term-end action | `as_of_date` is the contract `end_date` and `auto_renew` is true | Runs the one action in §1.5 | Any action while `auto_renew` is false |

`lead_time_days` of 90 on a term ending 2027-03-31 makes 2026-12-31 the first kickoff date and 2026-12-30 a non-event. The 90 is inactive while `kickoff_opt_in` is false: the value is stored, the job does not open a draft [D].

Today the CAM `RenewalScheduler` (02:00) selects `ACTIVE` subscriptions with `renewal_date <= today` and publishes `SubscriptionRenewalEligibleEvent` (`subscriptionId`, `renewalDate`, `currentPlan`, `usageBand`). Accept sets `renewal_date` to `end_date` minus 30 days. Nothing consumes the event, and the same subscription is published again the next night [S `RenewalScheduler`, `SubscriptionRepository.findRenewalEligible`, `Subscription`; transcript 3.2; DRG-540, DRG-544, DRG-545]. That 30-day field is not this policy’s lead time. Until the scheduler changes, the consumer applies the table above to every event it receives, including events published 30 days out or published again the next night.

### 1.5 Term-end actions

Executed only when `auto_renew` is true and no non-terminal renewal LifecycleChange exists for that contract and term [D; I]. If one exists, the job leaves it untouched and appends an audit event `TermEndDeferredToOpenRenewal`. A half-edited seller draft is not auto-executed.

Apply still happens only after `EXECUTED` [S transcript, resolved OQ2]. The system renewal uses a signature policy that can reach `EXECUTED` without a seller signature. Which of `NOTICE_ONLY` or `NOT_REQUIRED` is the default is OQ-RN-3. Rate periods are not written before `EXECUTED`.

| Action | Result on the successor | After it succeeds |
|---|---|---|
| `Renew` | New term of `renewal_term_months` (or the current term length). RN1 and RN2 apply. Lines follow §1.6 | `auto_renew` stays true on the successor |
| `RenewOnce` | Same term extension as `Renew` | `auto_renew` is set false on the successor. `kickoff_opt_in` is copied as-is, so a later draft can still open and a later term-end will not auto-apply |
| `Evergreen` | Header `term_model = Evergreen` and `end_date` null, which the schema already allows for that model [S schema 2.2]. RN2 does not apply. Lead-time and term-end stop because there is no end date | `auto_renew` stays true and has no further term end to fire |
| `Cancel` | No renewal LifecycleChange, no extended `end_date`, no renewed rate periods. An audit event records the non-renewal. Existing expiry is what ends the term | `auto_renew` is set false |

While `auto_renew` is false, none of the four actions run. The contract is not silently renewed and not silently cancelled by this policy [D].

`Evergreen` leaves line `effective_to` null. The schema also uses a null `effective_to` for perpetual lines [S schema 2.5]. Header `term_model` is the distinguisher proposed here; OQ-RN-5 asks whether that is enough.

### 1.6 Rules RN1–RN5

Promoted from the stub [S stub 2]. Local numbers, not backlog ids.

- **RN1.** Renewed `effective_from` is the day after the current term `end_date`. A co-term anchor supplied by DRG-554 replaces that date, and the line’s PolicyDecision records the replacement.
- **RN2.** After apply, `contracts.end_date` equals the latest `effective_to` among lines whose action is `Renew`. A line that is kept with `NoChange`, and a perpetual line, do not move the header end.
- **RN3.** Each renewed line’s PolicyDecision records uplift basis, cap applied, price-as-of date, rounding mode, and the precedence level that won (clause, line, agreement, account, or rules default) [S sanity G1]. The pricing service produces the decision and the trace id (spine V8). This policy does not price inside quote-service or CAM.
- **RN4.** At most one non-terminal renewal LifecycleChange per contract and current term end. A repeated trigger, from the lead-time job, the term-end job, a manual create, or a replayed eligibility event, returns the existing id and writes nothing. Terminal outcomes (`WITHDRAWN`, `VOID`, `REJECTED`, `SUPERSEDED`, `EXPIRED`) free the key so another renewal for that term may be opened. This is the guard against DRG-545.
- **RN5.** A line with `isPermanent` is not given action `Renew` and is not given action `Remove`. Its linked maintenance line is renewed [S transcript 4.7; research 7.6].

Allowed actions on the same change remain `Renew`, `Add`, `Update` and `Remove` for upsell, downsell and cross-sell. A line the seller leaves off the renewal uses `NoChange`, not `Remove` [S stub 1].

Worked term used by the acceptance criteria: current term 2026-04-01 through 2027-03-31. With no co-term anchor and an empty `renewal_term_months`, the renewed line runs 2027-04-01 through 2028-03-31, and the header `end_date` becomes 2028-03-31.

### 1.7 Interaction with DRG-544 and DRG-545

DRG-544: eligibility never opens a draft. DRG-545: the same ACTIVE subscription is published every night, with no emitted marker. DRG-540: the event has no consumer. This policy is that consumer, gated by the settings in §1.3 [S stub 3; I].

**Publish key (DRG-545) [I].** One emission per (`subscription_id`, term `end_date`). A later night for the same pair does not publish again. The marker is per term, so the next term can emit.

**Consume key (RN4, DRG-544) [I].** One non-terminal Renew LifecycleChange per (`contract_id`, contract `end_date`). Several subscription events for that pair collapse to one draft. The consumer returns the existing id with `created = false`.

Until a contract header exists (OQ3), the consumer keys by (`subscription_id`, that subscription’s `end_date`) and still refuses a second non-terminal renewal for that key. When the header exists, subscription events collapse onto the contract key. A subscription end that differs from the contract end does not open a second renewal; co-term alignment stays with DRG-554.

Consumer matrix for an eligibility event or a lead-time run:

| `kickoff_opt_in` | Inside lead window | Open renewal for the key | Result |
|---|---|---|---|
| false | either | either | No draft. The event is acknowledged |
| true | no | no | No draft |
| true | yes | no | One DRAFT, `initiation_trigger = LeadTime`, creation mode auto-generate (§1.8) |
| true | yes | yes | No-op. Existing id returned |

Manual and API creates ignore `kickoff_opt_in` and still obey RN4. `usageBand` on today’s event is the subscription’s band [S event DTO]. It is not the prior-term usage context in §1.8.

### 1.8 Quote creation modes

The rep chooses at creation time [D, DRG-593].

| Mode | Contents | Rules that still apply |
|---|---|---|
| Auto-generate from terms | Renewal term, each renewable line, and the resolved renewal pricing policy (mode, uplift basis, cap, floor, price-as-of, rounding, precedence) | RN1–RN5. Lead-time kickoff always uses this mode |
| Start from scratch | A Renew LifecycleChange for the same contract with no copied lines until the rep adds them | RN1 supplies the default `effective_from`. RN4 blocks a second open renewal. RN5 still rejects a perpetual line added as `Renew` |

Prior-term usage is opt-in per quote and is informational context [D]. The pricing input snapshot omits it unless the resolved pricing policy declares that it consumes usage. Attaching it does not change price by itself. The on-prem source of that context stays parked (DRG-587) and is not designed here.

## 2. Acceptance criteria

Each criterion is pass or fail on the worked term in §1.6 unless it names other inputs. Failure of RN1–RN5 blocks the transition the stub names (pricing, or apply) and writes an audited rejection. Job no-ops are successes when the table in §1.7 says no draft.

**RN1**

- **AC-RN-01.** No co-term anchor. Every renewed line has `effective_from` 2027-04-01.
- **AC-RN-02.** Empty `renewal_term_months`. Those lines have `effective_to` 2028-03-31. A supplied `renewal_term_months` of 12 yields the same end. A supplied 24 yields `effective_to` 2029-03-31.
- **AC-RN-03.** A co-term anchor date is present. `effective_from` equals that anchor, and the PolicyDecision records that the anchor replaced RN1. The anchor’s own meaning is DRG-554’s.

**RN2**

- **AC-RN-04.** After apply, `contracts.end_date` equals the latest renewed `effective_to`. A `NoChange` line whose old `effective_to` is earlier does not pull the header end backward. A perpetual line does not supply the max.

**RN3**

- **AC-RN-05.** Each `Renew` line carries policy id, policy version, pricing-service trace id, uplift basis, cap applied, price-as-of date, rounding mode, and precedence level. A missing field fails the line. Quote-service and CAM do not compute the price.

**RN4 and DRG-545**

- **AC-RN-06.** Two lead-time runs, or two eligibility events, for the same contract and term end, while the first renewal is DRAFT, produce one LifecycleChange. The second call returns that id and `created = false` and does not add a revision.
- **AC-RN-07.** The same pair is published by the scheduler on a second night. The publish marker suppresses the second emission. If a duplicate is still delivered, AC-RN-06 holds.
- **AC-RN-08.** After the open renewal is `WITHDRAWN`, a new lead-time or manual create for that same term is allowed and receives a new id.

**RN5**

- **AC-RN-09.** An `isPermanent` line is absent from `Renew` lines and is not `Remove`. The maintenance line linked to it is present with action `Renew`.

**Defaults [D]**

- **AC-RN-10.** A contract with no settings write has `auto_renew` false and `kickoff_opt_in` false, and stores `lead_time_days` 90.
- **AC-RN-11.** Writing the clause fields `notice_period_days` and `term_end_action` leaves `auto_renew` false and creates no LifecycleChange.
- **AC-RN-12.** An audited settings change of `auto_renew` from false to true stores actor, role, UTC time, old value and new value, and does not update the previous audit row.

**Lead time and notice**

- **AC-RN-13.** `kickoff_opt_in` true, `lead_time_days` 90, no open renewal. A run on 2026-12-31 opens one DRAFT with `initiation_trigger = LeadTime` and creation mode auto-generate. A run on 2026-12-30 opens nothing.
- **AC-RN-14.** `kickoff_opt_in` false. An eligibility event, including one published under today’s `renewal_date = end_date - 30 days` rule, opens nothing.
- **AC-RN-15.** Effective notice period N, no kickoff. When `end_date - as_of_date <= N`, one notice record is stored and no LifecycleChange is created. A per-contract override N′ is the N that the record shows. With no override, the record shows the global default.

**Term-end actions**

- **AC-RN-16.** `auto_renew` false, any stored `term_end_action`, as-of date equal to `end_date`. No renewal is created, `end_date` is unchanged, and `auto_renew` stays false.
- **AC-RN-17.** `auto_renew` true, action `Renew`, no open renewal. The system renewal reaches `EXECUTED` under the signature policy from OQ-RN-3 before any rate period is written, the new term matches AC-RN-01 and AC-RN-02, and the successor has `auto_renew` true.
- **AC-RN-18.** Same inputs as AC-RN-17 with an existing DRAFT for that term. The draft is unchanged, no second renewal is created, and `TermEndDeferredToOpenRenewal` is appended.
- **AC-RN-19.** Action `RenewOnce`. The successor term matches `Renew`, and the successor has `auto_renew` false. A later term end for that successor does not auto-renew. Copied `kickoff_opt_in` true still allows a lead-time draft on the successor.
- **AC-RN-20.** Action `Evergreen`. The successor header has `term_model` Evergreen and a null `end_date`. A later lead-time run opens nothing.
- **AC-RN-21.** Action `Cancel`. No Renew LifecycleChange is created, `end_date` is unchanged, no renewed rate period is written, a non-renewal audit event is appended, and `auto_renew` becomes false.

**DRG-544**

- **AC-RN-22.** Inside the lead window, with kickoff on and no open renewal, the eligibility consumer opens the single draft AC-RN-13 describes. Outside that matrix row it opens none.

**DRG-593**

- **AC-RN-23.** Manual create with mode auto-generate copies renewable lines and the pricing inputs RN3 requires, and sets `effective_from` per RN1.
- **AC-RN-24.** Manual create with mode start-from-scratch persists a Renew LifecycleChange and zero copied lines, still dated per RN1, and a second manual create for that term returns the same id.
- **AC-RN-25.** Opting in prior-term usage stores the context reference on the quote. The pricing input snapshot omits it when the resolved policy does not declare a usage input, and the priced amount matches the run without the context.

## 3. Dependencies

Stories this proposal unblocks, and what they take from it:

| Story | Takes from this proposal |
|---|---|
| DRG-555 (RA-21) | Settings fields, the three clocks, the four term-end actions, and AC-RN-13 through AC-RN-21. The call narrowing on that issue is already applied here |
| DRG-596 (RA-62) | Defaults, the clause-versus-settings split, settings audit, AC-RN-10 through AC-RN-12 and AC-RN-15 |
| DRG-593 (RA-59) | Creation modes and usage-context boundary, AC-RN-23 through AC-RN-25, with RN1–RN5 still in force |

Behaviour this proposal assumes those stories will implement together with the existing gaps:

| Dependency | Why it is on the path |
|---|---|
| DRG-544 (RA-10) | Consumer that opens the one draft. AC-RN-22 is the contract |
| DRG-545 (RA-11) | Publish marker. AC-RN-07 is the contract. RN4 remains the consumer-side guard |
| DRG-540 (RA-06) | The unconsumed `SubscriptionRenewalEligibleEvent`. This policy is the consumer, and only when kickoff is on and the lead window is open |
| DRG-549 (RA-15), DRG-567 (RA-33) | Pricing modes, uplift basis, cap, floor, price-as-of, precedence. RN3 records their output |
| DRG-564 (RA-30) | PolicyDecision trail and input snapshot that RN3 and AC-RN-25 rely on |
| DRG-554 (RA-20) | Co-term anchor that AC-RN-03 defers to |
| DRG-541 (RA-07) | Renewal uplift Drools rule currently does not execute; auto-generate has no price until a pricing-service decision exists |
| DRG-543 (RA-09) | Done 2026-10-16. `POST /api/v1/subscriptions/{id}/renew` returns 410 Gone (`Deprecation: true`, successor `Link` to `POST /api/v1/quotes/continuations`, error `SUBSCRIPTION_RENEW_DEPRECATED`). `Subscription.renew` and `SubscriptionService.renew` are removed. RN3 is recorded on the priced continuation, applied on acceptance by `AcceptedQuoteSubscriptionService` (DRG-608) |
| Amendment spine (DRG-579 and the first-slice stories) | LifecycleChange, AmendmentDocument kind `RENEWAL`, apply-after-EXECUTED, numbering that does not reset on renewal [S transcript, resolved OQ2 and OQ4] |
| DRG-458 / schema OQ3 | Thin contract header. Settings may ship as a sibling row before the header is enriched [I] |
| DRG-561 (RA-27) | Placement ADR is Done. This proposal does not reopen where the document lives. The scheduler that publishes eligibility stays the CAM `RenewalScheduler` until an implementation story moves it |

Inherited spine constraints the implementation does not reopen: V3 (`observedRevision`), V8 (pricing-service decision), V10 (content digest), V12 (atomic units), V13 (apply idempotency), A1–A8 [S spec 4, 5, 7].

## 4. Open questions

These block a number or a legal choice. They do not block the defaults, RN1–RN5, or the DRG-544 / DRG-545 matrix.

- **OQ-RN-1.** Numeric global notice-period default. The call requires one global default and a per-contract override, and does not set the number [D].
- **OQ-RN-2.** Numeric `cut_off_days`, and whether passing the cut-off only blocks a change of mind or also withdraws an open draft. This proposal’s term-end rule defers to an open draft either way (AC-RN-18).
- **OQ-RN-3.** Signature policy for an unattended term-end renewal: `NOTICE_ONLY` or `NOT_REQUIRED`. Apply stays after `EXECUTED` [S resolved OQ2]. The choice needs Legal.
- **OQ-RN-4.** `grace_period_days`, and whether a subscription in `InGrace` or `Expired` may still be renewed. Proposed default is no grace and no renewal from either status [I; S stub 6].
- **OQ-RN-5.** How an evergreen line’s null `effective_to` is distinguished from a perpetual line’s null `effective_to` if header `term_model` is not considered sufficient [S schema 2.2, 2.5].
- **OQ-RN-6.** Whether `renewal_term_months` should default to 12 instead of the current term length when the current term is not a whole number of months.
- **OQ-RN-7.** Whether an open, unapplied mid-term amendment is projected into the auto-generated renewal [S stub 6; sanity scenario 4]. This proposal copies the applied term only.
- **OQ-RN-8.** Shape of the prior-term usage context for SaaS metering. On-prem self-reported usage stays parked (DRG-587). AC-RN-25 holds either way.
- **OQ-RN-9.** Whether settings audit rows join the CAM amendment hash chain or a separate append-only settings log. Either satisfies AC-RN-12.
- **OQ-RN-10.** Collapse of subscription-level events onto one contract key when subscription end dates differ and the contract header is not in place yet. §1.7 gives the interim subscription key and leaves co-term to DRG-554.
