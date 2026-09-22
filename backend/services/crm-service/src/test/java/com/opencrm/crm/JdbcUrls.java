package com.opencrm.crm;

/**
 * JDBC URL helpers for tests.
 * <p>
 * Kept out of {@link BaseIntegrationTest} on purpose: that class starts PostgreSQL and Redis
 * containers in its static initializer, so any unit test that touched one of its static
 * helpers pulled Docker into the Docker-free {@code unit-tests} profile.
 */
final class JdbcUrls {

    private JdbcUrls() {
    }

    /** Testcontainers may already append query params (e.g. loggerLevel=OFF); use & not a second ?. */
    static String appendQueryParam(String jdbcUrl, String name, String value) {
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + name + "=" + value;
    }
}
