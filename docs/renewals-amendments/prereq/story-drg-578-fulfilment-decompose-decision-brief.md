# DRG-578 — Action-aware fulfilment decomposition: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** accept product code, scaffold `order-service`, amend ADR-024/026/027 into runtime authority, authorize an implementation brief, flip a flag, register an event, or deploy. Every recommendation below is `Status: Proposed`. No row is Adopted by this brief. Adoption for the **product path** requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** Cluster F — fulfilment operation selection (RA-44). **Story:** [DRG-578](https://linear.app/drgamtd-workspace/issue/DRG-578/action-aware-fulfilment-decomposition-for-amendments-and-renewals) (Action-aware fulfilment decomposition for amendments and renewals — provision, modify, extend, revoke, regenerate). Linear read this pass: **In Review**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Aligned briefs / frames (standing inputs, not reopened wholesale):**

| Record | Role for this Decision brief |
|---|---|
| [cluster-f-fulfilment-decision-frame.md](cluster-f-fulfilment-decision-frame.md) | Historical C1–C4 / F1–F9 fork catalogue for DRG-578 (and S1–S7 for DRG-558). Frame text retains pre-decision wording; see its Current authority header. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) §Fulfilment | Under owner delegation, assigned dispositions to Cluster F C\* / F\* (and related fulfilment contracts). **Does not** authorize Java/TS, migrations, `EventContractRegistry` rows, or flags. |
| Sibling Decision brief DRG-558 (draft [#817](https://github.com/drgaciw/open-crm-cpq/pull/817)) | Commitment projections / fulfilment-required signal. Operation selection stays **here**. |
| Sibling Decision brief DRG-460/469 (draft [#809](https://github.com/drgaciw/open-crm-cpq/pull/809)) | Activation Decision brief. `PROVISION` evidence and entitlement ledger remain product-gated there; this brief does not reopen activation AC. |
| [adr-reconcile-adr-024-027.md](adr-reconcile-adr-024-027.md) | Accepted reconcile. D-R5 is the ChangeLine → fulfilment-operation projection used as input text for the forks below. |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, registry entry, gateway route, or deploy.** An implementation brief for DRG-578 (or any product story that emits typed `FulfillmentRequest`s / wires `DecompositionRule` on ChangeLine action) is **blocked** until CPQ Decision scores and the owner closes the forks below (or explicitly defers a named fork with words and a time). Holds in [AGENT.md](../../../AGENT.md) and [ARCHITECTURE.md §17](../../ARCHITECTURE.md) stay in force: no dual-read flip, no Jev enablement, no contract-asset FE work from this brief.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear issue named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `graph` | GitNexus CLI impact/query on **this** checkout (`npx gitnexus … --repo .` / `node .gitnexus/run.cjs …`) after `npx gitnexus analyze --index-only`. |
| `Status: Proposed` | Recommendation or default only. Not adopted by **this** Decision brief. Slot stays `Owner decision: ___ (drg, time)`. |
| Standing input | Earlier Accepted / delegated documentation. Used here; not re-litigated as code authority. |

## Context (why DRG-578 needs its own Decision brief)

DRG-578 asks which **fulfilment operation** a ChangeLine selects after commercial apply: Add → provision; quantity Update → modify; Renew → extend (SaaS date) or regenerate (term licence); Upgrade/Swap → provision then revoke inside a dual-entitlement window; Downgrade/Remove/Terminate → revoke or cap; Suspend/Resume → disable/enable. Cluster F and the 2026-10-17 delegated record already wrote **policy dispositions**. Product readiness still fails: there is no fulfilment package under `backend/services/`, no `DecompositionRule`, no typed `FulfillmentRequest` row, and commercial apply does not select an operation from `action_code`.

Scoring DRG-578 separately from DRG-558 keeps commitment-state vocabulary out of this brief. Scoring it separately from DRG-460/469 keeps activation AC (quantity, term, minimal status, `ListEntitlements`) from silently absorbing operation decomposition.

### Related tickets (Linear read 2026-11-06)

| Ticket | Linear status | Role for this Decision brief |
|---|---|---|
| [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558) | In Review | Commitment projections / fulfilment-required signal. Sibling Cluster F story; scored on draft #817. |
| [DRG-460](https://linear.app/drgamtd-workspace/issue/DRG-460) / [DRG-469](https://linear.app/drgamtd-workspace/issue/DRG-469) | Backlog | Activation Decision brief #809. `PROVISION` product path and entitlement ledger stay there. |
| [DRG-465](https://linear.app/drgamtd-workspace/issue/DRG-465) | Backlog | Product→fulfilment-item decomposition. Not the ChangeLine action key. |
| [DRG-466](https://linear.app/drgamtd-workspace/issue/DRG-466) | Backlog | Fulfilment orchestrator / compensations dependency. No event/transport decided here. |
| [DRG-574](https://linear.app/drgamtd-workspace/issue/DRG-574) | Backlog | Conversion surrender caller of `REVOKE` after dual-use; not a second revocation mechanism unless owner picks that. |
| [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576) | related | Catalog relationship qualifier for SKU-replacement downgrade / upgrade pairs. |
| [DRG-577](https://linear.app/drgamtd-workspace/issue/DRG-577) | Done | Linear `blockedBy` for DRG-578; reconcile is input text only. |
| [DRG-655](https://linear.app/drgamtd-workspace/issue/DRG-655) | Done | Asset ChangeLine actions; related, not this Decision. |
| Flag `opencrm.erp-sync.enabled` | Default on (`read` CAM `application.yml`) | Not flipped here. Today's PENDING→ACTIVE path rides simulated ERP sync; that is **not** action-aware fulfilment. |
| Flag `opencrm.assets.legacy-acceptance-write-enabled` | Default off | Stays off. No part of this brief authorizes turning it on. |

**Verdict on product readiness:** No fulfilment service, no `DecompositionRule`, no `PROVISION`/`MODIFY`/`REVOKE`/`REGENERATE` request types in product Java, and no apply-path emission of fulfilment work keyed on `ActionCode`. CAM ERP sync re-queues on amendment/renewal mutate and does not select an operation. **Implementation briefs remain NO-GO** until the forks below are closed and residual blockers at the end are disclosed.

## Current code facts (`read` + `graph` + text confirm)

Facts verified against `main` SHA above. Paths are relative to the repo root.

| Fact | Evidence |
|---|---|
| Services on `main` include CAM, quote, pricing, product, gateway, CRM, … — **no** `order-service` and **no** fulfilment package directory | `backend/services/` listing (`read`) |
| QuoteAccepted apply accepts deal types `NEW`, `AMENDMENT`, `EXPANSION`, `RENEWAL`; `NEW` queues `erpSyncStatus = QUEUED`; non-`NEW` calls `mutate` | `AcceptedQuoteSubscriptionService.apply` / `mutate` (`read`); `graph` upstream impact **risk LOW**, 1 direct caller |
| `mutate` re-queues ERP sync (`ErpSyncStatus.QUEUED`) on the same subscription; renewals also update `endDate` / `renewalDate`; **no** ChangeLine → fulfilment operation map | Same file, mutate path (`read`) |
| Simulated ERP: `ErpAdapter.syncToErp` sleeps and returns `"ERP-" + subscriptionNumber` | `ErpAdapter.java` (`read`) |
| `ErpSyncJob` claims `QUEUED→IN_PROGRESS`, calls adapter, on success sets `SYNCED` and `activate()` only when status is still `PENDING` | `ErpSyncJob.java` (`read`); `graph` context exact |
| ERP sync gated by `opencrm.erp-sync.enabled` match-if-missing `true` | `application.yml`; `@ConditionalOnProperty` on `ErpSyncJob` (`read`) |
| Amendment apply orchestrates commercial units (block / apply / ledger / digests); **no** fulfilment request emission in apply | `DocumentApplyOrchestrator.applyUnit` (`read`); `graph` upstream **risk LOW** (controller only) |
| ChangeLine `action_code` enum on `main`: `ADD`, `UPDATE`, `REMOVE`, `NO_CHANGE`, `RENEW`, `UPGRADE`, `DOWNGRADE`, `SWAP`, `CONVERT_METRIC`, `SUSPEND`, `RESUME`, `TERMINATE` | `ActionCode.java`; `V1_7__contract_documents.sql` `chk_amendment_line_action` (`read`) |
| Reconcile D-R5 table projects those codes to fulfilment ops (`PROVISION`, `MODIFY`, `REVOKE`, `REGENERATE`, `SUSPEND`, `RESUME`); effect row also lists reserved `EXTEND` | [adr-reconcile-adr-024-027.md](adr-reconcile-adr-024-027.md) D-R5 (`read`) |
| No product Java matches for `DecompositionRule`, `FulfillmentRequest`, or fulfilment op type names as runtime types | Repo text search this pass (excluding docs) |
| ADR-024 and ADR-027 files are **Accepted** as documentation (2026-10-17); they still **do not** authorize scaffolding `order-service`, topics, or registry entries by themselves | ADR headers (`read`); same posture as #809 / #817 |
| Query for fulfilment/apply flows returns amendment apply + lifecycle catalog symbols, not a fulfilment saga | `npx gitnexus query "fulfilment apply provision revoke…" --repo .` — definitions only; empty `processes` |

### Gap vs issue / D-R5 operation vocabulary

| Operation (issue / reconcile / Cluster F) | On `main` product code? |
|---|---|
| `PROVISION` | No typed request; ERP sync is not provision |
| `MODIFY` | No |
| `EXTEND` | No emitter; SaaS renewal mutates subscription dates only |
| `REVOKE` | No |
| `REGENERATE` | No |
| `SUSPEND` / `RESUME` | Action codes stored; no fulfilment op |
| Action-keyed `DecompositionRule` | No |
| Dual-entitlement create-before-terminate unit pair | Commercial units exist; no fulfilment-pair dispatch |

## Market research refs (fork **options**, not decided policy)

Cited on [DRG-578](https://linear.app/drgamtd-workspace/issue/DRG-578) and in research / spike docs. **None of the following is adopted as the Open CRM/CPQ operation set or API by this brief.**

| Source | What the research shows (illustrative) | How this brief treats it |
|---|---|---|
| Revenera FlexNet Operations | Separate renew / upgrade / upsell fulfilment ops after commercial change | Option material for typed ops (OQ1–OQ4). Not copied as CAM method names. |
| Thales Sentinel EMS | Regenerate License after recharge / term change | Option for `REGENERATE` vs reserved `EXTEND` (OQ1). |
| Amendment document design spike §4 | Create-before-terminate; ENTITLEMENT-pending units | Option for Upgrade/Swap / revoke timing (OQ3, OQ9). |
| Cluster F / delegated §Fulfilment | Durable per-unit request after commercial apply; async adapter; REVOKE/REGENERATE non-compensatable | Standing policy dispositions. **Still Proposed for product Decision scoring in this file.** |

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Recommendations are Proposed.
2. CPQ Decision must score the forks. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work for DRG-578.
4. Even after forks close: no dual-read flip; no Jev flag enablement; no `legacy-acceptance-write-enabled` flip from this brief; no merge authority in this brief; DRG-465 / DRG-466 / DRG-460 / ADR-024 runtime cutover remain residual product-path blockers to disclose on any later implementation brief.
5. First registration of any fulfilment event or command in `EventContractRegistry` remains owner-gated (same posture as #809 OQ1; HIGH blast radius).

---

## Owner-decision table (summary)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells. Recommended defaults align with Cluster F baseline A + delegated §Fulfilment where that record already chose a disposition — alignment is **not** adoption by this brief.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ1 | `EXTEND` vs SaaS renew / term `REGENERATE` (F1) | Reserve `EXTEND` with no v1 ChangeLine emitter; SaaS renew = CAM date mutate; term-licence `Renew` → `REGENERATE` | Drop `EXTEND`; emit `EXTEND` for SaaS and term | Yes — Renew operation |
| OQ2 | Quantity up and down (F2) | Both directions emit `MODIFY` (cap/raise at effective date); quantity-down does **not** emit `REVOKE` | Quantity-down emits `REVOKE` for the delta | Yes — Update |
| OQ3 | Downgrade disjunction (F3) | Quantity-only → F2 `MODIFY`; SKU-replacement waits on DRG-576 relationship, then Upgrade/Swap shape (`PROVISION` then `REVOKE`) | Whole-entitlement `REVOKE`; no Downgrade emitter at all | Yes — Downgrade |
| OQ4 | Decomposition key (F4) | `(licenseModel, ActionCode)`; product / relationship type optional until DRG-576; DealType is **not** the selector | Require relationship type now; select by quote `DealType` | Yes — rule identity |
| OQ5 | Compensatability (F5) | Assume `REVOKE` and `REGENERATE` non-compensatable (reconcile D-R5 / delegated); other ops unclassified here; do **not** treat ADR-027 acceptance as runtime GO | Every request compensated (DRG-466 text); invent new compensations in this story | Yes — failure / saga |
| OQ6 | Unmapped on-prem line (F6) | No dispatch; failed uncommitted unit `BLOCKED`; document `APPLY_BLOCKED` / `PARTIALLY_APPLIED` per OQ5; preserve committed units | Block whole document; dispatch `PROVISION` anyway | Yes — partial apply |
| OQ7 | Evidence writer (F7) | Future fulfilment component returns evidence; CAM writes commercial / commitment projections (see #817); no command added in this brief | Fulfilment component writes subscription/commitment status | Soft for emit; hard for CAM single-writer |
| OQ8 | Revocation outside conversions (F8) | One `REVOKE` op; Remove/Terminate/Upgrade-old/Swap-old share it; DRG-574 calls same op after its own acceptance | Second revocation mechanism for conversions; specify DRG-574 payload here | Yes — Remove/Terminate |
| OQ9 | When `REVOKE` is submitted (F9) | Record scheduled revoke after commercial commit; dispatch no earlier than `entitlementEndDate` and only after replacement evidence (delegated); leave partial-apply Q4 timing nuance explicit | Same step as `PROVISION`; always delay recording until window ends | Yes — Upgrade/Swap window |
| OQ10 | Cluster delivery envelope (C1–C4) | Docs Decision only for this PR; product path later = CAM durable per-unit request + async idempotent adapter HTTP (delegated C2); no revision / no auto-rollback on post-commit failure (C3); money stays on Accepted proration (C4); **no** `order-service` scaffold in this story | CAM-local row without Decision; scaffold order-service + DecompositionRule now; compensate every request; reprice inside fulfilment | Yes — any fulfilment build |
| OQ11 | Holds / registry / coupling | No flag flips; no `EventContractRegistry` edit; FE held; no dual-read; no Jev; commitment signal scored on #817; activation on #809 | Enable ERP or asset writer as fulfilment stand-in; register `FulfillmentDispatched` opportunistically | Yes if violated |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ1 — `EXTEND` (Cluster F F1)

| Option | What the owner would be choosing |
|---|---|
| **A. Reserved name, no v1 emitter (Recommended, Proposed)** | Keep `EXTEND` in the proposed op set for later. SaaS renewal continues as CAM `mutate` end-date update. Term-licence `RENEW` emits `REGENERATE`. Matches D-R5 SaaS-vs-term wording and Cluster F F1-A. |
| **B. Drop `EXTEND`** | Narrow the reconcile effect row. |
| **C. Emit `EXTEND` for SaaS and term** | Departs from D-R2/D-R5 mid-term SaaS-without-order story. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Default until owner picks (Proposed):** No `EXTEND` request type or emitter is added. `Owner decision: ___ (drg, time)`.

### OQ2 — Quantity up and down (F2)

| Option | What the owner would be choosing |
|---|---|
| **A. Both directions `MODIFY` (Recommended, Proposed)** | Seat/quantity changes stay one operation; down does not revoke the entitlement row. Matches D-R5 `Update` → `MODIFY`. |
| **B. Quantity-down → `REVOKE` delta** | Adds an operation the D-R5 Update row does not name; couples to dual-entitlement windows. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ3 — Downgrade (F3)

| Option | What the owner would be choosing |
|---|---|
| **A. Split quantity vs SKU (Recommended, Proposed)** | Quantity-only uses OQ2. SKU replacement uses create-before-terminate once DRG-576 supplies an allowed pair; until then no SKU-downgrade emitter. |
| **B. Always whole-entitlement `REVOKE`** | Over-revokes quantity-only downgrades. |
| **C. No Downgrade emitter** | Blocks all Downgrade fulfilment until a later Decision. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ4 — Decomposition key (F4)

| Option | What the owner would be choosing |
|---|---|
| **A. `(licenseModel, ActionCode)` with optional qualifiers (Recommended, Proposed)** | Uses landed enum spellings (`ADD`, not prose `Add`). Product id / relationship type optional until DRG-576. Quote `DealType` is not the key (reconcile). |
| **B. Relationship type required now** | Blocks on catalog relationship delivery. |
| **C. `DealType` selects the operation** | Rejected by reconcile D-R5 unless owner overrides. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ5 — Compensatability (F5)

| Option | What the owner would be choosing |
|---|---|
| **A. Non-compensatable `REVOKE` / `REGENERATE` assumption (Recommended, Proposed)** | Matches Accepted reconcile D-R5 and delegated §Fulfilment. ADR-027 Accepted-as-docs does **not** authorize saga tables or runtime point-of-no-return wiring in this story. Other ops stay unclassified here. |
| **B. Every request has a compensation** | DRG-466 acceptance text; conflicts with OQ5 option A / no automatic rollback. |
| **C. Treat ADR-027 merge as product GO** | Over-reads documentation acceptance as implementation authorization. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ6 — Unmapped on-prem line (F6)

| Option | What the owner would be choosing |
|---|---|
| **A. No dispatch; preserve committed units (Recommended, Proposed)** | Unmapped line does not invent `PROVISION`. Unit/document outcomes follow OQ5 partial-apply. No commitment state assigned here (#817). |
| **B. Block the whole document** | Conflicts with partial-apply option A. |
| **C. Dispatch `PROVISION` anyway** | Hides missing DecompositionRule. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ7 — Evidence (F7)

| Option | What the owner would be choosing |
|---|---|
| **A. Evidence in; CAM writes (Recommended, Proposed)** | Aligns Cluster F F7-A / delegated writer and #817 OQ4. This brief adds no command, event, or transport. |
| **B. Fulfilment writes status** | Second writer while entitlement ledger still empty. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ8 — Revocation outside conversions (F8)

| Option | What the owner would be choosing |
|---|---|
| **A. One `REVOKE` (Recommended, Proposed)** | Shared by Remove, Terminate, Upgrade/Swap old line, and (after DRG-574 acceptance) surrender after dual-use. No `Surrender` action token invented here. |
| **B. Second conversion-only revocation** | Forks the op set. |
| **C. Specify DRG-574 payload here** | Out of scope; belongs on DRG-574 / Cluster B. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ9 — When `REVOKE` is submitted (F9)

| Option | What the owner would be choosing |
|---|---|
| **A. Scheduled after commit; gate on end date + replacement evidence (Recommended, Proposed)** | Matches delegated F9 / §Fulfilment. `REVOKE` remains a separate unit from `PROVISION`. Create-before-terminate orders units commercially; dispatch timing is fulfilment. Window length stays with DRG-341 / ramp — not set here. |
| **B. Same step as `PROVISION`** | Can end the old entitlement before the dual-use window. |
| **C. Always delay recording until window ends** | Closes partial-apply open question 4 without owning that spec. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ10 — Cluster delivery envelope (C1–C4)

| Option | What the owner would be choosing |
|---|---|
| **A. Confirm delegated envelope for later product path (Recommended, Proposed)** | **This PR ships documentation only (C1-A).** When a later implementation brief is authorized: CAM durable per-unit request after commercial unit commit + async idempotent adapter HTTP (delegated C2); post-commit failure → correcting LifecycleChange, no executed-document revision, no automatic rollback (C3-A); fulfilment does not recompute proration (C4-A). No `order-service` / public bus schema in that envelope. |
| **B. Scaffold `order-service` + DecompositionRule in the first fulfilment slice** | Requires separate ADR-024 runtime cutover GO beyond docs Accepted. |
| **C. Compensate every fulfilment request on failure** | Conflicts with C3-A / OQ5 option A. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Default until owner picks (Proposed):** No fulfilment request table, worker, or adapter contract is added by this Decision brief. `Owner decision: ___ (drg, time)`.

### OQ11 — Holds, registry, coupling

| Option | What the owner would be choosing |
|---|---|
| **A. No flips; keep sibling briefs as gates (Recommended, Proposed)** | Leave ERP sync and legacy asset writer as found; leave dual-read and Jev holds untouched; no `EventContractRegistry` edit. Commitment signal / PendingActive machine stay on #817. Activation / entitlement ledger stay on #809. |
| **B. Treat ERP sync as fulfilment stand-in** | Collapses DealType re-queue into action-aware ops; false “done”. |
| **C. Flip dual-read or enable Jev** | Forbidden by ARCHITECTURE §17 / AGENT.md. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Blast radius note (`graph`):** There is no fulfilment symbol to impact yet. Editing commercial apply (`DocumentApplyOrchestrator`) or QuoteAccepted mutate is the future attachment point — current upstream risk readings are **LOW** for those classes alone; adding registry/event contracts later inherits the **HIGH** `EventContractRegistry` posture from #809.

---

## Mapping: Cluster F → this brief OQx

| Cluster F fork | Delegated disposition (standing input) | Decision brief OQ |
|---|---|---|
| C1 What this frame ships | Baseline A (docs); product later via fulfilment contract | OQ10 |
| C2 Transport | CAM durable request + async adapter HTTP | OQ10 |
| C3 Failure after commit | Baseline A (correcting LifecycleChange) | OQ10 |
| C4 Money | Baseline A (Accepted proration) | OQ10 |
| F1 `EXTEND` | Baseline A | OQ1 |
| F2 Quantity up/down | Baseline A | OQ2 |
| F3 Downgrade | Baseline A | OQ3 |
| F4 Decomposition key | Baseline A | OQ4 |
| F5 Compensatability | Baseline A | OQ5 |
| F6 Unmapped line | Baseline A | OQ6 |
| F7 Evidence | Baseline A | OQ7 |
| F8 Revocation outside conversions | One REVOKE shared | OQ8 |
| F9 When `REVOKE` submitted | Scheduled; gate on end date + replacement evidence | OQ9 |
| (holds / sibling briefs) | No impl authorization | OQ11 |

DRG-558 S1–S7 stay on draft [#817](https://github.com/drgaciw/open-crm-cpq/pull/817). They are **out of scope** for scoring in this file.

---

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Story | DRG-578 |
| Artifact | Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ1–OQ11 |

Example shape once scored (illustrative only, **not** a claim): `DRG-578 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## What this Decision brief does not do

- Does not authorize an implementation brief or product code for DRG-578.
- Does not adopt Cluster F or delegated §Fulfilment as runtime policy by merging this file.
- Does not scaffold `order-service`, `DecompositionRule`, fulfilment workers, or adapter schemas.
- Does not settle commitment states, fulfilment-required events, or activation AC (#817 / #809).
- Does not accept ADR-024–027 as permission to cut over production paths.
- Does not flip `opencrm.erp-sync.enabled` or `opencrm.assets.legacy-acceptance-write-enabled`.
- Does not mark Linear DRG-578 Done.

## Residual blockers (disclose on any later implementation brief)

- Owner close of OQ1–OQ11 on this Decision brief (words + time per fork).
- Owner close of sibling Decision briefs #809 (DRG-460/469) and #817 (DRG-558) where product paths share evidence / entitlement ledger / commitment projections.
- Separate implementation authorization for DRG-465 / DRG-466 (and any ADR-024 runtime cutover) if the chosen envelope needs them.
- DRG-576 relationship rows before SKU-replacement Downgrade/Upgrade emitters under OQ3-A / OQ4-A.
- Adapter endpoint schemas, authenticated evidence, idempotent natural keys, and failure/reconciliation tests (delegated §Fulfilment delivery artifacts) — not claimed by this brief.
- AGENT.md / ARCHITECTURE §17 holds (dual-read, Jev enablement, contract-asset FE, etc.).

## Reading checklist (this pass)

- [x] Linear DRG-578 + comments + relations (`Linear read`)
- [x] Cluster F frame + delegated §Fulfilment + reconcile D-R5 (`read`)
- [x] Sibling Decision briefs #809 / #817 shape (`read` from draft branches)
- [x] CAM ERP sync + QuoteAccepted apply/mutate + DocumentApplyOrchestrator (`read`)
- [x] `ActionCode` / V1_7 check (`read`)
- [x] GitNexus analyze + query/impact on this checkout (`graph`; fulfilment processes empty; apply/mutate risk LOW)
- [x] No product code / migrations / flags changed by this PR
