-- Local identities never enter Jev state. Claims fence duplicate evaluation across replicas.
CREATE UNIQUE INDEX jev_decision_log_uuid ON jev_decision_log(uuid);
CREATE TABLE order_advisory_claims (
 id uuid PRIMARY KEY, dedup_key char(64) NOT NULL UNIQUE,
 scope_id varchar(200) NOT NULL, customer_id varchar(200) NOT NULL,
 order_id varchar(36) NOT NULL REFERENCES orders(id), order_version bigint NOT NULL,
 exception_id varchar(36) NOT NULL REFERENCES order_exceptions(id), exception_version bigint NOT NULL,
 features_hash char(64) NOT NULL, evaluator_model varchar(128) NOT NULL,
 rubric_version varchar(64) NOT NULL, created_at timestamptz NOT NULL, expires_at timestamptz NOT NULL,
 CHECK (expires_at > created_at)
);
CREATE INDEX order_advisory_claim_subject ON order_advisory_claims(scope_id,customer_id,exception_id,created_at);
CREATE TABLE order_advisory_decisions (
 claim_id uuid PRIMARY KEY REFERENCES order_advisory_claims(id),
 decision_uuid uuid NOT NULL UNIQUE REFERENCES jev_decision_log(uuid)
);
CREATE TRIGGER order_advisory_claim_immutable BEFORE UPDATE OR DELETE ON order_advisory_claims
 FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
CREATE TRIGGER order_advisory_decision_immutable BEFORE UPDATE OR DELETE ON order_advisory_decisions
 FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
-- Existing runtime-grants.sql gives new tables SELECT/INSERT only; no UPDATE/DELETE needed.
