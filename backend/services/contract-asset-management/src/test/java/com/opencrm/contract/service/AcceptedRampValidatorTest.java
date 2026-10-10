package com.opencrm.contract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcceptedRampValidatorTest {
    static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private final AcceptedRampValidator validator = new AcceptedRampValidator();

    static QuoteAcceptedEvent fixture(String name) throws IOException {
        try (var input = AcceptedRampValidatorTest.class.getResourceAsStream("/accepted-v1/" + name + ".json")) {
            return MAPPER.readValue(input, QuoteAcceptedEvent.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBSCRIPTION", "PRODUCT", "SERVICE", "SAAS_SEAT_NAMED", "SAAS_SEAT_CONCURRENT"})
    void actualFixedRecurringLabelsKeepAcceptedRates(String label) throws Exception {
        var event = fixture("ramp-new");
        event.getLineItems().getFirst().setLineItemType(label);
        var result = validator.validate(event, null, 0);
        assertThat(result.periods()).hasSize(3);
        assertThat(result.firstMrr()).isEqualByComparingTo("2000");
        assertThat(result.exitMrr()).isEqualByComparingTo("2500");
        assertThat(result.periods().getLast().getEndDate()).hasToString("2029-12-31");
    }

    @ParameterizedTest
    @ValueSource(strings = {"FEE", "TAX", "DISCOUNT", "ONPREM_PERPETUAL", "SAAS_USAGE", "SAAS_CREDITS"})
    void unsupportedTypedKindsFailWithoutExtrapolatingRates(String label) throws Exception {
        var event = fixture("ramp-new");
        event.getLineItems().getFirst().setLineItemType(label);
        assertThatThrownBy(() -> validator.validate(event, null, 0)).isInstanceOf(NonRetryableEventException.class);
    }

    @Test void incoherentPricedAmountIsRejected() throws Exception {
        var event = fixture("ramp-new");
        event.getLineItems().getFirst().getRamp().getFirst().setPeriodAmount(new BigDecimal("23999"));
        assertThatThrownBy(() -> validator.validate(event, null, 0)).isInstanceOf(NonRetryableEventException.class);
    }

    @Test void tamperedAcceptedAcvCannotPassCommercialFactValidation() throws Exception {
        var event = fixture("ramp-new");
        event.getPricing().setAcv(new BigDecimal("1"));
        assertThatThrownBy(() -> validator.validate(event, null, 0)).isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("ACV");
    }

    @Test void typedNewScheduleCannotInventMissingProductClassification() throws Exception {
        var event = fixture("ramp-new");
        event.getLineItems().getFirst().setProductType(null);
        assertThatThrownBy(() -> validator.validate(event, null, 0)).isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("classification");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SAAS_USAGE_METERED", "SAAS_USAGE", "SAAS_CREDITS", "ONPREM_PERPETUAL",
            "SAAS_FLAT_RATE", "ONPREM_SUBSCRIPTION", "SUPPORT_BASIC", "SUPPORT_PREMIUM", "SUPPORT_ENTERPRISE"})
    void genericServiceLabelCannotDisguiseUnsupportedProductPricing(String productType) throws Exception {
        var event = fixture("ramp-new");
        event.getLineItems().getFirst().setLineItemType("SERVICE");
        event.getLineItems().getFirst().setProductType(productType);
        assertThatThrownBy(() -> validator.validate(event, null, 0)).isInstanceOf(NonRetryableEventException.class);
    }

    @Test void pairedCreditChangesTcvWhileRecurringRatesRemainGross() throws Exception {
        var event = fixture("hybrid-credit-new");
        event.getLineItems().get(0).setLineItemType("ONPREM_SUPPORT");
        event.getLineItems().get(1).setLineItemType("HYBRID_MIGRATION_RAMP");
        var result = validator.validate(event, null, 0);
        assertThat(result.periods()).hasSize(4);
        assertThat(result.firstMrr()).isEqualByComparingTo("1500");
        assertThat(result.exitMrr()).isEqualByComparingTo("1200");
        assertThat(result.periods().getFirst().getPeriodAmount()).isEqualByComparingTo("9000");
        assertThat(result.periods().getFirst().getNetMonthly()).isEqualByComparingTo("1000");
    }

    @Test void actualPricingPersistedQuoteAndAcceptedFactoryGoldenFactsRemainConsumable() throws Exception {
        // Exported from the real engine response through quote calculate, persistence reload and accepted factory.
        // Source JSON SHA256 EFF92EB5ABA8ABF1B90A255084D80A3E4F5D521B008B5254D92BD995A91A1393.
        var result = validator.validate(fixture("r4-accepted-event"), null, 0);
        assertThat(result.periods()).hasSize(4);
        assertThat(result.firstMrr()).isEqualByComparingTo("1500");
        assertThat(result.exitMrr()).isEqualByComparingTo("1200");
        assertThat(result.periods().getFirst().getGrossPeriodAmount()).isEqualByComparingTo("12000");
        assertThat(result.periods().getFirst().getMigrationCredit()).isEqualByComparingTo("3000");
        assertThat(result.periods().getFirst().getPeriodAmount()).isEqualByComparingTo("9000");
        assertThat(result.periods().getFirst().getNetUnitPrice()).isEqualByComparingTo("100");
        assertThat(result.planName()).isEqualTo("R4-SYNTHETIC-ONPREM-SUPPORT-001, R4-SYNTHETIC-HYBRID-RISING-001");
    }

    @Test void missingProductNameAndIdentifierCannotBecomeInventedPlanName() throws Exception {
        var event = fixture("ramp-new");
        event.getLineItems().getFirst().setProductName(null);
        event.getLineItems().getFirst().setProductExternalId(null);
        assertThatThrownBy(() -> validator.validate(event, null, 0)).isInstanceOf(NonRetryableEventException.class);
    }

    @Test void publishedTcvCannotReplaceTheAcceptedMonthlyScalarTotal() throws Exception {
        var event = fixture("r4-accepted-event");
        event.setTotalAmount(event.getPricing().getTcv());
        event.getPricing().setTotalAmount(event.getPricing().getTcv());
        assertThatThrownBy(() -> validator.validate(event, null, 0)).isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("scalar total");
    }

    @Test void pricingScalarTotalMustMatchAcceptedLineScalarsIndependentlyOfRootTotal() throws Exception {
        var event = fixture("r4-accepted-event");
        event.getPricing().setTotalAmount(new BigDecimal("1501"));
        assertThatThrownBy(() -> validator.validate(event, null, 0)).isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("pricing scalar total");
    }

    @Test void creditWithoutTypedPairIsRejected() throws Exception {
        var event = fixture("hybrid-credit-new");
        event.getLineItems().getFirst().setHybridMigration(null);
        assertThatThrownBy(() -> validator.validate(event, null, 0)).isInstanceOf(NonRetryableEventException.class);
    }

    @Test void hybridPairCannotReuseOneProductIdentityForBothRoles() throws Exception {
        var event = fixture("hybrid-credit-new");
        event.getLineItems().get(1).setProductExternalId(event.getLineItems().getFirst().getProductExternalId());
        assertThatThrownBy(() -> validator.validate(event, null, 0)).isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("product identities");
    }

    @Test void hybridPairCannotAuthorizeCreditWithoutBothProductIdentities() throws Exception {
        var event = fixture("hybrid-credit-new");
        event.getLineItems().getFirst().setProductExternalId(" ");
        assertThatThrownBy(() -> validator.validate(event, null, 0)).isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("product identities");
    }

    @Test void creditAboveAuthorizedThirtyPercentIsRejectedEvenBelowBookValueAndGross() throws Exception {
        var event = fixture("hybrid-credit-new");
        var period = event.getLineItems().getFirst().getRamp().getFirst();
        period.setMigrationCredit(new BigDecimal("4000"));
        period.setPeriodAmount(new BigDecimal("8000"));
        assertThatThrownBy(() -> validator.validate(event, null, 0)).isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("authorized");
    }

    @Test void mixedFeeUsesPublishedCalculatorTotalsButOnlyRecurringSubscriptionRates() throws Exception {
        var event = fixture("ramp-new");
        var lines = new java.util.ArrayList<>(event.getLineItems());
        lines.add(QuoteAcceptedEvent.LineItemSnapshot.builder().lineUuid("11111111-1111-4111-8111-000000000299")
                .productName("Setup fee").lineItemType("FEE").billingFrequency("ONE_TIME")
                .extendedAmount(new BigDecimal("100")).build());
        event.setLineItems(lines);
        event.setTotalAmount(new BigDecimal("2100"));
        event.getPricing().setTotalAmount(event.getTotalAmount());
        event.getPricing().setTcv(new BigDecimal("84600"));
        event.getPricing().setAcv(new BigDecimal("28200"));
        event.getPricing().setArr(new BigDecimal("25200"));
        event.getPricing().setExitArr(new BigDecimal("31200"));
        var result = validator.validate(event, null, 0);
        assertThat(result.periods()).hasSize(3);
        assertThat(result.firstMrr()).isEqualByComparingTo("2000");
        assertThat(result.exitMrr()).isEqualByComparingTo("2500");
        event.getPricing().setArr(new BigDecimal("24000"));
        assertThatThrownBy(() -> validator.validate(event, null, 0)).isInstanceOf(NonRetryableEventException.class);
    }
}
