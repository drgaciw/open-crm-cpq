package com.opencrm.crm.duplicate;

import java.util.Locale;

/**
 * Read-time normalization for duplicate suggestions. Create and update still store the raw
 * website, domain, email, and name values.
 */
public final class DomainNormalizer {

    private DomainNormalizer() {
    }

    /**
     * Trim, lowercase, and reduce a website or domain to a host. Scheme, user info, port, path,
     * query, fragment, a trailing dot, and a leading {@code www.} are removed. Blank input matches nothing.
     */
    public static String normalizeDomain(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int fragment = value.indexOf('#');
        if (fragment >= 0) {
            value = value.substring(0, fragment);
        }
        int at = value.lastIndexOf('@');
        if (at >= 0) {
            value = value.substring(at + 1);
        }
        int colon = value.lastIndexOf(':');
        if (colon >= 0) {
            value = value.substring(0, colon);
        }
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.startsWith("www.")) {
            value = value.substring(4);
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Trim and lowercase an email. Plus-tags and dots inside the local part are preserved.
     */
    public static String normalizeEmail(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    /**
     * Trim, lowercase, and collapse whitespace in first and last name, then join them with one space.
     * A blank side does not match.
     */
    public static String normalizeName(String first, String last) {
        String firstName = collapse(first);
        String lastName = collapse(last);
        if (firstName.isEmpty() || lastName.isEmpty()) {
            return null;
        }
        return firstName + " " + lastName;
    }

    private static String collapse(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
