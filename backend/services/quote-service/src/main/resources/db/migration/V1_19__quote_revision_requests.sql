CREATE TABLE quote_revision_operations (
 operation_key VARCHAR(255) PRIMARY KEY, request_hash VARCHAR(64) NOT NULL,
 quote_id VARCHAR(36) NOT NULL, accepted_revision INTEGER NOT NULL CHECK(accepted_revision>0),
 order_id VARCHAR(255) NOT NULL, scope_id VARCHAR(255) NOT NULL, customer_id VARCHAR(255) NOT NULL,
 reason TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(quote_id,accepted_revision)
);
CREATE TABLE quote_revision_inbox (
 event_id VARCHAR(255) PRIMARY KEY, operation_key VARCHAR(255) NOT NULL REFERENCES quote_revision_operations(operation_key),
 request_hash VARCHAR(64) NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE quotes ADD COLUMN oms_correction_operation_key VARCHAR(255) REFERENCES quote_revision_operations(operation_key);
CREATE TRIGGER quote_revision_operation_immutable BEFORE UPDATE OR DELETE ON quote_revision_operations
 FOR EACH ROW EXECUTE FUNCTION forbid_accepted_snapshot_mutation();
CREATE TRIGGER quote_revision_inbox_immutable BEFORE UPDATE OR DELETE ON quote_revision_inbox
 FOR EACH ROW EXECUTE FUNCTION forbid_accepted_snapshot_mutation();
