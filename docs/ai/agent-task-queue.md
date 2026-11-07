# Agent task queue

`ai-service` keeps background work in `ai.agent_task`. The dispatcher leases a bounded batch and calls the handler registered for `kind`. It does not choose the work, call a model, or write CRM data.

`ai.agent-task.dispatcher.enabled` is false in `application.yml`. No profile turns it on. A process starts the single polling loop only when that property is `true`.

`tenant_id` is `TEXT NOT NULL`. Callers pass an opaque string. This queue does not parse it, cap it, or decide which tenant authority is real.

Claiming uses `FOR UPDATE SKIP LOCKED` with `LIMIT` at most 100. A live lease is `leased_until >= now`. When it expires, the unfinished row can be claimed again. Failures wait with exponential backoff, capped at 15 minutes, and park at `max_attempts` with `outcome = 'parked'`.

Parked rows are not an admin screen yet. That view is a follow-up. Until then:

```sql
SELECT id, tenant_id, kind, subject_type, subject_id, attempts, max_attempts, last_error, finished_at
FROM ai.agent_task
WHERE outcome = 'parked'
ORDER BY finished_at DESC
LIMIT 100;
```

A claim can prefer one tenant's higher-priority rows. Fairness across tenants is a follow-up. Event sources, real handlers, and outbound fetch guards are follow-ups too. This queue has no Kafka topic and no outbox.
