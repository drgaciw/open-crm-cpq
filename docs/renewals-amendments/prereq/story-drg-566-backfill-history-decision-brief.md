# DRG-566 — Backfill amendment history for existing subscriptions: residual Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-566](https://linear.app/drgamtd-workspace/issue/DRG-566/backfill-amendment-history-for-existing-subscriptions) (RA-32).

**Status:** Proposed residual Decision brief for CPQ Decision scoring. This file **cites** the merged design note [#665](https://github.com/drgaciw/open-crm-cpq/pull/665) / [story-drg-566-backfill-history-design-note.md](story-drg-566-backfill-history-design-note.md) **without treating its F1–F12 defaults as Adopted**, and **does not reopen** Adopted **OQ7** (header-only first slice; no synthetic history). It inventories **remaining owner forks as `OQ-566-*`**. **It authorizes no production code path, no schema change, no Flyway migration (including inventing a CAM version that collides with reserved draft slots V1_16–V1_21 on [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), no feature flag, no dual-read flip, no Jev enablement, no ramp enable, no S3 / S4 / S6 product start, no S7 `contract_header_backfill_apply` run or hold bypass, no asset-write hold bypass, no history dry-run against real tenants, and no deploy.** Product / schema / flag work that synthesizes ORIGINAL / BACKFILLED documents or widens the S7 header job is **NO-GO** until the owner closes the residual OQ-566-\* forks below (or explicitly defers a named fork with words and a time). Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Linear status when read:** In Review (not Done). This brief leaves that status unchanged.

## Alignment (cite; do not reopen closed cells)

| Artifact | Relation to this brief |
|---|---|
| [DRG-566](https://linear.app/drgamtd-workspace/issue/DRG-566) issue body (RA-32) | Problem: existing subscriptions lack contract / amendment documents; history lives in non-NEW `applied_quote_acceptances` and some `LEGACY_AGGREGATE` rate periods (`V1_6`). Asks for thin `contracts` header + `subscriptions.contract_id`, synthesized ORIGINAL (seq 0), BACKFILLED APPLIED documents for non-NEW acceptances (legacy evidence, no signed copy), no retroactive events, cutover for in-flight continuation quotes, and a grouping rule. **Cite; inventory residuals as OQs.** |
| Design note [#665](https://github.com/drgaciw/open-crm-cpq/pull/665) / [story-drg-566-backfill-history-design-note.md](story-drg-566-backfill-history-design-note.md) | Merged **Proposed** design note. Forks **F1–F12** stay Proposed until owner close. Accepting that note authorized **no** code, migration, flag, topic, gateway route, backfill run, or deploy. **This residual brief restates those forks as `OQ-566-*` for CPQ Decision; it does not adopt them.** |
| Register **OQ7** in [renewals-amendments-open-questions-register.md](../renewals-amendments-open-questions-register.md) | **ADOPTED, header-only first slice** (2026-10-17): one thin contract per existing subscription; no regrouping or synthetic history. History backfill stays DRG-566. **Cite; do not reopen.** |
| [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md) / `V1_8__contract_header_backfill.sql` / [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585) | S7 companion: count / owner-only apply / compensate for **headers only**. Apply is a manual owner step; no bot runs it. History synthesis is explicitly later (this story). **This brief does not call, wrap, fold into, or bypass that hold.** |
| [oq7-header-backfill-owner-decision.md](oq7-header-backfill-owner-decision.md) | Thin-contract / seq-0 collision notes. Cite; do not reopen OQ7. |
| [partial-apply-owner-decision.md](partial-apply-owner-decision.md) OQ5 Option A | First-slice apply cut. History synthesis is **not** an apply of ApplicationUnits. **Cited; not reopened.** |
| [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md) | Rule 2: executed / applied documents are immutable; correction is a new LifecycleChange. Cite; does not start S3. |
| Spike §6.3 / [amendment-document-design-spike.md](../amendment-document-design-spike.md) | `[INFERENCE]` steps matching RA-32; addendum left history on DRG-566 after header-only first slice. Cite only. |
| [cluster-g-visibility-queues-migration-decision-frame.md](cluster-g-visibility-queues-migration-decision-frame.md) | F-598-4 / F-598-5 / F-598-6: history outside the phase; null `contract_id` stays on current acceptance path; S7 functions stay in the companion. Cite; do not reopen. |
| Related stories (cite only) | [DRG-535](https://linear.app/drgamtd-workspace/issue/DRG-535) entity; [DRG-458](https://linear.app/drgamtd-workspace/issue/DRG-458) generate contract; [DRG-546](https://linear.app/drgamtd-workspace/issue/DRG-546) EXTENSION vs RENEWAL collapse; [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554) later regroup / co-term; [DRG-598](https://linear.app/drgamtd-workspace/issue/DRG-598) phased migration; [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) S3; [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) entitlements / conversion price (design not accepted as product authority for this brief). |
| Related Wave-2 drafts (**cite only; do not adopt open OQs**) | [#834](https://github.com/drgaciw/open-crm-cpq/pull/834) ChangeLine (DRG-552); [#835](https://github.com/drgaciw/open-crm-cpq/pull/835) dates (DRG-553); [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) partial-apply residual (DRG-565); apply-ledger / gate residuals for DRG-562 / DRG-563 when present. |
| CAM Flyway draft slots | [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) reserve **V1_16–V1_21**. Any schema fork from this story stays **Proposed / NO-GO**; this brief invents **no** migration number. |

## Verification marks

| Mark | Meaning here |
|---|---|
| `verified by reading the issue` | Read from Linear in this pass. |
| `verified by reading docs` | Read from Adopted / Proposed docs cited in Alignment. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed unless a row says so. |
| `unverified` | Not established in this pass. |

GitNexus: this checkout had **no** indexed repository (`list_repos` empty; no usable graph for this pass). Graph impact for this docs-only change is **UNKNOWN**, not an all-clear. Text search / file reads confirm the schema tokens and migration inventory below.

## Adopted OQ7 — restated (do not re-open)

`verified by reading docs` (register OQ7; [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md); design note Boundaries; `V1_8__contract_header_backfill.sql` header comment).

| ID | Contract | Source |
|---|---|---|
| **H1** | **One thin contract per existing subscription.** No regrouping in the S7 header job. | OQ7 Adopted; V1_8 companion |
| **H2** | **No synthetic amendment history** and **no retroactive events** in the S7 header job. | OQ7; V1_8; design note Boundaries |
| **H3** | A later merge (tracked on DRG-554) is the place to regroup. The S7 backfill does not regroup. | OQ7 / V1_8 wording |
| **H4** | History synthesis (ORIGINAL / BACKFILLED documents) stays **DRG-566**, outside the header-only slice. | Register OQ7 “DRG-566 history remains later”; design note |
| **H5** | `contract_header_backfill_count` / `_apply` / `_compensate` remain the S7 surface. Apply is **owner-only**. This brief does **not** authorize calling them, folding history into them, or bypassing that hold. | S7 companion; Cluster G F-598-6 |

**Cite; do not reopen.** This residual brief does not re-litigate H1–H5.

## What the design note left open (still residual)

`verified by reading docs` ([story-drg-566-backfill-history-design-note.md](story-drg-566-backfill-history-design-note.md) Owner-decision table F1–F12; Linear hygiene 2026-10-17).

After [#665](https://github.com/drgaciw/open-crm-cpq/pull/665) merged, DRG-566 remains **In Review** because the design note’s per-fork defaults stayed **Proposed, unaccepted, owner decision required**, and documentation acceptance ([#662](https://github.com/drgaciw/open-crm-cpq/pull/662)) authorizes **no data backfill**. Persisting ORIGINAL / BACKFILLED rows, a nullable-column migration, or any environment dry-run / apply still needs owner close on the forks below plus a separate implementation authorization. This brief inventories those residuals as **Proposed OQ-566-\*** — it does not invent product rows, run S7, or start S3/S4/S6.

## What the tree does today (orientation only)

`verified by reading code` / `verified by reading docs` at the base SHA.

1. **Acceptance + rate history.** `applied_quote_acceptances` (`V1_6`) has unique `accepted_quote_id`, `subscription_id`, `operation`, `commercial_digest`. No document id / tenant id / action date. Rate periods may store provenance string `'LEGACY_AGGREGATE'`; no provenance enum / check. V1_6: no retroactive `RampApplied`.
2. **Document vocabulary already allows** `document_kind` `ORIGINAL` / `BACKFILLED`, `status` `APPLIED`, `signature_policy` `NOT_REQUIRED`, `evidence_class` `STANDARD` / `LEGACY` (`V1_7`). Java `EvidenceClass.LEGACY` is reserved for a later backfill and is **not** written by S1. That is **not** authorization to synthesize history rows.
3. **NOT NULL gaps.** Documents require `lifecycle_change_id`, money totals, digests, triggers, dates, etc. that legacy acceptances do not supply. Design-note F6 / OQ-566-6 below: invent nothing until an owner-authorized migration (outside reserved slots) says which LEGACY columns may be null.
4. **CAM migrations present through V1_15** (including `V1_8` header-backfill functions, `V1_12` apply ledger, `V1_13`/`V1_14` assets, `V1_15` replay evidence). Historical notes that reserved “V1_9 for ledger” or said “next free is V1_10” describe earlier plans; this brief writes **no** new migration and does **not** claim Free Flyway slots. **CAM V1_16–V1_21 are reserved (#805–#812) — NO-GO.**
5. **S7 header functions** exist; Flyway installs them and does not execute them. Whether any environment has called `contract_header_backfill_apply` is `unverified`. This brief does **not** call it and does **not** authorize a hold bypass.
6. **Asset paths** (`V1_13` / `V1_14`) and related holds stay in force. This brief does **not** authorize asset-write, installed-base migration ([DRG-657](https://linear.app/drgamtd-workspace/issue/DRG-657) S7-class NO-GO), or dual-write from history synthesis.

## Related Wave-2 drafts (cite only — do not adopt open OQs)

| PR / story | Role for this brief |
|---|---|
| [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) / DRG-565 | Partial-apply residual; cite OQ5 A only — history synthesis is not unit apply. **Do not adopt** open OQ-565-\*. |
| [#834](https://github.com/drgaciw/open-crm-cpq/pull/834) / DRG-552 | ChangeLine vocabulary. Cite only. |
| [#835](https://github.com/drgaciw/open-crm-cpq/pull/835) / DRG-553 | Effective vs action dates. Cite only; no product date-writer from this brief. |
| DRG-562 / DRG-563 Wave-2 residuals (when present) | Gate / ledger shape for live apply — not a license to backfill history. Cite only. |

## Remaining owner forks (Proposed OQ-566-\*)

Every row below is **Proposed, unaccepted, owner decision required**. Recommendations restate the design-note F\* defaults for scoring — they are **not** Adopted by this brief. Alternatives sit beside the recommendation. Picking a row authorizes **no** code, migration, flag, S7 apply, asset-write, history dry-run against real tenants, S3/S4/S6 start, or deploy. Product work that depends on these forks stays **NO-GO** until they are closed (or explicitly deferred).

| ID | Question | Maps to design note | Recommendation (Proposed) | Alternatives | Depends on / blocked by |
|---|---|---|---|---|---|
| **OQ-566-1** | Which history is in scope, and where relative to a DRG-598 phase? | F1 | Sets named by RA-32 / spike §6.3 steps 2–4 (ORIGINAL + BACKFILLED non-NEW), as a **later** story **outside** the phase. | ORIGINAL only; synthesize nothing; phase writes history (contradicts Cluster G F-598-4) | DRG-598; Cluster G; OQ7 H4 |
| **OQ-566-2** | Which contract does a history document hang on? | F2 | Subscription’s existing thin-contract header. **No regroup.** | Group by customer + original quote; wait for DRG-554 before any document | OQ7 H1/H3; DRG-554; seq-0 uniqueness |
| **OQ-566-3** | Are `LEGACY` / `LEGACY_AGGREGATE` a provenance enum? | F3 | Both names stay **PROPOSED** provenance enum values until a migration + S3/S7 contract decision. Not an existing provenance enum. | Infer unit price from `net_monthly`; rewrite stored string to `AMENDMENT_APPLIED` | S3 / DRG-581; S7 contract; price-lock stub; DRG-609 |
| **OQ-566-4** | How is intent chosen for a non-NEW acceptance? | F4 | Map only onto tokens already in `chk_amendment_document_intent`; `document_kind` `BACKFILLED` / `ORIGINAL`; **no quote rewrite**. | Classify from quote `deal_type`; parse `continuation_request_json`; add `EXTEND` + backfill quotes | DRG-546; Cluster E E5/E6; quote `V1_12` |
| **OQ-566-5** | What makes a second pass insert nothing? | F5 | Keys already on main: seq 0 per contract; `request_identifier = accepted_quote_id`. | New idempotency table; delete and reinsert | `uq_amendment_document_seq` / `_request`; F6 lifecycle id |
| **OQ-566-6** | What happens to NOT NULL columns legacy sources do not fill? | F6 | **Invent nothing.** No insert until a later owner-authorized migration states which LEGACY columns may be null. | Zero money + synthetic `lifecycle_change_id`; wait until S5 creates real LifecycleChanges | V1_7 shape; S5; V1_6 “do not invent TCV” |
| **OQ-566-7** | Do BACKFILLED documents consume `last_executed_seq`? | F7 | ORIGINAL is seq 0. BACKFILLED rows do **not** take seq 1..n and do **not** change `last_executed_seq`. | Assign seq 1..n and set `last_executed_seq` to n; store as `DRAFT` with null seq | OQ4 / field schema; S7 header `last_executed_seq = 0` |
| **OQ-566-8** | Does history emit an event or an outbox row? | F8 | **No** retroactive event and **no** outbox (mirror V1_6). | Retroactive event + outbox; hash-chain only; `commercial_decision_audit` row | S6; ramp / amendment audit topics; ERP queue |
| **OQ-566-9** | When would a write be allowed, vs S7 V1_8 / #634? | F9 | Dry-run first. Owner-only apply stays **separate**. History is **not** the #634 header job. **Do not** fold into `contract_header_backfill_apply`. **Do not** bypass the S7 hold. | Fold history into S7 apply; write rows from Flyway at deploy | S7 companion; DRG-585; Cluster G F-598-6 |
| **OQ-566-10** | What happens to in-flight continuation quotes? | F10 | Stay **out** of this history set. **No** flag. | Synthesize SENT/APPROVED now; turn on OQ11 per-tenant flag | S6 / OQ11; Cluster G F-598-5 |
| **OQ-566-11** | Which migration version, and what rollback? | F11 | **No file in this brief.** A later additive migration only after forks close; **never** consume reserved **V1_16–V1_21**; never edit landed V1_7 / V1_8 in place; compensate deletes only history `created_by` marker (not header markers). | Use a reserved V1_16–V1_21 slot; edit V1_7/V1_8 in place | #805–#812; S7 compensate boundary |
| **OQ-566-12** | Do `LEGACY_AGGREGATE` periods become document lines? | F12 | **No** line insert and **no** rate-period update. | Insert lines with null price; add `amendment_document_line_id` on rate period | Line NOT NULL fields; Cluster F; DRG-609 |
| **OQ-566-IMPL** | Implementation / migration / flag / S7 / asset authorization | — | Separate implementation story **after** OQ-566-1..12 (or a deferred subset) close. **No** S7 hold bypass; **no** asset-write hold bypass; **no** reserved Flyway slots; **no** flag / dual-read / Jev / ramp; **no** environment dry-run or apply from this brief. | Piggy-back V1_16–V1_21; run S7 now; synthesize history inside header apply; enable asset-write | Owner release; AGENT.md holds; S3/S4/S6 gates; DRG-585; DRG-657 |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (residuals only)

### OQ-566-1 — History scope vs DRG-598 phase

| Option | What the owner would be choosing |
|---|---|
| **A. RA-32 / spike §6.3 sets 2–4 outside the phase (Recommended, Proposed)** | ORIGINAL + BACKFILLED non-NEW; keeps Cluster G F-598-4 and OQ7 H4. |
| **B. ORIGINAL only** | Narrows RA-32; leaves non-NEW acceptances without documents. |
| **C. Synthesize nothing** | Spike decision 7’s other bullet; story becomes wontfix. |
| **D. Phase writes history** | Contradicts F-598-4 default; not selected in the design note. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-566-2 — Grouping / contract attachment

| Option | What the owner would be choosing |
|---|---|
| **A. Thin header per subscription; no regroup (Recommended, Proposed)** | Preserves OQ7 H1; avoids seq-0 collision across two subscriptions on one contract. |
| **B. Group by customer + original quote** | Needs a defined “original quote”; `subscriptions.quote_id` as that value is `unverified`. |
| **C. Wait for DRG-554** | Parks all history documents until regroup. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-566-3 — Provenance / evidence class

| Option | What the owner would be choosing |
|---|---|
| **A. Keep LEGACY / LEGACY_AGGREGATE Proposed until S3/S7 decide (Recommended, Proposed)** | `evidence_class` check already allows `LEGACY`; rate-period provenance is a string, not an enum. |
| **B. Infer unit price from `net_monthly`** | Invents price; contradicts V1_6 and price-lock stub. |
| **C. Rewrite provenance string to `AMENDMENT_APPLIED`** | Mutates historical evidence without a scored contract. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-566-4 — Intent without a new token

| Option | What the owner would be choosing |
|---|---|
| **A. Map onto existing intent tokens only (Recommended, Proposed)** | RENEWAL→`RENEW`, AMENDMENT/EXPANSION→`AMEND`; kind BACKFILLED/ORIGINAL; no quote rewrite. |
| **B. Classify from `deal_type`** | EXTENSION vs RENEWAL collapse (DRG-546). |
| **C. Parse `continuation_request_json`** | Sample payload `unverified`; still no EXTENSION on acceptances. |
| **D. Add `EXTEND` + backfill quotes** | Cluster E E5-B/E5-C — not selected in the design note. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-566-5 — Idempotency

| Option | What the owner would be choosing |
|---|---|
| **A. Seq 0 + `request_identifier = accepted_quote_id` (Recommended, Proposed)** | Uses uniqueness already on main; second pass inserts nothing. |
| **B. New idempotency table** | Needs a future additive migration outside reserved slots. |
| **C. Delete and reinsert** | Unsafe vs S7 compensate markers and audit. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-566-6 — NOT NULL / invent-nothing gate

| Option | What the owner would be choosing |
|---|---|
| **A. Invent nothing; migration must name nullable LEGACY columns (Recommended, Proposed)** | Blocks inserts on today’s V1_7 shape without a scored schema fork. |
| **B. Zero money + synthetic lifecycle id** | Invents commercial totals and LifecycleChange identity. |
| **C. Wait for S5 real LifecycleChanges** | Parks history until S5; this brief does not start S5. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-566-7 — Sequence consumption

| Option | What the owner would be choosing |
|---|---|
| **A. ORIGINAL seq 0; BACKFILLED does not consume `last_executed_seq` (Recommended, Proposed)** | Leaves next real execution at seq 1; does not rewrite S7 headers. |
| **B. Assign seq 1..n and raise `last_executed_seq`** | Presents legacy evidence as the executed sequence. |
| **C. DRAFT with null seq** | Weakens APPLIED / legacy-evidence framing in RA-32. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-566-8 — Events / outbox

| Option | What the owner would be choosing |
|---|---|
| **A. No retroactive event and no outbox (Recommended, Proposed)** | Mirrors V1_6; avoids new-occurrence effects on ramp / amendment audit / ERP queue. |
| **B. Emit event + outbox** | Downstream treats history as a new occurrence (design-note F8 alternative). |
| **C. Hash-chain only / commercial_decision_audit** | Still invents audit surfaces without prices / S6 auth. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-566-9 — Dry-run vs S7 hold (explicit)

| Option | What the owner would be choosing |
|---|---|
| **A. Separate dry-run-first history story; S7 apply stays owner-only and separate (Recommended, Proposed)** | **Does not** authorize S7 hold bypass; does not fold history into `contract_header_backfill_apply`; does not run anything against real data from this brief. |
| **B. Fold history into `contract_header_backfill_apply`** | Violates OQ7 H2/H5 and the S7 companion. **NO-GO** without explicit owner supersede of OQ7. |
| **C. Write history from Flyway at deploy** | Removes owner gate; conflicts with dry-run-first. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-566-10 — In-flight continuation quotes

| Option | What the owner would be choosing |
|---|---|
| **A. Out of history set; no flag (Recommended, Proposed)** | Keeps S6 / OQ11 unauthorized; preserves Cluster G F-598-5. |
| **B. Synthesize SENT/APPROVED now** | Invents documents for unapplied quotes. |
| **C. Enable OQ11 per-tenant flag** | Starts S6 surface — **NO-GO** from this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-566-11 — Future migration / rollback / reserved slots

| Option | What the owner would be choosing |
|---|---|
| **A. No file here; later additive only; never V1_16–V1_21; history `created_by` ≠ header markers (Recommended, Proposed)** | Hard NO-GO on reserved slots and in-place edits of landed V1_7/V1_8. |
| **B. Consume a reserved V1_16–V1_21 slot** | Violates #805–#812 reservation. |
| **C. Edit V1_7 / V1_8 in place** | Rewrites landed history / header contracts. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-566-12 — LEGACY_AGGREGATE → lines

| Option | What the owner would be choosing |
|---|---|
| **A. No line insert; no rate-period update (Recommended, Proposed)** | Avoids inventing product_external_id / pricebook / configuration_hash / application unit. |
| **B. Insert lines with null price** | Still invents other NOT NULL fields. |
| **C. Add `amendment_document_line_id` on rate period** | Schema add outside reserved slots + Cluster F coupling. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-566-IMPL — Implementation gate

| Option | What the owner would be choosing |
|---|---|
| **A. Separate impl after residual OQx close; no S7 bypass; no asset-write; no reserved slots; no flags (Recommended, Proposed)** | Hard product / schema / flag **NO-GO** until then. |
| **B. Use CAM V1_16–V1_21 now** | Violates #805–#812. |
| **C. Run S7 / fold history into header apply / enable asset-write / flip flags now** | Explicitly out of scope — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

## Explicit product / schema / flag NO-GO

Until OQ-566-1..12 (or an owner-deferred subset) and OQ-566-IMPL close with words and a time:

1. **No** Java/TypeScript product change that synthesizes ORIGINAL / BACKFILLED amendment documents or mutates acceptance / rate-period history for this story.
2. **No** Flyway / schema change. **CAM V1_16–V1_21 reserved (#805–#812).** No edit in place of landed V1_7 / V1_8.
3. **No** inventing money totals, lifecycle ids, unit prices, price locks, entitlement history, or conversion prices to satisfy NOT NULL columns.
4. **No** feature-flag flip for S6 / OQ11, dual-read, Jev (`opencrm.jev.*`), or ramp.
5. **No** running S7 `contract_header_backfill_count` / `_apply` / `_compensate`, and **no S7 hold bypass**. History must not be folded into the header apply.
6. **No** asset-write hold bypass, installed-base migration, or dual-write from this story ([DRG-657](https://linear.app/drgamtd-workspace/issue/DRG-657) remains S7-class NO-GO).
7. **No** environment dry-run, tenant count against real data, or history apply from this brief alone.
8. **No** starting S3 / DRG-581, S4 apply enablement, or S6 topic registration from this brief.
9. **No** adopting open OQs from Wave-2 drafts (#834 / #835 / #839 / 562–563 residuals) by side effect.
10. **No** treating design-note F1–F12 defaults as Adopted, and **no** marking Linear DRG-566 Done from this brief alone.

## Out of scope (explicit)

- Reopening Adopted OQ7 (H1–H5) or the S7 companion’s owner-only apply rule.
- Reopening OQ5 Option A, OQ6 revision rules, Accepted proration, or uplift D-04.
- Accepting ADR-017 or ADR-024–027; accepting DRG-609 design as product authority.
- Rewriting quote `deal_type` / `continuation_operation` history (Cluster E).
- Starting DRG-598 phase writers for BACKFILLED documents.
- Merge authority for this file into `main` beyond draft PR review.

## What accepting this brief would unblock

Documentation clarity only: implementation stories may cite **OQ-566-1..12** and **OQ-566-IMPL** as the residual owner menu without re-litigating Adopted OQ7 or reopening the design note’s closed Boundaries. Accepting / merging this file **does not** open an implementation story, does not authorize a history migration, does not run S7, does not bypass asset-write holds, and does not mark DRG-566 Done.

## Unverified (carried forward)

- Whether any environment has called `contract_header_backfill_apply`.
- Multiple NULL `amendment_seq` values under `uq_amendment_document_seq` (not executed).
- Whether a Java writer rejects `status = APPLIED` when `amendment_seq` is null.
- Whether `subscriptions.quote_id` is the “original quote”, and whether its `varchar` parses as revision `target_quote_id` uuid.
- A sample `continuation_request_json` payload.
- Row counts (no query run this pass).
- DRG-554’s effect on `subscriptions.contract_id`.

## References

- Linear: [DRG-566](https://linear.app/drgamtd-workspace/issue/DRG-566/backfill-amendment-history-for-existing-subscriptions), [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585), [DRG-535](https://linear.app/drgamtd-workspace/issue/DRG-535), [DRG-458](https://linear.app/drgamtd-workspace/issue/DRG-458), [DRG-546](https://linear.app/drgamtd-workspace/issue/DRG-546), [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554), [DRG-598](https://linear.app/drgamtd-workspace/issue/DRG-598), [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581), [DRG-657](https://linear.app/drgamtd-workspace/issue/DRG-657), [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609).
- GitHub (design note): [#665](https://github.com/drgaciw/open-crm-cpq/pull/665).
- GitHub (cite-only Wave-2 drafts): [#834](https://github.com/drgaciw/open-crm-cpq/pull/834), [#835](https://github.com/drgaciw/open-crm-cpq/pull/835), [#839](https://github.com/drgaciw/open-crm-cpq/pull/839).
- Docs: [story-drg-566-backfill-history-design-note.md](story-drg-566-backfill-history-design-note.md); [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md); register OQ7; [oq7-header-backfill-owner-decision.md](oq7-header-backfill-owner-decision.md); [partial-apply-owner-decision.md](partial-apply-owner-decision.md); [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md); [cluster-g-visibility-queues-migration-decision-frame.md](cluster-g-visibility-queues-migration-decision-frame.md); [amendment-document-design-spike.md](../amendment-document-design-spike.md); [documentation-acceptance-2026-10-17.md](documentation-acceptance-2026-10-17.md).
- Schema (read-only): `V1_6__subscription_rate_schedule.sql`, `V1_7__contract_documents.sql`, `V1_8__contract_header_backfill.sql` (inventory through V1_15).
- Holds: [AGENT.md](../../../AGENT.md); dual-read / Jev / ramp / S4; **S7 apply owner-only**; **asset-write**; CAM Flyway V1_16–V1_21 reservation (#805–#812).
