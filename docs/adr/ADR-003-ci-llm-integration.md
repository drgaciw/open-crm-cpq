# ADR-003: CI LLM Integration and Jenkins Architecture

## Status

Accepted

**Amendment 2026-10-16 (Proposed; accepted when the owner merges it):** a post-merge gate on `main`, `.github/workflows/main-verify.yml`, and a nightly scheduled run of the full `ci.yml` matrix (Decision section 8). Push and pull-request triggers on `ci.yml` stay disabled.

**Amended 2026-09-28:** GitHub Actions now also runs one pull-request gate, `.github/workflows/pr-check.yml` (Decision section 7). `ci.yml` is unchanged and stays manual.

**Verified 2026-08-31:** `.github/workflows/ci.yml` is `on: workflow_dispatch` only (no push/PR; no `ai-assist`). `Jenkinsfile` gates advisory stages on `AI_CI_ENABLED`. `docs/ci-contract.yaml` forbids adding ai-assist to GHA without amending this ADR. Matches decision.

## Context

OpenCRM CPQ uses:

- **Jenkins** (`Jenkinsfile`, `infra/jenkins/`) as **primary CI** for branches and pull requests
- **GitHub Actions** (`.github/workflows/ci.yml`) as **manual fallback** (`workflow_dispatch`), plus the small pull-request gate `.github/workflows/pr-check.yml` (section 7)
- **`ai-service`** for product CPQ agents (`AgentRouter`, `/api/v1/ai/agents/*`)
- **`infra/jenkins/ai-assist/`** for pipeline advisory LLM stages (review, test gaps, log triage)

Cutover runbook: **[docs/ci-jenkins-cutover.md](../ci-jenkins-cutover.md)**.

ADR-002 introduces GLM-5 (Zhipu) for runtime agents while `application.yml` still documents Azure OpenAI. CI must not conflate these paths.

## Decision

### 1. Single CI command source (SSOT)

All build/test commands live in `scripts/ci/run-core.sh` and stage scripts. **Jenkins** runs them on every branch/PR. **GitHub Actions** uses the same entrypoints when manually dispatched.

### 2. CI LLM boundary

| Caller | Target | Purpose |
|--------|--------|---------|
| `ai-assist/*.py` | Azure OpenAI or OpenAI-compatible **gateway** (`LLM_ENDPOINT`) | Advisory CI only |
| `ai-service` | Product agents via gateway `/api/v1/ai/**` | Runtime CPQ features |
| Jenkins sidecar overlay | `ai-service` **Compose health dependency** (`depends_on: service_healthy`) | **Not** LLM for `ai-assist` |

Environment variables: `LLM_PROVIDER`, `LLM_API_KEY`, `LLM_ENDPOINT`, `LLM_MODEL`, `LLM_TIMEOUT_MS` (legacy `AI_*` aliases supported).

### 3. AI gate policy

- **Phase A (default):** `AI_CI_ENABLED=false` or advisory (`AI_CI_BLOCKING=false`)
- **Phase B:** Blocking only after false-positive review; never block on subjective review alone

### 4. Jenkins execution model

| Environment | Controller | Docker socket | Executors |
|-------------|------------|---------------|-----------|
| Local dev | `docker-compose.jenkins.yml` | On controller (trusted host) | `JENKINS_NUM_EXECUTORS=2` |
| Production | `docker-compose.build-agent.yml` overlay | On labeled agents only | `JENKINS_NUM_EXECUTORS=0` |

### 5. GHA stage graph parity

`backend-integration` depends on `shared-libraries` + `backend-unit` only. Frontend and `cpq_beta` run in parallel but do not gate integration.

### 6. AI assist platform scope

**`ci.yml` does not run automatically on push/PR** (Option B cutover). The manual workflow retains SSOT stages for emergency use. The automatic GitHub Actions workflows are the pull-request gate in section 7 and, from the section 8 amendment, the post-merge gate and the nightly `ci.yml` schedule. Advisory LLM stages exist only in `Jenkinsfile` when `AI_CI_ENABLED=true`.

Jenkins publishes GitHub commit status context **`jenkins/opencrm-cpq-ci`** when `scm-token` is configured.

### 7. Pull-request gate on GitHub Actions (amendment 2026-09-28)

**Why.** No Jenkins instance or webhook was reachable, so `main` had no commit statuses and pull requests merged on local runs only. The security-baseline and event-contract fitness tests that enforce ADR-016 and ADR-018 run in the unit stage, so without a PR check they were advisory. The Option B rationale that GitHub Actions billing blocked PR runs no longer holds: a `pull_request` run started and executed steps on 2026-09-28 (PR #555).

**What.** `.github/workflows/pr-check.yml` runs on `pull_request` to `main`, with one job, **Backend Unit (PR gate)**:

- `verify-drift.py` (the blocking non-AI drift gate);
- `bash scripts/ci/run-core.sh backend-unit`, the same Docker-free stage as Jenkins, which also fails if a unit test starts a Testcontainer.

**Boundaries.**

- The workflow calls the SSOT entrypoint only. It holds no build logic, no LLM stage and no secrets, and `contents: read` is its only permission.
- Integration (Testcontainers), frontend, `cpq_beta` and the AI assist stages stay Jenkins-only or manual, as before.
- Jenkins remains the primary CI. When a Jenkins instance is reachable, `jenkins/opencrm-cpq-ci` becomes the fuller gate and this job stays as the fast one.
- `verify-drift.py` checks the workflow against [docs/ci-contract.yaml](../ci-contract.yaml) (`github_pr_gate`).

**Limit.** The repository is private and its plan does not offer branch protection or rulesets (the API answers 403), so the check cannot be made *required* on GitHub. Until the plan changes or the repository is public, the rule is procedural: merge only when **Backend Unit (PR gate)** is green (`gh pr checks <n> --watch`).

### 8. Post-merge gate and nightly full run (amendment 2026-10-16, Proposed)

**Why.** Section 7 checks each pull request on its own head. Nothing checks the head of `main` that results when several pull requests merge in quick succession. On 2026-10-16, #612 and #615 were each green and merged minutes apart, and `main` stopped compiling (DRG-613). Later the same evening eight pull requests merged within eight minutes with no run on the combined head. Separately, Jenkins is still unreachable, so the integration (Testcontainers), frontend and `cpq_beta` stages had not run anywhere since the May billing block. That block is gone: a manual `ci.yml` dispatch on 2026-10-16 acquired GitHub-hosted runners for every job (issue #238).

**What.**

- `.github/workflows/main-verify.yml` runs on `push` to `main` (and `workflow_dispatch`), with one job, **Backend Unit (main)**: `verify-drift.py` plus `bash scripts/ci/run-core.sh backend-unit`, the same Docker-free stage as the pull-request gate. A single concurrency group with `cancel-in-progress` means a burst of merges is verified once, on its newest head.
- `ci.yml` gains a nightly `schedule` (02:17 UTC) in addition to `workflow_dispatch`, so the full matrix runs on `main` once a day.

**Boundaries.**

- Both workflows call the SSOT entrypoint only, hold no build logic, no LLM stage and no secrets.
- `ci.yml` still has no `push` or `pull_request` trigger; `verify-drift.py` keeps enforcing that, and now also validates `main-verify.yml` against `github_main_gate` in [docs/ci-contract.yaml](../ci-contract.yaml).
- Jenkins remains the primary CI. When a Jenkins instance is reachable, the nightly schedule can be removed again; `main-verify.yml` stays as the fast post-merge signal.

**Cost.** One unit run per merge burst plus one full matrix run per day, on top of the existing per-pull-request runs.

**Procedure.** A red **Backend Unit (main)** or nightly run is treated like a broken build: the next merge to `main` is the fix or a revert.

Machine-readable stage names, job `needs`, and Jenkins stage mapping: **[docs/ci-contract.yaml](../ci-contract.yaml)**.

## Consequences

- **Positive:** Single primary CI platform; AI assist native; no GHA minutes on every PR; SSOT scripts shared with manual GHA fallback
- **Negative:** Jenkins ops burden; branch protection must target Jenkins status; hosted Jenkins required for the full PR gate. The GitHub PR gate covers only the unit stage and cannot be made a required check on the current plan (section 7)

## References

- [docs/ci-contract.yaml](../ci-contract.yaml) — GHA/Jenkins stage contract (drift prevention)
- `infra/jenkins/README.md`
- `Jenkinsfile`
- `.github/workflows/ci.yml`
- `.github/workflows/pr-check.yml`
- `.github/workflows/main-verify.yml`
- `docs/plans/jenkins-ai-ci-plan-summary.md`
- `docs/phase0/phase0-ai-stack-decision.md`
