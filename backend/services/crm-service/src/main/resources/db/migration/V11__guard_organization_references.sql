-- Serialize new and changed child references with merge's organization row lock.
-- Existing legacy references remain untouched until separately reconciled.
CREATE FUNCTION require_active_organization_reference() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE inactive boolean;
BEGIN
    IF NEW.organization_id IS NULL THEN RETURN NEW; END IF;
    IF TG_OP = 'UPDATE' AND NEW.organization_id IS NOT DISTINCT FROM OLD.organization_id THEN
        RETURN NEW;
    END IF;
    SELECT is_deleted INTO inactive FROM organizations WHERE id = NEW.organization_id FOR SHARE;
    IF NOT FOUND OR inactive THEN
        RAISE EXCEPTION 'organization_reference_active: organization is missing or deleted' USING ERRCODE = '23514', CONSTRAINT = 'organization_reference_active';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER guard_contact_organization BEFORE INSERT OR UPDATE OF organization_id ON contacts
FOR EACH ROW EXECUTE FUNCTION require_active_organization_reference();
CREATE TRIGGER guard_lead_organization BEFORE INSERT OR UPDATE OF organization_id ON leads
FOR EACH ROW EXECUTE FUNCTION require_active_organization_reference();
CREATE TRIGGER guard_deal_organization BEFORE INSERT OR UPDATE OF organization_id ON deals
FOR EACH ROW EXECUTE FUNCTION require_active_organization_reference();
