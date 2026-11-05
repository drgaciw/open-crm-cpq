-- Copy as a new Flyway migration into EVERY service owning outbox_events before upgrading common.
-- Do not edit historical migrations. Stop old relay instances before starting lease-aware relays.
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS lease_token VARCHAR(36);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS exhausted_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_outbox_delivery_ready ON outbox_events (created_at, id)
    WHERE published = FALSE AND is_deleted = FALSE AND exhausted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_outbox_delivery_exhausted ON outbox_events (exhausted_at, id)
    WHERE published = FALSE AND is_deleted = FALSE AND exhausted_at IS NOT NULL;
