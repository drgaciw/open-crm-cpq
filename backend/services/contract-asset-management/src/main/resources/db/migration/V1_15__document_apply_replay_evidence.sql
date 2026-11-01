-- DRG-621: replay evidence is stored with the terminal unit in the same transaction.
-- Existing terminal rows intentionally remain null: their observed pins and block reasons
-- cannot be reconstructed safely and apply must fail closed until reconciled.
ALTER TABLE amendment_application_units
    ADD COLUMN attempt_revision_no integer,
    ADD COLUMN attempt_pin_fingerprint varchar(67),
    ADD COLUMN block_reason varchar(64),
    ADD CONSTRAINT chk_apply_attempt_revision CHECK (attempt_revision_no IS NULL OR attempt_revision_no >= 1),
    ADD CONSTRAINT chk_apply_attempt_pin CHECK (
        attempt_pin_fingerprint IS NULL OR attempt_pin_fingerprint ~ '^v1:[0-9a-f]{64}$'
    );

-- Rollback only after the code stops reading these columns; it destroys replay evidence.
-- ALTER TABLE amendment_application_units DROP COLUMN block_reason,
--     DROP COLUMN attempt_pin_fingerprint, DROP COLUMN attempt_revision_no;
