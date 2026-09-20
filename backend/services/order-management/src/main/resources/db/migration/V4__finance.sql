CREATE TABLE finance_requests (
 request_key varchar(64) PRIMARY KEY, order_id varchar(36) NOT NULL REFERENCES orders(id),
 line_id varchar(36) NOT NULL UNIQUE REFERENCES order_lines(id), scope_id varchar(200) NOT NULL,
 quantity integer NOT NULL CHECK(quantity>0), gross_cents bigint NOT NULL CHECK(gross_cents>=0),
 discount_cents bigint NOT NULL CHECK(discount_cents>=0), tax_cents bigint NOT NULL CHECK(tax_cents>=0),
 total_cents bigint NOT NULL CHECK(total_cents=gross_cents-discount_cents+tax_cents AND total_cents>=0),
 payload text NOT NULL, created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE finance_acknowledgments (
 id varchar(36) PRIMARY KEY, request_key varchar(64) NOT NULL REFERENCES finance_requests(request_key),
 scope_id varchar(200) NOT NULL, external_reference varchar(500) NOT NULL, body_hash varchar(64) NOT NULL,
 quantity integer NOT NULL CHECK(quantity>0), outcome varchar(30) NOT NULL CHECK(outcome IN ('ACKNOWLEDGED','REJECTED','UNKNOWN')),
 gross_cents bigint NOT NULL CHECK(gross_cents>=0), discount_cents bigint NOT NULL CHECK(discount_cents>=0),
 tax_cents bigint NOT NULL CHECK(tax_cents>=0), total_cents bigint NOT NULL CHECK(total_cents=gross_cents-discount_cents+tax_cents AND total_cents>=0),
 occurred_at timestamp with time zone NOT NULL, recorded_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 actor varchar(200) NOT NULL, reason text NOT NULL, UNIQUE(scope_id,external_reference)
);
CREATE INDEX finance_ack_request ON finance_acknowledgments(request_key);
CREATE TRIGGER finance_request_immutable BEFORE UPDATE OR DELETE ON finance_requests FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
CREATE TRIGGER finance_ack_immutable BEFORE UPDATE OR DELETE ON finance_acknowledgments FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
