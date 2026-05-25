---
name: jenkins-failure-log-triage
description: >-
  Triages Jenkins pipeline failures using console logs and ai-assist log_analysis.
  Covers Testcontainers and advisory UNSTABLE stages. Use when Jenkins builds fail.
---

# Jenkins failure log triage

## 1. Identify failing stage

From Blue Ocean or console: Toolchain, Shared Libraries, Backend Integration, AI Assist, Security Audit.

## 2. AI log analysis (when enabled)

```bash
# From workspace on agent or locally with log excerpt
bash infra/jenkins/ai-assist/run-ai-stage.sh log_analysis \
  --stage "<stage-name>" \
  --output ci-reports/ai-log-analysis.md
```

Jenkinsfile `post { failure { ... } }` runs this automatically when `AI_CI_ENABLED=true`.

## 3. Map to SSOT script

| Stage | Script |
|-------|--------|
| Toolchain | `scripts/ci/toolchain-check.sh` |
| Shared Libraries | `scripts/ci/shared-libraries.sh` |
| Backend Unit | `scripts/ci/backend-unit.sh` |
| Backend Integration | `scripts/ci/backend-integration.sh` |
| Frontend | `scripts/ci/frontend.sh` |
| CPQ Beta | `scripts/ci/cpq-beta-python.sh` |
| Security Audit | `scripts/ci/security-audit.sh` |

Re-run locally: `bash scripts/ci/run-core.sh <stage>`

## 4. Testcontainers

- Docker required on executor
- Docker Desktop: `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` in `jenkins.env`

## 5. Advisory UNSTABLE

`AI_CI_BLOCKING=false` → AI/security failures may mark build UNSTABLE without failing core stages.

## Related

- Agent: **`jenkins-failure-triage`**
- Playbook: `failure-loop`
- GHA PR checks: use **`fix-ci`** skill instead
