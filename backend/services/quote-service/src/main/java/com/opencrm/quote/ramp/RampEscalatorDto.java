package com.opencrm.quote.ramp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Authoring generator inputs. Kept for display. Only the expanded steps are priced.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RampEscalatorDto {

    private BigDecimal startPrice;
    private RampEscalatorMode mode;
    private BigDecimal amount;
    private Integer everyMonths;

    /** Omitted/null retains compound growth; false applies each increase to startPrice. */
    private Boolean compounding;
}
