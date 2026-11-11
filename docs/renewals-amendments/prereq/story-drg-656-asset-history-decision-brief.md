# DRG-656 — Asset history / supersede chain: Decision brief (residual forks)

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring. This file **cites** the merged delivery [#719](https://github.com/drgaciw/open-crm-cpq/pull/719) / [DRG-656-asset-history.md](../../design/DRG-656-asset-history.md), the [asset write hold](../../runbooks/asset-write-hold.md), the [S4 asset-lifecycle hold](../../runbooks/s4-asset-lifecycle-hold.md), [asset read access](../../runbooks/asset-read-access.md), related Wave-2 draft **[#870](https://github.com/drgaciw/open-crm-cpq/pull/870)** (DRG-654 asset-create residual Decision brief) **without adopting open OQ-654-\***, and related Wave-2 draft **[#864](https://github.com/drgaciw/open-crm-cpq/pull/864)** (S7 / DRG-585 residual Decision brief) **without adopting open OQ-585-\*** (or any other open OQ set). It inventories **remaining owner forks as `OQ-656-*`**. **It authorizes no production code path, no schema change, no Flyway migration (including inventing a CAM version that collides with reserved draft slots V1_16–V1_21 on [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), no feature flag, no dual-read flip, no Jev enablement, no ramp enable, no S7 apply/hold bypass, no asset-write hold bypass, no S4 readiness release, no environment dry-run against real tenants, and no deploy.** Product / schema / flag work that treats the history/as-of/supersede OHS as a complete installed-base source of truth, enables `opencrm.assets.legacy-acceptance-write-enabled`, releases `AssetLifecycleReadiness`, invents a production Kafka history consumer under this ticket, or consumes reserved CAM Flyway slots is **NO-GO** until the owner closes the residual OQ-656-\* forks below (or explicitly defers a named fork with words and a time). Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** installed base / asset history. **Story:** [DRG-656](https://linear.app/drgamtd-workspace/issue/DRG-656/asset-c-queryable-asset-history-and-supersede-chain) (Asset (c)). Linear read this pass: **In Review**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

## Alignment (cite; do not reopen closed cells; do not adopt open OQs)

| Artifact | Role |
|---|---|
| [DRG-653](https://linear.app/drgamtd-workspace/issue/DRG-653) ADR Accepted (drg, 2026-10-29 14:11 Dublin) | Aggregate `Asset`; Proposed event schemas; OHS + outbox. Placement vs CAM (DRG-561) remains open. **Cite; do not re-litigate Accepted ADR.** |
| Merged [#719](https://github.com/drgaciw/open-crm-cpq/pull/719) / design note / `abafd089` on `main` | Landed read-side `asset_history` (`V1_14__create_asset_history.sql`), `AssetHistoryProjection`, `AssetQueryController` history / supersede-chain / as-of routes, and replay IT. **Cite as tree fact; not authorization to treat reads as complete installed base.** |
| Owner post-merge reviews on #719 | Production-enablement gaps: coarse auth / no account scope; no production Kafka consumer into the projection; arrival-derived `appliedOrder`; incomplete CREATED-only production feed while lifecycle producers are held; premature #713 `AssetCreated` must not be treated as proof of correct history. **Cite as residual forks below.** |
| [asset-write-hold.md](../../runbooks/asset-write-hold.md) | Acceptance creates subscriptions/rates without assets while hold is on; replay after lift does **not** backfill; no repair of existing rows/events. **Stays in force.** |
| [s4-asset-lifecycle-hold.md](../../runbooks/s4-asset-lifecycle-hold.md) / #749 | New S4 apply that would mutate installed assets → audited HTTP 503 `ASSET_LIFECYCLE_HOLD`. No runtime switch. **Stays in force.** |
| [asset-read-access.md](../../runbooks/asset-read-access.md) | Coarse `InstalledBase.Read` (+ delegated `scp`); not tenant/account isolation. Access guard does **not** make history complete. |
| Wave-2 draft **[#870](https://github.com/drgaciw/open-crm-cpq/pull/870)** (DRG-654) | **Cite only.** Inventories **OQ-654-\***. This brief **does not adopt** any OQ-654-\* cell and does not authorize enabling the acceptance-time writer or inventing activation-time create under 654. |
| Wave-2 draft **[#864](https://github.com/drgaciw/open-crm-cpq/pull/864)** (S7 / DRG-585) | **Cite only.** Inventories **OQ-585-\***. This brief **does not adopt** any OQ-585-\* cell and does not authorize S7 `contract_header_backfill_*`, asset lineage backfill, or hold bypass. |
| Related delivery (cite as tree fact) | [DRG-655](https://linear.app/drgamtd-workspace/issue/DRG-655) / [#741](https://github.com/drgaciw/open-crm-cpq/pull/741) landed `AssetChangeApply` + registered `AssetAmended` / `AssetTerminated` / `AssetSuperseded` contracts, but production apply remains behind S4 readiness `false`. [DRG-659](https://linear.app/drgamtd-workspace/issue/DRG-659) reporting reads the same projection. **Cite; do not treat Linear Done on those tickets as history production-GO.** |
| Related S7-class asset tracks | [DRG-657](https://linear.app/drgamtd-workspace/issue/DRG-657) / [DRG-660](https://linear.app/drgamtd-workspace/issue/DRG-660) remain **S7-class NO-GO**. Header/lineage backfill does **not** seed or repair `asset_history` under this brief. |
| CAM Flyway draft slots | [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) reserve **V1_16–V1_21**. This brief invents **no** migration number and does **not** edit landed `V1_13` / `V1_14` / `V1_15` in place. |

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

[#719](https://github.com/drgaciw/open-crm-cpq/pull/719) merged a read-side history / supersede / as-of projection onto `main` (`abafd089`). Linear moved Done→**In Review** after the stacked merge path and remains **In Review** (`Linear read`). Owner comments on #719 record that the routes must stay gated for production reliance until server-enforced account-scoped authorization, durable consumer/replay semantics, and source-of-truth bootstrap exist. The [asset write hold](../../runbooks/asset-write-hold.md) keeps the acceptance writer disabled, so production CREATED facts are not being written by default. The [S4 asset-lifecycle hold](../../runbooks/s4-asset-lifecycle-hold.md) keeps `AssetLifecycleReadiness.isReady()` false, so amend / terminate / supersede apply that would feed the projection stays refused.

Without a scored residual brief, a later lane could treat merge as GO to expose history as complete installed base, flip `opencrm.assets.legacy-acceptance-write-enabled` to “fill” history, release S4 readiness so `AssetChangeApply` writes under this ticket, invent a Kafka consumer / cutover stream under DRG-656, fold S7 / DRG-657 lineage into `asset_history`, claim reserved CAM Flyway **V1_16–V1_21**, or mark Linear Done because the OHS routes compile. **None of that is authorized here.** Wave-2 docs only; product / schema / flag = **NO-GO**.

## Already settled on the tree (do not re-decide as product work here)

Restated from merged artifacts and owner acts (`read`). This Decision brief does **not** reopen these rows and does **not** authorize enabling or extending them into production reliance.

| ID | Contract | Source |
|---|---|---|
| L1 | ADR-653 Accepted: Asset aggregate + Proposed event schemas; registry registration only via implementing stories after Decision | DRG-653 (`Linear read`) |
| L2 | Read model stays in CAM until Placement Decision (DRG-561); no new module from #719 | design note |
| L3 | OHS: `GET /api/v1/assets/{id}/history`, `…/supersede-chain?direction=…`, `GET /api/v1/assets?accountId=&asOf=` | #719; `AssetQueryController` |
| L4 | `asset_history` append-only; idempotent on `event_id`; CREATED / AMENDED / SUPERSEDED / TERMINATED shapes | `V1_14`; design note |
| L5 | `accountId` on history facts comes from the asset/event field (DRG-654 `customerId` mapping); projection does not call crm-service | design note; #713 |
| L6 | Migration `V1_14__create_asset_history.sql` assigned as next free CAM version after `V1_13` at build time | design note; tree |
| L7 | Asset-write hold + S4 asset-lifecycle hold stay in force; acceptance writer default off; readiness returns false | runbooks; `AssetActivation`; `AssetLifecycleReadiness` (`verified by reading code`) |
| L8 | Coarse `InstalledBase.Read` (+ delegated `scp`) gates history / chain / as-of / reports; not account isolation | [asset-read-access.md](../../runbooks/asset-read-access.md); `InstalledBaseReadAccess` |
| L9 | Holds: no dual-read flip; no Jev enablement; no contract-asset FE from this brief; CAM **V1_16–V1_21** claimed by other drafts — **not available** here | AGENT.md; #805–#812 |

## What the tree does today (orientation only)

`verified by reading code` at the base SHA.

1. `AssetQueryController` serves history, supersede-chain, and as-of installed base over `AssetHistoryProjection`. List-without-`asOf` remains on `AssetController` / `AssetQueryService` from DRG-654.
2. `AssetHistoryProjection.apply(...)` accepts published event DTOs and writes `asset_history`. There is **no** `@KafkaListener` (or equivalent production consumer) that feeds `AssetCreated` / amend / terminate / supersede topics into the projection (`verified by reading code` — only `QuoteAcceptedEventConsumer` is a Kafka listener in CAM main).
3. `AssetActivation` exists only when `opencrm.assets.legacy-acceptance-write-enabled=true`. Default deployments create **no** assets and **no** `AssetCreated` rows. Acceptance dedupe still runs; replay after a later enable **will not** backfill missed assets or history.
4. `AssetChangeApply` (DRG-655) can publish lifecycle events and call `history.apply` inside document apply, but `DocumentApplyOrchestrator` refuses new installed-asset work while `AssetLifecycleReadiness.isReady()` is false (`ASSET_LIFECYCLE_HOLD`). Optional `setAssetChanges` wiring does not lift that hold.
5. Replay IT (`AssetHistoryReplayIntegrationTest`) drops the projection and re-applies facts in-process; it does **not** prove a durable Kafka consumer, cutover bootstrap, or concurrent store contract.
6. `JpaAssetHistoryStore.append` is check-then-insert by `event_id` and assigns `maxAppliedOrder()+1` (arrival-derived). Owner #719 review: concurrent duplicates / same-day fold / effective-end residual remain production-enablement gaps — **not** closed by this brief.
7. CAM migrations present through `V1_15` (including `V1_13` assets, `V1_14` asset history). **No** CAM `V1_16`–`V1_21` files in this tree; those numbers stay reserved on draft PRs #805–#812.
8. DRG-654 residual forks live on draft [#870](https://github.com/drgaciw/open-crm-cpq/pull/870). S7 / DRG-585 residual forks live on draft [#864](https://github.com/drgaciw/open-crm-cpq/pull/864). This brief does not adopt either set.

### Explicit hold section (asset-write + S4 + S7 cite) — NO-GO

| Hold | What stays forbidden | Cite |
|---|---|---|
| **Asset-write hold** | No enabling `opencrm.assets.legacy-acceptance-write-enabled` (or successor) from this story. No treating acceptance-time ACTIVE assets / premature `AssetCreated` as proof of correct history. No invented replay backfill of held acceptances into `asset_history`. | [asset-write-hold.md](../../runbooks/asset-write-hold.md); #870 cite-only; #719 owner comments |
| **S4 asset-lifecycle hold** | No making `AssetLifecycleReadiness.isReady()` return true; no bypass of `ASSET_LIFECYCLE_HOLD` via tooling or consumer so that `AssetChangeApply` “fills” history under this ticket. | [s4-asset-lifecycle-hold.md](../../runbooks/s4-asset-lifecycle-hold.md) |
| **Production history reliance** | No treating history / supersede / as-of OHS as complete installed-base source of truth until consumer, ordering/replay, auth, and bootstrap forks close. | #719 owner comments; [asset-read-access.md](../../runbooks/asset-read-access.md) |
| **S7 / #864 (cite only)** | No adopting OQ-585-\*; no `contract_header_backfill_*` run; no folding header/history into `asset_history`. | [#864](https://github.com/drgaciw/open-crm-cpq/pull/864) |
| **DRG-654 / #870 (cite only)** | No adopting OQ-654-\*; no enabling acceptance writer “to seed history.” | [#870](https://github.com/drgaciw/open-crm-cpq/pull/870) |
| **DRG-657 / DRG-660** | Remain S7-class **NO-GO** tracks. No installed-base migration or `subscriptions.contract_id` lineage backfill into history from this brief. | Linear `read` |
| **CAM V1_16–V1_21** | Reserved. This brief invents no migration and does not edit landed V1_13 / V1_14 / V1_15 in place. | #805–#812 |

### GitNexus (this checkout)

Repository: `open-crm-cpq` (`/agent/repos/open-crm-cpq`). Index rebuilt this pass via `npx gitnexus@latest analyze --index-only` (process walk **truncated** — not a clean graph). Docs-only: no production symbol edited. A later implementation must re-run impact before changing writers, consumers, holds, or projection store semantics.

| Symbol | Upstream risk | Notes |
|---|---|---|
| `AssetHistoryProjection` | **LOW** | `verified by GitNexus` summaryOnly; 2 direct / 3 impacted. Docs-only: no symbol edit. |
| `AssetChangeApply` | **LOW** | 2 direct / 3 impacted. Still behind S4 readiness; **do not release** from a docs PR. |
| `AssetQueryController` | **UNKNOWN** | Zero resolved callers (HTTP entry). `risk: UNKNOWN` is unresolved, not an all-clear — confirm with text search before any delete/rename. |
| Docs-only path for this PR | N/A product blast | No Java/TS/SQL edited. Pre-commit `detect_changes` must still be reported; `partial` / `truncated` / `UNKNOWN` is **not** a clean check. |

## Relation to #870 / #864 (cite only)

[#870](https://github.com/drgaciw/open-crm-cpq/pull/870) records residual **OQ-654-\*** (acceptance vs activation, flag enablement, missed-asset reconciliation, entitlement/DRG-700, S4 readiness, placement, OHS auth residual, Flyway, Done gate, 657/660). Those rows stay **Proposed** on that draft.

[#864](https://github.com/drgaciw/open-crm-cpq/pull/864) records residual **OQ-585-\*** (S7 owner execution GO, customer uuid mapping, tenant semantics, AC-S7-3 / DRG-619, eligibility, DRG-598 phasing, DRG-566 boundary, Flyway NO-GO, asset-write relation, impl gate). Those rows stay **Proposed** on that draft.

**This Decision brief:**

- Treats #870 as the **asset-create** residual record and #864 as the **S7 header-backfill** residual record.
- Does **not** fill, close, or restate as Adopted any OQ-654-\* or OQ-585-\* recommendation.
- Keeps DRG-656 residual forks on **history production reliance, consumer/ordering/replay, auth scope, hold coupling, bootstrap of premature facts, and Done gates**, assuming L1–L9 and leaving create/S7 OQs on their drafts.
- Does **not** claim that citing L3 answers “when may production rely on history”; that remains OQ-656-\* below.

## Remaining owner forks (Proposed OQ-656-\*)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No Adopted cells in this table.** Do not use these rows to reopen L1–L9, Adopted ADR-653, or open OQs on #870 / #864.

| ID | Question | Source residual | Recommended (Proposed) | Reject / avoid | Blocks |
|---|---|---|---|---|---|
| **OQ-656-1** | When may history / supersede / as-of OHS be treated as complete installed-base source of truth? | #719 owner reviews; design note AC | **Only** after durable consumer (or equivalent verified apply projection path), producer causal ordering / replay, account-scoped auth, and bootstrap/reconcile of premature #713 facts — **and** owner writes GO (words, time, env). Until then: read-side demo / test only. | Merge of #719 / this brief as production-GO; treat empty or CREATED-only projection as complete | #719 comments; asset-read-access |
| **OQ-656-2** | How do production facts enter `asset_history`? | No Kafka history consumer; apply path held | Keep **in-process apply from authorized writers** as the interim design-note path; a durable consumer / cutover stream (owner-proposed `AssetLifecycleChanged` or successor) is a **separate scored story**, not authorized here. Do not invent a consumer under DRG-656 docs alone. | Ship unattended Kafka consumer now; claim IT direct-apply equals production consumer | #719 projection follow-up comments |
| **OQ-656-3** | Ordering / `appliedOrder` / same-day fold / effective-end | `JpaAssetHistoryStore`; owner review | Require a **producer causal sequence** (per asset or per subscription) before production reliance. Arrival-derived `maxAppliedOrder()+1` stays interim. Define gap / late / terminal / effective-end rules in the implementing story. | Treat arrival order as Done; ignore same-day AMENDED-after-terminal fold risk | #719 2026-11-01 review |
| **OQ-656-4** | Concurrent append / duplicate event_id content conflict | check-then-insert store | Require **atomic duplicate + content-conflict** handling (unique violation vs silent same-id different payload) before production reliance. | Rely on check-then-insert alone under concurrency | #719 review |
| **OQ-656-5** | Premature / synthetic `AssetCreated` from #713 under hold | Owner cutover comment; asset-write hold | **Inventory + audited correction + snapshot/offset bootstrap** before treating replay of those facts as proof. Do not double-apply old and new facts. No silent retrospective history fabrication from this brief. | Replay QuoteAccepted / AssetCreated as proof of correct history; delete evidence | asset-write-hold; #713; #870 |
| **OQ-656-6** | Coupling to DRG-654 / #870 without adopting | #870 OQ-654-* | Cite #870; **do not adopt OQ-654-\***. Do not flip acceptance writer “to seed history.” History completeness waits on create Decision + hold release on their own gates. | Adopt OQ-654-\* here; enable legacy writer from 656 | #870; asset-write-hold |
| **OQ-656-7** | Coupling to S4 / DRG-655 apply without releasing hold | readiness false; AssetChangeApply | Keep **S4 hold**. Lifecycle facts from amend/terminate/supersede stay refused in production apply until a verified readiness release **separate** from this residual brief. | Flip readiness so 655 “fills” history under 656; add runtime override | s4-asset-lifecycle-hold |
| **OQ-656-8** | Coupling to S7 / #864 without adopting | #864 OQ-585-* | Cite #864; **do not adopt OQ-585-\***. Header apply does not seed `asset_history` or lift asset-write hold. | Adopt OQ-585-\* here; run S7 as history backfill | #864; DRG-585 |
| **OQ-656-9** | Account / tenant authorization residual | Coarse `InstalledBase.Read`; no tenant column on `asset_history` | Keep coarse role as interim capability gate; **account/tenant isolation remains a rollout prerequisite** before broader production access. No FE. | Ship FE; treat coarse role as multi-tenant Done; expose cross-customer history | asset-read-access; AGENT.md FE hold |
| **OQ-656-10** | Relation to DRG-659 reporting / DRG-658 renewal-from-assets | Downstream readers of projection / as-of | Reporting and renewal-from-assets may keep using the read APIs in **test / held** mode, but must **not** claim production reconciliation against a complete installed base until OQ-656-1 closes. | Treat DRG-659 Linear Done as history production-GO | DRG-659; DRG-658 |
| **OQ-656-11** | Placement vs CAM (DRG-561) | ADR open item; design note | Keep package **inside CAM** for now; move only after Placement Decision. No new module / root `pom.xml` from this brief. | Extract module now as prerequisite to “finish” history | DRG-561; L2 |
| **OQ-656-12** | Flyway / further schema under 656 | V1_14 landed; reserved band | **No new migration in this brief.** Never consume **V1_16–V1_21**. Do not edit V1_13/V1_14/V1_15 in place. Later additive schema (tenant column, sequence, inbox) only after forks close and under a new authorized story. | Claim V1_16–V1_21; widen `asset_history` “for cutover” here | #805–#812 |
| **OQ-656-13** | Linear Done gate for #719 era | In Review; stacked-merge history; CPQ Decision practice on 654 | Done only after **CPQ Decision PASS** on residual forks (or explicit deferrals), **green `ci.yml` on `main` containing the merge commit**, and **drg OK**. This docs PR alone does **not** mark Done. | Auto-Done on merge; Done because routes exist; Done when this docs PR merges | Linear status; DoD |
| **OQ-656-14** | DRG-657 / DRG-660 boundary | S7-class NO-GO | Keep both **NO-GO**. History Decision does not authorize installed-base migration or lineage `contract_id` backfill into `asset_history`. | Start 657/660 under 656; treat inventory as migration GO | DRG-657; DRG-660 |
| **OQ-656-IMPL** | Implementation / consumer / flag / schema / hold authorization | — | Separate owner act **after** OQ-656-1..14 (or a deferred subset) close. **No** asset-write hold bypass; **no** S4 readiness release; **no** reserved Flyway slots; **no** flag / dual-read / Jev / ramp; **no** S7 apply; **no** production Kafka consumer from this brief; **no** marking Linear Done from this brief. | Piggy-back V1_16–V1_21; enable legacy writer; release S4; adopt #870/#864 OQs | Owner release; AGENT.md holds |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (residuals only)

### OQ-656-1 — Production reliance on history OHS

| Option | What the owner would be choosing |
|---|---|
| **A. GO only after consumer/ordering/auth/bootstrap + written owner GO (Recommended, Proposed)** | Matches #719 post-merge reviews and asset-read-access caution. |
| **B. Merge / docs PR as GO** | Collapses Decision and operational GO. **NO-GO**. |
| **C. Treat CREATED-only / empty projection as complete** | Contradicts held writers and incomplete feed. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-2 — Fact ingress path

| Option | What the owner would be choosing |
|---|---|
| **A. Interim in-process apply; durable consumer is a separate scored story (Recommended, Proposed)** | Preserves design-note “no producer in 656”; avoids inventing cutover under docs. |
| **B. Implement owner-proposed `AssetLifecycleChanged` stream under this ticket now** | Product work; needs its own Decision + tests. Out of this residual docs lane. |
| **C. Claim replay IT direct-apply equals production consumer** | False equivalence. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-3 — Ordering / fold / effective-end

| Option | What the owner would be choosing |
|---|---|
| **A. Producer causal sequence required before production reliance (Recommended, Proposed)** | Addresses same-day fold and arrival-order races called out on #719. |
| **B. Keep arrival `appliedOrder` as Done** | Leaves known incorrect fold risk. |
| **C. Ignore effective-end in as-of forever** | Leaves expired assets incorrectly in base if terminal facts are late. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-4 — Concurrent append

| Option | What the owner would be choosing |
|---|---|
| **A. Atomic duplicate + content-conflict contract before reliance (Recommended, Proposed)** | Matches owner review of check-then-insert races. |
| **B. Rely on unique constraint alone** | Incomplete content-conflict behavior. |
| **C. Drop uniqueness to allow re-apply** | Breaks idempotent replay. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-5 — Premature #713 facts

| Option | What the owner would be choosing |
|---|---|
| **A. Inventory + audited correction + snapshot/offset bootstrap (Recommended, Proposed)** | Matches owner cutover prerequisite comment. |
| **B. Replay AssetCreated as proof** | Forbidden as proof of correct installed-base history. |
| **C. Delete published evidence / fabricate retrospective rows** | Forbidden by asset-write hold. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-6 — DRG-654 / #870 boundary

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #870; do not adopt OQ-654-\*; no writer enable from 656 (Recommended, Proposed)** | Matches Wave-2 cite-only rule. |
| **B. Adopt selected OQ-654-\* cells here** | Risks contradictory Adopted cells. |
| **C. Enable legacy writer to seed history** | Asset-write hold bypass. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-7 — S4 / DRG-655 coupling

| Option | What the owner would be choosing |
|---|---|
| **A. Keep S4 hold; separate verified readiness release (Recommended, Proposed)** | Matches #749 runbook. |
| **B. Release readiness so AssetChangeApply fills history under 656** | Wrong gate; product **NO-GO**. |
| **C. Add runtime override flag in this story** | Explicitly refused by runbook. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-8 — S7 / #864 boundary

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #864; do not adopt OQ-585-\*; no S7/history fold-in (Recommended, Proposed)** | Matches Wave-2 cite-only rule. |
| **B. Adopt selected OQ-585-\* cells here** | Out of lane. |
| **C. Run header apply as asset-history backfill** | Wrong story; S7-class safeguards ignored. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-9 — Auth residual

| Option | What the owner would be choosing |
|---|---|
| **A. Coarse role interim; isolation before broader production access (Recommended, Proposed)** | Matches asset-read-access.md. |
| **B. Ship FE / treat coarse role as Done** | Violates FE hold and multi-tenant caution. |
| **C. Remove InstalledBase.Read until account-scoped auth lands** | Larger product change; not this residual brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-10 — Downstream 658 / 659

| Option | What the owner would be choosing |
|---|---|
| **A. Allow test/held use; withhold production reconcile claims until OQ-656-1 (Recommended, Proposed)** | Separates reader delivery from history GO. |
| **B. Linear Done on 659 implies history production-GO** | False. |
| **C. Block all 659/658 code paths until history GO** | Over-blocks already-merged readers; needs explicit owner supersede. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-11 — Placement

| Option | What the owner would be choosing |
|---|---|
| **A. Stay in CAM until Placement Decision (Recommended, Proposed)** | Matches design note. |
| **B. New module / pom change now** | Out of residual docs lane. |
| **C. Move package as prerequisite to finish history** | Couples Placement to history GO incorrectly. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-12 — Flyway

| Option | What the owner would be choosing |
|---|---|
| **A. No number in this brief; never V1_16–V1_21; no in-place V1_13/V1_14/V1_15 edit (Recommended, Proposed)** | Hard rule for this Wave-2 docs lane. |
| **B. Reserve or ship V1_16–V1_21 here** | Conflicts with #805–#812. **NO-GO**. |
| **C. Widen `asset_history` “for cutover” under 656 without a new story** | Product/schema **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-13 — Linear Done

| Option | What the owner would be choosing |
|---|---|
| **A. Decision PASS + green main ci.yml (merge commit) + drg OK (Recommended, Proposed)** | Aligns with DRG-654 Done-gate practice; keeps In Review honest. |
| **B. Done because #719 merged / routes exist** | Already reversed once on related asset work; do not repeat. |
| **C. Done when this docs PR merges** | Docs ≠ CPQ Decision PASS. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-14 — DRG-657 / DRG-660

| Option | What the owner would be choosing |
|---|---|
| **A. Keep S7-class NO-GO; separate owner GO (Recommended, Proposed)** | Aligns Linear issue text on sibling tracks. |
| **B. Start migration under 656 residual close** | Wrong story and wrong gate. |
| **C. Treat dry-run counts in this brief as execution permission** | A decision in principle is not execution permission. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-656-IMPL — Implementation / hold / flag gate

| Option | What the owner would be choosing |
|---|---|
| **A. Product/schema/flag/consumer NO-GO until forks close or are deferred (Recommended, Proposed)** | Docs PR is not authorization. |
| **B. Merge of this brief authorizes enablement, consumers, or hold release** | Explicitly refused. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed.
2. CPQ Decision must score the OQ-656-\* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those gates pass, **do not** write or accept an implementation brief that authorizes a production history consumer, hold release, Flyway, flag flips, or treating the OHS as complete installed base.
4. Even after forks close: L1–L9 hold; asset-write hold and S4 lifecycle hold stay until their own verified releases; **OQ-654-\* stay Proposed** unless scored on #870; **OQ-585-\* stay Proposed** unless scored on #864; CAM **V1_16–V1_21** stay reserved elsewhere; this brief claims **no** migration number.
5. Linear status for DRG-656 stays **In Review** until the owner’s Done gate (OQ-656-13) says otherwise. This brief does not mark Done.

## Out of scope

- Enabling `opencrm.assets.legacy-acceptance-write-enabled` or any successor flag.
- Changing `AssetActivation`, `AssetChangeApply`, `AssetHistoryProjection`, `JpaAssetHistoryStore`, `AssetLifecycleReadiness`, or S4 apply preflight.
- Creating or editing CAM Flyway (including **V1_16–V1_21** and in-place V1_13/V1_14/V1_15 edits).
- Implementing a Kafka / outbox history consumer or `AssetLifecycleChanged` cutover stream.
- Adopting #870 OQ-654-\*, #864 OQ-585-\*, or DRG-566 history forks.
- Running S7 `contract_header_backfill_*`, DRG-657 installed-base migration, or DRG-660 lineage backfill.
- Contract-asset FE, dual-read flip, Jev enablement, ramp enable.
- Marking Linear Done or merging this draft as product authorization.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-656, DRG-653, DRG-654, DRG-655, DRG-657, DRG-658, DRG-659, DRG-660, DRG-585 | Linear, this pass |
| Merged delivery | GitHub PR #719; `docs/design/DRG-656-asset-history.md`; `abafd089` on `main` |
| Owner production-enablement comments | #719 review comments 2026-11-01 |
| Holds / access | `docs/runbooks/asset-write-hold.md`; `docs/runbooks/s4-asset-lifecycle-hold.md`; `docs/runbooks/asset-read-access.md` |
| Create residual cite-only | Draft PR #870 (OQ-654-\*; not adopted) |
| S7 residual cite-only | Draft PR #864 (OQ-585-\*; not adopted) |
| Writer / readiness / store | `AssetActivation`; `AssetLifecycleReadiness`; `AssetChangeApply`; `JpaAssetHistoryStore`; `InstalledBaseReadAccess` |
| Migrations | CAM `V1_13`–`V1_15` present; reserved V1_16–V1_21 via #805–#812 |
| GitNexus | impact on `AssetHistoryProjection` (LOW), `AssetChangeApply` (LOW), `AssetQueryController` (UNKNOWN); process index truncated |

## Unverified in this pass

- Any runtime enablement, outbox relay publish, or production `asset_history` row inventory.
- Whether a future durable consumer reuses `AssetHistoryProjection.apply` or a new type.
- Byte-level event payload parity against every ADR-653 Proposed field in deployed topics.
- AutoJev tools (not connected in this environment); no AutoJev verdict is claimed.
- A non-truncated GitNexus process walk; treat process membership as incomplete.
