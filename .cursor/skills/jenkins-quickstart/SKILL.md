---
name: jenkins-quickstart
description: >-
  Starts local dockerized Jenkins for OpenCRM CPQ: env file, compose up, health check,
  first green build checklist. Use when bringing up Jenkins locally or verifying dev CI.
---

# Jenkins quickstart

## Prerequisites

- Docker with compose v2
- Repo cloned; `infra/jenkins/env/jenkins.env` from example

## Quickstart scripts (recommended)

From repository root:

```bash
# Bash (Linux, macOS, Git Bash)
bash infra/jenkins/scripts/quickstart.sh

# PowerShell (Windows)
pwsh -File infra/jenkins/scripts/quickstart.ps1
```

Options (both scripts): `--up-only` / `-UpOnly` (skip toolchain smoke), `--health-only` / `-HealthOnly`, `--smoke-only` / `-SmokeOnly`, `--down` / `-Down`.

## Manual steps

```bash
cd infra/jenkins
cp env/jenkins.env.example env/jenkins.env
# Edit: JENKINS_ADMIN_PASSWORD (required); optional LLM_* for later AI

docker compose -f docker-compose.jenkins.yml --env-file env/jenkins.env up -d --build
```

Verify: http://localhost:8080/login

## First build (AI off)

1. Job: JCasC seed `opencrm-cpq-ci` **or** Pipeline from SCM → `Jenkinsfile` at repo root.
2. Set `AI_CI_ENABLED=false` in env/JCasC for first run.
3. Expect: Toolchain → Shared Libraries → parallel fast checks → Backend Integration → Security Audit (may be UNSTABLE).

## Smoke test (host)

```bash
bash scripts/ci/run-core.sh toolchain
```

## Checklist

See [infra/jenkins/README.md](infra/jenkins/README.md) — **First green build checklist**.

## Related

- Agent: **`jenkins-compose-bootstrap`**
- Playbook: `first-green-build` in `.cursor/teams/jenkins-ci-team.yaml`
