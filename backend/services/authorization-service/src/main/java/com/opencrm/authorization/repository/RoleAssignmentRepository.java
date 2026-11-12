package com.opencrm.authorization.repository;

import com.opencrm.authorization.entity.RoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, Long> {

    @Query("select ra from RoleAssignment ra where ra.principalId = :principalId and ra.role.name = :roleName "
            + "and ra.revokedAt is null")
    Optional<RoleAssignment> findActiveAssignment(@Param("principalId") String principalId,
                                                   @Param("roleName") String roleName);

    @Query("select ra from RoleAssignment ra where ra.principalId = :principalId and ra.revokedAt is null")
    List<RoleAssignment> findActiveAssignments(@Param("principalId") String principalId);

    /**
     * The effective (action, resource) pairs of every permission granted, through any active role, to
     * {@code principalId}. Backs {@link com.opencrm.authorization.service.AuthorizationQueryService}'s cache loader:
     * one query per cache miss, nothing per cache hit.
     */
    @Query("select distinct p.action, p.resource from RoleAssignment ra "
            + "join ra.role r join r.permissions p "
            + "where ra.principalId = :principalId and ra.revokedAt is null")
    List<Object[]> findEffectivePermissionRows(@Param("principalId") String principalId);
}
