# DRG-586 — S8 first-slice e2e acceptance suite: residual Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-586](https://linear.app/drgamtd-workspace/issue/DRG-586/s8-first-slice-end-to-end-acceptance-suite-fixtures-and-ci-wiring) (RA-52 / first-slice **S8**).

**Status:** Proposed residual Decision brief for CPQ Decision scoring. This file **cites** the first-slice requirements (AC-S8-1..AC-S8-6), technical-approach §8 test strategy, [story-s3-s4-authorization-gate.md](story-s3-s4-authorization-gate.md), the delegated CAM contracts, and Wave-2 residual drafts **[#866](https://github.com/drgaciw/open-crm-cpq/pull/866)** (DRG-582 / S4), **[#862](https://github.com/drgaciw/open-crm-cpq/pull/862)** (DRG-584 / S6), and **[#864](https://github.com/drgaciw/open-crm-cpq/pull/864)** (DRG-585 / S7) **without adopting their open OQ-\*** cells. It inventories **remaining owner forks as `OQ-586-*`**. **It authorizes no Testcontainers suite code, no CI job / path-filter wiring, no production code path, no schema change, no Flyway migration (including inventing a CAM version that collides with reserved draft slots V1_16–V1_21 on [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), no feature flag, no dual-read flip, no Jev enablement, no ramp enable, no S4 apply endpoint / document-path tenant enablement (DRG-625), no S7 header apply / asset-write hold bypass, no Kafka topic or registry registration, and no deploy.** Product / schema / flag / CI work that claims AC-S8-1..AC-S8-6 Done, turns on document-path tenants for “green e2e,” or treats this brief as authorization is **NO-GO** until the owner closes the residual OQ-586-\* forks below (or explicitly defers a named fork with words and a time). Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Linear status when read:** **Backlog** (not Done). This brief leaves that status unchanged.

## Explicit holds — product / schema / flag / CI NO-GO

Closing or merging this documentation PR does **not** lift any of the following.

| Hold | What stays forbidden | Cite |
|---|---|---|
| **S8 suite / CI wiring** | No new `*Acceptance*` / cross-service Testcontainers class, no golden fixture pack commit as “authorized,” no `pr-check` / `ci.yml` / Jenkins path-filter change from this brief. AC-S8-5 stays unscored for product Done. | Issue AC-S8-5; technical approach §8; this Wave-2 docs lane |
| **S4 enablement / DRG-625** | No apply-endpoint call, no `opencrm.amendment.routing.document-path-tenants` flip, no treating flag-off apply IT green as product Done. | [#866](https://github.com/drgaciw/open-crm-cpq/pull/866) cite-only; DRG-625 |
| **Dual-read / ADR-005** | `approvals.legacy-local-read-enabled` / `APPROVALS_LEGACY_LOCAL_READ` stay as held. | [AGENT.md](../../../AGENT.md); [docs/ARCHITECTURE.md](../../ARCHITECTURE.md) §17 |
| **S7 asset-write / header apply** | No S7 `contract_header_backfill_*` run; no `opencrm.assets.legacy-acceptance-write-enabled` (or successor); no installed-base / lineage backfill from S8 fixtures. | [#864](https://github.com/drgaciw/open-crm-cpq/pull/864) cite-only; asset-write hold |
| **CAM Flyway `V1_16`–`V1_21`** | Reserved (#805–#812). This brief writes **no** migration and does not consume those slots. | #805–#812 |
| **Jev / ramp / NgRx / registry** | No `opencrm.jev.*`; no ramp enable; no NgRx; no new `EventContractRegistry` / `KafkaTopics` / consumer registration for “e2e completeness.” | AGENT.md; [#862](https://github.com/drgaciw/open-crm-cpq/pull/862) cite-only |

**This note authorizes no code, schema migration, CI wiring, job, data rewrite, feature flag, topic, gateway route, dual-read flip, Jev enablement, ramp enable, S4 apply endpoint enablement (DRG-625), S7 apply, asset-write hold bypass, or deploy.**

## Spec / frame authority (cite; do not reopen closed cells)

| Artifact | Role |
|---|---|
| [DRG-586](https://linear.app/drgamtd-workspace/issue/DRG-586) issue body (RA-52 / S8) | Scope: Testcontainers (PostgreSQL, Kafka) e2e from LifecycleChange to APPLIED; rule-named V1..V14 failing-input tests; replay + tamper; flag-off regression of existing CAM tests; CI on PRs touching CAM or quote-service amendment code; benchmarks. AC-S8-1..AC-S8-6. Dependencies: S3–S6; OQ19. Risk: fixture drift. **Cite; inventory residuals as OQs.** |
| [`amendment-first-slice-requirements-and-acceptance.md`](../amendment-first-slice-requirements-and-acceptance.md) § S8 | Written AC-S8-1..AC-S8-6. AC-S8-6 adopted under delegation against durable delivery contracts; DTO/schema completion and **test evidence** remain required. Cite; do not reopen Adopted OQ6 delivery criteria. |
| [`amendment-technical-approach-and-dependencies.md`](../amendment-technical-approach-and-dependencies.md) §8 | Unit / DB / Kafka IT / contract / concurrency / property-replay / regression / NF / evidence naming. Strategy text only — **not** CI wiring authorization. |
| [`story-s3-s4-authorization-gate.md`](story-s3-s4-authorization-gate.md) / [#621](https://github.com/drgaciw/open-crm-cpq/pull/621) | S3–S8 remain implementation-gated; S8 depends on S3 through S6. Gate authorizes **no** suite. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) D-ACCEPTANCE | Adopts AC-S3-9..11, AC-S5-7, AC-S8-6 as **required future tests**, not executed evidence. Cite; do not reopen. |
| Register **OQ19** in [renewals-amendments-open-questions-register.md](../renewals-amendments-open-questions-register.md) | **DEFAULT PROPOSED** performance targets; blocks column **S8**; Platform; needed **before S8**. Cite; do not invent Adopted NF numbers here. |
| Adopted OQ5 A / OQ6 / OQ7 / OQ10 / OQ18 | Partial-apply cut; executed immutability + durable delivery; header-only S7; signature set; CAM-COMMERCIAL-1 digest. **Cite; do not reopen.** |

## In-flight alignment (cite only — do not adopt open OQs)

| Ticket / artifact | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582) residual Decision brief | Draft [#866](https://github.com/drgaciw/open-crm-cpq/pull/866); **In Review** | S4 enablement / DRG-625 / **OQ-582-***. **Cite only. Do not adopt.** |
| [DRG-584](https://linear.app/drgamtd-workspace/issue/DRG-584) residual Decision brief | Draft [#862](https://github.com/drgaciw/open-crm-cpq/pull/862); **In Review** | Event contract / routing flag / registry **OQ-584-***. **Cite only. Do not adopt.** |
| [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585) residual Decision brief | Draft [#864](https://github.com/drgaciw/open-crm-cpq/pull/864); **In Review** | S7 apply / asset-write **OQ-585-***. **Cite only. Do not adopt.** S7 is **not** a hard Linear `blockedBy` of DRG-586; do not invent one. |
| [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583) / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) | `Linear read`: **Done** | S5 / S3 tracker Done does **not** authorize S8 suite landing or CI. AC-S8-6 still needs cross-service evidence after residual forks close. |
| [DRG-625](https://linear.app/drgamtd-workspace/issue/DRG-625) | Backlog | Apply endpoint / flag enablement. **NO-GO** here. |
| OQ19 | Register DEFAULT PROPOSED | NF targets before S8. Residual as **OQ-586-*** below; do not Adopt here. |

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` / `verified by reading docs` | The file named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `Adopted` | Already selected outside this file. Restated; **not** re-opened. |
| `Status: Proposed` | Residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `unverified` | Not established in this pass. |

GitNexus MCP / `.gitnexus/run.cjs`: this checkout had **no** usable indexed graph runner for this pass. Graph impact for this docs-only change is **UNKNOWN**, not an all-clear. Code claims below are text/`rg` evidence only.

## Why this residual Decision brief exists

S8 is still **Backlog** (`Linear read`) and is the slice acceptance gate after S3–S6. Wave-2 refill assigns this **docs-only** residual brief to inventory still-open **DRG-586** forks as **Proposed OQ-586-*** for CPQ Decision — without wiring CI, without writing the suite, without adopting #866 / #862 / #864 open OQs, and without flipping dual-read, S4 enablement, S7 asset-write, or reserved Flyway slots.

## Story restatement (RA-52) — scope only

`verified by reading the issue` (Linear DRG-586 description) and `verified by reading docs` (requirements § S8; technical approach §8).

- Testcontainers PostgreSQL + Kafka e2e: LifecycleChange → APPLIED for a seeded SaaS contract with two subscriptions and a two-line Amend (AC-S8-1).
- Chain verifier passes after the run and fails after injected tamper (AC-S8-2).
- Whole-stream replay twice → identical ledger state (AC-S8-3).
- Each of V1..V14 has at least one failing-input test named by rule id (AC-S8-4).
- Suite runs in CI on PRs touching CAM or quote-service amendment code (AC-S8-5).
- Cross-service revision-vs-execution race + durable delivery evidence (AC-S8-6; adopted criterion; evidence still required).
- Flag-off regression of named existing CAM tests; apply latency / chain-append benchmarks; OQ19 NF targets.

This restatement does **not** authorize suite code, CI wiring, flag flips, or marking Linear Done.

## Landed evidence on `main` (not new authorization)

Facts `verified by reading code` at the base SHA. They show **partial** building blocks already present. They do **not** close Proposed OQ-586-* and do **not** authorize S8 product Done, CI path filters, dual-read flip, S4 enablement, S7 apply, or reserved Flyway slots.

| Fact | Evidence |
|---|---|
| Document apply orchestrator + internal apply route exist (flag-off / internal) | `DocumentApplyOrchestrator`; `DocumentApplyController` |
| Apply IT class exists (CAM-local; `@Tag("integration")`) | `DocumentApplyIntegrationTest` and related apply tests |
| Chain verifier + unit tamper tests exist | `AmendmentChainVerifier`; `AmendmentChainVerifierTest` |
| Event-chain IT exists | `AmendmentEventChainIntegrationTest` |
| Legacy Kafka IT + `accepted-v1` fixtures exist | `QuoteAcceptedSubscriptionKafkaIntegrationTest`; `/accepted-v1/*.json`; routing tests cite AC-S6-1b |
| Header backfill IT exists (S7 functions; not S8 e2e) | `ContractHeaderBackfillIntegrationTest` |
| CI already splits unit vs Testcontainers | `.github/workflows/ci.yml` `backend-integration`; `scripts/ci/backend-unit.sh` fails if a unit test starts Testcontainers |
| CAM migrations present through `V1_15` | Inventory under `backend/services/contract-asset-management/src/main/resources/db/migration/` |
| No CAM `V1_16`–`V1_21` files | Directory listing this pass — reserved range untouched |
| **No** dedicated S8 cross-service LifecycleChange→APPLIED acceptance suite class named in tree | `rg` for `AC-S8` / `S8Acceptance` suite types: no product suite; only docs / requirements references |

## Adopted cells this brief cites and does not reopen

| Cell | Source |
|---|---|
| OQ5 option A (first slice) | register / `#626` lineage |
| OQ6 executed immutability + durable S5/S8 delivery criteria (AC-S3-9..11, AC-S5-7, AC-S8-6 as required future tests) | delegated record; oq6 owner decision |
| OQ7 thin contract / OQ10 signature set / OQ18 `CAM-COMMERCIAL-1` | delegated record / register |
| ADR-035 placement for S1/S2 only | ADR + gate |

## Owner-decision table (remaining forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No Adopted cells in this table.** Picking a row authorizes **no** suite code, CI wiring, schema, flag, S4 enablement, dual-read flip, S7 apply, asset-write, or deploy.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product / schema / flag / CI? |
|---|---|---|---|---|
| **OQ-586-1** | **When may S8 suite implementation start?** | Only after S3–S6 **owner-authorized** surfaces needed by AC-S8-1..6 are available **and** residual cite-only forks on #866 / #862 (and any blocking S5 delivery gaps for AC-S8-6) are closed or explicitly deferred. Tracker Done on S3/S5 alone is **not** start. Stay **Backlog** until that gate. | Start suite now because S3/S5 are Done; treat #866/#862 merge as S8 GO | Yes — story start / tracker |
| **OQ-586-2** | **OQ19 performance targets** | Keep register **DEFAULT PROPOSED**. Confirm or supersede the requirements NF sentence (p95 apply under 500 ms on a 50-line subscription [I]) in a scored Platform decision **before** treating benchmarks as pass/fail gates for S8 Done. Suite may **record** timings without inventing Adopted SLOs here. | Adopt 500 ms silently; ship S8 with no NF gate; invent stricter SLOs without Platform | Yes — NF / Done claim |
| **OQ-586-3** | **Suite home / module layout** | Prefer a dedicated CAM `@Tag("integration")` acceptance package that orchestrates quote-service / Kafka / Postgres Testcontainers as required by AC-S8-1/6, without a new top-level Maven module, until owner names otherwise. No new module in this brief. | New multi-module `acceptance` artifact now; browser Playwright as S8; fold into unit stage | Soft for layout; hard if it breaks Docker-free unit split |
| **OQ-586-4** | **AC-S8-1 happy-path fixture ownership** | One versioned golden seed (SaaS contract, two subscriptions, two-line Amend) owned under CAM test resources; quote-service payloads referenced, not forked. Drift remediation is a scored fixture PR — not silent rewrite of production data. | Live-DB scrape as golden; regenerate from production nightly without owner GO | Yes if production scrape / tenant data |
| **OQ-586-5** | **AC-S8-2 verifier / tamper harness** | Reuse `AmendmentChainVerifier` (+ IT wrapper). Injected tamper must fail the same verifier the happy path passes. Do not invent a second hash chain. | Parallel verifier library; skip tamper until “later” | Soft for docs; hard for AC-S8-2 Done |
| **OQ-586-6** | **AC-S8-3 whole-stream replay scope** | Replay means re-drive the recorded Kafka / inbox / apply inputs for the fixture stream twice and assert identical ledger + chain; CAM-local apply idempotency ITs remain necessary but **not** sufficient for AC-S8-3. | Treat `DocumentApplyIdempotencyTest` alone as AC-S8-3; skip Kafka | Soft until scored; hard for Done claim |
| **OQ-586-7** | **AC-S8-4 V1..V14 named failing-input tests** | Require rule-id-named failing cases (unit and/or IT) covering all fourteen; may land incrementally with S3/S5 validation ownership but S8 Done needs the full set linked by rule id. | Partial set “good enough”; rename existing asserts without rule ids | Soft for partial landing; hard for S8 Done |
| **OQ-586-8** | **AC-S8-5 CI wiring** | **NO-GO in this brief.** Later owner-authorized change: run the tagged suite in the existing `backend-integration` (Testcontainers) path with path filters for CAM / quote-service amendment touchpoints — **not** in Docker-free `backend-unit` / `pr-check` unit-only stage. No workflow edit here. | Wire CI in this docs PR; put Testcontainers in unit stage; skip CI forever | Yes — CI / AC-S8-5 |
| **OQ-586-9** | **AC-S8-6 cross-service race / durable delivery** | Keep Adopted AC-S8-6 as required evidence beyond CAM-local AC-S3-11. Suite must assert CAM winner, executed digest, and eventual confirmed/rejected source-operation state under S5 delivery. Cite S5/OQ6 contracts; do **not** reopen them; do **not** invent a second delivery protocol. | Drop AC-S8-6; treat S3 local race as enough; adopt open S5 product forks here | Yes — Done / delivery evidence |
| **OQ-586-10** | **Flag-off regression vs document-path** | Flag-off regression of named existing CAM tests remains mandatory. Document-path e2e steps stay **off** until S4/S6 enablement forks (#866 / #862 / DRG-625) close. Do not flip `document-path-tenants` to “make e2e green.” | Enable tenants inside S8; skip flag-off regression | Yes — flag / dual-path |
| **OQ-586-11** | **Interaction with #866 / #862 / #864** | Cite **OQ-582-***, **OQ-584-***, **OQ-585-*** only. Affirm S8 does not adopt or close them. S7 apply / asset-write stay NO-GO even if fixtures need contract headers (use S1 shape / test seeds; do not run V1_8 apply). | Adopt 582/584/585 open OQs; run S7 apply for seeds; enable S4 from S8 | Soft for scoring; **hard** if adopt / enable |
| **OQ-586-12** | **Benchmarks vs acceptance** | Benchmarks (apply latency, chain append, migration timing) are **evidence** under OQ-586-2. They do not authorize production SLOs or CI fail-the-build until OQ19 is scored. | Fail CI on unadopted 500 ms; skip benchmarks | Soft until OQ19; hard if silent SLO |
| **OQ-586-13** | **AGENT.md / ARCHITECTURE holds + Flyway reservation** | Leave dual-read, asset-write, Jev, ramp, NgRx **untouched**. Write **no** migration; never consume **V1_16–V1_21**. | Flip dual-read for “realistic e2e”; use V1_16 for fixture tables | Yes — holds / reservation |
| **OQ-586-14** | **Linear / DoD gate** | Remain **Backlog** until owner authorizes suite implementation after residual forks close (or defer). Merging this brief does **not** move Linear. Done waits on AC automation + owner words — not on docs merge. | Mark In Progress/Done because this brief landed; mark Done when only CAM-local ITs are green | Yes — tracker |
| **OQ-586-IMPL** | **Implementation / CI / flag authorization** | Separate owner record **after** OQ-586-1..14 (or an owner-deferred subset) close. That record — not this brief — may authorize suite code and later CI wiring. Still must not flip dual-read / S4 enablement / S7 asset-write / reserved Flyway / Jev / ramp without their own gates. | Treat this residual brief as authorization; piggy-back CI + flag-on on merge | Yes — hard NO-GO until then |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-586-1 — S8 start gate vs S3–S6

| Option | What the owner would be choosing |
|---|---|
| **A. Wait for owner-authorized S3–S6 surfaces + deferred residuals (Recommended, Proposed)** | Matches issue dependencies and the S3–S8 gate. S3/S5 tracker Done is insufficient alone while S4/S6 remain In Review with open enablement forks. |
| **B. Start now because S3/S5 are Done** | Skips S4/S6 dependency and AC-S8-1/5/6 realism. |
| **C. Treat #866/#862/#864 merge as S8 GO** | Docs residual merge ≠ suite authorization. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-2 — OQ19 performance targets

| Option | What the owner would be choosing |
|---|---|
| **A. Keep DEFAULT PROPOSED; score before NF gates (Recommended, Proposed)** | Matches register “before S8”; requirements 500 ms stays `[I]`. |
| **B. Adopt 500 ms silently in this brief** | Invents Adopted NF without Platform. |
| **C. Ship S8 with no NF consideration** | Leaves OQ19 unmet while claiming slice Done. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-3 — Suite home

| Option | What the owner would be choosing |
|---|---|
| **A. CAM integration-tagged acceptance package (Recommended, Proposed)** | Keeps Docker tests out of unit stage; no new module from this brief. |
| **B. New top-level acceptance module now** | Extra reactor surface without owner GO. |
| **C. Browser UAT as S8** | Wrong layer for AC-S8-1..6 (Kafka/ledger/verifier). |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-4 — Happy-path fixture ownership

| Option | What the owner would be choosing |
|---|---|
| **A. Versioned CAM golden seed; no production scrape (Recommended, Proposed)** | Addresses fixture-drift risk without tenant data. |
| **B. Nightly regenerate from production** | Compliance / PII / drift without GO. **NO-GO** here. |
| **C. Ad-hoc inline builders only** | Harder AC linking; higher drift. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-5 — Verifier / tamper

| Option | What the owner would be choosing |
|---|---|
| **A. Reuse `AmendmentChainVerifier` (Recommended, Proposed)** | Matches landed S2/S3 chain tooling. |
| **B. Second verifier library** | Dual hash semantics. |
| **C. Defer tamper** | Leaves AC-S8-2 open. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-6 — Replay scope

| Option | What the owner would be choosing |
|---|---|
| **A. Full fixture stream replay (Kafka/inbox/apply) (Recommended, Proposed)** | Matches AC-S8-3 wording (“whole event stream”). |
| **B. CAM apply idempotency only** | Necessary but not sufficient. |
| **C. Skip replay** | Leaves AC-S8-3 open. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-7 — V1..V14 failing-input coverage

| Option | What the owner would be choosing |
|---|---|
| **A. Full rule-id-named set required for S8 Done (Recommended, Proposed)** | Matches AC-S8-4 and technical approach §8 unit bullet. |
| **B. Partial coverage at Done** | Weakens FR-07 evidence. |
| **C. Untagged asserts count** | Breaks Linear/result linking. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-8 — CI wiring

| Option | What the owner would be choosing |
|---|---|
| **A. No CI change in this brief; later integration-stage + path filters (Recommended, Proposed)** | Honors docs-lane NO-GO and Docker-free unit split. |
| **B. Wire workflows in this PR** | Out of scope — **NO-GO**. |
| **C. Put suite in unit/`pr-check` only** | Breaks `backend-unit` Testcontainers ban. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-9 — AC-S8-6 cross-service evidence

| Option | What the owner would be choosing |
|---|---|
| **A. Keep Adopted AC-S8-6; require cross-service evidence (Recommended, Proposed)** | Matches delegated D-ACCEPTANCE; beyond AC-S3-11. |
| **B. Drop / weaken to CAM-local only** | Needs explicit owner supersession. |
| **C. Adopt open S5 product forks here** | Out of lane — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-10 — Flag-off vs document-path

| Option | What the owner would be choosing |
|---|---|
| **A. Flag-off regression mandatory; document-path off until enablement (Recommended, Proposed)** | Aligns with #866 / #862 cite-only and technical approach regression bullet. |
| **B. Flip document-path tenants inside S8** | S4/S6 flag enablement — **NO-GO**. |
| **C. Skip flag-off regression** | Regresses legacy path guarantee. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-11 — Cite-only Wave-2 S4/S6/S7 residuals

| Option | What the owner would be choosing |
|---|---|
| **A. Cite OQ-582/584/585 only; no adopt; no S7 apply for seeds (Recommended, Proposed)** | Matches Wave-2 instruction. |
| **B. Adopt those OQs by reference** | Closes other stories’ residuals incorrectly. |
| **C. Run S7 apply / lift asset-write to seed e2e** | Hold bypass — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-12 — Benchmarks vs OQ19

| Option | What the owner would be choosing |
|---|---|
| **A. Record timings; fail-the-build only after OQ19 scored (Recommended, Proposed)** | Separates evidence from Adopted SLO. |
| **B. Enforce unadopted 500 ms in CI now** | Silent Platform decision. |
| **C. Omit benchmarks** | Leaves technical approach NF bullet unmet. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-13 — Holds and Flyway reservation

| Option | What the owner would be choosing |
|---|---|
| **A. Leave holds untouched; no V1_16–V1_21 (Recommended, Proposed)** | Matches AGENT.md / #805–#812. |
| **B. Flip dual-read for e2e realism** | Explicit hold — **NO-GO**. |
| **C. Consume reserved Flyway for fixture schema** | Reservation violation — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-14 — Linear Backlog / DoD

| Option | What the owner would be choosing |
|---|---|
| **A. Stay Backlog until owner authorizes suite work (Recommended, Proposed)** | Docs merge ≠ status change. |
| **B. Move In Progress because this brief exists** | Tracker inflation. |
| **C. Mark Done when CAM-local ITs are green** | Skips AC-S8-1/5/6. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-586-IMPL — Implementation authorization

| Option | What the owner would be choosing |
|---|---|
| **A. Separate owner act after residual OQx close; no CI/suite/flag/schema from this brief (Recommended, Proposed)** | Hard product / schema / flag / CI **NO-GO** until then. |
| **B. Treat this brief merge as authorization** | Violates Wave-2 docs lane. |
| **C. Land suite + CI + flag-on now** | Explicitly out of scope — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

## Explicit product / schema / flag / CI NO-GO (summary)

1. **No** Testcontainers S8 acceptance suite code, golden fixture pack as authorized delivery, or CI workflow / Jenkins / path-filter change from this PR.
2. **No** S4 apply endpoint / `document-path-tenants` enablement (DRG-625 / #866).
3. **No** dual-read flip, Jev enablement, ramp enable, NgRx introduction.
4. **No** S7 `contract_header_backfill_*` run or asset-write hold bypass (#864).
5. **No** CAM `V1_16`–`V1_21` consumption or in-place edit of landed migrations for fixtures.
6. **No** adopting open **OQ-582-***, **OQ-584-***, or **OQ-585-*** cells from #866 / #862 / #864.
7. **No** Linear status change; DRG-586 stays **Backlog**.

## Suggested next steps (after owner scoring — not authorized here)

1. **Owner / CPQ Decision:** score and close **Proposed OQ-586-1..OQ-586-14** (or defer named rows with words + time).
2. Close or explicitly defer blocking enablement residuals on **#866** / **#862** (and any AC-S8-6 delivery gaps) before suite start.
3. Score **OQ19** (or accept an explicit deferral) before NF fail-the-build gates.
4. Only then: separate implementation brief / PR for suite code; later separate PR for AC-S8-5 CI wiring under the Docker-aware integration stage.

## Out of scope

- Product Java/TS for the e2e suite or rule fixtures.
- CI / Jenkins / GitHub Actions edits.
- Feature flags, dual-read, S4 enablement, S7 apply, asset-write, Kafka registry/topics.
- Flyway / schema / reserved V1_16–V1_21.
- Adopting or closing Wave-2 open OQs on other stories.
- Marking [DRG-586](https://linear.app/drgamtd-workspace/issue/DRG-586) Done or leaving Backlog.

---

*End of residual Decision brief. All OQ-586-* rows remain `Status: Proposed` until `Owner decision: ___ (drg, time)`.*
