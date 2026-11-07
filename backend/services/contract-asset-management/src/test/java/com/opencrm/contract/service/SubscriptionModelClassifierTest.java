package com.opencrm.contract.service;

import com.opencrm.contract.domain.SubscriptionModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-666 AC2 — one test per mapped product type (ADR §2.2 / AcceptedQuoteSubscriptionService allow-list seed).
 */
@Tag("unit")
class SubscriptionModelClassifierTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "SAAS_FLAT_RATE, SAAS_RECURRING",
            "ONPREM_SUBSCRIPTION, ONPREM_TRANSACTIONAL",
            "SUPPORT_BASIC, SAAS_RECURRING",
            "SUPPORT_PREMIUM, SAAS_RECURRING",
            "SUPPORT_ENTERPRISE, SAAS_RECURRING",
            "SAAS_SEAT_NAMED, SAAS_RECURRING",
            "SAAS_SEAT_CONCURRENT, SAAS_RECURRING",
            "ONPREM_SUPPORT, ONPREM_TRANSACTIONAL",
            "ONPREM_CAPACITY, ONPREM_CAPACITY",
            "HYBRID_MIGRATION_RAMP, SAAS_RECURRING"
    })
    void mapsEachSeededProductType(String productType, SubscriptionModel expected) {
        assertThat(SubscriptionModelClassifier.map(productType)).contains(expected);
    }

    @Test
    void unknownProductTypeIsEmpty() {
        assertThat(SubscriptionModelClassifier.map("SAAS_USAGE_METERED")).isEmpty();
        assertThat(SubscriptionModelClassifier.map("ONPREM_PERPETUAL")).isEmpty();
        assertThat(SubscriptionModelClassifier.map("")).isEmpty();
        assertThat(SubscriptionModelClassifier.map(null)).isEmpty();
    }

    @Test
    void mappingTableExposesEverySeededEntry() {
        Map<String, SubscriptionModel> table = SubscriptionModelClassifier.mappingTable();
        assertThat(table).hasSize(10);
        assertThat(table).containsEntry("SAAS_FLAT_RATE", SubscriptionModel.SAAS_RECURRING);
        assertThat(table).containsEntry("ONPREM_SUBSCRIPTION", SubscriptionModel.ONPREM_TRANSACTIONAL);
        assertThat(table).containsEntry("SUPPORT_BASIC", SubscriptionModel.SAAS_RECURRING);
        assertThat(table).containsEntry("SUPPORT_PREMIUM", SubscriptionModel.SAAS_RECURRING);
        assertThat(table).containsEntry("SUPPORT_ENTERPRISE", SubscriptionModel.SAAS_RECURRING);
        assertThat(table.values()).containsOnly(
                SubscriptionModel.SAAS_RECURRING,
                SubscriptionModel.ONPREM_TRANSACTIONAL,
                SubscriptionModel.ONPREM_CAPACITY);
    }

    @Test
    void hybridOnPremSupportPlusRampResolvesToSaasRecurring() {
        Optional<SubscriptionModel> model = SubscriptionModelClassifier.classifyLines(List.of(
                "ONPREM_SUPPORT", "HYBRID_MIGRATION_RAMP"));
        assertThat(model).contains(SubscriptionModel.SAAS_RECURRING);
    }
}
