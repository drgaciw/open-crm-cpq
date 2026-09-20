-- Copy as a new Flyway migration into EVERY service owning outbox_events before upgrading common.
-- Do not edit historical migrations. Stop old relay instances before starting lease-aware relays.
ALTER TABLE outbox_events ADD COLUMN lease_token VARCHAR(36);
ALTER TABLE outbox_events ADD COLUMN lease_until TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN exhausted_at TIMESTAMPTZ;
CREATE INDEX idx_outbox_delivery_ready ON outbox_events (created_at, id)
    WHERE published = FALSE AND is_deleted = FALSE AND exhausted_at IS NULL;
CREATE INDEX idx_outbox_delivery_exhausted ON outbox_events (exhausted_at, id)
    WHERE published = FALSE AND is_deleted = FALSE AND exhausted_at IS NOT NULL;
