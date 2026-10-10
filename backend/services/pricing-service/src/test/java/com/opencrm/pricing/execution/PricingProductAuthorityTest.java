package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.pricing.*;
import com.opencrm.pricing.customer.*;
import com.opencrm.pricing.integration.pricebook.*;
import com.opencrm.pricing.product.*;
import com.opencrm.pricing.rule.RuleRepository;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import com.opencrm.pricing.waterfall.*;
import com.opencrm.rules.drools.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class PricingProductAuthorityTest {
    private PricingExecutionService service;
    private PricingProductRepository products;
    private PricingContext context;
    private PricingLineRequest line;

    @BeforeEach
    void setUp() {
        var customers = mock(CustomerRepository.class);
        when(customers.findByExternalIdAndDeletedFalse("customer")).thenReturn(Optional.of(Customer.builder()
                .externalId("customer").region(CustomerRegion.NA).name("Synthetic").build()));
        products = mock(PricingProductRepository.class);
        when(products.findByExternalIdInAndDeletedFalse(anyCollection())).thenReturn(List.of());
        var books = mock(PriceBookResolutionClient.class);
        when(books.resolveBatch(anyString(), anyList())).thenReturn(ResolveBatchResponse.builder()
                .entries(Map.of("product", ProductEntryResolveDto.builder().entryMatched(true)
                        .listPrice(new BigDecimal("100")).currency("USD").build())).priceBookCurrency("USD").build());
        var rules = mock(RuleSetRepository.class);
        when(rules.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any())).thenReturn(List.of());
        service = new PricingExecutionService(customers, products, rules, mock(RuleRepository.class), books,
                mock(PricingAuditLogRepository.class), new PricingContextValidator(), new MetricsCalculator(),
                new ObjectMapper().findAndRegisterModules(),
                new DroolsRulesEngine(new DroolsSessionFactory(new DroolsRuleCompiler())));
        service.setRampEnabled(true);
        line = PricingLineRequest.builder().productExternalId("product").lineItemType("SUBSCRIPTION")
                .quantity(2).billingFrequency("MONTHLY").metadata(new HashMap<>()).build();
        context = PricingContext.builder().customerExternalId("customer").durationInMonths(12).region("NA")
                .currency("USD").lineItems(List.of(line)).build();
    }

    private void catalog(ProductType type) {
        when(products.findByExternalIdInAndDeletedFalse(anyCollection())).thenReturn(List.of(PricingProduct.builder()
                .externalId("product").productType(type).listPrice(new BigDecimal("100")).currency("USD").build()));
    }
    private void scheduled() {
        line.setSchedule(List.of(RampStep.builder().sequence(1).startMonth(1).months(12).quantity(2)
                .priceBasis(RampPriceBasis.ABSOLUTE).unitPrice(new BigDecimal("100")).build()));
    }

    @Test void scheduledUsageCannotMasqueradeAsFixedRecurring() {
        catalog(ProductType.SAAS_USAGE); scheduled(); line.setLineItemType("SAAS_SEAT_NAMED");
        assertThatThrownBy(() -> service.execute(context)).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }
    @Test void genuineFixedProductKeepsGenericScheduledKindAndReturnsServerProof() {
        catalog(ProductType.SAAS_SEAT_CONCURRENT); scheduled();
        assertThat(service.execute(context).getLineResults().getFirst().getMetadata())
                .containsEntry("authoritativeProductType", "SAAS_SEAT_CONCURRENT");
    }
    @Test void pricebookFlatProofComesFromCatalogInsteadOfCaller() {
        catalog(ProductType.SAAS_USAGE); line.getMetadata().put("authoritativeProductType", "SAAS_SEAT_NAMED");
        var result = service.execute(context).getLineResults().getFirst();
        assertThat(result.getFinalPrice()).isEqualByComparingTo("200");
        assertThat(result.getMetadata()).containsEntry("authoritativeProductType", "SAAS_USAGE");
        assertThat(line.getMetadata()).containsEntry("authoritativeProductType", "SAAS_SEAT_NAMED");
    }
    @Test void missingFlatCatalogRetainsLegacyScalarWithoutForgedProof() {
        line.setMetadata(Map.of("authoritativeProductType", "SAAS_SEAT_NAMED", "ordinary", "kept"));
        var result = service.execute(context).getLineResults().getFirst();
        assertThat(result.getFinalPrice()).isEqualByComparingTo("200");
        assertThat(result.getMetadata()).doesNotContainKey("authoritativeProductType").containsEntry("ordinary", "kept");
    }
}
