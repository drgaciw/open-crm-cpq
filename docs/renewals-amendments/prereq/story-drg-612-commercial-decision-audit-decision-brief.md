# DRG-612 — Commercial-decision audit mechanism for pricing rule outcomes: Decision brief (owner forks)

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted commercial-decision snapshot contracts in the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md) § Pricing and ARR, does **not** accept [`story-drg-564-policy-decision-audit-trail.md`](story-drg-564-policy-decision-audit-trail.md), does **not** adopt open **OQ-564-*** forks from the DRG-564 Decision brief draft [#822](https://github.com/drgaciw/open-crm-cpq/pull/822), does **not** authorize an implementation brief, authorize product code, open a Flyway migration, claim a CAM version in the reserved **V1_16–V1_21** band, flip a flag, register a route, enable ramp / Jev / dual-read / S4, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / commercial-decision audit. **Story:** [DRG-612](https://linear.app/drgamtd-workspace/issue/DRG-612/story-commercial-decision-audit-mechanism-for-pricing-rule-outcomes) (RA-78). Linear read this pass: **In Review**. Notion mirror: [RA-78](https://app.notion.com/p/3fbf7cb4e4df81eeb1b1e985778eda35) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / authority (cite, do not reopen Adopted cells; do not adopt #822 OQs):**

| Artifact | Role |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Pricing and ARR | **Adopted baseline** for a pricing-owned append-only commercial-decision snapshot before commercial use; distinct confirmation command; quote retains ids; CAM priced-write validates; preview ≠ confirmed. Explicitly: *does not accept all DRG-564/612 details* |
| Wave-2 DRG-564 Decision brief [#822](https://github.com/drgaciw/open-crm-cpq/pull/822) | Cite-only residual PolicyDecision / continuation audit forks (**OQ-564-1..12**). This brief **does not score or close** those rows |
| [story-drg-564-policy-decision-audit-trail.md](story-drg-564-policy-decision-audit-trail.md) ([#643](https://github.com/drgaciw/open-crm-cpq/pull/643), merged) | Proposed field list / homes A–E / table sketch; **not accepted**. Design note for DRG-564; not a writer authorization |
| [story-drg-609-pricing-service-continuation-design.md](story-drg-609-pricing-service-continuation-design.md) + [drg-609-pricing-design-acceptance-record.md](drg-609-pricing-design-acceptance-record.md) | Accepted design for continuation evaluate returning decision identity; commercial-decision **store / one-mechanism** delivery remains this story |
| [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md) D-07 | Accepted uplift snapshot field names a confirmed trail must eventually carry — not a store home |
| Quote `V1_11__processed_events_inbox.sql` | Landed `commercial_decision_audit` for **draft-open** only; comment assigns the general continuation trail to DRG-564 |

**Related (cite; do not reopen or start product work):**

| Ticket / PR | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564) / [#822](https://github.com/drgaciw/open-crm-cpq/pull/822) | In Review / draft | Open **OQ-564-***. Cite only; do **not** adopt |
| [DRG-541](https://linear.app/drgamtd-workspace/issue/DRG-541) / [#614](https://github.com/drgaciw/open-crm-cpq/pull/614) | Related | Uplift wiring marked N/A for audit — rule outcome still lacks a commercial-decision body |
| [DRG-544](https://linear.app/drgamtd-workspace/issue/DRG-544) / [#615](https://github.com/drgaciw/open-crm-cpq/pull/615) | Related / merged path | Introduced append-only `commercial_decision_audit`; must be reconciled into **one** mechanism, not a second price trail |
| [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) | Related | Evaluate may return decision identity; store remains DRG-612 after sequence |
| [DRG-311](https://linear.app/drgamtd-workspace/issue/DRG-311) / [DRG-312](https://linear.app/drgamtd-workspace/issue/DRG-312) | Related | Waterfall replay / evidence chain — not a substitute for continuation confirmation |
| CAM drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) | Flyway **V1_16–V1_21 reserved** | This brief writes **no** migration and claims **no** CAM version |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.pricing.ramp.enabled` stays default off. No `opencrm.jev.*.enabled` flag is turned on. Dual-read / S4 enablement / asset-write holds stay in force. An implementation brief for DRG-612 is **blocked** until CPQ Lead sequences the work **and** CPQ Decision scores the open **OQ-612-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-612-* row stays open. Closing #822 OQ-564-* is a separate owner act; this brief does not close them by citation.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read / searched in the tree at the base SHA. Not executed. |
| `verified by GitNexus` | Impact/query after `npx gitnexus@latest analyze --index-only` on this checkout. Process walk reported **truncated** entry-point coverage — not a clean graph. |

## Problem (RA-78)

The repo still has **no** single commercial-decision audit mechanism for pricing rule / continuation outcomes:

* PolicyDecision remains a Proposed design note (DRG-564 / [#643](https://github.com/drgaciw/open-crm-cpq/pull/643)); no Java type and no body store on `main`.
* `pricing_audit_log.rules_fired` and `waterfall_trace` exist and stay unused on the production execute insert path.
* DRG-541 uplift wiring ([#614](https://github.com/drgaciw/open-crm-cpq/pull/614)) is marked N/A for audit, so the rule outcome is still not audited as a commercial decision.
* Quote-service already has append-only `commercial_decision_audit` ([#615](https://github.com/drgaciw/open-crm-cpq/pull/615) / `V1_11`) for the decision to **open** a system renewal draft. Without a scored residual brief, a later story could widen that table into a second price trail, treat execute columns as the continuation body, or invent a third store beside the Adopted pricing-owned snapshot.

DRG-612's issue text requires reconciling that draft-open table, the PolicyDecision design note, and the PricingAuditLog columns so there is **one** audit mechanism rather than two (or three). Sequenced work must not start without a **CPQ Lead sequence**. Design / Decision first; **no code** until sequenced and residual forks close.

Linear DRG-612 (`Linear read` 2026-11-06): **In Review**; related to DRG-564, DRG-541; attachments include Proposed design note #643. Wave-2 residual Decision brief only; product/schema/flag **NO-GO**.

## Already Adopted (do not re-decide)

Restated from the delegated record § Pricing and ARR and landed migration comments (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | Before commercial use, **pricing-service** durably confirms one **immutable versioned** commercial-decision snapshot per priced continuation **line** (policy/mode/version, all inputs and schedule revision, output/explanations/actor and digest) in a **pricing-owned append-only store** | Delegated § Pricing and ARR |
| A2 | Distinct **idempotent confirmation** command in pricing-service; pure resolver stays read-only / no-persistence | Same |
| A3 | Confirmation returns a **decision id** and immutable reference/digest; quote-service **retains** those identities; CAM priced-write **validates** them | Same |
| A4 | Pure **preview** output cannot be presented as a confirmed commercial price | Same |
| A5 | Decision-store DTO/API completion, audit retention, and crash/replay tests are **delivery prerequisites** | Same |
| A6 | Delegated adoption **replaces the overlapping ownership/handoff question** and **does not accept all DRG-564/612 details** | Same; opening hold on wholesale DRG-564/612 |
| A7 | `commercial_decision_audit` (quote `V1_11`) records only the decision to **open** a system renewal draft; the general continuation pricing-decision trail is **not** that table's job | Migration comment (`read`); design note |
| A8 | Lifecycle change audit uses its own path (`lifecycle_change_audit`), not `commercial_decision_audit` | Code comments / migration guard tests (`read`) |
| A9 | Holds: ramp flag off; no Jev enablement; no product code / schema / flag from docs PRs alone; CAM **V1_16–V1_21** claimed by other drafts — **not available** here | AGENT.md; #805–#812 |

## What the tree does today (audit surfaces)

`verified by reading code` at the base SHA.

1. `QuoteContinuationPolicy.plan` computes renewal / amendment / expansion periods in quote-service. It does **not** call pricing-service and does **not** insert a commercial-decision price body.
2. `QuoteContinuationService.recordDecision` (and `AssetRenewalDrafts.recordDecision`) insert into `commercial_decision_audit` with `decision_kind` `SYSTEM_RENEWAL_DRAFT`, `policy_id` `QuoteContinuationPolicy`, `mode` `RENEWAL_EXIT_RATE`. Columns have **no** policy version, **no** per-line decision id, **no** pricing-service trace id, **no** digest. Preview / author paths do not insert.
3. `PricingExecutionService.persistAuditLog` writes `input_snapshot` / `output_snapshot` on **execute** only. It does **not** set `rulesFired` or `waterfallTrace`. Simulate does not insert.
4. No Java type `PolicyDecision` / `record PolicyDecision` under `backend/`. No `policy_decisions` table. Pin columns exist on lifecycle / CAM lines without a continuation body store those ids can point at.
5. Migration tips on this `main`: quote `V1_16__ramp_escalator_compounding.sql`; pricing `V9__disable_unedited_system_amendment_proration.sql`; CAM `V1_15__document_apply_replay_evidence.sql`. Draft CAM PRs [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) reserve **V1_16–V1_21**. **This brief claims no migration number.**

### GitNexus (this checkout)

Repository: `open-crm-cpq` (`/agent/repos/open-crm-cpq`). Index rebuilt this pass via `npx gitnexus@latest analyze --index-only` (process walk **truncated** — not a clean graph). Docs-only: no production symbol edited. A later implementation must re-run impact before changing writers.

| Symbol | Upstream risk | Notes |
|---|---|---|
| `PricingAuditLog` | **UNKNOWN** | Empty caller set from the graph. Text search shows `persistAuditLog` builder. UNKNOWN is unresolved, not low. `verified by GitNexus` + text search |
| `persistAuditLog` | **HIGH** | Direct execute path; processes `execute` / `simulate` / `simulateBulk`. **Do not edit** from this docs PR. Warn: any future writer change is HIGH blast |
| `QuoteContinuationService` | LOW | Direct: renewal-eligible consumer / `QuoteService` setter. Docs-only: no symbol edit |
| Docs-only path for this PR | N/A product blast | No Java/TS/SQL edited. Pre-commit `detect_changes` must still be reported; `partial` / `truncated` / `UNKNOWN` is **not** a clean check |

## Relation to DRG-564 / #822 (cite only)

[#822](https://github.com/drgaciw/open-crm-cpq/pull/822) records residual **OQ-564-1..12** (home after A1, segment grain, confirmation vs resolve, stub traces, pre-uplift tokens, JSON vs digest, draft-open relationship, execute columns, AC-568-6 keys, Flyway would-be tips, sequencing, OF-568 coupling). Those rows stay **Proposed** on that draft.

**This Decision brief:**

- Treats #822 as the **PolicyDecision / continuation trail** residual record.
- Does **not** fill, close, or restate as Adopted any OQ-564-* recommendation.
- Keeps DRG-612 residual forks on **mechanism reconciliation, sequencing, DoD, and migration NO-GO**, assuming A1–A9 and leaving OQ-564-* for their own owner scoring.
- Does **not** claim that citing A1 answers OQ-564-1; ownership is Adopted; delivery details in #822 remain open.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed` under id **OQ-612-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No new Adopted cells.** Product / schema / flag stay **NO-GO** until blocking rows close (or the owner defers them with words and a time). Do **not** adopt open OQs from [#822](https://github.com/drgaciw/open-crm-cpq/pull/822) by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-612-1 | What counts as the **one** commercial-decision audit mechanism | **One pricing-owned confirmation store** (A1) is the commercial price/rule-outcome mechanism; draft-open `commercial_decision_audit` and execute `pricing_audit_log` stay **adjacent grains**, not parallel price trails | Treat draft-open + execute columns as co-equal "mechanisms"; invent a third store; widen V1_11 into the price body | Yes — any writer / schema |
| OQ-612-2 | Coupling to #822 / OQ-564-* without adopting | Cite #822; **wait to implement** until CPQ Decision scores blocking OQ-564-* **or** the owner explicitly defers named 564 forks with words and a time. Closing this brief alone does **not** close OQ-564-* | Silently treat #822 recommendations as Adopted; start writers while OQ-564-* open without deferral | Yes — implementation brief |
| OQ-612-3 | CPQ Lead **sequence** gate | No product work until Lead publishes a sequence that places DRG-612 after design/Decision readiness (and relative to DRG-564 / 609 / 541 as Lead names) | Start under DRG-564 without 612; start without Lead sequence | Yes — issue text |
| OQ-612-4 | Who creates the store | **DRG-612** is the story that would create the pricing-owned store **after** sequence + closed/deferred forks; DRG-564 stays design/Decision | Writer under DRG-564 only; writer under DRG-541 uplift | Yes — ownership of migration PR |
| OQ-612-5 | `commercial_decision_audit` reconciliation | Keep **draft-open grain** (A7); optional later nullable link from draft row → confirmation decision ids; do **not** widen into per-line PolicyDecision body in this story | Absorb price trail into V1_11; drop draft-open table | Soft for docs; hard for any V1_11 alter |
| OQ-612-6 | `rules_fired` / `waterfall_trace` | Leave **unused for continuation confirmation**; execute log stays the waterfall execute record; do not treat those columns as the commercial-decision body | Populate as sole continuation trail; dual-write confirmation + those columns as the "one" body | Soft if OQ-612-1 stays A1; hard if chosen as sole trail |
| OQ-612-7 | Confirmation command surface (delivery of A2) | Separate idempotent confirmation API/DTO with crash/replay tests (A5); resolve/preview/simulate never insert | Allow resolve to insert; allow quote-service to insert the pricing body | Yes — A2/A4 |
| OQ-612-8 | Flyway / CAM numbering | Would-be only; **claim no number in this brief**. CAM **never** V1_16–V1_21. Pricing/quote next free settled only when an **authorized** migration PR exists | Reserve or ship V1_16–V1_21 here; invent quote V1_17 / pricing V10 as reserved | Yes — any migration |
| OQ-612-9 | DRG-541 uplift audit N/A | Keep uplift rule-outcome audit **out of scope** until sequenced with this mechanism; do not invent a parallel uplift audit table in 541 | Backfill uplift outcomes into execute columns only; audit uplift under draft-open table | Soft until uplift commercial use; hard for uplift confirm AC |
| OQ-612-10 | Retention / append-only enforcement | Append-only store with stated retention + crash/replay DoD before commercial use (A5); UPDATE/DELETE refused | Mutable JSON upsert; TTL that drops commercial evidence while documents live | Yes — commercial confirm DoD |
| OQ-612-11 | Implementation brief gate | **NO-GO** on product/schema/flag until OQ-612-* blocking rows + Lead sequence + (OQ-612-2) 564 readiness are satisfied | Enable writers when this file merges; treat docs PR as authorization | Yes — any code PR |
| OQ-612-12 | Evidence / waterfall boundary (311/312) | DRG-311/312 remain the evidence-query path; confirmation row may hold digests + inline JSON without absorbing the full waterfall replay store | Merge evidence store into confirmation table now; require 311/312 Done before any confirmation design | Soft for first confirmation slice |
| OQ-612-MKT | Vendor / research on issue | Cite as research only; no vendor parity commitment for audit schema | Encode vendor audit field names as wire format | Soft |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-612-1 — One mechanism

| Option | What the owner would be choosing |
|---|---|
| **A. Pricing-owned confirmation is the commercial mechanism; draft-open + execute stay adjacent (Recommended, Proposed)** | Matches A1 + A7. "One mechanism" means one **price/rule-outcome** trail, not collapsing every audit table in the repo. |
| **B. Co-equal mechanisms** | Leaves DRG-612's reconcile mandate unmet; invites dual writers. |
| **C. Third store** | The split the issue says to avoid. |
| **D. Widen V1_11 into the price body** | Mixes draft-open grain with per-line confirmation; conflicts with A7 comment intent. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-612-2 — Coupling to #822 without adopting OQ-564-*

| Option | What the owner would be choosing |
|---|---|
| **A. Cite only; block impl until 564 forks scored or explicitly deferred (Recommended, Proposed)** | Prevents silent adoption of #822 recommendations while still sequencing 612 after Decision readiness. |
| **B. Treat #822 Recommended rows as Adopted** | Out of scope; would adopt open OQs from another draft. **NO-GO**. |
| **C. Implement 612 while ignoring #822** | Risks a second trail or wrong grain vs AC-568-6 / home residuals. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-612-3 — CPQ Lead sequence

| Option | What the owner would be choosing |
|---|---|
| **A. No product work without Lead sequence (Recommended, Proposed)** | Matches issue text. |
| **B. Start under DRG-564 without 612** | Splits the "one mechanism" mandate. |
| **C. Start without Lead sequence** | Explicitly refused by DRG-612. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-612-4 — Story that creates the store

| Option | What the owner would be choosing |
|---|---|
| **A. DRG-612 after sequence + closed/deferred forks (Recommended, Proposed)** | Matches issue ownership of the mechanism. |
| **B. Writer only under DRG-564** | Leaves 612 without a delivery home. |
| **C. Writer under DRG-541** | Uplift path is N/A for audit today; wrong story. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-612-5 — Draft-open table

| Option | What the owner would be choosing |
|---|---|
| **A. Keep separate; optional later link (Recommended, Proposed)** | A7. Reconciliation ≠ absorption. |
| **B. Widen into price trail** | Design-note Option B territory; not adopted here. |
| **C. Drop draft-open table** | Product/schema edit; out of this docs brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-612-6 — Execute audit columns

| Option | What the owner would be choosing |
|---|---|
| **A. Leave unused for continuation confirmation (Recommended, Proposed)** | Execute log stays execute grain; confirmation is A1. |
| **B. Make those columns the continuation trail** | Rejects A1 grain; plan/preview/draft still write nothing useful for PolicyDecision. |
| **C. Dual-write as the "one" body** | Two writers for one decision; fails reconcile intent. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-612-7 — Confirmation command

| Option | What the owner would be choosing |
|---|---|
| **A. Separate idempotent confirmation (Recommended, Proposed)** | Restates A2/A4/A5. |
| **B. Resolve inserts** | Preview becomes durable commercial evidence. |
| **C. Quote inserts the pricing body** | Conflicts with A1 writer ownership. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-612-8 — Flyway numbering

| Option | What the owner would be choosing |
|---|---|
| **A. Claim no number; never CAM V1_16–V1_21; migration PR settles (Recommended, Proposed)** | Hard rule for this Wave-2 docs lane. |
| **B. Reserve V1_16–V1_21 here** | Conflicts with #805–#812 reservation. **NO-GO**. |
| **C. Publish would-be pricing V10 / quote V1_17 as reserved** | Numbering settled only when migration PR exists; do not reserve in this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-612-9 — DRG-541 uplift

| Option | What the owner would be choosing |
|---|---|
| **A. Out of scope until sequenced with this mechanism (Recommended, Proposed)** | Keeps N/A audit honest; no parallel uplift audit. |
| **B. Audit uplift only via execute columns** | Same grain problem as OQ-612-6 B. |
| **C. Write uplift into draft-open table** | Wrong grain (A7). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-612-10 — Retention / append-only

| Option | What the owner would be choosing |
|---|---|
| **A. Append-only + retention + crash/replay DoD before commercial use (Recommended, Proposed)** | A5. |
| **B. Mutable upsert** | Breaks immutable snapshot contract (A1). |
| **C. Short TTL while documents live** | Loses commercial evidence. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-612-11 — Implementation brief gate

| Option | What the owner would be choosing |
|---|---|
| **A. Product/schema/flag NO-GO until forks + Lead sequence + 564 readiness (Recommended, Proposed)** | Docs PR is not authorization. |
| **B. Merge of this brief authorizes writers** | Explicitly refused. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-612-12 — Evidence store boundary

| Option | What the owner would be choosing |
|---|---|
| **A. Keep 311/312 separate; confirmation may hold digests + inline JSON (Recommended, Proposed)** | Avoids blocking first confirmation design on full waterfall replay. |
| **B. Merge evidence store into confirmation now** | Widens scope beyond DRG-612. |
| **C. Require 311/312 Done first** | Over-blocks confirmation design. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed.
2. CPQ Decision must score the OQ-612-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. CPQ Lead must publish a sequence before product work starts (OQ-612-3).
4. Until those gates pass, **do not** write or accept an implementation brief that authorizes Java/TS product work, Flyway, Drools edits, or flag flips for the commercial-decision audit mechanism.
5. Even after forks close: A1–A9 hold; ramp flag stays off; no Jev enablement; design note #643 remains non-accepted until the owner separately accepts it; **OQ-564-* stay Proposed** unless scored on #822; CAM **V1_16–V1_21** stay reserved elsewhere; this brief claims **no** migration number.
6. Linear status for DRG-612 stays **In Review** until the owner merges and DoD says otherwise. This brief does not mark Done.

## Out of scope

- Creating any migration, Java/TS type named `PolicyDecision`, or pricing confirmation route.
- Editing `QuoteContinuationPolicy`, `recordDecision`, `persistAuditLog`, CAM event hasher/writer, or lifecycle pin enforcement.
- Accepting the DRG-564 design note or adopting #822 OQ-564-*.
- Claiming or writing CAM Flyway **V1_16–V1_21**.
- Enabling `opencrm.pricing.ramp.enabled` or any `opencrm.jev.*.enabled` flag.
- Reopening Adopted commercial-decision snapshot contracts (A1–A6).
- DRG-311/312 evidence-store design beyond naming the boundary.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-612, DRG-564, DRG-541 | Linear, this pass |
| Delegated commercial-decision snapshot | `delegated-renewals-amendments-decisions-2026-10-17.md` § Pricing and ARR |
| #822 residual OQ-564-* | Draft PR #822 (cite only; not adopted) |
| Design note + #643 | `story-drg-564-policy-decision-audit-trail.md`; GitHub PR #643 |
| Draft-open audit | `V1_11__processed_events_inbox.sql`; `QuoteContinuationService.recordDecision` |
| Execute audit columns | `PricingAuditLog.java`; `PricingExecutionService.persistAuditLog` |
| Migration tips | quote `V1_16`; pricing `V9`; CAM `V1_15`; reserved CAM V1_16–V1_21 via #805–#812 |
| GitNexus | impact on `PricingAuditLog` (UNKNOWN), `persistAuditLog` (HIGH), `QuoteContinuationService` (LOW); process index truncated |

## Unverified in this pass

- Any runtime insert, digest value, or trigger behaviour.
- Byte size of a frozen schedule / rule-outcome payload in a confirmation row.
- Whether a future confirmation command name differs from the DRG-609 evaluate sketch.
- AutoJev tools (not connected in this environment); no AutoJev verdict is claimed.
- A non-truncated GitNexus process walk; treat process membership as incomplete.
