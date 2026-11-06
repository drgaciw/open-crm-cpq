# DRG-562 — Gate subscription apply on executed amendment (not quote acceptance): Decision brief (owner forks)

**Status:** Proposal only. Wave-2 residual Decision brief for CPQ Decision scoring. This file does **not** accept or reopen closed cells in [`story-drg-562-563-gate-apply-and-ledger-design-note.md`](story-drg-562-563-gate-apply-and-ledger-design-note.md) ([#668](https://github.com/drgaciw/open-crm-cpq/pull/668)), does **not** reopen Adopted OQ5 option A / OQ6 / OQ7 / OQ10 / OQ18 cells, does **not** adopt open OQs from the in-flight DRG-563 residual Decision brief, does **not** authorize S4 / [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) product landing, does **not** enable the S4 apply endpoint ([DRG-625](https://linear.app/drgamtd-workspace/issue/DRG-625)), does **not** bypass the asset-write hold, authorize product code, open a Flyway migration (CAM `V1_16`–`V1_21` stay reserved for [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), flip a flag, register a topic, enable dual-read, enable Jev, enable ramp, or deploy. Every residual fork below is `Status: Proposed` and labeled **Proposed OQ-562-***. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / S4 apply gate (RA-28). **Story:** [DRG-562](https://linear.app/drgamtd-workspace/issue/DRG-562/gate-subscription-apply-on-executed-amendment-not-quote-acceptance) (RA-28). Linear read this pass: **In Review**. Notion: [RA-28](https://app.notion.com/p/3fbf7cb4e4df81a890e4f6f496913809) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen closed cells):**

| Artifact | Role |
|---|---|
| [`story-drg-562-563-gate-apply-and-ledger-design-note.md`](story-drg-562-563-gate-apply-and-ledger-design-note.md) ([#668](https://github.com/drgaciw/open-crm-cpq/pull/668), merged) | **Cite-only design note.** Proposed defaults for executed-state gate + ledger. Status on that file remains Proposed / unaccepted / owner decision required. This residual brief inventories still-open forks as **Proposed OQ-562-***; it does **not** reopen closed already-decided cells the note cites (OQ5 A, OQ6, OQ7, OQ10, OQ18, Accepted proration, Accepted uplift D-04). |
| [`oq6-revision-model-owner-decision.md`](oq6-revision-model-owner-decision.md) (#642) | **Adopted** mid-flight / executed immutability rule. Cite for OQ-562-5; do **not** reopen. |
| [`partial-apply-owner-decision.md`](partial-apply-owner-decision.md) / register OQ5 A | **Adopted** first-slice partial-apply cut. Cite; do not reopen. |
| [`story-s3-s4-authorization-gate.md`](story-s3-s4-authorization-gate.md) | S3–S8 remain implementation-gated; S4 / DRG-582 stays owner-authorized separately. |
| [`specs/apply-authorization.md`](specs/apply-authorization.md) | Role / 401/403 proposal; **DRG-625 enablement stays an owner gate**. This brief does not enable the endpoint. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) | Adopted CAM contracts (D-DIGEST, D-REVISION, D-STORAGE, …). Cite; do not reopen. |

**In-flight alignment (cite only — do not adopt open OQs):**

| Ticket / artifact | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-563](https://linear.app/drgamtd-workspace/issue/DRG-563) residual Decision brief | In Review; Wave-2 agent target branch `cursor/drg-563-apply-ledger-decision-brief-4fa7` | Multi-subscription ledger home, per-unit rows, digest bytes, provenance token, `ONE_TIME` ledger row. **Cite only.** Do **not** adopt its open Proposed OQs. |
| [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) (S4) | In Review | Document-driven apply after `EXECUTED`. Remains **owner-gated**. This brief does **not** authorize S4 product landing, DRG-625 enablement, or flag-on routing. |
| [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) (S3) | Related | Closing `EXECUTED` / auto-execute transitions are S3. This brief does not launch S3. |
| [DRG-584](https://linear.app/drgamtd-workspace/issue/DRG-584) (S6) | Related | Event fields + per-tenant legacy gating. Cite; do not unblock. |
| [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) / draft residual #839 | Related | Partial-apply ordering / unit rules. Cite OQ5 A; do not reopen. |
| [DRG-625](https://linear.app/drgamtd-workspace/issue/DRG-625) | Related | Apply endpoint enablement. **NO-GO** here. |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, dual-read flip, Jev enablement, ramp enable, S4 apply endpoint enablement (DRG-625), asset-write hold bypass, or deploy.** CAM Flyway `V1_16`–`V1_21` remain reserved (#805–#812). An implementation brief that wires the production apply gate, flips `opencrm.amendment.routing.document-path-tenants`, or treats S4 as authorized is **blocked** until CPQ Decision scores and the owner closes the Proposed OQ-562-* forks below (or explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted` | Already selected outside this file (delegated record / OQ6 / OQ5 A / …). Restated; **not** re-opened. |
| `#668 Proposed default` | Default written in the merged design note. Still Proposed / unaccepted there. Cited for scoring; **not** adopted by this residual brief. |
| `Status: Proposed` | Residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |

## Gate restatement (RA-28)

**Today’s legacy path** (`Linear read` + `verified by reading code`): when `QuoteAccepted` arrives, `AcceptedQuoteSubscriptionService.apply` can write rate periods, end-date replaced lines, increment `commercial_revision`, and queue ERP — with **no** legal execution step between acceptance and apply.

**Target gate (this story):** subscription apply for an amendment document is allowed only after an **EXECUTED** amendment document (exactly one revision closed `EXECUTED`), **not** on bare quote acceptance. Research orientation (Salesforce Contracted / DealHub executed) is fork illustration only — not a vendor parity decision.

Closing `EXECUTED` (including any auto-execute for `CLICK_THROUGH` / `NOTICE_ONLY` / `NOT_REQUIRED`) remains an **S3** concern. Apply is a **reader** of the executed revision (#668 fork 8 default). S4 / DRG-582 remains separately owner-gated.

## Why this residual Decision brief exists

[#668](https://github.com/drgaciw/open-crm-cpq/pull/668) merged the combined DRG-562 / DRG-563 design note. Linear comments (`Linear read` 2026-10-17) corrected status to **In Review**: a merged docs frame is not the story deliverable while owner forks and product/policy remain open. Wave-2 refill (`Linear read` 2026-11-06) assigns this docs-only residual brief to inventory still-open **DRG-562** forks as **Proposed OQ-562-*** for CPQ Decision — without reopening #668 closed already-decided citations, without adopting DRG-563 open OQs, and without authorizing S4 product landing.

## What #668 already proposed (cite — do not reopen)

From `read` `story-drg-562-563-gate-apply-and-ledger-design-note.md`. Every row below remains **Proposed, unaccepted, owner decision required** on that note. This residual brief does **not** flip any of them to Adopted.

| #668 fork | Proposed default (still unaccepted) | Owned primarily by |
|---|---|---|
| 1 Executed-state gate | First write only when `amendment_documents.status = EXECUTED` and exactly one revision has `status_at_close = EXECUTED`; `QuoteAccepted` does **not** write the reserved ledger | **DRG-562** → OQ-562-1 |
| 2 Replay after status leaves `EXECUTED` | Same `(document_id, unit_no)` + same `apply_key_digest` is a no-op even when status is already `APPLIED` / `PARTIALLY_APPLIED`; other commands refused; no retry of `PARTIALLY_APPLIED` / `APPLY_BLOCKED` | **DRG-562** → OQ-562-2 |
| 5 Legacy path and flag | Apply stays flag-off; `AcceptedQuoteSubscriptionService` / `QuoteAcceptedEventConsumer` unchanged by the note | **DRG-562** (+ S6) → OQ-562-7 |
| 8 Signature / auto-execute | Apply does **not** read `signature_policy` and does **not** move a document to `EXECUTED` | **DRG-562** / S3 → OQ-562-6 |
| 9 ERP queue | Queue ERP when a subscription unit **commits** (after `EXECUTED`); replay does not re-queue | **DRG-562** → OQ-562-8 |
| 13 `commercial_revision` | Increment inside the committed unit only; stale `observed_commercial_revision` fails that unit (AC-S4-3 / V3) | **DRG-562** → OQ-562-3 |
| 15 S4 / S5 / S6 | S4 / DRG-582 stays unauthorized; S5/S6 blocked; apply flag-off | Gate — restated as NO-GO |

**Closed cells #668 cites and this brief does not reopen:** OQ5 option A; OQ6 executed immutability; OQ7 thin contract; OQ10 signature-policy set; OQ18 `CAM-COMMERCIAL-1`; Accepted proration; Accepted uplift D-04; Cluster B named-unbuilt units; ONE_TIME later scope (#654).

**DRG-563 ledger forks (#668 3, 4, 6, 7, 10, 12, 14)** stay on the adjacent residual brief. This file **cites** the coupling (OQ-562-4) and does **not** adopt those OQs.

## Landed evidence on `main` (not new authorization)

Facts `verified by reading code` at the base SHA. They show direction already partially present. They do **not** close Proposed OQ-562-* and do **not** authorize S4 product Done, DRG-625 enablement, or flag-on routing.

| Fact | Evidence |
|---|---|
| Document apply refuses non-`EXECUTED` documents | `DocumentApplyOrchestrator` preflight `NOT_EXECUTED` / message "apply is refused unless the document is EXECUTED" |
| `document_apply_ledger` table exists (unique `(document_id, unit_no)`, stored `apply_key_digest`) | `V1_12__create_document_apply_ledger.sql` (later than #668’s reserved-V1_9 wording; table shape matches the gate paragraph) |
| Legacy `applied_quote_acceptances` path still present | `AcceptedQuoteSubscriptionService` / `QuoteAcceptedEventConsumer`; `V1_6__subscription_rate_schedule.sql` |
| Document-path tenant routing flag exists and defaults empty | `AmendmentRoutingFlag.PROPERTY` = `opencrm.amendment.routing.document-path-tenants` (default empty string) |
| OQ6 post-`EXECUTED` revision refuse remains Adopted | `oq6-revision-model-owner-decision.md` rule 2; 409 `IMMUTABLE_EXECUTED` |
| Apply-authorization frame keeps DRG-625 as owner enablement gate | `specs/apply-authorization.md` |

GitNexus MCP reports **no indexed repositories** in this environment; `.gitnexus/run.cjs` is absent. Impact / `detect_changes` are therefore **UNKNOWN / unavailable** — not a clean blast-radius check. Code claims above are text/`rg` evidence only.

## Implementation brief gate (explicit) — product / schema / flag NO-GO

1. This Decision brief closes **nothing** by itself. Options are Proposed.
2. CPQ Decision must score the forks. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work, CAM Flyway (including any use of reserved `V1_16`–`V1_21`), dual-read flips, Jev flags, ramp changes, S4 hold lifts, DRG-625 apply-endpoint enablement, or asset-write hold bypass for DRG-562.
4. Even after forks close: S4 / DRG-582 still needs its **own** owner authorization record. This brief is not that record. Landed orchestrator / `V1_12` ledger rows are not S4 Done.
5. Citing DRG-563 does **not** adopt its open OQs. Citing OQ6 does **not** reopen OQ6 or start S3. Choosing the executed-state gate does **not** flip `opencrm.amendment.routing.document-path-tenants`.

---

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)** and matches the #668 proposed default where one exists. Alternatives are listed. **No Adopted cells in this table.**

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product / schema / flag? |
|---|---|---|---|---|
| **OQ-562-1** | **Trigger event** — when may CAM first write subscription state for an amendment? | Require `EXECUTED` document + one `status_at_close = EXECUTED` revision; bare `QuoteAccepted` is not the document-path write (#668 fork 1) | Apply while `APPROVED`; keep today’s bare `QuoteAccepted` as the document path | Yes — legal correctness / ERP timing / audit position |
| **OQ-562-2** | **Replay after status leaves `EXECUTED`** | Same-digest replay is a no-op from the ledger before status refusal; no retry of `PARTIALLY_APPLIED` / `APPLY_BLOCKED` (#668 fork 2) | Treat AC-S4-8 as absolute refusal after leave-`EXECUTED`; retry uncommitted units while `PARTIALLY_APPLIED` | Yes — AC-S4-2 vs AC-S4-8; couples to OQ5 A remedy |
| **OQ-562-3** | **Observed revision checks** | Per committed subscription unit: require `observed_commercial_revision` == current `commercial_revision`; increment by one only inside a successful unit write; stale observation fails that unit only (AC-S4-3 / V3; #668 fork 13) | Increment once per document; increment at `EXECUTED` before any unit commits | Yes — V3 / partial-apply coupling |
| **OQ-562-4** | **Interaction with DRG-563 multi-sub ledger** | Keep document-path identity `(document_id, unit_no)` + digest; leave ledger-home / per-sub rows / provenance / digest-bytes to DRG-563 residual (**cite only**) | Mutate `applied_quote_acceptances` UNIQUE; adopt schema-2.6 columns here | Soft here; **hard on DRG-563** — do not close 563 OQs in this file |
| **OQ-562-5** | **Mid-flight revision / OQ6 window** | Affirm Adopted OQ6: post-`EXECUTED` (including unapplied) is immutable; remedy is a new correcting LifecycleChange / document; no `:rebase` (#668 §1 + OQ6 rule 2) | Allow same-document revision while executed-but-unapplied (historical mid-flight option B — rejected by OQ6) | Yes if B — would reopen OQ6 / AC-S3-5 |
| **OQ-562-6** | **Signature / auto-execute** | Apply does not auto-execute; S3 owns `EXECUTED` transition; OQ10 set unchanged (#668 fork 8) | Apply auto-executes `CLICK_THROUGH` / `NOTICE_ONLY` / `NOT_REQUIRED`; require `OUT_FOR_SIGNATURE` before apply | Yes — S3 boundary / unattended-`EXECUTED` hold |
| **OQ-562-7** | **Legacy `QuoteAccepted` path + routing flag** | Leave legacy consumer as-is; document-path tenants list stays empty / flag-off until a later authorized S6/S4 record (#668 fork 5) | Gate consumer in this story; enable `opencrm.amendment.routing.document-path-tenants` now | Yes — flag / dual-path routing |
| **OQ-562-8** | **ERP queue timing on document path** | Queue ERP when a subscription unit commits after `EXECUTED`; same-digest replay does not re-queue (#668 fork 9) | Queue at `EXECUTED` before any unit commits | Soft until document path is authorized; hard for ERP audit position |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-562-1 — Trigger event (executed-state gate)

| Option | What the owner would be choosing |
|---|---|
| **A. EXECUTED + one executed revision (Recommended, Proposed)** | Matches #668 fork 1. `DRAFT` / `IN_REVIEW` / `APPROVED` / terminal non-executed statuses fail. Bare `QuoteAccepted` does not write document-path subscription state. |
| **B. Apply while `APPROVED`** | Writes subscription state before legal execution. |
| **C. Keep bare `QuoteAccepted` as the document path** | Continues today’s RA-28 failure mode for amendments. |

**Recommended (Proposed):** A. Does **not** authorize S4 landing or DRG-625. `Owner decision: ___ (drg, time)`.

### OQ-562-2 — Replay after status leaves `EXECUTED`

| Option | What the owner would be choosing |
|---|---|
| **A. Ledger same-digest no-op before status refusal (Recommended, Proposed)** | Matches #668 fork 2 / AC-S4-2. Different digest rejected and audited (audit shape stays on #668 fork 7 / DRG-563–adjacent surface — not closed here). No unit retry on `PARTIALLY_APPLIED` / `APPLY_BLOCKED`; remedy stays correcting amendment (OQ5 A). |
| **B. Absolute AC-S4-8 refusal after leave-`EXECUTED`** | Collides with AC-S4-2 for legitimate replays. |
| **C. Retry uncommitted units while `PARTIALLY_APPLIED`** | Second recovery path; conflicts with OQ6 / OQ5 A single-remedy reading. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-562-3 — Observed revision checks

| Option | What the owner would be choosing |
|---|---|
| **A. Per-unit observed revision + per-unit increment (Recommended, Proposed)** | Matches #668 fork 13 and AC-S4-3: unit 1 success + unit 2 stale → unit 2 `BLOCKED`, document `PARTIALLY_APPLIED`, no rollback of unit 1. Replay increments nothing. |
| **B. Increment once per document** | Couples multi-sub documents to a single revision bump — conflicts with per-subscription observed checks. |
| **C. Increment at `EXECUTED` before commits** | Moves revision before commercial write; breaks V3 observation at apply. |

**Recommended (Proposed):** A. Couples to DRG-563 unit grain without adopting 563 OQs. `Owner decision: ___ (drg, time)`.

### OQ-562-4 — Interaction with DRG-563 multi-sub ledger (cite only)

| Option | What the owner would be choosing |
|---|---|
| **A. Gate stays document/unit keyed; ledger residuals stay on DRG-563 (Recommended, Proposed)** | DRG-562 owns *when* apply may run. DRG-563 residual owns ledger home, per-subscription row shape, digest bytes, provenance `AMENDMENT_APPLIED`, `ONE_TIME` ledger row, audit-record shape for digest mismatch. Cite in-flight branch `cursor/drg-563-apply-ledger-decision-brief-4fa7` / Linear DRG-563 — **do not adopt** its open OQs here. |
| **B. Close ledger-home / UNIQUE mutation inside this brief** | Would adopt DRG-563 scope and reopen #668 forks 3–4. Out of scope. |
| **C. Require DRG-563 Done before scoring OQ-562-1** | Sequencing preference only; does not invent a second gate predicate. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-562-5 — Mid-flight revision / OQ6 window

| Option | What the owner would be choosing |
|---|---|
| **A. Affirm Adopted OQ6 cut at `EXECUTED` (Recommended, Proposed)** | Pre-execution: new document revision under same provisional number. At/after `EXECUTED` (including unapplied): refuse with 409 `IMMUTABLE_EXECUTED`; correcting LifecycleChange / new document is the remedy. No special “executed but not yet applied” revision path. |
| **B. Allow same-document revision until apply finishes** | Historical mid-flight option B in `mid-flight-revision-owner-decision.md`. Contradicts Adopted OQ6 / AC-S3-5 / A1. |

**Recommended (Proposed):** A. **Cite Adopted OQ6; do not reopen.** `Owner decision: ___ (drg, time)` only if the owner intends to supersede OQ6 (out of ordinary residual scoring).

### OQ-562-6 — Signature / auto-execute

| Option | What the owner would be choosing |
|---|---|
| **A. Apply does not auto-execute (Recommended, Proposed)** | Matches #668 fork 8. S3 owns transition to `EXECUTED`. OQ10 set (`NOT_REQUIRED` / `NOTICE_ONLY` / `CLICK_THROUGH`) unchanged. Unattended `EXECUTED` stays withheld for renewal automation. |
| **B. Apply auto-executes click-through / notice-only / not-required** | Moves an S3 transition into apply; collides with “no unattended path reaches `EXECUTED`” for renewals. |
| **C. Require `OUT_FOR_SIGNATURE` before apply** | Collides with OQ6 / D-REVISION first-slice unsupported signature envelope. |

**Recommended (Proposed):** A. Does not launch S3. `Owner decision: ___ (drg, time)`.

### OQ-562-7 — Legacy path + routing flag

| Option | What the owner would be choosing |
|---|---|
| **A. Legacy unchanged; document-path tenants empty (Recommended, Proposed)** | Matches #668 fork 5. `opencrm.amendment.routing.document-path-tenants` stays default-empty. No flag enablement in this brief. |
| **B. Gate / rewrite `QuoteAcceptedEventConsumer` in this story** | Product work; NO-GO until scored **and** S4/S6 authorized. |
| **C. Enable document-path tenants now** | Flag flip; NO-GO here. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-562-8 — ERP queue timing

| Option | What the owner would be choosing |
|---|---|
| **A. Queue on committed subscription unit after `EXECUTED` (Recommended, Proposed)** | Matches #668 fork 9. Replay does not re-queue. No external ERP call invented here. |
| **B. Queue at `EXECUTED` before any unit commits** | An `APPLY_BLOCKED` document would already have been queued. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Relation map: DRG-563 residual and S4 (cite only)

| External item | Relation to this brief | What this brief must not do |
|---|---|---|
| DRG-563 residual Decision brief (`cursor/drg-563-apply-ledger-decision-brief-4fa7`) | Ledger home, per-sub rows, digest bytes, provenance, `ONE_TIME`, audit-record shape | Adopt, close, or re-score its open OQs |
| #668 forks 3, 4, 6, 7, 10, 12, 14 | Ledger / digest / provenance surface | Reopen or adopt here |
| S4 / DRG-582 | Consumer of the executed-state gate | Authorize product landing or mark S4 Done |
| DRG-625 | Apply endpoint enablement | Enable endpoint / flip security config |
| OQ6 / RA-26 | Post-`EXECUTED` immutability for OQ-562-5 | Reopen OQ6; start S3 |
| OQ5 A / DRG-565 | Partial-apply remedy for OQ-562-2 / OQ-562-3 | Reopen OQ5 A |
| CAM `V1_16`–`V1_21` (#805–#812) | Reserved Flyway range | Claim those versions; write migrations |

---

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Story | DRG-562 (RA-28) |
| Artifact | Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ-562-1..OQ-562-8 |

Example shape once scored (illustrative only, **not** a claim): `DRG-562 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## What this Decision brief does not do

- Does not authorize an implementation brief or product code for DRG-562 / document-path apply / routing-flag enablement.
- Does not mark any fork Adopted; does not fill any `Owner decision: ___ (drg, time)` slot.
- Does not reopen closed cells cited by #668 (OQ5 A, OQ6, OQ7, OQ10, OQ18, Accepted proration, Accepted uplift D-04).
- Does not adopt in-flight DRG-563 residual open OQs.
- Does not authorize S4 / DRG-582 product landing, DRG-625 apply-endpoint enablement, or asset-write hold bypass.
- Does not write CAM Flyway (reserved `V1_16`–`V1_21` untouched); does not alter `V1_12` or legacy `applied_quote_acceptances`.
- Does not flip dual-read, Jev, ramp, or `opencrm.amendment.routing.document-path-tenants`.
- Does not launch S3 / S5 / S6.
- Does not mark Linear DRG-562 Done.
- Does not merge or enable auto-merge on any PR.

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close **Proposed OQ-562-1..OQ-562-8**. Until then, implementation brief is blocked (**NO-GO** for product Java/TS / schema / flags on the executed-apply gate).
2. **DRG-563 In Review / residual brief in flight:** ledger OQs open; cite only.
3. **S4 / DRG-582:** still owner-gated; landed orchestrator / `V1_12` are not authorization.
4. **DRG-625:** apply endpoint enablement remains a separate owner gate.
5. **S3 / DRG-581:** `EXECUTED` transition / auto-execute still S3-owned.
6. **CAM Flyway `V1_16`–`V1_21`:** reserved (#805–#812); this brief writes none.
7. **AGENT.md / ARCHITECTURE §17 holds:** dual-read, NgRx, Jev enablement, ramp, asset-write — untouched.
8. **GitNexus:** index unavailable this pass → impact **UNKNOWN** (not clean).

## Sources read this pass

- Linear: DRG-562 (description, attachments, relations, comments), DRG-563 (status + Wave-2 residual launch comment), DRG-582 (status).
- Design note: `docs/renewals-amendments/prereq/story-drg-562-563-gate-apply-and-ledger-design-note.md` (#668).
- OQ6: `oq6-revision-model-owner-decision.md`; mid-flight analysis `mid-flight-revision-owner-decision.md` (cite only).
- Gate: `story-s3-s4-authorization-gate.md`; apply auth `specs/apply-authorization.md`.
- Delegation: `delegated-renewals-amendments-decisions-2026-10-17.md` (D-DIGEST / D-REVISION citations).
- Code (`verified by reading code` at base SHA): `DocumentApplyOrchestrator.java` (`NOT_EXECUTED`); `V1_12__create_document_apply_ledger.sql`; `AmendmentRoutingFlag.java`; `AcceptedQuoteSubscriptionService` / `QuoteAcceptedEventConsumer` presence.
- Wave-2 format peers: draft residual briefs DRG-570 / DRG-552 (structure only).
- Holds: `AGENT.md`, `docs/ARCHITECTURE.md` §17.
