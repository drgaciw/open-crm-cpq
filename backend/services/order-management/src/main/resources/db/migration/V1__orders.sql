CREATE TABLE snapshot_inbox (
 snapshot_id varchar(200) PRIMARY KEY, scope_id varchar(200) NOT NULL, customer_id varchar(200) NOT NULL,
 quote_id varchar(36) NOT NULL, accepted_revision integer NOT NULL, payload text NOT NULL, payload_hash varchar(64) NOT NULL,
 received_at timestamp with time zone NOT NULL, UNIQUE(scope_id,quote_id,accepted_revision)
);
CREATE TABLE orders (
 id varchar(36) PRIMARY KEY, order_number varchar(80) NOT NULL UNIQUE,
 scope_id varchar(200) NOT NULL, customer_id varchar(200) NOT NULL, quote_id varchar(36) NOT NULL,
 quote_number varchar(200) NOT NULL, accepted_revision integer NOT NULL, snapshot_id varchar(200) NOT NULL REFERENCES snapshot_inbox(snapshot_id),
 snapshot_json text NOT NULL, snapshot_hash varchar(64) NOT NULL, status varchar(40) NOT NULL,
 finance_status varchar(40) NOT NULL DEFAULT 'PENDING', manual_hold boolean NOT NULL DEFAULT false,
 version bigint NOT NULL DEFAULT 0, created_at timestamp with time zone NOT NULL, updated_at timestamp with time zone NOT NULL,
 created_by varchar(200) NOT NULL, updated_by varchar(200) NOT NULL, is_deleted boolean NOT NULL DEFAULT false,
 UNIQUE(scope_id,quote_id,accepted_revision)
);
CREATE INDEX orders_scope_customer ON orders(scope_id,customer_id,created_at);
CREATE TABLE order_lines (
 id varchar(36) PRIMARY KEY, order_id varchar(36) NOT NULL REFERENCES orders(id), source_line_id varchar(200) NOT NULL,
 description text NOT NULL, ordered integer NOT NULL CHECK(ordered>0), cancelled integer NOT NULL DEFAULT 0,
 allocated integer NOT NULL DEFAULT 0, shipped integer NOT NULL DEFAULT 0, delivered integer NOT NULL DEFAULT 0,
 fulfilled integer NOT NULL DEFAULT 0, status varchar(40) NOT NULL DEFAULT 'PENDING', UNIQUE(order_id,source_line_id),
 CHECK(cancelled>=0 AND fulfilled>=0 AND cancelled+fulfilled<=ordered),
 CHECK(allocated>=0 AND allocated<=ordered-cancelled-fulfilled),
 CHECK(delivered>=0 AND delivered<=shipped AND shipped<=ordered-cancelled)
);
CREATE TABLE order_tasks (
 id varchar(36) PRIMARY KEY, order_id varchar(36) NOT NULL REFERENCES orders(id), line_id varchar(36) NOT NULL REFERENCES order_lines(id),
 kind varchar(40) NOT NULL, template_version integer NOT NULL, status varchar(40) NOT NULL,
 dispatched_at timestamp with time zone, UNIQUE(order_id,line_id,kind)
);
CREATE TABLE order_holds (
 order_id varchar(36) NOT NULL REFERENCES orders(id), code varchar(240) NOT NULL, PRIMARY KEY(order_id,code)
);
CREATE TABLE order_idempotency (
 scope_id varchar(200) NOT NULL, operation_key varchar(200) NOT NULL, body_hash varchar(64) NOT NULL,
 order_id varchar(36) NOT NULL REFERENCES orders(id), PRIMARY KEY(scope_id,operation_key)
);
CREATE TABLE order_audit (
 id varchar(36) PRIMARY KEY, order_id varchar(36) NOT NULL REFERENCES orders(id), actor varchar(200) NOT NULL,
 occurred_at timestamp with time zone NOT NULL, action varchar(40) NOT NULL, reason text NOT NULL,
 version bigint NOT NULL, evidence_reference varchar(500)
);
CREATE INDEX order_audit_order ON order_audit(order_id,occurred_at);
-- Deploy with a migration owner distinct from OMS_DB_USERNAME. Normal role receives INSERT/SELECT only on audit.

CREATE TABLE order_task_dependencies (
 task_id varchar(36) NOT NULL REFERENCES order_tasks(id), predecessor_id varchar(36) NOT NULL REFERENCES order_tasks(id),
 PRIMARY KEY(task_id,predecessor_id), CHECK(task_id<>predecessor_id)
);
CREATE TABLE outbox_events (
 id BIGSERIAL PRIMARY KEY, uuid varchar(36) NOT NULL UNIQUE, aggregate_type varchar(100) NOT NULL,
 aggregate_id varchar(100) NOT NULL, event_type varchar(100) NOT NULL, payload text NOT NULL,
 published boolean NOT NULL DEFAULT false, published_at timestamp with time zone, error_message varchar(500),
 retry_count integer NOT NULL DEFAULT 0, created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at timestamp with time zone, created_by varchar(100), updated_by varchar(100),
 version bigint NOT NULL DEFAULT 0, is_deleted boolean NOT NULL DEFAULT false,
 lease_token varchar(36), lease_until timestamp with time zone, next_attempt_at timestamp with time zone, exhausted_at timestamp with time zone
);
CREATE INDEX idx_outbox_published ON outbox_events(published);
CREATE INDEX idx_outbox_event_type ON outbox_events(event_type);
-- POSTGRESQL_IMMUTABILITY
CREATE FUNCTION reject_immutable_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'immutable OMS record';
END;
$$;
CREATE TRIGGER order_audit_immutable BEFORE UPDATE OR DELETE ON order_audit FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
CREATE TRIGGER snapshot_inbox_immutable BEFORE UPDATE OR DELETE ON snapshot_inbox FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();

CREATE FUNCTION protect_order_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.snapshot_json,NEW.snapshot_hash,NEW.snapshot_id,NEW.scope_id,NEW.customer_id,NEW.quote_id,NEW.accepted_revision,NEW.quote_number)
 IS DISTINCT FROM ROW(OLD.snapshot_json,OLD.snapshot_hash,OLD.snapshot_id,OLD.scope_id,OLD.customer_id,OLD.quote_id,OLD.accepted_revision,OLD.quote_number) THEN
  RAISE EXCEPTION 'immutable order snapshot';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER order_snapshot_immutable BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION protect_order_snapshot();
