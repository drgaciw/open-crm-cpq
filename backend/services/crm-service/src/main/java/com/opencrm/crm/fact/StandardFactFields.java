package com.opencrm.crm.fact;

import java.util.Locale;

/**
 * Field keys whose band or column behaviour is special.
 * <p>
 * Email, an organization link, and a parent link are sensitive (ADR-028 D-03).
 * {@code parentOrganizationId} is not written here. {@code AccountHierarchyService} owns that
 * column and refuses work outside its test seam, so accepting a re-parent fact does not
 * bypass the cycle check.
 */
public final class StandardFactFields {

    private StandardFactFields() {
    }

    public static String canonical(String fieldKey) {
        return fieldKey.trim().replace("_", "").toLowerCase(Locale.ROOT);
    }

    public static boolean sensitive(String fieldKey) {
        return switch (canonical(fieldKey)) {
            case "email", "organizationid", "parentorganizationid" -> true;
            default -> false;
        };
    }

    public static boolean reparent(FactSubjectType subjectType, String fieldKey) {
        return subjectType == FactSubjectType.ORGANIZATION
                && "parentorganizationid".equals(canonical(fieldKey));
    }

    public static boolean columnWritable(FactSubjectType subjectType, String fieldKey) {
        String key = canonical(fieldKey);
        return switch (subjectType) {
            case CONTACT -> switch (key) {
                case "jobtitle", "email", "phone", "department", "linkedinurl", "organizationid" -> true;
                default -> false;
            };
            case ORGANIZATION -> switch (key) {
                case "industry", "website", "phone", "linkedinurl", "employeecount" -> true;
                default -> false;
            };
        };
    }
}
