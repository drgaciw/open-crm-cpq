-- Advisory lead score provenance (ADR-015). leads.score was always 0 before this; score_source tells an
-- unscored lead (NULL) from a scored one ('JEV'). Probabilities are stored as JSON text, not JSONB: the
-- service boots with ddl-auto=validate and the value is only ever read back whole.

ALTER TABLE leads
    ADD COLUMN score_source         VARCHAR(16),
    ADD COLUMN score_confidence     NUMERIC(5, 4),
    ADD COLUMN score_probabilities  TEXT,
    ADD COLUMN score_rubric_version VARCHAR(64),
    ADD COLUMN score_model          VARCHAR(128),
    ADD COLUMN scored_at            TIMESTAMPTZ;
