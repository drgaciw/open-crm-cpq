package com.opencrm.contract.service;

import com.opencrm.contract.domain.SubscriptionModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-666 AC3 — dry-run classification counts for legacy rows. Backfill itself is S7-class and NO-GO.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SubscriptionModelDryRunTest {

    @Mock JdbcTemplate jdbc;

    @Test
    @SuppressWarnings("unchecked")
    void countsLegacyRowsByClassifiedModelWithoutUpdating() {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(
                new java.util.AbstractMap.SimpleEntry<>("SAAS_FLAT_RATE", 3L),
                new java.util.AbstractMap.SimpleEntry<>("ONPREM_SUBSCRIPTION", 2L),
                new java.util.AbstractMap.SimpleEntry<>("SUPPORT_BASIC", 1L),
                new java.util.AbstractMap.SimpleEntry<>("SAAS_USAGE_METERED", 4L),
                new java.util.AbstractMap.SimpleEntry<>(null, 5L)));

        SubscriptionModelDryRun dryRun = new SubscriptionModelDryRun(jdbc);
        Map<String, Long> counts = dryRun.classifyLegacyCounts();

        assertThat(counts.get(SubscriptionModel.SAAS_RECURRING.name())).isEqualTo(4L);
        assertThat(counts.get(SubscriptionModel.ONPREM_TRANSACTIONAL.name())).isEqualTo(2L);
        assertThat(counts.get("UNMAPPED")).isEqualTo(4L);
        assertThat(counts.get("NULL_PRODUCT_TYPE")).isEqualTo(5L);
        assertThat(counts.get("TOTAL_LEGACY")).isEqualTo(15L);

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class));
        assertThat(sql.getValue().toLowerCase()).contains("subscription_model is null");
        assertThat(sql.getValue().toLowerCase()).doesNotContain("update ");
        assertThat(sql.getValue().toLowerCase()).doesNotContain("set subscription_model");
    }
}
