package com.opencrm.quote.ramp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code POST /api/v1/quotes/{quoteId}/ramp-schedule/expand}. Nothing is stored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RampExpandRequest {

    private Integer durationInMonths;
    private RampEscalatorDto escalator;
    /** Copied onto each expanded step when the caller already knows the line quantity. */
    private Integer quantity;
}
