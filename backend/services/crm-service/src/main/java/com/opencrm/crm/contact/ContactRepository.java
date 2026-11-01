package com.opencrm.crm.contact;

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
 * Repository for Contact entity.
 */
@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    /**
     * Find contact by ID with organization eagerly loaded.
     */
    @Override
    @EntityGraph(attributePaths = {"organization"})
    Optional<Contact> findById(Long id);

    /**
     * Find contact by UUID.
     */
    @EntityGraph(attributePaths = {"organization"})
    Optional<Contact> findByUuid(String uuid);

    /**
     * Find contact by email (case-insensitive).
     */
    Optional<Contact> findByEmailIgnoreCase(String email);

    /**
     * Find all contacts belonging to an organization.
     */
    @EntityGraph(attributePaths = {"organization"})
    Page<Contact> findByOrganizationIdAndDeletedFalse(Long organizationId, Pageable pageable);

    /**
     * Every contact row for the organization, including soft-deleted rows, so a merge can repoint the foreign key.
     */
    List<Contact> findAllByOrganizationId(Long organizationId);

    /**
     * Find all contacts with a specific status (non-deleted).
     */
    @EntityGraph(attributePaths = {"organization"})
    Page<Contact> findByStatusAndDeletedFalse(ContactStatus status, Pageable pageable);

    /**
     * Find all non-deleted contacts.
     */
    @EntityGraph(attributePaths = {"organization"})
    Page<Contact> findAllByDeletedFalse(Pageable pageable);

    /**
     * Search contacts by first or last name (case-insensitive).
     */
    @EntityGraph(attributePaths = {"organization"})
    @Query("SELECT c FROM Contact c WHERE c.deleted = false AND " +
           "(LOWER(c.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.lastName) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Contact> searchByName(@Param("query") String query, Pageable pageable);

    /**
     * Check if a contact with the given email exists (non-deleted, case-insensitive).
     */
    boolean existsByEmailIgnoreCaseAndDeletedFalse(String email);

    /**
     * Count contacts by organization (excluding deleted).
     */
    long countByOrganizationIdAndDeletedFalse(Long organizationId);
}
