package com.opencrm.contract.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-663 AC4 — dry-run report: uuid-parse failures + mismatches. No write. Backfill is S7 NO-GO.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SubscriptionCustomerAccountDryRunTest {

    @Mock JdbcTemplate jdbc;

    @Test
    void reportsParseFailuresAndMismatchesWithoutUpdating() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(7L);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);

        SubscriptionCustomerAccountDryRun dryRun = new SubscriptionCustomerAccountDryRun(jdbc);
        Map<String, Long> report = dryRun.report();

        assertThat(report.get("UUID_PARSE_FAILURES")).isEqualTo(7L);
        assertThat(report.get("MISMATCHES")).isEqualTo(2L);

        org.mockito.ArgumentCaptor<String> parseSql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> mismatchSql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(parseSql.capture(), eq(Long.class), any());
        verify(jdbc).queryForObject(mismatchSql.capture(), eq(Long.class));
        for (String statement : java.util.List.of(parseSql.getValue(), mismatchSql.getValue())) {
            String lower = statement.toLowerCase();
            assertThat(lower).doesNotContain("update ");
            assertThat(lower).doesNotContain("insert ");
            assertThat(lower).doesNotContain("set customer_account_id");
        }
    }
}
