package com.opencrm.crm.integration.dealdesk;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Local request contract for promoting a CRM deal into Deal Desk.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DealPromotionRequest {

    private Long crmDealId;
    private String crmDealUuid;
    private String name;
    private Long organizationId;
    private Long primaryContactId;
    private BigDecimal amount;
    private String currency;
    private String ownerId;
    private String description;
}
