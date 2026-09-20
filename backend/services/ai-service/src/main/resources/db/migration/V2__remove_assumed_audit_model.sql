-- An absent model is unknown, not evidence that the historical ADR-002 model ran.
-- Preserve historical rows and immutable V1 checksums.
ALTER TABLE ai.agent_audit_log ALTER COLUMN model SET DEFAULT 'unknown';
