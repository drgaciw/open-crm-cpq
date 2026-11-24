package com.opencrm.approval.repository;

import com.opencrm.approval.domain.ApprovalCaseComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApprovalCaseCommentRepository extends JpaRepository<ApprovalCaseComment, Long> {

    Optional<ApprovalCaseComment> findByIdAndDeletedFalse(Long id);
}
