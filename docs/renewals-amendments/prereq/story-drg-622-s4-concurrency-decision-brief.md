# DRG-622 — S4 concurrency test uses a mock, not Postgres: residual Decision brief

**Status:** Proposal only. Wave-2 residual Decision brief for CPQ Decision scoring. This file does **not** authorize S4 / [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) product landing ([#866](https://github.com/drgaciw/open-crm-cpq/pull/866)), does **not** enable the S4 apply endpoint or flags ([DRG-625](https://linear.app/drgamtd-workspace/issue/DRG-625) / [#868](https://github.com/drgaciw/open-crm-cpq/pull/868)), does **not** adopt open OQs from cite-only [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) / [#868](https://github.com/drgaciw/open-crm-cpq/pull/868) / [#877](https://github.com/drgaciw/open-crm-cpq/pull/877), does **not** treat Testcontainers suite presence or green Docker IT as product enablement, does **not** reopen Adopted OQ5 option A / OQ6 / OQ7 / OQ10 / OQ18, does **not** flip dual-read, Jev, ramp, NgRx, or asset-write holds, does **not** mark Linear Done, and does **not** open a Flyway migration (CAM `V1_16`–`V1_21` stay reserved for [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)). Every residual fork below is `Status: Proposed` and labeled **Proposed OQ-622-***. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Product / schema / flag NO-GO.** Merge of this docs PR authorizes **no** product landing, schema, Flyway, feature-flag flip, gateway route, dual-read flip, S4 enablement, asset-lifecycle hold release, DRG-627 retry tooling, Testcontainers suite as enablement evidence, or deploy.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / S4 document apply quality (concurrency). **Story:** [DRG-622](https://linear.app/drgamtd-workspace/issue/DRG-622/s4-concurrency-test-uses-a-mock-not-postgres). Linear read this pass: **Backlog**. This brief leaves that status unchanged.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

## Explicit S4 / DRG-625 / dual-read hold (NO-GO)

| Hold | Status in this brief |
|---|---|
| **S4 / DRG-582 enablement** | **NO-GO.** Landed flag-off orchestrator / ledger from [#685](https://github.com/drgaciw/open-crm-cpq/pull/685) is **not** Done and is **not** authorization to call apply in product. Delivery residuals live on [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) (**cite only**). |
| **DRG-625 apply endpoint / flag enablement** | **NO-GO.** Owner gate; residual forks on [#868](https://github.com/drgaciw/open-crm-cpq/pull/868) (**cite only**). Concurrency IT hygiene ≠ enablement. |
| **Dual-read / ADR-005** | **NO-GO.** `approvals.legacy-local-read-enabled` / `APPROVALS_LEGACY_LOCAL_READ` stay as held in [AGENT.md](../../../AGENT.md) and [docs/ARCHITECTURE.md](../../ARCHITECTURE.md) §17. Untouched. |
| **Asset-write / Jev / ramp / NgRx / `#749` lifecycle** | **NO-GO.** AGENT.md / ARCHITECTURE / [s4-asset-lifecycle-hold](../../runbooks/s4-asset-lifecycle-hold.md) holds untouched. |
| **CAM Flyway `V1_16`–`V1_21`** | **RESERVED** (#805–#812). This brief writes **no** migration and does not consume those slots. |
| **Testcontainers / Docker IT as product GO** | **NO-GO.** Real Postgres lock-race / HTTP-race coverage (when present) is **quality evidence only**. It does **not** flip flags, enable the apply endpoint, or authorize S4 product Done. |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, dual-read flip, Jev enablement, ramp enable, S4 apply endpoint enablement (DRG-625), asset-write / lifecycle hold bypass, DRG-627 recovery tooling, Testcontainers suite product enablement, or deploy.**

## Spec / frame authority (cite; do not reopen closed cells)

| Artifact | Role |
|---|---|
| [#685](https://github.com/drgaciw/open-crm-cpq/pull/685) (merged) | Landed S4 **flag-off** document apply orchestrator + ledger. Source of the original “concurrency test uses a mock” review note. Evidence of direction — **not** Done, **not** DRG-625 enablement. |
| [#722](https://github.com/drgaciw/open-crm-cpq/pull/722) (merged) | Persist replay evidence + refresh locked state. Landed PostgreSQL Testcontainers concurrency / OSIV races on `DocumentApplyIntegrationTest`. **Cite as direction / quality evidence — not product enablement, not Linear Done.** |
| [`docs/plans/cpq-parallel-followups-20261101.md`](../../plans/cpq-parallel-followups-20261101.md) | Readiness note: DRG-622 “already covered” by #722 IT; leave false-positive code churn untouched. **Cite; do not treat as owner close of OQ-622-*.** |
| [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) DRG-582 residual | Delivery residuals **OQ-582-*** (includes OQ-582-12 quality follow-ups 622/623/624). **Cite only. Do not adopt.** |
| [#868](https://github.com/drgaciw/open-crm-cpq/pull/868) DRG-625 residual | Enablement residuals **OQ-625-***. **Cite only. Do not adopt. Do not enable.** |
| [#877](https://github.com/drgaciw/open-crm-cpq/pull/877) DRG-623 residual | Membership / `TARGET_CONTRACT_UNSET` residuals **OQ-623-***. Peer hygiene. **Cite only. Do not adopt.** |
| [`partial-apply-owner-decision.md`](partial-apply-owner-decision.md) / register OQ5 A | **Adopted** first-slice partial-apply cut. Cite; do not reopen. |
| [`oq6-revision-model-owner-decision.md`](oq6-revision-model-owner-decision.md) (#642) | **Adopted** executed immutability. Cite; do not reopen. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) | Adopted CAM contracts. Cite; do not reopen. |

## In-flight alignment (cite only — do not adopt open OQs)

| Ticket / artifact | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) residual Decision brief | Draft [#866](https://github.com/drgaciw/open-crm-cpq/pull/866); **In Review** | Delivery forks **OQ-582-*** (incl. OQ-582-12 naming 622 as related hygiene). **Cite only. Do not adopt.** |
| [DRG-625](https://linear.app/drgamtd-workspace/issue/DRG-625) residual Decision brief | Draft [#868](https://github.com/drgaciw/open-crm-cpq/pull/868); **Backlog** | Enablement forks **OQ-625-***. **Cite only. Do not adopt. Do not enable.** |
| [DRG-623](https://linear.app/drgamtd-workspace/issue/DRG-623) residual Decision brief | Draft [#877](https://github.com/drgaciw/open-crm-cpq/pull/877); **In Review** | Membership / lookup residuals **OQ-623-***. Peer S4 hygiene. **Cite only. Do not adopt.** |
| [DRG-621](https://linear.app/drgamtd-workspace/issue/DRG-621) | Related mid-loop DIGEST_CONFLICT | Pre-enablement audit gap tracking elsewhere. Not concurrency-suite authorization. |
| [DRG-627](https://linear.app/drgamtd-workspace/issue/DRG-627) / [DRG-626](https://linear.app/drgamtd-workspace/issue/DRG-626) / [DRG-628](https://linear.app/drgamtd-workspace/issue/DRG-628) | Related | Recovery / consumer / roles. Cite; do not close here. |
| [DRG-624](https://linear.app/drgamtd-workspace/issue/DRG-624) | Related | Null `effectiveEndDate` quality follow-up. Peer hygiene; not this brief. |

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted` / `Accepted` | Already selected outside this file. Restated; **not** re-opened. |
| `Status: Proposed` | Residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |

GitNexus MCP reports **no indexed repositories** usable for this docs pass; `.gitnexus/run.cjs` impact against product symbols is **not** claimed as clean. This brief edits **no** product symbols. Code claims below are text/`rg` / `git show` evidence only. Docker-backed `*IntegrationTest` classes were **not** re-executed this pass.

## Why this residual Decision brief exists

S4 review of [#685](https://github.com/drgaciw/open-crm-cpq/pull/685) filed **DRG-622** because the apply concurrency check used an in-memory mock (`ApplyFixture` / `DocumentApplyIdempotencyTest.concurrentApplySerializesOnTheAdvisoryLock`) rather than real Postgres advisory locks. Later [#722](https://github.com/drgaciw/open-crm-cpq/pull/722) landed Testcontainers PostgreSQL lock-race and HTTP/OSIV race coverage on `DocumentApplyIntegrationTest`. Wave-2 refill assigns this **docs-only** residual brief to inventory still-open **DRG-622** forks as **Proposed OQ-622-*** for CPQ Decision — without authorizing product/schema/flag work, without adopting #866 / #868 / #877 open OQs, without treating IT presence as enablement, and without flipping dual-read or reserved Flyway slots. Linear stays **Backlog**.

## Story restatement — scope only

`verified by reading the issue` (Linear DRG-622 description; no comments this pass).

- Found in S4 review; not blocking merge of #685 except where stated.
- Source: PR #685 (head `bb5c6e92`, CPQ Decision conditional PASS), story DRG-582.
- Defect: apply concurrency test uses a mock rather than a real database.
- Desired direction (issue text): replace with a Testcontainers Postgres test.
- Related to DRG-582; quality follow-up, **not** S4 enablement.

This restatement does **not** authorize calling apply, enabling tenants, closing residual forks, or marking Done.

## Landed evidence on `main` (not new authorization)

Facts `verified by reading code` at the base SHA. They show direction already partially present. They do **not** close Proposed OQ-622-* and do **not** authorize S4 product Done, DRG-625 enablement, dual-read flip, or flag-on routing.

| Fact | Evidence |
|---|---|
| Mock / in-memory concurrency unit still present | `DocumentApplyIdempotencyTest.concurrentApplySerializesOnTheAdvisoryLock` (`@Tag("unit")`); uses `ApplyFixture` ReentrantLock stand-in for `pg_advisory_xact_lock` |
| ApplyFixture documents mock lock lifetime | `ApplyFixture` class javadoc: in-memory graph; lock held until unit txn completes |
| Real Postgres Testcontainers IT present | `DocumentApplyIntegrationTest` `@Tag("integration")` `@Testcontainers` `postgres:16-alpine` |
| Postgres lock-race IT | `concurrentIdenticalAttemptsSerializeAndReplayWithoutDuplicateEffects` — asserts `pg_locks` advisory waiters ≥ 2; RepeatedTest(5) |
| HTTP / OSIV concurrency IT | `concurrentHttpRequestsUseFreshLockedStateUnderOpenEntityManagerInView` — dual MockMvc apply under OEIV; RepeatedTest(5) |
| Additional locked-state / race coverage | `requestBoundPersistenceContextRefreshesPreloadedDocumentAndUnitsAfterAnotherCommit`; related interleaved apply paths in same class / `DocumentApplyReplayEvidenceTest` |
| Replay evidence migration (not new here) | `V1_15__document_apply_replay_evidence.sql` via #722 — already on `main`; this brief does **not** add migrations |
| CAM migrations through `V1_15`; no `V1_16`–`V1_21` files | Directory listing this pass — reserved range untouched |
| Document-path routing flag defaults empty | `opencrm.amendment.routing.document-path-tenants` (flag-off) — untouched |
| Readiness plan cites coverage | `docs/plans/cpq-parallel-followups-20261101.md` — “already covered by … #722”; Docker unavailable then so IT not rerun |

## Adopted cells this brief cites and does not reopen

| Cell | Source |
|---|---|
| OQ5 option A (first slice) | `partial-apply-owner-decision.md` / register |
| OQ6 executed immutability | `oq6-revision-model-owner-decision.md` |
| S4 enablement is a **separate owner gate** | DRG-625 / #868 |
| `#749` / `ASSET_LIFECYCLE_HOLD` authoritative | Runbook + readiness |
| CAM Flyway `V1_16`–`V1_21` reserved | #805–#812 |

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No new Adopted cells in this table.** Picking a row authorizes **no** product code, schema, flag flip, endpoint enablement, dual-read flip, Testcontainers-as-enablement, or deploy.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product / schema / flag? |
|---|---|---|---|---|
| **OQ-622-1** | **Residual scope vs original “replace mock with Postgres” title** | Keep residual scope = **concurrency quality / evidence inventory**. Affirm that real Postgres advisory-lock + HTTP race IT on `main` (via #722) is the intended direction for AC-S4 concurrency proof; mock unit may remain as fast unit | Delete all mock concurrency; redesign lock protocol; treat title as requiring product enablement | Soft for docs; **hard** if used as enablement |
| **OQ-622-2** | **#722 IT vs still-open Linear** | Treat #722 IT as **cite-only landed direction**. Owner still decides whether story DoD / Linear Done requires green Docker IT re-run + explicit close words | Auto-Done because #722 merged; ignore IT and leave forever open | Soft tracker; hard if auto-Done without DoD |
| **OQ-622-3** | **Mock unit retention** | Keep `DocumentApplyIdempotencyTest` mock concurrency as **unit-tagged** fast signal; real lock proof stays on `@Tag("integration")` Testcontainers | Delete mock concurrent test; move all concurrency into unit stage (would start containers / break Docker-free unit CI) | Soft; hard if Docker bleeds into unit stage |
| **OQ-622-4** | **Acceptance evidence bar** | DoD for this hygiene story = Docker-backed green `DocumentApplyIntegrationTest` concurrency methods (lock-race + HTTP race at minimum) recorded by owner/CI — **not** docs merge alone | Accept unit mock alone as Done; accept unreadiness plan prose alone as Done | Soft for scoring |
| **OQ-622-5** | **Relation to #866 / #868 / #877** | Cite **OQ-582-*** / **OQ-625-*** / **OQ-623-*** only. Do **not** adopt or close them. Concurrency hygiene ≠ enablement ≠ membership | Close 582/625/623 forks here; treat this brief as S4 enablement | Soft here; **hard on #866/#868/#877** — do not adopt |
| **OQ-622-6** | **Testcontainers suite ≠ product enablement** | IT presence / green runs prove lock serialization under Postgres; they do **not** flip `document-path-tenants`, enable DRG-625, or authorize product apply | Treat green IT as S4 GO; flip routing tenants because races pass | Yes — hard |
| **OQ-622-7** | **CI stage / tag contract** | Keep concurrency Postgres tests `@Tag("integration")`; unit stage must remain Docker-free (`scripts/ci/backend-unit.sh` contract) | Untag / run Testcontainers in unit job | Soft for docs; hard for CI contract |
| **OQ-622-8** | **Serialization with peer CAM apply writers** | When any follow-up test/code edits apply sources, serialize writers (no parallel with DRG-623 / DRG-627 product drafts). This brief implements **neither** | Parallel agents editing orchestrator + IT fixtures without coordination | Soft for docs; hard for code coordination |
| **OQ-622-9** | **AGENT.md / ARCHITECTURE holds** | Leave dual-read, asset-write, Jev, ramp, NgRx holds **untouched** | Flip dual-read with concurrency close; enable asset-write / `opencrm.jev.*` | Yes — explicit holds |
| **OQ-622-10** | **CAM Flyway** | Write **no** new migration here. Do **not** consume reserved **`V1_16`–`V1_21`**. Concurrency IT needs no schema | Use V1_16–V1_21; mutate ledger UNIQUE under this story | Yes — reservation |
| **OQ-622-11** | **Linear status / Done gate** | Remain **Backlog** (or owner-moved In Review) until DoD + owner close words. This brief does **not** move status; does **not** mark Done | Mark Done because #722 exists; mark Done because this docs PR exists; mark Done because readiness prose said “already covered” | Yes — tracker |
| **OQ-622-IMPL** | **Implementation authorization** | Separate owner record **after** OQ-622-1..11 (or an owner-deferred subset) close **may** authorize further concurrency IT edits — still without S4 enablement, Flyway reservation breach, dual-read flip, hold release, or treating IT as product GO. **This residual brief alone is hard NO-GO** for product/schema/flag / Testcontainers-as-enablement | Treat this brief or #722 presence as authorization; piggy-back enablement on #866 / #868 | Yes — hard NO-GO until then |

`Owner decision: ___ (drg, time)` for every **Proposed** row above.

---

## Fork detail

### OQ-622-1 — Residual scope vs original title

| Option | What the owner would be choosing |
|---|---|
| **A. Quality / evidence residual (Recommended, Proposed)** | Inventory mock vs Postgres proof; #722 IT is the intended Postgres direction. |
| **B. Delete mock and redesign lock protocol** | New product surface — out of this brief. |
| **C. Title requires product enablement** | Collapses hygiene into DRG-625 — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-622-2 — #722 IT vs still-open Linear

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #722; owner still closes DoD (Recommended, Proposed)** | Matches Backlog + readiness “covered” without auto-Done. |
| **B. Auto-Done because #722 merged** | Tracker inflation; rejected by hygiene pattern on DRG-582. |
| **C. Ignore IT forever** | Leaves known gap untracked. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-622-3 — Mock unit retention

| Option | What the owner would be choosing |
|---|---|
| **A. Keep unit mock; IT holds real lock proof (Recommended, Proposed)** | Preserves Docker-free unit stage. |
| **B. Delete mock concurrent test** | Loses fast signal; optional later. |
| **C. Move Postgres concurrency into unit stage** | Breaks Docker-free unit CI — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-622-4 — Acceptance evidence bar

| Option | What the owner would be choosing |
|---|---|
| **A. Docker-backed green IT concurrency methods (Recommended, Proposed)** | Lock-race + HTTP race minimum; owner/CI record. |
| **B. Unit mock alone** | Does not meet original issue intent. |
| **C. Docs / readiness prose alone** | Insufficient DoD. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-622-5 — Relation to #866 / #868 / #877

| Option | What the owner would be choosing |
|---|---|
| **A. Cite only; do not adopt (Recommended, Proposed)** | Delivery / enablement / membership stay on their briefs. |
| **B. Close OQ-582-* / OQ-625-* / OQ-623-* here** | Out of scope. |
| **C. Treat this brief as enablement** | Collapses hygiene into DRG-625 — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-622-6 — Testcontainers ≠ product enablement

| Option | What the owner would be choosing |
|---|---|
| **A. IT = quality only (Recommended, Proposed)** | No flag flip, no endpoint enablement. |
| **B. Green IT = S4 GO** | Explicit enablement — **NO-GO** here. |
| **C. Flip routing tenants because races pass** | Flag flip — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-622-7 — CI tag contract

| Option | What the owner would be choosing |
|---|---|
| **A. Keep `@Tag("integration")` (Recommended, Proposed)** | Honors backend-unit Docker-free contract. |
| **B. Untag / run containers in unit** | CI contract breach. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-622-8 — Serialization with peer writers

| Option | What the owner would be choosing |
|---|---|
| **A. Cite peers; serial writers when code starts (Recommended, Proposed)** | Matches wave-2 CAM apply hygiene. |
| **B. Parallel writers on apply sources** | Conflict risk. |
| **C. Implement recovery / membership here** | Out of scope — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-622-9 — Dual-read / asset-write / Jev / ramp / NgRx holds

| Option | What the owner would be choosing |
|---|---|
| **A. Leave holds untouched (Recommended, Proposed)** | AGENT.md / ARCHITECTURE §17 stand. |
| **B. Flip dual-read with concurrency close** | Explicitly held — **NO-GO**. |
| **C. Enable asset-write / Jev / ramp from apply** | Same. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-622-10 — CAM Flyway reservation

| Option | What the owner would be choosing |
|---|---|
| **A. No new migration; V1_16–V1_21 reserved (Recommended, Proposed)** | Honors #805–#812. Concurrency IT needs no schema. |
| **B. Consume V1_16–V1_21 now** | Violates reservation. |
| **C. Mutate ledger UNIQUE under this story** | Schema — **NO-GO** here. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-622-11 — Linear status / Done gate

| Option | What the owner would be choosing |
|---|---|
| **A. Stay Backlog until DoD + owner words (Recommended, Proposed)** | This brief does **not** transition Linear. |
| **B. Done because #722 merged** | Rejected by hygiene. |
| **C. Done because this docs PR exists** | Docs ≠ Done. |

**Recommended (Proposed):** A. This brief does **not** transition Linear. `Owner decision: ___ (drg, time)`.

### OQ-622-IMPL — Implementation / enablement gate

| Option | What the owner would be choosing |
|---|---|
| **A. Separate owner record after residual OQx close (Recommended, Proposed)** | Hard **NO-GO** on product/schema/flag/enablement / Testcontainers-as-GO from this brief alone. |
| **B. Merge of this brief = authorization** | Explicitly false. |
| **C. Enable dual-read / reserved Flyway / DRG-625 on merge** | Violates holds. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Relation map: #866 / #868 / #877 (cite only)

| External item | Relation to this brief | What this brief must not do |
|---|---|---|
| [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) DRG-582 residual | Delivery OQ-582-* (incl. quality follow-ups naming 622) | Adopt, close, or re-score those OQs |
| [#868](https://github.com/drgaciw/open-crm-cpq/pull/868) DRG-625 residual | Enablement OQ-625-* | Adopt open OQs; enable endpoint or flip flags |
| [#877](https://github.com/drgaciw/open-crm-cpq/pull/877) DRG-623 residual | Membership OQ-623-* | Adopt open OQs; pull membership into concurrency |
| [#722](https://github.com/drgaciw/open-crm-cpq/pull/722) | Landed Postgres concurrency IT direction | Treat as product enablement or auto-Done |
| CAM `V1_16`–`V1_21` (#805–#812) | Reserved Flyway range | Claim those versions; write migrations |
| Dual-read ADR-005 | AGENT.md / ARCHITECTURE hold | Flip `APPROVALS_LEGACY_LOCAL_READ` |

## CPQ Decision scoring tuple (stub)

Fill after scoring. Do not invent a PASS without owner close.

| Field | Value |
|---|---|
| Story | DRG-622 (S4 concurrency mock→Postgres) |
| Artifact | Decision brief (this file) |
| Verdict | _Pending CPQ Decision_ |
| Confidence | _%_ |
| Scored by | CPQ Decision · _date_ |
| Owner closes | `Owner decision: ___ (drg, time)` per OQ-622-1..OQ-622-11 and OQ-622-IMPL |

Example shape once scored (illustrative only, **not** a claim): `DRG-622 · PASS with fixes · nn% · CPQ Decision YYYY-MM-DD`.

---

## What this Decision brief does not do

- Does not authorize S4 product landing, DRG-625 apply-endpoint enablement, or any S4 flag flip.
- Does not mark any fork Adopted; does not fill any `Owner decision: ___ (drg, time)` slot.
- Does not adopt open OQs from [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) (DRG-582), [#868](https://github.com/drgaciw/open-crm-cpq/pull/868) (DRG-625), or [#877](https://github.com/drgaciw/open-crm-cpq/pull/877) (DRG-623).
- Does not reopen Adopted OQ5 A, OQ6, OQ7, OQ10, or OQ18.
- Does not treat Testcontainers suite code or green Docker IT as product enablement.
- Does not write CAM Flyway (reserved `V1_16`–`V1_21` untouched); does not alter `V1_12` / `V1_15` or legacy `applied_quote_acceptances`.
- Does not flip dual-read, Jev, ramp, NgRx, or asset-write holds.
- Does not register topics, gateway routes, or production consumers.
- Does not mark Linear DRG-622 Done (leaves **Backlog**).
- Does not merge or enable auto-merge on any PR.

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close **Proposed OQ-622-1..OQ-622-11** and **OQ-622-IMPL**. Until then, further product/schema/flag/enablement work tied to this story is **NO-GO**.
2. **DRG-625 / #868:** endpoint/flag enablement remains a separate owner gate; concurrency IT ≠ enablement.
3. **#866 / #868 / #877:** cite-only; their open OQs stay open.
4. **CAM Flyway `V1_16`–`V1_21`:** reserved (#805–#812); this brief writes none.
5. **AGENT.md / ARCHITECTURE §17 holds:** dual-read, NgRx, Jev enablement, ramp, asset-write — untouched.
6. **Docker IT re-run:** this pass did not execute `DocumentApplyIntegrationTest`; a Docker-less docs pass is not acceptance evidence for the IT suite.
7. **GitNexus:** index unavailable this pass → impact **UNKNOWN** (not clean). Docs-only; no product symbols edited.

## Sources read this pass

- Linear: DRG-622 (description, attachments empty, relations → DRG-582, comments empty, status **Backlog**).
- Cite-only drafts: [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) (DRG-582), [#868](https://github.com/drgaciw/open-crm-cpq/pull/868) (DRG-625), [#877](https://github.com/drgaciw/open-crm-cpq/pull/877) (DRG-623).
- Landed quality direction: [#722](https://github.com/drgaciw/open-crm-cpq/pull/722); `docs/plans/cpq-parallel-followups-20261101.md`.
- Code (`verified by reading code` at base SHA): `DocumentApplyIdempotencyTest.java` (mock concurrent unit); `ApplyFixture.java` (in-memory advisory stand-in); `DocumentApplyIntegrationTest.java` (Testcontainers Postgres lock-race + HTTP race); CAM migration inventory through `V1_15` (no `V1_16`–`V1_21`).
- Holds: `AGENT.md`, `docs/ARCHITECTURE.md` §17; CAM Flyway V1_16–V1_21 reservation (#805–#812); S4 enablement hold via DRG-625 / #868.
