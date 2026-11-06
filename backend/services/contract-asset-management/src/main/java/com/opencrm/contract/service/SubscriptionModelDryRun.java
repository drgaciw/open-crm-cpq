package com.opencrm.contract.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Dry-run classification counts for legacy {@code subscription_model IS NULL} rows (DRG-666 AC3).
 * Does not write. Backfill apply is S7-class and NO-GO.
 */
@Service
public class SubscriptionModelDryRun {
    private final JdbcTemplate jdbc;

    public SubscriptionModelDryRun(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Long> classifyLegacyCounts() {
        return Map.of();
    }
}
