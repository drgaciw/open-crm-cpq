package com.opencrm.crm.lead;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Lead entity.
 */
@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    /**
     * Find lead by UUID.
     */
    @EntityGraph(attributePaths = {"contact", "organization"})
    Optional<Lead> findByUuid(String uuid);

    /**
     * Find leads by status (excluding deleted).
     */
    @EntityGraph(attributePaths = {"contact", "organization"})
    Page<Lead> findByStatusAndDeletedFalse(LeadStatus status, Pageable pageable);

    /**
     * Find leads by owner (excluding deleted).
     */
    @EntityGraph(attributePaths = {"contact", "organization"})
    Page<Lead> findByOwnerIdAndDeletedFalse(String ownerId, Pageable pageable);

    /**
     * Find all non-deleted leads.
     */
    @EntityGraph(attributePaths = {"contact", "organization"})
    Page<Lead> findAllByDeletedFalse(Pageable pageable);

    /**
     * Find leads by priority (excluding deleted).
     */
    @EntityGraph(attributePaths = {"contact", "organization"})
    Page<Lead> findByPriorityAndDeletedFalse(LeadPriority priority, Pageable pageable);

    /**
     * Search leads by title using case-insensitive partial match.
     */
    @EntityGraph(attributePaths = {"contact", "organization"})
    @Query("SELECT l FROM Lead l WHERE LOWER(l.title) LIKE LOWER(CONCAT('%', :query, '%')) AND l.deleted = false")
    Page<Lead> searchByTitle(@Param("query") String query, Pageable pageable);

    /**
     * Count leads by status (excluding deleted).
     */
    long countByStatusAndDeletedFalse(LeadStatus status);

    /**
     * Count leads grouped by status (excluding deleted).
     */
    @Query("SELECT l.status, COUNT(l) FROM Lead l WHERE l.deleted = false GROUP BY l.status")
    List<Object[]> countGroupByStatus();
}
