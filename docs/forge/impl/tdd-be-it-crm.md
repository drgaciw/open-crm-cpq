---
status: blocked_no_docker
---
# tdd-be-it-crm

## Intent

Run **all** `crm-service` Testcontainers integration tests (`-Pintegration-tests` / `@Tag("integration")`), TDD any red tests, then push + open PR. Do **not** skip or untag ITs; do **not** flip dual-read YAML; do **not** edit `00-orchestrator-status.md`; do **not** delete product validate.

## Gate: `docker info`

```bash
docker info
```

### Exact error

```
Client:
 Version:    29.7.2
 Context:    default
 Debug Mode: false
 Plugins:
  buildx: Docker Buildx (Docker Inc.)
    Version:  0.37.0
    Path:     /usr/lib/docker/cli-plugins/docker-buildx
  compose: Docker Compose (Docker Inc.)
    Version:  5.5.1
    Path:     /usr/lib/docker/cli-plugins/docker-compose

Server:
permission denied while trying to connect to the docker API at unix:///var/run/docker.sock
```

### Host facts

| Item | Value |
|------|-------|
| Socket | `/var/run/docker.sock` — `srw-rw---- 1 root docker` |
| User | `uid=1000(username01)` |
| Groups | `username01`, `wheel` — **not** in `docker` group |
| Docker Engine client | 29.7.2 (needs Testcontainers API 1.44 — already in parent pom) |
| JDK | `JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2` |
| Date | 2026-09-12 |

## Blocked action (not run)

```bash
cd backend
JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2 ./mvnw -B \
  -pl services/crm-service -am -Pintegration-tests verify --no-transfer-progress
```

Focused alternative (also not run): `-Dgroups=integration`.

## What was not done

- No IT classes skipped, disabled, or `@Tag("integration")` weakened
- No dual-read YAML flip
- No edits to `docs/forge/00-orchestrator-status.md`
- No product `POST /validate` deletion
- No production code changes (docs-only closeout)

## Unblock

Add the CI/agent user to the `docker` group (or otherwise grant access to `/var/run/docker.sock`), re-login/newgrp, confirm `docker info` shows a Server section, then re-run the Maven command above on this branch.
