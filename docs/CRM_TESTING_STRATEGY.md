# CRM testing strategy (crm-service + frontend)

Honest split between **CI** (no Docker requirement beyond what the runner provides) and **full integration**.

## Backend (`crm-service`)

### Unit / pure tests (CI-friendly)

- Example: `DealServiceTest`, `LeadServiceTest`, `QuoteAcceptedEventListenerTest`, `QuoteAcceptedDealProcessorTest`, `QuoteAcceptedIdempotencyTest`.
- Run **without** Testcontainers Postgres when they mock repositories/services.
- Use the `unit-tests` Maven profile for fast PR checks that must not require Docker.

### Integration tests (`BaseIntegrationTest` + Testcontainers)

- Classes under `src/test/java/.../*IntegrationTest.java` and `LeadToDealLifecycleTest` extend `BaseIntegrationTest`, which starts a **PostgreSQL Testcontainer**.
- **Requires Docker.** GitHub-hosted `ubuntu-latest` includes Docker; `mvn verify` from `backend/` should run these on Docker-backed runners.
- Docker-backed tests are tagged with `@Tag("integration")` on the concrete test classes and `BaseIntegrationTest`. The `integration-tests` Maven profile is the explicit full Docker-backed verification profile; use `-Dgroups=integration` only when you intentionally want a focused integration-tag run.

### Jacoco (bundle line coverage ≥ 80%)

- Configured in `backend/services/crm-service/pom.xml` (`jacoco-maven-plugin`, rule `LINE` `COVEREDRATIO` minimum **0.80**).
- After adding code, either:
  - Run locally with Docker: `cd backend && mvn -pl services/crm-service verify`, or
  - Add enough **unit** tests so the bundle stays green without relying only on integration tests.

### Commands (repo root `open-crm-cpq`)

```bash
cd backend
mvn -pl services/crm-service -am -Punit-tests verify       # no Docker; skips integration-tagged tests and Jacoco gate
mvn -pl services/crm-service -am -Pintegration-tests verify # full verification with Jacoco; Docker required
mvn -pl services/crm-service -am -Dgroups=integration test  # focused Testcontainers tag run; Docker required
```

Windows PowerShell is the same paths; use `mvnw.cmd` from `backend/` if needed.

### Windows Docker Desktop readiness

Before running `-Pintegration-tests` on Windows:

```powershell
docker version
docker info --format '{{.ServerVersion}}'
docker context ls
Test-Path '\\.\pipe\dockerDesktopLinuxEngine'
```

If Docker CLI works but Testcontainers reports `Could not find a valid Docker environment`, start Docker Desktop, confirm it is using Linux containers, clear stale `DOCKER_HOST` values, and only then set an explicit host if auto-detection still fails:

```powershell
Remove-Item Env:\DOCKER_HOST -ErrorAction SilentlyContinue
$env:DOCKER_HOST = "npipe:////./pipe/dockerDesktopLinuxEngine"
```

or `%USERPROFILE%\.testcontainers.properties`:

```properties
docker.host=npipe:////./pipe/dockerDesktopLinuxEngine
```

## Frontend (`frontend/libs/crm`, `frontend/libs/shared/models`)

### Unit tests (Vitest via Nx)

```bash
cd frontend
npm ci --legacy-peer-deps
npx nx run-many --target=test --projects="crm-deals,crm-leads,crm-contacts,crm-organizations,crm-dashboard,shared-models" --parallel=3
```

Use **only** `package-lock.json` with npm in `frontend/`. A stray `pnpm-lock.yaml` (without pnpm) confuses the Nx lockfile plugin and fails the project graph.

### E2E / smoke

- No Playwright suite was present in-repo at authoring time; **manual smoke** through **gateway** (4200 → 8080) is the cutover gate — see [CRM_CUTOVER_CHECKLIST.md](./CRM_CUTOVER_CHECKLIST.md).

## CI alignment (GitHub Actions)

- Workflow: `.github/workflows/ci.yml`
  - **backend-unit:** `mvn -B -U verify -Punit-tests` over the whole reactor with `SPRING_PROFILES_ACTIVE=test` — fast Docker-free feedback for every lib and service (`integration` and `docker` tags excluded). The stage fails if Testcontainers starts a container.
  - **backend-integration:** Docker readiness check, then full `mvn -B -U verify` with `SPRING_PROFILES_ACTIVE=test` — preserves coverage gates and Testcontainers on default Ubuntu runners.
  - **frontend:** `nx run-many` build/lint + **Test (CRM + shared models)** for `crm-deals`, `crm-leads`, `crm-contacts`, `crm-organizations`, `crm-dashboard`, `shared-models`.

If integration tests ever fail in CI for Docker reasons, prefer **tagging** integration tests and a **nightly** full verify job rather than dropping coverage.
