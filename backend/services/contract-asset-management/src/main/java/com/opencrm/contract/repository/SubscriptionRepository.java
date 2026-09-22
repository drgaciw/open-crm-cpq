package com.opencrm.contract.repository;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByCustomerId(String customerId);

    List<Subscription> findByStatus(SubscriptionStatus status);

    @Query("SELECT s FROM Subscription s WHERE s.renewalDate <= :date AND s.status = 'ACTIVE'")
    List<Subscription> findRenewalEligible(LocalDate date);

    boolean existsBySubscriptionNumber(String subscriptionNumber);

    @Query("SELECT s.id FROM Subscription s WHERE s.erpSyncStatus = :status ORDER BY s.id")
    List<Long> findIdsByErpSyncStatus(@Param("status") String status, Pageable page);

    /**
     * Compare-and-set on {@code erp_sync_status}: moves {@code id} from {@code from} to {@code to}. Returns 1 if this
     * caller made the move, 0 if the row was not in {@code from} (another replica got there first).
     */
    @Modifying
    @Query("UPDATE Subscription s SET s.erpSyncStatus = :to, s.updatedAt = :now "
            + "WHERE s.id = :id AND s.erpSyncStatus = :from")
    int transitionErpSyncStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to,
                                @Param("now") Instant now);

    /** Returns claims left {@code IN_PROGRESS} by a crashed run to {@code QUEUED}. */
    @Modifying
    @Query("UPDATE Subscription s SET s.erpSyncStatus = 'QUEUED', s.updatedAt = :now "
            + "WHERE s.erpSyncStatus = 'IN_PROGRESS' AND s.updatedAt < :staleBefore")
    int requeueStaleErpSyncs(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
