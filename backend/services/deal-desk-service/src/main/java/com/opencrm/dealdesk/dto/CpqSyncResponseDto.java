package com.opencrm.dealdesk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpqSyncResponseDto {
    private String operation;
    private String quoteId;
    private String status;
    private Boolean salesforceEnabled;
    private List<Map<String, Object>> evaluationRows;
}
