package com.opencrm.pricing.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.config.PricingClockConfig;
import com.opencrm.pricing.customer.*;
import com.opencrm.pricing.integration.pricebook.*;
import com.opencrm.pricing.product.PricingProductRepository;
import com.opencrm.pricing.rule.RuleRepository;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import com.opencrm.pricing.waterfall.*;
import com.opencrm.rules.drools.DroolsRulesEngine;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PricingReplayClockTest {
    @Test
    void systemClockDefaultsUtcAndReplayBeanOverridesDefault() {
        try (var context = new AnnotationConfigApplicationContext(PricingClockConfig.class)) {
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
        }
        Clock pinned = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("replayClock", Clock.class, () -> pinned);
            context.register(PricingClockConfig.class);
            context.refresh();
            assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
            assertThat(context.getBean(Clock.class)).isSameAs(pinned);
        }
    }

    @Test
    void serializationFailureNeverReportsSuccessfulAuditedExecution() throws Exception {
        CustomerRepository customers = mock(CustomerRepository.class);
        when(customers.findByExternalIdAndDeletedFalse("C")).thenReturn(Optional.of(Customer.builder().externalId("C").build()));
        PriceBookResolutionClient books = mock(PriceBookResolutionClient.class);
        when(books.resolveBatch(any(), any())).thenReturn(ResolveBatchResponse.builder().entries(Map.of()).build());
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("recording failed") {});
        PricingAuditLogRepository audit = mock(PricingAuditLogRepository.class);
        PricingExecutionService service = new PricingExecutionService(customers, mock(PricingProductRepository.class),
                mock(RuleSetRepository.class), mock(RuleRepository.class), books, audit,
                new PricingContextValidator(), new MetricsCalculator(), mapper, mock(DroolsRulesEngine.class));
        service.setClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        PricingContext request = PricingContext.builder().customerExternalId("C").quoteExternalId("Q")
                .region("NA").currency("USD").durationInMonths(12).lineItems(List.of(PricingLineRequest.builder()
                        .productExternalId("P").lineItemType("SUBSCRIPTION").quantity(1).listPriceOverride(BigDecimal.TEN).build())).build();
        assertThatThrownBy(() -> service.execute(request)).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("AUDIT_PERSISTENCE_FAILED"));
        verifyNoInteractions(audit);
        assertThatThrownBy(() -> service.setClock(null)).isInstanceOf(NullPointerException.class);
    }
}
