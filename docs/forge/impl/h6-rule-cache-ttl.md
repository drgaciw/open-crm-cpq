---
status: done
id: h6-rule-cache-ttl
---

# h6-rule-cache-ttl

## Summary

Added a thin in-process TTL cache on `ProductConfigurationRuleClient.getRulesForProduct` (S2B-9 partial). Cache key is `productId`; entries expire by configurable TTL only (default `30s`). No Kafka / event-bus invalidation this wave.

## Files

| Path | Change |
|------|--------|
| `backend/services/configuration-engine/.../ProductConfigurationRuleClient.java` | ConcurrentHashMap + `InstantSource` TTL cache; Spring `@Value` TTL |
| `backend/services/configuration-engine/src/main/resources/application.yml` | `services.product.rules-cache-ttl` (env `PRODUCT_RULES_CACHE_TTL`, default `30s`) |
| `.../ProductConfigurationRuleClientTest.java` | Within-TTL no second RestClient; after-TTL refetch |
| `.../StatelessConfigurationEvaluatorRestClientTest.java` | Construct client with TTL + clock |
| `docs/forge/impl/h6-rule-cache-ttl.md` | This status note |

## Acceptance

- [x] Second `getRulesForProduct` within TTL does not call RestClient
- [x] TTL expiry refetches via RestClient
- [x] Configurable TTL (default 30s)
- [x] No Kafka invalidation / no product→CE event contract

## Blast radius

GitNexus impact on `ProductConfigurationRuleClient`: **UNKNOWN** (symbol not in index). Confirmed via `rg`: production callers are `StatelessConfigurationEvaluator` and `ConstraintSolver` (method references). Method signature unchanged; constructor gains TTL (+ package clock for tests). Risk: **MED** (stale rules up to TTL) per Wave H architecture — intentional thin cache.

## Verify

```bash
export JAVA_HOME=~/.local/share/mise/installs/java/21.0.2
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
./mvnw -pl services/configuration-engine -am test \
  -Dtest=ProductConfigurationRuleClientTest,StatelessConfigurationEvaluatorRestClientTest \
  -DfailIfNoTests=false
```

## Notes

- Prefer JDK 21 (`InstantSource`, `Duration` binding). No Caffeine dependency.
- Zero/negative TTL disables caching (fetch every call).
