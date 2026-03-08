package com.opencrm.crm.integration.dealdesk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local response contract for Deal Desk deal creation.
 *
 * Kept intentionally small and tolerant so downstream schema evolution
 * in deal-desk-service does not break CRM deserialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DealPromotionResponse {

    private Long id;
    private String uuid;
    private String status;
}
