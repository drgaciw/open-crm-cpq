# DRG-337 — Usage tier rating for quoting: residual Decision brief

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring of **REQ / boundary / start / hold forks that remain after** Accepted ADR-023 D-09 (usage-tier rating split out of ramp), the ramp sprint docs that leave DRG-337 **Backlog** and unedited, and the R1–R4 ramp delivery wave that intentionally never built BC-03-12. This file does **not** invent or accept a `REQ-usage-tier-rating` document, does **not** reopen Adopted ADR-023 cells (including D-07 period-quantity volume tiers and D-09 split), does **not** adopt open **OQ-336-*** rows from the DRG-336 residual Decision brief draft [#876](https://github.com/drgaciw/open-crm-cpq/pull/876), does **not** adopt open **OQ2–OQ7** / Defaults slots from the DRG-534 Decision brief draft [#803](https://github.com/drgaciw/open-crm-cpq/pull/803), does **not** accept [`specs/ramp-amendments.md`](specs/ramp-amendments.md), does **not** authorize an implementation brief, authorize product code, open a Flyway migration, claim a CAM version in the reserved **V1_16–V1_21** band, flip a flag, register a route, enable ramp / usage / Jev / dual-read / S4, bypass the asset-write hold, start estimate or delivery, or deploy. Every open fork below is `Status: Proposed` under id **OQ-337-***. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `usage` / BC-03-12 quoting-time RateTier (split-out from ramp). **Story:** [DRG-337](https://linear.app/drgamtd-workspace/issue/DRG-337/bc-03-12-usage-tier-rating-for-quoting). Linear read this pass: **Backlog**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / authority (cite, do not reopen Adopted cells; do not adopt #876 / #803 OQs):**

| Artifact | Role |
|---|---|
| [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) (Accepted 2026-10-03) | **Adopted:** D-09 keeps usage-tier rating **out** of the ramp sprint; D-07 period-own quantity for *ramp* volume tiers is **not** BC-03-12; D-06 ramp flag default **off** |
| [REQ-price-ramping.md](../../backlog/REQ-price-ramping.md) | Records the D-09 split and maps BC-03-12 / DRG-337 as **not covered** by REQ-PRC-001; states DRG-337 needs **its own REQ** |
| [forge/41-price-ramp-sprint.md](../../forge/41-price-ramp-sprint.md) | Explicitly leaves DRG-337 Backlog / unedited; D-09 unchanged |
| Wave-2 DRG-336 residual Decision brief [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) | Cite-only R1 pricing residual forks (**OQ-336-***). Restates **Ramp hold**. This brief **does not score or close** those rows |
| Wave-2 DRG-534 Decision brief [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | Cite-only amendment forks (**OQ2–OQ7** + Defaults / OQ1 / OQ8). Restates stored-step path and **ramp hold**. This brief **does not score or close** those rows |
| [`specs/ramp-amendments.md`](specs/ramp-amendments.md) | Proposal only (DRG-639). Not accepted by this file; not a usage-tier REQ |
| Linear BC-03-12 / Notion `3eaf7cb4e4df8160b30dc5c1554995d9` | Story AC (estimated volume → PriceElement; handoff to BC-09-03); **Needs own REQ**; RateTier overlap with BC-03-05 called out |

**Related (cite; do not reopen or start product work):**

| Ticket / PR | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-336](https://linear.app/drgamtd-workspace/issue/DRG-336) / [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) | In Review / draft | R1 residual. Do **not** adopt **OQ-336-***. Do **not** pull usage tiers back into ramp Done |
| [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | In Progress / draft | Open **OQ2–OQ7**. Cite only; do **not** adopt. Amendment elapsed lock is **not** usage-tier Acceptance |
| [DRG-435](https://linear.app/drgamtd-workspace/issue/DRG-435) | Backlog | BC-03-05 adjustment / tiered / volume pricing also realizes **RateTier**. Boundary must be stated in the future REQ so the two do not build the same thing twice |
| [DRG-471](https://linear.app/drgamtd-workspace/issue/DRG-471) | Backlog | BC-09-03 RateUsage(period). Consumer of quoting-time RateTier output; not started by this brief |
| [DRG-340](https://linear.app/drgamtd-workspace/issue/DRG-340) | Backlog | Credit draw-down (D-09 sibling). Stays separate; also needs its own REQ |
| [DRG-341](https://linear.app/drgamtd-workspace/issue/DRG-341) | Related | Hybrid migration ramp. Explicitly keeps usage tiers out |
| [DRG-339](https://linear.app/drgamtd-workspace/issue/DRG-339) | In Progress | Ramp apply path. Not a license to start usage-tier product work |
| [DRG-614](https://linear.app/drgamtd-workspace/issue/DRG-614) | Backlog | Ramp enablement / rollout. Not a usage-tier un-hold |
| [DRG-283](https://linear.app/drgamtd-workspace/issue/DRG-283) | In Progress | BC-03 epic parent. Does not authorize BC-03-12 delivery without REQ + Decision |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, REQ acceptance, estimate, or deploy.** `opencrm.pricing.ramp.enabled` stays default off (**Ramp hold**). No `opencrm.pricing.usage*.enabled` (or equivalent) flag is invented or flipped here. `opencrm.pricing.metrics.acv-version` stays committed `legacy`. No `opencrm.jev.*.enabled` flag is turned on. Dual-read / S4 enablement / asset-write / subscriptions-UI holds stay in force. An implementation brief or product PR for DRG-337 is **blocked** until CPQ Decision scores the open **OQ-337-*** forks below (or the owner explicitly defers a named fork with words and a time) **and** a dedicated REQ exists under the owner's words. **Product / schema / flag = NO-GO** while any blocking OQ-337-* row stays open. Closing #876 OQ-336-* or #803 OQ2–OQ7 is a separate owner act; this brief does not close them by citation.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (ADR-023)` | Already selected. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Residual REQ / boundary / start / hold fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read / searched in the tree at the base SHA. Not executed. |
| `UNKNOWN` | Graph could not answer; not a clean / low-risk check. |

## Problem (DRG-337 residual)

ADR-023 Accepted D-09: usage-tier rating and credit draw-down are **not** in the ramp sprint. REQ-price-ramping maps BC-03-12 / DRG-337 as split-out and **not covered** there; forge/41 leaves the Linear issue Backlog with estimate none and no replacement description. Ramp R1–R4 product work proceeded without building quoting-time usage RateTier. Linear story text still requires **its own REQ** before estimate or start, and calls out overlap with BC-03-05 (also realizes RateTier).

Without a residual Decision brief that separates **already Adopted** D-09 / ramp holds from **still-Proposed REQ / RateTier-boundary / handoff / start forks**, a follow-up could: treat ramp Done (#876 path) as permission to start usage tiers; invent schema or a usage flag from a docs PR; collapse BC-03-12 into BC-03-05 (or the reverse); treat `usage-overage-charge.drl` or ADR-023 D-07 period-quantity tiers as BC-03-12 Acceptance; adopt [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) open OQs by implication; or move Linear off **Backlog** without owner words.

Linear DRG-337 (`Linear read` 2026-11-06): **Backlog** (never started); parent epic DRG-283; related to DRG-341; attachments Notion BC-03-12 + REQ-price-ramping (D-09). Estimate none. Comments: none. **This brief does not change Linear status.**

## Already Adopted (do not re-decide)

Restated from ADR-023 (`read`), REQ-price-ramping (`read`), forge/41 (`read`), and the orientation baselines in [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) (`read`). This Decision brief does **not** reopen these rows. Those drafts mark their OQ rows Proposed — those are **not** Adopted here.

| ID | Contract | Source |
|---|---|---|
| A1 | **D-09 split:** usage-tier rating is **out** of BC-03-09 / ramp REQ and sprint; DRG-337 keeps its own AC | ADR-023 D-09; REQ-price-ramping; forge/41 |
| A2 | Ramp volume tiers use **that period's own quantity** (AC-02). That is **not** consumption-based usage-tier rating | ADR-023 D-07 |
| A3 | **`opencrm.pricing.ramp.enabled` defaults false**; turning it on is outside ramp Acceptance (**Ramp hold**) | ADR-023 D-06; #876 / #803 orientation |
| A4 | ACV version switch is **`opencrm.pricing.metrics.acv-version`**, committed default **`legacy`** | ADR-023 D-03 |
| A5 | DRG-337 story AC (cite only, not Acceptance proven): estimated volume rated through usage tiers → PriceElement; output available to BC-09-03 as quoting-time input | Linear BC-03-12 |
| A6 | DRG-337 **needs its own REQ** before estimate or start; RateTier boundary vs BC-03-05 must be stated so the two do not double-build | Linear; REQ-price-ramping split-out row |
| A7 | Credit draw-down stays on DRG-340 / BC-09-06 (D-09 sibling); not this story | ADR-023 D-09; forge/41 |
| A8 | Docs / Decision briefs alone authorize **no** product code, REQ acceptance, flag flip, or deploy | AGENT.md / ADR-023 / #803 / #876 |

## What the tree does today (usage / RateTier / adjacent)

`verified by reading code` at the base SHA.

1. **No dedicated usage-tier REQ** — `docs/backlog/` has `REQ-price-ramping.md` (excludes BC-03-12) and no `REQ-usage-tier*` (`read` listing).
2. **Ramp flag off** — pricing `application.yml` keeps `opencrm.pricing.ramp.enabled: false` and `acv-version: legacy` (`read`). Flag-off schedule is 422 `RAMP_NOT_SUPPORTED` (`PricingRampEngineTest#ac08FlagOff`).
3. **Ramp period quantity ≠ usage tiers** — AC-02 named-seat tier applies to **each period's own quantity** (`PricingRampEngineTest#ac02PeriodQuantity`). That is ADR-023 D-07 / A2, not BC-03-12 estimated-usage rating.
4. **Usage overage DRL exists; not quoting RateTier** — `usage-overage-charge.drl` (salience 610) computes `overageAmount` from `includedUnits` / `actualUnits` / `overageRate` on `QuoteFact` (`read`). Agent skill `usage-based-pricing` documents overage / prepaid / committed-floor workflow. **Neither** is BC-03-12 Acceptance (estimated volume → rated PriceElement for quoting handoff to BC-09-03).
5. **Catalog type present** — `ProductType.SAAS_USAGE_METERED` exists; ramp / subscription validators reject or exclude it from some ramp paths (`read` tests). Presence of the enum is **not** usage-tier rating delivery.
6. **No DRG-337 product PRs** — GitHub search this pass found no open/merged implementation PR titled for DRG-337 usage-tier rating beyond the D-09 docs origin in the ramp sprint merges (`read` `gh pr list`).
7. **Migration tips** — quote tip includes later ramp escalator `V1_16`; draft CAM PRs reserve **V1_16–V1_21**. **This brief claims no migration number and opens no Flyway.**

### GitNexus (this checkout)

Docs-only change: **no production symbol is edited** by this brief. GitNexus MCP reported **no indexed repositories** this pass (`query` / `impact` unavailable). Treat blast radius for any later `RateTier` / usage-rating symbols as **UNKNOWN** — not a clean check. A later implementation must run GitNexus `impact` before touching pricing execute paths, `usage-overage-charge.drl`, quote PriceElement persistence, or any new usage-tier schema. `UNKNOWN` / `partial` / `truncated` graph results are unresolved, not low risk.

## Owner-decision table (remaining REQ / boundary / start / hold forks)

Every row is `Status: Proposed` under id **OQ-337-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No new Adopted cells.** Product / schema / flag stay **NO-GO** until blocking rows close (or the owner defers them with words and a time). Do **not** adopt open OQs from [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) or [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-337-1 | Dedicated REQ before estimate / start | Keep Linear **Backlog**; author a separate `REQ-usage-tier-rating` (name TBD by owner) **before** estimate or In Progress; this Decision brief is **not** that REQ | Start coding from Linear AC alone; reuse REQ-price-ramping as the usage REQ; auto-estimate from old combined 8 points | Yes — start / estimate |
| OQ-337-2 | RateTier boundary vs BC-03-05 (DRG-435) | REQ must state: BC-03-05 = adjustment / quantity / segment / family / term **DefineAdjustmentRule** RateTier; BC-03-12 = **estimated usage volume** rating at quote time → PriceElement. Shared primitives OK; no double-build | Merge 337 into 435; let 435 ship usage rating; leave boundary unspecified | Yes — REQ / scope |
| OQ-337-3 | Handoff to BC-09-03 (DRG-471) | REQ must name the quoting-time artifact BC-09-03 consumes; do **not** implement RateUsage here | Ship RateUsage inside 337; leave handoff undefined until runtime | Soft for REQ; hard if product |
| OQ-337-4 | Keep D-09 / usage hold vs ramp | **NO-GO** on pulling usage tiers into ramp R1–R4 Done or enablement. Cite A1; keep split | Re-fold usage into DRG-336 Acceptance; treat #876 Done as usage un-hold | Yes — scope / hold |
| OQ-337-5 | Coupling to #876 without adopting OQ-336-* | Cite [#876](https://github.com/drgaciw/open-crm-cpq/pull/876); score R1 residuals separately; **do not** adopt **OQ-336-*** | Treat #876 Recommended rows as Adopted; start 337 when 336 marks Done | Soft for 337 PASS; hard for sibling claims |
| OQ-337-6 | Coupling to #803 without adopting OQ2–OQ7 | Cite [#803](https://github.com/drgaciw/open-crm-cpq/pull/803); no amendment product; **do not** adopt OQ2–OQ7 | Treat #803 Recommended rows as Adopted; implement elapsed-lock inside 337 | Yes — amendment path |
| OQ-337-7 | Adjacent overage DRL / skill ≠ BC-03-12 | Disclose `usage-overage-charge.drl` + skill as **non-Acceptance** adjacent corpus; do not claim AC from them | Treat overage DRL as quoting RateTier Done; delete DRL from this brief's scope claim | Soft for REQ; hard if claiming Done |
| OQ-337-8 | ADR-023 D-07 vs usage tiers | Keep A2: period quantity tiers stay ramp; usage tiers stay consumption/estimate | Redefine D-07 as usage rating; reuse ramp period engine for estimated usage without REQ | Yes — model confusion |
| OQ-337-9 | Sibling DRG-340 | Keep credit draw-down separate; no shared delivery PR from this brief | Bundle 337+340; invent prepaid schema under 337 | Soft until 340 REQ; hard if product |
| OQ-337-10 | Schema / Flyway / flags | **NO-GO.** No new migration, no CAM `V1_16`–`V1_21`, no new `opencrm.pricing.usage*` flag from this brief | Open usage-tier tables now; flip or invent usage flag; hitch to ramp flag | Yes — any schema / flag |
| OQ-337-11 | Implementation brief / product edits | **NO-GO** on Java/TS/DRL product work until OQ-337-* blocking rows close **and** dedicated REQ exists under owner words | Authorize pricing/quote work when this docs file merges; "spike" without REQ | Yes — any code PR |
| OQ-337-12 | Linear status / estimate | Leave **Backlog**; estimate remains none until REQ + owner words; no bot status change | Move to Todo/In Progress from this PR; invent points from old combined 8 | Yes — workflow |
| OQ-337-13 | Standing holds (Jev / dual-read / subs UI / asset-write) | Holds stay; usage-tier Decision does not un-hold them | Couple usage rating to subscriptions UI or Jev enablement | Yes — hold violations |
| OQ-337-14 | Ramp flag / GA coupling | Keep ramp flag false; DRG-614 owns ramp enablement; usage story does not flip ramp or claim GA | Require ramp-on before usage quoting; flip ramp from 337 | Yes — Ramp hold |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-337-1 — Dedicated REQ before estimate / start

| Option | What the owner would be choosing |
|---|---|
| **A. Separate REQ first; stay Backlog until then (Recommended, Proposed)** | Matches Linear "Needs: its own REQ" and REQ-price-ramping split-out row. |
| **B. Start from Linear AC bullets alone** | Skips the stated REQ gate. **NO-GO** for product. |
| **C. Treat REQ-price-ramping as the usage REQ** | Contradicts D-09 / "not covered here". **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-337-2 — RateTier boundary vs BC-03-05 (DRG-435)

| Option | What the owner would be choosing |
|---|---|
| **A. REQ draws the boundary; shared primitives allowed; no double-build (Recommended, Proposed)** | Matches Linear Needs note. |
| **B. Merge BC-03-12 into BC-03-05** | Collapses D-09 split story; needs explicit owner words if ever chosen. |
| **C. Leave boundary unspecified and start both** | Highest double-build risk. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-337-3 — Handoff to BC-09-03 (DRG-471)

| Option | What the owner would be choosing |
|---|---|
| **A. REQ names quoting-time input; RateUsage stays on 471 (Recommended, Proposed)** | Matches story AC-2 without starting Phase 3 rating. |
| **B. Implement RateUsage inside 337** | Wrong epic/phase. **NO-GO** from this brief. |
| **C. Defer naming the artifact until coding** | Leaves AC-2 unverifiable at REQ time. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-337-4 — Keep D-09 / usage hold vs ramp

| Option | What the owner would be choosing |
|---|---|
| **A. Keep split; no usage pull-into ramp Done (Recommended, Proposed)** | Preserves A1 / forge/41. |
| **B. Re-fold usage into DRG-336 Acceptance** | Reopens D-09. **NO-GO** without owner ADR amend. |
| **C. Treat #876 path as usage un-hold** | Confuses R1 residual with BC-03-12. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-337-5 — Coupling to #876 without adopting OQ-336-*

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #876; do not adopt OQ-336-* (Recommended, Proposed)** | Closing this brief does **not** close R1 residuals. |
| **B. Treat #876 Recommended rows as Adopted** | Adopts open OQs from another draft. **NO-GO.** |
| **C. Gate 337 start on 336 Linear Done alone** | Optional sequencing; still needs REQ (OQ-337-1). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-337-6 — Coupling to #803 without adopting OQ2–OQ7

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #803; wait; no amendment product here (Recommended, Proposed)** | Preserves amendment hold orientation. |
| **B. Treat #803 Recommended rows as Adopted** | Out of scope. **NO-GO.** |
| **C. Implement elapsed-period lock inside DRG-337** | Wrong story. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-337-7 — Adjacent overage DRL / skill ≠ BC-03-12

| Option | What the owner would be choosing |
|---|---|
| **A. Disclose as adjacent non-Acceptance (Recommended, Proposed)** | Avoids false Done from existing DRL/skill. |
| **B. Claim AC from `usage-overage-charge.drl`** | Overclaims. **NO-GO.** |
| **C. Rewrite overage DRL as the quoting RateTier engine without REQ** | Product without gate. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-337-8 — ADR-023 D-07 vs usage tiers

| Option | What the owner would be choosing |
|---|---|
| **A. Keep period-quantity tiers ≠ usage tiers (Recommended, Proposed)** | Preserves A2. |
| **B. Redefine D-07 as usage rating** | Reopens Accepted ADR cell. **NO-GO.** |
| **C. Reuse ramp period engine for estimated usage without REQ** | Product conflation. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-337-9 — Sibling DRG-340

| Option | What the owner would be choosing |
|---|---|
| **A. Keep separate; own REQ later (Recommended, Proposed)** | Matches D-09 / forge/41. |
| **B. Bundle 337+340 delivery** | Couples prepaid to quoting RateTier. |
| **C. Open prepaid schema under 337** | Product/schema. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-337-10 — Schema / Flyway / flags

| Option | What the owner would be choosing |
|---|---|
| **A. No migration / no new usage flag / no CAM V1_16–V1_21 (Recommended, Proposed)** | Hard product/schema/flag **NO-GO**. |
| **B. Open usage-tier tables now** | Blocked. |
| **C. Hitch usage enablement to `opencrm.pricing.ramp.enabled`** | Violates A1/A3. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-337-11 — Implementation brief / product edits

| Option | What the owner would be choosing |
|---|---|
| **A. NO-GO on product PRs until forks + dedicated REQ (Recommended, Proposed)** | Docs PR is not authorization. |
| **B. Authorize pricing/quote work when this file merges** | Violates hard product NO-GO. |
| **C. Allow undocumented spike / prototype** | Still product; needs OQ-337-1 first. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-337-12 — Linear status / estimate

| Option | What the owner would be choosing |
|---|---|
| **A. Leave Backlog; estimate none until REQ + owner words (Recommended, Proposed)** | Matches fetched state and DoD owner rule. |
| **B. Move to Todo/In Progress from this PR** | Status change without REQ. **NO-GO** for this wave. |
| **C. Invent points from the old combined 8** | Linear says not split arithmetically. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-337-13 — Standing holds

| Option | What the owner would be choosing |
|---|---|
| **A. Holds stay in force (Recommended, Proposed)** | Jev / dual-read / subscriptions UI / asset-write unchanged. |
| **B. Un-hold subscriptions UI to show usage quotes** | Hold violation. **NO-GO.** |
| **C. Enable a Jev flag for usage advising** | Hold violation. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-337-14 — Ramp flag / GA coupling

| Option | What the owner would be choosing |
|---|---|
| **A. Ramp flag stays false; no GA from 337 (Recommended, Proposed)** | Preserves Ramp hold / DRG-614 ownership. |
| **B. Require ramp-on before usage quoting** | Optional product coupling; needs REQ + owner words; not this brief. |
| **C. Flip ramp from this story** | **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed (`OQ-337-1` .. `OQ-337-14`).
2. CPQ Decision must score the OQ-337-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed **and** a dedicated usage-tier REQ exists under owner words, **do not** write or accept an implementation brief that authorizes Java/TS/DRL product work, Flyway, gateway routes, estimates-as-commitment, or flag flips for DRG-337.
4. Even after forks close: A1–A8 hold; **Ramp hold** stays; no invented usage flag from this wave; #876 OQ-336-* and #803 OQ2–OQ7 stay Proposed unless those Decision briefs close them; DRG-435 / DRG-471 / DRG-340 remain separate Backlog stories.
5. Linear status for DRG-337 stays **Backlog** until the owner and Decision say otherwise. This brief does **not** mark Todo/In Progress/Done and does **not** change Linear status.

## What this Decision brief does not do

- Does not authorize an implementation brief or product code for DRG-337.
- Does not mark any OQ-337-* fork Adopted.
- Does not author or accept a `REQ-usage-tier-rating` (or any new REQ file).
- Does not adopt or close [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) OQ-336-* rows.
- Does not adopt or close [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) OQ2–OQ7 / Defaults / OQ8.
- Does not flip `opencrm.pricing.ramp.enabled`, invent/flip a usage flag, or change `acv-version`.
- Does not open or claim CAM Flyway `V1_16`–`V1_21`, or any new quote/pricing migration.
- Does not close DRG-435, DRG-471, DRG-340, DRG-336, DRG-339, DRG-534, or DRG-614.
- Does not reopen ADR-023 Adopted cells (including D-07 and D-09).
- Does not change Linear status (remains **Backlog**).

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ-337-* (especially OQ-337-1, OQ-337-2, OQ-337-4, OQ-337-10, OQ-337-11). Until then, product work is **NO-GO**.
2. **Dedicated REQ:** still missing; required before estimate/start.
3. **RateTier boundary vs DRG-435:** still Proposed.
4. **BC-09-03 handoff:** still Proposed; DRG-471 Backlog.
5. **Usage / ramp holds:** D-09 split and ramp flag stay; no usage un-hold from this wave.
6. **#876 OQ-336-* / #803 OQ2–OQ7:** still Proposed; not adopted.
7. **Sibling DRG-340:** still Backlog; own REQ later.
8. **GitNexus index:** not refreshed in this pass; any future code edit needs a live `impact` / `detect_changes` on the editing checkout.

## Sources read this pass

- Linear: DRG-337 (Backlog; relations; attachments), DRG-435, DRG-471, DRG-340, DRG-283, DRG-341 orientation.
- GitHub: draft Decision briefs [#876](https://github.com/drgaciw/open-crm-cpq/pull/876) (DRG-336) and [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) (DRG-534) — cite-only; no DRG-337 implementation PR found.
- ADR-023; REQ-price-ramping; forge/41 (DRG-337 / DRG-340 not edited).
- Landed adjacent code: pricing `application.yml` ramp flag + ACV; `PricingRampEngineTest` AC-02/AC-08; `usage-overage-charge.drl`; `ProductType.SAAS_USAGE_METERED`; `agents/skills/usage-based-pricing/SKILL.md`.
- AGENT.md / `docs/ARCHITECTURE.md` §17 holds orientation (Jev / dual-read / subscriptions UI unchanged; ramp flag treated as ADR-023 D-06 hold).

## Unverified in this pass

- Runtime execution of pricing execute or overage DRL fixtures on this checkout.
- AutoJev tools (not connected); no AutoJev verdict is claimed.
- Fresh GitNexus analyze / `detect_changes` graph coverage beyond docs-only claim (MCP reported no indexed repositories until analyze).
- Notion BC-03-12 page body beyond the Linear attachment link (Linear description used as story AC source).
- Whether the owner will authorize a dedicated REQ in the same Decision PASS that scores OQ-337-*.
