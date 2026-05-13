SET search_path TO pricing;

ALTER TABLE pricing_audit_log
    ADD COLUMN rule_set_version INTEGER,
    ADD COLUMN price_book_id BIGINT,
    ADD COLUMN price_book_version BIGINT,
    ADD COLUMN price_book_snapshot TEXT;
