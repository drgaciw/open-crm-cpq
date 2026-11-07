# DRG-558 — Entitlement fulfilment step & commitment state model: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** accept product code, amend ADR-024/026, authorize an implementation brief, flip a flag, register an event, or deploy. Every recommendation below is `Status: Proposed`. No row is Adopted by this brief. Adoption for the **product path** requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** Cluster F — fulfilment / commitment projections (RA-24). **Story:** [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558/entitlement-fulfilment-step-and-commitment-state-model-pendingactive) (Entitlement fulfilment step and commitment state model — PendingActive to Terminated). Linear read this pass: **In Review**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Aligned briefs / frames (standing inputs, not reopened wholesale):**

| Record | Role for this Decision brief |
|---|---|
| [cluster-f-fulfilment-decision-frame.md](cluster-f-fulfilment-decision-frame.md) | Historical S1–S7 fork catalogue for DRG-558 (and C1–C4 / F1–F9 for DRG-578). Frame text retains pre-decision wording; see its Current authority header. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) §Fulfilment | Under owner delegation, assigned dispositions to Cluster F S1–S7 (and related fulfilment contracts). **Does not** authorize Java/TS, migrations, `EventContractRegistry` rows, or flags. |
| [story-drg-460-469-activation-decision-brief.md](story-drg-460-469-activation-decision-brief.md) (merged in #809; decisions Proposed) | Activation Decision brief. Its **OQ9** leaves the full PendingActive…Terminated set waiting on **this** DRG-558 Decision. |
| [adr-reconcile-adr-024-027.md](adr-reconcile-adr-024-027.md) | Effect row lists eight candidate commitment names as specification input for DRG-558. |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, registry entry, gateway route, or deploy.** An implementation brief for DRG-558 (or any product story that stores commitment states / emits a fulfilment-required event) is **blocked** until CPQ Decision scores and the owner closes the forks below (or explicitly defers a named fork with words and a time). Holds in [AGENT.md](../../../AGENT.md) and [ARCHITECTURE.md §17](../../ARCHITECTURE.md) stay in force: no dual-read flip, no Jev enablement, no contract-asset FE work from this brief.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear issue named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `graph` | GitNexus CLI impact/query on **this** checkout (`node .gitnexus/run.cjs … --repo .`) after `npx gitnexus analyze --index-only`. |
| `Status: Proposed` | Recommendation or default only. Not adopted by **this** Decision brief. Slot stays `Owner decision: ___ (drg, time)`. |
| Standing input | Earlier Accepted / delegated documentation. Used here; not re-litigated as code authority. |

## Context (why DRG-558 needs its own Decision brief)

DRG-558 asks for (1) a fulfilment step after commercial apply when licence regeneration / entitlement change is required, and (2) an explicit commitment status vocabulary from `PendingActive` through `Terminated` (plus grace / expiry / supersession names). Cluster F and the 2026-10-17 delegated record already wrote **policy dispositions**. Product readiness still fails the same gates as DRG-460/469: no entitlement ledger on `main`, no commitment-status column or table, no fulfilment-required event registered, and activation Decision forks remain open in the brief merged by #809.

Scoring DRG-558 separately from DRG-578 keeps operation-selection (provision / modify / revoke / …) out of this brief. Scoring it separately from DRG-460 keeps activation AC (quantity, term, minimal status, `ListEntitlements`) from silently absorbing the full commitment machine.

### Related tickets (Linear read 2026-11-06)

| Ticket | Linear status | Role for this Decision brief |
|---|---|---|
| [DRG-578](https://linear.app/drgamtd-workspace/issue/DRG-578) | In Review | Action-aware fulfilment decomposition. Sibling Cluster F story; not scored here. |
| [DRG-460](https://linear.app/drgamtd-workspace/issue/DRG-460) / [DRG-469](https://linear.app/drgamtd-workspace/issue/DRG-469) | Backlog | Activation Decision brief #809. OQ9 defers full commitment set to DRG-558. |
| [DRG-654](https://linear.app/drgamtd-workspace/issue/DRG-654) / [DRG-700](https://linear.app/drgamtd-workspace/issue/DRG-700) | related | Assets may carry `entitlement_id`; production lookup is empty until entitlements exist. |
| [DRG-574](https://linear.app/drgamtd-workspace/issue/DRG-574) | Backlog | Conversion surrender / revocation. `Surrendered` as a commitment name is a fork option (OQ1). |
| [DRG-466](https://linear.app/drgamtd-workspace/issue/DRG-466) | Backlog | Fulfilment orchestrator dependency. No event/transport decided in this brief. |
| [DRG-577](https://linear.app/drgamtd-workspace/issue/DRG-577) | Done | Linear `blockedBy` for DRG-558; reconcile is input text only. |
| Flag `opencrm.erp-sync.enabled` | Default on (`read` CAM `application.yml`) | Not flipped here. Today's PENDING→ACTIVE path rides simulated ERP sync; that is **not** a commitment-state machine. |
| Flag `opencrm.assets.legacy-acceptance-write-enabled` | Default off | Stays off. No part of this brief authorizes turning it on. |

**Verdict on product readiness:** No `PendingActive` / `PendingTerminate` / `InGrace` / commitment `Superseded` / `Surrendered` values exist in CAM production schema or Java enums. Subscription row status remains the five-value `SubscriptionStatus` check. Entitlement lookup returns empty. **Implementation briefs remain NO-GO** until the forks below are closed and residual blockers at the end are disclosed.

## Current code facts (`read` + `graph` + text confirm)

Facts verified against `main` SHA above. Paths are relative to the repo root.

| Fact | Evidence |
|---|---|
| Subscription row statuses on `main`: `PENDING`, `ACTIVE`, `SUSPENDED`, `CANCELLED`, `EXPIRED` | `SubscriptionStatus.java` (`read`); `V1_1__create_contract_tables.sql` `chk_subscription_status` (`read`); FE mirror `subscription.model.ts` (`read`) |
| Those five values are **not** the DRG-558 commitment set. No `PendingActive`, `PendingTerminate`, `InGrace`, commitment-`Superseded`, or `Surrendered` in product Java/SQL outside docs | Repo text search this pass (excluding docs): no matches for `PendingActive` / `PendingTerminate` / `InGrace` as product identifiers |
| PENDING→ACTIVE today: `Subscription.activate()`, HTTP activate, and `ErpSyncJob` after simulated ERP sync when status is still `PENDING` | `ErpSyncJob.java`, `Subscription.java` (`read`); Cluster F CAM ERP sync table |
| ERP sync status is a **separate** string set: `PENDING`, `QUEUED`, `IN_PROGRESS`, `SYNCED`, `FAILED` | `ErpSyncStatus.java` (`read`) |
| No entitlement entity / ledger / status enum under CAM production code; assets keep nullable `entitlement_id` | `V1_13__create_assets.sql`; `EmptyEntitlementLookup` always returns empty (`read`) |
| `EmptyEntitlementLookup` — `graph` upstream impact: **risk UNKNOWN**, 0 resolved callers; text confirm: Spring-injected into `AssetActivation` | `node .gitnexus/run.cjs impact "EmptyEntitlementLookup" --direction upstream --repo .`; `AssetActivation.java` line using `entitlements.findEntitlementId` |
| Query for entitlement/commitment status flows returns asset activation / `entitlementId` plumbing, not a commitment state machine | `node .gitnexus/run.cjs query "entitlement commitment status PendingActive fulfilment" --repo .` |
| No `EntitlementProvisioned` / fulfilment-required event in `EventContractRegistry` / `KafkaTopics` | Same evidence as #809 brief; `ContractExecuted` remains RESERVED |
| Asset lifecycle statuses (`ACTIVE`, `SUPERSEDED`, `TERMINATED`) are installed-base facts, not entitlement commitment states | `AssetStatus.java` (`read`) |
| Cluster F S1–S7 remain the named fork catalogue; delegated §Fulfilment assigned dispositions without landing schema | [cluster-f](cluster-f-fulfilment-decision-frame.md); [delegated record](delegated-renewals-amendments-decisions-2026-10-17.md) |

### Gap vs issue vocabulary (PendingActive → Terminated)

| Name (issue / research / reconcile) | On `main` product code? |
|---|---|
| `PendingActive` | No |
| `Active` (commitment) | No separate commitment `Active`; subscription has `ACTIVE` |
| `Suspended` (commitment) | No separate; subscription has `SUSPENDED` |
| `InGrace` | No |
| `PendingTerminate` | No |
| `Terminated` (commitment) | No; assets have `TERMINATED` |
| `Expired` (commitment) | No separate; subscription has `EXPIRED` (no CAM production assign found in Cluster F earlier pass) |
| `Superseded` (commitment) | No; OQ6 document `SUPERSEDED` and asset `SUPERSEDED` are different facts |
| `Surrendered` (conversions proposal / delegated) | No |

## Market research refs (fork **options**, not decided policy)

Cited on [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558) and in [cpq-renewals-amendments-research.md](../cpq-renewals-amendments-research.md) §§4.1, 4.2, 7.2, 7.3, 7.6. **None of the following is adopted as the Open CRM/CPQ enum or operation set by this brief.**

| Source | What the research shows (illustrative) | How this brief treats it |
|---|---|---|
| Revenera FlexNet Operations | Separate fulfilment ops (`renewFulfillment` / `upgradeFulfillment` / `upsellFulfillment`); licence regeneration as a second step after commercial change | Option material for OQ2 / OQ3 (typed fulfilment step). Not copied as CAM API names. |
| Thales Sentinel EMS | Regenerate License after entitlement recharge / change | Option material for OQ2 / OQ3 (`REGENERATE`-shaped evidence). Not a schema decision. |
| Snow / Flexera license manager | Base→upgrade quantity mapping, including metric changes (e.g. installations→processors) | Option material for quantity/metric conversion evidence. Does not settle commitment names. |
| TM Forum TMF637 `ProductStatusType` | `created`, `pendingActive`, `active`, `suspended`, `pendingTerminate`, `terminated`, `cancelled`, `aborted` (illustrative) | Option material for OQ1-B (TMF extras). Issue already calls these illustrative. |

The [delegated fulfilment decision](delegated-renewals-amendments-decisions-2026-10-17.md#fulfilment) has already adopted nine commitment projections: the eight research names from `PendingActive` through `Superseded`, plus `Surrendered`. That policy decision remains in force. This brief does not authorize an enum, schema, or implementation; the product-specific gates below remain open.

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Recommendations are Proposed.
2. CPQ Decision must score the forks. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work for DRG-558 commitment states, fulfilment-required signals, or entitlement status persistence.
4. Even after forks close: no dual-read flip; no Jev flag enablement; no `legacy-acceptance-write-enabled` flip from this brief; no merge authority in this brief; DRG-460/469 activation Decision and DRG-578 operation selection remain sibling gates.
5. First registration of any new event in `EventContractRegistry` remains owner-gated (same HIGH-risk posture as #809 OQ1).

---

## Owner-decision table (summary)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells in **this** file.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product? |
|---|---|---|---|---|
| OQ1 | Commitment status vocabulary | Confirm delegated set for product: eight names + `Surrendered` as projections; do not land yet | Eight-only (drop `Surrendered`); add TMF `created`/`cancelled`/`aborted`; reopen to research-only eight without Surrendered | Yes — no status enum/schema without close |
| OQ2 | Market research treatment | Evidence / fork options only; do not copy Revenera/Thales/Snow/TMF enums or op names | Copy TMF637 as CAM enum; copy Revenera fulfilment op names as public API | Yes if owner demands a copy-as-policy path |
| OQ3 | Fulfilment step / signal | Confirm delegated S2: per-unit durable CAM request + correlated evidence callback; **no** new public bus event in this story | Document-level signal; register `EntitlementProvisioned` / `FulfillmentDispatched` now (needs separate registry GO) | Yes — signal shape gates implementation |
| OQ4 | Who writes commitment status | Confirm delegated S3: CAM sole writer; adapter supplies evidence only | Orchestrator (DRG-466) writes status | Yes |
| OQ5 | SaaS subscription row vs commitment | Confirm delegated S4: keep `SubscriptionStatus` + `ErpSyncStatus`; do not map onto commitment names in v1 | Map five subscription values onto commitment names | Yes if B chosen without migration plan |
| OQ6 | `InGrace` / `Expired` | Confirm delegated S5: grace off unless policy supplies finite grace-end; else expire at term end; no job invented here | Define grace length + expiry job in this story | Yes for grace/expiry product work |
| OQ7 | Commitment `Superseded` vs OQ6 | Confirm delegated S6: distinct facts; commitment `Superseded` needs linked confirmed successor | Store replacement in revision `status_at_close` | Yes |
| OQ8 | When `PendingActive` / `Active` start | Confirm delegated S7: `PendingActive` until authenticated evidence; `Active` only after CAM records evidence | `Active` at commercial apply before evidence | Yes |
| OQ9 | Coupling to DRG-460 OQ9 | Keep #809 OQ9-A: minimal entitlement status for activation AC; full machine lands only after **this** Decision closes | Ship full set inside DRG-460; reuse subscription status only | Yes — sequencing with #809 |
| OQ10 | Persistence shape (still open) | Design-only until Decision: CAM-owned commitment projection periods (separate from `subscriptions.status`); schema number assigned at build time only | Column on subscription; event-sourced only with no read model; entitlement table required before any status | Yes — storage choice |
| OQ11 | Holds / flags / registry | No flag flips; no `EventContractRegistry` edit; FE held; no dual-read; no Jev enablement | Enable legacy asset writer; register fulfilment event opportunistically; flip dual-read / Jev | Yes if violated |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ1 — Commitment status vocabulary

| Option | What the owner would be choosing |
|---|---|
| **A. Confirm delegated nine-name projection set (Recommended, Proposed)** | Product path may later implement: `PendingActive`, `Active`, `Suspended`, `InGrace`, `PendingTerminate`, `Terminated`, `Expired`, `Superseded`, and `Surrendered`. Matches delegated §Fulfilment and Cluster F S1-C-shaped adoption. Still **no** schema in this brief. |
| **B. Eight names only** | Drop `Surrendered` from the product vocabulary; leave it on DRG-574 until separately scored. |
| **C. Add TMF637 extras** | Also land `created`, `cancelled`, `aborted` (illustrative in the issue). Widens the enum beyond reconcile + delegated text. |
| **D. Reopen** | Treat delegated S1 as non-binding for product; re-score from Cluster F historical A/B/C only. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Default until owner picks (Proposed):** No commitment status column, check constraint, or Java enum is added. `Owner decision: ___ (drg, time)`.

### OQ2 — Market research as options (not policy)

| Option | What the owner would be choosing |
|---|---|
| **A. Evidence only (Recommended, Proposed)** | Revenera / Thales / Snow / TMF637 citations justify *that* a fulfilment step and a status vocabulary are needed. They do not dictate CAM type names, HTTP paths, or Kafka contracts. |
| **B. Copy TMF637 ProductStatusType** | Use TMF spelling/set as the CAM enum (includes cancelled/aborted/created). |
| **C. Copy Revenera fulfilment operation names** | Public or internal API uses renew/upgrade/upsell fulfillment names. Conflicts with Cluster F operation catalogue unless explicitly remapped. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ3 — Fulfilment step / signal shape

| Option | What the owner would be choosing |
|---|---|
| **A. Per-unit durable request + evidence callback; no new public bus event (Recommended, Proposed)** | Matches delegated S2 and Cluster F S2-A spirit: after an on-prem unit commits, CAM records a fulfilment request; evidence returns asynchronously. Does **not** register `EntitlementProvisioned` or `FulfillmentDispatched`. |
| **B. Document-level signal at `APPLIED`** | One signal for the whole document; weaker fit to OQ5 partial-apply units. |
| **C. Register a named domain event now** | Requires separate `EventContractRegistry` GO (HIGH blast radius — same posture as #809 OQ1). DRG-460 / DRG-466 names stay candidates only until that GO. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ4 — Writer

| Option | What the owner would be choosing |
|---|---|
| **A. CAM sole writer (Recommended, Proposed)** | Adapter / fulfilment component supplies correlated, authenticated, version-bound evidence; CAM writes the projection. Matches delegated S3 / F7. |
| **B. Orchestrator writes** | DRG-466 becomes the writer. Event/transport still undecided; expands blast radius. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ5 — SaaS subscription row

| Option | What the owner would be choosing |
|---|---|
| **A. Keep subscription + ERP sync statuses as today (Recommended, Proposed)** | Commitment projections do not rename `chk_subscription_status` or `ErpSyncStatus`. SaaS mid-term v1 does not require the fulfilment-required signal (reconcile effect row). |
| **B. Map subscription statuses onto commitment names** | Collapses BC-09 subscription facts into entitlement commitment facts; rejected by taxonomy unless owner overrides. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ6 — `InGrace` and `Expired`

| Option | What the owner would be choosing |
|---|---|
| **A. Policy-gated grace; default off (Recommended, Proposed)** | Enter `InGrace` only when a policy supplies a finite grace-end; otherwise finite rights expire at term end into `Expired`. No grace-length constant invented in this brief. |
| **B. Ship grace length + expiry job in the first product slice** | Couples renewal-policy numbers into DRG-558 delivery. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ7 — `Superseded` versus OQ6

| Option | What the owner would be choosing |
|---|---|
| **A. Distinct facts (Recommended, Proposed)** | Commitment `Superseded` requires a linked confirmed successor entitlement/commitment. OQ6 `SUPERSEDED` remains revision `status_at_close`. Asset `SUPERSEDED` remains installed-base. |
| **B. Reuse revision close status** | Mixes document lifecycle with delivery state. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ8 — `PendingActive` → `Active`

| Option | What the owner would be choosing |
|---|---|
| **A. Evidence-gated Active (Recommended, Proposed)** | After on-prem unit commit, projection starts `PendingActive` until CAM records successful fulfilment evidence; then `Active`. Request intent alone never grants `Active`. |
| **B. Active at commercial apply** | Removes the waiting step the issue exists to name. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ9 — Coupling to DRG-460 / #809 OQ9

| Option | What the owner would be choosing |
|---|---|
| **A. Minimal status for DRG-460; full machine after this Decision (Recommended, Proposed)** | Keeps activation AC unblocked conceptually once #809 closes, without requiring the full PendingActive…Terminated set in the first entitlement row. Full set is **this** story's Decision. |
| **B. Full set inside DRG-460** | Couples BC-07 delivery to In Review / this Decision; slows activation. |
| **C. Subscription status only** | Entitlement ≠ subscription (DRG-661); rejected as sole model. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ10 — Persistence shape

Delegated text requires CAM-owned commitment **projections** and state periods; it does **not** pick a Flyway shape. That choice is still an owner fork for any later implementation brief.

| Option | What the owner would be choosing |
|---|---|
| **A. Separate commitment projection / period store (Recommended, Proposed)** | New CAM tables (numbers assigned at build time only). Do not overload `subscriptions.status`. |
| **B. Add columns on `subscriptions`** | Collides with OQ5-A and the existing check constraint. |
| **C. Entitlement table mandatory before any status** | Stronger sequencing: DRG-460 ledger first, then status machine. Compatible with A as a later migration wave. |
| **D. Event-sourced only, no read model in v1** | Harder `ListEntitlements` / asset link; usually rejected for BC-07 AC. |

**Recommended (Proposed):** A (with C allowed as a sequencing constraint the owner may still impose). `Owner decision: ___ (drg, time)`.

### OQ11 — Holds and flags

| Option | What the owner would be choosing |
|---|---|
| **A. No flips (Recommended, Proposed)** | Leave ERP sync and legacy asset writer as found; leave dual-read and Jev holds untouched; no `EventContractRegistry` edit from this Decision brief. |
| **B. Enable legacy asset writer as entitlement stand-in** | Rejected by asset-write-hold / DRG-654 delivery hold. |
| **C. Flip dual-read or enable Jev** | Forbidden by ARCHITECTURE §17 / AGENT.md. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Mapping: Cluster F S\* → this brief OQx

| Cluster F fork | Delegated disposition (standing input) | Decision brief OQ |
|---|---|---|
| S1 Commitment states | Eight + `Surrendered`, evidence-based | OQ1 (+ OQ2 market extras) |
| S2 Fulfilment-required signal | Per-unit durable request; no new public bus event | OQ3 |
| S3 Writer | CAM sole writer | OQ4 |
| S4 SaaS subscription row | Keep `V1_1` / ERP sync statuses | OQ5 |
| S5 `InGrace` / `Expired` | Grace off unless policy; else expire at term | OQ6 |
| S6 `Superseded` vs OQ6 | Distinct facts | OQ7 |
| S7 `PendingActive` / `Active` | Evidence-gated | OQ8 |
| (activation coupling) | — | OQ9 |
| (schema shape) | Projections required; Flyway unset | OQ10 |
| (holds) | No impl authorization | OQ11 |

DRG-578 F1–F9 and cluster C1–C4 stay on Cluster F / delegated fulfilment contracts; they are **out of scope** for scoring in this file.

---

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Story | DRG-558 |
| Artifact | Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ1–OQ11 |

Example shape once scored (illustrative only, **not** a claim): `DRG-558 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## What this Decision brief does not do

- Does not authorize an implementation brief or product code for DRG-558.
- Does not mark any fork Adopted in **this** file (delegated dispositions remain standing documentation inputs, not a schema GO).
- Does not create entitlement tables, commitment status columns, fulfilment request tables, gateway routes, or FE.
- Does not register or publish `EntitlementProvisioned`, `FulfillmentDispatched`, or any fulfilment-required event.
- Does not flip `opencrm.erp-sync.enabled`, `opencrm.assets.legacy-acceptance-write-enabled`, dual-read, or any `opencrm.jev.*` flag.
- Does not score DRG-578 operation selection or DRG-460/469 activation forks.
- Does not mark Linear DRG-558 Done.
- Does not merge or enable auto-merge on any PR.

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ1–OQ11. Until then, implementation briefs are blocked (**NO-GO** for product Java/TS).
2. **DRG-460/469 Decision (#809):** activation forks still Proposed; OQ9 sequencing depends on this brief closing.
3. **DRG-578 In Review:** operation selection still a sibling gate for any fulfilment adapter that would supply evidence.
4. **Event registration GO:** any future named event still needs explicit HIGH-risk registry authorization.
5. **Empty entitlement lookup / DRG-700:** `entitlementId` stays empty until a real entitlement exists.
6. **Asset write hold:** legacy acceptance writer stays off.
7. **AGENT.md / ARCHITECTURE §17 holds:** dual-read, Jev enablement, contract-asset FE remain held.
