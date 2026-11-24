package com.opencrm.approval.isolation;

import java.lang.reflect.Method;

/**
 * Account column helper. {@code approval_requests.tenant_id} goes through {@link ApprovalRowTenant},
 * the canonical H4 type. Account stamping stays here until that column exists.
 */
final class StoredScope {

    private StoredScope() {
    }

    static String read(Object target, String property) {
        Method getter = accessor(target, "get", property);
        if (getter == null) {
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

    static void stamp(Object target, String property, String value) {
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
