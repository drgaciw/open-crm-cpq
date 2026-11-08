# DRG-555 — Auto-renew with lead time and term-end actions: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** accept [`renewal-policy-spec.md`](renewal-policy-spec.md), amend the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md), authorize an implementation brief, authorize product code, flip a flag, register a consumer, enable the renewal outbox relay, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewal-automation`. **Story:** [DRG-555](https://linear.app/drgamtd-workspace/issue/DRG-555/auto-renew-with-lead-time-and-term-end-actions-renew-renew-once) (RA-21). Linear read this pass: **In Review**. Notion mirror: [RA-21](https://app.notion.com/p/3fbf7cb4e4df8183b13cc57460f47908) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen):**

| Artifact | Role |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Renewal automation + Cluster D disposition (RN-A..RN-D, AS-A..AS-D) | **Adopted baseline** for opt-in defaults, distinct CAM kickoff scan, Evergreen header shape, and no unattended `EXECUTED` in this slice |
| [cluster-d-renewal-automation-decision-frame.md](cluster-d-renewal-automation-decision-frame.md) | Historical forks RN-A..RN-D / OQ-RN-DRIVE (rationale); dispositions already indexed |
| [renewal-policy-spec.md](renewal-policy-spec.md) §1.3–1.5, OQ-RN-1..10 | Working proposal; authorizes no code |
| [specs/renewal-automation.md](specs/renewal-automation.md) AC-555-1..14 | Proposed acceptance criteria; authorizes no code |
| [renewal-policy-stub.md](../renewal-policy-stub.md) Clarification 2026-10-16 | Call narrowing already on DRG-555 |

**In-flight alignment (do not contradict Accepted / draft semantics):**

| Ticket / PR | Linear / GH | What this brief must not contradict |
|---|---|---|
| [DRG-568](https://linear.app/drgamtd-workspace/issue/DRG-568) [#807](https://github.com/drgaciw/open-crm-cpq/pull/807) | In Progress / draft | Escalator / lock ACs 568-1..568-7, OF-568-1..9 stay Proposed; `opencrm.pricing.ramp.enabled` stays **false**; multi-year schedule pricing is out of DRG-555 scope |
| [DRG-664](https://linear.app/drgamtd-workspace/issue/DRG-664) [#812](https://github.com/drgaciw/open-crm-cpq/pull/812) | In Progress / draft (S-T3 Accepted story) | `subscriptions.end_date` = current committed period end and stays `NOT NULL`; Evergreen / auto-roll advances through the **RENEWAL path** without nulling that column; additive `renewal_mode` (`AUTO_ROLL` \| `MANUAL` \| null fail-safe); `chk_contracts_dates` / PERPETUAL null end remains **Proposed / NO-GO** |
| [DRG-593](https://linear.app/drgamtd-workspace/issue/DRG-593) | In Review | Creation modes (auto-generate vs start-from-scratch) and prior-term usage context; kickoff uses auto-generate when on |
| [DRG-596](https://linear.app/drgamtd-workspace/issue/DRG-596) | Related settings carrier | Owns settings audit / clause split with this ticket; defaults already adopted in the delegated record |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.outbox.relay.enabled` stays default off. `opencrm.pricing.ramp.enabled` stays default off. No `opencrm.jev.*.enabled` flag is turned on. An implementation brief for DRG-555 is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |

## Problem (RA-21)

There is still no owner-scored Decision brief that (a) separates **already Adopted** renewal-automation contracts from **remaining open forks**, and (b) cites market research (Conga / Chargebee / Zuora / Revenera) as **forks, not decisions**. Without that split, an implementation story could re-litigate RN-A..RN-D or silently invent unattended execution, a single merged clock, or a null `subscriptions.end_date`.

Linear DRG-555 (`read` 2026-11-06): High; **In Review**; attachments include Cluster D frame #649 (merged). Call comment 2026-10-16 already narrows: auto-renew opt-in and off by default; clauses are notice triggers only; one global notice default with per-contract override; kickoff opt-in at a configurable 90-day lead; term-end actions must not fire without opt-in.

## Market research — cited as forks only

Issue research links are orientation only. They are **not** selected options and do not override the delegated record.

| Vendor pattern | Observed options (research) | How this brief treats them |
|---|---|---|
| Conga CPQ | Auto vs OnDemand renewal modes; Renewal Lead Time | Fork illustrations for kickoff timing / mode — not a Conga parity decision |
| Chargebee | `action_at_term_end` ∈ {`renew`, `evergreen`, `cancel`, `renew_once`} | Naming parallel for the four stored actions — enum already in the proposal; semantics stay OpenCRM's |
| Zuora | `termType` TERMED / EVERGREEN; `renewalSetting`; `autoRenew` | Fork illustration for termed vs evergreen and auto-renew flag — not Zuora field mapping |
| Revenera FlexNet | Auto-renew job configuration | Fork illustration for a scheduled job — does not authorize enabling CAM's relay or rewriting `RenewalScheduler` here |

## Already Adopted (do not re-decide)

Restated from the delegated record (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | `auto_renew` default **false**, opt-in per contract; clauses cannot enable it | Delegated § Renewal automation; RN-A / AS-A |
| A2 | `kickoff_opt_in` default **false**; `lead_time_days` default **90**, unused while kickoff is off | Same; AS-A |
| A3 | Keep the existing **30-day** eligibility publication / receipt identity (`renewalDate = end − 30`) | RN-B replacement |
| A4 | Add a **distinct CAM daily kickoff scan** for opted-in contracts with a finite end date when `as_of_date ≥ endDate − leadTimeDays` and before term end; one operation per tenant/contract/end-date cycle; settings version is evidence, not part of the dedupe key; catch-up allowed; recheck opt-in and end-date/version before dispatch | RN-B replacement; AC-555-8 / AC-555-13 / AC-555-14 |
| A5 | Term-end uses a **separate clock** from notice and from kickoff; notice timing is independently configured | Delegated; three clocks in renewal-policy-spec §1.4 |
| A6 | No invented global notice integer: missing required notice input → no automated notice/action that requires it, with an explicit configuration reason | AS-B disposition; OQ-RN-1 remains open for the number |
| A7 | Four stored actions `Renew` / `RenewOnce` / `Evergreen` / `Cancel`; while `auto_renew` is false they are **inert** | RN-A; §1.5 |
| A8 | Evergreen distinguisher (policy): header `term_model = Evergreen` with **null contract `end_date`**, distinct from perpetual lines; finite-date scans skip it | RN-C; see OQ-555-ALIGN with DRG-664 |
| A9 | **No unattended path reaches `EXECUTED`** in this slice; flow stops for the existing explicit signature/approval process. Automated legal-signature execution is excluded | RN-D; AC-555-3 / AC-555-9..12 |
| A10 | Manual drafting remains available; widening auto-generate beyond the landed single-line zero-uplift 12-month draft needs delivered pricing contracts (DRG-593 / DRG-549) | QM-B; delegated |
| A11 | Versioned CAM settings + separate append-only settings-change audit; clause notice fields remain distinct | AS-C replacement |
| A12 | Holds: renewal outbox relay off; ramp flag off; no Jev enablement; S3 / S6 not authorized by this story | Cluster D holds; AGENT.md |

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells in this table.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-555-1 | Global notice-period integer (policy OQ-RN-1) | Leave unset; no automated notice until override or later integer | Pick an integer now (e.g. 30 / 45 / 60 / 90) | Soft for term-end; hard for notice ACs |
| OQ-555-2 | `cut_off_days` number and effect (OQ-RN-2) | Empty = no cut-off; open draft still wins at term end (AC-RN-18) | Numeric cut-off; cut-off withdraws open draft | Soft until notice/cut-off jobs ship |
| OQ-555-3 | Signature policy for a *later* unattended path (OQ-RN-3) | Keep Legal menu open (`NOTICE_ONLY` vs `NOT_REQUIRED`); **do not** authorize unattended `EXECUTED` in this slice | Pick NOTICE_ONLY; pick NOT_REQUIRED; authorize unattended now (contradicts A9) | Soft while A9 holds; hard if owner supersedes A9 |
| OQ-555-4 | Grace / renew from `EXPIRED` or proposed `InGrace` (OQ-RN-4) | No grace; refuse term-end and kickoff from those statuses (AC-555-5) | Finite `grace_period_days`; allow renew from InGrace only | Yes — AC-555-5 |
| OQ-555-5 | Evergreen vs perpetual line distinguisher (OQ-RN-5) | Header `term_model` is sufficient for contract/policy; perpetual stays line `isPermanent` / RN5 | Require sentinel end; require line-level flag; remove Evergreen until closed | Yes — AC-555-6 / AC-555-11 |
| OQ-555-ALIGN | Align A8 (null **contract** `end_date` for Evergreen) with DRG-664 (`subscriptions.end_date` NOT NULL; auto-roll via RENEWAL) | Keep both: contract header may be Evergreen/null; subscription period end stays non-null and advances only through RENEWAL; `renewal_mode` is complementary intent, not a replacement for `auto_renew` | Force null `subscriptions.end_date` for Evergreen (contradicts DRG-664); collapse `auto_renew` into `renewal_mode` only; change `chk_contracts_dates` for PERPETUAL now (NO-GO on #812) | Yes — successor Evergreen write |
| OQ-555-6 | Successor outcomes after explicit workflow for the four actions (AC-555-9..12) | Encode proposal §1.5 / AC-RN-17..21 as the target after explicit success; unattended still withheld | Soften Cancel to a status write; drop RenewOnce; drop Evergreen | Yes — AC-555-9..12 |
| OQ-555-7 | `renewal_term_months` empty default (OQ-RN-6) | Same length as current term when whole months; else refuse / owner pick | Default 12 always | Soft for AC-555-9 |
| OQ-555-8 | Open unapplied mid-term amendment projection into auto-generate (OQ-RN-7) | Copy applied term only | Project open amendment | Soft; DRG-593 / pricing |
| OQ-555-9 | Settings audit join CAM amendment hash chain vs separate log (OQ-RN-9) | Separate append-only settings log (matches AS-C) | Join amendment hash chain | Soft for AC-RN-12 |
| OQ-555-10 | Kickoff scan delivery vehicle | New CAM daily scan job (adopted design); **implementation** still delivery work; do not edit `RenewalScheduler` publication key in this story | Retarget publication to lead_time_days (historical RN-B-B); drive only from 30-day event | Yes — AC-555-8 |
| OQ-555-11 | `auto_renew` vs DRG-664 `renewal_mode` | Keep both: settings `auto_renew` gates term-end actions; `renewal_mode` records AUTO_ROLL/MANUAL intent on the subscription with null fail-safe; neither enables unattended EXECUTED alone | Alias them 1:1; ignore `renewal_mode` until later | Soft until CAM settings row exists |
| OQ-555-12 | Relation to DRG-568 escalators / locks | No choice here; OF-568-* stay Proposed; term-end / kickoff must not assume multi-period pricing while ramp flag is off | Require escalator resolution before any kickoff draft | Soft for flat renewals; hard for multi-year priced renewals |
| OQ-555-MKT | Conga / Chargebee / Zuora / Revenera parity depth | Cite as research forks only; no vendor parity commitment in this slice | Commit to Chargebee-shaped `action_at_term_end` wire format; Conga Auto mode parity | Soft |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-555-1 — Global notice-period integer (OQ-RN-1)

| Option | What the owner would be choosing |
|---|---|
| **A. Leave unset (Recommended, Proposed)** | Matches delegated AS-B: no invented global integer. Automated notice that needs a day count stays at count 0 until a per-contract override (or a later integer) exists. |
| **B. Pick an integer now** | e.g. 30 / 45 / 60 / 90. Becomes the tenant default; override still wins. |
| **C. Clause field is the day count** | Contradicts call decision and A1/A11 (clauses are triggers only). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-555-2 — Cut-off days (OQ-RN-2)

| Option | What the owner would be choosing |
|---|---|
| **A. Empty = no cut-off; open draft wins (Recommended, Proposed)** | AC-RN-18 / AC-555-4: term-end defers to an open non-terminal renewal. |
| **B. Numeric cut-off blocks change-of-mind only** | Seller cannot flip `term_end_action` after cut-off; open draft still stands. |
| **C. Cut-off withdraws open draft** | Stronger automation; conflicts with RN4 spirit unless separately authorized. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-555-3 — Signature policy for unattended term-end (OQ-RN-3)

| Option | What the owner would be choosing |
|---|---|
| **A. Legal menu open; unattended still excluded (Recommended, Proposed)** | Restates A9 / RN-D. `NOTICE_ONLY` and `NOT_REQUIRED` remain unselected. AC-555-3 and AC-555-9..12 stay withheld. |
| **B. NOTICE_ONLY** | Legal may pick later; does **not** by itself authorize S3 or unattended apply. |
| **C. NOT_REQUIRED** | Same caveat as B. |
| **D. Authorize unattended EXECUTED now** | Would require a superseding decision; **out of scope** for this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-555-4 — Grace / Expired / InGrace (OQ-RN-4)

| Option | What the owner would be choosing |
|---|---|
| **A. Refuse (Recommended, Proposed)** | AC-555-5: term-end action count 0 from `EXPIRED` or proposed `InGrace`. Empty `grace_period_days`. |
| **B. Finite grace-end date from policy** | Aligns with Cluster F grace wording elsewhere; needs a day count and status model. |
| **C. Allow renew from InGrace only** | Still needs the status to exist on `SubscriptionStatus` (it does not today). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-555-5 — Evergreen vs perpetual distinguisher (OQ-RN-5)

| Option | What the owner would be choosing |
|---|---|
| **A. Header `term_model` sufficient (Recommended, Proposed)** | Matches RN-C / A8. Perpetual lines stay RN5 (`isPermanent`: not Renew, not Remove). |
| **B. Sentinel non-null end required** | Conflicts with schema allowance for Evergreen null end on contracts. |
| **C. Remove Evergreen from enum until closed** | Drops a title action of DRG-555. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-555-ALIGN — Contract Evergreen null end vs subscription period end (DRG-664)

| Option | What the owner would be choosing |
|---|---|
| **A. Dual-field reading (Recommended, Proposed)** | **Contract** header may be `term_model=Evergreen` with null `contracts.end_date` (A8). **`subscriptions.end_date`** remains the current committed period end and stays `NOT NULL`; evergreen / auto-roll advances that period only through the RENEWAL path (DRG-664 AC1). `renewal_mode` null/MANUAL = no auto-roll; AUTO_ROLL records intent without authorizing unattended EXECUTED (A9). PERPETUAL null-end CHECK change stays NO-GO until its own ADR + dry-run (#812). |
| **B. Null subscription end for Evergreen** | Contradicts S-T3 Accepted / #812 docs. Rejected unless owner supersedes DRG-664. |
| **C. Collapse `auto_renew` into `renewal_mode` only** | Loses contract-level settings audit and clause split (DRG-596). Premature. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Default until owner picks (Proposed):** Document the dual-field reading in design text; write no migration here. `Owner decision: ___ (drg, time)`.

### OQ-555-6 — Four actions after explicit workflow (AC-555-9..12)

| Option | What the owner would be choosing |
|---|---|
| **A. Proposal §1.5 successor table (Recommended, Proposed)** | After explicit success only: Renew keeps `auto_renew` true; RenewOnce clears successor `auto_renew` and copies `kickoff_opt_in`; Evergreen sets header Evergreen + null contract end; Cancel writes no Renew LifecycleChange, leaves end unchanged, audits non-renewal, clears `auto_renew`. Unattended path still count 0. |
| **B. Soft Cancel** | Cancel also flips subscription status immediately. Out of slice without fulfilment evidence (Cluster F). |
| **C. Drop RenewOnce or Evergreen** | Contradicts issue title and Chargebee-shaped research enum. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-555-7 — Empty `renewal_term_months` (OQ-RN-6)

| Option | What the owner would be choosing |
|---|---|
| **A. Same as current whole-month term (Recommended, Proposed)** | AC-RN-02 / AC-555-9 illustration (2027-04-01..2028-03-31). Non-whole-month current term refuses or asks for an explicit months value. |
| **B. Always 12** | Simpler; wrong for multi-year terms. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-555-8 — Open amendment projection (OQ-RN-7)

| Option | What the owner would be choosing |
|---|---|
| **A. Applied term only (Recommended, Proposed)** | Matches proposal §4 and landed `draftRenewal` boundary. |
| **B. Project open unapplied amendment** | Needs S3/S4 and pricing contracts not authorized here. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-555-9 — Settings audit store (OQ-RN-9)

| Option | What the owner would be choosing |
|---|---|
| **A. Separate append-only settings log (Recommended, Proposed)** | Matches AS-C. Satisfies AC-RN-12 without joining the amendment hash chain. |
| **B. Join CAM amendment hash chain** | Couples settings writes to document digests. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-555-10 — Kickoff scan delivery vehicle

| Option | What the owner would be choosing |
|---|---|
| **A. Distinct CAM daily scan (Recommended, Proposed)** | Already Adopted as design (A4). This fork only confirms that **delivery** (job, idempotency store, config) remains a later implementation story and must not retarget the 30-day publication key. |
| **B. Retarget `RenewalScheduler` to lead_time_days** | Historical RN-B option B; changes receipt identity. |
| **C. Infer kickoff only from the 30-day event** | Cannot recover a 90-day window; rejected by A4. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-555-11 — `auto_renew` vs `renewal_mode`

| Option | What the owner would be choosing |
|---|---|
| **A. Complementary fields (Recommended, Proposed)** | Settings `auto_renew` gates term-end action execution (A1/A7). Subscription `renewal_mode` records AUTO_ROLL/MANUAL with null fail-safe (#812). Neither alone reaches EXECUTED (A9). |
| **B. 1:1 alias** | Premature; different stores and audiences. |
| **C. Ignore `renewal_mode` until settings land** | Allowed as interim code behaviour; still document the complementary reading. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-555-12 — DRG-568 escalators / locks

| Option | What the owner would be choosing |
|---|---|
| **A. No coupling in this slice (Recommended, Proposed)** | Kickoff / term-end may open or withhold drafts without multi-period pricing. OF-568-1..9 and ramp flag off remain as on #807. |
| **B. Block kickoff until escalators Decision Done** | Unnecessary for flat 12-month renewals. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-555-MKT — Vendor parity depth

| Option | What the owner would be choosing |
|---|---|
| **A. Research forks only (Recommended, Proposed)** | Conga / Chargebee / Zuora / Revenera inform the menu; OpenCRM semantics stay as A1–A12 + OQ-555-*. |
| **B. Wire-level Chargebee parity** | Premature API commitment. |
| **C. Conga Auto mode parity** | Same. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed.
2. CPQ Decision must score the OQ-555-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work, Flyway, scheduler edits, consumer registration, or flag flips for DRG-555.
4. Even after forks close: no relay enablement; no ramp flag flip; no unattended EXECUTED without a superseding decision; S3 remains separately authorized; PERPETUAL null-end CHECK stays on DRG-664's NO-GO gate; DRG-568 OF-* stay Proposed.
5. Linear status for DRG-555 stays **In Review** until the owner merges and DoD says otherwise. This brief does not mark Done.

## Holds this brief keeps

| Hold | What stays true |
|---|---|
| Relay | `opencrm.outbox.relay.enabled` default false |
| Ramp | `opencrm.pricing.ramp.enabled` default false; DRG-534 / OF-568 not implemented by this brief |
| Jev | No `opencrm.jev.*.enabled` turn-on |
| Dual-read / NgRx / FE CPQ | AGENT.md holds untouched |
| S3 / S6 | Not authorized |
| Product code | **Forbidden** in the PR that carries this file |

## Out of scope

- Java / TypeScript / SQL / Flyway / workflow / flag changes.
- Accepting `renewal-policy-spec.md` as an ADR.
- Reopening RN-A..RN-D Adopted dispositions.
- Implementing AC-555-1..14.
- DRG-462 renewal opportunity creation.
- DRG-587 on-prem usage (parked).
- DRG-549 mode-catalog expansion / DRG-568 escalator enablement.

## References

- Linear: DRG-555, DRG-593, DRG-596, DRG-568, DRG-664, DRG-606, DRG-643.
- GitHub drafts: [#807](https://github.com/drgaciw/open-crm-cpq/pull/807), [#812](https://github.com/drgaciw/open-crm-cpq/pull/812); merged frame [#649](https://github.com/drgaciw/open-crm-cpq/pull/649).
- Docs: `cluster-d-renewal-automation-decision-frame.md`, `delegated-renewals-amendments-decisions-2026-10-17.md`, `renewal-policy-spec.md`, `specs/renewal-automation.md`, `renewal-policy-stub.md`.
- Market research URLs on the Linear issue (Conga OnDemand renewal mode; Chargebee subscription update; Zuora subscription PUT; Revenera entitlement configuration).
