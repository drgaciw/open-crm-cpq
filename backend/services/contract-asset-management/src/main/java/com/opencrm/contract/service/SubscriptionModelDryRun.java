package com.opencrm.contract.service;

import com.opencrm.contract.domain.SubscriptionModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dry-run classification counts for legacy {@code subscription_model IS NULL} rows (DRG-666 AC3).
 * Read-only. Backfill apply is S7-class and NO-GO.
 */
@Service
public class SubscriptionModelDryRun {
    private final JdbcTemplate jdbc;

    public SubscriptionModelDryRun(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Counts legacy subscriptions by the model implied from their newest non-deleted rate period
     * {@code product_type}. Does not UPDATE.
     */
    public Map<String, Long> classifyLegacyCounts() {
        List<Map.Entry<String, Long>> rows = jdbc.query("""
                SELECT rp.product_type AS product_type, COUNT(*) AS cnt
                FROM subscriptions s
                LEFT JOIN LATERAL (
                    SELECT product_type
                    FROM subscription_rate_periods p
                    WHERE p.subscription_id = s.id AND p.is_deleted = FALSE
                    ORDER BY p.start_date DESC, p.accepted_line_uuid ASC, p.sequence ASC
                    LIMIT 1
                ) rp ON TRUE
                WHERE s.is_deleted = FALSE AND s.subscription_model IS NULL
                GROUP BY rp.product_type
                """, (rs, rowNum) -> Map.entry(
                rs.getString("product_type"),
                rs.getLong("cnt")));

        Map<String, Long> counts = new HashMap<>();
        for (SubscriptionModel model : SubscriptionModel.values()) {
            counts.put(model.name(), 0L);
        }
        counts.put("UNMAPPED", 0L);
        counts.put("NULL_PRODUCT_TYPE", 0L);
        long total = 0L;
        for (Map.Entry<String, Long> row : rows) {
            long count = row.getValue() == null ? 0L : row.getValue();
            total += count;
            String productType = row.getKey();
            if (productType == null || productType.isBlank()) {
                counts.merge("NULL_PRODUCT_TYPE", count, Long::sum);
                continue;
            }
            var mapped = SubscriptionModelClassifier.map(productType);
            if (mapped.isEmpty()) {
                counts.merge("UNMAPPED", count, Long::sum);
            } else {
                counts.merge(mapped.get().name(), count, Long::sum);
            }
        }
        counts.put("TOTAL_LEGACY", total);
        return counts;
    }
}
