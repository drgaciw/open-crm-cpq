-- DRG-664 (S-T3). Additive subscriptions.renewal_mode (AUTO_ROLL | MANUAL).
-- Slot coordination: main tip was V1_15. Wave 2 sibling drafts claimed:
--   #805 DRG-665 → V1_16/V1_17; #806 DRG-662 → V1_18; #808 DRG-666 → V1_19;
--   DRG-663 in flight → V1_20. This story uses V1_21 only.
-- Does NOT alter chk_contracts_dates / PERPETUAL null end_date (Proposed / NO-GO —
-- ships only on owner fork + ADR acceptance + dry-run count of PERPETUAL rows).
-- Legacy rows stay NULL (fail-safe = today's no-auto-roll behaviour).
-- Rollback: db/rollback/V1_21__drop_subscription_renewal_mode.sql

ALTER TABLE subscriptions
    ADD COLUMN renewal_mode VARCHAR(16);

ALTER TABLE subscriptions
    ADD CONSTRAINT chk_subscription_renewal_mode CHECK (
        renewal_mode IS NULL
            OR renewal_mode IN ('AUTO_ROLL', 'MANUAL')
    );
