SET search_path TO pricing;

-- DRG-431: application-level overlap checks race under concurrent writers. btree_gist lets a
-- GiST EXCLUDE constraint mix equality on the entry key with daterange overlap. Soft-deleted
-- rows are excluded so history can be replaced. Null start/end are unbounded (same as
-- EffectiveDateRange). Bounds are inclusive, matching the Java overlap rule.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE price_book_entries
    ADD CONSTRAINT price_book_entries_no_overlapping_range
    EXCLUDE USING gist (
        price_book_id WITH =,
        product_external_id WITH =,
        (COALESCE(currency, 'USD')) WITH =,
        unit_of_measure WITH =,
        daterange(effective_start_date, effective_end_date, '[]') WITH &&
    )
    WHERE (is_deleted = false);

ALTER TABLE cost_book_entries
    ADD CONSTRAINT cost_book_entries_no_overlapping_range
    EXCLUDE USING gist (
        cost_book_id WITH =,
        sku WITH =,
        (COALESCE(currency, 'USD')) WITH =,
        unit_of_measure WITH =,
        daterange(effective_start_date, effective_end_date, '[]') WITH &&
    )
    WHERE (is_deleted = false);
