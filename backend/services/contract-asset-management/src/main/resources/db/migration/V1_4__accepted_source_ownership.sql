CREATE TABLE oms_quote_identities (
 identity VARCHAR(255) PRIMARY KEY, quote_id VARCHAR(36) NOT NULL
);
CREATE TABLE oms_accepted_sources (
 scope_id VARCHAR(255) NOT NULL, quote_id VARCHAR(36) NOT NULL, quote_number VARCHAR(255) NOT NULL,
 accepted_revision INTEGER NOT NULL CHECK(accepted_revision>0), snapshot_id VARCHAR(255) NOT NULL UNIQUE,
 customer_id VARCHAR(255) NOT NULL, payload TEXT NOT NULL, payload_hash VARCHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(scope_id,quote_id,accepted_revision)
);
CREATE FUNCTION forbid_oms_source_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Accepted ownership source is immutable'; END;
$$;
CREATE TRIGGER oms_source_immutable BEFORE UPDATE OR DELETE ON oms_accepted_sources FOR EACH ROW EXECUTE FUNCTION forbid_oms_source_mutation();
CREATE TRIGGER oms_identity_immutable BEFORE UPDATE OR DELETE ON oms_quote_identities FOR EACH ROW EXECUTE FUNCTION forbid_oms_source_mutation();
