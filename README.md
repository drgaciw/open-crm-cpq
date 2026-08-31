# OpenCRM CPQ

Monorepo for OpenCRM CPQ: Java/Spring backend, Angular/NX frontend, and CPQ beta (Python/Frappe).

## CI

**Primary:** [GitHub Actions](.github/workflows/ci.yml) — build and test via `scripts/ci/run-core.sh` (single source of truth).

**Contract:** [docs/ci-contract.yaml](docs/ci-contract.yaml) — machine-readable stage graph for drift checks between GHA and Jenkins.

## Optional Jenkins CI

On-prem or local Jenkins mirrors the same `scripts/ci/*` stages as GitHub Actions. **AI assist** (code review, test suggestions, log triage) runs **only on Jenkins** when `AI_CI_ENABLED=true`; GHA does not invoke `infra/jenkins/ai-assist/`.

| Resource | Purpose |
|----------|---------|
| [infra/jenkins/README.md](infra/jenkins/README.md) | Docker stack, production build agents, troubleshooting, first green build |
| [docs/adr/README.md](docs/adr/README.md) | ADR catalog (001–006 + configuration reconciliation) |
| [docs/adr/ADR-003-ci-llm-integration.md](docs/adr/ADR-003-ci-llm-integration.md) | CI LLM boundaries, execution model, GHA vs Jenkins |
| [docs/plans/jenkins-ai-ci-plan-summary.md](docs/plans/jenkins-ai-ci-plan-summary.md) | Plan objectives and delivery status |

Quick start: `cd infra/jenkins && cp env/jenkins.env.example env/jenkins.env && docker compose -f docker-compose.jenkins.yml --env-file env/jenkins.env up -d --build`
