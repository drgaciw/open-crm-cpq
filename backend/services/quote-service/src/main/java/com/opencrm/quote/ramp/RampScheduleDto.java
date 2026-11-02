package com.opencrm.quote.ramp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RampScheduleDto {

    private List<RampStepDto> steps;
    private RampEscalatorDto escalator;
    /** Present only after an atomic extension save. */
    private Integer durationInMonths;
    private Long quoteVersion;
    private com.opencrm.quote.quote.QuoteStatus quoteStatus;
    private java.time.LocalDate endDate;
}
