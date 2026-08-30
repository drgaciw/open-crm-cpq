---
status: done
agent: impl-idem
id: b0-quote-idem
branch: fix/forge-b0-quote-idempotency
commit: 0eaaa7f
---

# b0-quote-idem — PR-A quote-accepted idempotency harden

## status

**done**

## files touched

- `backend/services/crm-service/src/main/java/com/opencrm/crm/event/QuoteAcceptedIdempotency.java`
- `backend/services/crm-service/src/main/java/com/opencrm/crm/event/QuoteAcceptedDealProcessor.java`
- `backend/services/crm-service/src/main/java/com/opencrm/crm/event/QuoteAcceptedEventListener.java`
- `backend/libs/common/src/main/java/com/opencrm/common/event/dto/QuoteAcceptedCrmMessage.java` (JavaDoc only)
- `backend/services/crm-service/src/test/java/com/opencrm/crm/event/QuoteAcceptedEventListenerTest.java`
- `backend/services/crm-service/src/test/java/com/opencrm/crm/event/QuoteAcceptedDealProcessorTest.java`
- `backend/services/crm-service/src/test/java/com/opencrm/crm/event/QuoteAcceptedIdempotencyTest.java`
- `docs/forge/impl/b0-quote-idem.md`

## tests run

```bash
./mvnw -pl services/crm-service -am -Punit-tests \
  -Dtest=QuoteAcceptedEventListenerTest,QuoteAcceptedDealProcessorTest,QuoteAcceptedIdempotencyTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Result: **BUILD SUCCESS** — 11 tests, 0 failures (listener 5, processor 2, idempotency 4). Java 21 via `mise`.

## notes

- **Semantics chosen:** claim-then-apply with **delete-on-no-op** (not set-after-success) to keep concurrent at-most-once via SET NX while avoiding poisoned keys.
- `applyWinFromQuote` returns `boolean`; listener deletes Redis key when deal missing or processor throws.
- Duplicate skip logged at **INFO**; blank `dealExternalId` skipped (`isBlank`).
- TTL centralized as `QuoteAcceptedIdempotency.TTL` (30 days).
- GitNexus MCP unavailable; blast radius via grep: processor ← listener only; listener ← `RedisSubscriberConfig` only. Risk medium per plan.
- No Redis Testcontainers IT (sibling `impl-qit`). No search API changes.
- Orchestrator commit: `0eaaa7f fix(crm): release quote-accepted idempotency key when deal missing`.
