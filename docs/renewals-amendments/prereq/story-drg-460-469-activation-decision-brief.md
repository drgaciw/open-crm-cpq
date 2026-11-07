# DRG-460 / DRG-469 — Contract & subscription activation: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** accept Cluster F, amend ADR-024/026, authorize an implementation brief, authorize product code, flip a flag, register an event, or deploy. Every recommendation below is `Status: Proposed`. No row is Adopted. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** contract → entitlement → subscription activation (BC-07-03 / BC-09-01). **Stories:** [DRG-460](https://linear.app/drgamtd-workspace/issue/DRG-460/bc-07-03-activate-contract-and-provision-entitlements) (ActivateContract · ProvisionEntitlement · EntitlementProvisioned · ListEntitlements) · [DRG-469](https://linear.app/drgamtd-workspace/issue/DRG-469/bc-09-01-activate-subscription-from-contract-line) (ActivateSubscription · SubscriptionActivated). Linear read this pass: both **Backlog**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Taxonomy authority (Accepted ADR, stories still gated):** [DRG-661](https://linear.app/drgamtd-workspace/issue/DRG-661) Accepted (drg, 2026-10-29 14:11 Dublin). Canonical chain: accepted quote → contract (+ contract lines) → subscription, asset and entitlement. Dependent activation stories still launch only after CPQ Decision scores **this** brief (and any named residual blockers close).

**Scout:** [bc-2b591593-c26a-539e-b265-7d76830dd327](https://cursor.com/agents/bc-2b591593-c26a-539e-b265-7d76830dd327) — activation not launchable next wave without owner GO; missing entitlement ledger/API; `EntitlementProvisioned` undecided; no `contract_lines` until DRG-665; `SubscriptionActivated` needs registry registration GO.

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, registry entry, gateway route, or deploy.** An implementation brief for DRG-460 or DRG-469 is **blocked** until CPQ Decision scores and the owner closes the forks below (or explicitly defers a named fork with words and a time). Holds in [AGENT.md](../../../AGENT.md) and [ARCHITECTURE.md §17](../../ARCHITECTURE.md) stay in force: no dual-read flip, no Jev enablement, no contract-asset FE work from this brief.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear issue named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `graph` | GitNexus CLI impact/query on **this** checkout (`node .gitnexus/run.cjs … --repo .`) after `npx gitnexus analyze --index-only`. |
| `Status: Proposed` | Recommendation or default only. Not adopted. Slot stays `Owner decision: ___ (drg, time)`. |

## Context (why these two stories share one brief)

DRG-460 (BC-07) provisions entitlements when a contract activates. DRG-469 (BC-09) activates a subscription from a contract line and emits `SubscriptionActivated`. Both sit on the same origin chain (DRG-661 / [DRG-665](https://linear.app/drgamtd-workspace/issue/DRG-665) S-T4) and share the same event-registry and idempotency gates. Scoring them separately would re-open the same forks twice. Product AC for either story remains **NO-GO** until the forks below are closed.

### Related tickets (Linear read 2026-11-06)

| Ticket | Linear status | Role for this Decision brief |
|---|---|---|
| [DRG-661](https://linear.app/drgamtd-workspace/issue/DRG-661) | Backlog (ADR Accepted) | Taxonomy + origin chain Accepted; OQ5 (who registers `SubscriptionActivated`) still open for owner. |
| [DRG-665](https://linear.app/drgamtd-workspace/issue/DRG-665) | In Progress | S-T4 `contract_lines` + nullable `subscriptions.contract_line_id`. Residual product-path blocker; interim line = `accepted_line_uuid`. Draft product PR exists (#805) — **out of scope of this docs brief**. |
| [DRG-662](https://linear.app/drgamtd-workspace/issue/DRG-662) | In Progress | S-T1 `subscriptions.tenant_id`. Tenant population still depends on DRG-631. |
| [DRG-458](https://linear.app/drgamtd-workspace/issue/DRG-458) | Backlog | GenerateContract / ContractGenerated. Preferred before full ActivateContract; not a reason to skip this Decision brief. |
| [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558) | In Review | Commitment state model (PendingActive…Terminated). Cluster F frame landed as docs; commitment names still Proposed. |
| [DRG-654](https://linear.app/drgamtd-workspace/issue/DRG-654) / [DRG-700](https://linear.app/drgamtd-workspace/issue/DRG-700) | related | Assets write at acceptance under a held flag; `entitlementId` empty until entitlements exist. |
| [DRG-668](https://linear.app/drgamtd-workspace/issue/DRG-668) | Backlog | S-TX3 field additions (`subscriptionModel`, `tenantId`) on `SubscriptionActivated` **after** that event exists. |
| Flag `opencrm.assets.legacy-acceptance-write-enabled` | Default off (`read` `AssetActivation` + [asset-write-hold](../../runbooks/asset-write-hold.md)) | Stays off. No part of this brief authorizes turning it on. |
| Flag `opencrm.erp-sync.enabled` | Default on (`read` CAM `application.yml`) | Not flipped here. Today's PENDING→ACTIVE path rides simulated ERP sync; that is interim, not the BC-09 command path. |

**Verdict on product readiness:** No entitlement ledger, no `ListEntitlements`, no `EntitlementProvisioned` / `SubscriptionActivated` registry entries, no `ActivateSubscription` command handler, and no `contract_lines` table on `main`. Interim path is QuoteAccepted → PENDING subscription → HTTP/`ErpSyncJob` activate. **Implementation briefs remain blocked on owner fork closure** and remain gated by residual blockers listed at the end.

## Current code facts (`read` + `graph`)

Facts verified against `main` SHA above. Paths are relative to the repo root.

| Fact | Evidence |
|---|---|
| QuoteAccepted creates a PENDING subscription (and rate periods); ledger digests make apply idempotent per quote | `AcceptedQuoteSubscriptionService.apply` (`…/service/AcceptedQuoteSubscriptionService.java`) |
| Consumer of that apply | `QuoteAcceptedEventConsumer` (`graph` upstream of `AcceptedQuoteSubscriptionService`: 1 direct, risk LOW) |
| PENDING→ACTIVE today: `Subscription.activate()`, HTTP `POST /api/v1/subscriptions/{id}/activate`, and `ErpSyncJob` on successful simulated ERP sync | `Subscription.java`, `SubscriptionController.java`, `ErpSyncJob.java` (`graph` query "subscription activation…": activate flows through controller → service → domain) |
| `ContractExecuted` is **RESERVED** (payload class exists; Outbox refuses until PUBLISHED) | `EventContractRegistry.CONTRACT_EXECUTED_V1`; `ContractExecutedEvent.java`; topic `cpq.contract.executed` |
| No `SubscriptionActivated`, `EntitlementProvisioned`, `ActivateSubscription` command, or entitlement topic in `KafkaTopics` / `EventContractRegistry` | `EventContractRegistry.java` ALL list; `KafkaTopics.java` (`read`) |
| ADR-024 / ADR-026 **Accepted** as docs; both still **do not authorize** code, topics, or registry entries | [ADR-024](../../adr/ADR-024-order-orchestration-context.md), [ADR-026](../../adr/ADR-026-command-messages.md) |
| ADR-026 names target shape: command `cpq.subscription.command.activate` + replies `SubscriptionActivated` / `SubscriptionActivationRejected` | ADR-026 D-01 |
| Thin `contracts` header exists; **no** `contract_lines` table on `main` | `V1_7__contract_documents.sql`; DRG-665 In Progress |
| Interim line identity for rates / assets: `subscription_rate_periods.accepted_line_uuid` (also used as `contractLineId` on assets when the held writer runs) | `SubscriptionRatePeriod.java`; `AssetActivation.onActivated`; DRG-469 Origin rule |
| Entitlement lookup is empty; assets cannot link an entitlement id | `EmptyEntitlementLookup` (`graph` upstream UNKNOWN — confirm: only implements `EntitlementLookup`; wired into `AssetActivation`) |
| Asset acceptance-time writer held off by default | `AssetActivation` `@ConditionalOnProperty(…legacy-acceptance-write-enabled=true)`; [docs/design/DRG-654-asset-on-activation.md](../../design/DRG-654-asset-on-activation.md) |
| Editing `EventContractRegistry` is HIGH blast radius | `graph` impact upstream: risk **HIGH**, 55 impacted symbols |
| Cluster F explicitly left `EntitlementProvisioned` undecided | [cluster-f-fulfilment-decision-frame.md](cluster-f-fulfilment-decision-frame.md) |

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Recommendations are Proposed.
2. CPQ Decision must score the forks. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work for DRG-460 or DRG-469.
4. Even after forks close: no dual-read flip; no Jev flag enablement; no `legacy-acceptance-write-enabled` flip from this brief; no merge authority in this brief; DRG-665 / DRG-662 / DRG-458 remain residual product-path blockers to disclose on any later implementation brief.
5. First registration of any new event or command in `EventContractRegistry` remains owner-gated (DRG-661 OQ5; ADR-026 "does not authorize any … topic or registry entry").

---

## Owner-decision table (summary)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ1 | Who may register activation events/commands in `EventContractRegistry` | Owner (drg) explicit GO per contract (or CI step only after that GO); first wave: RESERVED entries + payload stubs only after GO | CI auto-registers on merge of any CAM PR; register PUBLISHED with producer in the same PR as first emit | Yes — DRG-460 EntitlementProvisioned; DRG-469 SubscriptionActivated |
| OQ2 | Activation ordering (contract / entitlement / subscription / asset) | Contract active → ProvisionEntitlement per line → SubscriptionActivated → asset entitlement link (DRG-700); keep legacy asset-at-acceptance **off** | Parallel entitlement+subscription; subscription first then entitlement; asset-at-acceptance as activation proxy | Yes — both stories' AC sequencing |
| OQ3 | Trigger path for DRG-469 v1 | Keep QuoteAccepted → PENDING → activate strangler (`subscription-source=quote-accepted`); treat ADR-024 `ActivateSubscription` command as later cutover | Ship ActivateSubscription command + replies in this story; dual-publish QuoteAccepted and command | Yes — SubscriptionActivated emission site |
| OQ4 | Interim line identity until `contract_lines` | `accepted_line_uuid` is the activation line key; `contract_id` on subscription when present; no fake `contract_lines` rows | Block DRG-469 until DRG-665 merges; synthesize temporary contract_line UUIDs | Yes — ActivateSubscription(contractLine) |
| OQ5 | Idempotency / business key | Claim once on commandId (ADR-026) **and** business key = `(tenant_id, contract_line_id)` or interim `(tenant_id?, accepted_line_uuid, quote_id)`; replay returns success if already active | Quote-only key; order-line key only (needs order-service); subscriptionNumber only | Yes — replay / dual delivery |
| OQ6 | Failure modes mid-activation | Atomic per line unit; failed unit → no ACTIVE subscription for that line; no automatic compensation; correcting LifecycleChange later (align Cluster F C3-A / OQ5/OQ6 amendment rules) | Compensate prior entitlements; leave PENDING forever; open exception queue (ADR-027) in this slice | Yes — partial provision |
| OQ7 | Tenant and customer identity on activation | `tenant_id` from QuoteAccepted tid provenance (DRG-631/662); `customerId` remains external string (as today); entitlement account key = same string CAM subscriptions use; typed UUID customer key deferred to S-T2 | Require CRM account UUID before any activate; reject apply when tenant null | Soft for first emit; hard for multi-tenant isolation |
| OQ8 | `ContractExecuted` RESERVED → PUBLISHED | Leave RESERVED until ActivateContract product story has owner GO; do not overload it as EntitlementProvisioned | Flip to PUBLISHED as the sole activation signal; emit both ContractExecuted and EntitlementProvisioned | Soft for 469; hard for 460 if ContractExecuted is chosen as the entitlement feed |
| OQ9 | Commitment / entitlement status set | Defer full PendingActive…Terminated set to DRG-558 owner close; DRG-460 v1 stores quantity, term, and a minimal `{PENDING, ACTIVE, …}` subset named in the implementation brief after Decision | Adopt full TMF637-like set in DRG-460; reuse subscription status enum only | Yes — Entitlement status AC |
| OQ10 | `ListEntitlements(account)` surface | CAM-owned read API; account filter required; coarse role later (mirror assets `InstalledBase.Read` caution); no FE in this wave | Gateway-only BFF; CRM-owned list; ship FE with BC-07 | Yes — ListEntitlements AC |
| OQ11 | Holds / flags | No flag flips; FE held; no dual-read; no Jev enablement | Enable legacy asset writer; flip dual-read; enable Jev advisory beyond local | Yes if violated |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ1 — Event / command registration authorization

| Option | What the owner would be choosing |
|---|---|
| **A. Owner GO per contract (Recommended, Proposed)** | drg authorizes each new `EventContractRegistry` entry (and any `KafkaTopics` constant) by words+time. A follow-on CI check may enforce shape after that GO. Matches DRG-661 OQ5 and ADR-026 "does not authorize … registry entry". |
| **B. CI auto-register on any CAM merge** | Fitness tests alone gate registration. Rejected by taxonomy / scout gating. |
| **C. PUBLISHED + first producer in one PR** | Allowed only after A’s GO names that contract. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Default until owner picks (Proposed):** No new registry rows for `EntitlementProvisioned`, `SubscriptionActivated`, or ActivateSubscription command. `Owner decision: ___ (drg, time)`.

**Blast radius note (`graph`):** changing `EventContractRegistry` is **HIGH** risk (55 upstream symbols). Treat registration PRs as separately scored, not as drive-by edits inside an activation feature PR.

### OQ2 — Activation ordering

| Option | What the owner would be choosing |
|---|---|
| **A. Contract → entitlement → subscription → asset link (Recommended, Proposed)** | ActivateContract (or equivalent executed contract) then ProvisionEntitlement per line (DRG-460), then SubscriptionActivated (DRG-469), then DRG-700 fills `entitlementId` on assets. Legacy acceptance-time asset write stays off. |
| **B. Parallel entitlement and subscription** | Same trigger fans out; ordering only by outbox keys. Harder failure semantics (OQ6). |
| **C. Subscription-first** | Matches today's ERP activate more closely; entitlements lag; BC-07 AC and asset link stay empty longer. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ3 — Trigger path for subscription activation (strangler vs command)

| Option | What the owner would be choosing |
|---|---|
| **A. Keep QuoteAccepted strangler for v1 (Recommended, Proposed)** | Continue `AcceptedQuoteSubscriptionService` + PENDING→ACTIVE. Emit `SubscriptionActivated` only after OQ1 GO, from the activate path (HTTP and/or ErpSyncJob), keyed by interim line identity (OQ4). ADR-024 command cutover is a later story. Matches scout "keep `subscription-source=quote-accepted` until strangler". |
| **B. ActivateSubscription command in this story** | Requires order-service / saga (ADR-024/025) and three new contracts (ADR-026). Out of residual readiness for a next-wave CAM-only slice. |
| **C. Dual-publish** | QuoteAccepted path and command path both emit. Highest drift risk; rejected unless owner demands parallel run. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ4 — Interim line identity

| Option | What the owner would be choosing |
|---|---|
| **A. `accepted_line_uuid` interim (Recommended, Proposed)** | Until DRG-665 lands `contract_lines`, ActivateSubscription(contractLine) means the rate-period / asset `accepted_line_uuid`. Contract linkage uses `subscriptions.contract_id` when set. Matches DRG-469 Origin rule text. |
| **B. Block until `contract_lines`** | DRG-469 implementation brief waits on DRG-665 merge + Decision Done. |
| **C. Synthesize temporary contract_line UUIDs** | Creates a second identity to migrate; avoided. |

**Recommended (Proposed):** A for Decision scoring now; B remains a residual **implementation** gate the owner may still impose. `Owner decision: ___ (drg, time)`.

### OQ5 — Idempotency

| Option | What the owner would be choosing |
|---|---|
| **A. commandId claim + business key (Recommended, Proposed)** | ADR-026 D-03: inbox claim on commandId when commands exist; always also idempotent on business key. Interim business key: `(accepted_line_uuid, quote_id)` (and tenant when OQ7 lands). Already-active → success reply / no-op. Aligns with existing quote digest ledger for creation. |
| **B. Quote id only** | Insufficient once one quote maps to many lines. |
| **C. Order line id only** | Correct after order cutover; premature without order-service. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ6 — Failure modes

| Option | What the owner would be choosing |
|---|---|
| **A. Per-line atomic; no auto-compensate (Recommended, Proposed)** | A failed ProvisionEntitlement leaves that line without an ACTIVE subscription side-effect. Prior successful lines stay. Remedy = correcting change (Cluster F C3-A). |
| **B. Compensate prior entitlements** | Needs DRG-466 / ADR-027 acceptance as product work — not this brief. |
| **C. Exception queue in this slice** | Same; ADR-027 hold on implementation. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ7 — Tenant and customer identity

| Option | What the owner would be choosing |
|---|---|
| **A. tid + external customer string (Recommended, Proposed)** | Follow DRG-662/631 for tenant; keep `QuoteAcceptedEvent.customerId` string as account key (same as subscriptions/assets today). Typed UUID customer key stays S-T2. Missing tid → NULL + metrics (DRG-662 AC), not a hard reject on the interim strangler unless owner tightens. |
| **B. Require CRM account UUID before activate** | Blocks on S-T2 resolution. |
| **C. Reject when tenant null** | Stronger than DRG-662 AC3; owner may choose for activation only. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ8 — `ContractExecuted` vs new entitlement event

| Option | What the owner would be choosing |
|---|---|
| **A. Keep ContractExecuted RESERVED; add EntitlementProvisioned after GO (Recommended, Proposed)** | BC-07-03 AC names `EntitlementProvisioned`. Do not overload `ContractExecuted` as the entitlement feed. Flip ContractExecuted only under ActivateContract GO. |
| **B. ContractExecuted is the entitlement signal** | Collapses BC-07 events; breaks reserved semantics and Cluster F non-decision. |
| **C. Emit both** | Allowed only after separate GOs for each contract. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ9 — Entitlement status model

| Option | What the owner would be choosing |
|---|---|
| **A. Minimal subset now; full set with DRG-558 (Recommended, Proposed)** | DRG-460 AC needs quantity, term, status. Name a minimal set in the future implementation brief after this Decision. Full PendingActive…Terminated waits on DRG-558 / Cluster F S1 owner close. |
| **B. Full set in DRG-460** | Couples 460 to In Review commitment frame. |
| **C. Reuse subscription status only** | Entitlement ≠ subscription (DRG-661); rejected as sole model. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ10 — ListEntitlements API

| Option | What the owner would be choosing |
|---|---|
| **A. CAM read API, account required, FE held (Recommended, Proposed)** | Satisfies ListEntitlements(account) on CAM. Role model can mirror assets' coarse read with the same rollout caveat. No Angular CPQ FE (AGENT.md hold). |
| **B. CRM-owned list** | Cross-BC ownership drift. |
| **C. Include FE in the same story** | Violates FE hold. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ11 — Holds and flags

| Option | What the owner would be choosing |
|---|---|
| **A. No flips (Recommended, Proposed)** | Leave `legacy-acceptance-write-enabled` off; leave dual-read and Jev holds untouched; no workflow edits. |
| **B. Enable legacy asset writer as activation stand-in** | Explicitly rejected by asset-write-hold / DRG-654 delivery hold. |
| **C. Flip dual-read or enable Jev** | Forbidden by ARCHITECTURE §17 / AGENT.md. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Stories | DRG-460 · DRG-469 |
| Artifact | Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ1–OQ11 |

Example shape once scored (illustrative only, **not** a claim): `DRG-460/469 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## What this Decision brief does not do

- Does not authorize an implementation brief or product code for DRG-460 or DRG-469.
- Does not mark any fork Adopted.
- Does not register or publish `EntitlementProvisioned`, `SubscriptionActivated`, ActivateSubscription command, or flip `ContractExecuted` to PUBLISHED.
- Does not create `contract_lines`, entitlement tables, gateway routes, or FE.
- Does not flip `opencrm.assets.legacy-acceptance-write-enabled`, dual-read, or any `opencrm.jev.*` flag.
- Does not mark Linear DRG-460 / DRG-469 Done.
- Does not merge or enable auto-merge on any PR.

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ1–OQ11. Until then, implementation briefs are blocked (**NO-GO** for product Java/TS).
2. **Event registration GO (OQ1):** HIGH-risk `EventContractRegistry` edits need explicit authorization.
3. **DRG-665 In Progress:** `contract_lines` absent on `main`; interim `accepted_line_uuid` only if OQ4-A stands.
4. **DRG-662 / DRG-631:** tenant on subscription / event population incomplete.
5. **DRG-458 Backlog:** GenerateContract preferred before full ActivateContract.
6. **DRG-558 In Review:** full commitment state set not closed.
7. **ADR-024 cutover:** order-service / ActivateSubscription command still unauthorized as code despite Accepted ADR text.
8. **Asset write hold:** legacy acceptance writer stays off; DRG-700 waits on real entitlements.
9. **FE hold:** no contract-asset FE from this wave.

## Sources read this pass

- Linear: DRG-460, DRG-469, DRG-661, DRG-665, DRG-662, DRG-458, DRG-558 (and scout comments on 460/469).
- Pattern: draft PR #803 / `story-drg-534-ramp-amendments-decision-brief.md` on `cursor/drg-534-decision-brief-4fa7`.
- ADRs: ADR-018, ADR-024, ADR-026; Cluster F fulfilment frame; DRG-654 asset design; asset-write-hold runbook.
- Code: `EventContractRegistry`, `KafkaTopics`, `AcceptedQuoteSubscriptionService`, `ErpSyncJob`, `Subscription`, `SubscriptionController`, `AssetActivation`, `EmptyEntitlementLookup`, CAM `application.yml`, `V1_7__contract_documents.sql`.
- GitNexus CLI (this checkout): impact `AcceptedQuoteSubscriptionService` (LOW), `EventContractRegistry` (HIGH), `AssetActivation` (LOW), `EmptyEntitlementLookup` (UNKNOWN → text-confirmed via `EntitlementLookup`); query activate flows.
- Holds: `AGENT.md`, `docs/ARCHITECTURE.md` §17.
