---
status: done
---
# b0-quote-it

## Files
- QuoteAcceptedRedisIntegrationTest.java — Redis Testcontainers IT (WON + duplicate idempotency)
- crm-service/pom.xml — testcontainers + awaitility test deps

## Notes
Tagged `@Tag("integration")` / `@Tag("docker")`. Unit profile remains Docker-free.
Deal-missing key release owned by B0-2 branch; merge/rebase before stacking ITs if needed.
