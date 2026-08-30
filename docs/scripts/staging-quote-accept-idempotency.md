# Staging: quote accept → deal WON + duplicate idempotency

Runbook for cutover checklist item **Quote acceptance E2E**  
([CRM_CUTOVER_CHECKLIST.md](../CRM_CUTOVER_CHECKLIST.md)). Companion helper:  
[`staging-quote-accept-idempotency.sh`](./staging-quote-accept-idempotency.sh).

**Related:** Redis channel `quote:accepted`, DTO `QuoteAcceptedCrmMessage`, key prefix  
`crm:quote-accepted:processed:*` (`QuoteAcceptedIdempotency`), forge track  
[03-quote-deal-e2e.md](../forge/03-quote-deal-e2e.md).

---

## Prerequisites

| Need | Notes |
|------|--------|
| Gateway | `:8080` (or staging URL); CRM + quote routed through it |
| Services | `crm-service`, `quote-service`, Redis (shared pub/sub) |
| Auth | `TOKEN` Bearer if staging gateway requires it |
| Deal | Existing deal with known **UUID** (`Deal.uuid`) — used as `dealExternalId` |
| Quote | Lifecycle at least `SENT`, with `dealExternalId` = that UUID |
| Redis CLI | Access to the same Redis used by crm-service (`redis-cli` or `docker compose exec redis`) |

CRM gateway read smoke (separate):

```bash
# Linux/macOS
GATEWAY_BASE='http://localhost:8080' ./docs/scripts/staging-gateway-smoke.sh

# Windows PowerShell
./docs/scripts/staging-gateway-smoke.ps1 -GatewayBase 'http://localhost:8080'
```

---

## Happy path (accept → WON)

1. **Note deal UUID** (gateway, not `:8081`):

```bash
GATEWAY_BASE="${GATEWAY_BASE:-http://localhost:8080}"
curl -sS -H "Accept: application/json" ${TOKEN:+-H "Authorization: Bearer $TOKEN"} \
  "$GATEWAY_BASE/api/v1/deals/{id}" | jq '.data.uuid, .data.stage, .data.amount, .data.currency'
```

2. **Ensure quote** has `dealExternalId` = that UUID; approve → send until status is accept-eligible (`SENT`).

3. **Accept** via gateway:

```bash
curl -sS -X POST -H "Accept: application/json" ${TOKEN:+-H "Authorization: Bearer $TOKEN"} \
  "$GATEWAY_BASE/api/v1/quotes/{quoteId}/accept" | jq .
```

4. **Assert deal WON** (poll a few seconds — Redis pub/sub is async):

```bash
curl -sS -H "Accept: application/json" ${TOKEN:+-H "Authorization: Bearer $TOKEN"} \
  "$GATEWAY_BASE/api/v1/deals/{id}" | jq '{stage: .data.stage, amount: .data.amount, currency: .data.currency, wonAt: .data.wonAt, uuid: .data.uuid}'
```

Expect: `stage` = `WON`, amount/currency from quote totals, `wonAt` set.

5. **Confirm Redis processed key** (prefer `quoteUuid`):

```bash
# Compose example:
docker compose -f backend/docker-compose.yml exec -T redis \
  redis-cli KEYS 'crm:quote-accepted:processed:*'

# Key shape: crm:quote-accepted:processed:<quoteUuid>
# Fallback if quoteUuid blank: crm:quote-accepted:processed:<dealExternalId>:<quoteNumber>
```

---

## Duplicate idempotency check (required for cutover)

Goal: second delivery of the **same** `QuoteAcceptedCrmMessage` must **not** re-apply amount/currency/WON mutation.

### A — Preferred: republish identical Redis payload

1. Capture (or reconstruct) the JSON body that was published on `quote:accepted`  
   (minimum CRM fields: `dealExternalId`, `quoteUuid` or `quoteNumber`, `totalAmount`, `currency`, `acceptedAt`).

2. Optionally **mutate** the deal after first win so a second apply would be visible  
   (e.g. change amount via CRM API to a sentinel value). Skip if you only check logs/keys.

3. Republish:

```bash
PAYLOAD='{"schemaVersion":"QuoteAccepted.v1","dealExternalId":"<deal-uuid>","quoteUuid":"<quote-uuid>","quoteNumber":"Q-…","totalAmount":25000.50,"currency":"EUR","acceptedAt":"2025-06-15T14:30:00Z"}'

docker compose -f backend/docker-compose.yml exec -T redis \
  redis-cli PUBLISH quote:accepted "$PAYLOAD"
```

4. Wait ~2–4s; **GET deal again**. Amount/currency/`wonAt` must match post-first-apply (or your sentinel if you mutated). Version/`updatedAt` must not jump solely from the duplicate.

5. **Logs:** crm-service should log at INFO:  
   `Duplicate quote acceptance for key …, skipping`  
   (not a deserialize/apply error).

6. **Key still present:** `EXISTS crm:quote-accepted:processed:<quoteUuid>` → `1`.

### B — Alternate: second HTTP accept

If the quote API rejects a second accept (already ACCEPTED), that is **not** a Redis idempotency proof. Use path **A** (Redis `PUBLISH`) for the cutover checklist.

---

## Semantics operators should know

| Case | Expected behavior |
|------|-------------------|
| First successful apply | SET NX key retained **30 days**; deal → WON |
| Duplicate while key exists | Skip processor; INFO log; deal unchanged |
| Deal missing / apply throws | Key **deleted** so a later republish can retry (no poisoned key) |
| Blank `dealExternalId` | Listener skips; no WON update |
| CRM down at accept time | Redis Pub/Sub is fire-and-forget — message lost; re-accept/republish after CRM is up |

Kafka `QuoteAcceptedEvent` (document/contract consumers) is a **separate** path — do not use it to verify CRM WON.

---

## Automated IT (CI / local Docker)

When Docker is available:

```bash
cd backend
./mvnw -pl services/crm-service -am -Pintegration-tests \
  -Dtest=QuoteAcceptedRedisIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Covers publish → WON and duplicate skip with Testcontainers Redis.

---

## Checklist sign-off

Mark **Quote acceptance E2E** in [CRM_CUTOVER_CHECKLIST.md](../CRM_CUTOVER_CHECKLIST.md) only when staging evidence includes:

1. Accept with `dealExternalId` = deal UUID → deal `WON` + amounts  
2. Duplicate Redis publish → skip + no second mutation  
3. Key under `crm:quote-accepted:processed:*` observed
