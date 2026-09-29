-- Owner decision 9 (docs/forge/40-owner-decisions-adr-016-018.md): a newer discount request for the same quote line
-- supersedes the open one, so an approver can never approve figures the quote no longer carries.
--
-- SUPERSEDED is terminal. The row stays as history (ADR-013) and names its replacement: superseded_by is the id of the
-- request that replaced it, superseded_at the moment. Both are required together with the status, and the replacement
-- must exist. The change is additive: rows written before it are valid as they are, and a version that does not know
-- the status simply never produces it.
ALTER TABLE approval_requests DROP CONSTRAINT chk_approval_status;
ALTER TABLE approval_requests ADD CONSTRAINT chk_approval_status CHECK (status IN (
    'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'SUPERSEDED'
));

ALTER TABLE approval_requests ADD COLUMN superseded_by BIGINT REFERENCES approval_requests (id);
ALTER TABLE approval_requests ADD COLUMN superseded_at TIMESTAMPTZ;

ALTER TABLE approval_requests ADD CONSTRAINT chk_approval_superseded CHECK (
    (status = 'SUPERSEDED') = (superseded_by IS NOT NULL AND superseded_at IS NOT NULL)
    AND (status = 'SUPERSEDED' OR (superseded_by IS NULL AND superseded_at IS NULL))
);

CREATE INDEX idx_approval_superseded_by ON approval_requests (superseded_by) WHERE superseded_by IS NOT NULL;