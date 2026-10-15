# 00 — External reuse harvest index (agentic backlog)

**Architect verdict: APPROVED-WITH-GATES** — 2026-09-14  
**Authorized now:** Queue **C docs only** (C0 → C1 → C2) **∥** Queue **E docs only** (E0 → E1 → E2).  
**Not authorized:** C3 / E3 code, Queue D, Queue F, Queue B (including B0 ADR draft).  
**Queue A:** **CLOSED** on `origin/main` `a4c5e80` — G0 #505, T1-D1 #506, T1-1 #507, T1-2 #508, T1-3 #509, T1-4 #510, T1-5 #511 (Drools **10.2.0**).

**Purpose:** Single entry point for agents processing reuse / gap-analysis harvest from external repos into `open-crm-cpq`.  
**Location:** `docs/plans/00-harvest-index.md`  
**Steer from:** [docs/ARCHITECTURE.md](../ARCHITECTURE.md) → [docs/adr/README.md](../adr/README.md) → this index → assigned track docs.  
**Do not edit:** [docs/forge/00-orchestrator-status.md](../forge/00-orchestrator-status.md).

Read this file first. Then open only the track docs for the workstream you are assigned. Do **not** invent paths, flip forge holds, or merge source repos wholesale.

**Naming (do not confuse):** harvest **Track T1 / T2** (pricing-ai-rules queues A/B) are **not** forge **Wave T** (skipped — do not name forge T1/T2). Harvest tracks: pricing T1/T2, deal-desk D1/D2, price-book P1–P4, trycompai CRM CC1.

---

## 0. ARCHITECTURE.md alignment (architect gate)

Steer from [docs/ARCHITECTURE.md](../ARCHITECTURE.md) §§1–11. **Pass** = this index may authorize the named DAG. **Fail** = reject the harvest stream.

| Constraint | ARCHITECTURE.md | Verdict | Notes |
|------------|-----------------|---------|-------|
| Landed vs target | §2 | **PASS** | Boot **4.0.6** / Java **21** stay. Do not treat GLM-5, NgRx, Activities, AKS, Elasticsearch, customer-portal as current. |
| Dual-read default `true` | §7 | **PASS** | Leave `${APPROVALS_LEGACY_LOCAL_READ:true}`. ADR-005 **Proposed**. No harvest PR flips YAML/env. |
| ADR-006 DTOs only | §5 | **PASS** | Accepted. Controllers stay DTO + `ApiResponse<T>`. No entity leak. **No** subscriptions FE from this ADR. |
| ADR-004 CSRF gateway-only | §4 | **PASS** | Domain services `csrf.disable()` + STATELESS. Do not import source Keycloak / Cookie CSRF on microservices. |
| Runtime AI Azure gpt-4o / Spring AI 0.8.1 | §2 AI | **PASS** | `ai-service` only. **No AI in `pricing-service`**. Track 2 / Path B Spring AI upgrade **HOLD** until new ADR **Accepted**. GLM-5 **not** runtime. |
| Java 21 / Boot 4.0.6 | §2, §12 | **PASS** | Port algorithms, not Boot 3 / Java 25 modules. `JAVA_HOME` **21.0.2** for Maven. |
| Path owners | §3 | **PASS** | Pricing `/api/v1/pricing/**`; price books `/api/v1/price-books/**` (never `/api/v1/pricing/price-books`); approvals via quote BFF not `/api/v1/approvals` from FE; source Deal → Quote + `ApprovalRequest`. |
| R2 list-row delete | §7, §9 | **PASS** | **HELD**. Harvest must not un-hold. |
| NgRx | §6, §9 | **PASS** | Signals + HttpClient. Angular Nx only. |
| Activities API | §2, §9 | **PASS** | Do not invent `/api/v1/activities/**`. |
| Easy Rules / full SpEL / OpenL | §2, §9 | **PASS** | Drools only for CPQ commercial rules. No Easy Rules runtime. Full SpEL held. |
| Mongo / Eureka | §2 | **PASS** | PostgreSQL + Flyway + gateway. Exclude source Mongo/Mongock/Eureka. |
| React / Supabase / Next | §6 | **PASS** | No FE harvest from source React/Vite/Supabase. |
| CI #238 / Kafka #241 | §8, §9 | **PASS** | Do not invent runners or agent Kafka. ITs: Testcontainers + `newgrp docker` when login lacks docker group. |
| Product `POST /validate` retained | §7 | **PASS** | Out of harvest. |
| GitNexus `-r` | §10 | **PASS** | Always `-r /home/username01/Projects/open-crm-cpq`. `UNKNOWN` ≠ unused — confirm with `rg`. |
| Forge Wave T skipped | §9, §11 | **PASS** | Harvest Track T1/T2 naming is **pricing queues**, not forge waves. |
| Overlay / orchestrator status | §11 | **PASS** | Implementers **must not** edit `docs/forge/00-orchestrator-status.md`. |

**Overall:** Index **aligns**. Queue A **closed** (no forge-hold breach). Next authorized work is **docs-only** C∥E. Code queues remain gated.

**Rejected alternatives:** authorize C3/E3 before matrices/backlogs merge; start Path B / Spring AI in pricing-service; reopen T1-1…T1-5; flip dual-read; un-hold R2; merge source monorepos.

---

## 1. How agents should use this index

| Step | Action |
|------|--------|
| 1 | Confirm **workstream** + **authorized track** in §3–§4. If the ID is not in §10 **Authorized now**, stop. |
| 2 | Open **parent plan** → **matrix** (if any) → **harvest backlog** |
| 3 | Obey **gates / holds** in §5 before writing code |
| 4 | Prefer **serial DAG** inside a queue; C-docs ∥ E-docs only as specified |
| 5 | GitNexus impact before **symbol** edits (`-r /home/username01/Projects/open-crm-cpq`); `detect_changes` before commit. Docs-only PRs: skip impact; still no forge-status edits. |
| 6 | **Do not** edit this index from C/E workers. Lead / architect updates Status after merge. |

**Non-goals for harvest agents:** merging source monorepos; Easy Rules / SpEL platforms; React/Supabase UIs; dual-read flip; R2 list-row delete; Activities API; NgRx; Mongo/Eureka; AI inside `pricing-service`; editing `00-orchestrator-status.md`.

---

## 2. Source repos → target owners

| Source (GitHub) | Target owners | Primary vocabulary remap |
|-----------------|---------------|---------------------------|
| [drgaciw/pricing-ai-rules-service](https://github.com/drgaciw/pricing-ai-rules-service) | `pricing-service`, `libs/rules-engine`; Track 2 → `ai-service` | `/evaluate` → `POST /api/v1/pricing/execute` |
| [drgaciw/deal-desk-service](https://github.com/drgaciw/deal-desk-service) | `approval-routing`, `quote-service` BFF; TCV → pricing residual (not closed T1 PRs) | Source Deal → Quote + ApprovalRequest |
| [drgaciw/price-book-service](https://github.com/drgaciw/price-book-service) | `pricebook-service`, `@frontend/cpq-price-books` | `/api/pricebooks` → `/api/v1/price-books` |

Cross-cutting: gateway + `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` for any new external path; ADR-006 DTOs only.

**Local clones:** pricing-ai-rules is pinned at `/home/username01/Projects/pricing-ai-rules-service` @ `3aa45bf164469364389e096df07e591c81079f6a`. Deal-desk and price-book clones **do not exist yet** — C0 / E0 create them.

---

## 3. Document catalog (all harvest markdowns)

Paths are relative to `docs/plans/`.

### 3.1 Index (this file)

| Doc | Role | Status |
|-----|------|--------|
| [00-harvest-index.md](./00-harvest-index.md) | Agentic backlog SSOT | **Active** — architect **APPROVED-WITH-GATES** 2026-09-14 |

### 3.2 Pricing AI / Drools — `pricing-ai-rules-service`

| Doc | Role | Track | Agentic status |
|-----|------|-------|----------------|
| [pricing-ai-rules-reuse-gap-analysis.md](./pricing-ai-rules-reuse-gap-analysis.md) | Parent plan, priorities, architecture rules | T1 + T2 | **T1 CLOSED** on `a4c5e80`; **T2 HOLD** |
| [pricing-ai-rules-capability-matrix-t1.md](./pricing-ai-rules-capability-matrix-t1.md) | T1 capability matrix @ pinned SHA | T1 | **Done** (#506) |
| [pricing-ai-rules-harvest-backlog-t1.md](./pricing-ai-rules-harvest-backlog-t1.md) | Ranked T1 PR DAG (T1-G0…T1-5) | T1 | **Done** — all PRs merged; do not reopen |

**Pinned source (T1):** `3aa45bf164469364389e096df07e591c81079f6a`.  
**Priority locked:** (1) deterministic pricing parity → (2) AI agents (after Path B ADR).  
**Hybrid metrics (still law):** ARR/TCV/ACV/CLV in Java `MetricsCalculator`; policy in DRL — no Calculate ARR/TCV as DRL.

### 3.3 Deal desk — `deal-desk-service`

| Doc | Role | Track | Agentic status |
|-----|------|-------|----------------|
| [deal-desk-reuse-gap-analysis.md](./deal-desk-reuse-gap-analysis.md) | Parent plan, D1/D2 sequencing | D1 + D2 | **Plan ready** (land with C0 if not on `main`); matrix/backlog **not yet written** |

**Priority locked:** (1) approval / state-machine parity → (2) TCV handoff to pricing **residual** backlog (Queue A T1-1…T1-5 are **closed** — D2 must not reopen them).  
**Authorized docs (generate before D1 code):**

- `deal-desk-capability-matrix-d1.md` (C1)
- `deal-desk-harvest-backlog-d1.md` (C2)

**Gated (not now):** `deal-desk-tcv-handoff-to-pricing-t1.md` (Queue D after C-GATE).

### 3.4 Price book — `price-book-service`

| Doc | Role | Track | Agentic status |
|-----|------|-------|----------------|
| [price-book-reuse-gap-analysis.md](./price-book-reuse-gap-analysis.md) | Parent plan, P1 priority | P1–P4 | **Plan ready** (land with E0 if not on `main`); P1 matrix/backlog **not yet written** |
| [price-book-harvest-backlog-p2.md](./price-book-harvest-backlog-p2.md) | Scored P2 candidates (bulk/coverage/UX) | P2 | **Planning-only**; **not** code-authorized until E-GATE |

**Priority locked:** (1) catalog domain parity (entry dating / resolve-batch).  
**Authorized docs (generate before P1 code):**

- `price-book-capability-matrix-p1.md` (E1)
- `price-book-harvest-backlog-p1.md` (E2)

### 3.6 CRM relationship data — `trycompai/crm` (Track CC1)

| Doc | Purpose | Track | Status |
|-----|---------|-------|--------|
| [trycompai-crm-reuse-gap-analysis.md](./trycompai-crm-reuse-gap-analysis.md) | Parent plan, architecture gate, comparison with Notion/Linear, dispositions | CC1 | **Plan ready** (docs only) |
| [trycompai-crm-capability-matrix-cc1.md](./trycompai-crm-capability-matrix-cc1.md) | Capability matrix @ `6d4793d` | CC1 | **Done** (docs) |
| [trycompai-crm-harvest-backlog-cc1.md](./trycompai-crm-harvest-backlog-cc1.md) | 17 ranked stories, gated on ADR-028 to ADR-034 (Proposed) | CC1 | **Filed** in Linear P-DRG-6 and Notion; no code authorized |

Source is MIT and in TypeScript/Next/Prisma: **patterns only**, no file copies. The Activities hold is **not** lifted by this track; ADR-030 proposes it.

### 3.5 Out of harvest scope (same folder)

| Doc | Note |
|-----|------|
| [jenkins-ai-ci-plan-summary.md](./jenkins-ai-ci-plan-summary.md) | CI/Jenkins — **not** a reuse harvest workstream |

Ignore `.worktrees/**/docs/plans/*` copies unless you are operating inside that worktree.

---

## 4. Agentic processing backlog (ordered queues)

Use these queues for dispatcher / supervisor agents. Items are **work packages**, not git commits.

### Queue A — Pricing T1 — **CLOSED** (`origin/main` `a4c5e80`)

A-GATE is **closed**. Do **not** spawn T1 implementers. Do **not** reopen T1-1…T1-5.

| ID | Package | Status | Merge SHA | PR |
|----|---------|--------|-----------|-----|
| A0 | T1-G0 pin + env @ `3aa45bf` | **CLOSED** | `b3ccd588b343b2404021d9a1b2a0d9fab93dcd2f` | [#505](https://github.com/drgaciw/open-crm-cpq/pull/505) |
| — | T1-D1 matrix + backlog | **CLOSED** | `79b2fad91df97264539653fba1f8d0d93999de3c` | [#506](https://github.com/drgaciw/open-crm-cpq/pull/506) |
| A1 | T1-1 fixtures evaluate→execute | **CLOSED** | `6d6e9c18741ff6cc099f05a6a9c1ed616645b262` | [#507](https://github.com/drgaciw/open-crm-cpq/pull/507) |
| A2 | T1-2 MetricsCalculator parity | **CLOSED** | `ffd0a7bc013147acdf08dad9471e2435c78f3eef` | [#508](https://github.com/drgaciw/open-crm-cpq/pull/508) |
| A3 | T1-3 metric-gated DRL gaps only | **CLOSED** | `ea4a3baf63a41e6db92c705d0a8412a27db786e9` | [#509](https://github.com/drgaciw/open-crm-cpq/pull/509) |
| A4 | T1-4 validate/compile parity | **CLOSED** | `cef33071dd580f2d22f2f5b0e5a95f16598a8dda` | [#510](https://github.com/drgaciw/open-crm-cpq/pull/510) |
| A5 | T1-5 Drools BOM **10.2.0** | **CLOSED** | `a4c5e80c8a6459c44979b35fac1ee3967b37a86f` | [#511](https://github.com/drgaciw/open-crm-cpq/pull/511) |
| A-GATE | Track 1 done gate | **CLOSED** | `a4c5e80` (tip of `origin/main`) | #511 |

Evidence: parent `backend/pom.xml` has `<drools.version>10.2.0</drools.version>`. Hybrid §5.1a remains in force for any later pricing residual.

A-GATE **does not** auto-start Queue B. Path B ADR is still **not** Accepted.

### Queue B — Pricing T2 (HOLD)

| ID | Package | Gate |
|----|---------|------|
| B0 | Path B Spring AI ADR draft | **GATED** — not in this parallel set (even though A-GATE is closed) |
| B1 | RuleValidator / Author / Optimizer ports in `ai-service` | **Accepted** Path B ADR |
| B-BLOCK | Any Spring AI in `pricing-service` | **Forbidden** |

Runtime remains Azure OpenAI **gpt-4o** / Spring AI **0.8.1** in `ai-service`.

### Queue C — Deal desk D1 (docs first) — **AUTHORIZED (docs only)**

| ID | Package | Input docs | Output | Status |
|----|---------|------------|--------|--------|
| C0 | Pin source SHA + inventory | parent §D0 | Clone + SHA recorded on parent plan | **Authorized** |
| C1 | Write `deal-desk-capability-matrix-d1.md` | parent (pinned) | Matrix merged | **Authorized** after C0 |
| C2 | Write `deal-desk-harvest-backlog-d1.md` | matrix | Ranked PRs | **Authorized** after C1 |
| C3 | Implement D1 pilots (transitions / guards) | backlog | Code in `approval-routing` / quote BFF | **HOLD** until C1–C2 merge |
| C-GATE | D1 done gate | parent | Unlock Queue D | After C3 |
| C-HOLD | Dual-read flip / Easy Rules / Salesforce / Mongo deal API | — | **Never** in this queue | Hold |

### Queue D — Deal desk D2 (handoff only) — **GATED**

| ID | Package | Output |
|----|---------|--------|
| D0 | Write `deal-desk-tcv-handoff-to-pricing-t1.md` | Tickets into a **pricing residual** backlog — **no** parallel TCV PRs; **do not** reopen closed Queue A slices |

Depends on **C-GATE**. Not authorized now.

### Queue E — Price book P1 (docs first) — **AUTHORIZED (docs only)**

| ID | Package | Input docs | Output | Status |
|----|---------|------------|--------|--------|
| E0 | Pin source SHA + inventory | parent §P0 | Clone + SHA recorded on parent plan | **Authorized** |
| E1 | Write `price-book-capability-matrix-p1.md` | parent (pinned) | Matrix merged | **Authorized** after E0 |
| E2 | Write `price-book-harvest-backlog-p1.md` | matrix | Ranked PRs | **Authorized** after E1 |
| E3 | Implement P1 (dating / resolve-batch) | backlog | Tests + pricing `PriceBookResolutionClient` regression | **HOLD** until E1–E2 merge |
| E-GATE | P1 done gate | parent | Unlock Queue F | After E3 |
| E-HOLD | R2 list-row delete / Mongo / Eureka / Drools-in-pricebook / React-Supabase | — | **Never** | Hold |

### Queue F — Price book P2 (after E-GATE) — **GATED**

| ID | Package | Input docs |
|----|---------|------------|
| F1 | P2-1 Coverage / gap API | [price-book-harvest-backlog-p2.md](./price-book-harvest-backlog-p2.md) |
| F2 | P2-2 Bulk entries API | same |
| F3 | P2-3 Health / P2-4 filters / P2-5 Angular UX | same (ordered per P2 DAG) |

---

## 5. Global gates and holds

| Gate / hold | Rule |
|-------------|------|
| Dual-read | Leave `${APPROVALS_LEGACY_LOCAL_READ:true}` — do not flip (ADR-005 **Proposed**) |
| R2 | Price-book **list-row delete** held — not a harvest unlock |
| Full SpEL / OpenL | Forge hold — do not port SpEL platforms |
| Easy Rules | Exclude — Drools only for CPQ commercial rules |
| Spring AI upgrade | Track 2 needs **Accepted** Path B ADR; runtime remains Azure gpt-4o / Spring AI 0.8.1 until then |
| AI in pricing-service | **Forbidden** |
| Entities at API | ADR-006 **Accepted** — DTOs + MapStruct |
| Path contracts | Prefer `CPQ_API_CONTRACT_MATRIX.md`; no `/api/v1/pricing/price-books`; FE approvals = quote BFF only |
| TCV ownership | Single owner: pricing (Queue A closed; D2 = residual handoff only) |
| Drools in pricebook | Forbidden — hand off to pricing |
| FE stacks | Angular Nx only — no React/Supabase/Next harvest |
| Mongo / Eureka | Exclude |
| Activities / NgRx / #238 / #241 | Forge holds |
| C3 / E3 | No production code until C1–C2 / E1–E2 **merge** |
| Orchestrator status | Do not edit `docs/forge/00-orchestrator-status.md` |

---

## 6. Parallelization (orchestrator)

| Parallel set | Who may run | Constraint |
|--------------|-------------|------------|
| **Authorized now** | **C0→C1→C2 ∥ E0→E1→E2** (docs) | Two Grok 4.6 `--always-approve` workers max. Serial **inside** each queue. No production Java/TS. |
| Queue A | **None** | Closed on `a4c5e80` |
| C3 / E3 | **None** | Until C1–C2 / E1–E2 merge **and** a new architect un-hold |
| Queue D / F / B | **None** | Remain gated |

**Herdr / worktree:** 2 isolated worktrees (C-docs ∥ E-docs). No pricing / ai-service / FE implementer panes.

C1+C2 may be **one PR** after C0 (T1-D1 pattern) if exclusive files stay `docs/plans/deal-desk-*`. Same for E1+E2 → `docs/plans/price-book-*`. C0/E0 must record SHA **before** matrix claims a pin.

Parent plans and `price-book-harvest-backlog-p2.md` were **untracked vs `origin/main`** at review time. Architect lands them with this index when committing `docs/forge-architect`. C0/E0 then **only** add D0/P0 pin + inventory fields — do not rewrite parent recommendations.

---

## 7. Prompt stubs (copy for worker agents)

### 7.1 Deal desk docs worker (Queue C — authorized)

```text
You are writing deal-desk Track D1 docs only (no production code).
Read docs/plans/00-harvest-index.md Queue C and §10 DAG (C0 then C1 then C2).
Read docs/plans/deal-desk-reuse-gap-analysis.md.
C0: clone drgaciw/deal-desk-service, pin SHA on the parent plan, inventory controllers, secret-scan note (no .env copy).
C1: produce docs/plans/deal-desk-capability-matrix-d1.md (approval/state-machine only).
C2: produce docs/plans/deal-desk-harvest-backlog-d1.md (ranked PRs; no C3 code).
Map source Deal → Quote + approval-routing. Exclude Easy Rules, Salesforce, Mongo deal API, dual-read flip.
Do not edit docs/plans/00-harvest-index.md or docs/forge/00-orchestrator-status.md.
GitNexus always -r /home/username01/Projects/open-crm-cpq (docs-only: no symbol impact required).
```

### 7.2 Price book P1 docs worker (Queue E — authorized)

```text
You are writing price-book Track P1 docs only (no production code).
Read docs/plans/00-harvest-index.md Queue E and §10 DAG (E0 then E1 then E2).
Read docs/plans/price-book-reuse-gap-analysis.md.
E0: clone drgaciw/price-book-service, pin SHA on the parent plan, inventory controllers with /api/v1 remap, secret-scan note.
E1: produce docs/plans/price-book-capability-matrix-p1.md (entry dating / resolve-batch / selective header).
E2: produce docs/plans/price-book-harvest-backlog-p1.md (ranked P1 PRs).
Do not authorize P2/E3 code. P2 scored ideas stay in price-book-harvest-backlog-p2.md.
No Mongo/Eureka/Drools-in-pricebook/React/Supabase/R2 list-row delete.
Do not edit docs/plans/00-harvest-index.md or docs/forge/00-orchestrator-status.md.
GitNexus always -r /home/username01/Projects/open-crm-cpq (docs-only: no symbol impact required).
```

### 7.3 Pricing T1 implementer — **do not spawn**

Queue A is closed on `a4c5e80`. Historical stub only (T1-1…T1-5 merged #507–#511).

### 7.4 Price book P2 planner — **do not spawn** (post E-GATE)

```text
P1 gate is closed. Implement only the assigned P2-N from
docs/plans/price-book-harvest-backlog-p2.md.
Update CPQ_API_CONTRACT_MATRIX.md for new paths. No Drools/Cost/R2.
```

---

## 8. Completion checklist (lead agent)

- [x] Queue A: T1-G0…T1-5 + A-GATE recorded (`a4c5e80`, #505–#511)
- [ ] Queue C: matrix + backlog (**docs authorized now**) + D1 pilots + C-GATE (**code gated**)
- [ ] Queue D: TCV handoff file → pricing **residual** backlog (**gated** on C-GATE)
- [ ] Queue E: matrix + backlog (**docs authorized now**) + P1 pilots + E-GATE (**code gated**)
- [ ] Queue F: P2-1 / P2-2 (as authorized after E-GATE)
- [x] Queue B: still HOLD unless Path B ADR Accepted
- [ ] This index statuses updated after C1–C2 / E1–E2 merge (lead only)

---

## 9. Maven / GitNexus env (every **code** PR — C3/E3 later, not now)

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
# Unit tests (no docker):
cd backend && ./mvnw -B -pl services/<module> -am test -DskipITs
# ITs when Testcontainers needed and login `id` lacks docker:
newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
cd backend && ./mvnw -B -pl services/<module> -am test
EOF
```

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "<Symbol>" --direction upstream \
  --repo /home/username01/Projects/open-crm-cpq --file path/to/File.java
# UNKNOWN ≠ unused — confirm with rg
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all \
  --repo /home/username01/Projects/open-crm-cpq
```

C0/E0 may build **source** clones with the same `JAVA_HOME` (`mvn -DskipTests package`). **No** `newgrp docker` unless they run Testcontainers (they must not, for pin).

---

## 10. Implementation DAG for Grok 4.6 `--always-approve` workers

**Verdict for orchestrator:** spawn **exactly two** docs workers in parallel. Do **not** spawn C3, E3, D, F, B, or Queue A.

### 10.1 Authorized now (docs)

| ID | Parallel? | Exclusive files | Tests | JAVA_HOME 21.0.2 | `newgrp docker` |
|----|-----------|-----------------|-------|------------------|-----------------|
| **C0** | **∥ E0** | Local clone `/home/username01/Projects/deal-desk-service`; `docs/plans/deal-desk-reuse-gap-analysis.md` (**D0 pin + inventory + secret-scan only**) | None in open-crm-cpq. Optional source `mvn -DskipTests package` | Yes if source build | **No** |
| **C1** | After C0; **∥ E\*** | **New** `docs/plans/deal-desk-capability-matrix-d1.md` only | None | n/a | **No** |
| **C2** | After C1; **∥ E\*** | **New** `docs/plans/deal-desk-harvest-backlog-d1.md` only | None | n/a | **No** |
| **E0** | **∥ C0** | Local clone `/home/username01/Projects/price-book-service`; `docs/plans/price-book-reuse-gap-analysis.md` (**P0 pin + inventory + secret-scan only**) | None in open-crm-cpq. Optional source build (Boot 3.2 — document failures) | Yes if source build | **No** |
| **E1** | After E0; **∥ C\*** | **New** `docs/plans/price-book-capability-matrix-p1.md` only | None | n/a | **No** |
| **E2** | After E1; **∥ C\*** | **New** `docs/plans/price-book-harvest-backlog-p1.md` only | None | n/a | **No** |

**Shared exclusions (all six IDs):** `docs/plans/00-harvest-index.md`; `docs/forge/00-orchestrator-status.md`; `backend/**`; `frontend/**`; `docs/backlog/CPQ_API_CONTRACT_MATRIX.md`; `docs/adr/**`.

**C1+C2** (resp. **E1+E2**) may land as a single PR after pin, matching T1-D1.

### 10.2 Gated — do not spawn

| ID | Depends | Exclusive files (preview) | Tests when un-held | JAVA_HOME | `newgrp docker` |
|----|---------|---------------------------|--------------------|-----------|-----------------|
| **C3** | C1–C2 merged | `approval-routing`, quote-service BFF tests; **no** pricing DRL unless coordinated | `./mvnw -B -pl services/approval-routing,services/quote-service -am test` | **21.0.2** | **Yes** if ITs / Testcontainers |
| **E3** | E1–E2 merged | `pricebook-service` (+ Flyway); `resolve-batch` ⇒ retest pricing client | `./mvnw -B -pl services/pricebook-service,services/pricing-service -am test` | **21.0.2** | **Yes** if ITs |
| **D0** | C-GATE | `docs/plans/deal-desk-tcv-handoff-to-pricing-t1.md` | None | n/a | **No** |
| **F1–F3** | E-GATE | per P2 backlog; matrix rows for new paths | pricebook + gateway contract tests | **21.0.2** | **Yes** if ITs |
| **B0 / B1** | Path B ADR **Accepted** (B1); B0 still **not** in this set | `docs/adr/*` then `ai-service` only | ai-service tests | **21.0.2** | as needed |
| **A\*** | — | — | — | — | **Closed** |

**Blast radius (authorized set):** `docs/plans/*` only + local source clones outside this monorepo. No gateway, no dual-read, no Drools BOM, no FE.

---

## 11. Quick links

| Resource | Path |
|----------|------|
| Operating architecture | `docs/ARCHITECTURE.md` |
| Contract matrix | `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` |
| Steering | `AGENT.md`, `docs/adr/README.md` |
| Phase 0 pricing priors | `docs/phase0/phase0-*.md` |
| Approvals dual-read | `docs/adr/ADR-005-approvals-legacy-dual-read.md` |
| API boundary | `docs/adr/ADR-006-entities-never-at-api-boundary.md` |
| CSRF edge | `docs/adr/ADR-004-csrf-edge-boundary.md` |
| Queue A tip | `origin/main` `a4c5e80c8a6459c44979b35fac1ee3967b37a86f` |
| GitNexus repo flag | `-r /home/username01/Projects/open-crm-cpq` |
