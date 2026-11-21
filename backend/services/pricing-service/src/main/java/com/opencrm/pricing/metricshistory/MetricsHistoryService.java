package com.opencrm.pricing.metricshistory;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.customer.CustomerRepository;
import com.opencrm.pricing.execution.PricingExecutionService;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Persists MetricsCalculator outputs from simulate; does not reimplement ARR/TCV/ACV/CLV (PD2).
 */
@Service
@RequiredArgsConstructor
public class MetricsHistoryService {

    private final CustomerRepository customerRepository;
    private final MetricsSnapshotRepository snapshotRepository;
    private final PricingExecutionService pricingExecutionService;

    @Transactional
    public MetricsSnapshotDto snapshot(String customerExternalId, PricingContext context) {
        Customer customer = customerRepository.findByExternalIdAndDeletedFalse(customerExternalId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerExternalId));
        PricingContext toRun = context == null ? new PricingContext() : context;
        if (toRun.getCustomerExternalId() == null || toRun.getCustomerExternalId().isBlank()) {
            toRun.setCustomerExternalId(customer.getExternalId());
        } else if (!customerExternalId.equals(toRun.getCustomerExternalId())) {
            throw new BusinessException(
                    "Pricing context customer does not match path customer", "CUSTOMER_MISMATCH");
        }
        PricingResult result = pricingExecutionService.simulate(toRun);
        if (result == null || !result.isSuccess()) {
            throw new BusinessException(
                    result == null ? "Simulation returned null" : result.getErrorMessage(),
                    "SNAPSHOT_SIMULATION_FAILED");
        }
        FinancialMetrics metrics = result.getFinancialMetrics();
        if (metrics == null) {
            throw new BusinessException("Simulation produced no financial metrics", "SNAPSHOT_METRICS_MISSING");
        }
        MetricsSnapshot saved = snapshotRepository.save(MetricsSnapshot.builder()
                .customerExternalId(customerExternalId)
                .quoteExternalId(toRun.getQuoteExternalId())
                .arr(metrics.getArr())
                .tcv(metrics.getTcv())
                .acv(metrics.getAcv())
                .clv(metrics.getClv())
                .churnScore(metrics.getChurnScore())
                .discountRate(metrics.getDiscountRate())
                .totalDiscount(metrics.getTotalDiscount())
                .build());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<MetricsSnapshotDto> history(String customerExternalId, int days) {
        ensureCustomer(customerExternalId);
        int lookback = days <= 0 ? 30 : days;
        Instant cutoff = Instant.now().minus(Duration.ofDays(lookback));
        return snapshotRepository
                .findByCustomerExternalIdAndSnapshotAtGreaterThanEqualOrderBySnapshotAtDesc(
                        customerExternalId, cutoff)
                .stream()
                .map(MetricsHistoryService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChurnRiskDto churnRisk(String customerExternalId) {
        ensureCustomer(customerExternalId);
        MetricsSnapshot latest = snapshotRepository
                .findFirstByCustomerExternalIdOrderBySnapshotAtDesc(customerExternalId)
                .orElseThrow(() -> new ResourceNotFoundException("MetricsSnapshot", customerExternalId));
        return ChurnRiskDto.builder()
                .customerExternalId(customerExternalId)
                .churnScore(latest.getChurnScore())
                .snapshotAt(latest.getSnapshotAt())
                .snapshotId(latest.getId())
                .build();
    }

    private void ensureCustomer(String customerExternalId) {
        customerRepository.findByExternalIdAndDeletedFalse(customerExternalId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerExternalId));
    }

    private static MetricsSnapshotDto toDto(MetricsSnapshot snapshot) {
        return MetricsSnapshotDto.builder()
                .id(snapshot.getId())
                .uuid(snapshot.getUuid())
                .customerExternalId(snapshot.getCustomerExternalId())
                .quoteExternalId(snapshot.getQuoteExternalId())
                .arr(snapshot.getArr())
                .tcv(snapshot.getTcv())
                .acv(snapshot.getAcv())
                .clv(snapshot.getClv())
                .churnScore(snapshot.getChurnScore())
                .discountRate(snapshot.getDiscountRate())
                .totalDiscount(snapshot.getTotalDiscount())
                .snapshotAt(snapshot.getSnapshotAt())
                .build();
    }
}
