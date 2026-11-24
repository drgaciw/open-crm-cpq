package com.opencrm.approval.testdoubles;

import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.ApprovalMatrixOperations;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.ApprovalMatrixStore;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.ApprovalMatrixView;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.CreatedApprovalRequest;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.DefineApprovalMatrix;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.MatrixLookup;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.PendingApproval;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.SubmitForApproval;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.SubmitForApprovalResult;

import java.util.List;

/**
 * Not-implemented double for the DRG-443 contract. It accepts the collaborator calls the specs wire with Mockito,
 * then returns a sentinel that does not satisfy the assertions. The specs stay red until Feature replaces this
 * class with the production service. Do not teach it the real behavior.
 */
public final class NotImplementedApprovalMatrix implements ApprovalMatrixOperations {

    static final ApprovalMatrixView UNDEFINED = new ApprovalMatrixView(
            0L, 0, null, "not-implemented", null, List.of());

    static final ApprovalMatrixView LOOKUP_SENTINEL = new ApprovalMatrixView(
            -1L, -1, -1L, "not-implemented", null, List.of());

    static final SubmitForApprovalResult SUBMIT_SENTINEL = new SubmitForApprovalResult(
            List.of(new CreatedApprovalRequest(0L, "not-implemented", null, 0, null, null)),
            null);

    private final ApprovalMatrixStore store;
    private final MatrixLookup lookup;
    private final List<PendingApproval> pending;

    private NotImplementedApprovalMatrix(
            ApprovalMatrixStore store, MatrixLookup lookup, List<PendingApproval> pending) {
        this.store = store;
        this.lookup = lookup;
        this.pending = pending == null ? List.of() : List.copyOf(pending);
    }

    static NotImplementedApprovalMatrix defineOnly(ApprovalMatrixStore store) {
        return new NotImplementedApprovalMatrix(store, null, List.of());
    }

    static NotImplementedApprovalMatrix submitOnly(MatrixLookup lookup) {
        return new NotImplementedApprovalMatrix(null, lookup, List.of());
    }

    static NotImplementedApprovalMatrix queueOf(List<PendingApproval> pending) {
        return new NotImplementedApprovalMatrix(null, null, pending);
    }

    @Override
    public ApprovalMatrixView defineApprovalMatrix(DefineApprovalMatrix command) {
        if (store != null) {
            store.record(command);
        }
        return UNDEFINED;
    }

    @Override
    public ApprovalMatrixView approvalMatrix(long matrixId, int version) {
        return LOOKUP_SENTINEL;
    }

    @Override
    public SubmitForApprovalResult submitForApproval(SubmitForApproval command) {
        if (lookup != null && command != null) {
            lookup.find(command.matrixId(), command.matrixVersion());
        }
        return SUBMIT_SENTINEL;
    }

    /**
     * Returns every seeded row. The approver argument is ignored, so a queue filtered to one approver fails.
     */
    @Override
    public List<PendingApproval> listPendingApprovals(QuoteApprovalTier approver) {
        return pending;
    }
}
