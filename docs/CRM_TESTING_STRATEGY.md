# CRM testing strategy (crm-service + frontend)

Honest split between **CI** (no Docker requirement beyond what the runner provides) and **full integration**.

## Backend module test inventory (Stack S1B-2)

Reactor modules under `backend/` and how tests run today. **Default `mvn verify`** (without `-Pintegration`) runs Surefire only and excludes classes named `*IntegrationTest`, `*IT`, or `*LifecycleTest` when the parent POM split is enabled (see [backend/README.md](../backend/README.md)).

| Module | Tests (approx.) | Default verify | Docker / Testcontainers | Redis in `application-test.yml` | Notes |
|--------|-----------------|----------------|-------------------------|----------------------------------|-------|
| `libs/common` | 8 | Unit | No | N/A | Shared DTOs/events; [libs verify command](../backend/libs/README.md) |
| `libs/security` | 1 | Unit | No | N/A | |
| `libs/rules-engine` | 4 | Unit | No | N/A | Drools unit tests |
| `services/crm-service` | 13 (6 IT) | Unit + Jacoco | **Yes** — Postgres TC | Configured (localhost; cache off) | Only module with `*IntegrationTest` / `LeadToDealLifecycleTest` |
| `services/pricing-service` | 3 | Unit + Jacoco | No (mocks) | localhost | `application-test.yml` in test + main |
| `services/product-service` | 1 | Unit | No | localhost | |
| `services/quote-service` | 0 | Compile only | — | localhost | Test resources present; add tests in feature stacks |
| `services/gateway-service` | 0 | Compile only | — | — | WebFlux gateway; add tests when routes stabilize |
| `services/pricebook-service` | 0 | Compile only | — | — | |
| `services/ai-service` | 57 | Unit (pilot agents) | No | — | **Compile risk** on full reactor; fix in Stack 3E, not 1B |
| `services/configuration-engine` | 1 | Unit | No | — | |
| `services/approval-routing` | 1 | Unit | No | — | |
| `services/document-generation` | 1 | Unit | No | — | |
| `services/contract-asset-management` | 1 | Unit | No | — | |

**Profiles:** `-Pintegration-tests` (alias `-Pintegration`) runs Failsafe ITs (Docker required for `crm-service`). `-Punit-tests` skips Jacoco and keeps ITs off by default.

### Reactor verification commands (S1B-4)

```bash
cd backend
# Bottom of stack — shared libs
.\mvnw.cmd -B verify --no-transfer-progress -pl libs/common,libs/security,libs/rules-engine -am
# Top of stack — unit path (exclude ai-service if compile fails)
.\mvnw.cmd -B verify -Punit-tests -Djacoco.skip=true --no-transfer-progress -pl '!services/ai-service'
# Full integration (Docker)
.\mvnw.cmd -B verify -Pintegration-tests --no-transfer-progress -pl services/crm-service -am
```

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
