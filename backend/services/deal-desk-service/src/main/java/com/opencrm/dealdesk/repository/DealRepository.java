package com.opencrm.dealdesk.repository;

import com.opencrm.dealdesk.model.Deal;
import com.opencrm.dealdesk.model.DealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DealRepository extends JpaRepository<Deal, Long> {

    List<Deal> findAllByDeletedFalseOrderByCreatedAtDesc();

    Optional<Deal> findByIdAndDeletedFalse(Long id);

    @Query("""
            select d
            from Deal d
            where d.deleted = false
              and (:status is null or d.status = :status)
              and (:accountId is null or d.accountId = :accountId)
              and (:minValue is null or d.value >= :minValue)
              and (:salesforceOpportunityId is null or d.salesforceOpportunityId = :salesforceOpportunityId)
              and (:synced is null or d.synced = :synced)
              and (:createdAfter is null or d.createdAt >= :createdAfter)
            order by d.createdAt desc
            """)
    List<Deal> findAllByFilters(
            @Param("status") DealStatus status,
            @Param("accountId") String accountId,
            @Param("minValue") BigDecimal minValue,
            @Param("salesforceOpportunityId") String salesforceOpportunityId,
            @Param("synced") Boolean synced,
            @Param("createdAfter") Instant createdAfter
    );
}
