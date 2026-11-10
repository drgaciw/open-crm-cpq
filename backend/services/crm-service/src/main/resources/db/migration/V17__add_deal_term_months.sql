-- DRG-636 (RA-102): contract term on a deal, in months.
-- Owner decision SQ1 (2026-10-26): deals.amount stays TCV as entered. The term lets ARR be derived later;
-- nothing is derived or backfilled here, and existing deals keep a NULL term.

ALTER TABLE deals
    ADD COLUMN term_months INTEGER;

ALTER TABLE deals
    ADD CONSTRAINT chk_deals_term_months_positive CHECK (term_months IS NULL OR term_months > 0);

COMMENT ON COLUMN deals.term_months IS 'Contract term in months (DRG-636). NULL = not given. amount is TCV as entered (SQ1).';
