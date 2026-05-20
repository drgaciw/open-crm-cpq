# ADR-003: CI LLM Integration and Jenkins Architecture

## Status

Accepted

## Context

OpenCRM CPQ uses:

- **GitHub Actions** (`.github/workflows/ci.yml`) for primary CI
- **Jenkins** (`Jenkinsfile`, `infra/jenkins/`) as an optional/on-prem mirror
- **`ai-service`** for product CPQ agents (`AgentRouter`, `/api/v1/ai/agents/*`)
- **`infra/jenkins/ai-assist/`** for pipeline advisory LLM stages (review, test gaps, log triage)

ADR-002 introduces GLM-5 (Zhipu) for runtime agents while `application.yml` still documents Azure OpenAI. CI must not conflate these paths.

## Decision

### 1. Single CI command source (SSOT)

All build/test commands live in `scripts/ci/run-core.sh` and stage scripts. Jenkins and GitHub Actions invoke the same shell entrypoints.

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

**GitHub Actions does not run `infra/jenkins/ai-assist/`.** Advisory LLM stages (`code_review`, `test_suggestions`, `log_analysis`) exist only in `Jenkinsfile` when `AI_CI_ENABLED=true`. GHA and Jenkins share build/test stages via `scripts/ci/run-core.sh` only.

Machine-readable stage names, job `needs`, and Jenkins stage mapping: **[docs/ci-contract.yaml](../ci-contract.yaml)**.

## Consequences

- **Positive:** No drift between Jenkins and GHA; clear trust zones; ai-service remains product-focused
- **Negative:** Two CI platforms to maintain until one is retired; LLM provider unification with ADR-002 still required for runtime

## References

- [docs/ci-contract.yaml](../ci-contract.yaml) — GHA/Jenkins stage contract (drift prevention)
- `infra/jenkins/README.md`
- `Jenkinsfile`
- `.github/workflows/ci.yml`
- `docs/plans/jenkins-ai-ci-plan-summary.md`
- `docs/phase0/phase0-ai-stack-decision.md`
