# DRG-572 — One-time and retroactive charge lines: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted cells in the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md) § One-time charges and conversion / § 572 index, does **not** accept [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md) or invent a landed `servicePeriodStart/End` schema, does **not** authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a route, enable ramp / Jev / dual-read / S4, bypass the asset-write hold, select a billing receiver, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / one-time charges. **Story:** [DRG-572](https://linear.app/drgamtd-workspace/issue/DRG-572/one-time-and-retroactive-charge-lines-with-their-own-service-period) (RA-38). Linear read this pass: **In Review**. Notion: [RA-38](https://app.notion.com/p/3fbf7cb4e4df816d8118e5268c89d3cd) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / spec authority (cite, do not reopen Adopted cells; do not invent landed schema):**

| Artifact | Role |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § One-time charges and conversion + § 572 index | **Adopted baseline:** distinct optional `servicePeriodStart/End` pair; early window for `ONE_TIME` only; optional per-line `effectiveDate` → `contractEffectiveDate`; `ADD` + `ONE_TIME` for non-commitment charges; billing fields only (no receiver); later-scope — does not widen first-slice AMEND/ADD-UPDATE-REMOVE |
| [story-drg-572-one-time-retroactive-charge-lines-decision-frame.md](story-drg-572-one-time-retroactive-charge-lines-decision-frame.md) ([#654](https://github.com/drgaciw/open-crm-cpq/pull/654), merged) | Historical forks 1–7. Dispositions already indexed. Do **not** re-litigate Adopted rows |
| [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md) | Proposed intent vocabulary. Unaccepted |
| CAM `V1_7` `amendment_document_lines` | Landed `charge_type` / `effective_from` — **no** service-period columns yet |
| ADR-017 / ADR-024–027 | Stay **Proposed**. This brief does not accept them as a billing receiver |

**Related Wave-2 drafts (cite only; do not adopt open OQs):**

| Ticket / draft | What this brief must not adopt |
|---|---|
| [DRG-553](https://linear.app/drgamtd-workspace/issue/DRG-553) / [#835](https://github.com/drgaciw/open-crm-cpq/pull/835) | Effective vs action dates OQ-553-*; trigger dates stay on header |
| [DRG-550](https://linear.app/drgamtd-workspace/issue/DRG-550) / [#843](https://github.com/drgaciw/open-crm-cpq/pull/843) | Termination-fee / unused-days credit OQ-550-* |
| [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) / [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) | Partial-apply residual; ONE_TIME apply target cited only |
| [DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536) | Multi-line amendment design note — prerequisite, not reopened |
| [DRG-573](https://linear.app/drgamtd-workspace/issue/DRG-573) / [#848](https://github.com/drgaciw/open-crm-cpq/pull/848) | Conversion credit policy OQ-573-* |
| [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559) / [#850](https://github.com/drgaciw/open-crm-cpq/pull/850) | Restructure residual OQ-559-* |
| CAM drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) | Flyway **V1_16–V1_21 reserved** — this brief writes **no** migration |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, billing receiver, or deploy.** Ramp / Jev / dual-read / S4 / asset-write holds stay in force. An implementation brief for DRG-572 is **blocked** until CPQ Decision scores the open **OQ-572-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-572-* row stays open.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |

## Problem (RA-38)

Lifecycle changes need non-recurring lines whose service period can precede the change's effective date (back-maintenance, reinstatement fees, true-up arrears). Continuation today is one recurring line with one `effectiveDate`. CAM `V1_7` already stores `charge_type` and `effective_from` but has **no** `service_period_*` columns, so Scenario 2's "window 2026-04-01..2026-09-30 / effective 2026-10-01" cannot be recorded as designed. No billing service exists under `backend/services/`; ADR-017 / ADR-024–027 stay Proposed.

Linear DRG-572 (`Linear read` 2026-11-06): Medium; **In Review**; attachments include decision frame [#654](https://github.com/drgaciw/open-crm-cpq/pull/654).

## Already Adopted (do not re-decide)

Restated from the delegated record § One-time charges and conversion + § 572 index (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | `chargeType` closed set remains the landed `RECURRING` / `ONE_TIME` / `USAGE` baseline (no new `CREDIT` value from this story) | **Adopted (delegated)** Fork 1 — baseline default |
| A2 | Distinct optional pair **`servicePeriodStart` / `servicePeriodEnd`** — both absent or both present; end not before start; not reused as `effective_from`/`effective_to` | **Adopted (delegated)** Fork 2 — recommendation A |
| A3 | Service window may start **before** line effective date **for `ONE_TIME` only**; other charge types retain effective-date validation | **Adopted (delegated)** Fork 3 — recommendation A |
| A4 | Optional per-line **`effectiveDate`**, falling back to change **`contractEffectiveDate`**; trigger dates remain on the header | **Adopted (delegated)** Fork 4 — recommendation A |
| A5 | Non-commitment charge = **`ADD` + `ONE_TIME`**; no new action code | **Adopted (delegated)** Fork 5 — recommendation A |
| A6 | Billing: **field contract only** (service period, effective date, amount/currency, immutable decision identity); **no** outbound receiver / operation selected | **Adopted (delegated)** Fork 6 — recommendation A |
| A7 | Credits: baseline default — new negative credit-line behavior excluded here; credits retain separately versioned conversion policy | **Adopted (delegated)** Fork 7 — baseline default |
| A8 | Later-scope: does **not** widen first-slice AMEND/ADD-UPDATE-REMOVE commercial scope | Same § |
| A9 | Holds: no product code / schema / flag from docs alone; CAM **V1_16–V1_21** reserved elsewhere | AGENT.md; #805–#812 |

## What the tree does today (one-time lines)

`verified by reading docs` / prior frame evidence at the base SHA.

1. Continuation request: single operation, single line, no `chargeType` / service period.
2. CAM `amendment_document_lines` (`V1_7`): `charge_type` + `effective_from`/`effective_to` present; **no** `service_period_start` / `service_period_end`.
3. No billing service or accepted ADR-017/024–027 receiver in this repo.
4. Hybrid / conversion credits remain other stories (#848 / #850 cite paths) — not duplicated here.

### GitNexus (this checkout)

GitNexus MCP reported **no indexed repositories** this pass (`impact` unavailable). Treat blast radius as **UNKNOWN** — not a clean check. Docs-only change: one new markdown file under `docs/renewals-amendments/prereq/`. A later implementation must run GitNexus `impact` before touching amendment lines, continuation DTOs, or any billing field surface.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed` under id **OQ-572-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No new Adopted cells. Rows refine **delivery** against A1–A9; they do **not** reopen those contracts. **Do not adopt** open OQs from #835/#843/#839/#848/#850 by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-572-1 | **Persistence home** for A2 service-period pair | Additive nullable columns on CAM amendment document lines (or successor line store) after reserved V1_16–V1_21; both-null or both-set check | Quote-only DTO with no durable columns; overload `effective_to` | Yes — schema |
| OQ-572-2 | **Flyway / schema numbering** | Would-be only; **next free after reserved CAM V1_16–V1_21**. Number settled when an authorized migration PR exists. **Never** claim V1_16–V1_21 here | Reserve V1_16–V1_21 in this brief; ship schema before OQs close | Yes — any migration |
| OQ-572-3 | **Continuation / pricing DTO exposure** | Expose service-period pair + chargeType on multi-line / amendment pricing path only; leave single-line continuation unchanged until DRG-536 delivery | Force continuation request to carry one-time lines now | Soft until multi-line AC |
| OQ-572-4 | **Validation rule packaging** for A3 | Refuse non-ONE_TIME early windows at write/price; ONE_TIME early window allowed when both ends present | Soft-warn only; allow early window for USAGE | Yes — pricing AC |
| OQ-572-5 | **Billing field contract shape** (A6) | Versioned read model / DTO listing line id, chargeType, service period, effective date, amount, currency, decision id/digest — **no** outbox/command | Invent order-service call; enable ADR-024–027 from this brief | Soft until receiver Decision |
| OQ-572-6 | **Receiver authorization** | Remain **NO-GO**; separate Decision required before any outbound billing operation | Select Quote-to-Revenue saga / order line from this docs PR | Yes — hold |
| OQ-572-7 | **Credit line mapping** vs A7 / DRG-573 | Keep credits on conversion policy until a later Decision requires a negative ONE_TIME line; cite #848 without adopting OQ-573-* | Map Scenario 3 credits onto ChangeLines in this brief | Soft until conversion AC |
| OQ-572-8 | **First-slice scope gate** (A8) | ONE_TIME retroactive lines stay later-scope; do not add to AMEND/ADD-UPDATE-REMOVE first-slice acceptance criteria | Expand first-slice to include back-maintenance lines | Yes — slice AC |
| OQ-572-9 | **S4 / dual-read / Jev / ramp / asset-write** | Remain **NO-GO** from this brief | Enable holds from docs alone | Yes — hold |
| OQ-572-IMPL | **Implementation brief authorization** | Blocked until blocking OQ-572-* close or are explicitly deferred with owner words+time | Authorize implementation from this file alone | Yes |

## Explicit NO-GO

Until blocking **OQ-572-*** rows close (or the owner defers them with words and a time):

- No product code, Flyway/schema, feature flags, topics, gateway routes, billing receivers, or deploys for one-time service periods.
- No CAM migration number in **V1_16–V1_21**.
- No S4 / dual-read / Jev / ramp / asset-write hold bypass.
- Do not accept ADR-017 / ADR-024–027 or invent an outbound billing operation.
- Do not adopt open OQs from #835 / #843 / #839 / #848 / #850.
- Do not widen first-slice commercial scope to include retroactive ONE_TIME lines.

## Verification (this pass)

- [x] Docs-only file under `docs/renewals-amendments/prereq/`
- [x] Adopted A1–A9 restated from delegated record; residual forks Proposed only
- [x] Linear left **In Review** (orchestrator / agent must not mark Done)
- [x] GitNexus unavailable / UNKNOWN — not treated as clean
- [x] Secret scan: no credentials in this file

## Owner decision slots

For each **OQ-572-*** row scored: `Owner decision: ___ (drg, time)`.
