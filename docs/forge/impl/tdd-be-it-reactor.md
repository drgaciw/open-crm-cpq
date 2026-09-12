---
status: blocked_no_docker
---
# tdd-be-it-reactor

## Command (intended — not run)

CI `scripts/ci/backend-integration.sh` minus the `docker version` print:

```bash
cd backend
JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2 ./mvnw -B -U verify --no-transfer-progress
```

Prerequisite gate (ran first):

```bash
docker info
```

## Result

- **status:** `blocked_no_docker`
- **Docker:** client present (`29.7.2`), daemon **not usable**
- **Failure:** `permission denied while trying to connect to the docker API at unix:///var/run/docker.sock`
- **Socket:** `/var/run/docker.sock` exists (`root:docker`, mode `srw-rw----`); current user `username01` is in groups `username01`, `wheel` — **not** in `docker`
- **ITs:** **not skipped**; full reactor `mvn verify` (Testcontainers + remaining modules) **not executed**
- **Code diffs:** none (docs-only closeout)
- **Holds respected:** dual-read YAML untouched; no `00-orchestrator-status`; no `SECURITY_OPEN`; Jacoco gates not lowered
- JDK intended: 21.0.2 (`JAVA_HOME` as above)
- Date: 2026-09-12
- HEAD at block: `42f6f6e` (matches `main` at branch cut)

## Unblock

1. Grant Docker daemon access (add user to `docker` group and re-login, or equivalent rootless/`DOCKER_HOST` that can create containers).
2. Confirm `docker info` succeeds.
3. Re-run the command above; fix any TDD / Jacoco failures without lowering gates or touching held dual-read / orchestrator / `SECURITY_OPEN` surfaces.
