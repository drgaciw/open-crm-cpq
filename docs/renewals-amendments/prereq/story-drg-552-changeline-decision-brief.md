# DRG-552 — ChangeLine action codes, quantity semantics, and business categories: Decision brief (owner forks)

**Status:** Proposal only. Additive Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted Cluster E cells L1–L7 from the [2026-10-17 delegated record](delegated-renewals-amendments-decisions-2026-10-17.md) § E disposition index, does **not** reopen register **OQ12** (CONFIRMED 2026-10-16), does **not** accept [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md), does **not** adopt open OQs from the DRG-576 Decision brief ([#831](https://github.com/drgaciw/open-crm-cpq/pull/831)), does **not** adopt open OQs from the DRG-578 Decision brief ([#821](https://github.com/drgaciw/open-crm-cpq/pull/821)), does **not** reopen Adopted 594-F\* legal-review class cells, authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a route, enable Swap / Upgrade / Downgrade as first-slice submit actions, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / Cluster E ChangeLine vocabulary (RA-18). **Story:** [DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552/changeline-action-codes-quantity-semantics-and-business-categories) (RA-18). Linear read this pass: **In Review**. Notion mirror: [RA-18](https://app.notion.com/p/3fbf7cb4e4df81a59daee85949bcaea2) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen Adopted cells):**

| Artifact | Role |
|---|---|
| [cluster-e-lifecyclechange-model-decision-frame.md](cluster-e-lifecyclechange-model-decision-frame.md) § DRG-552 (L1–L7) | Historical decision frame (merged [#651](https://github.com/drgaciw/open-crm-cpq/pull/651)). Pre-decision proposal labels retained as rationale; **current authority** for L1–L7 is the delegated record |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § E disposition | **Adopted** L1–L7 = baseline defaults (L1-A … L7-A). Restated as A1–A8. Does **not** ship schema or a route |
| Register OQ12 | **CONFIRMED 2026-10-16**: first-slice actions are Add / Update / Remove only; Swap / Upgrade / Downgrade wait for RA-42 / DRG-576 |
| ADR-035 acceptance paragraph | Same OQ12 cut (read). Placement Accepted for S1/S2 only |
| [specs/lifecycle-intents.md](specs/lifecycle-intents.md) | Proposal only (incl. ChangeLine contract + phase-B fixture reading). Authorizes no code |
| [story-drg-576-catalog-eligibility-decision-brief.md](https://github.com/drgaciw/open-crm-cpq/blob/cursor/drg-576-catalog-eligibility-decision-brief-4fa7/docs/renewals-amendments/prereq/story-drg-576-catalog-eligibility-decision-brief.md) ([#831](https://github.com/drgaciw/open-crm-cpq/pull/831)) | **Cite only.** Couples when Swap / Upgrade / Downgrade may become executable after the catalog model. **Do not adopt** its open OQ1–OQ12 here |
| [story-drg-578-fulfilment-decompose-decision-brief.md](https://github.com/drgaciw/open-crm-cpq/blob/cursor/drg-578-fulfilment-decompose-decision-brief-4fa7/docs/renewals-amendments/prereq/story-drg-578-fulfilment-decompose-decision-brief.md) ([#821](https://github.com/drgaciw/open-crm-cpq/pull/821)) | **Cite only.** Couples ChangeLine `action_code` → fulfilment operation selection. **Do not adopt** its open OQs here |
| [story-drg-575-arr-bridge-per-change-line-decision-frame.md](story-drg-575-arr-bridge-per-change-line-decision-frame.md) | ARR / analytics decomposition beside one category. Cite overlap; do not close its forks here |
| [story-drg-560-594-governance-routing-decision-frame.md](story-drg-560-594-governance-routing-decision-frame.md) | Legal-review classes consume action / category evidence. Cite Adopted 594-F\*; do not reopen |

**In-flight alignment (do not contradict Adopted; do not enable held actions):**

| Ticket | Linear | What this brief must not contradict |
|---|---|---|
| [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576) | Related | Catalog relationship model; OQ12 wait; cite #831, do not adopt its open OQs |
| [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) | Related | Legal-review-free classes (pricing / term / seats). Action codes are inputs to classification, not a silent expand of legal-free scope |
| [DRG-536](https://linear.app/drgamtd-workspace/issue/DRG-536) | Related | Multi-line amendment; vocabulary is per line, not a second multi-line API |
| [DRG-535](https://linear.app/drgamtd-workspace/issue/DRG-535) / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) | Related | Amendment document / S3; this brief does not start S3 |
| [DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575) | Related | ARR bridge beside one `business_category`; cross-sell vs upsell is bridge, not a second category column |
| [DRG-578](https://linear.app/drgamtd-workspace/issue/DRG-578) | Related | Fulfilment decompose (#821); action → op map stays there |
| [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583) | Related | S5 LifecycleChange aggregate; phase A already on `main` (evidence below). This brief authorizes no further product work |
| [DRG-645](https://linear.app/drgamtd-workspace/issue/DRG-645) | Related | Phase-B reading of ChangeLine vocabulary; stays proposal text in `lifecycle-intents.md` |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, apply-gate wiring, or deploy.** No `opencrm.jev.*.enabled` flag is turned on. Dual-read and ramp holds are untouched. Swap / Upgrade / Downgrade stay **non-executable** at first-slice submit (OQ12 / A2). An implementation brief that retokens enums, widens submit, or changes category derivation is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Selected in the 2026-10-17 delegated record (L1–L7 baseline defaults). Restated; **not** re-opened. |
| `Adopted (OQ12)` | Register OQ12 confirmed 2026-10-16. Restated; **not** re-opened. |
| `Status: Proposed` | Residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |

## Problem (RA-18)

Continuation historically lacked a scored owner split between (a) the **already Adopted** ChangeLine storage / first-slice cut and (b) **residual delivery forks** still needed before further product, schema, or flag work: completing how the stored `actionCode` enum relates to executable submit, when `quantitySemantics` `REPLACE` may be used, who may widen the `businessCategory` set, how categories map into analytics / ARR, and sequencing against DRG-576 and DRG-594.

Without that split, an implementation story could shrink or expand `chk_amendment_line_action`, enable Swap / Upgrade / Downgrade without catalog + separate authorization, invent compound categories, treat `REPLACE` as a free-form delta omit, or silently adopt #831 / #821 open OQs.

Linear DRG-552 (`Linear read` 2026-11-06): Medium; **In Review**; relations include DRG-576, DRG-536, DRG-594, DRG-575, DRG-583, DRG-535/581 cluster, DRG-645. Attachments: Cluster E frame PR #651 (merged). Wave-2 lane refill comment assigns this residual Decision brief (docs-only; product code NO-GO).

## Already Adopted (do not re-decide)

Restated from the delegated record § E (L1–L7 = baseline defaults), register OQ12, and Cluster E § DRG-552 (`read`). This Decision brief does **not** reopen these rows. Baseline defaults = Cluster E recommendations **L1-A … L7-A**.

| ID | Contract | Source |
|---|---|---|
| A1 | Closed **stored** action-code set remains the twelve tokens in `chk_amendment_line_action` / `ActionCode.java`: `ADD`, `UPDATE`, `REMOVE`, `NO_CHANGE`, `RENEW`, `UPGRADE`, `DOWNGRADE`, `SWAP`, `CONVERT_METRIC`, `SUSPEND`, `RESUME`, `TERMINATE`. Research names map onto them; no `SURRENDER` / `CANCEL` / `DELETE` migration; check is not shrunk to three | **Adopted (delegated)** L1-A |
| A2 | **Executable first-slice submit** accepts `ADD`, `UPDATE`, `REMOVE` only, with `DELTA` or `ABSOLUTE`. `SWAP` / `UPGRADE` / `DOWNGRADE` wait on RA-42 / DRG-576 and remain rejectable as `ACTION_NOT_SUPPORTED_IN_SLICE`. Other stored tokens stay stored-only until their own stories | **Adopted (delegated)** L2-A + **Adopted (OQ12)** |
| A3 | Quantity identity keeps `chk_amendment_line_qty`: `start_qty + delta_qty = end_qty` and `end_qty >= 0` for **every** semantics value, including stored `REPLACE`. Callers send all three numbers. `REPLACE` stays in the enum and is **not** a first-slice semantics | **Adopted (delegated)** L3-A (+ L2-A semantics cut) |
| A4 | Exactly one stored `business_category` from the closed check. First-slice writer **derives** category from action and the sign of `delta_qty` (Cluster E L4 table). Seller does not type the category. No compound / `CONVERSION` / `OTHER` migration in that default | **Adopted (delegated)** L4-A |
| A5 | L4 derivation table (Adopted with A4): `ADD` → `UPSELL`; `UPDATE` & `delta_qty > 0` → `UPSELL`; `UPDATE` & `delta_qty < 0` → `DOWNSELL`; `UPDATE` & `delta_qty = 0` → `TERMS_AND_CONDITIONS`; `REMOVE` → `CANCELLATION`. Other category tokens stay in the check and are **not** assigned by that writer | **Adopted (delegated)** L4-A table |
| A6 | No new SQL intent→action trigger. Combined with Cluster E E7-A, the only pair a future first-slice submit allows is intent `AMEND` with `ADD` / `UPDATE` / `REMOVE` | **Adopted (delegated)** L5-A |
| A7 | Leave `chk_amendment_line_source_required` as written (`UPDATE` / `REMOVE` / relationship actions require source; `ADD` does not). No widen / drop migration in the default | **Adopted (delegated)** L6-A |
| A8 | Write **no** conversion line in the first slice; add no `CONVERSION` category token; DRG-573 chooses later | **Adopted (delegated)** L7-A |

### Landed evidence on `main` (not new authorization)

Facts `verified by reading code` at the base SHA. They show OQ12 / L2 / L4 direction already partially present in quote-service. They do **not** close the Proposed OQs below and do **not** authorize further product work from this brief.

| Fact | Evidence |
|---|---|
| CAM stores the twelve action tokens, three quantity semantics (`DELTA` / `ABSOLUTE` / `REPLACE`), and twelve business categories | `V1_7__contract_documents.sql` checks; `ActionCode.java`, `QuantitySemantics.java`, `BusinessCategory.java` (`read`) |
| Quote-service `LifecycleChangeService` create/store allows `STORABLE_ACTIONS` = `ADD`, `UPDATE`, `REMOVE`, `SWAP`, `UPGRADE`, `DOWNGRADE`; create rejects other action codes | `LifecycleChangeService.java` (`read`) |
| Submit rejects `SWAP` / `UPGRADE` / `DOWNGRADE` with `ACTION_NOT_SUPPORTED_IN_SLICE` | Same file, `UNSUPPORTED_AT_SUBMIT` (`read`) |
| Create accepts only `DELTA` or `ABSOLUTE`; enforces `start + delta = end` for `DELTA` only (Absolute may store unequal triple at create — see OQ2) | Same file, `prepareLines` (`read`) |
| Category writer today: `REMOVE` → `CANCELLATION`; `UPDATE` & negative delta → `DOWNSELL`; else `UPSELL` (does **not** yet assign `TERMS_AND_CONDITIONS` for zero-delta `UPDATE`) | Same file, `category(...)` (`read`) — residual vs A5 under OQ3 |

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells in this table. A1–A8 stay closed.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product / schema / flag? |
|---|---|---|---|---|
| OQ1 | **actionCode enum completion** (stored twelve vs executable three vs dual-store alignment) | Keep A1 stored set + A2 executable cut; treat quote `STORABLE_ACTIONS` / CAM check dual vocabulary as an alignment debt scored here without shrinking or growing the enum; do **not** import `lifecycle-intents.md` `Delete`/`Surrender`/`Cancel` tokens | Shrink CAM check to three; migrate in proposal tokens; make all twelve executable now | Yes — Flyway / submit gate / dual writers |
| OQ2 | **quantitySemantics** (`DELTA` / `ABSOLUTE` / `REPLACE`) | Keep A3: first-slice `DELTA`\|`ABSOLUTE` only; `REPLACE` stored-only; Absolute create may keep unequal triples until an owner picks enforce-at-create vs compute-delta; do **not** relax `chk_amendment_line_qty` for `REPLACE` | Enforce Absolute identity at create; enable `REPLACE` as first-slice; omit delta for `REPLACE` (migration) | Yes — validation / migration |
| OQ3 | **businessCategory set** (writer completeness vs closed check) | Keep A4/A5/A8 closed set + derivation; close the landed-writer gap by aligning quote `category(...)` to the full A5 table (including zero-delta → `TERMS_AND_CONDITIONS`); no `CONVERSION` / compound / `OTHER` token | Seller-typed category; add `CONVERSION`/`OTHER`; leave landed UPSELL-for-zero-delta as permanent | Yes — writer / optional migration |
| OQ4 | **Analytics mapping** (category → ARR / reporting) | One stored category remains the commercial label; ARR / rollup decomposition stays on DRG-575 bridge components (cite frame; **do not adopt** its open forks); no second category column; no Salesforce RCA parity commitment | Overload category for compound renewals; add category `NON_RENEWAL`; ship RCA-shaped rollup columns here | Soft for first-slice submit; hard for analytics ACs / DRG-575 |
| OQ5 | **Sequencing vs DRG-576 / DRG-594** (and cite-only #831 / #821) | OQ12 / A2 stand: Swap / Upgrade / Downgrade stay non-executable until catalog model **and** separate S5 / owner authorization (cite #831 OQ7 as **necessary but not sufficient** — do not adopt #831 OQs); legal-review classes on DRG-594 consume A2 actions as inputs and do **not** expand legal-free scope; fulfilment op selection stays on #821 (**cite, do not adopt**) | Enable the three actions when #831 merges; expand legal-free classes from residual tokens; wire fulfilment DecompositionRule from this brief | Yes — action authorization / legal gate / fulfilment |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ1 — actionCode enum completion

| Option | What the owner would be choosing |
|---|---|
| **A. Keep twelve stored + three executable; score dual-store alignment (Recommended, Proposed)** | Affirms A1 + A2. CAM `chk_amendment_line_action` stays twelve. First-slice submit stays Add/Update/Remove. Quote-service may store Swap/Upgrade/Downgrade for later reject-at-submit (landed). Closing this OQ may require an alignment note between CAM and quote vocabularies; it does **not** authorize enabling the three relationship actions. |
| **B. Shrink CAM check to `ADD`/`UPDATE`/`REMOVE`** | Reopens L1-C (migration). Rejects A1. |
| **C. Migrate in `SURRENDER`/`CANCEL`/`CONVERT`/`DELETE` from `lifecycle-intents.md`** | Reopens L1-B. Accepts the proposal file as enum authority. Out of scope. |
| **D. Make all twelve executable now** | Contradicts OQ12 / A2 and #831 coupling. |

**Recommended (Proposed):** A. Does **not** reopen A1/A2. `Owner decision: ___ (drg, time)`.

### OQ2 — quantitySemantics (`DELTA` / `ABSOLUTE` / `REPLACE`)

| Option | What the owner would be choosing |
|---|---|
| **A. First-slice DELTA\|ABSOLUTE; REPLACE stored-only; keep qty identity check; Absolute create tolerance scored (Recommended, Proposed)** | Affirms A3. `REPLACE` stays non-executable in the first slice. CAM `chk_amendment_line_qty` continues to require the three-number identity for every stored semantics value. Landed quote Absolute create that stores an unequal triple is a residual validation gap to close toward A3 or to explicitly accept as create-time Absolute behavior — owner picks inside this OQ without relaxing `REPLACE`. |
| **B. Enforce Absolute `start + delta = end` at quote create** | Tightens landed `prepareLines` toward CAM identity for Absolute. Still leaves `REPLACE` out of first slice. |
| **C. Enable `REPLACE` as first-slice semantics** | Contradicts L2-A / A3 first-slice cut; couples to swap shape / DRG-576. |
| **D. Relax check so `REPLACE` may omit delta (migration)** | Reopens L3-B. |

**Recommended (Proposed):** A (with B as the natural hardening of Absolute create if the owner wants CAM/quote parity immediately). `Owner decision: ___ (drg, time)`.

### OQ3 — businessCategory set

| Option | What the owner would be choosing |
|---|---|
| **A. Closed set + full A5 derivation; align landed writer (Recommended, Proposed)** | Affirms A4/A5/A8. Quote `category(...)` should assign `TERMS_AND_CONDITIONS` for `UPDATE` with `delta_qty = 0`, matching the Adopted table. No new tokens. Cross-sell vs upsell for a product add remains DRG-575 (bridge), not a second category. |
| **B. Seller types category from the stored set** | Reopens L4-B. |
| **C. Add `CONVERSION` / compound / `OTHER` (migration)** | Reopens L4-C / L7-C. Conflicts with A8 for conversion. |
| **D. Leave landed UPSELL-for-zero-delta as permanent** | Diverges from Adopted A5 without an explicit supersession. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ4 — Analytics mapping

| Option | What the owner would be choosing |
|---|---|
| **A. Category is commercial label; ARR / analytics decompose on DRG-575 (Recommended, Proposed)** | Affirms one `business_category` column. Cite [story-drg-575-arr-bridge-per-change-line-decision-frame.md](story-drg-575-arr-bridge-per-change-line-decision-frame.md) for bridge components (`priceEffect`, `qtyEffect`, `crossSell`, `churn`, …). **Do not adopt** that frame's open forks here. Market research (Salesforce AssetAction categories / rollups, Conga absolute qty, DealHub -old/+new) stays illustration only. |
| **B. Overload category for compound renewal + uplift + downsell** | Rejects A4 singularity; conflicts with `lifecycle-intents.md` AC-7 proposal direction and DRG-575 overlap note. |
| **C. Add category `NON_RENEWAL` or RCA rollup columns in this story** | Schema / analytics scope creep; non-renewal disposition is DRG-575. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ5 — Sequencing vs DRG-576 / DRG-594 (cite #831 / #821)

| Option | What the owner would be choosing |
|---|---|
| **A. Necessary-but-not-sufficient unlock; legal classes consume A2; fulfilment stays #821 (Recommended, Proposed)** | Affirms OQ12 / A2. Catalog eligibility Decision brief #831 may score when Swap / Upgrade / Downgrade become *model-ready*; that is **not** sufficient to enable them (cite #831 OQ7 recommendation — **do not adopt** #831 OQ1–OQ12 wholesale). DRG-594 legal-review-free classes stay pricing / term / seats; residual stored actions do not silently become legal-free. Fulfilment operation selection remains on #821 — **cite, do not adopt** its open OQs. Multi-line carriage stays DRG-536. |
| **B. Enable Swap / Upgrade / Downgrade when #831 merges** | Over-reads OQ12 "wait for DRG-576"; contradicts #831's own necessary-but-not-sufficient recommendation. |
| **C. Expand DRG-594 legal-free classes from residual action tokens here** | Reopens legal-review scope outside DRG-594's Adopted class cells. |
| **D. Wire `DecompositionRule` / fulfilment ops from this brief** | Pulls #821 into DRG-552; out of scope. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit NO-GO)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed.
2. CPQ Decision must score **OQ1–OQ5**. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **NO-GO** on:
   - product Java/TS that retokens `ActionCode` / `QuantitySemantics` / `BusinessCategory`, widens first-slice submit beyond Add/Update/Remove, or enables Swap / Upgrade / Downgrade
   - Flyway / schema changes to `chk_amendment_line_action`, `chk_amendment_line_qty_semantics`, `chk_amendment_line_qty`, or `chk_amendment_line_category`
   - feature flags that turn on relationship actions, `REPLACE` submit, or compound categories
   - adopting open OQs from [#831](https://github.com/drgaciw/open-crm-cpq/pull/831) or [#821](https://github.com/drgaciw/open-crm-cpq/pull/821) as if scored here
4. Holds unchanged: dual-read, Jev, ramp, S4 apply-ledger reservation, no S3 start from this brief.

## Related citations (orientation only)

| Topic | Cite |
|---|---|
| OQ12 confirmed cut | `docs/renewals-amendments/renewals-amendments-open-questions-register.md` row OQ12; ADR-035 acceptance |
| Cluster E L1–L7 rationale | `cluster-e-lifecyclechange-model-decision-frame.md` § DRG-552; dispositions in delegated record § E |
| Catalog unlock residual | #831 Decision brief (cite; do not adopt open OQs) |
| Fulfilment decompose residual | #821 Decision brief (cite; do not adopt open OQs) |
| ARR / analytics beside one category | `story-drg-575-arr-bridge-per-change-line-decision-frame.md` |
| Legal-review classes | `story-drg-560-594-governance-routing-decision-frame.md`; DRG-594 |
| Multi-line / amendment document | DRG-536; DRG-535 / ADR-035 |
| Phase-B fixture reading | `specs/lifecycle-intents.md` ChangeLine section + `fixtures/lifecycle-intents/changeline-*.json` |

## Known gaps (completion review)

- GitNexus MCP had **no indexed repositories** in this environment at authoring time; orientation used `read` + text search on this checkout. Re-run `node .gitnexus/run.cjs analyze --index-only` / `detect-changes` on the docs-only diff before merge if the index is restored.
- `#831` / `#821` bodies were cited from their draft branches; open OQs on those PRs remain **unowned by this brief**.
- Landed quote Absolute create / category zero-delta behavior is evidence of residual OQ2/OQ3 work, not an Adopted supersession of A3/A5.
- `*IntegrationTest` / browser UAT / Docker paths were not run (docs-only).
