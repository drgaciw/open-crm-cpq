package com.opencrm.contract.repository;

import com.opencrm.contract.domain.SubscriptionRatePeriod;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRatePeriodRepository extends JpaRepository<SubscriptionRatePeriod, Long> {
    List<SubscriptionRatePeriod> findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(Long subscriptionId);

    @Query("SELECT DISTINCT r.subscriptionId FROM SubscriptionRatePeriod r, Subscription s "
            + "WHERE s.id = r.subscriptionId AND s.status = 'ACTIVE' AND s.deleted = false "
            + "AND r.deleted = false AND r.retired = false AND r.applied = false AND r.startDate <= :date "
            + "ORDER BY r.subscriptionId")
    List<Long> findDueSubscriptionIds(@Param("date") LocalDate date, Pageable pageable);

    @Query("SELECT MIN(r.startDate) FROM SubscriptionRatePeriod r, Subscription s "
            + "WHERE s.id = r.subscriptionId AND s.status = 'ACTIVE' AND s.deleted = false "
            + "AND r.deleted = false AND r.retired = false AND r.applied = false AND r.startDate <= :date")
    LocalDate findOldestDueDate(@Param("date") LocalDate date);
}
