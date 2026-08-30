-- =====================================================
-- Flyway Migration V1_2: Quote BFF approval types (G3)
-- =====================================================
-- Adds QUOTE / GOVERNANCE for POST /api/v1/approvals create/submit.
-- Kafka discount path continues to use DISCOUNT + margin tiers.
-- =====================================================

ALTER TABLE approval_requests DROP CONSTRAINT IF EXISTS chk_approval_type;

ALTER TABLE approval_requests ADD CONSTRAINT chk_approval_type CHECK (approval_type IN (
    'DISCOUNT',
    'MARGIN',
    'TERMS',
    'PRICING_EXCEPTION',
    'CONTRACT_MODIFICATION',
    'QUOTE',
    'GOVERNANCE'
));
