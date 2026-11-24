package com.opencrm.approval.repository;

import com.opencrm.approval.domain.ApprovalMatrix;
import com.opencrm.approval.domain.MatrixStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalMatrixRepository extends JpaRepository<ApprovalMatrix, Long> {

    @EntityGraph(attributePaths = "rules")
    Optional<ApprovalMatrix> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = "rules")
    List<ApprovalMatrix> findByTenantIdAndStatusAndDeletedFalse(String tenantId, MatrixStatus status);
}
