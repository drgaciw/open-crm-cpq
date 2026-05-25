# ADR-003: CI LLM Integration and Jenkins Architecture

## Status

Accepted

## Context

OpenCRM CPQ uses:

- **Jenkins** (`Jenkinsfile`, `infra/jenkins/`) as **primary CI** for branches and pull requests
- **GitHub Actions** (`.github/workflows/ci.yml`) as **manual fallback only** (`workflow_dispatch`)
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

**GitHub Actions does not run automatically on push/PR** (Option B cutover). Manual workflow retains SSOT stages for emergency use. Advisory LLM stages exist only in `Jenkinsfile` when `AI_CI_ENABLED=true`.

Jenkins publishes GitHub commit status context **`jenkins/opencrm-cpq-ci`** when `scm-token` is configured.

Machine-readable stage names, job `needs`, and Jenkins stage mapping: **[docs/ci-contract.yaml](../ci-contract.yaml)**.

## Consequences

- **Positive:** Single primary CI platform; AI assist native; no GHA minutes on every PR; SSOT scripts shared with manual GHA fallback
- **Negative:** Jenkins ops burden; branch protection must target Jenkins status; hosted Jenkins required for team PR gating

## References

- [docs/ci-contract.yaml](../ci-contract.yaml) — GHA/Jenkins stage contract (drift prevention)
- `infra/jenkins/README.md`
- `Jenkinsfile`
- `.github/workflows/ci.yml`
- `docs/plans/jenkins-ai-ci-plan-summary.md`
- `docs/phase0/phase0-ai-stack-decision.md`
