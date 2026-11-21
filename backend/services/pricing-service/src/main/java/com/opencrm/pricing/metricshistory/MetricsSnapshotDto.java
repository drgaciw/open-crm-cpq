package com.opencrm.pricing.metricshistory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsSnapshotDto {

    private Long id;
    private String uuid;
    private String customerExternalId;
    private String quoteExternalId;
    private BigDecimal arr;
    private BigDecimal tcv;
    private BigDecimal acv;
    private BigDecimal clv;
    private Integer churnScore;
    private BigDecimal discountRate;
    private BigDecimal totalDiscount;
    private Instant snapshotAt;
}
