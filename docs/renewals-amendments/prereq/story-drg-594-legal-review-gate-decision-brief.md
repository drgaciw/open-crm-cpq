# DRG-594 — Legal-review-free amendments: residual Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594/legal-review-free-amendments-limited-to-pricing-term-and-seats-change) (RA-60). Call decision 2026-10-16 (**#6**).

**Status:** Proposed residual Decision brief. This file records what call **#6** already adopted for the legal-review free set, **cites** the existing Cluster A / governance-routing frames without rewriting them or treating them as the final Decision brief, and surfaces **remaining owner forks as OQx**. **It authorizes no production code path, no schema change, no Flyway migration, no new document status, no V-rule implementation, no approval-routing writer, no feature flag, no Kafka consumer registration, and no deploy.** S3 / DRG-581 stays unstarted. Dual-read, Jev, and ramp holds are untouched. [DRG-591](https://linear.app/drgamtd-workspace/issue/DRG-591) stays **parked** (this brief does not unpark it). Product code / schema / flag / state-machine work for the legal-review gate is **NO-GO** until the owner closes the residual OQx below (or explicitly defers a named fork with words and a time).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`.

**Linear status when read:** In Review (not Done). This brief leaves that status unchanged.

## Alignment (cite; do not accept frames as final)

| Artifact | Relation to this brief |
|---|---|
| Call 2026-10-16 (**#6**) on [DRG-594](https://linear.app/drgamtd-workspace/issue/DRG-594) | **Adopted** policy: skip legal review only for **pricing, term or seats** and nothing else. Cite; **do not reopen**. |
| [mid-term-amendment-policy-spec.md](../mid-term-amendment-policy-spec.md) clarification 2026-10-16 | Restates #6; explicitly leaves Add/Remove-as-seats **open** for product. |
| [cluster-a-amendment-modes-and-policy-decision-frame.md](cluster-a-amendment-modes-and-policy-decision-frame.md) §3 / [#650](https://github.com/drgaciw/open-crm-cpq/pull/650) | Historical forks **594-F1..F5** and recommended defaults. **Cite as frame; not the final Decision brief.** |
| [story-drg-560-594-governance-routing-decision-frame.md](story-drg-560-594-governance-routing-decision-frame.md) / [#666](https://github.com/drgaciw/open-crm-cpq/pull/666) | Joint DRG-560 + DRG-594 governance-routing frame (G1..G9). **Cite; do not rewrite; do not accept as final.** Legal stays the `DRAFT`→`IN_REVIEW` guard; commercial approvals are a separate row. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) Cluster A disposition rows for 594-F1..F5 | Catalog said “Adopt baseline default.” That disposition is **frame input**, not product confirmation of SaaS Add/Remove, not a landed gate, and not authorization to implement. This residual brief keeps classification / gate / audit / boundary forks **Proposed** until the owner closes them here (or with words + a time). |
| [specs/spine-dependent.md](specs/spine-dependent.md) § DRG-594 + fixtures | Proposed ACs AC-594-1..3 and 594-F1..F6. Blank `Owner decision` slots stay open. This brief does **not** accept that spec as an ADR. |
| OQ10 / OQ12 / OQ17 / OQ25 in [renewals-amendments-open-questions-register.md](../renewals-amendments-open-questions-register.md) | Signature cut; slice actions Add/Update/Remove; commercial approval thresholds **DEFAULT PROPOSED**; general approvals **parked** on DRG-591 (PL-9). |
| Related: [DRG-560](https://linear.app/drgamtd-workspace/issue/DRG-560), [DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552), [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581), [DRG-591](https://linear.app/drgamtd-workspace/issue/DRG-591) | Commercial approvals/revisions; ChangeLine action codes; S3 state machine; parked general approvals. This brief does not start them and does not unpark DRG-591. |

## Verification marks

| Mark | Meaning here |
|---|---|
| `verified by reading code` | Read in the tree at the base SHA. Not executed unless a row says so. |
| `verified by reading the issue` | Read from Linear in this pass. |
| `verified by reading docs` | Read from Accepted / adopted / Proposed docs cited in Alignment. |
| `unverified` | Not established in this pass. |

GitNexus: this checkout had no usable indexed repo (`list_repos` empty; no local impact walk). Graph impact for docs-only is **UNKNOWN**, not an all-clear. Text search confirms the schema tokens below.

## Call decision (already on the issue) — Adopted #6

`verified by reading the issue` (Linear DRG-594 description; Wave 2 lane comment 2026-11-06).

> An amendment can skip legal review only if it changes **pricing, term or seats** and nothing else. Any other change (clauses, products, special terms, governing agreement) requires legal review before execution.

**Cite; do not reopen.** This residual brief does not re-litigate the free-set sentence.

## Clarifications already on the issue (cite; do not reopen the free-set rule)

`verified by reading the issue` and `verified by reading docs`.

| Clarification | Effect on this brief |
|---|---|
| Spec / first slice (add, update, remove) define **no** legal-review gate or change classes | Gate and classifier are design work under DRG-594; not landed. |
| Adding or removing a product line is **not** in the free set under #6 unless product treats it as seats | Product confirmation is residual **OQ-594-1** (open). |
| General approvals (who / thresholds) stay parked on DRG-591 / PL-9 | This story only defines which changes can **bypass legal review**. Do **not** unpark DRG-591. |
| Scope named on the issue | Classification from ChangeLine action codes ([DRG-552](https://linear.app/drgamtd-workspace/issue/DRG-552)) + edited header fields; gate on `DRAFT`→`IN_REVIEW` + V-rule; audit of classification and overrides. |

## Adopted (do not re-open)

| ID | Contract | Source |
|---|---|---|
| **A1** | Legal-review skip is allowed only when the amendment changes **pricing, term or seats** and nothing else | Call **#6** |
| **A2** | Any other change class (clauses, products, special terms, governing agreement, …) requires legal review before execution | Call **#6** |
| **A3** | General commercial / renewals-amendments approvals stay a **separate** conversation on parked [DRG-591](https://linear.app/drgamtd-workspace/issue/DRG-591) / OQ25; this story does not define who approves or thresholds | Call clarifications; OQ25 |
| **A4** | Apply remains after `EXECUTED`; OQ6 revision rules stand (pre-execution revision returns to `DRAFT` and voids approvals/signatures; executed is immutable) | OQ2; OQ6; not reopened here |

## Existing frames (cite; Proposed / not final)

Do **not** rewrite [#666](https://github.com/drgaciw/open-crm-cpq/pull/666) or Cluster A §3. The tables below are pointers only. Recommendations in those frames are **frame defaults**, not closed owner decisions for this residual brief.

### Cluster A — 594-F1..F5 (recommended defaults in [#650](https://github.com/drgaciw/open-crm-cpq/pull/650))

`verified by reading docs`.

| Fork | Frame default (cite only) |
|---|---|
| **594-F1** Seats vs Add/Remove | Same-SKU quantity `UPDATE` is seats; `ADD` / `REMOVE` of a product line require review |
| **594-F2** Gate locus | Guard on `DRAFT`→`IN_REVIEW`; no new state |
| **594-F3** Override | Named legal role may override; until named, guard refuses; sales cannot override |
| **594-F4** Mixed edits | One edit outside the free set → whole document requires review; free header fields = price and term |
| **594-F5** Timing | Gate is a **later increment**; S1–S8 keep the current signature cut |

### #666 governance routing — G1..G9 (cite only)

`verified by reading docs`. Joint frame with [DRG-560](https://linear.app/drgamtd-workspace/issue/DRG-560). Defaults there keep legal as the `DRAFT`→`IN_REVIEW` guard (**G4**), commercial `CONTRACT_MODIFICATION` as a separate `IN_REVIEW`→`APPROVED` row when OQ17 fires (**G1/G2**), and leave the classification event type **OPEN** (**G9**). Those rows stay Proposed in that frame; this residual brief does not close them and does not implement them.

### Spine-dependent Proposed ACs (cite only)

`verified by reading docs` ([specs/spine-dependent.md](specs/spine-dependent.md)): AC-594-1 same-SKU `UPDATE` free; AC-594-2 `ADD` refused with `LEGAL_REVIEW_REQUIRED`; AC-594-3 mixed document (governing-agreement header) refused. Fixtures authorize **no** runtime behavior. **594-F6** (whether a `CONTRACT_MODIFICATION` writer is added for legal) stays Proposed there and maps to residual **OQ-594-4** / boundary with DRG-560 below.

## What the tree does today

`verified by reading code` at the base SHA.

1. **No legal-review status.** `chk_amendment_document_status` in `V1_7__contract_documents.sql`: `DRAFT`, `IN_REVIEW`, `APPROVED`, `OUT_FOR_SIGNATURE`, `REJECTED`, `EXECUTED`, `WITHDRAWN`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, `VOID`. No `LEGAL_REVIEW`.
2. **Action tokens present; no classifier.** `chk_amendment_line_action`: `ADD`, `UPDATE`, `REMOVE`, `NO_CHANGE`, `RENEW`, `UPGRADE`, `DOWNGRADE`, `SWAP`, `CONVERT_METRIC`, `SUSPEND`, `RESUME`, `TERMINATE`. No classification column or gate.
3. **`approval_ref` column exists; no legal writer.** `amendment_documents.approval_ref varchar(255)`. `ApprovalType.CONTRACT_MODIFICATION` is declared; spine-dependent text records no production writer assigning it for legal classification (`verified by reading docs`; runtime writer path `unverified` beyond that search claim).
4. **Signature cut unchanged.** OQ10 / `chk_amendment_document_signature` stand; FR-08 is not rewritten by this brief.
5. **S3 unstarted.** No landed `DRAFT`→`IN_REVIEW` legal V-rule (`unverified` as runtime; S3 / DRG-581 not authorized here).

## Remaining owner forks (OQx)

Every row below is **Proposed, unaccepted, owner decision required**. Alternatives sit beside the recommendation. Picking a row authorizes **no** code, migration, flag, state-machine change, or deploy. Product code for the legal-review gate stays **NO-GO** until they are closed (or explicitly deferred).

| ID | Question | Recommendation (Proposed) | Alternatives | Depends on / blocked by |
|---|---|---|---|---|
| **OQ-594-1** | Do **Add / Remove of SaaS product lines** count as seats (legal-free) or require legal review? | Require review for `ADD` of a SKU and `REMOVE` of a product line; same-SKU quantity `UPDATE` is seats (Cluster A 594-F1-A; matches AC-594-1/2 fixtures). | Same-SKU Add/Remove count as seats (594-F1-B); any quantity change including new SKU is free (594-F1-C). | Product confirmation on Linear body; DRG-552 action semantics. |
| **OQ-594-2** | **Classification map**: which ChangeLine `action_code`s + which **edited header fields** are legal-free vs required? | Free line set = same-SKU `UPDATE` of quantity / unit price / term fields only. Free headers = price and term only. Whole-document rule: one out-of-set line or header → review required (594-F4-A). Map non-slice actions (`SWAP`, `CONVERT_METRIC`, `TERMINATE`, …) as **required**. | Per-line skip (594-F4-B); treat governing agreement / clauses as free (594-F4-C opposite); invent a new action taxonomy instead of DRG-552 tokens. | DRG-552; OQ-594-1; mid-term header field list. |
| **OQ-594-3** | **Gate shape**: where it bites; V-rule; audit of classification and overrides | Guard on `DRAFT`→`IN_REVIEW` only; **no** new status (594-F2-A). Refusal `409 LEGAL_REVIEW_REQUIRED` when required and no override. Named legal role may override with actor recorded; until role named, refuse; sales cannot override (594-F3-B). Append a **local** CAM classification/override audit event (type name provisional — see OQ-594-5); no S6 topic. | New `LEGAL_REVIEW` state (594-F2-B); stop before `APPROVED` after entering `IN_REVIEW` (594-F2-C); no override ever / sales override (594-F3-A/C). | S3 / DRG-581 auth for V-rule delivery; DRG-580 writer; OQ6 void-on-revision. |
| **OQ-594-4** | **Boundary** vs [DRG-560](https://linear.app/drgamtd-workspace/issue/DRG-560) commercial approvals/revisions and parked [DRG-591](https://linear.app/drgamtd-workspace/issue/DRG-591) | Legal = classification guard only (**not** an `approval_requests` row). Commercial approvals stay on DRG-560 / #666 G1–G3 / OQ17. General category stays parked on DRG-591 / OQ25. Do **not** unpark DRG-591. Do **not** insert `CONTRACT_MODIFICATION` for legal (spine 594-F6-A). | Legal as a `TERMS` / `CONTRACT_MODIFICATION` approval row (#666 G4-B/C); fold legal into DRG-560; unpark DRG-591 for the legal role name. | DRG-560 Decision; #666 G-forks; OQ17; DRG-591 park. |
| **OQ-594-5** | **Classification / override audit** event contract | Local CAM hash-chain + audit-outbox event(s); names provisional (`LEGAL_CLASSIFICATION_RECORDED` / `LEGAL_REVIEW_OVERRIDE`); payload includes free/required result, triggering action codes / header fields, actor, revision. No schema-registry topic until a separate event story. Aligns with #666 **G9** remaining open on the exact type. | Store classification only in `approval_requests.reason` (contradicts OQ-594-4 default); new CAM classification table migration now; S6 domain topic now. | #666 G7/G9; D-RACE “no new S6 topic” in delegated record; DRG-580. |
| **OQ-594-6** | **First-slice timing** of the gate vs S1–S8 / S3 | Later increment; S1–S8 keep current signature cut (594-F5-B). Gate is **not** a prerequisite to start S3 unless the owner reopens with words + time. | Gate required before S3 starts (594-F5-A); soft-warn in slice 1 without hard refuse. | FR-08; DRG-581; DoD. |
| **OQ-594-IMPL** | Implementation / migration / state-machine authorization | Separate implementation story **after** OQ-594-1..6 (or deferred subset) close; additive only; never edit landed V1_7/V1_8; no new status enum value unless OQ-594-3 picks 594-F2-B (not recommended). | Piggy-back on S3 migration before forks close; alter V1_7 in place (forbidden). | Owner implementation release; S3 auth; holds in [AGENT.md](../../../AGENT.md). |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (residuals only)

### OQ-594-1 — SaaS Add / Remove vs seats

| Option | What the owner would be choosing |
|---|---|
| **A. ADD/REMOVE require review; same-SKU UPDATE is seats (Recommended, Proposed)** | Matches call #6 free-set + Cluster A 594-F1-A + AC-594-2. |
| **B. Same-SKU Add/Remove count as seats** | Different SKU still requires review. |
| **C. Any quantity change is free** | Includes new SKU; weakest reading of “seats”. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-594-2 — Classification map (lines + headers)

| Option | What the owner would be choosing |
|---|---|
| **A. Whole-document; free = same-SKU UPDATE + price/term headers (Recommended, Proposed)** | One out-of-set edit → review. Non-slice actions require review. |
| **B. Per-line skip** | Free lines proceed while others wait (diverges from document-level guard). |
| **C. Broader free headers** | Governing agreement / clauses also free (contradicts call #6 examples). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-594-3 — Gate, V-rule, override

| Option | What the owner would be choosing |
|---|---|
| **A. DRAFT→IN_REVIEW guard; refuse-until-named legal override (Recommended, Proposed)** | No `LEGAL_REVIEW` state. `409 LEGAL_REVIEW_REQUIRED`. Sales cannot override. |
| **B. New LEGAL_REVIEW state** | Expands `chk_amendment_document_status`; more S3 surface. |
| **C. Enter IN_REVIEW; stop before APPROVED** | Softens the issue-named gate locus. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-594-4 — Boundary vs DRG-560 / parked DRG-591

| Option | What the owner would be choosing |
|---|---|
| **A. Legal guard ≠ commercial approval row; DRG-591 stays parked (Recommended, Proposed)** | Matches issue clarification and #666 G4-A default. No legal `CONTRACT_MODIFICATION` writer. |
| **B. Legal as approval-routing row** | Couples legal to DRG-560 engine / tiers. |
| **C. Unpark DRG-591 for legal role + general approvals** | Out of scope for this story; forbidden by delivery holds. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-594-5 — Audit event

| Option | What the owner would be choosing |
|---|---|
| **A. Local CAM audit; type name provisional (Recommended, Proposed)** | Satisfies issue “audit of classification and overrides”; leaves G9 type selection for a later naming decision. |
| **B. reason text on approval row only** | No durable legal audit when no commercial row exists. |
| **C. S6 topic now** | Contradicts delegated “no new S6 topic” unless a later story authorizes it. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-594-6 — Timing vs first slice / S3

| Option | What the owner would be choosing |
|---|---|
| **A. Later increment (Recommended, Proposed)** | S1–S8 signature cut unchanged; S3 not blocked on this gate. |
| **B. Before S3** | Blocks S3 start on closed OQ-594-1..5 + impl. |
| **C. Soft-warn in slice 1** | No hard refuse; weakens #6. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-594-IMPL — Implementation gate

| Option | What the owner would be choosing |
|---|---|
| **A. Separate impl story after residual OQx close (Recommended, Proposed)** | Additive migration only if needed; V1_7/V1_8 immutable. |
| **B. Fold into S3 before forks close** | Couples unstarted S3 to open product forks. |
| **C. Edit V1_7** | Forbidden expand-only rule. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

## Out of scope (explicit)

- Any Java/TypeScript change, Flyway migration, feature flag, workflow, gateway route, or deploy.
- Adding a `LEGAL_REVIEW` status, classification column, or V-rule implementation.
- Starting S3 / DRG-581, authorizing S4/S6, or rewriting FR-08 / OQ10.
- Unparking [DRG-591](https://linear.app/drgamtd-workspace/issue/DRG-591) or [DRG-601](https://linear.app/drgamtd-workspace/issue/DRG-601).
- Closing or rewriting [#666](https://github.com/drgaciw/open-crm-cpq/pull/666) G1..G9 or accepting [specs/spine-dependent.md](specs/spine-dependent.md) as an ADR.
- Re-opening call **#6** (A1/A2) or OQ6 executed immutability.
- Dual-read flip, Jev enablement, ramp flag, or S4 hold changes.
- Marking Linear DRG-594 Done.

## What accepting this brief would unblock

Documentation clarity only: implementation stories may cite **OQ-594-1..6** and **OQ-594-IMPL** as the residual owner menu without re-litigating call **#6**. Accepting / merging this file **does not** open an implementation story, does not add a gate, does not change the state machine, and does not unpark DRG-591.

## References

- Linear: DRG-594, DRG-560, DRG-552, DRG-581, DRG-591, DRG-601, DRG-648.
- GitHub: [#650](https://github.com/drgaciw/open-crm-cpq/pull/650) (Cluster A frame), [#666](https://github.com/drgaciw/open-crm-cpq/pull/666) (governance-routing frame).
- Schema (read-only): `backend/services/contract-asset-management/src/main/resources/db/migration/V1_7__contract_documents.sql`.
- Docs: Cluster A §3; #666 governance frame; mid-term clarification 2026-10-16; delegated 2026-10-17 catalog; spine-dependent § DRG-594; OQ register OQ10/OQ12/OQ17/OQ25.
- Holds: [AGENT.md](../../../AGENT.md); parked DRG-591; dual-read / Jev / ramp / S4.
