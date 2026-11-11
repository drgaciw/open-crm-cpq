# DRG-597 — Separate renewals and amendments work queues: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted call 2026-10-16 (#13), reopen Adopted Cluster G cells F-597-1..F-597-5 (or related visibility cells F-595-* / F-597-3 assignment write rule), amend the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md), accept [`specs/spine-dependent.md`](specs/spine-dependent.md), authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a gateway route, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / Cluster G queues. **Story:** [DRG-597](https://linear.app/drgamtd-workspace/issue/DRG-597/separate-renewals-and-amendments-work-queues-on-a-shared-data-model) (RA-63). Linear read this pass: **In Review**. Notion: [RA-63 backlog row](https://app.notion.com/p/3fbf7cb4e4df81c3b8b6cb87614f76dd) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen):**

| Artifact | Role |
|---|---|
| Call 2026-10-16 **(#13)** / Linear DRG-597 | **Adopted baseline:** renewals and amendments are **two separate work queues** (separate views, assignment, SLAs) over **one shared data model** (LifecycleChange spine, AmendmentDocument, contract records). **No duplicate tables per queue.** |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Migration and access + Cluster G disposition F-597-1..5 | **Adopted baseline** for shared-spine queue filters, owner-only assignment/due-date writes, optional due date with no invented SLA duration, and filters on the sketched lifecycle list (no new accepted queue route) |
| [cluster-g-visibility-queues-migration-decision-frame.md](cluster-g-visibility-queues-migration-decision-frame.md) | Historical F-597-* options/rationale; dispositions already indexed. Do not re-litigate Adopted cells |
| [cluster-e-lifecyclechange-model-decision-frame.md](cluster-e-lifecyclechange-model-decision-frame.md) + [DRG-548](https://linear.app/drgamtd-workspace/issue/DRG-548) / [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md) | Spine / intent model frame; other intents stay proposals until an ADR. This brief does not design or accept the intent enum |
| [specs/spine-dependent.md](specs/spine-dependent.md) § DRG-597 + fixtures AC-597-1..3 | Proposed acceptance criteria / fixtures; authorizes no code |
| [story-drg-537-ui-spec.md](story-drg-537-ui-spec.md) § 5.5 | UI sketches one lifecycle list filter; **DRG-597 is not specified there**. Build gate still needs accepted OpenAPI then S3 |
| [story-drg-595-visibility-decision-brief.md](story-drg-595-visibility-decision-brief.md) (draft PR [#826](https://github.com/drgaciw/open-crm-cpq/pull/826)) | Visibility Decision brief — **cite only**. Do **not** adopt its OQ-595-* residual forks here |
| ADR-029 | Proposed `ai-service` agent queue — a **different** queue from DRG-597 |

**In-flight alignment (do not contradict Accepted / draft semantics):**

| Ticket / PR | Linear / GH | What this brief must not contradict |
|---|---|---|
| [DRG-595](https://linear.app/drgamtd-workspace/issue/DRG-595) / [#826](https://github.com/drgaciw/open-crm-cpq/pull/826) | Related visibility | Same visibility matrix for queue reads as document/audit reads once scored; **owner-only** assignment / due-date writes (Adopted F-597-3 B). Do not close or reopen OQ-595-* |
| [DRG-537](https://linear.app/drgamtd-workspace/issue/DRG-537) | Related UI | Two queue **views** couple to the UI spec later; this brief does not edit the UI spec or authorize screens |
| [DRG-548](https://linear.app/drgamtd-workspace/issue/DRG-548) / Cluster E | Related spine | Queue membership for intents other than `RENEW` / `AMEND` waits on an accepted intent model |
| [DRG-535](https://linear.app/drgamtd-workspace/issue/DRG-535) | Related | Issue text still says no amendment document; V1_7 on `main` is the settled schema record |
| [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) (S3) | Todo / separately gated | Queue filters do not start S3 |
| [DRG-591](https://linear.app/drgamtd-workspace/issue/DRG-591) / PL-9 / OQ25 | Parked | Approve stays parked; assignment is not approve |
| [DRG-596](https://linear.app/drgamtd-workspace/issue/DRG-596) | Related automation | 90-day kickoff figure stays renewal automation — not this queue's SLA |
| [DRG-648](https://linear.app/drgamtd-workspace/issue/DRG-648) / spine-dependent fixtures | Prereq | AC-597-* fixtures stay Proposed |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** No `opencrm.jev.*.enabled` flag is turned on. Dual-read / NgRx / Activities holds in `AGENT.md` stay untouched. An implementation brief for DRG-597 is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (#13)` / `Adopted (delegated)` | Already selected by the 2026-10-16 call and/or the 2026-10-17 delegated record / Cluster G disposition. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `GitNexus` | This checkout has **no** `.gitnexus/run.cjs` / no indexed repo. Graph impact is **UNKNOWN** (unresolved), not a clean check. |

## Problem (RA-63)

Call decision 2026-10-16 (#13): renewals and amendments are **two separate work queues** — separate views, assignment, and SLAs — over **one shared data model** (LifecycleChange spine, AmendmentDocument, and contract records). **No duplicate tables per queue.**

Cluster G + the delegated record already adopted the high-level F-597-* defaults (shared-spine filters, `RENEW`/`AMEND` membership, owner-only assignment writes, optional due date, filters on the sketched lifecycle list). There is still no owner-scored Decision brief that (a) cites Adopted #13 and those Adopted cells without reopening them, and (b) lists **residual delivery forks** (status filters, side-row shape, filter query contract, API-vs-UI sequencing outside the first slice, UI coupling to DRG-537, visibility alignment with DRG-595 draft #826 without adopting its OQs, other-intent parking vs Cluster E / DRG-548, SLA notifications, fixture freeze) that must close before product code, schema, flags, or gateway routes.

Without that split, a later API or UI story could invent copy tables, fold every non-`RENEW` intent into the amendments queue, grant sales-ops assignment writes, invent a 90-day SLA from the renewal stub, or ship dedicated `/queues/*` routes as if Cluster G had accepted them.

Linear DRG-597 (`Linear read` 2026-11-06): Medium; **In Review**; related DRG-537 / DRG-548 / DRG-535 / DRG-595 / DRG-607. Attachments: Notion backlog row; Cluster G draft PR [#656](https://github.com/drgaciw/open-crm-cpq/pull/656) (frame later adopted via the delegated record). Wave-2 lane refill comment records docs-only Decision brief scope.

## Already Adopted (do not re-decide)

Restated from call **#13**, the delegated record § Migration and access, and Cluster G disposition (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | Renewals and amendments are **two separate work queues** (separate views, assignment, SLAs) over **one shared data model** | Call 2026-10-16 **(#13)** |
| A2 | Shared model is the **LifecycleChange spine**, **AmendmentDocument**, and **contract** records | #13; V1_7 on `main` |
| A3 | **No duplicate tables per queue** | #13 |
| A4 | Queue membership is a **filter on the shared spine**. Assignment and optional due date, if stored, are a **side row** keyed by `lifecycle_change_id` that does **not** copy the document | F-597-1 A |
| A5 | Renewal queue is **`RENEW` only**. Amendment queue is **`AMEND` only**. Other intents wait | F-597-2 A |
| A6 | Only the **contract owner** may assign and set due dates on owned contracts. Sales ops / finance remain **read-only** for those writes | F-597-3 B; delegated |
| A7 | Queue stores an **optional due date** only. **No invented SLA duration** (90-day kickoff stays DRG-596) | F-597-4 A; delegated |
| A8 | HTTP surface is **two filters** on the sketched `GET /api/v1/lifecycle-changes` list. **No new accepted** `/queues/renewals` or `/queues/amendments` route from Cluster G. Quotes list filtered by `DealType` is **not** the queue | F-597-5 A |
| A9 | Queues are **outside the first slice** (S1–S8 / DRG-579–586). First-slice product shape remains **API-first**; UI is DRG-537 and stays separately gated | Issue notes; `renewals-amendments-dev-readiness.md` |
| A10 | Visibility for who may **see** a queue row comes from DRG-595 Adopted cells; this brief does not reopen F-595-* or adopt draft #826 OQ-595-* | Related DRG-595; Cluster G |
| A11 | Holds: no product code / Flyway / flag / gateway route / deploy from docs PRs alone; dual-read / NgRx / Jev / S4 apply holds in `AGENT.md` untouched | Cluster holds; AGENT.md |

## What the tree does today (queues / spine)

`verified by reading code` / docs at the base SHA.

1. V1_7 stores one `amendment_documents` row per `lifecycle_change_id` (`uq_amendment_document_lifecycle`). `intent` is constrained to `RENEW`, `AMEND`, `CANCEL`, `RESTRUCTURE`, `TRANSFER`, `SUSPEND`, `RESUME`, `TRUE_UP`, `CONVERT`. Indexes on `(contract_id, status)`, `(status, expires_at)`, and `(tenant_id, legal_hold, retention_until)` are marked work-queue support in the field schema; `expires_at` is **not** an SLA duration.
2. There is **no** renewals-queue table and **no** amendments-queue table. No `queue_assignments` side row on `main`.
3. `QuoteContinuationRequest.Operation` stores both `RENEWAL` and `EXTENSION` as `DealType.RENEWAL`. That collapse is a deal type, **not** a queue membership rule (Adopted A5 keeps EXTENSION out of the renewal queue until an intent model accepts otherwise).
4. UI spec §5.5 sketches `GET /api/v1/lifecycle-changes` with optional `contractId`, `status`, `intent` and explicitly says DRG-597 is not specified there. OpenAPI / S3 remain proposals / unstarted.
5. Spine-dependent fixtures AC-597-1..3 stay **Proposed** (`ASSIGNMENT_FORBIDDEN` on non-owner assignment write).
6. ADR-029 durable agent work queue is a different Proposed surface under `ai-service`.

### Outside the first slice — API vs UI (record only)

| Layer | First-slice (S1–S8) | DRG-597 (this story) |
|---|---|---|
| Product code | Separately authorized per S* story | **NO-GO** until OQ-597-* close |
| Queue **read** APIs (filters on lifecycle list) | Not in first slice | Residual fork OQ-597-4 / OQ-597-5 — may precede UI once authorized |
| Queue **assignment** writes (side row) | Not in first slice | Residual fork OQ-597-1; owner-only (A6) |
| Queue **UI** (two views, assignment UX) | First slice is API-oriented; screens are DRG-537 | Couples to DRG-537 (OQ-597-6); this brief does not authorize screens |
| Visibility enforcement | Out of first HTTP/UI slice per OpenAPI / UI spec | Cite DRG-595 / #826; do not adopt OQ-595-* here |

Meaning for implementers after forks close: a later implementation brief may ship **queue filter query semantics on the lifecycle-changes list API** before any DRG-537 screen, but must not invent a second data model, must not treat the quotes list as the queue, and must not claim UI authorization from this file.

### GitNexus (this checkout)

No indexed repository (no `.gitnexus/run.cjs`). Graph impact for any future CAM queue-filter or assignment symbol is **UNKNOWN** — unresolved, not low. A later implementation must run impact before editing list controllers, assignment writers, or gateway filters.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No Adopted cells in this table.**

| ID | Question | Recommended (Proposed) | Reject / avoid | Blocks implementation? |
|---|---|---|---|---|
| OQ-597-1 | Assignment / due-date side-row shape | Side row keyed by `lifecycle_change_id` with `assignee_user_id`, `due_on` (date), `updated_by`, `updated_at`. Exact migration number is **would-be only** — **NO-GO** in this brief | Copy document into queue tables (reopens A3/A4); put assignee on thin header | Yes — AC-597-3 |
| OQ-597-2 | Which document statuses appear in each queue | Default list filter includes non-terminal working statuses (`DRAFT`, `IN_REVIEW`, `APPROVED`, and apply outcomes the list already displays). Terminal `REJECTED` / `WITHDRAWN` / fully `APPLIED` are **opt-in** via explicit `status` filter, not the saved Renew/Amend queue preset | Hide all post-`EXECUTED` rows; invent queue-local status enums | Soft for read ACs; hard for UX consistency with OQ5 display |
| OQ-597-3 | Due-date semantics | Optional `due_on` (calendar date, tenant TZ deferred). Clearing `due_on` is allowed for owner. Overdue is a **client/list sort hint**, not an auto-transition | Fixed 90-day SLA (reopens A7 / DRG-596); auto-withdraw on overdue | Soft until notifications ship |
| OQ-597-4 | Filter query contract | Two **saved filter presets**: renewals ⇒ `intent=RENEW`; amendments ⇒ `intent=AMEND`. Optional `status`, `assignee`, `due_on` query params on the same lifecycle-changes collection. **No** new `/queues/*` path (A8) | New queue resources now; quotes `DealType` filter as queue (reopens A8) | Yes — OpenAPI / list ACs |
| OQ-597-5 | Sequencing: queue APIs vs UI (outside first slice) | After forks close, **queue read filters may ship in an API story before DRG-537 UI**. Assignment writes require delivered ownership check (DRG-595 A7 spirit). UI screens wait on DRG-537 build gate. This brief authorizes neither | Treat this brief as UI start; ship UI without list API; claim first-slice includes queues | Yes — release gate |
| OQ-597-6 | UI coupling to DRG-537 | Two saved views / filter chips on the lifecycle list sketched in UI spec §5.5. No dedicated queue app routes in this brief. Do **not** edit `story-drg-537-ui-spec.md` here | Separate SPA modules that copy rows; invent screens without OpenAPI | Soft until UI story |
| OQ-597-7 | Visibility alignment with DRG-595 | **Cite** draft #826: same visibility matrix for queue filters and document reads; assignment writes stay owner-only (A6/A10). **Do not adopt** OQ-595-1..10 in this brief | Close OQ-595-* here; grant sales-ops assignment write; queues owner-only while documents are tenant-wide read-only | Soft for reads; hard for AC-597-3 |
| OQ-597-8 | Other intents / EXTENSION / Cluster E | Keep Adopted A5. `CANCEL`, `RESTRUCTURE`, `TRANSFER`, `SUSPEND`, `RESUME`, `TRUE_UP`, `CONVERT`, and continuation `EXTENSION`/`EXPANSION`, stay in **neither** queue until DRG-548 / lifecycle-intents ADR accepts membership. Do not decide DRG-546 inside this brief | Fold EXTENSION into renewals because `DealType` collapses; dump every non-`RENEW` into amendments | Yes — AC-597-2 |
| OQ-597-9 | SLA beyond optional due date | No duration, escalation job, or notification topic in this brief. Optional `due_on` only (A7). Any reminder/escalation is a **later** story after DRG-596 / ops policy | Adopt stub 90-day as queue SLA; wire Kafka reminder here | Soft |
| OQ-597-10 | Fixture / error-code freeze | Keep Proposed fixture codes `ASSIGNMENT_FORBIDDEN` (and visibility codes from AC-595 / #826 when those land) until an implementation brief; do not invent new public queue codes here | Rename codes now; add gateway-only queue codes in this brief | Soft (docs) |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-597-1 — Assignment / due-date side-row shape

| Option | What the owner would be choosing |
|---|---|
| **A. Side row keyed by `lifecycle_change_id` (Recommended, Proposed)** | Delivers A4 without copying the document. Migration number is settled only in a later implementation PR — **NO-GO** here. |
| **B. Two queue tables that copy the document** | Reopens A3 / F-597-1 B. |
| **C. Columns on the thin contract header** | Conflicts with ADR-035 thin-header rule / OQ3 spirit. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-597-2 — Status membership of the saved queue presets

| Option | What the owner would be choosing |
|---|---|
| **A. Working statuses in preset; terminals opt-in (Recommended, Proposed)** | Matches “work queue” without hiding apply outcomes operators already need to see. OQ5 meanings of `PARTIALLY_APPLIED` / `APPLY_BLOCKED` stay display-only on the queue. |
| **B. Every status including terminals in the preset** | Noisy; conflicts with “work” framing. |
| **C. Queue-local status vocabulary** | Invents a second state machine; rejected. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-597-3 — Due-date semantics

| Option | What the owner would be choosing |
|---|---|
| **A. Optional `due_on`; clearable; overdue is sort/hint only (Recommended, Proposed)** | Restates A7. No auto-transition. |
| **B. Due date = end date minus 90 days** | Reopens F-597-4 B / pulls DRG-596 into this story. |
| **C. No due date field ever** | Reopens F-597-4 C against call “SLAs” wording that Cluster G resolved as optional due date. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-597-4 — Filter query contract

| Option | What the owner would be choosing |
|---|---|
| **A. Presets = intent filters on existing lifecycle-changes collection (Recommended, Proposed)** | Restates A8. Assignment/due filters are query params on the same collection once the side row exists. |
| **B. New `/queues/renewals` and `/queues/amendments` resources in this brief** | Reopens F-597-5 B. |
| **C. Quotes list by `DealType`** | Reopens F-597-5 C; confuses quote continuation with LifecycleChange. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-597-5 — API vs UI sequencing (outside first slice)

| Option | What the owner would be choosing |
|---|---|
| **A. Queue read API may precede UI; UI waits on DRG-537; neither authorized here (Recommended, Proposed)** | Records issue “outside first slice (API only)” without claiming first-slice scope. Ownership check gates assignment writes. |
| **B. This brief starts DRG-537 screens** | Out of scope; UI spec explicitly excludes DRG-597. |
| **C. Queues are in S1–S8** | Contradicts A9 / dev-readiness. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-597-6 — UI coupling to DRG-537

| Option | What the owner would be choosing |
|---|---|
| **A. Two saved views / chips on the sketched lifecycle list (Recommended, Proposed)** | Couples without editing the UI spec in this PR. |
| **B. Dedicated queue app routes/modules now** | Premature; needs OpenAPI + S3 gate first. |
| **C. No UI ever; API-only forever** | Contradicts call “separate views” once DRG-537 is unblocked. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-597-7 — Visibility alignment with DRG-595 (#826)

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #826; same matrix; do not adopt OQ-595-* here (Recommended, Proposed)** | Keeps RA-61 and RA-63 aligned without stealing DRG-595's residual forks. |
| **B. Close OQ-595-* inside this brief** | Out of ticket scope; conflicts with “cite, do not adopt its OQs”. |
| **C. Sales ops may write assignment** | Reopens F-597-3 A. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-597-8 — Other intents and Cluster E / DRG-548

| Option | What the owner would be choosing |
|---|---|
| **A. Keep RENEW/AMEND-only membership; others wait (Recommended, Proposed)** | Restates A5. Consistent with Cluster E / lifecycle-intents still Proposed. |
| **B. EXTENSION joins renewals because deal type collapses** | Decides DRG-546 inside this frame; rejected by Cluster G recommendation. |
| **C. Every non-`RENEW` intent is an amendment-queue row** | Reopens F-597-2 C; breaks AC-597-2. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-597-9 — SLA beyond optional due date

| Option | What the owner would be choosing |
|---|---|
| **A. No duration / escalation / notification in this brief (Recommended, Proposed)** | Restates A7. Later ops story may add reminders without changing membership rules. |
| **B. Wire 90-day SLA job now** | DRG-596 leak; unauthorized job. |
| **C. SLA is approve SLA under DRG-591** | Confuses assignment due date with parked approve. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-597-10 — Fixture / error-code freeze

| Option | What the owner would be choosing |
|---|---|
| **A. Keep Proposed fixture codes until implementation brief (Recommended, Proposed)** | AC-597-3 `ASSIGNMENT_FORBIDDEN` stays as written. |
| **B. Rename or add gateway-only queue codes in this brief** | Premature public contract. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit) — NO-GO

Until CPQ Decision scores OQ-597-1..OQ-597-10 (owner fills `Owner decision: ___ (drg, time)` or explicitly defers a named fork the same way):

1. **NO-GO:** product code (Java / TypeScript) for queue filters, assignment side rows, or queue UI.
2. **NO-GO:** Flyway / schema for `queue_assignments` or any queue copy table.
3. **NO-GO:** feature flags, outbox / relay / Jev enablement.
4. **NO-GO:** new or changed gateway routes / filters for renewals-amendments queues (including `/queues/*`).
5. **NO-GO:** treating this file, Cluster G, or spine-dependent fixtures as S3 / DRG-581 / DRG-537 authorization.
6. Even after forks close: S3 and DRG-537 remain separately authorized; approve stays on DRG-591; DRG-595 residual OQs stay on that brief; dual-read / NgRx / ramp / S4 holds stay as in `AGENT.md`.

Linear status for DRG-597 stays **In Review** until the owner merges and DoD says otherwise. This brief does **not** mark Done.

## Holds this brief keeps

| Hold | What stays true |
|---|---|
| Product code | **Forbidden** in the PR that carries this file |
| Schema / Flyway | No queue / assignment migration here |
| Flag / deploy | No flag flip; no deploy |
| Gateway | No new queue route or filter |
| Jev / ramp / dual-read / NgRx | Untouched |
| S3 / S4 / S6 | Not authorized by this brief |
| Approvals | DRG-591 / PL-9 / OQ25 remain parked |
| First slice | Queues remain outside S1–S8 |

## Out of scope

- Reopening call #13 or F-597-1..F-597-5 Adopted dispositions.
- Adopting or closing OQ-595-* from draft PR #826.
- Editing ADR-016, OpenAPI, or the UI spec.
- Implementing AC-597-1..3.
- Deciding the DRG-548 intent ADR or DRG-546 EXTENSION collapse.
- DRG-598 phase migration (separate Cluster G story).
- ADR-029 agent work queue.

## References

- Linear: DRG-597, DRG-595, DRG-548, DRG-537, DRG-535, DRG-591, DRG-581, DRG-596, DRG-607, DRG-648.
- GitHub: Cluster G frame [#656](https://github.com/drgaciw/open-crm-cpq/pull/656); DRG-595 Decision brief draft [#826](https://github.com/drgaciw/open-crm-cpq/pull/826); delegated record on `main`.
- Docs: `cluster-g-visibility-queues-migration-decision-frame.md`, `cluster-e-lifecyclechange-model-decision-frame.md`, `delegated-renewals-amendments-decisions-2026-10-17.md`, `specs/spine-dependent.md`, `specs/lifecycle-intents.md`, `story-drg-537-ui-spec.md`, `renewals-amendments-dev-readiness.md`.
- Fixtures: `specs/fixtures/spine-dependent/drg-597-*.json`.
