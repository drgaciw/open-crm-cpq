# Jenkins AI-Driven CI — Plan Summary

Brief status of the Jenkins + advisory LLM CI initiative. Full implementation lives in the repo; this page does not duplicate Cursor plan files.

## Objectives

1. **Dockerized Jenkins** with toolchain matching GHA (Java 21, Node 22, Maven, Python 3.11, Docker for Testcontainers).
2. **GHA parity** — same commands and dependency graph via `scripts/ci/run-core.sh`.
3. **Advisory AI** on Jenkins only — LLM gateway for diff review, test-gap hints, and failure log triage (`AI_CI_BLOCKING=false` by default).
4. **Clear boundaries** — CI LLM ≠ `ai-service` product agents; sidecar overlay is a **health dependency**, not the ai-assist endpoint.
5. **Security and ops** — credential `ai-api-key`, non-blocking security audit, resource limits, prod controller without `docker.sock`.

## Delivered artifacts

| Area | Paths |
|------|--------|
| SSOT scripts | `scripts/ci/run-core.sh`, `scripts/ci/*.sh` |
| GHA workflow | `.github/workflows/ci.yml` |
| Jenkins orchestration | `Jenkinsfile` |
| Docker / JCasC | `infra/jenkins/docker-compose*.yml`, `jenkins/Dockerfile`, `jenkins/casc/jenkins.yaml`, `jenkins/plugins.txt` |
| AI assist | `infra/jenkins/ai-assist/*` |
| Architecture | `docs/adr/ADR-003-ci-llm-integration.md` |
| Runbook | `infra/jenkins/README.md` |
| Drift contract | `docs/ci-contract.yaml` |
| Reports dir | `ci-reports/` (AI markdown artifacts) |

## Delivery status

| Track | Status | Notes |
|-------|--------|--------|
| Dev Jenkins stack | **Done** | `docker-compose.jenkins.yml`, 2 executors, docker.sock on trusted host |
| GHA graph parity | **Done** | Integration after shared-libs + unit only |
| AI assist (advisory) | **Done** | Jenkins-only; credential + `LLM_*` gateway |
| P1 documentation / drift | **Done** | `ci-contract.yaml`, README, ADR/runbook updates |
| Production build agents | **Partial** | `docker-compose.build-agent.yml` scaffold; inbound agent secret wiring is environment-specific |
| Blocking AI gates | **Deferred** | Enable `AI_CI_BLOCKING` only after false-positive review |
| Multibranch PR jobs | **Deferred** | JCasC seeds `opencrm-cpq-ci`; replace with multibranch when ready |

## References

- [docs/ci-contract.yaml](../ci-contract.yaml)
- [infra/jenkins/README.md](../../infra/jenkins/README.md)
- [ADR-003](../adr/ADR-003-ci-llm-integration.md)
