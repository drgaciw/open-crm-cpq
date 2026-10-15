# ADR-029: Durable agent work queue on PostgreSQL

**Status:** Proposed on 2026-10-15. Not accepted.
**Date:** 2026-10-15
**Deciders:** Project owner (pending).
**Relates to:** ARCHITECTURE.md §9 (agent Kafka #241 held), [ADR-025](ADR-025-saga-process-manager.md) (persisted process manager on PostgreSQL), [ADR-018](ADR-018-event-delivery-guarantees-idempotency.md) (events), reserved ADR-021 (AI topology — this ADR does not take that number or decide topology).
**Source pattern:** [trycompai/crm](https://github.com/trycompai/crm) `AgentTask` model and `lib/tasks.ts` (`README.md` l.88–93), at `6d4793d`.
**Does not authorize:** any code, table or scheduler.

---

## Context

Track CC1 adds background agent work: enrich a new contact, re-check a stale fact, run a team agent on a schedule. That work must survive restarts, must not run twice when two `ai-service` replicas are up, and must respect a budget. Agent Kafka (#241) is held. ADR-025 already chose PostgreSQL for persisted process state.

## Decision

### D-01 — One table, in `ai-service`'s schema
`agent_task`: `id`, `tenant_id`, `kind`, `subject_type`, `subject_id`, `reason`, `payload`, `priority`, `budget`, `attempts`, `max_attempts`, `due_at`, `leased_until`, `lease_owner`, `started_at`, `finished_at`, `outcome`, `last_error`, `created_at`.

### D-02 — Claim with a lease
A dispatcher claims due rows with `SELECT … WHERE due_at <= now() AND (leased_until IS NULL OR leased_until < now()) AND finished_at IS NULL ORDER BY priority DESC, due_at FOR UPDATE SKIP LOCKED LIMIT n`, then sets `leased_until`. Workers extend the lease while running. A crashed worker's row becomes claimable when the lease expires.

### D-03 — The dispatcher decides nothing
The dispatcher only leases and starts. What to do, and whether to stop, lives in the task's handler. Any "every N minutes, check X" becomes a task with a `due_at`, not a new scheduler.

### D-04 — No duplicate unfinished work
A partial unique index on (`tenant_id`, `kind`, `subject_type`, `subject_id`) `WHERE finished_at IS NULL` makes enqueueing idempotent.

### D-05 — Budget, retry and parking
Each task carries a budget (research steps or tokens). Failures retry with exponential backoff to `max_attempts`, then the task is parked with `outcome = 'parked'` and `last_error` for a human. Parked tasks are visible in an admin view.

### D-06 — Sources of tasks
Tasks are created by: CRM domain events consumed by `ai-service` (BC-12-05), schedules materialised as due rows, and explicit user actions ("Re-enrich"). Consuming events is not agent Kafka; no new agent topics are created.

### D-07 — Outbound safety
Handlers that fetch from the web use one SSRF-guarded client: deny private, loopback, link-local and metadata addresses; re-check after each redirect; cap size and time.

## Consequences
- Good: exactly-once claiming without new infrastructure; easy to inspect with SQL.
- Good: keeps #241 held.
- Cost: polling load; acceptable at CRM volumes, revisit if tasks exceed thousands per minute.

## Alternatives considered
- **Kafka topic per agent kind.** Rejected while #241 is held.
- **Spring `@Scheduled` per job.** Rejected: runs on every replica, no lease, no backlog visibility.
- **Temporal.** Deferred, same as ADR-025.
