package com.opencrm.approval.isolation;

import java.lang.reflect.Method;

/**
 * Canonical {@code approval_requests.tenant_id} for H4 (decision 7:41 PM CT, Nov 23).
 * The value is the caller's Entra {@code tid} ({@link ApprovalCallerScope}), never a request
 * body field and never {@code X-Tenant-Id}.
 *
 * <p>This is the tenant-isolation contract for an approval row. DRG-444 specs in #1076
 * ({@code com.opencrm.approval.testdoubles.PendingApproval}) re-point at this type instead of
 * declaring another tenant. Merge order is DRG-442, DRG-443, H4, DRG-444.
 * The column is not on the entity yet, so {@link #read(Object)} is null and {@link #stampOn(Object)}
 * does nothing until Feature adds {@code tenantId}.
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

    /** Tenant stamped from the verified caller. A missing tid is not a row tenant. */
    public static ApprovalRowTenant fromCaller() {
        String tid = ApprovalCallerScope.tenantId();
        if (tid == null || tid.isBlank()) {
            throw new IllegalStateException(
                    "approval_requests.tenant_id comes from the caller tid, not from a body or X-Tenant-Id");
        }
        return new ApprovalRowTenant(tid);
    }

    /** Row value, or null when {@code approval_requests.tenant_id} is not on the type yet. */
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

    static String readProperty(Object target, String property) {
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

    static void writeProperty(Object target, String property, String value) {
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
