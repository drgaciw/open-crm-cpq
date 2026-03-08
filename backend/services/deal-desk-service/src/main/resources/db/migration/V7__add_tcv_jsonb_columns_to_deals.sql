ALTER TABLE deal_desk.deals
    ADD COLUMN components JSONB,
    ADD COLUMN pricing_model JSONB,
    ADD COLUMN adjustments JSONB,
    ADD COLUMN contingent_revenue JSONB,
    ADD COLUMN tcv_calculation JSONB;
