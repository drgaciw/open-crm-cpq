# ADR-030: Activity timeline and mailbox/calendar ingestion (lifts the Activities hold)

**Status:** Accepted on 2026-11-03 by the project owner ([delegation log](https://app.notion.com/p/402f7cb4e4df8167be33f86e92adfc82)). Proposed on 2026-10-15. **The Activities hold in ARCHITECTURE.md was lifted 2026-11-03 ([delegation log, 7:24 PM CT entry](https://app.notion.com/p/402f7cb4e4df8167be33f86e92adfc82)).**
**Date:** 2026-10-15
**Deciders:** Project owner (drg amtd), 2026-11-03.
**Relates to:** ARCHITECTURE.md §2, §9 (Activities hold), [ADR-016](ADR-016-identity-authentication-service-trust.md) (identity), [ADR-033](ADR-033-crm-record-archive-purge-suppression.md) (suppression), BC-19-04 consent, BC-20-05 retention.
**Source pattern:** [trycompai/crm](https://github.com/trycompai/crm) `Activity`, `MailboxSync`, `EmailThread`, `CalendarEvent` models and `docs/plan/gmail-calendar-plan.md`, at `6d4793d`.
**Does not authorize:** any code, path, OAuth app registration or schema.

---

## Context

The hold says: do not invent `/api/v1/activities/**`. It exists because no design said who owns activities or how mail gets in. Without a timeline, the CRM half of the product cannot show a relationship, and agents have no first-party evidence (ADR-028's two strongest evidence kinds — "replied on a synced thread" and "signature block" — both need mail).

The source runs as a single-tenant internal tool and lets its agent read every mail body. We are multi-tenant and sell to customers, so the design must narrow access.

## Decision

### D-01 — `crm-service` owns activities
Activities live in `crm-service`'s schema (ADR-008). Path: `/api/v1/activities/**`, matching the existing CRM predicates (`/api/v1/leads`, `/deals`, `/contacts`, `/organizations`). When Accepted, this ADR is the decision that replaces the "Activities (deferred)" section of [CRM_API_PARITY.md](../CRM_API_PARITY.md): the gateway predicate, the Flyway table, the controller and the `frontend/libs/crm` client are added together, and the legacy `cpq_beta/crm/api/activities.py` inventory row is mapped. Mailbox endpoints sit under `/api/v1/mailboxes/**`.

### D-02 — Activity model
Types: `NOTE`, `TASK` (due, completed), `EMAIL` (links one thread), `MEETING` (links one calendar event). Each activity links to any of organization, contact, deal; at least one is required. Author is the human or agent actor.

### D-03 — Mailbox connection is a per-user grant, not sign-in
A seller connects Google or Microsoft 365 with an OAuth grant separate from Entra sign-in (ADR-016). Least scopes (read mail metadata and bodies, read calendar). Refresh tokens are encrypted at rest with a tenant key; disconnect deletes them and stops sync. A connector module inside `crm-service` runs the sync; it may later move to its own service.

### D-04 — Incremental sync and ingest filter
Each mailbox has a cursor (provider history id or delta token), `last_synced_at`, `retry_after` and `last_error`. Personal mail is dropped at ingest, not stored and hidden: a thread is kept only when a participant is a known contact or belongs to a non-free-mail business domain, and no participant is suppressed (ADR-033).

### D-05 — Visibility narrower than the source
By default other users in the tenant see that a thread or meeting happened (participants, subject, time). Bodies are visible to the mailbox owner and to people the owner shares with. Agents read bodies only for the owner's records and only within a team agent's declared scope (ADR-034).

### D-06 — Consent and retention
Processing depends on BC-19-04 consent records. Synced content follows BC-20-05 retention and ADR-033 purge.

## Consequences
- Good: unblocks the timeline, auto-capture and first-party evidence.
- Cost: two provider integrations, token security, provider review for restricted scopes.
- Risk: privacy. D-04 and D-05 are the mitigations; a DPIA is needed before any tenant turns it on.

## Alternatives considered
- **Keep the hold, manual notes only.** Rejected for Phase 2: the CRM stays empty.
- **BCC-to-CRM address.** Kept as a fallback for tenants that refuse OAuth; it does not give calendar.
- **Store all mail, hide personal.** Rejected: stored-then-hidden is still stored.
