# Gateway local runtime and verified startup

Build from `backend/` with Java 25:

```powershell
.\mvnw.cmd -pl services/gateway-service -am package -Punit-tests
java -jar services/gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar --server.port=8080 --spring.data.redis.host=localhost --spring.data.redis.port=6379
```

The default profile retains JWT authentication. Append `--spring.profiles.active=dev` only for local development; dev retains CSRF protection. Set `AI_SERVICE_URI` and `DOCUMENT_GENERATION_URI` for nondefault service destinations. AI defaults to `8090`, documents to `8086`; the frontend proxy stays on gateway `8080`.

The gateway's standard OAuth2 JWT resource-server configuration does not use the transitive Azure 5.8 credential bootstrap. Gateway YAML therefore excludes `AzureTokenCredentialAutoConfiguration`, which references the removed Boot 3 `TaskExecutorBuilder`, and disables that old starter's Boot-version verifier. This does not disable JWT authentication, CSRF, Redis rate limiting, or the gateway circuit breakers. Gateway uses the **reactive** Resilience4j starter so its declared `CircuitBreaker` filters can initialize.

## Request budgets and CircuitBreaker binding

Every default-profile and `dev`-profile route that forwards to an upstream (owners and CPQ aliases) uses structured CircuitBreaker `name` / `fallbackUri` arguments. CircuitBreaker's shortcut accepts only a positional name, so `CircuitBreaker=name=...` incorrectly uses the literal `name=...` ID and ignores a second fallback argument.

TimeLimiter configs are named by breaker ID and referenced by their instances:

| Breaker | Env override | Default |
| --- | --- | --- |
| `quoteCircuitBreaker` | `QUOTE_GATEWAY_TIMEOUT` | `45s` (5s connect + 30s read + 10s orchestration) |
| `pricingCircuitBreaker` | `PRICING_GATEWAY_TIMEOUT` | `35s` |
| `pricebookCircuitBreaker` / `configurationCircuitBreaker` | `PRICEBOOK_GATEWAY_TIMEOUT` / `CONFIGURATION_GATEWAY_TIMEOUT` | `35s` |
| `documentCircuitBreaker` / `aiCircuitBreaker` | `DOCUMENT_GATEWAY_TIMEOUT` / `AI_GATEWAY_TIMEOUT` | `45s` |
| `crm` / `product` / `approval` / `contract` / `communication` | `*_GATEWAY_TIMEOUT` | `30s` (ADR-017 edge default) |

Local fallback handlers accept GET/POST/PUT/PATCH/DELETE and return the existing HTTP 503 JSON response. Paths, destinations, rewrites, security and circuit-breaker thresholds are unchanged. `erp-stub` stays a direct forward without a breaker.

`GatewayCircuitBreakerTimeoutTest` exercises every packaged breaker route with real Netty forwarding: a successful upstream delayed 1.6 seconds returns HTTP 200; a configured 150 ms deadline around a two-second upstream returns HTTP 503 JSON for POST; each breaker ID is the intended name, not a literal `name=...`. This fixture isolates auth/Redis, which their dedicated tests cover.

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
