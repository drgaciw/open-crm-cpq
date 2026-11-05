CREATE TABLE order_replacement_requests (
 id varchar(36) PRIMARY KEY,
 order_id varchar(36) NOT NULL UNIQUE REFERENCES orders(id),
 scope_id varchar(200) NOT NULL, customer_id varchar(200) NOT NULL,
 quote_id varchar(36) NOT NULL, accepted_revision integer NOT NULL CHECK (accepted_revision > 0),
 operation_key varchar(200) NOT NULL, event_id varchar(36) NOT NULL UNIQUE,
 reason text NOT NULL, actor varchar(200) NOT NULL,
 order_version bigint NOT NULL, created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(scope_id,operation_key), UNIQUE(scope_id,quote_id,accepted_revision)
);
CREATE TRIGGER replacement_request_immutable BEFORE UPDATE OR DELETE ON order_replacement_requests
 FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();

CREATE TABLE order_replacement_lineage (
 predecessor_order_id varchar(36) PRIMARY KEY REFERENCES orders(id),
 successor_order_id varchar(36) NOT NULL UNIQUE REFERENCES orders(id),
 replacement_request_id varchar(36) NOT NULL UNIQUE REFERENCES order_replacement_requests(id),
 created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(predecessor_order_id <> successor_order_id)
);
CREATE TRIGGER replacement_lineage_immutable BEFORE UPDATE OR DELETE ON order_replacement_lineage
 FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
