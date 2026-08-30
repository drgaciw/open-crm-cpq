# Track 03 — Quote accepted → Deal WON E2E + idempotency

**Scout:** forge `quote-e2e` (Grok 4.5)  
**Mode:** discovery / plan only (no application source edits)  
**Related docs:** [CRM_CUTOVER_CHECKLIST.md](../CRM_CUTOVER_CHECKLIST.md), [CRM_TESTING_STRATEGY.md](../CRM_TESTING_STRATEGY.md), [CRM_GATEWAY_ENV.md](../CRM_GATEWAY_ENV.md), [scripts/staging-gateway-smoke.ps1](../scripts/staging-gateway-smoke.ps1)  
**Orchestrator:** [00-orchestrator-status.md](./00-orchestrator-status.md)

---

## 1. Track name + scope

| | |
|--|--|
| **Track** | Quote accepted → Deal WON E2E and idempotency |
| **In scope** | Redis channel `quote:accepted`; `QuoteAcceptedCrmMessage` contract fields; quote-service publish path; crm-service listener / processor / Redis idempotency keys; staging smoke gaps; unit + integration test gaps; cutover checklist verification |
| **Out of scope** | Kafka `QuoteAcceptedEvent` / document-generation / contract-asset consumers (except “do not conflate”); Frappe `cpq_beta`; FE quote UI; CPQ product/pricing rules; merging Redis + Kafka DTOs |

**Happy path (intended):**

1. CRM deal exists with `Deal.uuid`.
2. Quote created/updated with `dealExternalId` = that UUID string.
3. Quote lifecycle reaches `SENT`, then `POST /api/v1/quotes/{id}/accept`.
4. `quote-service` publishes JSON `QuoteAcceptedCrmMessage` on Redis `quote:accepted` **and** (separately) Kafka outbox `QUOTE_STATUS_ACCEPTED`.
5. `crm-service` `QuoteAcceptedEventListener` deserializes, SET NX idempotency key, `QuoteAcceptedDealProcessor.applyWinFromQuote` → deal `WON` + amount/currency/`wonAt`.
6. Duplicate Redis delivery → key hit → skip (no second apply).

---

## 2. Current state (what exists)

### Publisher (quote-service)

| Piece | Path / evidence |
|-------|-----------------|
| Channel constant | `QuoteEventPublisher.QUOTE_ACCEPTED_CHANNEL = "quote:accepted"` — `backend/services/quote-service/src/main/java/com/opencrm/quote/event/QuoteEventPublisher.java` |
| Publish | `StringRedisTemplate.convertAndSend` of serialized `QuoteAcceptedCrmMessage` |
| Accept API | `QuoteController` `POST /{id}/accept` → `QuoteService.accept(id)` |
| Payload factory | `QuoteAcceptedCrmMessageFactory.from(quote, lineItems)` — sets top-level fields **and** nested snapshots (`customer`, `deal`, `configuration`, `pricing`, `terms`, `lineItems`); `schemaVersion` default `"QuoteAccepted.v1"` |
| Kafka (parallel) | Same `accept()` also builds `QuoteAcceptedKafkaEventFactory` → outbox on `KafkaTopics.QUOTE_STATUS_ACCEPTED` |

### Contract DTO (shared)

`backend/libs/common/src/main/java/com/opencrm/common/event/dto/QuoteAcceptedCrmMessage.java`

**Top-level fields CRM docs care about:** `quoteNumber`, `quoteUuid`, `customerExternalId`, `dealExternalId` (**required for CRM**), `dealType`, `totalAmount`, `arr`, `tcv`, `acv`, `currency`, `durationInMonths`, `acceptedAt`, plus nested snapshots.

JavaDoc documents idempotency prefix `crm:quote-accepted:processed:*` and warns Redis-only dedupe is not durable across Redis loss.

### Consumer (crm-service)

| Piece | Path |
|-------|------|
| Subscribe | `RedisSubscriberConfig` → `ChannelTopic("quote:accepted")` |
| Listener | `QuoteAcceptedEventListener` — deserialize → require non-blank `dealExternalId` → `SET NX` TTL **30 days** → `applyWinFromQuote` → **delete key on processor exception** |
| Dedupe | `QuoteAcceptedIdempotency.dedupeKey`: prefer `quoteUuid`; else `dealExternalId:quoteNumber` (or `…:unknown-quote`) |
| Apply | `QuoteAcceptedDealProcessor`: `findByUuid(dealExternalId)` → `markWon()`; set `wonAt` from `acceptedAt` if present; set `amount`/`currency` from top-level fields only |

**CRM does not consume** nested `pricing`/`lineItems`/`arr`/`tcv`/`acv` today — only top-level `dealExternalId`, `totalAmount`, `currency`, `acceptedAt`, plus quote labels for logs.

### Redis config

| Service | Config |
|---------|--------|
| quote-service | `application.yml`: `spring.data.redis.host: ${REDIS_HOST:localhost}` (+ compose `SPRING_DATA_REDIS_HOST` / `REDIS_HOST`) |
| crm-service | `application.yml`: `host: localhost` / `port: 6379` (compose overrides via `SPRING_DATA_REDIS_HOST=redis`) |
| Local stack | `backend/docker-compose.yml` — `redis:7-alpine`; both services `depends_on: redis` healthy |
| Integration tests | `BaseIntegrationTest` points Redis at `localhost:6379`, sets `spring.cache.type=none` — **no Redis Testcontainers**; comment says “Disable Redis” but listener beans still load |

### Tests that exist (unit only for this path)

| Test | Coverage |
|------|----------|
| `QuoteAcceptedCrmMessageFactoryTest` | Factory field/snapshot mapping |
| `QuoteAcceptedKafkaEventFactoryTest` | Kafka payload (out of Redis E2E) |
| `QuoteAcceptedIdempotencyTest` | Key shape / prefer UUID |
| `QuoteAcceptedEventListenerTest` | First delivery invokes processor; duplicate skips (mocked Redis) |
| `QuoteAcceptedDealProcessorTest` | Marks WON + amount/currency; no-op if deal missing |

**Missing:** `QuoteEventPublisher` tests; Redis-backed listener/IT; cross-service accept→WON IT; staging automated quote accept.

### Cutover / smoke docs

- Checklist item **open**: Quote acceptance E2E + duplicate publish idempotency (`docs/CRM_CUTOVER_CHECKLIST.md` § Verification).
- `docs/scripts/staging-gateway-smoke.ps1` — CRM **GET** reads only; prints “Next: manual quote accept → deal WON…”.
- Priority next 1–2 days explicitly lists staging E2E + share field contract.

---

## 3. Gaps with evidence (paths / commands)

### G1 — Staging smoke does not exercise quote → WON or idempotency

**Evidence:** `docs/scripts/staging-gateway-smoke.ps1` only GETs deals/leads/contacts/organizations; checklist item still unchecked.

**Gap:** No automated (or scripted) staging path for:

1. Create/find deal UUID  
2. Create/send/accept quote with `dealExternalId`  
3. Assert deal `stage=WON`, amount/currency  
4. Republish same payload / second accept attempt and assert Redis key skip / no double mutation  

**Verify today (manual):**

```bash
# After stack up (gateway :8080, quote :8084, crm :8081, redis)
# 1) Create deal via gateway; note data.uuid
# 2) Create quote with dealExternalId=<deal.uuid>; approve → send → accept
curl -s -X POST "http://localhost:8080/api/v1/quotes/{id}/accept" -H "Accept: application/json"
# 3) GET deal; expect stage WON
# 4) redis-cli KEYS 'crm:quote-accepted:processed:*'
# 5) PUBLISH quote:accepted '<same json>' and confirm listener skip + deal unchanged
```

### G2 — No Redis / cross-service integration test

**Evidence:**

- Grep: no `RedisContainer` / Testcontainers Redis in backend tests.
- `BaseIntegrationTest` does not start Redis; CRM ITs never publish `quote:accepted`.
- quote-service tests under `…/quote/` are factory-only (`QuoteAcceptedCrmMessageFactoryTest`, `QuoteAcceptedKafkaEventFactoryTest`).

**Commands that do not cover E2E today:**

```bash
cd backend
mvn -pl services/crm-service -am -Punit-tests verify       # mocks only for listener
mvn -pl services/crm-service -am -Pintegration-tests verify # Postgres IT; no Redis pub/sub E2E
mvn -pl services/quote-service -am test                    # factory tests; no publisher IT
```

### G3 — Idempotency sticks on “deal not found” (silent success)

**Evidence:** `QuoteAcceptedEventListener` SETs key **before** `applyWinFromQuote`. Processor logs warn and returns when `findByUuid` empty — **no exception** → key **not** deleted.

**Risk:** Race / wrong UUID / late deal create → first message burns the 30-day key; later correct republish is skipped until ops deletes `crm:quote-accepted:processed:*`.

Also: Redis Pub/Sub is **fire-and-forget** — if crm-service is down at accept time, message is lost (no retry queue). Documented as Redis-only in DTO JavaDoc; still a staging/cutover reliability gap.

### G4 — Dead / divergent `acceptQuote` path

**Evidence:** `QuoteService.acceptQuote(Long)` publishes **Kafka-only** legacy `new QuoteAcceptedEvent(...)` and **does not** call `QuoteEventPublisher`. Controller wires **`accept()`**, not `acceptQuote()`.

`docs/CRM_GATEWAY_ENV.md` still says “`acceptQuote` still publishes `QuoteAcceptedEvent`…” — naming conflates methods and channels.

**Risk:** Future callers of `acceptQuote` would skip CRM WON updates.

### G5 — Contract field usage vs published surface

| Field / snapshot | Published by factory | Used by CRM processor |
|------------------|----------------------|------------------------|
| `dealExternalId` (top-level) | Yes | Yes (required) |
| `deal.dealExternalId` nested | Yes | **No** (nested ignored if top-level blank) |
| `quoteUuid` / `quoteNumber` | Yes | Idempotency + logs |
| `totalAmount` / `currency` / `acceptedAt` | Yes | Yes |
| `arr` / `tcv` / `acv` | Yes (top + pricing) | **No** |
| `lineItems`, `configuration`, `terms` | Yes | **No** (reserved for agents / future) |

**Gap:** Accept does not validate non-blank `dealExternalId` before publish → CRM skip + operator surprise. Nested-only `dealExternalId` would also fail CRM.

### G6 — Listener unit coverage holes

`QuoteAcceptedEventListenerTest` lacks:

- blank/missing `dealExternalId` skip  
- key **delete** when processor throws  
- assert Redis key string uses `QuoteAcceptedIdempotency.KEY_PREFIX` + UUID  

No `QuoteEventPublisher` serialize/publish test (channel name + JSON shape).

### G7 — crm-service Redis YAML asymmetry (low)

quote-service binds `${REDIS_HOST}`; crm-service hardcodes `localhost` relying on Spring `SPRING_DATA_REDIS_*` env. Compose is fine; bare-metal / partial env setups may diverge. Align for ops clarity.

### G8 — Cutover checklist still open; observability not scripted

Checklist § Observability asks distinguishing deserialize failures vs idempotency skips. Listener uses `log.debug` for duplicates — may be invisible at default INFO (`application.yml` `com.opencrm: INFO`). Staging may never see skip evidence without DEBUG or metric bump.

---

## 4. Blast radius / risk notes

**GitNexus:** MCP `gitnexus_*` tools were **not available** in this scout session. Blast radius below is from static call graph / docs. Before implementing, run `npx gitnexus analyze` if index stale, then `gitnexus_impact` on symbols listed; warn on HIGH/CRITICAL.

### Symbols most touched by this track

| Symbol | Direction of interest | Likely callers / dependents |
|--------|----------------------|-----------------------------|
| `QuoteService.accept` | upstream | `QuoteController.accept` |
| `QuoteEventPublisher.publishQuoteAccepted` | upstream | `QuoteService.accept` |
| `QuoteAcceptedCrmMessage` (+ factory) | both | quote-service publish; crm listener; common tests; agent docs |
| `QuoteAcceptedEventListener.onMessage` | upstream | Redis container only |
| `QuoteAcceptedDealProcessor.applyWinFromQuote` | upstream | listener |
| `QuoteAcceptedIdempotency.*` | upstream | listener + unit tests |
| `Deal.markWon` | upstream | processor + `DealService` win APIs |

### Risk levels (scout assessment)

| Area | Risk | Note |
|------|------|------|
| Changing Redis channel name or DTO rename/remove | **HIGH** | Cross-service; dual deploy needed |
| Additive JSON fields on `QuoteAcceptedCrmMessage` | Low | Documented as backward-compatible |
| Idempotency key algorithm change | **HIGH** | Can re-apply or skip incorrectly for in-flight keys |
| Processor amount/currency rules | Medium | Affects won pipeline KPIs |
| Kafka `QuoteAcceptedEvent` / outbox | **Do not touch** for this track | Separate consumers |
| Pub/Sub loss if CRM down | Medium (ops) | Architectural; outbox-style CRM path is a later ADR |

`Deal.markWon()` always sets `wonAt=now` then processor may overwrite with `acceptedAt` — intentional; re-entry after key expiry would re-`markWon` and refresh amount (usually OK for WON).

---

## 5. Proposed PRs

### PR-A — Idempotency + processor correctness (crm-service)

| | |
|--|--|
| **Title** | `fix(crm): harden quote-accepted idempotency when deal missing` |
| **Files** | `QuoteAcceptedEventListener.java`, `QuoteAcceptedDealProcessor.java` (optional), `QuoteAcceptedEventListenerTest.java`, `QuoteAcceptedDealProcessorTest.java`, maybe short note in `QuoteAcceptedCrmMessage` JavaDoc / cutover runbook |
| **Acceptance** | (1) Deal-not-found does **not** retain processed key (delete or set-after-success). (2) Unit tests: missing deal; processor throw → key deleted; blank `dealExternalId` skip. (3) Duplicate delivery still skips after successful apply. (4) Consider INFO (or metric) for idempotency skip so staging can prove G8. |
| **Risk** | Medium — changes at-most-once semantics; must not double-apply on success path. Prefer set-after-success **or** delete-on-no-op; document chosen semantics. |

### PR-B — Redis Testcontainers IT for listener → deal WON

| | |
|--|--|
| **Title** | `test(crm): Redis quote-accepted → deal WON + idempotency IT` |
| **Files** | New `*IntegrationTest` under `crm-service` (extend or sibling of `BaseIntegrationTest`); possibly Testcontainers Redis module in `pom.xml`; test resources |
| **Acceptance** | (1) Seed deal; `convertAndSend` / listener path with real Redis. (2) Assert stage `WON`, amount, currency, `wonAt`. (3) Second identical message → no second mutation; key present under `crm:quote-accepted:processed:`. (4) Tagged `@Tag("integration")`; runs under `-Pintegration-tests` / CI Docker job. |
| **Risk** | Low–medium (test infra). Keep unit profile Docker-free. |

### PR-C — Staging smoke: quote accept E2E + duplicate publish

| | |
|--|--|
| **Title** | `docs(scripts): staging smoke for quote accept → deal WON` |
| **Files** | `docs/scripts/staging-gateway-smoke.ps1` and/or new `docs/scripts/staging-quote-deal-e2e.ps1` (bash twin optional); update `CRM_CUTOVER_CHECKLIST.md` commands reference |
| **Acceptance** | Script (auth-aware) creates or accepts fixture quote with `dealExternalId`, asserts deal WON via gateway, republishes duplicate payload (redis-cli or admin hook), asserts idempotent. Checklist item can be checked when run green in staging. |
| **Risk** | Low (docs/scripts). Needs staging Redis CLI or publish helper; document auth headers. |

### PR-D — Publisher tests + remove/quarantine dead `acceptQuote`

| | |
|--|--|
| **Title** | `fix(quote): cover QuoteEventPublisher; retire dead acceptQuote path` |
| **Files** | `QuoteEventPublisher` (+ test), `QuoteService.java`, `CRM_GATEWAY_ENV.md` wording |
| **Acceptance** | (1) Unit test asserts channel `quote:accepted` + serializable `QuoteAcceptedCrmMessage`. (2) `acceptQuote` deleted or private/deprecated with clear Javadoc pointing to `accept()`. (3) Docs say Redis = CRM DTO; Kafka = document/contract — method names accurate. |
| **Risk** | Low if nothing calls `acceptQuote` (current grep: definition only). |

### PR-E — Accept-time contract guard (quote-service)

| | |
|--|--|
| **Title** | `feat(quote): require dealExternalId before accept publish` |
| **Files** | `QuoteService.accept`, validation tests, optional OpenAPI note |
| **Acceptance** | Accept with blank `dealExternalId` fails with clear `BusinessException` **or** documented warn+skip policy agreed with CRM; preferred: fail fast so CRM is never silently skipped. |
| **Risk** | Medium for clients that accept without deal link — confirm FE/API callers set UUID. Coordinate with track 05 (FE CPQ) / CRM FE if quotes created without deal. |

### Optional follow-up (not wave-1 required)

- Durable CRM idempotency column (`lastWinningQuoteUuid`) as JavaDoc suggests.  
- Align crm-service Redis YAML to `${REDIS_HOST:localhost}`.  
- ADR if Redis Pub/Sub must be replaced with outbox/Kafka for CRM reliability.

---

## 6. Parallelization / dependencies

```text
PR-A (idempotency harden) ──┬──► PR-B (Redis IT) ──► proves A
                            │
PR-D (publisher / dead code) ──► independent; do early
PR-E (accept guard) ──► independent but product-confirm before merge
PR-C (staging smoke) ──► after A (+ ideally B) green; needs staging Redis + auth

Track coupling:
  01 CRM API parity / 02 CRM FE — deal UUID surfaces only
  04 CPQ contracts — do not reshape QuoteAcceptedCrmMessage without this track
  05 FE CPQ UI — must set dealExternalId on quote create/update
  06 gateway/CI — quote routes already present; CI Docker needed for PR-B
```

**Suggested order:** D (safe) ∥ A → B → C; E after confirming callers.

**Cutover gate:** Checklist “Quote acceptance E2E” remains **blocked** until PR-C (or equivalent manual runbook with evidence) + PR-A semantics agreed.

---

## 7. Explicit DO NOT TOUCH list

1. **Kafka** `QuoteAcceptedEvent` / `QuoteAcceptedKafkaEventFactory` / document-generation `QuoteAcceptedEventConsumer` / contract-asset consumers — separate channel; do not merge with Redis DTO.  
2. **Rename/remove** fields on `QuoteAcceptedCrmMessage` without coordinated quote-service + crm-service (+ agent) release.  
3. **Channel rename** away from `quote:accepted` without dual-subscribe migration.  
4. **Frappe / `cpq_beta`** CRM or quote flows.  
5. **Gateway CRM predicates** (`leads|deals|contacts|organizations`) — unrelated to Redis path; quote routes already exist.  
6. **Deal stage machine** broadly (`DealService` advance/lose) beyond quote-accepted win application.  
7. **Agent 028/030** Redis consumers (future) — do not strip nested snapshots they will need.  
8. **Jacoco / CI profile split** (`unit-tests` vs `integration-tests`) — extend, do not collapse Docker requirement into unit job.  
9. **Application source outside** quote publish + crm Redis listener/processor/idempotency + agreed docs/scripts (this plan file is the discovery deliverable).

---

## 8. Discovery commands cheat sheet

```bash
# Contract + publish/consume
rg -n "QuoteAcceptedCrmMessage|quote:accepted|QuoteEventPublisher|QuoteAcceptedIdempotency" backend docs

# Dead path
rg -n "acceptQuote" backend

# Tests
rg -n "QuoteAccepted" backend/services/*/src/test

# Staging / cutover
rg -n "Quote acceptance E2E|idempotency|staging-gateway-smoke" docs

# Local verify (manual)
cd backend && docker compose up -d postgres redis
# … start crm-service + quote-service + gateway …
```

---

## 9. Exit criteria for this track (when implementation is approved)

- [ ] Duplicate Redis delivery does not double-apply deal WON/amount.  
- [ ] Deal-missing / failure paths do not poison idempotency keys incorrectly.  
- [ ] Automated IT (Redis) or equivalent CI-proof exists.  
- [ ] Staging smoke script covers accept → WON + duplicate; checklist item checkable.  
- [ ] Field contract shared (JavaDoc + checklist) and `dealExternalId` semantics enforced or explicitly waived.  
- [ ] Kafka path unchanged and clearly labeled in ops docs.
)
