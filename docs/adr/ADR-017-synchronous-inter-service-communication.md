# ADR-017: Synchronous inter-service communication

**Status:** Accepted on 2026-10-17. The project owner told, as relayed by the Projects Manager bot on 2026-10-17 at 7:31 AM CT, "yes to accepting ADR-017, ADR-024 to 027 and DRG-533 (#644); authorized opening their status-change PRs". The owner's merge of this PR is the confirming act and the status takes effect on merge. This supersedes the "retain their existing statuses" wording of PRs #662 and #663 only for ADR-017. The number was reserved by the [2026-09-22 ADR review](../forge/39-adr-gitnexus-review-2026-09-22.md) (§3, "ADR-017").
**Date:** 2026-10-04
**Deciders:** Project owner (accepted).
**Relates to:** [ADR-016](ADR-016-identity-authentication-service-trust.md) (identity and service trust — not re-decided here), [ADR-009](ADR-009-gateway-edge-only.md) (gateway edge-only), [ADR-026](ADR-026-command-messages.md) (commands are not HTTP), [ADR-024](ADR-024-order-orchestration-context.md) (order-service has no synchronous edges at start), ADR-020 and ADR-022 (reserved).
**Does not authorize:** any code or configuration change. Each item under Rollout is its own PR.

---

## Context

The review's brief for this ADR: the allowed call graph, the `/internal/v1` versus `/api/v1` rule, default timeouts, retry and idempotency policy, circuit breakers, the no-hairpin rule and error mapping. [ADR-016](ADR-016-identity-authentication-service-trust.md) has since settled who authenticates: `/internal/**` requires the `Internal.Invoke` app role (`OpenCrmSecurityAutoConfiguration.java:102`), and `OutboundAuth` either relays the user token or attaches a service token.

The service-to-service HTTP calls on `main`:

| Edge | Caller → callee | Paths | Auth | Timeouts (connect/read) | Resilience |
|---|---|---|---|---|---|
| E1 | quote → pricing | `POST /api/v1/pricing/execute` (a `/simulate` method exists, unused) | relay | 5s / 30s | none |
| E2 | quote → configuration-engine | `POST /internal/v1/configurations/evaluate` | service token | 5s / 30s | none |
| E3 | quote → approval-routing | `POST /api/v1/approvals`, `GET` by id, by quote and pending, `POST /{id}/approve`, `/{id}/reject` | relay | 5s / 30s | none; no idempotency key |
| E4 | pricing → pricebook | `POST /internal/v1/price-books/resolve-batch` | service token | **none set** | none |
| E5 | configuration-engine → product | `GET /api/v1/configuration/rules/product/{id}` | relay | 5s / 30s | 30s in-process cache |
| E6 | ai-service → **gateway** | `POST /api/v1/pricing/execute`, `GET /api/v1/quotes/{id}/line-items`, `POST /api/v1/configuration/validate` | relay | 5s / 30s | the gateway's |

Other facts that shape the decision:

- **The review's quote→gateway hairpin (F11) is gone.** quote now calls configuration-engine `/internal` directly. ai-service's agent tools (E6) are the only remaining hairpin, allowlisted by ADR-016 step 3 ("keep the bearer lane").
- **No service-side circuit breaker or retry exists on any edge.** Clients that set timeouts each build their own request factory; E4 sets none. There is no shared default.
- **Pricing execute is not free of side effects.** Every execute saves a `PricingAuditLog` row; simulate does not.
- **Downstream failures are mapped inconsistently:**
  - quote returns 502 `PRICING_SERVICE_UNAVAILABLE` or `CONFIGURATION_VALIDATE_UNAVAILABLE` for every failed pricing or configuration-engine call, including a 4xx or 5xx answer, because neither client handles status codes (`PricingServiceClient.java:45-50`, `ProductServiceClient.java:43-53`). It returns 502 `APPROVAL_ROUTING_UNAVAILABLE` when approval-routing is unreachable. pricing and configuration-engine return 503 for an unreachable callee (`PRICEBOOK_UNAVAILABLE`, `PRODUCT_SERVICE_UNAVAILABLE`).
  - pricing turns a pricebook error status into 400. It keeps the pricebook's own `errorCode` when the error body parses, and uses `PRICEBOOK_CLIENT_ERROR` otherwise and for an empty body (`RestPriceBookResolutionClient.java:44-60`). configuration-engine turns other product failures into 400 `PRODUCT_RULES_FETCH_ERROR`.
  - quote maps approval-routing errors per call. Submit, approve and reject pass 401, 403 and 409 through. A 404 on get-by-id, approve or reject becomes 404 `RESOURCE_NOT_FOUND`. Every other error status becomes 502: on the mutations, anything not listed; on the three GETs, anything except get-by-id's 404.
  - A product or pricebook 4xx becomes 400 in the caller, not the callee's status.
- **The gateway has circuit breakers per route,** with a shared default of a 10-call window, 50% failure threshold and 5s open. It sets no explicit response timeout.
- **`SecurityBaselineFitnessTest` already enforces two rules:** every client authenticates, and only allowlisted services may reference the gateway URL. Nothing checks timeouts, retries or which edges may exist.

## Decision

### D-01 — The call graph is an allow-list

E1 to E6 are the allowed edges. The graph is acyclic, and no service calls back into a caller. A new edge needs an amendment to this table, with its path, auth, timeouts and retry class. order-service starts with **no** synchronous edges ([ADR-024](ADR-024-order-orchestration-context.md) D-04).

### D-02 — Namespace

- A new edge uses `/internal/v1/**` with a service token.
- `/api/v1/**` with a relayed user token is kept only where the callee must authorize the end user's own action, as approval decisions (E3) do.
- E1 and E5 stay as they are. Moving them to `/internal/v1` is a later, optional change, not a requirement of this ADR.

### D-03 — Every client sets timeouts, and a shared default provides them

- A shared `RestClient` customizer in `libs/` sets the default: **5s connect, 30s read**, the values most clients already use.
- E4 (pricing → pricebook) gets them first; it is the only edge with none.
- An edge that needs different values states them in the D-01 table.
- The gateway gets an explicit response timeout per route instead of the library default.

### D-04 — Retries only where a repeat is harmless

- A call may retry only if it changes nothing in the callee: GETs, configuration evaluate (E2) and price-book resolution (E4). Pricing execute (E1) saves an audit row on every call, so it is not retried automatically; a retry would record two executions of one pricing.
- Retries happen at most twice, with jittered backoff, and only on connection failure, timeout or 503.
- State-changing calls (E3 submit, approve, reject) never retry automatically until they carry an `Idempotency-Key` that the callee deduplicates on (D-08).

### D-05 — Circuit breakers in the service

Each edge gets a Resilience4j circuit breaker in the calling service, with the gateway's shared defaults as the starting values. An open circuit maps to 503 under D-06. A service mesh is not assumed before ADR-022 decides the runtime topology.

### D-06 — One error-mapping rule

| What happened | Status | Code |
|---|---|---|
| Callee unreachable, timed out or circuit open | 503 | `<TARGET>_UNAVAILABLE` |
| Callee answered 5xx, or a body that cannot be parsed | 502 | `<TARGET>_<OPERATION>_FAILED` |
| Callee answered a 4xx business error | callee's status | callee's code, passed through |

What this changes, all to be checked against frontend handling first:

- quote's three `*_UNAVAILABLE` responses move from 502 to 503, and are used only when the callee is unreachable, timed out or behind an open circuit;
- quote passes a pricing (E1) or configuration-engine (E2) 4xx through with the callee's status, and maps a 5xx or an unparseable body to 502 `PRICING_EXECUTE_FAILED` or `CONFIGURATION_VALIDATE_FAILED`, instead of 502 `*_UNAVAILABLE` for every error;
- pricing's `PRICEBOOK_CLIENT_ERROR` and configuration-engine's `PRODUCT_RULES_FETCH_ERROR` move from 400 to 502 when the callee answered 5xx or an unparseable body;
- quote passes through every 4xx from approval-routing on every call, including the three GETs, instead of mapping most of them to 502;
- configuration-engine and pricing pass a product or pricebook 4xx through with the callee's status, instead of 400.

### D-07 — No hairpin through the gateway

Services do not call the gateway. ai-service's agent tools (E6) are the one ratified exception, because they deliberately exercise the same agent contract external agents use (ADR-016 step 3). This exception is reviewed when ADR-021 (AI topology, reserved) is drafted, or by 2027-01-31, whichever comes first.

### D-08 — Idempotency keys for state-changing calls

A state-changing synchronous call carries an `Idempotency-Key` header. The callee returns the first result for a repeated key within at least the caller's retry window. Each E3 mutation (submit, approve and reject) is enabled for retries separately, and only after approval-routing deduplicates that operation's key. A lost approve or reject response must not be replayed unkeyed.

## Options considered

| Topic | Chosen | Rejected and why |
|---|---|---|
| Graph governance | Allow-list in this ADR, checked by test | Free-form: the review found an undocumented hairpin and a missing timeout this way |
| Resilience | Resilience4j in each service | Mesh: no runtime decision yet (ADR-022); gateway-only: does not cover E1–E5, which bypass it |
| Retry | Only side-effect-free calls, until keys exist | Retry everything: double-submits approvals |
| Error codes | 503 unavailable, 502 bad answer | Keep both in use: clients cannot tell an outage from a bad response |

## Consequences

- **Positive:** Every edge has bounded latency, a breaker and a known retry class. New edges are reviewed, not discovered.
- **Negative:** Several error responses change status code (D-06). Each edge gains configuration to maintain.
- **Cleanup this enables**, all in quote-service unless noted:
  - the unused product URL: `src/main/resources/application.yml:80-81`, `src/main/resources/application-test.yml:22-23`, `src/test/resources/application-test.yml:32-33`, and `PRODUCT_SERVICE_URL` in `backend/docker-compose.yml:230`;
  - the test-only gateway URL: `src/test/resources/application-test.yml:34-35`;
  - the `/simulate` method on `PricingServiceClient`, unused in production code (only `PricingServiceClientTest` calls it).

## Fitness functions (on acceptance)

Extend the source scans in `SecurityBaselineFitnessTest`, or add a sibling test in the same style:

- every client file maps to an edge in the D-01 allow-list, and an unknown client fails; the external clients already carved out (`SecurityBaselineFitnessTest.java:60-64`) stay outside the allow-list;
- every client uses the shared customizer, or sets both timeouts explicitly;
- the existing gateway-URL rule stays, with ai-service as its only allowlisted service.

## Rollout

1. The shared customizer, plus timeouts on E4.
2. Circuit breakers per edge, and the gateway's explicit response timeout.
3. D-06 error normalization, after a frontend check.
4. `Idempotency-Key` deduplication in approval-routing for submit, approve and reject. Each operation's retries are turned on only once its own key is honored.
5. The fitness test.

## Open questions

- O-1: The gateway's effective per-route timeout today. It is not set in `application.yml`; the library default needs confirming.
- O-2: The gateway fallback handlers are GET-only (`FallbackController.java`). A forwarded POST may get 405 instead of 503; needs a test.
- O-3: Whether pricing should expose an idempotent execute (keyed, returning the stored result) so E1 can join D-04.

## Evidence (`main` at `1629070`; paths under `backend/`)

- `services/quote-service/src/main/java/com/opencrm/quote/config/RestClientConfig.java:27,41,52,56-61` — E1 to E3 auth and 5s/30s timeouts.
- `services/quote-service/src/main/java/com/opencrm/quote/client/PricingServiceClient.java:46`; `ProductServiceClient.java:44` (calls configuration-engine); `ApprovalRoutingClient.java:50,80,109,141,174,209`.
- `services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteService.java:606-610` — `RestClientException` mapped to 502 `PRICING_SERVICE_UNAVAILABLE`.
- `services/quote-service/src/main/java/com/opencrm/quote/client/ApprovalRoutingClient.java:82-91,250-253` — 404 becomes `ResourceNotFoundException`, other GET errors 502; submit, approve and reject pass 401, 403 and 409 through.
- `services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingExecutionService.java:65-67,345` — execute persists a `PricingAuditLog`; `PricingExecutionController.java:30,37` — "full audit trail" on execute, none on simulate.
- `services/pricing-service/src/main/java/com/opencrm/pricing/integration/pricebook/RestPriceBookResolutionClient.java:40,44-60,65-69` — E4; 400 `PRICEBOOK_CLIENT_ERROR`, 503 `PRICEBOOK_UNAVAILABLE`; `config/PriceBookClientConfig.java:15,23` — no request factory, service token.
- `services/configuration-engine/src/main/java/com/opencrm/configuration/product/ProductConfigurationRuleClient.java:78,87-95`; `config/ProductServiceClientConfig.java:24-26,30` — E5; 503 `PRODUCT_SERVICE_UNAVAILABLE`, 400 `PRODUCT_RULES_FETCH_ERROR`.
- `services/ai-service/src/main/java/com/opencrm/ai/registry/CpqAgentToolClients.java:70,105,138,181-183,187` — E6.
- `services/gateway-service/src/main/resources/application.yml:254-261` — circuit-breaker defaults.
- `libs/security/src/main/java/com/opencrm/security/web/OpenCrmSecurityAutoConfiguration.java:102` — `/internal/**` requires `Internal.Invoke`.
- `libs/security/src/test/java/com/opencrm/security/web/SecurityBaselineFitnessTest.java:205-220` — services do not call the gateway (ai-service allowlisted).
