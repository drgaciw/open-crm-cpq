package com.opencrm.quote.quote;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QuoteContinuationRequest {
    public enum Operation { AMENDMENT, EXPANSION, RENEWAL, EXTENSION }
    @NotNull private Operation operation;
    @NotBlank private String targetSubscriptionNumber;
    @NotBlank private String sourceSubscriptionLineUuid;
    @NotNull @PositiveOrZero private Long observedRevision;
    @NotBlank private String customerExternalId;
    @NotBlank private String currency;
    @NotNull @Positive private Integer quantity;
    private LocalDate effectiveDate;
    @Positive private Integer termMonths;
    @DecimalMin("0") @DecimalMax("100") private BigDecimal upliftPercent;
    private String name;
}
