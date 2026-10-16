package com.opencrm.contract.documents;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * S7 header backfill (DRG-585). One thin contract per eligible subscription.
 * No documents, events, outbox rows, or pricing changes. Regrouping stays on DRG-554.
 *
 * <p>The tenant argument is required. This service does not invent one.
 */
@Service
public class ContractHeaderBackfill {

    public static final String BACKFILL_ACTOR = "V1_8_HEADER_BACKFILL";
    public static final String CREATION_ACTOR = "S7_HEADER_AT_CREATION";
    public static final String TENANT_REQUIRED = "authoritative tenant is required";
    public static final String NOT_ELIGIBLE = "subscription is not eligible for a contract header";
    public static final String NUMBER_TAKEN = "contract number already belongs to a different header";
    public static final String RESTORE_FROM_BACKUP = "header backfill refuses to compensate: restore from backup";

    private final JdbcTemplate jdbc;

    public ContractHeaderBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Dry run. Count of subscriptions that would get a header. Writes nothing. */
    @Transactional(readOnly = true)
    public long countEligible(UUID tenantId) {
        requireTenant(tenantId);
        Long count = jdbc.queryForObject("SELECT contract_header_backfill_count(?)", Long.class, tenantId);
        return count == null ? 0L : count;
    }

    /** Inserts missing headers and sets contract_id. A second call links nothing. */
    @Transactional
    public long apply(UUID tenantId) {
        requireTenant(tenantId);
        Long linked = jdbc.queryForObject("SELECT contract_header_backfill_apply(?)", Long.class, tenantId);
        return linked == null ? 0L : linked;
    }

    /**
     * Header at creation for one subscription (AC-S7-3). The quote-accepted NEW writer is not
     * called from here: that path has no tenant.
     */
    @Transactional
    public UUID ensureHeaderAtCreation(long subscriptionId, UUID tenantId) {
        requireTenant(tenantId);
        return jdbc.queryForObject(
                "SELECT contract_header_at_creation(?, ?)", UUID.class, subscriptionId, tenantId);
    }

    /**
     * Deletes only backfilled headers. Refuses, and writes nothing, when another row still
     * references one. That case is restore-from-backup.
     */
    @Transactional
    public long compensate() {
        Long removed = jdbc.queryForObject("SELECT contract_header_backfill_compensate()", Long.class);
        return removed == null ? 0L : removed;
    }

    private static void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException(TENANT_REQUIRED);
        }
    }
}
