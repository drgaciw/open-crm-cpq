# DRG-542 — Amendment proration vs continuation formula: Decision brief (Wave-2 residual)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** invent a second proration formula, reopen or supersede the [Accepted amendment proration ADR](adr-amendment-proration-formula.md), reverse merged [#638](https://github.com/drgaciw/open-crm-cpq/pull/638), accept [#822](https://github.com/drgaciw/open-crm-cpq/pull/822) / DRG-564 audit forks, adopt open Cluster A / DRG-550 residual OQs, authorize product code, open a Flyway migration (pricing or CAM), flip a flag, register a route, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / proration. **Story:** [DRG-542](https://linear.app/drgamtd-workspace/issue/DRG-542/amendment-proration-rule-conflicts-with-continuation-formula) (RA-08). Linear read this pass: **In Review**. Notion: [RA-08](https://app.notion.com/p/3fbf7cb4e4df815693e7ecb6890334c3) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Formula authority (cite; do not restate numbers; do not invent a competitor):**

| Artifact | Role |
|---|---|
| [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) | **Accepted 2026-10-16** single amendment/expansion formula: day-stub + following full months in `QuoteContinuationPolicy.plan` (D-01 / D-02). Number: `none`. Rejected full-term ratio. |
| [#638](https://github.com/drgaciw/open-crm-cpq/pull/638) merge `8d8398e0` | **Merged** retire of classpath `amendment-proration.drl` + pricing `V9__disable_unedited_system_amendment_proration.sql` for unedited system `rule-007`. ADR D-03 delete list. |
| [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) D-04 | In-period day proration; no day proration on a whole-month boundary. Continuation matches; full-term ratio does not. |
| [DRG-603](https://linear.app/drgamtd-workspace/issue/DRG-603/adr-draft-proposed-single-amendment-proration-formula) | ADR acceptance story (blocks DRG-542 historically). |

**Cite-only related Wave-2 drafts (do not adopt open OQs):**

| Ticket / PR | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564) [#822](https://github.com/drgaciw/open-crm-cpq/pull/822) | In Review / draft Decision brief | Pricing-audit / PolicyDecision home, grain, confirmation vs resolve. Cite OQ-564-*; **do not** close them. Stub audit keys may reference Accepted D-02 fields; they are not a second formula. |
| [DRG-550](https://linear.app/drgamtd-workspace/issue/DRG-550) | In Review | Price mode / credit / termination fee. Delegated 2026-10-17 adopted Cluster A **550-F1..F4** baselines + Accepted proration formula. Cluster A frame and mid-term OQ10 remain cite-only for any still-open residual; this brief invents no new fee/credit arithmetic. |
| [cluster-a-amendment-modes-and-policy-decision-frame.md](cluster-a-amendment-modes-and-policy-decision-frame.md) §5 | Proposed frame / [#650](https://github.com/drgaciw/open-crm-cpq/pull/650) | Historical 550-F* rationale. Do not reopen proration. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) | Adopted catalog/policy sentence | `RetainContracted` / `NoCredit` / `NoFee` + **existing accepted proration formula**. Restate; do not re-decide the formula. |
| DRG-609 design notes | Design / acceptance records | Any later move of evaluation into pricing-service **moves** `QuoteContinuationPolicy.plan` (ADR D-02 / D-04). It does **not** revive `rule-007`. |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** CAM Flyway **V1_16–V1_21** are reserved by in-flight [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) — this brief claims none of them. Pricing tip on this `main` is already `V9`; a would-be later pricing migration is **not** numbered here. `opencrm.pricing.ramp.enabled` stays default off. No `opencrm.jev.*.enabled` flag is turned on. Dual-read, S4 enablement, and asset-write holds are untouched. Product / schema / flag work that would reintroduce a second proration formula, or that assumes residual forks below are closed, is **NO-GO** until those forks are scored (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted` | Already selected by Accepted ADR, merge, or delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `verified by GitNexus` | Impact/detect_changes after `npx gitnexus@latest analyze --index-only` on this checkout. |

## Problem (RA-08) — conflict restated

Two amendment proration procedures lived in the tree and did not compute the same charge.

1. **Continuation day-stub (the path that runs).** `QuoteContinuationService` / `QuoteService` ask `QuoteContinuationPolicy.plan` for amendment and expansion. Partial billing period: inclusive stub days over period days at scale 2 half-up, requested quantity at the accepted unit price of that period; later whole months full at accepted rates through subscription end; whole-month boundary → no day stub. Matches ADR-023 D-04. CAM `AcceptedQuoteSubscriptionService` consumes the resulting `ProrationSnapshot`; it does not recalculate days or money.

2. **Drools full-term ratio (seeded; not on that path).** Classpath `amendment-proration.drl` / DB `rule-007` used `amendmentDeltaAmount × remainingTermDays / fullTermDays` at scale 4. Quote-service never sent those inputs on calculate. Execute loads `ACTIVE` DB rules, not the classpath file. Wiring both would have priced the same amendment two ways — the conflict DRG-542 named.

**Accepted ADR D-01/D-02** picked (1) as the only formula and the only implementation place. **#638** deleted the classpath rule and added pricing `V9` to `DISABLED` unedited system `rule-007`. Hygiene (`Linear read`): GitHub auto-Done on #638 attach was reverted to **In Review**; Done needs DoD v0.2 + CPQ Decision; V9 applies on the owner's next pricing-service deploy.

This Wave-2 residual brief inventories what is already Adopted versus owner forks that still block calling the story Done / authorizing further product work. It does **not** propose an alternate formula.

## Already Adopted (do not re-decide)

Restated from the Accepted ADR (`read`), merge #638 (`read`), ADR-023 D-04 (`read`), and the delegated catalog/policy sentence (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | One amendment/expansion proration formula: day-stub + following full months; boundary = no day stub | ADR D-01 |
| A2 | Only implementation: `QuoteContinuationPolicy.plan` (stub + full-month loop). pricing-service does not compute `prorationAdjustment` for amendments | ADR D-02 |
| A3 | Pricing execute rate-check for the stub is not a second proration | ADR D-02 |
| A4 | CAM consumes `ProrationSnapshot`; does not grow a copy of the arithmetic | ADR D-02 |
| A5 | Full-term ratio rejected; wiring both formulas rejected; second body in pricing/shared helper rejected; deleting the continuation stub rejected | ADR Rejected |
| A6 | Retirement authorize list: delete classpath DRL + corpus/test allowlist edits; V9 DISABLE unedited system `rule-007`; do not rewrite V2/V7/V8 history | ADR D-03 / Code to delete; #638 |
| A7 | #638 merged to `main` (`8d8398e0`): DRL gone; `V9` on tree; phase-0 DRL count 7; ManagedSeed / golden fixture allowlist updates | GitHub merge |
| A8 | ADR-023 D-04 remains the day-proration rule the Accepted ADR follows | ADR-023; proration ADR |
| A9 | Delegated: `RetainContracted` / `NoCredit` / `NoFee` defaults + **existing accepted proration formula**; 550-F1..F4 baseline dispositions adopted | Delegated 2026-10-17 |
| A10 | Holds: ramp flag off; no Jev enablement; no product code from docs PRs alone; CAM V1_16–V1_21 reserved elsewhere | AGENT.md / #805–#812 |

## What the tree does today

`verified by reading code` at the base SHA.

1. **Classpath.** `backend/services/pricing-service/src/main/resources/rules/` has **no** `amendment-proration.drl`. Renewal uplift and the other phase-0 rules remain.
2. **Pricing Flyway.** `V9__disable_unedited_system_amendment_proration.sql` is present. It sets `pricing.rules` uuid `rule-007` to `DISABLED` only when `created_by = 'system'` and the newline-normalized body md5 still matches the unedited V8 seed. Owner-edited or non-system rows are left unchanged (notice logged). V2/V7/V8 history untouched. Tip = V9; next integer would be V10 only if a later migration PR exists.
3. **Continuation.** `QuoteContinuationPolicy` remains the live planner. No second proration body was added under pricing-service for amendments.
4. **Stale prose.** Filename / full-term formula mentions remain in agents, ADR-023 context, phase0 catalog, PRD, and some renewals docs (`read` search). They are not on the calculate path. ADR Consequences already say editing ADR-023 citation is a separate owner decision.
5. **CAM tip on this `main`.** Through `V1_15__document_apply_replay_evidence.sql`. In-flight drafts claim **V1_16–V1_21** (#805–#812). This brief adds **no** CAM migration.
6. **Test noise.** Some renewal-uplift unit fixtures reuse the string `rule-007` as a **synthetic uplift source id**. That is not the amendment proration seed body and is not revived arithmetic.

### GitNexus (this checkout)

Repository: `open-crm-cpq` (`/agent/repos/open-crm-cpq`). Worktree: same. Index: built this pass via `npx gitnexus@latest analyze --index-only` (MCP `list_repos` was empty before that analyze). Analyze logged process **truncation** (`truncated: true` / entry points dropped) — that is **not** a clean graph check for process coverage.

| Symbol | Upstream risk | Notes |
|---|---|---|
| `QuoteContinuationPolicy` | **UNKNOWN** | Empty caller set from the graph. Text search shows callers in `QuoteContinuationService`, `QuoteService`, controllers, and tests. UNKNOWN is unresolved, not low. `verified by GitNexus` + text search |
| `detect_changes --scope all` (pre-edit) | No changes detected | Docs-only file not yet written at check time. Re-run after the docs commit; a docs-only add should not imply production-symbol blast radius is clean if flags are partial/truncated |

No production symbol is edited in this docs-only draft. A later implementation must re-run impact before changing `QuoteContinuationPolicy.plan`, pricing `rule-007` rows, or V9 rollback SQL.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells in this table. **Product / schema / flag NO-GO** for any path that reopens a second formula, or that treats Done as automatic from #638 alone, until the blocking rows are closed (or explicitly deferred).

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC / Done? |
|---|---|---|---|---|
| OQ-542-1 | When is V9 considered applied for DoD? | Owner **deploy GO** + evidence that target env(s) ran Flyway to schema 9 (or equivalent); merge ≠ deploy | Treat #638 merge alone as Done; or require every named env in one ticket | Yes — Linear hygiene / DoD v0.2 |
| OQ-542-2 | Owner-edited / non-system `rule-007` left ACTIVE by V9 | Separate remediation story: inventory → owner decision per row (disable, rewrite to Accepted stub semantics, or leave with documented exception). Do **not** blanket-disable in a docs PR | Force-disable all `rule-007` regardless of body; leave forever without inventory | Soft until an edited ACTIVE row is found in an env; hard if found |
| OQ-542-3 | ADR number / `docs/adr/` promotion | Leave number `none` until owner assigns; no silent README row | Assign a number in this brief; copy into `docs/adr/` now | Soft (docs) |
| OQ-542-4 | ADR-023 context still names `amendment-proration.drl` | Leave citation historical until a dedicated ADR-023 edit story; do not reopen D-04 | Edit ADR-023 in the same docs PR as this brief | Soft |
| OQ-542-5 | Stale agent/PRD/phase0 prose describing full-term ratio | Separate docs hygiene story; calculate path already uses A1/A2 | Rewrite all mentions in this PR; or leave indefinitely | Soft |
| OQ-542-6 | Home if evaluation later moves to pricing-service (DRG-609) | **Move** the Accepted method (D-02/D-04); never reintroduce full-term ratio or a parallel body | Reimplement beside `rule-007`; or keep forever-only in quote-service with no move path | Soft while stub stays in quote-service; hard for any pricing-service proration writer |
| OQ-542-7 | DRG-550 / Cluster A residual around credit/fee **arithmetic** | Credit/fee **names** stay under DRG-550 / delegated 550-F*; any day arithmetic they call is A1 — cite only; do not invent fee math here | Treat full-term ratio as a `Daily` / credit option (ADR Rejected) | Soft for formula; hard if a story tries to revive `rule-007` for credit |
| OQ-542-8 | DRG-564 / #822 audit snapshot of the stub | Audit forks stay on #822 (OQ-564-*). Snapshot **fields** may mirror Accepted `ProrationSnapshot`; snapshot is not a second formula | Encode full-term inputs into PolicyDecision; or delay all amendment audit until stub moves | Soft while #822 open |
| OQ-542-9 | DISABLED `rule-007` row lifecycle | Keep DISABLED row; do not DELETE; do not rewrite V2 history | DELETE row in a later migration; or re-ACTIVE for rollback drills without owner GO | Soft |
| OQ-542-10 | Further **formula** product code after #638 | **None.** Residual work is deploy evidence, edited-row remediation, docs hygiene, and governance — not a new calculator | Add a shared library clone of the stub “for pricing”; or re-seed a Drools body | Yes — any new calculator |
| OQ-542-DONE | Linear Done vs In Review | Stay **In Review** until owner merges this brief (if desired), DoD v0.2 items pass, CPQ Decision scores blocking OQs, and OQ-542-1 deploy evidence exists | Bot/auto Done on docs attach; mark Done on #638 alone | Yes — workflow |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-542-1 — V9 deploy / DoD evidence

| Option | What the owner would be choosing |
|---|---|
| **A. Owner deploy GO + Flyway evidence (Recommended, Proposed)** | Matches Linear hygiene comment: V9 applies on next pricing-service deploy. Done is not merge-only. |
| **B. Merge equals Done** | Leaves env DB possibly still on ACTIVE unedited `rule-007` until an unrelated deploy. |
| **C. Multi-env matrix in this ticket** | Broader ops scope; still no formula change. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-542-2 — Edited / non-system `rule-007`

| Option | What the owner would be choosing |
|---|---|
| **A. Inventory + per-row remediation story (Recommended, Proposed)** | Honors V9 owner-edit guard; avoids silent disable of tenant-edited policy. |
| **B. Force-disable all uuid `rule-007`** | Breaks the V7/V8/V9 edit guard contract. |
| **C. Leave without inventory** | Unknown ACTIVE full-term bodies may remain. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-542-3 — ADR number

| Option | What the owner would be choosing |
|---|---|
| **A. Leave `none` until owner assigns (Recommended, Proposed)** | Matches Accepted ADR header. |
| **B. Assign and promote in this brief** | Exceeds docs-only residual scope; needs owner number. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-542-4 — ADR-023 citation

| Option | What the owner would be choosing |
|---|---|
| **A. Historical until dedicated edit (Recommended, Proposed)** | Matches Accepted ADR Consequences. |
| **B. Edit ADR-023 now** | Separate owner decision; not bundled here. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-542-5 — Stale prose

| Option | What the owner would be choosing |
|---|---|
| **A. Separate hygiene story (Recommended, Proposed)** | Keeps this brief residual/governance-focused. |
| **B. Rewrite in this PR** | Scope creep; still docs-only but mixes concerns. |
| **C. Leave indefinitely** | Agents/PRD keep teaching the rejected formula. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-542-6 — Future pricing-service home

| Option | What the owner would be choosing |
|---|---|
| **A. Move Accepted method only (Recommended, Proposed)** | ADR D-02 / D-04. No competing body. |
| **B. Reimplement beside `rule-007`** | Recreates RA-08. **NO-GO.** |
| **C. Forbid any future move** | Conflicts with DRG-609 design direction; still must not revive full-term. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-542-7 — DRG-550 arithmetic boundary

| Option | What the owner would be choosing |
|---|---|
| **A. Policies wrap A1; no new day math here (Recommended, Proposed)** | ADR D-04 telling DRG-550; delegated 550-F* for names/defaults. |
| **B. Revive full-term as credit/Daily** | Explicitly Rejected in Accepted ADR. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-542-8 — Audit (#822) coupling

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #822; do not adopt OQ-564-* (Recommended, Proposed)** | Audit can mirror Accepted stub fields without changing money. |
| **B. Block all amendment audit on stub move** | Over-blocks RA-30 on the live path. |
| **C. Put full-term inputs in PolicyDecision** | Documents a rejected formula. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-542-9 — DISABLED row

| Option | What the owner would be choosing |
|---|---|
| **A. Keep DISABLED; no DELETE (Recommended, Proposed)** | Matches #638 rollback notes and “history stays”. |
| **B. DELETE in a later migration** | Needs its own allowlist + deploy GO. |
| **C. Re-ACTIVE without owner GO** | Undoes retirement. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-542-10 — Further formula code

| Option | What the owner would be choosing |
|---|---|
| **A. No new calculator (Recommended, Proposed)** | Conflict closed at formula layer by A1–A7; residuals are ops/docs/governance. |
| **B. Shared helper clone now** | Second body risk (ADR Rejected). **NO-GO** without explicit move story under OQ-542-6. |
| **C. Re-seed Drools proration** | Recreates RA-08. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-542-DONE — Workflow

| Option | What the owner would be choosing |
|---|---|
| **A. Remain In Review until DoD + scored forks + deploy evidence (Recommended, Proposed)** | Matches Linear hygiene and DoD v0.2. |
| **B. Auto-Done on docs/PR attach** | Already reverted once after #638. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed.
2. CPQ Decision must score the OQ-542-* rows (or the owner fills `Owner decision: ___ (drg, time)` / explicit defer).
3. Until blocking forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work, Flyway (pricing or CAM), Drools edits, or flag flips that change amendment proration arithmetic or re-enable `rule-007`.
4. Even after forks close: A1–A10 hold; no competing formula; ramp flag stays off; no Jev enablement; CAM V1_16–V1_21 remain reserved to #805–#812; #822 / DRG-550 open OQs stay outside silent adoption.
5. Linear status for DRG-542 stays **In Review** until the owner and DoD say otherwise. This brief does not mark Done.

## Out of scope

- Any new or alternate proration formula (including full-term ratio, annual `n/365` as the billing-period stub, or scale-4 amendment money).
- Product code, CAM migrations (including reserved V1_16–V1_21), pricing V10+, dual-read, Jev, ramp enable, S4 enablement, asset-write hold bypass.
- Accepting #822 OQ-564-*, inventing DRG-550 credit/fee arithmetic, or accepting unread DRG-609 remainder wholesale.
- Deploying pricing-service or applying V9 in any environment from this PR.
- Editing ADR-023, agents, or PRD prose in this change set.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-542, DRG-550, DRG-564, DRG-603 | Linear, this pass |
| Accepted proration ADR | `adr-amendment-proration-formula.md` |
| #638 merge + V9 | GitHub PR #638; `V9__disable_unedited_system_amendment_proration.sql`; rules dir listing |
| #822 Decision brief | Open PR #822 (DRG-564) |
| Cluster A §5 / 550-F* | `cluster-a-amendment-modes-and-policy-decision-frame.md` |
| Delegated RetainContracted / NoCredit / NoFee + formula | `delegated-renewals-amendments-decisions-2026-10-17.md` |
| CAM V1_16–V1_21 reservation | Open PRs #805–#812 file lists |
| Stale prose hits | `agents/term-coterming.md`, ADR-023, phase0 catalog, `cpq-pricing-prd.md` (search) |
| GitNexus | `impact QuoteContinuationPolicy` → **UNKNOWN**; analyze truncation flagged; pre-edit `detect_changes` empty |

## Unverified in this pass

- Whether any deployed environment still has ACTIVE unedited or edited `rule-007`.
- Runtime execute behaviour against a post-V9 database (not started here).
- Byte-level identity of every stale prose mention beyond the search sample.
- AutoJev tools (not connected in this environment); no AutoJev verdict is claimed.
- MCP GitNexus `list_repos` was empty before local analyze; post-analyze CLI impact was used. Process coverage remains truncated per analyze log — **not** a clean check.
