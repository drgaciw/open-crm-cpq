# DRG-548 — Unified LifecycleChange intent model: Decision brief (owner forks)

**Status:** Proposal only. Decision brief for CPQ Decision scoring. This file does **not** reopen Adopted cells in the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md) § Fork disposition index Cluster E (E1–E7), does **not** accept [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md) wholesale, does **not** accept Cluster E as a second decision authority, authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a route, enable ramp / Jev / dual-read / S4 / S7, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / Cluster E LifecycleChange model. **Story:** [DRG-548](https://linear.app/drgamtd-workspace/issue/DRG-548/unified-lifecyclechange-intent-model-cancel-restructure-suspend-resume) (RA-14). Linear read this pass: **In Review**. Notion: [RA-14](https://app.notion.com/p/3fbf7cb4e4df8135a3d4c2db22d35523) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / spec authority (cite, do not reopen Adopted cells; do not invent landed schema):**

| Artifact | Role |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Fork disposition index **E** (E1–E7) | **Adopted baseline:** nine document intents; quote-service authors intent / CAM copies; no default-policy table; first-slice submit `AMEND` + slot `AMENDMENT` only; keep `RESTRUCTURE` and `CONVERT` separate until DRG-573; `EXTENSION`→document `RENEW`; `EXPANSION`→document `AMEND`; storage nine / submit `AMEND` only |
| [cluster-e-lifecyclechange-model-decision-frame.md](cluster-e-lifecyclechange-model-decision-frame.md) ([#651](https://github.com/drgaciw/open-crm-cpq/pull/651), merged) | Historical Cluster E frame + DRG-548 / 552 / 553 options. Dispositions already indexed. Do **not** re-litigate Adopted rows. Frame GAP text about “no LifecycleChange type” is **historical** (pre–DRG-583); see “What the tree does today” |
| [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md) | **Proposal only.** Lists `Extend` / `Expand` and a broader ChangeLine set. Authorizes no code. This brief does **not** accept it |
| ADR-035 | Accepted for S1/S2 placement only — D-03 places LifecycleChange in quote-service; does **not** authorize widening intent create/submit beyond Adopted E7 |
| Quote `V1_14__lifecycle_changes.sql` / `LifecycleChangeService` (DRG-583 phase A) | Landed **storage + create/submit for intent `AMEND` only**. Not authority to enable Cancel / Restructure / Suspend / Resume / Transfer / TrueUp / Convert create paths |

**Related Wave-2 drafts (cite only; do not adopt open OQs):**

| Ticket / draft | What this brief must not adopt |
|---|---|
| [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559) / [#850](https://github.com/drgaciw/open-crm-cpq/pull/850) | On-prem→SaaS restructure OQ-559-*; `priorAgreementId` / Restructure delivery. Cite A1–A7 and E4 only |
| [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558) / [#817](https://github.com/drgaciw/open-crm-cpq/pull/817) | Commitment-state OQ1–OQ11; Suspend/Resume **fulfilment** projections stay on that brief |
| [DRG-458](https://linear.app/drgamtd-workspace/issue/DRG-458) / [#816](https://github.com/drgaciw/open-crm-cpq/pull/816) | GenerateContract OQ1–OQ11; contract emission is not intent vocabulary |
| [DRG-460](https://linear.app/drgamtd-workspace/issue/DRG-460) / [#809](https://github.com/drgaciw/open-crm-cpq/pull/809) | Activation OQ1–OQ11; PendingActive path is not an intent token decision |
| [DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552) / [DRG-553](https://linear.app/drgamtd-workspace/issue/DRG-553) | Cluster E L* / D* action-code and date forks — sibling stories; not scored as OQ-548-* |
| [DRG-647](https://linear.app/drgamtd-workspace/issue/DRG-647) / [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576) | Catalog eligibility per intent — cite as residual dependency, do not score eligibility matrix here |
| CAM drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) | Flyway **V1_16–V1_21 reserved** — this brief writes **no** CAM migration and claims **no** number in that band |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** Ramp / Jev / dual-read / S4 / S7 / asset-write holds stay in force. An implementation brief for DRG-548 is **blocked** until CPQ Decision scores the open **OQ-548-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-548-* row stays open.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |

## Problem (RA-14)

Market research (DealHub orderType, Salesforce initiateAmendment/Renewal/Cancellation, Zuora Suspend/Resume/OwnerTransfer/Cancellation, Oracle ABO) recommends one change root with a typed intent that selects default policies. At research time, quote continuation stored only `AMENDMENT` / `EXPANSION` / `RENEWAL` / `EXTENSION` with no cancel, restructure, suspend, resume, transfer, true-up, or convert.

On `main` today the **document spine** already stores nine intents (`AmendmentIntent` / `chk_amendment_document_intent` in CAM `V1_7__contract_documents.sql`). Quote-service now has a `LifecycleChange` type (DRG-583 / `V1_14__lifecycle_changes.sql`) but **create and SQL check accept `AMEND` only**. Default policy-per-intent is still absent (Adopted E3). `specs/lifecycle-intents.md` still proposes `Extend` / `Expand`, which disagree with Adopted E1 / E5 / E6 and with the S1 check — that proposal is **not** accepted here.

Linear DRG-548 (`Linear read` 2026-11-06): High; **In Review**; attachment Cluster E [#651](https://github.com/drgaciw/open-crm-cpq/pull/651). Related: DRG-552, DRG-553, DRG-647, DRG-597, DRG-573, DRG-561, DRG-472, DRG-546 (Done), DRG-461.

## Already Adopted (do not re-decide)

Restated from the delegated record Cluster E index rows E1–E7 (`read`) selecting the Cluster E baseline Defaults at `285ffef8e08274600090d8ba077a0c92f489c062`. This Decision brief does **not** reopen these rows. L1–L7 and D1–D6 are Adopted on the same index but belong to DRG-552 / DRG-553; they are **not** reopened here and are **not** listed as OQ-548-*.

| ID | Contract | Source |
|---|---|---|
| A1 (E1) | Keep the **nine** document intent tokens: `RENEW`, `AMEND`, `CANCEL`, `RESTRUCTURE`, `TRANSFER`, `SUSPEND`, `RESUME`, `TRUE_UP`, `CONVERT`. Do **not** add `EXTEND` / `EXPAND`; do **not** collapse to the four quote continuation operations | **Adopted (delegated)** E1 |
| A2 (E2) | Once a LifecycleChange type exists, **quote-service authors** intent; CAM **copies** onto `amendment_documents.intent` (ADR-035 D-03 placement). No second authoring writer; do not make `QuoteContinuationRequest.Operation` the only intent | **Adopted (delegated)** E2 |
| A3 (E3) | **No** default-policy table. Future first-slice submit uses intent `AMEND` and policy slot `AMENDMENT` only. Other tokens remain storable on the document spine | **Adopted (delegated)** E3 |
| A4 (E4) | Keep **both** `RESTRUCTURE` and `CONVERT`. Neither selects a conversion policy until DRG-573. `CONVERT_METRIC` stays a line action | **Adopted (delegated)** E4 |
| A5 (E5) | Quote `continuation_operation = EXTENSION` maps to document intent **`RENEW`**. No new `EXTEND` token; no historical quote backfill | **Adopted (delegated)** E5 |
| A6 (E6) | Quote `EXPANSION` stays `DealType.EXPANSION` + continuation `EXPANSION`; a future document copies intent **`AMEND`** (not `EXPAND`, not `RENEW`) | **Adopted (delegated)** E6 |
| A7 (E7) | Storage allows nine intents; a future first-slice **submit** allows **`AMEND` only**. Enabling `RENEW` or all nine is a later owner act | **Adopted (delegated)** E7 |
| A8 | Holds: no product code / schema / flag from docs alone; CAM **V1_16–V1_21** reserved elsewhere; dual-read / S4 / S7 / asset-write / Jev / ramp holds untouched; S3–S8 need separate implementation authorization | AGENT.md; delegated record authority; #805–#812 |

## What the tree does today (intent)

`verified by reading code` / docs at the base SHA.

1. **CAM document intents (nine):** `AmendmentIntent.java`; SQL `chk_amendment_document_intent` in `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql`.
2. **Quote LifecycleChange (phase A):** `lifecycle_changes.intent` constrained to `'AMEND'` (`V1_14__lifecycle_changes.sql` `chk_lifecycle_changes_intent`). `LifecycleChangeService` rejects any other create intent with `INTENT_NOT_SUPPORTED_IN_SLICE` and forces `AMEND` + line `policy_slot = AMENDMENT`.
3. **Quote continuation (separate facade):** `QuoteContinuationRequest.Operation` remains `AMENDMENT` / `EXPANSION` / `RENEWAL` / `EXTENSION` (`V1_12__continuation_operation.sql`). Extension vs renewal distinction on the quote is Done (DRG-546); it is **not** a document `EXTEND` token (A5).
4. **No default-policy table** and no enforced intent→slot map beyond the phase-A hard-code for `AMEND`→`AMENDMENT` (A3).
5. **`specs/lifecycle-intents.md`** remains Status: Proposal only; its `Extend` / `Expand` tokens are **not** what `V1_7` or Adopted E1 store.
6. CAM tip on this `main` is at/before the reserved V1_16–V1_21 band claimed by sibling drafts. This brief claims **no** CAM migration number. Quote Flyway already used `V1_14` / `V1_15` for phase A / pricing validation — any future intent-check widen is a **new** authorized migration, not claimed here.

### GitNexus (this checkout)

GitNexus MCP reported **no indexed repositories** this pass (`impact` / `query` unavailable). Local CLI fallback `.gitnexus/run.cjs` is **absent** (`MODULE_NOT_FOUND`). Treat blast radius as **UNKNOWN** — not a clean check. Docs-only change: one new markdown file under `docs/renewals-amendments/prereq/`. A later implementation must run GitNexus `impact` before widening `chk_lifecycle_changes_intent`, `LifecycleChangeService` create/submit allow-lists, CAM copy-on-create of intent, or any default-policy enforcement path.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed` under id **OQ-548-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No new Adopted cells. Rows refine **delivery** against A1–A8; they do **not** reopen those contracts. **Do not adopt** open OQs from #850 / #817 / #816 / #809 by scoring a row here. Do **not** accept `specs/lifecycle-intents.md` by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-548-1 | **Quote create allow-list vs A7** | Keep quote create/SQL at **`AMEND` only** until a separately authorized slice widens `chk_lifecycle_changes_intent` + service gates. Document intents may already store nine on CAM | Open all nine on quote create in this brief; invent `EXTEND`/`EXPAND` on quote | Yes — create API |
| OQ-548-2 | **CAM copy path (A2) wiring** | Quote LifecycleChange remains system of record for intent; CAM document create copies the string into `amendment_documents.intent` with mismatch refused. No CAM-authored intent | CAM authors; derive document intent from `QuoteContinuationRequest.Operation` only | Yes — cross-service AC |
| OQ-548-3 | **Continuation → document map (A5/A6)** | When a document is produced from continuation: `EXTENSION`→`RENEW`, `EXPANSION`→`AMEND`, `RENEWAL`→`RENEW`, `AMENDMENT`→`AMEND`. Quote column remains the EXTENSION/RENEWAL distinction | Add document `EXTEND`/`EXPAND` (rejects A1); collapse EXTENSION into deal_type only | Yes — document intent AC |
| OQ-548-4 | **Default policy delivery (A3)** | No schema table; optional **docs-only** intent→slot map for later stories; first-slice runtime continues `AMEND`→`AMENDMENT` only. Enforcement of a broader map needs a later Decision | Ship SQL default-policy table now; seller free-picks every slot with no AMEND default | Soft until multi-intent submit |
| OQ-548-5 | **`specs/lifecycle-intents.md` disposition** | Leave as **unaccepted proposal**. Where it disagrees with A1/A5/A6/`V1_7`, the Adopted record and migrations win. Do not merge its ChangeLine/date sections through this ticket | Accept the spec wholesale; migrate in `EXTEND`/`EXPAND` | Yes — vocabulary AC |
| OQ-548-6 | **RESTRUCTURE / CONVERT enablement (A4)** | Keep both tokens storable; **no** create/submit enablement and **no** policy selection until DRG-573 / #850 OQ-559-* close or are deferred with owner words+time | Enable `RESTRUCTURE` create now; collapse `CONVERT` into `RESTRUCTURE` | Yes — conversion AC |
| OQ-548-7 | **Cancel / Suspend / Resume / Transfer / TrueUp create** | Remain **stored-only** on CAM; quote create stays NO-GO. Fulfilment Suspend/Resume commitment names stay on #817 — do not adopt OQ-558-* here | Enable quote create for CANCEL/SUSPEND/RESUME in this brief | Yes — lifecycle AC |
| OQ-548-8 | **Catalog eligibility coupling** | Cite [DRG-647](https://linear.app/drgamtd-workspace/issue/DRG-647) / DRG-576; intent create widen remains blocked on eligibility Decision, not invented here | Invent per-intent SKU allow-list in this brief | Soft until catalog AC |
| OQ-548-9 | **Flyway / schema numbering** | Would-be only. Quote intent-check widen uses **next free quote migration after current tip**; CAM claims **never** V1_16–V1_21. Number settled only on an authorized migration PR | Claim CAM V1_16–V1_21; ship schema before OQs close | Yes — any migration |
| OQ-548-10 | **S4 / S7 / dual-read / Jev / ramp / asset-write** | Remain **NO-GO**; this brief adds no hold flip and no S4/S7 acceptance criterion | Enable holds from this docs PR | Yes — hold |
| OQ-548-11 | **Work-queue / visibility coupling** | DRG-597 two-queue shared model stays outside this brief; intent tokens do not imply queue implementation | Design queues inside OQ-548 | Soft |
| OQ-548-IMPL | **Implementation brief authorization** | Blocked until blocking OQ-548-* (and cited 559/573/647 where named) close or are explicitly deferred with owner words+time | Authorize implementation from this file alone | Yes |

## Explicit NO-GO

Until blocking **OQ-548-*** rows close (or the owner defers them with words and a time):

- No product code, Flyway/schema, feature flags, topics, gateway routes, or deploys that widen LifecycleChange intent create/submit beyond `AMEND`.
- No CAM migration number in **V1_16–V1_21**.
- No S4 / S7 / dual-read / Jev / ramp / asset-write hold bypass.
- Do **not** accept `specs/lifecycle-intents.md` wholesale (especially `Extend` / `Expand`).
- Do **not** adopt open OQs from #850 / #817 / #816 / #809.
- Do **not** reopen Adopted E1–E7 (or L*/D*) as if this file were a second Cluster E decision authority.
- Do **not** mark Linear DRG-548 Done from this draft PR.

## Verification (this pass)

- [x] Docs-only file under `docs/renewals-amendments/prereq/`
- [x] Adopted A1–A8 restated from delegated Cluster E E1–E7 + holds; residual forks Proposed only as **OQ-548-***
- [x] Linear left **In Review** (orchestrator / agent must not mark Done)
- [x] GitNexus unavailable / UNKNOWN — not treated as clean
- [x] Secret scan: no credentials in this file

## Owner decision slots

For each **OQ-548-*** row scored: `Owner decision: ___ (drg, time)`.
