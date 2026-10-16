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

-- Carry forward the previous scheduler's composite keys before changing stores.
-- A legacy bare subscription id contains no term identity and cannot safely seed a receipt.
DO $$
DECLARE
    emitted record;
    source_id bigint;
    source_end date;
    source_window date;
BEGIN
    FOR emitted IN
        SELECT aggregate_id, min(created_at) AS published_at
        FROM outbox_events
        WHERE aggregate_type = 'Subscription'
          AND (topic = 'cpq.subscription.renewal.eligible'
               OR event_type IN ('cpq.subscription.renewal.eligible', 'SubscriptionRenewalEligible'))
        GROUP BY aggregate_id
    LOOP
        IF emitted.aggregate_id !~ '^[0-9]+:[0-9]{4}-[0-9]{2}-[0-9]{2}:([0-9]{4}-[0-9]{2}-[0-9]{2}|none)$' THEN
            CONTINUE;
        END IF;
        BEGIN
            source_id := split_part(emitted.aggregate_id, ':', 1)::bigint;
            source_end := split_part(emitted.aggregate_id, ':', 2)::date;
            source_window := nullif(split_part(emitted.aggregate_id, ':', 3), 'none')::date;
        EXCEPTION WHEN invalid_datetime_format OR datetime_field_overflow OR numeric_value_out_of_range THEN
            CONTINUE;
        END;
        INSERT INTO renewal_eligibility_publications
            (dedupe_key, subscription_id, term_end, renewal_window, published_at)
        SELECT emitted.aggregate_id, s.id, source_end, source_window, emitted.published_at
        FROM subscriptions s
        WHERE s.id = source_id
        ON CONFLICT (dedupe_key) DO NOTHING;
    END LOOP;
END;
$$;
