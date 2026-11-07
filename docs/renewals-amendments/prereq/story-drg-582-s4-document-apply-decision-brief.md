# DRG-582 — S4 document-driven apply after EXECUTED: residual Decision brief

**Status:** Proposal only. Wave-2 residual Decision brief for CPQ Decision scoring. This file does **not** authorize S4 / [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) product landing, does **not** enable the S4 apply endpoint or flags ([DRG-625](https://linear.app/drgamtd-workspace/issue/DRG-625)), does **not** flip dual-read, Jev, ramp, or asset-write holds, does **not** adopt open OQs from cite-only [#841](https://github.com/drgaciw/open-crm-cpq/pull/841) (DRG-562) or [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) (DRG-565), does **not** reopen Adopted OQ5 option A / OQ6 / OQ7 / OQ10 / OQ18, does **not** mark Linear Done, and does **not** open a Flyway migration (CAM `V1_16`–`V1_21` stay reserved for [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)). Every residual fork below is `Status: Proposed` and labeled **Proposed OQ-582-***. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / S4 document apply (RA-48). **Story:** [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582/s4-document-driven-apply-after-executed-with-multi-subscription) (RA-48). Linear read this pass: **In Review**. This brief leaves that status unchanged.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

## Explicit S4 / DRG-625 / dual-read hold (NO-GO)

| Hold | Status in this brief |
|---|---|
| **S4 / DRG-582 enablement** | **NO-GO.** Landed flag-off orchestrator / ledger from [#685](https://github.com/drgaciw/open-crm-cpq/pull/685) is **not** owner authorization to call apply in product, flip routing tenants, or mark Done. |
| **DRG-625 apply endpoint / flag enablement** | **NO-GO.** Owner gate; blocked by [DRG-621](https://linear.app/drgamtd-workspace/issue/DRG-621) until that mid-loop DIGEST_CONFLICT audit gap is resolved. This brief does not enable the endpoint or any S4 flag. |
| **Dual-read / ADR-005** | **NO-GO.** `approvals.legacy-local-read-enabled` / `APPROVALS_LEGACY_LOCAL_READ` stay as held in [AGENT.md](../../../AGENT.md) and [docs/ARCHITECTURE.md](../../ARCHITECTURE.md) §17. Untouched. |
| **Asset-write / Jev / ramp / NgRx** | **NO-GO.** AGENT.md / ARCHITECTURE holds untouched. |
| **CAM Flyway `V1_16`–`V1_21`** | **RESERVED** (#805–#812). This brief writes **no** migration and does not consume those slots. |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, dual-read flip, Jev enablement, ramp enable, S4 apply endpoint enablement (DRG-625), asset-write hold bypass, or deploy.**

## Spec / frame authority (cite; do not reopen closed cells)

| Artifact | Role |
|---|---|
| [#685](https://github.com/drgaciw/open-crm-cpq/pull/685) (merged) | Landed S4 **flag-off** document apply orchestrator + ledger shape on `main`. Evidence of direction — **not** Done, **not** DRG-625 enablement. |
| [`story-drg-562-563-gate-apply-and-ledger-design-note.md`](story-drg-562-563-gate-apply-and-ledger-design-note.md) ([#668](https://github.com/drgaciw/open-crm-cpq/pull/668)) | Cite-only design note for executed-state gate + ledger. Proposed defaults there stay unaccepted unless a later owner record says otherwise. |
| [`story-s3-s4-authorization-gate.md`](story-s3-s4-authorization-gate.md) | S3–S8 remain implementation-gated; S4 stays owner-authorized separately from docs merges. |
| [`specs/apply-authorization.md`](specs/apply-authorization.md) | Role / 401/403 proposal for AC-S4-10 / [DRG-628](https://linear.app/drgamtd-workspace/issue/DRG-628). **DRG-625 enablement stays an owner gate.** |
| [`partial-apply-owner-decision.md`](partial-apply-owner-decision.md) / register OQ5 A | **Adopted** first-slice partial-apply cut. Cite; do not reopen. |
| [`oq6-revision-model-owner-decision.md`](oq6-revision-model-owner-decision.md) (#642) | **Adopted** executed immutability. Cite; do not reopen. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) | Adopted CAM contracts (D-DIGEST, D-REVISION, D-STORAGE, …). Cite; do not reopen. |

## In-flight alignment (cite only — do not adopt open OQs)

| Ticket / artifact | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-562](https://linear.app/drgamtd-workspace/issue/DRG-562) residual Decision brief | Draft [#841](https://github.com/drgaciw/open-crm-cpq/pull/841); **In Review** | Executed-state gate residual forks **OQ-562-***. **Cite only. Do not adopt.** |
| [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) residual Decision brief | Draft [#839](https://github.com/drgaciw/open-crm-cpq/pull/839); **In Review** | Partial-apply residual forks **OQ-565-*** (+ IMPL). Restate Adopted OQ5 A only. **Cite only. Do not adopt open OQs.** |
| [DRG-563](https://linear.app/drgamtd-workspace/issue/DRG-563) residual | Draft [#840](https://github.com/drgaciw/open-crm-cpq/pull/840) | Ledger-home / digest / provenance residuals. **Cite only.** |
| [DRG-625](https://linear.app/drgamtd-workspace/issue/DRG-625) | Backlog; blocked by DRG-621 | Apply endpoint/flag enablement. **NO-GO** here. |
| [DRG-621](https://linear.app/drgamtd-workspace/issue/DRG-621) | Related | Mid-loop DIGEST_CONFLICT audit / document-outcome gap. Hard pre-enablement. |
| [DRG-627](https://linear.app/drgamtd-workspace/issue/DRG-627) / [DRG-626](https://linear.app/drgamtd-workspace/issue/DRG-626) / [DRG-628](https://linear.app/drgamtd-workspace/issue/DRG-628) | Related | Blocked-unit recovery; consumer hook; per-role apply. Cite; do not close here. |
| [DRG-622](https://linear.app/drgamtd-workspace/issue/DRG-622) / [DRG-623](https://linear.app/drgamtd-workspace/issue/DRG-623) / [DRG-624](https://linear.app/drgamtd-workspace/issue/DRG-624) | Related | Concurrency fixture, uuid lookup, null `effectiveEndDate`. Quality follow-ups; not enablement. |

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

S4 code landed flag-off via [#685](https://github.com/drgaciw/open-crm-cpq/pull/685) (`Linear read`: merge head recorded on the issue; tracker reverted from auto-Done to **In Review**; Done waits on DoD). Wave-2 refill assigns this **docs-only** residual brief to inventory still-open **DRG-582** forks as **Proposed OQ-582-*** for CPQ Decision — without authorizing enablement, without adopting #841 / #839 open OQs, and without flipping dual-read or reserved Flyway slots.

## Story restatement (RA-48) — scope only

`verified by reading the issue` (Linear DRG-582 description).

- Apply orchestrator in CAM reusing typed apply internals (digest, `observedRevision`, advisory lock).
- One unit per target subscription; atomic per unit; dependency order; no auto rollback.
- Ledger keyed `(document_id, unit_no, apply_key_digest)` (landed shape on `main` via `V1_12`; historical docs said V1_9).
- Legacy `applied_quote_acceptances` untouched; legacy path unchanged with flag off.
- First-slice actions: Add, Update, Remove.
- Requirements FR-09..FR-12, FR-15; AC-S4-1..AC-S4-10 as written on the issue.

This restatement does **not** authorize calling the path, enabling tenants, or closing residual forks.

## Landed evidence on `main` (not new authorization)

Facts `verified by reading code` at the base SHA. They show direction already partially present. They do **not** close Proposed OQ-582-* and do **not** authorize S4 product Done, DRG-625 enablement, dual-read flip, or flag-on routing.

| Fact | Evidence |
|---|---|
| Document apply orchestrator exists | `DocumentApplyOrchestrator.java` |
| Internal apply route exists | `DocumentApplyController.PATH` = `POST /internal/v1/amendments/{amendmentId}:apply`; requires `Internal.Invoke` via shared `/internal/**` chain |
| Document apply refuses non-`EXECUTED` documents | Orchestrator preflight / `NOT_EXECUTED` path (`read` peer brief #841 + controller/orchestrator presence) |
| `document_apply_ledger` table exists | `V1_12__create_document_apply_ledger.sql` (later than early “reserved V1_9” wording; table shape matches the gate paragraph) |
| Replay evidence migration exists | `V1_15__document_apply_replay_evidence.sql` |
| Legacy `applied_quote_acceptances` path still present | `AcceptedQuoteSubscriptionService` / `QuoteAcceptedEventConsumer`; `V1_6__subscription_rate_schedule.sql` |
| Document-path tenant routing flag exists and defaults empty | `AmendmentRoutingFlag.PROPERTY` = `opencrm.amendment.routing.document-path-tenants` (default empty string); consumer defaults OFF when unbound |
| Apply-authorization frame keeps DRG-625 as owner enablement gate | `specs/apply-authorization.md` proposed default: A until DRG-625, then B |
| CAM migrations present through `V1_15` | Inventory under `backend/services/contract-asset-management/src/main/resources/db/migration/` |
| No CAM `V1_16`–`V1_21` files | Directory listing this pass — reserved range untouched |

## Adopted cells this brief cites and does not reopen

| Cell | Source |
|---|---|
| OQ5 option A (first slice) | `#626` / `#631` / `#632`; register OQ5 |
| OQ6 executed immutability | `oq6-revision-model-owner-decision.md` |
| OQ7 thin contract / OQ10 signature set / OQ18 `CAM-COMMERCIAL-1` | Delegated record / register |
| Accepted proration; Accepted uplift D-04 | ADRs under `prereq/` |

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No Adopted cells in this table.** Picking a row authorizes **no** product code, schema, flag, DRG-625 enablement, dual-read flip, or deploy.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product / schema / flag? |
|---|---|---|---|---|
| **OQ-582-1** | **Landed #685 vs authorization** — does flag-off code on `main` count as S4 Done / enablement? | **No.** Keep **In Review**. Landed orchestrator + `V1_12`/`V1_15` are evidence only. Done waits on DoD + owner enablement record (DRG-625) after residual forks close or defer | Treat #685 merge as Done; treat internal route presence as production enablement | Yes — tracker / release correctness |
| **OQ-582-2** | **DRG-625 endpoint / flag enablement** | Stay **NO-GO**. Do not enable apply endpoint or S4 flags until DRG-621 is resolved **and** owner fills DRG-625 with words + time | Enable internal apply for all tenants now; flip `document-path-tenants` now | Yes — hard |
| **OQ-582-3** | **Apply roles (AC-S4-10)** | Keep `specs/apply-authorization.md` Proposed: A (`Internal.Invoke` only) until DRG-625, then B (person-role allow list). Owner slot blank; owned with [DRG-628](https://linear.app/drgamtd-workspace/issue/DRG-628) | Option C amount-tiered DealDesk/SalesVP/CFO; open gateway route now | Yes — security / 403 AC |
| **OQ-582-4** | **Mid-loop DIGEST_CONFLICT after committed units (DRG-621)** | Treat as **hard pre-enablement**: no endpoint call / flag-on until audit row + document outcome are defined and fixed | Defer audit forever; enable anyway and accept silent gap | Yes — DRG-625 blockedBy |
| **OQ-582-5** | **Interaction with DRG-562 residual (#841)** | Cite **OQ-562-*** only. Affirm apply remains a **reader** of `EXECUTED`; do **not** adopt or close 562 forks here | Close OQ-562-1..8 inside this brief; reopen bare `QuoteAccepted` as document path | Soft here; **hard on #841** — do not adopt |
| **OQ-582-6** | **Interaction with DRG-565 residual (#839) / OQ5 A** | Affirm Adopted OQ5 A (A1–A6). Cite **OQ-565-*** only; do **not** adopt open 565 forks; do not invent extra product states | Reopen all-or-nothing apply; adopt OQ-565-1..14 here; invent new `PARTIALLY_APPLIED_*` subtypes | Yes if reopen A3/A4 |
| **OQ-582-7** | **Blocked-unit recovery tooling (DRG-627)** | Correcting amendment remains first-slice remedy (OQ5 A). Retry/re-apply tooling stays a **separate** story; no product recovery path from this brief | Auto-retry blocked units while `PARTIALLY_APPLIED`; silent rebase | Soft until enablement; hard if it invents a second remedy |
| **OQ-582-8** | **Consumer hook for amendment events (DRG-626)** | No new consumer/topic registration from this brief. Post-#683 hook stays separate; S6 surface stays owner-gated | Register production consumers now; overload legacy `QuoteAccepted` as the only hook | Yes — event / S6 surface |
| **OQ-582-9** | **Document-path routing flag** | Leave `opencrm.amendment.routing.document-path-tenants` **empty / OFF**. No tenant list from this brief | Enable named tenants; default-on all tenants | Yes — dual-path routing |
| **OQ-582-10** | **AGENT.md / ARCHITECTURE holds (dual-read, asset-write, Jev, ramp, NgRx)** | Leave all holds **untouched**. S4 residual scoring does not waive dual-read ADR-005 or asset-write | Flip dual-read; enable asset-write from apply; turn on `opencrm.jev.*` | Yes — explicit holds |
| **OQ-582-11** | **CAM Flyway after landed ledger** | Write **no** new migration here. Do **not** consume reserved **`V1_16`–`V1_21`**. Do not rewrite `V1_12` / `V1_15` in place | Use V1_16–V1_21 for S4 follow-ups; alter `applied_quote_acceptances` UNIQUE | Yes — reservation |
| **OQ-582-12** | **Quality follow-ups DRG-622 / 623 / 624** | Keep as related hygiene/quality tickets. They do **not** by themselves authorize DRG-625; cite only | Bundle as silent enablement prerequisites without owner words; ignore | Soft for scoring; do not treat as enablement |
| **OQ-582-13** | **DoD / Done gate** | Remain **In Review** until DoD items the owner named for S4 pass **and** enablement forks (at least OQ-582-2 / DRG-625) close or explicitly defer | Mark Done because #685 merged; mark Done when only unit tests are green | Yes — tracker |
| **OQ-582-IMPL** | **Implementation / enablement authorization** | Separate owner record **after** OQ-582-1..13 (or an owner-deferred subset) close. That record — not this brief — may authorize further product work. Still must not consume `V1_16`–`V1_21` without a later reservation change; still must not flip dual-read / Jev / ramp / asset-write without their own gates | Treat this residual brief as authorization; piggy-back enablement on merge | Yes — hard NO-GO until then |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-582-1 — Landed #685 vs authorization

| Option | What the owner would be choosing |
|---|---|
| **A. Evidence only; keep In Review (Recommended, Proposed)** | Matches Linear hygiene (`Linear read`): draft/merge of flag-off code is not Done; enablement is DRG-625. |
| **B. Treat #685 as Done** | Collides with DoD wait and DRG-625 owner gate. |
| **C. Treat internal route presence as production enablement** | Silent flag-on — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-582-2 — DRG-625 endpoint / flag enablement

| Option | What the owner would be choosing |
|---|---|
| **A. Stay NO-GO until DRG-621 + owner words (Recommended, Proposed)** | Matches DRG-625 description and blockedBy DRG-621. |
| **B. Enable internal apply for all tenants now** | Product enablement without audit gap fix. |
| **C. Flip `document-path-tenants` now** | Flag flip — **NO-GO** here. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-582-3 — Apply roles (AC-S4-10)

| Option | What the owner would be choosing |
|---|---|
| **A → then B per `specs/apply-authorization.md` (Recommended, Proposed)** | Internal-only until DRG-625; then person-role allow list. Owner slot on that frame stays blank; pairs with DRG-628. |
| **B. Person roles immediately without DRG-625** | Opens apply beyond internal before enablement gate. |
| **C. Amount-tiered DealDesk / SalesVP / CFO** | Framed in apply-authorization.md; not default. |

**Recommended (Proposed):** A→B sequence as written in the frame. Does **not** enable DRG-625. `Owner decision: ___ (drg, time)`.

### OQ-582-4 — DRG-621 mid-loop DIGEST_CONFLICT

| Option | What the owner would be choosing |
|---|---|
| **A. Hard pre-enablement (Recommended, Proposed)** | No DRG-625 until audit row + document outcome exist for mid-loop conflict after earlier units committed. |
| **B. Enable with known silent gap** | Rejects CPQ Decision Q3 filing of DRG-625. |
| **C. Redefine AC to drop audit** | Needs explicit owner supersession of AC-S4-2 / audit expectations. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-582-5 — DRG-562 residual (#841) cite only

| Option | What the owner would be choosing |
|---|---|
| **A. Cite OQ-562-*; do not adopt (Recommended, Proposed)** | S4 remains a reader of executed state; gate forks stay on #841. |
| **B. Close 562 forks in this file** | Out of scope; would adopt another story’s residuals. |
| **C. Reopen bare QuoteAccepted as document path** | Reopens RA-28 failure mode. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-582-6 — DRG-565 residual (#839) / OQ5 A

| Option | What the owner would be choosing |
|---|---|
| **A. Affirm Adopted OQ5 A; cite OQ-565-* only (Recommended, Proposed)** | No silent rollback; `PARTIALLY_APPLIED` / `APPLY_BLOCKED` cut unchanged; correcting amendment remedy. |
| **B. Adopt OQ-565-1..14 here** | Would close another story’s residuals — **NO-GO**. |
| **C. Reopen all-or-nothing apply** | Reopens Adopted Option A. |

**Recommended (Proposed):** A. **Cite Adopted OQ5 A; do not reopen; do not adopt #839 open OQs.** `Owner decision: ___ (drg, time)`.

### OQ-582-7 — Blocked-unit recovery (DRG-627)

| Option | What the owner would be choosing |
|---|---|
| **A. Correcting amendment only; tooling separate (Recommended, Proposed)** | Matches OQ5 A / OQ6 single-remedy reading. |
| **B. Auto-retry blocked units on same document** | Second recovery path; couples to OQ-582-4 / OQ6. |
| **C. Silent rebase** | Conflicts with executed immutability. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-582-8 — Consumer hook (DRG-626)

| Option | What the owner would be choosing |
|---|---|
| **A. No new hook from this brief (Recommended, Proposed)** | Keeps S6 / event registration owner-gated. |
| **B. Register production consumers now** | Starts event surface without S6 auth. |
| **C. Legacy QuoteAccepted only forever** | Blocks document-path event consumers. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-582-9 — Document-path routing flag

| Option | What the owner would be choosing |
|---|---|
| **A. Tenants empty / OFF (Recommended, Proposed)** | Matches #668 fork 5 spirit and #685 flag-off delivery. |
| **B. Enable named tenants in this story** | Flag flip — belongs on DRG-625 after forks close. |
| **C. Default-on all tenants** | Broad enablement — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-582-10 — Dual-read / asset-write / Jev / ramp / NgRx holds

| Option | What the owner would be choosing |
|---|---|
| **A. Leave holds untouched (Recommended, Proposed)** | AGENT.md / ARCHITECTURE §17 stand. Dual-read ADR-005 stays Proposed / unflipped. |
| **B. Flip dual-read as part of S4** | Explicitly held — **NO-GO**. |
| **C. Enable asset-write / Jev / ramp from apply** | Same. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-582-11 — CAM Flyway reservation

| Option | What the owner would be choosing |
|---|---|
| **A. No new migration; V1_16–V1_21 reserved (Recommended, Proposed)** | Honors #805–#812. Landed `V1_12` / `V1_15` stay as-is. |
| **B. Consume V1_16–V1_21 for S4 follow-ups now** | Violates reservation. |
| **C. Mutate legacy `applied_quote_acceptances` UNIQUE** | Reopens DRG-563 bypass-vs-mutation fork. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-582-12 — Quality follow-ups 622 / 623 / 624

| Option | What the owner would be choosing |
|---|---|
| **A. Cite as related hygiene; not enablement (Recommended, Proposed)** | Owner may still require them before DRG-625; this brief does not silently convert them into authorization. |
| **B. Bundle as automatic enablement prerequisites without owner words** | Hidden gate inflation. |
| **C. Ignore** | Leaves known gaps untracked. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-582-13 — DoD / Done gate

| Option | What the owner would be choosing |
|---|---|
| **A. Stay In Review until DoD + enablement forks (Recommended, Proposed)** | Matches Linear comments: Done waits on DoD; enablement is separate. |
| **B. Done because #685 merged** | Rejected by hygiene already. |
| **C. Done when only unit tests are green** | Incomplete DoD. |

**Recommended (Proposed):** A. This brief does **not** transition Linear. `Owner decision: ___ (drg, time)`.

### OQ-582-IMPL — Implementation / enablement gate

| Option | What the owner would be choosing |
|---|---|
| **A. Separate owner record after residual OQx close (Recommended, Proposed)** | Hard **NO-GO** on further product/schema/flag/enablement from this brief alone. |
| **B. Merge of this brief = authorization** | Explicitly false. |
| **C. Enable dual-read / reserved Flyway / DRG-625 on merge** | Violates holds. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Relation map: #841 / #839 / DRG-625 (cite only)

| External item | Relation to this brief | What this brief must not do |
|---|---|---|
| [#841](https://github.com/drgaciw/open-crm-cpq/pull/841) DRG-562 residual | Executed-state gate OQ-562-* | Adopt, close, or re-score those OQs |
| [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) DRG-565 residual | Partial-apply OQ-565-* (+ IMPL) | Adopt open OQs; reopen OQ5 A |
| DRG-625 | Apply endpoint / flag enablement | Enable endpoint or flip flags |
| DRG-621 | Mid-loop DIGEST_CONFLICT | Waive as pre-enablement |
| CAM `V1_16`–`V1_21` (#805–#812) | Reserved Flyway range | Claim those versions; write migrations |
| Dual-read ADR-005 | AGENT.md / ARCHITECTURE hold | Flip `APPROVALS_LEGACY_LOCAL_READ` |

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Story | DRG-582 (RA-48) |
| Artifact | Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ-582-1..OQ-582-13 and OQ-582-IMPL |

Example shape once scored (illustrative only, **not** a claim): `DRG-582 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## What this Decision brief does not do

- Does not authorize S4 product landing, DRG-625 apply-endpoint enablement, or any S4 flag flip.
- Does not mark any fork Adopted; does not fill any `Owner decision: ___ (drg, time)` slot.
- Does not adopt open OQs from [#841](https://github.com/drgaciw/open-crm-cpq/pull/841) (DRG-562) or [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) (DRG-565).
- Does not reopen Adopted OQ5 A, OQ6, OQ7, OQ10, or OQ18.
- Does not write CAM Flyway (reserved `V1_16`–`V1_21` untouched); does not alter `V1_12` / `V1_15` or legacy `applied_quote_acceptances`.
- Does not flip dual-read, Jev, ramp, NgRx, or asset-write holds.
- Does not register topics, gateway routes, or production consumers.
- Does not mark Linear DRG-582 Done (leaves **In Review**).
- Does not merge or enable auto-merge on any PR.

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close **Proposed OQ-582-1..OQ-582-13** and **OQ-582-IMPL**. Until then, further product/schema/flag/enablement work on S4 is **NO-GO**.
2. **DRG-625:** endpoint/flag enablement remains a separate owner gate; blocked by DRG-621.
3. **#841 / #839:** cite-only; their open OQs stay open.
4. **CAM Flyway `V1_16`–`V1_21`:** reserved (#805–#812); this brief writes none.
5. **AGENT.md / ARCHITECTURE §17 holds:** dual-read, NgRx, Jev enablement, ramp, asset-write — untouched.
6. **GitNexus:** index unavailable this pass → impact **UNKNOWN** (not clean).

## Sources read this pass

- Linear: DRG-582 (description, attachments, relations, comments, status **In Review**), DRG-625 (Backlog; blockedBy DRG-621).
- Cite-only drafts: [#841](https://github.com/drgaciw/open-crm-cpq/pull/841) (DRG-562), [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) (DRG-565).
- Design / gates: `story-drg-562-563-gate-apply-and-ledger-design-note.md` (#668); `story-s3-s4-authorization-gate.md`; `specs/apply-authorization.md`; OQ5 / OQ6 owner decisions; delegated 2026-10-17 record.
- Code (`verified by reading code` at base SHA): `DocumentApplyOrchestrator.java`; `DocumentApplyController.java`; `AmendmentRoutingFlag.java`; `QuoteAcceptedEventConsumer.java`; CAM migration inventory through `V1_15` (including `V1_12__create_document_apply_ledger.sql`, `V1_15__document_apply_replay_evidence.sql`).
- Holds: `AGENT.md`, `docs/ARCHITECTURE.md` §17; CAM Flyway V1_16–V1_21 reservation (#805–#812).
