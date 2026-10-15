package com.opencrm.pricing.execution.margin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * The cost-basis margin of one pricing execution, as recorded in the audit {@code waterfall_trace}.
 *
 * <p>{@code quoteMarginFraction} is weighted by the same amounts as TCV and is absent when any line has no usable
 * cost. When this assessment replays an earlier one, {@code replayedFromAuditId} names that audit entry.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MarginAssessment {

    private String source;
    /** The pricing date period start dates are counted from. */
    private String costBasisAsOf;
    private BigDecimal quoteMarginFraction;
    private List<LineMargin> lines;
    private String replayedFromAuditId;

    /** A copy marked as the basis of a replay of the given audit entry. */
    public MarginAssessment withReplayedFrom(String auditId) {
        return toBuilder().replayedFromAuditId(auditId).build();
    }
}
