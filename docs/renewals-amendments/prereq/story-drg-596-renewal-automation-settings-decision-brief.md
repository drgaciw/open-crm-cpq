# DRG-596 — Renewal automation settings: Decision brief (Wave-2 residual)

**Status:** Proposal only. Wave-2 residual Decision brief for CPQ Decision scoring. This file does **not** accept [`renewal-policy-spec.md`](renewal-policy-spec.md), amend the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md), authorize an implementation brief, authorize product code, flip a flag, register a consumer, enable the renewal outbox relay, enable ramp, bypass any asset-write hold, or deploy. Every residual fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewal-automation`. **Story:** [DRG-596](https://linear.app/drgamtd-workspace/issue/DRG-596/renewal-automation-settings-auto-renew-opt-in-default-off-notice) (RA-62). Linear read this pass: **In Review**. Notion mirror: [DRG-596 backlog row](https://app.notion.com/p/3fbf7cb4e4df814a81e3e243c111b80f) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen):**

| Artifact | Role |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § [Renewal automation](delegated-renewals-amendments-decisions-2026-10-17.md#renewal-automation) + AS-A..AS-D disposition | **Adopted baseline** for opt-in defaults, notice/kickoff clocks, versioned CAM settings + separate append-only settings audit, and holds (relay / ramp / Jev off) |
| [cluster-d-renewal-automation-decision-frame.md](cluster-d-renewal-automation-decision-frame.md) ([#649](https://github.com/drgaciw/open-crm-cpq/pull/649)) | Historical Cluster D forks AS-A..AS-D (rationale); call decisions **#10 / #11 / #12** restated below; dispositions already indexed |
| [renewal-policy-spec.md](renewal-policy-spec.md) §1.3–1.4, OQ-RN-1 / OQ-RN-9 | Working proposal; authorizes no code |
| [specs/renewal-automation.md](specs/renewal-automation.md) AC-596-1..5 | Proposed acceptance criteria; authorizes no code |
| [renewal-policy-stub.md](../renewal-policy-stub.md) Clarification 2026-10-16 | Call narrowing already on DRG-596 / DRG-555 |

**Cite-only Wave-2 neighbors (do not adopt their open OQs; do not re-widen DRG-555):**

| Ticket / PR | Linear / GH | Cite rule for this brief |
|---|---|---|
| [DRG-555](https://linear.app/drgamtd-workspace/issue/DRG-555) [#819](https://github.com/drgaciw/open-crm-cpq/pull/819) | In Review / draft Decision brief | **Cite only.** Restates the same call narrowing (clauses = notice triggers only; auto-renew opt-in default off). This brief does **not** import OQ-555-* rows, does not score them, and does not re-open RN-A..RN-D or invent unattended `EXECUTED` |
| [DRG-644](https://linear.app/drgamtd-workspace/issue/DRG-644) [#800](https://github.com/drgaciw/open-crm-cpq/pull/800) | Wave-2 docs lane / draft | **Cite only.** Checklist membership for `renewal-automation` (DRG-555 / 593 / 596) on DRG-607. This brief does not tick that checkbox, does not mark DRG-607 Done, and does not adopt product forks |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.outbox.relay.enabled` stays default off. `opencrm.pricing.ramp.enabled` stays default off. No `opencrm.jev.*.enabled` flag is turned on. Dual-read / NgRx / FE CPQ holds in [AGENT.md](../../../AGENT.md) stay untouched. CAM Flyway **V1_16–V1_21** remain reserved for [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) — **no migrations** from this story. An implementation brief for DRG-596 is **blocked** until CPQ Decision scores the residual forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (call / delegated)` | Already selected by call decisions 2026-10-16 (#10–#12) and/or the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Residual owner fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |

## Problem (RA-62)

Call decisions **#10**, **#11**, and **#12** (2026-10-16) already fix the product defaults for renewal automation settings. Cluster D [#649](https://github.com/drgaciw/open-crm-cpq/pull/649) framed the AS-* forks; the delegated record adopted the baseline defaults. What remains is a **residual Decision brief**: separate those Adopted call contracts from **owner forks still open** (settings storage shape, how DRG-540 / 544 / 545 relate without inventing a scheduler landing, audit of opt-in changes, and conflict clarifications vs DRG-555) so an implementation story cannot silently invent a consumer, a Flyway version, or a widened auto-renew path.

Linear DRG-596 (`Linear read` 2026-11-06): High; **In Review**; blocked by DRG-606 (Done); related DRG-555 / 540 / 544 / 545 / 643; attachments include Cluster D frame #649.

## Already Adopted — call decisions #10 / #11 / #12 (do not re-decide)

Restated from the Linear issue, the stub clarification 2026-10-16, Cluster D § DRG-596, and the delegated renewal-automation contract (`read`). This Decision brief does **not** reopen these rows.

| ID | Call # | Contract | Source |
|---|---|---|---|
| A1 | **#10** | **Auto-renew** is **opt-in per contract** and **off by default**. Absence of a settings write means `auto_renew = false` | Issue Decision; stub clarification; AS-A; delegated § Renewal automation |
| A2 | **#10** | Contract **clauses are notice triggers only**. A clause may start a notice window or an alert. It **never** turns auto-renew on and **never** renews by itself | Same; AC-596-4; conflict note vs earlier stub / DRG-555 wording |
| A3 | **#11** | **Notice period** is **one global default** plus a **per-contract override**, stored as **settings**, not as clause content. Spike header `notice_period_days` / `term_end_action` stay clause data | Issue Decision; AS-B / AS-C; renewal-policy-spec §1.3 |
| A4 | **#11** | No invented global notice integer in this brief: missing required notice input → no automated notice that requires it, with an explicit configuration reason. The integer itself remains an open residual (OQ-596-NOTICE below) | AS-B disposition; OQ-RN-1 |
| A5 | **#12** | **Auto-kickoff** of the renewal process is **opt-in**, on a configurable schedule, **default 90 days** before end (`kickoff_opt_in` default false; `lead_time_days` stored 90 and unused while kickoff is off) | Issue Decision; AS-A; AC-596-1 / AC-596-3 |
| A6 | — | Keep the existing **30-day** eligibility publication / receipt identity (`renewalDate = end − 30`). Notice, publication, and kickoff stay **three distinct clocks** | Delegated; Cluster D RN-B / AS-B; AC-596-5 |
| A7 | — | Versioned **CAM settings** with a **separate append-only settings-change audit**; clause notice fields remain distinct | AS-C replacement in delegated record |
| A8 | — | Holds: renewal outbox relay off; ramp flag off; no Jev enablement; S3 / S6 / S4 enablement not authorized by this story; dual-read / asset-write holds untouched | Cluster D holds; AGENT.md |

**Narrowing vs DRG-555 (cite #819 only; do not re-widen):** under A1–A2, no automatic term-end renewal happens unless the contract opts in, and clauses only trigger notices. DRG-555 is **not** renumbered. This brief does not adopt OQ-555-* and does not authorize term-end action execution, unattended `EXECUTED`, or scheduler edits claimed on DRG-555.

## Owner-decision table (residual forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells in this table. **No scheduler/product landing is invented** — delivery vehicle rows stay design/gate questions only.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-596-STORE | Settings model storage home | Target shape: versioned CAM settings row sibling to the thin contract header (AS-C / A7); **write no table here** | Store on the renewal quote; reuse spike header columns as settings (contradicts A3) | Yes — AC-596-1..5 need a durable home before green tests |
| OQ-596-NOTICE | Global notice-period integer (policy OQ-RN-1) | Leave unset; per-contract override may still be stored later; no notice record until override or later integer | Pick 30 / 90 / other integer now | Soft for kickoff; hard for notice ACs (AC-596-2 Case B) |
| OQ-596-AUDIT | Audit of opt-in / settings changes (OQ-RN-9) | Separate append-only settings-change log (matches A7) | Join CAM amendment hash chain | Yes — AC-RN-12 / settings audit DoD |
| OQ-596-CONSUMER | Scheduler / consumer relationship to DRG-540 / 544 / 545 | Keep landed receipt + unregistered consumer + `draftRenewal` unreachable; a **future** consumer must apply the kickoff matrix (kickoff off → no draft; kickoff on + inside lead window → one draft; duplicate open renewal → no-op). **Do not** invent job class, cron, topic, or flag landing in this brief | Register `draftRenewal` for every eligibility event (opens drafts without opt-in — rejected by A1/A5); delete consumer until settings exist | Yes — AC-596-3 / AC-596-5; DRG-540/544/545 remain gated facts, not product landings |
| OQ-596-555 | Conflict clarifications vs DRG-555 | Keep the 2026-10-16 narrowing: settings (this ticket) vs term-end / lead-time policy carrier (DRG-555); cite #819 OQs without adopting them; do not re-widen auto-renew or treat clauses as renew triggers | Merge tickets; re-open stub automatic term-end; treat clause `term_end_action` as settings | Soft for docs; hard if an implementation brief conflates the two |
| OQ-596-FLYWAY | Migration slot if/when settings land | **No migration in this story.** When a later authorized story needs CAM DDL, take the next free version **after** reserved V1_16–V1_21 (#805–#812); never reuse those slots | Claim V1_16–V1_21; ship DDL in this PR | Hard — schema NO-GO while reserved |

## Residual fork detail

### OQ-596-STORE — Settings model storage

| Option | What the owner would be choosing |
|---|---|
| **A. CAM sibling settings row (Recommended, Proposed)** | Matches AS-C / A7 and renewal-policy-spec §1.3 home proposal. Header enrichment (OQ3 / DRG-458) can lag. **No table is created by this brief.** |
| **B. Per renewal-quote settings** | Couples automation flags to a single quote lifecycle; weak for notice/kickoff across quotes. |
| **C. Reuse spike header clause columns** | Contradicts call #11 / A3 (settings ≠ clause content). **Not taken.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-596-NOTICE — Global notice integer

| Option | What the owner would be choosing |
|---|---|
| **A. Leave unset (Recommended, Proposed)** | Matches AS-B / A4. AC-596-2 Case B: notice-record count 0. |
| **B. Global default 30** | Aligns numerically with publication window only; does **not** merge notice and publication clocks. |
| **C. Global default 90** | Aligns numerically with kickoff lead; does **not** merge notice and kickoff clocks. |
| **D. Other integer** | Owner-supplied; same clock-separation rule. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-596-AUDIT — Opt-in change audit

| Option | What the owner would be choosing |
|---|---|
| **A. Separate append-only settings log (Recommended, Proposed)** | Matches delegated AS-C. Actor, role, UTC time, field, old/new for every opt-in / notice / kickoff write. |
| **B. Join CAM amendment hash chain** | Couples settings writes to document digests; still satisfies “audit of opt-in changes” if chosen later. |

**Recommended (Proposed):** A. **Neither log is built by this brief.** `Owner decision: ___ (drg, time)`.

### OQ-596-CONSUMER — DRG-540 / 544 / 545 without inventing a landing

Landed facts (`read` Cluster D Settled / renewal-policy-spec §1.4): nightly scan publishes `SubscriptionRenewalEligibleEvent`; consumer unregistered; same subscription can re-publish (DRG-545); `draftRenewal` does not read `kickoff_opt_in` / `auto_renew` because those fields are not in the tree; ramp flag and renewal outbox relay stay off.

| Option | What the owner would be choosing |
|---|---|
| **A. Future matrix consumer; no landing invented here (Recommended, Proposed)** | Restates AS-D. Spec §1.7 / AC-596 matrix stays the decision. Implementation story (job, idempotency, config) is **later** and separately authorized. This brief names **no** class, cron expression, feature flag, or topic. |
| **B. Register `draftRenewal` now** | Opens drafts for contracts that never opted in. Contradicts A1 / A5. |
| **C. Delete consumer until settings exist** | Allowed cleanup later; not required to score this Decision brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Explicit non-claim:** accepting Option A does **not** start a kickoff scan, register a listener, or enable the relay. Distinct CAM daily kickoff scan language in the delegated record remains **design**, not delivery authorization.

### OQ-596-555 — Conflict clarifications vs DRG-555

| Option | What the owner would be choosing |
|---|---|
| **A. Keep narrowing; cite #819 only (Recommended, Proposed)** | DRG-596 owns settings model / notice default+override / kickoff opt-in defaults and residual storage/audit/consumer gates. DRG-555 remains the auto-renew / lead-time / term-end **policy carrier**. Shared call text (opt-in off; clauses = notice only) is **not** re-litigated. OQ-555-* stay on #819. |
| **B. Merge residual forks into DRG-555** | Loses the settings-vs-policy split the issue already recorded. |
| **C. Restore stub automatic term-end without opt-in** | Contradicts call #10 / A1–A2. **Rejected.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-596-FLYWAY — Reserved CAM versions

| Option | What the owner would be choosing |
|---|---|
| **A. No migration here; skip V1_16–V1_21 (Recommended, Proposed)** | Docs-only PR. Later authorized DDL uses the next free CAM version after the #805–#812 reservation window. |
| **B. Ship settings DDL in this PR** | **NO-GO.** Violates docs-only Wave-2 residual scope and reserved slots. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Product / schema / flag NO-GO (until residual forks closed)

Until CPQ Decision scores OQ-596-* (or the owner defers named forks with words and a time), the following remain **NO-GO** for DRG-596:

1. **Product code** — Java / TypeScript / workflow / gateway routes that persist or read renewal automation settings.
2. **Schema / Flyway** — any CAM or quote-service migration; especially **V1_16–V1_21** (#805–#812 reserved).
3. **Flags** — enabling `opencrm.outbox.relay.enabled`, `opencrm.pricing.ramp.enabled`, any `opencrm.jev.*.enabled`, dual-read flips, S4 enablement, or asset-write hold bypass.
4. **Scheduler / consumer landing** — registering the eligibility listener, rewriting `RenewalScheduler`, inventing a kickoff job class, or treating `draftRenewal` as the DRG-596 consumer.
5. **Implementation brief** — none may authorize the above while residual forks stay Proposed.
6. **Linear Done** — DRG-596 stays **In Review** until the owner merges and DoD says otherwise. This brief does not mark Done.

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Residual forks are Proposed.
2. CPQ Decision must score the OQ-596-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work, Flyway, scheduler edits, consumer registration, or flag flips for DRG-596.
4. Even after forks close: no relay enablement; no ramp flag flip; no Jev enablement; no unattended EXECUTED; S3 / S6 remain separately authorized; do not adopt open OQ-555-* from #819; do not treat #800 as product adoption.
5. Linear status for DRG-596 stays **In Review**.

## Holds this brief keeps

| Hold | What stays true |
|---|---|
| Relay | `opencrm.outbox.relay.enabled` default false |
| Ramp | `opencrm.pricing.ramp.enabled` default false |
| Jev | No `opencrm.jev.*.enabled` turn-on |
| Dual-read / NgRx / FE CPQ / asset-write | AGENT.md holds untouched |
| S3 / S6 / S4 enablement | Not authorized |
| CAM Flyway V1_16–V1_21 | Reserved (#805–#812); unused by this story |
| Product code | **Forbidden** in the PR that carries this file |

## Out of scope

- Java / TypeScript / SQL / Flyway / workflow / flag changes.
- Accepting `renewal-policy-spec.md` as an ADR.
- Reopening call decisions #10–#12 or AS-A..AS-D Adopted dispositions.
- Implementing AC-596-1..5.
- Scoring or importing OQ-555-* from [#819](https://github.com/drgaciw/open-crm-cpq/pull/819).
- Ticking the DRG-607 renewal-automation checkbox from [#800](https://github.com/drgaciw/open-crm-cpq/pull/800).
- Inventing a scheduler class, cron, topic, or product UI landing.
- DRG-462 renewal opportunity creation; DRG-593 creation-mode delivery; DRG-549 / DRG-568 pricing expansion.

## References

- Linear: DRG-596, DRG-555, DRG-540, DRG-544, DRG-545, DRG-606, DRG-643, DRG-644, DRG-607.
- GitHub: Cluster D frame [#649](https://github.com/drgaciw/open-crm-cpq/pull/649); cite-only [#819](https://github.com/drgaciw/open-crm-cpq/pull/819), [#800](https://github.com/drgaciw/open-crm-cpq/pull/800); Flyway reservation window [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812).
- Docs: `cluster-d-renewal-automation-decision-frame.md`, `delegated-renewals-amendments-decisions-2026-10-17.md`, `renewal-policy-spec.md`, `specs/renewal-automation.md`, `renewal-policy-stub.md`.

## GitNexus note (this pass)

GitNexus MCP reported **no indexed repositories** for this checkout; CLI `.gitnexus/run.cjs` status was checked separately when present. Docs-only add of this markdown path has **no callable product symbol** to impact. Pre-commit `detect_changes` that returns `partial: true`, `truncated: true`, or `risk: UNKNOWN` is **not** a clean graph check — recorded as unresolved, not as low blast radius.
