# DRG-595 — Renewal and amendment visibility: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted Cluster G cells F-595-1..F-595-7 (or related F-597-3 assignment write rule), amend the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md), accept [`specs/spine-dependent.md`](specs/spine-dependent.md), authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a gateway route, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / Cluster G visibility. **Story:** [DRG-595](https://linear.app/drgamtd-workspace/issue/DRG-595/renewal-and-amendment-visibility-contract-owner-plus-read-only-sales) (RA-61). Linear read this pass: **In Review**. Notion: [RA-61 backlog row](https://app.notion.com/p/3fbf7cb4e4df81188fffcce35557865a) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen):**

| Artifact | Role |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Migration and access + Cluster G disposition F-595-1..7 | **Adopted baseline** for explicit audited contract ownership, existing tenant boundaries, read-only sales-ops/finance under delivered permissions, no silent new Entra role or approve/execute grant, and ownership check before a mutating S3 endpoint |
| [cluster-g-visibility-queues-migration-decision-frame.md](cluster-g-visibility-queues-migration-decision-frame.md) | Historical F-595-* options/rationale; dispositions already indexed. Do not re-litigate Adopted cells |
| [specs/spine-dependent.md](specs/spine-dependent.md) § DRG-595 + fixtures AC-595-1..3 | Proposed acceptance criteria / fixtures; authorizes no code |
| [ADR-016](../../adr/ADR-016-identity-authentication-service-trust.md) decision 7 | Accepted app-role names (`CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, `Admin`); **record-level authorization stays deferred** |
| [identity-go-live.md](../../runbooks/identity-go-live.md) § People | Same five role names; `CFO` row describes finance leadership, not a finance app role |
| Mid-term policy "Audit and visibility" | Call sentence recorded; visibility **not yet in that spec** (`../mid-term-amendment-policy-spec.md`) |

**In-flight alignment (do not contradict Accepted / draft semantics):**

| Ticket / PR | Linear / GH | What this brief must not contradict |
|---|---|---|
| [DRG-597](https://linear.app/drgamtd-workspace/issue/DRG-597) | Related | Shared-spine queues; **owner-only** assignment / due-date writes (Adopted F-597-3 B). Sales ops / finance stay read-only for those writes |
| [DRG-591](https://linear.app/drgamtd-workspace/issue/DRG-591) / PL-9 / OQ25 | Parked | Approve stays parked; this brief does not invent approver visibility or an approve permission |
| [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) (S3) | Todo / separately gated | Owner check is a named prerequisite of a later mutating S3 API; this brief does **not** authorize S3 |
| [DRG-537](https://linear.app/drgamtd-workspace/issue/DRG-537) | Related UI | UI spec defines no role-specific screens; OpenAPI lists DRG-595 role enforcement out of that slice |
| [DRG-648](https://linear.app/drgamtd-workspace/issue/DRG-648) / spine-dependent fixtures | Prereq | AC-595-* fixtures stay Proposed labels (`SALES_OPS` / `FINANCE`), not Entra roles |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** No `opencrm.jev.*.enabled` flag is turned on. Dual-read / NgRx / Activities holds in `AGENT.md` stay untouched. An implementation brief for DRG-595 is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record / Cluster G disposition. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `GitNexus` | This checkout has **no** `.gitnexus/run.cjs` / no indexed repo. Graph impact is **UNKNOWN** (unresolved), not a clean check. |

## Problem (RA-61)

Call decision 2026-10-16 (#8): contracts, renewals, and amendments are visible to the **contract owner** (can act) plus **read-only** sales ops and finance. Read-only means no edit, submit, approve, or execute. Read-only also sees audit history. Approvers stay parked on DRG-591.

Cluster G + the delegated record already adopted the high-level F-595-* defaults. There is still no owner-scored Decision brief that (a) cites those Adopted cells without reopening them, and (b) lists **residual delivery forks** (initial owner assignment, permission delivery without new Entra roles, tenant enforcement surface, queue vs document visibility, override/audit) that must close before product code, schema, flags, or gateway routes.

Without that split, an S3 or UI story could re-litigate named-owner vs CRM-account-owner, silently map ticket words onto `DealDesk`/`CFO`, invent cross-tenant finance read, or ship read-only roles before the ownership check.

Linear DRG-595 (`Linear read` 2026-11-06): Medium; **In Review**; related DRG-537 / DRG-581 / DRG-591 / DRG-597 / DRG-648 / DRG-607. Attachments: Notion backlog row; Cluster G draft PR [#656](https://github.com/drgaciw/open-crm-cpq/pull/656) (frame later adopted via the delegated record).

## Already Adopted (do not re-decide)

Restated from the delegated record § Migration and access and Cluster G disposition (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | Contract owner is a **named user id**, distinct from `created_by` | F-595-1 A |
| A2 | Owner fact lives **outside** the ADR-035 thin-header list (same separation spirit as OQ27 / `precedence_flag`) | F-595-2 A |
| A3 | Ownership **transfer is explicit**, appends an audit row, and does **not** revise executed documents (OQ6 `IMMUTABLE_EXECUTED` untouched) | F-595-3 A |
| A4 | Once draft APIs exist, owner may **create / edit / submit**. **Approve** stays on DRG-591. **Execute** stays outside this visibility story | F-595-4 A |
| A5 | Ticket words "sales ops" / "finance" are **labels only** in design text. ADR-016's five app-role names stay as written. **No new Entra role** and no silent approve/execute grant | F-595-5 A; delegated § Migration and access |
| A6 | Inside the caller's `tenant_id`, that read-only access sees **every** contract / renewal / amendment document and that contract's `amendment_document_events`. **No other tenant**. Owner sees owned contracts | F-595-6 A |
| A7 | **Owner check** is a named prerequisite before a mutating S3 endpoint ships; read-only roles are a **later increment**. Accepting Cluster G / this brief does **not** start S3 | F-595-7 A; delegated "delivered ownership checks before a mutating S3 endpoint" |
| A8 | Queue assignment / due-date **writes** are **owner-only** on owned contracts; sales ops / finance remain read-only for those writes | F-597-3 B; delegated |
| A9 | Queue membership is a **filter on the shared spine** (not copied tables); HTTP surface stays filters on the sketched lifecycle list — no new accepted queue route from Cluster G | F-597-1 A; F-597-5 A |
| A10 | Holds: no product code / Flyway / flag / gateway route / deploy from docs PRs alone; dual-read / NgRx / Jev / S4 apply holds in `AGENT.md` untouched | Cluster holds; AGENT.md |

## What the tree does today (auth / visibility)

`verified by reading code` at the base SHA.

1. Thin header `contracts` in `V1_7__contract_documents.sql` has **no owner column**. Audit events live in `amendment_document_events`. Backfill `created_by` markers (`V1_8_HEADER_BACKFILL` / `S7_HEADER_AT_CREATION`) are not people.
2. ADR-016 decision 7 names `CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, `Admin` and **defers record-level authorization**. `EntraRoleAuthorities` maps each non-blank `roles` claim to `ROLE_<UPPER_SNAKE>`; class comments exemplify those roles and `Internal.Invoke`. No closed set named `SalesOps` / `Finance` / `finance.read`.
3. No contract-owner visibility check was found under `backend/services/contract-asset-management` via the Cluster G / spine-dependent searches cited on main. Behavior outside those paths is unverified.
4. Spine-dependent fixtures AC-595-1..3 stay **Proposed** (`role_label` `SALES_OPS` / `FINANCE` are ticket words, not Entra roles).
5. UI / OpenAPI still treat DRG-595 role enforcement as out of the first HTTP/UI slice.

### GitNexus (this checkout)

No indexed repository (no `.gitnexus/run.cjs`). Graph impact for any future CAM visibility symbol is **UNKNOWN** — unresolved, not low. A later implementation must run impact before editing authorization helpers, CAM controllers, or gateway filters.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No Adopted cells in this table.** Do not use these rows to reopen A1–A10.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-595-1 | Initial named owner (who is set first) | On human create: set `owner_user_id` from authenticated creator (still distinct from durable `created_by` audit). On S7 / V1_8 backfill: leave owner **null** until an explicit assign/transfer | Always derive from CRM account owner; stamp backfill `created_by` as owner (contradicts A1/A3) | Yes — AC-595-1 until first owner exists |
| OQ-595-2 | Owner-store shape (delivery of A2) | Side table/row outside thin header: at least `contract_id`, `owner_user_id`, `effective_from`, tenant-scoped unique current owner; history via transfer audit | Thin-header column (reopens A2); derive-only with no store (reopens A2) | Yes — any ownership check |
| OQ-595-3 | Who may transfer / Admin override | Current owner or `Admin` may transfer; transfer appends audit; executed docs unchanged. `Admin` break-glass **read+act** is explicit and audited, not silent role expansion | Any `CPQ.User` may transfer; CRM sync auto-follows (reopens A3); Admin acts without audit | Yes — transfer / override ACs |
| OQ-595-4 | How sales-ops / finance get read-only | Keep ADR-016 role catalog unchanged. Deliver **permission claims or group→permission mapping** in a later identity story; design labels stay `sales-ops.read` / `finance.read` until that delivery. No Entra role rename in this brief | Map now to `DealDesk`/`CFO` as accepted (rejected by A5); add Entra `SalesOps`/`Finance` in this brief (rejected by A5) | Yes — AC-595-2 / AC-595-3 |
| OQ-595-5 | Tenant boundary enforcement | Enforce `contracts.tenant_id == caller.tenant` in **CAM** on every document / event / queue read and write; gateway remains edge auth only (ADR-009/016). Cross-tenant always 403 `TENANT_MISMATCH` | Gateway-only filter; allow finance cross-tenant (reopens A6) | Yes — AC-595-3 |
| OQ-595-6 | Queue vs document visibility | Same visibility matrix for **queue filters** and **document/audit reads**: owner sees owned; delivered sales-ops/finance read-only see tenant-wide; writes stay owner-only (A8) | Queues owner-only; queues ignore document visibility; sales ops write assignment | Soft for read ACs; hard for DRG-597 assignment |
| OQ-595-7 | Override / legal-hold / Admin | Legal-hold and retention flags do **not** grant write. Admin override of owner for support is allowed only under OQ-595-3 audit. No silent approve/execute | Admin bypasses read-only verbs without audit; legal-hold unlocks edit | Soft until Admin paths ship |
| OQ-595-8 | Audit visibility and denial audit | Read-only callers may `READ_AUDIT` same-tenant `amendment_document_events` (A6). Ownership transfer audit is a separate append-only row/event type. Denied write attempts may emit refusal audit **without** mutating commercial state (aligns with S3 refusal-audit spirit; does not authorize S3) | Hide audit from finance; put transfer on commercial hash chain; no denial audit | Soft for denial; hard for AC-595-3 |
| OQ-595-9 | Sequencing vs S3 / OpenAPI | Mutating S3 endpoints require **delivered ownership check** first (A7). Read-only role wiring is a later increment after that check. This brief edits neither OpenAPI nor DRG-537 | Ship read-only first; treat this brief as S3 start (reopens A7) | Yes — S3 release gate |
| OQ-595-10 | Fixture / error-code freeze | Keep Proposed fixture codes `READ_ONLY_FORBIDDEN` / `TENANT_MISMATCH` / (597) `ASSIGNMENT_FORBIDDEN` until implementation brief; do not invent new public codes here | Rename codes now; add gateway-specific codes in this brief | Soft (docs) |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-595-1 — Initial named owner

| Option | What the owner would be choosing |
|---|---|
| **A. Creator on human create; null on backfill (Recommended, Proposed)** | Preserves A1 (named user ≠ `created_by` marker). Backfilled headers need an explicit assign/transfer before AC-595-1 can pass. |
| **B. Always CRM account owner of `customer_account_id`** | Reopens Adopted F-595-1 B. Visibility drifts with CRM without a transfer row. |
| **C. Use `contracts.created_by` including backfill markers** | Reopens F-595-1 C. Collides with `V1_8_HEADER_BACKFILL`. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-595-2 — Owner-store shape

| Option | What the owner would be choosing |
|---|---|
| **A. Side fact outside thin header (Recommended, Proposed)** | Delivery of A2. Exact migration number is **would-be only** and is settled in a later implementation PR — **NO-GO** in this brief. |
| **B. New thin-header column** | Reopens A2 / OQ3 thin-header rule. |
| **C. Derive only** | Reopens A2; breaks transfer audit (A3). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-595-3 — Transfer actors and Admin override

| Option | What the owner would be choosing |
|---|---|
| **A. Owner or Admin; audited; no doc revision (Recommended, Proposed)** | Delivers A3. Admin break-glass is explicit. |
| **B. Any authenticated `CPQ.User`** | Too wide; conflicts with owner-centric act model (A4). |
| **C. CRM auto-follow without transfer row** | Reopens A3. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-595-4 — Delivered read-only for sales ops / finance

| Option | What the owner would be choosing |
|---|---|
| **A. Labels now; permission delivery later; no Entra rename (Recommended, Proposed)** | Restates A5. Implementation story must name the delivered permission mechanism without editing ADR-016's role catalog in this brief. |
| **B. Treat `DealDesk` = sales ops and `CFO` = finance as accepted now** | Explicitly rejected by Adopted F-595-5 A / delegated "no new Entra role … silently granted". |
| **C. Add Entra roles in this brief** | Same rejection; would be a separate identity ADR / runbook change. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-595-5 — Tenant boundaries

| Option | What the owner would be choosing |
|---|---|
| **A. CAM enforces tenant on every read/write (Recommended, Proposed)** | Matches A6 and AC-595-3. |
| **B. Gateway-only tenant filter** | Insufficient under ADR-016 zero-trust-inside-perimeter posture. |
| **C. Finance may read other tenants** | Reopens A6 option C. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-595-6 — Queue vs document visibility

| Option | What the owner would be choosing |
|---|---|
| **A. One matrix for queues and documents (Recommended, Proposed)** | Queue filters do not create a second visibility policy. Assignment writes stay owner-only (A8/A9). |
| **B. Queues are owner-only; documents allow tenant-wide read-only** | Splits RA-61 and RA-63 in a way Cluster G did not adopt. |
| **C. Sales ops may write assignment** | Reopens F-597-3 A. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-595-7 — Override / legal-hold

| Option | What the owner would be choosing |
|---|---|
| **A. No write from hold flags; Admin override only via OQ-595-3 (Recommended, Proposed)** | Keeps read-only verbs refused (AC-595-2). |
| **B. Admin bypass without audit** | Conflicts with "every amendment is auditable" call decision #7. |
| **C. Legal-hold unlocks edit for finance** | Invents a write path RA-61 denied. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-595-8 — Audit visibility and denial audit

| Option | What the owner would be choosing |
|---|---|
| **A. Same-tenant READ_AUDIT for delivered read-only; separate transfer audit; optional refusal audit (Recommended, Proposed)** | Delivers A6 audit sentence without authorizing S2/S3 writers here. |
| **B. Finance cannot read `amendment_document_events`** | Conflicts with issue text and A6. |
| **C. Put ownership transfer on the commercial digest chain** | Couples access control to OQ18 commercial envelopes; premature. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-595-9 — Sequencing vs S3

| Option | What the owner would be choosing |
|---|---|
| **A. Ownership check before mutating S3; read-only later (Recommended, Proposed)** | Restates A7. This brief is not S3 authorization. |
| **B. This brief starts S3** | Reopens F-595-7 B. |
| **C. Ship read-only first, skip owner check** | Reopens F-595-7 C. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-595-10 — Fixture / error-code freeze

| Option | What the owner would be choosing |
|---|---|
| **A. Keep Proposed fixture codes until implementation brief (Recommended, Proposed)** | AC-595-2/3 and AC-597-3 codes stay as written in spine-dependent fixtures. |
| **B. Rename or add gateway-only codes in this brief** | Premature public contract. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit) — NO-GO

Until CPQ Decision scores OQ-595-1..OQ-595-10 (owner fills `Owner decision: ___ (drg, time)` or explicitly defers a named fork the same way):

1. **NO-GO:** product code (Java / TypeScript) for ownership checks, role matrices, or queue visibility.
2. **NO-GO:** Flyway / schema for an owner side table or permission catalog.
3. **NO-GO:** feature flags, outbox / relay / Jev enablement.
4. **NO-GO:** new or changed gateway routes / filters for renewals-amendments visibility.
5. **NO-GO:** treating this file, Cluster G, or spine-dependent fixtures as S3 / DRG-581 authorization.
6. Even after forks close: S3 remains separately authorized; approve stays on DRG-591; ADR-016 record-level deferral is lifted only by a scored delivery story; dual-read / NgRx / ramp / S4 holds stay as in `AGENT.md`.

Linear status for DRG-595 stays **In Review** until the owner merges and DoD says otherwise. This brief does **not** mark Done.

## Holds this brief keeps

| Hold | What stays true |
|---|---|
| Product code | **Forbidden** in the PR that carries this file |
| Schema / Flyway | No owner table / permission migration here |
| Flag / deploy | No flag flip; no deploy |
| Gateway | No new visibility route or filter |
| Jev / ramp / dual-read / NgRx | Untouched |
| S3 / S4 / S6 | Not authorized by this brief |
| Approvals | DRG-591 / PL-9 / OQ25 remain parked |

## Out of scope

- Reopening F-595-1..F-595-7 or F-597-3 Adopted dispositions.
- Editing ADR-016, the identity go-live runbook, OpenAPI, or the UI spec.
- Implementing AC-595-1..3 or AC-597-*.
- Mapping ticket words onto Entra roles as an accepted rule.
- DRG-598 phase migration (separate Cluster G story).

## References

- Linear: DRG-595, DRG-597, DRG-591, DRG-581, DRG-537, DRG-648, DRG-607.
- GitHub: Cluster G frame [#656](https://github.com/drgaciw/open-crm-cpq/pull/656); delegated record on `main`.
- Docs: `cluster-g-visibility-queues-migration-decision-frame.md`, `delegated-renewals-amendments-decisions-2026-10-17.md`, `specs/spine-dependent.md`, ADR-016, `identity-go-live.md`, mid-term policy "Audit and visibility".
- Fixtures: `specs/fixtures/spine-dependent/drg-595-*.json`.
