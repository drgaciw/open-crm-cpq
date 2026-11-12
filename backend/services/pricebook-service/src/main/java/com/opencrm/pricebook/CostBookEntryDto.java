package com.opencrm.pricebook;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CostBookEntryDto {

    private Long id;
    private String uuid;
    private Long costBookId;
    private String sku;
    private BigDecimal cost;
    private String currency;
    private String unitOfMeasure;
    private LocalDate effectiveStartDate;
    private LocalDate effectiveEndDate;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
