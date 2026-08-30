# Gateway / env parity (CRM)

## CORS / CSRF allowed origins

Gateway browser CORS (`spring.cloud.gateway.globalcors`) and CSRF Origin/Referer checks (`security.csrf.allowed-origins` / `CsrfProtectionFilter`) share the same **explicit** allow-list. Subdomain wildcards such as `https://*.azurewebsites.net` are **not** supported; `CsrfProtectionFilter` rejects any allow-list entry containing `*`.

| Variable | Role | Default |
|----------|------|---------|
| `FRONTEND_URL` | Primary SPA origin (scheme + host + port) | `http://localhost:4200` |
| `CORS_ALLOWED_ORIGINS` | Optional comma-separated **full** override of the allow-list | unset → uses `FRONTEND_URL` |

Resolution in `gateway-service` `application.yml`:

```text
CORS_ALLOWED_ORIGINS → else FRONTEND_URL → else http://localhost:4200
```

Examples:

```bash
# Local Angular CRM (default — no env required)
# FRONTEND_URL=http://localhost:4200

# Single deployed SPA
export FRONTEND_URL=https://crm-portal.azurewebsites.net

# Multiple explicit origins (include FRONTEND_URL in the list if both are needed)
export CORS_ALLOWED_ORIGINS=https://crm-portal.azurewebsites.net,https://admin.example.com
```

| Location | Notes |
|----------|--------|
| `backend/services/gateway-service/src/main/resources/application.yml` | `allowedOrigins` + `security.csrf.allowed-origins` use the placeholder above |
| `backend/docker-compose.yml` | Sets `FRONTEND_URL: http://localhost:4200` on `gateway-service` |
| Staging / Azure | Set `FRONTEND_URL` (or `CORS_ALLOWED_ORIGINS`) to the exact browser origin(s); never `*.` patterns |

**Rule:** Prefer one explicit origin via `FRONTEND_URL`. Use `CORS_ALLOWED_ORIGINS` only when several origins are required; when set, it **replaces** the `FRONTEND_URL` default (list every allowed origin explicitly).

## `CRM_SERVICE_URI`

| Location | Value / pattern |
|----------|-----------------|
| `backend/docker-compose.yml` | `CRM_SERVICE_URI: http://crm-service:8081` (service DNS inside compose network) |
| `backend/services/gateway-service/src/main/resources/application.yml` | `${CRM_SERVICE_URI:http://localhost:8081}` |
| `microsite-prompt.xml` | `${CRM_SERVICE_URI:http://localhost:8081}` |
| Deploy / Azure samples | Set `CRM_SERVICE_URI` to the **crm-service** base URL for that environment (not the gateway). |

**Rule:** Gateway listens on **8080** (default); `crm-service` is typically **8081** behind `CRM_SERVICE_URI`.

## Gateway path predicates (CRM)

From `gateway-service` `application.yml`, CRM routes **must** match only what `crm-service` implements:

```yaml
Path=/api/v1/leads/**,/api/v1/deals/**,/api/v1/contacts/**,/api/v1/organizations/**
```

- **No** `/api/v1/activities/**` until a real Spring controller + schema exist (Phase 2 / Frappe).

## Observability (staging / production)

Wire dashboards or log queries for:

1. **Gateway 5xx** (and latency) for routes under `/api/v1/leads`, `/api/v1/deals`, `/api/v1/contacts`, `/api/v1/organizations`.
2. **crm-service** application errors (deal/lead pipelines, validation).
3. **Redis listener:** logs from `QuoteAcceptedEventListener` — failures deserializing or applying `QuoteAcceptedCrmMessage`, and **idempotency skips** (duplicate delivery after successful key set). INFO: `Duplicate quote acceptance for key …, skipping`.

**Kafka:** `acceptQuote` still publishes `QuoteAcceptedEvent` for document-generation / contract-asset — **separate** from Redis `QuoteAcceptedCrmMessage`; do not conflate in dashboards without labeling the channel.

## Staging smoke scripts

| Script | Purpose |
|--------|---------|
| [scripts/staging-gateway-smoke.sh](./scripts/staging-gateway-smoke.sh) | Bash CRM GET smoke via gateway (`GATEWAY_BASE`, optional `TOKEN`, `CHECK_ERP=1`) |
| [scripts/staging-gateway-smoke.ps1](./scripts/staging-gateway-smoke.ps1) | PowerShell twin (`-GatewayBase`, `-Token`, `-CheckErp`) |
| [scripts/staging-quote-accept-idempotency.md](./scripts/staging-quote-accept-idempotency.md) | Quote accept → deal WON + duplicate Redis publish runbook |
| [scripts/staging-quote-accept-idempotency.sh](./scripts/staging-quote-accept-idempotency.sh) | Helper for accept + `PUBLISH quote:accepted` idempotency check |

CRM FE and cutover smokes must hit the **gateway** (`:8080`), not crm-service direct (`:8081`).
