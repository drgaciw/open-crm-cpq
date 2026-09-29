# Runbook: domain events (outbox → Kafka → consumers)

**Scope.** Operating the event pipeline defined by [ADR-018](../adr/ADR-018-event-delivery-guarantees-idempotency.md). The pipeline has five parts:

- the transactional outbox in each producer schema
- the relay that sends it to Kafka
- the idempotent consumers
- the dead-letter topics (DLT)
- the quote-accepted dual run to CRM

**Audience.** Whoever enables events in an environment, answers an event alert, or retires the Redis channel.

**Status.** Written 2026-09-23 with the ADR-018 close-out. The owner decisions were recorded on 2026-09-28 (§7). It has been exercised by the Testcontainers ITs named in each section, not yet in a shared environment. Update it after the first real enablement.

> Values in angle brackets are yours to fill in. Every SQL statement runs **per owning schema**: `product`, `quote`, `pricing` (pricebook shares it), `configuration`, `approval`, `contract` and `document`. Set the schema first, for example with `SET search_path TO quote;`.

---

## 1. Signals

All meters are Micrometer meters, scraped from each service's management port at `/actuator/prometheus` (no token; the port is open to the monitoring namespace only, see the [identity runbook](identity-go-live.md) section 5). The backend that alerts on them is ADR-020's decision. The names below are Micrometer names; Prometheus replaces the dots with underscores.

| Meter | Service | Meaning |
|-------|---------|---------|
| `opencrm.outbox.pending` | producers | Rows waiting for a broker acknowledgement |
| `opencrm.outbox.oldest.pending.age` (s) | producers | How far the relay is behind |
| `opencrm.outbox.failed` | producers | Rows at the retry limit. Each one **blocks its aggregate** |
| `opencrm.outbox.relay.attempts{result}` | producers | `published`, `failed`, `exhausted` |
| `opencrm.kafka.consumer.dead.letters{topic,group,exception}` | consumers | Records sent to a DLT, counted once the DLT publish succeeded |
| `opencrm.kafka.dlt.depth{topic}` | consumers | Records on `<topic>.DLT` that nobody has triaged (§5). It drops to zero after replay or skip |
| `kafka.consumer.fetch.manager.records.lag.max{client.id,…}` | consumers | Consumer lag, bound by Spring Boot's `KafkaMetricsAutoConfiguration` |
| `opencrm.crm.quote.accepted{channel,outcome}` | crm-service | Quote acceptances by `redis` or `kafka` and by `applied`, `duplicate`, `deal-not-found`, `no-deal` or `failed` (§6) |

The health indicator `outbox` (`/actuator/health`, on the management port when there is one) is **DOWN** when a row is `FAILED` or pending rows are older than `opencrm.outbox.health.max-pending-age`. It is **UNKNOWN** while the relay is disabled.

### Recommended alerts

Thresholds are starting points; tune them after a week of real traffic.

Severity routes to two destinations per environment: **page** goes to `<page channel>` and **ticket** to `<ticket channel>`. Naming them is a deployment fact the owner still owes; the alert rules cannot be enabled without them.

| Alert | Condition | Severity | First action |
|-------|-----------|----------|--------------|
| Outbox row failed | `opencrm.outbox.failed > 0` | page | §4: find the cause, then requeue or skip |
| Relay behind | `opencrm.outbox.oldest.pending.age > 300` for 10 min | ticket | Kafka reachable? Relay enabled? `relay.attempts{result=failed}` rising? |
| Dead letters arriving | increase of `opencrm.kafka.consumer.dead.letters` over 15 min `> 0` | ticket | §5 `peek` |
| DLT untriaged | `opencrm.kafka.dlt.depth > 0` for 24 h | ticket | §5: replay or skip |
| Consumer lag | `kafka.consumer.fetch.manager.records.lag.max > 1000` for 10 min | ticket | Is the consumer up? Is it retrying one record (D7 backoff holds the partition)? |

**DLT retention (decided 2026-09-28).** Set `retention.ms` on every `*.DLT` topic to at least 14 days (`1209600000`), so a record survives a two-week holiday before anyone triages it. `cpq.quote.status.accepted.DLT` gets 30 days (`2592000000`): a lost record there means a missing subscription or ERP sync. The severities above stand, and only a failed outbox row pages.

```bash
# compose; set it once per DLT topic (Kubernetes: the same command through kubectl exec on a broker pod)
docker exec opencrm-kafka /opt/kafka/bin/kafka-configs.sh --bootstrap-server kafka:29092 --alter \
  --entity-type topics --entity-name cpq.quote.status.accepted.DLT --add-config retention.ms=2592000000
```

---

## 2. Enabling events in an environment

Do this once per environment, in order. The relay is off everywhere (`opencrm.outbox.relay.enabled=false`), so every producer schema still holds its whole history of events that were never sent.

1. **Deploy the ADR-018 releases.** The D4 envelope and the `SKIPPED` status must be migrated in all seven schemas. Check `SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;` against the service's latest migration.
2. **Make Kafka reachable**, set `KAFKA_BOOTSTRAP_SERVERS`, and set DLT retention (§1).
3. **Inventory the backlog** in each producer schema:

   ```sql
   SELECT COALESCE(topic, event_type) AS topic, status, count(*) AS rows,
          min(created_at) AS oldest, max(created_at) AS newest
     FROM outbox_events
    GROUP BY 1, 2
    ORDER BY 1, 2;
   ```

4. **Decide, per topic, what history reaches whom (owner decision, 2026-09-28).** The rule:
   - Where the inventory shows **test data** (likely dev, test and staging, and production if it has not launched), skip it all with step 5.
   - Where **real history** exists, decide per topic and record the decision with the environment's change.
   - **`cpq.quote.status.accepted`**: send no history without sign-off from whoever owns contracts and the ERP. It causes a subscription and an ERP sync per quote. Today `ErpAdapter` is a stub that calls nothing, but once a real adapter replaces it, the ERP owner must first confirm that the ERP upserts by subscription number (decision 10), because a sync repeated after a crash is possible.
   - In **every** environment crm starts at the end of the topic (step 6), whatever else is decided.

   The consumers and their side effects on replay:

   | Topic | Consumers | Side effect of replaying history |
   |-------|-----------|----------------------------------|
   | `cpq.quote.status.accepted` | document-generation, contract-asset, crm | A PDF job per quote; a **subscription and an ERP sync** per quote; deal set WON (see step 6) |
   | `cpq.discount.approval.required` | approval-routing | A PENDING approval request per event whose figures differ from the open one for that quote line, which becomes `SUPERSEDED` (an identical event is a duplicate). Replaying history in order leaves one open request per line, carrying the last figures |
   | `cpq.product.status.retired`, `cpq.product.updated`, `cpq.product.bundle.structure.changed` | configuration-engine | Cancels in-progress sessions for retired products; cache invalidation |
   | `cpq.configuration.committed`, `cpq.subscription.renewal.eligible`, `cpq.quote.approval.rejected` | none yet | None |

   There are three levers:
   - **Skip rows** (step 5): nobody gets them.
   - **Send them** (step 7): every consumer gets them.
   - **Send them, but start one consumer group at the end of the topic**: that group ignores history. Stop that consumer before step 7 and reset its offsets after the backlog drains (step 6 shows how).
5. **Skip what must not be sent.** This statement is verbatim from the test `OutboxRelayLeasingIntegrationTest.runbookSkipUnblocksAndAgesOut`:

   ```sql
   UPDATE outbox_events
      SET status = 'SKIPPED', published = TRUE, published_at = now(), next_attempt_at = NULL,
          error_message = LEFT('skipped: ' || '<reason, decision reference>', 500)
    WHERE status IN ('PENDING', 'FAILED')
      AND created_at < '<cutoff timestamp>';
   -- optionally narrow: AND COALESCE(topic, event_type) = '<topic>'
   ```

   `SKIPPED` is terminal:
   - it never goes to Kafka;
   - it no longer blocks later rows of its aggregate;
   - it ages out with the published rows after `opencrm.outbox.relay.retention`;
   - its `error_message` records why it was skipped.
6. **CRM starts at the end of the topic.** Acceptances already applied through Redis before ADR-018 D8 have no dedupe claim, so replaying them would set WON, `wonAt`, amount and currency on their deals again (ADR-018, "Backlog caveat"). Keep crm's Kafka listener **off** (`CRM_QUOTE_ACCEPTED_KAFKA_ENABLED=false`) until the quote backlog has drained (step 7), then:

   ```bash
   # compose: the broker container is opencrm-kafka, internal listener kafka:29092
   docker exec opencrm-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:29092 \
     --group crm-service --topic cpq.quote.status.accepted --reset-offsets --to-latest --execute
   ```

   The group must have no active members when you run this. Redis still carries acceptances in the meantime (dual run).
7. **Enable the relay** in each producer: `OPENCRM_OUTBOX_RELAY_ENABLED=true`, one service at a time. Watch `opencrm.outbox.pending` fall to zero and `outbox` health turn UP.
8. **Enable crm's Kafka listener**: `CRM_QUOTE_ACCEPTED_KAFKA_ENABLED=true`.
9. **Smoke check.**
   - Accept a test quote that has a deal.
   - Within seconds, `opencrm.crm.quote.accepted` should show one `applied` and one `duplicate` across the two channels.
   - The document job and the subscription should exist.
   - The record on `cpq.quote.status.accepted` should carry the eight D4 headers.
   - Inspect it with:

     ```bash
     docker exec opencrm-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 \
       --topic cpq.quote.status.accepted --from-beginning --max-messages 1 --formatter-property print.headers=true
     ```

**Rollback.** Set the relay flag back to `false`. Rows stay in the outbox, and the relay resumes where it stopped.

---

## 3. Deploying and rolling back ADR-018 releases

- Every ADR-018 migration is additive. Code from before each step keeps working against the newer schema.
- **Before the D4 contract step**, any release can be rolled back by redeploying the previous one.
- **D4 contract step (later).** Once every environment runs the D4 expand release, switch `event_type` to the registry type:
  - change `OutboxEventPublisher` to write `contract.eventType()`;
  - run `UPDATE outbox_events SET event_type = <registry type for topic> WHERE topic IS NOT NULL`, with one `CASE` over the 12 registered topics.

  After that step, rolling back past D4 first needs the relay off and the reverse update, `UPDATE outbox_events SET event_type = topic WHERE topic IS NOT NULL AND status NOT IN ('PUBLISHED', 'SKIPPED')`. Otherwise a relay from before D4 would send those rows to a topic named after the event type.
- **`SKIPPED` and rollback.** A release from before the `SKIPPED` migration treats a `SKIPPED` row as blocking its aggregate. If you roll back that far, mark the skipped rows `PUBLISHED` first; their `error_message` still says they were skipped.

---

## 4. A `FAILED` outbox row

The row reached `opencrm.outbox.relay.max-attempts` (10). Every later row of the same aggregate waits behind it.

1. Read the cause:

   ```sql
   SELECT id, uuid, COALESCE(topic, event_type) AS topic, aggregate_type, aggregate_id,
          retry_count, error_message, created_at
     FROM outbox_events
    WHERE status = 'FAILED';
   ```

   Typical causes are an unreachable broker, authorisation, or a record that is too large.
2. Fix the cause.
3. Then either **requeue**, so the row is sent:

   ```sql
   UPDATE outbox_events SET status = 'PENDING', retry_count = 0, next_attempt_at = NULL WHERE id = <id>;
   ```

   or **skip**, so it is never sent, using the §2 step 5 statement narrowed to `id = <id>`. Skip only if the event must not reach consumers, and record why.

---

## 5. Dead letters: triage with `DeadLetterTool`

- **How a record gets there.** A record that failed in a listener is retried with backoff (D7: 4 retries, 1 s doubling to 10 s). Unreadable or incomplete records (`NonRetryableEventException`, Jackson parse errors) skip the retries. The record then goes to `<topic>.DLT` with Spring Kafka's `kafka_dlt-*` headers: origin topic and group, exception class and message.
- **One DLT per topic.** Several consumer groups can fail on the same topic, so they all share it.
- **Depth.** The triage cursor is the consumer group `opencrm-dlt-triage`. Its committed offsets mark what has been handled, and `opencrm.kafka.dlt.depth` counts what has not.
- **Where it runs.** `DeadLetterTool` ships in `libs/common`, so it is inside every consumer's image. It runs as a one-off admin process, with no HTTP endpoint (ADR-016 is not done). The broker comes from `--bootstrap-servers`, else `KAFKA_BOOTSTRAP_SERVERS`, which compose already sets.

```bash
# compose; any consumer image works (approval-routing, document-generation, contract-asset-management, ...)
DLT_TOOL="java -Dloader.main=com.opencrm.common.event.DeadLetterTool -cp app.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher"
docker compose exec document-generation sh -c "$DLT_TOOL depth  cpq.quote.status.accepted"
docker compose exec document-generation sh -c "$DLT_TOOL peek   cpq.quote.status.accepted --max 20"
docker compose exec document-generation sh -c "$DLT_TOOL replay cpq.quote.status.accepted --max 20 --dry-run"
docker compose exec document-generation sh -c "$DLT_TOOL replay cpq.quote.status.accepted --max 20"
docker compose exec document-generation sh -c "$DLT_TOOL skip   cpq.quote.status.accepted --max 1"
# Kubernetes: kubectl exec deploy/document-generation -- sh -c "$DLT_TOOL ..."
```

**Procedure.**

1. **`peek`.** Each line shows the partition and offset, the key, the original topic and group (`group=`), the `eventId`, and the exception class and message. Group the records by exception.
2. **Fix the cause** before replaying. A record that fails again is dead-lettered again, and the cursor has already moved past the earlier copy.
   - `NonRetryableEventException` or a parse error is a bad payload. It is usually a producer bug or a contract drift, so check `EventContractRegistry` and the payload class.
   - Anything else is usually an outage that has since passed: the database, the ERP, a deal that did not exist yet.
3. **`replay`** once the cause is fixed. Run `--dry-run` first. Replay sends each record back to its original topic with its original key, value and envelope headers, including `eventId`, minus the `kafka_dlt-*` headers, and only then moves the cursor.
   - Every consumer is idempotent (D6), so a group that already processed the event ignores the copy. That is why replay needs no filter on `group=`.
   - A crash between the send and the cursor commit replays that record twice, which is harmless for the same reason.
   - **Discount approvals need a check first.** A `cpq.discount.approval.required` record carries no ordering field. If newer events for the same quote line were processed after this record failed, replaying it makes its older figures the current request and supersedes the newer one. `peek`, then look at the quote line's approvals (`SELECT id, status, requested_discount, margin_delta, superseded_by FROM approval_requests WHERE quote_id = '<quote>' AND line_id = '<line>' ORDER BY id;`). Replay only if it is still the latest figures; otherwise `skip` it.
4. **`skip`** records that must never be processed, such as a poison payload with no business meaning. Note what you skipped and why in the incident or ticket.
5. **Confirm** that `opencrm.kafka.dlt.depth` is back to 0.

Run one triage at a time per DLT. The procedure is exercised against a real broker by `DeadLetterTriageIntegrationTest` (document-generation): poison, counted, depth 1, `peek`, `skip`, depth 0; then a fixed failure is replayed and processed.

---

## 6. Quote-accepted dual run (Redis and Kafka) and retiring Redis

- **The dual run.** While `CRM_QUOTE_ACCEPTED_KAFKA_ENABLED=true` and quote-service still publishes to Redis `quote:accepted`, every acceptance reaches crm-service twice.
- **Dedupe.** The first arrival claims the quote UUID in `crm.processed_events` (namespace `crm-quote-accepted`) and marks the deal WON; the second is a `duplicate`. Proven by `QuoteAcceptedDualRunIntegrationTest`.
- **Evidence.** `opencrm.crm.quote.accepted{channel,outcome}` counts both. Over a window, for acceptances that have a deal:
  - `kafka` (`applied` + `duplicate`) should equal `redis` (`applied` + `duplicate`);
  - `kafka` `failed` should be 0 once retries settle;
  - `opencrm.kafka.dlt.depth{topic=cpq.quote.status.accepted.DLT}` should be 0.
- **Cross-check in the database:**

  ```sql
  -- crm schema: acceptances applied since the window started
  SELECT count(*) FROM processed_events
   WHERE consumer_group = 'crm-quote-accepted' AND processed_at >= '<window start>';
  ```

**Exit criterion for D8 step 3** (decided 2026-09-28). Every environment shows the equality above for **7 consecutive days** with at least one business week of real traffic, **and at least 30 acceptances that have a deal**, whichever ends later, and has had no `cpq.quote.status.accepted.DLT` records for crm in that time. Seven quiet days prove nothing if there were three acceptances, hence the floor. The window starts when crm's Kafka listener is enabled in production; staging traffic does not count. Then remove, in one release:

- quote-service `QuoteEventPublisher` and its call in `QuoteService.accept`;
- crm-service `QuoteAcceptedEventListener` and `RedisSubscriberConfig`;
- the `REDIS_PUBLISH_ALLOWLIST` entry in `EventContractFitnessTest`, which fails until you do.

**If Kafka lags or fails during the dual run**, Redis keeps CRM current, and nothing needs doing beyond §1 and §5. If Redis drops a message, which Pub/Sub does whenever crm-service is down, Kafka applies it when it arrives. That catch-up is the reason for the migration.

---

## 7. Decisions (recorded 2026-09-28) and what is still owed

Taken by the owner's delegate; the [decision sheet](../forge/40-owner-decisions-adr-016-018.md) has the reasoning and the record.

| Decision | Outcome | Applies in |
|----------|---------|------------|
| Accept ADR-018 | Accepted. The D4 contract step (§3) and D8 step 3 (§6) stay ahead | ADR-018 |
| DLT retention and alert routing | 14 days floor, 30 days on `cpq.quote.status.accepted.DLT`; severities as in §1 | §1 |
| Backlog per environment and topic | Skip test data, decide real history per topic, crm at the end of the topic everywhere | §2 steps 4 to 6 |
| Dual-run window and exit criterion | 7 consecutive days, one business week of real traffic, and at least 30 acceptances | §6 |
| A discount event with changed figures | Supersede the open approval | §2 step 4, §5 |
| ERP upsert by subscription number | The ERP upserts; confirm before a real ERP is wired | §2 step 4 |

**Still owed by people:**

- The page and ticket destinations for the alerts (§1).
- Sign-off from the contracts and ERP owners for any history on `cpq.quote.status.accepted`, and the ERP owner's confirmation of the upsert.
- The inventory and the skip-or-send decision in each environment (§2).