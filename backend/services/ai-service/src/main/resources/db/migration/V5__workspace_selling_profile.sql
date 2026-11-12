-- DRG-524: one active workspace profile per opaque V3/V4 tenant string.
-- Isolation uses tenant predicates, not RLS; no cross-tenant identifier lookup is exposed.
CREATE TABLE ai.workspace_selling_profile (
    tenant_id TEXT PRIMARY KEY,
    company_overview TEXT NOT NULL DEFAULT '',
    product_overview TEXT NOT NULL DEFAULT '',
    value_propositions TEXT NOT NULL DEFAULT '',
    target_segments TEXT NOT NULL DEFAULT '',
    differentiators_competitors TEXT NOT NULL DEFAULT '',
    commercial_guardrails TEXT NOT NULL DEFAULT '',
    tone_guidelines TEXT NOT NULL DEFAULT '',
    company_url TEXT,
    fetched_summary TEXT,
    fetched_at TIMESTAMPTZ,
    summary_sha256 TEXT,
    updated_by TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1)
);
COMMENT ON TABLE ai.workspace_selling_profile IS 'Tenant-scoped structured agent context; fetched website material is untrusted data';
