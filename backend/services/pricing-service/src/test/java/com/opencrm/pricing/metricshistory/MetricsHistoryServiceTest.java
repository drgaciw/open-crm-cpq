package com.opencrm.pricing.metricshistory;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.customer.CustomerRepository;
import com.opencrm.pricing.execution.PricingExecutionService;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsHistoryServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private MetricsSnapshotRepository snapshotRepository;
    @Mock
    private PricingExecutionService pricingExecutionService;

    @InjectMocks
    private MetricsHistoryService historyService;

    @Test
    void snapshotStoresSimulateMetricsWithoutRecomputingFormulas() {
        Customer customer = Customer.builder().externalId("cust-1").name("Acme").build();
        when(customerRepository.findByExternalIdAndDeletedFalse("cust-1")).thenReturn(Optional.of(customer));
        when(pricingExecutionService.simulate(any())).thenReturn(PricingResult.builder()
                .success(true)
                .financialMetrics(FinancialMetrics.builder()
                        .arr(new BigDecimal("1200.00"))
                        .tcv(new BigDecimal("3600.00"))
                        .churnScore(42)
                        .build())
                .build());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> {
            MetricsSnapshot snap = invocation.getArgument(0);
            snap.setId(11L);
            snap.setUuid("u-1");
            snap.setSnapshotAt(Instant.parse("2026-11-16T12:00:00Z"));
            return snap;
        });

        MetricsSnapshotDto dto = historyService.snapshot("cust-1", PricingContext.builder()
                .quoteExternalId("Q-9")
                .build());

        ArgumentCaptor<MetricsSnapshot> captor = ArgumentCaptor.forClass(MetricsSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getArr()).isEqualByComparingTo("1200.00");
        assertThat(captor.getValue().getTcv()).isEqualByComparingTo("3600.00");
        assertThat(captor.getValue().getChurnScore()).isEqualTo(42);
        assertThat(dto.getQuoteExternalId()).isEqualTo("Q-9");
        assertThat(dto.getId()).isEqualTo(11L);
    }

    @Test
    void snapshotRejectsCustomerMismatch() {
        when(customerRepository.findByExternalIdAndDeletedFalse("cust-1"))
                .thenReturn(Optional.of(Customer.builder().externalId("cust-1").name("A").build()));

        assertThatThrownBy(() -> historyService.snapshot("cust-1", PricingContext.builder()
                .customerExternalId("other")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void churnRiskReadsLatestSnapshot() {
        when(customerRepository.findByExternalIdAndDeletedFalse("cust-1"))
                .thenReturn(Optional.of(Customer.builder().externalId("cust-1").name("A").build()));
        MetricsSnapshot latest = MetricsSnapshot.builder()
                .id(4L)
                .customerExternalId("cust-1")
                .churnScore(77)
                .snapshotAt(Instant.parse("2026-11-16T12:00:00Z"))
                .build();
        when(snapshotRepository.findFirstByCustomerExternalIdOrderBySnapshotAtDesc("cust-1"))
                .thenReturn(Optional.of(latest));

        ChurnRiskDto risk = historyService.churnRisk("cust-1");

        assertThat(risk.getChurnScore()).isEqualTo(77);
        assertThat(risk.getSnapshotId()).isEqualTo(4L);
    }

    @Test
    void historyRequiresExistingCustomer() {
        when(customerRepository.findByExternalIdAndDeletedFalse("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> historyService.history("missing", 30))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void historyMapsSnapshots() {
        when(customerRepository.findByExternalIdAndDeletedFalse("cust-1"))
                .thenReturn(Optional.of(Customer.builder().externalId("cust-1").name("A").build()));
        when(snapshotRepository.findByCustomerExternalIdAndSnapshotAtGreaterThanEqualOrderBySnapshotAtDesc(
                any(), any()))
                .thenReturn(List.of(MetricsSnapshot.builder()
                        .id(1L)
                        .customerExternalId("cust-1")
                        .arr(new BigDecimal("10.00"))
                        .build()));

        assertThat(historyService.history("cust-1", 7)).hasSize(1);
    }
}
