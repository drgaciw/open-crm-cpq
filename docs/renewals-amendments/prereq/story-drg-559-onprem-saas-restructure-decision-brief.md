# DRG-559 — On-prem → SaaS restructure in one lifecycle change: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted cells in the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md) § One-time charges and conversion / Cluster B DRG-559 index rows, does **not** accept [`specs/conversions.md`](specs/conversions.md) or invent a landed `Restructure` API / `priorAgreementId` schema, does **not** accept Cluster B as a second decision authority, authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a route, enable ramp / Jev / dual-read / S4, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / Cluster B restructure. **Story:** [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559/on-prem-to-saas-restructure-in-one-lifecycle-change) (RA-25). Linear read this pass: **In Review**. Notion: [RA-25](https://app.notion.com/p/3fbf7cb4e4df81b59c58d00a85d69b35) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / spec authority (cite, do not reopen Adopted cells; do not invent landed schema):**

| Artifact | Role |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § One-time charges and conversion + Cluster B DRG-559 rows | **Adopted baseline:** one `Restructure`; successor agreement with `priorAgreementId`; line plan Terminate/Surrender/Add; `creditPolicyRef` to conversion policy; create-before-terminate application units; first-slice conversion remains excluded; fulfilment evidence for operational revocation |
| [cluster-b-onprem-to-saas-and-maintenance-decision-frame.md](cluster-b-onprem-to-saas-and-maintenance-decision-frame.md) ([#652](https://github.com/drgaciw/open-crm-cpq/pull/652), merged) | Historical Cluster B frame + DRG-559 options. Dispositions already indexed. Do **not** re-litigate Adopted rows |
| [`specs/conversions.md`](specs/conversions.md) | **Proposal only.** Proposed `Restructure` intent and Scenario 3 line plan. Authorizes no code. This brief does **not** accept it |
| [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md) | Proposed intent vocabulary. Unaccepted |
| [partial-apply-owner-decision.md](partial-apply-owner-decision.md) / [partial-apply-rules-oq5-option-a.md](partial-apply-rules-oq5-option-a.md) | **Adopted** OQ5 Option A first slice; restructure scenarios deferred; S4 unauthorized |
| ADR-035 | Accepted for S1/S2 only — does **not** authorize a Restructure API |

**Related Wave-2 drafts (cite only; do not adopt open OQs):**

| Ticket / draft | What this brief must not adopt |
|---|---|
| [DRG-573](https://linear.app/drgamtd-workspace/issue/DRG-573) / [#848](https://github.com/drgaciw/open-crm-cpq/pull/848) | Conversion pricing OQ-573-*; no landed ConversionPolicy |
| [DRG-574](https://linear.app/drgamtd-workspace/issue/DRG-574) | Surrender disposition / entitlement end (cite Cluster B / fulfilment contract only) |
| [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) / [#849](https://github.com/drgaciw/open-crm-cpq/pull/849) | Perpetual+maintenance linkage OQ-556-* |
| [DRG-571](https://linear.app/drgamtd-workspace/issue/DRG-571) / [#847](https://github.com/drgaciw/open-crm-cpq/pull/847) | Maintenance pricing OQ-571-* |
| [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) / [#839](https://github.com/drgaciw/open-crm-cpq/pull/839) | Partial-apply residual OQ-565-*; restructure scenarios stay deferred |
| [DRG-563](https://linear.app/drgamtd-workspace/issue/DRG-563) / [#840](https://github.com/drgaciw/open-crm-cpq/pull/840) | Multi-subscription apply ledger OQ-563-* |
| [DRG-562](https://linear.app/drgamtd-workspace/issue/DRG-562) / [#841](https://github.com/drgaciw/open-crm-cpq/pull/841) | Executed-apply gate OQ-562-*; S4 / DRG-625 still NO-GO |
| [DRG-341](https://linear.app/drgamtd-workspace/issue/DRG-341) | Landed hybrid pair / MigrationCreditPolicy — keep; not a substitute for Restructure |
| CAM drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) | Flyway **V1_16–V1_21 reserved** — this brief writes **no** migration |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** Ramp / Jev / dual-read / S4 / asset-write holds stay in force. An implementation brief for DRG-559 is **blocked** until CPQ Decision scores the open **OQ-559-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-559-* row stays open.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |

## Problem (RA-25)

There is no single lifecycle change that ends perpetual maintenance, surrenders the perpetual licence, adds SaaS subscription lines, and applies a conversion credit policy. DealHub RESTRUCTURE / SAP new-contract change are research evidence; using them for this motion is a design inference, not a landed product path.

Today continuation is one operation on one source line (`QuoteContinuationRequest`); net-unit moves are rejected (`SUBSCRIPTION_CONTINUATION_RATE_CHANGED`). The landed hybrid quote (DRG-341) is a declining `ONPREM_SUPPORT` + rising `HYBRID_MIGRATION_RAMP` pair with a 30% book credit — it does **not** store `priorAgreementId` and does **not** surrender `ONPREM_PERPETUAL`. `applied_quote_acceptances` is unique on quote id with a single `subscription_id` (DRG-563 gap). ADR-035 authorizes S1/S2 only. OQ5 Option A first-slice is Adopted; broader restructure scenarios stay deferred; S4 stays unauthorized.

Linear DRG-559 (`Linear read` 2026-11-06): Medium; **In Review**; depends on linkage / conversion / surrender cluster; attachment Cluster B [#652](https://github.com/drgaciw/open-crm-cpq/pull/652).

## Already Adopted (do not re-decide)

Restated from the delegated record § One-time charges and conversion + Cluster B DRG-559 index (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | **One** commercial change with intent **`Restructure`** (not two quotes + manual credit; not a separate cancel-then-new flow as the primary design) | **Adopted (delegated)** How many commercial changes |
| A2 | **New / successor agreement** with required **`priorAgreementId`** pointing at the source agreement | **Adopted (delegated)** Agreement shape |
| A3 | Lines inside the change (baseline): maintenance **`Terminate`**, perpetual surrender as business disposition via **`TERMINATE` + entitlement-end metadata** (not a new Cluster E action token), SaaS **`Add`** with absolute quantity. Landed hybrid pair remains a hybrid quote under this option | **Adopted (delegated)** Lines inside the change + surrender encoding |
| A4 | Credit policy: **`creditPolicyRef`** to the conversion policy; missing version is unresolved / refused (never invent a multiple). Landed **`MigrationCreditPolicy`** stays the phased hybrid path — do not duplicate | **Adopted (delegated)** Which credit policy |
| A5 | Apply shape: **separate commercial application units**, **create before terminate**; correcting amendment is the remedy (OQ5 Option A). Operational revocation additionally requires fulfilment evidence. S3/S4 unauthorized from this brief | **Adopted (delegated)** Apply shape + fulfilment |
| A6 | **First-slice conversion remains excluded.** Metric-only conversion stays its own intent. Conversion pricing / audit contracts must be delivered and verified before execution | Same § |
| A7 | Holds: no product code / schema / flag from docs alone; CAM **V1_16–V1_21** reserved elsewhere; dual-read / S4 / asset-write / Jev / ramp holds untouched | AGENT.md; #805–#812 |

## What the tree does today (restructure)

`verified by reading code` / docs at the base SHA.

1. No production `Restructure` intent API or `priorAgreementId` column wiring as a conversion restructure path — proposal text lives in `specs/conversions.md` / lifecycle-intents proposals only.
2. Continuation cannot express big-bang conversion pricing (rate-changed guard).
3. Hybrid migration path is separate code (`HybridMigrationPolicy` / `MigrationCreditPolicy`) and is not a substitute for A1–A5.
4. Multi-subscription ledger gap remains DRG-563 (#840 residual). Executed-apply gate residual is DRG-562 (#841). Partial-apply restructure scenarios deferred (#839 / OQ5).
5. CAM tip on this `main` is at/before the reserved V1_16–V1_21 band claimed by sibling drafts. This brief claims **no** migration number.

### GitNexus (this checkout)

GitNexus MCP reported **no indexed repositories** this pass (`impact` unavailable). Treat blast radius as **UNKNOWN** — not a clean check. Docs-only change: one new markdown file under `docs/renewals-amendments/prereq/`. A later implementation must run GitNexus `impact` before touching apply ledger, continuation, hybrid migration, or any new Restructure store.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed` under id **OQ-559-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No new Adopted cells. Rows refine **delivery** against A1–A7; they do **not** reopen those contracts. **Do not adopt** open OQs from #847/#848/#849/#839/#840/#841 by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-559-1 | **Intent / document encoding** of A1 | CAM LifecycleChange / amendment document carries typed `Restructure` intent after ADR-035 follow-on acceptance; quote path only holds pricing pins | Quote-only intent; CRM-owned document; invent parallel intents | Yes — API surface |
| OQ-559-2 | **`priorAgreementId` persistence** (A2) | Store on successor CAM contract header + required validation at apply; refuse missing/cross-tenant ids | Quote-only field; soft reference without apply gate | Yes — schema / apply |
| OQ-559-3 | **Flyway / schema numbering** | Would-be only; **next free after reserved CAM V1_16–V1_21**. Number settled when an authorized migration PR exists. **Never** claim V1_16–V1_21 here | Reserve V1_16–V1_21 in this brief; ship schema before OQs close | Yes — any migration |
| OQ-559-4 | **Line-plan enforcement** (A3) vs hybrid pair | Big-bang Restructure requires Terminate + surrender disposition + SaaS Add; hybrid pair stays DRG-341 path and is **refused** as a Restructure substitute | Allow hybrid pair under Restructure intent; allow SaaS-Add-only | Yes — commercial shape AC |
| OQ-559-5 | **`creditPolicyRef` coupling** to DRG-573 (A4) | Require version pin to conversion policy; missing pin → refuse. Do not invent multiples/credits. Cite #848 OQ-573-* without adopting | Allow unnamed credit; hard-code MigrationCreditPolicy into Restructure | Yes — pricing AC |
| OQ-559-6 | **Application unit set** (A5) vs DRG-563 | Three named units (create SaaS, end maintenance, revoke/surrender entitlement) with create-before-terminate; **blocked** until multi-sub ledger OQ-563-* scored | All-or-nothing across agreements; two units only | Yes — apply AC |
| OQ-559-7 | **S4 / DRG-625 / DRG-582 enablement** | Remain **NO-GO**; this brief adds no S4 acceptance criterion | Enable S4 from this docs PR | Yes — hold |
| OQ-559-8 | **Partial-apply / OQ5 restructure scenarios** | Stay deferred per Adopted OQ5 first slice; cite #839 without adopting OQ-565-* | Expand OQ5 first slice to include Restructure | Soft until multi-line AC |
| OQ-559-9 | **First-slice conversion exclusion lift** (A6) | Keep excluded until owner names delivered pricing/audit contracts + Decision score | Lift exclusion in this brief | Yes — execution gate |
| OQ-559-10 | **Fulfilment / revocation scheduling** | REVOKE notBefore=`entitlementEndDate` with confirmed replacement dependency (delegated fulfilment contract); no silent success | Dispatch at pricing time; invent key-server call from CPQ | Soft until DRG-558/574 delivery |
| OQ-559-IMPL | **Implementation brief authorization** | Blocked until blocking OQ-559-* (and cited 562/563/573) close or are explicitly deferred with owner words+time | Authorize implementation from this file alone | Yes |

## Explicit NO-GO

Until blocking **OQ-559-*** rows close (or the owner defers them with words and a time):

- No product code, Flyway/schema, feature flags, topics, gateway routes, or deploys for Restructure.
- No CAM migration number in **V1_16–V1_21**.
- No S4 / DRG-625 / DRG-582 enablement; no dual-read / Jev / ramp / asset-write hold bypass.
- Do not accept `specs/conversions.md` or `specs/lifecycle-intents.md` wholesale.
- Do not adopt open OQs from #847 / #848 / #849 / #839 / #840 / #841.
- Do not treat the landed hybrid pair as the Adopted Restructure shape.

## Verification (this pass)

- [x] Docs-only file under `docs/renewals-amendments/prereq/`
- [x] Adopted A1–A7 restated from delegated record; residual forks Proposed only
- [x] Linear left **In Review** (orchestrator / agent must not mark Done)
- [x] GitNexus unavailable / UNKNOWN — not treated as clean
- [x] Secret scan: no credentials in this file

## Owner decision slots

For each **OQ-559-*** row scored: `Owner decision: ___ (drg, time)`.
