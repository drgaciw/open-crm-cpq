# DRG-599 — Amendment SKU mapping table: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** accept [`specs/catalog-model.md`](specs/catalog-model.md), does **not** accept [`specs/spine-dependent.md`](specs/spine-dependent.md) defaults as shipped code, does **not** reopen Call decision 2026-10-16 (#16) ownership/hold cells, authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a route, wire an apply gate, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / Cluster A mapping. **Story:** [DRG-599](https://linear.app/drgamtd-workspace/issue/DRG-599/amendment-sku-mapping-table-product-team-owned-sales-disputes) (RA-65). Linear read this pass: **In Review**. Notion mirror: [RA-65](https://app.notion.com/p/3fbf7cb4e4df81afb618eeb1127ce55f) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen Adopted cells):**

| Artifact | Role |
|---|---|
| Linear DRG-599 body — **Call decision 2026-10-16 (#16)** | **Adopted** ownership + contested-hold contract (restated below as A1–A2). Scope-to-design bullets remain residual forks |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Catalog and policy + Cluster A 599-F1..F5 | **Adopted baseline dispositions** (catalog relationship home; Active/Contested/Resolved with product resolve; whole-document hold at gates; same-SKU needs no mapping; pin at pricing). Restated as A3–A8. Does **not** ship schema or an apply gate |
| [cluster-a-amendment-modes-and-policy-decision-frame.md](cluster-a-amendment-modes-and-policy-decision-frame.md) §4 | Historical 599-F1..F5 rationale; dispositions already indexed |
| [story-drg-576-product-relationship-and-eligibility-decision-frame.md](story-drg-576-product-relationship-and-eligibility-decision-frame.md) | Relationship vocabulary candidate home; does **not** close DRG-599 dispute/hold delivery |
| [specs/spine-dependent.md](specs/spine-dependent.md) DRG-599 + DRG-649 | Proposed AC-599-1..4 / AC-649-1..3 and 649-F* residuals; authorizes no code |
| [specs/catalog-model.md](specs/catalog-model.md) | Proposal only; OQ8 leaves publish rights on DRG-599 |
| [specs/partial-apply.md](specs/partial-apply.md) / OQ5 | Contested mapping is **not** an OQ5 `BLOCKED` unit |

**In-flight alignment (do not contradict Adopted; do not unpark):**

| Ticket | Linear | What this brief must not contradict |
|---|---|---|
| [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576) | Related | Catalog relationship / eligibility model; candidate home for mappings |
| [DRG-648](https://linear.app/drgamtd-workspace/issue/DRG-648) | Spine-dependent cluster spec | Parent of the DRG-599 section on `main` |
| [DRG-649](https://linear.app/drgamtd-workspace/issue/DRG-649) | Follow-up deeper dispute/hold | Owns SLA / notifications / write-rights residual depth |
| [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) | Partial apply | Distinct hold; contested mapping ≠ per-line partial apply |
| [DRG-590](https://linear.app/drgamtd-workspace/issue/DRG-590) | **PARKED** | Conditional rules on top of mappings — do **not** unpark |
| [DRG-591](https://linear.app/drgamtd-workspace/issue/DRG-591) | **PARKED** | Broader approvals — do **not** unpark; resolver stays product-team |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, apply-gate wiring, or deploy.** No `opencrm.jev.*.enabled` flag is turned on. Dual-read and ramp holds are untouched. An implementation brief for DRG-599 / DRG-649 mapping persistence or dispute hold is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (#16)` | Call decision 2026-10-16 (#16) cell. Restated; **not** re-opened. |
| `Adopted (delegated)` | Selected in the 2026-10-17 delegated record. Restated; **not** re-opened. |
| `Status: Proposed` | Residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |

## Problem (RA-65)

Upgrade, swap, replacement, and conversion lines need a durable original→amendment SKU map. Call #16 already decides **who owns** that map and that a **contested** map **holds** the amendment. What remains un-scored for an implementation gate is the residual design: where the rows live relative to DRG-576 relationship types, column shape, dispute workflow (raise / status / SLA / audit / notifications), how the hold bites relative to DRG-565 partial apply, and when first-slice same-SKU lines stay out of scope.

Without a scored Decision brief that separates those **Adopted** cells from **Proposed** residuals, an implementation story could invent a second mapping store, treat contested lines as OQ5 `BLOCKED` units, unpark DRG-590/591, or wire an apply gate before owner forks close.

Linear DRG-599 (`Linear read` 2026-11-06): High; **In Review**; relations include DRG-565, DRG-576, DRG-590, DRG-591, DRG-607, DRG-648, DRG-649. Attachments: Notion RA-65; Cluster A draft #650.

## Already Adopted (do not re-decide)

Restated from Call #16 and the delegated record (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | An **amendment SKU mapping** defines how a SKU on the original contract maps to the SKU(s) on an amendment (upgrade / swap / replacement). It is **owned by the product team** | **Adopted (#16)** |
| A2 | **Sales can dispute** a mapping. A **contested mapping holds the amendment**: no execution and no apply while **any** line uses a contested mapping, until product resolves the dispute | **Adopted (#16)** |
| A3 | First-slice **Add / Update / Remove on the same SKU** do **not** use mappings; mappings apply once swaps, upgrades, or conversions are in scope | **Adopted (#16)** Notes; Cluster A 599-F4 baseline |
| A4 | Dispute **resolver role is product-team**; broader approval design stays in parked **DRG-591** — do not unpark | **Adopted (#16)** Scope; parked hold |
| A5 | Conditional rules on top of the mapping stay in parked **DRG-590** — do not unpark | **Adopted (#16)** Notes; OQ24 |
| A6 | Mapping/dispute **gates** include the immediate **pre-execution** check serialized with dispute changes; revalidate at submit, execute, and apply | **Adopted (delegated)** § Catalog and policy; 599-F3 baseline |
| A7 | Product-service owns one versioned relationship/eligibility **read** (DTO + version pins); shared DB read excluded; never returns a price | **Adopted (delegated)** § Catalog and policy |
| A8 | Baseline dispositions 599-F1..F5 (catalog relationship home; `Active`/`Contested`/`Resolved` with product resolve; whole-document hold; SKU-change-only requirement; pin at pricing) are selected as **design defaults** — they still require residual OQ scoring below before schema / apply-gate code | **Adopted (delegated)** Cluster A index |
| A9 | Contested mapping is a **different hold** from OQ5 partial-apply `BLOCKED` | Partial-apply specs; Cluster A; DRG-576 frame |
| A10 | Holds: no product code from docs PRs alone; no dual-read flip; no Jev enablement; S3/S4/S6 not authorized by this story; DRG-609 design not accepted for conversion price | AGENT.md / Cluster holds |

## What the tree does today (mapping / dispute)

`verified by reading code` at the base SHA.

1. Product-service migrations tip at `V1_8__allow_canonical_ramp_product_types.sql`. `CREATE TABLE` names in `V1__create_product_schema.sql` have no `product_relationship` / mapping table. Tree search for `ProductRelationship` / `product_relationship` under `backend/services/product-service` returned **no matches**.
2. CAM `chk_amendment_line_action` in `V1_7__contract_documents.sql` allows `SWAP` / `UPGRADE` / `DOWNGRADE` tokens. The check is **not** a mapping table and does not authorize those actions in the first slice (OQ12).
3. [`specs/spine-dependent.md`](specs/spine-dependent.md) on `main` carries Proposed AC-599-1..4 fixtures (`drg-599-*.json`) and DRG-649 AC-649-1..3 fixtures (`sku-mapping-*.json`). Fixture status fields remain `Proposed` / `Owner decision: ___ (drg, time)`.
4. [`specs/catalog-model.md`](specs/catalog-model.md) remains a proposal; dispute-and-hold is explicitly out of that slice.
5. No apply-gate, dispute state machine, or `MAPPING_CONTESTED` error path is implemented in product code on this `main`.

### Docs-only change impact

This branch adds one markdown file under `docs/renewals-amendments/prereq/`. No production symbol is edited. A later implementation must run GitNexus `impact` before changing product-service schema, CAM transition guards, or apply gates.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No new Adopted cells in this table. Rows refine #16 **scope-to-design** and DRG-649 residuals against the Adopted baselines above; they do not reopen A1–A2.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ1 | Mapping **home**: DRG-576 relationship types vs separate amendment SKU mapping table vs dual store | Affirm delegated 599-F1 **A**: one effective-dated **catalog relationship**; dispute status on that version; do **not** add a second commercial overlay | Separate amendment mapping table only (599-F1 B); catalog row **plus** commercial overlay (599-F1 C) | Yes — any writer / Flyway |
| OQ2 | **Column shape** inside the chosen home (source SKU, target SKU(s), effective dates, version) | Source SKU + one-or-more target SKUs + `effective_from` / optional `effective_to` + immutable **version id**; 1:N targets allowed for replacement bundles | Force 1:1 only; omit effective dating; mutable in-place row without version | Yes — schema / pin |
| OQ3 | Who may **write** a mapping version (649-F1) | Only `PRODUCT` writes; `SALES` receives 403 `MAPPING_WRITE_FORBIDDEN` | Second catalog-admin role; sales may stage unpublished drafts | Yes — AC-649-1 |
| OQ4 | How a dispute **opens** (649-F2) | `SALES` opens on an `ACTIVE` pinned version → `CONTESTED` + one audit record | Only `PRODUCT` opens; either role opens | Yes — AC-649-2 |
| OQ5 | Dispute **status set** residual | Keep three: `ACTIVE` / `CONTESTED` / `RESOLVED`; product clears; sales cannot resolve (403 `DISPUTE_RESOLVE_FORBIDDEN`) | Add `REJECTED` (hold remains); allow sales to clear | Yes — AC-599-2 |
| OQ6 | **SLA** clock (649-F3) | No clock; contested does not expire or auto-resolve | Clock starts at open (duration owner-picked); expiry auto-resolves | Soft for first hold; hard for SLA ACs |
| OQ7 | **Notifications** (649-F4) | No sender; audit record is the only open side effect | Notify `PRODUCT` on open / sales on resolve; notify a named queue | Soft |
| OQ8 | **Audit** record contents (649-F6) | Append-only per open and per resolve attempt (including refused sales resolve): actor role, from/to state, relationship version, time; no price/term | Audit only on successful writes | Soft for AC-649-2; hard for refused-resolve evidence |
| OQ9 | Where the **hold bites** vs DRG-565 partial apply | Whole document; recheck pinned version before `IN_REVIEW`, immediately before `EXECUTED`, and before apply; **not** an OQ5 `BLOCKED` unit; no per-line partial apply of contested lines | Apply-only hold; treat contested lines as partial-apply units | Yes — A2 / A6 / A9 |
| OQ10 | Hold **state field** naming (649-F5) | Lifecycle state unchanged; separate `hold_state=MAPPING_HOLD` while pinned version is `CONTESTED`; `apply_allowed=false`; gate returns 409 `MAPPING_CONTESTED` | New lifecycle state `HELD`; only `apply_allowed=false` with no `hold_state` | Soft for storage; hard for AC-649-3 |
| OQ11 | **Pin vs live** + contest after `EXECUTED` before apply | Pin relationship version at pricing; gates recheck **that pin**. Owner must reconcile: a contest raised after `EXECUTED` but before apply — either holds apply (599-F3 A) or executed immutability exempts apply (tension with 599-F5). **Recommended:** hold apply on contested pin until product resolves; do not rewrite an executed commercial envelope | Live row at apply; ignore post-execute contests | Yes — apply gate |
| OQ12 | Sequencing with DRG-576 / DRG-649 / catalog-model | Close OQ1–OQ11 here; DRG-576 supplies relationship vocabulary/read API; DRG-649 carries deeper dispute ACs; **do not** accept `catalog-model.md` wholesale from this brief | Ship mapping table before DRG-576 read API; accept catalog-model as ADR now | Yes — delivery order |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ1 — Mapping home (table vs DRG-576 relationship types)

| Option | What the owner would be choosing |
|---|---|
| **A. Catalog relationship (Recommended, Proposed)** | Affirms delegated 599-F1 A / Catalog §: one effective-dated product-service relationship version; dispute status points at that version. Aligns with DRG-576 vocabulary without a second store. |
| **B. Separate amendment SKU mapping table** | Matches the literal “table” wording in the #16 title; risks divergence from catalog relationships. |
| **C. Dual store (catalog + commercial overlay)** | Sales-disputable overlay on top of catalog; two sources of truth for the same pair. |

**Recommended (Proposed):** A. Does **not** reopen A1 ownership or A2 hold. `Owner decision: ___ (drg, time)`.

### OQ2 — Column shape

| Option | What the owner would be choosing |
|---|---|
| **A. Source + target SKU(s) + effective dating + immutable version (Recommended, Proposed)** | Covers #16 “source SKU, target SKU(s), effective dates, version”. Allows 1:N targets for multi-SKU replacements. |
| **B. 1:1 targets only** | Simpler; fails replacement bundles that need multiple targets. |
| **C. Mutable in-place row** | Breaks pin-at-pricing (A8) and auditability. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ3 — Who writes a mapping version

| Option | What the owner would be choosing |
|---|---|
| **A. PRODUCT only (Recommended, Proposed)** | Affirms A1 write ownership. AC-649-1. |
| **B. Second catalog-admin role** | Needs a named role this brief does not invent. |
| **C. Sales stages unpublished drafts** | Softens A1; still needs PRODUCT publish. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ4 — How a dispute opens

| Option | What the owner would be choosing |
|---|---|
| **A. Sales opens on ACTIVE pin (Recommended, Proposed)** | Affirms A2 “sales can dispute”. AC-649-2. |
| **B. Only PRODUCT opens** | Sales request is out-of-band; weakens A2. |
| **C. Either role opens** | Wider than A2; still PRODUCT resolves (A4). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ5 — Status set residual

| Option | What the owner would be choosing |
|---|---|
| **A. ACTIVE / CONTESTED / RESOLVED; product clears (Recommended, Proposed)** | Affirms 599-F2 A / A4 / A8. Sales resolve → 403 `DISPUTE_RESOLVE_FORBIDDEN`. |
| **B. Add REJECTED** | Product refuses the mapping; hold remains until a new ACTIVE version is pinned. |
| **C. Sales clears the hold** | Contradicts A4. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ6 — SLA clock

| Option | What the owner would be choosing |
|---|---|
| **A. No clock (Recommended, Proposed)** | 649-F3 A. Contested stays until PRODUCT resolves. |
| **B. Clock without auto-resolve** | Duration is a later owner integer. |
| **C. Expiry auto-resolves** | Risks silent clear of A2 hold. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ7 — Notifications

| Option | What the owner would be choosing |
|---|---|
| **A. No sender (Recommended, Proposed)** | 649-F4 A. Audit only. |
| **B. Notify PRODUCT on open / sales on resolve** | Needs a notification channel this brief does not design. |
| **C. Notify a queue** | Queue name deferred. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ8 — Audit contents

| Option | What the owner would be choosing |
|---|---|
| **A. Open + resolve attempts including refusals (Recommended, Proposed)** | 649-F6 A. Supports AC-599-2 refused sales resolve. |
| **B. Successful writes only** | Loses refused-resolve evidence. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ9 — Hold vs DRG-565 partial apply

| Option | What the owner would be choosing |
|---|---|
| **A. Whole-document mapping hold; not OQ5 BLOCKED (Recommended, Proposed)** | Affirms A2 / A6 / A9. Contested lines do **not** become partial-apply units. |
| **B. Apply-only hold** | Allows execute while contested — conflicts with A2. |
| **C. Per-line partial apply** | Collapses into DRG-565; contradicts A2 “any line”. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ10 — Hold state field

| Option | What the owner would be choosing |
|---|---|
| **A. `hold_state=MAPPING_HOLD` beside lifecycle (Recommended, Proposed)** | 649-F5 A / AC-649-3. |
| **B. New lifecycle `HELD`** | Expands the CAM state machine beyond current S3 text. |
| **C. `apply_allowed` only** | Weaker observability for queues/UI. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ11 — Pin vs live / post-EXECUTED contest

| Option | What the owner would be choosing |
|---|---|
| **A. Pin at pricing; contested pin holds apply even after EXECUTED (Recommended, Proposed)** | Keeps A2 apply half of the hold. Does not rewrite the executed commercial envelope; apply simply refuses with `MAPPING_CONTESTED` until PRODUCT resolves the **pinned** version. |
| **B. Executed documents ignore later contests for apply** | Favours immutability wording; weakens A2 apply half. |
| **C. Live row at apply** | A later catalog edit changes apply behaviour; breaks pin semantics (A8). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ12 — Sequencing

| Option | What the owner would be choosing |
|---|---|
| **A. Score OQ1–OQ11; DRG-576 read API; DRG-649 deeper ACs; catalog-model stays proposal (Recommended, Proposed)** | Matches A7 delivery note and spine-dependent layering. |
| **B. Mapping Flyway before DRG-576 read contract** | Orphans the delegated HTTP read. |
| **C. Accept catalog-model wholesale now** | Out of scope for this brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit NO-GO)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed.
2. CPQ Decision must score **OQ1–OQ12**. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **NO-GO** on:
   - product Java/TS code for mapping CRUD, dispute workflow, or hold enforcement
   - Flyway / schema for `product_relationship` or any amendment SKU mapping table
   - feature flags enabling mapping or dispute paths
   - CAM / quote **apply-gate** or execute-transition wiring that keys off `MAPPING_CONTESTED` / `MAPPING_HOLD`
   - Drools / conditional rules (DRG-590 stays parked)
   - broader approvals work (DRG-591 stays parked)
4. Even after forks close: A1–A10 hold; first-slice same-SKU lines still need no mapping; conversion **price** stays pending non-accepted DRG-609 design; S3/S4/S6 need their own authorization.
5. Linear status for DRG-599 stays **In Review** until the owner merges and DoD says otherwise. This brief does **not** mark Done.

## Out of scope

- Creating any migration or Java type for relationships / mapping / dispute.
- Editing CAM transition guards, apply services, or `chk_amendment_line_action` semantics.
- Accepting `catalog-model.md`, unparking DRG-590/591, or authorizing S3/S4/S6.
- Designing a dispute UI beyond naming that it is absent.
- Pricing, credit, or ARR arithmetic for swaps/upgrades/conversions (DRG-609 / DRG-575).
- Reopening Call #16 A1–A2 or delegated Catalog ownership of the versioned read.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-599 body / relations / In Review | Linear, this pass |
| Call #16 ownership + contested hold | DRG-599 description |
| Delegated Catalog § + 599-F1..F5 index | `delegated-renewals-amendments-decisions-2026-10-17.md` |
| Cluster A §4 forks | `cluster-a-amendment-modes-and-policy-decision-frame.md` |
| DRG-576 frame overlap note | `story-drg-576-product-relationship-and-eligibility-decision-frame.md` |
| Spine-dependent AC-599 / AC-649 + fixtures | `specs/spine-dependent.md`; `fixtures/spine-dependent/drg-599-*.json`; `sku-mapping-*.json` |
| Catalog proposal OQ8 | `specs/catalog-model.md` |
| Partial-apply exclusion | `specs/partial-apply.md` |
| No `product_relationship` on product-service | migrations `V1`–`V1_8`; tree search |
| Action tokens without mapping table | CAM `V1_7__contract_documents.sql` |

## Unverified in this pass

- Any runtime transition, HTTP status, or live database catalog.
- Whether a future product-service migration number is `V1_9` or later in every environment.
- AutoJev tools (not connected in this environment); no AutoJev verdict is claimed.
- Browser UAT / chaos scripts (not applicable to docs-only).
