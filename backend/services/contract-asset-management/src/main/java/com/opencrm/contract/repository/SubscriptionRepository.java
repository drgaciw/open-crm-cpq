package com.opencrm.contract.repository;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByCustomerId(String customerId);

    List<Subscription> findByStatus(SubscriptionStatus status);

    @Query("SELECT s FROM Subscription s WHERE s.renewalDate <= :date AND s.status = 'ACTIVE'")
    List<Subscription> findRenewalEligible(LocalDate date);

    boolean existsBySubscriptionNumber(String subscriptionNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.id = :id AND s.deleted = false")
    Optional<Subscription> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.subscriptionNumber = :number AND s.deleted = false")
    Optional<Subscription> findByNumberForUpdate(@Param("number") String number);

    Optional<Subscription> findBySubscriptionNumberAndDeletedFalse(String subscriptionNumber);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Subscription s SET s.erpSyncStatus = 'IN_PROGRESS', s.erpSyncClaimToken = :token, "
            + "s.erpSyncClaimRevision = s.commercialRevision, s.updatedAt = :now, s.version = s.version + 1 "
            + "WHERE s.id = :id AND s.erpSyncStatus = 'QUEUED' AND s.deleted = false")
    int claimErpSync(@Param("id") Long id, @Param("token") String token, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Subscription s SET s.erpSyncStatus = :status, s.erpSyncClaimToken = null, s.erpSyncClaimRevision = null, "
            + "s.updatedAt = :now, s.version = s.version + 1 WHERE s.id = :id "
            + "AND s.erpSyncStatus = 'IN_PROGRESS' AND s.erpSyncClaimToken = :token "
            + "AND s.erpSyncClaimRevision = :revision AND s.commercialRevision = :revision AND s.deleted = false")
    int finishErpSyncClaim(@Param("id") Long id, @Param("token") String token,
                         @Param("revision") Long revision, @Param("status") String status, @Param("now") Instant now);

    @Query("SELECT s.id FROM Subscription s WHERE s.erpSyncStatus = :status AND s.deleted = false ORDER BY s.id")
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
    @Query("UPDATE Subscription s SET s.erpSyncStatus = 'QUEUED', s.erpSyncClaimToken = null, s.erpSyncClaimRevision = null, "
            + "s.updatedAt = :now, s.version = s.version + 1 "
            + "WHERE s.erpSyncStatus = 'IN_PROGRESS' AND s.updatedAt < :staleBefore AND s.deleted = false")
    int requeueStaleErpSyncs(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
