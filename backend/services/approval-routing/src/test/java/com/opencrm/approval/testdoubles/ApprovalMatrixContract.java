package com.opencrm.approval.testdoubles;

import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.domain.ThresholdType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Test-local contract for DRG-443 (BC-04-03). These types are not production code.
 *
 * <p>Feature implements the operations on a new approval-routing service (expected package
 * {@code com.opencrm.approval.service}). {@link com.opencrm.approval.service.ApprovalService#submit} stays the
 * explicit-tier groundwork ({@code DEAL_DESK}, {@code SALES_VP}, {@code CFO}) and is not this contract.
 * The quote-service BFF is the writer (ADR-013); these unit specs cover the approval-routing operations that BFF
 * calls, not the HTTP edge.
 *
 * <p>Re-point when the production types exist:
 * <ol>
 *   <li>Change the three specs to construct the production service instead of
 *       {@link NotImplementedApprovalMatrix}.</li>
 *   <li>Replace {@link ApprovalMatrixStore} and {@link MatrixLookup} with the production repository (Mockito mocks
 *       of that repository are enough for these unit specs).</li>
 *   <li>Map {@link CreatedApprovalRequest#approverRole} onto {@code ApprovalRequest.approvalTier} (the enum name)
 *       and {@link CreatedApprovalRequest#status} onto {@link ApprovalStatus}.</li>
 *   <li>Publish {@link ApprovalRequested} through the outbox, the same way reject publishes
 *       {@code ApprovalRejectedEvent}. Point {@code submitForApproval_emitsApprovalRequested} at that publish.</li>
 *   <li>Version the matrix the way {@code CommercialPolicy} is versioned: a revision is a new row, version starts
 *       at 1, {@code supersedesId} names the prior row, and the prior version stays readable.</li>
 *   <li>Delete this package. Do not weaken the assertions to make {@link NotImplementedApprovalMatrix} pass.</li>
 * </ol>
 */
final class ApprovalMatrixContract {

    private ApprovalMatrixContract() {
    }

    enum StepMode {
        SERIAL,
        PARALLEL
    }

    /** DRG-442 verdict vocabulary, held here until that type exists in production. */
    enum PolicyVerdict {
        PASS,
        REQUIRES_APPROVAL,
        BLOCKED
    }

    /**
     * One matrix step. {@code minAmountInclusive} and {@code maxAmountExclusive} select the step for a breach
     * amount. A null bound means unbounded on that side. Same sequence with disjoint bands is a role map, not a
     * chain. {@link StepMode#SERIAL} steps with increasing sequences run in order. {@link StepMode#PARALLEL} roles
     * on one step all approve that step.
     */
    record MatrixStep(
            int sequence,
            StepMode mode,
            List<QuoteApprovalTier> roles,
            BigDecimal minAmountInclusive,
            BigDecimal maxAmountExclusive) {
    }

    record DefineApprovalMatrix(
            String name,
            ThresholdType breachType,
            List<MatrixStep> steps,
            Long supersedesId) {

        DefineApprovalMatrix reviseFrom(ApprovalMatrixView prior) {
            return new DefineApprovalMatrix(name, breachType, steps, prior.id());
        }
    }

    record ApprovalMatrixView(
            long id,
            int version,
            Long supersedesId,
            String name,
            ThresholdType breachType,
            List<MatrixStep> steps) {
    }

    record PolicyBreach(
            ThresholdType breachType,
            BigDecimal actual,
            BigDecimal limit,
            BigDecimal amount) {
    }

    record SubmitForApproval(
            String quoteId,
            String quoteVersionId,
            PolicyVerdict verdict,
            List<PolicyBreach> breaches,
            long matrixId,
            int matrixVersion) {
    }

    record CreatedApprovalRequest(
            Long id,
            String quoteId,
            QuoteApprovalTier approverRole,
            int stepSequence,
            StepMode stepMode,
            ApprovalStatus status) {
    }

    /**
     * Payload of the ApprovalRequested event. Feature publishes it when submit creates requests.
     * A pass verdict does not emit it.
     */
    record ApprovalRequested(
            String quoteId,
            String quoteVersionId,
            long matrixId,
            int matrixVersion,
            List<Long> approvalRequestIds) {
    }

    record SubmitForApprovalResult(List<CreatedApprovalRequest> requests, ApprovalRequested event) {
    }

    record PendingApproval(
            long id,
            QuoteApprovalTier approver,
            String quoteId,
            int stepSequence,
            ApprovalStatus status) {
    }

    interface ApprovalMatrixStore {
        void record(DefineApprovalMatrix command);
    }

    interface MatrixLookup {
        ApprovalMatrixView find(long matrixId, int version);
    }

    interface ApprovalMatrixOperations {
        ApprovalMatrixView defineApprovalMatrix(DefineApprovalMatrix command);

        ApprovalMatrixView approvalMatrix(long matrixId, int version);

        SubmitForApprovalResult submitForApproval(SubmitForApproval command);

        List<PendingApproval> listPendingApprovals(QuoteApprovalTier approver);
    }
}
