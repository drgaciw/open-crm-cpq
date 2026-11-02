package com.opencrm.crm.playbook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlaybookRepository extends JpaRepository<Playbook, Long> {

    @Query("""
            SELECT p FROM Playbook p
            WHERE p.id = :id
              AND p.deleted = false
            """)
    Optional<Playbook> findActiveById(@Param("id") Long id);
}
