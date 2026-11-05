CREATE TABLE fulfillment_evidence (
 id varchar(36) PRIMARY KEY, order_id varchar(36) NOT NULL REFERENCES orders(id), line_id varchar(36) NOT NULL REFERENCES order_lines(id),
 scope_id varchar(200) NOT NULL, kind varchar(30) NOT NULL, quantity integer NOT NULL CHECK(quantity>0),
 external_reference varchar(500) NOT NULL, allocation_reference varchar(500), shipment_reference varchar(500),
 occurred_at timestamp with time zone NOT NULL, expires_at timestamp with time zone, actor varchar(200) NOT NULL,
 reason text NOT NULL, body_hash varchar(64) NOT NULL, semantic_hash varchar(64) NOT NULL,
 recorded_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(kind<>'ALLOCATION' OR (expires_at IS NOT NULL AND expires_at>occurred_at))
);
CREATE INDEX fulfillment_evidence_order ON fulfillment_evidence(order_id,recorded_at);
CREATE TABLE fulfillment_evidence_state (
 evidence_id varchar(36) PRIMARY KEY REFERENCES fulfillment_evidence(id), status varchar(30) NOT NULL,
 detail varchar(500), applied_at timestamp with time zone
);
CREATE TABLE fulfillment_evidence_identity (
 scope_id varchar(200) NOT NULL, kind varchar(30) NOT NULL, external_reference varchar(500) NOT NULL,
 evidence_id varchar(36) NOT NULL REFERENCES fulfillment_evidence(id), PRIMARY KEY(scope_id,kind,external_reference)
);
CREATE TABLE fulfillment_evidence_receipts (
 scope_id varchar(200) NOT NULL, operation_key varchar(200) NOT NULL, evidence_id varchar(36) NOT NULL REFERENCES fulfillment_evidence(id),
 PRIMARY KEY(scope_id,operation_key), FOREIGN KEY(scope_id,operation_key) REFERENCES order_idempotency(scope_id,operation_key)
);
CREATE TABLE allocation_lots (
 id varchar(36) PRIMARY KEY, order_id varchar(36) NOT NULL REFERENCES orders(id), line_id varchar(36) NOT NULL REFERENCES order_lines(id),
 evidence_id varchar(36) NOT NULL REFERENCES fulfillment_evidence(id), external_reference varchar(500) NOT NULL,
 quantity integer NOT NULL CHECK(quantity>0), consumed integer NOT NULL DEFAULT 0, released integer NOT NULL DEFAULT 0,
 occurred_at timestamp with time zone NOT NULL, expires_at timestamp with time zone NOT NULL,
 CHECK(consumed>=0 AND released>=0 AND consumed+released<=quantity), UNIQUE(order_id,line_id,external_reference)
);
CREATE INDEX allocation_lots_expiry ON allocation_lots(expires_at);
-- POSTGRESQL_IMMUTABILITY
CREATE TRIGGER fulfillment_evidence_immutable BEFORE UPDATE OR DELETE ON fulfillment_evidence FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
