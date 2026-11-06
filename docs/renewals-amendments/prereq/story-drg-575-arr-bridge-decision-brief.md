# DRG-575 — ARR bridge per change line: Decision brief (owner forks)

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted cells from the [DRG-575 decision frame](story-drg-575-arr-bridge-per-change-line-decision-frame.md) ([#660](https://github.com/drgaciw/open-crm-cpq/pull/660), merged) as disposed in the [2026-10-17 delegated record](delegated-renewals-amendments-decisions-2026-10-17.md) § Pricing and ARR / § 575 index, does **not** accept [`specs/arr-bridge.md`](specs/arr-bridge.md), does **not** adopt open OQs from the DRG-552 ChangeLine Decision brief ([#834](https://github.com/drgaciw/open-crm-cpq/pull/834)), does **not** adopt open FQ forks from the DRG-588 CRM forecast definitions spec ([#823](https://github.com/drgaciw/open-crm-cpq/pull/823)), does **not** design or unblock DRG-600 automatic CRM re-forecast, authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a route, enable ramp / Jev / dual-read / S4, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / ARR bridge (RA-41). **Story:** [DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575/arr-bridge-per-change-line-price-quantity-cross-sell-conversion-and) (RA-41). Linear read this pass: **In Review**. Notion mirror: [RA-41](https://app.notion.com/p/3fbf7cb4e4df813eb74aeb6d28978920) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / spec authority (cite, do not reopen Adopted cells):**

| Artifact | Role |
|---|---|
| [story-drg-575-arr-bridge-per-change-line-decision-frame.md](story-drg-575-arr-bridge-per-change-line-decision-frame.md) ([#660](https://github.com/drgaciw/open-crm-cpq/pull/660), merged `0b74fef28809c36c02ca12f9d52f8d19ae12ba08`) | Historical decision frame for forks D1–D4, H1, R1, C1–C2, F1–F7, T1–T3. Pre-decision proposal labels retained as rationale; **current authority** is the delegated record § 575 |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Pricing and ARR + § 575 disposition index | **Adopted baseline** for every #660 fork (with explicit replacements for R1, C2, F4, F6, T1, T2). Restated as A1–A16 below. Does **not** ship schema or a route |
| [`specs/arr-bridge.md`](specs/arr-bridge.md) | **Proposal only.** Suggested `renewalDisposition` + `arrBridge` contract and ACs. Authorizes no code. This brief does **not** accept it |
| [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md) | Proposal: one `businessCategory`; compound renewal left to the bridge. Cite AC-7; do not accept |
| [../cpq-pricing-policy-sanity-check.md](../cpq-pricing-policy-sanity-check.md) Scenarios 3 and 4 | Illustrative labels only (examples, not customer data, not recomputed here) |
| Cluster E L4 / L7 (delegated § E) | One stored category; no `CONVERSION` category token; cross-sell vs upsell is bridge money. Do not reopen |
| [drg-609-pricing-design-acceptance-record.md](drg-609-pricing-design-acceptance-record.md) | Continuation evaluate design. Bridge response shape still needs a scored delivery contract (T1 Adopted at policy level; OpenAPI/DTO residual below) |

**Related Wave-2 drafts (cite only; do not adopt open OQs):**

| Ticket / draft | Linear / GH | What this brief must not adopt |
|---|---|---|
| [DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552) / [#834](https://github.com/drgaciw/open-crm-cpq/pull/834) | ChangeLine residual Decision brief | Open **OQ1–OQ5**. One category / first-slice Add\|Update\|Remove stand; do **not** score #834 forks here |
| [DRG-588](https://linear.app/drgamtd-workspace/issue/DRG-588) / [#823](https://github.com/drgaciw/open-crm-cpq/pull/823) | CRM forecast definitions spec | Open **FQ4–FQ10**. Forecast vocabulary only; **no** automatic re-forecast; non-overlap with DRG-600 |
| [DRG-600](https://linear.app/drgamtd-workspace/issue/DRG-600) | Automatic CRM re-forecast | Stays blocked / out of scope. Bridge storage does not push CRM |
| [DRG-573](https://linear.app/drgamtd-workspace/issue/DRG-573) / [#848](https://github.com/drgaciw/open-crm-cpq/pull/848) | Conversion pricing residual | Open **OQ-573-*** (incl. ARR figure coupling). Cite only |
| [DRG-640](https://linear.app/drgamtd-workspace/issue/DRG-640) / [#692](https://github.com/drgaciw/open-crm-cpq/pull/692) | Conversion figure decision frame (Done) | Candidate inventory. **Adopted C2-D** already selects signed recurring delta vs prior recurring ARR; do not reopen C2 |
| [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576) / [#831](https://github.com/drgaciw/open-crm-cpq/pull/831) | Catalog eligibility residual | Opaque `quantityMapping` needed for Replace / R1-C. Cite; do not adopt OQ1–OQ12 |
| [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564) / [#822](https://github.com/drgaciw/open-crm-cpq/pull/822) | Pricing-audit residual | PolicyDecision / commercial-decision trail the bridge's `policyVersionIds` must pin. Cite only |
| CAM drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) | Flyway **V1_16–V1_21 reserved** | This brief writes **no** migration and claims **no** CAM version in that band |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.pricing.ramp.enabled` stays default off. No `opencrm.jev.*.enabled` flag is turned on. Dual-read / S4 enablement / asset-write holds stay in force. An implementation brief for DRG-575 is **blocked** until CPQ Decision scores the open **OQ-575-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-575-* row stays open. **Do not invent `arr_bridge` / `renewal_disposition` schema as landed.**

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record (§ Pricing and ARR / § 575). Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read / searched in the tree at the base SHA. Not executed. |
| `verified by GitNexus` | Impact/query after a successful local index. **Unavailable this pass** — see GitNexus section. |

## Problem (RA-41)

A ChangeLine stores **one** `businessCategory`. That single value cannot express a renewal that is also uplifted and downsized, and a line that is simply not renewed has no disposition (`Remove` would wrongly invite credit or termination-fee policy). Illustrative Scenario 4 needs bridge movements for price uplift, cross-sell, maintenance uplift, list repricing, and non-renewal. A conversion (Scenario 3) must not be reported as churn plus new business. [DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552) defines categories; it does not define ARR decomposition or non-renewal disposition.

[#660](https://github.com/drgaciw/open-crm-cpq/pull/660) framed owner forks for disposition vocabulary, churn/NonRenew policy, Replace money, conversion classification, six-component bridge shape, Scenario 4 label mapping, price/quantity split, list-reprice visibility, document rollup, and compute/reprice timing. The 2026-10-17 delegation **Adopted** those forks (with replacements for R1, C2, F4, F6, T1, T2) at **policy** level.

What remains un-scored for an **implementation gate** is residual **delivery** design: where disposition + bridge persist, how the pricing response carries them, Flyway numbering outside the reserved CAM band, coupling to ChangeLine (#834) and forecast (#823) residuals without adopting their open OQs, Replace map-pin readiness, conversion fixture alignment under Adopted C2-D, FX / mixed-currency refusal, `LIST_DISCOUNT_RESET` warning home, golden rounding fixtures, override-invalidation delivery, and when first-slice bridge writes may be authorized — all as **Proposed OQ-575-*** without inventing schema as landed.

Linear DRG-575 (`Linear read` 2026-11-06): Low; **In Review**; related to DRG-552, DRG-640, DRG-607, DRG-600. Attachments include frame PR #660 (merged). Wave-2 refill comment assigns this residual Decision brief (docs-only; product/schema/flag **NO-GO**).

## Already Adopted (do not re-decide)

Restated from the delegated record § Pricing and ARR + § 575 index (`read`) against the #660 frame. This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | `renewalDisposition` is **`Renew` \| `NonRenew` \| `Replace`**, its own field — not `quantity_semantics` `REPLACE`, not action `REMOVE`, not a new category | **Adopted (delegated)** D1 baseline |
| A2 | `NonRenew` is a renewal-time disposition: **no credit and no termination fee** from this field; not action `REMOVE` | **Adopted (delegated)** D2 baseline |
| A3 | Removed recurring ARR on a `NonRenew` line is **`churn`**. A conversion movement is **not** also stored as `churn` | **Adopted (delegated)** H1 baseline |
| A4 | `Replace` means the commercial relationship continues (not churn + new logo). Money uses mapped price/quantity split | **Adopted (delegated)** D3 + **R1 → C** |
| A5 | Disposition is **null** off a renewal change; on a renewal, continued lines are `Renew`/`Replace` and dropped lines are `NonRenew`; conversion lines stay null under D4 | **Adopted (delegated)** D4 baseline |
| A6 | Conversion: arriving SaaS line is the **group accounting anchor** — `base` = prior recurring ARR (e.g. maintenance, excluding perpetual one-time licence); `conversion` = new recurring ARR − that base; source lines contribute **zero** bridge base/deltas | **Adopted (delegated)** C1 + **C2 → D** |
| A7 | Six components beside one category: `base`, `priceEffect`, `qtyEffect`, `crossSell`, `conversion`, `churn`. No compound category. No category `CONVERSION` | **Adopted (delegated)** F1 baseline |
| A8 | `base` is prior **recurring annualized** ARR; other components are signed annualized deltas in the line currency. Never annualize one-time licence charges or include one-time credits/fees | **Adopted (delegated)** F2 + § Pricing and ARR |
| A9 | Scenario 4 label homes: price movements → `priceEffect`; new product → `crossSell`; non-renewed line → `churn` + `NonRenew`. Illustrative amounts are not recomputed | **Adopted (delegated)** F3 baseline |
| A10 | Price then quantity: `priceEffect=(p1-p0)*q0`, `qtyEffect=p1*(q1-q0)`. Sum equals `p1*q1-p0*q0` before final currency rounding. Normalize replacement quantities with the pinned map first. Missing map / currency comparability / annualization evidence → **null bridge with explicit reason**, never guessed zero | **Adopted (delegated)** F4 replacement + § Pricing and ARR |
| A11 | New recurring product ARR is `crossSell`; quantity movement on a held SKU is `qtyEffect`; one `business_category` remains | **Adopted (delegated)** F5 baseline |
| A12 | List repricing that drops a prior discount records **`LIST_DISCOUNT_RESET`** in immutable pricingWarnings/explanations; money stays `priceEffect` | **Adopted (delegated)** F6 replacement |
| A13 | Bridge is **per line**. Document `arr_delta` stays the existing header column and is **not** redefined as the sum of bridge components | **Adopted (delegated)** F7 baseline |
| A14 | **Pricing-service** computes the bridge for the current revision and returns it with the audited pricing decision. Quote-service / CAM **store** the result and **never recompute** it | **Adopted (delegated)** T1 replacement |
| A15 | Reprice replaces the current revision's bridge; previous frozen bridge is immutable. A manual override **invalidates** the current bridge until pricing-service recomputes. Unpriced/uncomputed bridge is **null** (zeros never mean "not computed") | **Adopted (delegated)** T2 replacement + T3 baseline |
| A16 | Holds: ramp flag off; no Jev enablement; no product code / schema / flag from docs PRs alone; CAM **V1_16–V1_21** reserved by other drafts — not available here; S4 / dual-read / asset-write holds untouched; DRG-600 stays blocked | AGENT.md; #805–#812; #660 out-of-scope |

## What the tree does today (ARR bridge)

`verified by reading code` / token search at the base SHA.

1. CAM `amendment_document_lines` (`V1_7__contract_documents.sql`) stores one `business_category`, line actions including `REMOVE`/`RENEW`, and `quantity_semantics` including `REPLACE` (quantity identity). **No** `renewal_disposition` / `arr_bridge` columns.
2. Document header has `arr_delta` beside `tcv_delta`, `one_time_total`, `credit_total`. That header column is **not** the per-line bridge (A13).
3. Token search of `backend/` and `frontend/` for `arrBridge`, `arr_bridge`, `renewalDisposition`, `renewal_disposition`, `priceEffect`, `qtyEffect`, `LIST_DISCOUNT_RESET` returns **no production Java/TS/SQL hits** this pass (docs/spec references only). A differently named column would miss this search. **Unverified** beyond those tokens; a database catalog was not queried.
4. CAM tip on this `main` is `V1_15__document_apply_replay_evidence.sql`. Sibling Wave-2 briefs reserve CAM **V1_16–V1_21** for drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812). This brief claims **no** migration number.
5. [`specs/arr-bridge.md`](specs/arr-bridge.md) remains a proposal. Delegated policy choices supersede overlapping proposal open questions; the proposal file is **not** accepted as schema authority.

### GitNexus (this checkout)

GitNexus MCP reported **no indexed repositories**. Local `.gitnexus/run.cjs` is absent; `npx gitnexus analyze` was **not** run to completion this pass. Graph impact / `detect_changes` are therefore **UNKNOWN / unavailable** — **not a clean check**, not evidence of zero blast radius.

**Docs-only change impact:** this branch adds one markdown file under `docs/renewals-amendments/prereq/`. No production symbol is edited. A later implementation must run GitNexus `impact` before adding bridge columns, pricing-response DTOs, or writers that touch amendment lines / pricing confirmation. Pre-commit `detect_changes` on this docs-only diff remains **UNKNOWN** until an index exists; `partial` / `truncated` / `UNKNOWN` is **not** a clean check.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed` under id **OQ-575-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No new Adopted cells. Rows refine **delivery** against A1–A16; they do **not** reopen those contracts. **Do not adopt** open OQs from [#834](https://github.com/drgaciw/open-crm-cpq/pull/834) or [#823](https://github.com/drgaciw/open-crm-cpq/pull/823) by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives (not selected) | Blocks impl? |
|---|---|---|---|---|
| OQ-575-1 | **Persistence home** for `renewalDisposition` + `arrBridge` | Store on the **priced commercial line** (CAM line + quote retention of pricing-returned values); pricing-service computes, quote/CAM never recompute (A14) | Pricing-envelope only (no durable line fields); seller-typed components; invent parallel stores in both services | Yes — persistence |
| OQ-575-2 | **Flyway / schema numbering** | Would-be only; **next free after reserved CAM V1_16–V1_21**. Number settled when an authorized migration PR exists. **Never** claim V1_16–V1_21 here | Reserve / ship V1_16–V1_21 in this brief; ship schema before OQs close | Yes — any migration |
| OQ-575-3 | **Pricing response / DTO contract** | Versioned pricing response carries bridge components + `currency` + `computedAt` + `policyVersionIds[]` + null-reason when uncomputable; OpenAPI/DTO + golden fixtures required before impl | Embed bridge only in free-form JSON explanations; omit null-reason; let quote invent missing components | Yes — pricing contract AC |
| OQ-575-4 | **Accept `specs/arr-bridge.md`?** | Keep **Proposal**; score delivery via OQ-575-* without wholesale acceptance (Adopted A\* already supersede overlapping proposal OQs) | Accept arr-bridge.md as ADR / schema mandate; treat Scenario 4 AC sums as Adopted arithmetic | Yes — if treated as schema mandate |
| OQ-575-5 | **Coupling to DRG-552 / #834** | Affirm one `business_category` (A7/A11); bridge money does not add category tokens. Cite #834 only; **do not adopt** OQ1–OQ5; do not enable first-slice `RENEW` from this brief | Add `CONVERSION` / `NON_RENEWAL` category; compound categories; adopt #834 OQs as closed here | Soft for docs; hard for category/migration AC |
| OQ-575-6 | **Coupling to forecast / #823 / DRG-600** | Bridge is durable commercial evidence only; **no** CRM forecast push from this story. Cite #823; **do not adopt** FQ4–FQ10. DRG-600 stays blocked | Wire bridge into forecast definitions; design DRG-600 event here; treat #823 FQs as scored | Soft for store; hard for CRM push AC |
| OQ-575-7 | **Replace map-pin readiness (R1-C)** | Require version-pinned quantity map before computing Replace bridge; missing map → null + reason (A10). Cite #831 opacity; do not adopt #831 OQs | Guess 1:1 map; compute Replace as all-`priceEffect` without map; evaluate ratio in product-service | Yes — Replace AC |
| OQ-575-8 | **Conversion fixture under Adopted C2-D** | Keep A6 classification. Fixture needs a **priced new-recurring ARR input** from conversion pricing delivery (cite #848 / DRG-640 inventory). Do **not** reopen C2; do not pick Scenario 3 illustrative nets as defaults | Reopen C2 to parity ARR / year-1 net; put credits into run-rate; invent conversion figure here | Soft until conversion price ships; hard for ARR conversion AC |
| OQ-575-9 | **Mixed currency / FX** | Refuse document-level sum / cross-line aggregation without a **separate accepted FX contract**; components stay in line currency (A8) | Silent FX at document rollup; redefine header `arr_delta` as multi-currency sum | Soft for single-currency; hard for mixed-currency AC |
| OQ-575-10 | **`LIST_DISCOUNT_RESET` warning home** | Persist on the **immutable pricing decision / warnings** trail (A12); line does not grow a separate boolean column in this story | Named boolean column on the line (migration); encode only as category text | Soft for money; hard for list-reprice visibility AC |
| OQ-575-11 | **First-slice bridge-write authorization** | Bridge writes stay unauthorized until a **named** implementation slice after pricing-response (OQ-575-3) + audit pin (cite #822) are delivered and verified. Closing this brief alone does **not** enable writes | Enable column writes when OQ-575-* close; write zeros on draft create | Yes — any bridge write / migration |
| OQ-575-12 | **Rounding residual golden fixtures** | Deliver fixtures that assert A10 formula + half-up minor-unit rounding with residual assigned to `qtyEffect` so rounded bridge reconciles to rounded closing ARR | Leave residual unspecified; allow quote-side re-round | Yes — numeric AC |
| OQ-575-13 | **Override invalidation delivery (T2)** | Manual override clears/invalidates current bridge (null + reason) until pricing-service recomputes; quote never fills components. Frozen prior revision untouched (A15) | Quote recomputes on override; mark stale but keep old numbers as current | Soft for docs; hard for override AC |
| OQ-575-14 | **Audit / PolicyDecision coupling** | Bridge `policyVersionIds` must pin the **one** pricing-owned commercial-decision trail (cite #822 / DRG-612). Do not invent a bridge-only audit table | Widen unrelated audit tables; mint quote-local fake traces | Soft until store exists; hard for commercial confirm |
| OQ-575-15 | **Header `arr_delta` vs line bridge tests** | Keep A13: tests may sum line components for fixtures; they must **not** require `arr_delta` to equal that sum or redefine the header | Persist document-level component rollups; redefine `arr_delta` as bridge sum | Soft for first store; hard if reporting assumes equality |
| OQ-575-MKT | Sanity-check / vendor research (SF AssetAction rollups, Zuora Renew+RemoveProduct) | Cite as research only; no vendor parity commitment | Encode Salesforce rollup field names as wire format | Soft |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-575-1 — Persistence home

| Option | What the owner would be choosing |
|---|---|
| **A. Priced commercial line stores disposition + bridge returned by pricing (Recommended, Proposed)** | Aligns with A14/A15. Pricing computes; quote/CAM retain; no quote recompute. Does **not** ship Flyway in this brief. |
| **B. Pricing-envelope only** | Weak durable line evidence for later apply/reporting readers. |
| **C. Seller-typed components** | Rejects A14. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-2 — Flyway numbering

| Option | What the owner would be choosing |
|---|---|
| **A. Next free after V1_16–V1_21; number at migration PR (Recommended, Proposed)** | Affirms A16. Docs claim no reserved slot. |
| **B. Claim V1_16–V1_21** | Conflicts with #805–#812. **NO-GO.** |
| **C. Ship schema before OQs close** | Product/schema **NO-GO** while blocking OQs open. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-3 — Pricing response / DTO contract

| Option | What the owner would be choosing |
|---|---|
| **A. Versioned response fields + null-reason + fixtures (Recommended, Proposed)** | Makes A14 executable without accepting `specs/arr-bridge.md` wholesale. |
| **B. Free-form explanations only** | Unreliable for store/validate. |
| **C. Quote invents missing components** | Rejects A14/A15. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-4 — Accept `specs/arr-bridge.md`?

| Option | What the owner would be choosing |
|---|---|
| **A. Keep Proposal; score via OQ-575-* (Recommended, Proposed)** | Adopted A\* already close overlapping proposal questions. |
| **B. Accept as ADR / schema mandate now** | Over-reads a proposal file; invents schema authority. |
| **C. Treat Scenario 4 AC arithmetic as Adopted** | Illustrative labels were not recomputed; not defaults. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-5 — Coupling to DRG-552 / #834

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #834 only; keep one category; do not adopt OQ1–OQ5 (Recommended, Proposed)** | Affirms A7/A11 and Cluster E L4. Does not enable `RENEW` submit. |
| **B. Add `CONVERSION` / `NON_RENEWAL` / compound categories** | Reopens F1-C / Cluster E alternatives. **NO-GO** without supersession. |
| **C. Close #834 OQs from this brief** | Out of scope. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-6 — Forecast / #823 / DRG-600

| Option | What the owner would be choosing |
|---|---|
| **A. Store only; no CRM push; cite #823; leave DRG-600 blocked (Recommended, Proposed)** | Affirms #660 / delegated out-of-scope for DRG-600. |
| **B. Wire bridge into #823 forecast definitions** | Adopts open FQs. **NO-GO.** |
| **C. Design DRG-600 event here** | Blocked story. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-7 — Replace map-pin readiness

| Option | What the owner would be choosing |
|---|---|
| **A. Require pinned map; else null + reason (Recommended, Proposed)** | Affirms A4/A10 and 576-F13 opacity. Cite #831; do not adopt its OQs. |
| **B. Guess 1:1** | Silent commercial invention. **NO-GO.** |
| **C. All delta in `priceEffect` without map** | Reopens R1-B against Adopted R1-C. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-8 — Conversion fixture under C2-D

| Option | What the owner would be choosing |
|---|---|
| **A. Keep A6; fixture waits on priced new-recurring input (Recommended, Proposed)** | Does not reopen C2. Cite #848 / Done DRG-640 inventory only. |
| **B. Reopen C2 to parity ARR or year-1 net** | Contradicts delegated C2-D. **NO-GO** unless owner supersedes. |
| **C. Put year-1 credits into run-rate** | Rejects A8 (no one-time credits in bridge). **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-9 — Mixed currency / FX

| Option | What the owner would be choosing |
|---|---|
| **A. No cross-currency aggregate without separate FX contract (Recommended, Proposed)** | Affirms A8 / delegated currency rule. |
| **B. Silent document FX** | Invents FX policy. |
| **C. Redefine header `arr_delta` as multi-currency sum** | Conflicts with A13. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-10 — `LIST_DISCOUNT_RESET` home

| Option | What the owner would be choosing |
|---|---|
| **A. Pricing warnings/explanations trail (Recommended, Proposed)** | Affirms A12 without a line boolean migration in this story. |
| **B. Named boolean on the line** | Extra migration; name still an owner choice. |
| **C. Category text only** | Overloads the singular category. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-11 — First-slice bridge-write authorization

| Option | What the owner would be choosing |
|---|---|
| **A. Named slice after OQ-575-3 + audit pin verified (Recommended, Proposed)** | Closing this Decision brief is necessary but **not sufficient**. |
| **B. Enable writes when OQ-575-* close alone** | Skips pricing/audit delivery gate. |
| **C. Write zeros on draft create** | Rejects A15 / T3. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-12 — Rounding residual fixtures

| Option | What the owner would be choosing |
|---|---|
| **A. Golden fixtures for A10 + residual→`qtyEffect` (Recommended, Proposed)** | Makes delegated rounding executable in tests. |
| **B. Leave residual unspecified** | Non-deterministic ACs. |
| **C. Quote re-rounds** | Rejects A14. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-13 — Override invalidation delivery

| Option | What the owner would be choosing |
|---|---|
| **A. Invalidate to null + reason until pricing recomputes (Recommended, Proposed)** | Affirms A15. |
| **B. Quote recomputes on override** | Rejects A14. **NO-GO.** |
| **C. Keep old numbers as current while "stale"** | Ambiguous commercial evidence. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-14 — Audit / PolicyDecision coupling

| Option | What the owner would be choosing |
|---|---|
| **A. Pin the one pricing-owned trail; cite #822 only (Recommended, Proposed)** | Do not invent a bridge-only audit table. |
| **B. Bridge-only audit table** | Second trail. |
| **C. Quote-local fake traces** | Rejects durable confirmation expectations. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-15 — Header `arr_delta` vs line bridge

| Option | What the owner would be choosing |
|---|---|
| **A. Fixture sums allowed; header definition unchanged (Recommended, Proposed)** | Affirms A13. |
| **B. Persist document-level component rollups now** | Extra scope; optional later story. |
| **C. Redefine `arr_delta` as bridge sum** | Reopens F7-C. **NO-GO** without supersession. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-575-MKT — Research only

| Option | What the owner would be choosing |
|---|---|
| **A. Cite SF / Zuora research as illustration only (Recommended, Proposed)** | No vendor parity commitment. |
| **B. Encode vendor field names as wire format** | Out of scope. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit NO-GO)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed **OQ-575-***.
2. CPQ Decision must score the OQ-575-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **NO-GO** on:
   - product Java/TS that adds `renewalDisposition` / `arrBridge` writers, pricing-response bridge fields, or quote-side recomputation
   - Flyway / schema changes for bridge or disposition columns (including any claim on CAM **V1_16–V1_21**)
   - feature flags that enable bridge persistence, CRM forecast push, or conversion bridge writes
   - adopting open OQs from [#834](https://github.com/drgaciw/open-crm-cpq/pull/834) or [#823](https://github.com/drgaciw/open-crm-cpq/pull/823) as if scored here
4. Even after forks close: A1–A16 hold; bridge writes still need a **named** implementation authorization (OQ-575-11); ramp flag stays off; no Jev / dual-read / S4 / asset-write hold bypass; `specs/arr-bridge.md` stays non-accepted until the owner separately accepts it; DRG-600 stays blocked; CAM **V1_16–V1_21** stay reserved elsewhere.
5. Linear status for DRG-575 stays **In Review** until the owner merges and DoD says otherwise. This brief does not mark Done.

## Out of scope

- Creating any migration or Java/TS type that lands `arr_bridge` / `renewal_disposition` schema.
- Editing amendment line checks, category enums, pricing continuation guards, CAM apply ledger, or forecast runtime.
- Enabling `opencrm.pricing.ramp.enabled`, any `opencrm.jev.*.enabled` flag, dual-read flip, S4 enablement, or asset-write hold bypass.
- Adopting open OQs from [#834](https://github.com/drgaciw/open-crm-cpq/pull/834) (ChangeLine) or [#823](https://github.com/drgaciw/open-crm-cpq/pull/823) (forecast definitions).
- Designing DRG-600 automatic re-forecast or pushing bridge components to CRM.
- Reopening #660 / delegated Adopted cells (including C2-D, F4 price-then-quantity, T1 pricing-service writer).
- Claiming CAM Flyway **V1_16–V1_21**.

## Related citations (orientation only)

| Topic | Cite |
|---|---|
| #660 frame + dispositions | `story-drg-575-arr-bridge-per-change-line-decision-frame.md`; delegated record § 575 + § Pricing and ARR |
| Proposal contract (not accepted) | `specs/arr-bridge.md` |
| One category / ChangeLine residual | #834 Decision brief (cite; do not adopt open OQs) |
| Forecast definitions residual | #823 (cite; do not adopt open FQs); DRG-600 blocked |
| Conversion pricing residual | #848 / DRG-573; DRG-640 Done frame for candidate inventory |
| Catalog map opacity | #831 / DRG-576 (cite; do not adopt) |
| Pricing audit trail | #822 / DRG-564 (cite; do not adopt) |
| Scenario labels | `../cpq-pricing-policy-sanity-check.md` Scenarios 3–4 |

## Known gaps (completion review)

- GitNexus index / `impact` / `detect_changes` were **unavailable** this pass (`No indexed repositories`; no `.gitnexus/run.cjs`). Result is **UNKNOWN**, not clean.
- `#834` / `#823` / `#848` bodies were cited from open draft PRs; their open OQs remain **unowned by this brief**.
- Token search for bridge writers is documentation evidence only; a live DB catalog was not queried.
- `*IntegrationTest` / browser UAT / Docker paths were not run (docs-only).
- AutoJev completion-review MCP is not connected in this environment; carry on without inventing a verdict.
