package com.opencrm.quote.history;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatusHistoryDto {

    private Long id;
    private Long quoteId;
    private String fromStatus;
    private String toStatus;
    private String changedBy;
    private String reason;
    private Instant changedAt;
}
