package com.opencrm.crm.buyingrole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Buying-role rows. Queries are static JPQL with bound parameters.
 */
@Repository
public interface DealBuyingRoleRepository extends JpaRepository<DealBuyingRole, Long> {

    @Query("""
            SELECT assignment
            FROM DealBuyingRole assignment
            WHERE assignment.deal.id = :dealId
              AND assignment.deleted = false
              AND assignment.contact.deleted = false
            ORDER BY assignment.id
            """)
    List<DealBuyingRole> findActiveByDealId(@Param("dealId") Long dealId);

    @Query("""
            SELECT assignment
            FROM DealBuyingRole assignment
            WHERE assignment.deal.id = :dealId
              AND assignment.contact.id = :contactId
            """)
    Optional<DealBuyingRole> findByDealIdAndContactId(@Param("dealId") Long dealId,
                                                      @Param("contactId") Long contactId);
}
