package com.opencrm.contract.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dry-run report for typed {@code customer_account_id} reconciliation (DRG-663 AC4).
 * Read-only. Backfill apply is S7-class and NO-GO.
 */
@Service
public class SubscriptionCustomerAccountDryRun {

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final JdbcTemplate jdbc;

    public SubscriptionCustomerAccountDryRun(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Counts {@code customer_id} values that fail uuid parse, and typed-key mismatches
     * against the linked contract. Does not UPDATE.
     */
    public Map<String, Long> report() {
        Long parseFailures = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM subscriptions s
                WHERE s.is_deleted = FALSE
                  AND s.customer_id !~ ?
                """, Long.class, UUID_PATTERN);
        Long mismatches = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM subscriptions s
                JOIN contracts c ON c.id = s.contract_id
                WHERE s.is_deleted = FALSE
                  AND s.contract_id IS NOT NULL
                  AND s.customer_account_id IS NOT NULL
                  AND s.customer_account_id IS DISTINCT FROM c.customer_account_id
                """, Long.class);

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("UUID_PARSE_FAILURES", parseFailures == null ? 0L : parseFailures);
        counts.put("MISMATCHES", mismatches == null ? 0L : mismatches);
        return counts;
    }
}
