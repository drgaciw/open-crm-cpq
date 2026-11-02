package com.opencrm.crm.guided;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GuidedSellingSessionRepository extends JpaRepository<GuidedSellingSession, Long> {

    Optional<GuidedSellingSession> findByIdAndDeletedFalse(Long id);
}
