package com.opencrm.approval.service;

import java.math.BigDecimal;

public record ApprovalPolicyRequest(
        BigDecimal quoteTotalAmount,
        BigDecimal maximumDiscountPercent,
        BigDecimal minimumGrossMarginPercent,
        String requesterId,
        String requesterApprovalTier
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private BigDecimal quoteTotalAmount;
        private BigDecimal maximumDiscountPercent;
        private BigDecimal minimumGrossMarginPercent;
        private String requesterId;
        private String requesterApprovalTier;

        public Builder quoteTotalAmount(BigDecimal quoteTotalAmount) {
            this.quoteTotalAmount = quoteTotalAmount;
            return this;
        }

        public Builder maximumDiscountPercent(BigDecimal maximumDiscountPercent) {
            this.maximumDiscountPercent = maximumDiscountPercent;
            return this;
        }

        public Builder minimumGrossMarginPercent(BigDecimal minimumGrossMarginPercent) {
            this.minimumGrossMarginPercent = minimumGrossMarginPercent;
            return this;
        }

        public Builder requesterId(String requesterId) {
            this.requesterId = requesterId;
            return this;
        }

        public Builder requesterApprovalTier(String requesterApprovalTier) {
            this.requesterApprovalTier = requesterApprovalTier;
            return this;
        }

        public ApprovalPolicyRequest build() {
            return new ApprovalPolicyRequest(
                    quoteTotalAmount,
                    maximumDiscountPercent,
                    minimumGrossMarginPercent,
                    requesterId,
                    requesterApprovalTier
            );
        }
    }
}
