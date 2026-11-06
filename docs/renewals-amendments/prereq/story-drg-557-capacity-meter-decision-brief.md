# DRG-557 — Capacity and meter readings with true-up intent: residual Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-557](https://linear.app/drgamtd-workspace/issue/DRG-557/capacity-and-meter-readings-with-true-up-intent) (RA-23). Cluster C / capacity-meters.

**Status:** Proposed residual Decision brief. This file restates what Cluster C already **Adopted** versus what remains **Proposed** for the meter / true-up-intent product path, **cites** the merged Cluster C frame without rewriting or reopening those Adopted forks, and surfaces **remaining owner forks as Proposed OQ-557-***. **It authorizes no production code path, no schema change, no Flyway migration, no meter table as landed schema, no `TRUE_UP` addition to `QuoteContinuationRequest.Operation`, no threshold job, no feature flag, no dual-read flip, no Jev enablement, no ramp flag, no asset-write hold bypass, no Kafka topic, no gateway route, and no deploy.** Product / schema / flag work for capacity meters and true-up intent is **NO-GO** until the owner closes the residual OQ-557-* forks below (or explicitly defers a named fork with words and a time).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`.

**Linear status when read:** In Review (not Done). This brief leaves that status unchanged.

## Alignment (cite; do not reopen Adopted cluster decisions)

| Artifact | Relation to this brief |
|---|---|
| [cluster-c-trueup-and-meters-decision-frame.md](cluster-c-trueup-and-meters-decision-frame.md) / [#648](https://github.com/drgaciw/open-crm-cpq/pull/648) | Historical H1–H5 (and sibling F*/G*) frame. Status line cites 2026-10-17 delegation. **Cite; do not reopen Adopted H1–H5.** |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) §C | Disposition cells say `Adopt baseline default` for **H1–H5** (and F1–F10 / G1–G7). That record adopts the **cluster policy choices**. It does **not** mark Linear DRG-557 Done, does **not** authorize product code, and does **not** invent a landed meter schema. |
| [`specs/capacity-meters.md`](specs/capacity-meters.md) | Proposal only. Proposed shapes (`CommitmentLine.metric`, `MeterReading`, …) are **not** accepted schema. Blank owner slots stay open. This brief does **not** accept that spec. |
| ADR-035 D-03 / D-06 | Accepted name list already includes `TrueUp` on the LifecycleChange root (S1/S2 scope). Action date ≠ effective date. Consumed; not amended here. |
| Related Wave-2 drafts (**cite-only**) | [#818](https://github.com/drgaciw/open-crm-cpq/pull/818) (DRG-569 capacity true-up Decision brief); in-flight / adjacent [DRG-570](https://linear.app/drgamtd-workspace/issue/DRG-570) meter provenance; [#821](https://github.com/drgaciw/open-crm-cpq/pull/821) (DRG-578 fulfilment decompose). **Do not adopt their open OQs.** |
| CAM Flyway slots | `V1_16`–`V1_21` reserved by draft CAM PRs [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) (contract lines, tenant_id, subscription_model, customer_account_id, renewal_mode, …). **No migrations in this brief.** |
| Related: [DRG-569](https://linear.app/drgamtd-workspace/issue/DRG-569), [DRG-570](https://linear.app/drgamtd-workspace/issue/DRG-570), [DRG-641](https://linear.app/drgamtd-workspace/issue/DRG-641), [DRG-667](https://linear.app/drgamtd-workspace/issue/DRG-667), [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576), [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558), [DRG-470](https://linear.app/drgamtd-workspace/issue/DRG-470) | Pricing knobs; provenance/freeze; Proposed worked examples (Done docs, not product); S-TX2 placeholder; catalogue eligibility; fulfilment; ingestion. This brief does not start them. |

## Verification marks

| Mark | Meaning here |
|---|---|
| `verified by reading code` | Read in the tree at the base SHA. Not executed unless a row says so. |
| `verified by reading the issue` | Read from Linear in this pass. |
| `verified by reading docs` | Read from Accepted / Adopted / Proposed docs cited in Alignment. |
| `unverified` | Not established in this pass. |

GitNexus: this checkout had **no indexed repositories** (`gitnexus` MCP: `No indexed repositories`; `.gitnexus/run.cjs` absent). Graph `impact` / `detect_changes` are **UNKNOWN** / unavailable — **not** a clean blast-radius check. Code claims below are text/`rg` evidence only.

## Why this residual brief exists

`verified by reading the issue` (Linear DRG-557; Wave-2 refill comment 2026-11-06).

RA-23: no capacity/meter model and no true-up intent on the running continuation path. Research (Thales peak thresholds, Snow PVU/Oracle/SAP metrics, Tacton meters) proposes capacity amendments as Add/Update with Delta semantics **plus** a TrueUp intent fed by MeterReading — design inference, not an enum to copy.

Cluster C / #648 + the 2026-10-17 delegated record already **Adopted** the H1–H5 baseline defaults. Linear reverted Done → **In Review** because a Proposed frame merge is not the story deliverable while the product path (or residual owner closes) remains open. This brief does **not** re-score H1–H5. It lists **residual product-path forks** the owner still must close before any implementation brief or schema work.

## Adopted from Cluster C (do not re-open)

`verified by reading docs` (Cluster C H1–H5; delegated §C disposition `Adopt baseline default`).

| ID | Contract (Adopted cluster baseline) | Source |
|---|---|---|
| **A-H1** | Capacity **metric is pinned on the commitment line** (`Core` / `Pvu` / `Tb` / `Session` / `Other(code)`). Catalogue narrowing is later (DRG-576); the line pin is the commercial record. | Cluster C H1-A |
| **A-H2** | **`TrueUp` stays its own intent name** as ADR-035 D-03 lists it. Amend remains a different intent. Replacing this would require a later ADR amending D-03. | Cluster C H2-A |
| **A-H3** | A **threshold only warns**; it **never blocks** and **never auto-bills**. Cycle opens the candidate; pricing waits on dry-run + frozen snapshot (sibling stories). | Cluster C H3-A |
| **A-H4** | True-up line stores **`startQty` / `deltaQty` / `endQty`**; semantics label defaults to **Delta**; Absolute allowed when the contract says the new entitlement replaces outright. | Cluster C H4-A |
| **A-H5** | A reading belongs to **one commitment line** and stores window start/end, value, and unit (measurement body only). Provenance fields are DRG-570. | Cluster C H5-A |

**Cite; do not reopen.** This residual brief does not re-litigate Peak vs Average (that is DRG-569 / F*), nor attestation / freeze (DRG-570 / G*).

## Still Proposed (not product-ready)

| Item | Why still Proposed |
|---|---|
| Linear DRG-557 Done | In Review. Frame merge ≠ Done while residual product path is open. |
| [`specs/capacity-meters.md`](specs/capacity-meters.md) shapes | Proposal. `MeterReading` / `TrueUpSnapshot` text is **not** landed schema. |
| `TRUE_UP` on `QuoteContinuationRequest.Operation` | Not present on `main` (`verified by reading code`). ADR-035 name ≠ continuation enum. |
| Meter reading store / table | No `MeterReading` Java type or SQL table on `main` (`rg` this pass). Inventing one here is **NO-GO**. |
| Threshold warning job / channel | Not implemented. A-H3 adopts warn-only semantics; delivery channel is residual **OQ-557-3**. |
| DRG-569 / #818 OQ1–OQ11 | Sibling pricing forks. **Cite-only; do not adopt.** |
| DRG-570 G* / provenance | Sibling. **Cite-only; do not adopt.** |
| #821 fulfilment OQs | Sibling Cluster F residual. **Cite-only; do not adopt.** |
| CAM `V1_16`–`V1_21` | Reserved by #805–#812. This story writes **no** migration into those slots. |

## What the tree does today

`verified by reading code` at the base SHA.

1. **Continuation operations:** `QuoteContinuationRequest.Operation` = `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION` only — no `TRUE_UP`.
2. **CAM vocabulary already names true-up:** `AmendmentIntent.TRUE_UP`, `InitiationTrigger.TRUE_UP_CYCLE` (and `THRESHOLD`), `PolicySlot.TRUE_UP`, `DocumentKind.TRUE_UP_NOTICE`, `BusinessCategory.TRUE_UP`; check constraints in `V1_7__contract_documents.sql`. Vocabulary ≠ product meter path.
3. **Change-line quantity columns exist:** `amendment_document_lines` already has `quantity_semantics`, `start_qty`, `delta_qty`, `end_qty`, and a free-string `metric varchar(64)`. That is **not** a CommitmentLine meter model and **not** a reading store.
4. **No `MeterReading` type or table** in backend Java/SQL on this SHA.
5. **Highest CAM Flyway on `main`:** through `V1_15`. Draft sibling PRs claim `V1_16`–`V1_21` — reserved; do not collide.

## Remaining owner forks (Proposed OQ-557-*)

Every row below is **Proposed, unaccepted, owner decision required**. Alternatives sit beside the recommendation. Picking a row authorizes **no** code, migration, flag, dual-read, Jev, ramp, asset-write, or deploy. Product / schema / flag work for DRG-557 stays **NO-GO** until they are closed (or explicitly deferred).

| ID | Question | Recommendation (Proposed) | Alternatives | Depends on / blocked by |
|---|---|---|---|---|
| **OQ-557-1** | **Metric model on CommitmentLine** — where the Adopted line pin (A-H1) becomes a durable commercial field, and which closed value set is enforced | Pin on the **commitment / contract line** commercial record (A-H1). Closed set `{Core, Pvu, Tb, Session, Other}` plus optional `Other(code)`. Do **not** treat free-string `amendment_document_lines.metric` as the finished model. Do **not** invent a meter table as landed schema in this brief. Catalogue allowed-metric lists wait on DRG-576. | Product-only pin copied at draft (Cluster C H1-B — **rejected by Adopted A-H1**; cite only); product lists allowed + line pins one (H1-C — waits DRG-576). | A-H1; DRG-576; future impl story after OQs close; **no** use of reserved CAM `V1_16`–`V1_21`. |
| **OQ-557-2** | **TrueUp intent shape** — LifecycleChange / CAM intent vs continuation Operation | Keep **dedicated** `TrueUp` intent (A-H2 / ADR-035 D-03). Do **not** reopen “Amend + policy slot only.” Adding `TRUE_UP` to `QuoteContinuationRequest.Operation` is a **later code story** after this Decision, not authorized here. | Fold into Amend + policy slot (would amend ADR-035 — out of scope); add continuation `TRUE_UP` in the same slice as the meter table (couples schema to open forks). | A-H2; ADR-035; DRG-552 / lifecycle-intents (Proposed). |
| **OQ-557-3** | **Threshold initiation** — warn channel given A-H3 (never block, never auto-bill) | Threshold crossing records a **non-blocking warning** (audit / ops signal). Only a **person** opening a true-up **cycle** candidate may start a document; price still waits on DRG-569 dry-run + DRG-570 frozen snapshot. No auto-draft, no auto-charge. | Threshold opens a draft (Cluster C H3-B); cycle **or** threshold each open a draft (H3-C); threshold blocks renewal/fulfilment (H3-D — **rejected by A-H3**); threshold auto-bills (H3-E — **rejected by A-H3**). | A-H3; cite-only #818 dry-run / DRG-570 freeze; do not start DRG-551. |
| **OQ-557-4** | **Add/Update + Delta vs dedicated TrueUp intent** for capacity changes | **Dedicated TrueUp** for meter-driven true-up (A-H2). Ordinary mid-term capacity seat changes that are **not** true-ups continue to use existing Amend / Add / Update paths with Delta/Absolute line semantics (A-H4 quantities). Research “Add/Update + Delta” is evidence for quantity display, **not** a second true-up intent. | Every capacity change is TrueUp; TrueUp is only Amend+Delta with a policy slot (reopens A-H2); invent a third operation. | A-H2; A-H4; DRG-552 action codes; Cluster A amendment modes (cite only). |
| **OQ-557-5** | **Interaction with DRG-569 / #818** (capacity true-up policy) | **Boundary:** DRG-557 owns metric, reading **body**, intent name, initiation shape. DRG-569 owns aggregation, window, tolerance, pack, true-down, overage, arrears, rounding, dry-run, rung order, policy storage. **Cite #818 OQ1–OQ11 only; do not adopt or close them here.** | Merge 557+569 into one implementation brief before either Decision closes (forbidden by this Wave-2 residual). | #818 (cite-only); A-H*; Cluster C F* Adopted baselines remain sibling authority for pricing knobs. |
| **OQ-557-6** | **Interaction with DRG-570** (provenance / dispute / freeze) | **Boundary:** DRG-557 owns per-line reading body (A-H5: window, value, unit). DRG-570 owns status, attestation, freeze-at-dry-run, correction via OQ6, snapshot id/hash pins. **Cite DRG-570 / G* only; do not adopt open provenance OQs here.** No reading store migration in this brief. | Bake provenance columns into the first meter table before DRG-570 Decision (couples stories); put reading rows in contract-documents (Cluster C G6-B — rejected by sibling baseline). | DRG-570 In Review; A-H5; ADR-008 schema boundary. |
| **OQ-557-IMPL** | Implementation / migration / flag authorization | Separate implementation story **after** OQ-557-1..6 (or deferred subset) close. Additive only. **Never** edit landed CAM `V1_7`/`V1_8`/… in place. **Do not** consume reserved `V1_16`–`V1_21` (#805–#812). Next free CAM version **after** those reservations clear, chosen at build time. No flag, dual-read, Jev, ramp, or asset-write hold lift in that story without a separate owner release. | Piggy-back a `meter_readings` table onto #805–#812; invent landed schema from `specs/capacity-meters.md` before Decision; flip any hold from this brief. | Owner implementation release; CAM slot map; AGENT.md holds. |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (residuals only)

### OQ-557-1 — Metric model on CommitmentLine

| Option | What the owner would be choosing |
|---|---|
| **A. Line pin + closed metric set (Recommended, Proposed)** | Durable pin on the commitment/contract line; `{Core, Pvu, Tb, Session, Other(+code)}`. Matches Adopted A-H1. Schema shape and Flyway version are **later** and must avoid `V1_16`–`V1_21`. |
| **B. Reuse `amendment_document_lines.metric` as-is** | Free string already on change lines. Insufficient as the commercial commitment pin; mixes document line with commitment meter. |
| **C. Invent `meter_readings` + metric columns now** | **NO-GO** in this brief. Spec shapes are Proposed, not landed. |

**Recommended (Proposed):** A. **This brief does not adopt a schema.** `Owner decision: ___ (drg, time)`.

### OQ-557-2 — TrueUp intent shape

| Option | What the owner would be choosing |
|---|---|
| **A. Dedicated TrueUp intent (Recommended, Proposed)** | Keep A-H2 / ADR-035 D-03. Continuation `Operation.TRUE_UP` is a later additive code story after Decision, not authorized here. |
| **B. Amend + true-up policy slot only** | Reopens Adopted A-H2; needs ADR amending D-03. |
| **C. Ship continuation enum + meter table in one slice before OQs close** | Couples open forks to schema; **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-557-3 — Threshold initiation

| Option | What the owner would be choosing |
|---|---|
| **A. Warn-only; cycle opens candidate (Recommended, Proposed)** | Matches Adopted A-H3. Warning never blocks quote/renewal/fulfilment and never auto-bills. |
| **B/C. Threshold (or cycle∨threshold) opens a draft** | More than warn; still must not block or auto-bill; price still waits on 569/570. |
| **D/E. Block or auto-bill** | Explicitly rejected by Adopted A-H3 — listed only so the fork stays visible. |

**Recommended (Proposed):** A. Delivery channel for the warning (audit event vs ops queue vs UI badge) is part of the later implementation brief after this Decision — not designed here. `Owner decision: ___ (drg, time)`.

### OQ-557-4 — Add/Update+Delta vs dedicated intent

| Option | What the owner would be choosing |
|---|---|
| **A. Dedicated TrueUp for meter-driven true-up; Amend/Add/Update elsewhere (Recommended, Proposed)** | Matches A-H2 + A-H4. Thales Add vs Overwrite informs Delta vs Absolute labels, not the intent enum. |
| **B. All capacity changes are TrueUp** | Overloads the intent; conflates ordinary expansions with meter true-up. |
| **C. No TrueUp intent; Amend+Delta only** | Reopens A-H2. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-557-5 / OQ-557-6 — Sibling boundaries

Cite [#818](https://github.com/drgaciw/open-crm-cpq/pull/818) (DRG-569) and [DRG-570](https://linear.app/drgamtd-workspace/issue/DRG-570) only. **Do not copy, close, or contradict their open OQs in this file.** Fulfilment remains on [#821](https://github.com/drgaciw/open-crm-cpq/pull/821) / DRG-558 — cite-only; no asset-write hold bypass.

**Recommended (Proposed):** keep the boundaries in the summary table. `Owner decision: ___ (drg, time)`.

### OQ-557-IMPL — Implementation authorization

| Option | What the owner would be choosing |
|---|---|
| **A. Separate impl story after residual OQs close (Recommended, Proposed)** | Additive migrations only; never edit landed versions; skip reserved `V1_16`–`V1_21`. |
| **B. Fold meter schema into #805–#812** | Slot collision; out of this story’s allowlist. |
| **C. Treat `specs/capacity-meters.md` as landed schema** | Forbidden — Status Proposal only. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Explicit NO-GO (until OQ-557-* close)

Until the owner fills `Owner decision: ___ (drg, time)` on OQ-557-1..6 (or explicitly defers named rows the same way):

1. **No product Java/TypeScript** for CommitmentLine metric enforcement, MeterReading persistence, threshold jobs, or TrueUp continuation wiring.
2. **No schema / Flyway** — especially no meter table inventiveness as landed schema; no writes into CAM **`V1_16`–`V1_21`** (#805–#812 reserved).
3. **No feature flags**, dual-read flip, Jev enablement, ramp enable, or **asset-write hold** bypass.
4. **No adoption** of #818 / DRG-570 / #821 open OQs by reference as if closed here.
5. **No Linear Done** on DRG-557 from this documentation PR alone.

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Options are Proposed.
2. CPQ Decision must score the forks. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work for DRG-557 / capacity meters / true-up intent.
4. Even after forks close: no flag flip; no dual-read / Jev / FE / asset-write hold lift; no merge authority in this brief; DRG-569 / DRG-570 / DRG-667 / fulfilment remain residual product-path blockers to disclose on any later implementation brief.

---

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Story | DRG-557 (RA-23) |
| Artifact | Residual Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ-557-1..6 and OQ-557-IMPL |

Example shape once scored (illustrative only, **not** a claim): `DRG-557 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## Out of scope (explicit)

- Any Java/TypeScript change, Flyway migration, feature flag, workflow, gateway route, or deploy.
- Reopening Cluster C Adopted H1–H5 (or F*/G* pricing/provenance baselines).
- Adopting or closing [#818](https://github.com/drgaciw/open-crm-cpq/pull/818) OQ1–OQ11, DRG-570 provenance OQs, or [#821](https://github.com/drgaciw/open-crm-cpq/pull/821) fulfilment OQs.
- Starting DRG-470 ingestion, DRG-576 catalogue eligibility, DRG-558 fulfilment, S3 / DRG-581, or S6 / DRG-584.
- Un-parking PL-1..PL-5 / DRG-587 self-report.
- Marking Linear DRG-557 Done.
- Consuming CAM Flyway `V1_16`–`V1_21`.

## What accepting this brief would unblock

Documentation clarity only: implementation stories may cite **OQ-557-1..6** and **OQ-557-IMPL** as the residual owner menu without re-litigating Adopted A-H1..A-H5. Accepting / merging this file **does not** open an implementation story, does not add a meter table, does not extend `QuoteContinuationRequest`, and does not lift any hold.

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ-557-1..6 (+ IMPL). Until then, implementation brief is blocked (**NO-GO** for product Java/TS / schema / flags on capacity meters + true-up intent).
2. **DRG-569 In Review / #818:** pricing knobs still open (cite-only).
3. **DRG-570 In Review:** provenance / freeze still open (cite-only).
4. **DRG-667 Backlog:** S-TX2 placeholder; blocked on meter/policy closes among others.
5. **No MeterReading type** on `main`; CAM has intent vocabulary and change-line quantity columns only.
6. **CAM `V1_16`–`V1_21` reserved** (#805–#812) — next free slot after those land or are renumbered.
7. **AGENT.md holds:** dual-read, NgRx, Jev enablement beyond local, contract-asset FE / asset-write — untouched.
8. **GitNexus:** impact/detect_changes **UNKNOWN** this environment — not a clean check.

## Sources read this pass

- Linear: DRG-557 (description, comments, relations), DRG-569, DRG-570, DRG-641, DRG-667.
- Frame: `docs/renewals-amendments/prereq/cluster-c-trueup-and-meters-decision-frame.md` (H1–H5; F*/G* cited only).
- Delegation: `docs/renewals-amendments/prereq/delegated-renewals-amendments-decisions-2026-10-17.md` §C.
- Spec: `docs/renewals-amendments/prereq/specs/capacity-meters.md` (Proposal).
- Pattern: Wave-2 residual briefs on `cursor/drg-569-capacity-true-up-decision-brief-4fa7`, `cursor/drg-594-legal-review-gate-decision-brief-4fa7`.
- Related drafts (cite-only): GitHub #818, #821; CAM slot PRs #805–#812.
- Code: `QuoteContinuationRequest.java`; CAM `AmendmentIntent` / `InitiationTrigger` / `PolicySlot` / `DocumentKind` / `BusinessCategory`; `AmendmentDocumentLine` quantity + metric columns; `V1_7__contract_documents.sql`; `rg` for `MeterReading` (none).
- Holds: `AGENT.md`, `docs/ARCHITECTURE.md` §17.
