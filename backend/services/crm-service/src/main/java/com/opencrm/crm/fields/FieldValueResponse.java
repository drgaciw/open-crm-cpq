package com.opencrm.crm.fields;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class FieldValueResponse {
    Long id;
    Long definitionId;
    Long organizationId;
    Long contactId;
    Long dealId;
    String text;
    BigDecimal numeric;
    LocalDate date;
    Boolean bool;
    Long optionId;
    String userId;
    String createdBy;
    String updatedBy;
    long version;
}
