# Jenkins AI-Driven CI for OpenCRM CPQ

**Primary CI** for branches and pull requests. GitHub Actions is **manual fallback only** (`workflow_dispatch`). All build/test commands use **`scripts/ci/run-core.sh`**. Optional AI assist calls an **LLM gateway** (not `ai-service`).

**Cutover:** [docs/ci-jenkins-cutover.md](../../docs/ci-jenkins-cutover.md) · **Architecture:** [ADR-003](../../docs/adr/ADR-003-ci-llm-integration.md) · **Contract:** [docs/ci-contract.yaml](../../docs/ci-contract.yaml)

## Cursor Jenkins CI agent team

Autonomous agents and skills for Jenkins/GHA CI live under **`.cursor/teams/jenkins-ci-team.yaml`**.

| Playbook | Goal |
|----------|------|
| `first-green-build` | Local compose + toolchain smoke |
| `enable-pr-ci` | Multibranch JCasC ([jenkins/casc/multibranch.yaml.example](jenkins/casc/multibranch.yaml.example)) |
| `prod-hardening` | Build agents without controller `docker.sock` |
| `ci-change-safe` | SSOT stage edits + drift verification |
| `ai-advisory-on` | Enable AI assist (non-blocking default) |
| `failure-loop` | Jenkins failure triage |

Validate after changes:

```powershell
pwsh -File .cursor/teams/validate-jenkins-ci-team.ps1 -RepoRoot <repo-root>
python .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py
```

Orchestrator agent: **`jenkins-ci-orchestrator`**. For GHA PR check failures, use the separate **`fix-ci`** skill (not the Jenkins triage agent).

## Design decisions (DevOps + Architect review)

| Topic | Choice |
|-------|--------|
| **Controller image** | `jenkins/jenkins:2.580.2-lts-jdk25` (+ separate Temurin 25 for Maven `JAVA_HOME`) |
| **Primary CI** | Jenkins multibranch job `opencrm-cpq` (PR + branch builds) |
| **GHA** | `ci.yml` is manual fallback (`workflow_dispatch`) and nightly; `pr-check.yml` triggers on `pull_request` |
| **PR gate status** | `jenkins/opencrm-cpq-ci` (GitHub commit status API) |
| CI commands | `scripts/ci/*` — SSOT for Jenkins and manual GHA |
| LLM for CI | `LLM_*` env → chat completions gateway |
| `ai-service` sidecar | **Compose health dependency only** — Jenkins waits for healthy `ai-service`; does **not** supply `LLM_ENDPOINT` for ai-assist |
| GHA graph | Integration after shared-libs + unit; frontend parallel |
| GHA AI | **None** — ai-assist is **Jenkins-only** |
| Dev executors | `JENKINS_NUM_EXECUTORS=2` on controller |
| Prod | Controller `numExecutors=0` + `docker-compose.build-agent.yml` |
| AI gates | Advisory default (`AI_CI_BLOCKING=false`) |
| Secrets | Jenkins credential `ai-api-key` — not compose env in prod |

## Quick start (dev)

**Scripts (from repo root):**

```bash
bash infra/jenkins/scripts/quickstart.sh
```

```powershell
pwsh -File infra/jenkins/scripts/quickstart.ps1
```

Creates `env/jenkins.env` if missing, starts compose, waits for `/login`, then runs `scripts/ci/run-core.sh toolchain` on the host. Use `--down` / `-Down` to stop the stack.

**Manual:**

```bash
cd infra/jenkins
cp env/jenkins.env.example env/jenkins.env
# Set JENKINS_ADMIN_PASSWORD; optional LLM_API_KEY + LLM_ENDPOINT for AI

docker compose -f docker-compose.jenkins.yml --env-file env/jenkins.env up -d --build
```

Open http://localhost:8080/ (bound to localhost only).

### JCasC seed job vs manual Pipeline from SCM

| Approach | When to use |
|----------|-------------|
| **JCasC seed** (`jenkins/casc/jenkins.yaml` → `opencrm-cpq` multibranch) | Fast local bootstrap; discovers branches/PRs with Jenkinsfile |
| **Manual Pipeline from SCM** | Legacy; prefer multibranch + webhook per [ci-jenkins-cutover.md](../../docs/ci-jenkins-cutover.md) |

For PR builds, the **`opencrm-cpq`** multibranch job is primary — see [docs/ci-jenkins-cutover.md](../../docs/ci-jenkins-cutover.md). Template reference: [jenkins/casc/multibranch.yaml.example](jenkins/casc/multibranch.yaml.example).

## Compose overlays

| File | Use |
|------|-----|
| `docker-compose.jenkins.yml` | Default dev stack (6G RAM, docker.sock, 2 executors) |
| `docker-compose.build-agent.yml` | Prod pattern: controller without socket |
| `docker-compose.ai-sidecar.yml` | Optional: start `ai-service` + deps; **Jenkins `depends_on` with `service_healthy`** |

```bash
# Sidecar overlay — health dependency only (does NOT power ai-assist LLM calls)
cd ../../backend && mvn -pl services/ai-service -am package -DskipTests
cd ../infra/jenkins
docker compose -f docker-compose.jenkins.yml -f docker-compose.ai-sidecar.yml --env-file env/jenkins.env up -d
```

`ai-assist` scripts use `LLM_ENDPOINT` (Azure/APIM). Do not set it to `http://ai-service:8086`.

## Pipeline stages

| Stage | SSOT script |
|-------|-------------|
| Toolchain | `scripts/ci/run-core.sh toolchain` |
| Shared Libraries | `shared-libraries` |
| Parallel: Backend Unit, CPQ Beta, Frontend | `backend-unit`, `cpq-beta-python`, `frontend` |
| Backend Integration | `backend-integration` (Docker required) |
| AI Assist (**Jenkins only**) | `infra/jenkins/ai-assist/*` when `AI_CI_ENABLED=true` |
| Security Audit | `security-audit` (non-blocking) |

GitHub Actions runs the same SSOT stages **except** AI Assist — see [ci-contract.yaml](../../docs/ci-contract.yaml).

## AI configuration (Jenkins only)

```bash
AI_CI_ENABLED=true
AI_CI_BLOCKING=false   # set true only after tuning false-positive rate
LLM_API_KEY=...        # Jenkins credential id: ai-api-key
LLM_ENDPOINT=https://your-resource.openai.azure.com
LLM_MODEL=gpt-4o
```

Reports: `ci-reports/ai-code-review.md`, `ai-test-suggestions.md`, `ai-log-analysis.md` (on failure), `ai-drift-check.md` (when AI enabled), `ai-security-triage.md` (after security audit when AI enabled).

## Production build agent (step-by-step)

1. **Controller without executors or Docker socket**
   ```bash
   docker compose -f docker-compose.jenkins.yml -f docker-compose.build-agent.yml \
     --env-file env/jenkins.env up -d --build
   ```
   Confirm `JENKINS_NUM_EXECUTORS=0` in compose env for `jenkins` service.

2. **Create an inbound agent secret** in Jenkins → **Manage Jenkins** → **Nodes** → **build-agent-1** (or add node) → copy the agent secret.

3. **Mount the secret** for the `build-agent` service (example):
   ```bash
   echo -n '<agent-secret>' > /path/to/agent_secret
   # Mount /path/to/agent_secret:/run/secrets/agent_secret:ro in build-agent volumes
   ```
   The compose file reads `/run/secrets/agent_secret` for `-secret` (see `docker-compose.build-agent.yml` comment).

4. **Verify labels** — agent must match pipeline expectations: `linux docker maven node22 node24` (default in compose). `node22` remains a compatibility alias; the image runs Node 24 LTS.

5. **Restrict docker.sock** to the build agent host only; never on the controller in production.

6. **Secrets** — store `LLM_API_KEY` in vault; map to Jenkins credential `ai-api-key`. Omit `LLM_API_KEY` from compose in prod.

7. **TLS and auth** — reverse proxy in front of Jenkins; rotate `JENKINS_ADMIN_PASSWORD`.

8. **Egress** — allow Git, Maven Central, container registries, and your LLM endpoint only.

9. **Run a test build** on `main` and confirm **Backend Integration** runs on the labeled agent (Testcontainers needs Docker on that node).

## First green build checklist

- [ ] `env/jenkins.env` copied from `jenkins.env.example`; admin password changed
- [ ] `docker compose ... up -d --build` healthy (`curl -f http://localhost:8080/login`)
- [ ] `SCM_TOKEN` + repo owner/name set; multibranch job `opencrm-cpq` scanned
- [ ] Branch protection requires `jenkins/opencrm-cpq-ci` (see [ci-jenkins-cutover.md](../../docs/ci-jenkins-cutover.md))
- [ ] Workspace has Docker available for integration stage (controller dev or build agent prod)
- [ ] `AI_CI_ENABLED=false` for first run (optional: enable after core stages pass)
- [ ] Build completes: Toolchain → Shared Libraries → parallel fast checks → Backend Integration → Security Audit (may be UNSTABLE)
- [ ] JUnit archives present under `backend/**/target/surefire-reports/`
- [ ] If AI enabled: `ci-reports/ai-*.md` archived; `LLM_ENDPOINT` is **not** `:8086`

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|----------------|-----|
| Integration fails immediately | No Docker on executor | Mount `docker.sock` on agent or use dev compose |
| Testcontainers cannot start (Desktop) | Host networking | Set `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` in `jenkins.env` |
| AI stage skipped | `AI_CI_ENABLED` not `true` | Set in env/JCasC; rebuild container if env not passed |
| AI stage fails, build UNSTABLE | Advisory mode | Expected when `AI_CI_BLOCKING=false`; fix LLM config or disable AI |
| AI stage fails, build FAILED | `AI_CI_BLOCKING=true` | Fix gateway key, endpoint, model; or set blocking false |
| `ai-api-key` missing | Credential not seeded | Dev: set `LLM_API_KEY` in `jenkins.env`; prod: Jenkins credential only |
| Wrong LLM target (`:8086`) | Misconfigured endpoint | Use Azure/APIM chat URL; see `ai_client.py` guard |
| Sidecar stack won't start | `ai-service` JAR not built | `mvn -pl services/ai-service -am package -DskipTests` before overlay |
| Jenkins waits forever on sidecar | Health check failing | Check `docker logs opencrm-jenkins-ai-sidecar`; postgres/redis up |
| Build agent not picking jobs | Secret/labels | Verify agent online, labels `linux docker maven node22 node24`, secret mounted |
| Drift vs GHA | Different stage order | Compare [ci-contract.yaml](../../docs/ci-contract.yaml) and `.github/workflows/ci.yml` |

## Testcontainers

- Requires Docker on the build executor.
- Compose sets `DOCKER_HOST` and 6G memory limit for `mvn verify`.
- **Docker Desktop (Windows/Mac):** set `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` in `jenkins.env` if containers cannot start.

## Production checklist

1. `JENKINS_NUM_EXECUTORS=0` on controller; labeled agents with Docker (`linux docker maven node22 node24`).
2. No `docker.sock` on controller — use `docker-compose.build-agent.yml`.
3. Store `LLM_API_KEY` in vault → Jenkins credential only.
4. TLS reverse proxy in front of Jenkins; rotate default admin password.
5. Egress allowlist: Git, Maven Central, registries, LLM endpoint.
6. Keep `AI_CI_BLOCKING=false` until advisory quality is validated.
7. Multibranch pipeline for PRs (replace seed `pipelineJob` in JCasC when ready).
8. Backup `jenkins_home` volume; pin plugins via `jenkins/plugins.txt`.

## File layout

```
infra/jenkins/
├── scripts/quickstart.sh
├── scripts/quickstart.ps1
├── docker-compose.jenkins.yml
├── docker-compose.build-agent.yml
├── docker-compose.ai-sidecar.yml
├── env/jenkins.env.example
├── jenkins/{Dockerfile,plugins.txt,casc/jenkins.yaml}
└── ai-assist/{ai_client.py,code_review.py,test_suggestions.py,log_analysis.py,drift_sentinel.py,security_triage.py,run-ai-stage.sh}
.cursor/teams/jenkins-ci-team.yaml
.cursor/agents/jenkins-*.agent.md
.cursor/skills/jenkins-*/SKILL.md

scripts/ci/          # SSOT for GHA + Jenkins
docs/ci-contract.yaml
Jenkinsfile          # Orchestration only
```
