-- Publication receipts deliberately outlive outbox retention and inbox cleanup.
-- V1_7_1 follows contract documents without overtaking future V1_8/V1_9 migrations.
CREATE TABLE renewal_eligibility_publications (
    dedupe_key varchar(100) PRIMARY KEY,
    subscription_id bigint NOT NULL REFERENCES subscriptions (id),
    term_end date NOT NULL,
    renewal_window date,
    published_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_renewal_publication_subscription
    ON renewal_eligibility_publications (subscription_id);
