package com.opencrm.approval.dto;

import com.opencrm.approval.domain.MatrixStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** API view of one approval-matrix version. The JPA entity stays inside the service. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalMatrixDto {

    private Long id;
    private String uuid;
    private String name;
    private MatrixStatus status;
    private Integer matrixVersion;
    private Long supersedesMatrixId;
    private String tenantId;
    private List<MatrixRuleDto> rules;
    private Instant createdAt;
}
