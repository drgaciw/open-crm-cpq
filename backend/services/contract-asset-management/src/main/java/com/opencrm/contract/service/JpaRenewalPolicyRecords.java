package com.opencrm.contract.service;

import com.opencrm.contract.domain.documents.RenewalPolicyRecord;
import com.opencrm.contract.domain.documents.RenewalPolicyScopeKind;
import com.opencrm.contract.repository.documents.RenewalPolicyRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** JPA adapter. Every read passes {@code tenantId}. {@code findById} is not used. */
@Component
public class JpaRenewalPolicyRecords implements RenewalPolicyRecords {

    private final RenewalPolicyRecordRepository repository;

    public JpaRenewalPolicyRecords(RenewalPolicyRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<RenewalPolicyRecord> findScoped(UUID tenantId, RenewalPolicyScopeKind scopeKind, UUID scopeRef) {
        return repository.findByTenantIdAndScopeKindAndScopeRefOrderByPolicyVersionAsc(tenantId, scopeKind, scopeRef);
    }

    @Override
    public RenewalPolicyRecord insert(RenewalPolicyRecord record) {
        try {
            return repository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            throw new RenewalPolicyVersionConflictException(ex);
        }
    }
}
