package com.opencrm.quote.ramp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body of {@code POST /api/v1/quotes/{quoteId}/ramp-schedule/extend}.
 * Without the paired line/version fields this previews only. With both supplied,
 * the extended line schedule and quote term are committed atomically.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RampExtendRequest {

    private List<RampStepDto> steps;
    /** Whole term after the extension, not the number of months to add. */
    private Integer durationInMonths;
    private RampEscalatorDto escalator;
    private Long lineItemId;
    private Long expectedQuoteVersion;
}
