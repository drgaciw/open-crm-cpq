package com.opencrm.crm.fields;

import java.util.List;

/**
 * Built-in record columns listed in the same registry as custom fields.
 * They can be hidden or reordered. They cannot be archived, relabeled, or retyped.
 * Audit columns and derived FX amounts are not registry fields.
 */
public final class StandardFieldCatalog {

    public record StandardField(
            FieldEntityKind entity,
            String key,
            String label,
            FieldType type,
            boolean required,
            boolean showOnList,
            int position) {
    }

    private static final List<StandardField> FIELDS = List.of(
            org(10, "name", "Name", FieldType.TEXT, true, true),
            org(20, "website", "Website", FieldType.URL, false, false),
            org(30, "website_domain", "Website domain", FieldType.TEXT, false, true),
            org(40, "industry", "Industry", FieldType.TEXT, false, true),
            org(50, "employee_count", "Employee count", FieldType.NUMBER, false, false),
            org(60, "annual_revenue", "Annual revenue", FieldType.NUMBER, false, false),
            org(70, "phone", "Phone", FieldType.PHONE, false, true),
            org(80, "address_line1", "Address line 1", FieldType.TEXT, false, false),
            org(90, "address_line2", "Address line 2", FieldType.TEXT, false, false),
            org(100, "city", "City", FieldType.TEXT, false, false),
            org(110, "state", "State", FieldType.TEXT, false, false),
            org(120, "postal_code", "Postal code", FieldType.TEXT, false, false),
            org(130, "country", "Country", FieldType.TEXT, false, false),
            org(140, "description", "Description", FieldType.LONG_TEXT, false, false),
            org(150, "linkedin_url", "LinkedIn URL", FieldType.URL, false, false),
            org(160, "type", "Type", FieldType.TEXT, false, true),
            org(170, "owner_id", "Owner", FieldType.USER, false, true),
            org(180, "territory", "Territory", FieldType.TEXT, false, true),

            contact(10, "first_name", "First name", FieldType.TEXT, true, true),
            contact(20, "last_name", "Last name", FieldType.TEXT, true, true),
            contact(30, "email", "Email", FieldType.EMAIL, false, true),
            contact(40, "phone", "Phone", FieldType.PHONE, false, true),
            contact(50, "mobile", "Mobile", FieldType.PHONE, false, false),
            contact(60, "job_title", "Job title", FieldType.TEXT, false, true),
            contact(70, "department", "Department", FieldType.TEXT, false, false),
            contact(80, "organization_id", "Organization", FieldType.NUMBER, false, true),
            contact(90, "address_line1", "Address line 1", FieldType.TEXT, false, false),
            contact(100, "address_line2", "Address line 2", FieldType.TEXT, false, false),
            contact(110, "city", "City", FieldType.TEXT, false, false),
            contact(120, "state", "State", FieldType.TEXT, false, false),
            contact(130, "postal_code", "Postal code", FieldType.TEXT, false, false),
            contact(140, "country", "Country", FieldType.TEXT, false, false),
            contact(150, "linkedin_url", "LinkedIn URL", FieldType.URL, false, false),
            contact(160, "notes", "Notes", FieldType.LONG_TEXT, false, false),
            contact(170, "is_primary", "Primary", FieldType.CHECKBOX, false, false),
            contact(180, "status", "Status", FieldType.TEXT, false, true),
            contact(190, "owner_id", "Owner", FieldType.USER, false, true),

            deal(10, "name", "Name", FieldType.TEXT, true, true),
            deal(20, "organization_id", "Organization", FieldType.NUMBER, false, true),
            deal(30, "primary_contact_id", "Primary contact", FieldType.NUMBER, false, false),
            deal(40, "stage", "Stage", FieldType.TEXT, true, true),
            deal(50, "amount", "Amount", FieldType.NUMBER, false, true),
            deal(60, "currency", "Currency", FieldType.TEXT, false, false),
            deal(70, "probability", "Probability", FieldType.NUMBER, false, false),
            deal(80, "expected_close_date", "Expected close date", FieldType.DATE, false, true),
            deal(90, "actual_close_date", "Actual close date", FieldType.DATE, false, false),
            deal(100, "description", "Description", FieldType.LONG_TEXT, false, false),
            deal(110, "owner_id", "Owner", FieldType.USER, false, true),
            deal(120, "type", "Type", FieldType.TEXT, false, true),
            deal(130, "lost_reason", "Lost reason", FieldType.TEXT, false, false),
            deal(140, "next_step", "Next step", FieldType.TEXT, false, false),
            deal(150, "next_step_date", "Next step date", FieldType.DATE, false, false),
            deal(160, "competitors", "Competitors", FieldType.TEXT, false, false)
    );

    private StandardFieldCatalog() {
    }

    public static List<StandardField> forEntity(FieldEntityKind entity) {
        return FIELDS.stream().filter(field -> field.entity() == entity).toList();
    }

    public static boolean isStandard(FieldEntityKind entity, String key) {
        return FIELDS.stream().anyMatch(field -> field.entity() == entity && field.key().equals(key));
    }

    private static StandardField org(int position, String key, String label, FieldType type,
                                      boolean required, boolean showOnList) {
        return new StandardField(FieldEntityKind.ORGANIZATION, key, label, type, required, showOnList, position);
    }

    private static StandardField contact(int position, String key, String label, FieldType type,
                                          boolean required, boolean showOnList) {
        return new StandardField(FieldEntityKind.CONTACT, key, label, type, required, showOnList, position);
    }

    private static StandardField deal(int position, String key, String label, FieldType type,
                                       boolean required, boolean showOnList) {
        return new StandardField(FieldEntityKind.DEAL, key, label, type, required, showOnList, position);
    }
}
