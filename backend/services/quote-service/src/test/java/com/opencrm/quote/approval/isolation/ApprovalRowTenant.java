package com.opencrm.quote.approval.isolation;

import java.lang.reflect.Method;

/**
 * Quote-service view of the same H4 contract as
 * {@code com.opencrm.approval.isolation.ApprovalRowTenant}: {@code approval_requests.tenant_id}
 * is the caller's Entra {@code tid}. DRG-444 quote specs re-point here if they take a tenant.
 * The approval-routing type is the one {@code PendingApproval} in #1076 re-points at.
 * Merge order is DRG-442, DRG-443, H4, DRG-444.
 *
 * <p>On this BFF the quote already stores {@code tenantId}. Legacy approval rows do not, so
 * {@link #read(Object)} is null and {@link #stampOn(Object)} does nothing until that column exists.
 */
public record ApprovalRowTenant(String tenantId) {

    public static final String COLUMN = "tenant_id";
    public static final String PROPERTY = "tenantId";

    public ApprovalRowTenant {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("approval_requests.tenant_id is required");
        }
        tenantId = tenantId.strip();
    }

    public static ApprovalRowTenant fromCaller() {
        String tid = ApprovalCallerScope.tenantId();
        if (tid == null || tid.isBlank()) {
            throw new IllegalStateException(
                    "approval_requests.tenant_id comes from the caller tid, not from a body or X-Tenant-Id");
        }
        return new ApprovalRowTenant(tid);
    }

    public static ApprovalRowTenant read(Object row) {
        String value = readProperty(row, PROPERTY);
        return value == null ? null : new ApprovalRowTenant(value);
    }

    public void stampOn(Object row) {
        writeProperty(row, PROPERTY, tenantId);
    }

    public boolean visibleTo(ApprovalRowTenant caller) {
        return caller != null && tenantId.equals(caller.tenantId);
    }

    private static String readProperty(Object target, String property) {
        Method getter = accessor(target, "get", property);
        if (getter == null || getter.getParameterCount() != 0) {
            return null;
        }
        try {
            Object value = getter.invoke(target);
            if (value == null) {
                return null;
            }
            String text = value.toString().strip();
            return text.isEmpty() ? null : text;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void writeProperty(Object target, String property, String value) {
        if (value == null) {
            return;
        }
        Method setter = accessor(target, "set", property);
        if (setter == null || setter.getParameterCount() != 1 || setter.getParameterTypes()[0] != String.class) {
            return;
        }
        try {
            setter.invoke(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Method accessor(Object target, String prefix, String property) {
        if (target == null || property == null || property.isEmpty()) {
            return null;
        }
        String name = prefix + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }
}
