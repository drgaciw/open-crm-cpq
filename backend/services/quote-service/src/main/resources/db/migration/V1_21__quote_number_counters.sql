-- Additive. The allocator reconciles committed numeric quote numbers on each allocation,
-- so existing and soft-deleted quotes require no backfill or updates.
-- Drain old quote writers, renewal event consumers, and direct importers before enabling
-- the new binary. Writers that do not use this allocator can still race with it.
-- For binary rollback, drain new writers first and leave the counter table in place.
-- Verify that every current-day suffix and the next number fit the old signed-int
-- parser before reverting. A malformed or oversized suffix makes rollback unsafe.
CREATE TABLE quote_number_counters (
    quote_day DATE PRIMARY KEY,
    last_number NUMERIC NOT NULL CHECK (last_number > 0 AND last_number = trunc(last_number))
);
