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

    /**
     * Active contacts with the same trimmed, lowercased email. Read-only: no {@code @Modifying}.
     * The caller passes a 50-row page.
     */
    @EntityGraph(attributePaths = {"organization"})
    @Query("""
            SELECT c FROM Contact c
            WHERE c.deleted = false
              AND c.status = :status
              AND c.id <> :excludeId
              AND c.email IS NOT NULL
              AND LOWER(TRIM(c.email)) = :normalizedEmail
            """)
    List<Contact> findActiveByNormalizedEmail(@Param("excludeId") Long excludeId,
                                              @Param("normalizedEmail") String normalizedEmail,
                                              @Param("status") ContactStatus status,
                                              Pageable pageable);

    /**
     * Active contacts in one organization whose normalized first and last name equal
     * {@code normalizedName}. The predicate trims, lowercases, and collapses internal whitespace
     * before the caller's page, which is at most 50 rows ordered by id. Read-only: no {@code @Modifying}.
     */
    @EntityGraph(attributePaths = {"organization"})
    @Query("""
            SELECT c FROM Contact c
            WHERE c.deleted = false
              AND c.status = :status
              AND c.id <> :excludeId
              AND c.organization.id = :organizationId
              AND concat(
                    function('regexp_replace',
                        function('regexp_replace', lower(c.firstName), '^[[:space:]]+|[[:space:]]+$', '', 'g'),
                        '[[:space:]]+', ' ', 'g'),
                    ' ',
                    function('regexp_replace',
                        function('regexp_replace', lower(c.lastName), '^[[:space:]]+|[[:space:]]+$', '', 'g'),
                        '[[:space:]]+', ' ', 'g')
                  ) = :normalizedName
            ORDER BY c.id ASC
            """)
    List<Contact> findActiveInOrganization(@Param("excludeId") Long excludeId,
                                           @Param("organizationId") Long organizationId,
                                           @Param("status") ContactStatus status,
                                           @Param("normalizedName") String normalizedName,
                                           Pageable pageable);
}
