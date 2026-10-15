package com.opencrm.crm.fx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6 has not shipped, so the backfill still lives in that migration. The placeholder must be
 * normalized in both the predicate and the stored base currency, matching {@link FxProperties}.
 */
class DealBaseCurrencyMigrationTest {

    @Test
    void backfillNormalizesTheBaseCurrencyPlaceholder() throws IOException {
        String sql;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V6__add_deal_base_currency_amounts.sql")) {
            assertThat(in).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("base_currency        = UPPER(TRIM('${crm_base_currency}'))");
        assertThat(sql).contains("AND UPPER(TRIM(currency)) = UPPER(TRIM('${crm_base_currency}'))");
        assertThat(sql).doesNotContain("= '${crm_base_currency}'");
    }
}
