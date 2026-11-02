package com.opencrm.crm.territory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * One row per organization, including a soft-deleted row that records an unassigned account.
 */
@Repository
public interface OrganizationTerritoryAssignmentRepository
        extends JpaRepository<OrganizationTerritoryAssignment, Long> {

    Optional<OrganizationTerritoryAssignment> findByOrganizationId(Long organizationId);
}
