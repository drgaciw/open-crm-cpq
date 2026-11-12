-- ADR-018 backlog triage: allow status SKIPPED, a terminal state an operator sets with the runbook SQL for a row that
-- is deliberately not sent (a historic backlog row, or a FAILED row given up on). Like PUBLISHED it no longer blocks
-- its aggregate and is removed after the retention period; unlike PUBLISHED it records that the broker never got it.
-- Widening the check cannot fail, so it is added NOT VALID and validated separately (no long exclusive lock).
ALTER TABLE outbox_events DROP CONSTRAINT IF EXISTS ck_outbox_events_status;
ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'SKIPPED')) NOT VALID;
ALTER TABLE outbox_events VALIDATE CONSTRAINT ck_outbox_events_status;
