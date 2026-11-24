package com.opencrm.approval.repository;

import com.opencrm.approval.domain.PolicyException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PolicyExceptionRepository extends JpaRepository<PolicyException, Long> {

    List<PolicyException> findByQuoteIdAndQuoteVersionAndTenantIdAndDeletedFalse(
            String quoteId, Long quoteVersion, String tenantId);
}
