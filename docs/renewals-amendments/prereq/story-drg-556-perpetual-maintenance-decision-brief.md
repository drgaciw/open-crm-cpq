# DRG-556 — On-prem perpetual licence + maintenance linkage: residual Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556/on-prem-perpetual-licence-plus-maintenance-linkage-renew-maintenance) (RA-22).

**Status:** Proposed residual Decision brief. This file **restates Adopted Cluster B forks for DRG-556** (delegated 2026-10-17 baseline defaults, with the version-date replacement) without reopening them, **cites** the Cluster B frame, `specs/maintenance.md`, renewal RN5 proposal text, and related Wave-2 drafts, and surfaces **remaining owner forks as Proposed OQ-556-\***. **It authorizes no production code path, no schema change, no Flyway migration (including CAM slots V1_16–V1_21 reserved by [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), no inventing of landed `licenseModel` / `isPermanent` / `relatedPerpetualLineId` columns, no feature flag, no Kafka consumer registration, no S4 apply enablement, no dual-read / Jev / ramp flip, no asset-write hold bypass, and no deploy.** Product / schema / flag work that persists linkage fields, wires RN5 into quote continuation, or retires the legacy 22% DRL is **NO-GO** until the owner closes the residual OQ-556-\* forks below (or explicitly defers a named fork with words and a time).

**Scope boundary:** This brief is **linkage and renew-maintenance only**. Maintenance **pricing** formula residuals (percent of licence net, recalc, uplift field, reinstatement knobs, legacy 22% reconciliation) stay on [DRG-571](https://linear.app/drgamtd-workspace/issue/DRG-571) — **cite only**; do **not** land pricing formula choices here.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`.

**Linear status when read:** In Review (not Done). This brief leaves that status unchanged.

## Alignment (cite; do not reopen Adopted DRG-556 dispositions)

| Artifact | Relation to this brief |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Cluster B DRG-556 rows + § One-time charges and conversion (version-date replacement) | **Adopted baseline** for licence-model home, instance link, renew-maintenance rule, quantity match, and version-date exclusion. Cite; **do not reopen**. |
| [cluster-b-onprem-to-saas-and-maintenance-decision-frame.md](cluster-b-onprem-to-saas-and-maintenance-decision-frame.md) § DRG-556 | Historical options / rationale for the five DRG-556 forks; dispositions already indexed. Do not re-litigate Adopted cells |
| [specs/maintenance.md](specs/maintenance.md) | **Proposal only; not accepted.** Linkage contract sketch (`licenseModel`, `isPermanent`, `relatedPerpetualLineId`), renew-maintenance ACs, provisional DRG-642 fixtures. Authorizes no code |
| [renewal-policy-spec.md](renewal-policy-spec.md) RN5 / AC-RN-09 | **Proposal** commercial rule: `isPermanent` line is not `Renew` / not `Remove`; linked maintenance is renewed. This brief does **not** accept the renewal-policy-spec file as an ADR |
| [renewal-policy-stub.md](../renewal-policy-stub.md) RN5 | Same rule at stub level; still a proposal |
| Catalog [story-drg-576-…](story-drg-576-product-relationship-and-eligibility-decision-frame.md) 576-F2 + delegated A2 | `MAINTENANCE_FOR` vocabulary exists; it does **not** replace `relatedPerpetualLineId` and is **not** a gate on that instance id |
| DRG-642 fixtures under [specs/fixtures/maintenance/](specs/fixtures/maintenance/) | Provisional docs fixtures (`"status": "provisional"`, `"licenceRenewed": false`). Amounts illustrate AC text; **not** Adopted product oracles |

**In-flight alignment (cite-only — do not adopt their open OQs / pricing residuals):**

| Ticket / PR | What this brief must not contradict / adopt |
|---|---|
| [DRG-571](https://linear.app/drgamtd-workspace/issue/DRG-571) | Maintenance **pricing** residuals (LicenceNet percent, recalc, uplift, reinstatement, legacy `perpetual-maintenance.drl` 22%). **Cite only.** Linkage comes first; this brief does **not** seed 20%, retire the 22% rule, or close 571 forks |
| [DRG-642](https://linear.app/drgamtd-workspace/issue/DRG-642) | Maintenance percent-of-net recalculation fixtures (prereq theme 3). Fixtures stay provisional until an implementation brief after linkage + pricing forks close |
| [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576) / Wave-2 catalog eligibility residual | Open catalog OQs (composition, persistence, routes). Cite; **do not adopt**. `MAINTENANCE_FOR` remains non-gate for the instance id (Adopted 576-F2) |
| [DRG-574](https://linear.app/drgamtd-workspace/issue/DRG-574) / [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559) | Surrender / restructure consume the perpetual line this link names. Adopted Cluster B dispositions stand; this brief does not reopen surrender tokens or Restructure apply |
| [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558) | Commitment projections / version-date fulfilment regenerate. Cite fulfilment home; do not invent regenerate product here |
| [DRG-593](https://linear.app/drgamtd-workspace/issue/DRG-593) | Renewal quote creation modes. RN5 still applies when modes exist; do not adopt open 593 OQs |
| Dual-read / NgRx / Jev / ramp / S4 / asset-write | `AGENT.md` holds — untouched |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** No `opencrm.jev.*.enabled` flag is turned on. `opencrm.pricing.ramp.enabled` stays default off. An implementation brief for DRG-556 (or any product change that persists `licenseModel` / `isPermanent` / `relatedPerpetualLineId`, enforces RN5 in continuation, or links maintenance renewals to perpetual lines) is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `GitNexus` | This checkout has **no** `.gitnexus/` index and MCP `query`/`impact` returned **no indexed repositories**. Graph impact is **UNKNOWN** (unresolved), not a clean check. `detect_changes` could not run cleanly (missing `run.cjs`). A zero or empty caller set would not be evidence of safety. |

## Problem (RA-22)

Sold lines today are **recurring subscription** continuation sources. A perpetual on-prem licence with a **separate maintenance / support line**, where the commercial renewal acts on the **maintenance** (or a term licence) and the perpetual line stays in place as `NoChange`, is **not modeled** as instance linkage.

Market research on the Linear issue (Revenera FlexNet Operations order types New/Renewal/Upgrade/Upsell with parent line; permanent lines not renewed except version-date features; Snow License Manager maintenance/support periods and upgrade rights) is **orientation only**. The issue’s “about 60% on-prem” priority rationale is **not** a measured figure in this repository.

What already exists on `main` (`read` / `verified by reading code`):

- Catalog / pricing product types include `ONPREM_PERPETUAL` and `ONPREM_SUPPORT` (product-service / pricing-service). A product type does **not** point a sold maintenance line at a sold perpetual line.
- Continuation plans one recurring source and keeps or uplifts its accepted unit rate (`QuoteContinuationPolicy`). `ONPREM_SUPPORT` can be a continuation source when it is in `AcceptedRampSnapshots`’ fixed set. `ONPREM_PERPETUAL` is outside that set (`verified by reading code` in `AcceptedRampSnapshots.java`).
- Legacy pricing rule `perpetual-maintenance.drl` seeds a fixed **22% of list** for `ONPREM_PERPETUAL` (distinct from linked-line percent-of-net). That rule is a **DRG-571** reconciliation concern — cite only here.
- Cluster B DRG-556 forks are **Adopted** as baseline defaults (plus version-date replacement). Specs and RN5 remain **proposals** for delivery text.
- Tree search for production `licenseModel`, `isPermanent`, or `relatedPerpetualLineId` Java/SQL columns returns **no matches** (same observation as the Cluster B frame; re-checked this pass at the base SHA).

What is still missing for product:

1. Residual **delivery** forks the delegated baseline did **not** close (persistence home, stored vs derived `isPermanent`, same-document vs cross-agreement link scope, RN5 acceptance vs runtime enforcement sequencing, TermLicense share of the renew engine, version-date fulfilment home).
2. Explicit **NO-GO** on inventing schema for the three linkage fields or consuming reserved CAM Flyway **V1_16–V1_21**.
3. Cite-only boundaries with **DRG-571 pricing residuals** and catalog / fulfilment siblings so a later story does not silently land percent formulas or `MAINTENANCE_FOR` gates under this ticket.

Without that split, a later PR could re-litigate Adopted Cluster B cells, treat `specs/maintenance.md` or DRG-642 fixtures as Adopted oracles, invent Flyway in reserved slots, wire pricing percent under DRG-556, or claim this brief authorized S4 / asset-write / dual-read / Jev / ramp.

Linear DRG-556 (`Linear read` 2026-11-06): High; **In Review**; related DRG-666, DRG-664, DRG-661, DRG-658, DRG-655, DRG-642, DRG-607, DRG-571, DRG-547. Attachment: Cluster B draft [#652](https://github.com/drgaciw/open-crm-cpq/pull/652) (merged; dispositions Adopted via the delegated record). Wave-2 refill comments record docs-only residual Decision brief scope with product/schema/flag **NO-GO**.

## Market research — cited as forks only

Issue research links are orientation only. They are **not** selected options and do not override the delegated record.

| Vendor pattern | Observed options (research) | How this brief treats them |
|---|---|---|
| Revenera FlexNet Operations entitlement lines | Order Type New/Renewal/Upgrade/Upsell with parent line; permanent lines not commercially renewed except version-date features; maintenance shown with expiry | Orientation for Adopted renew-maintenance + version-date exclusion — not a FlexNet field map |
| Snow License Manager | Maintenance/support periods and upgrade rights per licence purchase | Orientation for linked maintenance period — not a Snow schema |
| Caller 60% on-prem base | Priority rationale on the Linear issue | Not a measured repo figure; does not set a default percent (pricing stays DRG-571) |

## Already Adopted (do not re-decide)

Restated from the delegated record § Cluster B DRG-556 + version-date replacement (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | **`licenseModel` lives on the sold line** with values `Perpetual`, `TermLicense`, `Maintenance`, `Subscription`. Catalog `ProductType` remains product classification, not the sold-line model | Cluster B “Where the licence model lives” baseline; delegated “Adopt baseline default” |
| A2 | **`isPermanent` is true only when `licenseModel` is `Perpetual`**; any other combination is refused; a SaaS line is `Subscription` | Same fork baseline |
| A3 | **`relatedPerpetualLineId` is required** when `licenseModel` is `Maintenance`; a missing id refuses the line. Catalogue `MAINTENANCE_FOR` (when present) authorizes SKU pairs and does **not** replace the instance id and is **not** a gate on that id | Baseline + delegated 576-F2 |
| A4 | **Renewal renews the maintenance line** (and a `TermLicense` line when that was sold). The perpetual line may appear as `NoChange`. Commercial `Renew` or `Remove` of a `Perpetual` line is refused | Baseline; aligns with proposal RN5 / AC-RN-09 without accepting `renewal-policy-spec.md` |
| A5 | **Quantity match:** linked maintenance quantity equals the perpetual quantity; mismatch refused; no silent subset rule | Baseline |
| A6 | **Version-date refresh is excluded from commercial renewal**; a later fulfilment request may regenerate without repricing | Delegated replacement (not the historical “leave out” wording alone) |
| A7 | Maintenance **pricing** (percent, recalc, uplift, reinstatement) is owned by **DRG-571**; linkage precedes pricing. Missing maintenance percentage remains a refused/unresolved **request input**, never an invented 20% | Delegated § Catalog and policy; Cluster B 571 rows |
| A8 | Holds: no product code / Flyway / dual-read / Jev / ramp / S4 / asset-write from this brief; S3 remains separately authorized; CAM V1_16–V1_21 reserved (#805–#812) | AGENT.md; task holds |

## What the tree does today (linkage)

`verified by reading code` at the base SHA.

1. No production Java or SQL fields named `licenseModel`, `isPermanent`, or `relatedPerpetualLineId` under `backend/`.
2. `AcceptedRampSnapshots` fixed continuation products include `ONPREM_SUPPORT` and exclude `ONPREM_PERPETUAL`.
3. CAM Flyway tip on this `main` is through `V1_15__document_apply_replay_evidence.sql`. No linkage columns. **V1_16–V1_21 remain reserved** for other stories (#805–#812); this brief must not consume them.
4. [`specs/maintenance.md`](specs/maintenance.md) and DRG-642 JSON fixtures under `specs/fixtures/maintenance/` remain proposals / provisional.
5. Legacy `perpetual-maintenance.drl` (22% of list) remains in pricing-service resources — a **DRG-571** reconciliation item, not a DRG-556 schema.

### Docs-only change impact

This branch adds one markdown file under `docs/renewals-amendments/prereq/`. No production symbol is edited. A later implementation must run GitNexus `impact` **after** an index exists, before changing quote-service continuation, CAM line schema, or pricing-service rules. **This pass: GitNexus impact / detect_changes = UNKNOWN (no index) — not a clean check.**

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No new Adopted cells in this table. Picking a row authorizes **no** code, migration, flag, S4 enablement, dual-read / Jev / ramp flip, asset-write, inventing linkage columns, or deploy. Rows refine **delivery** against A1–A8; they do **not** reopen those contracts. **Do not land DRG-571 pricing formula residuals** by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| **OQ-556-1** | **Persistence home** for `licenseModel` / `isPermanent` / `relatedPerpetualLineId` | Snapshot the three fields on the **priced commercial line** (proposal: CommitmentLine / amendment line projection) owned with the commercial document; catalog type stays separate (A1). **No migration in this brief**; additive shape only after forks close and **never** in reserved V1_16–V1_21 | Put fields only on product-service SKU; only on CRM opportunity lines; invent a second parallel store in quote-service and CAM | Yes — any public create/submit that claims linkage |
| **OQ-556-2** | **`isPermanent` stored vs derived** | Store `licenseModel`; treat `isPermanent` as a **derived/checked** boolean that must equal (`licenseModel == Perpetual`) — refuse mismatches (A2). Physical column vs check-only is an implementation detail scored with the DTO | Independent mutable `isPermanent` that can disagree with `licenseModel`; drop `isPermanent` from APIs and infer only in RN5 | Soft for docs; hard for wire contracts |
| **OQ-556-3** | **Link scope** (same document / agreement) | Require the perpetual target of `relatedPerpetualLineId` to be an **active sold line on the same commercial agreement** (or same amendment document’s source set) when the maintenance line is accepted; refuse dangling / cross-tenant ids | Allow cross-agreement links; allow catalogue-only ids without an instance; allow soft links that resolve at renew time only | Yes — linkage AC-1 |
| **OQ-556-4** | **Catalog `MAINTENANCE_FOR` before instance id** | Keep **A3 / 576-F2**: instance id required; catalogue row is **not** a gate and does **not** replace the id. Cite DRG-576 residuals; **do not adopt** open 576 OQs | Require both before accept; catalogue-only (drops A3) | Soft until 576 delivery; hard if gate invented early |
| **OQ-556-5** | **RN5 / `renewal-policy-spec.md` acceptance vs runtime** | Keep the **commercial rule** as Adopted (A4). Leave `renewal-policy-spec.md` status as **proposal** until its own acceptance story; do **not** treat this brief as accepting that file wholesale | Accept renewal-policy-spec as ADR now; postpone RN5 enforcement until DRG-555 automation ships | Soft for docs; hard for continuation reject codes |
| **OQ-556-6** | **TermLicense renew path sharing** | `TermLicense` uses the same renew eligibility path as other non-permanent lines (A4); it does **not** require `relatedPerpetualLineId`. Do not fold TermLicense into Maintenance | Require a phantom perpetual parent for TermLicense; refuse TermLicense renew until maintenance pricing (571) lands | Soft for TermLicense ACs |
| **OQ-556-7** | **Quantity match enforcement timing** | Keep **A5** equal-quantity. Enforce at line accept / price; subset coverage stays **absent** until a superseding owner decision with words + time | Allow subset cores now; defer mismatch to renew only | Yes — true-up / renew pair ACs (measurement still DRG-557/569) |
| **OQ-556-8** | **Version-date fulfilment home** | Keep **A6**: no commercial renew/repricing for version-date. Fulfilment regenerate is a **later** DRG-558 / Cluster F request with evidence — cite only; no regenerate product here | Treat version-date as commercial Renew; put regenerate inside quote-service pricing | Soft for commercial ACs; hard for entitlement regenerate |
| **OQ-556-9** | **Boundary with DRG-571 pricing residuals** | **Cite only.** This brief closes **no** percent / recalc / uplift / reinstatement / 22%-DRL fork. A maintenance line may remain commercially unpriced (`UNRESOLVED`) when percent input is missing (A7). Do not seed 20% or retire `perpetual-maintenance.drl` under DRG-556 | Land LicenceNet 20% here; retire 22% DRL in this PR; copy 571 fixture amounts as Adopted oracles | Yes — if someone ships pricing under 556 |
| **OQ-556-10** | **Fixture / AC freeze (DRG-642)** | Keep `specs/maintenance.md` ACs and `specs/fixtures/maintenance/*.json` **`Status: Proposed` / provisional** until an implementation brief after OQ-556-\* (and needed 571 forks) close. `"licenceRenewed": false` stays the linkage illustration | Treat fixture money as Adopted product oracles; add gateway linkage routes in this brief | Soft (docs) |
| **OQ-556-11** | **Surrender / restructure consumers** | Cite Adopted Cluster B DRG-574 / DRG-559. Linkage supplies the perpetual id those flows name; this brief does **not** reopen Surrender tokens, Restructure apply units, or entitlement end | Invent SURRENDER action token here; start multi-subscription apply for restructure now | Soft until those stories’ own gates |
| **OQ-556-IMPL** | **Implementation / migration / flag authorization** | Separate implementation story **after** OQ-556-1..11 (or a deferred subset) close. Additive migrations only if needed; **never** consume reserved **V1_16–V1_21**; **do not** enable S4 apply / asset-write / dual-read / Jev / ramp from this brief; **do not** flip pricing under 571 from this brief | Piggy-back migrations in V1_16–V1_21; ship linkage columns + RN5 rejects before forks close | Owner release; S3/S4 gates; AGENT.md holds; #805–#812 reservation |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (residuals only)

### OQ-556-1 — Persistence home

| Option | What the owner would be choosing |
|---|---|
| **A. Priced commercial line snapshot; migration later outside reserved slots (Recommended, Proposed)** | Affirms A1 without inventing schema now. |
| **B. Product-service SKU only** | Loses instance linkage (conflicts with A3). |
| **C. Dual stores without a single snapshot** | Digest / pin risk across quote-service and CAM. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-556-2 — `isPermanent` stored vs derived

| Option | What the owner would be choosing |
|---|---|
| **A. Derived/checked from `licenseModel` (Recommended, Proposed)** | Affirms A2; refuse disagreeing pairs. |
| **B. Independent mutable flag** | Allows illegal Perpetual + `isPermanent=false`. |
| **C. API omits `isPermanent`** | Still need RN5/AC-RN-09 wording to name permanence. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-556-3 — Link scope

| Option | What the owner would be choosing |
|---|---|
| **A. Same agreement / source document set (Recommended, Proposed)** | Prevents dangling ids; keeps renew pairing local. |
| **B. Cross-agreement links** | Needs co-term / merge stories (DRG-554) — cite only, do not select here. |
| **C. Soft resolve at renew only** | Breaks accept-time AC-1. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-556-4 — `MAINTENANCE_FOR` gate

| Option | What the owner would be choosing |
|---|---|
| **A. Cite 576-F2; no gate; do not adopt open 576 OQs (Recommended, Proposed)** | Affirms A3. |
| **B. Require both catalogue + instance** | Reopens A3 / 576-F2 unless owner supersedes. |
| **C. Catalogue alone** | Drops instance linkage. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-556-5 — RN5 file acceptance vs runtime rule

| Option | What the owner would be choosing |
|---|---|
| **A. Keep Adopted commercial rule; leave renewal-policy-spec Proposed (Recommended, Proposed)** | Avoids wholesale ADR acceptance of RN1–RN10 in this story. |
| **B. Accept renewal-policy-spec now** | Out of scope for a linkage residual brief. |
| **C. Defer RN5 rejects until DRG-555 jobs** | Leaves interactive renew without the Adopted refuse rule. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-556-6 — TermLicense renew path

| Option | What the owner would be choosing |
|---|---|
| **A. Same non-permanent renew path; no perpetual parent required (Recommended, Proposed)** | Matches A4 / maintenance.md AC-10. |
| **B. Phantom perpetual parent** | Invents false linkage. |
| **C. Block TermLicense until DRG-571** | Couples term renew to pricing residuals incorrectly. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-556-7 — Quantity match timing

| Option | What the owner would be choosing |
|---|---|
| **A. Enforce equal quantity at accept/price (Recommended, Proposed)** | Affirms A5; subset stays absent. |
| **B. Allow subset cores now** | Requires superseding A5 with words + time. |
| **C. Check only at renew** | Leaves mid-term true-up pairs inconsistent. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-556-8 — Version-date fulfilment home

| Option | What the owner would be choosing |
|---|---|
| **A. Fulfilment regenerate later (DRG-558); no commercial renew (Recommended, Proposed)** | Affirms A6. |
| **B. Commercial Renew for version-date** | Contradicts A6. |
| **C. Quote-service regenerate at price time** | Mixes fulfilment with pricing. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-556-9 — DRG-571 pricing boundary

| Option | What the owner would be choosing |
|---|---|
| **A. Cite only; no percent/DRL decision here (Recommended, Proposed)** | Hard split: linkage vs pricing. |
| **B. Seed 20% LicenceNet under DRG-556** | **NO-GO** — pricing residual. |
| **C. Retire 22% DRL in this PR** | **NO-GO** — DRG-571 reconciliation. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-556-10 — Fixture freeze

| Option | What the owner would be choosing |
|---|---|
| **A. Keep provisional until implementation brief (Recommended, Proposed)** | Soft docs gate. |
| **B. Treat fixtures as Adopted oracles / add gateway routes now** | Premature public contract; gateway **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-556-11 — Surrender / restructure consumers

| Option | What the owner would be choosing |
|---|---|
| **A. Cite Adopted 574/559; no reopen (Recommended, Proposed)** | Linkage id only. |
| **B. Invent surrender/apply product here** | **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-556-IMPL — Implementation gate

| Option | What the owner would be choosing |
|---|---|
| **A. Separate impl after residual OQx close; no reserved slots; no flags; no 571 pricing land (Recommended, Proposed)** | Hard **NO-GO** on product/schema/flag until then. |
| **B. Use CAM V1_16–V1_21 now** | Violates #805–#812 reservation. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit) — NO-GO

Until CPQ Decision scores OQ-556-1..OQ-556-11 and OQ-556-IMPL (owner fills `Owner decision: ___ (drg, time)` or explicitly defers a named fork the same way):

1. **NO-GO:** product code (Java / TypeScript) that persists `licenseModel` / `isPermanent` / `relatedPerpetualLineId`, enforces RN5 rejects in continuation, or pairs renewals to perpetual parents.
2. **NO-GO:** Flyway / schema for those columns or any edit that consumes reserved **CAM V1_16–V1_21** (#805–#812).
3. **NO-GO:** feature flags (including any `opencrm.jev.*.enabled`), outbox / relay / ramp enablement, dual-read flip, S4 apply enablement, asset-write hold bypass.
4. **NO-GO:** new or changed gateway routes / filters for linkage or renew-maintenance semantics.
5. **NO-GO:** landing DRG-571 pricing formula residuals (percent, recalc, uplift, reinstatement, 22% DRL retirement) under this ticket.
6. **NO-GO:** treating this file, Cluster B, `specs/maintenance.md`, or DRG-642 fixtures as authorization to ship RA-22 product behavior.
7. Even after forks close: S3/S4 remain separately authorized; dual-read / NgRx / ramp / S4 / Jev / asset-write holds stay as in `AGENT.md`; Adopted A1–A8 stay closed; DRG-571 residuals stay on DRG-571.

Linear status for DRG-556 stays **In Review** until the owner merges and DoD says otherwise. This brief does **not** mark Done.

## Holds this brief keeps

| Hold | What stays true |
|---|---|
| Product code | **Forbidden** in the PR that carries this file |
| Schema / Flyway | No linkage columns here; **V1_16–V1_21 reserved** |
| Flag / deploy | No flag flip; no deploy |
| Gateway | No linkage / renew-maintenance route or filter |
| Jev / ramp / dual-read / NgRx / S4 / asset-write | Untouched |
| DRG-571 pricing | Cite only — not landed |
| DRG-642 fixtures | Stay provisional |
| `specs/maintenance.md` / `renewal-policy-spec.md` | Stay proposals (commercial RN5 rule already Adopted as A4 without accepting those files wholesale) |
| Sibling Wave-2 OQs | Cite only — not adopted |

## Out of scope

- Reopening Adopted Cluster B DRG-556 dispositions or the version-date replacement.
- Editing the delegated decision record, accepting `specs/maintenance.md`, or accepting `renewal-policy-spec.md` as an ADR.
- Implementing maintenance ACs or running DRG-642 fixtures against a database.
- Authorizing S3, S4, S6, billing receiver, surrender apply, or asset writes.
- Adopting open OQs from DRG-571, DRG-576, DRG-558, or DRG-593.
- Consuming CAM Flyway V1_16–V1_21.
- Seeding maintenance percent or retiring `perpetual-maintenance.drl`.

## References

- Linear: DRG-556, DRG-571, DRG-642, DRG-576, DRG-574, DRG-559, DRG-558, DRG-593, DRG-607, DRG-547, DRG-666, DRG-664, DRG-661.
- GitHub: Cluster B frame [#652](https://github.com/drgaciw/open-crm-cpq/pull/652); CAM reservation drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812).
- Docs: `cluster-b-onprem-to-saas-and-maintenance-decision-frame.md`, `delegated-renewals-amendments-decisions-2026-10-17.md`, `specs/maintenance.md`, `renewal-policy-spec.md`, `renewal-policy-stub.md`, `story-drg-576-product-relationship-and-eligibility-decision-frame.md`, `specs/catalog-model.md`.
- Fixtures: `specs/fixtures/maintenance/renewal-maintenance-only.json`, `licence-count-change.json`, `reinstatement-after-lapse.json`.
- Code (read-only evidence): `AcceptedRampSnapshots.java` (`ONPREM_SUPPORT` in fixed set; `ONPREM_PERPETUAL` excluded); pricing-service `rules/perpetual-maintenance.drl` (legacy 22% — DRG-571); CAM migration directory through `V1_15` (no linkage columns).
- GitNexus: **UNKNOWN** this pass (no `.gitnexus` index / MCP “No indexed repositories”); not a clean impact or detect_changes result.
