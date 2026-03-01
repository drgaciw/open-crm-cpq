package com.opencrm.document.repository;

import com.opencrm.document.domain.DocumentGenerationJob;
import com.opencrm.document.domain.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentGenerationJobRepository extends JpaRepository<DocumentGenerationJob, Long> {
    
    boolean existsByIdempotencyKey(String idempotencyKey);
    
    List<DocumentGenerationJob> findByStatus(JobStatus status);
    
    List<DocumentGenerationJob> findByQuoteId(String quoteId);
}
