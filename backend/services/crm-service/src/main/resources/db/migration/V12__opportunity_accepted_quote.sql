-- DRG-485 / BC-13-01: record the accepted quote that wins an opportunity (deal).
-- Nullable so open and lost deals stay valid. No cross-service foreign key: the quote lives in quote-service.
-- Added only when the column is absent. QuoteAcceptedDealProcessor writes it when that processor applies the win.

ALTER TABLE deals
    ADD COLUMN IF NOT EXISTS accepted_quote_id VARCHAR(255);

COMMENT ON COLUMN deals.accepted_quote_id IS
    'Quote id recorded when an accepted quote is applied to this deal (quote UUID, otherwise quote number). NULL until then. DRG-485.';
