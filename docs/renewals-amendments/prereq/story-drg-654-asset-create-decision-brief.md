# DRG-654 — Asset create on order activation: Decision brief (residual forks)

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring. This file **cites** the merged delivery [#713](https://github.com/drgaciw/open-crm-cpq/pull/713) / [DRG-654-asset-on-activation.md](../../design/DRG-654-asset-on-activation.md), the [asset write hold](../../runbooks/asset-write-hold.md), the [S4 asset-lifecycle hold](../../runbooks/s4-asset-lifecycle-hold.md), and related Wave-2 draft **[#864](https://github.com/drgaciw/open-crm-cpq/pull/864)** (S7 / DRG-585 residual Decision brief) **without adopting open OQ-585-\*** (or any other open OQ set). It inventories **remaining owner forks as `OQ-654-*`**. **It authorizes no production code path, no schema change, no Flyway migration (including inventing a CAM version that collides with reserved draft slots V1_16–V1_21 on [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), no feature flag, no dual-read flip, no Jev enablement, no ramp enable, no S7 apply/hold bypass, no asset-write hold bypass, no S4 readiness release, no environment dry-run against real tenants, and no deploy.** Product / schema / flag work that enables `opencrm.assets.legacy-acceptance-write-enabled`, treats acceptance-time assets as the target lifecycle, invents activation-time writers under this ticket, or consumes reserved CAM Flyway slots is **NO-GO** until the owner closes the residual OQ-654-\* forks below (or explicitly defers a named fork with words and a time). Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** installed base / asset create. **Story:** [DRG-654](https://linear.app/drgamtd-workspace/issue/DRG-654/asset-a-create-asset-when-an-order-activates-linked-to-contract-line) (Asset (a)). Linear read this pass: **In Review**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

## Alignment (cite; do not reopen closed cells; do not adopt open OQs)

| Artifact | Role |
|---|---|
| [DRG-653](https://linear.app/drgamtd-workspace/issue/DRG-653) ADR Accepted (drg, 2026-10-29 14:11 Dublin) | Aggregate `Asset`; Proposed event schemas; OHS + outbox. Placement vs CAM (DRG-561) remains open. **Cite; do not re-litigate Accepted ADR.** |
| Merged [#713](https://github.com/drgaciw/open-crm-cpq/pull/713) / design note | Landed acceptance-time writer, `V1_13__create_assets.sql`, OHS `GET /api/v1/assets`, outbox contracts. Writer is **held off by default**. **Cite as tree fact; not authorization to enable.** |
| [asset-write-hold.md](../../runbooks/asset-write-hold.md) | Acceptance creates subscriptions/rates without assets while hold is on; replay after lift does **not** backfill; no repair of existing rows/events. **Stays in force.** |
| [s4-asset-lifecycle-hold.md](../../runbooks/s4-asset-lifecycle-hold.md) / #749 | New S4 apply that would mutate installed assets → audited HTTP 503 `ASSET_LIFECYCLE_HOLD`. No runtime switch. **Stays in force.** |
| Owner decision (delegation page, 2026-11-01) | Launch 654 with `entitlementId` empty when nothing to link; follow-up [DRG-700](https://linear.app/drgamtd-workspace/issue/DRG-700). **Cite; AC-1 PARTIAL by that decision.** |
| Wave-2 draft **[#864](https://github.com/drgaciw/open-crm-cpq/pull/864)** (S7 / DRG-585) | **Cite only.** Inventories **OQ-585-\***. This brief **does not adopt** any OQ-585-\* cell and does not authorize S7 `contract_header_backfill_*`, asset lineage backfill, or hold bypass. |
| Related Wave-2 activation draft (DRG-460/469 Decision brief) | **Cite only.** Activation ordering / registry GO remain Proposed there. Do **not** adopt those OQs by scoring a row here. |
| Related S7-class asset tracks | [DRG-657](https://linear.app/drgamtd-workspace/issue/DRG-657) / [DRG-660](https://linear.app/drgamtd-workspace/issue/DRG-660) remain **S7-class NO-GO**. Header/lineage backfill does **not** create or repair assets under this brief. |
| CAM Flyway draft slots | [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) reserve **V1_16–V1_21**. This brief invents **no** migration number and does **not** edit landed `V1_13` / `V1_14` in place. |

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

[#713](https://github.com/drgaciw/open-crm-cpq/pull/713) merged an acceptance-time asset writer and schema, then Linear moved Done→**In Review** pending CPQ Decision PASS, a green `ci.yml` run on `main` containing the merge commit, and drg's OK (`Linear read` comment 2026-11-01). The [asset write hold](../../runbooks/asset-write-hold.md) keeps the writer disabled; the design note states that path is **not** the target lifecycle. Issue AC still says “on activation,” while the tree writes (when enabled) at quote acceptance via `AcceptedQuoteSubscriptionService` → `AssetActivation.onActivated`.

Without a scored residual brief, a later lane could treat merge as GO to flip `opencrm.assets.legacy-acceptance-write-enabled`, invent an activation-time create path under this ticket, fold S7 / DRG-657 lineage into asset create, claim reserved CAM Flyway **V1_16–V1_21**, or lift the S4 asset-lifecycle hold. **None of that is authorized here.** Wave-2 docs only; product / schema / flag = **NO-GO**.

## Already settled on the tree (do not re-decide as product work here)

Restated from merged artifacts and owner acts (`read`). This Decision brief does **not** reopen these rows and does **not** authorize enabling or extending them.

| ID | Contract | Source |
|---|---|---|
| L1 | ADR-653 Accepted: Asset aggregate + Proposed event schemas; registry registration only via implementing stories after Decision | DRG-653 (`Linear read`) |
| L2 | One asset per distinct accepted contract line; idempotent on `(subscription_id, contract_line_id)` when the held writer runs | #713; design note |
| L3 | `accountId` = `QuoteAcceptedEvent.customerId`; no `customer_id` column on `assets` | #713; design note |
| L4 | `entitlementId` empty when lookup returns nothing; production `EmptyEntitlementLookup`; link + backfill → **DRG-700** | Owner decision 2026-11-01; DRG-700 |
| L5 | `AssetCreated` + `AssetAuditRecorded` via outbox when writer runs; OHS `GET /api/v1/assets` (+ `{id}`) with coarse `InstalledBase.Read` | #713 |
| L6 | Migration `V1_13__create_assets.sql` (+ later `V1_14` history) assigned at build time; nothing reserved ahead | design note; tree |
| L7 | Writer gated by `@ConditionalOnProperty(…legacy-acceptance-write-enabled=true)`; **default off** | `AssetActivation` (`verified by reading code`) |
| L8 | Asset-write hold + S4 asset-lifecycle hold stay in force; no runtime readiness override | runbooks; `AssetLifecycleReadiness.isReady()` false |
| L9 | Holds: no dual-read flip; no Jev enablement; no contract-asset FE from this brief; CAM **V1_16–V1_21** claimed by other drafts — **not available** here | AGENT.md; #805–#812 |

## What the tree does today (orientation only)

`verified by reading code` at the base SHA.

1. `QuoteAcceptedEventConsumer` → `AcceptedQuoteSubscriptionService.apply` / `mutate` create PENDING subscriptions and rate periods. `recordAssets` calls `AssetActivation.onActivated` only when Spring injects the bean.
2. `AssetActivation` exists only when `opencrm.assets.legacy-acceptance-write-enabled=true`. Default deployments create **no** assets and **no** `AssetCreated` / `AssetAuditRecorded` rows. Acceptance dedupe still runs; replay after a later enable **will not** backfill missed assets.
3. Interim line identity is `subscription_rate_periods.accepted_line_uuid` (also used as `contractLineId` on assets when the held writer runs). Canonical `contract_lines` remains a separate story (DRG-665).
4. S4 apply preflight refuses new installed-asset mutations with `ASSET_LIFECYCLE_HOLD` while `AssetLifecycleReadiness.isReady()` is false. Identical completed replay still works.
5. CAM migrations present through `V1_15` (including `V1_13` assets, `V1_14` asset history). **No** CAM `V1_16`–`V1_21` files in this tree; those numbers stay reserved on draft PRs #805–#812.
6. S7 / DRG-585 residual forks live on draft [#864](https://github.com/drgaciw/open-crm-cpq/pull/864). DRG-657 / DRG-660 stay S7-class **NO-GO**. This brief does not run header apply, lineage backfill, or asset migration.

### Explicit hold section (asset-write + S4 + S7 cite) — NO-GO

| Hold | What stays forbidden | Cite |
|---|---|---|
| **Asset-write hold** | No enabling `opencrm.assets.legacy-acceptance-write-enabled` (or successor) from this story. No treating acceptance-time ACTIVE assets as the target lifecycle. No invented replay backfill of held acceptances. | [asset-write-hold.md](../../runbooks/asset-write-hold.md); AGENT.md |
| **S4 asset-lifecycle hold** | No making `AssetLifecycleReadiness.isReady()` return true; no bypass of `ASSET_LIFECYCLE_HOLD` via tooling or consumer. | [s4-asset-lifecycle-hold.md](../../runbooks/s4-asset-lifecycle-hold.md) |
| **S7 / #864 (cite only)** | No adopting OQ-585-\*; no `contract_header_backfill_*` run; no folding header/history into asset create. | [#864](https://github.com/drgaciw/open-crm-cpq/pull/864); OQ7 companion |
| **DRG-657 / DRG-660** | Remain S7-class **NO-GO** tracks. No installed-base migration or `subscriptions.contract_id` lineage backfill from this brief. | Linear `read` |
| **CAM V1_16–V1_21** | Reserved. This brief invents no migration and does not edit landed V1_13 / V1_14 in place. | #805–#812 |

### GitNexus (this checkout)

Repository: `open-crm-cpq` (`/agent/repos/open-crm-cpq`). Index rebuilt this pass via `npx gitnexus@latest analyze --index-only` (process walk **truncated** — not a clean graph). Docs-only: no production symbol edited. A later implementation must re-run impact before changing writers or holds.

| Symbol | Upstream risk | Notes |
|---|---|---|
| `AssetActivation` | **LOW** | `verified by GitNexus` summaryOnly; 2 direct / 3 impacted. Conditional bean; still **do not flip** the property from a docs PR. |
| `AcceptedQuoteSubscriptionService` | **LOW** | 1 direct caller (`QuoteAcceptedEventConsumer`). Docs-only: no symbol edit. |
| Concept query for acceptance writer | definitions only | Process list empty / truncated index — **not** proof of unused. Confirm with text search before any delete/enable. |
| Docs-only path for this PR | N/A product blast | No Java/TS/SQL edited. Pre-commit `detect_changes` must still be reported; `partial` / `truncated` / `UNKNOWN` is **not** a clean check. |

## Relation to S7 / #864 and activation drafts (cite only)

[#864](https://github.com/drgaciw/open-crm-cpq/pull/864) records residual **OQ-585-\*** (owner execution GO, customer uuid mapping, tenant semantics, AC-S7-3 / DRG-619, eligibility, DRG-598 phasing, DRG-566/#859 boundary, Flyway NO-GO, asset-write relation, impl gate). Those rows stay **Proposed** on that draft.

**This Decision brief:**

- Treats #864 as the **S7 header-backfill** residual record.
- Does **not** fill, close, or restate as Adopted any OQ-585-\* recommendation.
- Keeps DRG-654 residual forks on **asset create timing, hold release, reconciliation, entitlement link boundary, and Done gates**, assuming L1–L9 and leaving OQ-585-\* for their own owner scoring.
- Cites DRG-460/469 / DRG-558 activation and entitlement Decision work **without adopting** their open OQs.
- Does **not** claim that citing L7 answers “when may production write assets”; that remains OQ-654-\* below.

## Remaining owner forks (Proposed OQ-654-\*)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No Adopted cells in this table.** Do not use these rows to reopen L1–L9, Adopted ADR-653, or open OQs on #864 / activation drafts.

| ID | Question | Source residual | Recommended (Proposed) | Reject / avoid | Blocks |
|---|---|---|---|---|---|
| **OQ-654-1** | Is acceptance-time create the target “on activation” AC, or a held interim? | Issue AC vs design note / hold | Keep landed writer as **held interim only**. Target create stays on durable effective-activation / ordered lifecycle (DRG-460/469 path + later readiness). Acceptance ≠ activation. | Flip flag and call acceptance “activation Done”; delete held writer without a scored replacement | asset-write-hold; design note |
| **OQ-654-2** | When may `legacy-acceptance-write-enabled` turn on in a deployed env? | Hold runbook | **Never** as a substitute for the target lifecycle. Enable only after durable effective-date lifecycle, ordered replay, and verified reconciliation are delivered **and** owner writes GO (words, time, env). | Enable to “unblock” S4 or UAT; treat PR merge as GO | asset-write-hold; AGENT.md |
| **OQ-654-3** | How do acceptances that ran under the hold get assets later? | Hold: no replay backfill | **Operator inventory + verified reconciliation** under an approved process (likely DRG-657 class, still S7-class NO-GO until its own gates). Do **not** invent silent retrospective `AssetCreated` from this brief. | Replay QuoteAccepted to backfill; fabricate identities; delete history | asset-write-hold; DRG-657 |
| **OQ-654-4** | Coupling to activation Decision briefs without adopting | DRG-460/469 draft | Cite activation OQs; **wait** to implement target create until those blocking forks are scored **or** owner explicitly defers named forks with words and a time. Closing this brief alone does **not** close activation OQs. | Silently treat activation recommendations as Adopted; ship ActivateSubscription asset create under 654 without Decision | DRG-460/469; OQ-654-1 |
| **OQ-654-5** | Coupling to S7 / #864 without adopting | #864 OQ-585-* | Cite #864; **do not adopt OQ-585-\***. Header apply does not create assets or lift asset-write hold (aligns with OQ-585-9 intent without adopting it). | Adopt OQ-585-\* here; run S7 apply as asset backfill | #864; DRG-585 |
| **OQ-654-6** | `entitlementId` / DRG-700 boundary | Owner decision; EmptyEntitlementLookup | Keep **empty until entitlements exist**; link + backfill stay on **DRG-700** after DRG-558/460. No entitlement ledger invented under 654. | Require non-null entitlementId before any create; backfill without DRG-700 GO | DRG-700; L4 |
| **OQ-654-7** | S4 lifecycle hold relation | #749; readiness false | Keep **S4 hold**. Releasing readiness needs verified lifecycle implementation **separate** from flipping the acceptance writer. | Flip acceptance writer and treat S4 as ready; add runtime override in this story | s4-asset-lifecycle-hold |
| **OQ-654-8** | Placement vs CAM (DRG-561) | ADR open item; design note | Keep package **inside CAM** for now; move only after Placement Decision. No new module / root `pom.xml` from this brief. | Extract module now; rewrite package as “authorization” to enable writer | DRG-561; L1 |
| **OQ-654-9** | OHS authorization residual | Coarse `InstalledBase.Read` | Keep coarse role as interim; **account/tenant isolation and role provisioning remain rollout prerequisites** before production reliance. No FE. | Ship FE; treat coarse role as multi-tenant Done | design note; AGENT.md FE hold |
| **OQ-654-10** | Flyway / further schema under 654 | V1_13 landed; reserved band | **No new migration in this brief.** Never consume **V1_16–V1_21**. Do not edit V1_13/V1_14 in place. Later additive schema only after forks close and under a new authorized story. | Claim V1_16–V1_21; widen assets table “for activation” here | #805–#812 |
| **OQ-654-11** | Linear Done gate for #713 era | Comment 2026-11-01 | Done only after **CPQ Decision PASS** on residual forks (or explicit deferrals), **green `ci.yml` on `main` containing the merge commit**, and **drg OK**. This docs PR alone does **not** mark Done. | Auto-Done on merge; Done because writer code exists behind a flag | Linear comment; DoD |
| **OQ-654-12** | DRG-657 / DRG-660 boundary | S7-class NO-GO | Keep both **NO-GO**. Asset create Decision does not authorize installed-base migration or lineage `contract_id` backfill. | Start 657/660 under 654; treat inventory as migration GO | DRG-657; DRG-660 |
| **OQ-654-IMPL** | Implementation / flag / schema / hold authorization | — | Separate owner act **after** OQ-654-1..12 (or a deferred subset) close. **No** asset-write hold bypass; **no** S4 readiness release; **no** reserved Flyway slots; **no** flag / dual-read / Jev / ramp; **no** S7 apply; **no** marking Linear Done from this brief. | Piggy-back V1_16–V1_21; enable legacy writer now; adopt #864 OQs; lift S4 hold | Owner release; AGENT.md holds |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (residuals only)

### OQ-654-1 — Acceptance vs activation

| Option | What the owner would be choosing |
|---|---|
| **A. Held interim only; target create on effective activation (Recommended, Proposed)** | Preserves design-note / hold language; keeps DRG-460/469 as the activation path. |
| **B. Redefine AC so acceptance = activation** | Contradicts hold and “not the target lifecycle.” |
| **C. Delete held writer immediately without replacement** | Leaves OHS/schema without a create path; needs a scored successor story. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-654-2 — Flag enablement

| Option | What the owner would be choosing |
|---|---|
| **A. Never as lifecycle substitute; GO only after lifecycle + reconciliation (Recommended, Proposed)** | Matches asset-write-hold.md. |
| **B. Enable for UAT / S4 unblock** | Violates hold; can write incorrect ACTIVE assets early. |
| **C. Merge / docs PR as GO** | Collapses Decision and operational GO. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-654-3 — Missed assets under the hold

| Option | What the owner would be choosing |
|---|---|
| **A. Inventory + verified reconciliation under approved process (Recommended, Proposed)** | Hold already forbids replay backfill. |
| **B. Replay QuoteAccepted after enable** | Deduped; will not create assets. |
| **C. Publish retrospective AssetCreated without inventory** | Forbidden by hold. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-654-4 — Activation Decision coupling

| Option | What the owner would be choosing |
|---|---|
| **A. Cite; wait for scored/deferred activation forks (Recommended, Proposed)** | Avoids dual Adopted cells. |
| **B. Adopt activation OQs here** | Out of lane. |
| **C. Implement ActivateSubscription asset create under DRG-654 now** | Product **NO-GO** while holds and activation forks open. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-654-5 — S7 / #864 boundary

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #864; do not adopt OQ-585-\*; no S7/asset fold-in (Recommended, Proposed)** | Matches Wave-2 cite-only rule. |
| **B. Adopt selected OQ-585-\* cells here** | Risks contradictory Adopted cells. |
| **C. Run header apply as asset backfill** | Wrong story; S7-class safeguards ignored. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-654-6 — Entitlement link

| Option | What the owner would be choosing |
|---|---|
| **A. Empty until DRG-700 after entitlements exist (Recommended, Proposed)** | Restates owner decision; keeps AC-1 PARTIAL honest. |
| **B. Block all asset create until entitlement ledger lands** | Reopens the Nov 1 launch decision. |
| **C. Backfill entitlementId under 654 without DRG-700** | Wrong ticket; bypasses backfill GO. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-654-7 — S4 readiness

| Option | What the owner would be choosing |
|---|---|
| **A. Keep hold; separate verified lifecycle release (Recommended, Proposed)** | Matches #749 runbook. |
| **B. Acceptance-writer enable implies S4 ready** | False equivalence. |
| **C. Add runtime override flag in this story** | Explicitly refused by runbook. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-654-8 — Placement

| Option | What the owner would be choosing |
|---|---|
| **A. Stay in CAM until Placement Decision (Recommended, Proposed)** | Matches design note. |
| **B. New module / pom change now** | Out of residual docs lane. |
| **C. Move package as prerequisite to enable writer** | Couples Placement to hold release incorrectly. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-654-9 — OHS auth residual

| Option | What the owner would be choosing |
|---|---|
| **A. Coarse role interim; isolation/provisioning before production reliance (Recommended, Proposed)** | Matches design-note caution. |
| **B. Ship FE / treat coarse role as Done** | Violates contract-asset FE hold and multi-tenant caution. |
| **C. Remove OHS until account-scoped auth lands** | Larger product change; not this residual brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-654-10 — Flyway

| Option | What the owner would be choosing |
|---|---|
| **A. No number in this brief; never V1_16–V1_21; no in-place V1_13/V1_14 edit (Recommended, Proposed)** | Hard rule for this Wave-2 docs lane. |
| **B. Reserve or ship V1_16–V1_21 here** | Conflicts with #805–#812. **NO-GO**. |
| **C. Widen assets schema “for activation” under 654 without a new story** | Product/schema **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-654-11 — Linear Done

| Option | What the owner would be choosing |
|---|---|
| **A. Decision PASS + green main ci.yml (merge commit) + drg OK (Recommended, Proposed)** | Matches 2026-11-01 status comment. |
| **B. Done because #713 merged** | Already reversed once; do not repeat. |
| **C. Done when this docs PR merges** | Docs ≠ CPQ Decision PASS. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-654-12 — DRG-657 / DRG-660

| Option | What the owner would be choosing |
|---|---|
| **A. Keep S7-class NO-GO; separate owner GO (Recommended, Proposed)** | Aligns Linear issue text. |
| **B. Start migration under 654 residual close** | Wrong story and wrong gate. |
| **C. Treat dry-run counts in this brief as execution permission** | A decision in principle is not execution permission. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-654-IMPL — Implementation / hold / flag gate

| Option | What the owner would be choosing |
|---|---|
| **A. Product/schema/flag NO-GO until forks close or are deferred (Recommended, Proposed)** | Docs PR is not authorization. |
| **B. Merge of this brief authorizes enablement or writers** | Explicitly refused. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed.
2. CPQ Decision must score the OQ-654-\* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those gates pass, **do not** write or accept an implementation brief that authorizes enabling the legacy writer, a new activation-time create path under DRG-654, Flyway, hold release, or flag flips.
4. Even after forks close: L1–L9 hold; asset-write hold and S4 lifecycle hold stay until their own verified releases; **OQ-585-\* stay Proposed** unless scored on #864; activation OQs stay on their drafts; CAM **V1_16–V1_21** stay reserved elsewhere; this brief claims **no** migration number.
5. Linear status for DRG-654 stays **In Review** until the owner’s Done gate (OQ-654-11) says otherwise. This brief does not mark Done.

## Out of scope

- Enabling `opencrm.assets.legacy-acceptance-write-enabled` or any successor flag.
- Changing `AssetActivation`, `AcceptedQuoteSubscriptionService`, `AssetLifecycleReadiness`, or S4 apply preflight.
- Creating or editing CAM Flyway (including **V1_16–V1_21** and in-place V1_13/V1_14 edits).
- Adopting #864 OQ-585-\*, activation Decision OQs, or DRG-566/#859 history forks.
- Running S7 `contract_header_backfill_*`, DRG-657 installed-base migration, or DRG-660 lineage backfill.
- Contract-asset FE, dual-read flip, Jev enablement, ramp enable.
- Marking Linear Done or merging this draft as product authorization.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-654, DRG-653, DRG-700, DRG-657, DRG-660 | Linear, this pass |
| Merged delivery | GitHub PR #713; `docs/design/DRG-654-asset-on-activation.md` |
| Holds | `docs/runbooks/asset-write-hold.md`; `docs/runbooks/s4-asset-lifecycle-hold.md` |
| S7 residual cite-only | Draft PR #864 (OQ-585-\*; not adopted) |
| Writer gate | `AssetActivation` `@ConditionalOnProperty`; `AssetLifecycleReadiness` |
| Migrations | CAM `V1_13`–`V1_15` present; reserved V1_16–V1_21 via #805–#812 |
| GitNexus | impact on `AssetActivation` (LOW), `AcceptedQuoteSubscriptionService` (LOW); process index truncated |

## Unverified in this pass

- Any runtime enablement, outbox relay publish, or production asset row inventory.
- Whether a future effective-activation create path reuses `AssetActivation` or a new type.
- Byte-level event payload parity against every ADR-653 Proposed field in deployed topics.
- AutoJev tools (not connected in this environment); no AutoJev verdict is claimed.
- A non-truncated GitNexus process walk; treat process membership as incomplete.
