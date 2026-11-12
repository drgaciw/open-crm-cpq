package com.opencrm.product.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializes same-tenant PublishCatalog {@code max(version_number)+1} until the surrounding
 * transaction commits. Uses {@link JdbcTemplate} so Postgres {@code pg_advisory_xact_lock}
 * (void) is not projected through Spring Data as {@code Integer}.
 */
@Component
public class CatalogPublishLock {

    private final JdbcTemplate jdbc;

    public CatalogPublishLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param tenantKey {@code COALESCE(tenant_id, "")} so the null-tenant bucket shares one lock
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String tenantKey) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(CAST(? AS text)))", rs -> null, tenantKey);
    }
}
