# Gateway local runtime and verified startup

Build from `backend/` with Java 25:

```powershell
.\mvnw.cmd -pl services/gateway-service -am package -Punit-tests
java -jar services/gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar --server.port=8080 --spring.data.redis.host=localhost --spring.data.redis.port=6379
```

The default profile retains JWT authentication. Append `--spring.profiles.active=dev` only for local development; dev retains CSRF protection. Set `AI_SERVICE_URI` and `DOCUMENT_GENERATION_URI` for nondefault service destinations. AI defaults to `8090`, documents to `8086`; the frontend proxy stays on gateway `8080`.

The gateway's standard OAuth2 JWT resource-server configuration does not use the transitive Azure 5.8 credential bootstrap. Gateway YAML therefore excludes `AzureTokenCredentialAutoConfiguration`, which references the removed Boot 3 `TaskExecutorBuilder`, and disables that old starter's Boot-version verifier. This does not disable JWT authentication, CSRF, Redis rate limiting, or the gateway circuit breakers. Gateway uses the **reactive** Resilience4j starter so its declared `CircuitBreaker` filters can initialize.

## Quote and pricing request budgets

Default-profile quote and pricing routes, including their CPQ aliases, retain their circuit breakers with bounded compute deadlines. `QUOTE_GATEWAY_TIMEOUT` defaults to `45s`: quote-service's pricing client allows 5 seconds to connect and 30 seconds to read, with another 10 seconds for service-token/local orchestration work. `PRICING_GATEWAY_TIMEOUT` defaults to `35s` for direct pricing calls. Cold rule compilation exceeding one second can therefore complete without the library's default one-second timeout cancelling a valid response.

The TimeLimiter configs are named by breaker ID and referenced by their instances. Quote/pricing filters use structured `name` and `fallbackUri` arguments: CircuitBreaker's shortcut accepts only a positional name, so `CircuitBreaker=name=...` incorrectly uses the literal `name=...` ID and ignores a second fallback argument. Their local fallback handlers accept GET/POST/PUT/PATCH/DELETE and return the existing HTTP 503 JSON response for unavailable upstreams. Paths, destinations, rewrites, security and circuit-breaker thresholds are unchanged.

`GatewayCircuitBreakerTimeoutTest` exercises all four packaged compute routes with real Netty forwarding and the actual reactive breaker: a successful upstream delayed 1.6 seconds returns HTTP 200; a configured 150 ms deadline around a two-second upstream returns HTTP 503 JSON for POST. It also checks the configured 45/35-second limits and unrelated CRM's existing one-second default. This fixture isolates auth/Redis, which their dedicated tests cover.

## Other route budgets (DRG-528)

Every other default-profile route family now uses the same structured `name`/`fallbackUri` arguments: crm, pricebook, configuration (including the validate shim), product, approval, document, contract, communication and ai, plus their CPQ aliases. Each family also has its own named TimeLimiter config and instance, and fallback handlers for GET, POST, PUT, PATCH and DELETE that keep the HTTP 503 JSON response. Paths, destinations, rewrites, security and circuit-breaker thresholds are unchanged.

| Breaker | Property (default) | Basis |
|---------|--------------------|-------|
| `crmCircuitBreaker`, `pricebookCircuitBreaker`, `productCircuitBreaker`, `approvalCircuitBreaker`, `documentCircuitBreaker`, `contractCircuitBreaker` | `CRM_`, `PRICEBOOK_`, `PRODUCT_`, `APPROVAL_`, `DOCUMENT_`, `CONTRACT_GATEWAY_TIMEOUT` (`10s`) | Database-bound: no outbound HTTP on the request path. Document generation and ERP sync are asynchronous. The budget is well above the 1s library default and below Hikari's 30s acquire timeout. |
| `configurationCircuitBreaker` | `CONFIGURATION_GATEWAY_TIMEOUT` (`45s`) | A cold rules cache reads product-service with 5s connect and 30s read, plus 10s for token handling and evaluation, as quote does. |
| `communicationCircuitBreaker` | `COMMUNICATION_GATEWAY_TIMEOUT` (`10s`) | `lb://communication-service` has no registry. It fails at once and degrades to its fallback. |
| `aiCircuitBreaker` | `AI_GATEWAY_TIMEOUT` (`120s`) | One completion, one 35s gateway tool call and a second completion come to about 95s. ai-service caps its own model timeouts at 2 minutes. |

A deadline bounds the wait for the upstream's response headers. An ai-service SSE stream that has already started is not cut while its body continues.

`GatewayRouteFamilyCircuitBreakerTest` uses real Netty forwarding through the actual reactive breaker on all 17 routes. For each route it checks that a 1.6-second successful upstream returns 200 and is recorded on the intended breaker ID. It also checks that a 150 ms budget, set through the family's named property, returns the family's 503 JSON for every admitted method. The test also checks:

- the bound structured arguments;
- the named defaults and their overrides;
- communication's packaged `lb://` destination degrading to its fallback;
- the ai SSE case.

`FallbackControllerTest` covers all eleven breaker fallbacks for the five methods. Dev-profile routes declare no circuit breakers and keep their existing configuration.

## Verification evidence (2026-09-19)

The packaged application started under Java 25 / Boot 4.0.6 in both default and dev profiles, using separate ephemeral loopback ports and a private disposable `redis:7` container. No provider credentials, compatibility command-line overrides, existing stack restarts, or user data were involved.

| Probe | Result |
| --- | --- |
| Default and dev `/actuator/health` | HTTP 200, `status=UP` |
| Default unauthenticated `/api/v1/ai/agents` | HTTP 401 |
| Dev GET synthetic `/api/v1/erp/synthetic-smoke` | HTTP 501 from the built-in ERP stub |
| Dev POST same path without CSRF token | HTTP 403 |
| Private Redis after the routed ERP call | 2 rate-limiter keys |
| Both profiles' actuator route listing | AI `http://localhost:8090`; documents `http://localhost:8086` |

`GatewayApplicationStartupTest` loads whole application configuration for both profiles, verifies the reactive circuit-breaker factory and AI route, and asserts default unauthenticated access is rejected. It does not require Docker.

`GatewayDispatchSmokeTest` runs an actual ephemeral Netty gateway with actual HTTP upstream fixture servers. Both profiles prove AI/copilot dispatch, document legacy/CPQ rewrites, approval-history legacy/CPQ dispatch to the quote BFF, and unknown-path 404. This routing fixture excludes Redis/auth/circuit-breaker infrastructure, covered separately by the full startup checks above. Approval history domain behavior is tested in quote-service; these gateway tests prove routing, not database semantics.

All temporary processes and the private Redis container were cleaned up. Browser visual UAT and live cloud-provider validation were not part of these backend checks.
