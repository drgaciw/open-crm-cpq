# DRG-576 — Catalog product relationship and lifecycle-eligibility: Decision brief (owner forks)

**Status:** Proposal only. Additive Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted cells from the [2026-10-17 delegated record](delegated-renewals-amendments-decisions-2026-10-17.md) § Catalog and policy or the 576-F1..F13 disposition index, does **not** accept [`specs/catalog-model.md`](specs/catalog-model.md), does **not** adopt open OQs from the DRG-599 Decision brief (#829), authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a route, wire an apply gate, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / catalog relationship + eligibility. **Story:** [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576/catalog-product-relationship-and-lifecycle-eligibility-model-for) (RA-42). Linear read this pass: **In Review**. Notion mirror: [RA-42](https://app.notion.com/p/3fbf7cb4e4df81c58037edfb2e96b7f4) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen Adopted cells):**

| Artifact | Role |
|---|---|
| [story-drg-576-product-relationship-and-eligibility-decision-frame.md](story-drg-576-product-relationship-and-eligibility-decision-frame.md) | Historical decision frame (merged #659). Pre-decision proposal labels retained as rationale; **current authority** is the delegated record |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Catalog and policy + § 576 disposition index | **Adopted** 576-F1..F13 (baseline defaults; 576-F11 replaced with versioned read-only query). Restated as A1–A14. Does **not** ship schema or a route |
| Register OQ12 | **CONFIRMED 2026-10-16**: first-slice actions are Add / Update / Remove only; Swap / Upgrade / Downgrade wait for RA-42 / DRG-576 |
| Linear DRG-576 comment (Call decision 2026-10-16, audit item 17) | Sales SKU → delivery BOM is **one catalog with explicit composition links** (dumb data); conditional logic stays in Drools / parked DRG-590. Amendment SKU mapping stays DRG-599 |
| [specs/catalog-model.md](specs/catalog-model.md) | Proposal only (incl. DRG-647 lifecycle-intent eligibility section). Authorizes no code |
| [story-drg-599-sku-mapping-decision-brief.md](https://github.com/drgaciw/open-crm-cpq/blob/cursor/drg-599-sku-mapping-decision-brief-4fa7/docs/renewals-amendments/prereq/story-drg-599-sku-mapping-decision-brief.md) (#829) | **Cite only.** Couples to this model as candidate relationship home. **Do not adopt** its open OQ1–OQ12 here; do **not** implement the DRG-599 mapping table from this brief |

**In-flight alignment (do not contradict Adopted; do not unpark):**

| Ticket | Linear | What this brief must not contradict |
|---|---|---|
| [DRG-599](https://linear.app/drgamtd-workspace/issue/DRG-599) | Related | Dispute status / contested hold / mapping table residuals — cite #829, do not close its OQs |
| [DRG-647](https://linear.app/drgamtd-workspace/issue/DRG-647) | Related | Intent→eligible-type matrix on `catalog-model.md` remains Proposed |
| [DRG-421](https://linear.app/drgamtd-workspace/issue/DRG-421) | Catalog versions | Catalog version id optional until that story supplies one (A11); not a silent schema invent |
| [DRG-422](https://linear.app/drgamtd-workspace/issue/DRG-422) | Account/segment/region eligibility | Not the four lifecycle flags |
| [DRG-423](https://linear.app/drgamtd-workspace/issue/DRG-423) | Catalog serving API | Open Host Service surface; not a blocker for the delegated internal read direction |
| [DRG-429](https://linear.app/drgamtd-workspace/issue/DRG-429) | Reconfigure-from-hash | Not a product-to-product type |
| [DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552) | Action codes | Cluster E owns action vocabulary; this brief does not retoken `chk_amendment_line_action` |
| [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) | Maintenance instance link | `MAINTENANCE_FOR` does not replace `relatedPerpetualLineId` (A3) |
| [DRG-539](https://linear.app/drgamtd-workspace/issue/DRG-539) | Price-book list bases | Successor SKU still needs that read for list basis; not redesigned here |
| [DRG-590](https://linear.app/drgamtd-workspace/issue/DRG-590) | **PARKED** | Conditional rules — do **not** unpark |
| [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) | Continuation pricing design | Not accepted; opaque refs stay unpriced |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, apply-gate wiring, or deploy.** No `opencrm.jev.*.enabled` flag is turned on. Dual-read and ramp holds are untouched. An implementation brief for product relationship / eligibility persistence or the allowed-transition read is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Selected in the 2026-10-17 delegated record. Restated; **not** re-opened. |
| `Adopted (OQ12)` | Register OQ12 confirmed 2026-10-16. Restated; **not** re-opened. |
| `Adopted (#17)` | Call decision 2026-10-16 audit item 17 on Linear DRG-576 (composition vs Drools). Restated; **not** re-opened as ownership of conditionals. |
| `Status: Proposed` | Residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |

## Problem (RA-42)

product-service has a unique `sku`, `ProductType`, DRAFT→ACTIVE→DEPRECATED→RETIRED, and `PRODUCT_UPDATED` / `PRODUCT_RETIRED` outbox events, but **no product-to-product relationships** and **no lifecycle eligibility flags**. Mid-term policy names Upgrade / Downgrade / Swap; renewal may substitute a successor SKU; Revenera-class catalogs require configured relationships. The 2026-10-17 delegation already chose the relationship type set, eligibility-row shape, pin semantics, opaque mapping refs, and a versioned read-only query. What remains un-scored for an **implementation gate** is residual delivery design: composition-link placement relative to the six Adopted types, schema/DTO field completion, write surface, exact HTTP contract fields, DRG-421 sequencing, when OQ12 Swap/Upgrade/Downgrade become executable after the model lands, DRG-647 intent eligibility residuals, and sequencing with DRG-599 without adopting that story's open OQs.

Without a scored Decision brief that separates those **Adopted** cells from **Proposed** residuals, an implementation story could invent a second mapping store, accept `catalog-model.md` wholesale, gate first-slice Add on `ADD_ON_OF`, enable Swap/Upgrade/Downgrade without a separate authorization, unpark DRG-590, or ship Flyway before owner forks close.

Linear DRG-576 (`Linear read` 2026-11-06): High; **In Review**; relations include DRG-590, DRG-647, DRG-607, DRG-561, DRG-599. Attachments: Notion RA-42; decision frame PR #659 (merged).

## Already Adopted (do not re-decide)

Restated from the delegated record § Catalog and policy + § 576 index, register OQ12, and Linear call #17 (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | Relationship type set is the six ticket types: `UPGRADE`, `DOWNGRADE`, `SWAP`, `SUCCESSOR`, `MAINTENANCE_FOR`, `ADD_ON_OF` | **Adopted (delegated)** 576-F1 |
| A2 | `MAINTENANCE_FOR` is in the vocabulary; direction is maintenance SKU → perpetual SKU; it does **not** replace `relatedPerpetualLineId` and is not a gate on that instance id | **Adopted (delegated)** 576-F2 |
| A3 | `SUCCESSOR` is required only when the renewal target SKU differs; same-SKU renew needs no row | **Adopted (delegated)** 576-F3 |
| A4 | `ADD_ON_OF` does **not** gate first-slice OQ12 Add / Update / Remove | **Adopted (delegated)** 576-F4 |
| A5 | Many active `toSku` values are allowed for one `fromSku` + type; the change line names `toSku` | **Adopted (delegated)** 576-F5 |
| A6 | New `toSku` may be `ACTIVE`, or `DEPRECATED` only for `SUCCESSOR`; `RETIRED` refused as a new `toSku`; existing pins stay readable | **Adopted (delegated)** 576-F6 |
| A7 | The four flags (`renewable`, `amendableMidTerm`, `termRequired`, `coTermable`) live on a **versioned eligibility row**, not only as mutable `products` columns | **Adopted (delegated)** 576-F7 |
| A8 | Missing `renewable` / `amendableMidTerm` is unresolved (not allow); `termRequired` / `coTermable` apply only when the row says true | **Adopted (delegated)** 576-F8 |
| A9 | First-slice Add / Update / Remove do **not** read the flags; `amendableMidTerm` applies to Upgrade / Downgrade / Swap once those are in scope; `renewable` on Renew; `coTermable` does not choose a co-term mode | **Adopted (delegated)** 576-F9 |
| A10 | Line pins relationship-row version, and eligibility-row version when a flag was read; `products.version` is **not** that pin; catalog version id fills when DRG-421 supplies one | **Adopted (delegated)** 576-F10 |
| A11 | Product-service owns **one** versioned relationship/eligibility **read** (DTO + version pins); tenant-scoped lookup by source/target SKU, type, effective date; return matching versions and explicit missing/contested reasons; **never** a price or mutable entity; shared DB read excluded | **Adopted (delegated)** 576-F11 + Catalog § |
| A12 | **No new catalog event**; `PRODUCT_UPDATED` / `PRODUCT_RETIRED` stay as registered | **Adopted (delegated)** 576-F12 |
| A13 | `quantityMapping` and `creditPolicyRef` are optional and opaque; no ratio evaluated; no price returned; conversion price pending non-accepted DRG-609 | **Adopted (delegated)** 576-F13 |
| A14 | First-slice action cut remains Add / Update / Remove only; Swap / Upgrade / Downgrade wait on this model (RA-42) — this brief does **not** by itself make those three executable | **Adopted (OQ12)** |
| A15 | Sales SKU → delivery BOM uses **explicit composition links** as plain catalog data (no conditionals in the data); region/license/tier selection stays in Drools (parked DRG-590). Amendment SKU mapping disputes stay on DRG-599 | **Adopted (#17)** |
| A16 | Holds: no product code from docs PRs alone; no dual-read flip; no Jev enablement; S3/S4/S6 not authorized by this story; do not implement the DRG-599 table from this brief | AGENT.md / task holds |

## What the tree does today (relationship / eligibility)

`verified by reading code` at the base SHA.

1. Product-service migrations tip at `V1_8__allow_canonical_ramp_product_types.sql`. `CREATE TABLE` names in `V1__create_product_schema.sql` have no `product_relationship` / eligibility table. Tree search for `ProductRelationship`, `product_relationship`, `amendableMidTerm`, `coTermable` under `backend/services/product-service` returned **no matches**.
2. Packages under `com.opencrm.product` remain attribute, bundle, catalog, config, configrule, option — no relationship package. `BundleItem` is composition of products into a bundle; it is not an upgrade/successor/maintenance type.
3. CAM `chk_amendment_line_action` in `V1_7__contract_documents.sql` allows `SWAP` / `UPGRADE` / `DOWNGRADE` tokens. The check is **not** a relationship table and does not authorize those actions in the first slice (A14 / OQ12).
4. [`specs/catalog-model.md`](specs/catalog-model.md) remains a proposal, including the DRG-647 intent-eligibility section (empty eligible lists; Proposed error codes; Proposed `intent` query param).
5. No allowed-transition route, relationship CRUD, or eligibility writer exists on this `main`.

### Docs-only change impact

This branch adds one markdown file under `docs/renewals-amendments/prereq/`. No production symbol is edited. A later implementation must run GitNexus `impact` before changing product-service schema, ProductController routes, or quote-service clients.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No new Adopted cells in this table. Rows refine delivery against A1–A16; they do not reopen those contracts. **Do not adopt DRG-599 #829 OQs** by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks code? |
|---|---|---|---|---|
| OQ1 | **Composition link** vs six Adopted types (A1 / A15) | Add catalog type `COMPOSITION` (sales SKU → delivery BOM component) as dumb data beside the six lifecycle types; keep Drools for conditionals (DRG-590 parked) | Fold composition into `ADD_ON_OF`; keep only `BundleItem` / options and add no type; treat composition as out-of-scope for DRG-576 | Yes — schema type enum |
| OQ2 | **Persistence shape** (tables / versioning) | Two versioned tables: `product_relationship` + `product_eligibility`, effective-dated, immutable versions on publish | Single combined table; mutable in-place rows (conflicts A7/A10) | Yes — Flyway |
| OQ3 | Relationship **lifecycle status** set (catalog row, not dispute) | `DRAFT` / `ACTIVE` / `RETIRED` on the relationship version; dispute `CONTESTED` stays DRG-599 | Only `ACTIVE`/`RETIRED`; reuse dispute statuses on the catalog row (pulls 599 into this story) | Yes — column / gate |
| OQ4 | **Write / publish** surface | Product-team writers only via product-service admin/internal APIs; quote-service is read-only (aligns A11 / A15; does **not** close DRG-599 dispute OQs) | Shared catalog-admin role; sales stages unpublished drafts | Soft for first migration; hard before any write API |
| OQ5 | **Read HTTP / DTO completion** | Versioned read-only internal query (Catalog §): path and response fields scored here as a contract sketch — `fromSku`, `toSku`, `type`, `onDate`, optional pins; returns `allowed`, relationshipVersion, eligibilityVersion when read, opaque refs, refusal reason; never a price | Extend `GET /api/v1/products/sku/{sku}`; wait for DRG-423 public serving API before any read | Yes — route / client |
| OQ6 | **DRG-421 sequencing** | Ship relationship/eligibility versions first; nullable `catalogVersion` until DRG-421 (affirms A10); do not block Flyway on catalog-version publish | Block all schema until DRG-421; invent a catalog-version id here | Yes — column nullability |
| OQ7 | **When Swap / Upgrade / Downgrade become executable** | Model + scored OQs here are necessary but **not sufficient**; separate S5 / owner authorization still required after the model lands (A14 stands) | Closing this brief alone enables the three actions; enable as soon as Flyway merges | Yes — action authorization |
| OQ8 | **DRG-647 intent eligibility** | Keep Cancel / Restructure / Suspend / Resume / Transfer / Convert eligible-type lists empty until owner names types; score on DRG-647 / `catalog-model.md`, not by inventing types here | Name a subset of A1 types for Restructure/Convert now | Soft here; hard for 647 fixtures |
| OQ9 | **`intent` on the allowed-transition read** | Optional `intent` query param later; first read contract validates type+SKU+date without requiring intent (A11); 647 can extend | Require `intent` on day one; never take `intent` | Soft for first route; hard for 647 |
| OQ10 | Relationship **refusal error-code** shape | `SCREAMING_SNAKE` reasons such as `RELATIONSHIP_NOT_FOUND` / `RELATIONSHIP_TYPE_MISMATCH` / `ENDPOINT_STATUS_REFUSED` (no new prefix family); do not reuse 647 per-intent codes for catalog misses | One shared `CATALOG_TRANSITION_REFUSED`; invent a `REL_` prefix | Soft for docs; hard for AC fixtures |
| OQ11 | `MAINTENANCE_FOR` **cardinality** (same support SKU, many licences) | One row per (maintenance SKU, perpetual SKU) pair (fits A5 many-targets / A2 direction) | Typed group / wildcard target; single maintenance SKU row without pair | Yes if group key differs |
| OQ12 | Sequencing with **DRG-599** / catalog-model | Score OQ1–OQ11 here; DRG-599 keeps dispute/hold OQs on #829 (**do not adopt**); **do not** accept `catalog-model.md` wholesale; **do not** implement the 599 mapping table from this brief | Ship 599 mapping table before 576 read; accept catalog-model as ADR now | Yes — delivery order |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ1 — Composition link vs six Adopted types

| Option | What the owner would be choosing |
|---|---|
| **A. Add `COMPOSITION` beside the six (Recommended, Proposed)** | Affirms A15 call #17 without reopening A1's six lifecycle types. Composition stays dumb data; Drools stays parked on DRG-590. |
| **B. Fold into `ADD_ON_OF`** | Overloads commercial add-on attachment with BOM composition. |
| **C. BundleItem / options only** | Leaves call #17 without a catalog relationship type; composition stays outside this model. |
| **D. Out of scope for DRG-576** | Track composition on a separate story; A15 principle still stands. |

**Recommended (Proposed):** A. Does **not** reopen A1. `Owner decision: ___ (drg, time)`.

### OQ2 — Persistence shape

| Option | What the owner would be choosing |
|---|---|
| **A. Two versioned tables (Recommended, Proposed)** | Affirms A7 eligibility row and A10 pins. Relationship and eligibility versions are independently pinable. |
| **B. Single combined table** | Couples flag edits to relationship publishes. |
| **C. Mutable in-place rows** | Conflicts with A7/A10 pinning. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ3 — Relationship lifecycle status (not dispute)

| Option | What the owner would be choosing |
|---|---|
| **A. DRAFT / ACTIVE / RETIRED (Recommended, Proposed)** | Catalog publication lifecycle. Contested hold remains DRG-599 (#829). |
| **B. ACTIVE / RETIRED only** | No draft staging. |
| **C. Include CONTESTED on the catalog row** | Pulls dispute workflow into DRG-576; contradicts separation from DRG-599. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ4 — Write / publish surface

| Option | What the owner would be choosing |
|---|---|
| **A. PRODUCT writers via product-service (Recommended, Proposed)** | Matches A11 ownership and A15 product-team composition data. Does **not** score DRG-599 dispute open/resolve OQs. |
| **B. Second catalog-admin role** | Needs a named role this brief does not invent. |
| **C. Sales stages unpublished drafts** | Softens product ownership of published versions. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ5 — Read HTTP / DTO completion

| Option | What the owner would be choosing |
|---|---|
| **A. Internal versioned query contract sketch (Recommended, Proposed)** | Affirms A11 Catalog §: DTO + pins, never price/entity, no shared DB. Exact path string is an implementation detail scored with the contract (historical sketch: `GET /internal/v1/catalog/allowed-transitions`). |
| **B. Extend public product-by-sku GET** | Mixes mutable product with versioned relationship facts. |
| **C. Block until DRG-423** | Leaves quote-service with no read while serving API stays Backlog. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ6 — DRG-421 sequencing

| Option | What the owner would be choosing |
|---|---|
| **A. Relationship version first; nullable catalogVersion (Recommended, Proposed)** | Affirms A10. |
| **B. Block schema on DRG-421** | Holds Upgrade/Downgrade/Swap pin work behind catalog publish. |
| **C. Invent catalogVersion here** | Scope creep into DRG-421. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ7 — Action unlock after the model

| Option | What the owner would be choosing |
|---|---|
| **A. Necessary but not sufficient (Recommended, Proposed)** | Affirms A14. Closing forks + landing schema does not auto-enable Swap/Upgrade/Downgrade; S5 / owner authorization remains. |
| **B. This brief alone enables the three** | Over-reads OQ12 "wait for DRG-576". |
| **C. Enable on Flyway merge** | Skips authorization gate. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ8 — DRG-647 intent eligibility lists

| Option | What the owner would be choosing |
|---|---|
| **A. Keep empty lists; score on 647 (Recommended, Proposed)** | Affirms current `catalog-model.md` Proposed section. |
| **B. Name types for Restructure/Convert now** | Invents conversion relationship types outside conversions.md. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ9 — `intent` query param

| Option | What the owner would be choosing |
|---|---|
| **A. Optional later; first contract without required intent (Recommended, Proposed)** | Affirms A11 minimum lookup keys. |
| **B. Required on day one** | Couples first read to 647. |
| **C. Never** | Forces a second endpoint for intent checks. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ10 — Refusal error-code shape

| Option | What the owner would be choosing |
|---|---|
| **A. Per-reason SCREAMING_SNAKE without new prefix (Recommended, Proposed)** | Aligns with 2026-10-27 style ruling cited on `catalog-model.md`; distinct from 647 per-intent codes. |
| **B. One shared code** | Weaker fixture discrimination. |
| **C. New `REL_` prefix family** | Conflicts with "no new prefix" ruling direction. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ11 — `MAINTENANCE_FOR` cardinality

| Option | What the owner would be choosing |
|---|---|
| **A. One row per pair (Recommended, Proposed)** | Affirms A2 direction + A5 many targets. |
| **B. Typed group / wildcard** | Needs a group key this brief does not invent. |
| **C. Single row without pair** | Breaks A2 toSku direction. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ12 — Sequencing with DRG-599 / catalog-model

| Option | What the owner would be choosing |
|---|---|
| **A. Score OQ1–OQ11 here; leave 599 OQs on #829; catalog-model stays proposal (Recommended, Proposed)** | Matches task coupling rule: cite DRG-599, do not adopt its open OQs, do not implement its table. |
| **B. Ship 599 mapping table before 576 read** | Orphans A11. |
| **C. Accept catalog-model wholesale now** | Out of scope; would silently adopt still-Proposed 647 rows. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit NO-GO)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed.
2. CPQ Decision must score **OQ1–OQ12**. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **NO-GO** on:
   - product Java/TS code for relationship / eligibility CRUD or the allowed-transition read
   - Flyway / schema for `product_relationship`, `product_eligibility`, or any DRG-599 mapping table
   - feature flags enabling relationship validation paths
   - quote-service / CAM clients that call a catalog transition read
   - enabling Swap / Upgrade / Downgrade as executable first-slice actions
   - Drools / conditional rules (DRG-590 stays parked)
4. Even after forks close: A1–A16 hold; first-slice Add/Update/Remove still do not read flags (A9) and are not gated by `ADD_ON_OF` (A4); conversion **price** stays pending non-accepted DRG-609; S3/S4/S6 need their own authorization; DRG-599 dispute/hold remains a separate scored brief.
5. Linear status for DRG-576 stays **In Review** until the owner merges and DoD says otherwise. This brief does **not** mark Done.

## Out of scope

- Creating any migration or Java type for relationships / eligibility.
- Editing CAM transition guards, apply services, or `chk_amendment_line_action` semantics.
- Accepting `catalog-model.md`, implementing the DRG-599 table, or unparking DRG-590.
- Redesigning DRG-539 list-basis reads or DRG-556 instance links.
- Pricing, credit, or ARR arithmetic (DRG-609 / DRG-575).
- Reopening delegated 576-F1..F13, Catalog §, or OQ12.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-576 body / relations / In Review | Linear, this pass |
| Call #17 composition vs Drools | Linear DRG-576 comment 2026-10-16 |
| Delegated Catalog § + 576-F1..F13 index | `delegated-renewals-amendments-decisions-2026-10-17.md` |
| Historical 576 frame | `story-drg-576-product-relationship-and-eligibility-decision-frame.md` |
| OQ12 confirmed cut | `renewals-amendments-open-questions-register.md` |
| Catalog-model proposal + DRG-647 section | `specs/catalog-model.md` |
| DRG-599 Decision brief (cite only) | #829 branch `story-drg-599-sku-mapping-decision-brief.md` |
| No relationship/eligibility tables on product-service | migrations `V1`–`V1_8`; tree search |
| Action tokens without relationship table | CAM `V1_7__contract_documents.sql` |

## Unverified in this pass

- Any runtime transition, HTTP status, or live database catalog.
- Whether a future product-service migration number is `V1_9` or later in every environment.
- AutoJev tools (not connected in this environment); no AutoJev verdict is claimed.
- Browser UAT / chaos scripts (not applicable to docs-only).
- Full text of every DRG-599 #829 OQ body beyond the coupling rule (cite, do not adopt).
