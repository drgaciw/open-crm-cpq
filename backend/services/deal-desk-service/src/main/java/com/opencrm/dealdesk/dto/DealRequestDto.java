package com.opencrm.dealdesk.dto;

import com.opencrm.dealdesk.model.DealStatus;
import com.opencrm.dealdesk.model.tcv.ContingentRevenue;
import com.opencrm.dealdesk.model.tcv.DiscountsAndAdjustments;
import com.opencrm.dealdesk.model.tcv.PricingModel;
import com.opencrm.dealdesk.model.tcv.TCVCalculation;
import com.opencrm.dealdesk.model.tcv.TCVComponent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealRequestDto {

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    private BigDecimal value;

    @Builder.Default
    @NotNull(message = "status is required")
    private DealStatus status = DealStatus.DRAFT;

    private String salesforceOpportunityId;

    private String accountId;

    private String accountName;

    private String notes;

    private List<TCVComponent> components;

    private PricingModel pricingModel;

    private DiscountsAndAdjustments adjustments;

    private ContingentRevenue contingentRevenue;

    private TCVCalculation tcvCalculation;

    private List<String> productIds;
}
