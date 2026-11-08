# DRG-564 — Continuation pricing audit / policy trail: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** accept [`story-drg-564-policy-decision-audit-trail.md`](story-drg-564-policy-decision-audit-trail.md), does **not** reopen or supersede the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md) § Pricing and ARR commercial-decision snapshot sentence, authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a route, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / pricing audit. **Story:** [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564/continuation-pricing-decisions-are-not-audited-no-policy-trail-or) (RA-30). Linear read this pass: **In Review**. Notion: [RA-30](https://app.notion.com/p/3fbf7cb4e4df8160afadfa10cb5f8d18) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Design note (cite, do not accept):** [`story-drg-564-policy-decision-audit-trail.md`](story-drg-564-policy-decision-audit-trail.md) — Status: **Proposed** (merged via [#643](https://github.com/drgaciw/open-crm-cpq/pull/643); owner home choice still open in that note's Options A–E). This Decision brief records residual owner forks against current `main` and against [DRG-568](https://linear.app/drgamtd-workspace/issue/DRG-568) AC-568-6 / OF-568-* on draft [#807](https://github.com/drgaciw/open-crm-cpq/pull/807). **It does not adopt #807 policy.**

**Spec / frame authority (cite, do not reopen):**

| Artifact | Role |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Pricing and ARR | **Adopted baseline** for a pricing-owned append-only commercial-decision snapshot before commercial use; confirmation command distinct from the pure resolver; quote retains ids; CAM priced-write validates; preview ≠ confirmed. Explicitly: *does not accept all DRG-564/612 details* |
| [story-drg-564-policy-decision-audit-trail.md](story-drg-564-policy-decision-audit-trail.md) | Proposed field list / homes A–E / table sketch; **not accepted** |
| [story-drg-609-pricing-service-continuation-design.md](story-drg-609-pricing-service-continuation-design.md) + [drg-609-pricing-design-acceptance-record.md](drg-609-pricing-design-acceptance-record.md) | Accepted design for continuation evaluate returning decision identity; commercial-decision **store** remains DRG-612 / this trail |
| [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md) D-07 | Accepted uplift snapshot field names the trail must eventually carry |
| [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) D-02 | Accepted proration keeps amendment stub in `QuoteContinuationPolicy.plan` until a move |

**In-flight alignment (do not contradict Accepted / draft semantics; do not adopt OF-*):**

| Ticket / PR | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-568](https://linear.app/drgamtd-workspace/issue/DRG-568) [#807](https://github.com/drgaciw/open-crm-cpq/pull/807) | In Progress / draft | AC-568-6: PolicyDecision stores **inputs + policy version per generated segment**. OF-568-1..9 stay **Proposed**. `opencrm.pricing.ramp.enabled` stays **false**. This brief names residual audit forks for that AC; it does **not** adopt escalator money, compounding, or lock defaults |
| [DRG-612](https://linear.app/drgamtd-workspace/issue/DRG-612) | In Review | Owns sequencing of the commercial-decision audit mechanism; must reconcile `commercial_decision_audit`, PolicyDecision, and `PricingAuditLog` columns into **one** mechanism. Do not start without CPQ Lead sequence |
| [DRG-549](https://linear.app/drgamtd-workspace/issue/DRG-549) / [DRG-567](https://linear.app/drgamtd-workspace/issue/DRG-567) | Related | Audit fields point at the Proposed design note only after owner acceptance of that note; this brief does not grant that acceptance |
| [DRG-311](https://linear.app/drgamtd-workspace/issue/DRG-311) / [DRG-312](https://linear.app/drgamtd-workspace/issue/DRG-312) | Related | Pricing-service waterfall replay / evidence chain; not a substitute for continuation PolicyDecision |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.pricing.ramp.enabled` stays default off. No `opencrm.jev.*.enabled` flag is turned on. An implementation brief for DRG-564 / DRG-612 is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `verified by GitNexus` | Impact/query after `npx gitnexus@latest analyze --index-only` on this checkout. |

## Problem (RA-30)

Continuation rates are still computed in quote-service by `QuoteContinuationPolicy`. That path writes no per-line / per-segment PolicyDecision with policy version, frozen schedule inputs, outputs, explanation codes, and digest. `commercial_decision_audit` records only the decision to **open** a system renewal draft. `pricing_audit_log` on execute still leaves `rules_fired` and `waterfall_trace` null. Line pin columns (`policy_decision_id`, `policy_id`, `policy_version`, `pricing_trace_id`) exist on lifecycle / CAM lines, but there is still no durable PolicyDecision body those ids can point at for continuation pricing.

Without a scored Decision brief that separates **already Adopted** ownership from **remaining open forks** (including AC-568-6 segment grain and the Proposed design note's A–E residuals), an implementation story could invent a second audit table, silently widen `commercial_decision_audit`, or treat execute snapshots as the continuation trail.

Linear DRG-564 (`Linear read` 2026-11-06): High; **In Review**; attachments include Proposed design note #643 (merged). Hygiene comments: note stays Proposed; Done needs DoD + CPQ Decision; no PolicyDecision row exists on `main`.

## Already Adopted (do not re-decide)

Restated from the delegated record (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | Before commercial use, **pricing-service** durably confirms one **immutable versioned** commercial-decision snapshot per priced continuation **line** (policy/mode/version, all inputs and schedule revision, output/explanations/actor and digest) in a **pricing-owned append-only store** | Delegated § Pricing and ARR |
| A2 | Distinct **idempotent confirmation** command in pricing-service; pure resolver stays read-only / no-persistence | Same |
| A3 | Confirmation returns a **decision id** and immutable reference/digest; quote-service **retains** those identities; CAM priced-write **validates** them | Same |
| A4 | Pure **preview** output cannot be presented as a confirmed commercial price | Same |
| A5 | Decision-store DTO/API completion, audit retention, and crash/replay tests are **delivery prerequisites** | Same |
| A6 | Delegated adoption **replaces the overlapping ownership/handoff question** and **does not accept all DRG-564/612 details** | Same; opening hold on wholesale DRG-564/612 |
| A7 | `commercial_decision_audit` (V1_11) records only the decision to **open** a system renewal draft; DRG-564 owns the general continuation pricing-decision trail | Migration comment (`read`); design note |
| A8 | Holds: ramp flag off; no Jev enablement; no product code from docs PRs alone | AGENT.md / Cluster holds |

## What the tree does today (continuation audit)

`verified by reading code` at the base SHA.

1. `QuoteContinuationPolicy.plan` computes renewal / amendment / expansion periods in quote-service. It does **not** call pricing-service and does **not** insert an audit row.
2. `QuoteContinuationService.recordDecision` (and `AssetRenewalDrafts.recordDecision`) insert into `commercial_decision_audit` with `decision_kind` `SYSTEM_RENEWAL_DRAFT`, `policy_id` `QuoteContinuationPolicy`, `mode` `RENEWAL_EXIT_RATE`. Inputs include observed revision, exit qty/unit, and `upliftPercent` `"0"`. Columns have **no** policy version, **no** per-line decision id, **no** pricing-service trace id, **no** digest. `author` / preview paths do not insert.
3. `PricingExecutionService.persistAuditLog` writes `input_snapshot` / `output_snapshot` on execute only. It does **not** set `rulesFired` or `waterfallTrace`. Simulate does not insert.
4. No Java type `PolicyDecision` / `record PolicyDecision` under `backend/`. No `policy_decisions` table. Pin columns exist on `lifecycle_change_lines` (`V1_14`) and `amendment_document_lines` (`V1_7`) without a continuation body store.
5. Quote-service Flyway tip is `V1_16__ramp_escalator_compounding.sql` (so a would-be quote migration is **not** V1_13). Pricing tip is `V9__disable_unedited_system_amendment_proration.sql` (next integer **V10**, would-be only). CAM tip is past `V1_15`.

### GitNexus (this checkout)

| Symbol | Upstream risk | Notes |
|---|---|---|
| `QuoteContinuationPolicy` | **UNKNOWN** | Empty caller set from the graph. Text search shows callers in `QuoteContinuationService`, `AssetRenewalDrafts`, `QuoteService`, controllers, and tests. UNKNOWN is unresolved, not low. `verified by GitNexus` + text search |
| `QuoteContinuationService.recordDecision` | LOW | Direct: draftRenewal path; process `onRenewalEligible`. Docs-only: no symbol edit |
| `PricingAuditLog` | **UNKNOWN** | Empty graph callers; text search shows `persistAuditLog` builder. UNKNOWN is unresolved |

No production symbol is edited in this docs-only draft. A later implementation must re-run impact before changing `QuoteContinuationPolicy.plan`, `recordDecision`, or `persistAuditLog`.

## Relation to DRG-568 AC-568-6 / OF forks (#807)

[#807](https://github.com/drgaciw/open-crm-cpq/pull/807) draft encodes AC-568-6: each **generated renewal segment**'s PolicyDecision stores generator inputs (period-1 unit, escalator mode/amount/compounding/everyMonths, lock bounds when present) and the **policy version** used. Fixture: `ac-568-6-policy-decision-audit.json` on that branch cites this story's design note and keeps status `proposed`. OF-568-1..9 stay Proposed (ADR wording, scale-4 vs scale-2, compounding default, post-lock percent, unequal segments, named-seat tier, month-boundary locks, retention marker ≠ lock, product enablement).

**This Decision brief:**

- Treats AC-568-6 as a **consumer requirement** on the audit grain (segment-level inputs + policy version), not as acceptance of escalator product policy.
- Does **not** fill any OF-568-* slot.
- Leaves residual forks below for how DRG-564 / DRG-612 would satisfy that AC once home and body are scored.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells in this table.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-564-1 | Residual home / schema vs design-note A–E after A1 | Keep A1 (pricing-owned store); treat design-note Option A (`quote.policy_decisions`) as **superseded for ownership** but keep field list as Proposed input to the pricing store | Reopen Option A (contradicts A1); Option B widen `commercial_decision_audit`; Option C treat execute `rules_fired`/`waterfall_trace` as the body; Option E put body in `amendment_document_events` | Yes — any writer |
| OQ-564-2 | Grain: line vs segment (AC-568-6) | One immutable snapshot **per priced renewal segment** when a multi-period schedule is confirmed; one per line when the schedule is a single flat period | Always one per quote line only (fails AC-568-6); always one per execute call | Yes — AC-568-6 |
| OQ-564-3 | Confirmation vs pure resolve (A2) | Confirmation is a separate idempotent command; resolve/preview never insert | Allow resolve to insert; allow quote-service to insert the pricing body | Yes — A4 / mid-term V8 |
| OQ-564-4 | Trace id while quote-service still computes stubs | Allow `pricing_trace_id` null on amendment stub rows with `policy_id` `QuoteContinuationPolicy` until pricing-service evaluation returns a trace; document line pins stay empty until then | Insert no amendment snapshot until the stub moves; invent a quote-local fake trace | Soft for renewals; hard for amendment stub audit |
| OQ-564-5 | Pre-uplift / scale-2 silent-zero rows | If any interim row is written from today's branch: `policy_version` `continuation-policy-scale2`, mode token distinct from Accepted `UpliftPct` | Insert no renewal price snapshot until DRG-567 merge is live | Soft until uplift lands |
| OQ-564-6 | Inline JSON vs digest-only + evidence store | Store canonical `inputs_json` / `outputs_json` with digests beside them in the pricing-owned row (DRG-311/312 evidence store remains separate) | Digest-only + external evidence blob | Soft for first writer |
| OQ-564-7 | `commercial_decision_audit` relationship | Keep draft-open grain (A7); optional later nullable link from draft row → line/segment decision ids | Widen V1_11 into per-line trail (design Option B) | Soft |
| OQ-564-8 | `pricing_audit_log.rules_fired` / `waterfall_trace` | Leave unused for continuation PolicyDecision; do not treat execute snapshots as the continuation trail | Populate those columns as the sole continuation body (design Option C) | Soft if OQ-564-1 stays A1 |
| OQ-564-9 | AC-568-6 input key set / policy version token | Require at least the fixture keys (`period1Unit`, escalator.*, lock.from/to when present) + a non-blank `policyVersion`; example token `renewal-escalator-v1` is illustrative only | Defer key list until OF-568-* close; invent a different token catalog now | Yes — AC-568-6 once ramp path is authorized |
| OQ-564-10 | Flyway numbering | Would-be only: pricing next integer **V10** for a pricing-owned table; quote next integer **V1_17** only if a quote-side link column is later chosen. Number settled when the migration PR exists | Reserve numbers in this brief | Soft (docs) |
| OQ-564-11 | Sequencing owner | DRG-612 creates the store after CPQ Lead sequence + closed OQ-564-*; DRG-564 stays the design/Decision story | Start a writer under DRG-564 without DRG-612; start without Lead sequence | Yes — DRG-612 text |
| OQ-564-12 | Align with OF-568-* without adopting | Keep OF-568-1..9 Proposed; AC-568-6 audit writer must not assume adopted escalator money/lock defaults while ramp flag is off | Require all OF-568-* closed before any PolicyDecision writer | Soft while flag off; hard for multi-period priced renewals |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-564-1 — Residual home after delegated A1

| Option | What the owner would be choosing |
|---|---|
| **A. Pricing-owned store; design-note Option A superseded for ownership (Recommended, Proposed)** | Matches A1. Field list / digests / append-only semantics from the Proposed design note remain inputs. Schema module is pricing, not quote. |
| **B. Reopen quote `policy_decisions` (design Option A)** | Contradicts A1 unless the owner supersedes the delegated sentence. |
| **C. Widen `commercial_decision_audit` (design Option B)** | Mixes draft-open grain with per-line/segment price body. Conflicts with A7 comment intent. |
| **D. Populate execute `rules_fired` / `waterfall_trace` only (design Option C)** | Wrong grain; plan/preview/author/draftRenewal still write nothing; no policy version / observed revision columns. |
| **E. Body in `amendment_document_events` (design Option E)** | Writer requires an AmendmentDocument; renewal drafts have none. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-564-2 — Grain (line vs segment) for AC-568-6

| Option | What the owner would be choosing |
|---|---|
| **A. Per segment when multi-period; per line when flat (Recommended, Proposed)** | Satisfies AC-568-6 without forcing three rows on today's flat 12-month renewal. |
| **B. Always one per quote line** | Fails AC-568-6 for Y1/Y2/Y3 audits. |
| **C. Always one per pricing execute** | Continues today's execute grain; not a continuation PolicyDecision. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-564-3 — Confirmation vs pure resolve

| Option | What the owner would be choosing |
|---|---|
| **A. Separate confirmation command (Recommended, Proposed)** | Restates A2/A4. Preview/simulate/resolve do not insert. |
| **B. Resolve inserts** | Preview becomes durable commercial evidence. |
| **C. Quote-service inserts the pricing body** | Conflicts with A1 writer ownership. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-564-4 — Trace id on quote-computed amendment stubs

| Option | What the owner would be choosing |
|---|---|
| **A. Nullable trace until pricing-service returns one (Recommended, Proposed)** | Matches design-note owner decision 2 recommendation; keeps V8 "not null when priced" on document lines by leaving pins empty until a real trace exists. |
| **B. No amendment snapshot until stub moves** | Leaves the RA-30 gap open on the amendment path. |
| **C. Mint a quote-local fake trace** | Undermines CAM validation (A3). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-564-5 — Pre-Accepted-uplift renewal rows

| Option | What the owner would be choosing |
|---|---|
| **A. Distinct version token if interim rows exist (Recommended, Proposed)** | `continuation-policy-scale2` (or equivalent) so readers do not confuse silent-zero scale-2 with Accepted uplift. |
| **B. No renewal price snapshot until DRG-567** | Cleaner history; longer RA-30 gap on renewals. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-564-6 — Snapshot body storage

| Option | What the owner would be choosing |
|---|---|
| **A. Inline canonical JSON + digests (Recommended, Proposed)** | Matches design-note recommendation; DRG-311/312 remain the evidence-query path. |
| **B. Digest-only + external store** | Needs an evidence store design this brief does not open. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-564-7 — Draft-open table

| Option | What the owner would be choosing |
|---|---|
| **A. Keep separate; optional later link (Recommended, Proposed)** | A7. One mechanism for **price** snapshots still lives in the pricing-owned store (OQ-564-1 A). |
| **B. Widen into the price trail** | Design Option B. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-564-8 — Execute audit columns

| Option | What the owner would be choosing |
|---|---|
| **A. Leave unused for continuation PolicyDecision (Recommended, Proposed)** | Execute log stays the waterfall execute record; continuation trail is the pricing-owned confirmation row. |
| **B. Make those columns the continuation trail** | Design Option C; rejects A1 grain. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-564-9 — AC-568-6 fields (without adopting OF-568-*)

| Option | What the owner would be choosing |
|---|---|
| **A. Minimum fixture key set + non-blank policyVersion (Recommended, Proposed)** | Aligns with #807 AC-568-6 fixture; example `renewal-escalator-v1` is not a catalog decision. OF-568-* stay Proposed. |
| **B. Defer keys until all OF-568-* close** | Blocks encoding the audit contract for fixtures. |
| **C. Adopt OF-568 money/lock defaults here** | Out of scope; would adopt #807 policy. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-564-10 — Flyway numbering

| Option | What the owner would be choosing |
|---|---|
| **A. Would-be only; migration PR settles (Recommended, Proposed)** | Pricing **V10** / quote **V1_17** are next integers on this `main`, not reserved. |
| **B. Reserve in this brief** | Conflicts with "numbering settled when migration PR exists." |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-564-11 — Sequencing

| Option | What the owner would be choosing |
|---|---|
| **A. DRG-612 after Lead sequence + closed forks (Recommended, Proposed)** | Matches DRG-612 issue text. |
| **B. Writer under DRG-564 without 612** | Splits the "one mechanism" mandate. |
| **C. Start without Lead sequence** | Explicitly refused by DRG-612. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-564-12 — OF-568 coupling

| Option | What the owner would be choosing |
|---|---|
| **A. No adoption of OF-568-* in this slice (Recommended, Proposed)** | Audit forks can close independently of escalator money forks while ramp flag is off. |
| **B. Block all PolicyDecision work on OF-568 closure** | Over-blocks flat renewals / amendment stub audit. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed.
2. CPQ Decision must score the OQ-564-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work, Flyway, Drools edits, or flag flips for DRG-564 / DRG-612 continuation PolicyDecision persistence.
4. Even after forks close: A1–A8 hold; ramp flag stays off; no Jev enablement; design note remains non-accepted until the owner separately accepts it; OF-568-* stay Proposed; delegated "not all DRG-564/612 details" remainder stays outside any silent adoption.
5. Linear status for DRG-564 stays **In Review** until the owner merges and DoD says otherwise. This brief does not mark Done.

## Out of scope

- Creating any migration or Java/TS type named `PolicyDecision`.
- Editing `QuoteContinuationPolicy`, `recordDecision`, `persistAuditLog`, CAM event hasher/writer, or lifecycle pin enforcement.
- Accepting the design note, the renewal policy proposal, or #807 OF-568-*.
- Enabling `opencrm.pricing.ramp.enabled` or any `opencrm.jev.*.enabled` flag.
- DRG-311/312 evidence-store design beyond naming the boundary.
- Reopening Accepted uplift / proration formulas.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-564, DRG-568, DRG-612, DRG-609 | Linear, this pass |
| Design note + #643 | `story-drg-564-policy-decision-audit-trail.md`; GitHub PR #643 |
| #807 AC-568-6 / OF-568-* / fixture | Draft PR #807; `story-drg-568-renewal-escalators-and-price-locks.md`; `ac-568-6-policy-decision-audit.json` on that branch |
| Delegated commercial-decision snapshot | `delegated-renewals-amendments-decisions-2026-10-17.md` § Pricing and ARR |
| Draft-open audit | `V1_11__processed_events_inbox.sql`; `QuoteContinuationService.recordDecision` |
| Execute audit columns | `PricingAuditLog.java`; `PricingExecutionService.persistAuditLog` |
| Pin columns without body store | `V1_14__lifecycle_changes.sql`; CAM `V1_7__contract_documents.sql` |
| Migration tips | quote `V1_16`; pricing `V9`; CAM `V1_15` |
| GitNexus | impact on `QuoteContinuationPolicy` (UNKNOWN), `recordDecision` (LOW), `PricingAuditLog` (UNKNOWN) |

## Unverified in this pass

- Any runtime insert, digest value, or trigger behaviour.
- Byte size of a frozen `SubscriptionScheduleView` in `inputs_json`.
- Whether a future pricing confirmation command name differs from the DRG-609 evaluate sketch.
- AutoJev tools (not connected in this environment); no AutoJev verdict is claimed.
