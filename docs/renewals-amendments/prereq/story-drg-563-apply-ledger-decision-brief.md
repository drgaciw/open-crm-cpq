# DRG-563 — Apply ledger across multiple subscriptions: residual Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-563](https://linear.app/drgamtd-workspace/issue/DRG-563/apply-ledger-cannot-record-one-amendment-across-multiple-subscriptions) (RA-29).

**Status:** Proposed residual Decision brief for CPQ Decision scoring. This file **cites** the merged design note [#668](https://github.com/drgaciw/open-crm-cpq/pull/668) / [story-drg-562-563-gate-apply-and-ledger-design-note.md](story-drg-562-563-gate-apply-and-ledger-design-note.md) **without reopening** that note’s closed “Already decided” cells or treating its Proposed forks as Adopted. It inventories **remaining owner forks as `OQ-563-*`**. **It authorizes no production code path, no schema change, no Flyway migration (including inventing a CAM version that collides with reserved draft slots V1_16–V1_21 on [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), no feature flag, no dual-read flip, no Jev enablement, no ramp enable, no DRG-625 apply-endpoint enablement, no asset-write hold bypass, and no deploy.** [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) (S4) stays **owner-gated**; this brief does **not** authorize S4 product landing or treat any landed apply/ledger artifact as Done for Linear DRG-563. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Linear status when read:** In Review (not Done). This brief leaves that status unchanged.

## Alignment (cite; do not reopen closed cells)

| Artifact | Relation to this brief |
|---|---|
| [DRG-563](https://linear.app/drgamtd-workspace/issue/DRG-563) issue body (RA-29) | Problem statement: `applied_quote_acceptances` UNIQUE quote + one `subscription_id`; missing amendment identity on ledger / `QuoteAcceptedEvent` / rate-period provenance. Suggested next steps (columns or join table; event fields; `AMENDMENT_APPLIED` provenance). **Cite; inventory residuals as OQs.** |
| Design note [#668](https://github.com/drgaciw/open-crm-cpq/pull/668) / [story-drg-562-563-gate-apply-and-ledger-design-note.md](story-drg-562-563-gate-apply-and-ledger-design-note.md) | Merged **Proposed** S4 design note for DRG-562 + DRG-563. Forks 1–15 stay Proposed until owner close. **This residual brief does not reopen** the note’s “Already decided” table (OQ5 A, OQ6, OQ7 thin contract, OQ10, OQ18, proration Accepted, uplift D-04, …). |
| [amendment-technical-approach-and-dependencies.md](../amendment-technical-approach-and-dependencies.md) §3 | Gate paragraph: new `document_apply_ledger` unique `(document_id, unit_no)` + `apply_key_digest`; keep `applied_quote_acceptances` unchanged (bypass, not mutation). Historical “V1_9” naming — **cite; do not invent a new migration number here.** |
| [amendment-entity-field-schema.md](../amendment-entity-field-schema.md) §2.6 | Still says `applied_quote_acceptances` gains nullable `amendment_document_id` and `application_unit_no`. Competing shape vs gate bypass. Left unchosen by [#668](https://github.com/drgaciw/open-crm-cpq/pull/668) Fork 3. |
| [partial-apply-owner-decision.md](partial-apply-owner-decision.md) / OQ5 A | First-slice: one unit per target subscription; atomic units; no auto-rollback; `PARTIALLY_APPLIED` / `APPLY_BLOCKED`; correcting amendment as remedy. **Cited; not reopened.** |
| [DRG-562](https://linear.app/drgamtd-workspace/issue/DRG-562) | Executed-state gate (companion story in #668). This brief does not close DRG-562 forks. |
| [DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536) / [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559) | Multi-line amendments and on-prem→SaaS restructure **depend** on a multi-subscription ledger. Cite only; do not adopt their open work. |
| [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) | S4 product story. **Owner-gated.** This brief does not authorize landing, enablement, or Linear Done. |
| Related Wave-2 drafts (**cite only; do not adopt open OQs**) | [#824](https://github.com/drgaciw/open-crm-cpq/pull/824) blocked-unit recovery (DRG-627); [#827](https://github.com/drgaciw/open-crm-cpq/pull/827) `TARGET_CONTRACT_UNSET` (DRG-623); [#834](https://github.com/drgaciw/open-crm-cpq/pull/834) ChangeLine residual (DRG-552); [#835](https://github.com/drgaciw/open-crm-cpq/pull/835) effective vs action dates (DRG-553). DRG-565 partial-apply rules already have merged docs ([#631](https://github.com/drgaciw/open-crm-cpq/pull/631) / [#626](https://github.com/drgaciw/open-crm-cpq/pull/626)); no open Wave-2 residual PR for 565 was found this pass — cite OQ5 A / partial-apply owner decision only. |
| CAM Flyway draft slots | [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) reserve **V1_16–V1_21**. Any schema fork from this story stays **Proposed / NO-GO**; this brief invents **no** migration number. |

## Verification marks

| Mark | Meaning here |
|---|---|
| `verified by reading code` | Read in the tree at the base SHA. Not executed unless a row says so. |
| `verified by reading the issue` | Read from Linear in this pass. |
| `verified by reading docs` | Read from the cited design note / gate / schema docs. |
| `unverified` | Not established in this pass. |

GitNexus: index rebuilt this pass (`npx gitnexus@latest analyze --index-only`). Upstream `impact` on `AppliedQuoteAcceptance` and `DocumentApplyLedgerEntry` returned `risk: LOW` with **`epistemic: lower-bound`** (dispatch-boundary implementations not fully traced; process aggregation empty). Index process build reported **truncation** (entry points dropped / walks cut). **That is not a clean blast-radius check** — treat graph coverage as unresolved for product paths. This brief is docs-only; it does not edit those symbols.

## Problem (RA-29) — restated

`verified by reading the issue` and `verified by reading code`.

The apply ledger cannot represent **one amendment document** that commits **separate atomic outcomes across several subscriptions**.

1. **Legacy acceptance table.** `applied_quote_acceptances` (`V1_6__subscription_rate_schedule.sql`) has `accepted_quote_id VARCHAR(255) NOT NULL UNIQUE` and a **single** `subscription_id`. Idempotency is quote id + `commercial_digest` in `AcceptedQuoteSubscriptionService`. One quote → at most one acceptance row → at most one subscription. Mixed SaaS/on-prem lines and restructures that create one subscription and end another cannot be recorded as separate units on that table alone.
2. **Amendment identity on the chain (issue wording).** The story asked for amendment identity on (a) ledger rows, (b) `QuoteAcceptedEvent`, and (c) rate-period provenance / amendment-line link. Gaps vs today’s `main` are inventoried below as residuals — some fields landed as optional or under different tokens; **none of that closes Linear DRG-563 or authorizes further product work from this brief.**

**Priority (issue):** High — blocks multi-line apply ([DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536)), restructures ([DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559)), and document↔ledger audit linkage. Companion gate story: [DRG-562](https://linear.app/drgamtd-workspace/issue/DRG-562).

## What #668 already proposed (cite; do not reopen)

`verified by reading docs` — [story-drg-562-563-gate-apply-and-ledger-design-note.md](story-drg-562-563-gate-apply-and-ledger-design-note.md). All rows were **Proposed, unaccepted, owner decision required** in that note. This residual brief **does not adopt** them.

| #668 fork (DRG-563-relevant) | Proposed default in #668 | Explicit alternatives left open |
|---|---|---|
| **Fork 3 — Ledger home** | Keep gate paragraph: reserved `document_apply_ledger` unique `(document_id, unit_no)` + `apply_key_digest`; **`applied_quote_acceptances` unchanged** (bypass, not mutation); `amendment_application_units` stays the V1_7 plan row | Schema §2.6 columns on `applied_quote_acceptances`; or use only `amendment_application_units.apply_key_digest` and never create the reserved table |
| **Fork 4 — Per-subscription rows** | One ledger row per `(document_id, unit_no)`; first-slice unit grain = one unit per target subscription (OQ5 A); ledger key does **not** gain `subscription_id` | Add `subscription_id` to the ledger; or drop `accepted_quote_id` uniqueness and insert one legacy row per subscription |
| **Fork 5 — Legacy path and flag** | Apply stays flag-off; legacy consumer/service unchanged by the note | Gate the consumer in the design; add/enable S6 per-tenant flag |
| **Fork 10 — Provenance** | Keep `ACCEPTED_PRICED` / `ACCEPTED_PRORATED`; amendment link via existing `amendment_document_line_id` + `revision_no` (V1_7) | Set rate-period `provenance` to `AMENDMENT_APPLIED` |
| **Fork 15 — S4/S5/S6** | S4 / DRG-582 unauthorized; S5/S6 blocked; apply flag-off; DRG-562/563 stay In Review | Separate owner record that explicitly authorizes DRG-582 |

**Closed cells in #668 (do not reopen):** OQ5 option A; OQ6 executed immutability; OQ7 thin contract; OQ10 signature cut; OQ18 / D-DIGEST; Accepted proration; Accepted uplift D-04; Cluster B named-unbuilt units; ONE_TIME later scope; fulfilment-after-commit contract as recorded in #663 — see the design note’s “Already decided” table.

**Issue suggested next steps vs #668:** Linear suggested “columns **or** join table” on the acceptance ledger, `QuoteAcceptedEvent` fields, and `AMENDMENT_APPLIED` provenance. #668’s Proposed default for the multi-subscription conflict is **bypass** (new document ledger), not mutation of `applied_quote_acceptances`. Provenance default in #668 is **not** the issue’s `AMENDMENT_APPLIED` token. Those deltas are residual owner forks below.

## Current `main` inventory (facts only — not authorization)

`verified by reading code` at the base SHA. **Inventory ≠ Done for DRG-563. Inventory ≠ S4 authorization.**

| Fact | Evidence |
|---|---|
| Legacy table still UNIQUE quote + one subscription | `V1_6__subscription_rate_schedule.sql` `applied_quote_acceptances`; `AppliedQuoteAcceptance.java` — no `amendment_document_id` / `application_unit_no` |
| Document apply ledger table exists as **V1_12** (not the historical “V1_9” name) | `V1_12__create_document_apply_ledger.sql` — PK `(document_id, unit_no)`, unique replay `(document_id, unit_no, apply_key_digest)` |
| Replay-evidence columns on units | `V1_15__document_apply_replay_evidence.sql` (attempt revision / pin / block reason) — cite only; not a DRG-563 close |
| `QuoteAcceptedEvent` has optional `amendmentDocumentId` and per-line `applicationUnitNo` | `QuoteAcceptedEvent.java` (S6 additive, null when absent) |
| Legacy consumer still routes legacy; document id is logged, not used to load a document when routing flag keeps legacy | `QuoteAcceptedEventConsumer` / `AmendmentRoutingFlag` |
| Document-path rate periods write provenance `DOCUMENT_APPLY` | `DocumentApplyPeriods.java` — **not** the issue string `AMENDMENT_APPLIED` |
| `AMENDMENT_APPLIED` exists as a **reserved lifecycle event** catalog entry (`AmendmentApplied` → document status `APPLIED`), Publication.RESERVED | `AmendmentLifecycleEventCatalog.java` — distinct from rate-period provenance |
| Rate periods already have nullable `amendment_document_line_id` + `revision_no` | `V1_7__contract_documents.sql` |
| CAM migrations through V1_15 present on this `main`; draft PRs claim V1_16–V1_21 | migration directory listing; PRs #805–#812 |

## Implementation / product NO-GO (explicit)

1. This Decision brief closes **nothing** by itself. Options are Proposed.
2. CPQ Decision must score the forks. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes further Java/TS product work, Flyway (any version), dual-read flips, Jev flags, ramp enable, **DRG-625** apply-endpoint enablement, or **asset-write hold bypass** for DRG-563.
4. **Do not invent a CAM Flyway version.** V1_16–V1_21 are reserved on draft [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812). Any schema fork stays Proposed / NO-GO in this brief.
5. Closing an OQ here does **not** authorize [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) product landing, does **not** adopt open OQs from [#824](https://github.com/drgaciw/open-crm-cpq/pull/824) / [#827](https://github.com/drgaciw/open-crm-cpq/pull/827) / [#834](https://github.com/drgaciw/open-crm-cpq/pull/834) / [#835](https://github.com/drgaciw/open-crm-cpq/pull/835), and does **not** reopen OQ5 A / OQ6 / DRG-562 gate forks.
6. Holds in [AGENT.md](../../../AGENT.md) and [ARCHITECTURE.md §17](../../ARCHITECTURE.md) stay in force.

---

## Owner-decision table (residuals)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)** and cites #668 / gate where applicable. **No Adopted cells in this table.**

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product / schema / flag? |
|---|---|---|---|---|
| **OQ-563-1** | Ledger home for multi-subscription document apply | Affirm gate / #668 Fork 3 **bypass**: document ledger keyed `(document_id, unit_no)` (+ digest); leave `applied_quote_acceptances` untouched for legacy | Schema §2.6 columns on acceptance table; join table beside acceptance; units-only digest store | Yes — schema home |
| **OQ-563-2** | Mutate legacy UNIQUE vs keep legacy path forever | Keep `accepted_quote_id` UNIQUE; never drop it for multi-sub documents (#668 Fork 4 alt rejected) | Drop UNIQUE and insert one legacy row per subscription | Yes — constraint / load risk |
| **OQ-563-3** | Per-subscription grain on the document ledger | One unit / one ledger row per target subscription; no `subscription_id` column on ledger PK (#668 Fork 4) | Add `subscription_id` to ledger key; collapse multiple subs into one ledger row | Yes — AC-S4-1 shape |
| **OQ-563-4** | `QuoteAcceptedEvent` amendment identity | Keep optional `amendmentDocumentId` + per-line `applicationUnitNo`; null = legacy; document apply does not depend on mutating the acceptance table | Require non-null document id for all accepts; omit fields and infer only from CAM document aggregate | Yes — event contract / S6 |
| **OQ-563-5** | Rate-period provenance token vs amendment-line link | Prefer existing V1_7 line link + document-path token already written as `DOCUMENT_APPLY`; treat issue `AMENDMENT_APPLIED` as **lifecycle event** name (catalog), not a required rate-period string (#668 Fork 10 aligned) | Force rate-period `provenance = AMENDMENT_APPLIED`; keep only `ACCEPTED_*` on document path | Yes — audit / reporting vocabulary |
| **OQ-563-6** | Interaction fence with DRG-536 / 559 / 562 | Cite only: multi-line (536) and restructure (559) consume the closed ledger home; executed gate stays on DRG-562 / #668 Fork 1 — **not decided here** | Bundle gate + ledger into one impl story before forks close | Yes — story boundaries |
| **OQ-563-IMPL** | Implementation / migration / flag / S4 enablement | Separate owner-authorized impl story **after** OQ-563-1..6 (or deferred subset); additive only; **no** V1_16–V1_21 collision; **no** DRG-625 enablement; **no** asset-hold bypass; **no** S4 Linear Done from this brief | Piggy-back schema on reserved draft slots; enable apply endpoint or lift asset hold from this story | Owner release; S4; holds |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (residuals only)

### OQ-563-1 — Ledger home (bypass vs columns / join table)

**Status: Proposed.** Maps to #668 Fork 3 and the gate §3 “bypass, not mutation” sentence. Does **not** reopen OQ5 A.

| Option | What the owner would be choosing |
|---|---|
| **A. Document ledger bypass; legacy acceptance unchanged (Recommended, Proposed)** | Multi-subscription document apply records committed units on `document_apply_ledger` (shape already described by the gate / present as V1_12 on this `main`). `applied_quote_acceptances` stays the legacy quote path. Schema §2.6 column sentence is **retired as a competing first-slice path**. |
| **B. Nullable columns on `applied_quote_acceptances` (schema §2.6)** | Add `amendment_document_id` + `application_unit_no` (and likely relax or replace UNIQUE). Load-bearing constraint change under the acceptance path. |
| **C. Join table between documents/units and acceptance rows** | Spike §5 alternative. Still mutates the “one quote → one acceptance” mental model; needs a version **not** colliding with V1_16–V1_21. |
| **D. Units table digest only; no document ledger** | Leaves the reserved/gate ledger unused. Conflicts with technical-approach [I] marking unless the owner explicitly supersedes it. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.  
**Schema NO-GO** until closed. This brief writes **no** migration and assigns **no** Flyway version.

### OQ-563-2 — Legacy UNIQUE constraint

**Status: Proposed.** Maps to #668 Fork 4 alternative “drop uniqueness”.

| Option | What the owner would be choosing |
|---|---|
| **A. Keep `accepted_quote_id` UNIQUE forever for legacy (Recommended, Proposed)** | Document multi-sub path never inserts multiple acceptance rows for one quote. |
| **B. Drop UNIQUE; one legacy row per subscription** | Directly contradicts the gate’s “avoids altering a UNIQUE constraint under load” rationale. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-563-3 — Per-subscription grain

**Status: Proposed.** Maps to #668 Fork 4 default; depends on unchanged OQ5 A (one unit per target subscription).

| Option | What the owner would be choosing |
|---|---|
| **A. `(document_id, unit_no)` only; subscription via unit target_ref (Recommended, Proposed)** | Several subscriptions → several units → several ledger rows under one `document_id`. |
| **B. Add `subscription_id` to the ledger key** | Different shape from the gate paragraph; would need an additive migration **after** reserved draft slots clear — still NO-GO here. |
| **C. One ledger row for the whole document** | Breaks per-unit atomic apply / partial-apply accounting. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.  
Does **not** adopt [#824](https://github.com/drgaciw/open-crm-cpq/pull/824) recovery OQs or [#827](https://github.com/drgaciw/open-crm-cpq/pull/827) membership semantics.

### OQ-563-4 — `QuoteAcceptedEvent` amendment identity

**Status: Proposed.** Issue asked to add `amendmentDocumentId` / `applicationUnitNo`. Fields exist as optional on this `main`; residual is **contract semantics**, not a silent “Done”.

| Option | What the owner would be choosing |
|---|---|
| **A. Optional fields; null means legacy; document apply identity is the document aggregate + ledger (Recommended, Proposed)** | Matches current DTO comments. Does not require acceptance-table mutation. S6 publication / routing flags stay a separate owner gate (#668 Fork 5 / DRG-584). |
| **B. Require non-null `amendmentDocumentId` on every accept once S6 is on** | Forces quote→document linkage for all new accepts; still does not authorize flag enablement here. |
| **C. Remove / ignore event fields; CAM-only identity** | Weakens cross-service audit the issue named. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.  
Closing A does **not** enable S6 routing or DRG-625.

### OQ-563-5 — Provenance token vs amendment-line reference

**Status: Proposed.** Maps to #668 Fork 10 and the issue’s `AMENDMENT_APPLIED` suggestion. Distinguish **rate-period provenance string** from **reserved lifecycle event** `AmendmentApplied`.

| Option | What the owner would be choosing |
|---|---|
| **A. Line link columns + document-path provenance `DOCUMENT_APPLY`; keep `AMENDMENT_APPLIED` as reserved lifecycle event only (Recommended, Proposed)** | Affirms V1_7 `amendment_document_line_id` / `revision_no` as the amendment-line audit link. Aligns with #668 Fork 10’s “no new provenance token required for the link,” while acknowledging the landed document-path string differs from `ACCEPTED_*`. |
| **B. Rate-period `provenance = AMENDMENT_APPLIED`** | Matches issue wording literally; vocabulary collision with the reserved event type name unless the owner accepts dual use. |
| **C. Document path keeps only `ACCEPTED_PRICED` / `ACCEPTED_PRORATED`** | Pure #668 Fork 10 default; would imply rolling back or forbidding `DOCUMENT_APPLY` — **not** authorized by this brief. |

**Recommended (Proposed):** A as the Decision-scoring baseline (inventory of landed `DOCUMENT_APPLY` + V1_7 link). **This brief does not adopt or authorize provenance code changes.** `Owner decision: ___ (drg, time)`.

### OQ-563-6 — Fence vs DRG-536 / 559 / 562

**Status: Proposed.**

| Option | What the owner would be choosing |
|---|---|
| **A. Ledger home closes on DRG-563; executed gate on DRG-562; multi-line/restructure consume both (Recommended, Proposed)** | Keeps story boundaries. Cite [#834](https://github.com/drgaciw/open-crm-cpq/pull/834) / [#835](https://github.com/drgaciw/open-crm-cpq/pull/835) ChangeLine/dates drafts without adopting their OQs. |
| **B. Single combined impl story before residual OQs close** | Couples unrelated owner forks; blocks scoring clarity. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-563-IMPL — Implementation gate

**Status: Proposed.**

| Option | What the owner would be choosing |
|---|---|
| **A. Separate impl story only after OQ-563-1..6 (or named deferred subset) close; additive schema only; no reserved-slot collision; no DRG-625; no asset-hold bypass; no S4 authorization from this file (Recommended, Proposed)** | Matches docs-only Wave-2 residual pattern. |
| **B. Treat landed V1_12 / apply orchestrator as Linear Done for DRG-563 without owner OQ close** | Rejected by the 2026-10-17 In Review correction pattern on this issue (design merge ≠ story deliverable). |
| **C. Issue a migration in V1_16–V1_21 range from this story** | **Hard NO-GO** — slots reserved on #805–#812. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Out of scope (explicit)

- Product code, tests-as-authorization, Flyway/SQL, flags, dual-read, Jev, ramp enable.
- DRG-625 apply endpoint enablement; asset-lifecycle / asset-write hold bypass.
- Reopening #668 closed cells; accepting ADR-017 / ADR-024–027 as prerequisites; accepting DRG-609 wholesale.
- Adopting open OQs from [#824](https://github.com/drgaciw/open-crm-cpq/pull/824), [#827](https://github.com/drgaciw/open-crm-cpq/pull/827), [#834](https://github.com/drgaciw/open-crm-cpq/pull/834), [#835](https://github.com/drgaciw/open-crm-cpq/pull/835).
- Authorizing [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) S4 product landing or marking Linear DRG-563 Done.
- Changing OQ5 A partial-apply remedy; mid-flight recovery contracts (cite #824 only).

## What accepting this brief would unblock

Documentation clarity only: implementation stories may cite **OQ-563-1..6** and **OQ-563-IMPL** as the residual owner menu without re-litigating the RA-29 problem statement or reopening #668’s closed cells. Accepting / merging this file **does not** open an implementation story, does not add or alter a migration, does not enable apply, and does not move Linear out of **In Review**.

## Unverified

- Live database contents outside this tree (whether every environment has V1_12 applied).
- Full Linear body / DoD checklist of DRG-582 beyond gate citations.
- Whether draft PRs #805–#812 will keep V1_16–V1_21 numbers through merge (slots treated as reserved **now**).
