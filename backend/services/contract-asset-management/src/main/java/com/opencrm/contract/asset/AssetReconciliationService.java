package com.opencrm.contract.asset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Read-only reconciliation inventory (DRG-902 AC3). No endpoint, scheduler, or migration.
 * The body is intentionally unimplemented until the failing tests are committed.
 */
@Service
public class AssetReconciliationService {

    public AssetReconciliationService(JdbcTemplate jdbc, AssetHistoryProjection projection) {
        if (jdbc == null || projection == null) {
            throw new IllegalArgumentException("jdbc and projection are required");
        }
    }

    @Transactional(readOnly = true)
    public AssetReconciliationReport inventory(LocalDate asOf) {
        throw new UnsupportedOperationException("DRG-902 AC3 inventory is not implemented");
    }
}
