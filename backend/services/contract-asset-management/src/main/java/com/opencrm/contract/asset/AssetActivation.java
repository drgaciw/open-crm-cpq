package com.opencrm.contract.asset;

import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Legacy acceptance-time writer; held off until an effective-activation implementation exists. */
@Service
@ConditionalOnProperty(prefix = "opencrm.assets", name = "legacy-acceptance-write-enabled", havingValue = "true")
public class AssetActivation {
    private final AssetRepository assets;
    private final EntitlementLookup entitlements;
    private final AssetEventPublisher events;
    private final AssetAuditOutbox audit;
    /** Flushes the subscription row before the asset insert so the foreign key is visible. */
    private EntityManager entityManager;

    public AssetActivation(AssetRepository assets, EntitlementLookup entitlements,
                           AssetEventPublisher events, AssetAuditOutbox audit) {
        this.assets = assets;
        this.entitlements = entitlements;
        this.events = events;
        this.audit = audit;
    }

    @Autowired(required = false)
    void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public List<Asset> onActivated(QuoteAcceptedEvent event, Subscription subscription,
                                   List<SubscriptionRatePeriod> periods) {
        if (event == null || subscription == null || subscription.getId() == null || periods == null) {
            return List.of();
        }
        // accountId = QuoteAcceptedEvent.customerId (CustomerSnapshot); same key CAM subscriptions use.
        String accountId = event.getCustomerId();
        String correlationId = event.getCorrelation() == null ? null : event.getCorrelation().getCorrelationId();
        Map<String, List<SubscriptionRatePeriod>> byLine = new LinkedHashMap<>();
        for (SubscriptionRatePeriod period : periods) {
            if (period.getAcceptedLineUuid() == null || period.getAcceptedLineUuid().isBlank()) {
                continue;
            }
            byLine.computeIfAbsent(period.getAcceptedLineUuid(), ignored -> new ArrayList<>()).add(period);
        }
        if (entityManager != null) {
            entityManager.flush();
        }
        List<Asset> result = new ArrayList<>();
        for (Map.Entry<String, List<SubscriptionRatePeriod>> entry : byLine.entrySet()) {
            String contractLineId = entry.getKey();
            Asset existing = assets.findBySubscriptionIdAndContractLineIdAndDeletedFalse(
                    subscription.getId(), contractLineId).orElse(null);
            if (existing != null) {
                result.add(existing);
                continue;
            }
            List<SubscriptionRatePeriod> linePeriods = entry.getValue();
            SubscriptionRatePeriod first = linePeriods.getFirst();
            LocalDate start = linePeriods.stream().map(SubscriptionRatePeriod::getStartDate)
                    .min(LocalDate::compareTo).orElseThrow();
            LocalDate end = linePeriods.stream().map(SubscriptionRatePeriod::getEndDate)
                    .max(LocalDate::compareTo).orElseThrow();
            String productRef = first.getProductExternalId();
            if (productRef == null || productRef.isBlank()) {
                productRef = first.getProductName();
            }
            String entitlementId = entitlements.findEntitlementId(contractLineId).orElse(null);
            Asset asset = assets.save(Asset.builder()
                    .accountId(accountId)
                    .contractLineId(contractLineId)
                    .subscriptionId(subscription.getId())
                    .entitlementId(entitlementId)
                    .productRef(productRef)
                    .quantity(first.getQuantity())
                    .status(AssetStatus.ACTIVE)
                    .effectiveStart(start)
                    .effectiveEnd(end)
                    .build());
            String causation = event.getCorrelation() == null ? null : event.getCorrelation().getCausationId();
            AssetEventPublisher.Created created = events.publishCreated(asset, correlationId, causation);
            audit.stage(asset, created.eventId(), correlationId);
            result.add(asset);
        }
        return result;
    }
}
