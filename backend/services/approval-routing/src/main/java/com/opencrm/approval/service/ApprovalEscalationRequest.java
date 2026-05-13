package com.opencrm.approval.service;

import java.time.Instant;

public record ApprovalEscalationRequest(
        String currentTier,
        Instant slaDeadline,
        String status
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String currentTier;
        private Instant slaDeadline;
        private String status;

        public Builder currentTier(String currentTier) {
            this.currentTier = currentTier;
            return this;
        }

        public Builder slaDeadline(Instant slaDeadline) {
            this.slaDeadline = slaDeadline;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public ApprovalEscalationRequest build() {
            return new ApprovalEscalationRequest(currentTier, slaDeadline, status);
        }
    }
}
