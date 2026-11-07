# DRG-625 — S4 apply endpoint / flag enablement (owner gate): residual Decision brief

**Status:** Proposal only. Wave-2 residual Decision brief for CPQ Decision scoring. This file does **not** enable the S4 apply endpoint, does **not** flip `opencrm.amendment.routing.document-path-tenants` or any other S4 flag, does **not** authorize S4 / [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) product Done, does **not** adopt open OQs from cite-only [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) (DRG-582 residual), [#841](https://github.com/drgaciw/open-crm-cpq/pull/841) (DRG-562), or [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) (DRG-565), does **not** reopen Adopted OQ5 option A / OQ6 / OQ7 / OQ10 / OQ18, does **not** flip dual-read, Jev, ramp, NgRx, or asset-write holds, does **not** mark Linear Done, and does **not** open a Flyway migration (CAM `V1_16`–`V1_21` stay reserved for [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)). Every residual fork below is `Status: Proposed` and labeled **Proposed OQ-625-***. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / S4 enablement owner gate. **Story:** [DRG-625](https://linear.app/drgamtd-workspace/issue/DRG-625/s4-apply-endpointflag-enablement-owner-gate). Linear read this pass: **Backlog**. This brief leaves that status unchanged — do **not** invent Done.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

## Explicit S4 / DRG-625 / dual-read hold (NO-GO)

| Hold | Status in this brief |
|---|---|
| **DRG-625 apply endpoint / flag enablement** | **NO-GO.** This is the owner enablement gate itself. Docs merge ≠ enablement. |
| **S4 / DRG-582 product Done** | **NO-GO.** Landed flag-off orchestrator / ledger from [#685](https://github.com/drgaciw/open-crm-cpq/pull/685) is **not** Done and is **not** authorization to call apply in product or flip routing tenants. Residual delivery forks live on [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) (**cite only**). |
| **Dual-read / ADR-005** | **NO-GO.** `approvals.legacy-local-read-enabled` / `APPROVALS_LEGACY_LOCAL_READ` stay as held in [AGENT.md](../../../AGENT.md) and [docs/ARCHITECTURE.md](../../ARCHITECTURE.md) §17. Untouched. |
| **Asset-write / Jev / ramp / NgRx** | **NO-GO.** AGENT.md / ARCHITECTURE holds untouched. |
| **CAM Flyway `V1_16`–`V1_21`** | **RESERVED** (#805–#812). This brief writes **no** migration and does not consume those slots. |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, dual-read flip, Jev enablement, ramp enable, S4 apply endpoint enablement, asset-write hold bypass, or deploy.**

## Spec / frame authority (cite; do not reopen closed cells)

| Artifact | Role |
|---|---|
| [#685](https://github.com/drgaciw/open-crm-cpq/pull/685) (merged) | Landed S4 **flag-off** document apply orchestrator + ledger shape on `main`. Evidence of direction — **not** Done, **not** DRG-625 enablement. |
| [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) DRG-582 residual Decision brief | Delivery residuals **OQ-582-***. **Cite only. Do not adopt.** This DRG-625 brief owns enablement forks as **OQ-625-***. |
| [`story-s3-s4-authorization-gate.md`](story-s3-s4-authorization-gate.md) | S3–S8 remain implementation-gated; S4 enablement stays owner-authorized separately from docs merges. |
| [`specs/apply-authorization.md`](specs/apply-authorization.md) | Role / 401/403 proposal for AC-S4-10 / [DRG-628](https://linear.app/drgamtd-workspace/issue/DRG-628). Proposed default: **A until DRG-625, then B**. **DRG-625 enablement stays an owner gate** — this brief does not flip that sequence to live. |
| [`story-drg-562-563-gate-apply-and-ledger-design-note.md`](story-drg-562-563-gate-apply-and-ledger-design-note.md) ([#668](https://github.com/drgaciw/open-crm-cpq/pull/668)) | Cite-only design note for executed-state gate + ledger. |
| [`partial-apply-owner-decision.md`](partial-apply-owner-decision.md) / register OQ5 A | **Adopted** first-slice partial-apply cut. Cite; do not reopen. |
| [`oq6-revision-model-owner-decision.md`](oq6-revision-model-owner-decision.md) (#642) | **Adopted** executed immutability. Cite; do not reopen. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) | Adopted CAM contracts (D-DIGEST, D-REVISION, D-STORAGE, …). Cite; do not reopen. |

## In-flight alignment (cite only — do not adopt open OQs)

| Ticket / artifact | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) residual Decision brief | Draft [#866](https://github.com/drgaciw/open-crm-cpq/pull/866); **In Review** | Delivery forks **OQ-582-***. **Cite only. Do not adopt.** Enablement is **this** story. |
| [DRG-562](https://linear.app/drgamtd-workspace/issue/DRG-562) residual Decision brief | Draft [#841](https://github.com/drgaciw/open-crm-cpq/pull/841); **In Review** | Executed-state gate residual forks **OQ-562-***. **Cite only. Do not adopt.** |
| [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) residual Decision brief | Draft [#839](https://github.com/drgaciw/open-crm-cpq/pull/839); **In Review** | Partial-apply residual forks **OQ-565-*** (+ IMPL). Restate Adopted OQ5 A only. **Cite only. Do not adopt open OQs.** |
| [DRG-563](https://linear.app/drgamtd-workspace/issue/DRG-563) residual | Draft [#840](https://github.com/drgaciw/open-crm-cpq/pull/840) | Ledger-home / digest / provenance residuals. **Cite only.** |
| [DRG-621](https://linear.app/drgamtd-workspace/issue/DRG-621) | **Done** (`Linear read`); attachments [#688](https://github.com/drgaciw/open-crm-cpq/pull/688) / [#718](https://github.com/drgaciw/open-crm-cpq/pull/718) | Mid-loop DIGEST_CONFLICT audit / document-outcome gap — the hard pre-enablement named when DRG-625 was filed. **Landed as Done** does **not** by itself enable the endpoint or flags. Linear still lists `blockedBy` DRG-621 on DRG-625; treat that edge as historical filing context, not as a silent green light. |
| [DRG-627](https://linear.app/drgamtd-workspace/issue/DRG-627) / [DRG-626](https://linear.app/drgamtd-workspace/issue/DRG-626) / [DRG-628](https://linear.app/drgamtd-workspace/issue/DRG-628) | Related | Blocked-unit recovery; consumer hook; per-role apply. Cite; do not close here; do not treat as enablement. |
| [DRG-622](https://linear.app/drgamtd-workspace/issue/DRG-622) / [DRG-623](https://linear.app/drgamtd-workspace/issue/DRG-623) / [DRG-624](https://linear.app/drgamtd-workspace/issue/DRG-624) | Related | Concurrency fixture, uuid lookup, null `effectiveEndDate`. Quality follow-ups; not enablement by themselves. |

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted` | Already selected outside this file. Restated; **not** re-opened. |
| `Status: Proposed` | Residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |

GitNexus MCP reports **no indexed repositories** in this environment; `.gitnexus/run.cjs` is absent. Impact / `detect_changes` are therefore **UNKNOWN / unavailable** — not a clean blast-radius check. Code claims below are text/`rg` evidence only.

## Why this residual Decision brief exists

S4 code landed flag-off via [#685](https://github.com/drgaciw/open-crm-cpq/pull/685). Owner / CPQ Decision (Q3) filed **DRG-625** as the separate **owner enablement gate** so that merge of flag-off code is never mistaken for endpoint or flag enablement. Wave-2 refill assigns this **docs-only** residual brief to inventory still-open **DRG-625** forks as **Proposed OQ-625-*** for CPQ Decision — without enabling anything, without adopting #866 / #841 / #839 open OQs, and without flipping dual-read or reserved Flyway slots. Linear stays **Backlog**.

## Owner-gate restatement (what DRG-625 is)

`verified by reading the issue` (Linear DRG-625 description + relations).

- **Scope:** Owner-gated enablement of the S4 apply endpoint **and** S4 routing / apply flags, separate from DRG-582's delivered flag-off scope (#685).
- **Filing rule:** No S4 flag enablement and no product call of the apply endpoint until the owner fills this gate with words + time (originally blocked by DRG-621; that fix is now **Done** — see above — and still does not authorize enablement).
- **Not in scope of this brief:** closing DRG-582 delivery residuals (#866), reopening Adopted OQ5 A / OQ6, writing schema, or changing Linear from Backlog.

## Landed evidence on `main` (not new authorization)

Facts `verified by reading code` at the base SHA. They show direction already partially present. They do **not** close Proposed OQ-625-* and do **not** authorize endpoint enablement, flag-on routing, dual-read flip, or S4 Done.

| Fact | Evidence |
|---|---|
| Document apply orchestrator exists | `DocumentApplyOrchestrator.java` |
| Internal apply route exists | `DocumentApplyController.PATH` = `POST /internal/v1/amendments/{amendmentId}:apply` |
| Document apply refuses non-`EXECUTED` documents | Orchestrator preflight / `NOT_EXECUTED` path (`read` peer briefs #866 / #841 + controller/orchestrator presence) |
| `document_apply_ledger` table exists | `V1_12__create_document_apply_ledger.sql` |
| Replay evidence migration exists | `V1_15__document_apply_replay_evidence.sql` |
| Legacy `applied_quote_acceptances` path still present | `AcceptedQuoteSubscriptionService` / `QuoteAcceptedEventConsumer` |
| Document-path tenant routing flag exists and defaults empty / OFF | `AmendmentRoutingFlag.PROPERTY` = `opencrm.amendment.routing.document-path-tenants` (default empty string); consumer defaults OFF when unbound |
| Alert condition for document-path selection stays at zero | `AmendmentRoutingFlag.DOCUMENT_PATH_SELECTED` counter; consumer comment: stay at zero is the alert condition |
| Apply-authorization frame keeps DRG-625 as owner enablement gate | `specs/apply-authorization.md` proposed default: A until DRG-625, then B |
| CAM migrations present through `V1_15` | Inventory under `backend/services/contract-asset-management/src/main/resources/db/migration/` |
| No CAM `V1_16`–`V1_21` files | Directory listing this pass — reserved range untouched |
| Mid-loop DIGEST_CONFLICT pre-enablement fix | DRG-621 **Done** with [#688](https://github.com/drgaciw/open-crm-cpq/pull/688) / [#718](https://github.com/drgaciw/open-crm-cpq/pull/718) (`Linear read`) — still **not** DRG-625 enablement |

## Adopted / owner-gate facts this brief cites and does not reopen

| Cell | Source |
|---|---|
| OQ5 option A (first slice) | `#626` / `#631` / `#632`; register OQ5 |
| OQ6 executed immutability | `oq6-revision-model-owner-decision.md` |
| OQ7 thin contract / OQ10 signature set / OQ18 `CAM-COMMERCIAL-1` | Delegated record / register |
| Accepted proration; Accepted uplift D-04 | ADRs under `prereq/` |
| S4 enablement is a **separate owner gate** from flag-off code landing | Linear DRG-625 description; `specs/apply-authorization.md`; #866 OQ-582-2 cite |
| Apply-authorization proposed sequence A → B | `specs/apply-authorization.md` — **Proposed** there; restated, not adopted live by this brief |

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No Adopted cells in this table.** Picking a row authorizes **no** product code, schema, flag flip, endpoint enablement, dual-read flip, or deploy.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product / schema / flag? |
|---|---|---|---|---|
| **OQ-625-1** | **What “enablement” means** | Keep three distinct acts: (1) allow product/internal callers to invoke apply under an owner-named policy; (2) optionally populate `document-path-tenants`; (3) optionally expose a non-`/internal` gateway route. This brief enables **none** of them | Treat internal route presence alone as enablement; treat #685 merge as enablement | Yes — hard |
| **OQ-625-2** | **Whether enablement is authorized now** | Stay **NO-GO**. Leave Linear **Backlog**. Docs / residual briefs are not authorization | Flip flags / call endpoint because DRG-621 is Done; mark Done because this brief merges | Yes — hard |
| **OQ-625-3** | **Relation to #866 / OQ-582-*** | Cite **OQ-582-*** only. Do **not** adopt or close 582 delivery forks here. Enablement may still wait on owner deferral / close of named 582 residuals, but that wait is an **owner words** act — not silent adoption | Close OQ-582-* inside this file; treat #866 merge as enablement | Soft here; **hard on #866** — do not adopt |
| **OQ-625-4** | **Relation to #841 / #839** | Cite **OQ-562-*** / **OQ-565-*** only. Affirm Adopted OQ5 A and that apply remains a **reader** of `EXECUTED`. Do **not** adopt open forks | Close 562/565 forks here; reopen all-or-nothing apply | Soft here; **hard on #841/#839** — do not adopt |
| **OQ-625-5** | **DRG-621 pre-enablement** | Record DRG-621 as **Done** (fix landed). Treat Done as clearing the original hard audit gap — **not** as endpoint/flag enablement | Re-open DRG-621 as a silent hold forever; enable without acknowledging the original Q3 gate | Soft for tracker; hard if used to waive owner words |
| **OQ-625-6** | **`document-path-tenants` roll-out** | Leave property **empty / OFF** until a later owner enablement record names tenants (and environment). No tenant CSV from this brief | Enable named tenants in this story; default-on all tenants | Yes — flag |
| **OQ-625-7** | **Apply roles at enablement (AC-S4-10 / DRG-628)** | Keep `specs/apply-authorization.md` Proposed sequence: **A** (`Internal.Invoke` only) until enablement is authorized, then **B** (person-role allow list). Owner slot blank; pair with DRG-628. This brief does **not** switch A→B live | Jump to B without enablement record; open Option C amount tiers now; open unauthenticated apply | Yes — security / 403 AC |
| **OQ-625-8** | **Gateway / public surface** | Keep apply on CAM **`/internal/**`** only until a later owner record explicitly authorizes a gateway route. No gateway route from this brief | Publish `:apply` on the public gateway now; collapse internal + public without policy | Yes — surface / authz |
| **OQ-625-9** | **Observability / alert condition** | Keep `DOCUMENT_PATH_SELECTED` at **zero** until an owner-named tenant list is live and alerting is re-baselined. No metric redefinition here | Silence the alert and flip tenants; treat non-zero as success without owner words | Yes — ops / safety |
| **OQ-625-10** | **Related S4 follow-ups (626 / 627 / 628 / 622–624)** | Cite as related. They do **not** by themselves authorize DRG-625. Owner may still require named ones before enablement — that requirement needs words + time, not silent bundling | Bundle all as automatic prerequisites without owner words; ignore | Soft for scoring; do not treat as enablement |
| **OQ-625-11** | **AGENT.md / ARCHITECTURE holds** | Leave dual-read, asset-write, Jev, ramp, NgRx holds **untouched**. Enablement of S4 apply does **not** waive them | Flip dual-read with S4 enablement; enable asset-write / `opencrm.jev.*` from apply | Yes — explicit holds |
| **OQ-625-12** | **CAM Flyway** | Write **no** new migration here. Do **not** consume reserved **`V1_16`–`V1_21`**. Do not rewrite `V1_12` / `V1_15` in place as part of enablement | Use V1_16–V1_21 for enablement side effects; mutate legacy `applied_quote_acceptances` UNIQUE | Yes — reservation |
| **OQ-625-13** | **Linear status / Done gate** | Remain **Backlog** until an owner enablement record (words + time) exists **and** any owner-named prerequisites close or explicitly defer. This brief does **not** move status | Mark In Progress / Done because this docs PR exists; mark Done because DRG-621 is Done | Yes — tracker |
| **OQ-625-IMPL** | **Implementation / enablement authorization** | Separate owner record **after** OQ-625-1..13 (or an owner-deferred subset) close. That record — not this brief — may authorize endpoint calls, tenant list population, role sequence B, and/or a gateway route. Still must not consume `V1_16`–`V1_21` without a later reservation change; still must not flip dual-read / Jev / ramp / asset-write without their own gates | Treat this residual brief as authorization; piggy-back enablement on merge of #866 / #841 / #839 | Yes — hard NO-GO until then |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-625-1 — What “enablement” means

| Option | What the owner would be choosing |
|---|---|
| **A. Three distinct acts; enable none here (Recommended, Proposed)** | Invoke policy, tenant CSV, and gateway exposure stay separate owner acts. |
| **B. Internal route presence = enablement** | Silent product enablement — **NO-GO**. |
| **C. #685 merge = enablement** | Collides with Q3 filing of DRG-625. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-625-2 — Whether enablement is authorized now

| Option | What the owner would be choosing |
|---|---|
| **A. Stay NO-GO / Backlog (Recommended, Proposed)** | Matches issue filing and Wave-2 docs-only residual rule. |
| **B. Enable because DRG-621 is Done** | Clears a prerequisite, not the gate. |
| **C. Enable / Done on docs merge** | Explicitly false. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-625-3 — Relation to #866 / OQ-582-*

| Option | What the owner would be choosing |
|---|---|
| **A. Cite only; do not adopt (Recommended, Proposed)** | Delivery residuals stay on #866; enablement forks stay here. |
| **B. Close OQ-582-* in this file** | Out of scope. |
| **C. Treat #866 as enablement** | Collapses delivery residual into gate — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-625-4 — Relation to #841 / #839

| Option | What the owner would be choosing |
|---|---|
| **A. Cite only; affirm OQ5 A + EXECUTED reader (Recommended, Proposed)** | No adoption of open 562/565 forks; no reopen of Adopted Option A. |
| **B. Close 562/565 forks here** | Out of scope. |
| **C. Reopen all-or-nothing apply** | Reopens Adopted OQ5 A. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-625-5 — DRG-621 pre-enablement

| Option | What the owner would be choosing |
|---|---|
| **A. Done recorded; still not enablement (Recommended, Proposed)** | Honors the original hard gap **and** keeps the owner gate. |
| **B. Keep DRG-621 as a forever silent hold** | Ignores Done status / #688 / #718. |
| **C. Enable without acknowledging the Q3 gate** | Rejects filing of DRG-625. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-625-6 — `document-path-tenants` roll-out

| Option | What the owner would be choosing |
|---|---|
| **A. Empty / OFF (Recommended, Proposed)** | Matches #685 flag-off delivery and #668 fork 5 spirit. |
| **B. Named tenants in this story** | Flag flip — belongs only after OQ-625-IMPL. |
| **C. Default-on all tenants** | Broad enablement — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-625-7 — Apply roles at enablement

| Option | What the owner would be choosing |
|---|---|
| **A → then B per `specs/apply-authorization.md` (Recommended, Proposed)** | Internal-only until enablement; then person-role allow list. Does **not** switch live here. |
| **B. Person roles immediately without enablement record** | Opens apply beyond internal before the gate. |
| **C. Amount-tiered DealDesk / SalesVP / CFO** | Framed; not default. |

**Recommended (Proposed):** A→B sequence as written in the frame. Does **not** enable DRG-625. `Owner decision: ___ (drg, time)`.

### OQ-625-8 — Gateway / public surface

| Option | What the owner would be choosing |
|---|---|
| **A. `/internal/**` only until later record (Recommended, Proposed)** | Matches current controller path. |
| **B. Publish on gateway now** | New public surface — **NO-GO** here. |
| **C. Collapse internal + public without policy** | Authz regression. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-625-9 — Observability / alert condition

| Option | What the owner would be choosing |
|---|---|
| **A. Keep DOCUMENT_PATH_SELECTED at zero (Recommended, Proposed)** | Matches code alert condition until owner-named tenants go live. |
| **B. Silence alert and flip tenants** | Ops bypass — **NO-GO**. |
| **C. Redefine metric as success without owner words** | Hides enablement. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-625-10 — Related S4 follow-ups

| Option | What the owner would be choosing |
|---|---|
| **A. Cite; not enablement (Recommended, Proposed)** | Owner may still name prerequisites with words + time. |
| **B. Bundle as automatic prerequisites without owner words** | Hidden gate inflation. |
| **C. Ignore** | Leaves known follow-ups untracked. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-625-11 — Dual-read / asset-write / Jev / ramp / NgRx holds

| Option | What the owner would be choosing |
|---|---|
| **A. Leave holds untouched (Recommended, Proposed)** | AGENT.md / ARCHITECTURE §17 stand. |
| **B. Flip dual-read with S4 enablement** | Explicitly held — **NO-GO**. |
| **C. Enable asset-write / Jev / ramp from apply** | Same. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-625-12 — CAM Flyway reservation

| Option | What the owner would be choosing |
|---|---|
| **A. No new migration; V1_16–V1_21 reserved (Recommended, Proposed)** | Honors #805–#812. |
| **B. Consume V1_16–V1_21 for enablement side effects** | Violates reservation. |
| **C. Mutate legacy `applied_quote_acceptances` UNIQUE** | Reopens ledger bypass-vs-mutation fork. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-625-13 — Linear status / Done gate

| Option | What the owner would be choosing |
|---|---|
| **A. Stay Backlog until owner enablement record (Recommended, Proposed)** | Matches Wave-2 instruction: do not invent Done. |
| **B. Move because this docs PR exists** | Tracker inflation. |
| **C. Done because DRG-621 is Done** | Prerequisite ≠ gate. |

**Recommended (Proposed):** A. This brief does **not** transition Linear. `Owner decision: ___ (drg, time)`.

### OQ-625-IMPL — Implementation / enablement authorization

| Option | What the owner would be choosing |
|---|---|
| **A. Separate owner record after residual OQx close (Recommended, Proposed)** | Hard **NO-GO** on endpoint/flag/schema/product from this brief alone. |
| **B. Merge of this brief = authorization** | Explicitly false. |
| **C. Enable dual-read / reserved Flyway / flags on merge** | Violates holds. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Relation map: #866 / #841 / #839 (cite only)

| External item | Relation to this brief | What this brief must not do |
|---|---|---|
| [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) DRG-582 residual | Delivery residuals OQ-582-* | Adopt, close, or re-score those OQs; treat as enablement |
| [#841](https://github.com/drgaciw/open-crm-cpq/pull/841) DRG-562 residual | Executed-state gate OQ-562-* | Adopt, close, or re-score those OQs |
| [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) DRG-565 residual | Partial-apply OQ-565-* (+ IMPL) | Adopt open OQs; reopen OQ5 A |
| DRG-621 | Mid-loop DIGEST_CONFLICT (now **Done**) | Treat Done as silent enablement |
| CAM `V1_16`–`V1_21` (#805–#812) | Reserved Flyway range | Claim those versions; write migrations |
| Dual-read ADR-005 | AGENT.md / ARCHITECTURE hold | Flip `APPROVALS_LEGACY_LOCAL_READ` |

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Story | DRG-625 (S4 enablement owner gate) |
| Artifact | Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ-625-1..OQ-625-13 and OQ-625-IMPL |

Example shape once scored (illustrative only, **not** a claim): `DRG-625 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## What this Decision brief does not do

- Does not enable the S4 apply endpoint or any S4 flag (`document-path-tenants` stays empty / OFF).
- Does not authorize S4 / DRG-582 product Done.
- Does not mark any fork Adopted; does not fill any `Owner decision: ___ (drg, time)` slot.
- Does not adopt open OQs from [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) (DRG-582), [#841](https://github.com/drgaciw/open-crm-cpq/pull/841) (DRG-562), or [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) (DRG-565).
- Does not reopen Adopted OQ5 A, OQ6, OQ7, OQ10, or OQ18.
- Does not write CAM Flyway (reserved `V1_16`–`V1_21` untouched); does not alter `V1_12` / `V1_15` or legacy `applied_quote_acceptances`.
- Does not flip dual-read, Jev, ramp, NgRx, or asset-write holds.
- Does not register topics, gateway routes, or production consumers.
- Does not mark Linear DRG-625 Done (leaves **Backlog**).
- Does not merge or enable auto-merge on any PR.

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close **Proposed OQ-625-1..OQ-625-13** and **OQ-625-IMPL**. Until then, S4 endpoint/flag enablement is **NO-GO**.
2. **#866 / #841 / #839:** cite-only; their open OQs stay open.
3. **CAM Flyway `V1_16`–`V1_21`:** reserved (#805–#812); this brief writes none.
4. **AGENT.md / ARCHITECTURE §17 holds:** dual-read, NgRx, Jev enablement, ramp, asset-write — untouched.
5. **Linear:** remains **Backlog** — do not invent Done.
6. **GitNexus:** index unavailable this pass → impact **UNKNOWN** (not clean).

## Sources read this pass

- Linear: DRG-625 (description, relations, status **Backlog**), DRG-582 (**In Review**; #685 / #866 attachments), DRG-621 (**Done**; #688 / #718).
- Cite-only drafts: [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) (DRG-582), [#841](https://github.com/drgaciw/open-crm-cpq/pull/841) (DRG-562), [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) (DRG-565).
- Design / gates: `story-s3-s4-authorization-gate.md`; `specs/apply-authorization.md`; `story-drg-562-563-gate-apply-and-ledger-design-note.md` (#668); OQ5 / OQ6 owner decisions; delegated 2026-10-17 record.
- Code (`verified by reading code` at base SHA): `DocumentApplyOrchestrator.java`; `DocumentApplyController.java`; `AmendmentRoutingFlag.java`; `QuoteAcceptedEventConsumer.java`; CAM migration inventory through `V1_15` (including `V1_12__create_document_apply_ledger.sql`, `V1_15__document_apply_replay_evidence.sql`).
- Holds: `AGENT.md`, `docs/ARCHITECTURE.md` §17; CAM Flyway V1_16–V1_21 reservation (#805–#812).
