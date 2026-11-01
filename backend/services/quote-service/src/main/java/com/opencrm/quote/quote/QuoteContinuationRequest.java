package com.opencrm.quote.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QuoteContinuationRequest {
    public enum Operation { AMENDMENT, EXPANSION, RENEWAL, EXTENSION }

    /**
     * Renewal line source. Absent keeps the single accepted source line.
     * DRG-593 "from terms" and "from scratch" are not modes in this service.
     */
    public enum RenewalSourceMode { FROM_ACTIVE_ASSETS }

    @NotNull private Operation operation;
    @NotBlank private String targetSubscriptionNumber;
    /** Required unless {@link #renewalSourceMode} is {@link RenewalSourceMode#FROM_ACTIVE_ASSETS}. */
    private String sourceSubscriptionLineUuid;
    @NotNull @PositiveOrZero private Long observedRevision;
    @NotBlank private String customerExternalId;
    @NotBlank private String currency;
    /** Required unless lines are taken from active assets. */
    @Positive private Integer quantity;
    private LocalDate effectiveDate;
    @Positive private Integer termMonths;
    @DecimalMin("0") @DecimalMax("100") private BigDecimal upliftPercent;
    private String name;

    /** Set by the caller on {@code POST /api/v1/quotes/continuations}. */
    private RenewalSourceMode renewalSourceMode;

    /** Narrows from-active-assets to one contract line. Absent uses every active asset on the target subscription. */
    private String contractLineId;

    /**
     * CAM renewal-policy account scope. Used when {@code customerExternalId} is not a UUID.
     * Asset reads still key on the subscription customer id.
     */
    private UUID renewalPolicyAccountId;

    /** Optional CAM renewal-policy agreement scope. */
    private UUID renewalPolicyAgreementId;

    /** Server-recorded asset ids. Callers do not choose the lines by sending this. */
    private List<String> renewalAssetIds;

    @JsonIgnore
    @AssertTrue(message = "A continuation needs a source line and a positive quantity")
    public boolean continuationIdentitiesPresent() {
        if (renewalSourceMode == RenewalSourceMode.FROM_ACTIVE_ASSETS) {
            return true;
        }
        return sourceSubscriptionLineUuid != null && !sourceSubscriptionLineUuid.isBlank()
                && quantity != null && quantity > 0;
    }
}
