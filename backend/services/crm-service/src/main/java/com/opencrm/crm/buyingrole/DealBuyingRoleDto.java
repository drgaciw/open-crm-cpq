package com.opencrm.crm.buyingrole;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One seat on a deal's buying committee.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DealBuyingRoleDto {

    private Long id;
    private String uuid;
    private Long dealId;
    private Long contactId;
    private String contactName;
    private BuyingRole role;
}
