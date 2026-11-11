-- DRG-452: salesperson preference only, not a commercial acceptance or forecast input.
-- Expand-only. Application rollback leaves this unused table and its preference evidence intact.
-- Manual destructive rollback, only after checking/exporting preference data and reverting callers:
-- DROP TABLE quote.quote_primary_selections;
SET search_path TO quote;

CREATE TABLE quote_primary_selections (
    tenant_id VARCHAR(128) NOT NULL,
    deal_external_id VARCHAR(255) NOT NULL,
    selected_quote_id BIGINT NOT NULL REFERENCES quotes(id),
    selected_by VARCHAR(255) NOT NULL,
    selected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, deal_external_id),
    CHECK (length(trim(tenant_id)) > 0),
    CHECK (length(trim(deal_external_id)) > 0)
);

CREATE INDEX idx_quote_comparison_opportunity ON quotes (tenant_id, deal_external_id, id) WHERE is_deleted = false;
