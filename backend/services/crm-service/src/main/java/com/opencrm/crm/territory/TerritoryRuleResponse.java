package com.opencrm.crm.territory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A stored territory and segment rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerritoryRuleResponse {

    private Long id;
    private String name;
    private String country;
    private String state;
    private String industry;
    private Integer minEmployeeCount;
    private Integer maxEmployeeCount;
    private String territoryCode;
    private String segmentCode;
    private int priority;
    private boolean active;
}
