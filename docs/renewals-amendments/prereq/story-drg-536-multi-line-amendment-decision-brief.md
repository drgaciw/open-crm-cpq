# DRG-536 — Multi-line amendment support: residual Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536/no-multi-line-amendment-support) (RA-02).

**Status:** Proposed residual Decision brief. This file **cites** the merged multi-line design note ([#664](https://github.com/drgaciw/open-crm-cpq/pull/664) → [story-drg-536-multi-line-amendment-design-note.md](story-drg-536-multi-line-amendment-design-note.md)), **restates Adopted constraints from other records without reopening them**, and renumbers the design note's still-open forks as **Proposed OQ-536-\***. **It authorizes no production code path, no schema change, no Flyway migration (including CAM slots V1_16–V1_21 reserved by #805–#812), no edit of `QuoteContinuationRequest` / `QuoteContinuationService` / `QuoteContinuationPolicy`, no feature flag, no Kafka consumer registration, no S3–S8 enablement, no S7 backfill run, no asset-write path, no dual-read / Jev / ramp flip, and no deploy.** Product / schema / flag work that lands multi-line authoring or apply is **NO-GO** until the owner closes the residual OQ-536-\* forks below (or explicitly defers a named fork with words and a time).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`.

**Linear status when read:** In Review (not Done). This brief leaves that status unchanged.

## Alignment (cite; do not reopen Adopted cells)

| Artifact | Relation to this brief |
|---|---|
| [#664](https://github.com/drgaciw/open-crm-cpq/pull/664) → [story-drg-536-multi-line-amendment-design-note.md](story-drg-536-multi-line-amendment-design-note.md) | Merged **design note** (docs acceptance). Forks **536-F1..F12** and **536-C1..C9** remain Proposed; this residual brief renumbers them as **OQ-536-\***. Cite; do not treat the merge as product Done. |
| [#626](https://github.com/drgaciw/open-crm-cpq/pull/626) / [#631](https://github.com/drgaciw/open-crm-cpq/pull/631) / [#632](https://github.com/drgaciw/open-crm-cpq/pull/632) → OQ5 Option A | **Adopted** first-slice partial-apply cut. Design note 536-F6 / 536-F8 point here. Cite; **do not reopen**. Residual delivery of ordering writers stays on [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) (DRG-565) — **do not adopt** its open OQs. |
| [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md) + [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) | **Adopted** pre-execution revision / executed immutability; **D-STORAGE**, **D-DIGEST / OQ18**, **D-REVISION**, **D-RACE** selected under delegation. Cite; **do not reopen**. Residual is how multi-line lines sit on those contracts (OQ-536-\* below). |
| [#650](https://github.com/drgaciw/open-crm-cpq/pull/650) Cluster A / [#651](https://github.com/drgaciw/open-crm-cpq/pull/651) Cluster E | **Accepted as documentation** for modes / LifecycleChange split. One document mode + many lines already framed. Cite; do not reopen Adopted L\* / E\* / 550 / 554 / 592 cells. |
| [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md) D-04 | **Accepted** uplift precedence. Design note 536-F12 follows it per line. Cite; **do not reopen**. |
| [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) | **Accepted**. Not restated. |
| Related Wave-2 Decision drafts (cite only; **do not adopt** their open OQs) | Partial-apply residual [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) (DRG-565); one-time charge residual [#851](https://github.com/drgaciw/open-crm-cpq/pull/851) (DRG-572); LifecycleChange intent residual [#854](https://github.com/drgaciw/open-crm-cpq/pull/854) (DRG-548). |

## Verification marks

| Mark | Meaning here |
|---|---|
| `verified by reading the issue` | Read from Linear in this pass. |
| `verified by reading docs` | Read from Adopted / Proposed docs cited in Alignment. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed unless a row says so. |
| `unverified` | Not established in this pass. |

GitNexus: this checkout had **no** indexed repository (`list_repos` empty; no `.gitnexus/` / `run.cjs`). Graph impact for this docs-only change is **UNKNOWN**, not an all-clear. Text search confirms the schema tokens and migration inventory below.

## Problem (RA-02)

`verified by reading the issue` (Linear DRG-536, 2026-11-06): High; **In Review**; labels `renewals-amendments`, `Open-CRM-CPQ`. A continuation supports **one** recurring source line. Real amendments change several lines at once (add, remove, change quantity). Suggested next step on the issue: multi-line continuation request and per-line source mapping. Attachment: design note [#664](https://github.com/drgaciw/open-crm-cpq/pull/664).

`verified by reading code` at the base SHA: `QuoteContinuationRequest` still carries one `sourceSubscriptionLineUuid`, one `quantity`, one `effectiveDate`. `QuoteContinuationService` still refuses additional lines ("additional lines require a separate quote"). CAM `amendment_document_lines` already stores many lines per revision (`uq_amendment_line_triple`). The product gap remains the **authoring / pricing / apply path that fills those lines**, not the line table itself.

## Adopted constraints this brief must not reopen

`verified by reading docs`. Restated for orientation only. This residual brief does **not** re-litigate these cells.

| ID | Contract | Source |
|---|---|---|
| **A1** | First-slice apply = OQ5 **Option A**: one unit per target subscription, atomic units, no silent rollback, `PARTIALLY_APPLIED` / `APPLY_BLOCKED` cut, correcting amendment as remedy (not rebase) | [#626](https://github.com/drgaciw/open-crm-cpq/pull/626) / [#631](https://github.com/drgaciw/open-crm-cpq/pull/631) / [#632](https://github.com/drgaciw/open-crm-cpq/pull/632) |
| **A2** | Pre-execution revision opens a new document revision; executed / apply states are immutable (`IMMUTABLE_EXECUTED`); at-most-one executed revision | OQ6 + delegated CAM contracts |
| **A3** | Unpriced revision storage / digests / nullable pins follow **D-STORAGE**; commercial digest utility / OQ18 / **D-DIGEST** (`changeLineRef` sort); CAM revision command **D-REVISION**; concurrency **D-RACE** | [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md); CAM `V1_10__amendment_unpriced_revision_storage.sql` landed |
| **A4** | One document amendment mode; lines may carry per-line price modes; first-slice actions `ADD` / `UPDATE` / `REMOVE`; intent/slot follow Cluster E | Cluster A / Cluster E docs acceptance |
| **A5** | Uplift precedence D-04 per priced line; first-slice submit does not send `RENEW` | Accepted uplift ADR |
| **A6** | Holds: no product / schema / flag from docs alone; CAM **V1_16–V1_21** reserved (#805–#812); S3–S8 need separate implementation authorization; S7 backfill is a manual owner step; dual-read / Jev / ramp / asset-write untouched | AGENT.md; #805–#812; design note holds |

**Cite; do not reopen.** Design-note forks that merely pointed at A1–A6 are **delivery residuals** below, not second votes on those Adopted cells.

## What the tree does today (orientation only)

`verified by reading code` / `verified by reading docs` at the base SHA.

1. **Quote continuation is still one line.** `QuoteContinuationRequest` / `QuoteContinuationService` / `QuoteContinuationPolicy` enforce a single recurring source. Multi-line authoring is **not** landed.
2. **CAM line spine already multi-line.** `V1_7__contract_documents.sql`: many lines per revision; partial unique `uq_amendment_line_source`; `line_no >= 1`; units via `application_unit_no` / `dependency_order`; frozen membership `V1_7_2`.
3. **CAM migrations present through V1_15** (including `V1_10` unpriced storage, `V1_12` apply ledger, `V1_15` replay evidence). Historical design-note text that reserved “V1_9 for ledger” / “V1_10+ for nulls” describes an earlier plan; this brief writes **no** new migration and does **not** claim Free Flyway slots. **CAM V1_16–V1_21 are reserved (#805–#812) — NO-GO.**
4. **OQ7 thin contract** unchanged: one header per existing subscription; backfill does not regroup; this brief does not run it.
5. **Quote-service LifecycleChange GAP** (Cluster E) is not filled here. Intent create remains a separate residual ([#854](https://github.com/drgaciw/open-crm-cpq/pull/854)).

## Related Wave-2 drafts (cite only — do not adopt open OQs)

| PR / story | Role for this brief |
|---|---|
| [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) / DRG-565 partial-apply residual | Cite for unit ordering / create-before-terminate writer GAP and OQ-565-\*. Shared-target lines ↔ units is an interaction (design note 536-F6; OQ-565-8). **Do not adopt** open OQ-565-\*. |
| [#851](https://github.com/drgaciw/open-crm-cpq/pull/851) / DRG-572 one-time charge residual | Cite for later-scope `ONE_TIME` / service-period line shape. First-slice multi-line stays ADD/UPDATE/REMOVE recurring unless owner scores OQ-536-5 / OQ-536-17. **Do not adopt** open OQ-572-\*. |
| [#854](https://github.com/drgaciw/open-crm-cpq/pull/854) / DRG-548 LifecycleChange intent residual | Cite for intent create/submit allow-list vs document spine. Multi-line does not widen intent create. **Do not adopt** open OQ-548-\*. |

## Remaining owner forks (Proposed OQ-536-\*)

Every row below is **Proposed, unaccepted, owner decision required**. Defaults follow the design note recommendations unless a later Adopted cell already closed the question (noted). Alternatives sit beside the recommendation. Picking a row authorizes **no** code, migration, flag, S3–S8 enablement, S7 backfill, asset-write, or deploy. Product work that depends on these forks stays **NO-GO** until they are closed (or explicitly deferred).

| ID | Design-note fork | Question | Recommendation (Proposed) | Alternatives | Depends on / blocked by |
|---|---|---|---|---|---|
| **OQ-536-1** | 536-F1 | What carries the lines | One `lifecycle_change_id`, one amendment document, many lines on the current revision | One document per line; one quote per line with no shared document | Cluster E document spine; A4 |
| **OQ-536-2** | 536-F2 | What happens to `QuoteContinuationRequest` | Leave the one-line request as stored until a separately authorized S5 / multi-line authoring change. Later shape (not applied here): one request with a line list | Edit the Java request in this Decision; never add a line list | S5 / DRG-583; OQ-536-IMPL |
| **OQ-536-3** | 536-F3 | How modes sit on one amendment | One document amendment mode; mixed first-slice line actions; per-line price modes; one co-term mode. Follow Cluster A / E; do not reopen them | One action code for the whole document; one price mode for the whole document; amendment mode only on LifecycleChange | A4; legal-review / SKU holds stay Cluster A |
| **OQ-536-4** | 536-F4 | Source identity / repeated products | Keep `uq_amendment_line_source`. Two `ADD` lines may share `product_external_id` | Refuse duplicate ADD products; allow two lines to name the same source (needs migration) | Landed `V1_7` checks |
| **OQ-536-5** | 536-F5 | What `line_no` orders | Seller order for display; **not** the apply order. Digest interaction with D-DIGEST is OQ-536-13 | `line_no` is apply order; catalog order with gaps | OQ-536-13; A3 D-DIGEST |
| **OQ-536-6** | 536-F6 | How lines become units | Use Adopted OQ5 Option A (A1). Who writes `dependency_order` / create-before-terminate remains the **GAP** on [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) — not closed here | One unit per line; one unit for the whole document across subscriptions | A1; do **not** adopt OQ-565-\* |
| **OQ-536-7** | 536-F7 | Pricing / validation failure mid-revision | Whole revision stays unpriced; no partial price / dropped line. Uses landed D-STORAGE null path (A3) | Price successes and omit failures; copy previous totals | A3 `V1_10` |
| **OQ-536-8** | 536-F8 | Unit fails at apply | Adopted OQ5 Option A cut (A1). No silent rollback; correcting amendment | Roll back committed units; block one line inside a committed unit; rebase executed document | A1; S4 separately gated |
| **OQ-536-9** | 536-F9 | Header totals | One priced-revision snapshot of the four headers; not recomputed at apply; no per-line ARR bridge columns in this story | Recompute at apply from committed units; add per-line bridge columns now | DRG-575 bridge frame; OQ-536-16 |
| **OQ-536-10** | 536-F10 | Audit grain | One existing document event; **no** new S6 event contract | One event per line; new multi-line topic/registry entry | A2 / D-RACE local audit; S6 unauthorized |
| **OQ-536-11** | 536-F11 | Which subscriptions a line may name | Only subscriptions whose `contract_id` equals the document's; OQ7 not regrouped | Cross-contract lines; one continuation per subscription even when they share a contract | OQ7; DRG-563 ledger limit |
| **OQ-536-12** | 536-F12 | Uplift on a later priced line | Per-line Accepted D-04; first-slice submit still does not send `RENEW` | One document uplift percent; different precedence stack | A5 |
| **OQ-536-13** | 536-C1 | Digest line order vs D-DIGEST | Leave owner fork open: seller `line_no` (OQ-536-5) vs stable sort by unique `changeLineRef` (Adopted D-DIGEST). Score which identity the commercial envelope uses for multi-line | Force `changeLineRef` sort for digest only; force `line_no` as digest order and reopen D-DIGEST | A3 D-DIGEST |
| **OQ-536-14** | 536-C2 | Multi-line payload on CAM revision command | D-REVISION is **Adopted** (A3). Residual: complete commercial line list on that command for multi-line first slice; no header-only partial revision | Invent a second revision route; keep one-line-only payload forever | A3; OQ-536-2 |
| **OQ-536-15** | 536-C3 | Unpriced totals storage | **Closed by A3 / `V1_10`.** Residual only: multi-line pricing failure must use the landed null path (same as OQ-536-7). Do not reopen D-STORAGE | Re-open nullable totals; invent placeholder zeroes | A3 |
| **OQ-536-16** | 536-C4 | ARR bridge storage vs header `arr_delta` | Keep bridge off line columns here; pricing-service bridge store stays DRG-575 / delegated Pricing+ARR paragraphs — cite only | Overload `arr_delta`; add per-line bridge columns in this story | DRG-575; OQ-536-9 |
| **OQ-536-17** | 536-C5 | Later-scope ONE_TIME / per-line effective date | Keep first-slice multi-line on recurring ADD/UPDATE/REMOVE + header dates. Carry DRG-572 later-scope shape only after [#851](https://github.com/drgaciw/open-crm-cpq/pull/851) OQs close or are deferred | Widen first slice to ONE_TIME + service period now | Do **not** adopt OQ-572-\* |
| **OQ-536-18** | 536-C6 | Create-before-terminate sentence | Leave writer unstated here; cite [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) GAP / OQ-565-2. Do not invent a writer | Apply create-before-terminate for every multi-line document without a named writer | Do **not** adopt OQ-565-\* |
| **OQ-536-19** | 536-C7 | Local audit type names | Keep OQ-536-10 default; optional local names `AMENDMENT_SUPERSEDED` / `AMENDMENT_REVISION_REJECTED` on the existing document event (D-RACE) — **not** a new S6 topic | New S6 topic; keep zero type names forever | A3 D-RACE; S6 hold |
| **OQ-536-20** | 536-C8 | OQ18 status | **Closed by A3.** Follow current delegated confirmation of OQ18 / shared digest utility. Do not follow only the historical "OQ18 remains Proposed" sentence | Reopen OQ18 | A3 |
| **OQ-536-21** | 536-C9 | Classification / mapping adoption on multi-line | Leave class list + contested SKU mapping as owner options until Cluster A / DRG-594 / DRG-599 Decisions close them for multi-line docs; one contested line still holds the whole document | Treat #663 "adopted baseline" as multi-line product law without a scored Decision | Cluster A 594 / 599 |
| **OQ-536-IMPL** | (gate) | Implementation brief / product authorization | Blocked until blocking OQ-536-\* (and cited 565/572/548 where named) close or are explicitly deferred with owner words+time. **No** CAM V1_16–V1_21; **no** flag flip | Authorize product from this file alone; claim reserved Flyway slots | All above |

### GAP (not a fork)

Who writes `dependency_order` / `dependsOnUnitNos` (create-before-terminate) remains **UNMET** as recorded in [partial-apply-rules-oq5-option-a.md](partial-apply-rules-oq5-option-a.md) and the design note GAP section. Ownership of that writer is inventoried on [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) (OQ-565-\*). This brief does **not** choose a writer and does **not** adopt those OQs.

## Explicit product / schema / flag NO-GO

Until OQ-536-1..21 (or an owner-deferred subset) and OQ-536-IMPL close with words and a time:

1. **No** Java/TypeScript product change to multi-line continuation, per-line source mapping, or amendment revision payloads beyond what separate authorized stories already landed.
2. **No** Flyway / schema change. **CAM V1_16–V1_21 reserved (#805–#812).** No edit in place of landed `V1_7` / `V1_7_2` / `V1_10`–`V1_15`.
3. **No** feature-flag flip for S3–S8, asset-write, dual-read, Jev (`opencrm.jev.*`), or ramp.
4. **No** running S7 `contract_header_backfill_apply` (or any environment backfill) from this story.
5. **No** new S6 topic / registry entry from OQ-536-10 / OQ-536-19.
6. **No** adopting open OQs from Wave-2 drafts [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) / [#851](https://github.com/drgaciw/open-crm-cpq/pull/851) / [#854](https://github.com/drgaciw/open-crm-cpq/pull/854).
7. **No** reopening Adopted A1–A6 (OQ5 Option A, OQ6 / D-\* contracts, Cluster modes, D-04, holds) as if this file were a second authority.
8. **No** marking Linear DRG-536 Done from this brief alone.

## Out of scope (explicit)

- Reopening Adopted OQ5 Option A, OQ6, D-STORAGE / D-DIGEST / D-REVISION / D-RACE, Cluster A/E Adopted cells, or Accepted uplift / proration ADRs.
- Editing `QuoteContinuationRequest`, `QuoteContinuationService`, or `QuoteContinuationPolicy` in this PR.
- Filling the quote-service LifecycleChange GAP; widening intent create ([#854](https://github.com/drgaciw/open-crm-cpq/pull/854)).
- Starting or “finishing” S3 / DRG-581, S4 / DRG-582, S5 / DRG-583, S6 / DRG-584, or S7 apply.
- Asset lifecycle enablement / write paths; fulfilment registry changes.
- Dual-read flip, NgRx, Jev enablement, ramp flag.
- Rewriting the design note body; accepting `specs/partial-apply.md` or `specs/lifecycle-intents.md`.
- Merge authority for this file into `main` beyond draft PR review.

## What accepting this brief would unblock

Documentation clarity only: implementation stories may cite **OQ-536-1..21** and **OQ-536-IMPL** as the residual owner menu without re-litigating Adopted A1–A6 or the design note's long form. Accepting / merging this file **does not** open an implementation story, does not enable multi-line product paths, does not run S7, and does not mark DRG-536 Done.

## Verification (this pass)

- [x] Docs-only file under `docs/renewals-amendments/prereq/`
- [x] Design note [#664](https://github.com/drgaciw/open-crm-cpq/pull/664) cited; residual forks Proposed only as **OQ-536-\***
- [x] Wave-2 drafts [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) / [#851](https://github.com/drgaciw/open-crm-cpq/pull/851) / [#854](https://github.com/drgaciw/open-crm-cpq/pull/854) cited without adopting open OQs
- [x] Product / schema / flag **NO-GO**; CAM V1_16–V1_21 untouched
- [x] Linear left **In Review** (orchestrator / agent must not mark Done)
- [x] GitNexus unavailable / UNKNOWN — not treated as clean
- [x] Secret scan: no credentials in this file

## Owner decision slots

For each **OQ-536-\*** row scored: `Owner decision: ___ (drg, time)`.

## References

- Linear: [DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536/no-multi-line-amendment-support), [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565), [DRG-572](https://linear.app/drgamtd-workspace/issue/DRG-572), [DRG-548](https://linear.app/drgamtd-workspace/issue/DRG-548), [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583), [DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575), [DRG-563](https://linear.app/drgamtd-workspace/issue/DRG-563).
- GitHub (design note): [#664](https://github.com/drgaciw/open-crm-cpq/pull/664).
- GitHub (Adopted / delegated): [#626](https://github.com/drgaciw/open-crm-cpq/pull/626), [#631](https://github.com/drgaciw/open-crm-cpq/pull/631), [#632](https://github.com/drgaciw/open-crm-cpq/pull/632), [#650](https://github.com/drgaciw/open-crm-cpq/pull/650), [#651](https://github.com/drgaciw/open-crm-cpq/pull/651), [#663](https://github.com/drgaciw/open-crm-cpq/pull/663).
- GitHub (cite-only Wave-2 drafts): [#839](https://github.com/drgaciw/open-crm-cpq/pull/839), [#851](https://github.com/drgaciw/open-crm-cpq/pull/851), [#854](https://github.com/drgaciw/open-crm-cpq/pull/854); CAM reservation [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812).
- Docs: [story-drg-536-multi-line-amendment-design-note.md](story-drg-536-multi-line-amendment-design-note.md); [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md); [partial-apply-owner-decision.md](partial-apply-owner-decision.md); [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md); [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md).
- Schema (read-only): `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql` (and inventory through V1_15); quote `QuoteContinuationRequest.java`.
- Holds: [AGENT.md](../../../AGENT.md); dual-read / Jev / ramp / S4; CAM Flyway V1_16–V1_21 reservation (#805–#812).
