package com.opencrm.pricing.metricshistory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChurnRiskDto {

    private String customerExternalId;
    private Integer churnScore;
    private Instant snapshotAt;
    private Long snapshotId;
}
