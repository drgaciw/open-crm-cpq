-- =====================================================
-- Flyway Migration V1_6: FreezeConfiguration / ComputeHash (DRG-428, BC-02-05)
-- =====================================================
-- Description: adds the frozen-configuration snapshot columns (configuration_hash, frozen_at) and widens
-- the status CHECK to allow FROZEN. Widening a CHECK cannot fail on existing rows, so it is added NOT VALID
-- and validated separately (no long exclusive lock), matching V1_4's pattern.
-- =====================================================

ALTER TABLE configuration_sessions
    ADD COLUMN configuration_hash VARCHAR(64),
    ADD COLUMN frozen_at TIMESTAMPTZ;

ALTER TABLE configuration_sessions DROP CONSTRAINT IF EXISTS chk_config_session_status;
ALTER TABLE configuration_sessions
    ADD CONSTRAINT chk_config_session_status CHECK (status IN (
        'IN_PROGRESS', 'COMPLETED', 'EXPIRED', 'CANCELLED', 'FROZEN'
    )) NOT VALID;
ALTER TABLE configuration_sessions VALIDATE CONSTRAINT chk_config_session_status;

CREATE INDEX idx_config_session_hash ON configuration_sessions(configuration_hash);
