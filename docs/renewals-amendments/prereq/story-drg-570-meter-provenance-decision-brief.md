# DRG-570 — Meter reading provenance, dispute and frozen true-up snapshot: Decision brief (owner forks)

**Status:** Proposal only. Residual Decision brief for CPQ Decision scoring. This file does **not** accept [`specs/capacity-meters.md`](specs/capacity-meters.md), amend [cluster-c-trueup-and-meters-decision-frame.md](cluster-c-trueup-and-meters-decision-frame.md) beyond a one-line pointer, rewrite [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md), reopen Adopted Cluster C G1–G7 disposition cells, adopt any open fork from draft [PR #818](https://github.com/drgaciw/open-crm-cpq/pull/818) (DRG-569 Decision brief), authorize an implementation brief, authorize product code, flip a flag, or deploy. Every recommendation below is `Status: Proposed`. No row is Adopted. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `capacity-meters`. **Story:** [DRG-570](https://linear.app/drgamtd-workspace/issue/DRG-570/meter-reading-provenance-dispute-and-frozen-true-up-snapshot) (RA-36). Linear read this pass: **In Review**. Notion: [RA-36 backlog](https://app.notion.com/p/3fbf7cb4e4df817b97bce854c3c873ad). Sanity-check write-up: [Pricing policy sanity check](https://app.notion.com/p/3fbf7cb4e4df816bba1fe1de4b180a60).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (still not Linear Done for DRG-570):**

| Artifact | Role |
|---|---|
| [`specs/capacity-meters.md`](specs/capacity-meters.md) | Proposal. Provenance / freeze AC and DRG-641 frozen-snapshot section remain Status: Proposed with blank `Owner decision: ___ (drg, time)` slots (hash algorithm; credit-note after dispute). |
| [cluster-c-trueup-and-meters-decision-frame.md](cluster-c-trueup-and-meters-decision-frame.md) | Historical F* / **G1–G7** / H* frame. Status line cites 2026-10-17 delegation; proposal labels retained as rationale. **This brief cites Adopted G cells; it does not reopen them.** |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) §C | Disposition cells say `Adopt baseline default` for G1–G7. That record **does not** fill capacity-meters / DRG-641 owner slots for provenance fixtures and **does not** mark Linear DRG-570 Done. |
| Fixture [`specs/fixtures/capacity-meters/frozen-snapshot-dispute.proposed.json`](specs/fixtures/capacity-meters/frozen-snapshot-dispute.proposed.json) | Proposed dispute-after-freeze check (DRG-570 / DRG-641). Status Proposed; credit-note outcome slot blank. |
| Draft [PR #818](https://github.com/drgaciw/open-crm-cpq/pull/818) | DRG-569 Decision brief (OQ1–OQ11). **Cite only.** Do **not** adopt its open OQs here. Freeze couples to #818 OQ9 (dry-run) without closing either story. |
| [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md) | RA-26 / OQ6 revision rule (pre-execution new revision; post-`EXECUTED` correcting LifecycleChange). Cluster C G5 quotes that rule. This brief **cites** it; it does not reopen OQ6 or start S3. |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** An implementation brief for DRG-570 (or product work that adds `MeterReading` provenance fields, dispute UI, or freezes a true-up snapshot) is **blocked** until CPQ Decision scores and the owner closes the forks below (or explicitly defers a named fork with words and a time). Holds in [AGENT.md](../../../AGENT.md) and [ARCHITECTURE.md §17](../../ARCHITECTURE.md) stay in force.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear issue named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Status: Proposed` | Option or default only. Not adopted. Slot stays `Owner decision: ___ (drg, time)`. |
| Adopted Cluster C cell | Disposition `Adopt baseline default` for that G-fork in the 2026-10-17 delegated record. **Cited, not reopened.** Prior frame baseline text is kept for Decision scoring context only. |
| Prior frame baseline | The Cluster C / capacity-meters proposed default named for scoring. **Not** Adopted by this brief. |

## Why this Decision brief exists

RA-36: on-prem usage arrives as a licence-server log or a customer self-declaration. Both can be disputed or corrected. The meta model's `MeterReading` has no `source`, attestation, status, or snapshot identity, so a true-up cannot prove which readings it priced, and a corrected reading would silently change a presented quote. Illustrative Scenario 2 (`read` `docs/renewals-amendments/cpq-pricing-policy-sanity-check.md`) needs the nine monthly readings frozen into a snapshot (`snapshotId` + content hash) that the TrueUp change references; a correction must produce a new revision (ties to RA-26 / OQ6).

[DRG-470](https://linear.app/drgamtd-workspace/issue/DRG-470) covers idempotent ingestion only — not attestation, dispute, or freeze. [DRG-557](https://linear.app/drgamtd-workspace/issue/DRG-557) owns the meter body and TrueUp intent (H forks). [DRG-569](https://linear.app/drgamtd-workspace/issue/DRG-569) / draft [#818](https://github.com/drgaciw/open-crm-cpq/pull/818) owns pricing-policy knobs (F / OQ1–OQ11). This ticket owns provenance, dispute, and freeze (G forks).

Cluster C ([#648](https://github.com/drgaciw/open-crm-cpq/pull/648)) and the 2026-10-17 delegated disposition index adopted G1–G7 baseline defaults as design disposition. Linear comment on DRG-570 (2026-10-17): a Proposed frame merge is not Done while the policy decision or code is open. This residual brief re-lists the owner-close surface as OQx with blank `Owner decision: ___ (drg, time)` slots for CPQ Decision — without reopening the Adopted cells and without adopting #818's open policy OQs.

**This brief does not pick a commercial provenance policy.** It lists open owner forks as OQx with Proposed options for CPQ Decision to score. Prior frame baselines are cited so Decision has a concrete row to accept, replace, or defer — not so this PR adopts them.

### Related tickets (Linear read 2026-11-06)

| Ticket | Linear status | Role for this Decision brief |
|---|---|---|
| [DRG-570](https://linear.app/drgamtd-workspace/issue/DRG-570) | In Review | This story. Owner forks below. |
| [DRG-569](https://linear.app/drgamtd-workspace/issue/DRG-569) | In Review | Policy knobs. Draft #818 OQ1–OQ11 — **cite, do not adopt**. OQ9 (dry-run) couples to freeze (this OQ4). |
| [DRG-557](https://linear.app/drgamtd-workspace/issue/DRG-557) | In Review | Meter model + TrueUp intent (H forks). Reading body precedes provenance. |
| [DRG-641](https://linear.app/drgamtd-workspace/issue/DRG-641) | Done | Worked examples + Proposed frozen-snapshot fixture; blank owner slots for hash / credit-note remain. Prerequisite docs, not Linear Done for 570. |
| [DRG-470](https://linear.app/drgamtd-workspace/issue/DRG-470) | related | Idempotent `IngestUsage` only. Not attestation / dispute / freeze. Not started by this brief. |
| [DRG-667](https://linear.app/drgamtd-workspace/issue/DRG-667) | Backlog (ADR Accepted placeholder) | S-TX2 meter readings / rating. Blocked on provenance among others. No product start from this brief. |
| [DRG-587](https://linear.app/drgamtd-workspace/issue/DRG-587) | Parked | On-prem self-report entry (PL-1..PL-5). Stays parked; attester **role** stays inside PL-2. |
| [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564) | related | Commercial decision audit note. Not the meter snapshot store. |
| [DRG-551](https://linear.app/drgamtd-workspace/issue/DRG-551) | related | Platform dry-run / revert. Freeze-at-dry-run does not start it. |
| PR #648 | merged docs frame | Cluster C frame. Does not mark DRG-570 Done. |
| PR #642 / OQ6 | merged | RA-26 revision rule quoted under Cluster C G5. Does not start S3. |

## Current code / docs facts (`read`)

Facts verified against `main` SHA above. **No product meter-provenance or frozen-snapshot path exists.**

| Fact | Evidence |
|---|---|
| No `MeterReading` Java type or SQL table on `main` | `rg` over `backend/**/*.java` and `backend/**/*.sql` this pass (no matches) |
| No product `snapshotId` / true-up freeze store on `main` | Same pass; only docs/fixtures name the shape |
| CAM document vocabulary already names `TRUE_UP` intent / policy slot / cycle trigger | `AmendmentIntent`, `PolicySlot`, `InitiationTrigger`, `DocumentKind`; `V1_7__contract_documents.sql` |
| `QuoteContinuationRequest.Operation` has no `TRUE_UP` | `QuoteContinuationRequest.java` |
| OQ6 / RA-26 correction rule is on `main` (pre-`EXECUTED` → new revision with pins cleared; post-`EXECUTED` → new correcting LifecycleChange / 409 `IMMUTABLE_EXECUTED`) | `oq6-revision-model-owner-decision.md` §2; Cluster C G5 fence |
| S2 hash chain records document lifecycle, not meter rows | `AmendmentEventWriter` / `amendment_document_events`; Proposed DRG-564 note boundary |
| Adopted Cluster C G1–G7 disposition: `Adopt baseline default` | `delegated-renewals-amendments-decisions-2026-10-17.md` §C |
| Scenario 2 nine monthly readings are illustrative examples, not customer data | `cpq-pricing-policy-sanity-check.md` Scenario 2 |
| Self-report / attestation UI stays parked | `renewals-amendments-parking-lot.md` PL-1..PL-5; DRG-587 |
| ADR-008: one schema per service — reading store and document store do not join across schemas | Accepted ADR-008 |
| ADR-035 D-03 / D-05: document pins results; policies add no tables; a snapshot of readings is not an amendment-document table | ADR-035 |

GitNexus MCP / `.gitnexus/run.cjs` unavailable in this environment for a clean graph check. Code claims above are text/`rg` evidence only — treat graph impact as unresolved (`UNKNOWN`), not as a clean blast-radius check.

## Implementation brief gate (explicit) — product NO-GO

1. This Decision brief closes **nothing** by itself. Options are Proposed.
2. CPQ Decision must score the forks. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work, Flyway, dual-read flips, Jev flags, ramp changes, or S4 hold lifts for DRG-570 / meter provenance / dispute / freeze.
4. Even after forks close: no flag flip; no dual-read / Jev / FE hold lift; no merge authority in this brief; DRG-557 / DRG-569 (#818 OQs) / DRG-667 remain residual product-path blockers to disclose on any later implementation brief.
5. Choosing freeze-at-dry-run does **not** adopt #818 OQ9 and does **not** start DRG-551. Citing OQ6 / RA-26 does **not** start S3 / DRG-581. Choosing a named attester does **not** un-park PL-1..PL-5 / DRG-587. DRG-470 stays ingestion-only.

---

## Adopted Cluster C cells (cite only — do not reopen)

From `read` `delegated-renewals-amendments-decisions-2026-10-17.md` §C, disposition for each G-fork is **`Adopt baseline default`**. The historical baseline meanings (Cluster C summary) are:

| Cell | Adopted baseline meaning (historical frame summary) |
|---|---|
| G1 Reading status | Provisional, Attested, Disputed, Corrected |
| G2 Attestation | Named internal actor required; declaration is a source |
| G3 Pricing input | Attested readings in a Frozen snapshot only |
| G4 Freeze | At dry-run completion |
| G5 Correction | Quoted OQ6 / RA-26 text; new snapshot on the document that rule produces |
| G6 Snapshot home | `snapshotId` + content hash pinned on the change and on the document; reading rows not in the contract-documents module |
| G7 Dispute scope | Blocks snapshots that include the reading; does not block an unrelated renewal |

This residual brief **does not** replace, narrow, or reopen those disposition cells. The OQx table below is the **Linear / CPQ Decision owner-close surface** (blank slots, Proposed options) required before product work — the same pattern as draft #818 for F* / DRG-569.

---

## Owner-decision table (summary)

Every row is `Status: Proposed`. Prior frame baseline is the Cluster C G* proposed default for scoring. Alternatives are listed. **No Adopted cells in this brief. This brief does not pick policy.**

| ID | Fork | Maps to | Prior frame baseline (Proposed for scoring) | Alternatives | Blocks product AC? |
|---|---|---|---|---|---|
| OQ1 | `MeterReading.status` set | G1 | Provisional, Attested, Disputed, Corrected; Corrected is a new row that supersedes | Attested + Disputed only | Yes — without status, freeze / dispute AC cannot be stated |
| OQ2 | `MeterReading.source` + attestation | G2 + source field | Named internal actor attests; declaration / licence-server log are sources, not attestation | Licence-server auto-attest; any stored reading priceable | Yes — attestation gate and source enum |
| OQ3 | What TrueUp pricing may read | G3 | Attested readings inside a Frozen snapshot only; refuse Provisional/Disputed in-snapshot | Provisional prices with a warning | Yes — silent quote drift vs refuse |
| OQ4 | When snapshot freezes (`snapshotId` + hash) | G4; couples to #818 OQ9 | Freeze when dry-run completes; Open until then; `frozenAt`/`frozenBy` in hash inputs | Freeze at presentation; freeze at execution | Yes — which artifact the presented price binds |
| OQ5 | Correction after freeze → revision | G5; RA-26 / OQ6 | Use quoted OQ6 text; corrected reading → new snapshot id+hash on the document that rule produces | Re-price the same quote in place | Yes — RA-36 failure mode if B |
| OQ6 | Where snapshot id+hash live | G6 | Pins on LifecycleChange line + amendment document; reading rows in owning service (ADR-008) | Reading rows + snapshot table inside contract-documents | Yes — schema / ADR-035 placement |
| OQ7 | Dispute scope vs renewal | G7 | Dispute blocks snapshots that include the reading; unrelated renewal proceeds | Any open dispute blocks agreement renewal | Yes — renewal gate coupling |
| OQ8 | Dispute-after-freeze workflow | Fixture / capacity-meters open Q | Keep frozen reading Attested inside snapshot; record dispute beside; Proposed credit-note outcome (not applied) | Rewrite frozen reading to Disputed (would trip G3 refuse / change priced quote) | Yes — post-freeze dispute AC |

---

## Residual owner forks (detail)

### OQ1 — `MeterReading.status` set (Cluster C G1)

| Option | What the owner would be choosing |
|---|---|
| **A. Provisional, Attested, Disputed, Corrected (prior frame baseline, Proposed)** | Corrected is a **new** reading that supersedes; it does not mutate the prior row. Old row keeps original value so the old hash still resolves. |
| **B. Attested and Disputed only** | No provisional holding state and no explicit successor status. |

**Adopted Cluster C cell:** G1 `Adopt baseline default` — **cited, not reopened.**  
**Prior frame baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ2 — `MeterReading.source` and attestation (Cluster C G2)

| Option | What the owner would be choosing |
|---|---|
| **A. Named internal actor required; declaration / licence-server log are sources (prior frame baseline, Proposed)** | Store attester id, time, and method. Customer declaration is a `source`, not an attestation. |
| **B. Licence-server log auto-attested; declaration still needs A** | Monitoring connectivity is the attestation for logs. |
| **C. Any stored reading is priceable** | No attestation gate. |

**Adopted Cluster C cell:** G2 `Adopt baseline default` — **cited, not reopened.** Attester **role** (rep vs customer-ops) stays parked in PL-2 even if A is closed. This brief does not un-park PL-1..PL-5 / DRG-587.  
**Prior frame baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ3 — What TrueUp pricing may read (Cluster C G3)

| Option | What the owner would be choosing |
|---|---|
| **A. Attested readings inside a Frozen snapshot only (prior frame baseline, Proposed)** | Provisional and Disputed readings are excluded. A snapshot that contains either is refused; refusal names reading id and status. |
| **B. Provisional readings price with a warning** | A presented number can move when the reading is later attested or disputed. |

**Adopted Cluster C cell:** G3 `Adopt baseline default` — **cited, not reopened.**  
**Prior frame baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ4 — Freeze timing: `snapshotId` + content hash (Cluster C G4; relation to #818 OQ9)

| Option | What the owner would be choosing |
|---|---|
| **A. Freeze when dry-run completes (prior frame baseline, Proposed)** | Hash fixed before the customer sees the number. Presentation and execution cite it. Snapshot status Open until that dry-run, then Frozen. `frozenAt` and `frozenBy` are hash inputs. |
| **B. Freeze at presentation** | A dry-run could still be recomputed after preview. |
| **C. Freeze at execution** | The presented number is not yet bound to a hash. |

**Relation to draft #818 (DRG-569):** #818 **OQ9** (dry-run required before customer-visible) is the policy-side couple of this freeze. This brief **cites** that couple and **does not adopt** #818 OQ1–OQ11. Closing OQ4-A here does not close #818 OQ9; closing #818 OQ9 does not close this OQ4. Neither starts DRG-551.

A priceable snapshot content hash covers reading ids, values, units, window bounds, statuses, attester ids, and the policy version id from DRG-569. Cluster C proposes SHA-256 as 64 lowercase hex (same family as `AmendmentEventHasher` encoding). The fixture algorithm row remains Status: Proposed with blank owner slot — **not** closed by this brief.

**Adopted Cluster C cell:** G4 `Adopt baseline default` — **cited, not reopened.**  
**Prior frame baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ5 — Correction after freeze → new revision (Cluster C G5; RA-26 / OQ6)

The correction rule is the OQ6 / RA-26 text already on `main` (`oq6-revision-model-owner-decision.md` §2), quoted under Cluster C G5. Summary only (fence not restated):

1. **Before `EXECUTED`:** new LifecycleChange / document revision; previous closes `SUPERSEDED`; pins cleared; re-price required.
2. **At/after `EXECUTED` (any apply state):** 409 `IMMUTABLE_EXECUTED`; remedy is a **new** LifecycleChange / correcting amendment.
3. Terminals `REJECTED` / `WITHDRAWN` are not reopened by revision.

| Option | What the owner would be choosing |
|---|---|
| **A. Use the quoted OQ6 / RA-26 text (prior frame baseline, Proposed)** | A corrected reading after freeze cites a **new** `snapshotId` and content hash on whatever document that rule produces. |
| **B. Re-price the same quote in place** | The presented number changes with no new revision — the failure RA-36 names. |

**Adopted Cluster C cell:** G5 `Adopt baseline default` — **cited, not reopened.** Using A does **not** start S3 / DRG-581 and does **not** add an S6 event. OQ6's undecided CAM command / unpriced-pin representation / supersession event stay undecided.  
**Prior frame baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ6 — Where `snapshotId` + hash live (Cluster C G6)

| Option | What the owner would be choosing |
|---|---|
| **A. Pins on change line + document; reading rows in owning service (prior frame baseline, Proposed)** | LifecycleChange line stores `snapshotId` + content hash; amendment document pins those two values. Reading rows live under ADR-008 in the service that owns readings — not as document-line columns beyond id and hash. |
| **B. Reading rows + snapshot table in contract-documents** | Mixes meter evidence into the document aggregate. |

Authoritative store (quote schema vs later usage schema) is an implementation choice for the story that creates the table. This brief does not choose a Flyway version.

**Adopted Cluster C cell:** G6 `Adopt baseline default` — **cited, not reopened.**  
**Prior frame baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ7 — Dispute scope versus renewal (Cluster C G7)

| Option | What the owner would be choosing |
|---|---|
| **A. Dispute blocks only snapshots that include the reading (prior frame baseline, Proposed)** | True-up quote waits. An unrelated renewal that does not cite the snapshot proceeds under renewal policy. |
| **B. Any open dispute blocks renewal of the agreement** | Couples meter dispute to the renewal gate. Belongs with renewal policy / parked PL-5 — not added here. |

**Adopted Cluster C cell:** G7 `Adopt baseline default` — **cited, not reopened.**  
**Prior frame baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

### OQ8 — Dispute-after-freeze workflow (fixture residual; relation to G3)

Capacity-meters / DRG-641 fixture `frozen-snapshot-dispute.proposed.json` (`read`): a dispute raised after freeze leaves the snapshot unchanged (`writesSnapshot: false`, `reopensSnapshot: false`); reading `r-2026-09` stays `Attested` value 80 inside the snapshot; content hash unchanged; named outcome is a **credit note** at Status: Proposed, `applied: false`, owner slot blank.

| Option | What the owner would be choosing |
|---|---|
| **A. Record dispute beside frozen snapshot; keep in-snapshot status Attested; Proposed credit-note outcome (fixture baseline, Proposed)** | Matches the Proposed fixture. Does not rewrite the frozen hash. Credit-note application is a later commercial path, not started here. |
| **B. Rewrite the frozen reading to Disputed inside the snapshot** | Would meet G3's refusal for any re-price of that snapshot and would change the priced quote if reopened — conflicts with RA-36 freeze intent unless a new snapshot+revision path (OQ5) is used instead. |

**This is residual to the Adopted G cells**, not a reopening of G3/G7. Closing OQ8 does not accept the fixture as a runtime contract and does not authorize billing credit-note code.  
**Prior frame / fixture baseline (Proposed for Linear close):** A. **This brief does not adopt it.** `Owner decision: ___ (drg, time)`.

---

## Relation map: #818 OQs and RA-26 (cite only)

| External item | Relation to this brief | What this brief must not do |
|---|---|---|
| #818 OQ1–OQ8, OQ10–OQ11 (aggregation, window, tolerance, pack, overage, arrears, true-down, rounding, rung, storage) | Policy knobs for amounts once a frozen snapshot exists | Adopt, close, or re-score them |
| #818 OQ9 (dry-run before customer-visible) | Couples to **OQ4** freeze-at-dry-run | Adopt #818 OQ9; start DRG-551 |
| RA-26 / OQ6 revision rule (`oq6-revision-model-owner-decision.md`) | **OQ5** correction path after freeze | Reopen OQ6; start S3; invent a second post-`EXECUTED` edit path |
| DRG-569 F9 / Cluster C F9 | Same dry-run couple as #818 OQ9 | Treat F9 close as DRG-570 Done |

---

## Worked Scenario 2 check (illustrative only)

If every prior-frame G baseline and fixture OQ8-A were later Accepted with words+time, on the example nine readings: a dry-run freezes `snapshotId` + SHA-256 hex hash over attested rows; TrueUp pricing reads only that Frozen snapshot; a later correction of e.g. September's 80 opens a **new** revision (OQ6/RA-26) with a **new** snapshot id+hash; a dispute after freeze leaves the original hash and in-snapshot Attested row unchanged and records a Proposed credit-note path beside it. These are fixtures for Decision, not an adoption claim.

---

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Story | DRG-570 (RA-36) |
| Artifact | Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ1–OQ8 |

Example shape once scored (illustrative only, **not** a claim): `DRG-570 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## What this Decision brief does not do

- Does not authorize an implementation brief or product code for DRG-570 / provenance fields / dispute UI / freeze store.
- Does not mark any fork Adopted; does not fill any `Owner decision: ___ (drg, time)` slot.
- Does not reopen Adopted Cluster C G1–G7 disposition cells or amend the 2026-10-17 delegated disposition index.
- Does not adopt draft #818 OQ1–OQ11 (DRG-569).
- Does not accept `specs/capacity-meters.md` or flip the frozen-snapshot fixture from Proposed to accepted.
- Does not start DRG-557, DRG-569, DRG-470, DRG-551, DRG-564, DRG-667, or un-park DRG-587 / PL-1..PL-5.
- Does not reopen OQ6 / RA-26, start S3 / DRG-581, or authorize S6 events.
- Does not create a `MeterReading` table, add Flyway, or register gateway routes.
- Does not mark Linear DRG-570 Done.
- Does not merge or enable auto-merge on any PR.
- Does not flip dual-read, Jev, asset-write, ramp, or S4 holds.

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ1–OQ8. Until then, implementation brief is blocked (**NO-GO** for product Java/TS / schema / flags on meter provenance, dispute, or freeze).
2. **Blank DRG-641 / capacity-meters slots:** hash algorithm and credit-note owner cells remain `___ (drg, time)`.
3. **DRG-569 In Review / #818 draft:** policy OQs open; freeze couples to #818 OQ9 without adopting it.
4. **DRG-557 In Review:** meter model / TrueUp intent not Closed for product.
5. **DRG-667 Backlog:** S-TX2 placeholder; no migration numbers; blocked on provenance among others.
6. **No `MeterReading` / freeze store types** on `main`; CAM has intent vocabulary only.
7. **DRG-470:** ingestion remains separate; attestation/dispute/freeze stay here.
8. **AGENT.md holds:** dual-read, NgRx, Jev enablement beyond local, contract-asset FE, S4 — untouched.

## Sources read this pass

- Linear: DRG-570 (full description + comments + relations), DRG-569, DRG-557, DRG-641, DRG-470, DRG-667.
- Spec: `docs/renewals-amendments/prereq/specs/capacity-meters.md` (provenance AC, frozen-snapshot section, open Q5/Q9).
- Frame: `docs/renewals-amendments/prereq/cluster-c-trueup-and-meters-decision-frame.md` (DRG-570 G1–G7).
- Delegation: `docs/renewals-amendments/prereq/delegated-renewals-amendments-decisions-2026-10-17.md` §C (G1–G7 Adopt baseline default).
- RA-26 / OQ6: `docs/renewals-amendments/prereq/oq6-revision-model-owner-decision.md`.
- Sanity check Scenario 2: `docs/renewals-amendments/cpq-pricing-policy-sanity-check.md`.
- Fixture: `docs/renewals-amendments/prereq/specs/fixtures/capacity-meters/frozen-snapshot-dispute.proposed.json`.
- Pattern / cite: draft PR #818 Decision brief (`story-drg-569-capacity-true-up-decision-brief.md` on that branch).
- Code: `rg` for `MeterReading` (none); CAM intent vocabulary / `V1_7__contract_documents.sql`; `QuoteContinuationRequest.java`.
- Holds: `AGENT.md`, `docs/ARCHITECTURE.md` §17.
