CREATE TABLE order_command_audit (
 scope_id varchar(200) NOT NULL, operation_key varchar(200) NOT NULL,
 order_id varchar(36) NOT NULL REFERENCES orders(id), actor varchar(200) NOT NULL,
 reason text NOT NULL, before_version bigint NOT NULL CHECK(before_version>=0),
 after_version bigint NOT NULL CHECK(after_version>=before_version),
 occurred_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(scope_id,operation_key),
 FOREIGN KEY(scope_id,operation_key) REFERENCES order_idempotency(scope_id,operation_key)
);
CREATE INDEX order_command_audit_timeline ON order_command_audit(order_id,occurred_at,operation_key);
CREATE TRIGGER order_command_audit_immutable BEFORE UPDATE OR DELETE ON order_command_audit FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
