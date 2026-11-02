package com.opencrm.crm.deal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO for Deal entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DealDto {

    private Long id;
    private String uuid;
    private String name;
    private Long organizationId;
    private String organizationName;
    private Long primaryContactId;
    private String primaryContactName;
    private DealStage stage;
    private BigDecimal amount;
    private String currency;
    private Integer probability;
    private LocalDate expectedCloseDate;
    private LocalDate actualCloseDate;
    private String description;
    private String ownerId;
    private Long sourceLeadId;
    private DealType type;
    private String lostReason;
    /** Quote id recorded by an accepted quote. Null until that acceptance is applied. */
    private String acceptedQuoteId;
    private Instant wonAt;
    private Instant lostAt;
    private String nextStep;
    private LocalDate nextStepDate;
    private String competitors;
    private BigDecimal weightedAmount;
    /** ADR-032: amount frozen in the base currency; null when not converted. */
    private BigDecimal baseAmount;
    private String baseCurrency;
    private BigDecimal baseWeightedAmount;
    private BigDecimal fxRate;
    private Instant fxRateAsOf;
    private String fxSource;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
