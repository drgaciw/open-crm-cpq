# Gateway / env parity (CRM)

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
3. **Redis listener:** logs from `QuoteAcceptedEventListener` — failures deserializing or applying `QuoteAcceptedCrmMessage`, and **idempotency skips** (duplicate delivery after successful key set).

**Kafka:** `acceptQuote` still publishes `QuoteAcceptedEvent` for document-generation / contract-asset — **separate** from Redis `QuoteAcceptedCrmMessage`; do not conflate in dashboards without labeling the channel.
