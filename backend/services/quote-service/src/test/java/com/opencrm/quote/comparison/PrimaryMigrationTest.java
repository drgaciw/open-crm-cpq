package com.opencrm.quote.comparison;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class PrimaryMigrationTest {
    @Test void primaryIndexIsTenantScopedPartialAndCoversLegacyNullTenant() throws Exception {
        try (var resource = getClass().getResourceAsStream("/db/migration/V1_19__quote_primary_flag.sql")) {
            assertThat(resource).isNotNull();
            String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("is_primary BOOLEAN NOT NULL DEFAULT FALSE")
                    .contains("CREATE UNIQUE INDEX")
                    .contains("(tenant_id, deal_external_id) NULLS NOT DISTINCT")
                    .contains("is_primary = TRUE AND deal_external_id IS NOT NULL AND is_deleted = FALSE");
        }
    }
}
