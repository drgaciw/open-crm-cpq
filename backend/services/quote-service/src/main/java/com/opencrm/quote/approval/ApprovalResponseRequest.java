package com.opencrm.quote.approval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Approve or reject. The approver is the authenticated user, never a field (ADR-016 decision 6). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalResponseRequest {

    private String comments;
}
