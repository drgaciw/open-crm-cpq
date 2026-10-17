package com.opencrm.contract.repository.documents;

import com.opencrm.contract.domain.documents.RenewalPolicyRecord;
import com.opencrm.contract.domain.documents.RenewalPolicyScopeKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped finders only. Callers do not use the inherited unscoped {@code findById}. */
@Repository
public interface RenewalPolicyRecordRepository extends JpaRepository<RenewalPolicyRecord, UUID> {

    List<RenewalPolicyRecord> findByTenantIdAndScopeKindAndScopeRefOrderByPolicyVersionAsc(
            UUID tenantId, RenewalPolicyScopeKind scopeKind, UUID scopeRef);

    Optional<RenewalPolicyRecord> findByTenantIdAndScopeKindAndScopeRefAndPolicyVersion(
            UUID tenantId, RenewalPolicyScopeKind scopeKind, UUID scopeRef, int policyVersion);
}
