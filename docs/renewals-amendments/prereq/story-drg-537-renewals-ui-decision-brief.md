# DRG-537 — No UI for renewals and amendments: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** reopen the build gate or screen inventory in [`story-drg-537-ui-spec.md`](story-drg-537-ui-spec.md), accept [`DRAFT-openapi-amendments.yaml`](DRAFT-openapi-amendments.yaml), authorize an implementation brief, authorize product code (Angular or Java), open a Flyway migration, flip a feature flag, register a gateway route, enable a continuation facade, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / UI (Cluster G adjacent). **Story:** [DRG-537](https://linear.app/drgamtd-workspace/issue/DRG-537/no-ui-for-renewals-and-amendments). Linear read this pass: **In Review**. Notion: [backlog row](https://app.notion.com/p/3fbf7cb4e4df8134a981f1820a544255) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen):**

| Artifact | Role |
|---|---|
| [story-drg-537-ui-spec.md](story-drg-537-ui-spec.md) ([#620](https://github.com/drgaciw/open-crm-cpq/pull/620), on `main` via [#622](https://github.com/drgaciw/open-crm-cpq/pull/622)) | **Draft UI reading** of the OpenAPI proposal: screens, states, empty/error copy, API call table, and the §1 build gate. Authorizes **no** routes, clients, or flags. This Decision brief does **not** rewrite that inventory |
| [DRAFT-openapi-amendments.yaml](DRAFT-openapi-amendments.yaml) ([#610](https://github.com/drgaciw/open-crm-cpq/pull/610); [DRG-605](https://linear.app/drgamtd-workspace/issue/DRG-605) Done as draft) | HTTP proposal only. `info.version` `0.1.0-draft`, `x-opencrm-status: proposal`. Not an accepted contract, gateway route, or feature flag |
| [oq9-pricing-continuation-capability.md](oq9-pricing-continuation-capability.md) §6 | Pricing gap: today's pricing execute API does not return AC-S5-2 pins; price screen stays blocked |
| [crm-quote-dealtype-mapping.md](../crm-quote-dealtype-mapping.md) | `AMENDMENT` / `RENEWAL` deal-type labels are not a start-continuation flow |
| [renewals-amendments-dev-readiness.md](../renewals-amendments-dev-readiness.md) | First-slice API-first posture; renewals not ready; UI separately gated |
| [mid-term-amendment-policy-spec.md](../mid-term-amendment-policy-spec.md) §3–§4 + V1–V14 | Document transitions and validation rule ids the UI banner may display after an accepted contract |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) + Cluster G | Visibility / queue Adopted cells that constrain later UI; do not reopen here |

**Related Wave-2 drafts (cite only; do not adopt open OQs):**

| Ticket / draft | What this brief must not adopt |
|---|---|
| [DRG-595](https://linear.app/drgamtd-workspace/issue/DRG-595) / [#826](https://github.com/drgaciw/open-crm-cpq/pull/826) | Visibility residual **OQ-595-*** — cite matrix spirit only; do not score ownership / Entra / tenant forks here |
| [DRG-597](https://linear.app/drgamtd-workspace/issue/DRG-597) / [#828](https://github.com/drgaciw/open-crm-cpq/pull/828) | Work-queue residual **OQ-597-*** — cite two saved intent filters on the lifecycle list; do not design assignment side rows or `/queues/*` here |
| [DRG-548](https://linear.app/drgamtd-workspace/issue/DRG-548) / [#854](https://github.com/drgaciw/open-crm-cpq/pull/854) | Lifecycle intent residual **OQ-548-*** — `RENEW` create/submit widen stays on that brief; UI must not invent a RENEW create path ahead of it |
| [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) (S3) | Amendment document HTTP / state machine — separately gated; this brief is not S3 authorization |
| [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) / OQ9 pricing design | Pricing continuation capability / pin contract — price screen waits on an accepted pricing contract, not this UI brief |
| [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) | Legal-review gate — UI spec §9 item 6; no legal-review control invented here |
| [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554) | Co-termination — UI spec sends contract end as `effectiveTo` default; co-term policy picker is a separate story |
| CAM drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) | Flyway **V1_16–V1_21 reserved** — this brief writes **no** CAM migration and claims **no** number in that band |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, frontend feature enablement, or deploy.** No `opencrm.jev.*.enabled` flag is turned on. Dual-read / NgRx / Activities holds in `AGENT.md` stay untouched. An implementation brief for DRG-537 is **blocked** until CPQ Decision scores the open **OQ-537-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-537-* row stays open.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (UI-spec gate)` / `Adopted (delegated)` | Already selected in the UI spec §1 gate, call/delegated Cluster G cells, or landed docs on `main`. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `GitNexus` | This checkout has **no** `.gitnexus/run.cjs` / no indexed repo. Graph impact is **UNKNOWN** (unresolved), not a clean check. |

## Problem

[DRG-537](https://linear.app/drgamtd-workspace/issue/DRG-537/no-ui-for-renewals-and-amendments) (`Linear read` 2026-11-06): High; **In Review**; blocked by DRG-605 (draft OpenAPI — Linear **Done** as the draft landing, contract still `proposal`); related DRG-595 / DRG-597. Issue text: the frontend only has `QuoteDealType` labels; there is no continuation client or screens to start, price, or review a renewal or amendment. Suggested next step on the ticket is a continuation API client and start-renewal / start-amendment flow — that is **product work**, not this Decision brief.

What landed on `main` is the draft UI specification only (`story-drg-537-ui-spec.md`, PR [#620](https://github.com/drgaciw/open-crm-cpq/pull/620) via owner-merged [#622](https://github.com/drgaciw/open-crm-cpq/pull/622)). Hygiene comment on Linear correctly moved Done → **In Review**: the spec alone does not close the ticket.

There is still no owner-scored Decision brief that (a) cites the UI-spec build gate and screen inventory without rewriting them, and (b) lists **residual delivery forks** (OpenAPI acceptance, S3, pricing pins, amend-first vs renewal, client shape, contract entry, pickers, signature default, coupling to #826 / #828 / #854 without adopting their OQs, continuation facade, route freeze, CAM reservation, frontend enablement) that must close before Angular routes, clients, flags, or gateway changes.

Without that split, a later PR could ship screens against the draft YAML, invent a RENEW create UI ahead of DRG-548 / OpenAPI, reuse the quotes `DealType` filter as a lifecycle queue, flip a continuation-facade flag, or claim a CAM Flyway number in the reserved V1_16–V1_21 band.

## Already settled (do not re-decide)

Restated from [`story-drg-537-ui-spec.md`](story-drg-537-ui-spec.md) (`read`), related docs on `main`, and Cluster G / delegated cells that constrain UI. This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | Sellers have **no** Angular continuation / lifecycle / amendment client or screens today. `QuoteDealType` labels and the quotes deal-type filter are **not** a start-renewal / start-amendment flow | Issue; UI spec §3; `crm-quote-dealtype-mapping.md` |
| A2 | The UI specification on `main` is a **draft reading** for reviewers. It is **not** a build order, route change, client, or feature-flag authorization | UI spec opening + §10 |
| A3 | **Build gate (all required):** (1) owner accepts the OpenAPI draft and `x-opencrm-status` leaves `proposal`; (2) S3 HTTP + amendment state machine implemented under that accepted contract; (3) pricing can return AC-S5-2 pins (OQ9 gap closed by a later accepted pricing contract) | UI spec §1 |
| A4 | First-slice seller flow the draft can describe is **amend-in-place** (`intent` fixed `AMEND`). **Renewal** start / price / review screens are **not** specified until an accepted contract accepts intent `RENEW` | UI spec §5, §8 |
| A5 | Start-amendment screen calls **`POST /api/v1/lifecycle-changes`** directly. It does **not** call `POST /quotes/continuations` or enable a continuation facade switch | UI spec §5.2 |
| A6 | Proposed URLs (`/contracts/:id`, `/lifecycle-changes/:id`, `/amendments/:id`) are **names for the UI spec**. They are **not** routes in `app.routes.ts` until an authorized implementation | UI spec §5 |
| A7 | Quotes list / detail / approvals inbox stay quote surfaces. Lifecycle queue is a **different** list; DRG-597 is **not** specified in UI spec §5.5 | UI spec §5.5; Cluster G F-597-* |
| A8 | DRG-595 role enforcement and role-specific screens are **out of** the first UI/HTTP slice as written | UI spec §2, §9; OpenAPI draft |
| A9 | Holds: no product code / Flyway / flag / gateway / frontend enablement / deploy from docs PRs alone; CAM **V1_16–V1_21** reserved by sibling CAM drafts; dual-read / NgRx / Jev / S4 apply holds in `AGENT.md` untouched | AGENT.md; #805–#812 |

## What the tree does today (UI / continuation)

`verified by reading code` / docs at the base SHA.

1. **Routes / nav.** `frontend/apps/crm/src/app/app.routes.ts` and `sidebar.component.ts` have no `contracts`, `amendments`, or `lifecycle-changes` paths. Quote routes remain list / new / `:id` under `frontend/libs/cpq/quotes/`.
2. **Deal types only.** `QuoteDealType` includes `AMENDMENT` and `RENEWAL` with labels. `QuotesListComponent` can filter by deal type. That lists quotes; it does not create a lifecycle change.
3. **No continuation client.** A search-oriented reading of `QuoteService` (list through calculate / line items, as recorded in the UI spec) shows no continuation or lifecycle-change method. `QuoteContinuationController` (`/api/v1/quotes/continuations`) exists in quote-service; **no Angular caller** was recorded in the UI spec pass.
4. **OpenAPI still proposal.** `DRAFT-openapi-amendments.yaml` remains `x-opencrm-status: proposal`. DRG-605 Linear status is **Done** for landing the draft, not for accepting the contract.
5. **S3 / pricing.** S3 amendment HTTP is not authorized by this brief. OQ9 records that current pricing execute does not return AC-S5-2 pins; the price screen depends on a later accepted pricing contract (UI spec §1 item 3, §9 item 5).
6. **CAM tip.** CAM migrations through `V1_15__document_apply_replay_evidence.sql` are present. This brief claims **no** CAM migration and **never** V1_16–V1_21.

### GitNexus (this checkout)

No indexed repository (no `.gitnexus/run.cjs`). Graph impact for any future Angular route, `QuoteService`, lifecycle client, or gateway filter is **UNKNOWN** — unresolved, not low. A later implementation must run impact before editing those symbols. Docs-only change: one new markdown file under `docs/renewals-amendments/prereq/`.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed` under id **OQ-537-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No Adopted cells in this table.** Rows refine **delivery** against A1–A9; they do **not** reopen those contracts. **Do not adopt** open OQs from #826 / #828 / #854 / DRG-609 by scoring a row here. Do **not** rewrite `story-drg-537-ui-spec.md` by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-537-1 | OpenAPI acceptance gate | Keep screens **blocked** until the owner accepts `DRAFT-openapi-amendments.yaml` and `x-opencrm-status` is no longer `proposal` (A3.1). DRG-605 Done ≠ accepted contract | Build Angular against the draft YAML; treat DRG-605 Done as acceptance | Yes — any screen |
| OQ-537-2 | S3 HTTP prerequisite | Keep screens **blocked** until S3 (`POST /contracts/{id}/amendments` + document state machine) is implemented under the **accepted** contract (A3.2). This brief does **not** authorize DRG-581 | Ship UI mocks as production; treat domain tables as S3 HTTP | Yes — document screens |
| OQ-537-3 | Pricing pins / price screen | Keep **price** (and any submit that requires V8 pins) **blocked** until an accepted pricing continuation contract closes the OQ9 gap (A3.3). Cite DRG-609; do not score its forks here | Build price UI on today's `/pricing/execute`; invent pin fields client-side | Yes — price / V8 ACs |
| OQ-537-4 | Amend-first vs renewal UI | First authorized UI increment is **amend-only** (§5.1–§5.4). Renewal start/price/review waits on accepted OpenAPI + DRG-548 / intent allow-list that accepts `RENEW` (A4). Do not adopt OQ-548-* here | Ship RENEW screens now; use quotes `dealType=RENEWAL` as renewal start | Yes — renewal ACs |
| OQ-537-5 | Angular client shape | New dedicated lifecycle / amendment API client(s) under CPQ libs; **do not** overload `QuoteService` parsers (ApiResponse + UUID ids ≠ Spring page + numeric quote ids) (UI spec §4) | Extend `QuoteService` with lifecycle methods; call CAM ports directly from the browser | Yes — client ACs |
| OQ-537-6 | Contract entry / discovery | Deep-link / known `contractId` only until an accepted contract adds a collection or an authorized picker story. Empty URL → empty state, no invented contract row (UI spec §5.1) | Invent `GET /contracts` in this brief; scrape quotes for contract ids | Soft until multi-contract UX |
| OQ-537-7 | Subscription / source-line picker | Leave picker **unspecified** until a separately authorized data story names the read API. Seller-supplied UUIDs stay as in the UI spec; do not invent a catalog of subscriptions here | Build picker on unverified endpoints; skip V3/V4 fields in the form | Soft until picker AC |
| OQ-537-8 | Signature-policy UI default | Keep UI-spec proposal: default shown **`CLICK_THROUGH`**; do not offer `WET` / `E_SIGN` in the first slice. Record that the draft YAML names **no** UI default — acceptance is owner-scored | Auto-default `NOT_REQUIRED`; offer WET/E_SIGN buttons that always 422 | Soft until open-document AC |
| OQ-537-9 | Queue UI coupling (DRG-597) | **Cite** #828: two saved intent-filter views on the sketched lifecycle list after gates. **Do not adopt** OQ-597-*. Do not edit the UI spec here | New `/queues/*` SPA modules; treat quotes DealType filter as the queue (reopens A7) | Soft until queue UI |
| OQ-537-10 | Visibility UI coupling (DRG-595) | **Cite** #826: no role-specific screens in the first increment; owner-check / read-only wiring stay on DRG-595. **Do not adopt** OQ-595-* | Ship sales-ops/finance layouts now; invent Entra roles in the UI brief | Soft until visibility AC |
| OQ-537-11 | Continuation facade / feature flag | Facade switch and any `X-Lifecycle-Change-Id` client stay **off**. No feature flag, env toggle, or proxy change enables continuation-as-lifecycle in this brief (A5) | Enable facade from this docs PR; teach `QuoteService.create` to read lifecycle headers now | Yes — flag / facade |
| OQ-537-12 | Route / nav freeze | Proposed URLs and sidebar items stay **documentation names** until an implementation brief after OQ-537-1..3. No `app.routes.ts` / sidebar edit in docs PRs (A6) | Land empty route stubs now; sidebar “Amendments” without API | Soft (docs) / Yes if stubs ship |
| OQ-537-13 | CAM Flyway / schema | Would-be only: UI stories claim **no** CAM migration. **Never** V1_16–V1_21. Schema stays on S3 / other authorized backend stories | Claim V1_16–V1_21 for UI support tables; ship Flyway from this brief | Yes — any migration |
| OQ-537-14 | Frontend feature enablement | **NO-GO** on routes, clients, feature flags, Nx project enablement, and gateway filters until blocking OQ-537-* close (or owner defers with words+time) | Partial UI behind a dark flag from this brief; “spec landed ⇒ Done” | Yes — release gate |
| OQ-537-IMPL | Implementation brief authorization | Blocked until blocking OQ-537-* (and cited OpenAPI / S3 / pricing / intent where named) close or are explicitly deferred with owner words+time | Authorize implementation from this file or the UI spec alone | Yes |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-537-1 — OpenAPI acceptance gate

| Option | What the owner would be choosing |
|---|---|
| **A. Block until accepted contract (Recommended, Proposed)** | Restates A3.1. Linear DRG-605 Done means the draft file landed, not that `x-opencrm-status` left `proposal`. |
| **B. Build against the draft YAML** | Couples Angular to an unaccepted HTTP shape; rejected by the UI-spec opening. |
| **C. Treat ticket Done as acceptance** | Confuses draft delivery with contract acceptance. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-2 — S3 HTTP prerequisite

| Option | What the owner would be choosing |
|---|---|
| **A. Block document screens until S3 under accepted contract (Recommended, Proposed)** | Restates A3.2. Domain tables / V1_7 presence ≠ S3 HTTP. |
| **B. UI mocks as production** | Sellers get non-authoritative screens; rejected. |
| **C. This brief starts DRG-581** | Out of scope; S3 stays separately gated. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-3 — Pricing pins / price screen

| Option | What the owner would be choosing |
|---|---|
| **A. Block price/submit-on-pins until accepted pricing contract (Recommended, Proposed)** | Restates A3.3 / OQ9 / UI spec §9 item 5. Cite DRG-609 without adopting its OQs. |
| **B. Price UI on today's execute API** | Cannot meet AC-S5-2 / V8. |
| **C. Client-fabricated pin fields** | Breaks digest / audit; rejected. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-4 — Amend-first vs renewal UI

| Option | What the owner would be choosing |
|---|---|
| **A. Amend screens first; renewal waits on contract + intent allow-list (Recommended, Proposed)** | Restates A4 / UI spec §8. Do not adopt OQ-548-* (#854). |
| **B. Ship RENEW UI now** | OpenAPI create enum is `[AMEND]` in-slice; `INTENT_NOT_SUPPORTED_IN_SLICE` for `RENEW`. |
| **C. Quotes `dealType=RENEWAL` as start** | Reopens A1; lists quotes only. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-5 — Angular client shape

| Option | What the owner would be choosing |
|---|---|
| **A. Dedicated lifecycle/amendment client(s) (Recommended, Proposed)** | Matches UI spec §4 envelope / id / money rules. |
| **B. Overload `QuoteService`** | Wrong page parser and id type; high defect risk. |
| **C. Browser → CAM direct** | Bypasses gateway / CSRF lane the draft assumes. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-6 — Contract entry / discovery

| Option | What the owner would be choosing |
|---|---|
| **A. Known `contractId` / deep link only (Recommended, Proposed)** | Matches UI spec §5.1 until a collection exists. |
| **B. Invent collection in this brief** | Premature public contract. |
| **C. Derive from quotes list** | No verified contract id on quote rows in the UI-spec reading. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-7 — Subscription / source-line picker

| Option | What the owner would be choosing |
|---|---|
| **A. Leave unspecified; UUID fields as in UI spec (Recommended, Proposed)** | Avoids inventing an unverified read API. |
| **B. Build picker on guessed endpoints** | Unverified; can fail V2–V4 silently. |
| **C. Omit observed revision / source line** | Breaks V3/V4; rejected. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-8 — Signature-policy UI default

| Option | What the owner would be choosing |
|---|---|
| **A. Show CLICK_THROUGH default; no WET/E_SIGN (Recommended, Proposed)** | Matches UI spec §5.3 proposal; owner still scores because YAML has no UI default. |
| **B. Default NOT_REQUIRED** | Hides the seller accept step; changes first-slice UX. |
| **C. Offer WET/E_SIGN** | First slice cannot execute those policies. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-9 — Queue UI coupling (DRG-597 / #828)

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #828; do not adopt OQ-597-* (Recommended, Proposed)** | Keeps RA-63 on its brief; UI spec §5.5 unchanged here. |
| **B. Close OQ-597-* inside this brief** | Out of ticket scope. |
| **C. Quotes DealType as queue** | Reopens A7 / F-597-5 C. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-10 — Visibility UI coupling (DRG-595 / #826)

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #826; no role screens in first increment; do not adopt OQ-595-* (Recommended, Proposed)** | Restates A8. |
| **B. Close OQ-595-* here** | Out of ticket scope. |
| **C. Ship finance layouts now** | Premature; conflicts with OpenAPI “out of slice”. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-11 — Continuation facade / feature flag

| Option | What the owner would be choosing |
|---|---|
| **A. Facade and flags stay off (Recommended, Proposed)** | Restates A5. No dark launch of continuation-as-lifecycle from this brief. |
| **B. Enable facade from this docs PR** | Product / flag NO-GO violation. |
| **C. Teach QuoteService to read lifecycle headers now** | Implementation without accepted contract. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-12 — Route / nav freeze

| Option | What the owner would be choosing |
|---|---|
| **A. Names only until implementation brief (Recommended, Proposed)** | Restates A6. |
| **B. Empty route stubs on main now** | Still product code; rejected for this docs PR. |
| **C. Sidebar without API** | Dead nav; poor seller UX. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-13 — CAM Flyway / schema

| Option | What the owner would be choosing |
|---|---|
| **A. No CAM migration from UI; never V1_16–V1_21 (Recommended, Proposed)** | Matches sibling Wave-2 reservation (#805–#812). |
| **B. Claim reserved band for UI support tables** | Conflicts with CAM draft reservation. |
| **C. Ship Flyway in this brief** | Schema NO-GO. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-14 — Frontend feature enablement

| Option | What the owner would be choosing |
|---|---|
| **A. Full NO-GO until blocking forks close (Recommended, Proposed)** | Product / flag NO-GO for the Wave-2 docs lane. |
| **B. Dark flag partial UI from this brief** | Still frontend enablement; rejected. |
| **C. Spec on main closes DRG-537** | Hygiene already rejected; Linear stays In Review. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-537-IMPL — Implementation brief authorization

| Option | What the owner would be choosing |
|---|---|
| **A. Blocked until blocking OQ-537-* (+ named prereqs) close or defer (Recommended, Proposed)** | Standard Wave-2 residual pattern. |
| **B. This file or the UI spec alone authorizes build** | Rejected by A2 / A3. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit) — NO-GO

Until CPQ Decision scores OQ-537-1..OQ-537-14 and OQ-537-IMPL (owner fills `Owner decision: ___ (drg, time)` or explicitly defers a named fork the same way):

1. **NO-GO:** product code (TypeScript / Angular / Java) for renewal or amendment UI, continuation clients, or lifecycle clients.
2. **NO-GO:** Flyway / schema — including any CAM number in **V1_16–V1_21**.
3. **NO-GO:** feature flags, frontend feature enablement, outbox / relay / Jev enablement.
4. **NO-GO:** new or changed gateway routes / filters for contracts, amendments, or lifecycle-changes.
5. **NO-GO:** treating this file, `story-drg-537-ui-spec.md`, or `DRAFT-openapi-amendments.yaml` as S3 / DRG-581 / pricing / intent-widen authorization.
6. **NO-GO:** adopting open OQs from #826 / #828 / #854 / DRG-609 by implication.
7. Even after forks close: S3, pricing continuation, and `RENEW` create remain separately authorized; approve stays on DRG-591; dual-read / NgRx / ramp / S4 holds stay as in `AGENT.md`.

Linear status for DRG-537 stays **In Review** until the owner merges and DoD says otherwise. This brief does **not** mark Done.

## Holds this brief keeps

| Hold | What stays true |
|---|---|
| Product code | **Forbidden** in the PR that carries this file |
| Schema / Flyway | No CAM / quote migration; **never** V1_16–V1_21 from this brief |
| Flag / frontend enablement | No flag flip; no route/client enablement |
| Gateway | No new contracts / amendments / lifecycle route or filter |
| Jev / ramp / dual-read / NgRx | Untouched |
| S3 / S4 / S5 / S6 | Not authorized by this brief |
| OpenAPI | Remains `proposal` until a separate acceptance act |
| Approvals | DRG-591 / PL-9 / OQ25 remain parked |

## Out of scope

- Rewriting screen inventories, empty copy, or API tables in `story-drg-537-ui-spec.md`.
- Accepting or editing `DRAFT-openapi-amendments.yaml`.
- Scoring OQ-595-*, OQ-597-*, OQ-548-*, or DRG-609 pricing forks.
- Implementing AC-S3 / AC-S5 or Angular routes.
- Mapping deal-type filters onto lifecycle queues.
- Legal-review controls (DRG-594) or co-term policy pickers (DRG-554).

## Verification (this pass)

- [x] Docs-only file under `docs/renewals-amendments/prereq/`
- [x] Cites `story-drg-537-ui-spec.md`; residual forks Proposed only as **OQ-537-***
- [x] Product / schema / flag / frontend enablement = **NO-GO**; CAM **V1_16–V1_21** unclaimed
- [x] Related Wave-2 drafts cited without adopting open OQs
- [x] Linear left **In Review** (agent must not mark Done)
- [x] GitNexus unavailable / UNKNOWN — not treated as clean
- [x] Secret scan: no credentials in this file

## Owner decision slots

For each **OQ-537-*** row scored: `Owner decision: ___ (drg, time)`.

## References

- Linear: DRG-537, DRG-605, DRG-581, DRG-595, DRG-597, DRG-548, DRG-609, DRG-594, DRG-554, DRG-591.
- GitHub: UI spec [#620](https://github.com/drgaciw/open-crm-cpq/pull/620) / [#622](https://github.com/drgaciw/open-crm-cpq/pull/622); OpenAPI draft [#610](https://github.com/drgaciw/open-crm-cpq/pull/610); visibility [#826](https://github.com/drgaciw/open-crm-cpq/pull/826); queues [#828](https://github.com/drgaciw/open-crm-cpq/pull/828); intent [#854](https://github.com/drgaciw/open-crm-cpq/pull/854); CAM reservation [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812).
- Docs: `story-drg-537-ui-spec.md`, `DRAFT-openapi-amendments.yaml`, `oq9-pricing-continuation-capability.md`, `crm-quote-dealtype-mapping.md`, `renewals-amendments-dev-readiness.md`, `delegated-renewals-amendments-decisions-2026-10-17.md`, mid-term amendment policy §3–§4.
