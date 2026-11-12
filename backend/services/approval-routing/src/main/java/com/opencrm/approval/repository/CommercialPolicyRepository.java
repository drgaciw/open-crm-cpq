package com.opencrm.approval.repository;

import com.opencrm.approval.domain.CommercialPolicy;
import com.opencrm.approval.domain.PolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommercialPolicyRepository extends JpaRepository<CommercialPolicy, Long> {

    Optional<CommercialPolicy> findByIdAndDeletedFalse(Long id);

    Page<CommercialPolicy> findByDeletedFalse(Pageable pageable);

    List<CommercialPolicy> findByStatusAndDeletedFalse(PolicyStatus status);
}
