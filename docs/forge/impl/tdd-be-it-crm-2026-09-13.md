---
status: green
---
# tdd-be-it-crm 2026-09-13

## Intent

Run **all** `crm-service` Testcontainers ITs (`-Pintegration-tests`), TDD red tests, PR. Dual-read YAML untouched. No `00-orchestrator-status`. Jacoco line 0.80 not lowered.

## Docker (`newgrp docker`)

Login `id` lacks `docker`; `getent group docker` includes `username01`. Maven/Testcontainers wrapped in `newgrp docker`. Engine **29.7.2**, Testcontainers Docker API **1.44** (parent surefire). Images: `postgres:16-alpine`, `redis:7-alpine`, `testcontainers/ryuk:0.12.0`.

## Command

```bash
newgrp docker <<'EOF'
cd backend
JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2 \
  ./mvnw -B -pl services/crm-service -am -Pintegration-tests verify --no-transfer-progress
EOF
```

## Result

| Module | Tests | Failures | Errors | Status |
|--------|------:|---------:|-------:|--------|
| common | 86 | 0 | 0 | SUCCESS |
| security | 13 | 0 | 0 | SUCCESS |
| **crm-service** | **200** | **0** | **0** | **SUCCESS** (ITs + units) |
| Jacoco check | line ≥ 0.80 | | | met |

`BUILD SUCCESS` 2026-09-13T06:00:15-05:00 (~2m 24s).

## TDD (red → green)

1. Boot 4 Jackson 3: `ObjectMapper` is `tools.jackson.databind.ObjectMapper` (listener + ITs).
2. Flyway 11 + `spring-boot-starter-flyway` (Boot 4 modularization).
3. JDBC URL: Testcontainers already has `?loggerLevel=OFF` — append `currentSchema=crm` with `&`.
4. `spring-cloud-azure` 5.8.0 on security lib: disable verifier + exclude `AzureTokenCredentialAutoConfiguration` (Boot 3 `TaskExecutorBuilder` missing).
5. `RedisMessageListenerContainer` needs a live Redis — shared `redis:7-alpine` Testcontainer on `BaseIntegrationTest`.

## Holds

- Dual-read `${APPROVALS_LEGACY_LOCAL_READ:true}` untouched
- No `docs/forge/00-orchestrator-status.md`
- No product `POST /validate` deletion
- Architect worktree not touched
