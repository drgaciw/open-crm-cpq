# DRG-554 — Co-termination policy and agreement merge: residual Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554/co-termination-policy-and-agreement-merge) (RA-20).

**Status:** Proposed residual Decision brief. This file **restates Adopted Cluster A forks 554-F1..554-F8** (delegated 2026-10-17 baseline defaults) without reopening them, **cites** the co-termination proposal, DRG-638 day-stub fixtures ([#699](https://github.com/drgaciw/open-crm-cpq/pull/699)), OQ7 thin-contract / S7 backfill wording, and related Wave-2 drafts, and surfaces **remaining owner forks as Proposed OQ-554-\***. **It authorizes no production code path, no schema change, no Flyway migration (including CAM slots V1_16–V1_21 reserved by [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), no inventing of a landed `CoTermPolicy` table or `mergedFromIds` column, no feature flag, no Kafka consumer registration, no S4 apply enablement, no S7 backfill run, no asset-write path, no dual-read / Jev / ramp flip, and no deploy.** Product / schema / flag work that implements co-term modes, agreement merge, or regrouping is **NO-GO** until the owner closes the residual OQ-554-\* forks below (or explicitly defers a named fork with words and a time).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`.

**Linear status when read:** In Review (not Done). This brief leaves that status unchanged.

## Alignment (cite; do not reopen Adopted 554-F1..F8)

| Artifact | Relation to this brief |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Cluster A 554-F1..F8 | **Adopted baseline** (“Adopt baseline default” for every 554-F\* row). Cite; **do not reopen**. |
| [cluster-a-amendment-modes-and-policy-decision-frame.md](cluster-a-amendment-modes-and-policy-decision-frame.md) § DRG-554 | Historical options / rationale for 554-F1..F8; dispositions already indexed. Do not re-litigate Adopted cells |
| [specs/co-termination.md](specs/co-termination.md) | **Proposal only; not accepted.** Mode contract sketch, merge identity sketch, open questions 1–9, Scenario 4 acceptance text. Authorizes no code |
| DRG-638 fixtures under [specs/fixtures/co-termination/](specs/fixtures/co-termination/) ([#699](https://github.com/drgaciw/open-crm-cpq/pull/699)) | **Docs-only day-stub worked examples** (same-end, earlier-end, later-end, merge-of-2). Amounts follow Accepted DRG-603 day-stub. Fixture `coTermMode` / merge disposition rows stay **Proposed** |
| [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) D-01 | **Accepted** day-stub. This brief chooses which **end date** feeds that formula and does **not** reopen it |
| Mid-term [V5](../mid-term-amendment-policy-spec.md) + FR-11 | Single-agreement amendment co-terms to the **existing** end unless a co-term policy says otherwise — the policy this story owns |
| [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md) + `V1_8__contract_header_backfill.sql` | **Adopted OQ7** first-slice: one thin contract per subscription; **“A later merge, tracked on DRG-554, is the place to regroup. The backfill does not regroup.”** Regrouping remains a follow-up option only under Adopted 554-F6 |
| [oq7-header-backfill-owner-decision.md](oq7-header-backfill-owner-decision.md) | Records that a `subscriptions.contract_id` retarget on merge is **`unverified` / not specified** by co-term proposal text |
| [renewal-policy-stub.md](../renewal-policy-stub.md) clarification 2026-10-16 | Call-decision coterm notes (blended / rates-carry-forward, all-or-nothing, renewal group). **Not** an accepted policy; residual forks below |
| [renewal-policy-spec.md](renewal-policy-spec.md) RN1 / AC-RN-03 | Co-term **anchor** replaces day-after-end; anchor meaning stays DRG-554’s |
| [story-drg-536-multi-line-amendment-design-note.md](story-drg-536-multi-line-amendment-design-note.md) 536-F3 | Multi-line consequence of Adopted 554-F1/F2/F3 (one document co-term mode; only lines in the change). Cite; do not reopen 536 forks |

**In-flight alignment (cite-only Wave-2 drafts — do not adopt their open OQs):**

| Ticket / PR | What this brief must not contradict / adopt |
|---|---|
| [DRG-553](https://linear.app/drgamtd-workspace/issue/DRG-553) [#835](https://github.com/drgaciw/open-crm-cpq/pull/835) | Effective vs action dates residual. Co-term moves **end** dates; it does not invent a fifth header trigger or reopen Adopted Cluster E D1–D6 / OQ-553-\* |
| [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) | Partial-apply residual. A merge that spans subscriptions is an apply-unit consumer of Adopted OQ5; **do not adopt** OQ-565-\* |
| [DRG-557](https://linear.app/drgamtd-workspace/issue/DRG-557) [#838](https://github.com/drgaciw/open-crm-cpq/pull/838) | Capacity-meter residual sibling. Cite lane only; no meter/true-up OQs adopted here |
| [DRG-562](https://linear.app/drgamtd-workspace/issue/DRG-562) / [DRG-563](https://linear.app/drgamtd-workspace/issue/DRG-563) [story-drg-562-563-gate-apply-and-ledger-design-note.md](story-drg-562-563-gate-apply-and-ledger-design-note.md) | Apply gate + multi-subscription ledger design note (Proposed). Merge / multi-agreement apply units stay behind S4 authorization — cite only |
| [DRG-461](https://linear.app/drgamtd-workspace/issue/DRG-461) | BC-07-04 capability home (Adopted 554-F8 link). No second co-term implementation; no `ContractAmended` event invented here |
| [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576) [#831](https://github.com/drgaciw/open-crm-cpq/pull/831) | Catalog eligibility / proposed `coTermable`. Cite; do not adopt open 576 OQs |
| Dual-read / NgRx / Jev / ramp / S4 / asset-write | `AGENT.md` holds — untouched |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** No `opencrm.jev.*.enabled` flag is turned on. `opencrm.pricing.ramp.enabled` stays default off. An implementation brief for DRG-554 (or any product change that persists `CoTermPolicy`, writes `mergedFromIds`, retargets `subscriptions.contract_id`, or enables multi-agreement co-term apply) is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `GitNexus` | This checkout has **no** `.gitnexus/run.cjs` / no indexed repo for MCP `query`/`impact`. Graph impact is **UNKNOWN** (unresolved), not a clean check. `detect_changes` was not a clean zero. |

## Problem (RA-20)

Research gap (Salesforce CPQ Latest/Earliest End Date and Disable Amendment Co-Term; SAP Change Process Groups Co-Term and Merge; DealHub / Maxio market co-terming with proration; Chargebee / Salesforce CPQ renewals with Combine Subscription Quantities): OpenCRM has **no configurable co-termination policy** and **no accepted agreement-merge** path that aligns end dates across agreements / subscriptions, snapshots the chosen mode, and (when merge is in scope) preserves survivor identity.

What already exists on `main` (`read` / `verified by reading code`):

- Mid-term **V5** / FR-11: amended lines co-term to the **existing** agreement end unless a co-term policy says otherwise.
- Amendment documents store a nullable `co_term_date` column (`V1_7__contract_documents.sql`) — a date field, **not** a mode enum or policy table.
- Adopted OQ7 / S7: one thin contract per subscription; regrouping deferred to “a later merge, tracked on DRG-554”; backfill does not regroup.
- Docs-only co-termination proposal + DRG-638 day-stub fixtures (#699) with **Proposed** mode / merge dispositions.
- Cluster A 554-F1..F8 **Adopted** as baseline defaults (mode set including `AlignToDate`, missing-policy split, only-lines-in-change, shortening without credit from this policy, mid-term refuse lengthening past governing end, no regrouping selected, source stays reference, link DRG-461).

What is still missing for product:

1. Residual commercial forks the delegated baseline did **not** close (customer all-or-nothing vs document line set; rate blended vs carry-forward; renewal group; mid-period anchor vs day-stub end constraint; storage shape for mode + merge identity).
2. Explicit **NO-GO** on inventing schema (`CoTermPolicy`, `mergedFromIds`, `contract_id` retarget) or consuming reserved CAM Flyway **V1_16–V1_21**.
3. Cite-only boundaries with Wave-2 siblings (dates #835, partial-apply #839 / capacity #838, apply-gate 562/563) so a later story does not silently adopt their open OQs.

Without that split, a later PR could re-litigate 554-F\*, treat #699 fixtures as Adopted mode selection, invent merge regrouping despite Adopted 554-F6, collide with reserved Flyway slots, or claim this brief authorized S4 / asset-write.

Linear DRG-554 (`Linear read` 2026-11-06): Medium; **In Review**; related DRG-591, DRG-461, DRG-638, DRG-607, DRG-585, DRG-547. Attachment: Cluster A draft [#650](https://github.com/drgaciw/open-crm-cpq/pull/650) (merged; dispositions Adopted via the delegated record). Wave-2 refill comment records docs-only residual Decision brief scope with product/schema/flag **NO-GO**.

## Market research — cited as forks only

Issue research links are orientation only. They are **not** selected options and do not override the delegated record.

| Vendor pattern | Observed options (research) | How this brief treats them |
|---|---|---|
| Salesforce CPQ Latest / Earliest End Date; Disable Amendment Co-Term | Enum of end-date rules; ability to turn co-term off | Orientation for Adopted 554-F1 modes + `None` — not a Salesforce field map |
| SAP CPQ/BRIM Change Process Groups Co-Term and Merge | Align dates and/or fold agreements | Split already Adopted: co-term dates vs merge/regroup follow-up (554-F6) |
| DealHub / Maxio co-term + proration | Shared end with money math | Dates here; money stays Accepted day-stub (DRG-603) — formula not reopened |
| Chargebee / SFDC Combine Subscription Quantities | Quantity combine on renew | Out of this story; quantity combine is not selected here |

## Already Adopted (do not re-decide)

Restated from the delegated record § Cluster A 554-F1..F8 and OQ7 / S7 (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | **Mode set** = DRG-554’s four (`LatestEnd`, `EarliestEnd`, `None`, `AlignToAgreement`) **plus** `AlignToDate` | 554-F1 baseline B |
| A2 | **Missing policy:** single-agreement amendment keeps mid-term V5; multi-agreement change with no mode is **refused** | 554-F2 baseline C |
| A3 | **Which lines move:** only lines **in the change**; untouched lines keep their end | 554-F3 baseline B |
| A4 | **Shortening:** end may move earlier under `EarliestEnd` / earlier anchor; **this policy writes no credit** (credit, if any, stays DRG-550 / sibling money stories; conversion price pending DRG-609) | 554-F4 baseline B |
| A5 | **Lengthening past governing end:** mid-term co-term **refuses** an end after the governing contract end (renew / new-contract mode are the exits) | 554-F5 baseline B |
| A6 | **Merge vs co-term / OQ7:** Adopted OQ7 stays — one thin contract per subscription; backfill does **not** regroup; **regrouping is not selected**. Co-term still aligns dates across agreements that remain separate | 554-F6 + S7 companion |
| A7 | **Source after merge** (if a later merge is ever selected): source stays active as a **reference** until a later executed close — not cancelled/deleted as a side effect of the pointer | 554-F7 baseline A |
| A8 | **Home:** DRG-461 stays the BC-07-04 capability item; this brief is the policy residual frame — extend/link, do not duplicate; no `ContractAmended` event invented here | 554-F8 baseline A |
| A9 | Proration method stays the **Accepted** day-stub (DRG-603 / #623). Co-term chooses the **resulting end**; it does not add a third formula | Proration ADR; #699 fixtures |
| A10 | Holds: no product code / Flyway / dual-read / Jev / ramp / S4 / asset-write from this brief; S3 remains separately authorized | AGENT.md; Cluster A holds |

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No new Adopted cells in this table. Picking a row authorizes **no** code, migration, flag, S4 enablement, S7 backfill, asset-write, inventing `CoTermPolicy` / `mergedFromIds`, or deploy.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| **OQ-554-1** | **Customer all-or-nothing vs document line set** (call-decision stub vs A3) | Keep **A3** for amendment documents (only lines in the change). Treat call-decision “all-or-nothing per customer” as a **separate residual** for a future **customer-level coterm event / renewal grouping** story — not a silent override of A3. Do not invent a second date engine (rejected 554-F1-C) | Make every coterm event force all customer contracts now (conflicts with A3 unless owner supersedes); drop customer-level grouping forever | Yes — multi-contract renewal coterm ACs |
| **OQ-554-2** | **Rate policy when dates align** (blended vs rates-carry-forward) | Date alignment stays A1–A5. **Rates-carry-forward** as the Proposed default for commercial rates when ends move (stub clarification); **blended** remains an explicit alternate. Rate choice is **not** a co-term mode and does not reopen A4’s no-credit rule | Blended-only; seller must pick every time with no tenant default; invent a third rate engine | Soft for date-only ACs; hard for renewal coterm money ACs |
| **OQ-554-3** | **Optional renewal group** (one consolidated renewal quote) | Keep **optional** and **out of first-slice amendment co-term**. RN1 / AC-RN-03 still consume a co-term **anchor** from this story’s mode; grouping UX / quote consolidation stays a later renewal-automation fork (cite DRG-593 modes; do not adopt their OQs) | Require a renewal group whenever multi-agreement co-term runs; forbid consolidated quotes | Soft until renewal automation delivery |
| **OQ-554-4** | **Mid-period co-term anchor vs day-stub end constraint** | Keep A9. Fixtures already require resulting end = `billingAnchor.plusMonths(n).minusDays(1)`. Proposed rule: co-term **modes resolve a calendar end**; if that end is not a valid day-stub period end, **refuse at price** (or snap under a separately scored rule) — **do not** invent a third proration formula. Whether snap vs refuse is the residual | Allow mid-period ends by changing the Accepted formula (forbidden); silently day-count without whole-month rule | Yes — Scenario 4 / #699 executable pricing |
| **OQ-554-5** | **Where mode + anchor are stored / snapshotted** | Document-level snapshot of `{mode, anchorDate?, anchorAgreementId?, policyVersionId}` on the priced revision (proposal contract in `specs/co-termination.md`). Existing `co_term_date` remains the **resolved end** column already on `amendment_documents` (`verified by reading code`) — not a substitute for mode storage. **No migration in this brief**; additive shape only after forks close and **never** in reserved V1_16–V1_21 | Infer mode only from `co_term_date`; store mode only on LifecycleChange; tenant-only live mode with no snapshot (conflicts with AC-9 snapshot intent) | Yes — any public create/submit that claims mode |
| **OQ-554-6** | **Merge identity fields without selecting regrouping** | Keep **A6**: regrouping / `contract_id` retarget **not selected**. Keep #699 `merge-of-2` as a **Proposed fixture** illustrating survivor/`mergedFromIds` **if** a later owner selects merge. Until then: co-term keeps agreements **separate**; do **not** land `mergedFromIds` schema or treat merge fixture as Adopted product behavior | Select merge + `mergedFromIds` now (reopens A6); delete source rows on merge (conflicts with A7); retarget `subscriptions.contract_id` in first product PR | Yes — merge ACs; soft for date-only co-term |
| **OQ-554-7** | **Interaction with DRG-553 / #835 date clocks** | Cite only. Co-term changes line / agreement **end** (`effective_to` / contract end). It does **not** set `action_date`, invent header triggers, or reopen OQ-553-\*. Apply vs entitlement vs billing clocks stay on #835 | Fold co-term end into `action_date`; treat AlignToDate anchor as `service_activation_date` | Soft for date-only; hard if coupled to 553 wire |
| **OQ-554-8** | **Interaction with DRG-565 / #839 and capacity #838** | Cite only. Multi-agreement co-term / future merge that touches several subscriptions consumes Adopted OQ5 unit rules when S4 is authorized — **do not adopt** OQ-565-\* or OQ-557-\*. First-slice SaaS still one unit per subscription | Bundle all co-termed subscriptions as one ApplicationUnit now; start partial-apply product for merge now | Soft until S4; hard if someone ships merge apply |
| **OQ-554-9** | **Interaction with DRG-562 / DRG-563 apply-gate note** | Cite the Proposed gate/ledger note only. Execute / apply still require separately authorized S4. Co-term policy does not gate quote acceptance and does not invent ledger rows | Gate co-term on quote accept; write ledger rows from this brief | Soft (docs); hard for apply ACs |
| **OQ-554-10** | **`coTermable` catalog flag** | Cite DRG-576 / #831. Until catalog flags land under that story’s scored forks: missing `coTermable` does **not** invent a refusal (matches Cluster A dependency note). When a true flag exists, non-co-termable lines in a moving mode fail validation (proposal AC-7); mode `None` accepts them. **Do not adopt** open 576 OQs here | Refuse all co-term when flag missing; ignore flag forever | Soft until 576 delivery |
| **OQ-554-11** | **Fixture / Scenario 4 freeze** | Keep DRG-638 / #699 fixtures and `specs/co-termination.md` acceptance rows **`Status: Proposed`** until an implementation brief. Do not rename public error codes here | Treat #699 amounts as Adopted product oracles now; add gateway co-term routes in this brief | Soft (docs) |
| **OQ-554-12** | **Approver for coterm events** | Stay **parked on DRG-591** / PL-9. No approver role invented here | Sales self-approve; invent legal role in this brief | Soft until approvals unpark |
| **OQ-554-IMPL** | **Implementation / migration / flag / backfill authorization** | Separate implementation story **after** OQ-554-1..12 (or a deferred subset) close. Additive CAM migrations only if needed; **never** consume reserved **V1_16–V1_21**; never edit landed V1_7 / V1_8 in place; **do not** run S7 backfill; **do not** retarget `contract_id`; **do not** enable S4 apply / asset-write / dual-read / Jev / ramp from this brief | Piggy-back migrations in V1_16–V1_21; run S7 now; flip apply flags before forks close | Owner release; S3/S4 gates; AGENT.md holds; #805–#812 reservation |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (residuals only)

### OQ-554-1 — Customer all-or-nothing vs document line set

| Option | What the owner would be choosing |
|---|---|
| **A. Keep A3 for documents; park customer-level all-or-nothing as a later event scope (Recommended, Proposed)** | Honors Adopted 554-F3. Call-decision “all-or-nothing per customer” needs its own scored story before product forces every contract. |
| **B. Force all customer contracts on every coterm** | Conflicts with A3 unless the owner supersedes with words + time. |
| **C. Drop customer-level grouping forever** | Closes the stub note’s renewal-group path early. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-554-2 — Rate policy when dates align

| Option | What the owner would be choosing |
|---|---|
| **A. Rates-carry-forward default; blended alternate; dates stay A1–A5 (Recommended, Proposed)** | Matches renewal-policy-stub clarification without reopening A4 no-credit. |
| **B. Blended-only** | Commercial alternate as sole path. |
| **C. No tenant default; seller picks every time** | Operator burden; still needs storage (OQ-554-5). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-554-3 — Optional renewal group

| Option | What the owner would be choosing |
|---|---|
| **A. Optional; out of first-slice amendment co-term (Recommended, Proposed)** | Anchor still feeds RN1; consolidation is later. |
| **B. Required whenever multi-agreement co-term runs** | Couples quote UX to date policy. |
| **C. Forbid consolidated renewal quotes** | Closes stub path. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-554-4 — Mid-period anchor vs day-stub end constraint

| Option | What the owner would be choosing |
|---|---|
| **A. Resolve calendar end; refuse (or separately snap) if not a valid day-stub period end; no third formula (Recommended, Proposed)** | Preserves A9 / #699. Snap vs refuse still needs owner words if snap is desired. |
| **B. Change Accepted day-stub to allow arbitrary mid-period ends** | Forbidden — reopens proration ADR. |
| **C. Ignore period-end constraint** | Breaks `QuoteContinuationPolicy.plan` preconditions cited by fixtures. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-554-5 — Mode + anchor storage / snapshot

| Option | What the owner would be choosing |
|---|---|
| **A. Document snapshot of mode/anchors/version; `co_term_date` = resolved end; migration later outside reserved slots (Recommended, Proposed)** | Matches proposal AC-9 snapshot intent; uses existing date column for the result only. |
| **B. Infer mode from `co_term_date` alone** | Cannot distinguish LatestEnd vs AlignToDate when values coincide. |
| **C. LifecycleChange-only mode with no document snapshot** | Conflicts with priced-revision immutability expectations after execute. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-554-6 — Merge identity without regrouping

| Option | What the owner would be choosing |
|---|---|
| **A. Keep A6; merge-of-2 fixture stays Proposed illustration only (Recommended, Proposed)** | No `mergedFromIds` schema; no `contract_id` retarget; agreements stay separate under co-term. |
| **B. Select merge + land `mergedFromIds` now** | Reopens A6; still needs A7 source disposition and S4 apply. |
| **C. Retarget `subscriptions.contract_id` in the first product PR** | Explicitly out of OQ7 first slice; `unverified` in oq7 note. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-554-7 — DRG-553 / #835 clocks

| Option | What the owner would be choosing |
|---|---|
| **A. Cite only; co-term owns ends, not action/trigger clocks (Recommended, Proposed)** | No adoption of OQ-553-\*. |
| **B. Couple AlignToDate to a header trigger** | Reopens Cluster E / #835. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-554-8 — Partial-apply / capacity siblings

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #839 / #838 only; no adoption of their OQs (Recommended, Proposed)** | Merge/multi-sub apply waits on S4 + those briefs’ scored forks. |
| **B. Ship merge as one ApplicationUnit now** | Invents apply product without authorization. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-554-9 — Apply-gate / ledger (562/563)

| Option | What the owner would be choosing |
|---|---|
| **A. Cite Proposed design note; S4 stays unauthorized (Recommended, Proposed)** | No ledger rows from this brief. |
| **B. Gate or ledger from this story** | **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-554-10 — `coTermable`

| Option | What the owner would be choosing |
|---|---|
| **A. Defer to DRG-576; missing flag ≠ refusal; true flag enforces AC-7 later (Recommended, Proposed)** | Cite #831; do not adopt 576 OQs. |
| **B. Refuse co-term when flag missing** | Invents catalog behavior. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-554-11 — Fixture freeze

| Option | What the owner would be choosing |
|---|---|
| **A. Keep #699 / Scenario 4 Proposed until implementation brief (Recommended, Proposed)** | Soft docs gate. |
| **B. Treat fixtures as Adopted product oracles / add gateway routes now** | Premature public contract; gateway **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-554-12 — Approver

| Option | What the owner would be choosing |
|---|---|
| **A. Park on DRG-591 (Recommended, Proposed)** | Matches stub + Adopted A8 parking. |
| **B. Invent approver here** | Out of scope. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-554-IMPL — Implementation gate

| Option | What the owner would be choosing |
|---|---|
| **A. Separate impl after residual OQx close; no reserved slots; no S7; no regroup; no flags (Recommended, Proposed)** | Hard **NO-GO** on product/schema/flag until then. |
| **B. Use CAM V1_16–V1_21 now** | Violates #805–#812 reservation. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit) — NO-GO

Until CPQ Decision scores OQ-554-1..OQ-554-12 and OQ-554-IMPL (owner fills `Owner decision: ___ (drg, time)` or explicitly defers a named fork the same way):

1. **NO-GO:** product code (Java / TypeScript) that resolves co-term modes, writes merge identity, retargets `subscriptions.contract_id`, or enables multi-agreement co-term apply.
2. **NO-GO:** Flyway / schema for `CoTermPolicy`, `mergedFromIds`, mode enum columns, or any edit in place of landed V1_7 / V1_8. **CAM V1_16–V1_21 reserved (#805–#812).**
3. **NO-GO:** feature flags (including any `opencrm.jev.*.enabled`), outbox / relay / ramp enablement.
4. **NO-GO:** new or changed gateway routes / filters for co-term or merge semantics.
5. **NO-GO:** treating this file, Cluster A, #699 fixtures, S7 “later merge” sentence, or sibling Wave-2 drafts (#835 / #839 / #838 / 562–563 note) as authorization to ship RA-20 product behavior.
6. Even after forks close: S3/S4 remain separately authorized; dual-read / NgRx / ramp / S4 / Jev / asset-write holds stay as in `AGENT.md`; Accepted proration formula stays closed; Adopted 554-F1..F8 stay closed.

Linear status for DRG-554 stays **In Review** until the owner merges and DoD says otherwise. This brief does **not** mark Done.

## Holds this brief keeps

| Hold | What stays true |
|---|---|
| Product code | **Forbidden** in the PR that carries this file |
| Schema / Flyway | No `CoTermPolicy` / `mergedFromIds` / `contract_id` retarget migration here; **V1_16–V1_21 reserved** |
| Flag / deploy | No flag flip; no deploy |
| Gateway | No co-term/merge route or filter |
| Jev / ramp / dual-read / NgRx / S4 / asset-write | Untouched |
| S7 backfill | Not run; regrouping not selected (A6) |
| DRG-591 approvals | Parked |
| Accepted proration ADR | Not reopened |
| Sibling Wave-2 OQs | Cite only — not adopted |

## Out of scope

- Reopening Adopted 554-F1..F8 or OQ7 thin-contract first-slice wording.
- Editing the delegated decision record, `specs/co-termination.md` status line, OpenAPI, or UI spec.
- Implementing Scenario 4 ACs or running #699 fixtures against a database.
- Authorizing S3, S4, S6, billing receiver, or agreement-merge apply.
- Adopting open OQs from #835, #839, #838, or the 562/563 design note.
- Consuming CAM Flyway V1_16–V1_21.

## References

- Linear: DRG-554, DRG-461, DRG-638, DRG-585, DRG-591, DRG-553, DRG-565, DRG-557, DRG-562, DRG-563, DRG-576, DRG-607, DRG-547.
- GitHub: Cluster A frame [#650](https://github.com/drgaciw/open-crm-cpq/pull/650); co-termination day-stub fixtures [#699](https://github.com/drgaciw/open-crm-cpq/pull/699); proration accept [#623](https://github.com/drgaciw/open-crm-cpq/pull/623); dates residual [#835](https://github.com/drgaciw/open-crm-cpq/pull/835); partial-apply residual [#839](https://github.com/drgaciw/open-crm-cpq/pull/839); capacity residual [#838](https://github.com/drgaciw/open-crm-cpq/pull/838); catalog eligibility residual [#831](https://github.com/drgaciw/open-crm-cpq/pull/831); CAM reservation drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812).
- Docs: `cluster-a-amendment-modes-and-policy-decision-frame.md`, `delegated-renewals-amendments-decisions-2026-10-17.md`, `specs/co-termination.md`, `s7-v1-8-header-backfill.md`, `oq7-header-backfill-owner-decision.md`, `renewal-policy-stub.md`, `renewal-policy-spec.md`, `adr-amendment-proration-formula.md`, `mid-term-amendment-policy-spec.md`, `story-drg-536-multi-line-amendment-design-note.md`, `story-drg-562-563-gate-apply-and-ledger-design-note.md`.
- Fixtures: `specs/fixtures/co-termination/same-end.json`, `earlier-end.json`, `later-end.json`, `merge-of-2.json`.
- Code (read-only evidence): `V1_7__contract_documents.sql` (`co_term_date`), `V1_8__contract_header_backfill.sql` (OQ7; no regroup), CAM migration directory through `V1_15` (no `merged_from` / `CoTermPolicy` files).
