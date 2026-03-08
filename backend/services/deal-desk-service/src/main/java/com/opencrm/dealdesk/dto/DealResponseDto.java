package com.opencrm.dealdesk.dto;

import com.opencrm.dealdesk.model.DealStatus;
import com.opencrm.dealdesk.model.tcv.ContingentRevenue;
import com.opencrm.dealdesk.model.tcv.DiscountsAndAdjustments;
import com.opencrm.dealdesk.model.tcv.PricingModel;
import com.opencrm.dealdesk.model.tcv.TCVCalculation;
import com.opencrm.dealdesk.model.tcv.TCVComponent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealResponseDto {
    private Long id;
    private String uuid;
    private String name;
    private String description;
    private BigDecimal value;
    private DealStatus status;
    private String salesforceOpportunityId;
    private String accountId;
    private String accountName;
    private String notes;
    private List<TCVComponent> components;
    private PricingModel pricingModel;
    private DiscountsAndAdjustments adjustments;
    private ContingentRevenue contingentRevenue;
    private TCVCalculation tcvCalculation;
    private Boolean synced;
    private List<String> productIds;
    private Instant createdAt;
    private Instant updatedAt;
}
