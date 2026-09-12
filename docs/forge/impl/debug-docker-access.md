---
status: blocked_no_docker
id: debug-docker-access
date: 2026-09-12
host: omarchy-drgamtd0
user: username01
---

# debug-docker-access

Host diagnosis for Docker Engine / Testcontainers on this agent machine. **Docker is not usable** for `username01`. This does **not** close `tdd-be-it-reactor.md` (#493) or `tdd-be-it-crm.md` (#494) — those ITs remain blocked until a human with sudo grants socket access and `docker info` shows a Server section.

## Root cause

The daemon is **running**. The CLI client is **present**. Access fails because:

1. The API socket is `unix:///var/run/docker.sock` (same inode as `/run/docker.sock`).
2. Mode is `0660` (`srw-rw----`), owner `root`, group `docker` (gid **967**). No ACL beyond `user::rw- group::rw- other::---`.
3. `username01` supplementary groups are `username01` (1000) and `wheel` (998) only — **not** `docker`.
4. `getent group docker` → `docker:x:967:` (empty member list). `newgrp docker` / `sg docker` cannot work until `usermod` adds the user.
5. `sudo` for this user is `(ALL) ALL` **with a password**. `sudo -n` cannot `usermod` or `docker info`.

Unix connect as this user is `EACCES` (errno 13). Testcontainers 1.21.4 then reports “Could not find a valid Docker environment” because `UnixSocketClientProviderStrategy` fails with `java.net.BindException: Permission denied` on that socket.

**This is a host ACL problem, not an Engine 29 / Testcontainers API mismatch, and not a stopped daemon.**

## Evidence (2026-09-12)

### `id`

```
uid=1000(username01) gid=1000(username01) groups=1000(username01),998(wheel)
```

### Socket

```
srw-rw---- 1 root docker 0 Sep  8 16:22 /var/run/docker.sock
# same inode:
2348 srw-rw---- 1 root docker /run/docker.sock
2348 srw-rw---- 1 root docker /var/run/docker.sock
```

`stat`: `Access: (0660/srw-rw----) Uid: (0/root) Gid: (967/docker)`

`getfacl /var/run/docker.sock`: owner `rw-`, group `rw-`, other `---`. No user ACE for `username01`.

### `getent group docker`

```
docker:x:967:
```

Python `grp.getgrgid(967).gr_mem` is `[]`.

### `docker context ls`

```
NAME        DESCRIPTION                               DOCKER ENDPOINT
default *   Current DOCKER_HOST based configuration   unix:///var/run/docker.sock
```

`DOCKER_HOST` unset. No `~/.docker/config.json`. No rootless socket at `$XDG_RUNTIME_DIR/docker.sock`.

### `systemctl`

| Unit | State |
|------|--------|
| `docker.service` | **active (running)** since 2026-09-12 13:32:09 CDT; unit file **disabled** (socket-activated) |
| `docker.socket` | **active (running)** / **enabled**; `ListenStream=/run/docker.sock`; `SocketMode=0660`; `SocketUser=root`; `SocketGroup=docker` |

`dockerd` Main PID listening: `API listen on /run/docker.sock`. Engine **29.7.2**.

### CLI

`docker version` (exit 1):

```
Client:
 Version:           29.7.2
 API version:       1.55
 Context:           default
permission denied while trying to connect to the docker API at unix:///var/run/docker.sock
```

`docker info` (exit 1): Client 29.7.2 + plugins (buildx 0.37.0, compose 5.5.1); Server section empty; stderr:

```
permission denied while trying to connect to the docker API at unix:///var/run/docker.sock
```

`timeout 8 docker ps` → same permission denied, exit 1.

Python `AF_UNIX` connect to `/var/run/docker.sock`: `os.access` R/W **false**; `connect` → `[Errno 13] Permission denied`.

### Testcontainers 1.21.4 (no Maven IT hang)

Standalone `DockerClientFactory.instance().isDockerAvailable()` with `-Dapi.version=1.44`, `TESTCONTAINERS_RYUK_DISABLED=true`, 30s cap (did **not** start `postgres:16-alpine` / `redis:7-alpine`):

```
Trying out strategy: UnixSocketClientProviderStrategy
WARN  DOCKER_HOST unix:///var/run/docker.sock is not listening
java.net.BindException: Permission denied
    at sun.nio.ch.UnixDomainSockets.connect0
    ...
    at com.github.dockerjava.transport.UnixSocket
ERROR Could not find a valid Docker environment. Please check configuration.
isDockerAvailable=false
```

Rootless strategy: `$XDG_RUNTIME_DIR` set, `/run/user/1000/docker.sock` **does not exist**. Docker Desktop / docker-machine paths absent.

The “is not listening” warning is **misleading**: `docker.socket` is listening; the process cannot *connect* (`EACCES`). Same failure ITs would hit in `BaseIntegrationTest` static `POSTGRES.start()` (`postgres:16-alpine`) and `QuoteAcceptedRedisIntegrationTest` (`redis:7-alpine`).

## Non-destructive attempts (no sudo password) — all failed

| Attempt | Result |
|---------|--------|
| `sudo -n docker info` | `sudo: a password is required` |
| `sudo -n usermod -aG docker username01` | password required |
| `usermod -aG docker username01` | `Permission denied` / cannot lock `/etc/passwd` |
| `gpasswd -a username01 docker` | `Permission denied` |
| `newgrp docker` | `newgrp: crypt failed: Invalid argument` then `permission denied` (user is not a member) |
| `sg docker` | command not found |
| `setfacl -m u:username01:rw /var/run/docker.sock` | `Operation not permitted` |
| `chmod 660 /var/run/docker.sock` | `Operation not permitted` (**did not** try `chmod 666`) |
| `chgrp docker /var/run/docker.sock` | `Operation not permitted` |
| `DOCKER_HOST=unix:///run/docker.sock docker info` | same `EACCES` (same socket) |
| User `dockerd --host unix:///tmp/user-docker.sock` | `dockerd needs to be started with root privileges` (rootlesskit / slirp4netns / fuse-overlayfs **not** installed) |
| Podman / nerdctl / user docker.sock | not present |
| TCP 2375/2376 | not listening |
| `/run/containerd/containerd.sock` | `root:root` `0660`, also `EACCES` |

`wheel` does **not** grant docker.sock. Passwordless sudo is only a few Omarchy helpers (`omarchy-dns`, `timedatectl set-timezone`, …), not `usermod` or `docker`.

## Ruled out

| Hypothesis | Why it is not the blocker |
|------------|---------------------------|
| Daemon down | `systemctl is-active docker` → `active`; journal `API listen on /run/docker.sock` |
| Missing CLI | `/usr/bin/docker` Engine **29.7.2**; `ci_require_cmd docker` in `scripts/ci/backend-integration.sh` would pass |
| Wrong context / `DOCKER_HOST` | default context is the unix socket; env unset |
| Engine 29 vs Testcontainers default API 1.32 | Parent `backend/pom.xml` already sets Surefire `api.version=1.44` for Engine 29+. This host never reaches an API negotiation; connect is `EACCES` first. Do not bump the POM as a substitute for group membership. |
| Testcontainers 1.21.4 too old | BOM `${testcontainers.version}` is 1.21.4; ping used that jar and failed on **permission**, not API version. |
| nftables journal noise on docker start | Unrelated to unix socket mode/group. |
| Dual-read YAML / product validate / orchestrator status | Untouched; not involved. |
| Need to skip `@Tag("integration")` | **Rejected.** Do not skip, untag, or activate `skip-docker-tests` (`-Dskip.docker.tests=true`). |

## What a human with sudo must run

```bash
# 1. Add the agent user to the docker group (gid 967 on this host).
sudo usermod -aG docker username01

# 2. Confirm membership is recorded (before re-login the current session still lacks the group).
getent group docker
# expect: docker:x:967:username01

# 3. Re-login (or reboot). newgrp only works AFTER usermod:
#    log out of the graphical/session, log back in, or:
sg docker -c 'id -nG'   # after usermod; sg may be missing — then re-login
```

**Re-login is required.** `usermod` does not change groups of already-running agent/IDE/CI processes. After re-login, `id` must show `docker` in the group list.

Do **not**:

- `chmod 666 /var/run/docker.sock` (world-writable Docker API).
- Skip or untag `@Tag("integration")` / `@Tag("docker")` tests.
- Run `-Dskip.docker.tests=true` / profile `skip-docker-tests` as the “fix”.
- Flip `approvals.legacy-local-read-enabled` / dual-read YAML.
- Edit `docs/forge/00-orchestrator-status.md`.
- Treat #493 / #494 as green until `docker info` + ITs actually run.

Optional (only if group add is refused by policy): install Docker rootless (`rootlesskit`, `slirp4netns`, `fuse-overlayfs`) and a user `docker.sock` under `$XDG_RUNTIME_DIR`, then point `DOCKER_HOST` at it. That is a larger host change than `usermod` and is **not** done here.

## Verify (after re-login)

```bash
id
# must include docker (gid 967)

docker info
# Client + Server sections; no "permission denied"

docker info --format '{{.ServerVersion}}'
# 29.7.2 (or current Engine)

# CRM Testcontainers ITs (Postgres 16-alpine + Redis 7-alpine on QuoteAcceptedRedis):
cd backend
JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2 \
  ./mvnw -B -pl services/crm-service -am -Pintegration-tests verify --no-transfer-progress
```

Full reactor (CI `scripts/ci/backend-integration.sh` after `ci_require_cmd docker`):

```bash
cd backend
JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2 \
  ./mvnw -B -U verify --no-transfer-progress
```

`ci_require_cmd docker` only checks that the **binary** exists. Local `backend-integration.sh` will still fail at `docker version` / `docker info` until the socket is usable.

## Testcontainers 1.44 note

`backend/pom.xml` Surefire:

```xml
<!-- Docker Engine 29+ requires API >= 1.44 (Testcontainers 1.x default is 1.32). -->
<api.version>1.44</api.version>
```

Keep this after Docker access is granted. Engine 29.7.2 client reports API 1.55; a default 1.32 client would fail *after* the socket works. Do not revert it. ITs are `*Test` / `*IntegrationTest` (Surefire), not Failsafe.

## Related notes (still blocked)

| Note | PR | Status |
|------|----|--------|
| `docs/forge/impl/tdd-be-it-reactor.md` | #493 | `blocked_no_docker` — reactor `mvn verify` not run |
| `docs/forge/impl/tdd-be-it-crm.md` | #494 | `blocked_no_docker` — crm `-Pintegration-tests` not run |

This file is the host-level diagnosis. Re-run those IT tracks on a follow-up PR **after** verify above is green. Do not mark them fixed from this docs-only change.

## Holds (this PR)

- No Java / POM / Surefire / Failsafe / `@Tag` edits
- Dual-read YAML default still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- No `00-orchestrator-status.md`
- No `SECURITY_OPEN`
- No product `POST /validate` deletion
- No Jacoco gate change
- No world-writable docker.sock
