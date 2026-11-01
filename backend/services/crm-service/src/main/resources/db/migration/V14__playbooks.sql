-- DRG-486 / BC-13-02 playbook definitions.
-- definition_version is a local integer starting at 1. It increases when steps or
-- questions change. BC-18 document versioning is not landed, so this column is not
-- a promoted revision and older definitions are not retained.
-- Steps store a DealStage name. They are not coupled to buying roles.

CREATE TABLE playbooks (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    definition_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_playbooks_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_playbooks_definition_version_positive CHECK (definition_version >= 1)
);

CREATE TABLE playbook_steps (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    playbook_id BIGINT NOT NULL,
    stage VARCHAR(30) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_playbook_steps_playbook FOREIGN KEY (playbook_id) REFERENCES playbooks (id),
    CONSTRAINT ck_playbook_steps_stage CHECK (stage IN (
        'PROSPECTING',
        'QUALIFICATION',
        'NEEDS_ANALYSIS',
        'PROPOSAL',
        'NEGOTIATION',
        'WON',
        'LOST'
    )),
    CONSTRAINT ck_playbook_steps_sort_order CHECK (sort_order >= 0),
    CONSTRAINT uq_playbook_steps_stage_order UNIQUE (playbook_id, stage, sort_order)
);

CREATE TABLE playbook_questions (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    step_id BIGINT NOT NULL,
    prompt TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_playbook_questions_step FOREIGN KEY (step_id) REFERENCES playbook_steps (id),
    CONSTRAINT ck_playbook_questions_prompt_not_blank CHECK (length(btrim(prompt)) > 0),
    CONSTRAINT ck_playbook_questions_sort_order CHECK (sort_order >= 0),
    CONSTRAINT uq_playbook_questions_order UNIQUE (step_id, sort_order)
);

COMMENT ON TABLE playbooks IS
    'DRG-486 sales playbook. definition_version is a local integer because BC-18 is not landed.';
COMMENT ON COLUMN playbooks.definition_version IS
    'Local definition integer starting at 1. Increments when steps or questions change. Not a BC-18 revision.';
COMMENT ON COLUMN playbook_steps.stage IS
    'DealStage name. Not a buying role.';
COMMENT ON COLUMN playbook_questions.prompt IS
    'Question text asked when the step is the next incomplete step for its stage.';
