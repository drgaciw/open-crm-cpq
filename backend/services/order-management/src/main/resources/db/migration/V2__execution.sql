CREATE TABLE task_operations (
 operation_key varchar(64) PRIMARY KEY, task_id varchar(36) NOT NULL UNIQUE REFERENCES order_tasks(id),
 order_id varchar(36) NOT NULL REFERENCES orders(id), body_hash varchar(64) NOT NULL,
 status varchar(40) NOT NULL, lease_token varchar(36), lease_until timestamp with time zone,
 worker_id varchar(200), attempt integer NOT NULL DEFAULT 0, next_attempt_at timestamp with time zone,
 deadline_at timestamp with time zone, evidence_reference varchar(500), completed_at timestamp with time zone,
 subscription_id varchar(200), created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX task_operations_deadline ON task_operations(status,deadline_at);
CREATE TABLE order_exceptions (
 id varchar(36) PRIMARY KEY, order_id varchar(36) NOT NULL REFERENCES orders(id), task_id varchar(36) REFERENCES order_tasks(id),
 category varchar(80) NOT NULL, severity varchar(30) NOT NULL, status varchar(30) NOT NULL DEFAULT 'OPEN',
 owner varchar(200), next_action varchar(500) NOT NULL, reason text NOT NULL,
 first_occurred_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_occurred_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 version bigint NOT NULL DEFAULT 0, UNIQUE(order_id,task_id,category)
);
CREATE INDEX order_exceptions_queue ON order_exceptions(status,last_occurred_at);
CREATE TABLE execution_inbox (
 event_id varchar(200) PRIMARY KEY, operation_key varchar(64) NOT NULL, body_hash varchar(64) NOT NULL,
 received_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
