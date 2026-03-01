package com.opencrm.contract.service;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.contract.acl.ErpAdapter;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service layer for subscription lifecycle management.
 * Authoritative source for subscription/asset state.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final ErpAdapter erpAdapter;

    @Transactional(readOnly = true)
    public Subscription getById(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id));
    }

    @Transactional(readOnly = true)
    public List<Subscription> getByCustomerId(String customerId) {
        return subscriptionRepository.findByCustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<Subscription> getByStatus(SubscriptionStatus status) {
        return subscriptionRepository.findByStatus(status);
    }

    public Subscription activate(Long id) {
        Subscription subscription = getById(id);
        subscription.activate();

        // Sync to ERP on activation
        try {
            String erpId = erpAdapter.syncToErp(subscription);
            subscription.setErpSubscriptionId(erpId);
            subscription.setErpSyncStatus("SYNCED");
        } catch (Exception e) {
            log.error("Failed to sync subscription {} to ERP", subscription.getSubscriptionNumber(), e);
            subscription.setErpSyncStatus("FAILED");
        }

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Activated subscription: {}", saved.getSubscriptionNumber());
        return saved;
    }

    public Subscription suspend(Long id) {
        Subscription subscription = getById(id);
        subscription.suspend();
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Suspended subscription: {}", saved.getSubscriptionNumber());
        return saved;
    }

    public Subscription cancel(Long id) {
        Subscription subscription = getById(id);
        subscription.cancel();
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Cancelled subscription: {}", saved.getSubscriptionNumber());
        return saved;
    }

    public Subscription renew(Long id, LocalDate newEndDate) {
        Subscription subscription = getById(id);
        subscription.renew(newEndDate);
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Renewed subscription: {} until {}", saved.getSubscriptionNumber(), newEndDate);
        return saved;
    }
}
