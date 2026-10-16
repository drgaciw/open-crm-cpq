package com.opencrm.contract.service;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.contract.acl.ErpAdapter;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.dto.SubscriptionDto;
import com.opencrm.contract.mapper.SubscriptionMapper;
import com.opencrm.contract.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SubscriptionMapper subscriptionMapper;
    private final SubscriptionRateApplyService rateApply;

    @Transactional(readOnly = true)
    public SubscriptionDto getById(Long id) {
        return subscriptionMapper.toDto(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionDto> list(Pageable pageable) {
        return subscriptionRepository.findAll(pageable).map(subscriptionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> getByCustomerId(String customerId) {
        return subscriptionMapper.toDtoList(subscriptionRepository.findByCustomerId(customerId));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> getByStatus(SubscriptionStatus status) {
        return subscriptionMapper.toDtoList(subscriptionRepository.findByStatus(status));
    }

    public SubscriptionDto activate(Long id) {
        Subscription subscription = subscriptionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id));
        subscription.activate();
        rateApply.applyLocked(subscription);

        // Sync to ERP on activation — do not remove or bypass ErpAdapter
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
        return subscriptionMapper.toDto(saved);
    }

    public SubscriptionDto suspend(Long id) {
        Subscription subscription = findEntityById(id);
        subscription.suspend();
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Suspended subscription: {}", saved.getSubscriptionNumber());
        return subscriptionMapper.toDto(saved);
    }

    public SubscriptionDto cancel(Long id) {
        Subscription subscription = findEntityById(id);
        subscription.cancel();
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Cancelled subscription: {}", saved.getSubscriptionNumber());
        return subscriptionMapper.toDto(saved);
    }

    private Subscription findEntityById(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id));
    }
}
