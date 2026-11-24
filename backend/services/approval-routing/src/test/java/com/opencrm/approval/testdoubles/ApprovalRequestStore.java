package com.opencrm.approval.testdoubles;

import java.util.Optional;

/** Load seam for the DRG-444 specs. Feature re-points this at the approval repository. */
public interface ApprovalRequestStore {

    Optional<PendingApproval> find(long approvalId);
}
