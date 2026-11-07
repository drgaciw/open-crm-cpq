package com.opencrm.crm.fields;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldValueWriteRequest {

    private Long organizationId;
    private Long contactId;
    private Long dealId;
    private String text;
    private BigDecimal numeric;
    private LocalDate date;
    private Boolean bool;
    private Long optionId;
    private String userId;

    private String createdBy;
    private String updatedBy;
    private String actor;
    private String archivedBy;
}
