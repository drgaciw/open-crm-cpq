package com.opencrm.contract.repository;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    
    List<Subscription> findByCustomerId(String customerId);
    
    List<Subscription> findByStatus(SubscriptionStatus status);

    boolean existsBySubscriptionNumber(String subscriptionNumber);
    
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.status = 'ACTIVE'
              AND s.renewalDate IS NOT NULL
              AND s.renewalDate <= :date
              AND s.endDate >= :date
            """)
    List<Subscription> findRenewalEligible(LocalDate date);
}
