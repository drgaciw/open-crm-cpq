-- ============================================================
-- AI Service — Flyway V3
-- Rule optimization experiments (Wave 3 / Price-Drools port)
-- No MCP tooling; apply-winner requires DEAL_DESK or ADMIN.
-- ============================================================

CREATE TABLE IF NOT EXISTS ai.rule_experiment (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid(),
    name                    VARCHAR(255)    NOT NULL,
    description             TEXT,
    rule_id                 BIGINT          NOT NULL,
    rule_name               VARCHAR(255),
    rule_set_id             BIGINT,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'CREATED',
    control_traffic_pct     DOUBLE PRECISION NOT NULL DEFAULT 0.20,
    winning_variant_id      UUID,
    conclusion              TEXT,
    applied_draft_rule_id   BIGINT,
    applied_at              TIMESTAMPTZ,
    applied_by              VARCHAR(255),
    created_by              VARCHAR(255),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_rule_experiment PRIMARY KEY (id),
    CONSTRAINT chk_rule_experiment_status CHECK (
        status IN ('CREATED', 'RUNNING', 'COMPLETED', 'FAILED', 'APPLIED')
    )
);

CREATE INDEX IF NOT EXISTS idx_rule_experiment_rule_id ON ai.rule_experiment (rule_id);
CREATE INDEX IF NOT EXISTS idx_rule_experiment_status ON ai.rule_experiment (status);

CREATE TABLE IF NOT EXISTS ai.rule_experiment_variant (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid(),
    experiment_id           UUID            NOT NULL,
    variant_name            VARCHAR(255)    NOT NULL,
    description             TEXT,
    rule_content            TEXT            NOT NULL,
    control_variant         BOOLEAN         NOT NULL DEFAULT FALSE,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    overall_score           DOUBLE PRECISION NOT NULL DEFAULT 0,
    revenue_impact          DOUBLE PRECISION NOT NULL DEFAULT 0,
    conversion_rate_impact  DOUBLE PRECISION NOT NULL DEFAULT 0,
    average_execution_ms    DOUBLE PRECISION NOT NULL DEFAULT 0,
    sample_count            INTEGER         NOT NULL DEFAULT 0,
    average_discount        DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_revenue           DOUBLE PRECISION NOT NULL DEFAULT 0,
    customer_satisfaction   DOUBLE PRECISION NOT NULL DEFAULT 0,
    metrics_json            JSONB,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_rule_experiment_variant PRIMARY KEY (id),
    CONSTRAINT fk_variant_experiment FOREIGN KEY (experiment_id)
        REFERENCES ai.rule_experiment (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rule_experiment_variant_exp
    ON ai.rule_experiment_variant (experiment_id);

COMMENT ON TABLE ai.rule_experiment IS 'Price-Drools-style rule A/B experiments; apply creates DRAFT only';
COMMENT ON TABLE ai.rule_experiment_variant IS 'Control and challenger DRL variants with recorded metrics';
