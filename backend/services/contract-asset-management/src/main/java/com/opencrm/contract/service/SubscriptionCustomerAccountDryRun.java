package com.opencrm.contract.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RED stub for DRG-663 AC4 dry-run. Real counts land in the green commit. Backfill is S7 NO-GO.
 */
@Service
public class SubscriptionCustomerAccountDryRun {

    private final JdbcTemplate jdbc;

    public SubscriptionCustomerAccountDryRun(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Read-only. Counts uuid-parse failures and typed-key mismatches. Does not UPDATE. */
    public Map<String, Long> report() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("UUID_PARSE_FAILURES", 0L);
        counts.put("MISMATCHES", 0L);
        return counts;
    }
}
