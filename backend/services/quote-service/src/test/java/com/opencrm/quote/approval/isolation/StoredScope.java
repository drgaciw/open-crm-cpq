package com.opencrm.quote.approval.isolation;

import java.lang.reflect.Method;

/**
 * Account column helper for approval rows. {@code tenant_id} goes through {@link ApprovalRowTenant}.
 * Quote ownership is {@code Quote.tenantId} and {@code Quote.customerExternalId}.
 */
final class StoredScope {

    private StoredScope() {
    }

    static void stamp(Object target, String property, String value) {
        if (value == null) {
            return;
        }
        Method setter = accessor(target, property);
        if (setter == null || setter.getParameterCount() != 1 || setter.getParameterTypes()[0] != String.class) {
            return;
        }
        try {
            setter.invoke(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Method accessor(Object target, String property) {
        if (target == null || property == null || property.isEmpty()) {
            return null;
        }
        String name = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }
}
