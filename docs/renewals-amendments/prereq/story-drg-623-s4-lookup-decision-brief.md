# DRG-623 — S4 subscription lookup by uuid / TARGET_CONTRACT_UNSET: residual Decision brief

**Status:** Proposal only. Wave-2 residual Decision brief for CPQ Decision scoring. This file does **not** authorize further CAM apply product code, does **not** enable the S4 apply endpoint or flags ([DRG-625](https://linear.app/drgamtd-workspace/issue/DRG-625) / [#868](https://github.com/drgaciw/open-crm-cpq/pull/868)), does **not** authorize S4 / [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) product Done ([#866](https://github.com/drgaciw/open-crm-cpq/pull/866)), does **not** adopt open OQs from cite-only [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) / [#868](https://github.com/drgaciw/open-crm-cpq/pull/868) or the wave-2 peer draft path that includes [#824](https://github.com/drgaciw/open-crm-cpq/pull/824) / [#825](https://github.com/drgaciw/open-crm-cpq/pull/825) / [#827](https://github.com/drgaciw/open-crm-cpq/pull/827), does **not** reopen Adopted OQ5 option A / OQ6 / OQ7 / OQ10 / OQ18, does **not** reopen the **Accepted** DRG-623 option A membership rule, does **not** flip dual-read, Jev, ramp, NgRx, or asset-write holds, does **not** mark Linear Done, and does **not** open a Flyway migration (CAM `V1_16`–`V1_21` stay reserved for [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)). Every residual fork below is `Status: Proposed` and labeled **Proposed OQ-623-***. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Product / schema / flag NO-GO.** Merge of this docs PR authorizes **no** product landing, schema, Flyway, feature-flag flip, gateway route, dual-read flip, S4 enablement, asset-lifecycle hold release, DRG-627 retry tooling, or deploy. Draft code on [#827](https://github.com/drgaciw/open-crm-cpq/pull/827) is **cite-only evidence of direction**, not authorization from this brief.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / S4 document apply membership. **Story:** [DRG-623](https://linear.app/drgamtd-workspace/issue/DRG-623/s4-subscription-lookup-by-uuid-only). Linear read this pass: **In Review**. This brief leaves that status unchanged.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

## Explicit S4 / DRG-625 / dual-read hold (NO-GO)

| Hold | Status in this brief |
|---|---|
| **S4 / DRG-582 enablement** | **NO-GO.** Landed flag-off orchestrator / ledger from [#685](https://github.com/drgaciw/open-crm-cpq/pull/685) is **not** Done and is **not** authorization to call apply in product. Delivery residuals live on [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) (**cite only**). |
| **DRG-625 apply endpoint / flag enablement** | **NO-GO.** Owner gate; residual forks on [#868](https://github.com/drgaciw/open-crm-cpq/pull/868) (**cite only**). This brief does not enable the endpoint or any S4 flag. |
| **Dual-read / ADR-005** | **NO-GO.** `approvals.legacy-local-read-enabled` / `APPROVALS_LEGACY_LOCAL_READ` stay as held in [AGENT.md](../../../AGENT.md) and [docs/ARCHITECTURE.md](../../ARCHITECTURE.md) §17. Untouched. |
| **Asset-write / Jev / ramp / NgRx / `#749` lifecycle** | **NO-GO.** AGENT.md / ARCHITECTURE / [s4-asset-lifecycle-hold](../../runbooks/s4-asset-lifecycle-hold.md) holds untouched. `AssetLifecycleReadiness.isReady()` stays false. |
| **CAM Flyway `V1_16`–`V1_21`** | **RESERVED** (#805–#812). This brief writes **no** migration and does not consume those slots. |
| **DRG-660 lineage backfill** | **NO-GO.** Decision-in-principle only; needs green IT, named environment/tenant, dry-run counts, restore point, and explicit owner execution GO. |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, dual-read flip, Jev enablement, ramp enable, S4 apply endpoint enablement (DRG-625), asset-write / lifecycle hold bypass, DRG-627 recovery tooling, or deploy.**

## Spec / frame authority (cite; do not reopen closed cells)

| Artifact | Role |
|---|---|
| [#685](https://github.com/drgaciw/open-crm-cpq/pull/685) (merged) | Landed S4 **flag-off** document apply orchestrator + ledger. Source of the original “uuid only” review note. Evidence of direction — **not** Done, **not** DRG-625 enablement. |
| Owner decision: DRG-623 option A | **Accepted** (voice 2026-10-29 ~13:30 Dublin; Linear comment + [Notion record](https://app.notion.com/p/408f7cb4e4df81f48e3ace381db17315)). Exact `subscriptions.contract_id` == document contract; missing contract → `APPLY_UNIT_BLOCKED` / `TARGET_CONTRACT_UNSET`; never infer a contract. Options B/C rejected. **Cite; do not reopen.** |
| [`docs/plans/cpq-next-wave-readiness-20261101.md`](../../plans/cpq-next-wave-readiness-20261101.md) § DRG-623 | Names the residual audit distinction on `main` and acceptance criteria. Proposed delivery frame — **not** product authorization from this brief. |
| [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) DRG-582 residual | Delivery residuals **OQ-582-***. **Cite only. Do not adopt.** |
| [#868](https://github.com/drgaciw/open-crm-cpq/pull/868) DRG-625 residual | Enablement residuals **OQ-625-***. **Cite only. Do not adopt.** |
| Wave-2 peer draft path [#824](https://github.com/drgaciw/open-crm-cpq/pull/824) / [#825](https://github.com/drgaciw/open-crm-cpq/pull/825) / [#827](https://github.com/drgaciw/open-crm-cpq/pull/827) | Recovery contract (#824), unrelated gateway CB routes (#825), TARGET_CONTRACT_UNSET code draft (#827). **Cite only. Do not adopt open OQs or treat any as enablement.** |
| [`partial-apply-owner-decision.md`](partial-apply-owner-decision.md) / register OQ5 A | **Adopted** first-slice partial-apply cut. Cite; do not reopen. |
| [`oq6-revision-model-owner-decision.md`](oq6-revision-model-owner-decision.md) (#642) | **Adopted** executed immutability. Cite; do not reopen. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) | Adopted CAM contracts. Cite; do not reopen. |

## In-flight alignment (cite only — do not adopt open OQs)

| Ticket / artifact | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) residual Decision brief | Draft [#866](https://github.com/drgaciw/open-crm-cpq/pull/866); **In Review** | Delivery forks **OQ-582-***. **Cite only. Do not adopt.** |
| [DRG-625](https://linear.app/drgamtd-workspace/issue/DRG-625) residual Decision brief | Draft [#868](https://github.com/drgaciw/open-crm-cpq/pull/868); **Backlog** | Enablement forks **OQ-625-***. **Cite only. Do not adopt. Do not enable.** |
| [DRG-627](https://linear.app/drgamtd-workspace/issue/DRG-627) recovery contract | Draft [#824](https://github.com/drgaciw/open-crm-cpq/pull/824) | Recovery policy / tooling. Serialization note: no parallel writers with DRG-623 in CAM apply sources. **Cite only. Do not implement retry tooling here.** |
| Gateway CB routes (DRG-528) | Draft [#825](https://github.com/drgaciw/open-crm-cpq/pull/825) | Unrelated surface. Wave-2 peer path only. **Cite only. Do not pull into S4 membership.** |
| TARGET_CONTRACT_UNSET code draft | Draft [#827](https://github.com/drgaciw/open-crm-cpq/pull/827); branch `cursor/drg-623-target-contract-unset-4fa7` | Implements Accepted option A membership enum on a draft PR. **Cite only.** This residual brief does **not** authorize merge, Done, or enablement. |
| [DRG-621](https://linear.app/drgamtd-workspace/issue/DRG-621) | **Done** (`Linear read`); [#688](https://github.com/drgaciw/open-crm-cpq/pull/688) / [#718](https://github.com/drgaciw/open-crm-cpq/pull/718) | Mid-loop DIGEST_CONFLICT / document-outcome. Landed. Not membership enablement. |
| [DRG-660](https://linear.app/drgamtd-workspace/issue/DRG-660) | Tracking-only | Lineage backfill (option D in principle). **NO-GO** for execution. |

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted` / `Accepted` | Already selected outside this file. Restated; **not** re-opened. |
| `Status: Proposed` | Residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA (or named draft branch). Not executed. |

GitNexus MCP reports **no indexed repositories** usable for this docs pass; `.gitnexus/run.cjs` impact against product symbols is **not** claimed as clean. This brief edits **no** product symbols. Code claims below are text/`rg` / `git show` evidence only.

## Why this residual Decision brief exists

S4 review of [#685](https://github.com/drgaciw/open-crm-cpq/pull/685) filed **DRG-623** because apply target lookup is by subscription **uuid** only, with a later membership check that (on `main`) collapses every failed membership into `TARGET_CONTRACT_MISMATCH`. Owner Accepted **option A** (exact contract equality; null contract → `TARGET_CONTRACT_UNSET`; no inference). Wave-2 refill assigns this **docs-only** residual brief to inventory still-open **DRG-623** forks as **Proposed OQ-623-*** for CPQ Decision — without authorizing product/schema/flag work, without adopting #866 / #868 / #824–#827-path open OQs, and without flipping dual-read or reserved Flyway slots. Linear stays **In Review**.

## Story restatement — scope only

`verified by reading the issue` (Linear DRG-623 description + comments).

- Original defect note: apply orchestrator subscription lookup is by **uuid only** (found in S4 review of #685 / story DRG-582).
- Membership residual (Accepted option A): require `subscriptions.contract_id` equals `amendment_documents.contract_id` (same tenant); missing contract blocks the unit as `APPLY_UNIT_BLOCKED` / `TARGET_CONTRACT_UNSET`; wrong contract or tenant stays mismatch; apply never infers a contract from an asset or another chain.
- Out of scope for this brief: S4 enablement (DRG-625), recovery tooling (DRG-627), lineage backfill execution (DRG-660), schema / Flyway, dual-read / Jev / ramp / asset holds.

This restatement does **not** authorize merging [#827](https://github.com/drgaciw/open-crm-cpq/pull/827), calling apply in product, or closing residual forks.

## Landed evidence on `main` (not new authorization)

Facts `verified by reading code` at the base SHA. They show direction already partially present. They do **not** close Proposed OQ-623-* and do **not** authorize product Done, DRG-625 enablement, dual-read flip, or flag-on routing.

| Fact | Evidence |
|---|---|
| Subscription lock + lookup keyed by uuid string | `JdbcDocumentApplyGateway.lockSubscription` / `subscription(String subscriptionUuid)` — `where uuid = ?` |
| Membership on `main` is boolean | `DocumentApplyGateway.targetBelongsToContract` → `boolean`; join requires non-null `s.contract_id` |
| Failed membership maps only to `TARGET_CONTRACT_MISMATCH` | `DocumentApplyOrchestrator.applyUnit` ternary on `!gateway.targetBelongsToContract(...)` |
| `TARGET_CONTRACT_UNSET` string absent on `main` mapping | `DocumentApplyPeriods` detail switch has `TARGET_CONTRACT_MISMATCH` only (`read`) |
| Missing subscription remains a separate path | Orchestrator loads target then `findByIdForUpdate`; null subscription feeds period/`V3` path, not membership ternary alone |
| `#749` readiness hard-false | `AssetLifecycleReadiness.isReady()` returns `false`; hold refuses before unit membership when pending asset work exists |
| CAM migrations present through `V1_15` | Inventory under `backend/services/contract-asset-management/src/main/resources/db/migration/` |
| No CAM `V1_16`–`V1_21` files | Directory listing this pass — reserved range untouched |

## Draft evidence on [#827](https://github.com/drgaciw/open-crm-cpq/pull/827) (cite only — not authorization)

`verified by reading` draft branch `cursor/drg-623-target-contract-unset-4fa7` (SHA `055f802a`, `Linear read` / PR body). Direction only.

| Fact | Evidence |
|---|---|
| Membership enum `MATCHED` / `UNSET` / `MISMATCH` | `TargetContractMembership.java` |
| Null `subscriptions.contract_id` → `UNSET` | `JdbcDocumentApplyGateway.targetContractMembership` |
| Orchestrator maps `UNSET` → `TARGET_CONTRACT_UNSET`, else mismatch | Draft orchestrator / periods mapping (`read` PR files) |
| No Flyway / flag / frontend / DRG-627 tooling in that PR | PR body out-of-scope list |

**This brief does not adopt, merge, or score #827.** Product/schema/flag remain **NO-GO** from this file.

## Adopted / Accepted facts this brief cites and does not reopen

| Cell | Source |
|---|---|
| DRG-623 **option A Accepted** — exact contract equality; null → `TARGET_CONTRACT_UNSET`; no inference; B/C rejected | Linear comment 2026-10-29; Notion owner decision page |
| Option D lineage backfill in principle → **DRG-660**, S7-class, execution **NO-GO** until owner GO | Same owner record / readiness plan |
| OQ5 option A (first slice) | `#626` / `#631` / `#632`; register OQ5 |
| OQ6 executed immutability | `oq6-revision-model-owner-decision.md` |
| S4 enablement is a **separate owner gate** | DRG-625 / #868 |
| `#749` / `ASSET_LIFECYCLE_HOLD` authoritative | Runbook + readiness |

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No new Adopted cells in this table.** Picking a row authorizes **no** product code, schema, flag flip, endpoint enablement, dual-read flip, or deploy. Accepted option A is restated only where marked **Accepted (cite)** — not re-opened.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product / schema / flag? |
|---|---|---|---|---|
| **OQ-623-1** | **Residual scope vs original “uuid only” title** | Keep residual scope = **membership audit distinction** under Accepted option A. Uuid-keyed load stays; do **not** redesign lookup keys (tenant/contract composite) in this story without a new owner record | Expand to composite lookup keys now; treat title as requiring schema | Soft for docs; **hard** if used to force schema |
| **OQ-623-2** | **Null `contract_id` audit reason** | Restate **Accepted option A**: block as `APPLY_UNIT_BLOCKED` / `TARGET_CONTRACT_UNSET`; no rates / ledger / commercial revision mutation for that unit | Reopen B/C; keep collapsing to `TARGET_CONTRACT_MISMATCH` forever | Soft (Accepted); hard if reopened |
| **OQ-623-3** | **Mismatch vs unset vs missing subscription** | Keep three distinguishable outcomes: missing subscription (existing `V3`/absent path), `TARGET_CONTRACT_UNSET`, `TARGET_CONTRACT_MISMATCH` (wrong contract/tenant). No inference / no silent populate | Collapse unset into mismatch; invent a fourth “infer from asset” path | Soft for scoring; hard if inference is chosen |
| **OQ-623-4** | **Contract inference** | **Never** infer contract from asset, sibling unit, or other chain (**Accepted**) | Infer from asset lineage; backfill during apply | Yes — hard (**Accepted NO**) |
| **OQ-623-5** | **`#749` hold precedence** | Hold refuses before unit membership when pending asset-mutating work exists; readiness stays false. Membership distinction must not bypass hold | Bypass hold to exercise unset path in prod; flip readiness | Yes — hard |
| **OQ-623-6** | **Replay / multi-unit siblings** | Replay produces no duplicate block audit for same terminal evidence; committed siblings stay committed (OQ5 A). Document outcome rules unchanged | Re-block audit on every replay; silent rollback of APPLIED siblings | Soft; hard if rollback chosen |
| **OQ-623-7** | **Serialization with DRG-627 / #824** | Cite #824 only. When product code runs, **no parallel writers** with DRG-627 in CAM apply sources. This brief implements **neither** | Parallel agents editing orchestrator/gateway/periods; invent recovery tooling here | Soft for docs; hard for code coordination |
| **OQ-623-8** | **Relation to #866 / #868** | Cite **OQ-582-*** / **OQ-625-*** only. Do **not** adopt or close them. Membership hygiene ≠ enablement | Close 582/625 forks here; treat this brief as S4 enablement | Soft here; **hard on #866/#868** — do not adopt |
| **OQ-623-9** | **Relation to #825-path peers (#824/#825/#827)** | Cite wave-2 peer drafts only. Do **not** adopt #824 recovery OQs, #825 gateway changes, or treat #827 merge as authorization from this file | Bundle peers as automatic Done; pull gateway CB into membership | Soft; hard if used as silent GO |
| **OQ-623-10** | **AGENT.md / ARCHITECTURE holds** | Leave dual-read, asset-write, Jev, ramp, NgRx holds **untouched** | Flip dual-read with membership fix; enable asset-write / `opencrm.jev.*` | Yes — explicit holds |
| **OQ-623-11** | **CAM Flyway** | Write **no** new migration here. Do **not** consume reserved **`V1_16`–`V1_21`**. Membership distinction needs no schema | Use V1_16–V1_21; mutate subscription FK constraints under this story | Yes — reservation |
| **OQ-623-12** | **DRG-660 lineage backfill** | Remain tracking-only **NO-GO**. Decision-in-principle ≠ execution permission | Run backfill because option A is Accepted; couple backfill to #827 | Yes — hard |
| **OQ-623-13** | **Linear status / Done gate** | Remain **In Review**. Docs / #827 draft ≠ Done; ≠ DRG-625 enablement. This brief does **not** move status | Mark Done because option A Accepted; mark Done because this docs PR exists; mark Done because #827 exists | Yes — tracker |
| **OQ-623-IMPL** | **Implementation authorization** | Separate owner record **after** OQ-623-1..13 (or an owner-deferred subset) close **may** authorize CAM membership mapping aligned to Accepted option A — still without S4 enablement, Flyway reservation breach, dual-read flip, hold release, or DRG-627 tooling. **This residual brief alone is hard NO-GO** for product/schema/flag | Treat this brief or #827 draft presence as authorization; piggy-back enablement on #866 / #868 | Yes — hard NO-GO until then |

`Owner decision: ___ (drg, time)` for every **Proposed** row above. Accepted option A cells stay Accepted outside this table's reopen power.

---

## Fork detail

### OQ-623-1 — Residual scope vs “uuid only” title

| Option | What the owner would be choosing |
|---|---|
| **A. Membership audit residual only (Recommended, Proposed)** | Matches readiness plan + Accepted option A; uuid load retained. |
| **B. Redesign lookup keys now** | New story / schema surface — out of this brief. |
| **C. Title requires schema** | Rejected by Accepted option A frame. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-623-2 — Null `contract_id` audit reason

| Option | What the owner would be choosing |
|---|---|
| **A. `TARGET_CONTRACT_UNSET` (Accepted — cite only)** | Owner voice 2026-10-29. Restated; not re-opened. |
| **B/C. Rejected alternatives** | Do not reopen. |

**Recommended:** Restate Accepted A. Residual scoring may still confirm audit wiring without reopening B/C. `Owner decision: ___ (drg, time)` only if owner elects to reopen (not recommended).

### OQ-623-3 — Distinguishability

| Option | What the owner would be choosing |
|---|---|
| **A. Missing / UNSET / MISMATCH distinct (Recommended, Proposed)** | Matches readiness acceptance. |
| **B. Collapse UNSET into MISMATCH** | Undoes Accepted A. |
| **C. Infer / populate contract** | Forbidden by Accepted A. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-623-4 — Contract inference

| Option | What the owner would be choosing |
|---|---|
| **A. Never infer (Accepted — cite only)** | Binding. |
| **B. Infer from asset / sibling** | Rejected. |

**Recommended:** Keep Accepted A. `Owner decision: ___ (drg, time)` only to reopen (not recommended).

### OQ-623-5 — `#749` hold precedence

| Option | What the owner would be choosing |
|---|---|
| **A. Hold before membership; readiness false (Recommended, Proposed)** | Matches runbook + readiness. |
| **B. Bypass hold for unset path** | Hold violation — **NO-GO**. |
| **C. Flip readiness here** | Hold release — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-623-6 — Replay / siblings

| Option | What the owner would be choosing |
|---|---|
| **A. No duplicate block audit; no sibling rollback (Recommended, Proposed)** | Affirms OQ5 A. |
| **B. Re-audit every replay** | Noise / evidence rewrite risk. |
| **C. Silent sibling rollback** | Reopens OQ5 A. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-623-7 — Serialization with DRG-627 / #824

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #824; serial writers when code starts (Recommended, Proposed)** | Matches readiness + #824 brief. |
| **B. Parallel writers** | Conflict risk on reason codes. |
| **C. Implement recovery here** | Out of scope — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-623-8 — Relation to #866 / #868

| Option | What the owner would be choosing |
|---|---|
| **A. Cite only; do not adopt (Recommended, Proposed)** | Delivery / enablement stay on their briefs. |
| **B. Close OQ-582-* / OQ-625-* here** | Out of scope. |
| **C. Treat this brief as enablement** | Collapses hygiene into DRG-625 — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-623-9 — Relation to #825-path peers

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #824 / #825 / #827 only; do not adopt (Recommended, Proposed)** | Wave-2 peer path; #827 is draft direction only. |
| **B. Bundle peers into Done** | Tracker inflation. |
| **C. Pull #825 gateway into membership** | Unrelated surface. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-623-10 — Dual-read / asset-write / Jev / ramp / NgRx holds

| Option | What the owner would be choosing |
|---|---|
| **A. Leave holds untouched (Recommended, Proposed)** | AGENT.md / ARCHITECTURE §17 stand. |
| **B. Flip dual-read with membership** | Explicitly held — **NO-GO**. |
| **C. Enable asset-write / Jev / ramp from apply** | Same. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-623-11 — CAM Flyway reservation

| Option | What the owner would be choosing |
|---|---|
| **A. No new migration; V1_16–V1_21 reserved (Recommended, Proposed)** | Honors #805–#812. Membership needs no schema. |
| **B. Consume V1_16–V1_21 now** | Violates reservation. |
| **C. Mutate subscription FK under this story** | Schema — **NO-GO** here. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-623-12 — DRG-660 lineage backfill

| Option | What the owner would be choosing |
|---|---|
| **A. Stay execution NO-GO (Recommended, Proposed)** | Decision-in-principle only. |
| **B. Run because option A Accepted** | Conflates policy with S7 execution. |
| **C. Couple backfill to #827** | Out of contract. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-623-13 — Linear status / Done gate

| Option | What the owner would be choosing |
|---|---|
| **A. Stay In Review (Recommended, Proposed)** | Matches Wave-2 instruction: leave status unchanged. |
| **B. Done because option A Accepted** | Policy ≠ delivery DoD / enablement. |
| **C. Done because this docs PR or #827 exists** | Explicitly false. |

**Recommended (Proposed):** A. This brief does **not** transition Linear. `Owner decision: ___ (drg, time)`.

### OQ-623-IMPL — Implementation authorization

| Option | What the owner would be choosing |
|---|---|
| **A. Separate owner record after residual OQx close (Recommended, Proposed)** | Hard **NO-GO** on product/schema/flag/enablement from this brief alone. |
| **B. Merge of this brief = authorization** | Explicitly false. |
| **C. Enable dual-read / reserved Flyway / DRG-625 / hold release on merge** | Violates holds. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Relation map: #866 / #868 / #825-path (cite only)

| External item | Relation to this brief | What this brief must not do |
|---|---|---|
| [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) DRG-582 residual | Delivery residuals OQ-582-* | Adopt, close, or re-score those OQs; treat as enablement |
| [#868](https://github.com/drgaciw/open-crm-cpq/pull/868) DRG-625 residual | Enablement residuals OQ-625-* | Adopt, close, or enable endpoint/flags |
| [#824](https://github.com/drgaciw/open-crm-cpq/pull/824) DRG-627 recovery | Serialization peer | Implement recovery tooling; parallel-edit apply sources without serial handoff |
| [#825](https://github.com/drgaciw/open-crm-cpq/pull/825) gateway CB (DRG-528) | Unrelated wave-2 peer | Pull gateway changes into membership scope |
| [#827](https://github.com/drgaciw/open-crm-cpq/pull/827) TARGET_CONTRACT_UNSET code | Draft direction for Accepted option A | Treat draft as authorization from this file; mark Done; flip holds |
| CAM `V1_16`–`V1_21` (#805–#812) | Reserved Flyway range | Claim those versions; write migrations |
| Dual-read ADR-005 | AGENT.md / ARCHITECTURE hold | Flip `APPROVALS_LEGACY_LOCAL_READ` |

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Story | DRG-623 (S4 uuid lookup / TARGET_CONTRACT_UNSET residual) |
| Artifact | Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ-623-1..OQ-623-13 and OQ-623-IMPL (Accepted option A remains Accepted unless explicitly reopened) |

Example shape once scored (illustrative only, **not** a claim): `DRG-623 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## What this Decision brief does not do

- Does not authorize CAM apply product code, schema, Flyway, or flag flips.
- Does not enable the S4 apply endpoint or any S4 flag (`document-path-tenants` stays empty / OFF).
- Does not authorize S4 / DRG-582 product Done.
- Does not mark any **Proposed** fork Adopted; does not fill any `Owner decision: ___ (drg, time)` slot.
- Does not reopen Accepted DRG-623 option A, Adopted OQ5 A, OQ6, OQ7, OQ10, or OQ18.
- Does not adopt open OQs from [#866](https://github.com/drgaciw/open-crm-cpq/pull/866), [#868](https://github.com/drgaciw/open-crm-cpq/pull/868), or the #825-path peers [#824](https://github.com/drgaciw/open-crm-cpq/pull/824) / [#825](https://github.com/drgaciw/open-crm-cpq/pull/825) / [#827](https://github.com/drgaciw/open-crm-cpq/pull/827).
- Does not write CAM Flyway (reserved `V1_16`–`V1_21` untouched).
- Does not flip dual-read, Jev, ramp, NgRx, asset-write, or `#749` lifecycle readiness.
- Does not implement DRG-627 recovery tooling or DRG-660 backfill.
- Does not mark Linear DRG-623 Done (leaves **In Review**).
- Does not merge or enable auto-merge on any PR (including #827).

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close **Proposed OQ-623-1..OQ-623-13** and **OQ-623-IMPL**. Until then, further product/schema/flag work under DRG-623 from this brief is **NO-GO**.
2. **#866 / #868:** cite-only; their open OQs stay open; S4 enablement stays **NO-GO**.
3. **#825-path peers (#824 / #825 / #827):** cite-only; #827 draft ≠ authorization from this file.
4. **CAM Flyway `V1_16`–`V1_21`:** reserved (#805–#812); this brief writes none.
5. **AGENT.md / ARCHITECTURE §17 holds:** dual-read, NgRx, Jev enablement, ramp, asset-write — untouched; `#749` readiness stays false.
6. **DRG-660:** lineage backfill remains execution **NO-GO**.
7. **Linear:** remains **In Review** — do not invent Done.
8. **GitNexus:** impact not claimed clean this docs pass.

## Sources read this pass

- Linear: DRG-623 (description, attachments [#827](https://github.com/drgaciw/open-crm-cpq/pull/827), comments including Accepted option A + wave-2 refill, status **In Review**).
- Cite-only drafts: [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) (DRG-582), [#868](https://github.com/drgaciw/open-crm-cpq/pull/868) (DRG-625), [#824](https://github.com/drgaciw/open-crm-cpq/pull/824) (DRG-627), [#825](https://github.com/drgaciw/open-crm-cpq/pull/825) (gateway CB peer), [#827](https://github.com/drgaciw/open-crm-cpq/pull/827) (TARGET_CONTRACT_UNSET code).
- Design / gates: [`docs/plans/cpq-next-wave-readiness-20261101.md`](../../plans/cpq-next-wave-readiness-20261101.md); Notion owner decision DRG-623 option A; OQ5 / OQ6 owner decisions; delegated 2026-10-17 record; `docs/runbooks/s4-asset-lifecycle-hold.md`.
- Code (`verified by reading code` at base SHA): `JdbcDocumentApplyGateway.java` (uuid lookup + boolean membership); `DocumentApplyOrchestrator.java` (`TARGET_CONTRACT_MISMATCH` collapse); `DocumentApplyPeriods.java`; CAM migration inventory through `V1_15`. Draft branch `cursor/drg-623-target-contract-unset-4fa7` for cite-only enum direction.
- Holds: `AGENT.md`, `docs/ARCHITECTURE.md` §17; CAM Flyway V1_16–V1_21 reservation (#805–#812).
