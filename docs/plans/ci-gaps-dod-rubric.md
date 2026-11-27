# CI, UAT & Repo Hygiene Follow-Ups — Definition of Done (DoD) Rubric

> **Pane-map note (re-pinned 2026-11-27):** The `w3:p*` pane ids in this document are from the historical `w3` workspace, which no longer exists (`herdr workspace list` → only `w1`; `w3` → `workspace_not_found`). Live mapping: `pi-00` = `w1:p1`, `pi-01` = `w1:p7`, `agy` = `w1:p3`. `pi-decision` (was `w3:pA`), `architect` (was `w3:pB`) and `codex` (was `w3:p8`) have no live counterpart in this workspace — treat those references as historical. Do not resolve a `w3:p*` id against the current session.

**Owner:** `pi-decision` (`w3:pA`)
**Requestor:** Architect (`w3:pB`)
**Decision Engine:** TypeSafe Jev (`typesafe/jev-1.13` via `frontend/e2e/lib/decide.mjs` + `.env` `OPENROUTER_API_KEY`)

---

## Jev-Calibrated Design Decisions (`D1–D5`)

| ID | Task | Chosen Option | Jev Confidence | Summary |
|---|---|---|---|---|
| **D1** | T1 | **`a`** | `0.48` (`p=0.65`) | Nx `typecheck` target (`tsc --noEmit` on app/lib + spec tsconfigs) on every project, run by `scripts/ci/frontend.sh` with `--all` |
| **D2** | T1 | **`a`** | `0.92` (`p=0.95`) | New `frontend` job in `.github/workflows/pr-check.yml` calling `bash scripts/ci/run-core.sh frontend` (full build/lint/typecheck/test) |
| **D3** | T2 | **`a`** | `0.96` (`p=0.97`) | New SSOT stage `frontend-uat` booting Postgres/Redis + `crm-service` + `gateway` + `nx serve` in CI and running `frontend/e2e/uat-cdk-ux.mjs`; wired in `ci.yml`, `Jenkinsfile`, and `docs/ci-contract.yaml` (not `pr-check.yml`) |
| **D4** | T4 | **`b`** | `0.98` (`p=0.99`) | `git rm --cached` + `.gitignore` for `.cursor/hooks/state/continual-learning.json` and `.cursor/hooks/state/continual-learning-index.json` |
| **D5** | T5 | **`b`** | `0.42` (`p=0.61`) | Written convention in `AGENTS.md` + coordination message to `codex` (`w3:p8`) + scripted guard (pre-commit/CI check failing on foreign paths, if feasible) |

---

## Global Blocking Gates (Apply to Every Task)

- **G1 — CI SSOT & Drift Check:** Any CI stage change updates `scripts/ci/<stage>.sh`, `scripts/ci/run-core.sh`, `Jenkinsfile`, `.github/workflows/ci.yml`, and `docs/ci-contract.yaml` together. `python3 .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py` exits `0`. `backend-integration` depends only on `shared-libraries` and `backend-unit`. Zero `ai-assist` in GitHub Actions.
- **G2 — Workflow Security Hygiene:** GitHub Actions remain pinned to exact SHAs/versions matching existing workflows, `permissions` remain least-privilege, and zero secrets are committed.
- **G3 — Shared Stack & Peer Isolation:** Do **not** restart `:4200`/`:8080`/`:8081`, do **not** kill pane `w3:pC`, and do **not** touch or disrupt `codex` (`w3:p8`) or its `~/worktrees/goal-*` worktrees.
- **G4 — Frontend CI Green:** `./scripts/ci/frontend.sh` exits `0` across all Nx projects.
- **G5 — Diff & Docs Accuracy:** `git diff --check` is clean, and the `AGENTS.md` completion-review gap list is updated to reflect what is now true (typecheck target, `pr-check.yml` frontend stage, CI UAT stage).
- **G6 — TDD Discipline:** All new scripts, targets, and guards are developed red→green with automated tests/checks.

---

## Per-Task Acceptance Criteria

### T1 — Frontend `typecheck` Target & `pr-check.yml` Stage (`D1=a`, `D2=a`)
- **T1.1 (Nx `typecheck` target):** Every Nx project (20/20) exposes a `typecheck` target running `tsc --noEmit` on its app/lib and spec tsconfigs; `cd frontend && npx nx run-many -t typecheck --all` exits `0`.
- **T1.2 (`scripts/ci/frontend.sh`):** Invokes `nx run-many -t typecheck --all` alongside build/lint/test; `./scripts/ci/frontend.sh` exits `0`.
- **T1.3 (`pr-check.yml` DRG-527):** `.github/workflows/pr-check.yml` includes a `frontend` job calling `bash scripts/ci/run-core.sh frontend` with pinned actions and least-privilege `permissions`.
- **T1.4 (Drift Check):** `python3 .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py` exits `0`.

### T2 — `frontend-uat` SSOT Stage (`D3=a`)
- **T2.1 (Stage Script):** `scripts/ci/frontend-uat.sh` boots Postgres + Redis, `crm-service` (`:8081`), `gateway-service` (`:8080`), and `nx serve crm` (`:4200`), waits for readiness, runs `node e2e/uat-cdk-ux.mjs` (from `frontend/`), and tears down cleanly on exit.
- **T2.2 (SSOT Wiring):** `frontend-uat` is wired in `scripts/ci/run-core.sh`, `Jenkinsfile`, `.github/workflows/ci.yml` (nightly/full matrix), and `docs/ci-contract.yaml` — and explicitly **excluded** from `.github/workflows/pr-check.yml`.
- **T2.3 (Drift Check):** `python3 .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py` exits `0`.

### T3 — Plan Status Update
- **T3.1:** `docs/plans/angular-cdk-ux-improvements-plan.md` `**Status:**` is updated from `In Progress` to `Complete` (explicitly requested by the user), with no unintended edits to other sections.

### T4 — Hook State Untracked & `AGENTS.md` Gap List (`D4=b`)
- **T4.1 (`.gitignore` + Untrack):** `.cursor/hooks/state/continual-learning.json` and `.cursor/hooks/state/continual-learning-index.json` are untracked via `git rm --cached` and ignored in `.gitignore` (`git status` no longer shows them as modified).
- **T4.2 (`AGENTS.md` Gap List):** `AGENTS.md` completion-review gap list is updated to remove/correct stale claims about missing frontend typecheck, missing `pr-check.yml` frontend stage, and un-wired CDK UAT.

### T5 — One-Writer-Per-Checkout Guarantee (`D5=b`)
- **T5.1 (Convention in `AGENTS.md`):** `AGENTS.md` documents the one-writer-per-checkout rule requiring `codex` (`w3:p8`) to write only in its `~/worktrees/goal-*` worktrees and not in the main checkout.
- **T5.2 (Coordination):** Coordination message sent to `codex` (`w3:p8`) confirming the worktree-only write boundary without disrupting its active work.
- **T5.3 (Scripted Guard):** A lightweight guard script (e.g., pre-commit/CI check failing on foreign/out-of-lane paths when invoked, or documented feasibility note if blocked) is added and verified with a red→green test.

---

## Peer-to-Peer Sign-Off Protocol

1. **Submit Evidence:** Implementer(s) or Architect (`w3:pB`) submit task ID(s) (`T1–T5` or `FINAL`), modified files, and exact command outputs (`./scripts/ci/frontend.sh`, `verify-drift.py`, `git status`, `git diff --check`) to `pi-decision` (`w3:pA`).
2. **Thresholds:** Per-task sign-off requires TypeSafe Jev confidence **`>= 0.85`**; final sign-off requires **`>= 0.90`** plus all Global Blocking Gates (`G1–G6`) green.
3. **Verdicts:**
   - **`PASS`** — All in-scope criteria and blocking gates verified; confidence meets threshold.
   - **`REVISE`** — Specific failing check or missing criterion identified; fix and resubmit.
   - **`HOLD`** — Violation of SSOT/drift check, workflow security, or shared-stack/worktree isolation (`G1–G3`).
