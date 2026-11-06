# DRG-458 — GenerateContract from accepted quote version: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** accept BC-07-01, amend ADR-024/025/026/035, authorize an implementation brief, authorize product code, flip a flag, register an event, or deploy. Every recommendation below is `Status: Proposed`. No row is Adopted. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** contract generation from accepted quote (BC-07-01). **Story:** [DRG-458](https://linear.app/drgamtd-workspace/issue/DRG-458/bc-07-01-generate-contract-from-an-accepted-quote-version) (GenerateContract · ContractGenerated · ContractLines). Parent epic: [DRG-288](https://linear.app/drgamtd-workspace/issue/DRG-288/epic-bc-07-contract-and-entitlement-from-accepted-quote-to-activated). Linear read this pass: **Backlog**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Taxonomy authority (Accepted ADR, stories still gated):** [DRG-661](https://linear.app/drgamtd-workspace/issue/DRG-661) Accepted (drg, 2026-10-29 14:11 Dublin). Canonical chain: accepted quote → contract (+ contract lines) → subscription, asset and entitlement. DRG-458 is the GenerateContract step of that chain. Dependent activation Decision brief: draft [#809](https://github.com/drgaciw/open-crm-cpq/pull/809) / `story-drg-460-469-activation-decision-brief.md` (Proposed; not on `main` yet).

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, registry entry, gateway route, or deploy.** An implementation brief for DRG-458 is **blocked** until CPQ Decision scores and the owner closes the forks below (or explicitly defers a named fork with words and a time). Holds in [AGENT.md](../../../AGENT.md) and [ARCHITECTURE.md §17](../../ARCHITECTURE.md) stay in force: no dual-read flip, no Jev enablement, no contract-asset FE work from this brief.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear issue named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `graph` | GitNexus CLI impact/query on **this** checkout. Prefer `node .gitnexus/run.cjs … --repo .` after analyze. |
| `Status: Proposed` | Recommendation or default only. Not adopted. Slot stays `Owner decision: ___ (drg, time)`. |

**GitNexus this pass:** MCP catalog empty (`gitnexus://repos` → no indexed repos); `.gitnexus/run.cjs` absent on this checkout. No `graph` claims below. Blast-radius notes for `EventContractRegistry` cite the activation brief's prior HIGH result and text confirmation on this SHA — treat as unresolved until re-indexed (`risk: UNKNOWN` handling).

## Context (why GenerateContract needs its own brief)

DRG-458 AC names three product outcomes that do not exist on `main` today:

1. Triggered by `QuoteAccepted` **via the Q2R saga**.
2. `ContractLines` reference **ConfigurationHash** and **PriceElements**.
3. **`ContractGenerated`** event is emitted.

What *does* exist is an interim strangler: Kafka `QuoteAccepted` → CAM `QuoteAcceptedEventConsumer` → `AcceptedQuoteSubscriptionService.apply` creates a PENDING subscription (and rate periods), not a GenerateContract command path, and not a ContractGenerated emission. Thin `contracts` headers exist for the amendment spine (ADR-035 / S1); enrichment was explicitly deferred to DRG-458. Draft [#805](https://github.com/drgaciw/open-crm-cpq/pull/805) (DRG-665) adds `contract_lines` **without** ConfigurationHash / PriceElements columns and leaves the QuoteAccepted path unchanged.

Scoring activation (DRG-460/469) without scoring generation would leave the origin chain's first product step undecided. Product AC for DRG-458 remains **NO-GO** until the forks below are closed.

### Related tickets (Linear read 2026-11-06)

| Ticket | Linear status | Role for this Decision brief |
|---|---|---|
| [DRG-288](https://linear.app/drgamtd-workspace/issue/DRG-288) | Backlog | BC-07 epic; exit criterion starts with "accepted quote generates a contract". |
| [DRG-661](https://linear.app/drgamtd-workspace/issue/DRG-661) | Backlog (ADR Accepted) | Taxonomy + origin chain Accepted; event registration GO (OQ5 there) still owner-gated. |
| [DRG-665](https://linear.app/drgamtd-workspace/issue/DRG-665) | In Progress | S-T4 `contract_lines` + nullable `subscriptions.contract_line_id`. Draft product PR [#805](https://github.com/drgaciw/open-crm-cpq/pull/805). Schema **does not** yet carry ConfigurationHash / PriceElements — OQ5 below. |
| [DRG-460](https://linear.app/drgamtd-workspace/issue/DRG-460) / [DRG-469](https://linear.app/drgamtd-workspace/issue/DRG-469) | Backlog | Activation Decision brief draft [#809](https://github.com/drgaciw/open-crm-cpq/pull/809). Prefers GenerateContract before full ActivateContract; does not authorize this story's product work. |
| [DRG-631](https://linear.app/drgamtd-workspace/issue/DRG-631) / [DRG-662](https://linear.app/drgamtd-workspace/issue/DRG-662) | related / In Progress | Tenant provenance for QuoteAccepted / subscriptions. |
| ADR-024 / ADR-025 / ADR-026 | **Accepted** as docs | Authorize **no** `order-service`, saga tables, command topics, or registry entries (`read` [docs/adr/README.md](../../adr/README.md)). Q2R saga remains design text. |
| ADR-035 OQ3 | Confirmed thin header | Richer contract header fields stay on DRG-458 (`read` ADR-035 acceptance checklist / S1 companion). |
| Flag `opencrm.assets.legacy-acceptance-write-enabled` | Default off | Stays off. Not an authorization surface for GenerateContract. |
| Flag `opencrm.erp-sync.enabled` | Default on | Not flipped here. ERP sync activates subscriptions; it is not ContractGenerated. |

**Verdict on product readiness:** No `GenerateContract` command, no Q2R saga runtime, no `ContractGenerated` registry entry/topic, no ConfigurationHash / PriceElements on contract lines (main or #805 draft), and QuoteAccepted does not create contract headers (tenant missing on that writer). Thin header + subscription strangler are interim. **Implementation briefs remain blocked on owner fork closure** and on residual blockers listed at the end.

## Current code facts (`read`)

Facts verified against `main` SHA above. Paths are relative to the repo root.

| Fact | Evidence |
|---|---|
| QuoteAccepted Kafka consumer creates subscriptions via apply; no GenerateContract | `QuoteAcceptedEventConsumer` → `AcceptedQuoteSubscriptionService.apply` (`…/event/QuoteAcceptedEventConsumer.java`, `…/service/AcceptedQuoteSubscriptionService.java`) |
| Apply path: NEW → PENDING subscription `SUB-<quoteId>`, rate periods, applied-quote ledger; mutate for AMENDMENT/EXPANSION/RENEWAL | `AcceptedQuoteSubscriptionService.apply` / `mutate` |
| Amendment routing flag: document path not built; legacy route only | `QuoteAcceptedEventConsumer.route` throws if not `LEGACY` |
| Thin `contracts` header exists; Javadoc: "Richer fields stay with DRG-458" | `ContractHeader.java`; `V1_7__contract_documents.sql` |
| Header-at-creation / backfill require authoritative tenant; QuoteAccepted NEW writer is **not** called from header creation (path has no tenant) | `ContractHeaderBackfill` Javadoc on `ensureHeaderAtCreation` |
| No `contract_lines` table on `main` | CAM migrations tip includes V1_15-class siblings; `contract_lines` only on draft #805 (`V1_16`) |
| Draft #805 `contract_lines` columns: id, tenant (from contract), contract_id, line_no, accepted_line_uuid, quote_id, product_*, quantity, status, effective_*, charge_type — **no** `configuration_hash`, **no** price-element payload | `V1_16__create_contract_lines.sql` on `cursor/drg-665-contract-lines-4fa7` |
| Draft #805 leaves QuoteAccepted path unchanged (guard test) | `ContractLineQuoteAcceptedPathUnchangedTest` on that branch |
| `QuoteAcceptedEvent.LineItemSnapshot` has priced scalars (`unitPrice`, `listPrice`, …) but **no** `configurationHash` and **no** `PriceElements` list | `QuoteAcceptedEvent.java` |
| Quote `ConfigurationSnapshot.committedVersion` is present; factory currently hard-codes `"v1"` | `QuoteAcceptedEvent.ConfigurationSnapshot`; `QuoteAcceptedKafkaEventFactory` |
| `ContractExecuted` is **RESERVED** (payload class exists; Outbox refuses until PUBLISHED) | `EventContractRegistry.CONTRACT_EXECUTED_V1`; `KafkaTopics.CONTRACT_EXECUTED` |
| No `ContractGenerated`, `GenerateContract`, or Q2R saga symbols in registry / KafkaTopics / CAM | `EventContractRegistry.java` ALL list; `KafkaTopics.java`; repo text search this pass |
| ADR-025 names Quote-to-Revenue saga reacting to QuoteAccepted and driving "contract (when policy requires)" | [ADR-025](../../adr/ADR-025-saga-process-manager.md) — Accepted as docs; no saga code |
| PriceElement is a pricing-service period output concept (ADR-023 / ramp REQ), not a CAM contract-line column today | [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md); `REQ-price-ramping.md` FR-05 |
| Amendment document lines already pin `configuration_hash` (64 hex) | `V1_7__contract_documents.sql` / V1_10 nullable-while-unpriced |

## AC gap map (code vs Linear AC)

| AC (Linear DRG-458) | On `main` today | Gap |
|---|---|---|
| Triggered by QuoteAccepted via the Q2R saga | Direct CAM Kafka consumer strangler; no saga runtime | OQ1 — trigger / saga vs strangler |
| ContractLines reference ConfigurationHash and PriceElements | No `contract_lines` on main; #805 draft lines lack both; event line snapshot lacks configurationHash / PriceElements | OQ5, OQ6 |
| ContractGenerated event is emitted | Absent from registry and topics; only `ContractExecuted` RESERVED | OQ2, OQ3 |

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Recommendations are Proposed.
2. CPQ Decision must score the forks. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work for DRG-458. **PRODUCT CODE FORBIDDEN** until Decision Accepted.
4. Even after forks close: no dual-read flip; no Jev flag enablement; no `legacy-acceptance-write-enabled` flip from this brief; no merge authority in this brief; DRG-665 / DRG-631 / DRG-662 / Q2R runtime remain residual product-path blockers to disclose on any later implementation brief.
5. First registration of `ContractGenerated` (or any GenerateContract command) in `EventContractRegistry` remains owner-gated (DRG-661 OQ5; ADR-026 "does not authorize any … topic or registry entry").

---

## Owner-decision table (summary)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ1 | Trigger path for GenerateContract v1 | CAM strangler on QuoteAccepted (same consumer / apply seam); treat ADR-025 Q2R saga as later cutover that *calls* GenerateContract | Ship Q2R saga + GenerateContract command in this story; dual-run saga and strangler | Yes — AC "via the Q2R saga" |
| OQ2 | Who may register `ContractGenerated` / GenerateContract | Owner (drg) explicit GO per contract; first wave: RESERVED + payload stub only after GO | CI auto-registers on any CAM PR; PUBLISHED + first producer in one PR without prior GO | Yes — ContractGenerated AC |
| OQ3 | `ContractGenerated` vs `ContractExecuted` | New `ContractGenerated` (emit on create); leave `ContractExecuted` RESERVED until Activate/Execute story GO | Overload ContractExecuted as create signal; emit both with shared payload | Yes — event identity |
| OQ4 | Header create on accept vs enrich-only | On accept (after tenant available): ensure thin header + link `subscriptions.contract_id`; richer enrichment fields are a named additive migration in the same story or a follow-on with explicit defer | Enrich-only (assume S7/backfill already linked); create header without linking subscription; wait for full enrichment before any header write | Soft for ContractGenerated; hard for chain integrity |
| OQ5 | ConfigurationHash / PriceElements on ContractLines | Additive columns/JSON on `contract_lines` (or sibling table) **coordinated with DRG-665**; hash = 64 lowercase hex pin (amendment-line rule); PriceElements = frozen priced facts (ADR-023 shape), not a live pricing call | Block DRG-458 until #805 merges then migrate; store only quote_id and re-read quote-service; omit PriceElements until ramp flag on | Yes — ContractLines AC |
| OQ6 | Provenance of ConfigurationHash / PriceElements at accept | Require fields on QuoteAccepted v1 (additive) before ContractLine open; fail closed if missing for NEW commercial lines | Compute interim hash in CAM from product + options; allow null hash for legacy aggregate lines only; pull from quote-service HTTP (rejected by ADR-017 for saga commands; reads still cost coupling) | Yes — AC referenceability |
| OQ7 | Quote version identity for GenerateContract(quoteVersion) | Business key = accepted quote id (`quoteId`); `committedVersion` / ramp `quoteVersion` recorded as provenance, not a second aggregate id | Require numeric quote_version as sole key; invent contract-version sequence at generate | Soft for v1; hard if multi-accept versions coexist |
| OQ8 | Idempotency / business key | Claim once on eventId/inbox **and** business key `(tenant_id, quote_id)` → one contract; lines keyed `(tenant_id, accepted_line_uuid)` (matches #805 unique); replay no-ops | Quote-only without tenant; contract_number only; per-line contracts | Yes — redelivery |
| OQ9 | Ordering vs subscription strangler | Contract (+ lines) **before** or **in the same transaction as** PENDING subscription create; subscription keeps `quote_id` provenance; `contract_line_id` link when #805 lands | Subscription-first (today); async contract after ERP activate; skip subscription until ActivateSubscription | Soft for 458 AC; hard for DRG-661 chain |
| OQ10 | Header enrichment field set (historical OQ3 remainder) | Defer non-AC enrichment (notice, legal terms, …) to a named follow-on; this Decision only requires fields needed to open lines + emit ContractGenerated | Bundle full enrichment into DRG-458; freeze enrichment forever at thin header | Soft for AC; hard if owner expects "richer fields" DoD in this story |
| OQ11 | Holds / flags / FE | No flag flips; FE held; no dual-read; no Jev enablement; no EventContractRegistry edit without OQ2 GO | Enable legacy asset writer; flip dual-read; enable Jev; register events drive-by | Yes if violated |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ1 — Trigger path (Q2R saga vs CAM strangler)

| Option | What the owner would be choosing |
|---|---|
| **A. CAM QuoteAccepted strangler for v1 (Recommended, Proposed)** | GenerateContract runs inside CAM when QuoteAccepted is consumed (extend `AcceptedQuoteSubscriptionService` / adjacent collaborator). ADR-025 Q2R saga remains Accepted design and later cutover that issues GenerateContract as a command. Matches how activation brief OQ3 keeps the subscription strangler. AC wording "via the Q2R saga" is satisfied later by cutover, or the owner amends AC to "QuoteAccepted (strangler until Q2R)". |
| **B. Q2R saga in this story** | Requires `order-service` / saga tables / command topics (ADR-024/025/026). Out of residual readiness; Accepted ADRs authorize no code. |
| **C. Dual-run** | Saga and strangler both generate. Highest drift; rejected unless owner demands parallel run. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Default until owner picks (Proposed):** No saga scaffolding; no GenerateContract command topic. `Owner decision: ___ (drg, time)`.

### OQ2 — Event / command registration authorization

| Option | What the owner would be choosing |
|---|---|
| **A. Owner GO per contract (Recommended, Proposed)** | drg authorizes `ContractGenerated` (and any GenerateContract command) registry/topic entries by words+time. Matches DRG-661 OQ5 and ADR-026. |
| **B. CI auto-register on any CAM merge** | Fitness tests alone gate registration. Rejected by taxonomy gating. |
| **C. PUBLISHED + first producer in one PR** | Allowed only after A's GO names that contract. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

**Blast radius note:** Prior activation brief recorded `EventContractRegistry` impact as **HIGH**. This checkout could not re-run GitNexus; treat registration PRs as separately scored.

### OQ3 — `ContractGenerated` vs `ContractExecuted`

| Option | What the owner would be choosing |
|---|---|
| **A. New ContractGenerated on create (Recommended, Proposed)** | BC-07-01 AC names ContractGenerated. ContractExecuted stays RESERVED until Activate/Execute GO (activation brief OQ8). |
| **B. ContractExecuted is the create signal** | Collapses create and execute; breaks RESERVED semantics. |
| **C. Emit both** | Allowed only after separate GOs for each contract. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ4 — Contract header on accept

| Option | What the owner would be choosing |
|---|---|
| **A. Ensure thin header + link on accept (Recommended, Proposed)** | Once tenant is on the event (DRG-631/662), QuoteAccepted NEW path calls header-at-creation (or equivalent) and sets `subscriptions.contract_id`. Uses existing thin shape; does not invent enrichment columns unless OQ10 expands scope. |
| **B. Enrich-only** | Assume every subscription already has a header (S7). False for live accept today. |
| **C. Block header writes until full enrichment** | Reopens the spike risk ADR-035 already mitigated by thin header now. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ5 — ConfigurationHash / PriceElements storage

| Option | What the owner would be choosing |
|---|---|
| **A. Additive contract_lines storage coordinated with DRG-665 (Recommended, Proposed)** | Extend #805 schema (or a follow-on migration on the free CAM slot after it) with configuration_hash (64 hex) and a PriceElements representation (JSONB of ADR-023 PriceElement facts or normalized child rows). Do not silently redefine #805 columns. |
| **B. Block 458 product until #805 merges, then migrate** | Decision can still score now; implementation brief waits. |
| **C. Store quote_id only; rehydrate from quote-service** | Fails "immutable quote snapshot" intent; couples CAM reads. |

**Recommended (Proposed):** A for Decision; B remains a residual **implementation** gate. `Owner decision: ___ (drg, time)`.

### OQ6 — Provenance at accept time

| Option | What the owner would be choosing |
|---|---|
| **A. Additive QuoteAccepted fields; fail closed (Recommended, Proposed)** | Carry configurationHash and priced PriceElements (or equivalent frozen facts) on line snapshots before ContractLine open. Legacy aggregate path may be exempt only if owner names it. |
| **B. CAM computes interim hash** | Matches amendment INTERIM pin formula; still needs priced facts for PriceElements. |
| **C. Null hash allowed broadly** | Fails AC "reference ConfigurationHash". |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ7 — quoteVersion identity

| Option | What the owner would be choosing |
|---|---|
| **A. quoteId is GenerateContract key (Recommended, Proposed)** | Linear title says quote version; platform identity today is `quoteId` on QuoteAccepted. Record `committedVersion` / authoring version as provenance columns. |
| **B. Numeric quote_version sole key** | Needs quote-service versioning product work first. |
| **C. New contract_version sequence** | Extra identity to migrate; avoid for v1. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ8 — Idempotency

| Option | What the owner would be choosing |
|---|---|
| **A. Inbox eventId + (tenant, quote_id) contract + (tenant, accepted_line_uuid) lines (Recommended, Proposed)** | Aligns with KafkaInbox on the consumer and #805 unique constraints. Replay returns existing contract. |
| **B. Quote id only** | Weak under multi-tenant. |
| **C. contract_number only** | Number allocation races need a separate rule. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ9 — Ordering vs subscription create

| Option | What the owner would be choosing |
|---|---|
| **A. Same transaction: contract/lines then PENDING subscription (Recommended, Proposed)** | Preserves DRG-661 chain without waiting for ActivateSubscription. ERP activate stays downstream. |
| **B. Keep subscription-first** | Continues interim; contract becomes async backfill — weakens AC. |
| **C. Suppress subscription until ActivateSubscription** | Couples 458 to 469/460 Decision + product; larger blast. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ10 — Header enrichment scope

| Option | What the owner would be choosing |
|---|---|
| **A. Defer non-AC enrichment (Recommended, Proposed)** | Historical "richer fields" (notice windows, legal terms, …) stay a follow-on. This story's Decision covers generate + lines + ContractGenerated only. |
| **B. Full enrichment in DRG-458** | Expands schema blast; list fields in implementation brief after Decision. |
| **C. Never enrich beyond thin header** | Contradicts ADR-035 OQ3 deferral text unless owner reverses it. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ11 — Holds and flags

| Option | What the owner would be choosing |
|---|---|
| **A. No flips (Recommended, Proposed)** | Leave asset legacy writer off; dual-read and Jev holds untouched; no FE. |
| **B. Enable legacy asset writer as stand-in** | Rejected by asset-write-hold. |
| **C. Flip dual-read or enable Jev** | Forbidden by ARCHITECTURE §17 / AGENT.md. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Story | DRG-458 |
| Artifact | Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ1–OQ11 |

Example shape once scored (illustrative only, **not** a claim): `DRG-458 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## What this Decision brief does not do

- Does not authorize an implementation brief or product code for DRG-458 (**NO-GO** / PRODUCT CODE FORBIDDEN until Decision Accepted).
- Does not mark any fork Adopted.
- Does not register or publish `ContractGenerated`, GenerateContract command, or flip `ContractExecuted` to PUBLISHED.
- Does not create or alter `contract_lines`, enrich `contracts`, or change QuoteAccepted consumers.
- Does not scaffold Q2R saga / `order-service`.
- Does not flip `opencrm.assets.legacy-acceptance-write-enabled`, dual-read, or any `opencrm.jev.*` flag.
- Does not mark Linear DRG-458 Done.
- Does not merge or enable auto-merge on any PR.
- Does not supersede draft [#805](https://github.com/drgaciw/open-crm-cpq/pull/805) or [#809](https://github.com/drgaciw/open-crm-cpq/pull/809).

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ1–OQ11. Until then, implementation briefs are blocked (**NO-GO** for product Java/TS).
2. **Event registration GO (OQ2):** `ContractGenerated` needs explicit authorization before registry edits.
3. **DRG-665 In Progress (#805):** `contract_lines` absent on `main`; ConfigurationHash / PriceElements not in that draft schema (OQ5).
4. **Q2R saga runtime absent:** ADR-025 Accepted as docs only; AC saga wording needs OQ1 owner close or AC amend.
5. **DRG-631 / DRG-662:** tenant on QuoteAccepted / subscriptions incomplete — blocks OQ4-A header-at-creation on the accept path.
6. **QuoteAccepted line snapshot gaps:** no configurationHash / PriceElements today (OQ6).
7. **Activation Decision (#809) still Proposed:** GenerateContract preferred before ActivateContract; does not unlock 458 product work by itself.
8. **FE hold:** no contract-asset FE from this wave.
9. **GitNexus:** re-index this checkout before any product PR claims graph-clean impact on registry or apply path.

## Sources read this pass

- Linear: DRG-458, DRG-288, DRG-665, DRG-460, DRG-469 (and related statuses on 661/662).
- Pattern: draft PR [#809](https://github.com/drgaciw/open-crm-cpq/pull/809) activation Decision brief; draft PR [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) ramp Decision brief; draft PR [#805](https://github.com/drgaciw/open-crm-cpq/pull/805) contract_lines.
- ADRs: ADR-018, ADR-023, ADR-024, ADR-025, ADR-026, ADR-035; adr README status table; S1 companion / OQ3 thin header.
- Code: `QuoteAcceptedEventConsumer`, `AcceptedQuoteSubscriptionService`, `ContractHeader`, `ContractHeaderBackfill`, `EventContractRegistry`, `KafkaTopics`, `QuoteAcceptedEvent`, `QuoteAcceptedKafkaEventFactory`, `V1_7__contract_documents.sql`; #805 `V1_16__create_contract_lines.sql` / `ContractLine` / `ContractLineOpenService`.
- GitNexus: unavailable this checkout (empty MCP index; no `.gitnexus/run.cjs`).
- Holds: `AGENT.md`, `docs/ARCHITECTURE.md` §17.
