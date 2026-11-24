package com.opencrm.quote.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body for approval-routing {@code POST /api/v1/approvals/matrix-submissions}.
 * The quote BFF copies {@code quoteTenantId} from the quote row. It names no requester.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingMatrixSubmitRequest {

    private String quoteId;
    private String quoteVersion;
    private String verdict;
    private String quoteTenantId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long matrixId;
    private List<Breach> breaches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Breach {
        private String breachType;
        private BigDecimal actual;
        private BigDecimal limit;
        private BigDecimal amount;
        private String clauseCode;
    }
}
