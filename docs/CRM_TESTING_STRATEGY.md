# CRM testing strategy (crm-service + frontend)

Honest split between **CI** (no Docker requirement beyond what the runner provides) and **full integration**.

## Backend (`crm-service`)

### Unit / pure tests (CI-friendly)

- Example: `DealServiceTest`, `LeadServiceTest`, `QuoteAcceptedEventListenerTest`, `QuoteAcceptedDealProcessorTest`, `QuoteAcceptedIdempotencyTest`.
- Run **without** Testcontainers Postgres when they mock repositories/services.
- **Must stay in default `mvn test` / `mvn verify`** path so CI stays green.

### Integration tests (`BaseIntegrationTest` + Testcontainers)

- Classes under `src/test/java/.../*IntegrationTest.java` and `LeadToDealLifecycleTest` extend `BaseIntegrationTest`, which starts a **PostgreSQL Testcontainer**.
- **Requires Docker.** GitHub-hosted `ubuntu-latest` includes Docker; `mvn verify` from `backend/` should run these today.
- If a pipeline **disables** Docker or uses a runner without it: split tests with JUnit tags (`@Tag("integration")`) and exclude them from PR builds, or add a dedicated job with Docker + `mvn verify -Pintegration` (after you introduce the profile).

### Jacoco (bundle line coverage ≥ 80%)

- Configured in `backend/services/crm-service/pom.xml` (`jacoco-maven-plugin`, rule `LINE` `COVEREDRATIO` minimum **0.80**).
- After adding code, either:
  - Run locally with Docker: `cd backend && mvn -pl services/crm-service verify`, or
  - Add enough **unit** tests so the bundle stays green without relying only on integration tests.

### Commands (repo root `open-crm-cpq`)

```bash
cd backend
mvn -pl services/crm-service test              # unit-focused
mvn -pl services/crm-service verify            # tests + jacoco check (needs Docker for ITs)
```

Windows PowerShell is the same paths; use `mvn.cmd` if needed.

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
  - **backend:** `mvn -B verify` with `SPRING_PROFILES_ACTIVE=test` — sufficient for Testcontainers on default Ubuntu runners.
  - **frontend:** `nx run-many` build/lint + **Test (CRM + shared models)** for `crm-deals`, `crm-leads`, `crm-contacts`, `crm-organizations`, `crm-dashboard`, `shared-models`.

If integration tests ever fail in CI for Docker reasons, prefer **tagging** integration tests and a **nightly** full verify job rather than dropping coverage.
