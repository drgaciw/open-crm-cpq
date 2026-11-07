# DRG-655 — Asset modify / cancel / supersede via ChangeLine: Decision brief (residual forks)

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring. This file **cites** the merged delivery [#741](https://github.com/drgaciw/open-crm-cpq/pull/741) (`AssetChangeApply` inside `DocumentApplyOrchestrator`), the [asset write hold](../../runbooks/asset-write-hold.md), the [S4 asset-lifecycle hold](../../runbooks/s4-asset-lifecycle-hold.md), related Wave-2 draft **[#870](https://github.com/drgaciw/open-crm-cpq/pull/870)** (DRG-654 asset-create residual Decision brief) **without adopting open OQ-654-\***, related Wave-2 draft **[#873](https://github.com/drgaciw/open-crm-cpq/pull/873)** (DRG-656 asset-history residual Decision brief) **without adopting open OQ-656-\***, and related Wave-2 draft **[#864](https://github.com/drgaciw/open-crm-cpq/pull/864)** (S7 / DRG-585 residual Decision brief) **without adopting open OQ-585-\*** (or any other open OQ set). It inventories **remaining owner forks as `OQ-655-*`**. **It authorizes no production code path, no schema change, no Flyway migration (including inventing a CAM version that collides with reserved draft slots V1_16–V1_21 on [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), no feature flag, no dual-read flip, no Jev enablement, no ramp enable, no S7 apply/hold bypass, no asset-write hold bypass, no S4 readiness release, no environment dry-run against real tenants, and no deploy.** Product / schema / flag work that makes `AssetLifecycleReadiness.isReady()` return true, enables `opencrm.assets.legacy-acceptance-write-enabled`, treats Linear **Done** or merged [#741](https://github.com/drgaciw/open-crm-cpq/pull/741) as production lifecycle-GO, invents a parallel apply path / new refusal codes under this ticket, or consumes reserved CAM Flyway slots is **NO-GO** until the owner closes the residual OQ-655-\* forks below (or explicitly defers a named fork with words and a time). Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** installed base / asset amend. **Story:** [DRG-655](https://linear.app/drgamtd-workspace/issue/DRG-655/asset-b-modify-cancel-or-supersede-assets-through-amendment-changeline) (Asset (b)). Linear read this pass: **Done** (completed after [#741](https://github.com/drgaciw/open-crm-cpq/pull/741) merge). **Design / code ≠ enablement** — asset-write hold and S4 asset-lifecycle hold stay in force; this brief does **not** restore In Review (prefer leave Done unless clearly wrong like DRG-600).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

## Alignment (cite; do not reopen closed cells; do not adopt open OQs)

| Artifact | Role |
|---|---|
| [DRG-653](https://linear.app/drgamtd-workspace/issue/DRG-653) ADR Accepted (drg, 2026-10-29 14:11 Dublin) | Aggregate `Asset`; Proposed event schemas; OHS + outbox. Placement vs CAM (DRG-561) remains open. **Cite; do not re-litigate Accepted ADR.** |
| Merged [#741](https://github.com/drgaciw/open-crm-cpq/pull/741) / `AssetChangeApply` | Landed ChangeLine → asset ops inside the existing document-apply unit: `UPDATE` → `AssetAmended`; `REMOVE`/`TERMINATE` → `AssetTerminated`; `SWAP`/`UPGRADE` → successor create then `AssetSuperseded`. Registered event contracts; outbox + audit; replay unit tests. **Cite as tree fact; not authorization to release S4 readiness or enable asset writes.** |
| [asset-write-hold.md](../../runbooks/asset-write-hold.md) | Acceptance creates subscriptions/rates without assets while hold is on; replay after lift does **not** backfill; no repair of existing rows/events. Legacy writer does **not** implement replacement / termination / ordered lifecycle. **Stays in force.** |
| [s4-asset-lifecycle-hold.md](../../runbooks/s4-asset-lifecycle-hold.md) / #749 | New S4 apply that would mutate installed assets → audited HTTP 503 `ASSET_LIFECYCLE_HOLD`. No runtime switch. **Stays in force.** |
| Wave-2 draft **[#870](https://github.com/drgaciw/open-crm-cpq/pull/870)** (DRG-654) | **Cite only.** Inventories **OQ-654-\***. This brief **does not adopt** any OQ-654-\* cell and does not authorize enabling the acceptance-time writer or inventing activation-time create under 654. |
| Wave-2 draft **[#873](https://github.com/drgaciw/open-crm-cpq/pull/873)** (DRG-656) | **Cite only.** Inventories **OQ-656-\***. This brief **does not adopt** any OQ-656-\* cell and does not authorize treating history OHS as complete installed base or releasing readiness “to fill history.” |
| Wave-2 draft **[#864](https://github.com/drgaciw/open-crm-cpq/pull/864)** (S7 / DRG-585) | **Cite only.** Inventories **OQ-585-\***. This brief **does not adopt** any OQ-585-\* cell and does not authorize S7 `contract_header_backfill_*`, asset lineage backfill, or hold bypass. |
| Related delivery (cite as tree fact) | [DRG-654](https://linear.app/drgamtd-workspace/issue/DRG-654) / [#713](https://github.com/drgaciw/open-crm-cpq/pull/713) acceptance-time create (held off). [DRG-656](https://linear.app/drgamtd-workspace/issue/DRG-656) / [#719](https://github.com/drgaciw/open-crm-cpq/pull/719) history projection. [DRG-621](https://linear.app/drgamtd-workspace/issue/DRG-621) / [DRG-627](https://linear.app/drgamtd-workspace/issue/DRG-627) apply-unit / blocked-unit contracts reused by #741. **Cite; Linear Done on 655 is not S4 enablement.** |
| Related S7-class asset tracks | [DRG-657](https://linear.app/drgamtd-workspace/issue/DRG-657) / [DRG-660](https://linear.app/drgamtd-workspace/issue/DRG-660) remain **S7-class NO-GO**. Header/lineage backfill does **not** seed amend/terminate/supersede under this brief. |
| CAM Flyway draft slots | [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) reserve **V1_16–V1_21**. This brief invents **no** migration number and does **not** edit landed `V1_13` / `V1_14` / `V1_15` in place. #741 added **no** Flyway. |

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (owner)` | Already selected by an owner act outside this file. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read / searched in the tree at the base SHA. Not executed. |
| `verified by GitNexus` | Impact/query after `npx gitnexus@latest analyze --index-only` on this checkout. Process walk reported **truncated** entry-point coverage — not a clean graph. |

## Problem (why a residual brief)

[#741](https://github.com/drgaciw/open-crm-cpq/pull/741) merged `AssetChangeApply` onto `main` and Linear moved **Done** (`Linear read`, completed 2026-11-01). The code maps ChangeLine actions onto installed assets **inside** the existing DRG-621 apply unit and reuses `APPLY_UNIT_*` outcomes — but production apply still refuses new installed-asset work while `AssetLifecycleReadiness.isReady()` is false (`ASSET_LIFECYCLE_HOLD`, [s4-asset-lifecycle-hold.md](../../runbooks/s4-asset-lifecycle-hold.md)). The [asset write hold](../../runbooks/asset-write-hold.md) keeps the acceptance-time writer disabled, so default deployments still create **no** assets for amend/terminate/supersede to find. Missing or already-closed assets are **skipped** (apply is not blocked), which preserves the held writer but means commercial apply can succeed with **zero** asset mutations when the installed base is empty.

**Design / code ≠ enablement.** Linear **Done** after a code PR is **not** GO to flip readiness, enable `opencrm.assets.legacy-acceptance-write-enabled`, claim reserved CAM Flyway, or treat amend/terminate/supersede as production lifecycle. Without a scored residual brief, a later lane could treat Done/#741 as S4 enablement, invent a parallel apply path, fold S7 lineage into asset mutate, adopt open OQs from #870/#873/#864 by scoring a row here, or mark residual forks closed because unit tests pass. **None of that is authorized here.** Wave-2 docs only; product / schema / flag = **NO-GO**. This brief **leaves Linear Done unchanged** (not restored to In Review); the Done cell records code delivery, not hold release.

## Already settled on the tree (do not re-decide as product work here)

Restated from merged artifacts and owner acts (`read`). This Decision brief does **not** reopen these rows and does **not** authorize enabling or extending them into production reliance.

| ID | Contract | Source |
|---|---|---|
| L1 | ADR-653 Accepted: Asset aggregate + Proposed event schemas; registry registration only via implementing stories after Decision | DRG-653 (`Linear read`) |
| L2 | Reuse DRG-621 apply path; no parallel apply; no new refusal codes beyond existing `APPLY_UNIT_*` / hold audit | Issue AC; #741 |
| L3 | Action map: `UPDATE` → amend + `AssetAmended`; `REMOVE`/`TERMINATE` → `AssetTerminated`; `SWAP`/`UPGRADE` → create-before-terminate + `AssetSuperseded` (successor `AssetCreated` via existing create site) | `AssetChangeApply` (`verified by reading code`) |
| L4 | Missing / closed asset → skip (do not block commercial apply) so legacy acceptance writer can stay off | #741 PR body; `AssetChangeApply` |
| L5 | Events via outbox; one publisher call site per lifecycle contract; audit staged with event id; unit replay does not re-emit | #741; `AssetEventPublisher`; `DocumentApplyAssetReplayTest` |
| L6 | Optional `setAssetChanges` wiring on `DocumentApplyOrchestrator`; readiness gate runs **before** unit apply | orchestrator (`verified by reading code`) |
| L7 | Asset-write hold + S4 asset-lifecycle hold stay in force; acceptance writer default off; readiness returns **false** | runbooks; `AssetActivation`; `AssetLifecycleReadiness` |
| L8 | Holds: no dual-read flip; no Jev enablement; no contract-asset FE from this brief; CAM **V1_16–V1_21** claimed by other drafts — **not available** here | AGENT.md; #805–#812 |
| L9 | #741 added **no** Flyway; CAM migrations present through `V1_15` only on this tree | tree `read` |

## What the tree does today (orientation only)

`verified by reading code` at the base SHA.

1. `DocumentApplyOrchestrator` preflight: if apply is otherwise allowed and `!assetLifecycleReadiness.isReady()` and pending units have asset-touching actions (`ADD`, `UPDATE`, `REMOVE`, `TERMINATE`, `SWAP`, `UPGRADE`, or empty/unrecognized), refuse with audited HTTP 503 `ASSET_LIFECYCLE_HOLD`. Document stays EXECUTED; units stay pending (retryable after a verified readiness release).
2. `AssetLifecycleReadiness.isReady()` hard-codes `false`. There is **no** runtime override flag (`verified by reading code`).
3. When readiness were true and `AssetChangeApply` is injected, after rate periods commit in the unit transaction the orchestrator calls `assetChanges.onUnitApplied(...)` (`MANDATORY` propagation). That path is **not** reachable in production while the hold is on.
4. `AssetChangeApply` maps actions per L3. Absent `sourceAcceptedLineUuid` or missing asset → no-op. Swap/upgrade saves successor (`AssetCreated`) then marks predecessor `SUPERSEDED` and publishes `AssetSuperseded`, then applies history facts in-process.
5. `AssetActivation` exists only when `opencrm.assets.legacy-acceptance-write-enabled=true`. Default deployments create **no** assets; skip-on-missing means amend/terminate/supersede would no-op even if readiness were released without a create path.
6. Upstream amendment validation still restricts client-submitted actions (review on #741: `SWAP`/`UPGRADE`/`TERMINATE` may not reach apply through normal amendment API). Replay tests construct executed documents directly. **Cite as residual fork; not fixed by this docs PR.**
7. CAM migrations present through `V1_15`. **No** CAM `V1_16`–`V1_21` files in this tree; those numbers stay reserved on draft PRs #805–#812.
8. DRG-654 residual forks live on draft [#870](https://github.com/drgaciw/open-crm-cpq/pull/870). DRG-656 residual forks live on draft [#873](https://github.com/drgaciw/open-crm-cpq/pull/873). S7 / DRG-585 residual forks live on draft [#864](https://github.com/drgaciw/open-crm-cpq/pull/864). This brief does not adopt any of those sets.

### Explicit hold section (asset-write + S4 + S7 cite) — NO-GO

| Hold | What stays forbidden | Cite |
|---|---|---|
| **Asset-write hold** | No enabling `opencrm.assets.legacy-acceptance-write-enabled` (or successor) from this story. No treating acceptance-time ACTIVE assets as the target mutate base. No invented replay backfill of held acceptances so amend/terminate/supersede “have something to find.” | [asset-write-hold.md](../../runbooks/asset-write-hold.md); #870 cite-only |
| **S4 asset-lifecycle hold** | No making `AssetLifecycleReadiness.isReady()` return true; no bypass of `ASSET_LIFECYCLE_HOLD` via tooling, feature flag, or “Done means ready.” No S4 enablement under this residual docs lane. | [s4-asset-lifecycle-hold.md](../../runbooks/s4-asset-lifecycle-hold.md) |
| **Linear Done ≠ enablement** | Leave Linear **Done**. Do not treat Done / #741 merge as production lifecycle-GO, readiness release, or flag flip. | Issue state (`Linear read`); this brief |
| **S7 / #864 (cite only)** | No adopting OQ-585-\*; no `contract_header_backfill_*` run; no folding header/history into asset mutate. | [#864](https://github.com/drgaciw/open-crm-cpq/pull/864) |
| **DRG-654 / #870 (cite only)** | No adopting OQ-654-\*; no enabling acceptance writer “to feed 655.” | [#870](https://github.com/drgaciw/open-crm-cpq/pull/870) |
| **DRG-656 / #873 (cite only)** | No adopting OQ-656-\*; no releasing readiness so apply “fills” history under 655. | [#873](https://github.com/drgaciw/open-crm-cpq/pull/873) |
| **DRG-657 / DRG-660** | Remain S7-class **NO-GO** tracks. No installed-base migration or lineage backfill into mutate from this brief. | Linear `read` |
| **CAM V1_16–V1_21** | Reserved. This brief invents no migration and does not edit landed V1_13 / V1_14 / V1_15 in place. | #805–#812 |

### GitNexus (this checkout)

Repository: `open-crm-cpq` (`/agent/repos/open-crm-cpq`). Index rebuilt this pass via `npx gitnexus@latest analyze --index-only` (process walk **truncated** — not a clean graph). Docs-only: no production symbol edited. A later implementation must re-run impact before changing readiness, apply wiring, or event contracts.

| Symbol | Upstream risk | Notes |
|---|---|---|
| `AssetChangeApply` | **LOW** | `verified by GitNexus` summaryOnly; 2 direct / 3 impacted. Docs-only: no symbol edit. Behind S4 readiness; **do not release** from a docs PR. |
| `AssetLifecycleReadiness` | **LOW** | 1 direct / 1 impacted. Hard `false`; **do not flip** from this brief. |
| `DocumentApplyOrchestrator` | **LOW** | 2 direct / 2 impacted. Hold preflight stays; docs PR does not edit. |
| Docs-only path for this PR | N/A product blast | No Java/TS/SQL edited. Pre-commit `detect_changes` must still be reported; `partial` / `truncated` / `UNKNOWN` is **not** a clean check. |

## Relation to #870 / #873 / #864 / #741 (cite only)

[#741](https://github.com/drgaciw/open-crm-cpq/pull/741) is the **merged code delivery** for ChangeLine → asset ops. Cite as tree fact. It is **not** Decision PASS for enablement.

[#870](https://github.com/drgaciw/open-crm-cpq/pull/870) records residual **OQ-654-\*** (acceptance vs activation, flag enablement, missed-asset reconciliation, entitlement/DRG-700, S4 readiness, placement, OHS auth residual, Flyway, Done gate, 657/660). Those rows stay **Proposed** on that draft.

[#873](https://github.com/drgaciw/open-crm-cpq/pull/873) records residual **OQ-656-\*** (history production reliance, consumer/ordering, auth, hold coupling, bootstrap of premature facts, Done gates). Those rows stay **Proposed** on that draft.

[#864](https://github.com/drgaciw/open-crm-cpq/pull/864) records residual **OQ-585-\*** (S7 owner execution GO, mapping/tenant, eligibility, phasing, Flyway NO-GO, asset-write relation, impl gate). Those rows stay **Proposed** on that draft.

**This Decision brief:**

- Treats #741 as the **landed mapper** and #870 / #873 / #864 as sibling residual records.
- Does **not** fill, close, or restate as Adopted any OQ-654-\*, OQ-656-\*, or OQ-585-\* recommendation.
- Keeps DRG-655 residual forks on **readiness / enablement, skip-vs-block, upstream action validation, create-before-supersede pricing residuals, hold coupling, and Done≠GO**, assuming L1–L9 and leaving sibling OQs on their drafts.
- Does **not** claim that citing L3 answers “when may production mutate assets on apply”; that remains OQ-655-\* below.

## Remaining owner forks (Proposed OQ-655-\*)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No Adopted cells in this table.** Do not use these rows to reopen L1–L9, Adopted ADR-653, or open OQs on #870 / #873 / #864.

| ID | Question | Source residual | Recommended (Proposed) | Reject / avoid | Blocks |
|---|---|---|---|---|---|
| **OQ-655-1** | When may production S4 apply mutate installed assets via `AssetChangeApply`? | readiness `false`; s4 hold | **Only** after durable effective-date lifecycle, ordering/replay, and reconciliation are verified — **and** owner writes GO (words, time, env) that changes readiness in code. Until then: held HTTP 503. | Linear Done / #741 merge as GO; runtime override flag | s4-asset-lifecycle-hold |
| **OQ-655-2** | Relationship to asset-write hold / create path | skip-on-missing; held writer | Keep **skip-on-missing**. Do not enable legacy acceptance writer to “feed” amend/terminate/supersede. Target create remains on activation Decision / #870 forks. | Flip `legacy-acceptance-write-enabled` under 655; invent synthetic assets at apply | asset-write-hold; #870 |
| **OQ-655-3** | Skip missing asset vs block apply | #741 design | Keep **skip** so commercial ledger can commit while create is held; document operational inventory for silent no-ops. Owner may later score block-with-`APPLY_UNIT_*` after create GO — **not** this docs PR. | Silently treat skip as “assets updated”; add new refusal code without Decision | Issue AC; #741 |
| **OQ-655-4** | Upstream amendment validation for `SWAP`/`UPGRADE`/`TERMINATE` | #741 review P1 | Require a **scored follow-up** to align `AmendmentWorkflow` / validation rules with apply support before claiming end-to-end client path. Replay-constructed docs ≠ API path. | Claim AC closed because apply mapper exists; widen validation without Decision | #741 review |
| **OQ-655-5** | Swap/upgrade pricing inheritance | #741 review P2 | Keep commercial amounts on successor as a **separate scored story**; do not redefine pricing under this residual brief. | Ship pricing fix as silent enablement of readiness | #741 review |
| **OQ-655-6** | Coupling to DRG-654 / #870 without adopting | #870 OQ-654-* | Cite #870; **do not adopt OQ-654-\***. Mutate GO waits on create Decision + hold release on their gates. | Adopt OQ-654-\* here; enable writer from 655 | #870 |
| **OQ-655-7** | Coupling to DRG-656 / #873 without adopting | #873 OQ-656-* | Cite #873; **do not adopt OQ-656-\***. Do not release readiness so apply “fills” history under 655. In-process `history.apply` stays behind the same hold. | Adopt OQ-656-\* here; treat history projection as proof of mutate GO | #873 |
| **OQ-655-8** | Coupling to S7 / #864 without adopting | #864 OQ-585-* | Cite #864; **do not adopt OQ-585-\***. Header apply does not seed assets or lift holds. | Adopt OQ-585-\* here; run S7 as asset backfill | #864 |
| **OQ-655-9** | Parallel apply / new refusal codes | Issue “must reuse DRG-621” | Keep **single apply path**. No parallel asset apply service; no new refusal codes invented in this residual lane. | Fork apply for assets; invent codes outside `APPLY_UNIT_*` / hold audit | DRG-621; issue AC |
| **OQ-655-10** | Partial-apply / blocked-unit retry | DRG-627; replay tests | Keep replay/idempotency contracts from #741 unit tests as **design evidence**, not production GO. Blocked-unit recovery stays on DRG-627 Decision. | Claim Docker-less unit tests close DRG-627 AC | DRG-627; #741 |
| **OQ-655-11** | Placement | CAM hosting | Stay in CAM until Placement Decision (DRG-561). | New module / pom change now | DRG-561 |
| **OQ-655-12** | Flyway | #741 no migration; reserved slots | **No** number in this brief; never V1_16–V1_21; no in-place V1_13/V1_14/V1_15 edit. | Reserve or ship V1_16–V1_21 here | #805–#812 |
| **OQ-655-13** | Linear Done vs enablement | Done after #741 | **Leave Done.** Record that Done = code delivery, **not** readiness / flag / schema GO. Do not restore In Review from this brief (unlike DRG-600-class wrong Done). | Treat Done as S4 enablement; flip status without owner words | Linear `read` |
| **OQ-655-14** | DRG-657 / DRG-660 | S7-class tracks | Keep S7-class **NO-GO**; separate owner GO. | Start migration under 655 residual close | Linear |
| **OQ-655-IMPL** | Implementation / hold / flag gate | Wave-2 docs lane | Product/schema/flag/readiness **NO-GO** until forks close or are deferred. Docs PR is not authorization. | Merge of this brief authorizes enablement or hold release | AGENT.md; holds |

### OQ-655-1 — Production mutate GO / S4 readiness

| Option | What the owner would be choosing |
|---|---|
| **A. Verified lifecycle + ordering/replay + reconciliation, then code change to readiness (Recommended, Proposed)** | Matches s4-asset-lifecycle-hold.md (no runtime switch). |
| **B. Linear Done / #741 merge implies ready** | Collapses design delivery and operational GO. **NO-GO**. |
| **C. Add runtime override flag in this story** | Explicitly refused by runbook. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-2 — Asset-write hold / create coupling

| Option | What the owner would be choosing |
|---|---|
| **A. Keep skip-on-missing; never enable legacy writer from 655 (Recommended, Proposed)** | Matches asset-write-hold.md and #741 skip design. |
| **B. Enable acceptance writer to feed mutate** | Hold bypass; early ACTIVE assets. **NO-GO**. |
| **C. Fabricate assets inside apply when missing** | Invents create under amend story; wrong ticket. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-3 — Skip vs block when asset missing

| Option | What the owner would be choosing |
|---|---|
| **A. Keep skip while create is held; inventory silent no-ops (Recommended, Proposed)** | Preserves commercial apply under holds. |
| **B. Block unit with existing `APPLY_UNIT_*` after create GO** | Possible later scored change; not authorized here. |
| **C. Invent new refusal code now** | Violates “no new refusal codes” AC. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-4 — Upstream action validation

| Option | What the owner would be choosing |
|---|---|
| **A. Separate scored story to widen validation (Recommended, Proposed)** | Closes #741 P1 without pretending AC is end-to-end today. |
| **B. Claim mapper merge closes client path** | False equivalence with replay fixtures. |
| **C. Quietly widen validation in an enablement PR without Decision** | Product **NO-GO** while holds open. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-5 — Swap/upgrade pricing

| Option | What the owner would be choosing |
|---|---|
| **A. Separate scored commercial story (Recommended, Proposed)** | Addresses #741 P2 without readiness release. |
| **B. Ignore and ship readiness anyway** | Couples pricing defect to lifecycle GO. |
| **C. Redefine pricing under this residual docs brief** | Out of lane. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-6 — DRG-654 / #870 boundary

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #870; do not adopt OQ-654-\*; no writer enable from 655 (Recommended, Proposed)** | Matches Wave-2 cite-only rule. |
| **B. Adopt selected OQ-654-\* cells here** | Risks contradictory Adopted cells. |
| **C. Enable legacy writer from 655** | Asset-write hold bypass. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-7 — DRG-656 / #873 boundary

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #873; do not adopt OQ-656-\*; keep S4 hold (Recommended, Proposed)** | Matches Wave-2 cite-only rule. |
| **B. Adopt selected OQ-656-\* cells here** | Out of lane. |
| **C. Release readiness so apply fills history under 655** | Wrong gate. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-8 — S7 / #864 boundary

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #864; do not adopt OQ-585-\*; no S7/asset fold-in (Recommended, Proposed)** | Matches Wave-2 cite-only rule. |
| **B. Adopt selected OQ-585-\* cells here** | Risks contradictory Adopted cells. |
| **C. Run header apply as asset mutate backfill** | Wrong story; S7-class safeguards ignored. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-9 — Single apply path

| Option | What the owner would be choosing |
|---|---|
| **A. Keep DRG-621 path only (Recommended, Proposed)** | Matches issue hard requirement. |
| **B. Parallel asset apply service** | Forbidden by AC. **NO-GO**. |
| **C. New refusal taxonomy under 655** | Forbidden by AC. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-10 — Partial-apply / blocked-unit

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #741 replay tests as design evidence; GO on DRG-627 Decision (Recommended, Proposed)** | Separates unit proof from recovery enablement. |
| **B. Unit tests close DRG-627** | Over-claims; Docker IT may be absent. |
| **C. Invent recovery tooling under 655 docs** | Wrong ticket. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-11 — Placement

| Option | What the owner would be choosing |
|---|---|
| **A. Stay in CAM until Placement Decision (Recommended, Proposed)** | Matches sibling asset briefs. |
| **B. New module / pom change now** | Out of residual docs lane. |
| **C. Move package as prerequisite to release readiness** | Couples Placement to hold release incorrectly. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-12 — Flyway

| Option | What the owner would be choosing |
|---|---|
| **A. No number in this brief; never V1_16–V1_21; no in-place V1_13/V1_14/V1_15 edit (Recommended, Proposed)** | Hard rule for this Wave-2 docs lane. |
| **B. Reserve or ship V1_16–V1_21 here** | Conflicts with #805–#812. **NO-GO**. |
| **C. Widen assets schema “for mutate” under 655 without a new story** | Product/schema **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-13 — Linear Done

| Option | What the owner would be choosing |
|---|---|
| **A. Leave Done; Done ≠ enablement GO (Recommended, Proposed)** | Prefer leave status unchanged; design/code ≠ hold release. |
| **B. Restore In Review because holds remain** | Allowed only if owner wants status honesty pass; this brief prefers A (not DRG-600-class wrong Done). |
| **C. Done authorizes readiness / flag flip** | Explicitly refused. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-14 — DRG-657 / DRG-660

| Option | What the owner would be choosing |
|---|---|
| **A. Keep S7-class NO-GO; separate owner GO (Recommended, Proposed)** | Aligns Linear issue text on sibling tracks. |
| **B. Start migration under 655 residual close** | Wrong story and wrong gate. |
| **C. Treat dry-run counts in this brief as execution permission** | A decision in principle is not execution permission. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-655-IMPL — Implementation / hold / flag gate

| Option | What the owner would be choosing |
|---|---|
| **A. Product/schema/flag/readiness NO-GO until forks close or are deferred (Recommended, Proposed)** | Docs PR is not authorization. |
| **B. Merge of this brief authorizes enablement or hold release** | Explicitly refused. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed.
2. CPQ Decision must score the OQ-655-\* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those gates pass, **do not** write or accept an implementation brief that authorizes S4 readiness release, enabling the legacy writer, Flyway, flag flips, parallel apply, or treating Linear Done as lifecycle-GO.
4. Even after forks close: L1–L9 hold; asset-write hold and S4 lifecycle hold stay until their own verified releases; **OQ-654-\* stay Proposed** unless scored on #870; **OQ-656-\* stay Proposed** unless scored on #873; **OQ-585-\* stay Proposed** unless scored on #864; CAM **V1_16–V1_21** stay reserved elsewhere; this brief claims **no** migration number.
5. Linear status for DRG-655 stays **Done** (unchanged). This brief does not mark enablement and does not restore In Review.

## Out of scope

- Enabling `opencrm.assets.legacy-acceptance-write-enabled` or any successor flag.
- Changing `AssetLifecycleReadiness`, `AssetChangeApply`, `DocumentApplyOrchestrator` preflight, `AssetActivation`, or S4 apply behavior.
- Creating or editing CAM Flyway (including **V1_16–V1_21** and in-place V1_13/V1_14/V1_15 edits).
- Adopting #870 OQ-654-\*, #873 OQ-656-\*, or #864 OQ-585-\*.
- Running S7 `contract_header_backfill_*`, DRG-657 installed-base migration, or DRG-660 lineage backfill.
- Widening amendment validation / swap pricing under this docs PR.
- Contract-asset FE, dual-read flip, Jev enablement, ramp enable, S4 enablement.
- Restoring Linear to In Review or treating Done/merge as product authorization.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-655, DRG-653, DRG-654, DRG-656, DRG-621, DRG-627, DRG-657, DRG-660, DRG-585 | Linear, this pass |
| Merged delivery | GitHub PR #741; `AssetChangeApply`; `DocumentApplyOrchestrator`; `AssetLifecycleReadiness` |
| Holds | `docs/runbooks/asset-write-hold.md`; `docs/runbooks/s4-asset-lifecycle-hold.md` |
| Create residual cite-only | Draft PR #870 (OQ-654-\*; not adopted) |
| History residual cite-only | Draft PR #873 (OQ-656-\*; not adopted) |
| S7 residual cite-only | Draft PR #864 (OQ-585-\*; not adopted) |
| Review residuals | #741 review: validation P1; swap/upgrade pricing P2 |
| Migrations | CAM `V1_13`–`V1_15` present; reserved V1_16–V1_21 via #805–#812; #741 no Flyway |
| GitNexus | impact on `AssetChangeApply` (LOW), `AssetLifecycleReadiness` (LOW), `DocumentApplyOrchestrator` (LOW); process index truncated |

## Unverified in this pass

- Any runtime readiness flip, outbox relay publish, or production asset row inventory under amend/terminate/supersede.
- Whether a future readiness release reuses `AssetLifecycleReadiness` hard-code or a successor policy type.
- Byte-level event payload parity against every ADR-653 Proposed field in deployed topics.
- Docker `*IntegrationTest` / `AssetHistoryReplayIntegrationTest` execution (not run this pass).
- AutoJev tools (not connected in this environment); no AutoJev verdict is claimed.
- A non-truncated GitNexus process walk; treat process membership as incomplete.
