# DRG-584 — S6 Amendment event contract (`QuoteAcceptedEvent` fields) and per-tenant legacy gating: Decision brief (owner forks)

**Status:** Proposal only. Wave-2 residual Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted OQ5 option A / OQ6 / OQ7 / OQ10 / OQ18 cells, does **not** adopt open OQs from in-flight Wave-2 drafts [#809](https://github.com/drgaciw/open-crm-cpq/pull/809) (DRG-460), [#841](https://github.com/drgaciw/open-crm-cpq/pull/841) (DRG-562), or [#807](https://github.com/drgaciw/open-crm-cpq/pull/807) (DRG-568), does **not** authorize S6 product completion beyond the narrow #683 flag-off seam, does **not** register new Kafka consumers or event types into `EventContractRegistry` / `KafkaTopics`, does **not** flip `opencrm.amendment.routing.document-path-tenants`, open a Flyway migration (CAM `V1_16`–`V1_21` stay reserved for [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), authorize product code, enable dual-read, enable Jev, enable ramp, or deploy. Every residual fork below is `Status: Proposed` and labeled **Proposed OQ-584-***. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / S6 event contract (RA-50). **Story:** [DRG-584](https://linear.app/drgamtd-workspace/issue/DRG-584/s6-amendment-event-contract-quoteacceptedevent-fields-and-per-tenant) (RA-50). Linear read this pass: **In Review**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen closed cells):**

| Artifact | Role |
|---|---|
| [`amendment-first-slice-requirements-and-acceptance.md`](../amendment-first-slice-requirements-and-acceptance.md) | FR-16; AC-S6-1..4. Requirements authority; story still owner-gated for product Done. |
| [`amendment-technical-approach-and-dependencies.md`](../amendment-technical-approach-and-dependencies.md) | Risks R3 (double apply), R4 (digest drift); S6 owns `QuoteAcceptedEvent` fields + amendment event schemas. |
| [`story-s3-s4-authorization-gate.md`](story-s3-s4-authorization-gate.md) | S3–S8 remain implementation-gated; S6 remains owner-authorized separately. |
| [`renewals-amendments-open-questions-register.md`](../renewals-amendments-open-questions-register.md) | **OQ11** still `DEFAULT PROPOSED` (per-tenant flag, default off). **OQ18** Confirmed under delegation — cite; do **not** reopen. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) | D-DIGEST / CAM-COMMERCIAL-1; S6 reuses, does not fork. Cite; do not reopen. |
| Merged [#683](https://github.com/drgaciw/open-crm-cpq/pull/683) | Narrow flag-off seam only. AC-S6-2 (flag on → document path) **unmet**. Linear left **In Review**, not Done. |

**In-flight alignment (cite only — do not adopt open OQs):**

| Ticket / artifact | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-460](https://linear.app/drgamtd-workspace/issue/DRG-460) activation Decision brief | Draft [#809](https://github.com/drgaciw/open-crm-cpq/pull/809) | Event-registry authorization, ContractExecuted / entitlement ordering. **Cite only.** Do **not** adopt its open Proposed OQ1–OQ11. |
| [DRG-562](https://linear.app/drgamtd-workspace/issue/DRG-562) executed-apply gate residual | Draft [#841](https://github.com/drgaciw/open-crm-cpq/pull/841) | OQ-562-7 legacy path + routing flag; executed-state gate. **Cite only.** Do **not** adopt its open Proposed OQ-562-*. |
| [DRG-568](https://linear.app/drgamtd-workspace/issue/DRG-568) renewal escalators / locks | Draft [#807](https://github.com/drgaciw/open-crm-cpq/pull/807) | OF-568-* / ramp flag. **Cite only.** Do **not** adopt its open forks; do not flip `opencrm.pricing.ramp.enabled`. |
| [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) (S4) | In Review | Document-driven apply. Remains **owner-gated**. This brief does **not** authorize S4 or document-path apply. |
| [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) (S3) | Related | Document API / EXECUTED transitions. This brief does not launch S3. |
| [DRG-563](https://linear.app/drgamtd-workspace/issue/DRG-563) | Related | Multi-sub ledger. Cite; do not adopt residual OQs. |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, `EventContractRegistry` entry, Kafka consumer registration, gateway route, dual-read flip, Jev enablement, ramp enable, S4 apply, or deploy.** CAM Flyway `V1_16`–`V1_21` remain reserved (#805–#812). An implementation brief that registers lifecycle types as PUBLISHED, flips `opencrm.amendment.routing.document-path-tenants`, adds a tenant field and turns document-path routing on, or treats S6 as Done is **blocked** until CPQ Decision scores and the owner closes the Proposed OQ-584-* forks below (or explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted` | Already selected outside this file (delegated record / OQ5 A / OQ6 / OQ18 / …). Restated; **not** re-opened. |
| `#683 landed` | Behaviour merged by [#683](https://github.com/drgaciw/open-crm-cpq/pull/683). Narrow seam only; not S6 Done. |
| `Status: Proposed` | Residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |

## Gate restatement (RA-50 / FR-16)

**Target (requirements):** optional `amendmentDocumentId` and per-line `applicationUnitNo` on `QuoteAcceptedEvent`; CAM routes document-bearing events to the document path and others to legacy behind a **per-tenant** flag (default off); eight amendment lifecycle events each have a registered schema and a contract test (AC-S6-1..4).

**Today after #683 (`Linear read` + `verified by reading code`):** additive optional fields exist; eight lifecycle DTOs live in `AmendmentLifecycleEventCatalog` as **RESERVED** (not in `EventContractRegistry`); CAM routing seam always selects `LEGACY` because the event carries **no tenant**; property `opencrm.amendment.routing.document-path-tenants` defaults empty / OFF; no document is loaded; document-path decision enum value does not exist yet (`AmendmentRoutingDecision` contains only `LEGACY`).

**Hard NO-GO from this brief:** do **not** register new Kafka consumers/event types, flip the routing flag, or authorize product code that claims AC-S6-2 / AC-S6-4 Done.

## Why this residual Decision brief exists

[#683](https://github.com/drgaciw/open-crm-cpq/pull/683) merged the tenant-independent flag-off seam (`Linear read` 2026-10-18: merge `96432257…`, head `dbfb65be…`). Linear comments corrected status to **In Review**: AC-S6-2 unmet, no tenant source, catalog-only “registered” interpretation for AC-S6-4 under ruling B1-A. Wave-2 refill (`Linear read` 2026-11-06) assigns this docs-only residual brief to inventory still-open **DRG-584** forks as **Proposed OQ-584-*** for CPQ Decision — without adopting open OQs from #809 / #841 / #807, and without authorizing S6 product completion.

## What #683 already landed (cite — not S6 Done)

Facts `verified by reading code` at the base SHA. They show direction. They do **not** close Proposed OQ-584-* and do **not** authorize flag-on routing or registry publication.

| Fact | Evidence |
|---|---|
| Optional `amendmentDocumentId` (String) on `QuoteAcceptedEvent`; optional per-line `applicationUnitNo` (Integer) on `LineItemSnapshot`; `@JsonInclude(NON_NULL)`; `EVENT_TYPE` / `EVENT_VERSION` stay v1; **no tenant** | `QuoteAcceptedEvent.java` |
| Eight lifecycle DTO records + `AmendmentLifecycleEventCatalog` (8 entries, all `Publication.RESERVED`) | `AmendmentLifecycleEventCatalog.java` |
| Catalog explicitly **not** in `EventContractRegistry`; no Kafka topic / producer for the eight types | Catalog javadoc; `EventContractRegistry` has `AmendmentAuditRecorded` only (topic `cpq.amendment.document.audit`) |
| CAM routing seam: after inbox claim + parse, flag off **or** no event tenant → legacy `acceptedQuotes.apply` exactly once; `decide()` always `LEGACY` | `AmendmentRoutingFlag.java`, `QuoteAcceptedEventConsumer.java` |
| Property `opencrm.amendment.routing.document-path-tenants` (CSV, default empty); not in `application*.yml` | `AmendmentRoutingFlag.PROPERTY` |
| `AmendmentRoutingDecision` enum values: **`LEGACY` only** | `AmendmentRoutingDecision.java` |
| Alert counter `document_path_selected` registered and must stay at zero | `AmendmentRoutingFlag.DOCUMENT_PATH_SELECTED` |

## Landed evidence vs unmet ACs

| AC | Status after #683 | Residual fork |
|---|---|---|
| AC-S6-1 (compat both directions) | Additive fields + `@JsonIgnoreProperties(ignoreUnknown = true)` precedent; fixture ownership / both-direction pack still open for product Done | OQ-584-8 |
| AC-S6-2 (flag on → document path; flag off → legacy; never both) | **Unmet** — always legacy; no tenant; no `DOCUMENT` decision | OQ-584-1, OQ-584-2, OQ-584-3, OQ-584-4 |
| AC-S6-3 (in-flight legacy ACCEPTED at cutover → legacy + inbox dedupe) | Legacy path + inbox exist; cutover policy still owner-scored | OQ-584-5 |
| AC-S6-4 (schema + contract test per lifecycle event) | Catalog + DTO + tests under B1-A “registered”; **not** `EventContractRegistry` PUBLISHED | OQ-584-6, OQ-584-7 |

GitNexus MCP reports **no indexed repositories** usable for this pass / `.gitnexus/run.cjs` may be absent. Impact / `detect_changes` are therefore **UNKNOWN / unavailable** — not a clean blast-radius check. Code claims above are text/`rg` evidence only.

## Implementation brief gate (explicit) — product / registry / flag NO-GO

1. This Decision brief closes **nothing** by itself. Options are Proposed.
2. CPQ Decision must score the forks. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work for document-path routing, `EventContractRegistry` / `KafkaTopics` registration of the eight lifecycle types, new Kafka consumers, CAM Flyway (including any use of reserved `V1_16`–`V1_21`), dual-read flips, Jev flags, or ramp changes for DRG-584.
4. Even after forks close: S3 / S4 still need their **own** owner authorization. This brief is not that record. Landed #683 is not S6 Done.
5. Citing #809 / #841 / #807 does **not** adopt their open OQs. Citing OQ18 does **not** reopen OQ18. Choosing a tenant source does **not** flip `opencrm.amendment.routing.document-path-tenants`.

---

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No Adopted cells in this table.**

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product / registry / flag? |
|---|---|---|---|---|
| **OQ-584-1** | **Tenant identity source** for per-tenant routing (prerequisite for AC-S6-2) | Additive optional `tenantId` (or equivalent) on `QuoteAcceptedEvent` (or envelope), null-safe, ignored by old consumers; factory fills from quote/account tenant when known | Derive tenant only inside CAM from quote/customer lookup; route by account id instead of tenant; defer document path until a separate tenant-source story Done | Yes — without a source, `decide()` stays forever LEGACY |
| **OQ-584-2** | **OQ11 flag granularity** | Affirm register default: CSV allow-list property `opencrm.amendment.routing.document-path-tenants`, default empty / off; internal tenant first when owner enables | Global boolean flag; per-environment only; per-customer (not tenant) allow-list | Yes — Platform gate “before S6” on register |
| **OQ-584-3** | **When document-path may turn on** | Flag stays empty until S4 document apply + executed-state gate are owner-authorized **and** OQ-584-1 closes; enable one internal tenant first under a separate enablement record | Enable tenants as soon as fields land; couple enablement to S6 merge | Yes — flag flip / dual-path |
| **OQ-584-4** | **Exclusive routing + malformed id** | Never both paths for one acceptance; document id present + tenant on allow-list → document path only; flag off / unknown tenant / absent id → legacy only; malformed / unparseable document id → legacy once + metric (no document load) until document path exists | Fail closed (DLT) on malformed id when flag on; run both paths “for safety” | Yes — R3 double-apply |
| **OQ-584-5** | **In-flight legacy ACCEPTED at cutover (AC-S6-3)** | Events without `amendmentDocumentId` always legacy; Kafka inbox dedupe unchanged; no backfill rewrite of in-flight accepts | Force-route in-flight accepts to document path; drain/replay before any tenant enablement | Soft until cutover; hard for cutover runbook |
| **OQ-584-6** | **AC-S6-4 “registered” meaning** | Keep B1-A for first slice: catalog entry + DTO + contract test = AC-S6-4 met; **`EventContractRegistry` / `KafkaTopics` / consumers stay untouched** until a later owner GO (separate from this residual) | Promote all eight to PUBLISHED + topics now; register only Executed/Applied subset | Yes — registry / topic / fitness tests |
| **OQ-584-7** | **Lifecycle emission ownership** | No producer in S6 residual; CAM local audit / existing `AmendmentAuditRecorded` stay; domain lifecycle bus emission waits owner GO after S3/S4 | Quote-service emits lifecycle; CAM outbox emits on every status transition in this story | Yes if emission chosen now — consumers + outbox |
| **OQ-584-8** | **AC-S6-1 fixture pack ownership** | Both directions required for Done: (a) old consumer/parser fixture fed extended event; (b) current consumer vs `accepted-v1/*.json`. Pack owned under CAM/common tests; no new topic | Only one direction; share fixtures with DRG-460 activation pack | Soft for docs; hard for S6 Done claim |
| **OQ-584-9** | **Digest reuse (OQ18) for S6** | Affirm Adopted OQ18: S6 reuses shared `backend/libs/common` CAM-COMMERCIAL-1 utility delivered with S3; **no CAM-local fork**; S6 does not invent a second digest library | CAM-local interim hasher; S6-owned digest format | Yes if B/C — would reopen OQ18 |
| **OQ-584-10** | **Coupling to Wave-2 drafts / S4 gate** | Cite #809 / #841 / #807 only; document-path apply still requires S4 + executed gate (DRG-562 OQ-562-*); do **not** adopt their open OQs here | Block all OQ-584 scoring until those PRs merge; adopt their OQs by reference | Soft for scoring; hard if product brief tries to enable document path without S4 |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-584-1 — Tenant identity source

| Option | What the owner would be choosing |
|---|---|
| **A. Additive event/envelope tenant field (Recommended, Proposed)** | Matches #683 follow-up note (“Option (a), a tenant source on `Quote` or `QuoteAcceptedEvent`, is a **separate prerequisite story, not started**”). Null-by-default; old consumers ignore. Factory fills when known. Enables `AmendmentRoutingFlag.decide` to consult the allow-list. |
| **B. CAM-side lookup only** | Keeps event unchanged; CAM resolves tenant from quote/customer before `decide`. Extra sync dependency inside the listener path. |
| **C. Defer document path until a dedicated tenant-source story** | Leaves AC-S6-2 unmet; #683 seam remains the only landed S6 slice. |

**Recommended (Proposed):** A. Does **not** flip the flag or add `DOCUMENT` routing in this brief. `Owner decision: ___ (drg, time)`.

### OQ-584-2 — OQ11 flag granularity

| Option | What the owner would be choosing |
|---|---|
| **A. Affirm per-tenant CSV allow-list, default empty (Recommended, Proposed)** | Matches register OQ11 default and landed `AmendmentRoutingFlag.PROPERTY`. Internal tenant first when later enabled. |
| **B. Global boolean** | Coarser blast radius; contradicts register default. |
| **C. Per-customer allow-list** | Different grain than tenant; needs a new property and identity story. |

**Recommended (Proposed):** A. **Does not reopen other register cells.** `Owner decision: ___ (drg, time)`.

### OQ-584-3 — When document-path may turn on

| Option | What the owner would be choosing |
|---|---|
| **A. After S4 + executed gate authorized and OQ-584-1 closed; internal tenant first (Recommended, Proposed)** | Aligns with #841 OQ-562-7 cite-only (legacy unchanged / tenants empty until later authorized S6/S4 record). Separate enablement record required; this brief is not it. |
| **B. Enable as soon as fields land** | Flag-on without document apply → double-apply / empty document path risk (R3). |
| **C. Couple enablement to S6 merge** | Treats docs/scoring as authorization. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-584-4 — Exclusive routing + malformed document id

| Option | What the owner would be choosing |
|---|---|
| **A. Never both; malformed → legacy once + metric until document path exists (Recommended, Proposed)** | Pins R3 mitigation. Matches #683 R2 pinning note that real routing tests replace the ignore-input stubs later. |
| **B. Fail closed (DLT) on malformed id when flag on** | Stronger; needs DLT runbook and consumer change. |
| **C. Run both paths** | Explicitly forbidden by AC-S6-2 (“never both”). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-584-5 — In-flight legacy ACCEPTED at cutover

| Option | What the owner would be choosing |
|---|---|
| **A. Absent document id → legacy + existing inbox dedupe (Recommended, Proposed)** | AC-S6-3. No rewrite of in-flight accepts. |
| **B. Force document path for in-flight accepts** | Requires document ids that do not exist on legacy accepts. |
| **C. Drain before any tenant enablement** | Sequencing preference; compatible with A as a runbook step. |

**Recommended (Proposed):** A (C allowed as operational add-on, not a substitute gate). `Owner decision: ___ (drg, time)`.

### OQ-584-6 — AC-S6-4 “registered” meaning

| Option | What the owner would be choosing |
|---|---|
| **A. Keep B1-A catalog+DTO+contract test; registry untouched (Recommended, Proposed)** | Matches #683 owner ruling. Hard NO-GO on new `EventContractRegistry` / `KafkaTopics` / consumer registration from this residual. Fitness tests stay green without amendment lifecycle PUBLISHED entries. |
| **B. Promote all eight to PUBLISHED + topics now** | Full bus contract; needs topics, producers, consumers, fitness updates — product authorization, not this brief. |
| **C. Publish subset (Executed / Applied / …)** | Partial registry; still product + still NO-GO here. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-584-7 — Lifecycle emission ownership

| Option | What the owner would be choosing |
|---|---|
| **A. No producer in this residual; keep local / audit path (Recommended, Proposed)** | Catalog stays RESERVED. Existing `AmendmentAuditRecorded` on `cpq.amendment.document.audit` is unrelated domain-lifecycle publication. |
| **B. Quote-service emits lifecycle** | Cross-service outbox; couples to S5. |
| **C. CAM outbox emits on every status transition in S6** | Requires S3 state machine + registry GO. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-584-8 — AC-S6-1 fixture pack

| Option | What the owner would be choosing |
|---|---|
| **A. Both directions under CAM/common tests (Recommended, Proposed)** | Requirements wording. Uses existing `accepted-v1/*.json` plus an extended-event → old-parser case. |
| **B. One direction only** | Incomplete vs AC-S6-1. |
| **C. Share pack with DRG-460 activation fixtures** | Cite #809 only; do not adopt DRG-460 OQs or mix activation registry GO into S6. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-584-9 — Digest reuse (OQ18)

| Option | What the owner would be choosing |
|---|---|
| **A. Affirm Adopted OQ18 shared utility (Recommended, Proposed)** | S3 delivers; S5/S6 reuse; no CAM-local fork. Technical approach R4 (digest drift) stays a test obligation, not a new library. |
| **B. CAM-local interim hasher** | Rejected by delegated record. |
| **C. S6-owned digest format** | Reopens OQ18. |

**Recommended (Proposed):** A. **Cite Adopted OQ18; do not reopen.** `Owner decision: ___ (drg, time)` only if the owner intends to supersede OQ18 (out of ordinary residual scoring).

### OQ-584-10 — Coupling to Wave-2 drafts / S4 gate

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #809 / #841 / #807; keep S4/executed-gate as separate owner gates (Recommended, Proposed)** | Document-path routing without S4 apply is not a complete FR-16 path. Do **not** adopt open OQs from those drafts. |
| **B. Block OQ-584 scoring until those PRs merge** | Process preference only. |
| **C. Adopt their OQs by reference** | Out of scope; would smuggle foreign forks into S6. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Relation map: Wave-2 drafts and S3/S4 (cite only)

| External item | Relation to this brief | What this brief must not do |
|---|---|---|
| #809 DRG-460/469 activation Decision brief | Event-registry authorization patterns; activation chain | Adopt OQ1–OQ11; register activation or S6 lifecycle types |
| #841 DRG-562 executed-apply residual | OQ-562-7 legacy path + flag; EXECUTED gate | Adopt OQ-562-*; authorize S4 / DRG-625 |
| #807 DRG-568 escalators/locks | Adjacent renewals pricing | Adopt OF-568-*; flip ramp flag |
| S4 / DRG-582 | Consumer of document-path routing | Authorize product landing or mark S4 Done |
| S3 / DRG-581 | Produces document statuses the catalog maps | Launch S3; invent OUT_FOR_SIGNATURE / REJECTED / VOID lifecycle events (catalog explicitly omits them) |
| OQ11 register cell | Flag granularity | Flip flag; claim OQ11 Adopted without owner words |
| OQ18 / D-DIGEST | Shared digest utility | Reopen; add CAM-local fork |
| CAM `V1_16`–`V1_21` (#805–#812) | Reserved Flyway range | Claim those versions; write migrations |

---

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Story | DRG-584 (RA-50 / S6) |
| Artifact | Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ-584-1..OQ-584-10 |

Example shape once scored (illustrative only, **not** a claim): `DRG-584 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## What this Decision brief does not do

- Does not authorize an implementation brief or product code for document-path routing, lifecycle PUBLISHED registration, or new Kafka consumers.
- Does not mark any fork Adopted; does not fill any `Owner decision: ___ (drg, time)` slot.
- Does not reopen Adopted OQ5 A / OQ6 / OQ7 / OQ10 / OQ18.
- Does not adopt in-flight open OQs from #809 (DRG-460), #841 (DRG-562), or #807 (DRG-568).
- Does not claim AC-S6-2 or full AC-S6-4 registry publication Done; #683 remains the narrow flag-off seam.
- Does not flip `opencrm.amendment.routing.document-path-tenants` or add a `DOCUMENT` routing decision.
- Does not write CAM Flyway (reserved `V1_16`–`V1_21` untouched).
- Does not flip dual-read, Jev, or ramp.
- Does not authorize S3 / S4 / S5 / S8 product landing.
- Does not mark Linear DRG-584 Done (status stays **In Review**).
- Does not merge or enable auto-merge on any PR.

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close **Proposed OQ-584-1..OQ-584-10**. Until then, implementation brief is blocked (**NO-GO** for product Java/TS / registry / flag-on for S6 document path).
2. **Tenant source story:** still not started (#683 follow-up); blocks AC-S6-2.
3. **S4 / DRG-582 + DRG-562 residual:** document-path apply / executed gate still owner-gated; cite #841 only.
4. **S3 / DRG-581:** document status machine / EXECUTED still S3-owned.
5. **OQ11:** remains DEFAULT PROPOSED on the register until owner words close OQ-584-2.
6. **CAM Flyway `V1_16`–`V1_21`:** reserved (#805–#812); this brief writes none.
7. **AGENT.md / ARCHITECTURE §17 holds:** dual-read, NgRx, Jev enablement, ramp, asset-write — untouched.
8. **GitNexus:** index unavailable / UNKNOWN this pass → impact **UNKNOWN** (not clean).

## Sources read this pass

- Linear: DRG-584 (description, attachments #683 / #621, comments on #683 In Review / AC-S6-2 unmet).
- Requirements: `amendment-first-slice-requirements-and-acceptance.md` (FR-16, AC-S6-1..4).
- Technical approach: `amendment-technical-approach-and-dependencies.md` (R3, R4).
- Gate: `story-s3-s4-authorization-gate.md`.
- Register: OQ11 DEFAULT PROPOSED; OQ18 Confirmed under delegation.
- Delegation: `delegated-renewals-amendments-decisions-2026-10-17.md` (D-DIGEST).
- Merged #683 PR body + Linear comments (flag-off seam; B1-A registry interpretation).
- Code (`verified by reading code` at base SHA): `QuoteAcceptedEvent.java`; `AmendmentLifecycleEventCatalog.java`; `AmendmentRoutingFlag.java`; `AmendmentRoutingDecision.java`; `QuoteAcceptedEventConsumer.java`; `EventContractRegistry` / `KafkaTopics` (audit topic only for amendments).
- Wave-2 cite-only drafts: #809 (DRG-460), #841 (DRG-562), #807 (DRG-568).
- Holds: `AGENT.md`, `docs/ARCHITECTURE.md` §17.
