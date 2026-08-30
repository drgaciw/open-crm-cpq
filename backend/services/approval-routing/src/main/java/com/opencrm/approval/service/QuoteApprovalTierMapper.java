package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.common.exception.BusinessException;

/**
 * Maps quote BFF tiers to canonical approval-routing tier + type.
 * Discount Kafka path uses a separate vocabulary (MANAGER / DIRECTOR / VP_SALES).
 */
public final class QuoteApprovalTierMapper {

    private QuoteApprovalTierMapper() {
    }

    public record MappedApproval(String approvalTier, ApprovalType approvalType) {
    }

    public static MappedApproval map(QuoteApprovalTier tier) {
        if (tier == null) {
            throw new BusinessException("Approval tier is required", "INVALID_APPROVAL_TIER");
        }
        return switch (tier) {
            case DEAL_DESK -> new MappedApproval("DEAL_DESK", ApprovalType.QUOTE);
            case SALES_VP -> new MappedApproval("SALES_VP", ApprovalType.QUOTE);
            case CFO -> new MappedApproval("CFO", ApprovalType.GOVERNANCE);
        };
    }
}
