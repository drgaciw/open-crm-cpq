# DRG-573 — Conversion pricing for on-prem → SaaS: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted cells in the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md) § One-time charges and conversion / Cluster B DRG-573 index rows, does **not** accept [`specs/conversions.md`](specs/conversions.md) or invent a landed `ConversionPolicy` schema, does **not** accept Cluster B as a second decision authority, authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a route, enable ramp / Jev / dual-read / S4, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / Cluster B conversion pricing. **Story:** [DRG-573](https://linear.app/drgamtd-workspace/issue/DRG-573/conversion-pricing-policy-for-on-prem-to-saas-quantity-mapping-parity) (RA-39). Linear read this pass: **In Review**. Notion: [RA-39](https://app.notion.com/p/3fbf7cb4e4df81b4866ee57ac7b2ba67) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / spec authority (cite, do not reopen Adopted cells; do not invent landed schema):**

| Artifact | Role |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § One-time charges and conversion + Cluster B DRG-573 rows | **Adopted baseline:** SpendParity with explicitly configured multiple; version-pinned quantity map; defined unit rounding; empty default credit rows; no default price lock or parity ramp; do **not** duplicate or silently sum existing migration-credit code; metric-only conversion stays its own intent; **first-slice conversion remains excluded**; conversion pricing execution still requires delivered and verified pricing/audit contracts; no older design accepted wholesale |
| [cluster-b-onprem-to-saas-and-maintenance-decision-frame.md](cluster-b-onprem-to-saas-and-maintenance-decision-frame.md) ([#652](https://github.com/drgaciw/open-crm-cpq/pull/652), merged) | Historical Cluster B frame + DRG-573 options/defaults. Dispositions already indexed. Do **not** re-litigate Adopted rows. Illustrative Scenario 3 figures are **not** defaults |
| [`specs/conversions.md`](specs/conversions.md) ([#611](https://github.com/drgaciw/open-crm-cpq/pull/611) cluster) | **Proposal only.** Proposed `ConversionPolicy` shape and Scenario 3 ACs. Authorizes no code. This brief does **not** accept it |
| [../cpq-pricing-policy-sanity-check.md](../cpq-pricing-policy-sanity-check.md) Scenario 3 | Illustrative P3 gap (G7): worked numbers are examples, not customer data and not adopted multiples/caps |
| [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) D-04 | **Accepted:** unused-days credit is assigned to DRG-550. This brief does **not** invent a second unused-days formula (e.g. does not adopt `184/365`) |
| [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md) D-01 | **Accepted** renewal basis enum. A conversion parity multiple is a **different** question; this brief does not add SpendParity to that enum |
| [drg-609-pricing-design-acceptance-record.md](drg-609-pricing-design-acceptance-record.md) + [story-drg-609-pricing-service-continuation-design.md](story-drg-609-pricing-service-continuation-design.md) | Accepted continuation **evaluate** design. The acceptance record states the design note has **no conversion-price rule**. Delivery of conversion pricing still needs verified contracts (delegated hold) |
| [`specs/conversions.md`](specs/conversions.md) § DRG-640 conversion figure | Cite-only Proposed figure frame (parity ARR / year-1 net / delta vs maintenance). Owner slot blank. This brief does **not** choose the ARR `conversion` figure |

**Related Wave-2 / Cluster B drafts (cite only; do not adopt open OQs):**

| Ticket / draft | Linear / GH | What this brief must not adopt |
|---|---|---|
| [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559) | In Review; Cluster B § DRG-559 in [#652](https://github.com/drgaciw/open-crm-cpq/pull/652) | Restructure intent / `priorAgreementId` / apply units. **No Wave-2 residual Decision brief** for DRG-559 was present on this pass — cite Cluster B only; do not invent OQ-559-* closures here |
| [DRG-574](https://linear.app/drgamtd-workspace/issue/DRG-574) | In Review; Cluster B § DRG-574 | Surrender disposition / entitlement end / dual-use. **No Wave-2 residual Decision brief** present this pass — cite only |
| [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) | In Review; Cluster B § DRG-556 | Perpetual + maintenance linkage. **No Wave-2 residual Decision brief** present this pass — cite only |
| [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576) / [#831](https://github.com/drgaciw/open-crm-cpq/pull/831) | Wave-2 catalog residual | Opaque `quantityMapping` / `creditPolicyRef` (Adopted 576-F13). Cite #831; **do not adopt** its open OQ1–OQ12 |
| [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564) / [#822](https://github.com/drgaciw/open-crm-cpq/pull/822) | Wave-2 pricing-audit residual | PolicyDecision / commercial-decision trail. Cite only; do not invent a second audit store |
| [DRG-550](https://linear.app/drgamtd-workspace/issue/DRG-550) Wave-2 residual | Cite unused-days credit home | Do not reopen Accepted proration; do not lift `NoCredit` here |
| [DRG-568](https://linear.app/drgamtd-workspace/issue/DRG-568) / [#807](https://github.com/drgaciw/open-crm-cpq/pull/807) | Price-lock / escalator draft | Keep OF-568-* Proposed; ramp flag stays off |
| [DRG-341](https://linear.app/drgamtd-workspace/issue/DRG-341) | Hybrid migration ramp credit | Landed 30% book credit on declining line — keep; do not re-implement under a second path |
| [DRG-539](https://linear.app/drgamtd-workspace/issue/DRG-539) | Price-book read | Required only if `TargetListLessDiscount` is exercised |
| [DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575) / [`specs/arr-bridge.md`](specs/arr-bridge.md) | ARR bridge `conversion` component | Classification Adopted at policy level; figure choice stays Proposed (DRG-640) |
| CAM drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) | Flyway **V1_16–V1_21 reserved** | This brief writes **no** migration and claims **no** CAM version in that band |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** `opencrm.pricing.ramp.enabled` stays default off. No `opencrm.jev.*.enabled` flag is turned on. Dual-read / S4 enablement / asset-write holds stay in force. An implementation brief for DRG-573 is **blocked** until CPQ Decision scores the open **OQ-573-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-573-* row stays open. **Do not invent conversion-credit schema as landed.**

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `verified by GitNexus` | Impact/query after `npx gitnexus@latest analyze --index-only` on this checkout. |

## Problem (RA-39)

On-prem → SaaS mid-term conversion needs a **typed conversion price**, not today's subscription continuation. A perpetual cores + maintenance book converting to a SaaS metric needs: a versioned quantity map (ratio, rounding, minimum, version pin), a price basis (`SpendParity(multiple)` | `TargetListLessDiscount` | `Override`), optional parity ramp, a credit stack with caps and application target (`FirstInvoice` | `Spread` | `DecliningLine`), cross-agreement credit transfer, and an optional price-lock term.

Agreement-level restructure ([DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559)) and a metric-only `ConvertMetric` do **not** carry that policy. Inherited-rate continuation rejects a net-unit move with `SUBSCRIPTION_CONTINUATION_RATE_CHANGED`, so a conversion **cannot** be a continuation of the current kind. The landed hybrid credit ([DRG-341](https://linear.app/drgamtd-workspace/issue/DRG-341) / `MigrationCreditPolicy`) is one phased shape (30% of book on the declining line's first period, capped by that line's TCV / first period) — it fits a hybrid ramp, not a big-bang conversion whose declining maintenance line has ended.

Illustrative Scenario 3 (sanity check; **example numbers only**): 80 perpetual cores + $40,000/yr maintenance → 160 CU at spend-parity 2.5× → unit $52.08 → ARR $99,993.60; unused prepaid $20,164.38 and trade-in cap $19,998.72 → year-1 net $59,830.50. Those figures are **not** adopted defaults (no silent 2.5×, 20%, or `184/365`).

The 2026-10-17 delegation already chose the commercial **defaults** for those forks. What remains un-scored for an **implementation gate** is residual delivery design: where the versioned policy body lives, how the required multiple and credit rows are configured without inventing values, how map pins couple to DRG-576 without accepting `catalog-model.md`, how UnusedPrepaid couples to DRG-550 without a second day formula, how MigrationCreditPolicy stays the sole phased path, when first-slice exclusion lifts, Flyway numbering outside the reserved CAM band, and audit/ARR couplings — all as **Proposed OQ-573-*** without inventing conversion-credit schema as landed.

Without that split, an implementation story could re-open Adopted Cluster B cells, invent a second 30% credit path, claim CAM **V1_16–V1_21**, treat Scenario 3 numbers as defaults, accept `specs/conversions.md` wholesale, or start conversion pricing while first-slice conversion remains excluded.

Linear DRG-573 (`Linear read` 2026-11-06): High; **In Review**; related DRG-559, DRG-341, DRG-548; attachments include Cluster B draft [#652](https://github.com/drgaciw/open-crm-cpq/pull/652) and Notion RA-39 / sanity check.

## Already Adopted (do not re-decide)

Restated from the delegated record § One-time charges and conversion + Cluster B DRG-573 index (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | Price basis for conversion is **`SpendParity`** with an **explicitly configured multiple**; missing multiple → unresolved / refused, **never** invent 2.5 | **Adopted (delegated)** DRG-573 / Price basis |
| A2 | Rounding holds the **unit** at scale 2 half-up (matching continuation / Accepted proration snapshot style); ARR is derived; does not amend uplift D-05 | **Adopted (delegated)** What rounding holds fixed |
| A3 | Default **credit list is empty** until a rung adds a row; rows combine only when each named row is present | **Adopted (delegated)** Credit rows |
| A4 | Landed phased credit stays in **`MigrationCreditPolicy`** (30% of book, declining-line first period caps). Do **not** duplicate or silently sum that credit under a second path | **Adopted (delegated)** + § One-time charges |
| A5 | Application target: **`FirstInvoice`** for big-bang (source maintenance ended / no hybrid pair); **`DecliningLine`** when `HybridMigrationPolicy` accepts the hybrid pair | **Adopted (delegated)** Application target |
| A6 | Catalogue relationship is the quantity-map **source of truth** when DRG-576 model is in place; the **priced line stores a map version pin**; no pin → unresolved | **Adopted (delegated)** Where the quantity map lives |
| A7 | Map rounding default **`Nearest`**, half-up to whole quantity, **minimum 1** | **Adopted (delegated)** Map rounding and minimum |
| A8 | Credit transfer: consumption on the **target** agreement; source agreement stored and not reduced again; unused remainder expires at end of the row's application window (`FirstInvoice` = that invoice) | **Adopted (delegated)** Credit transfer and expiry |
| A9 | Default **no price lock** (field unset); unset lock does not suppress Accepted renewal uplift. Length belongs with DRG-568 | **Adopted (delegated)** Price-lock length |
| A10 | **`ConvertMetric`** stays its own intent; same-licence-model metric change does not use ConversionPolicy | **Adopted (delegated)** |
| A11 | Default **`parityRamp` empty**; one multiple covers the term unless an explicit list is configured | **Adopted (delegated)** Parity ramp |
| A12 | One **Restructure** + successor agreement with **`priorAgreementId`** remains the commercial shape (DRG-559 disposition). Metric-only stays separate | Delegated § One-time charges |
| A13 | **First-slice conversion remains excluded.** Conversion pricing execution still requires **delivered and verified** pricing/audit contracts; no older design accepted wholesale | Same |
| A14 | Missing policy / conversion multiple / mapping version is a **refused request input**, never an invented commercial value | Delegated § Catalog and policy |
| A15 | ARR bridge: conversion uses a group accounting anchor on the arriving SaaS line (base = prior recurring ARR such as maintenance; `conversion` = new recurring − base; source lines contribute zero bridge deltas). Figure choice among Scenario 3 candidates stays Proposed (DRG-640) | Delegated § Pricing and ARR; conversions.md DRG-640 |
| A16 | Holds: ramp flag off; no Jev enablement; no product code / schema / flag from docs PRs alone; CAM **V1_16–V1_21** reserved by other drafts — not available here; S4 / dual-read / asset-write holds untouched | AGENT.md; #805–#812 |

## What the tree does today (conversion pricing)

`verified by reading code` at the base SHA.

1. Continuation operations are `AMENDMENT`, `EXPANSION`, `RENEWAL`, and `EXTENSION` (`QuoteContinuationRequest`). `QuoteContinuationPolicy` plans from an accepted source line and keeps/uplifts accepted unit rates. Pricing that moves the net unit off that plan is rejected with `SUBSCRIPTION_CONTINUATION_RATE_CHANGED` (`QuoteService`).
2. Recurring continuation sources in `AcceptedRampSnapshots` include `SAAS_SEAT_*`, `ONPREM_SUPPORT`, `HYBRID_MIGRATION_RAMP`. `ONPREM_PERPETUAL` is a catalog type and is **outside** that continuation set.
3. Landed hybrid credit: `MigrationCreditPolicy` (30% of book, scale 2 half-up, capped by declining-line gross TCV and first period; no spill to rising line). `HybridMigrationPolicy` requires the declared dual-entitlement window to equal contiguous positive-quantity overlap. Declining member `ONPREM_SUPPORT`; rising member `HYBRID_MIGRATION_RAMP`.
4. Tree search for `ConversionPolicy`, `SpendParity`, `TradeInPctOfBook`, `parityRamp` under `backend/` returns **docs references only** — no production Java type or Flyway table for conversion credit schema. **Do not treat proposal text as landed.**
5. CAM tip on this `main` is `V1_15__document_apply_replay_evidence.sql`. Quote tip includes `V1_16__ramp_escalator_compounding.sql`. Sibling Wave-2 briefs reserve CAM **V1_16–V1_21** for other CAM drafts (#805–#812). This brief claims **no** migration number.
6. [`specs/conversions.md`](specs/conversions.md) and Cluster B remain documentation. DRG-609 acceptance does not add a conversion-price rule.

### GitNexus (this checkout)

`verified by GitNexus` after `npx gitnexus@latest analyze --index-only` (index reported truncated process discovery: entry-point candidates dropped / walks cut by budget — **not a clean completeness claim**).

| Symbol | Upstream risk | Notes |
|---|---|---|
| `MigrationCreditPolicy` | **LOW** (1 direct) | Phased credit calculator. Docs-only this pass — **do not edit**. Future product work must re-run impact |
| `HybridMigrationPolicy` | **HIGH** | Direct callers into pricing `execute` / `simulate` / `simulateBulk`. **WARN:** any future code change is HIGH risk; this docs brief must not touch it |
| Concept query (conversion / SpendParity) | Weak / off-topic process hit | Graph did not surface a `ConversionPolicy` symbol — consistent with “not landed.” Text search remains required for proposal docs |

**Docs-only change impact:** this branch adds one markdown file under `docs/renewals-amendments/prereq/`. No production symbol is edited. A later implementation must run GitNexus `impact` before changing `MigrationCreditPolicy`, `HybridMigrationPolicy`, `QuoteContinuationPolicy`, or any new conversion store. Pre-commit `detect_changes` on this docs-only diff is reported in the PR / Linear comment; `partial` / `truncated` / `UNKNOWN` is **not** a clean check.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed` under id **OQ-573-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No new Adopted cells. Rows refine **delivery** against A1–A16; they do **not** reopen those contracts. **Do not adopt** open OQs from DRG-559/574/556 Wave-2 drafts (none present this pass) or from #831 / #822 / #807 by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-573-1 | **Policy body home** (delivery of A1–A11) | Versioned ConversionPolicy bodies in **CAM policy records** (tenant + account/agreement/document line keys), consistent with delegated renewal-policy storage pattern; pricing-service evaluates; quote retains version pins | Pricing-owned store only; quote-local JSON only; invent parallel tables in both | Yes — persistence |
| OQ-573-2 | **Flyway / schema numbering** | Would-be only; **next free after reserved CAM V1_16–V1_21** (or pricing next integer if OQ-573-1 chooses pricing-owned). Number settled when an authorized migration PR exists. **Never** claim V1_16–V1_21 here | Reserve V1_16–V1_21 in this brief; ship schema before OQs close | Yes — any migration |
| OQ-573-3 | **Configured SpendParity multiple** (A1 / A14) | Multiple is a **required configured input** on the resolved policy rung (clause → line → agreement → account); missing → refuse. Scenario 3's 2.5 is illustrative only | Hard-code 2.5; silent default 2.5; allow null → treat as 1.0 | Yes — parity price AC |
| OQ-573-4 | **When empty credit list gains rows** (A3) | Keep empty until a **separate owner-configured rung** adds `UnusedPrepaid` / `TradeInPctOfBook` / `Fixed`; no silent Scenario 3 stack | Always include both unused + trade-in; copy 30% book into credits[] | Soft while empty; hard when credit ACs ship |
| OQ-573-5 | **UnusedPrepaid day-count** | Day count / money from **DRG-550 / Accepted stub** path once credit is lifted there; do **not** adopt `184/365` or a second formula in ConversionPolicy | Embed Scenario 3 `184/365` in ConversionPolicy; invent conversion-only day math | Yes — unused prepaid AC |
| OQ-573-6 | **MigrationCreditPolicy vs credits[] `MigrationBookPct`** | Keep **code path** = `MigrationCreditPolicy` for hybrid pairs (A4/A5 DecliningLine). Proposal field name in `specs/conversions.md` stays documentation only until a later mapping story — **no second calculator** | Reimplement 30% inside ConversionPolicy evaluator; sum both | Yes — hybrid / big-bang credit ACs |
| OQ-573-7 | **`TargetListLessDiscount` / `Override` readiness** | Refuse those bases until DRG-539 (list) / named Override gate are delivered and verified; missing list → no price | Allow with quote-local list; silent fallback to SpendParity | Yes — non-parity basis ACs |
| OQ-573-8 | **Quantity-map SoT vs DRG-576 / #831** | Affirm A6: catalog relationship holds map SoT when 576 model lands; line stores pin. Cite #831 only; **do not adopt** OQ1–OQ12; keep `quantityMapping` **opaque** (576-F13) until conversion execution is authorized | Evaluate ratio inside product-service now; accept `catalog-model.md` wholesale; pin-less map | Yes — map pin AC |
| OQ-573-9 | **Cross-agreement creditTransfer persistence** | Store sourceAgreement + targetAgreement + expiry on each credit **snapshot** at price time; no schema invented in this brief | Mutate source agreement balances; allow remainder to return to source | Soft for docs; hard for transfer AC |
| OQ-573-10 | **Price-lock coupling to DRG-568** | Keep A9 (unset); length/suppression scored on DRG-568 / #807. **Do not adopt** OF-568-*. Ramp flag stays off | Set `priceLockMonths` default here; suppress uplift by silence | Soft while unset; hard if lock ships |
| OQ-573-11 | **First-slice exclusion lift** (A13) | Exclusion stands until owner authorizes a **named conversion execution slice** after pricing/audit contracts are verified (DRG-609 delivery + DRG-564/612 trail). Closing this brief alone does **not** enable conversion | Closing OQ-573-* alone enables Restructure conversion pricing; widen continuation to carry conversion | Yes — any conversion execute AC |
| OQ-573-12 | **Coupling to DRG-559 / 574 / 556** | Cite Cluster B + Linear only; wait for those stories' own residual briefs if/when filed; **do not invent** surrender tokens, linkage columns, or apply units here | Start conversion price without Restructure / linkage / entitlement-end dates | Soft for policy docs; hard for end-to-end conversion AC |
| OQ-573-13 | **ARR `conversion` figure (DRG-640)** | Leave Proposed candidates (parity ARR / year-1 net / delta vs maintenance); default proposal in conversions.md remains unscored. Bridge classification A15 stands | Pick a figure in this brief; put year-1 credits into run-rate | Soft for pricing; hard for ARR AC-8 |
| OQ-573-14 | **Audit / PolicyDecision coupling** | Cite #822 / DRG-612 only; conversion confirmation must eventually pin the **one** pricing-owned trail — do not invent a conversion-only audit table here | Widen `commercial_decision_audit`; mint quote-local fake traces | Soft until store exists; hard for commercial confirm |
| OQ-573-15 | **Accept `specs/conversions.md`?** | Keep **Proposal**; score delivery via OQ-573-* without wholesale acceptance | Accept conversions.md as ADR now; treat Scenario 3 ACs as Adopted numbers | Yes — if treated as schema mandate |
| OQ-573-MKT | Sanity-check / vendor research (Oracle BRM, Snow, DealHub, SAP) | Cite as research only; no vendor parity commitment | Encode DealHub RESTRUCTURE field names as wire format | Soft |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-573-1 — Policy body home

| Option | What the owner would be choosing |
|---|---|
| **A. CAM versioned policy records + pricing evaluate (Recommended, Proposed)** | Aligns with delegated renewal-policy storage pattern. Pricing computes; CAM stores versioned body; quote/CAM lines pin versions. Does **not** ship Flyway in this brief. |
| **B. Pricing-owned store only** | Viable if DRG-564/612 sequence places commercial-decision bodies in pricing; still needs pins on the commercial document. |
| **C. Quote-local JSON only** | Weak audit; conflicts with durable confirmation expectations (A13 / OQ-573-14). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-573-2 — Flyway numbering

| Option | What the owner would be choosing |
|---|---|
| **A. Next free after V1_16–V1_21; number at migration PR (Recommended, Proposed)** | Affirms A16. Docs claim no reserved slot. |
| **B. Claim V1_16–V1_21** | Conflicts with #805–#812. **NO-GO.** |
| **C. Ship schema before OQs close** | Product/schema **NO-GO** while blocking OQs open. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-573-3 — SpendParity multiple configuration

| Option | What the owner would be choosing |
|---|---|
| **A. Required configured multiple; refuse if missing (Recommended, Proposed)** | Restates A1/A14. 2.5 stays illustrative. |
| **B. Hard-code / silent 2.5** | Contradicts A14. **NO-GO.** |
| **C. Null means 1.0** | Silent under-price; hides missing commercial input. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-573-4 — Populating credit rows

| Option | What the owner would be choosing |
|---|---|
| **A. Stay empty until configured rung adds rows (Recommended, Proposed)** | Restates A3. Scenario 3 unused + trade-in are fixtures for a later configured stack, not defaults. |
| **B. Always unused + trade-in** | Reopens credit-rows alternative; invents caps (20% year-1) as standards. |
| **C. Copy 30% book into credits[]** | Conflicts with A4. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-573-5 — UnusedPrepaid arithmetic

| Option | What the owner would be choosing |
|---|---|
| **A. DRG-550 / Accepted stub day path (Recommended, Proposed)** | Affirms proration ADR D-04 assignment. No `184/365` adoption. |
| **B. Embed Scenario 3 fraction in ConversionPolicy** | Second formula. **NO-GO** unless owner supersedes the ADR. |
| **C. Conversion-only day math** | Same conflict as B. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-573-6 — Phased credit path

| Option | What the owner would be choosing |
|---|---|
| **A. Keep `MigrationCreditPolicy` as sole phased calculator (Recommended, Proposed)** | A4/A5. Proposal docs may name `MigrationBookPct` for vocabulary; runtime stays the landed class until a mapping story is authorized. |
| **B. Second ConversionPolicy calculator** | Duplicate/sum risk. **NO-GO.** |
| **C. Sum both** | Explicitly refused by delegated “not duplicated or silently summed.” **NO-GO.** |

**Recommended (Proposed):** A. **WARN:** GitNexus upstream risk on `HybridMigrationPolicy` is **HIGH** — future edits need a fresh impact before any code PR. `Owner decision: ___ (drg, time)`.

### OQ-573-7 — Non-parity bases

| Option | What the owner would be choosing |
|---|---|
| **A. Refuse until DRG-539 / Override gate verified (Recommended, Proposed)** | Keeps SpendParity as the first customer-facing basis (A1) without pretending list/override are ready. |
| **B. Quote-local list override** | Narrow escape; still needs audit (OQ-573-14). |
| **C. Silent fallback to SpendParity** | Hides the selected basis. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-573-8 — Map SoT vs catalog residual

| Option | What the owner would be choosing |
|---|---|
| **A. Catalog SoT + line pin; cite #831 only (Recommended, Proposed)** | Affirms A6 and 576-F13 opacity. Does **not** adopt #831 OQs or accept `catalog-model.md`. |
| **B. Product-service evaluates ratio now** | Conflicts with opaque ref + “never a price” on the catalog read. |
| **C. Pin-less map on ConversionPolicy only** | Historical alternative; loses catalogue version authority. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-573-9 — Credit transfer snapshot

| Option | What the owner would be choosing |
|---|---|
| **A. Snapshot source/target/expiry on priced credit rows (Recommended, Proposed)** | Affirms A8 without inventing balances tables in this brief. |
| **B. Mutate source agreement** | Double-consumption risk. |
| **C. Remainder returns to source** | Historical alternative; needs explicit owner supersession of A8. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-573-10 — Price lock

| Option | What the owner would be choosing |
|---|---|
| **A. Keep unset; defer length to DRG-568 (Recommended, Proposed)** | A9. Do not adopt #807 OF-*. |
| **B. Default months here** | Invents a commercial length. |
| **C. Silent uplift suppression** | Conflicts with Accepted renewal uplift when lock unset. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-573-11 — First-slice exclusion

| Option | What the owner would be choosing |
|---|---|
| **A. Exclusion until named execution slice + verified contracts (Recommended, Proposed)** | Restates A13. Closing this Decision brief is necessary but **not sufficient**. |
| **B. Enable on OQ close alone** | Skips pricing/audit delivery gate. |
| **C. Widen continuation to carry conversion** | Conflicts with rate-changed guard and A10/A12 intent split. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-573-12 — Cluster B siblings

| Option | What the owner would be choosing |
|---|---|
| **A. Cite-only; no invented sibling OQs (Recommended, Proposed)** | DRG-559/574/556 Wave-2 residual briefs were **not present** this pass. Cluster B [#652](https://github.com/drgaciw/open-crm-cpq/pull/652) remains the cite. |
| **B. Price conversion without Restructure/linkage/entitlement-end** | Incomplete commercial motion; blocked for E2E ACs. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-573-13 — ARR conversion figure

| Option | What the owner would be choosing |
|---|---|
| **A. Leave DRG-640 Proposed (Recommended, Proposed)** | A15 classification stands; figure slot stays blank. |
| **B. Choose parity ARR / year-1 net / delta here** | Belongs on DRG-640 / ARR story, not a silent adopt. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-573-14 — Audit trail

| Option | What the owner would be choosing |
|---|---|
| **A. One pricing-owned trail via #822 / DRG-612 (Recommended, Proposed)** | Cite only; do not invent conversion-only audit schema. |
| **B. Widen `commercial_decision_audit`** | Wrong grain (draft-open vs price body). |
| **C. Fake quote-local traces** | Undermines CAM validation. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-573-15 — conversions.md acceptance

| Option | What the owner would be choosing |
|---|---|
| **A. Keep Proposal; deliver via scored OQs (Recommended, Proposed)** | Avoids treating illustrative ACs as Adopted money rules. |
| **B. Accept wholesale as ADR** | Would pull open Scenario 3 numbers and unaccepted sibling scopes. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed **OQ-573-***.
2. CPQ Decision must score the OQ-573-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work, Flyway, Drools edits, or flag flips for ConversionPolicy / conversion credit schema.
4. Even after forks close: A1–A16 hold; first-slice conversion stays excluded until a **named** execution authorization; ramp flag stays off; no Jev / dual-read / S4 / asset-write hold bypass; `specs/conversions.md` stays non-accepted until the owner separately accepts it; sibling 559/574/556 / #831 / #822 / #807 open OQs stay unadopted; CAM **V1_16–V1_21** stay reserved elsewhere.
5. Linear status for DRG-573 stays **In Review** until the owner merges and DoD says otherwise. This brief does not mark Done.

## Out of scope

- Creating any migration or Java/TS type named `ConversionPolicy`, credit-stack tables, or conversion-credit schema.
- Editing `MigrationCreditPolicy`, `HybridMigrationPolicy`, `QuoteContinuationPolicy`, catalog relationship DDL, CAM apply ledger, or continuation rate-changed guards.
- Enabling `opencrm.pricing.ramp.enabled`, any `opencrm.jev.*.enabled` flag, dual-read flip, S4 enablement, or asset-write hold bypass.
- Adopting open OQs from DRG-559 / DRG-574 / DRG-556 Wave-2 drafts (none present this pass), DRG-576 #831, DRG-564 #822, or DRG-568 #807.
- Choosing the DRG-640 ARR `conversion` figure or accepting Scenario 3 illustrative multiples/caps as defaults.
