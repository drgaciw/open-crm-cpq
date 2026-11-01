package com.opencrm.crm.organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Organization entity.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {

    /**
     * Find organization by UUID.
     */
    Optional<Organization> findByUuid(String uuid);

    /** Legacy imports may have stored canonical UUIDs in uppercase. */
    Optional<Organization> findByUuidIgnoreCase(String uuid);

    /**
     * Find organizations by name (case-insensitive partial match).
     */
    Page<Organization> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);

    /**
     * Find all organizations with a specific type (non-deleted).
     */
    Page<Organization> findByTypeAndDeletedFalse(OrganizationType type, Pageable pageable);

    /**
     * Find all non-deleted organizations.
     */
    Page<Organization> findAllByDeletedFalse(Pageable pageable);

    /**
     * Search organizations by name (case-insensitive).
     */
    @Query("SELECT o FROM Organization o WHERE o.deleted = false AND " +
           "LOWER(o.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Organization> searchByName(@Param("query") String query, Pageable pageable);

    /**
     * Check if organization exists by ID.
     */
    boolean existsById(Long id);
}
