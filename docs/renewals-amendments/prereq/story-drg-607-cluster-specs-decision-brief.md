# DRG-607 — Per-cluster dev-ready specs (umbrella): residual Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607/per-cluster-dev-ready-specs-umbrella) (spec umbrella).

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring. This file **inventories** remaining Proposed / open OQ clusters under the DRG-607 umbrella, **cites** landed proposal specs and the open Wave-2 residual Decision-brief draft PR range, and surfaces **umbrella-level residual forks as Proposed OQ-607-\***. It does **not** accept any `specs/<cluster>.md` wholesale, does **not** adopt open OQs from any Wave-2 sibling Decision brief, does **not** tick Linear checklist lines, does **not** authorize an implementation brief, authorize product code, open a Flyway migration (including CAM slots **V1_16–V1_21** reserved by [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), invent schema as landed, flip a flag, register a route, enable ramp / Jev / dual-read / S4, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / per-cluster specs umbrella. **Story:** [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607). Linear read this pass: **In Review**. This brief leaves that status unchanged (status ceiling: In Review; never Done without owner approval).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

## Alignment (cite; do not accept specs or reopen Adopted cells)

| Artifact | Role |
|---|---|
| Linear [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607) description | Umbrella checklist (11 cluster lines, all unchecked on this read). Relations are **related to** only (not blocks). Docs-only draft proposal. |
| [#611](https://github.com/drgaciw/open-crm-cpq/pull/611) (merged `ff58c5112`) | Landed nine cluster proposal specs: capacity-meters, conversions, lifecycle-intents, maintenance, catalog-model, co-termination, partial-apply, arr-bridge, ramp-amendments. **Proposal only.** |
| [#705](https://github.com/drgaciw/open-crm-cpq/pull/705) / [#732](https://github.com/drgaciw/open-crm-cpq/pull/732) (merged); Linear [DRG-648](https://linear.app/drgamtd-workspace/issue/DRG-648) Done | Landed [`specs/spine-dependent.md`](specs/spine-dependent.md). **Proposed.** Hygiene note on DRG-607 (2026-10-16) still requires an owner decision before ticking `spine-dependent`. |
| [#701](https://github.com/drgaciw/open-crm-cpq/pull/701) (merged); Linear [DRG-643](https://linear.app/drgamtd-workspace/issue/DRG-643) Done | Landed [`specs/renewal-automation.md`](specs/renewal-automation.md). **Proposed.** |
| [#800](https://github.com/drgaciw/open-crm-cpq/pull/800) (open); Linear [DRG-644](https://linear.app/drgamtd-workspace/issue/DRG-644) In Progress | Git checklist membership record for the `renewal-automation` line. Explicitly **does not tick** the DRG-607 checkbox. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) | **Adopted baseline** for many cluster forks (A–G and renewal automation). Restated only where the umbrella needs orientation; **do not reopen**. Spec files remain Proposal even where forks were Adopted at policy level. |
| Cluster frames A–G under `prereq/cluster-*-decision-frame.md` | Historical option lists / rationale. Dispositions live in the delegated record. |
| Theme follow-ups [DRG-637](https://linear.app/drgamtd-workspace/issue/DRG-637)–[DRG-649](https://linear.app/drgamtd-workspace/issue/DRG-649) | Per-cluster deeper specs / fixtures under the umbrella. Related only. Closing a theme ticket does **not** auto-tick DRG-607 or accept a cluster spec. |
| CAM drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) | Flyway **V1_16–V1_21 reserved**. This brief writes **no** migration and claims **no** CAM version in that band. |

**Wave-2 residual Decision briefs (cite only; do not adopt open OQs):**

Open docs drafts observed this pass in roughly **#803–#861** (Wave-2 residual Decision-brief / related docs lane). Cluster-mapped drafts used for inventory (non-exhaustive of every open docs PR):

| Checklist line | Constituent stories (Linear) | Wave-2 residual draft PRs (cite; do not adopt OQs) | Spec on `main` |
|---|---|---|---|
| capacity-meters | [DRG-557](https://linear.app/drgamtd-workspace/issue/DRG-557) / [569](https://linear.app/drgamtd-workspace/issue/DRG-569) / [570](https://linear.app/drgamtd-workspace/issue/DRG-570) | [#838](https://github.com/drgaciw/open-crm-cpq/pull/838), [#818](https://github.com/drgaciw/open-crm-cpq/pull/818), [#836](https://github.com/drgaciw/open-crm-cpq/pull/836) | [`capacity-meters.md`](specs/capacity-meters.md) — Proposal only |
| conversions | [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559) / [573](https://linear.app/drgamtd-workspace/issue/DRG-573) / [574](https://linear.app/drgamtd-workspace/issue/DRG-574) | [#850](https://github.com/drgaciw/open-crm-cpq/pull/850), [#848](https://github.com/drgaciw/open-crm-cpq/pull/848), [#853](https://github.com/drgaciw/open-crm-cpq/pull/853) | [`conversions.md`](specs/conversions.md) — Proposal only |
| lifecycle-intents | [DRG-548](https://linear.app/drgamtd-workspace/issue/DRG-548) / [552](https://linear.app/drgamtd-workspace/issue/DRG-552) / [553](https://linear.app/drgamtd-workspace/issue/DRG-553) | [#854](https://github.com/drgaciw/open-crm-cpq/pull/854), [#834](https://github.com/drgaciw/open-crm-cpq/pull/834), [#835](https://github.com/drgaciw/open-crm-cpq/pull/835) | [`lifecycle-intents.md`](specs/lifecycle-intents.md) — Proposal only |
| maintenance | [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) / [571](https://linear.app/drgamtd-workspace/issue/DRG-571) | [#849](https://github.com/drgaciw/open-crm-cpq/pull/849), [#847](https://github.com/drgaciw/open-crm-cpq/pull/847) | [`maintenance.md`](specs/maintenance.md) — Proposal only |
| catalog-model | [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576) | [#831](https://github.com/drgaciw/open-crm-cpq/pull/831) | [`catalog-model.md`](specs/catalog-model.md) — Proposal only |
| co-termination | [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554) | [#842](https://github.com/drgaciw/open-crm-cpq/pull/842) | [`co-termination.md`](specs/co-termination.md) — Proposal only |
| partial-apply | [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) | [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) | [`partial-apply.md`](specs/partial-apply.md) — Proposal only |
| arr-bridge | [DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575) | [#852](https://github.com/drgaciw/open-crm-cpq/pull/852) | [`arr-bridge.md`](specs/arr-bridge.md) — Proposal only |
| ramp-amendments | [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534) (with [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533)) | [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | [`ramp-amendments.md`](specs/ramp-amendments.md) — Proposal only |
| spine-dependent | [DRG-592](https://linear.app/drgamtd-workspace/issue/DRG-592) / [594](https://linear.app/drgamtd-workspace/issue/DRG-594) / [595](https://linear.app/drgamtd-workspace/issue/DRG-595) / [597](https://linear.app/drgamtd-workspace/issue/DRG-597) / [598](https://linear.app/drgamtd-workspace/issue/DRG-598) / [599](https://linear.app/drgamtd-workspace/issue/DRG-599) | [#830](https://github.com/drgaciw/open-crm-cpq/pull/830), [#833](https://github.com/drgaciw/open-crm-cpq/pull/833), [#826](https://github.com/drgaciw/open-crm-cpq/pull/826), [#828](https://github.com/drgaciw/open-crm-cpq/pull/828), [#832](https://github.com/drgaciw/open-crm-cpq/pull/832), [#829](https://github.com/drgaciw/open-crm-cpq/pull/829) | [`spine-dependent.md`](specs/spine-dependent.md) — Proposed |
| renewal-automation | [DRG-555](https://linear.app/drgamtd-workspace/issue/DRG-555) / [593](https://linear.app/drgamtd-workspace/issue/DRG-593) / [596](https://linear.app/drgamtd-workspace/issue/DRG-596) (spec [DRG-643](https://linear.app/drgamtd-workspace/issue/DRG-643)) | [#819](https://github.com/drgaciw/open-crm-cpq/pull/819), [#820](https://github.com/drgaciw/open-crm-cpq/pull/820), [#845](https://github.com/drgaciw/open-crm-cpq/pull/845) | [`renewal-automation.md`](specs/renewal-automation.md) — Proposed |

Adjacent Wave-2 docs drafts in the same band (examples only; cite, do not adopt): [#822](https://github.com/drgaciw/open-crm-cpq/pull/822) DRG-564, [#823](https://github.com/drgaciw/open-crm-cpq/pull/823) DRG-588, [#855](https://github.com/drgaciw/open-crm-cpq/pull/855)–[#861](https://github.com/drgaciw/open-crm-cpq/pull/861) (560 / 612 / 551 / 566 / 536 / 539, …). Their open OQs stay on those tickets.

**Non-checklist proposal specs also on `main` (`read`):** [`apply-authorization.md`](specs/apply-authorization.md), [`amendment-reforecast.md`](specs/amendment-reforecast.md). They are **not** DRG-607 checklist lines. This umbrella does not accept them and does not invent checklist membership for them.

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.pricing.ramp.enabled` stays default off. No `opencrm.jev.*.enabled` flag is turned on. Dual-read / S4 enablement / asset-write holds stay in force. An implementation brief that ships product for any checklist cluster from this umbrella alone is **blocked**. **Product / schema / flag = NO-GO** while residual **OQ-607-\*** rows (and the open per-story Wave-2 OQs they cite) remain unscored. **Do not invent cluster schema, routes, or flags as landed from a proposal spec.**

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` / docs | Read / searched in the tree at the base SHA. Not executed. |
| `GitNexus` | This checkout has **no** usable GitNexus index (`query` returned no indexed repositories). Graph impact is **UNKNOWN** (unresolved), not a clean check. |

## Problem (umbrella)

DRG-607 tracks whether each renewals/amendments **policy cluster** has a **dev-ready proposal spec** under `docs/renewals-amendments/prereq/specs/`. Spec files landed for every checklist line (`read` on base). Linear checkboxes remain **unchecked**. Wave-2 residual Decision briefs reopen **delivery** forks per constituent story without accepting those specs.

Without an umbrella residual brief, a later PR could:

1. Treat a merged proposal spec (#611 / #701 / #705) as an accepted ADR or schema mandate.
2. Tick checklist lines because a file exists, skipping owner review and Wave-2 OQ closure.
3. Adopt open **OQ-\*** rows from Wave-2 drafts #803–#861 as if this umbrella scored them.
4. Claim CAM Flyway numbers inside the reserved **V1_16–V1_21** band for cluster product work.
5. Move DRG-607 to Done while product/schema/flag work is still **NO-GO**.

Linear DRG-607 (`Linear read` 2026-11-06): **In Review**; labels `renewals-amendments`, `Open-CRM-CPQ`, `type:docs`; related to the checklist constituent stories plus theme follow-ups (DRG-637…649, DRG-643/644/648, …). Hygiene comments: spine-dependent still needs owner tick decision; renewal-automation line recorded in git via #800 without ticking.

## Already on record (do not re-decide as blank slots)

| ID | Contract | Source |
|---|---|---|
| A1 | DRG-607 is a **docs-only** spec umbrella; relations are related-to, not blocks | Linear description (`Linear read`) |
| A2 | Status ceiling is **In Review**; Done requires owner approval | Linear description |
| A3 | Split a cluster into its own ticket **only when claimed** | Linear description |
| A4 | Nine cluster proposal specs landed via #611; they authorize **no** code | #611 merge + spec Status headers (`read`) |
| A5 | Spine-dependent proposal landed via #705/#732; DRG-648 Done does **not** auto-tick DRG-607 | Hygiene comment 2026-10-16 + specs (`read`) |
| A6 | Renewal-automation proposal landed via #701; DRG-643 Done; checklist membership hygiene is DRG-644 / #800 and **must not** tick from that note alone | Hygiene comments 2026-10-29 / 2026-11-06 |
| A7 | Many cluster **policy** forks are **Adopted (delegated)** 2026-10-17; proposal specs and Wave-2 residuals still carry open **delivery** OQs | Delegated record (`read`) |
| A8 | CAM **V1_16–V1_21** are reserved by open drafts #805–#812; docs briefs claim no slot in that band | PR titles/bodies (`read`) |
| A9 | Holds in `AGENT.md` (dual-read, NgRx, Jev flags, asset-write, ramp default off) stay in force | Project steering (`read`) |

## Inventory — remaining Proposed / open OQ clusters

All **11** DRG-607 checklist lines are **unchecked** (`Linear read`). Every corresponding `specs/<cluster>.md` on this base is **Proposal / Proposed** (`read`). Wave-2 residual Decision briefs for constituent stories remain **open drafts** with **Proposed** per-story OQs (cite table above; **do not adopt**).

| Cluster | Spec status on `main` | Wave-2 residual posture (cite only) | Umbrella residual |
|---|---|---|---|
| capacity-meters | Proposal only | Open OQs on #838 / #818 / #836 | Keep Proposed; no tick; no product |
| conversions | Proposal only | Open OQs on #850 / #848 / #853 | Same |
| lifecycle-intents | Proposal only | Open OQs on #854 / #834 / #835 | Same |
| maintenance | Proposal only | Open OQs on #849 / #847 | Same |
| catalog-model | Proposal only | Open OQs on #831 | Same |
| co-termination | Proposal only | Open OQs on #842 | Same |
| partial-apply | Proposal only | Open OQs on #839 | Same |
| arr-bridge | Proposal only | Open OQs on #852 | Same |
| ramp-amendments | Proposal only | Open OQs on #803; ramp flag stays off | Same |
| spine-dependent | Proposed | Open OQs on #830 / #833 / #826 / #828 / #832 / #829; Hygiene tick still owner | Same |
| renewal-automation | Proposed | Open OQs on #819 / #820 / #845; #800 membership record only | Same |

Theme follow-ups under the umbrella (related; not checklist ticks): DRG-637 partial-apply reconcile, DRG-638 co-term fixtures, DRG-639 ramp-amendment ADR align, DRG-640 conversion figure frame, DRG-641 true-up parameters, DRG-642 maintenance fixtures, DRG-643 renewal-automation spec (Done), DRG-644 checklist line (In Progress), DRG-645 ChangeLine codes, DRG-646 effective/action dates, DRG-647 catalog eligibility, DRG-648 spine-dependent spec (Done), DRG-649 SKU mapping deeper spec. Closing or drafting those stories does **not** accept cluster specs or authorize product from this umbrella.

## Owner-decision table (remaining umbrella forks)

Every row below is **Status: Proposed**. Alternatives sit beside the recommendation. Picking a row authorizes **no** code, migration, flag, checklist tick, or deploy.

| ID | Question | Recommendation (Proposed) | Alternatives | Blocks product? |
|---|---|---|---|---|
| OQ-607-1 | **Accept `specs/<cluster>.md` wholesale?** | Keep every checklist spec **Proposal / Proposed**. Score delivery via per-story Wave-2 residuals. Do **not** treat a merged proposal file as an ADR or schema mandate | Accept all eleven specs as ADR-equivalent now; accept only #611 nine and leave spine/renewal open | Yes — any “spec accepted ⇒ ship” claim |
| OQ-607-2 | **When may a checklist line be ticked?** | Tick only after owner words+time that the **proposal file is the accepted docs baseline for that cluster** *or* an explicit Hygiene ruling that “file on `main`” is enough for the checkbox — **without** implying product GO. Default: **do not tick** while Wave-2 blocking OQs for that cluster’s stories remain open | Tick because #611/#701/#705 merged; auto-tick when DRG-648/643 Done | Soft for docs Hygiene; hard if tick is read as product GO |
| OQ-607-3 | **Coupling to Wave-2 residual OQs (#803–#861 band)** | **Cite only.** This umbrella does **not** score, close, or adopt any `OQ-<story>-*` from sibling drafts. Per-story briefs remain the scoring surface | Close sibling OQs from this file; treat open Wave-2 drafts as Adopted by citation | Yes — if someone ships from a cited OQ |
| OQ-607-4 | **Spine-dependent Hygiene after DRG-648 Done** | Keep `spine-dependent` **unchecked** until owner answers OQ-607-2 for that line. Spec stays Proposed; Wave-2 residuals #826/#828–#833 remain cite-only | Tick now because #705/#732 landed; reopen DRG-648 | Soft (checkbox); hard for spine product |
| OQ-607-5 | **Renewal-automation line / DRG-644 / #800** | Keep membership record in git; **do not tick** from #800 or from this brief. Score renewal delivery on #819/#820/#845 (and related) without adopting their OQs here | Tick `renewal-automation` when #800 merges; mark DRG-555/593/596 Done from umbrella | Soft (checkbox); hard for automation product |
| OQ-607-6 | **Theme follow-ups DRG-637…649 vs umbrella Done** | Follow-ups may deepen fixtures/specs **under** the umbrella. Umbrella **Done** still needs owner approval after OQ-607-2 (and any named deferrals). Theme Done ≠ umbrella Done ≠ product GO | Require every 637–649 Done before any tick; drop theme tickets as out of umbrella | Soft for docs; hard if theme Done is read as ship |
| OQ-607-7 | **Split claimed clusters into their own tickets** | Keep A3: split **only when claimed**. This residual does not claim or split any cluster | Split all eleven now; forbid further splits | Soft |
| OQ-607-8 | **Non-checklist specs** (`apply-authorization`, `amendment-reforecast`) | Leave **outside** the DRG-607 checkbox set unless the owner adds a line. Do not accept them via this umbrella | Add checklist lines now; accept them as part of #611 scope | Soft until listed; hard if accepted silently |
| OQ-607-9 | **CAM Flyway / schema claims from umbrella** | **No** migration from this brief. Next free after **V1_16–V1_21**; number only at a separately authorized migration PR. Never edit landed V1_7–V1_15 in place | Claim V1_16–V1_21 for a cluster; ship schema when OQ-607-1 “accepts” a spec | Yes — schema |
| OQ-607-10 | **Status ceiling / Done gate** | Leave Linear **In Review**. Done only with owner words+time after checklist Hygiene (OQ-607-2) and explicit statement that Done ≠ product GO | Move to Done when this PR merges; move to Done when all Wave-2 drafts merge | Soft (Linear); hard if Done ⇒ implement |
| OQ-607-IMPL | **Implementation / flag / route authorization from the umbrella** | **NO-GO.** Separate implementation stories after per-story Wave-2 OQs close (or named deferrals). No ramp / Jev / dual-read / S4 / asset-write enablement from DRG-607 | Authorize product when specs exist on `main`; piggy-back flags in this PR | Yes — any product/schema/flag |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-607-1 — Spec wholesale acceptance

| Option | What the owner would be choosing |
|---|---|
| **A. Keep Proposal; score via per-story Wave-2 residuals (Recommended, Proposed)** | Spec files stay documentation proposals. Adopted (delegated) policy forks stay Adopted without promoting proposal ACs to schema. |
| **B. Accept all checklist specs as ADR / schema mandate now** | Over-reads Proposal headers; invents product authority. **NO-GO** without supersession. |
| **C. Accept only the #611 nine** | Leaves spine-dependent / renewal-automation as second-class without an explicit tick rule. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-607-2 — Checklist tick criteria

| Option | What the owner would be choosing |
|---|---|
| **A. Owner words+time per line; default leave unchecked while blocking Wave-2 OQs open (Recommended, Proposed)** | Separates “file exists” from “docs baseline accepted” from “product GO”. |
| **B. Tick because the proposal file is on `main`** | Matches a pure Hygiene reading; still must **not** authorize product. |
| **C. Auto-tick when related theme ticket is Done** | Conflicts with Hygiene notes on DRG-648 / DRG-644. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-607-3 — Wave-2 residual coupling

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #803–#861 band only; do not adopt sibling OQs (Recommended, Proposed)** | Umbrella inventories; stories own scoring. |
| **B. Close sibling OQs from this brief** | Out of scope; corrupts per-story Decision surfaces. **NO-GO.** |
| **C. Treat citation as adoption** | Silent product authorization. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-607-4 — Spine-dependent Hygiene

| Option | What the owner would be choosing |
|---|---|
| **A. Keep unchecked until OQ-607-2 answered for this line (Recommended, Proposed)** | Affirms 2026-10-16 Hygiene comment. |
| **B. Tick now (DRG-648 Done / #705)** | Checkbox-only; still no product. |
| **C. Reopen DRG-648** | Unnecessary; spec already on `main`. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-607-5 — Renewal-automation / DRG-644

| Option | What the owner would be choosing |
|---|---|
| **A. Membership via #800; no tick from #800 or this brief (Recommended, Proposed)** | Affirms 2026-11-06 Hygiene note. |
| **B. Tick when #800 merges** | Checkbox Hygiene only; still no product. |
| **C. Mark DRG-555/593/596 Done from umbrella** | Violates status ceiling and story ownership. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-607-6 — Theme follow-ups vs Done

| Option | What the owner would be choosing |
|---|---|
| **A. Themes deepen docs; umbrella Done still needs owner after OQ-607-2 (Recommended, Proposed)** | Keeps A2. |
| **B. Require 637–649 all Done before any tick** | Over-gates Hygiene; optional owner choice. |
| **C. Drop themes from umbrella scope** | Loses related inventory; needs explicit owner cut. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-607-7 — Split claimed clusters

| Option | What the owner would be choosing |
|---|---|
| **A. Split only when claimed (Recommended, Proposed)** | Affirms A3. |
| **B. Split all eleven now** | Process churn without claimers. |
| **C. Forbid splits** | Conflicts with Linear note. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-607-8 — Non-checklist specs

| Option | What the owner would be choosing |
|---|---|
| **A. Leave outside checkbox set (Recommended, Proposed)** | Avoids silent scope creep. |
| **B. Add checklist lines for apply-authorization / amendment-reforecast** | Explicit Linear edit; not this PR. |
| **C. Accept them under #611 narrative** | False history; those files are separate. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-607-9 — CAM Flyway reservation

| Option | What the owner would be choosing |
|---|---|
| **A. No migration; next free after V1_16–V1_21; number at migration PR (Recommended, Proposed)** | Affirms A8. |
| **B. Claim V1_16–V1_21 for a cluster** | Conflicts with #805–#812. **NO-GO.** |
| **C. Ship schema when a spec is “accepted” in docs only** | Product/schema **NO-GO** while OQ-607-IMPL open. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-607-10 — Status ceiling / Done gate

| Option | What the owner would be choosing |
|---|---|
| **A. Stay In Review; Done only with owner words+time and Done ≠ product GO (Recommended, Proposed)** | Affirms A2. |
| **B. Done when this Decision brief merges** | Over-reads a proposal PR. |
| **C. Done when all Wave-2 cluster drafts merge** | Still not product GO; optional Hygiene rule only if owner says so. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-607-IMPL — Implementation gate

| Option | What the owner would be choosing |
|---|---|
| **A. NO-GO until per-story Wave-2 OQs close or are deferred with words+time (Recommended, Proposed)** | Umbrella inventories; stories authorize implementation briefs. |
| **B. Product GO because specs exist on `main`** | Rejects Proposal headers. **NO-GO.** |
| **C. Flip ramp / Jev / dual-read / S4 / asset-write from DRG-607** | Violates holds. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit) — NO-GO

This residual Decision brief is **not** an implementation brief.

Forbidden until owner closes (or explicitly defers) the relevant **OQ-607-\*** rows **and** the per-story Wave-2 OQs for the cluster being built:

- Product code, gateway routes, Kafka consumers/producers, UI workflows for any checklist cluster
- Flyway / schema (especially **V1_16–V1_21**)
- Feature flags (`opencrm.pricing.ramp.enabled`, any `opencrm.jev.*.enabled`, dual-read / S4 / asset-write enables)
- Treating Proposal specs or Wave-2 drafts as Accepted ADRs
- Ticking DRG-607 checklist lines or moving DRG-607 to Done without owner words+time

## Holds this brief keeps

- `AGENT.md` dual-read / NgRx / Activities / Jev-enable holds
- Ramp default off
- S4 apply enablement and asset-write hold
- CAM V1_16–V1_21 reservation (#805–#812)
- Spec Status: Proposal / Proposed on every checklist file

## Out of scope

- Scoring or rewriting any `OQ-<story>-*` inside Wave-2 sibling briefs
- Editing Linear checklist markdown beyond what the owner later decides under OQ-607-2
- Accepting ADR-023 / DRG-533 escalator semantics, conversion figures, meter policy, or spine modes
- Authorizing S1–S8 delivery stories or CPQ service bring-up
- Security scan / runtime verification of services (docs-only change)

## GitNexus

`gitnexus` MCP `query` this pass: **no indexed repositories**. Impact for a new docs-only file is **UNKNOWN**, not an all-clear. Text search confirms no product code path is edited by this PR. Before commit, `detect_changes` should be attempted; a missing index is recorded here as unresolved graph coverage, not as zero blast radius.

## References

- Linear: [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607)
- Specs dir: [`docs/renewals-amendments/prereq/specs/`](specs/)
- Delegated record: [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md)
- Landed umbrella-related merges: [#611](https://github.com/drgaciw/open-crm-cpq/pull/611), [#701](https://github.com/drgaciw/open-crm-cpq/pull/701), [#705](https://github.com/drgaciw/open-crm-cpq/pull/705), [#732](https://github.com/drgaciw/open-crm-cpq/pull/732)
- Hygiene membership draft: [#800](https://github.com/drgaciw/open-crm-cpq/pull/800)
- CAM reservation drafts: [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)
- Wave-2 residual draft band (cite): roughly [#803](https://github.com/drgaciw/open-crm-cpq/pull/803)–[#861](https://github.com/drgaciw/open-crm-cpq/pull/861)
