# Forge Scout Plan — Gateway routes & CI contract hardening

| Field | Value |
|-------|-------|
| **Track** | Gateway routes and CI contract hardening (`gateway-ci`) |
| **Scout** | forge scout gateway-ci (Grok 4.5) |
| **Mode** | Plan / discovery only (no application source edits in this wave) |
| **Date** | 2026-08-30 |
| **Plan path** | `docs/forge/06-gateway-ci.md` |

---

## 1. Track name + scope

### In scope

- **Gateway route completeness** vs published contracts (`docs/backlog/CPQ_API_CONTRACT_MATRIX.md` §9, `docs/CRM_GATEWAY_ENV.md`, S2E CPQ aliases).
- **CI contract parity** across `docs/ci-contract.yaml`, `Jenkinsfile`, `.github/workflows/ci.yml`, and `scripts/ci/*`.
- **Flaky / missing tests** for `backend/services/gateway-service` and CI gates that should catch route/contract regressions.
- **Safe hardening PRs** — small, reversible, acceptance-testable changes that do not implement new domain services.

### Out of scope (this track)

- Implementing `erp-adapter-service` or real ERP backends.
- Adding `/api/v1/activities/**` (explicitly deferred until a real CRM controller + schema exist).
- Re-enabling GitHub Actions push/PR auto-triggers (Option B cutover).
- Wiring Jenkins AI assist into GHA, or pointing `LLM_ENDPOINT` at product `ai-service:8086`.
- Frontend CPQ UI work, CRM entity schema changes, Drools/OpenL rule content.

---

## 2. Current state (what exists)

### Gateway service

| Area | Status | Evidence |
|------|--------|----------|
| Module in reactor | Present | `backend/pom.xml` module `services/gateway-service` |
| Spring Cloud Gateway + Redis rate limit + Resilience4j CB | Configured | `application.yml` default-filters `RequestRateLimiter`; CB instances per aggregate |
| CRM paths | Present (leads/deals/contacts/organizations only) | `application.yml` route `crm-service`; matches `docs/CRM_GATEWAY_ENV.md` |
| Legacy `/api/v1/...` CPQ aggregates | Present | pricing, price-books, products, quotes, configurations, approvals, documents, subscriptions |
| CPQ public aliases `/api/v1/cpq/...` + RewritePath | Present (S2E-1) | `CpqGatewayRoutes.ROUTE_IDS` + yaml `cpq-*` routes |
| ERP stub → 501 | Present (S2A-7) | route `erp-stub` → `forward:/fallback/erp/unavailable`; `FallbackController.erpNotImplemented()` |
| Communication / AI routes | Present in **default** profile only | `communication-service`, `ai-service` with `lb://` defaults |
| Dev profile routes | Partial subset of default | Same CRM/CPQ/ERP set; **no** communication/ai |
| Security | Dev permit-all + CSRF; prod JWT + CSRF | `SecurityConfig.java` |
| Compose service | Partial env | `backend/docker-compose.yml` `gateway-service` sets CRM/PRICING/PRICEBOOK/PRODUCT/QUOTE + Redis only |
| Unit tests | **One** file, YAML string assertions | `CpqGatewayRoutesTest.java` (`@Tag("unit")`) |
| Route ID constants | Incomplete vs yaml | `CpqGatewayRoutes.java` — see §3 |

### CI contract & platforms

| Area | Status | Evidence |
|------|--------|----------|
| Machine contract | Present; `last_reviewed: "2026-05-20"` | `docs/ci-contract.yaml` |
| SSOT entrypoint | `scripts/ci/run-core.sh` | Stages: toolchain, shared-libraries, backend-unit, backend-integration, cpq-beta-python, frontend, security-audit |
| Jenkins primary | Full pipeline + GitHub status `jenkins/opencrm-cpq-ci` | `Jenkinsfile` |
| GHA | Manual `workflow_dispatch` only | `.github/workflows/ci.yml` |
| Drift verifier | Green today | `python .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py` → **OK** |
| Drift in CI | **Advisory only** when `AI_CI_ENABLED=true` | `Jenkinsfile` AI Assist → `drift_check` → `drift_sentinel.py`; **not** in `scripts/ci/toolchain-check.sh` |
| Backend unit scope | **crm-service only** | `scripts/ci/backend-unit.sh`: `-pl services/crm-service -am verify -Punit-tests` |
| Backend integration | Full reactor `mvn verify` (includes gateway compile + its unit test) | `scripts/ci/backend-integration.sh` |
| Toolchain | Java/mvn/node/npm/docker/python presence only | `scripts/ci/toolchain-check.sh` — no contract drift invoke |
| Staging smoke | PowerShell CRM reads only | `docs/scripts/staging-gateway-smoke.ps1` |

### Docs already encoding intent

- `docs/CRM_GATEWAY_ENV.md` — CRM URI parity; forbid activities until controller exists.
- `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` §9 — agent-critical gateway predicates.
- `docs/ci-jenkins-cutover.md` + `docs/adr/ADR-003-ci-llm-integration.md` — Jenkins primary; AI assist Jenkins-only.
- Graphite todo S2E-1 marked implemented (`docs/graphite-todos/S2E/...`).

---

## 3. Gaps with evidence (paths / commands)

### G1 — Gateway unit tests never run on the fast CI path

**Gap:** `backend-unit` only verifies `crm-service`. `CpqGatewayRoutesTest` is tagged `unit` but is never executed until the slower Docker-backed `backend-integration` full reactor verify.

**Evidence:**

```bash
# scripts/ci/backend-unit.sh
ci_mvn -B -U -pl services/crm-service -am verify -Punit-tests --no-transfer-progress

# Only gateway test:
find backend/services/gateway-service -name '*Test*.java'
# → .../CpqGatewayRoutesTest.java
```

**Impact:** Route/contract regressions can ship past Parallel — Fast checks and only fail (or be missed until) integration.

---

### G2 — Route ID constants / tests under-cover yaml routes

**Gap:** Default `application.yml` declares 20 route ids. Constants + tests cover only CPQ aliases + a subset of agent-contract legacy ids. Uncovered in constants:

| Route id in yaml | In `ROUTE_IDS` / `AGENT_CONTRACT_ROUTE_IDS`? |
|------------------|-----------------------------------------------|
| `pricebook-service` | No |
| `document-generation` | No |
| `contract-asset-management` | No |
| `communication-service` | No |
| `ai-service` | No |

**Evidence:**

```bash
# Extracted 2026-08-30 from application.yml vs CpqGatewayRoutes.java
# default missing from constants:
#   ai-service, communication-service, contract-asset-management,
#   document-generation, pricebook-service
```

Tests only `yaml.contains("id: " + routeId)` — no predicate/URI/rewrite assertions per route, no WebTestClient ERP 501 behavior test.

---

### G3 — Dev profile drops communication + AI routes

**Gap:** Activating `spring.profiles.active=dev` replaces the routes list without `communication-service` / `ai-service`. Local DX and “dev” deploys silently lose those predicates.

**Evidence:** `application.yml` lines ~299–413 (dev document) vs default routes ending at `ai-service` (~199–204). Diff of route ids: `dev missing vs default: ai-service, communication-service`.

---

### G4 — Compose gateway env incomplete for CPQ owners behind gateway

**Gap:** Compose sets only `CRM_SERVICE_URI`, `PRICING_SERVICE_URI`, `PRICEBOOK_SERVICE_URI`, `PRODUCT_SERVICE_URI`, `QUOTE_SERVICE_URI`. Missing for routes that exist in yaml:

- `CONFIGURATION_ENGINE_URI` (default `localhost:8088`)
- `APPROVAL_ROUTING_URI` (`8085`)
- `DOCUMENT_GENERATION_URI` (`8086`)
- `CONTRACT_ASSET_MANAGEMENT_URI` (`8087`)
- `AI_SERVICE_URI` / `COMMUNICATION_SERVICE_URI`

Inside the compose network, `localhost` defaults are wrong; those routes will mis-proxy even if downstream containers exist.

**Evidence:** `backend/docker-compose.yml` `gateway-service.environment` (~221–229). Compare to `application.yml` URI placeholders.

**Note:** There is **no** `backend/services/communication-service` module today — route is aspirational / `lb://` only.

---

### G5 — CI drift check is not a blocking SSOT gate

**Gap:** Canonical verifier exists and is green, but primary CI does not run it unless AI Assist is enabled (often `AI_CI_ENABLED=false`).

**Evidence:**

```bash
python .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py
# → CI contract drift check OK

rg -n "verify-drift|drift_check" scripts/ci Jenkinsfile
# scripts/ci: no hits
# Jenkinsfile: only inside stage('AI Assist') when AI_CI_ENABLED=true
```

Contract itself lists verify command under `drift_prevention.verify_commands`, but toolchain does not invoke it. `last_reviewed` is stale relative to ongoing cutover (`2026-05-20` vs scout date `2026-08-30`).

**Secondary gap:** Verifier hardcodes stage names; it does not parse `scripts_ci.stages` from YAML as SSOT. Adding a stage requires manual updates to verifier + platforms + yaml (documented, but easy to miss one).

---

### G6 — CRM env doc vs CPQ/gateway operational surface

**Gap:** `docs/CRM_GATEWAY_ENV.md` covers CRM only. Operators lack a single env table for all `*_SERVICE_URI` vars used by gateway (CPQ aggregates, AI, communication). Staging smoke is CRM-only and PowerShell-only (`docs/scripts/staging-gateway-smoke.ps1`).

---

### G7 — Soft / latent reliability risks (not necessarily broken today)

| Risk | Evidence | Notes |
|------|----------|-------|
| Global `RequestRateLimiter` requires Redis | `application.yml` default-filters + `spring.data.redis` | Compose correctly depends on Redis; bare local gateway without Redis is fragile |
| Circuit-breaker fallbacks are `@GetMapping` only | `FallbackController` | Forward fallback URIs are typically GET; confirm under POST failure (test gap) |
| `communication-service` has no backend module | `ls backend/services` — no `communication-service` | Route + CB exist; calls will fail until service exists or route is stubbed like ERP |
| AI route `lb://ai-service` without discovery in compose | yaml default; compose omits `AI_SERVICE_URI` | Same class of misconfig as G4 |
| Fallback ERP path relies on WebFlux annotated controller | `forward:/fallback/erp/unavailable` + `@RequestMapping("/erp/**")` | Covered only by YAML string test, not HTTP assertion |

---

### G8 — Explicit non-gaps (do not “fix”)

| Item | Status |
|------|--------|
| CRM activities path | Correctly **absent** per `CRM_GATEWAY_ENV.md` |
| GHA auto push/PR | Correctly **disabled** (Option B) |
| AI assist on GHA | Correctly **absent** (ADR-003) |
| Agent-critical CPQ prefixes in default yaml | Present per matrix §9 (configuration / configurations / pricing / rules / quotes / CRM / erp) |
| Current `verify-drift.py` vs Jenkins/GHA | **No drift** as of scout run |

---

## 4. Blast radius / risk notes

> GitNexus MCP tools were **not available** in this scout session. Blast radius below is from static dependency / CI graph analysis. Before implementing symbol edits, re-run `gitnexus_impact` / `gitnexus_detect_changes` per repo rules.

| Change surface | Upstream callers / dependents | Risk |
|----------------|-------------------------------|------|
| `scripts/ci/backend-unit.sh` module list | Jenkins Parallel — Fast checks; GHA `backend-unit` job; developer local fast loop | **Medium** — longer unit stage; must stay Docker-free |
| `CpqGatewayRoutes` + yaml route ids | Gateway tests; any future codegen/docs that import constants; agent clients via path stability | **Low–Medium** — path changes are HIGH; constant/test-only is Low |
| `application.yml` routes / filters | All FE (`4200→8080`), agents, compose, staging smoke, cutover checklist | **HIGH** if predicates/URIs change incorrectly |
| Dev profile route list | Local `dev` profile users only | **Medium** for DX; Low for prod if default profile used in deploy |
| `docker-compose.yml` gateway env | Local full-stack only | **Low** |
| `toolchain-check.sh` + drift script | Jenkins Toolchain (GHA has no toolchain job) | **Low** if verifier stays green; fails closed on real drift |
| `docs/ci-contract.yaml` | Drift sentinel + human ops | **Low** if platforms updated in same PR |
| Rate-limiter / Redis optionalization | Every gateway request path | **Medium–High** — avoid unless carefully profiled |

**HIGH / CRITICAL warning threshold:** Any PR that removes or rewrites agent-contract path predicates (`/api/v1/configuration/**`, `/api/v1/pricing/**`, `/api/v1/quotes/**`, CRM paths, `/api/v1/erp/**`) without matrix + ADR update must be rejected or escalated.

---

## 5. Proposed PRs (title, files, acceptance, risk)

### PR-A — `ci: run gateway-service unit tests on fast backend-unit path`

| | |
|--|--|
| **Intent** | Close G1 — fail fast on route-contract unit regressions |
| **Files** | `scripts/ci/backend-unit.sh`; optionally one-line note in `docs/ci-contract.yaml` (`backend-unit` notes / `last_reviewed`); `docs/ci-jenkins-cutover.md` only if behavior text needs update |
| **Approach** | Extend Maven `-pl` to include `services/gateway-service` (e.g. `-pl services/crm-service,services/gateway-service -am`) keeping `-Punit-tests` and Docker-free |
| **Acceptance** | (1) `bash scripts/ci/run-core.sh backend-unit` executes `CpqGatewayRoutesTest`; (2) no Testcontainers/Docker required; (3) `python .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py` still OK; (4) Jenkins/GHA still call same `run-core.sh` arg |
| **Risk** | **Low–Medium** (CI time ↑ slightly; wrong `-pl` could pull unwanted modules — verify reactor list) |
| **Depends on** | None |

---

### PR-B — `test(gateway): expand route-id contract coverage + ERP 501 assertion`

| | |
|--|--|
| **Intent** | Close G2 (and partially G7) without changing runtime routes |
| **Files** | `CpqGatewayRoutes.java`; `CpqGatewayRoutesTest.java`; optionally new thin WebFlux test with mocked env / disabled rate limiter if needed |
| **Approach** | Add constants for `pricebook-service`, `document-generation`, `contract-asset-management` (and decide whether communication/ai are “published” or “experimental”); assert every default-profile route id; add focused test that ERP stub configuration remains 501-oriented (`erp-stub` + forward fallback). Prefer filesystem/YAML contract tests first; add `WebTestClient` only if Redis/security can be stubbed cleanly under `@Tag("unit")` |
| **Acceptance** | (1) Removing any covered route id from yaml fails unit test; (2) agent-contract prefixes from matrix §9 still asserted; (3) tests remain Docker-free; (4) green under PR-A command |
| **Risk** | **Low** (test + constants); **Medium** if WebTestClient forces Spring context + Redis |
| **Depends on** | Best after or with PR-A so CI catches failures on fast path |

---

### PR-C — `fix(gateway): align dev-profile routes with default CPQ/CRM/ERP set; document AI/comm`

| | |
|--|--|
| **Intent** | Close G3; clarify G4/G7 for communication/ai |
| **Files** | `application.yml` (dev document); `docs/CRM_GATEWAY_ENV.md` (expand env table) **or** new short `docs/GATEWAY_SERVICE_ENV.md` linked from CRM doc; optionally `backend/docker-compose.yml` gateway env for missing `*_URI`s that have real services |
| **Approach** | (1) Add missing CPQ owner URIs to compose for services that exist (configuration-engine, approval-routing, document-generation, contract-asset-management, ai-service). (2) Either add communication/ai to **dev** profile with localhost/`AI_SERVICE_URI` defaults, **or** explicitly document that they are default-profile-only and remove surprise. Prefer documenting + compose URI for `ai-service`; keep communication as stub/501 **only if** product agrees — otherwise leave route but document “no module yet”. **Do not** invent a communication microservice in this PR. |
| **Acceptance** | (1) Dev profile retains all CRM + CPQ alias + ERP routes; (2) compose gateway can resolve non-localhost URIs for configured CPQ owners; (3) docs list every `*_SERVICE_URI` / `*_URI` key; (4) no activities path added |
| **Risk** | **Medium** (yaml route list edits — review carefully); compose-only/docs-only slices are **Low** |
| **Depends on** | None for docs/compose URI fill; coordinate with AI track if changing `ai-service` URI defaults |

---

### PR-D — `ci: run contract drift verify in toolchain (blocking, non-AI)`

| | |
|--|--|
| **Intent** | Close G5 — make drift prevention a first-class SSOT gate on Jenkins |
| **Files** | `scripts/ci/toolchain-check.sh`; optionally mirror a one-liner in GHA `shared-libraries` or `backend-unit` so manual fallback also checks; bump `docs/ci-contract.yaml` `last_reviewed`; keep AI `drift_sentinel` as advisory report (no duplication conflict) |
| **Approach** | After toolchain version prints: `python3 .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py` (fail on non-zero). Do **not** add ai-assist to GHA. |
| **Acceptance** | (1) `bash scripts/ci/run-core.sh toolchain` fails if GHA needs graph drifts; (2) verify-drift still OK on main; (3) ADR-003 boundaries unchanged; (4) contract `last_reviewed` updated in same PR |
| **Risk** | **Low** |
| **Depends on** | None; parallel with PR-A |

---

### PR-E — `chore(gateway): add bash staging smoke for CRM + agent-critical prefixes` (optional)

| | |
|--|--|
| **Intent** | Close G6 operability gap; complement `.ps1` for Linux CI agents / cutover |
| **Files** | `docs/scripts/staging-gateway-smoke.sh` (new); link from `CRM_CUTOVER_CHECKLIST.md` / `CRM_GATEWAY_ENV.md` |
| **Approach** | curl-based GETs for CRM list paths + optional dry checks that `/api/v1/erp/costs/bulk` returns 501 (not 404) when gateway is up; skip auth-heavy paths unless token env provided |
| **Acceptance** | Script exits 0 against local compose with healthies; documents required env (`GATEWAY_BASE`, optional `TOKEN`); does not require Windows |
| **Risk** | **Low** |
| **Depends on** | PR-C compose URI completeness for non-CRM paths if those are asserted |

---

### PR ordering summary

| ID | Title (short) | Risk | Priority |
|----|---------------|------|----------|
| PR-A | Gateway on backend-unit fast path | Low–Med | P0 |
| PR-D | Drift verify in toolchain | Low | P0 |
| PR-B | Expand gateway route contract tests | Low | P1 |
| PR-C | Dev profile + compose/docs URI alignment | Med | P1 |
| PR-E | Bash staging smoke | Low | P2 |

---

## 6. Parallelization / dependencies

```text
Batch 0 (parallel, no code conflict expected):
  PR-A  (scripts/ci/backend-unit.sh)
  PR-D  (scripts/ci/toolchain-check.sh + ci-contract last_reviewed)

Batch 1 (after or parallel if separate worktrees; touch gateway):
  PR-B  (gateway test/constants)     ── prefers PR-A merged for CI signal
  PR-C  (yaml/compose/docs)          ── review carefully vs PR-B constants

Batch 2:
  PR-E  (docs/scripts smoke)         ── after PR-C if asserting non-CRM routes
```

**Cross-track dependencies**

- **CRM cutover track:** do not add activities routes; keep `CRM_SERVICE_URI` semantics.
- **CPQ contracts track:** matrix §9 is source of truth for agent-critical paths; gateway PRs must not invent alternate agent paths.
- **AI / Jenkins AI assist:** PR-D must remain non-LLM; do not conflate with `ai-service` product routes.
- **Frontend track:** assumes gateway `:8080`; coordinate only if CORS/allowed-origins change (not proposed here).

**CI edit rule:** Any change to `Jenkinsfile` / `.github/workflows/ci.yml` / `scripts/ci/*` / `docs/ci-contract.yaml` must update the contract in the **same** PR and pass `verify-drift.py` (use `ci-contract-drift-check` / `ci-stage-ssot-edit` skills).

---

## 7. Explicit DO NOT TOUCH list

1. **Do not** re-enable GHA `push` / `pull_request` triggers without amending `docs/ci-jenkins-cutover.md` and branch protection.
2. **Do not** add `ai-assist` / `run-ai-stage` to `.github/workflows/ci.yml` (ADR-003).
3. **Do not** set `LLM_ENDPOINT` / AI CI to `ai-service:8086` (product AgentRouter ≠ chat API).
4. **Do not** add gateway route `/api/v1/activities/**` until a real Spring controller + schema exist (`CRM_GATEWAY_ENV.md`).
5. **Do not** embed domain / pricing / approval business logic in `gateway-service` (DDD: rewrite + delegate only).
6. **Do not** remove or rename agent-contract external paths without matrix + ADR (`CPQ_API_CONTRACT_MATRIX.md`).
7. **Do not** implement full `erp-adapter-service` under this track (keep 501 stub until a dedicated PR).
8. **Do not** invent / scaffold `communication-service` microservice as a drive-by (document or stub only).
9. **Do not** change Jenkins GitHub status context away from `jenkins/opencrm-cpq-ci`.
10. **Do not** make `security-audit` blocking without an explicit contract + platform change.
11. **Do not** weaken CSRF / JWT production security filter chain for test convenience without a dedicated `test` profile.
12. **Do not** modify Frappe `cpq_beta/` application code for gateway/CI hardening.

---

## 8. Suggested verification commands (for implementers)

```bash
# Contract parity
python .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py

# Fast path (after PR-A)
bash scripts/ci/run-core.sh backend-unit

# Gateway module only
cd backend && ./mvnw -pl services/gateway-service -am test -Punit-tests

# Toolchain (after PR-D)
bash scripts/ci/run-core.sh toolchain

# Route id inventory
rg -n "^\s+- id:" backend/services/gateway-service/src/main/resources/application.yml
```

---

## 9. Scout conclusions

1. **Route surface for CRM + CPQ aliases + ERP stub is largely complete** relative to matrix §9 and S2E-1; biggest holes are **test/CI enforcement**, **dev/compose env parity**, and **undocumented/uncovered** pricebook/document/contract/communication/ai route ids.
2. **CI platforms are not drifted** right now; hardening should make drift **blocking on every Jenkins build** and pull **gateway unit tests onto the fast path**.
3. Safest first wave: **PR-A + PR-D** in parallel, then **PR-B + PR-C**, then optional **PR-E**.
