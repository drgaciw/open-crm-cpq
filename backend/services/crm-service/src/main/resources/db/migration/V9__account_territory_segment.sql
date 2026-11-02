-- DRG-483 / BC-12-03: territory and segment assignment for an account (organization).
-- One assignment row per organization. Rules match geography (country/state), industry, and size.
-- Application queries bind values as parameters; this script contains no interpolated input.

CREATE TABLE territory_assignment_rules (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                VARCHAR(36)  NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    country             VARCHAR(255) NOT NULL,
    state               VARCHAR(255),
    industry            VARCHAR(255) NOT NULL,
    min_employee_count  INTEGER,
    max_employee_count  INTEGER,
    territory_code      VARCHAR(255) NOT NULL,
    segment_code        VARCHAR(255) NOT NULL,
    priority            INTEGER      NOT NULL DEFAULT 100,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    version             BIGINT       DEFAULT 0,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_territory_rule_text CHECK (
        length(btrim(name)) > 0
        AND length(btrim(country)) > 0
        AND length(btrim(industry)) > 0
        AND length(btrim(territory_code)) > 0
        AND length(btrim(segment_code)) > 0
        AND (state IS NULL OR length(btrim(state)) > 0)
    ),
    CONSTRAINT chk_territory_rule_employee_bounds CHECK (
        (min_employee_count IS NULL OR min_employee_count >= 0)
        AND (max_employee_count IS NULL OR max_employee_count >= 0)
        AND (min_employee_count IS NULL OR max_employee_count IS NULL
             OR min_employee_count <= max_employee_count)
    ),
    CONSTRAINT chk_territory_rule_priority CHECK (priority >= 0)
);

CREATE INDEX idx_territory_rule_match
    ON territory_assignment_rules (country, industry)
    WHERE is_deleted = FALSE AND active = TRUE;

CREATE TABLE organization_territory_assignments (
    id              BIGSERIAL PRIMARY KEY,
    uuid            VARCHAR(36)  NOT NULL UNIQUE,
    organization_id BIGINT       NOT NULL UNIQUE REFERENCES organizations (id),
    territory_code  VARCHAR(255) NOT NULL,
    segment_code    VARCHAR(255) NOT NULL,
    rule_id         BIGINT       REFERENCES territory_assignment_rules (id) ON DELETE SET NULL,
    assigned_at     TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    version         BIGINT       DEFAULT 0,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_org_territory_assignment_codes CHECK (
        length(btrim(territory_code)) > 0
        AND length(btrim(segment_code)) > 0
    )
);

CREATE INDEX idx_org_territory_assignment_rule
    ON organization_territory_assignments (rule_id);

COMMENT ON TABLE territory_assignment_rules IS
    'DRG-483 rules keyed by country/state, industry, and employee-count bounds.';
COMMENT ON TABLE organization_territory_assignments IS
    'DRG-483 current territory and segment for one organization. is_deleted means unassigned.';
COMMENT ON COLUMN territory_assignment_rules.priority IS
    'Lower priority wins when more than one rule matches. Specificity then id break remaining ties.';
COMMENT ON COLUMN territory_assignment_rules.state IS
    'NULL matches any state in the country. A non-null state matches only that state.';
COMMENT ON COLUMN territory_assignment_rules.min_employee_count IS
    'Inclusive lower bound. NULL means no lower bound.';
COMMENT ON COLUMN territory_assignment_rules.max_employee_count IS
    'Inclusive upper bound. NULL means no upper bound.';
