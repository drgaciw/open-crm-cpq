package com.opencrm.contract.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Open Host Service view of an asset. accountId is the only customer key. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDto {
    private Long id;
    private String uuid;
    private String accountId;
    private String contractLineId;
    private Long subscriptionId;
    private String entitlementId;
    private String productRef;
    private BigDecimal quantity;
    private AssetStatus status;
    private LocalDate effectiveStart;
    private LocalDate effectiveEnd;
    private String supersededBy;
    private String supersedes;
    private Instant createdAt;
    private Instant updatedAt;
}
