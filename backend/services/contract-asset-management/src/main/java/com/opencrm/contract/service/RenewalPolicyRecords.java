package com.opencrm.contract.service;

import com.opencrm.contract.domain.documents.RenewalPolicyRecord;
import com.opencrm.contract.domain.documents.RenewalPolicyScopeKind;

import java.util.List;
import java.util.UUID;

/** Persistence port for renewal policy versions. Inserts are tenant-scoped; there is no unscoped read. */
public interface RenewalPolicyRecords {

    List<RenewalPolicyRecord> findScoped(UUID tenantId, RenewalPolicyScopeKind scopeKind, UUID scopeRef);

    /**
     * Inserts one version. A duplicate {@code (tenant, scope kind, scope ref, version)} throws
     * {@link RenewalPolicyVersionConflictException}.
     */
    RenewalPolicyRecord insert(RenewalPolicyRecord record);
}
