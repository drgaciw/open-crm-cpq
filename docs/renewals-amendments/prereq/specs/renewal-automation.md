# Renewal automation dev-ready spec (DRG-643)

**Status:** Proposed. This file records testable acceptance criteria for a later implementation. It authorizes no code, schema change, migration, flag, route, or deploy. `opencrm.outbox.relay.enabled` stays false. `opencrm.pricing.ramp.enabled` stays false.

**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `renewal-automation`. **Date:** 2026-10-29. **Base:** `main` at `bf41c230f6309964a25ef5c2c12e84c41b0c37de`.

**Issues.** [DRG-643](https://linear.app/drgamtd-workspace/issue/DRG-643). One section each for [DRG-555](https://linear.app/drgamtd-workspace/issue/DRG-555), [DRG-593](https://linear.app/drgamtd-workspace/issue/DRG-593), [DRG-596](https://linear.app/drgamtd-workspace/issue/DRG-596), [DRG-567](https://linear.app/drgamtd-workspace/issue/DRG-567), and [DRG-568](https://linear.app/drgamtd-workspace/issue/DRG-568).

**Sources, linked and not copied.** The [delegated decision record](../delegated-renewals-amendments-decisions-2026-10-17.md#renewal-automation) is the adopted baseline and overrides historical forks in the cluster-d frame. [DRG-606](https://linear.app/drgamtd-workspace/issue/DRG-606) RN1–RN5 in [renewal-policy-spec.md](../renewal-policy-spec.md) §1.6. Forks in [cluster-d-renewal-automation-decision-frame.md](../cluster-d-renewal-automation-decision-frame.md). Mode catalog in [drg-549-renewal-pricing-resolution-design-frame.md](../drg-549-renewal-pricing-resolution-design-frame.md). Uplift basis, the D-04 overlay, and a null percent: per DRG-604 Accepted (#623), [adr-renewal-uplift-basis-and-precedence.md](../adr-renewal-uplift-basis-and-precedence.md).

Worked term below, taken from renewal-policy-spec §1.6: current term ends **2027-03-31**. Day counts are `end_date − as_of_date`. 2026-12-31 is 90 days before that end. 2026-12-30 is 91. 2027-03-01 is 30. 2027-03-30 is 1. 2027-02-14 is 45. 2027-02-13 is 46. Figures are the sanity-check illustration (exit net 40.0000), not customer data.

This spec and its acceptance criteria remain Proposed; they do not authorize implementation. The separately adopted decision record selects the opt-in defaults, distinct CAM daily kickoff scan, catch-up and cycle deduplication, and excludes unattended execution in this slice. Historical fork labels and blank slots below do not reopen those adopted choices.

---

## Acceptance criteria

Expected values are in the tables. Pass or fail is against those values. This file adds no fixture JSON; the tables are the fixture.

### DRG-555 — auto-renew, lead time, and term-end actions

| ID | Given | Expected |
|---|---|---|
| AC-555-1 | Subscription `ACTIVE`. `end_date` 2027-03-31. `as_of_date` 2027-03-30. `auto_renew` true. Stored action `Renew`. No open non-terminal renewal. `kickoff_opt_in` false. | Term-end action count 0. LifecycleChange count 0. `end_date` remains 2027-03-31. No renewed rate period. `auto_renew` remains true. |
| AC-555-2 | Subscription `ACTIVE`. `as_of_date` equals `end_date` 2027-03-31. `auto_renew` false. `kickoff_opt_in` false. Run once for each stored action `Renew`, `RenewOnce`, `Evergreen`, `Cancel`. | For each stored action: LifecycleChange count 0, `end_date` remains 2027-03-31, no renewed rate period, `auto_renew` remains false. The `Cancel` run does not cancel the term. |
| AC-555-3 | Subscription `ACTIVE`. `as_of_date` equals `end_date`. `auto_renew` true. Stored action `Renew`. No open non-terminal renewal. Signature policy unset. | LifecycleChange is not `EXECUTED`. Renewed rate-period count 0. `end_date` remains 2027-03-31. The same withheld result holds when the stored action is `RenewOnce`, `Evergreen`, or `Cancel`: the other three actions also stay at count 0, because one stored action is the only candidate and that candidate is withheld. |
| AC-555-4 | Subscription `ACTIVE`. `as_of_date` equals `end_date`. `auto_renew` true. Stored action `RenewOnce`. A non-terminal renewal for that contract and term end already exists (state `DRAFT`). | Existing id unchanged. New renewal id count 0. Audit event `TermEndDeferredToOpenRenewal` appended once. `auto_renew` remains true. No successor is written, so the successor flag is not cleared. |
| AC-555-5 | Status `EXPIRED`, or the proposed status `InGrace`. `as_of_date` on or after `end_date`. `auto_renew` true. Stored action `Renew`. | Term-end action count 0 for `Renew`, `RenewOnce`, `Evergreen`, and `Cancel`. `InGrace` is not a `SubscriptionStatus` constant (`PENDING`, `ACTIVE`, `SUSPENDED`, `CANCELLED`, `EXPIRED`). |
| AC-555-6 | Header `term_model` `Evergreen` and `end_date` null. `auto_renew` true. | Term-end action count 0 for all four actions. `end_date` remains null. |
| AC-555-7 | Status `PENDING`, `SUSPENDED`, or `CANCELLED`. Any stored action. `auto_renew` true or false. | Term-end action count 0 for all four actions. `CANCELLED` does not receive a second `Cancel`. |
| AC-555-8 | `end_date` 2027-03-31. `lead_time_days` 90. `kickoff_opt_in` true. Eligibility publication dated 2027-03-01 (`end_date` minus 30 days). | Proposed future scan: none on 2026-12-30; one kickoff operation/draft on or after 2026-12-31 and before term end. On 2027-03-01, a missed scan catches up; if that cycle already ran, it returns the existing operation and creates no second draft. The 30-day publication is separate. Current implementation is not changed by this document. |
| AC-555-9 | `ACTIVE`. `end_date` 2027-03-31. `as_of_date` 2027-03-31. `auto_renew` true. Stored action `Renew`. No open non-terminal renewal. `renewal_term_months` empty. No co-term anchor. The signature-policy value is not chosen in this row. | No unattended path reaches `EXECUTED`, writes a renewed rate period, or changes the header. The flow stops for the existing explicit signature/approval process. Only after that separate process succeeds may the proposed successor use `effective_from` 2027-04-01, `effective_to` 2028-03-31, header `end_date` 2028-03-31, and `auto_renew` true (AC-RN-01, AC-RN-02, AC-RN-17). |
| AC-555-10 | Same gate as AC-555-9, stored action `RenewOnce`. Source `kickoff_opt_in` true. | No unattended execution or successor write. After the separate explicit flow succeeds, the proposed successor term is 2027-04-01 through 2028-03-31, `auto_renew` false, and `kickoff_opt_in` true (AC-RN-19). |
| AC-555-11 | Same gate as AC-555-9, stored action `Evergreen`. | No unattended execution or successor write. After the separate explicit flow succeeds, the proposed successor is Evergreen with null `end_date` and `auto_renew` true; finite-date kickoff skips it (AC-RN-20). |
| AC-555-12 | Same gate as AC-555-9, stored action `Cancel`. | No unattended `EXECUTED` or renewed rate-period write. The non-renewal outcome and its audit remain a Proposed later criterion (AC-RN-21); this row does not choose an automated cancellation mechanism. |
| AC-555-13 | An opted-in finite contract is due for kickoff. Run the future scan twice for the same tenant, contract and end date, including once after a settings-version change. | One kickoff operation for the cycle; the settings version is captured as evidence, not added to the deduplication key. No second draft is created. |
| AC-555-14 | A kickoff is selected, then the contract opts out or its end-date/version changes before dispatch. | Recheck before dispatch and do not dispatch the stale candidate. A missed scan may catch up only while the adopted due window is still open. |

#### AC-555-9 `Renew`

Status: Proposed

Owner decision: ___ (drg, time)

#### AC-555-10 `RenewOnce`

Status: Proposed

Owner decision: ___ (drg, time)

#### AC-555-11 `Evergreen`

Status: Proposed

Owner decision: ___ (drg, time)

#### AC-555-12 `Cancel`

Status: Proposed

Owner decision: ___ (drg, time)

#### Term-end table

Four actions by state. `SubscriptionStatus` values are the enum on this `main`. `InGrace` and the Evergreen header are separate rows because they are not those enum constants. The term-end automation does not execute a renewal unattended; the independent lead-time kickoff may create a draft before term end.

| State | Renew | RenewOnce | Evergreen | Cancel |
|---|---|---|---|---|
| `PENDING` | not allowed (AC-555-7). No ACTIVE term-end scan. | not allowed (AC-555-7). No ACTIVE term-end scan. | not allowed (AC-555-7). No ACTIVE term-end scan. | not allowed (AC-555-7). No ACTIVE term-end scan. |
| `ACTIVE`, `as_of_date` before `end_date` | not allowed (AC-555-1). The term-end clock is `as_of_date` equal to `end_date`. | not allowed (AC-555-1). Same clock. | not allowed (AC-555-1). Same clock. | not allowed (AC-555-1). Same clock. |
| `ACTIVE`, `as_of_date` = `end_date`, `auto_renew` false | not allowed (AC-555-2). `auto_renew` false withholds `Renew`. | not allowed (AC-555-2). `auto_renew` false withholds `RenewOnce`. | not allowed (AC-555-2). `auto_renew` false withholds `Evergreen`. | not allowed (AC-555-2). `auto_renew` false withholds `Cancel`. The term is not cancelled by this policy. |
| `ACTIVE`, `as_of_date` = `end_date`, `auto_renew` true, no open non-terminal renewal | No unattended `EXECUTED` or successor write (AC-555-9). Explicit workflow required. | Same gate (AC-555-10). | Same gate (AC-555-11). | No unattended execution or successor write (AC-555-12). |
| `ACTIVE`, `as_of_date` = `end_date`, `auto_renew` true, open non-terminal renewal | not allowed (AC-555-4). Open draft is left untouched. | not allowed (AC-555-4). Successor `auto_renew` is not cleared while the action is withheld. | not allowed (AC-555-4). Header `end_date` is not cleared. | not allowed (AC-555-4). The open draft is not cancelled by the term-end job. |
| `SUSPENDED` | not allowed (AC-555-7). Eligibility selection described in cluster-d is `ACTIVE` only. | not allowed (AC-555-7). Same. | not allowed (AC-555-7). Same. | not allowed (AC-555-7). Same. |
| `CANCELLED` | not allowed (AC-555-7). Outside the `ACTIVE` scan. | not allowed (AC-555-7). Outside the `ACTIVE` scan. | not allowed (AC-555-7). Outside the `ACTIVE` scan. | not allowed (AC-555-7). A second `Cancel` is not run. |
| `EXPIRED` | not allowed (AC-555-5). | not allowed (AC-555-5). | not allowed (AC-555-5). | not allowed (AC-555-5). |
| `InGrace` (proposed name, not a `SubscriptionStatus` constant) | not allowed (AC-555-5). | not allowed (AC-555-5). | not allowed (AC-555-5). | not allowed (AC-555-5). |
| Header `term_model` `Evergreen`, `end_date` null | not allowed (AC-555-6). No end date, so the term-end clock has no day to fire. | not allowed (AC-555-6). Same. | not allowed (AC-555-6). The header is already Evergreen. The action does not run again. | not allowed (AC-555-6). Same. No end date to cancel against. |

**OQ-RN-3, historical unattended term-end fork.** The adopted record excludes unattended `EXECUTED` in this slice regardless of the policy value. `NOTICE_ONLY` and `NOT_REQUIRED` remain unselected here; a different unattended path would require a separate superseding decision.

Status: Proposed

Owner decision: ___ (drg, time)

**OQ-RN-4, grace.** Renewal from `EXPIRED` or `InGrace` stays refused by AC-555-5.

Status: Proposed

Owner decision: ___ (drg, time)

### DRG-593 — renewal quote creation modes

| ID | Given | Expected |
|---|---|---|
| AC-593-1 | Manual create. Mode start-from-scratch. Contract `end_date` 2027-03-31. No co-term anchor. Then a second manual create for the same contract and term end while the first is non-terminal. | First create: Renew LifecycleChange count 1, copied line count 0, `effective_from` 2027-04-01. The date is DRG-606 RN1 (renewal-policy-spec §1.6); this row does not define a second date rule. Second create: same id returned, new id count 0. That guard is DRG-606 RN4. |
| AC-593-2 | A lead-time kickoff path, when kickoff is on. | Creation mode `auto-generate`. Start-from-scratch count 0. Copied shape remains the landed single accepted recurring line, uplift left unset, term 12 months. `draftRenewal` gains no parameter from this file. |
| AC-593-3 | Two creates of the same renewal. One opts in prior-term usage. The resolved policy declares no usage input. | The opted-in quote stores the context reference. Its pricing input snapshot omits that reference. The priced result equals the create with the opt-in off. A missing percent still resolves as AC-567-1 on both creates. Usage does not supply a percent. |
| AC-593-4 | `SubscriptionRenewalEligibleEvent` delivered in description only. `opencrm.pricing.ramp.enabled` false. Renewal outbox relay disabled. | The publication alone opens no draft; the independent opted-in CAM daily scan remains a separate adopted design and may open one when due. This file registers no consumer or worker. |

### DRG-596 — automation settings

#### Default: auto-renew off

`auto_renew` is opt-in per contract. A contract with no settings write has `auto_renew` false. A clause write cannot set it true (AC-596-4). This default is adopted in the delegated decision record; these test criteria remain Proposed.

#### Default: notice-period default with override

One global notice period, plus a per-contract override. This file sets no global integer. With no override and no global integer, notice-record count is 0 on every date, including `end_date`. A per-contract override is the day count the notice record shows when a record is written. The clause field is a trigger source, not that day count. The delegated record leaves the global notice number unset; these test criteria remain Proposed.

#### Default: 90-day opt-in

`kickoff_opt_in` is opt-in per contract and false when no settings write exists. `lead_time_days` is stored as 90 and is unused while `kickoff_opt_in` is false. Both defaults are adopted in the delegated decision record; these test criteria remain Proposed.

| ID | Given | Expected |
|---|---|---|
| AC-596-1 | No settings write. | `auto_renew` false. `kickoff_opt_in` false. `lead_time_days` stored 90. Draft count 0. Term-end action count 0. |
| AC-596-2 | `end_date` 2027-03-31. No kickoff. Case A: per-contract notice override 45, no reliance on a global integer. Case B: override absent, global integer unset. | Case A on 2027-02-14: notice-record count 1, days shown 45, source the contract override, LifecycleChange count 0. Case A on 2027-02-13: notice-record count 0. Case B on 2027-02-14 and on 2027-03-31: notice-record count 0, LifecycleChange count 0. |
| AC-596-3 | No settings write, so `kickoff_opt_in` false and `lead_time_days` 90. `end_date` 2027-03-31. `as_of_date` 2026-12-31. | Draft count 0. The stored 90 does not open a draft. |
| AC-596-4 | Clause fields written: `notice_period_days` 60 and `term_end_action` `Renew`. No settings write. | `auto_renew` remains false. `kickoff_opt_in` remains false. LifecycleChange count 0. |
| AC-596-5 | `end_date` 2027-03-31. Publication at 2027-03-01. `kickoff_opt_in` false. | Draft count 0. The publication date is the 30-day clock. It is not read as the 90-day opt-in. |

### DRG-567 — uplift basis, caps, price-as-of, precedence

Covered, and not re-decided here:

| Topic | Covered by |
|---|---|
| Default basis `ContractedNet`. A rung may select `CurrentList` or `ListLessContractedDiscount`. | per DRG-604 Accepted (#623), D-01 |
| D-04 sparse per-field precedence overlay. | per DRG-604 Accepted (#623), D-04 |
| A null percent means `UNRESOLVED_UPLIFT`. | per DRG-604 Accepted (#623) |
| Mode catalog `Same`, `List`, `UpliftPct`, `UpliftAmt`, `Override`, `Renegotiate`, and which of those a first slice prices. | [DRG-549](../drg-549-renewal-pricing-resolution-design-frame.md) |

Cap, floor, price-as-of, and single-unit rounding stay in that accepted record. This section does not restate them and does not open a second precedence.

Residual criteria (the automation path still has no price for these inputs):

| ID | Given | Expected |
|---|---|---|
| AC-567-1 | Exit net 40.0000. Mode slot would be percent-based. No rung sets a percent. Caller percent null. | Code `UNRESOLVED_UPLIFT`. Unit field absent. Stored unit is not 0 and not 40.0000. This file does not change `QuoteContinuationPolicy`. |
| AC-567-2 | A rung sets basis `CurrentList`. List fact absent. Account percent 7. Exit net 40.0000. | Code `UNRESOLVED_BASIS`. Unit field absent. The result is not 42.8000 and not 55.6400. No list price is invented. The price-book read stays DRG-539. |
| AC-567-3 | A rung sets basis `ListLessContractedDiscount`. Prior list 0. Current list 52. Account percent 7. Exit net 40.0000. | Code `UNRESOLVED_BASIS`. Unit field absent. A zero prior list is not read as a zero discount. |

### DRG-568 — multi-year escalators and time-bounded locks

DRG-533 is Accepted on 2026-10-17, as recorded in [its ADR](../DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md) and merged by #684. Acceptance is the status of that document; its recommendations are not in force and it authorizes no implementation. The ADR says later renewal periods stay unpriced. The DRG-549 later-year row keeps one flat priced period. This section makes no further choice of escalator semantics: compounding, percent-of-original, and percent-of-prior stay unselected here.

#### Whether a lock becomes time-bounded

Status: Proposed

Owner decision: ___ (drg, time)

The price-protection marker is not a cap.

| ID | Given | Expected |
|---|---|---|
| AC-568-1 | Term length 36 months. An annual 7 percent escalator is copied as intent only. Exit net 40.0000. Account percent 7. Basis left to DRG-604 D-01. | Priced period count 1. Period-1 unit 42.8000, the worked example per DRG-604 Accepted (#623). Period-2 unit absent. Period-3 unit absent. Compound flag absent. |
| AC-568-2 | The AC-568-1 input. | Stored percent-of-original choice absent. Stored percent-of-prior choice absent. Year-2 unit absent. Year-3 unit absent. The ticket illustrations 45.80, 45.7960, 49.01, 49.00, and 49.0017 are not expected values. |
| AC-568-3 | Rules rung returns `ENABLE_12M_PRICE_PROTECTION`. No rung sets `maxIncreasePct`. Exit net 40.0000. Account percent 7. | Marker stored as `ENABLE_12M_PRICE_PROTECTION`. `capApplied` absent. Priced period count 1. The marker does not change the unit. The marker is not a cap. |
| AC-568-4 | Flat renewal 2027-04-01 through 2028-03-31 (12 months). Promotional unit 20.00 with bound 2027-08-31 inclusive, inside that renewal. | Priced period count 1. A 5-month segment at 20.00 from 2027-04-01 through 2027-08-31 is absent. A following 7-month segment at 21.40 from 2027-09-01 through 2028-03-31 is absent. Those two segments sum to 12 months. The time bound stays Proposed, as the slot above. |

---

## What these criteria add

The tables above are the spec. This part states only the delta against DRG-606 RN1–RN5 and the cluster-d frame. It does not copy those documents.

### DRG-555

Frame: [DRG-555](../cluster-d-renewal-automation-decision-frame.md#drg-555--auto-renew-lead-time-and-term-end-actions), forks [RN-A](../cluster-d-renewal-automation-decision-frame.md#fork-rn-a--when-a-term-end-action-may-run), [RN-B](../cluster-d-renewal-automation-decision-frame.md#fork-rn-b--lead-time-versus-the-30-day-publication-window), [RN-C](../cluster-d-renewal-automation-decision-frame.md#fork-rn-c--evergreen-null-end-versus-a-perpetual-line-spec-oq-rn-5), [RN-D](../cluster-d-renewal-automation-decision-frame.md#fork-rn-d--how-an-unattended-term-end-reaches-executed-spec-oq-rn-3), and [OQ-RN-DRIVE](../cluster-d-renewal-automation-decision-frame.md#oq-rn-drive--drive-the-scheduler-open-question-only). Policy actions: renewal-policy-spec §1.5. RN1–RN5: §1.6.

Deltas:

1. The term-end table pairs the four actions with each state. The adopted decision excludes unattended `EXECUTED`; any later successor results require the existing explicit workflow and remain Proposed criteria.
2. AC-555-3 and AC-555-9 through AC-555-12 do not authorize an unattended path even if a signature-policy value is later selected. This file does not choose `NOTICE_ONLY` or `NOT_REQUIRED`.
3. AC-555-8 tests the adopted separate CAM daily kickoff scan, including catch-up and one operation per tenant/contract/end-date cycle. It is independent of the existing 30-day eligibility publication and does not edit `RenewalScheduler`.
4. The historical OQ-RN-DRIVE fork is superseded by the adopted distinct CAM scan; its implementation, idempotency and configuration are still delivery work, not current behavior.

DRG-606 RN2 (header `end_date` follows the latest renewed `effective_to`) is the proposed header end 2028-03-31 in AC-555-9, only after the separate explicit workflow succeeds. The unattended path writes no renewed line. RN5 (a perpetual line is not `Renew` and not `Remove`) is a line rule in §1.6. It is not a header term-end row, and it is not restated.

### DRG-593

Frame: [DRG-593](../cluster-d-renewal-automation-decision-frame.md#drg-593--renewal-quote-creation-modes), forks [QM-A](../cluster-d-renewal-automation-decision-frame.md#fork-qm-a--who-chooses-the-mode), [QM-B](../cluster-d-renewal-automation-decision-frame.md#fork-qm-b--what-auto-generate-copies-given-the-current-draft), [QM-C](../cluster-d-renewal-automation-decision-frame.md#fork-qm-c--prior-term-usage-context), [QM-D](../cluster-d-renewal-automation-decision-frame.md#fork-qm-d--entry-points-while-the-consumer-is-unregistered). Modes: renewal-policy-spec §1.8.

Deltas:

1. Basis, precedence, and a null percent are per DRG-604 Accepted (#623). The mode catalog is DRG-549. AC-593-2 does not list those fields again. Auto-generate, in this file, keeps the landed single-line draft (fork QM-B).
2. AC-593-1 uses RN1 for `effective_from` and RN4 for the second create. It adds the expected copied-line count of 0 for start-from-scratch.
3. AC-593-3 adds the expected price equality when prior-term usage is opted in and the policy declares no usage input. The SaaS payload shape (policy OQ-RN-8) and on-prem usage (DRG-587) stay outside this file.
4. AC-593-4 keeps the publication from creating a draft by itself while its consumer is unregistered. The adopted independent CAM kickoff scan, once implemented and opted in, uses auto-generate. Start-from-scratch is a manual or API choice in AC-593-1.

### DRG-596

Frame: [DRG-596](../cluster-d-renewal-automation-decision-frame.md#drg-596--automation-settings), forks [AS-A](../cluster-d-renewal-automation-decision-frame.md#fork-as-a--opt-in-defaults), [AS-B](../cluster-d-renewal-automation-decision-frame.md#fork-as-b--the-global-notice-number-spec-oq-rn-1), [AS-C](../cluster-d-renewal-automation-decision-frame.md#fork-as-c--where-settings-live-versus-clause-columns), [AS-D](../cluster-d-renewal-automation-decision-frame.md#fork-as-d--the-landed-draft-method-versus-the-kickoff-matrix). Settings text: renewal-policy-spec §1.3 and §1.4. The three clocks stay distinct there. This file does not merge notice, publication, and kickoff.

Deltas:

1. The criteria remain Proposed, while the delegated record already adopts both opt-ins off and a 90-day lead time. Those adopted defaults are not a new owner decision in this file.
2. AC-596-2 is the notice delta. Fork AS-B leaves the global integer unset. The expected counts are 0 until an override (or a later integer) exists, and 1 on 2027-02-14 when the override is 45, with no LifecycleChange.
3. AC-596-4 is the clause delta. Writing clause `notice_period_days` and `term_end_action` leaves both opt-ins false. The adopted design uses versioned CAM settings and a separate append-only settings audit; this document creates no table.
4. AC-596-3 and AC-596-5 keep the 90-day value unused while kickoff is off. The adopted opt-in CAM scan and the 30-day publication remain separate, and neither is newly enabled by this document.

### DRG-567

Cluster-d has no decision section for DRG-567. The uplift slice is cluster-d "Settled on this main", item 6, and the mode catalog is the [DRG-549 dependent](../cluster-d-renewal-automation-decision-frame.md#drg-549--pricing-mode-catalog). RN3 in renewal-policy-spec §1.6 records basis, cap applied, price-as-of, rounding, and the winning precedence level. Those fields stay the accepted record's fields.

Delta: AC-567-1, AC-567-2, and AC-567-3 are the inputs for which that record already requires no price (`UNRESOLVED_UPLIFT`, `UNRESOLVED_BASIS`). They are the residual on the automation path. They do not choose a basis, do not order the rungs, and do not publish the 7 percent worked example as a new rule. DRG-549's mode walk is not repeated.

### DRG-568

Frame: [DRG-568 dependent](../cluster-d-renewal-automation-decision-frame.md#drg-568--multi-year-escalators-and-price-locks). DRG-604 D-06 withholds later periods until the escalator procedure is accepted. The DRG-533 ADR is Accepted on this base, but still says its recommendations are not in force and later renewal periods stay unpriced. DRG-549's later-year row keeps one flat period.

Delta: This file records the accepted status of DRG-533 without treating its recommendations as in force or selecting escalator semantics for this proposal. AC-568-1 and AC-568-2 fix the expected priced shape at one period and leave both later-period bases unselected. AC-568-3 states that `ENABLE_12M_PRICE_PROTECTION` is stored as a marker and is not a cap (`capApplied` absent). AC-568-4 leaves a bound of 2027-08-31, inside the renewal 2027-04-01 through 2028-03-31, unpriced as a 5-month segment plus a 7-month segment. Whether a lock becomes time-bounded stays Proposed, with the owner slot blank. This file does not unblock DRG-568's implementation, and it does not amend ADR-023.

### DRG-606 RN1–RN5

| Rule | Where it lives | Use in this file |
|---|---|---|
| RN1 | renewal-policy-spec §1.6, AC-RN-01 | Cited by AC-593-1 for `effective_from` 2027-04-01. No second start-date rule. |
| RN2 | §1.6, AC-RN-04 | Cited by AC-555-9 for a proposed header `end_date` 2028-03-31 after the explicit workflow succeeds; the unattended path writes no renewed `effective_to`. |
| RN3 | §1.6, AC-RN-05 | Field list stays in §1.6. Basis and precedence: per DRG-604 Accepted (#623). Modes: DRG-549. |
| RN4 | §1.6, AC-RN-06 | Cited by AC-593-1 (second create) and AC-555-4 (open draft withheld). |
| RN5 | §1.6, AC-RN-09 | Not a header term-end row. Not restated. |
