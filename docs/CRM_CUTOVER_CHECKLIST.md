# CRM service big-bang cutover checklist

Use this list before and after promoting `crm-service` as the only CRM API behind the gateway (Spring stack; Frappe `cpq_beta` is **out of scope** for this cutover — see **Phase 2** below).

**Related:** [CRM_API_PARITY.md](./CRM_API_PARITY.md) (API ↔ FE table), [CRM_GATEWAY_ENV.md](./CRM_GATEWAY_ENV.md), [CRM_TESTING_STRATEGY.md](./CRM_TESTING_STRATEGY.md).

---

## Pre-cutover

- [ ] Verify CRM gateway paths for leads, deals, contacts, organizations, playbooks, guided sessions and activities resolve to their real CRM controllers. See [CRM_API_PARITY.md](./CRM_API_PARITY.md) for the activity timeline contract.
- [ ] `CRM_SERVICE_URI` in gateway / compose / deployment targets the intended `crm-service` instance ([CRM_GATEWAY_ENV.md](./CRM_GATEWAY_ENV.md)).
- [ ] Database: `crm` schema migrations applied; **backup** taken (owner + time boxed).
- [ ] Redis: available to `crm-service` and `quote-service` (pub/sub channel `quote:accepted`).
- [ ] Contract: `quote-service` publishes `com.opencrm.common.event.dto.QuoteAcceptedCrmMessage` on `quote:accepted` (see `QuoteEventPublisher`). **No stale publishers** with old payload type names in any environment.
- [ ] **Freeze window:** agree CRM DB migrations + **image tags** (gateway, crm-service, quote-service) for cutover; avoid drifting midship.
- [ ] Confirm search/list envelopes are `ApiResponse` + `data: T[]` + `pagination` (`PageInfo`) for leads/deals/contacts/organizations (Batch 0 / B0-1); FE reads interceptor `.content`.

---

## Verification (staging)

- [ ] **Parity:** Review [CRM_API_PARITY.md](./CRM_API_PARITY.md); resolve API-without-FE and FE-without-API gaps as intentional or bugs.
- [ ] Health: `GET` crm-service `/actuator/health` (and gateway if exposed).
- [ ] CRUD smoke: organizations, contacts, leads, deals via **gateway** paths used by `frontend/libs/crm` (**4200 → 8080**, not direct **8081**).
- [ ] Pipeline/dashboard: `/api/v1/deals/pipeline` and dashboard loads succeed.
- [ ] **Quote acceptance E2E:** Accept a sent quote with `dealExternalId` set to the deal’s **UUID** string; confirm deal moves to **WON** and amounts/currency update; publish duplicate Redis messages and confirm **idempotency** (`crm:quote-accepted:processed:*` skips re-application). Runbook: [scripts/staging-quote-accept-idempotency.md](./scripts/staging-quote-accept-idempotency.md) (helper: `staging-quote-accept-idempotency.sh`).
- [ ] Automated smoke (optional CRM reads): bash `GATEWAY_BASE='https://…' ./docs/scripts/staging-gateway-smoke.sh` or PowerShell `docs/scripts/staging-gateway-smoke.ps1 -GatewayBase 'https://…'`.

---

## Observability (staging → production, first 24–48h)

- [ ] Gateway 5xx / latency for `/api/v1/{leads,deals,contacts,organizations}` ([CRM_GATEWAY_ENV.md](./CRM_GATEWAY_ENV.md)).
- [ ] crm-service error logs (CRM domain).
- [ ] Redis listener logs: `QuoteAcceptedCrmMessage` failures vs **idempotency skip** (duplicate).
- [ ] Kafka path unchanged: `QuoteAcceptedEvent` for document/contract consumers — **not** merged with Redis DTO.

---

## Rollback

- [ ] **Owner** assigned for rollback decision.
- [ ] Revert gateway route config and redeploy previous gateway image/config.
- [ ] Revert `crm-service` (and **quote-service** if contract tied) to last known good tags.
- [ ] If bad data was written, restore DB from **pre-cutover** backup (coordinate with DBA).

---

## Post-cutover window

- [ ] Watch metrics **24–48h**; keep rollback steps rehearsed.
- [ ] Confirm business KPIs (pipeline, won value) sane vs pre-cutover baseline.

---

## Phase 2 (Frappe / `cpq_beta`) — when ready

- [ ] Inventory `cpq_beta` CRM APIs and DocTypes vs Spring `crm-service` — expect a **large capability gap** list.
- [ ] **Do not** big-bang Frappe: pick **one vertical** (e.g. read-only activities feed or notes), design migration + `crm-service` API, own plan.
- [ ] **Activities API:** verify the CRM timeline controller, V16 migration, gateway predicate and Angular client together. Legacy `cpq_beta/crm/api/activities.py` is an inventory reference; no legacy data migration is implied.
- [ ] Verify manual email/meeting references retain `source=MANUAL`; provider ingestion and bodies remain a separate DRG-521 delivery.

---

## Commands reference (Windows + Nx + Maven)

Repo root: `open-crm-cpq`.

**Staging gateway CRM smoke (reads via :8080):**

```bash
# Linux / macOS / CI agents
GATEWAY_BASE='http://localhost:8080' ./docs/scripts/staging-gateway-smoke.sh
# Optional ERP stub (expect 501, not 404):
CHECK_ERP=1 GATEWAY_BASE='http://localhost:8080' ./docs/scripts/staging-gateway-smoke.sh
```

```powershell
# Windows
./docs/scripts/staging-gateway-smoke.ps1 -GatewayBase 'http://localhost:8080'
./docs/scripts/staging-gateway-smoke.ps1 -GatewayBase 'http://localhost:8080' -CheckErp
```

**Quote accept → WON + duplicate idempotency:** [scripts/staging-quote-accept-idempotency.md](./scripts/staging-quote-accept-idempotency.md)

**Frontend tests (after `npm ci` in `frontend`):**

```powershell
cd frontend
npx nx run-many --target=test --projects="crm-deals,crm-leads,crm-contacts,crm-organizations,crm-dashboard,shared-models" --parallel=3
```

**Backend crm-service:**

```powershell
cd backend
mvn -pl services/crm-service verify
```

**Grep helpers:**

```powershell
# CRM_SERVICE_URI usages
Select-String -Path **\* -Pattern 'CRM_SERVICE_URI' -Recurse -Include *.yml,*.yaml,*.xml,*.md

# Gateway CRM predicates
Select-String -Path backend\services\gateway-service\src\main\resources\application.yml -Pattern 'leads|deals|contacts|organizations'
```

**OpenAPI / Swagger:** `crm-service` — `/api-docs` or Swagger UI (port **8081** direct, or via gateway **8080** same paths).

**CI:** GitHub Actions — `.github/workflows/ci.yml` (`backend` job runs `mvn verify` on Ubuntu with Docker available for Testcontainers). Azure DevOps would mirror: JDK 21, Maven, Docker service for integration tests.

---

## Next 1–2 days (priority)

1. Keep [CRM_API_PARITY.md](./CRM_API_PARITY.md) updated when routes change.
2. Staging E2E: quote accept → deal WON + duplicate publish idempotency.
3. CI: confirm `mvn verify` passes with Docker; if not, tag integration tests + nightly job ([CRM_TESTING_STRATEGY.md](./CRM_TESTING_STRATEGY.md)).
4. Share `QuoteAcceptedCrmMessage` field contract (JavaDoc + this checklist) with teams touching CPQ.
5. Schedule production cutover: **rollback owner**, **DB backup** slot, image tags frozen.
