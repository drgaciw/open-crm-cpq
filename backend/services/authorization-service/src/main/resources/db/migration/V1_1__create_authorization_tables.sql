-- =====================================================
-- Flyway Migration V1_1: Create Authorization Schema
-- =====================================================
-- DRG-307 / BC-19-03. Role and permission model: roles, permissions, the role<->permission grant and the
-- principal<->role assignment (active when revoked_at is null). AuthorizationQueryService.Authorize reads
-- role_assignments joined through roles to permissions (see RoleAssignmentRepository.findEffectivePermissionRows).
-- =====================================================

CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    action VARCHAR(150) NOT NULL,
    resource VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uk_permissions_action_resource UNIQUE (action, resource)
);

-- GrantPermission links a Permission to a Role. No audit columns: the link's lifetime is the grant itself.
CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id),
    permission_id BIGINT NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

-- AssignRole. Active while revoked_at is null; AuthorizationQueryService never considers a revoked row.
CREATE TABLE role_assignments (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    principal_id VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL REFERENCES roles(id),
    assigned_by VARCHAR(255) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    revoked_by VARCHAR(255),
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- Authorize's cache-miss path: active assignments of one principal.
CREATE INDEX idx_role_assignments_principal_active ON role_assignments(principal_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_role_assignments_role ON role_assignments(role_id);

-- Outbox events table for Transactional Outbox pattern (ADR-018). Same baseline every service starts from.
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    error_message VARCHAR(500),
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_outbox_published ON outbox_events(published);
CREATE INDEX idx_outbox_event_type ON outbox_events(event_type);
