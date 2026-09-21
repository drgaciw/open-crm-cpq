CREATE TABLE recovery_command_tombstones (
 scope_id varchar(200) NOT NULL, operation_key varchar(200) NOT NULL,
 order_id varchar(36) NOT NULL REFERENCES orders(id), recovery_operation_key varchar(200) NOT NULL,
 actor varchar(200) NOT NULL, reason text NOT NULL, abandoned_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 order_version bigint NOT NULL, PRIMARY KEY(scope_id,operation_key),
 FOREIGN KEY(scope_id,operation_key) REFERENCES order_idempotency(scope_id,operation_key)
);
CREATE TABLE recovery_receipts (
 scope_id varchar(200) NOT NULL, operation_key varchar(200) NOT NULL, order_id varchar(36) NOT NULL REFERENCES orders(id),
 exception_id varchar(36) REFERENCES order_exceptions(id), action varchar(40) NOT NULL, result varchar(40) NOT NULL,
 recorded_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, actor varchar(200) NOT NULL, reason text NOT NULL,
 PRIMARY KEY(scope_id,operation_key), FOREIGN KEY(scope_id,operation_key) REFERENCES order_idempotency(scope_id,operation_key)
);
CREATE TABLE recovery_daily_runs (
 scope_id varchar(200) NOT NULL, run_date date NOT NULL, upper_bound timestamptz NOT NULL,
 cursor_created_at timestamptz NOT NULL DEFAULT '-infinity', cursor_id varchar(36) NOT NULL DEFAULT '',
 scanned bigint NOT NULL DEFAULT 0, completed_at timestamptz, PRIMARY KEY(scope_id,run_date)
);
CREATE TABLE recovery_findings (
 scope_id varchar(200) NOT NULL, run_date date NOT NULL, order_id varchar(36) NOT NULL REFERENCES orders(id),
 category varchar(80) NOT NULL, detail varchar(500) NOT NULL, recorded_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(scope_id,run_date,order_id,category), FOREIGN KEY(scope_id,run_date) REFERENCES recovery_daily_runs(scope_id,run_date)
);
CREATE INDEX recovery_exception_order_status ON order_exceptions(order_id,status,first_occurred_at,id);
CREATE TRIGGER recovery_tombstones_immutable BEFORE UPDATE OR DELETE ON recovery_command_tombstones FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
CREATE TRIGGER recovery_receipts_immutable BEFORE UPDATE OR DELETE ON recovery_receipts FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
CREATE TRIGGER recovery_findings_immutable BEFORE UPDATE OR DELETE ON recovery_findings FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
