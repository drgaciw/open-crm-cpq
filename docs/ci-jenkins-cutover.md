# CI cutover — Jenkins primary (Option B)

## Decision

**Jenkins is the primary CI executor** for pushes and pull requests. GitHub Actions remains as a **manual fallback** only (`workflow_dispatch` on `.github/workflows/ci.yml`).

Rationale for this repo:

- Single-org workflow (no fork CI requirement).
- GitHub Actions billing blocked automated runs on PRs (May 2026). Resolved by 2026-09-28: a `pull_request` run started and executed steps (PR #555), which is why the unit gate below exists.
- Jenkins already hosts advisory AI assist and drift checks.
- `scripts/ci/run-core.sh` SSOT keeps commands identical if GHA is run manually.

## Required GitHub setup

### 1. Jenkins credentials

In `infra/jenkins/env/jenkins.env` (never commit):

| Variable | Purpose |
|----------|---------|
| `SCM_TOKEN` | GitHub PAT (repo + status + webhook admin) → JCasC credential `scm-token` |
| `GITHUB_REPO_OWNER` | e.g. `drgaciw` |
| `GITHUB_REPO_NAME` | e.g. `open-crm-cpq` |
| `GIT_REPO_URL` | `https://github.com/<owner>/<repo>.git` |

PAT scopes: `repo`, `read:org` (if org repo), `admin:repo_hook` (for webhooks).

### 2. Multibranch job

JCasC seeds **`opencrm-cpq`** multibranch job (branches + PRs with `Jenkinsfile`).

After `docker compose up`, open Jenkins → **Scan Repository Now** on `opencrm-cpq`, or configure a webhook:

- **Payload URL:** `${JENKINS_URL}/github-webhook/`
- **Events:** Push, Pull request

For local dev, periodic scan (1h) is enabled in JCasC.

### 3. Branch protection (GitHub repo settings)

On `main` (and release branches as needed):

- **Require status checks:** `jenkins/opencrm-cpq-ci`
- **Do not require** GitHub Actions CI jobs for merge (they no longer run on PR).
- Optional: require up-to-date branch before merge.

### 4. PR experience

`Jenkinsfile` publishes commit status via GitHub API when `scm-token` is set:

- Context: **`jenkins/opencrm-cpq-ci`**
- States: `pending` → `success` / `failure`

## PR gate on GitHub Actions (added 2026-09-28)

`.github/workflows/pr-check.yml` runs on every pull request to `main` and executes the drift check plus `scripts/ci/run-core.sh backend-unit`. It exists because no Jenkins instance was reachable, so `main` had no commit statuses; see ADR-003 section 7.

- It is fast and Docker-free. Integration, frontend and AI assist remain Jenkins-only or manual.
- The repository is private and the plan offers no branch protection (the API answers 403), so the check cannot be marked required. Merge only when **Backend Unit (PR gate)** is green: `gh pr checks <n> --watch`.
- If Jenkins comes back, keep requiring `jenkins/opencrm-cpq-ci` as described above. This job stays as the quick first signal.

## Manual GHA fallback

When Jenkins is unavailable or for debugging:

1. GitHub → **Actions** → **CI (manual fallback)** → **Run workflow**
2. Same SSOT stages as Jenkins; no AI assist stages.

## Rollback to GHA auto-CI

Re-add to `.github/workflows/ci.yml`:

```yaml
on:
  workflow_dispatch:
  push:
    branches: [main]
  pull_request:
    branches: [main]
```

Then disable or deprioritize Jenkins branch protection until stable.

## References

- [infra/jenkins/README.md](../infra/jenkins/README.md)
- [docs/adr/ADR-003-ci-llm-integration.md](adr/ADR-003-ci-llm-integration.md)
- [docs/ci-contract.yaml](ci-contract.yaml)
