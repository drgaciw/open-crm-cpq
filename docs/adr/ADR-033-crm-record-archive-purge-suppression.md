# ADR-033: CRM record archive, retention purge and suppression list

**Status:** Accepted on 2026-11-03 by the project owner ([delegation log](https://app.notion.com/p/402f7cb4e4df8167be33f86e92adfc82)). Proposed on 2026-10-15.
**Date:** 2026-10-15
**Deciders:** Project owner (drg amtd), 2026-11-03.
**Relates to:** BC-19-04 (consent and privacy record), BC-20-05 (retention and legal hold), [ADR-030](ADR-030-activity-timeline-and-mailbox-ingestion.md) (auto-capture). Does **not** touch the R2 price-book list-row delete hold.
**Source pattern:** [trycompai/crm](https://github.com/trycompai/crm) `apps/api/src/archive/*`, `SuppressedContact`, `SuppressedDomain`, at `6d4793d`.
**Does not authorize:** any code or migration.

---

## Context

`crm-service` has an `is_deleted` flag and nothing else. Erasure requests (GDPR Art. 17) need real deletion. Once mail sync and enrichment exist (ADR-030, ADR-028), a deleted person can come straight back on the next sync or import. The source solves this with archive, a retention purge and a suppression list that every intake path checks.

## Decision

### D-01 — Three states
`ACTIVE` → `ARCHIVED` (hidden from lists, restorable, still counted for audit) → `PURGED` (row and dependents removed). `is_deleted` maps to `ARCHIVED` in the migration.

### D-02 — Retention purge
Each tenant sets a retention period for archived records (default 90 days). A purge task (ADR-029) removes records past retention. A BC-20-05 legal hold blocks purge for the held subject.

### D-03 — Erasure on demand
An authorised user can purge a person immediately. The purge removes the contact, its facts (ADR-028), activities and mail it is the only business participant in (ADR-030), and its custom-field values (ADR-031).

### D-04 — Suppression list
Purging a person writes their normalised email as a salted hash to `suppressed_identity` (tenant, kind `EMAIL`|`DOMAIN`, hash, reason, created_at). Admins can suppress a whole domain. Import, mail auto-capture, enrichment and API create all refuse suppressed identities with a clear error.

### D-05 — Audit keeps the fact, not the content
The audit trail records that a purge happened, by whom, and why. It does not keep the purged content.

## Consequences
- Good: erasure works and stays done.
- Cost: every intake path must call the suppression check; a contract test enforces it.
- Risk: hashing means a suppressed email cannot be listed back in clear; that is the intent.

## Alternatives considered
- **Soft delete only.** Rejected: not erasure.
- **Plain-text suppression list.** Rejected: keeps personal data after erasure.
