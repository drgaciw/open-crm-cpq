-- DRG-487 / BC-13-03: guided selling session, answers, and advisory derived selections.
-- Does not alter deals, organizations, or the configuration-engine schema.
-- deal_id is an opportunity reference stored as given. It is not a foreign key:
-- this migration does not take a lock on deals, which other changes own.
-- The configuration engine remains the constraint solver. Rows here never mark a deal won.

CREATE TABLE guided_selling_sessions (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    deal_id BIGINT,
    status VARCHAR(30) NOT NULL,
    product_id VARCHAR(255),
    configuration_session_id BIGINT,
    recorded_locally BOOLEAN NOT NULL DEFAULT FALSE,
    seller_actor VARCHAR(255) NOT NULL,
    rationale VARCHAR(500),
    derived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_guided_sessions_status CHECK (status IN ('OPEN', 'DERIVED'))
);

CREATE INDEX idx_guided_sessions_deal ON guided_selling_sessions (deal_id);
CREATE INDEX idx_guided_sessions_status ON guided_selling_sessions (status);

COMMENT ON TABLE guided_selling_sessions IS
    'DRG-487 guided selling session. DERIVED means an advisory solution was recorded, not that a deal was won.';
COMMENT ON COLUMN guided_selling_sessions.configuration_session_id IS
    'Configuration-engine session id when the HTTP starter reached it. NULL when the base URL was blank and the intent was recorded locally.';
COMMENT ON COLUMN guided_selling_sessions.recorded_locally IS
    'True when opencrm.configuration.base-url was blank and no configuration HTTP call was made.';
COMMENT ON COLUMN guided_selling_sessions.seller_actor IS
    'Actor from the verified token (CurrentActor), never from the request body.';

CREATE TABLE guided_selling_answers (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    session_id BIGINT NOT NULL REFERENCES guided_selling_sessions (id),
    question_key VARCHAR(64) NOT NULL,
    answer_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_guided_answer_question UNIQUE (session_id, question_key)
);

COMMENT ON TABLE guided_selling_answers IS
    'One current answer per question on a guided selling session. A later answer for the same key replaces the value.';

CREATE TABLE guided_selling_selections (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    session_id BIGINT NOT NULL REFERENCES guided_selling_sessions (id),
    option_key VARCHAR(128) NOT NULL,
    option_value VARCHAR(255) NOT NULL,
    advisory BOOLEAN NOT NULL DEFAULT TRUE,
    rationale VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_guided_selection_option UNIQUE (session_id, option_key),
    CONSTRAINT ck_guided_selection_advisory CHECK (advisory = TRUE)
);

COMMENT ON TABLE guided_selling_selections IS
    'Pre-selected configuration options from needs derivation. Always advisory; the configuration engine enforces constraints.';
