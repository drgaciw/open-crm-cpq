package com.opencrm.crm.playbook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaybookStepRepository extends JpaRepository<PlaybookStep, Long> {

    @Query("""
            SELECT s FROM PlaybookStep s
            WHERE s.playbook.id = :playbookId
              AND s.deleted = false
              AND s.stage = :stage
            ORDER BY s.sortOrder ASC, s.id ASC
            """)
    List<PlaybookStep> findActiveForStage(@Param("playbookId") Long playbookId, @Param("stage") PlaybookStage stage);
}
