package com.opencrm.authorization.service;

/**
 * An (action, resource) pair held in {@link AuthorizationQueryService}'s per-principal cache entry. {@code "*"} on
 * either side matches anything requested on that side, so {@code ("*", "*")} is a superuser grant and
 * {@code ("quote:approve", "*")} matches any resource for that action.
 */
record PermissionKey(String action, String resource) {

    static final String ANY = "*";

    boolean matches(String requestedAction, String requestedResource) {
        return matchesPart(action, requestedAction) && matchesPart(resource, requestedResource);
    }

    private static boolean matchesPart(String granted, String requested) {
        return ANY.equals(granted) || granted.equals(requested);
    }
}
