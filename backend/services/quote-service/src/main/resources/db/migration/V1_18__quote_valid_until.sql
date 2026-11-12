-- DRG-447 / BC-05-01. CreateQuote: ValidityPeriod. The date through which a quote is valid.
-- Nullable; QuoteService.create backfills new rows with a default validity window. Existing rows are left NULL --
-- their validity was never tracked, and a backfilled guess would misrepresent quotes already in the pipeline.
-- Rollback: ALTER TABLE quote.quotes DROP COLUMN valid_until;
SET search_path TO quote;

ALTER TABLE quotes ADD COLUMN valid_until DATE;
